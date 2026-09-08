package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ParallelResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentSupportTest {
    @Test fun uwuPersonaRemainsAvailableForCasualUse() {
        val preset = AssistantPreset.byId("uwu")
        assertThat(preset.title).contains("UWU")
        assertThat(preset.prompt).contains("serious professional work")
    }
    @Test fun toolRoundsNeverExceedConfiguredOrHardMaximum() {
        assertThat(ToolRoundLimiter.canRun(0, 3)).isTrue()
        assertThat(ToolRoundLimiter.canRun(2, 3)).isTrue()
        assertThat(ToolRoundLimiter.canRun(3, 3)).isFalse()
        assertThat(ToolRoundLimiter.canRun(7, 8)).isTrue()
        assertThat(ToolRoundLimiter.canRun(8, 8)).isFalse()
        assertThat(ToolRoundLimiter.canRun(8, 99)).isFalse()
    }

    @Test fun citationMappingFiltersInvalidLinksAndDeduplicatesUrls() {
        val result = CitationMapper.map(
            listOf(
                ParallelResult("https://example.com", "Example", "2025-01-01", listOf("one", "two")),
                ParallelResult("https://example.com", "Duplicate"),
                ParallelResult("javascript:alert(1)", "Unsafe")
            )
        )
        assertThat(result).hasSize(1)
        assertThat(result.single().excerpt).contains("one")
    }

    @Test fun deepSearchForcesTwoPassesWithoutBreakingConfiguredBound() {
        assertThat(DeepSearchPolicy.shouldForceAnotherPass(0, 3)).isTrue()
        assertThat(DeepSearchPolicy.shouldForceAnotherPass(1, 3)).isTrue()
        assertThat(DeepSearchPolicy.shouldForceAnotherPass(2, 3)).isFalse()
        assertThat(DeepSearchPolicy.shouldForceAnotherPass(1, 1)).isFalse()
    }
}
