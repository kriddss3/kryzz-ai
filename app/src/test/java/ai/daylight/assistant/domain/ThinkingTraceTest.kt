package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ChunkDelta
import ai.daylight.assistant.data.remote.reasoningText
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class ThinkingTraceTest {
    @Test fun aMultiStepTurnKeepsWhatItSaidAndEveryToolInOrder() {
        val trace = TurnTrace()
        trace.reasoning("The user wants today's")
        trace.reasoning("The user wants today's figures, so I should search.")
        trace.endRound("Let me check the latest numbers.", listOf("parallel_search" to "pixel 10 battery"))
        trace.reasoning("Two sources agree.")
        trace.endRound("", listOf("fetch_url" to "gsmarena.com/pixel_10", "calculate" to null))
        trace.reasoning("Now I can answer.")
        trace.finishRound()

        val steps = trace.steps()
        assertThat(steps.map { it.kind }).containsExactly(
            ThoughtKind.THINK, ThoughtKind.SAY, ThoughtKind.TOOL,
            ThoughtKind.THINK, ThoughtKind.TOOL, ThoughtKind.TOOL,
            ThoughtKind.THINK
        ).inOrder()
        assertThat(steps[0].text).isEqualTo("The user wants today's figures, so I should search.")
        assertThat(steps[1].text).isEqualTo("Let me check the latest numbers.")
        assertThat(steps[2]).isEqualTo(ThoughtStep(ThoughtKind.TOOL, "parallel_search", "pixel 10 battery"))
        assertThat(ThinkingTrace.stepCount(steps)).isEqualTo(4)
    }

    @Test fun reasoningInProgressShowsAsTheNewestStep() {
        val trace = TurnTrace()
        trace.endRound("Searching.", listOf("parallel_search" to "x"))
        trace.reasoning("Reading the results")
        assertThat(trace.steps().last()).isEqualTo(ThoughtStep(ThoughtKind.THINK, "Reading the results"))
        assertThat(trace.isEmpty).isFalse()
        assertThat(TurnTrace().isEmpty).isTrue()
    }

    @Test fun encodesAndDecodesAndToleratesJunk() {
        val steps = listOf(ThoughtStep(ThoughtKind.THINK, "hmm"), ThoughtStep(ThoughtKind.TOOL, "calculate", "2+2"))
        assertThat(ThinkingTrace.decode(ThinkingTrace.encode(steps))).isEqualTo(steps)
        assertThat(ThinkingTrace.encode(emptyList())).isEqualTo("[]")
        assertThat(ThinkingTrace.decode("[]")).isEmpty()
        assertThat(ThinkingTrace.decode(null)).isEmpty()
        assertThat(ThinkingTrace.decode("not json")).isEmpty()
    }

    @Test fun longReasoningIsCutAndManyStepsKeepTheStartAndTheEnd() {
        val trace = TurnTrace()
        trace.reasoning("x".repeat(ThinkingTrace.MAX_STEP_CHARS + 500))
        trace.finishRound()
        assertThat(trace.steps().single().text.length).isEqualTo(ThinkingTrace.MAX_STEP_CHARS)

        val many = TurnTrace()
        repeat(ThinkingTrace.MAX_STEPS + 10) { many.endRound("", listOf("calculate" to "$it")) }
        val steps = many.steps()
        assertThat(steps).hasSize(ThinkingTrace.MAX_STEPS)
        assertThat(steps.first().detail).isEqualTo("0")
        assertThat(steps.last().detail).isEqualTo("${ThinkingTrace.MAX_STEPS + 9}")
    }

    @Test fun latestLineIsTheLastSentenceOrTheToolLabel() {
        val think = listOf(ThoughtStep(ThoughtKind.THINK, "First I list the options. Then I compare their prices carefully."))
        assertThat(ThinkingTrace.latestLine(think) { "?" }).isEqualTo("Then I compare their prices carefully.")
        val tool = listOf(ThoughtStep(ThoughtKind.TOOL, "parallel_search", "q"))
        assertThat(ThinkingTrace.latestLine(tool) { "Searching: ${it.detail}" }).isEqualTo("Searching: q")
        assertThat(ThinkingTrace.latestLine(emptyList()) { "?" }).isNull()
        assertThat(ThinkingTrace.lastSentence("**Plan.** Ok")).isEqualTo("Plan. Ok")
        assertThat(ThinkingTrace.lastSentence("a".repeat(300))!!.length).isEqualTo(140)
    }

    @Test fun durationLabels() {
        assertThat(ThinkingTrace.durationLabel(0)).isEqualTo("0S")
        assertThat(ThinkingTrace.durationLabel(11_600)).isEqualTo("12S")
        assertThat(ThinkingTrace.durationLabel(65_000)).isEqualTo("1M 05S")
    }

    @Test fun inlineThinkBlocksAreReadForTheTrace() {
        assertThat("<think>weigh it up</think>Answer".thinkText()).isEqualTo("weigh it up")
        assertThat("<think>still going".thinkText()).isEqualTo("still going")
        assertThat("<think>a</think>x<think>b</think>y".thinkText()).isEqualTo("a\n\nb")
        assertThat("no tags".thinkText()).isEmpty()
    }

    @Test fun streamChunksCarryReasoningFromAnyOfItsFields() {
        val json = Json { ignoreUnknownKeys = true }
        assertThat(json.decodeFromString<ChunkDelta>("""{"reasoning":"Let me see"}""").reasoningText()).isEqualTo("Let me see")
        assertThat(json.decodeFromString<ChunkDelta>("""{"reasoning_content":"Hmm"}""").reasoningText()).isEqualTo("Hmm")
        val details = """{"reasoning_details":[{"type":"reasoning.text","text":"A"},{"type":"reasoning.encrypted","data":"zz"},{"type":"reasoning.summary","summary":"B"}]}"""
        assertThat(json.decodeFromString<ChunkDelta>(details).reasoningText()).isEqualTo("AB")
        // Both fields carry the same words: the plain one wins, so nothing doubles.
        val both = """{"reasoning":"X","reasoning_details":[{"type":"reasoning.text","text":"X"}]}"""
        assertThat(json.decodeFromString<ChunkDelta>(both).reasoningText()).isEqualTo("X")
        assertThat(json.decodeFromString<ChunkDelta>("""{"content":"hi"}""").reasoningText()).isEmpty()
    }

    @Test fun makeStyleDressesOnlyTheProvidersPrompt() {
        assertThat(MakeStyle.PLAIN.styled("a red bike")).isEqualTo("a red bike")
        val nothing = MakeStyle.NOTHING.styled("  a red bike ")
        assertThat(nothing).startsWith("a red bike\n\n")
        assertThat(nothing).contains("Glyph")
        assertThat(nothing).contains("dot")
        assertThat(nothing).contains("#D71921")
        assertThat(MakeStyle.from("nothing")).isEqualTo(MakeStyle.NOTHING)
        assertThat(MakeStyle.from(null)).isEqualTo(MakeStyle.PLAIN)
        assertThat(MediaOptions().style).isEqualTo(MakeStyle.PLAIN)
    }
}
