package ai.daylight.assistant.domain

/**
 * v5.11: the block model behind every generated document (DOCX, PDF and the fallback PDF
 * writer). create_artifact hands over Markdown; this turns it into headings, paragraphs
 * with styled inline runs, lists, tables, code blocks, quotes and rules so each renderer
 * only decides how a block looks.
 *
 * ui/RichMarkdown.kt keeps its own lighter parser for chat bubbles (it hands raw inline
 * text to Compose). This one is stricter and richer: fenced code, one level of nested
 * lists, ordered list start numbers and parsed inline runs. Pure Kotlin, no Android.
 */
object DocumentMarkdown {

    /** A piece of inline text with one combination of styles. [link] is an http(s) URL. */
    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
        val link: String? = null
    ) {
        fun sameStyle(other: Run) = bold == other.bold && italic == other.italic &&
            code == other.code && strike == other.strike && link == other.link
    }

    sealed interface Block

    /** [level] is 1..3; Markdown headings at levels 4-6 are folded into level 3. */
    data class Heading(val level: Int, val runs: List<Run>) : Block
    data class Paragraph(val runs: List<Run>) : Block
    data class Quote(val paragraphs: List<List<Run>>) : Block
    data class CodeBlock(val language: String?, val text: String) : Block
    data object Rule : Block

    /** A list; [start] is the first number of an ordered list. */
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<ListItem>) : Block

    /** One list entry; [children] is at most one nested list (one nesting level). */
    data class ListItem(val runs: List<Run>, val children: ListBlock? = null)

    /** Header and body cells, every row padded or trimmed to the header's column count. */
    data class Table(val header: List<List<Run>>, val rows: List<List<List<Run>>>) : Block {
        val columnCount: Int get() = header.size
    }

    fun parse(markdown: String): List<Block> = BlockParser(markdown.replace("\r\n", "\n").replace('\r', '\n').lines()).parse()

    /** Plain text of [runs] (used for measuring, fallbacks and table widths). */
    fun plain(runs: List<Run>): String = runs.joinToString("") { it.text }

    // ── Block level ─────────────────────────────────────────────────────────

    private val headingPattern = Regex("^(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$")
    private val fencePattern = Regex("^(\\s{0,3})(`{3,}|~{3,})\\s*([^`\\s]*)")
    private val rulePattern = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val listPattern = Regex("^(\\s*)([-*+]|(\\d{1,9})[.)])\\s+(.*)$")
    private val separatorCell = Regex("^:?-{1,}:?$")

    private data class ListLine(val indent: Int, val ordered: Boolean, val number: Int, val text: String)

    private fun listLine(line: String): ListLine? {
        val match = listPattern.find(line) ?: return null
        val indent = match.groupValues[1].replace("\t", "    ").length
        val number = match.groupValues[3]
        return ListLine(indent, number.isNotEmpty(), number.toIntOrNull() ?: 1, match.groupValues[4])
    }

    private fun isTableSeparator(line: String): Boolean {
        if (!line.contains('-')) return false
        val cells = splitTableRow(line)
        return cells.isNotEmpty() && cells.all { separatorCell.matches(it) }
    }

    /** Splits `| a | b |` into cells; `\|` stays a literal pipe inside a cell. */
    internal fun splitTableRow(line: String): List<String> {
        var body = line.trim()
        if (body.startsWith("|")) body = body.substring(1)
        if (body.endsWith("|") && !body.endsWith("\\|")) body = body.substring(0, body.length - 1)
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var index = 0
        while (index < body.length) {
            val char = body[index]
            when {
                char == '\\' && body.getOrNull(index + 1) == '|' -> { cell.append('|'); index++ }
                char == '|' -> { cells += cell.toString().trim(); cell.clear() }
                else -> cell.append(char)
            }
            index++
        }
        cells += cell.toString().trim()
        return cells
    }

    private class BlockParser(private val lines: List<String>) {
        private var index = 0
        private val blocks = mutableListOf<Block>()

        fun parse(): List<Block> {
            while (index < lines.size) {
                val line = lines[index]
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> index++
                    fencePattern.containsMatchIn(line) -> blocks += codeBlock()
                    headingPattern.matches(trimmed) -> {
                        val match = headingPattern.find(trimmed)!!
                        blocks += Heading(match.groupValues[1].length.coerceAtMost(3), inline(match.groupValues[2]))
                        index++
                    }
                    rulePattern.matches(line) -> { blocks += Rule; index++ }
                    trimmed.startsWith(">") -> blocks += quote()
                    listLine(line) != null -> blocks += list()
                    startsTable(index) -> blocks += table()
                    else -> blocks += paragraph()
                }
            }
            return blocks
        }

        private fun startsTable(position: Int): Boolean =
            position + 1 < lines.size && lines[position].contains('|') && isTableSeparator(lines[position + 1])

        /**
         * True when [position] begins a block other than a paragraph continuation. As in
         * CommonMark, only a numbered list starting at 1 interrupts a paragraph, so a wrapped
         * line such as "2026. gada budžets" stays text.
         */
        private fun startsBlock(position: Int): Boolean {
            val line = lines[position]
            val trimmed = line.trim()
            val list = listLine(line)
            return trimmed.isEmpty() || fencePattern.containsMatchIn(line) || headingPattern.matches(trimmed) ||
                rulePattern.matches(line) || trimmed.startsWith(">") || startsTable(position) ||
                (list != null && (!list.ordered || list.number == 1))
        }

        private fun codeBlock(): CodeBlock {
            val open = fencePattern.find(lines[index])!!
            val fence = open.groupValues[2]
            val language = open.groupValues[3].trim().takeIf { it.isNotEmpty() }
            index++
            val body = mutableListOf<String>()
            while (index < lines.size) {
                val line = lines[index]
                if (line.trim().startsWith(fence.substring(0, 3)) && line.trim().all { it == fence[0] }) {
                    index++
                    break
                }
                body += line
                index++
            }
            return CodeBlock(language, body.joinToString("\n").trimEnd())
        }

        private fun quote(): Quote {
            val paragraphs = mutableListOf<List<Run>>()
            val current = mutableListOf<String>()
            fun flush() {
                if (current.isNotEmpty()) paragraphs += inline(current.joinToString("\n"))
                current.clear()
            }
            while (index < lines.size && lines[index].trim().startsWith(">")) {
                val text = lines[index].trim().removePrefix(">").removePrefix(" ")
                if (text.isBlank()) flush() else current += text.trim()
                index++
            }
            flush()
            return Quote(paragraphs.ifEmpty { listOf(emptyList()) })
        }

        private fun table(): Table {
            val header = splitTableRow(lines[index])
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                rows += splitTableRow(lines[index])
                index++
            }
            val width = header.size
            fun fit(cells: List<String>) = List(width) { column -> inline(cells.getOrElse(column) { "" }) }
            return Table(header.map(::inline), rows.map(::fit))
        }

        private fun paragraph(): Paragraph {
            val text = mutableListOf(lines[index].trim())
            index++
            while (index < lines.size && !startsBlock(index)) {
                text += lines[index].trim()
                index++
            }
            return Paragraph(inline(text.joinToString("\n")))
        }

        /**
         * Reads a list starting at [index]. Items indented two or more spaces past the first
         * item become the nested list of the item above them; further levels are
         * flattened into that one nested level. A blank line ends the list unless another
         * item of the same kind follows it. A switch between bullets and numbers at the top
         * level starts a new list.
         */
        private fun list(): ListBlock {
            val first = listLine(lines[index])!!
            val baseIndent = first.indent
            val items = mutableListOf<ListItem>()
            var itemText = StringBuilder()
            var children = mutableListOf<Pair<ListLine, StringBuilder>>()

            fun childBlock(): ListBlock? {
                if (children.isEmpty()) return null
                val ordered = children.first().first.ordered
                return ListBlock(
                    ordered,
                    children.first().first.number,
                    children.map { (_, text) -> ListItem(inline(itemTextOf(text))) }
                )
            }
            fun flushItem() {
                items += ListItem(inline(itemTextOf(itemText)), childBlock())
                itemText = StringBuilder()
                children = mutableListOf()
            }

            itemText.append(first.text)
            index++
            while (index < lines.size) {
                val line = lines[index]
                if (line.isBlank()) {
                    val next = (index + 1 until lines.size).firstOrNull { lines[it].isNotBlank() } ?: break
                    val nextItem = listLine(lines[next]) ?: break
                    if (nextItem.indent < baseIndent + 2 && nextItem.ordered != first.ordered) break
                    index = next
                    continue
                }
                val item = listLine(line)
                when {
                    item != null && item.indent >= baseIndent + 2 -> {
                        children += item to StringBuilder(item.text)
                    }
                    item != null -> {
                        if (item.ordered != first.ordered) break
                        flushItem()
                        itemText.append(item.text)
                    }
                    // A fence, heading, quote or table ends the list; anything else indented
                    // (or a lazy continuation line) belongs to the current item.
                    fencePattern.containsMatchIn(line) || headingPattern.matches(line.trim()) ||
                        rulePattern.matches(line) || line.trim().startsWith(">") || startsTable(index) -> break
                    else -> {
                        val target = children.lastOrNull()?.second ?: itemText
                        target.append('\n').append(line.trim())
                    }
                }
                index++
            }
            flushItem()
            return ListBlock(first.ordered, first.number, items)
        }

        private fun itemTextOf(text: StringBuilder): String {
            val value = text.toString()
            // GitHub task list markers become check boxes.
            return when {
                value.startsWith("[ ] ") -> "☐ " + value.substring(4)
                value.startsWith("[x] ", ignoreCase = true) -> "☑ " + value.substring(4)
                else -> value
            }
        }
    }

    // ── Inline level ────────────────────────────────────────────────────────

    private val linkPattern = Regex("^\\[((?:[^\\[\\]\\\\]|\\\\.)*)]\\(\\s*<?(https?://[^\\s)>]+|mailto:[^\\s)>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)")
    private val autoLinkPattern = Regex("^https?://[^\\s<>()\\[\\]]+[^\\s<>()\\[\\].,;:!?'\"*_]")
    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~>"

    /** Parses inline Markdown into merged runs. Unmatched markers stay literal text. */
    fun inline(text: String): List<Run> {
        val out = mutableListOf<Run>()
        parseInline(text, Run(""), out)
        return merge(out)
    }

    private fun parseInline(text: String, style: Run, out: MutableList<Run>) {
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) out += style.copy(text = plain.toString())
            plain.clear()
        }
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val rest = text.substring(index)
            // Backslash escape.
            if (char == '\\' && index + 1 < text.length && text[index + 1] in ESCAPABLE) {
                plain.append(text[index + 1])
                index += 2
                continue
            }
            // Inline code: the run between matching backtick strings, taken literally.
            if (char == '`') {
                val ticks = rest.takeWhile { it == '`' }
                val close = text.indexOf(ticks, index + ticks.length)
                if (close >= 0) {
                    val body = text.substring(index + ticks.length, close)
                    if (body.isNotBlank()) {
                        flush()
                        out += style.copy(text = body.trim(), code = true)
                        index = close + ticks.length
                        continue
                    }
                }
            }
            // Link [label](url).
            if (char == '[' && style.link == null) {
                val match = linkPattern.find(rest)
                if (match != null && match.groupValues[1].isNotBlank()) {
                    flush()
                    parseInline(match.groupValues[1], style.copy(link = match.groupValues[2]), out)
                    index += match.value.length
                    continue
                }
            }
            // Bare URL.
            if ((char == 'h' || char == 'H') && style.link == null && (index == 0 || !text[index - 1].isLetterOrDigit())) {
                val match = autoLinkPattern.find(rest)
                if (match != null) {
                    flush()
                    out += style.copy(text = match.value, link = match.value)
                    index += match.value.length
                    continue
                }
            }
            // Emphasis: ***x***, **x**, __x__, ~~x~~, *x*, _x_.
            if (char == '*' || char == '_' || char == '~') {
                val emphasis = emphasisAt(text, index)
                if (emphasis != null) {
                    flush()
                    val (delimiter, close) = emphasis
                    val inner = text.substring(index + delimiter.length, close)
                    val innerStyle = when {
                        delimiter == "~~" -> style.copy(strike = true)
                        delimiter.length == 3 -> style.copy(bold = true, italic = true)
                        delimiter.length == 2 -> style.copy(bold = true)
                        else -> style.copy(italic = true)
                    }
                    parseInline(inner, innerStyle, out)
                    index = close + delimiter.length
                    continue
                }
            }
            plain.append(char)
            index++
        }
        flush()
    }

    /**
     * Finds an emphasis span opening at [start]: returns the delimiter and the index of its
     * closing twin, or null. Openers must be followed by non-space and closers preceded by
     * non-space; underscores only count at word boundaries (snake_case stays literal).
     */
    private fun emphasisAt(text: String, start: Int): Pair<String, Int>? {
        val char = text[start]
        val runLength = text.substring(start).takeWhile { it == char }.length
        val candidates = when (char) {
            '~' -> if (runLength >= 2) listOf("~~") else emptyList()
            else -> listOf(3, 2, 1).filter { it <= runLength }.map { char.toString().repeat(it) }
        }
        for (delimiter in candidates) {
            val afterOpen = start + delimiter.length
            if (afterOpen >= text.length || text[afterOpen].isWhitespace()) continue
            if (char == '_' && start > 0 && text[start - 1].isLetterOrDigit()) continue
            var search = afterOpen + 1
            while (search <= text.length - delimiter.length) {
                val close = text.indexOf(delimiter, search)
                if (close < 0) break
                val before = text[close - 1]
                val after = text.getOrNull(close + delimiter.length)
                val validClose = !before.isWhitespace() &&
                    (after == null || after != char) &&
                    !(char == '_' && after != null && after.isLetterOrDigit())
                if (validClose) return delimiter to close
                search = close + 1
            }
        }
        return null
    }

    private fun merge(runs: List<Run>): List<Run> {
        val merged = mutableListOf<Run>()
        runs.filter { it.text.isNotEmpty() }.forEach { run ->
            val last = merged.lastOrNull()
            if (last != null && last.sameStyle(run)) merged[merged.lastIndex] = last.copy(text = last.text + run.text)
            else merged += run
        }
        return merged
    }
}
