package ai.daylight.assistant.domain

import ai.daylight.assistant.domain.DocumentMarkdown.Block
import ai.daylight.assistant.domain.DocumentMarkdown.Run
import kotlin.math.sqrt

/**
 * v5.11: layout decisions for generated documents that do not need a font engine, so the
 * DOCX writer, the Android PdfDocument renderer and tests share them: list markers, table
 * column widths, the flat PDF item plan (roles, indents, spacing) and pagination over
 * measured line heights. The PDF renderer only measures text and draws.
 */
object DocumentLayout {

    // ── Lists ───────────────────────────────────────────────────────────────

    /** Marker text for item [index] (0-based) of a list at [depth] 0 or 1. */
    fun listMarker(ordered: Boolean, start: Int, index: Int, depth: Int): String = when {
        !ordered -> if (depth == 0) "•" else "◦"
        depth == 0 -> "${start + index}."
        else -> "${alphabetic(start + index)}."
    }

    /** 1 -> a, 26 -> z, 27 -> aa (spreadsheet-style letters, lower case). */
    fun alphabetic(number: Int): String {
        var value = number.coerceAtLeast(1)
        val out = StringBuilder()
        while (value > 0) {
            value--
            out.append('a' + value % 26)
            value /= 26
        }
        return out.reverse().toString()
    }

    // ── Tables ──────────────────────────────────────────────────────────────

    /**
     * Splits [totalWidth] across a table's columns by content length. Weights grow with the
     * square root of the longest cell so one long description does not starve short columns,
     * and every column gets at least [minWidth] (when the total allows it).
     */
    fun columnWidths(table: DocumentMarkdown.Table, totalWidth: Float, minWidth: Float): List<Float> {
        val count = table.columnCount.coerceAtLeast(1)
        val lengths = List(count) { column ->
            val header = table.header.getOrNull(column)?.let(DocumentMarkdown::plain).orEmpty()
            val longest = table.rows.maxOfOrNull { row -> row.getOrNull(column)?.let(DocumentMarkdown::plain)?.length ?: 0 } ?: 0
            maxOf(header.length, longest, 1)
        }
        val weights = lengths.map { sqrt(it.toFloat()) + 1.5f }
        val floor = minWidth.coerceAtMost(totalWidth / count)
        val flexible = totalWidth - floor * count
        val weightSum = weights.sum()
        return weights.map { floor + flexible * it / weightSum }
    }

    // ── PDF item plan ───────────────────────────────────────────────────────

    enum class Role { H1, H2, H3, BODY, QUOTE, CODE }

    sealed interface Item {
        val spaceBefore: Float
        val spaceAfter: Float
    }

    /**
     * One block of text. [indent] is the left edge of the text; a [marker] (bullet or number)
     * hangs in the space left of it.
     */
    data class TextItem(
        val role: Role,
        val runs: List<Run>,
        val indent: Float = 0f,
        val marker: String? = null,
        override val spaceBefore: Float,
        override val spaceAfter: Float,
        val keepWithNext: Boolean = false
    ) : Item

    data class TableRowItem(
        val cells: List<List<Run>>,
        val columnWidths: List<Float>,
        val header: Boolean,
        override val spaceBefore: Float,
        override val spaceAfter: Float,
        val keepWithNext: Boolean = false
    ) : Item

    data class RuleItem(override val spaceBefore: Float = 6f, override val spaceAfter: Float = 10f) : Item

    /** Hanging indent of one list level, in points. */
    const val LIST_INDENT = 18f

    /** Flattens [blocks] into PDF items for a text column [contentWidth] points wide. */
    fun pdfItems(blocks: List<Block>, contentWidth: Float): List<Item> {
        val items = mutableListOf<Item>()
        blocks.forEach { block ->
            when (block) {
                is DocumentMarkdown.Heading -> items += TextItem(
                    role = when (block.level) { 1 -> Role.H1; 2 -> Role.H2; else -> Role.H3 },
                    runs = block.runs,
                    spaceBefore = when (block.level) { 1 -> 18f; 2 -> 14f; else -> 10f },
                    spaceAfter = when (block.level) { 1 -> 8f; else -> 5f },
                    keepWithNext = true
                )
                is DocumentMarkdown.Paragraph -> items += TextItem(Role.BODY, block.runs, spaceBefore = 0f, spaceAfter = 8f)
                is DocumentMarkdown.Quote -> block.paragraphs.forEachIndexed { index, runs ->
                    items += TextItem(
                        Role.QUOTE, runs, indent = 14f,
                        spaceBefore = if (index == 0) 2f else 0f,
                        spaceAfter = if (index == block.paragraphs.lastIndex) 10f else 4f
                    )
                }
                is DocumentMarkdown.CodeBlock -> items += TextItem(
                    Role.CODE, listOf(Run(block.text.ifEmpty { " " }, code = true)), indent = 8f,
                    spaceBefore = 6f, spaceAfter = 12f
                )
                DocumentMarkdown.Rule -> items += RuleItem()
                is DocumentMarkdown.ListBlock -> {
                    addList(block, depth = 0, items = items)
                    val last = items.lastOrNull()
                    if (last is TextItem) items[items.lastIndex] = last.copy(spaceAfter = 9f)
                }
                is DocumentMarkdown.Table -> {
                    val widths = columnWidths(block, contentWidth, minWidth = 48f)
                    items += TableRowItem(block.header, widths, header = true, spaceBefore = 4f, spaceAfter = 0f, keepWithNext = true)
                    block.rows.forEachIndexed { index, row ->
                        items += TableRowItem(
                            row, widths, header = false, spaceBefore = 0f,
                            spaceAfter = if (index == block.rows.lastIndex) 12f else 0f
                        )
                    }
                    if (block.rows.isEmpty()) {
                        val header = items.removeAt(items.lastIndex) as TableRowItem
                        items += header.copy(spaceAfter = 12f, keepWithNext = false)
                    }
                }
            }
        }
        return items
    }

