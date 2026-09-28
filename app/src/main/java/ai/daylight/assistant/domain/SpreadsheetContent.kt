package ai.daylight.assistant.domain

import java.math.BigDecimal

/**
 * v5.11: how spreadsheet artifact content (CSV) becomes typed XLSX sheets. Pure, so the
 * XLSX writer, ArtifactKinds and tests share one reading of the content.
 *
 * Several sheets: a line `### Sheet: Name` starts a sheet (one to three #, any case). Text
 * before the first marker, or content with no marker at all, is one sheet named
 * [DEFAULT_SHEET_NAME].
 */
object SpreadsheetContent {

    const val DEFAULT_SHEET_NAME = "Kryzz AI"
    const val MAX_SHEETS = 32

    data class Sheet(val name: String, val rows: List<List<String>>)

    /** A section of the raw content: the marker's sheet name (null before any marker) and its CSV. */
    data class Section(val name: String?, val body: String)

    private val sheetMarker = Regex("^\\s*#{1,3}\\s*sheet\\s*:\\s*(.+?)\\s*$", RegexOption.IGNORE_CASE)

    /**
     * Splits [content] at sheet marker lines. Lines inside a quoted CSV field (a quote opened
     * on an earlier line) are never read as markers.
     */
    fun sections(content: String): List<Section> {
        val sections = mutableListOf<Section>()
        var name: String? = null
        val body = StringBuilder()
        var quoted = false
        fun flush() {
            if (name != null || body.isNotBlank()) sections += Section(name, body.toString().trim('\n', '\r'))
            body.clear()
        }
        content.replace("\r\n", "\n").split('\n').forEach { line ->
            val marker = if (!quoted) sheetMarker.find(line) else null
            if (marker != null) {
                flush()
                name = marker.groupValues[1]
            } else {
                body.append(line).append('\n')
                if (line.count { it == '"' } % 2 == 1) quoted = !quoted
            }
        }
        flush()
        return sections
    }

    /** Applies [transform] to each section's body and joins the content back with its markers. */
    fun mapSections(content: String, transform: (String) -> String): String =
        sections(content).joinToString("\n") { section ->
            val body = transform(section.body)
            if (section.name == null) body else "### Sheet: ${section.name}\n$body"
        }

    /** The sheets of [content], with valid unique names and at least one row each. */
    fun sheets(content: String): List<Sheet> {
        val sheets = mutableListOf<Sheet>()
        val used = mutableSetOf<String>()
        sections(content).forEach { section ->
            val rows = parseCsv(section.body)
            // A marker with nothing under it still yields a (blank) sheet; stray blank text
            // before the first marker does not.
            if (section.name == null && rows.isEmpty()) return@forEach
            if (sheets.size >= MAX_SHEETS) return@forEach
            val name = uniqueName(sanitizeSheetName(section.name ?: DEFAULT_SHEET_NAME), used)
            used += name.lowercase()
            sheets += Sheet(name, rows.ifEmpty { listOf(listOf("")) })
        }
        return sheets.ifEmpty { listOf(Sheet(DEFAULT_SHEET_NAME, listOf(listOf(DEFAULT_SHEET_NAME)))) }
    }

    /** Excel sheet names: 1..31 chars, none of \ / ? * [ ] :, no leading or trailing quote. */
    fun sanitizeSheetName(raw: String): String {
        val clean = raw.replace(Regex("[\\\\/?*\\[\\]:]"), " ").replace(Regex("\\s+"), " ").trim().trim('\'').trim()
        return clean.take(31).trim().ifEmpty { DEFAULT_SHEET_NAME }
    }

    private fun uniqueName(name: String, used: Set<String>): String {
        if (name.lowercase() !in used) return name
        var counter = 2
        while (true) {
            val suffix = " ($counter)"
            val candidate = name.take(31 - suffix.length).trimEnd() + suffix
            if (candidate.lowercase() !in used) return candidate
            counter++
        }
    }

