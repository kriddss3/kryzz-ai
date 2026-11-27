package ai.daylight.assistant.domain

import ai.daylight.assistant.domain.DocumentMarkdown.Run
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * v5.13.7: Markdown to slides, for the .pptx writer (PresentationGenerator).
 *
 * Slides are split at `---` rules and at slide-level headings. The slide level is the top
 * heading level, or the next one when a single top heading opens the text ("# Deck" then
 * "## Slide" headings). Deeper headings become bold sub-heads on their slide. A first slide
 * with only a heading and a short line or two becomes the dark cover.
 *
 * PowerPoint does not shrink text when a file is opened, so the sizes are chosen here: the
 * body takes the largest size whose estimated height fits, and text that does not fit even
 * at the smallest size continues on a "(cont.)" slide, as do tables (header repeated).
 * The estimates are deliberately generous; a slide with room to spare beats one that spills.
 *
 * All measures are in points on a 16:9 slide of 960 x 540. Pure and unit tested.
 */
internal object SlideDeck {

    const val SLIDE_W = 960.0
    const val SLIDE_H = 540.0
    const val MARGIN_X = 48.0
    const val CONTENT_W = SLIDE_W - 2 * MARGIN_X
    const val TITLE_Y = 28.0
    const val TITLE_H = 76.0
    const val RULE_Y = 114.0
    const val BODY_Y = 134.0
    const val BODY_H = 350.0
    const val FOOTER_Y = 500.0
    const val GAP = 14.0
    const val MAX_SLIDES = 200

    /** Cell margins (PowerPoint's defaults): 7.2 pt left and right, 3.6 pt top and bottom. */
    const val CELL_PAD_X = 7.2
    const val CELL_PAD_Y = 3.6

    private val BODY_SIZES = listOf(28, 24, 22, 20, 18, 16, 14)
    private val TABLE_SIZES = listOf(16, 14, 13, 12, 11, 10)
    private const val SPLIT_SIZE = 18
    private const val SPLIT_TABLE_SIZE = 12
    private const val CHAR_EM = 0.5
    private const val WRAP_SLACK = 0.9
    private const val LINE = 1.2

    enum class Marker { NONE, BULLET, NUMBER }
    enum class Style { BODY, SUBHEAD, QUOTE, CODE }

    /** One paragraph on a slide. [number] is the item's number in an ordered list. */
    data class Line(
        val runs: List<Run>,
        val level: Int = 0,
        val marker: Marker = Marker.NONE,
        val number: Int = 0,
        val style: Style = Style.BODY
    )

    data class TablePlan(
        val header: List<List<Run>>,
        val rows: List<List<List<Run>>>,
        val size: Int,
        val columnWidths: List<Double>,
        val rowHeights: List<Double>,
        /** Columns whose body cells are all numbers; they are right-aligned. */
        val numeric: List<Boolean>
    ) {
        val height: Double get() = rowHeights.sum()
    }

    data class Slide(
        val title: List<Run>,
        val titleSize: Int,
        val cover: Boolean = false,
        val subtitle: List<List<Run>> = emptyList(),
        val lines: List<Line> = emptyList(),
        val bodySize: Int = 24,
        /** Estimated height of [lines]; a table goes below it. */
        val bodyHeight: Double = 0.0,
        val table: TablePlan? = null
    )

    private class Raw(var title: List<Run>?, val blocks: MutableList<DocumentMarkdown.Block> = mutableListOf()) {
        val empty: Boolean get() = title == null && blocks.isEmpty()
    }

    /** A run of content that shares one slide: some lines, then at most one table. */
    private class Part(val lines: MutableList<Line> = mutableListOf(), var table: DocumentMarkdown.Table? = null)

