package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ApiMessage
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** v5.10: the request layout that keeps the system prompt and history a cacheable prefix. */
class PromptLayoutTest {

    private val json = Json { explicitNulls = false; encodeDefaults = false }
    private val system = "You are Kryzz AI. You are in Agent mode."

    private fun encode(message: ApiMessage) = json.encodeToString(ApiMessage.serializer(), message)

    private val turnOne = listOf(ApiMessage("user", "What is the weather in Riga?"))
    private val turnTwo = turnOne + listOf(
        ApiMessage("assistant", "It is 12 degrees and cloudy in Riga."),
        ApiMessage("user", "And tomorrow in Tallinn?")
    )

    @Test fun systemMessageIsByteIdenticalWhateverWasRetrieved() {
        val first = PromptLayout.build(
            system, turnOne,
            PromptLayout.turnContext(listOf("<memory context>\n- Lives in Riga\n</memory context>", "Location: Riga"))
        )
        val second = PromptLayout.build(
            system, turnTwo,
            PromptLayout.turnContext(listOf("<past chats>\n- \"Trip\" · user: Tallinn in May\n</past chats>"))
        )
        val none = PromptLayout.build(system, turnTwo, PromptLayout.turnContext(emptyList()))
        assertThat(encode(first[0])).isEqualTo(encode(second[0]))
        assertThat(encode(first[0])).isEqualTo(encode(none[0]))
        assertThat(first[0].role).isEqualTo("system")
        assertThat(encode(first[0])).doesNotContain("Riga")
    }

    @Test fun retrievedContextSitsRightBeforeTheLatestUserMessage() {
        val context = PromptLayout.turnContext(listOf("Location: Tallinn"))
        val messages = PromptLayout.build(system, turnTwo, context)
        assertThat(messages.map { it.role }).containsExactly("system", "user", "assistant", "user", "user").inOrder()
        assertThat((messages[3].content as JsonPrimitive).content).startsWith(PromptLayout.CONTEXT_PREAMBLE)
        assertThat((messages[3].content as JsonPrimitive).content).contains("Location: Tallinn")
        assertThat(messages[4]).isEqualTo(turnTwo.last())
    }

    @Test fun historyBeforeTheNewTurnIsAStablePrefixAcrossTurns() {
        // Turn 2 repeats turn 1's request up to the previous context message, so the provider
        // can serve everything before it from cache.
        val one = PromptLayout.build(system, turnOne, PromptLayout.turnContext(listOf("A")))
        val two = PromptLayout.build(system, turnTwo, PromptLayout.turnContext(listOf("B")))
        assertThat(two.take(1).map(::encode)).isEqualTo(one.take(1).map(::encode))
        assertThat(encode(two[1])).isEqualTo(encode(turnOne[0]))
    }

    @Test fun noContextMessageWhenNothingWasRetrieved() {
        val messages = PromptLayout.build(system, turnTwo, PromptLayout.turnContext(listOf("", "  ")))
        assertThat(messages.map { it.role }).containsExactly("system", "user", "assistant", "user").inOrder()
    }

    @Test fun cacheBreakpointsMarkSystemLastHistoryAndLatestUser() {
        val messages = PromptLayout.build(system, turnTwo, PromptLayout.turnContext(listOf("Location: Tallinn")), cacheBreakpoints = true)
        assertThat(PromptLayout.breakpointCount(messages)).isEqualTo(3)
        assertThat(PromptLayout.breakpointCount(messages)).isAtMost(PromptLayout.MAX_CACHE_BREAKPOINTS)
        fun marked(index: Int) = PromptLayout.breakpointCount(listOf(messages[index])) == 1
        assertThat(marked(0)).isTrue() // system
        assertThat(marked(2)).isTrue() // last reply before the new turn
        assertThat(marked(3)).isFalse() // the per-turn context changes every turn
        assertThat(marked(4)).isTrue() // latest user message: shared by every round of this turn
        // The text itself is unchanged.
        val systemPart = (messages[0].content as JsonArray)[0] as JsonObject
        assertThat((systemPart["text"] as JsonPrimitive).content).isEqualTo(system)
        assertThat(systemPart["cache_control"].toString()).isEqualTo("""{"type":"ephemeral"}""")
    }

    @Test fun firstTurnHasNoHistoryBreakpoint() {
        val messages = PromptLayout.build(system, turnOne, "", cacheBreakpoints = true)
        assertThat(PromptLayout.breakpointCount(messages)).isEqualTo(2)
    }

    @Test fun noBreakpointsUnlessAsked() {
        val messages = PromptLayout.build(system, turnTwo, "", cacheBreakpoints = false)
        assertThat(PromptLayout.breakpointCount(messages)).isEqualTo(0)
        assertThat(messages[0].content).isEqualTo(JsonPrimitive(system))
    }

    @Test fun onlyAnthropicModelsUseExplicitBreakpoints() {
        assertThat(PromptLayout.usesCacheBreakpoints("anthropic/claude-sonnet-5")).isTrue()
        assertThat(PromptLayout.usesCacheBreakpoints("google/gemini-3.8-flash")).isFalse()
        assertThat(PromptLayout.usesCacheBreakpoints("openai/gpt-4o-mini")).isFalse()
        assertThat(PromptLayout.usesCacheBreakpoints("deepseek/deepseek-v3.2")).isFalse()
        assertThat(PromptLayout.usesCacheBreakpoints("MiniMax-M3")).isFalse()
    }

    @Test fun attachmentMessageMarksItsLastTextPart() {
        val parts = buildJsonArray {
            add(buildJsonObject { put("type", "text"); put("text", "What is in this photo?") })
            add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", "data:image/png;base64,AAAA") }) })
        }
        val marked = PromptLayout.withBreakpoint(ApiMessage(role = "user", content = parts))
        val array = marked.content as JsonArray
        assertThat((array[0] as JsonObject).containsKey("cache_control")).isTrue()
        assertThat((array[1] as JsonObject).containsKey("cache_control")).isFalse()
    }

    @Test fun emptyTextIsNeverMarked() {
        val blank = ApiMessage("assistant", "")
        assertThat(PromptLayout.withBreakpoint(blank)).isEqualTo(blank)
        val noContent = ApiMessage("assistant", null as String?)
        assertThat(PromptLayout.withBreakpoint(noContent)).isEqualTo(noContent)
    }
}
