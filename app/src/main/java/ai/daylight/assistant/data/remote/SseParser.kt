package ai.daylight.assistant.data.remote

/** Small spec-aware SSE framing parser. Comment/keep-alive lines are deliberately ignored. */
class SseParser {
    private val data = mutableListOf<String>()

    fun accept(line: String?): List<String> {
        if (line == null || line.isEmpty()) return flush()
        if (line.startsWith(':')) return emptyList()
        if (line.startsWith("data:")) data += line.removePrefix("data:").trimStart()
        return emptyList()
    }

    fun finish(): List<String> = flush()

    private fun flush(): List<String> {
        if (data.isEmpty()) return emptyList()
        val event = data.joinToString("\n")
        data.clear()
        return listOf(event)
    }
}
