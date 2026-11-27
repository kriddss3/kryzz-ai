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

    @Test fun theLatestFilesCarryTheirSourceForEdits() {
        // v5.13.7: "add a slide" needs the deck as written, not a rewrite from memory.
        val deck = GeneratedOutput("d", OutputKind.PRESENTATION, "Pitch", "pitch.pptx", "x", content = "# Pitch\n## Problem")
        val image = GeneratedOutput("i", OutputKind.IMAGE, "Logo", "logo.png", "image/png", content = "a yellow squid")
        val annotated = TurnEvidence.annotate("Here is the deck.", emptyList(), listOf(deck, image), withSource = true)
        assertThat(annotated).contains("--- pitch.pptx (source as written")
        assertThat(annotated).contains("# Pitch\n## Problem")
        assertThat(annotated).contains("--- end of pitch.pptx ---")
        // Only file deliverables; an image prompt is not a source.
        assertThat(annotated).doesNotContain("a yellow squid")
    }

    @Test fun sourcesShareOneBudget() {
        val big = "x".repeat(TurnEvidence.MAX_SOURCE_CHARS + 500)
        val first = GeneratedOutput("a", OutputKind.TEXT, "A", "a.txt", "text/plain", content = big)
        val second = GeneratedOutput("b", OutputKind.TEXT, "B", "b.txt", "text/plain", content = "second file")
        val annotated = TurnEvidence.annotate("Done.", emptyList(), listOf(first, second), withSource = true)
        assertThat(annotated).contains("[... the rest is not shown ...]")
        assertThat(annotated).doesNotContain("second file")
    }
}
