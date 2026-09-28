package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ApiMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * v5.10 prompt layout for provider prompt caching. Providers cache the longest repeated
 * request prefix (tools, then the system prompt, then messages in order), so anything that
 * changes early in the request makes everything after it full price again.
 *
 * Up to 5.9 the system prompt carried the skills, memories and past-chat snippets retrieved
 * for the latest message plus the location line, so it changed nearly every turn and no
 * history was ever cached. Now:
 *
 *  1. The system message holds only turn-stable text: persona, mode, capability and the fixed
 *     instructions. It is byte-identical across turns while the settings stay the same.
 *  2. History follows unchanged.
 *  3. The retrieved context travels in its own message right before the latest user message,
 *     as a user-role message with a preamble saying the app wrote it. A system-role message
 *     there could be merged into the system prompt by some provider adapters, which would
 *     break the cache again; consecutive user messages are accepted everywhere (history
 *     already sends them after a failed reply).
 *
 * On the next turn the previous context message is gone and a new one sits before the new
 * user message, so everything up to the previous assistant reply is a cache hit. Within a
 * turn every tool round only appends, so each round reuses the whole prefix.
 *
 * Anthropic models on OpenRouter cache only at explicit cache_control breakpoints on content
 * parts, at most four per request. [build] marks up to three when asked: the system prompt,
 * the last history message before the new turn (the hit across turns) and the latest user
 * message (the hit across tool rounds of one turn). OpenAI, Gemini 2.5+ and DeepSeek cache
 * automatically and never receive cache_control.
 */
internal object PromptLayout {

    const val CONTEXT_PREAMBLE =
        "[Context the app retrieved for the next message: saved memories, related past chats, " +
            "active skills and the user's location. The user did not write this. Use only what " +
            "is relevant and do not mention this note.]"

    /** Anthropic's limit on cache_control breakpoints per request. */
    const val MAX_CACHE_BREAKPOINTS = 4

    private val EPHEMERAL = buildJsonObject { put("type", "ephemeral") }

    /** Whether [model] needs explicit cache_control breakpoints (Anthropic on OpenRouter). */
    fun usesCacheBreakpoints(model: String): Boolean = model.trim().lowercase().startsWith("anthropic/")

    /** Joins the per-turn retrieved parts under [CONTEXT_PREAMBLE]; blank when there are none. */
    fun turnContext(parts: List<String>): String {
        val present = parts.map(String::trim).filter(String::isNotEmpty)
        if (present.isEmpty()) return ""
        return (listOf(CONTEXT_PREAMBLE) + present).joinToString("\n\n")
    }

    /**
     * The request messages: [system], then [history] with a context message (when
     * [turnContext] is not blank) placed right before the last user message. With
     * [cacheBreakpoints] the stable points get cache_control (see the class comment).
     */
    fun build(
        system: String,
        history: List<ApiMessage>,
        turnContext: String,
        cacheBreakpoints: Boolean = false
    ): List<ApiMessage> {
        val latestUser = history.indexOfLast { it.role == "user" }
        val context = turnContext.takeIf { it.isNotBlank() && latestUser >= 0 }?.let { ApiMessage("user", it) }
        val messages = mutableListOf(ApiMessage("system", system))
        history.forEachIndexed { index, message ->
            if (index == latestUser && context != null) messages += context
            messages += message
        }
        if (!cacheBreakpoints) return messages
        // Offsets in [messages]: the system prompt is 0, history item i sits at i + 1, or
        // i + 2 once the context message has been inserted before it.
        val latestUserAt = if (latestUser < 0) -1 else latestUser + 1 + (if (context != null) 1 else 0)
        val historyEndAt = if (latestUser > 0) latestUser else -1
        val marks = listOf(0, historyEndAt, latestUserAt).filter { it >= 0 }.distinct().take(MAX_CACHE_BREAKPOINTS)
        return messages.mapIndexed { index, message ->
            if (index in marks) withBreakpoint(message) else message
        }
    }

    /** Number of cache_control breakpoints in [messages] (for tests and sanity checks). */
    fun breakpointCount(messages: List<ApiMessage>): Int = messages.sumOf { message ->
        (message.content as? JsonArray)?.count { (it as? JsonObject)?.containsKey("cache_control") == true } ?: 0
    }

    /**
     * Puts cache_control on the message's text: a plain string becomes one text part, and in a
     * part array the last text part is marked (attachments after it simply stay uncached).
     * Empty text is left alone because Anthropic rejects a breakpoint on an empty block.
     */
    internal fun withBreakpoint(message: ApiMessage): ApiMessage {
        val content = message.content ?: return message
        val marked: JsonElement = when (content) {
            is JsonPrimitive -> {
                val text = content.contentOrNull?.takeIf { it.isNotBlank() } ?: return message
                buildJsonArray { add(textPart(text)) }
            }
            is JsonArray -> {
                val target = content.indexOfLast { part ->
                    val obj = part as? JsonObject
                    (obj?.get("type") as? JsonPrimitive)?.contentOrNull == "text" &&
                        !(obj["text"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()
                }
                if (target < 0) return message
                JsonArray(content.mapIndexed { index, part ->
                    if (index == target) JsonObject((part as JsonObject) + ("cache_control" to EPHEMERAL)) else part
                })
            }
            else -> return message
        }
        return message.copy(content = marked)
    }

    private fun textPart(text: String): JsonObject = buildJsonObject {
        put("type", "text")
        put("text", text)
        putJsonObject("cache_control") { put("type", "ephemeral") }
    }
}
