package ai.daylight.assistant.domain

/**
 * Artifact type acceptance and content normalisation for `create_artifact`.
 *
 * v5.7.1: models rarely use the exact canonical type strings when the user asks
 * for "an excel file" — they write "excel", "xlsx", "csv", "docx", "pdf"… The old
 * strict `OutputKind.valueOf` check rejected every one of those, the tool call
 * failed validation, the model burned tool rounds retrying, and the turn ended
 * with no file (user-visible as "the agent crashes when I ask for a spreadsheet").
 * Aliases now resolve to the same kinds, and the content is normalised before
 * generation: one wrapping code fence is stripped, and a Markdown table handed
 * over as "spreadsheet" content is converted to real CSV so the XLSX opens clean.
 */
internal object ArtifactKinds {

    /** Kinds the AUTO capability may produce through `create_artifact`. */
    val fileKinds: Set<OutputKind> = setOf(
        OutputKind.DOCUMENT,
        OutputKind.SPREADSHEET,
        OutputKind.DATABASE,
        OutputKind.PDF
    )

    private val documentAliases = setOf(
        "document", "doc", "docx", "word", "markdown", "md", "report", "text", "txt", "file"
    )
    private val spreadsheetAliases = setOf(
        "spreadsheet", "sheet", "excel", "xlsx", "xls", "csv", "table", "workbook"
    )
    private val databaseAliases = setOf(
        "database", "db", "sqlite", "sql"
    )
    private val pdfAliases = setOf(
        "pdf", "pdf file", "pdf document"
    )

    /** Maps the model's `type` argument (canonical or alias) to an [OutputKind], or null. */
    fun kindFor(rawType: String?): OutputKind? {
        val value = rawType?.trim()?.lowercase().orEmpty()
        if (value.isEmpty()) return null
        return when (value) {
            in documentAliases -> OutputKind.DOCUMENT
            in spreadsheetAliases -> OutputKind.SPREADSHEET
            in databaseAliases -> OutputKind.DATABASE
            in pdfAliases -> OutputKind.PDF
            else -> runCatching { OutputKind.valueOf(value.uppercase()) }.getOrNull()
                ?.takeIf { it in fileKinds }
        }
    }

    /**
     * Normalises model-supplied artifact content before file generation:
     * removes one wrapping Markdown code fence, and converts a Markdown table
     * to CSV when the deliverable is a spreadsheet.
     */
    fun normalizeContent(kind: OutputKind, content: String): String {
        var text = stripOuterFence(content.trim())
        if (kind == OutputKind.SPREADSHEET) {
            // v5.11: each `### Sheet: Name` section is converted on its own, so the markers
            // survive and a workbook of Markdown tables still becomes separate sheets.
            text = SpreadsheetContent.mapSections(text) { section ->
                if (looksLikeMarkdownTable(section)) markdownTableToCsv(section) else section
            }
        }
        return text
    }

    /** Removes a single ``` fence wrapper if the whole content is fenced. */
    fun stripOuterFence(text: String): String {
        if (!text.startsWith("```")) return text
        val firstNewline = text.indexOf('\n')
        if (firstNewline < 0) return text
        val closing = text.lastIndexOf("```")
        if (closing <= firstNewline) return text
        val body = text.substring(firstNewline + 1, closing)
        // Only strip when nothing but the closing fence follows the body.
        if (text.substring(closing + 3).isNotBlank()) return text
        return body.trim()
    }

    private fun looksLikeMarkdownTable(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        return lines.size >= 2 && lines.count { it.trimStart().startsWith("|") } * 2 >= lines.size
    }

    private val separatorCell = Regex("^:?-+:?$")

    /** Converts a GitHub-style Markdown table to CSV. Returns the input rows joined as CSV. */
    fun markdownTableToCsv(markdown: String): String {
        val rows = markdown.lines()
            .map { it.trim() }
            .filter { it.startsWith("|") }
            .map { line ->
                line.removePrefix("|").removeSuffix("|")
                    .split("|")
                    .map { it.trim() }
            }
            .filterNot { cells -> cells.isNotEmpty() && cells.all { separatorCell.matches(it) } }
        return rows.joinToString("\n") { cells -> cells.joinToString(",") { csvEscape(it) } }
    }

    private fun csvEscape(value: String): String {
        val clean = value.replace("**", "")
        return if (clean.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + clean.replace("\"", "\"\"") + "\""
        } else clean
    }
}