    private fun addList(list: DocumentMarkdown.ListBlock, depth: Int, items: MutableList<Item>) {
        list.items.forEachIndexed { index, item ->
            items += TextItem(
                role = Role.BODY,
                runs = item.runs,
                indent = LIST_INDENT * (depth + 1),
                marker = listMarker(list.ordered, list.start, index, depth),
                spaceBefore = 0f,
                spaceAfter = 3f
            )
            item.children?.let { addList(it, depth = 1, items = items) }
        }
    }

    // ── Pagination ──────────────────────────────────────────────────────────

    /**
     * An item after measuring: the height of each of its lines. A splittable item may break
     * between lines across pages; table rows and rules move to the next page whole.
     */
    data class Measure(
        val lineHeights: List<Float>,
        val spaceBefore: Float,
        val spaceAfter: Float,
        val splittable: Boolean,
        val keepWithNext: Boolean = false
    ) {
        val height: Float get() = lineHeights.sum()
    }

    /** Lines [fromLine, toLine) of item [item] drawn on [page] with their top at [y]. */
    data class Slice(val item: Int, val page: Int, val y: Float, val fromLine: Int, val toLine: Int)

    /**
     * Places measured items on pages [pageHeight] tall (the text area, margins excluded).
     * Space before an item is dropped at the top of a page. A heading moves to the next page
     * rather than end one without the first line of what follows it, and a paragraph never
     * leaves a single line at the bottom of a page when it has more than two lines.
     */
    fun paginate(measures: List<Measure>, pageHeight: Float): List<Slice> {
        val slices = mutableListOf<Slice>()
        var page = 0
        var y = 0f
        measures.forEachIndexed { index, measure ->
            if (measure.lineHeights.isEmpty()) return@forEachIndexed
            var from = 0
            while (from < measure.lineHeights.size) {
                val before = if (y == 0f || from > 0) 0f else measure.spaceBefore
                val remaining = measure.lineHeights.subList(from, measure.lineHeights.size)
                val remainingHeight = remaining.sum()
                if (from == 0 && measure.keepWithNext && y > 0f) {
                    val next = measures.getOrNull(index + 1)
                    val needed = before + remainingHeight + (next?.let { it.spaceBefore + (it.lineHeights.firstOrNull() ?: 0f) } ?: 0f)
                    if (y + needed > pageHeight) {
                        page++
                        y = 0f
                        continue
                    }
                }
                if (y + before + remainingHeight <= pageHeight || (!measure.splittable && y == 0f)) {
                    // Fits (or cannot be split and already starts a page: drawn clipped).
                    slices += Slice(index, page, y + before, from, measure.lineHeights.size)
                    y += before + remainingHeight + measure.spaceAfter
                    from = measure.lineHeights.size
                    continue
                }
                if (!measure.splittable) {
                    page++
                    y = 0f
                    continue
                }
                // Split: take as many lines as fit on this page.
                var fit = 0
                var used = 0f
                while (fit < remaining.size && y + before + used + remaining[fit] <= pageHeight) {
                    used += remaining[fit]
                    fit++
                }
                val wouldOrphan = from == 0 && fit == 1 && measure.lineHeights.size > 2
                if ((fit == 0 || wouldOrphan) && y > 0f) {
                    page++
                    y = 0f
                    continue
                }
                if (fit == 0) fit = 1 // a single line taller than a page: draw it clipped
                slices += Slice(index, page, y + before, from, from + fit)
                from += fit
                page++
                y = 0f
            }
        }
        return slices
    }
}
