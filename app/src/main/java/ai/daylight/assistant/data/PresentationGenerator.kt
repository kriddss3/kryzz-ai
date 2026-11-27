package ai.daylight.assistant.data

import ai.daylight.assistant.data.OfficeFileGenerator.xml
import ai.daylight.assistant.domain.DocumentMarkdown
import ai.daylight.assistant.domain.DocumentMarkdown.Run
import ai.daylight.assistant.domain.SlideDeck
import ai.daylight.assistant.domain.SlideDeck.Line
import ai.daylight.assistant.domain.SlideDeck.Marker
import ai.daylight.assistant.domain.SlideDeck.Style
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * v5.13.7: writes a real PowerPoint file (.pptx, 16:9) from Markdown, laid out by [SlideDeck].
 *
 * One theme, one master and two layouts: a dark cover (the first slide when it is just a
 * title and a line or two) and a white content slide with the title, a short yellow rule,
 * the body and a slide number. Bullets, numbered lists with one sub-level, bold, italic,
 * links, quotes, code and tables (dark header row, banded rows, numbers right-aligned) are
 * real PowerPoint formatting, so the deck stays editable in PowerPoint, Keynote and Slides.
 */
object PresentationGenerator {

    private const val A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val P_NS = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS = "xmlns:a=\"$A_NS\" xmlns:r=\"$R_NS\" xmlns:p=\"$P_NS\""
    private const val HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private const val INK = "1A1A1A"
    private const val MUTED = "5C5C5C"
    private const val FAINT = "9A9A9A"
    private const val ACCENT = "FFC700"
    private const val BULLET = "B88A00"
    private const val COVER_BG = "111111"
    private const val COVER_TEXT = "FFFFFF"
    private const val COVER_SUB = "BDBDBD"
    private const val HEAD_BG = "1A1A1A"
    private const val HEAD_TEXT = "FFFFFF"
    private const val BAND = "F4F4F4"
    private const val GRID = "D9D9D9"
    private const val LINK = "0B57D0"
    private const val CODE = "333333"
    private const val FONT = "Arial"
    private const val MONO = "Courier New"

    private const val SLIDE_CX = 12_192_000L
    private const val SLIDE_CY = 6_858_000L

    fun presentation(markdown: String, title: String = "Presentation"): ByteArray {
        val slides = SlideDeck.plan(markdown)
        val entries = linkedMapOf(
            "[Content_Types].xml" to contentTypes(slides.size),
            "_rels/.rels" to rootRels(),
            "docProps/core.xml" to core(title),
            "docProps/app.xml" to app(slides.size),
            "ppt/presentation.xml" to presentationXml(slides.size),
            "ppt/_rels/presentation.xml.rels" to presentationRels(slides.size),
            "ppt/presProps.xml" to "$HEADER<p:presentationPr $NS/>",
            "ppt/viewProps.xml" to "$HEADER<p:viewPr $NS><p:normalViewPr><p:restoredLeft sz=\"15620\"/><p:restoredTop sz=\"94660\"/></p:normalViewPr><p:gridSpacing cx=\"76200\" cy=\"76200\"/></p:viewPr>",
            "ppt/tableStyles.xml" to "$HEADER<a:tblStyleLst xmlns:a=\"$A_NS\" def=\"{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}\"/>",
            "ppt/theme/theme1.xml" to THEME,
            "ppt/slideMasters/slideMaster1.xml" to master(),
            "ppt/slideMasters/_rels/slideMaster1.xml.rels" to rels(
                "rId1" to ("$REL/slideLayout" to "../slideLayouts/slideLayout1.xml"),
                "rId2" to ("$REL/slideLayout" to "../slideLayouts/slideLayout2.xml"),
                "rId3" to ("$REL/theme" to "../theme/theme1.xml")
            ),
            "ppt/slideLayouts/slideLayout1.xml" to coverLayout(),
            "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to rels("rId1" to ("$REL/slideMaster" to "../slideMasters/slideMaster1.xml")),
            "ppt/slideLayouts/slideLayout2.xml" to contentLayout(),
            "ppt/slideLayouts/_rels/slideLayout2.xml.rels" to rels("rId1" to ("$REL/slideMaster" to "../slideMasters/slideMaster1.xml"))
        )
        slides.forEachIndexed { index, slide ->
            val links = Links()
            entries["ppt/slides/slide${index + 1}.xml"] = slideXml(slide, index + 1, links)
            entries["ppt/slides/_rels/slide${index + 1}.xml.rels"] = slideRels(slide.cover, links)
        }
        return OfficeFileGenerator.zip(entries)
    }

