package ai.daylight.assistant.domain

/**
 * Fresh-info gate for the AUTO agent.
 *
 * AUTO does not force a tool call on every turn the way DEEP_SEARCH / WIDE_SEARCH do —
 * that would make the agent fire `parallel_search` on "hi", "thanks", or "write me a poem",
 * which is wasteful and slow. Instead AUTO only *forces* the first `parallel_search` round
 * when the user's message actually depends on fresh, niche, uncertain, time-sensitive, or
 * source-backed facts. The model can still call `parallel_search` on its own when
 * `tool_choice = "auto"` for anything this heuristic misses; this just guarantees the
 * common case (news, prices, scores, weather, "who is the current …", recent releases)
 * does not get answered from stale memory.
 *
 * Matching is case-insensitive on word boundaries for single-word signals and on
 * substring for multi-word phrases, mirroring [MediaIntent].
 */
private val freshInfoKeywords = listOf(
    // time / recency
    "current", "latest", "newest", "recent", "recently", "today", "tonight", "tomorrow",
    "this week", "this month", "this year", "right now", "now", "live", "up to date",
    "up-to-date", "happening", "happened", "so far", "still",
    // news / headlines
    "news", "headlines", "breaking", "headline",
    // markets / numbers that move
    "price", "prices", "priced", "stock", "stocks", "market", "markets", "exchange rate",
    "conversion rate", "convert", "crypto", "bitcoin", "ethereum", "gas price", "fuel price",
    // weather / environment
    "weather", "forecast", "temperature", "rain", "snow", "humidity", "wind",
    "air quality", "pollen", "uv index",
    // sports / results / rankings
    "score", "scores", "result", "results", "standings", "leaderboard", "ranking",
    "rankings", "ranked", "who won", "winner", "versus", "match", "game", "fixture",
    // releases / updates
    "release", "released", "launch", "launched", "update", "updated", "patch", "patched",
    "version", "changelog", "roadmap", "announce", "announced", "announcement",
    // people / roles that change
    "who is the", "who's the", "who is currently", "president", "prime minister",
    "ceo", "champion", "holder", "governor", "mayor", "coach", "manager",
    // explicit lookup requests
    "search", "look up", "lookup", "google", "find online", "check online", "browse",
    // recent years imply the user wants current info
    "2024", "2025", "2026"
)

/**
 * True when the user's message plausibly depends on fresh / external / source-backed facts,
 * so the AUTO agent should force a `parallel_search` round instead of answering from memory.
 */
internal fun messageLikelyNeedsSearch(text: String): Boolean {
    if (text.isBlank()) return false
    val lower = text.lowercase()
    return freshInfoKeywords.any { keyword ->
        val needle = keyword.lowercase()
        if (needle.contains(' ')) {
            lower.contains(needle)
        } else {
            Regex("\\b" + Regex.escape(needle) + "\\b").containsMatchIn(lower)
        }
    }
}
