package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the v5.7.1 artifact-acceptance contract: models answer "make an excel file"
 * with type names like "excel" / "xlsx" / "csv", and those must resolve instead of
 * bouncing as validation errors (which is what made Auto die fileless before).
 */
class ArtifactKindsTest {

    @Test fun canonicalTypeNamesResolve() {
        assertThat(ArtifactKinds.kindFor("document")).isEqualTo(OutputKind.DOCUMENT)
        assertThat(ArtifactKinds.kindFor("spreadsheet")).isEqualTo(OutputKind.SPREADSHEET)
        assertThat(ArtifactKinds.kindFor("database")).isEqualTo(OutputKind.DATABASE)
        assertThat(ArtifactKinds.kindFor("pdf")).isEqualTo(OutputKind.PDF)
    }

    @Test fun commonAliasesResolve() {
        assertThat(ArtifactKinds.kindFor("excel")).isEqualTo(OutputKind.SPREADSHEET)
        assertThat(ArtifactKinds.kindFor("xlsx")).isEqualTo(OutputKind.SPREADSHEET)
        assertThat(ArtifactKinds.kindFor("csv")).isEqualTo(OutputKind.SPREADSHEET)
        assertThat(ArtifactKinds.kindFor("docx")).isEqualTo(OutputKind.DOCUMENT)
        assertThat(ArtifactKinds.kindFor("word")).isEqualTo(OutputKind.DOCUMENT)
        assertThat(ArtifactKinds.kindFor("PDF")).isEqualTo(OutputKind.PDF)
        assertThat(ArtifactKinds.kindFor(" sqlite ")).isEqualTo(OutputKind.DATABASE)
        assertThat(ArtifactKinds.kindFor("ics")).isEqualTo(OutputKind.CALENDAR)
        assertThat(ArtifactKinds.kindFor("calendar")).isEqualTo(OutputKind.CALENDAR)
    }

    @Test fun unknownAndMediaTypesDoNotResolve() {
        assertThat(ArtifactKinds.kindFor("hologram")).isNull()
        // v5.13.7: slides are a file type now.
        assertThat(ArtifactKinds.kindFor("powerpoint")).isEqualTo(OutputKind.PRESENTATION)
        assertThat(ArtifactKinds.kindFor("")).isNull()
        assertThat(ArtifactKinds.kindFor(null)).isNull()
        // Media kinds exist but are never valid create_artifact types.
        assertThat(ArtifactKinds.kindFor("image")).isNull()
        assertThat(ArtifactKinds.kindFor("video")).isNull()
    }

    @Test fun outerCodeFenceIsStripped() {
        assertThat(ArtifactKinds.stripOuterFence("```csv\na,b\n1,2\n```")).isEqualTo("a,b\n1,2")
        assertThat(ArtifactKinds.stripOuterFence("a,b\n1,2")).isEqualTo("a,b\n1,2")
        // Trailing prose after the fence means it is not a pure wrapper: keep as-is.
        assertThat(ArtifactKinds.stripOuterFence("```csv\na,b\n```\nnotes")).isEqualTo("```csv\na,b\n```\nnotes")
    }

    @Test fun markdownTableBecomesCsvForSpreadsheets() {
        val table = "| Name | Amount |\n|---|---|\n| Green, Inc. | 12 |\n| **Total** | 12 |"
        val csv = ArtifactKinds.normalizeContent(OutputKind.SPREADSHEET, table)
        assertThat(csv).isEqualTo("Name,Amount\n\"Green, Inc.\",12\nTotal,12")
    }

    @Test fun realCsvIsNotTouched() {
        val csv = "Name,Amount\nGreen Inc.,12"
        assertThat(ArtifactKinds.normalizeContent(OutputKind.SPREADSHEET, csv)).isEqualTo(csv)
    }

    @Test fun documentContentKeepsMarkdown() {
        val doc = "# Title\nSome **bold** text"
        assertThat(ArtifactKinds.normalizeContent(OutputKind.DOCUMENT, doc)).isEqualTo(doc)
    }

    @Test fun bareEventBecomesAnImportableCalendar() {
        val event = """
            BEGIN:VEVENT
            SUMMARY:Dentist
            DTSTART:20261102T140000Z
            DTEND:20261102T150000Z
            END:VEVENT
        """.trimIndent()
        val calendar = ArtifactKinds.normalizeContent(OutputKind.CALENDAR, "```ics\n$event\n```")
        assertThat(calendar).contains("BEGIN:VCALENDAR")
        assertThat(calendar).contains("END:VCALENDAR")
        assertThat(calendar).contains("SUMMARY:Dentist")
        assertThat(calendar).contains("UID:")
        assertThat(ArtifactKinds.calendarIsUsable(calendar)).isTrue()
        assertThat(ArtifactKinds.calendarIsUsable("Dentist at 3")).isFalse()
    }
}