    // ── Slides ────────────────────────────────────────────────────────────────

    /** Hyperlink relationships of one slide; rId1 is its layout. */
    private class Links {
        val ids = linkedMapOf<String, String>()
        fun id(url: String): String = ids.getOrPut(url) { "rId${ids.size + 2}" }
    }

    private class Ids(var next: Int = 2) {
        fun take(): Int = next++
    }

    private fun slideXml(slide: SlideDeck.Slide, number: Int, links: Links): String = buildString {
        append(HEADER).append("<p:sld $NS><p:cSld>")
        if (slide.cover) append(background(COVER_BG))
        append("<p:spTree>").append(GROUP)
        val ids = Ids()
        if (slide.cover) cover(slide, ids, links) else content(slide, number, ids, links)
        append("</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>")
    }

    private fun StringBuilder.cover(slide: SlideDeck.Slide, ids: Ids, links: Links) {
        val titleText = DocumentMarkdown.plain(slide.title)
        val titleSize = slide.titleSize.toDouble()
        val titleH = SlideDeck.wrappedLines(titleText, titleSize, SlideDeck.CONTENT_W) * titleSize * 1.15 + 8
        val subSize = if (slide.subtitle.sumOf { DocumentMarkdown.plain(it).length } > 160) 18.0 else 20.0
        val subH = if (slide.subtitle.isEmpty()) 0.0 else
            slide.subtitle.sumOf { SlideDeck.wrappedLines(DocumentMarkdown.plain(it), subSize, SlideDeck.CONTENT_W) } * subSize * 1.3 + 8
        val block = 5 + 20 + titleH + (if (subH > 0) 18 + subH else 0.0)
        val top = max(64.0, (SlideDeck.SLIDE_H - block) / 2 - 12)
        rect(ids.take(), "Accent", SlideDeck.MARGIN_X, top, 64.0, 5.0, ACCENT)
        val titleY = top + 25
        val title = paragraph("<a:pPr><a:lnSpc><a:spcPct val=\"95000\"/></a:lnSpc><a:buNone/></a:pPr>",
            runs(slide.title, titleSize, COVER_TEXT, bold = true, links = links), titleSize)
        textShape(ids.take(), "Title", "<p:ph type=\"ctrTitle\"/>", SlideDeck.MARGIN_X, titleY, SlideDeck.CONTENT_W, titleH, "t", title)
        if (slide.subtitle.isNotEmpty()) {
            val paragraphs = slide.subtitle.joinToString("") { line ->
                paragraph("<a:pPr><a:spcBef><a:spcPts val=\"600\"/></a:spcBef><a:buNone/></a:pPr>",
                    runs(line, subSize, COVER_SUB, links = links), subSize)
            }
            textShape(ids.take(), "Subtitle", "<p:ph type=\"subTitle\" idx=\"1\"/>", SlideDeck.MARGIN_X, titleY + titleH + 18,
                SlideDeck.CONTENT_W, subH, "t", paragraphs)
        }
    }

