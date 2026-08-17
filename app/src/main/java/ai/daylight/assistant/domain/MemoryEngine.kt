package ai.daylight.assistant.domain

import ai.daylight.assistant.data.local.MemoryEntity

/**
 * Local, offline memory: extracts durable user facts from plain chat text and
 * retrieves the most relevant stored memories for a new message. Pure Kotlin —
 * no Android dependencies, fully unit-testable.
 */
object MemoryEngine {

    enum class Category { USER, PREFERENCE, GOAL, PROJECT, OTHER }

    data class ExtractedMemory(val content: String, val category: Category)

    private data class Pattern(val regex: Regex, val category: Category, val capturesRest: Boolean)

    // First-person durable-statement signals. Each pattern must match a full
    // sentence fragment; the whole (normalized) sentence becomes the memory so
    // the fact is stored in the user's own words.
    private val PATTERNS = listOf(
        Pattern(Regex("""\bmy name is\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\b(call me|you can call me)\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi('m| am) from\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi (was born|grew up) (in|on|near)\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi live in\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi('m| am) based (in|near)\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bmy hometown (is|was)\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi speak\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bmy (phone number|email|birthday|address|age) is\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi('m| am) \d+ years old\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi('m| am) (a |an )?\d+[ -]year[ -]old\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi('m| am) (a )?(man|woman|male|female|non-binary|nonbinary|transgender|trans)\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bmy gender is\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi identify as\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bmy (ethnicity|nationality|race) is\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi('m| am) (a|an) [a-z]+(ian|ean|ish|ese)\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bmy (job|occupation|profession) (is|was)\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi do .* for a living\b""", RegexOption.IGNORE_CASE), Category.USER, true),
        Pattern(Regex("""\bi (really |very )?(like|love|enjoy)\b""", RegexOption.IGNORE_CASE), Category.PREFERENCE, true),
        Pattern(Regex("""\bi('m| am) into\b""", RegexOption.IGNORE_CASE), Category.PREFERENCE, true),
        Pattern(Regex("""\bi('m| am) interested in\b""", RegexOption.IGNORE_CASE), Category.PREFERENCE, true),
        Pattern(Regex("""\bi('m| am) a (big |huge )?fan of\b""", RegexOption.IGNORE_CASE), Category.PREFERENCE, true),
        Pattern(Regex("""\bi (don't|do not|dislike|hate)\b""", RegexOption.IGNORE_CASE), Category.PREFERENCE, true),
        Pattern(Regex("""\bi prefer\b""", RegexOption.IGNORE_CASE), Category.PREFERENCE, true),
        Pattern(Regex("""\bmy (favourite|favorite)""", RegexOption.IGNORE_CASE), Category.PREFERENCE, true),
        Pattern(Regex("""\bi want to\b""", RegexOption.IGNORE_CASE), Category.GOAL, true),
        Pattern(Regex("""\bmy goal is\b""", RegexOption.IGNORE_CASE), Category.GOAL, true),
        Pattern(Regex("""\bi ('m|am) (trying|planning|hoping) to\b""", RegexOption.IGNORE_CASE), Category.GOAL, true),
        Pattern(Regex("""\bi (work|study) (as|at|for|on|with)\b""", RegexOption.IGNORE_CASE), Category.PROJECT, true),
        Pattern(Regex("""\bi (play|practice|practise)\b""", RegexOption.IGNORE_CASE), Category.PROJECT, true),
        Pattern(Regex("""\bi (use|have) (a|an|my)\b""", RegexOption.IGNORE_CASE), Category.PROJECT, true),
        Pattern(Regex("""\bmy (hobby|hobbies|favourite|favorite) (is|are)\b""", RegexOption.IGNORE_CASE), Category.PROJECT, true),
        Pattern(Regex("""\bremember\b""", RegexOption.IGNORE_CASE), Category.OTHER, true)
    )

    // Transient topics that are not durable facts worth remembering.
    private val TRANSIENT_TOKENS = setOf(
        "question", "problem", "idea", "feeling", "suggestion", "request",
        "favor", "favour", "help", "quick", "today", "tomorrow", "yesterday"
    )

    // Pronouns / empty references after the signal: "I like it" is not a fact.
    private val EMPTY_REFERENTS = setOf(
        "it", "them", "that", "this", "these", "those", "stuff", "things",
        "something", "anything", "everything", "nothing"
    )

