package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.OpenRouterModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** v5.10: tool-aware model fallback and the agent model notices. */
class AgentModelChoiceTest {

    private fun model(id: String, vararg params: String) = OpenRouterModel(id = id, supportedParameters = params.toList())

    @Test fun storedModelIsKeptEvenWithoutTools() {
        assertThat(
            AgentModelChoice.choose(
                current = "a/no-tools",
                available = setOf("a/no-tools", "b/tools"),
                preferred = "b/tools",
                toolCapable = setOf("b/tools"),
                candidates = listOf("b/tools")
            )
        ).isEqualTo("a/no-tools")
    }

    @Test fun missingDefaultFallsBackToTheFirstListedToolCapableCandidate() {
        val available = setOf("aaa/chat-only", "google/gemini-x", "openai/mini", "zzz/other")
        assertThat(
            AgentModelChoice.choose(
                current = "gone/model",
                available = available,
                preferred = "missing/default",
                toolCapable = setOf("google/gemini-x", "openai/mini"),
                candidates = listOf("missing/candidate", "openai/mini", "google/gemini-x")
            )
        ).isEqualTo("openai/mini")
    }

    @Test fun withoutAListedCandidateAnyToolCapableModelBeatsTheAlphabeticalFirst() {
        // Before 5.10 this picked "aaa/chat-only", which cannot run the agent's tools.
        assertThat(
            AgentModelChoice.choose(
                current = null,
                available = setOf("aaa/chat-only", "mmm/tools", "zzz/tools"),
                preferred = "missing/default",
                toolCapable = setOf("mmm/tools", "zzz/tools"),
                candidates = listOf("missing/candidate")
            )
        ).isEqualTo("mmm/tools")
    }

    @Test fun aPreferredDefaultWithoutToolsIsSkippedWhenMetadataSaysSo() {
        assertThat(
            AgentModelChoice.choose(
                current = null,
                available = setOf("p/default", "q/tools"),
                preferred = "p/default",
                toolCapable = setOf("q/tools"),
                candidates = emptyList()
            )
        ).isEqualTo("q/tools")
    }

    @Test fun catalogWithoutToolMetadataKeepsThePre510Rule() {
        // MiniMax's catalog reports no parameters: tool support is unknown, not absent.
        assertThat(
            AgentModelChoice.choose(
                current = "old",
                available = setOf("MiniMax-M3", "MiniMax-Z"),
                preferred = "MiniMax-M3",
                toolCapable = null,
                candidates = AgentModelChoice.TOOL_CAPABLE_FALLBACKS
            )
        ).isEqualTo("MiniMax-M3")
        assertThat(
            AgentModelChoice.choose(null, setOf("z", "a"), "missing", toolCapable = null)
        ).isEqualTo("a")
    }

    @Test fun catalogWithNoToolCapableModelStillYieldsASelection() {
        assertThat(
            AgentModelChoice.choose(null, setOf("z", "a"), "missing", toolCapable = emptySet(), candidates = listOf("z"))
        ).isEqualTo("a")
    }

    @Test fun emptyCatalogIsNotAuthoritative() {
        assertThat(AgentModelChoice.choose("x", emptySet(), "y", setOf("y"), listOf("y"))).isNull()
    }

    @Test fun toolCapableIdsReadsSupportedParameters() {
        val catalog = listOf(model("a/tools", "tools", "reasoning"), model("b/plain", "temperature"))
        assertThat(AgentModelChoice.toolCapableIds(catalog)).containsExactly("a/tools")
        // No parameter data at all (MiniMax): unknown.
        assertThat(AgentModelChoice.toolCapableIds(listOf(model("MiniMax-M3")))).isNull()
    }

    @Test fun lacksToolsWarningOnlyWhenTheCatalogIsSure() {
        val catalog = listOf(model("a/tools", "tools"), model("b/plain", "temperature"))
        assertThat(AgentModelChoice.knownToLackTools("b/plain", catalog)).isTrue()
        assertThat(AgentModelChoice.knownToLackTools("a/tools", catalog)).isFalse()
        // Not in the catalog, or a catalog without parameter data: no guess.
        assertThat(AgentModelChoice.knownToLackTools("c/unknown", catalog)).isFalse()
        assertThat(AgentModelChoice.knownToLackTools("MiniMax-M3", listOf(model("MiniMax-M3")))).isFalse()
        assertThat(AgentModelChoice.knownToLackTools("b/plain", emptyList())).isFalse()
    }

    @Test fun upgradeSuggestionShowsOnceForTheOldDefaultOnOpenRouter() {
        val legacy = AgentModelChoice.LEGACY_AGENT_MODEL
        assertThat(AgentModelChoice.shouldSuggestUpgrade(ChatProvider.OPENROUTER, legacy, answered = false)).isTrue()
        assertThat(AgentModelChoice.shouldSuggestUpgrade(ChatProvider.OPENROUTER, legacy, answered = true)).isFalse()
        assertThat(AgentModelChoice.shouldSuggestUpgrade(ChatProvider.OPENROUTER, "openai/gpt-4o", answered = false)).isFalse()
        assertThat(AgentModelChoice.shouldSuggestUpgrade(ChatProvider.MINIMAX, legacy, answered = false)).isFalse()
    }

    @Test fun defaultsAreToolCapableCandidatesThemselves() {
        assertThat(AgentModelChoice.TOOL_CAPABLE_FALLBACKS).contains(AgentModelChoice.OPENROUTER_AGENT_DEFAULT)
        assertThat(AgentModelChoice.MAX_FALLBACKS.first()).isEqualTo(AgentModelChoice.OPENROUTER_MAX_DEFAULT)
        assertThat(AgentModelChoice.OPENROUTER_AGENT_DEFAULT).isNotEqualTo(AgentModelChoice.LEGACY_AGENT_MODEL)
    }
}
