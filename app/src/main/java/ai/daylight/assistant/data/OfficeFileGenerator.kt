package ai.daylight.assistant.data

import ai.daylight.assistant.domain.DocumentLayout
import ai.daylight.assistant.domain.DocumentMarkdown
import ai.daylight.assistant.domain.DocumentMarkdown.Run
import ai.daylight.assistant.domain.SpreadsheetContent
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OfficeFileGenerator {

    // ── DOCX ────────────────────────────────────────────────────────────────

    /**
     * v5.11: renders the Markdown block model (DocumentMarkdown) as real Word structure:
     * Heading 1-3 styles, bold / italic / monospace / struck runs, bulleted and numbered
     * lists (numbering.xml), bordered tables with a bold repeating header row, shaded code
     * blocks, quotes, rules and external hyperlinks. A4 page, 2 cm margins.
     */
    fun document(markdown: String): ByteArray {
        val writer = DocxWriter()
        val body = writer.body(DocumentMarkdown.parse(markdown))
        return zip(linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/_rels/document.xml.rels" to writer.relationships(),
            "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="$W_NS" xmlns:r="$R_NS"><w:body>$body<w:sectPr><w:pgSz w:w="$PAGE_W" w:h="$PAGE_H"/><w:pgMar w:top="$MARGIN" w:right="$MARGIN" w:bottom="$MARGIN" w:left="$MARGIN" w:header="709" w:footer="709" w:gutter="0"/></w:sectPr></w:body></w:document>""",
            "word/styles.xml" to DOCX_STYLES,
            "word/numbering.xml" to writer.numbering()
        ))
    }

    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val HYPERLINK_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
    private const val PAGE_W = 11906 // A4 in twips
    private const val PAGE_H = 16838
    private const val MARGIN = 1134 // 2 cm
    private const val TEXT_WIDTH = PAGE_W - 2 * MARGIN
    private const val BULLET_NUM_ID = 1

    /** Builds document.xml content; collects hyperlink relationships and list numbering on the way. */
    private class DocxWriter {
        private val links = linkedMapOf<String, String>() // url -> relationship id
        private val orderedNums = mutableListOf<Triple<Int, Int, Int>>() // numId, level, start

        fun body(blocks: List<DocumentMarkdown.Block>): String {
            val out = StringBuilder()
            blocks.forEach { block ->
                when (block) {
                    is DocumentMarkdown.Heading -> out.append(paragraph("<w:pStyle w:val=\"Heading${block.level}\"/>", runs(block.runs)))
                    is DocumentMarkdown.Paragraph -> out.append(paragraph("", runs(block.runs)))
                    is DocumentMarkdown.Quote -> block.paragraphs.forEach { out.append(paragraph("<w:pStyle w:val=\"Quote\"/>", runs(it))) }
                    is DocumentMarkdown.CodeBlock -> out.append(
                        paragraph("<w:pStyle w:val=\"CodeBlock\"/>", textRun(block.text.ifEmpty { " " }, "<w:rStyle w:val=\"CodeChar\"/>"))
                    )
                    DocumentMarkdown.Rule -> out.append(
                        paragraph("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"C9CED6\"/></w:pBdr>", "")
                    )
                    is DocumentMarkdown.ListBlock -> list(block, 0, out)
                    is DocumentMarkdown.Table -> {
                        out.append(table(block))
                        // Word merges tables that touch and wants a paragraph after a table.
                        out.append(paragraph("<w:spacing w:before=\"0\" w:after=\"60\"/>", ""))
                    }
                }
            }
            if (out.isEmpty()) out.append(paragraph("", ""))
            return out.toString()
        }

        private fun list(list: DocumentMarkdown.ListBlock, level: Int, out: StringBuilder) {
            val numId = if (list.ordered) {
                val id = BULLET_NUM_ID + 1 + orderedNums.size
                orderedNums += Triple(id, level, list.start)
                id
            } else BULLET_NUM_ID
            list.items.forEach { item ->
                out.append(
                    paragraph(
                        "<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"$numId\"/></w:numPr>",
                        runs(item.runs)
                    )
                )
                item.children?.let { list(it, 1, out) }
            }
        }

        private fun table(table: DocumentMarkdown.Table): String {
            val widths = DocumentLayout.columnWidths(table, TEXT_WIDTH.toFloat(), minWidth = 900f).map { it.toInt() }
            val border = "w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFC5CC\""
            return buildString {
                append("<w:tbl><w:tblPr><w:tblW w:w=\"${widths.sum()}\" w:type=\"dxa\"/>")
                append("<w:tblBorders><w:top $border/><w:left $border/><w:bottom $border/><w:right $border/><w:insideH $border/><w:insideV $border/></w:tblBorders>")
                append("<w:tblLayout w:type=\"fixed\"/>")
                append("<w:tblCellMar><w:top w:w=\"60\" w:type=\"dxa\"/><w:left w:w=\"100\" w:type=\"dxa\"/><w:bottom w:w=\"60\" w:type=\"dxa\"/><w:right w:w=\"100\" w:type=\"dxa\"/></w:tblCellMar>")
                append("</w:tblPr><w:tblGrid>")
                widths.forEach { append("<w:gridCol w:w=\"$it\"/>") }
                append("</w:tblGrid>")
                append(row(table.header, widths, header = true))
                table.rows.forEach { append(row(it, widths, header = false)) }
                append("</w:tbl>")
            }
        }

        private fun row(cells: List<List<Run>>, widths: List<Int>, header: Boolean): String = buildString {
            append("<w:tr>")
            if (header) append("<w:trPr><w:tblHeader/></w:trPr>")
            widths.forEachIndexed { column, width ->
                val cellRuns = cells.getOrElse(column) { emptyList() }.let { runs -> if (header) runs.map { it.copy(bold = true) } else runs }
                append("<w:tc><w:tcPr><w:tcW w:w=\"$width\" w:type=\"dxa\"/>")
                if (header) append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E3F1EC\"/>")
                append("</w:tcPr>")
                append(paragraph("<w:spacing w:before=\"0\" w:after=\"0\"/>", runs(cellRuns)))
                append("</w:tc>")
            }
            append("</w:tr>")
        }

        private fun paragraph(properties: String, content: String): String =
            if (properties.isEmpty()) "<w:p>$content</w:p>" else "<w:p><w:pPr>$properties</w:pPr>$content</w:p>"

        /** Runs, with consecutive runs of one link wrapped in a single w:hyperlink. */
        private fun runs(runs: List<Run>): String = buildString {
            var index = 0
            while (index < runs.size) {
                val link = runs[index].link
                if (link == null) {
                    append(run(runs[index]))
                    index++
                    continue
                }
                val id = links.getOrPut(link) { "rId${links.size + 3}" }
                append("<w:hyperlink r:id=\"$id\" w:history=\"1\">")
                while (index < runs.size && runs[index].link == link) {
                    append(run(runs[index]))
                    index++
                }
                append("</w:hyperlink>")
            }
        }

        private fun run(run: Run): String {
            // CT_RPr children must keep schema order: rStyle, rFonts, b, i, strike, ...
            val properties = buildString {
                when {
                    run.link != null -> append("<w:rStyle w:val=\"Hyperlink\"/>")
                    run.code -> append("<w:rStyle w:val=\"CodeChar\"/>")
                }
                if (run.link != null && run.code) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:cs=\"Consolas\"/>")
                if (run.bold) append("<w:b/><w:bCs/>")
                if (run.italic) append("<w:i/><w:iCs/>")
                if (run.strike) append("<w:strike/>")
            }
            return textRun(run.text, properties)
        }

        private fun textRun(text: String, properties: String): String = buildString {
            append("<w:r>")
            if (properties.isNotEmpty()) append("<w:rPr>$properties</w:rPr>")
            text.split('\n').forEachIndexed { index, line ->
                if (index > 0) append("<w:br/>")
                line.split('\t').forEachIndexed { tabIndex, part ->
                    if (tabIndex > 0) append("<w:tab/>")
                    if (part.isNotEmpty()) append("<w:t xml:space=\"preserve\">${xml(part)}</w:t>")
                }
            }
            append("</w:r>")
        }

        fun relationships(): String = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""")
            links.forEach { (url, id) ->
                append("""<Relationship Id="$id" Type="$HYPERLINK_TYPE" Target="${xml(url)}" TargetMode="External"/>""")
            }
            append("</Relationships>")
        }

        /** Abstract 0: bullets (• then ◦). Abstract 1: numbers (1. then a.). One num per ordered list so each restarts. */
        fun numbering(): String = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:numbering xmlns:w="$W_NS">""")
            append("""<w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="hybridMultilevel"/>""")
            append(level(0, "bullet", "•", 720))
            append(level(1, "bullet", "◦", 1440))
            append("</w:abstractNum>")
            append("""<w:abstractNum w:abstractNumId="1"><w:multiLevelType w:val="hybridMultilevel"/>""")
            append(level(0, "decimal", "%1.", 720))
            append(level(1, "lowerLetter", "%2.", 1440))
            append("</w:abstractNum>")
            append("""<w:num w:numId="$BULLET_NUM_ID"><w:abstractNumId w:val="0"/></w:num>""")
            orderedNums.forEach { (id, level, start) ->
                append("""<w:num w:numId="$id"><w:abstractNumId w:val="1"/><w:lvlOverride w:ilvl="$level"><w:startOverride w:val="${start.coerceAtLeast(0)}"/></w:lvlOverride></w:num>""")
            }
            append("</w:numbering>")
        }

        private fun level(ilvl: Int, format: String, text: String, left: Int): String =
            """<w:lvl w:ilvl="$ilvl"><w:start w:val="1"/><w:numFmt w:val="$format"/><w:lvlText w:val="$text"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="$left" w:hanging="360"/></w:pPr></w:lvl>"""
    }

    private val DOCX_STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="$W_NS">""" +
        """<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/><w:sz w:val="22"/><w:szCs w:val="22"/><w:lang w:val="en-US"/></w:rPr></w:rPrDefault>""" +
        """<w:pPrDefault><w:pPr><w:spacing w:after="140" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""" +
        """<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>""" +
        headingStyle(1, color = "087F5B", size = 32, before = 360, after = 120) +
        headingStyle(2, color = "1F2937", size = 26, before = 280, after = 100) +
        headingStyle(3, color = "374151", size = 23, before = 220, after = 80) +
        """<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:uiPriority w:val="34"/><w:qFormat/><w:pPr><w:spacing w:after="60"/><w:ind w:left="720"/><w:contextualSpacing/></w:pPr></w:style>""" +
        """<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="29"/><w:qFormat/><w:pPr><w:pBdr><w:left w:val="single" w:sz="18" w:space="8" w:color="087F5B"/></w:pBdr><w:ind w:left="284"/></w:pPr><w:rPr><w:i/><w:iCs/><w:color w:val="4B5563"/></w:rPr></w:style>""" +
        """<w:style w:type="paragraph" w:styleId="CodeBlock"><w:name w:val="Code Block"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:pBdr><w:top w:val="single" w:sz="4" w:space="4" w:color="E5E7EB"/><w:left w:val="single" w:sz="4" w:space="4" w:color="E5E7EB"/><w:bottom w:val="single" w:sz="4" w:space="4" w:color="E5E7EB"/><w:right w:val="single" w:sz="4" w:space="4" w:color="E5E7EB"/></w:pBdr><w:shd w:val="clear" w:color="auto" w:fill="F3F4F6"/><w:spacing w:before="120" w:after="200" w:line="240" w:lineRule="auto"/><w:ind w:left="113" w:right="113"/></w:pPr><w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/><w:sz w:val="19"/><w:szCs w:val="19"/></w:rPr></w:style>""" +
        """<w:style w:type="character" w:default="1" w:styleId="DefaultParagraphFont"><w:name w:val="Default Paragraph Font"/><w:uiPriority w:val="1"/><w:semiHidden/></w:style>""" +
        """<w:style w:type="character" w:styleId="CodeChar"><w:name w:val="Code Char"/><w:basedOn w:val="DefaultParagraphFont"/><w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/><w:color w:val="1F2937"/><w:sz w:val="20"/><w:szCs w:val="20"/><w:shd w:val="clear" w:color="auto" w:fill="F3F4F6"/></w:rPr></w:style>""" +
        """<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/><w:basedOn w:val="DefaultParagraphFont"/><w:uiPriority w:val="99"/><w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr></w:style>""" +
        "</w:styles>"

    private fun headingStyle(level: Int, color: String, size: Int, before: Int, after: Int): String =
        """<w:style w:type="paragraph" w:styleId="Heading$level"><w:name w:val="heading $level"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="9"/><w:qFormat/>""" +
            """<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before="$before" w:after="$after"/><w:outlineLvl w:val="${level - 1}"/></w:pPr>""" +
            """<w:rPr><w:b/><w:bCs/><w:color w:val="$color"/><w:sz w:val="$size"/><w:szCs w:val="$size"/></w:rPr></w:style>"""

    // ── XLSX ────────────────────────────────────────────────────────────────

    /**
     * v5.11: typed cells (numbers, percentages, booleans, formulas; leading-zero values stay
     * text), approximate column widths, a frozen header row and an autofilter per sheet, and
     * several sheets via `### Sheet: Name` lines (see SpreadsheetContent). Formulas are
     * recalculated when the workbook opens.
     */
    fun spreadsheet(csv: String): ByteArray {
        val sheets = SpreadsheetContent.sheets(csv)
        val entries = linkedMapOf<String, String>()
        entries["[Content_Types].xml"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            sheets.indices.forEach { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
            append("</Types>")
        }
        entries["_rels/.rels"] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        entries["xl/workbook.xml"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$R_NS"><bookViews><workbookView activeTab="0"/></bookViews><sheets>""")
            sheets.forEachIndexed { index, sheet -> append("""<sheet name="${xml(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""") }
            append("</sheets>")
            val filters = sheets.mapIndexedNotNull { index, sheet -> filterRange(sheet)?.let { index to "'${sheet.name.replace("'", "''")}'!${absolute(it)}" } }
            if (filters.isNotEmpty()) {
                append("<definedNames>")
                filters.forEach { (index, ref) -> append("""<definedName name="_xlnm._FilterDatabase" localSheetId="$index" hidden="1">${xml(ref)}</definedName>""") }
                append("</definedNames>")
            }
            append("""<calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""")
        }
        entries["xl/_rels/workbook.xml.rels"] = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            sheets.indices.forEach { append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
            append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
        }
        sheets.forEachIndexed { index, sheet -> entries["xl/worksheets/sheet${index + 1}.xml"] = worksheet(sheet, selected = index == 0) }
        entries["xl/styles.xml"] = XLSX_STYLES
        return zip(entries)
    }

    // cellXfs indexes in XLSX_STYLES.
    private const val XF_HEADER = 1
    private const val XF_THOUSANDS = 2
    private const val XF_THOUSANDS_DECIMAL = 3
    private const val XF_PERCENT = 4
    private const val XF_PERCENT_DECIMAL = 5
    private const val XF_WRAP = 6

    private fun worksheet(sheet: SpreadsheetContent.Sheet, selected: Boolean): String {
        val rows = sheet.rows
        val columnCount = rows.maxOf { it.size }.coerceAtLeast(1)
        val lastRef = "${columnName(columnCount - 1)}${rows.size}"
        val frozen = rows.size > 1
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<dimension ref="A1:$lastRef"/>""")
            append("""<sheetViews><sheetView workbookViewId="0"${if (selected) " tabSelected=\"1\"" else ""}>""")
            if (frozen) append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/><selection pane="bottomLeft" activeCell="A2" sqref="A2"/>""")
            append("</sheetView></sheetViews>")
            append("""<sheetFormatPr defaultRowHeight="15"/>""")
            append("<cols>")
            SpreadsheetContent.columnWidths(rows).forEachIndexed { index, width ->
                append("""<col min="${index + 1}" max="${index + 1}" width="${"%.1f".format(java.util.Locale.US, width)}" customWidth="1"/>""")
            }
            append("</cols><sheetData>")
            rows.forEachIndexed { rowIndex, cells ->
                append("<row r=\"${rowIndex + 1}\">")
                cells.forEachIndexed { columnIndex, value ->
                    append(cell("${columnName(columnIndex)}${rowIndex + 1}", value, header = rowIndex == 0))
                }
                append("</row>")
            }
            append("</sheetData>")
            filterRange(sheet)?.let { append("""<autoFilter ref="$it"/>""") }
            append("</worksheet>")
        }
    }

    /** Autofilter over the header and data rows, or null for a sheet without data rows. */
    private fun filterRange(sheet: SpreadsheetContent.Sheet): String? {
        if (sheet.rows.size < 2) return null
        val columnCount = sheet.rows.maxOf { it.size }.coerceAtLeast(1)
        return "A1:${columnName(columnCount - 1)}${sheet.rows.size}"
    }

    private fun absolute(range: String): String =
        range.split(':').joinToString(":") { ref -> ref.replace(Regex("^([A-Z]+)(\\d+)$"), "\\$$1\\$$2") }

    private fun cell(reference: String, value: String, header: Boolean): String {
        if (header) {
            return if (value.isEmpty()) "<c r=\"$reference\" s=\"$XF_HEADER\"/>"
            else "<c r=\"$reference\" t=\"inlineStr\" s=\"$XF_HEADER\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"
        }
        return when (val typed = SpreadsheetContent.classify(value)) {
            SpreadsheetContent.Cell.Empty -> ""
            is SpreadsheetContent.Cell.Formula -> "<c r=\"$reference\"><f>${xml(typed.expression)}</f></c>"
            is SpreadsheetContent.Cell.Bool -> "<c r=\"$reference\" t=\"b\"><v>${if (typed.value) 1 else 0}</v></c>"
            is SpreadsheetContent.Cell.Number -> {
                val style = when (typed.format) {
                    SpreadsheetContent.NumberFormat.GENERAL -> null
                    SpreadsheetContent.NumberFormat.THOUSANDS -> XF_THOUSANDS
                    SpreadsheetContent.NumberFormat.THOUSANDS_DECIMAL -> XF_THOUSANDS_DECIMAL
                    SpreadsheetContent.NumberFormat.PERCENT -> XF_PERCENT
                    SpreadsheetContent.NumberFormat.PERCENT_DECIMAL -> XF_PERCENT_DECIMAL
                }
                "<c r=\"$reference\"${style?.let { " s=\"$it\"" }.orEmpty()}><v>${typed.value}</v></c>"
            }
            is SpreadsheetContent.Cell.Text -> {
                val style = if ('\n' in typed.value) " s=\"$XF_WRAP\"" else ""
                "<c r=\"$reference\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${xml(typed.value)}</t></is></c>"
            }
        }
    }

    private val XLSX_STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font></fonts>""" +
        """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF087F5B"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="7">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
        """<xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="9" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>""" +
        """</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    // ── PDF (fallback writer) ───────────────────────────────────────────────

    /**
     * Minimal, valid single- or multi-page PDF built from the Markdown block model: bold
     * headings, bullets and numbers with hanging indents, table rows as `a | b | c`, and
     * code in Courier. Uses the standard-14 fonts so no font embedding is needed;
     * characters outside WinAnsi (e.g. CJK) are replaced with '?'.
     *
     * v5.11: this is the fallback. GeneratedOutputStore first renders PDFs with Android's
     * PdfDocument (PdfDocumentRenderer), which uses system fonts and keeps every character.
     */
    fun pdf(markdown: String): ByteArray {
        val lines = layoutPdfLines(markdown)
        // Paginate: Letter page, 56pt margins, top baseline at 736.
        val pages = mutableListOf<List<PdfLine>>()
        var current = mutableListOf<PdfLine>()
        var y = PDF_TOP
        lines.forEach { line ->
            val leading = line.leading
            if (y - leading < PDF_BOTTOM && current.isNotEmpty()) {
                pages += current
                current = mutableListOf()
                y = PDF_TOP
            }
            y -= leading
            current += line.copy(y = y)
        }
        if (current.isNotEmpty() || pages.isEmpty()) pages += current

        val encoding = Charsets.ISO_8859_1
        val out = ByteArrayOutputStream()
        val header = "%PDF-1.4\n"
        out.write(header.toByteArray(encoding))
        val offsets = mutableListOf<Int>()
        fun writeObject(number: Int, body: String) {
            check(offsets.size == number - 1) { "PDF objects must be written in order." }
            offsets += out.size()
            out.write("$number 0 obj\n$body\nendobj\n".toByteArray(encoding))
        }
        val firstPage = 6
        val pageObjectNumbers = pages.indices.map { firstPage + it * 2 }
        writeObject(1, "<< /Type /Catalog /Pages 2 0 R >>")
        writeObject(2, "<< /Type /Pages /Kids [${pageObjectNumbers.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>")
        writeObject(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        writeObject(4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        writeObject(5, "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
        pages.forEachIndexed { index, pageLines ->
            val pageNumber = firstPage + index * 2
            val stream = buildString {
                pageLines.forEach { line ->
                    if (line.text.isNotEmpty()) {
                        append("BT /F${line.font} ${line.size} Tf ${line.x} ${line.y} Td (${escapePdfText(line.text)}) Tj ET\n")
                    }
                }
                if (pages.size > 1) append("BT /F1 9 Tf 290 30 Td (${index + 1} / ${pages.size}) Tj ET\n")
            }
            writeObject(pageNumber, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> /Contents ${pageNumber + 1} 0 R >>")
            val streamBytes = stream.toByteArray(encoding)
            writeObject(pageNumber + 1, "<< /Length ${streamBytes.size} >>\nstream\n$stream\nendstream")
        }
        val xrefStart = out.size()
        val xref = buildString {
            append("xref\n0 ${offsets.size + 1}\n")
            append("0000000000 65535 f \n")
            offsets.forEach { append(String.format("%010d 00000 n \n", it)) }
            append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        }
        out.write(xref.toByteArray(encoding))
        return out.toByteArray()
    }

    /** [font] 1 = Helvetica, 2 = Helvetica-Bold, 3 = Courier. */
    private data class PdfLine(val text: String, val font: Int, val size: Int, val x: Int = PDF_LEFT, val y: Int = 0) {
        val leading: Int get() = if (text.isEmpty()) 8 else (size * 1.35f).toInt() + 3
    }

    private const val PDF_TOP = 736
    private const val PDF_BOTTOM = 56
    private const val PDF_LEFT = 56
    private const val PDF_WIDTH = 500

    private fun layoutPdfLines(markdown: String): List<PdfLine> {
        val lines = mutableListOf<PdfLine>()
        fun text(value: String, font: Int, size: Int, x: Int = PDF_LEFT, hanging: Int = 0) {
            val clean = sanitizePdfText(value)
            // Average Helvetica glyph ≈ half the point size; Courier is 0.6 em.
            val glyph = if (font == 3) size * 0.6 else size * 0.5
            val maxChars = ((PDF_WIDTH - (x - PDF_LEFT) - hanging) / glyph).toInt().coerceAtLeast(20)
            clean.split('\n').forEach { paragraph ->
                wrapPdfLine(paragraph, maxChars).forEachIndexed { index, part ->
                    lines += PdfLine(part, font, size, if (index == 0) x else x + hanging)
                }
            }
        }
        fun gap() { lines += PdfLine("", 1, 11) }
        fun list(block: DocumentMarkdown.ListBlock, depth: Int) {
            block.items.forEachIndexed { index, item ->
                val marker = DocumentLayout.listMarker(block.ordered, block.start, index, depth).let { if (it == "•" || it == "◦") "-" else it }
                text("$marker ${DocumentMarkdown.plain(item.runs)}", 1, 11, PDF_LEFT + 14 + depth * 16, hanging = 12)
                item.children?.let { list(it, 1) }
            }
        }
        DocumentMarkdown.parse(markdown).forEach { block ->
            when (block) {
                is DocumentMarkdown.Heading -> {
                    if (lines.isNotEmpty()) gap()
                    text(DocumentMarkdown.plain(block.runs), 2, when (block.level) { 1 -> 16; 2 -> 13; else -> 12 })
                }
                is DocumentMarkdown.Paragraph -> { text(DocumentMarkdown.plain(block.runs), 1, 11); gap() }
                is DocumentMarkdown.Quote -> { block.paragraphs.forEach { text(DocumentMarkdown.plain(it), 1, 11, PDF_LEFT + 16) }; gap() }
                is DocumentMarkdown.CodeBlock -> { block.text.split('\n').forEach { text(it.ifEmpty { " " }, 3, 9, PDF_LEFT + 8) }; gap() }
                DocumentMarkdown.Rule -> gap()
                is DocumentMarkdown.ListBlock -> { list(block, 0); gap() }
                is DocumentMarkdown.Table -> {
                    text(block.header.joinToString(" | ") { DocumentMarkdown.plain(it) }, 2, 10)
                    block.rows.forEach { row -> text(row.joinToString(" | ") { DocumentMarkdown.plain(it) }, 1, 10) }
                    gap()
                }
            }
        }
        while (lines.lastOrNull()?.text?.isEmpty() == true) lines.removeAt(lines.lastIndex)
        return lines.ifEmpty { listOf(PdfLine("Kryzz AI document", 1, 11)) }
    }

    private fun wrapPdfLine(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val out = mutableListOf<String>()
        val line = StringBuilder()
        text.split(' ').forEach { word ->
            if (line.isNotEmpty() && line.length + 1 + word.length > maxChars) {
                out += line.toString()
                line.clear()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) out += line.toString()
        return out.ifEmpty { listOf(text.take(maxChars)) }
    }

    private fun sanitizePdfText(text: String): String = buildString(text.length) {
        text.forEach { char ->
            append(
                when (char.code) {
                    in 32..126 -> char
                    in 160..255 -> char // WinAnsi high range (accents, currency, etc.)
                    9 -> ' '
                    10 -> '\n'
                    0x2022 -> '-'
                    else -> '?'
                }
            )
        }
    }

    private fun escapePdfText(text: String): String =
        text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    // ── Shared ──────────────────────────────────────────────────────────────

    private fun zip(entries: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() } }
        return bytes.toByteArray()
    }

    private fun columnName(index: Int): String { var value = index + 1; val result = StringBuilder(); while (value > 0) { value--; result.append(('A'.code + value % 26).toChar()); value /= 26 }; return result.reverse().toString() }

    /** Escapes XML text and drops characters XML 1.0 forbids (they would corrupt the file). */
    private fun xml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when {
                char == '&' -> append("&amp;")
                char == '<' -> append("&lt;")
                char == '>' -> append("&gt;")
                char == '"' -> append("&quot;")
                char == '\'' -> append("&apos;")
                char.code < 0x20 && char != '\t' && char != '\n' && char != '\r' -> Unit
                char == '￾' || char == '￿' -> Unit
                else -> append(char)
            }
        }
    }
}