    /** RFC 4180-style CSV: quoted fields may hold commas, doubled quotes and line breaks. */
    fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            val next = csv.getOrNull(index + 1)
            when {
                char == '"' && quoted && next == '"' -> { cell.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { row += cell.toString(); cell.clear() }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && next == '\n') index++
                    row += cell.toString(); cell.clear()
                    rows += row; row = mutableListOf()
                }
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        // Blank lines are not rows.
        return rows.filterNot { cells -> cells.all { it.isEmpty() } && cells.size <= 1 }
    }

    // ── Cell types ──────────────────────────────────────────────────────────

    enum class NumberFormat { GENERAL, THOUSANDS, THOUSANDS_DECIMAL, PERCENT, PERCENT_DECIMAL }

    sealed interface Cell {
        data object Empty : Cell
        data class Text(val value: String) : Cell
        /** [value] is a plain decimal string (no grouping, no exponent loss), ready for XML. */
        data class Number(val value: String, val format: NumberFormat = NumberFormat.GENERAL) : Cell
        data class Bool(val value: Boolean) : Cell
        /** [expression] without the leading '='. */
        data class Formula(val expression: String) : Cell
    }

    private val plainNumber = Regex("^-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d{1,3})?$")
    private val groupedNumber = Regex("^-?[1-9]\\d{0,2}(,\\d{3})+(\\.\\d+)?$")
    private val percent = Regex("^(-?(0|[1-9]\\d*)(\\.\\d+)?)\\s?%$")

    /** Numbers longer than this many digits keep full precision only as text (IDs, card numbers). */
    private const val MAX_NUMBER_DIGITS = 15

    /**
     * Types one CSV value. Strict numbers become numeric; values with leading zeros ("007"),
     * a plus sign or separators ("+371 2000 0000", "555-0100") stay text so nothing is lost.
     */
    fun classify(raw: String): Cell {
        val value = raw.trim()
        if (value.isEmpty()) return Cell.Empty
        if (value.length > 1 && value.startsWith("=")) return Cell.Formula(value.substring(1))
        if (value.equals("true", ignoreCase = true)) return Cell.Bool(true)
        if (value.equals("false", ignoreCase = true)) return Cell.Bool(false)
        if (plainNumber.matches(value) && digitCount(value) <= MAX_NUMBER_DIGITS) {
            return Cell.Number(value)
        }
        if (groupedNumber.matches(value)) {
            val plain = value.replace(",", "")
            if (digitCount(plain) <= MAX_NUMBER_DIGITS) {
                return Cell.Number(plain, if ('.' in plain) NumberFormat.THOUSANDS_DECIMAL else NumberFormat.THOUSANDS)
            }
        }
        percent.find(value)?.let { match ->
            val number = match.groupValues[1]
            if (digitCount(number) <= MAX_NUMBER_DIGITS - 2) {
                val fraction = BigDecimal(number).movePointLeft(2).stripTrailingZeros().toPlainString()
                return Cell.Number(fraction, if ('.' in number) NumberFormat.PERCENT_DECIMAL else NumberFormat.PERCENT)
            }
        }
        return Cell.Text(raw)
    }

    private fun digitCount(value: String): Int = value.substringBefore('e').substringBefore('E').count(Char::isDigit)

    // ── Column widths ───────────────────────────────────────────────────────

    /** Approximate Excel column widths (characters) from the longest value in each column. */
    fun columnWidths(rows: List<List<String>>): List<Double> {
        val count = rows.maxOfOrNull { it.size } ?: 0
        return List(count) { column ->
            val longest = rows.maxOf { row ->
                val value = row.getOrNull(column).orEmpty()
                val lines = value.split('\n')
                if (value.startsWith("=")) 10 else lines.maxOf { it.length }
            }
            (longest * 1.1 + 2.0).coerceIn(8.0, 60.0)
        }
    }
}
