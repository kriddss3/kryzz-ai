package ai.daylight.assistant.data

import ai.daylight.assistant.domain.DocumentLayout
import ai.daylight.assistant.domain.DocumentLayout.Role
import ai.daylight.assistant.domain.DocumentMarkdown
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import java.io.ByteArrayOutputStream

/**
 * v5.11: renders document Markdown to PDF with Android's PdfDocument. Text goes through
 * StaticLayout with system fonts, so Latvian, Polish, Greek, Cyrillic and CJK characters
 * survive (the standard-14 fallback writer turns them into '?'). Layout decisions (items,
 * indents, column widths, page breaks) come from DocumentLayout; this class measures text
 * and draws. A4 pages with page numbers.
 *
 * Callers fall back to OfficeFileGenerator.pdf when this throws.
 */
object PdfDocumentRenderer {
    private const val PAGE_WIDTH = 595 // A4 in points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 56f
    private const val MARGIN_TOP = 56f
    private const val MARGIN_BOTTOM = 64f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN_X
    private const val CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM
    private const val CELL_PAD_X = 6f
    private const val CELL_PAD_Y = 4f
    private const val CODE_PAD = 6f

    private val INK = Color.rgb(0x1F, 0x29, 0x37)
    private val MUTED = Color.rgb(0x4B, 0x55, 0x63)
    private val BRAND = Color.rgb(0x08, 0x7F, 0x5B)
    private val LINK = Color.rgb(0x05, 0x63, 0xC1)
    private val RULE = Color.rgb(0xC9, 0xCE, 0xD6)
    private val CODE_BG = Color.rgb(0xF3, 0xF4, 0xF6)
    private val HEADER_BG = Color.rgb(0xE3, 0xF1, 0xEC)

    /** A measured item: its StaticLayouts (one, or one per table cell) and its line heights. */
    private class Laid(val item: DocumentLayout.Item, val layouts: List<StaticLayout?>, val measure: DocumentLayout.Measure)