    private fun StringBuilder.content(slide: SlideDeck.Slide, number: Int, ids: Ids, links: Links) {
        if (slide.title.isNotEmpty()) {
            val size = slide.titleSize.toDouble()
            val title = paragraph("<a:pPr><a:lnSpc><a:spcPct val=\"95000\"/></a:lnSpc><a:buNone/></a:pPr>",
                runs(slide.title, size, INK, bold = true, links = links), size)
            textShape(ids.take(), "Title", "<p:ph type=\"title\"/>", SlideDeck.MARGIN_X, SlideDeck.TITLE_Y,
                SlideDeck.CONTENT_W, SlideDeck.TITLE_H, "b", title)
            rect(ids.take(), "Accent", SlideDeck.MARGIN_X, SlideDeck.RULE_Y, 56.0, 4.0, ACCENT)
        }
        val table = slide.table
        if (slide.lines.isNotEmpty()) {
            val height = if (table != null) slide.bodyHeight + 4 else SlideDeck.BODY_H
            val body = slide.lines.withIndex().joinToString("") { (index, line) ->
                lineXml(line, slide.lines.getOrNull(index - 1), numberStart(slide.lines, index), slide.bodySize, links)
            }
            textShape(ids.take(), "Content", "<p:ph idx=\"1\"/>", SlideDeck.MARGIN_X, SlideDeck.BODY_Y, SlideDeck.CONTENT_W, height, "t", body)
        }
        if (table != null) {
            val y = if (slide.lines.isEmpty()) SlideDeck.BODY_Y else SlideDeck.BODY_Y + slide.bodyHeight + SlideDeck.GAP
            tableFrame(ids.take(), table, y, links)
        }
        val numberText = "<a:p><a:pPr algn=\"r\"><a:buNone/></a:pPr><a:fld id=\"{B6F15528-21DE-4FAA-801E-634DDDAF4B2B}\" type=\"slidenum\">" +
            "<a:rPr lang=\"en-US\" sz=\"1100\" dirty=\"0\">${fill(FAINT)}<a:latin typeface=\"$FONT\"/></a:rPr><a:t>$number</a:t></a:fld></a:p>"
        textShape(ids.take(), "Slide Number", null, SlideDeck.SLIDE_W - SlideDeck.MARGIN_X - 120, SlideDeck.FOOTER_Y, 120.0, 20.0, "b", numberText)
    }

    /** An ordered list item restarts PowerPoint's numbering at the first item of its run. */
    private fun numberStart(lines: List<Line>, index: Int): Int {
        var start = index
        val line = lines[index]
        while (start > 0 && lines[start - 1].marker == Marker.NUMBER && lines[start - 1].level == line.level) start--
        // A sub-item between two numbered items does not break the outer run.
        if (line.level == 0) {
            var cursor = index
            while (cursor > 0) {
                val previous = lines[cursor - 1]
                when {
                    previous.level > 0 -> cursor--
                    previous.marker == Marker.NUMBER -> { cursor--; start = cursor }
                    else -> break
                }
            }
        }
        return lines[start].number
    }