    fun plan(markdown: String): List<Slide> {
        val blocks = DocumentMarkdown.parse(markdown)
        val slideLevel = slideLevel(blocks)
        val raws = mutableListOf<Raw>()
        var current = Raw(null)
        fun flush() {
            if (!current.empty) raws += current
            current = Raw(null)
        }
        blocks.forEach { block ->
            when {
                block is DocumentMarkdown.Rule -> flush()
                block is DocumentMarkdown.Heading && block.level <= slideLevel -> {
                    if (!current.empty) flush()
                    current.title = block.runs
                }
                else -> current.blocks += block
            }
        }
        flush()
        if (raws.isEmpty()) return listOf(Slide(listOf(Run("Presentation")), titleSize(listOf(Run("Presentation")), cover = true), cover = true))

        val slides = mutableListOf<Slide>()
        raws.forEachIndexed { index, raw ->
            val title = raw.title.orEmpty()
            if (index == 0 && isCover(raw, raws.size)) {
                val subtitle = raw.blocks.filterIsInstance<DocumentMarkdown.Paragraph>().map { it.runs }
                slides += Slide(title, titleSize(title, cover = true), cover = true, subtitle = subtitle)
                return@forEachIndexed
            }
            parts(raw.blocks).forEachIndexed { partIndex, part ->
                val partTitle = if (partIndex == 0) title else continued(title)
                slides += fit(partTitle, part)
            }
        }
        return slides.take(MAX_SLIDES)
    }

    private fun slideLevel(blocks: List<DocumentMarkdown.Block>): Int {
        val headings = blocks.filterIsInstance<DocumentMarkdown.Heading>()
        val levels = headings.map { it.level }.distinct().sorted()
        val top = levels.firstOrNull() ?: return 0
        val singleOpeningTop = levels.size > 1 && headings.count { it.level == top } == 1 &&
            blocks.firstOrNull() === headings.first()
        return if (singleOpeningTop) levels[1] else top
    }

    private fun isCover(raw: Raw, slideCount: Int): Boolean {
        if (raw.title == null || slideCount < 2) return false
        if (raw.blocks.any { it !is DocumentMarkdown.Paragraph }) return false
        val text = raw.blocks.sumOf { DocumentMarkdown.plain((it as DocumentMarkdown.Paragraph).runs).length }
        return raw.blocks.size <= 3 && text <= 280
    }

    /** "Title (cont.)", once, in the title's own run when it is plain text. */
    private fun continued(title: List<Run>): List<Run> {
        if (title.isEmpty() || DocumentMarkdown.plain(title).endsWith(CONTINUED)) return title
        val last = title.last()
        return if (last.sameStyle(Run(""))) title.dropLast(1) + last.copy(text = last.text + CONTINUED)
        else title + Run(CONTINUED)
    }

    private const val CONTINUED = " (cont.)"

    /** Splits a slide's blocks into parts: lines, then a table, then the next part. */
    private fun parts(blocks: List<DocumentMarkdown.Block>): List<Part> {
        val parts = mutableListOf(Part())
        blocks.forEach { block ->
            val part = parts.last()
            if (block is DocumentMarkdown.Table) {
                if (part.table != null) parts += Part(table = block) else part.table = block
                return@forEach
            }
            val target = if (part.table != null) Part().also { parts += it } else part
            target.lines += linesOf(block)
        }
        return parts.filter { it.lines.isNotEmpty() || it.table != null }.ifEmpty { listOf(Part()) }
    }

    private fun linesOf(block: DocumentMarkdown.Block): List<Line> = when (block) {
        is DocumentMarkdown.Heading -> listOf(Line(block.runs, style = Style.SUBHEAD))
        is DocumentMarkdown.Paragraph -> listOf(Line(block.runs))
        is DocumentMarkdown.Quote -> block.paragraphs.filter { it.isNotEmpty() }.map { Line(it, style = Style.QUOTE) }
        is DocumentMarkdown.CodeBlock -> block.text.lines().map { Line(listOf(Run(it.ifEmpty { " " }, code = true)), style = Style.CODE) }
        is DocumentMarkdown.ListBlock -> buildList {
            block.items.forEachIndexed { index, item ->
                add(Line(item.runs, 0, if (block.ordered) Marker.NUMBER else Marker.BULLET, block.start + index))
                item.children?.let { child ->
                    child.items.forEachIndexed { childIndex, sub ->
                        add(Line(sub.runs, 1, if (child.ordered) Marker.NUMBER else Marker.BULLET, child.start + childIndex))
                    }
                }
            }
        }
        is DocumentMarkdown.Image -> listOf(Line(listOf(Run(block.alt.ifBlank { "Picture" }))))
        is DocumentMarkdown.Table, DocumentMarkdown.Rule -> emptyList()
    }

