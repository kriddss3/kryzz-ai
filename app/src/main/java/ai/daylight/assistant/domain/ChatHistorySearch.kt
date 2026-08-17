package ai.daylight.assistant.domain

/**
 * Local, offline search over earlier conversations. Scores message snippets
 * against the current user turn so small models get a short pasted context
 * instead of having to invent a tool call.
 */
object ChatHistorySearch {

    data class Message(
        val conversationId: String,
        val conversationTitle: String,
        val role: String,
        val content: String,
        val createdAt: Long
    )

    data class Hit(
        val conversationId: String,
        val conversationTitle: String,
        val role: String,
        val snippet: String,
        val createdAt: Long,
        val score: Double
    )

    private val STOPWORDS = setOf(
        "a", "an", "the", "and", "or", "but", "to", "of", "for", "with", "on",
        "in", "at", "by", "is", "are", "was", "were", "am", "i", "my", "me",
        "we", "you", "your", "it", "its", "do", "don't", "dont", "not", "want",
        "have", "has", "from", "that", "this", "about", "really", "very", "just",
        "what", "when", "where", "which", "who", "how", "did", "does", "can",
        "could", "would", "should", "please", "hey", "hi", "hello", "ok", "okay"
    )

    private val RECALL_HINTS = listOf(
        "remember", "last time", "last chat", "earlier", "previous chat",
        "we talked", "we discussed", "you said", "you told", "past chat",
        "other chat", "other conversation", "yesterday", "the other day"
    )

    const val SNIPPET_CHARS = 180
    const val DEFAULT_SCAN = 400
    const val DEFAULT_LIMIT = 4

    fun looksLikeRecall(query: String): Boolean {
        val lower = query.lowercase()
        return RECALL_HINTS.any { it in lower }
    }

    fun retrieve(
        query: String,
        messages: List<Message>,
        limit: Int = DEFAULT_LIMIT,
        minScore: Double = 0.12
    ): List<Hit> {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty() || messages.isEmpty()) return emptyList()
        val floor = if (looksLikeRecall(query)) (minScore * 0.5) else minScore
        val titleBoost = 0.08
        return messages.asSequence()
            .mapNotNull { message ->
                val content = message.content.trim()
                if (content.isBlank()) return@mapNotNull null
                val haystack = "${message.conversationTitle} $content"
                val messageTokens = tokens(haystack)
                if (messageTokens.isEmpty()) return@mapNotNull null
                val overlap = queryTokens.count { it in messageTokens }
                if (overlap == 0) return@mapNotNull null
                val union = (queryTokens + messageTokens).distinct().size
                val coverage = overlap.toDouble() / queryTokens.size
                val jaccard = overlap.toDouble() / union
                var score = 0.7 * coverage + 0.3 * jaccard
                val titleTokens = tokens(message.conversationTitle)
                if (titleTokens.any { it in queryTokens }) score += titleBoost
                message to score
            }
            .filter { it.second >= floor }
            .sortedWith(
                compareByDescending<Pair<Message, Double>> { it.second }
                    .thenByDescending { it.first.createdAt }
            )
            .distinctBy { "${it.first.conversationId}|${normalize(it.first.content).take(48)}" }
            .take(limit.coerceAtLeast(1))
            .map { (message, score) ->
                Hit(
                    conversationId = message.conversationId,
                    conversationTitle = message.conversationTitle.ifBlank { "Untitled chat" },
                    role = message.role.lowercase(),
                    snippet = snippet(message.content),
                    createdAt = message.createdAt,
                    score = score
                )
            }
            .toList()
    }

    fun formatContext(hits: List<Hit>): String {
        if (hits.isEmpty()) return ""
        return buildString {
            appendLine("<past chats — snippets from earlier conversations on this phone; use only what is relevant, never invent>")
            hits.forEach { hit ->
                val who = if (hit.role == "assistant") "assistant" else "user"
                appendLine("- \"${hit.conversationTitle}\" · $who: ${hit.snippet}")
            }
            append("</past chats>")
        }
    }

    private fun snippet(content: String): String {
        val cleaned = content.replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= SNIPPET_CHARS) return cleaned
        return cleaned.take(SNIPPET_CHARS - 1).trimEnd() + "…"
    }

    private fun tokens(text: String): Set<String> = text.lowercase()
        .replace(Regex("[^a-z0-9']"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in STOPWORDS && it.length > 1 }
        .toSet()

    private fun normalize(content: String): String = content.lowercase()
        .replace(Regex("[^a-z0-9]"), "")
        .trim()
}