    private fun lineXml(line: Line, previous: Line?, numberStart: Int, bodySize: Int, links: Links): String {
        val size = SlideDeck.textSize(line, bodySize)
        val indent = SlideDeck.indentOf(line)
        val spacing = (SlideDeck.spaceBefore(line, previous, bodySize) * 100).roundToInt()
        val pPr = buildString {
            append("<a:pPr")
            when (line.marker) {
                Marker.NONE -> append(" marL=\"${emu(indent)}\" indent=\"0\"")
                else -> append(" marL=\"${emu(indent)}\" indent=\"-${emu(if (line.level == 0) 27.0 else 22.0)}\"")
            }
            if (line.level > 0) append(" lvl=\"1\"")
            append("><a:spcBef><a:spcPts val=\"$spacing\"/></a:spcBef>")
            when (line.marker) {
                Marker.BULLET -> append(
                    "<a:buClr><a:srgbClr val=\"$BULLET\"/></a:buClr><a:buSzPct val=\"100000\"/><a:buFont typeface=\"$FONT\"/>" +
                        "<a:buChar char=\"${if (line.level == 0) "•" else "–"}\"/>"
                )
                Marker.NUMBER -> append(
                    "<a:buClr><a:srgbClr val=\"$BULLET\"/></a:buClr><a:buSzPct val=\"100000\"/><a:buFont typeface=\"$FONT\"/>" +
                        "<a:buAutoNum type=\"arabicPeriod\"${if (numberStart != 1) " startAt=\"${numberStart.coerceIn(1, 32767)}\"" else ""}/>"
                )
                Marker.NONE -> append("<a:buNone/>")
            }
            append("</a:pPr>")
        }
        val color = when (line.style) {
            Style.QUOTE -> MUTED
            Style.CODE -> CODE
            else -> INK
        }
        val content = runs(
            line.runs, size, color,
            bold = line.style == Style.SUBHEAD,
            italic = line.style == Style.QUOTE,
            font = if (line.style == Style.CODE) MONO else FONT,
            links = links
        )
        return paragraph(pPr, content, size)
    }

    private fun paragraph(pPr: String, runs: String, size: Double): String =
        "<a:p>$pPr$runs<a:endParaRPr lang=\"en-US\" sz=\"${hundredths(size)}\" dirty=\"0\"/></a:p>"

    private fun runs(
        runs: List<Run>,
        size: Double,
        color: String,
        bold: Boolean = false,
        italic: Boolean = false,
        font: String = FONT,
        links: Links
    ): String = buildString {
        val sz = hundredths(size)
        runs.forEach { run ->
            run.text.split('\n').forEachIndexed { index, segment ->
                if (index > 0) append("<a:br><a:rPr lang=\"en-US\" sz=\"$sz\" dirty=\"0\"/></a:br>")
                if (segment.isEmpty()) return@forEachIndexed
                append("<a:r><a:rPr lang=\"en-US\" sz=\"$sz\"")
                if (bold || run.bold) append(" b=\"1\"")
                if (italic || run.italic) append(" i=\"1\"")
                if (run.link != null) append(" u=\"sng\"")
                if (run.strike) append(" strike=\"sngStrike\"")
                append(" dirty=\"0\">")
                append(fill(if (run.link != null) LINK else color))
                append("<a:latin typeface=\"${if (run.code) MONO else font}\"/>")
                run.link?.let { url -> append("<a:hlinkClick r:id=\"${links.id(url)}\"/>") }
                append("</a:rPr><a:t>").append(xml(segment)).append("</a:t></a:r>")
            }
        }
    }

    private fun StringBuilder.tableFrame(id: Int, table: SlideDeck.TablePlan, y: Double, links: Links) {
        val widths = table.columnWidths.map(::emu).toMutableList()
        val total = emu(SlideDeck.CONTENT_W)
        widths[widths.lastIndex] = total - widths.dropLast(1).sum()
        val heights = table.rowHeights.map(::emu)
        append("<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"$id\" name=\"Table $id\"/>")
        append("<p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>")
        append("<p:xfrm><a:off x=\"${emu(SlideDeck.MARGIN_X)}\" y=\"${emu(y)}\"/><a:ext cx=\"$total\" cy=\"${heights.sum()}\"/></p:xfrm>")
        append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\"><a:tbl>")
        append("<a:tblPr firstRow=\"1\" bandRow=\"1\"/><a:tblGrid>")
        widths.forEach { append("<a:gridCol w=\"$it\"/>") }
        append("</a:tblGrid>")
        val size = table.size.toDouble()
        val rows = listOf(table.header) + table.rows
        rows.forEachIndexed { rowIndex, cells ->
            val header = rowIndex == 0
            append("<a:tr h=\"${heights[rowIndex]}\">")
            for (column in widths.indices) {
                val runs = cells.getOrElse(column) { emptyList() }
                val align = if (table.numeric[column]) "r" else "l"
                val color = if (header) HEAD_TEXT else INK
                append("<a:tc><a:txBody><a:bodyPr/><a:lstStyle/>")
                append(paragraph("<a:pPr algn=\"$align\"><a:buNone/></a:pPr>", runs(runs, size, color, bold = header, links = links), size))
                append("</a:txBody><a:tcPr marL=\"91440\" marR=\"91440\" marT=\"45720\" marB=\"45720\" anchor=\"ctr\">")
                listOf("lnL", "lnR", "lnT", "lnB").forEach { side ->
                    append("<a:$side w=\"6350\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\">${fill(GRID)}<a:prstDash val=\"solid\"/></a:$side>")
                }
                append(fill(if (header) HEAD_BG else if (rowIndex % 2 == 0) BAND else "FFFFFF"))
                append("</a:tcPr></a:tc>")
            }
            append("</a:tr>")
        }
        append("</a:tbl></a:graphicData></a:graphic></p:graphicFrame>")
    }

    private fun StringBuilder.textShape(
        id: Int,
        name: String,
        placeholder: String?,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        anchor: String,
        paragraphs: String
    ) {
        append("<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name $id\"/>")
        append(if (placeholder != null) "<p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr>" else "<p:cNvSpPr txBox=\"1\"/>")
        append("<p:nvPr>${placeholder.orEmpty()}</p:nvPr></p:nvSpPr>")
        append("<p:spPr>").append(xfrm(x, y, width, height)).append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>")
        append("<p:txBody><a:bodyPr wrap=\"square\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" rtlCol=\"0\" anchor=\"$anchor\"><a:normAutofit/></a:bodyPr><a:lstStyle/>")
        append(paragraphs)
        append("</p:txBody></p:sp>")
    }

    private fun StringBuilder.rect(id: Int, name: String, x: Double, y: Double, width: Double, height: Double, color: String) {
        append("<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name $id\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>")
        append("<p:spPr>").append(xfrm(x, y, width, height))
        append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom>${fill(color)}<a:ln><a:noFill/></a:ln></p:spPr></p:sp>")
    }

    private fun xfrm(x: Double, y: Double, width: Double, height: Double) =
        "<a:xfrm><a:off x=\"${emu(x)}\" y=\"${emu(y)}\"/><a:ext cx=\"${emu(width)}\" cy=\"${emu(height)}\"/></a:xfrm>"

    private fun fill(color: String) = "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill>"

    private fun background(color: String) = "<p:bg><p:bgPr>${fill(color)}<a:effectLst/></p:bgPr></p:bg>"

    private fun emu(points: Double): Long = (points * 12_700).roundToLong()

    private fun hundredths(points: Double): Int = (points * 100).roundToInt().coerceIn(100, 400_000)

    private const val GROUP = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"

    private fun slideRels(cover: Boolean, links: Links): String = buildString {
        append(HEADER).append("<Relationships xmlns=\"$PKG_REL_NS\">")
        append("<Relationship Id=\"rId1\" Type=\"$REL/slideLayout\" Target=\"../slideLayouts/slideLayout${if (cover) 1 else 2}.xml\"/>")
        links.ids.forEach { (url, id) ->
            append("<Relationship Id=\"$id\" Type=\"$REL/hyperlink\" Target=\"${xml(url)}\" TargetMode=\"External\"/>")
        }
        append("</Relationships>")
    }

    // ── Package parts ─────────────────────────────────────────────────────────

    private fun rels(vararg entries: Pair<String, Pair<String, String>>): String = buildString {
        append(HEADER).append("<Relationships xmlns=\"$PKG_REL_NS\">")
        entries.forEach { (id, target) -> append("<Relationship Id=\"$id\" Type=\"${target.first}\" Target=\"${target.second}\"/>") }
        append("</Relationships>")
    }

    private fun contentTypes(slides: Int): String = buildString {
        val pml = "application/vnd.openxmlformats-officedocument.presentationml"
        append(HEADER).append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"$pml.presentation.main+xml\"/>")
        append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"$pml.slideMaster+xml\"/>")
        append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"$pml.slideLayout+xml\"/>")
        append("<Override PartName=\"/ppt/slideLayouts/slideLayout2.xml\" ContentType=\"$pml.slideLayout+xml\"/>")
        for (index in 1..slides) append("<Override PartName=\"/ppt/slides/slide$index.xml\" ContentType=\"$pml.slide+xml\"/>")
        append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
        append("<Override PartName=\"/ppt/presProps.xml\" ContentType=\"$pml.presProps+xml\"/>")
        append("<Override PartName=\"/ppt/viewProps.xml\" ContentType=\"$pml.viewProps+xml\"/>")
        append("<Override PartName=\"/ppt/tableStyles.xml\" ContentType=\"$pml.tableStyles+xml\"/>")
        append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
        append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
        append("</Types>")
    }

    private fun rootRels(): String = rels(
        "rId1" to ("$REL/officeDocument" to "ppt/presentation.xml"),
        "rId2" to ("http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" to "docProps/core.xml"),
        "rId3" to ("$REL/extended-properties" to "docProps/app.xml")
    )

    private fun core(title: String): String = HEADER +
        "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
        "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
        "xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
        "<dc:title>${xml(title.take(200))}</dc:title><dc:creator>Kryzz AI</dc:creator></cp:coreProperties>"

    private fun app(slides: Int): String = HEADER +
        "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" " +
        "xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">" +
        "<Application>Kryzz AI</Application><Slides>$slides</Slides></Properties>"

    private fun presentationXml(slides: Int): String = buildString {
        append(HEADER).append("<p:presentation $NS saveSubsetFonts=\"1\">")
        append("<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>")
        for (index in 0 until slides) append("<p:sldId id=\"${256 + index}\" r:id=\"rId${index + 2}\"/>")
        append("</p:sldIdLst><p:sldSz cx=\"$SLIDE_CX\" cy=\"$SLIDE_CY\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>")
    }

    private fun presentationRels(slides: Int): String = buildString {
        append(HEADER).append("<Relationships xmlns=\"$PKG_REL_NS\">")
        append("<Relationship Id=\"rId1\" Type=\"$REL/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
        for (index in 1..slides) append("<Relationship Id=\"rId${index + 1}\" Type=\"$REL/slide\" Target=\"slides/slide$index.xml\"/>")
        val next = slides + 2
        append("<Relationship Id=\"rId$next\" Type=\"$REL/presProps\" Target=\"presProps.xml\"/>")
        append("<Relationship Id=\"rId${next + 1}\" Type=\"$REL/viewProps\" Target=\"viewProps.xml\"/>")
        append("<Relationship Id=\"rId${next + 2}\" Type=\"$REL/theme\" Target=\"theme/theme1.xml\"/>")
        append("<Relationship Id=\"rId${next + 3}\" Type=\"$REL/tableStyles\" Target=\"tableStyles.xml\"/>")
        append("</Relationships>")
    }

    private fun placeholderShape(id: Int, name: String, placeholder: String, x: Double, y: Double, w: Double, h: Double, anchor: String, prompt: String) =
        "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr>$placeholder</p:nvPr></p:nvSpPr>" +
            "<p:spPr>${xfrm(x, y, w, h)}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr>" +
            "<p:txBody><a:bodyPr vert=\"horz\" wrap=\"square\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" rtlCol=\"0\" anchor=\"$anchor\"><a:normAutofit/></a:bodyPr>" +
            "<a:lstStyle/><a:p><a:r><a:rPr lang=\"en-US\"/><a:t>$prompt</a:t></a:r><a:endParaRPr lang=\"en-US\"/></a:p></p:txBody></p:sp>"

    private fun master(): String = HEADER + "<p:sldMaster $NS><p:cSld>" + background("FFFFFF") + "<p:spTree>" + GROUP +
        placeholderShape(2, "Title Placeholder 1", "<p:ph type=\"title\"/>", SlideDeck.MARGIN_X, SlideDeck.TITLE_Y, SlideDeck.CONTENT_W, SlideDeck.TITLE_H, "b", "Click to edit the title") +
        placeholderShape(3, "Text Placeholder 2", "<p:ph type=\"body\" idx=\"1\"/>", SlideDeck.MARGIN_X, SlideDeck.BODY_Y, SlideDeck.CONTENT_W, SlideDeck.BODY_H, "t", "Click to edit the text") +
        "</p:spTree></p:cSld>" +
        "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" " +
        "accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
        "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/><p:sldLayoutId id=\"2147483650\" r:id=\"rId2\"/></p:sldLayoutIdLst>" +
        "<p:txStyles>" +
        "<p:titleStyle><a:lvl1pPr algn=\"l\" rtl=\"0\"><a:lnSpc><a:spcPct val=\"95000\"/></a:lnSpc><a:spcBef><a:spcPct val=\"0\"/></a:spcBef><a:buNone/>" +
        "<a:defRPr sz=\"3200\" b=\"1\" kern=\"1200\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mj-lt\"/><a:ea typeface=\"+mj-ea\"/><a:cs typeface=\"+mj-cs\"/></a:defRPr></a:lvl1pPr></p:titleStyle>" +
        "<p:bodyStyle>" +
        "<a:lvl1pPr marL=\"342900\" indent=\"-342900\" algn=\"l\" rtl=\"0\"><a:spcBef><a:spcPts val=\"1000\"/></a:spcBef><a:buFont typeface=\"$FONT\"/><a:buChar char=\"•\"/>" +
        "<a:defRPr sz=\"2400\" kern=\"1200\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mn-lt\"/><a:ea typeface=\"+mn-ea\"/><a:cs typeface=\"+mn-cs\"/></a:defRPr></a:lvl1pPr>" +
        "<a:lvl2pPr marL=\"685800\" indent=\"-279400\" algn=\"l\" rtl=\"0\"><a:spcBef><a:spcPts val=\"500\"/></a:spcBef><a:buFont typeface=\"$FONT\"/><a:buChar char=\"–\"/>" +
        "<a:defRPr sz=\"2000\" kern=\"1200\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mn-lt\"/><a:ea typeface=\"+mn-ea\"/><a:cs typeface=\"+mn-cs\"/></a:defRPr></a:lvl2pPr>" +
        "</p:bodyStyle>" +
        "<p:otherStyle><a:defPPr><a:defRPr lang=\"en-US\"/></a:defPPr><a:lvl1pPr marL=\"0\" algn=\"l\" rtl=\"0\"><a:defRPr sz=\"1800\" kern=\"1200\">" +
        "<a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mn-lt\"/><a:ea typeface=\"+mn-ea\"/><a:cs typeface=\"+mn-cs\"/></a:defRPr></a:lvl1pPr></p:otherStyle>" +
        "</p:txStyles></p:sldMaster>"

    private fun coverLayout(): String = HEADER + "<p:sldLayout $NS type=\"title\" preserve=\"1\"><p:cSld name=\"Title Slide\">" +
        background(COVER_BG) + "<p:spTree>" + GROUP +
        placeholderShape(2, "Title 1", "<p:ph type=\"ctrTitle\"/>", SlideDeck.MARGIN_X, 190.0, SlideDeck.CONTENT_W, 120.0, "b", "Click to add the title") +
        placeholderShape(3, "Subtitle 2", "<p:ph type=\"subTitle\" idx=\"1\"/>", SlideDeck.MARGIN_X, 328.0, SlideDeck.CONTENT_W, 80.0, "t", "Click to add the subtitle") +
        "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"

    private fun contentLayout(): String = HEADER + "<p:sldLayout $NS type=\"obj\" preserve=\"1\"><p:cSld name=\"Title and Content\"><p:spTree>" + GROUP +
        placeholderShape(2, "Title 1", "<p:ph type=\"title\"/>", SlideDeck.MARGIN_X, SlideDeck.TITLE_Y, SlideDeck.CONTENT_W, SlideDeck.TITLE_H, "b", "Click to add the title") +
        placeholderShape(3, "Content Placeholder 2", "<p:ph idx=\"1\"/>", SlideDeck.MARGIN_X, SlideDeck.BODY_Y, SlideDeck.CONTENT_W, SlideDeck.BODY_H, "t", "Click to add text") +
        "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"

    private val THEME = HEADER + "<a:theme xmlns:a=\"$A_NS\" name=\"Kryzz\"><a:themeElements>" +
        "<a:clrScheme name=\"Kryzz\">" +
        "<a:dk1><a:srgbClr val=\"$INK\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1>" +
        "<a:dk2><a:srgbClr val=\"111111\"/></a:dk2><a:lt2><a:srgbClr val=\"F2F2F2\"/></a:lt2>" +
        "<a:accent1><a:srgbClr val=\"$ACCENT\"/></a:accent1><a:accent2><a:srgbClr val=\"1A1A1A\"/></a:accent2>" +
        "<a:accent3><a:srgbClr val=\"8A8A8A\"/></a:accent3><a:accent4><a:srgbClr val=\"D71921\"/></a:accent4>" +
        "<a:accent5><a:srgbClr val=\"0B57D0\"/></a:accent5><a:accent6><a:srgbClr val=\"1E8E3E\"/></a:accent6>" +
        "<a:hlink><a:srgbClr val=\"$LINK\"/></a:hlink><a:folHlink><a:srgbClr val=\"6B4FBB\"/></a:folHlink></a:clrScheme>" +
        "<a:fontScheme name=\"Kryzz\"><a:majorFont><a:latin typeface=\"$FONT\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>" +
        "<a:minorFont><a:latin typeface=\"$FONT\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>" +
        "<a:fmtScheme name=\"Kryzz\"><a:fillStyleLst>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"><a:tint val=\"50000\"/></a:schemeClr></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"><a:shade val=\"80000\"/></a:schemeClr></a:solidFill>" +
        "</a:fillStyleLst><a:lnStyleLst>" +
        "<a:ln w=\"6350\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>" +
        "<a:ln w=\"12700\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>" +
        "<a:ln w=\"19050\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>" +
        "</a:lnStyleLst><a:effectStyleLst>" +
        "<a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle>" +
        "</a:effectStyleLst><a:bgFillStyleLst>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"><a:tint val=\"95000\"/></a:schemeClr></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"><a:shade val=\"90000\"/></a:schemeClr></a:solidFill>" +
        "</a:bgFillStyleLst></a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>"
}
