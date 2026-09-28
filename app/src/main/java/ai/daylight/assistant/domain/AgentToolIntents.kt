package ai.daylight.assistant.domain

/**
 * Keyword gates for Auto-only tools. Same precision-over-recall rule as [MediaIntent]:
 * only offer a tool with side effects when the user actually asked for it.
 *
 * v5.8: the weather and broad artifact gates are gone. get_weather and create_artifact
 * are cheap and side-effect free, so the planner offers them every agent round; the old
 * gates hid them from requests like "put it in a table I can download".
 */
private val fetchKeywords = listOf(
    "fetch url", "open this link", "open the link", "read this page", "read the page",
    "read this article", "read the article", "summarise this url", "summarize this url",
    "summarise this link", "summarize this link", "what's on this page", "whats on this page"
)
private val scheduleKeywords = listOf(
    "schedule", "scheduled", "remind me", "reminder", "every morning", "every evening",
    "every day", "every weekday", "recurring task", "daily at", "weekly at"
)

/**
 * The user asked for a file to be CREATED, not merely mentioned a format ("what is a pdf
 * used for"). This high-precision set arms the one-shot create_artifact nudge in
 * AgentTurnPolicy; the tool itself is offered every agent round.
 */
private val artifactCreateKeywords = listOf(
    "write a document", "write a report", "write a brief", "markdown document",
    "make an excel", "make a excel", "create an excel", "create a excel",
    "excel file", "excel sheet", "excel spreadsheet",
    "make a spreadsheet", "create a spreadsheet", "build a spreadsheet",
    "make a workbook", "create a workbook",
    "make a pdf", "create a pdf", "into a pdf", "as a pdf",
    "pdf file", "pdf document", "pdf of this", "pdf of the", "pdf of my",
    "make a docx", "create a docx", "word document", "word doc",
    "make a csv", "create a csv", "csv file",
    "make a database", "create a database", "sqlite database"
)
private val skillKeywords = listOf(
    "save as a skill", "save this as a skill", "make a skill", "create a skill",
    "turn this into a skill", "reusable skill", "skill maker"
)
private val codeProjectKeywords = listOf(
    "code project", "full-stack", "fullstack", "zip of the code", "multi-file project",
    "scaffold an app", "generate a repo"
)

private val httpUrl = Regex("""https?://[^\s<>"'()]+""", RegexOption.IGNORE_CASE)

private fun matchesAnyWord(text: String, keywords: List<String>): Boolean {
    if (text.isBlank()) return false
    val lower = text.lowercase()
    return keywords.any { keyword ->
        val needle = keyword.lowercase()
        if (needle.contains(' ')) lower.contains(needle)
        else Regex("\\b" + Regex.escape(needle) + "\\b").containsMatchIn(lower)
    }
}

internal fun userWantsFetch(text: String): Boolean =
    httpUrl.containsMatchIn(text) || matchesAnyWord(text, fetchKeywords)

internal fun userWantsSchedule(text: String): Boolean = matchesAnyWord(text, scheduleKeywords)

internal fun userWantsArtifactCreated(text: String): Boolean = matchesAnyWord(text, artifactCreateKeywords)

internal fun userWantsSkill(text: String): Boolean = matchesAnyWord(text, skillKeywords)

internal fun userWantsCodeProject(text: String): Boolean = matchesAnyWord(text, codeProjectKeywords)

internal fun firstHttpUrl(text: String): String? =
    httpUrl.find(text)?.value?.trimEnd('.', ',', ';', ')', ']')