    private val STOPWORDS = setOf(
        "a", "an", "the", "and", "or", "but", "to", "of", "for", "with", "on",
        "in", "at", "by", "is", "are", "was", "were", "am", "i", "my", "me",
        "we", "you", "your", "it", "its", "do", "don't", "dont", "not", "want",
        "have", "has", "from", "that", "this", "about", "really", "very", "just"
    )

    const val MAX_MEMORY_CHARS = 200
    const val MIN_MEMORY_CHARS = 6

    /** Splits text into sentences and keeps only durable first-person facts. */
    fun extract(text: String): List<ExtractedMemory> = text
        .split(Regex("""(?<=[.!?])\s+|\n+"""))
        .map(String::trim)
        .filter { it.isNotBlank() }
        .mapNotNull { sentence -> extractSentence(sentence) }
        .distinctBy { normalize(it.content) }

    private fun extractSentence(sentence: String): ExtractedMemory? {
        if (sentence.contains('?') || sentence.contains('!')) return null
        val lower = sentence.lowercase()
        val pattern = PATTERNS.firstOrNull { it.regex.containsMatchIn(lower) } ?: return null
        val rest = lower.substring(pattern.regex.find(lower)?.range?.last?.plus(1) ?: 0)
        if (rest.containsAnyTransient() || rest.isBlank() || normalize(rest) in EMPTY_REFERENTS) return null
        val content = compactSentence(sentence)
        if (content.length < MIN_MEMORY_CHARS || content.length > MAX_MEMORY_CHARS) return null
        return ExtractedMemory(content, pattern.category)
    }

    private fun String.containsAnyTransient(): Boolean = TRANSIENT_TOKENS.any { token ->
        Regex("""\b$token\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)
    }

    /** Collapses whitespace, strips a trailing period, and capitalizes the first letter. */
    fun compactSentence(sentence: String): String {
        val cleaned = sentence.replace(Regex("""\s+"""), " ").trim()
            .removeSuffix(".")
            .trim()
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /**
     * Scores enabled memories against a user message: token overlap (coverage
     * weighted above similarity) plus usage, recency and pin bonuses. Returns
     * the best [limit] memories with score >= [minScore], best first.
     */
    fun retrieve(
        query: String,
        memories: List<MemoryEntity>,
        limit: Int = 4,
        minScore: Double = 0.12,
        now: Long = System.currentTimeMillis()
    ): List<MemoryEntity> {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return emptyList()
        val dayMs = 86_400_000L
        return memories.asSequence()
            .filter { it.enabled }
            .map { memory ->
                val memoryTokens = tokens(memory.content)
                if (memoryTokens.isEmpty()) return@map null
                val overlap = queryTokens.count { it in memoryTokens }
                val union = (queryTokens + memoryTokens).distinct().size
                val coverage = overlap.toDouble() / queryTokens.size
                val jaccard = overlap.toDouble() / union
                var score = 0.7 * coverage + 0.3 * jaccard
                score += (memory.usageCount.coerceAtMost(3) * 0.02)
                if (memory.pinned) score += 0.05
                if (now - memory.lastUsedAt.coerceAtMost(now) < 30 * dayMs) score += 0.03
                memory to score
            }
            .filterNotNull()
            .filter { it.second >= minScore }
            .sortedWith(compareByDescending<Pair<MemoryEntity, Double>> { it.second }.thenBy { it.first.updatedAt })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun tokens(text: String): Set<String> = text.lowercase()
        .replace(Regex("""[^a-z0-9']"""), " ")
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() && it !in STOPWORDS && it.length > 1 }
        .toSet()

    /** Normalized form used for deduplication. */
    fun normalize(content: String): String = content.lowercase()
        .replace(Regex("""[^a-z0-9]"""), "")
        .trim()

    /** True when [candidate] duplicates one of [existing] (equality or heavy containment). */
    fun looksDuplicate(candidate: String, existing: List<String>): Boolean {
        val a = normalize(candidate)
        if (a.isEmpty()) return true
        return existing.any { raw ->
            val b = normalize(raw)
            when {
                a == b -> true
                a.length >= 10 && b.length >= 10 && (a.contains(b) || b.contains(a)) -> true
                else -> false
            }
        }
    }
}