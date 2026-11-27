package ai.daylight.assistant.domain

/**
 * Keyword gates for Auto-only tools. Same precision-over-recall rule as [MediaIntent]:
 * only offer a tool with side effects when the user actually asked for it.
 *
 * v5.8: the weather and broad artifact gates are gone. get_weather and create_artifact
 * are cheap and side-effect free, so the planner offers them every agent round; the old
 * gates hid them from requests like "put it in a table I can download".
 *
 * v5.13.7: Talk offers create_artifact on every turn too. [userWantsArtifactCreated] now
 * only arms the one-shot nudge for a model that answered a clear file request in prose.
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
    "make a database", "create a database", "sqlite database",
    "calendar event", "calendar invite", "calendar file", "ics", "ics file", ".ics",
    "add to my calendar", "add to the calendar", "add it to my calendar",
    "put it on my calendar", "put this on my calendar", "put this on the calendar",
    "make a file", "create a file", "downloadable file", "as a file", "into a file",
    "file i can download", "file to download", "save as a file", "save this as a file"
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

/**
 * v5.13.7: a creation verb followed closely by a file format ("make me a pdf", "turn this
 * into slides", "export it as json", "code me a website"). The older phrase list missed
 * every wording it did not spell out, such as "make me a pdf".
 */
private const val FILE_VERBS = "make|create|generate|build|produce|write|draft|prepare|design|give|send|export|" +
    "save|turn|convert|put|output|render|compile|format|code|whip up"
private const val FILE_FORMATS = "pdf|docx?|word doc(?:ument)?|word file|excel|xlsx?|spreadsheet|workbook|csv|tsv|" +
    "pptx?|powerpoint|power point|slides?|slide ?deck|pitch deck|deck|presentation|keynote|html|web ?page|website|" +
    "landing page|json|xml|ya?ml|markdown|md file|txt|text file|svg|zip|archive|ics|vcard|vcf|contact card|" +
    "sqlite|gpx|kml|latex|cheat ?sheet|one-?pager"
private val formatRequest = Regex("""\b(?:$FILE_VERBS)\b[^.?!\n]{0,60}?\b(?:$FILE_FORMATS)\b""", RegexOption.IGNORE_CASE)
private val howToQuestion = Regex(
    """^\W*(?:how|what|why|which|where|when|who|is|are|does|do|did|should i|can i|could i)\b""", RegexOption.IGNORE_CASE
)
private val addressesKryzz = Regex("""\b(?:you|kryzz)\b""", RegexOption.IGNORE_CASE)

internal fun userWantsArtifactCreated(text: String): Boolean {
    if (text.isBlank()) return false
    // "How do I make a PDF?" asks for steps, not a file; "how about you make one" still counts.
    if (howToQuestion.containsMatchIn(text) && !addressesKryzz.containsMatchIn(text)) return false
    return matchesAnyWord(text, artifactCreateKeywords) || formatRequest.containsMatchIn(text)
}

internal fun userWantsSkill(text: String): Boolean = matchesAnyWord(text, skillKeywords)

internal fun userWantsCodeProject(text: String): Boolean = matchesAnyWord(text, codeProjectKeywords)

internal fun firstHttpUrl(text: String): String? =
    httpUrl.find(text)?.value?.trimEnd('.', ',', ';', ')', ']')