    // ── Fitting ───────────────────────────────────────────────────────────────

    private fun fit(title: List<Run>, part: Part): List<Slide> {
        val table = part.table
        val titleSize = titleSize(title, cover = false)
        if (table == null) return fitText(title, titleSize, part.lines)
        if (part.lines.isEmpty()) return fitTable(title, titleSize, table)
        // Text above a table gets at most about half the body.
        val textSize = BODY_SIZES.filter { it <= 22 }.firstOrNull { bodyHeight(part.lines, it) <= BODY_H * 0.5 }
        if (textSize != null) {
            val textHeight = bodyHeight(part.lines, textSize)
            val room = BODY_H - textHeight - GAP
            val tableSize = TABLE_SIZES.firstOrNull { tablePlan(table.header, table.rows, it).height <= room }
            if (tableSize != null) {
                return listOf(
                    Slide(title, titleSize, lines = part.lines, bodySize = textSize, bodyHeight = textHeight,
                        table = tablePlan(table.header, table.rows, tableSize))
                )
            }
        }
        // Too much for one slide: the text first, the table on its own slide after it.
        return fitText(title, titleSize, part.lines) +
            fitTable(continued(title), titleSize(continued(title), cover = false), table)
    }

    private fun fitText(title: List<Run>, titleSize: Int, lines: List<Line>): List<Slide> {
        BODY_SIZES.firstOrNull { bodyHeight(lines, it) <= BODY_H }?.let { size ->
            return listOf(Slide(title, titleSize, lines = lines, bodySize = size, bodyHeight = bodyHeight(lines, size)))
        }
        // Continue on more slides, filled at a readable size.
        val chunks = mutableListOf<MutableList<Line>>(mutableListOf())
        lines.forEach { line ->
            val chunk = chunks.last()
            if (chunk.isNotEmpty() && bodyHeight(chunk + line, SPLIT_SIZE) > BODY_H) chunks += mutableListOf(line)
            else chunk += line
        }
        // One size for the whole run of slides, so a short last slide does not jump to bigger text.
        val size = chunks.minOf { chunk -> BODY_SIZES.firstOrNull { bodyHeight(chunk, it) <= BODY_H } ?: BODY_SIZES.last() }
        return chunks.mapIndexed { index, chunk ->
            val chunkTitle = if (index == 0) title else continued(title)
            Slide(chunkTitle, titleSize(chunkTitle, cover = false), lines = chunk, bodySize = size, bodyHeight = bodyHeight(chunk, size))
        }
    }

    private fun fitTable(title: List<Run>, titleSize: Int, table: DocumentMarkdown.Table): List<Slide> {
        TABLE_SIZES.firstOrNull { tablePlan(table.header, table.rows, it).height <= BODY_H }?.let { size ->
            return listOf(Slide(title, titleSize, table = tablePlan(table.header, table.rows, size)))
        }
        // Rows continue on further slides, each with the header row again.
        val chunks = mutableListOf<MutableList<List<List<Run>>>>(mutableListOf())
        table.rows.forEach { row ->
            val chunk = chunks.last()
            if (chunk.isNotEmpty() && tablePlan(table.header, chunk + listOf(row), SPLIT_TABLE_SIZE).height > BODY_H) chunks += mutableListOf(row)
            else chunk += row
        }
        // One size for every slide of the table, as for text.
        val size = chunks.minOf { rows -> TABLE_SIZES.firstOrNull { tablePlan(table.header, rows, it).height <= BODY_H } ?: TABLE_SIZES.last() }
        return chunks.mapIndexed { index, rows ->
            val chunkTitle = if (index == 0) title else continued(title)
            Slide(chunkTitle, titleSize(chunkTitle, cover = false), table = tablePlan(table.header, rows, size))
        }
    }

