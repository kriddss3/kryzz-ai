package ai.daylight.assistant.domain

import ai.daylight.assistant.domain.SpreadsheetContent.Cell
import ai.daylight.assistant.domain.SpreadsheetContent.NumberFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpreadsheetContentTest {
    @Test fun classifiesStrictNumbersAndKeepsIdentifiersAsText() {
        assertThat(SpreadsheetContent.classify("42")).isEqualTo(Cell.Number("42"))
        assertThat(SpreadsheetContent.classify("-3.25")).isEqualTo(Cell.Number("-3.25"))
        assertThat(SpreadsheetContent.classify("0.5")).isEqualTo(Cell.Number("0.5"))
        assertThat(SpreadsheetContent.classify("1e3")).isEqualTo(Cell.Number("1e3"))
        assertThat(SpreadsheetContent.classify("007")).isEqualTo(Cell.Text("007"))
        assertThat(SpreadsheetContent.classify("+371 2000 0000")).isEqualTo(Cell.Text("+371 2000 0000"))
        assertThat(SpreadsheetContent.classify("555-0100")).isEqualTo(Cell.Text("555-0100"))
        assertThat(SpreadsheetContent.classify("4111111111111111")).isEqualTo(Cell.Text("4111111111111111"))
        assertThat(SpreadsheetContent.classify("2026-09-28")).isEqualTo(Cell.Text("2026-09-28"))
        assertThat(SpreadsheetContent.classify("")).isEqualTo(Cell.Empty)
    }

    @Test fun classifiesGroupedPercentBooleanAndFormula() {
        assertThat(SpreadsheetContent.classify("1,234")).isEqualTo(Cell.Number("1234", NumberFormat.THOUSANDS))
        assertThat(SpreadsheetContent.classify("1,234.50")).isEqualTo(Cell.Number("1234.50", NumberFormat.THOUSANDS_DECIMAL))
        assertThat(SpreadsheetContent.classify("12%")).isEqualTo(Cell.Number("0.12", NumberFormat.PERCENT))
        assertThat(SpreadsheetContent.classify("7.5 %")).isEqualTo(Cell.Number("0.075", NumberFormat.PERCENT_DECIMAL))
        assertThat(SpreadsheetContent.classify("100%")).isEqualTo(Cell.Number("1", NumberFormat.PERCENT))
        assertThat(SpreadsheetContent.classify("TRUE")).isEqualTo(Cell.Bool(true))
        assertThat(SpreadsheetContent.classify("false")).isEqualTo(Cell.Bool(false))
        assertThat(SpreadsheetContent.classify("=SUM(B2:B4)")).isEqualTo(Cell.Formula("SUM(B2:B4)"))
        assertThat(SpreadsheetContent.classify("=")).isEqualTo(Cell.Text("="))
    }

    @Test fun splitsSheetsAtMarkersWithUniqueValidNames() {
        val sheets = SpreadsheetContent.sheets(
            "### Sheet: Budget\nItem,Cost\nRent,900\n\n## sheet: Q1/Q2 [draft]\nA,B\n1,2\n### Sheet: budget\nX\n"
        )
        assertThat(sheets.map { it.name }).containsExactly("Budget", "Q1 Q2 draft", "budget (2)").inOrder()
        assertThat(sheets[0].rows).containsExactly(listOf("Item", "Cost"), listOf("Rent", "900")).inOrder()
        assertThat(sheets[2].rows).containsExactly(listOf("X"))
    }

    @Test fun contentWithoutMarkersIsOneSheet() {
        val sheets = SpreadsheetContent.sheets("Name,Amount\n\"Green, Inc.\",12")
        assertThat(sheets.single().name).isEqualTo(SpreadsheetContent.DEFAULT_SHEET_NAME)
        assertThat(sheets.single().rows[1]).containsExactly("Green, Inc.", "12").inOrder()
        assertThat(SpreadsheetContent.sheets("").single().rows).isNotEmpty()
    }

    @Test fun markerInsideQuotedFieldIsText() {
        val sheets = SpreadsheetContent.sheets("Note\n\"line one\n### Sheet: Not a sheet\"\n")
        assertThat(sheets).hasSize(1)
        assertThat(sheets.single().rows[1].single()).isEqualTo("line one\n### Sheet: Not a sheet")
    }

    @Test fun mapSectionsKeepsMarkers() {
        val mapped = SpreadsheetContent.mapSections("### Sheet: A\nx\n### Sheet: B\ny") { it.uppercase() }
        assertThat(mapped).isEqualTo("### Sheet: A\nX\n### Sheet: B\nY")
    }

    @Test fun sheetNamesAreTrimmedToExcelLimits() {
        assertThat(SpreadsheetContent.sanitizeSheetName("'Quarterly: revenue / costs by region and month'")).hasLength(31)
        assertThat(SpreadsheetContent.sanitizeSheetName("[]")).isEqualTo(SpreadsheetContent.DEFAULT_SHEET_NAME)
    }

    @Test fun columnWidthsFollowContent() {
        val widths = SpreadsheetContent.columnWidths(listOf(listOf("Id", "A long description header"), listOf("1", "x")))
        assertThat(widths[0]).isEqualTo(8.0)
        assertThat(widths[1]).isGreaterThan(20.0)
    }
}
