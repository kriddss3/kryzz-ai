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
