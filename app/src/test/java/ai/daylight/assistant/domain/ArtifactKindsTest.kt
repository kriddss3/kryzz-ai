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
    }

    @Test fun unknownAndMediaTypesDoNotResolve() {
        assertThat(ArtifactKinds.kindFor("powerpoint")).isNull()
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
}