    fun render(markdown: String): ByteArray {
        val items = DocumentLayout.pdfItems(DocumentMarkdown.parse(markdown), CONTENT_WIDTH)
            .ifEmpty { listOf(DocumentLayout.TextItem(Role.BODY, listOf(DocumentMarkdown.Run(" ")), spaceBefore = 0f, spaceAfter = 0f)) }
        val laid = items.map(::measure)
        val slices = DocumentLayout.paginate(laid.map { it.measure }, CONTENT_HEIGHT)
        val pageCount = (slices.maxOfOrNull { it.page } ?: 0) + 1

        val document = PdfDocument()
        try {
            val byPage = slices.groupBy { it.page }
            for (page in 0 until pageCount) {
                val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, page + 1).create())
                val canvas = pdfPage.canvas
                byPage[page].orEmpty().forEach { slice -> draw(canvas, laid[slice.item], slice) }
                if (pageCount > 1) drawPageNumber(canvas, page + 1, pageCount)
                document.finishPage(pdfPage)
            }
            val out = ByteArrayOutputStream()
            document.writeTo(out)
            return out.toByteArray()
        } finally {
            document.close()
        }
    }

    // ── Measuring ───────────────────────────────────────────────────────────

    private fun measure(item: DocumentLayout.Item): Laid = when (item) {
        is DocumentLayout.TextItem -> {
            val inset = if (item.role == Role.CODE) CODE_PAD else 0f
            val width = (CONTENT_WIDTH - item.indent - inset * 2).coerceAtLeast(40f)
            val layout = layout(spanned(item.runs), paint(item.role), width, item.role)
            val heights = lineHeights(layout).toMutableList()
            if (item.role == Role.CODE && heights.isNotEmpty()) {
                // Room for the shaded box's padding above the first and below the last line.
                heights[0] += CODE_PAD
                heights[heights.lastIndex] += CODE_PAD
            }
            Laid(item, listOf(layout), DocumentLayout.Measure(heights, item.spaceBefore, item.spaceAfter, splittable = true, keepWithNext = item.keepWithNext))
        }
        is DocumentLayout.TableRowItem -> {
            val paint = paint(Role.BODY).apply { textSize = 9.5f; if (item.header) typeface = Typeface.DEFAULT_BOLD }
            val layouts = item.columnWidths.mapIndexed { column, width ->
                layout(spanned(item.cells.getOrElse(column) { emptyList() }), paint, (width - 2 * CELL_PAD_X).coerceAtLeast(12f), Role.BODY)
            }
            val height = (layouts.maxOfOrNull { it.height } ?: 0) + 2 * CELL_PAD_Y
            Laid(item, layouts, DocumentLayout.Measure(listOf(height), item.spaceBefore, item.spaceAfter, splittable = false, keepWithNext = item.keepWithNext))
        }
        is DocumentLayout.RuleItem -> Laid(item, emptyList(), DocumentLayout.Measure(listOf(1f), item.spaceBefore, item.spaceAfter, splittable = false))
    }

    private fun lineHeights(layout: StaticLayout): List<Float> =
        (0 until layout.lineCount).map { (layout.getLineBottom(it) - layout.getLineTop(it)).toFloat() }

    private fun paint(role: Role): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        textSize = when (role) {
            Role.H1 -> 19f
            Role.H2 -> 15f
            Role.H3 -> 12.5f
            Role.BODY, Role.QUOTE -> 10.5f
            Role.CODE -> 9f
        }
        typeface = when (role) {
            Role.H1, Role.H2, Role.H3 -> Typeface.DEFAULT_BOLD
            Role.QUOTE -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            Role.CODE -> Typeface.MONOSPACE
            Role.BODY -> Typeface.DEFAULT
        }
        when (role) {
            Role.H1 -> color = BRAND
            Role.QUOTE -> color = MUTED
            else -> Unit
        }
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Float, role: Role): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, if (role == Role.CODE) 1.1f else 1.25f)
            .setIncludePad(false)
            .build()

    private fun spanned(runs: List<DocumentMarkdown.Run>): CharSequence {
        val text = SpannableStringBuilder()
        runs.forEach { run ->
            val start = text.length
            text.append(run.text)
            val end = text.length
            if (start == end) return@forEach
            fun span(what: Any) = text.setSpan(what, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            when {
                run.bold && run.italic -> span(StyleSpan(Typeface.BOLD_ITALIC))
                run.bold -> span(StyleSpan(Typeface.BOLD))
                run.italic -> span(StyleSpan(Typeface.ITALIC))
            }
            if (run.code) {
                span(TypefaceSpan("monospace"))
                span(BackgroundColorSpan(CODE_BG))
            }
            if (run.strike) span(StrikethroughSpan())
            if (run.link != null) {
                span(ForegroundColorSpan(LINK))
                span(UnderlineSpan())
            }
        }
        return text
    }

    // ── Drawing ─────────────────────────────────────────────────────────────

    private fun draw(canvas: Canvas, laid: Laid, slice: DocumentLayout.Slice) {
        val top = MARGIN_TOP + slice.y
        when (val item = laid.item) {
            is DocumentLayout.TextItem -> drawText(canvas, item, laid.layouts.first()!!, slice, top)
            is DocumentLayout.TableRowItem -> drawRow(canvas, item, laid.layouts, laid.measure.height, top)
            is DocumentLayout.RuleItem -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE; strokeWidth = 0.8f }
                canvas.drawLine(MARGIN_X, top + 0.5f, MARGIN_X + CONTENT_WIDTH, top + 0.5f, paint)
            }
        }
    }

    private fun drawText(canvas: Canvas, item: DocumentLayout.TextItem, layout: StaticLayout, slice: DocumentLayout.Slice, top: Float) {
        val code = item.role == Role.CODE
        val left = MARGIN_X + item.indent
        val lineTop = layout.getLineTop(slice.fromLine).toFloat()
        val lineBottom = layout.getLineBottom(slice.toLine - 1).toFloat()
        // Code padding sits inside the measured heights of the first and last line.
        val padTop = if (code && slice.fromLine == 0) CODE_PAD else 0f
        val padBottom = if (code && slice.toLine == layout.lineCount) CODE_PAD else 0f
        val textTop = top + padTop
        if (code) {
            val box = Paint().apply { color = CODE_BG; style = Paint.Style.FILL }
            canvas.drawRect(left - 2f, top, MARGIN_X + CONTENT_WIDTH, textTop + (lineBottom - lineTop) + padBottom, box)
        }
        if (item.role == Role.QUOTE) {
            val bar = Paint().apply { color = BRAND; style = Paint.Style.FILL }
            canvas.drawRect(left - 10f, top, left - 7.5f, textTop + (lineBottom - lineTop), bar)
        }
        val textLeft = left + if (code) CODE_PAD else 0f
        canvas.save()
        canvas.translate(textLeft, textTop - lineTop)
        canvas.clipRect(0f, lineTop, layout.width.toFloat(), lineBottom)
        layout.draw(canvas)
        canvas.restore()
        val marker = item.marker
        if (marker != null && slice.fromLine == 0) {
            val paint = TextPaint(layout.paint).apply { color = MUTED }
            val baseline = textTop + layout.getLineBaseline(0) - lineTop
            val markerWidth = paint.measureText(marker)
            canvas.drawText(marker, left - 6f - markerWidth, baseline, paint)
        }
    }

    private fun drawRow(canvas: Canvas, item: DocumentLayout.TableRowItem, layouts: List<StaticLayout?>, height: Float, top: Float) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val fill = Paint().apply { color = HEADER_BG; style = Paint.Style.FILL }
        var x = MARGIN_X
        item.columnWidths.forEachIndexed { column, width ->
            if (item.header) canvas.drawRect(x, top, x + width, top + height, fill)
            canvas.drawRect(x, top, x + width, top + height, border)
            layouts.getOrNull(column)?.let { layout ->
                canvas.save()
                canvas.translate(x + CELL_PAD_X, top + CELL_PAD_Y)
                canvas.clipRect(0f, 0f, width - 2 * CELL_PAD_X, height - 2 * CELL_PAD_Y)
                layout.draw(canvas)
                canvas.restore()
            }
            x += width
        }
    }

    private fun drawPageNumber(canvas: Canvas, page: Int, pages: Int) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 8.5f; textAlign = Paint.Align.CENTER }
        canvas.drawText("$page / $pages", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM / 2f, paint)
    }
}
