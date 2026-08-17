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

    private fun entries(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }
}
