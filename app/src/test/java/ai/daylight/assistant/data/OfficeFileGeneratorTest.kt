package ai.daylight.assistant.data

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Test

class OfficeFileGeneratorTest {
    @Test fun createsOpenXmlDocumentPackage() {
        val entries = entries(OfficeFileGenerator.document("# Project brief\nA practical plan & timeline"))
        assertThat(entries.keys).containsAtLeast("[Content_Types].xml", "word/document.xml", "word/styles.xml")
        assertThat(entries.getValue("word/document.xml")).contains("Project brief")
        assertThat(entries.getValue("word/document.xml")).contains("&amp;")
    }

    @Test fun createsOpenXmlSpreadsheetWithQuotedCsvAndFormula() {
        val entries = entries(OfficeFileGenerator.spreadsheet("Name,Amount\n\"Green, Inc.\",12\nTotal,=SUM(B2:B2)"))
        assertThat(entries.keys).containsAtLeast("xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/styles.xml")
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertThat(sheet).contains("Green, Inc.")
        assertThat(sheet).contains("<f>SUM(B2:B2)</f>")
    }

    @Test fun documentRendersHeadingsListsTablesCodeAndLinks() {
        val entries = entries(
            OfficeFileGenerator.document(
                "# Title\n## Part\n### Detail\nSome **bold**, *italic* and `code` with [a link](https://example.com/?a=1&b=2).\n\n" +
                    "- one\n  - nested\n- two\n\n1. first\n2. second\n\nBetween lists.\n\n5. restart\n\n| Name | Score |\n|---|---|\n| Ann | 9 |\n\n```\nval x = 1\n```\n> quoted"
            )
        )
        assertThat(entries.keys).containsAtLeast("word/numbering.xml", "word/_rels/document.xml.rels")
        val document = entries.getValue("word/document.xml")
        assertThat(document).contains("<w:pStyle w:val=\"Heading1\"/>")
        assertThat(document).contains("<w:pStyle w:val=\"Heading2\"/>")
        assertThat(document).contains("<w:pStyle w:val=\"Heading3\"/>")
        assertThat(document).contains("<w:b/><w:bCs/></w:rPr><w:t xml:space=\"preserve\">bold</w:t>")
        assertThat(document).contains("<w:i/><w:iCs/></w:rPr><w:t xml:space=\"preserve\">italic</w:t>")
        assertThat(document).contains("<w:rStyle w:val=\"CodeChar\"/></w:rPr><w:t xml:space=\"preserve\">code</w:t>")
        assertThat(document).doesNotContain("**")
        assertThat(document).doesNotContain("| Name")
        // Bullets share num 1 (level 1 for the nested item); each ordered list gets its own num.
        assertThat(document).contains("<w:ilvl w:val=\"1\"/><w:numId w:val=\"1\"/>")
        assertThat(document).contains("<w:numId w:val=\"2\"/>")
        assertThat(document).contains("<w:numId w:val=\"3\"/>")
        assertThat(entries.getValue("word/numbering.xml")).contains("<w:startOverride w:val=\"5\"/>")
        assertThat(document).contains("<w:tbl>")
        assertThat(document).contains("<w:tblHeader/>")
        assertThat(document).contains("<w:pStyle w:val=\"CodeBlock\"/>")
        assertThat(document).contains("<w:pStyle w:val=\"Quote\"/>")
        assertThat(document).contains("<w:hyperlink r:id=\"rId3\"")
        assertThat(entries.getValue("word/_rels/document.xml.rels"))
            .contains("Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"https://example.com/?a=1&amp;b=2\" TargetMode=\"External\"")
        assertThat(entries.getValue("[Content_Types].xml")).contains("/word/numbering.xml")
    }

    @Test fun documentDropsCharactersXmlForbids() {
        val document = entries(OfficeFileGenerator.document("bad\u0001char Rīga")).getValue("word/document.xml")
        assertThat(document).contains("badchar Rīga")
    }

    @Test fun spreadsheetTypesCellsAndAddsSheetFeatures() {
        val entries = entries(OfficeFileGenerator.spreadsheet("Id,Amount,Share,Active,Note\n007,12,12%,TRUE,\"a, b\"\n008,30.5,7.5%,false,x\nTotal,=SUM(B2:B3),,,"))
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        assertThat(sheet).contains("<c r=\"A2\" t=\"inlineStr\"><is><t xml:space=\"preserve\">007</t></is></c>")
        assertThat(sheet).contains("<c r=\"B2\"><v>12</v></c>")
        assertThat(sheet).contains("<c r=\"C2\" s=\"4\"><v>0.12</v></c>")
        assertThat(sheet).contains("<c r=\"C3\" s=\"5\"><v>0.075</v></c>")
        assertThat(sheet).contains("<c r=\"D2\" t=\"b\"><v>1</v></c>")
        assertThat(sheet).contains("<c r=\"B4\"><f>SUM(B2:B3)</f></c>")
        assertThat(sheet).contains("state=\"frozen\"")
        assertThat(sheet).contains("<autoFilter ref=\"A1:E4\"/>")
        assertThat(sheet).contains("<col min=\"1\" max=\"1\"")
        // Header cells stay text with the header style.
        assertThat(sheet).contains("<c r=\"B1\" t=\"inlineStr\" s=\"1\">")
        val workbook = entries.getValue("xl/workbook.xml")
        assertThat(workbook).contains("fullCalcOnLoad=\"1\"")
        assertThat(workbook).contains("_xlnm._FilterDatabase")
    }

    @Test fun spreadsheetWritesOneWorksheetPerSheetMarker() {
        val entries = entries(OfficeFileGenerator.spreadsheet("### Sheet: Income\nMonth,Amount\nJan,100\n### Sheet: Costs\nMonth,Amount\nJan,40"))
        assertThat(entries.keys).containsAtLeast("xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")
        val workbook = entries.getValue("xl/workbook.xml")
        assertThat(workbook).contains("<sheet name=\"Income\" sheetId=\"1\" r:id=\"rId1\"/>")
        assertThat(workbook).contains("<sheet name=\"Costs\" sheetId=\"2\" r:id=\"rId2\"/>")
        assertThat(entries.getValue("xl/_rels/workbook.xml.rels")).contains("Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\"")
        assertThat(entries.getValue("[Content_Types].xml")).contains("/xl/worksheets/sheet2.xml")
        assertThat(entries.getValue("xl/worksheets/sheet2.xml")).contains("<v>40</v>")
    }

    @Test fun createsValidPdfWithEscapedTextAndPages() {
        val bytes = OfficeFileGenerator.pdf("# Project brief\nA practical plan & (timeline) \\ v1\n" + ("Detail line\n".repeat(50)))
        val text = bytes.toString(Charsets.ISO_8859_1)
        assertThat(text).startsWith("%PDF-1.4")
        assertThat(text).contains("%%EOF")
        assertThat(text).contains("/Type /Catalog")
        assertThat(text).contains("/BaseFont /Helvetica")
        assertThat(text).contains("Project brief")
        // Parens and backslash in the source text must be escaped inside PDF strings.
        assertThat(text).contains("\\(timeline\\)")
        assertThat(text).contains("\\\\")
        // 50 detail lines cannot fit on one Letter page: a second page object exists.
        assertThat(text).contains("/Count 2")
    }

    @Test fun pdfHandlesBlankAndNonLatinInput() {
        val bytes = OfficeFileGenerator.pdf("")
        assertThat(bytes.toString(Charsets.ISO_8859_1)).startsWith("%PDF-1.4")
        val cjk = OfficeFileGenerator.pdf("タイトル")
        assertThat(cjk.toString(Charsets.ISO_8859_1)).contains("???")
    }

    private fun entries(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }
}
