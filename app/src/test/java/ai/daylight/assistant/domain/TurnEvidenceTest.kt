package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TurnEvidenceTest {

    @Test fun replyWithoutEvidenceIsUnchanged() {
        assertThat(TurnEvidence.annotate("Plain answer.", emptyList(), emptyList())).isEqualTo("Plain answer.")
    }

    @Test fun sourcesKeepTheirCitationNumbers() {
        val annotated = TurnEvidence.annotate(
            "Battery life is best on the X1 [2].",
            listOf(
                Citation("Laptop review roundup", "https://a.example.com/roundup"),
                Citation("X1 battery   test", "https://b.example.com/x1")
            ),
            emptyList()
        )
        assertThat(annotated).startsWith("Battery life is best on the X1 [2].")
        assertThat(annotated).contains("[1] Laptop review roundup: https://a.example.com/roundup")
        assertThat(annotated).contains("[2] X1 battery test: https://b.example.com/x1")
    }

    @Test fun sourceListIsCapped() {
        val citations = (1..14).map { Citation("Source $it", "https://example.com/$it") }
        val annotated = TurnEvidence.annotate("Answer", citations, emptyList())
        assertThat(annotated).contains("[${TurnEvidence.MAX_SOURCES}] Source ${TurnEvidence.MAX_SOURCES}")
        assertThat(annotated).doesNotContain("[${TurnEvidence.MAX_SOURCES + 1}]")
        assertThat(annotated).contains("and 4 more")
    }

    @Test fun filesAreNamedSoFollowUpsCanReferToThem() {
        val output = GeneratedOutput("id", OutputKind.SPREADSHEET, "Budget 2026", "budget-2026.csv", "text/csv", content = "a,b")
        val annotated = TurnEvidence.annotate("Here is your budget.", emptyList(), listOf(output))
        assertThat(annotated).contains("Files attached to this reply: Budget 2026 (spreadsheet, budget-2026.csv)")
        // The file body is not replayed.
        assertThat(annotated).doesNotContain("a,b")
    }
}
