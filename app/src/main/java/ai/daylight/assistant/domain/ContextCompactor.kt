package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ApiMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * v5.9 context compaction. With a 16-step budget the agent's working history grows by up to
 * ~12k characters per search and per page read, so a long research turn could overflow the
 * model's context or pay for the same excerpts on every round. When the tool results in
 * [ApiMessage] history pass [THRESHOLD_CHARS], results from rounds before the latest one are
 * shrunk in place:
 *
 *  - parallel_search keeps each source's citation number, title, url, publish date and its
 *    first excerpt clipped to [SEARCH_EXCERPT_CHARS], so citations stay valid.
 *  - fetch_url keeps url, title and the first [FETCH_TEXT_CHARS] characters with a note
 *    that the page can be fetched again.
 *  - Every other tool result, the system prompt, user messages, assistant messages and the
 *    whole latest round stay untouched.
 *
 * Deterministic and idempotent (compacted results carry "compacted": true); no LLM call.
 */
internal object ContextCompactor {

    const val THRESHOLD_CHARS = 60_000
    const val SEARCH_EXCERPT_CHARS = 300
    const val FETCH_TEXT_CHARS = 1_500
    const val FETCH_NOTE = "Page text was compacted to save context. Call fetch_url again with this url to read the full page."

    private const val SEARCH_TOOL = AgentTurnPolicy.PARALLEL_SEARCH
    private const val FETCH_TOOL = AgentTurnPolicy.FETCH_URL

    private val json = Json { ignoreUnknownKeys = true }

    fun toolChars(messages: List<ApiMessage>): Int =
        messages.filter { it.role == "tool" }.sumOf { textOf(it)?.length ?: 0 }

    /**
     * Returns [messages] with older search and page results compacted, or the same list when
     * nothing needs to change. [threshold] is exposed for tests.
     */
    fun compact(messages: List<ApiMessage>, threshold: Int = THRESHOLD_CHARS): List<ApiMessage> {
        if (toolChars(messages) <= threshold) return messages
        // The latest round starts at the last assistant message that made tool calls.
        val latestRoundStart = messages.indexOfLast { it.role == "assistant" && !it.toolCalls.isNullOrEmpty() }
        if (latestRoundStart <= 0) return messages
        var changed = false
        val result = messages.mapIndexed { index, message ->
            if (index >= latestRoundStart || message.role != "tool") return@mapIndexed message
            val compacted = when (message.name) {
                SEARCH_TOOL -> textOf(message)?.let(::compactSearch)
                FETCH_TOOL -> textOf(message)?.let(::compactFetch)
                else -> null
            }
            if (compacted == null) message else {
                changed = true
                message.copy(content = JsonPrimitive(compacted))
            }
        }
        return if (changed) result else messages
    }

    /** Compacted search JSON, or null when the result is not a successful, uncompacted search. */
    internal fun compactSearch(raw: String): String? {
        val root = parseObject(raw) ?: return null
        if (root.isCompacted()) return null
        val sources = root["sources"] as? JsonArray ?: return null
        return buildJsonObject {
            put("compacted", true)
            put("sources", buildJsonArray {
                sources.forEach { element ->
                    val source = element as? JsonObject ?: return@forEach
                    add(buildJsonObject {
                        source["citation"]?.let { put("citation", it) }
                        source.string("title")?.let { put("title", it) }
                        source.string("url")?.let { put("url", it) }
                        source.string("publish_date")?.let { put("publish_date", it) }
                        val firstExcerpt = (source["excerpts"] as? JsonArray)
                            ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
                        firstExcerpt?.let { put("excerpt", clip(it, SEARCH_EXCERPT_CHARS)) }
                    })
                }
            })
        }.toString()
    }

    /** Compacted page JSON, or null when the result is not a successful, uncompacted page read. */
    internal fun compactFetch(raw: String): String? {
        val root = parseObject(raw) ?: return null
        if (root.isCompacted()) return null
        val text = root.string("text") ?: return null
        val url = root.string("url") ?: return null
        return buildJsonObject {
            put("url", url)
            root.string("title")?.let { put("title", it) }
            put("compacted", true)
            put("note", FETCH_NOTE)
            put("text", clip(text, FETCH_TEXT_CHARS))
        }.toString()
    }

    private fun textOf(message: ApiMessage): String? = (message.content as? JsonPrimitive)?.contentOrNull

    private fun parseObject(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    private fun JsonObject.isCompacted(): Boolean =
        (this["compacted"] as? JsonPrimitive)?.contentOrNull == "true"

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun clip(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max).trimEnd() + "…"
}
