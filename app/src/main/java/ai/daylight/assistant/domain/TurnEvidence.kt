package ai.daylight.assistant.domain

/**
 * What a past assistant reply was built on, re-attached when that reply is replayed as
 * history. Tool rows are not replayed (their raw JSON would flood the context), so before
 * v5.8 a follow-up such as "open source 3" or "add a column to that spreadsheet" reached
 * the model with the citation list and file names already gone.
 */
internal object TurnEvidence {
    const val MAX_SOURCES = 10
    /** Only the most recent replies carry their evidence, which bounds prompt growth. */
    const val MAX_ANNOTATED_REPLIES = 4

    private const val MAX_TITLE_CHARS = 100
    private const val MAX_URL_CHARS = 300

    fun annotate(content: String, citations: List<Citation>, outputs: List<GeneratedOutput>): String {
        if (citations.isEmpty() && outputs.isEmpty()) return content
        return buildString {
            append(content.trimEnd())
            if (citations.isNotEmpty()) {
                append("\n\n(Sources behind this reply, numbered as cited:")
                citations.take(MAX_SOURCES).forEachIndexed { index, citation ->
                    append("\n[").append(index + 1).append("] ")
                    append(citation.title.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE_CHARS))
                    append(": ").append(citation.url.take(MAX_URL_CHARS))
                }
                if (citations.size > MAX_SOURCES) append("\n… and ${citations.size - MAX_SOURCES} more")
                append(")")
            }
            if (outputs.isNotEmpty()) {
                append("\n\n(Files attached to this reply: ")
                append(outputs.joinToString("; ") { "${it.title.take(MAX_TITLE_CHARS)} (${it.kind.name.lowercase()}, ${it.fileName})" })
                append(")")
            }
        }
    }
}
