package ai.daylight.assistant.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OfficeFileGenerator {
    fun document(markdown: String): ByteArray {
        val paragraphs = markdown.lines().joinToString("") { raw ->
            val line = raw.trimEnd()
            val clean = line.replace(Regex("^#{1,6}\\s+"), "")
            val style = when {
                line.startsWith("# ") -> "<w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>"
                line.startsWith("## ") -> "<w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>"
                else -> ""
            }
            "<w:p>$style<w:r><w:t xml:space=\"preserve\">${xml(clean.ifEmpty { " " })}</w:t></w:r></w:p>"
        }
        return zip(mapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            "word/document.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1080" w:right="1080" w:bottom="1080" w:left="1080"/></w:sectPr></w:body></w:document>""",
            "word/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:sz w:val="22"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="36"/><w:color w:val="087F5B"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="Heading 1"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="28"/></w:rPr></w:style></w:styles>"""
        ))
    }

    fun spreadsheet(csv: String): ByteArray {
        val rows = parseCsv(csv)
        val sheetRows = rows.mapIndexed { rowIndex, cells ->
            val rowNumber = rowIndex + 1
            val cellXml = cells.mapIndexed { columnIndex, value ->
                val reference = "${columnName(columnIndex)}$rowNumber"
                if (value.startsWith("=") && value.length > 1) "<c r=\"$reference\"><f>${xml(value.drop(1))}</f></c>"
                else {
                    val style = if (rowIndex == 0) " s=\"1\"" else ""
                    "<c r=\"$reference\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"
                }
            }.joinToString("")
            "<row r=\"$rowNumber\">$cellXml</row>"
        }.joinToString("")
        return zip(mapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Kryzz AI" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$sheetRows</sheetData></worksheet>""",
            "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Arial"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Arial"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF087F5B"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs></styleSheet>"""
        ))
    }

    private fun zip(entries: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() } }
        return bytes.toByteArray()
    }

    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var index = 0
        while (index < csv.length) {
            val char = csv[index]; val next = csv.getOrNull(index + 1)
            when {
                char == '"' && quoted && next == '"' -> { cell.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { row += cell.toString(); cell.clear() }
                (char == '\n' || char == '\r') && !quoted -> { if (char == '\r' && next == '\n') index++; row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows.ifEmpty { listOf(listOf("Kryzz AI")) }
    }

    private fun columnName(index: Int): String { var value = index + 1; val result = StringBuilder(); while (value > 0) { value--; result.append(('A'.code + value % 26).toChar()); value /= 26 }; return result.reverse().toString() }
    private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