    // ── Measures ──────────────────────────────────────────────────────────────

    /** Lines [text] wraps to at [size] points in [width] points (one per hard break at least). */
    fun wrappedLines(text: String, size: Double, width: Double): Int {
        val perLine = max(6, floor(width * WRAP_SLACK / (size * CHAR_EM)).toInt())
        return text.split('\n').sumOf { segment -> max(1, ceil(segment.length / perLine.toDouble()).toInt()) }
    }

    fun indentOf(line: Line): Double = when {
        line.marker != Marker.NONE -> if (line.level == 0) 27.0 else 54.0
        line.style == Style.QUOTE -> 18.0
        else -> 0.0
    }

    fun textSize(line: Line, bodySize: Int): Double = if (line.style == Style.CODE) bodySize * 0.8 else bodySize.toDouble() *
        (if (line.level > 0) 0.9 else 1.0)

    fun spaceBefore(line: Line, previous: Line?, bodySize: Int): Double = when {
        line.style == Style.CODE && previous?.style == Style.CODE -> 0.0
        line.style == Style.SUBHEAD -> bodySize * 0.8
        line.level > 0 -> bodySize * 0.25
        else -> bodySize * 0.4
    }

    fun bodyHeight(lines: List<Line>, size: Int): Double = lines.withIndex().sumOf { (index, line) ->
        val textSize = textSize(line, size)
        val rows = wrappedLines(DocumentMarkdown.plain(line.runs), textSize, CONTENT_W - indentOf(line))
        rows * textSize * LINE + spaceBefore(line, lines.getOrNull(index - 1), size)
    }

    private val numberCell = Regex("""^[-+−]?[${'$'}€£¥]?\s?\d[\d,.\s']*(%|[kKmMbB])?${'$'}""")

    fun tablePlan(header: List<List<Run>>, rows: List<List<List<Run>>>, size: Int): TablePlan {
        val columns = header.size.coerceAtLeast(1)
        val lengths = (0 until columns).map { column ->
            (listOf(header.getOrElse(column) { emptyList() }) + rows.map { it.getOrElse(column) { emptyList() } })
                .maxOf { DocumentMarkdown.plain(it).length }
                .coerceIn(3, 40)
                .toDouble()
        }
        // The square root keeps one long column from squeezing the others to nothing.
        val weights = lengths.map(::sqrt)
        val widths = weights.map { CONTENT_W * it / weights.sum() }
        fun rowHeight(cells: List<List<Run>>, bold: Boolean): Double {
            val lines = (0 until columns).maxOf { column ->
                val text = DocumentMarkdown.plain(cells.getOrElse(column) { emptyList() })
                wrappedLines(text, size * (if (bold) 1.05 else 1.0), widths[column] - 2 * CELL_PAD_X)
            }
            return lines * size * LINE + 2 * CELL_PAD_Y + 2
        }
        val numeric = (0 until columns).map { column ->
            val cells = rows.map { DocumentMarkdown.plain(it.getOrElse(column) { emptyList() }).trim() }.filter(String::isNotEmpty)
            cells.isNotEmpty() && cells.all { numberCell.matches(it) }
        }
        return TablePlan(header, rows, size, widths, listOf(rowHeight(header, true)) + rows.map { rowHeight(it, false) }, numeric)
    }

    /** The title's size: 32 (44 on the cover) when it fits, smaller for long titles. */
    fun titleSize(title: List<Run>, cover: Boolean): Int {
        val text = DocumentMarkdown.plain(title)
        val options = if (cover) listOf(44 to 2, 36 to 3, 30 to 4) else listOf(32 to 1, 28 to 2, 24 to 2)
        return options.firstOrNull { (size, maxLines) -> wrappedLines(text, size.toDouble(), CONTENT_W) <= maxLines }?.first
            ?: options.last().first
    }
}
