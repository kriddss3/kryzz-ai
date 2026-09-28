package ai.daylight.assistant.domain

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * v5.11: the short detail a live activity chip shows next to a tool call ("Searching:
 * pixel 10 battery test", "Reading: rtings.com/laptop/…"). The executor derives it from
 * the call's arguments when the call starts and sends the same text with the stop event,
 * so the chat can match each stop to its own chip.
 */
object ToolActivityDetail {
    const val MAX_CHARS = 40

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The chip detail for a call of [toolName] with raw JSON [arguments], or null for none. */
    fun of(toolName: String, arguments: String): String? {
        val args = runCatching { json.parseToJsonElement(arguments) }.getOrNull() as? JsonObject
        val raw = when (toolName) {
            AgentTurnPolicy.PARALLEL_SEARCH -> (args?.get("search_queries") as? JsonArray)
                ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
                ?: AgentLoopPolicy.parseSearchQueries(arguments).firstOrNull()
            AgentTurnPolicy.FETCH_URL -> args?.string("url")?.let(::hostAndPath)
            AgentTurnPolicy.GET_WEATHER -> args?.string("place")
            AgentTurnPolicy.CREATE_ARTIFACT -> args?.string("title")
            else -> null
        }
        return raw?.let(::shorten)?.takeIf(String::isNotBlank)
    }

    /** The chip label: a short verb plus the detail, or null when the tool keeps its plain label. */
    fun chipLabel(toolName: String, detail: String?): String? {
        if (detail.isNullOrBlank()) return null
        val verb = when (toolName) {
            AgentTurnPolicy.PARALLEL_SEARCH -> "Searching"
            AgentTurnPolicy.FETCH_URL -> "Reading"
            AgentTurnPolicy.GET_WEATHER -> "Weather"
            AgentTurnPolicy.CREATE_ARTIFACT -> "Creating"
            else -> return null
        }
        return "$verb: $detail"
    }

    /** "https://www.rtings.com/laptop/reviews/x" -> "rtings.com/laptop/…". Null for a bad URL. */
    fun hostAndPath(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.removePrefix("www.")?.takeIf(String::isNotBlank) ?: return null
        val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotBlank)
        return when {
            segments.isEmpty() -> host
            segments.size == 1 && uri.rawQuery == null -> "$host/${segments[0]}"
            else -> "$host/${segments[0]}/…"
        }
    }

    /** Collapses whitespace and cuts to [MAX_CHARS] with an ellipsis. */
    fun shorten(text: String, max: Int = MAX_CHARS): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}

/**
 * v5.11: live chip bookkeeping for the chat. Concurrent calls with the same label share one
 * chip, counted so it disappears only when the last of them stops; calls with different
 * details get their own chips, and each stop event removes only the chip with its label.
 */
class ActivityChipCounter {
    private val running = mutableMapOf<String, Int>()

    fun update(current: List<String>, label: String, started: Boolean): List<String> {
        val count = (running[label] ?: 0) + if (started) 1 else -1
        if (count > 0) running[label] = count else running.remove(label)
        return when {
            count <= 0 -> current - label
            label in current -> current
            else -> current + label
        }
    }
}
