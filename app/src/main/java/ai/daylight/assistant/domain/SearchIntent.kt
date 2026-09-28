package ai.daylight.assistant.domain

import java.time.LocalDate

/**
 * Fresh-info gate for the AUTO agent.
 *
 * AUTO only *forces* the first `parallel_search` round when the user's message almost
 * certainly depends on facts that move: news, prices, scores, who currently holds a role,
 * the latest release, or an explicit "search for it". Everything else is left to the
 * model, which can still call `parallel_search` on its own because `tool_choice` stays
 * "auto" otherwise.
 *
 * v5.8: the list used to include everyday words ("now", "still", "game", "result",
 * "update", "search", "manager", "current", "today"), so "write me a snake game" or
 * "explain how binary search works" burned a forced web search. It now holds phrases
 * with high precision only. Weather is excluded because `get_weather` answers it
 * directly. Recent years are computed from the clock instead of being hardcoded.
 *
 * Matching is case-insensitive on word boundaries for single words and on substring for
 * multi-word phrases, mirroring [MediaIntent].
 */
private val freshInfoKeywords = listOf(
    // explicit lookup requests
    "search online", "search the web", "search the internet", "web search", "look up",
    "look it up", "look online", "google it", "google this", "find online", "check online",
    "browse the web",
    // news
    "news", "headlines", "headline", "breaking",
    // recency
    "latest", "newest", "most recent", "up to date", "up-to-date", "as of today",
    "currently available",
    // markets and prices
    "price of", "prices of", "stock price", "share price", "market cap", "exchange rate",
    "conversion rate", "bitcoin", "ethereum", "crypto price", "gas price", "fuel price",
    // sports and competitions
    "who won", "final score", "live score", "standings", "leaderboard", "fixtures",
    // releases and announcements
    "release date", "just released", "just announced", "announced today", "changelog"
)

/** "who is the current CEO", "current prime minister", "who's the coach of" … */
private val currentRoleHolder = Regex(
    """\b(?:who(?:'s| is| are)(?: the)?(?: current(?:ly)?)?|current)\s+""" +
        """(?:ceo|cto|cfo|president|prime minister|chancellor|pm|governor|mayor|coach|manager|""" +
        """champion|champions|leader|head|owner|chair|chairman|chairwoman|monarch|king|queen|pope)\b""",
    RegexOption.IGNORE_CASE
)

/**
 * True when the user's message plausibly depends on fresh / external / source-backed facts,
 * so the AUTO agent should force a `parallel_search` round instead of answering from memory.
 * [currentYear] is injectable for tests; the previous, current and next year all count as
 * fresh because model training data lags the calendar.
 */
internal fun messageLikelyNeedsSearch(text: String, currentYear: Int = LocalDate.now().year): Boolean {
    if (text.isBlank()) return false
    val lower = text.lowercase()
    if (currentRoleHolder.containsMatchIn(lower)) return true
    val recentYears = (currentYear - 1..currentYear + 1).map(Int::toString)
    if (recentYears.any { Regex("\\b$it\\b").containsMatchIn(lower) }) return true
    return freshInfoKeywords.any { keyword ->
        val needle = keyword.lowercase()
        if (needle.contains(' ')) {
            lower.contains(needle)
        } else {
            Regex("\\b" + Regex.escape(needle) + "\\b").containsMatchIn(lower)
        }
    }
}
