package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ApiMessage
import ai.daylight.assistant.data.remote.FunctionCall
import ai.daylight.assistant.data.remote.ToolCall
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Test

class ContextCompactorTest {

    private fun searchJson(excerpt: String) = buildJsonObject {
        put("search_id", "s1")
        put("session_id", "sess")
        putJsonArray("sources") {
            add(buildJsonObject {
                put("citation", 1)
                put("title", "Example")
                put("url", "https://example.com/a")
                put("publish_date", "2026-09-01")
                put("excerpts", buildJsonArray { add(JsonPrimitive(excerpt)); add(JsonPrimitive("second")) })
            })
        }
    }.toString()

    private fun fetchJson(text: String) = buildJsonObject {
        put("url", "https://example.com/page")
        put("title", "Page")
        put("truncated", false)
        put("text", text)
    }.toString()

    private fun call(id: String, name: String) = ToolCall(id, function = FunctionCall(name, "{}"))

    private fun round(id: String, name: String, result: String) = listOf(
        ApiMessage("assistant", null as String?, toolCalls = listOf(call(id, name))),
        ApiMessage("tool", result, id, name)
    )

    private fun text(message: ApiMessage) = (message.content as JsonPrimitive).content

    private fun history(vararg rounds: List<ApiMessage>) =
        listOf(ApiMessage("system", "prompt"), ApiMessage("user", "question")) + rounds.flatMap { it }

    @Test fun belowThresholdNothingChanges() {
        val messages = history(round("a", "parallel_search", searchJson("x".repeat(1_000))))
        assertThat(ContextCompactor.compact(messages)).isSameInstanceAs(messages)
    }

    @Test fun olderSearchKeepsCitationFieldsAndClippedFirstExcerpt() {
        val messages = history(
            round("a", "parallel_search", searchJson("e".repeat(5_000))),
            round("b", "parallel_search", searchJson("f".repeat(5_000)))
        )
        val out = ContextCompactor.compact(messages, threshold = 1_000)
        val older = Json.parseToJsonElement(text(out[3])).jsonObject
        assertThat(older["compacted"]!!.jsonPrimitive.content).isEqualTo("true")
        val source = (older["sources"] as JsonArray)[0] as JsonObject
        assertThat(source["citation"]!!.jsonPrimitive.content).isEqualTo("1")
        assertThat(source["title"]!!.jsonPrimitive.content).isEqualTo("Example")
        assertThat(source["url"]!!.jsonPrimitive.content).isEqualTo("https://example.com/a")
        assertThat(source["publish_date"]!!.jsonPrimitive.content).isEqualTo("2026-09-01")
        assertThat(source["excerpt"]!!.jsonPrimitive.content.length).isAtMost(ContextCompactor.SEARCH_EXCERPT_CHARS + 1)
        assertThat(text(out[3])).doesNotContain("second")
        // The latest round is untouched.
        assertThat(out[5]).isEqualTo(messages[5])
    }

    @Test fun olderFetchKeepsHeadAndRefetchNote() {
        val messages = history(
            round("a", "fetch_url", fetchJson("p".repeat(12_000))),
            round("b", "fetch_url", fetchJson("q".repeat(12_000)))
        )
        val out = ContextCompactor.compact(messages, threshold = 1_000)
        val older = Json.parseToJsonElement(text(out[3])).jsonObject
        assertThat(older["url"]!!.jsonPrimitive.content).isEqualTo("https://example.com/page")
        assertThat(older["title"]!!.jsonPrimitive.content).isEqualTo("Page")
        assertThat(older["note"]!!.jsonPrimitive.content).isEqualTo(ContextCompactor.FETCH_NOTE)
        assertThat(older["text"]!!.jsonPrimitive.content.length).isAtMost(ContextCompactor.FETCH_TEXT_CHARS + 1)
        assertThat(out[5]).isEqualTo(messages[5])
    }

    @Test fun otherMessagesAndToolsStayAsTheyAre() {
        val weather = """{"place":"Riga","current":"${"w".repeat(3_000)}"}"""
        val error = """{"status":"error","error":"timeout"}"""
        val messages = history(
            round("a", "get_weather", weather),
            round("b", "fetch_url", error),
            round("c", "fetch_url", fetchJson("q".repeat(12_000)))
        )
        val out = ContextCompactor.compact(messages, threshold = 1_000)
        assertThat(out.subList(0, 6)).isEqualTo(messages.subList(0, 6))
        assertThat(out[7]).isEqualTo(messages[7])
    }

    @Test fun compactionIsIdempotent() {
        val messages = history(
            round("a", "parallel_search", searchJson("e".repeat(5_000))),
            round("b", "fetch_url", fetchJson("p".repeat(5_000))),
            round("c", "parallel_search", searchJson("f".repeat(5_000)))
        )
        val once = ContextCompactor.compact(messages, threshold = 1_000)
        val twice = ContextCompactor.compact(once, threshold = 1_000)
        assertThat(twice).isSameInstanceAs(once)
        assertThat(ContextCompactor.toolChars(once)).isLessThan(ContextCompactor.toolChars(messages))
    }

    @Test fun noToolRoundMeansNothingToCompact() {
        val messages = listOf(ApiMessage("system", "p"), ApiMessage("tool", fetchJson("x".repeat(5_000)), "a", "fetch_url"))
        assertThat(ContextCompactor.compact(messages, threshold = 10)).isSameInstanceAs(messages)
    }
}
