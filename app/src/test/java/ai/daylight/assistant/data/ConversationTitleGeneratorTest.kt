package ai.daylight.assistant.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConversationTitleGeneratorTest {
    @Test
    fun localFallbackIsConciseDeterministicAndPromptBased() {
        assertThat(localConversationTitle("Could you please help me plan a twelve-day trip to northern Japan?"))
            .isEqualTo("Plan a twelve-day trip to northern Japan")
        assertThat(localConversationTitle("Please analyse https://example.com/report and explain the risks in detail"))
            .isEqualTo("Analyse link and explain the risks in")
    }

    @Test
    fun generatedTitlesLoseLabelsMarkdownAndTrailingPunctuation() {
        assertThat(sanitizeGeneratedTitle("**Title: Northern Japan Itinerary.**"))
            .isEqualTo("Northern Japan Itinerary")
        assertThat(sanitizeGeneratedTitle("\n\n")).isNull()
    }
}
