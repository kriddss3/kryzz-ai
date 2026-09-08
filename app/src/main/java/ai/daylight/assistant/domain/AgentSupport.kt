package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ParallelResult

object ToolRoundLimiter {
    const val HARD_MAXIMUM = 8
    fun canRun(completedRounds: Int, configuredMaximum: Int): Boolean =
        completedRounds < configuredMaximum.coerceIn(1, HARD_MAXIMUM)
}

object DeepSearchPolicy {
    fun shouldForceAnotherPass(completedRounds: Int, configuredMaximum: Int): Boolean =
        completedRounds < minOf(2, configuredMaximum.coerceIn(1, 3))
}

object CitationMapper {
    fun map(results: List<ParallelResult>): List<Citation> = results
        .filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
        .distinctBy { it.url }
        .map { Citation(it.title.ifBlank { it.url }, it.url, it.publishDate, it.excerpts.joinToString("\n").take(1_500)) }
}
