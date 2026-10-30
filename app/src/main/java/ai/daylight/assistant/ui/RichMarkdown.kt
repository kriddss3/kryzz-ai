package ai.daylight.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import ai.daylight.assistant.ui.dot.DataMonoFamily
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.effects.DotSteps
import ai.daylight.assistant.ui.dot.effects.rememberDotBlink
import ai.daylight.assistant.ui.talk.citationSuperscriptStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class ListItems(val items: List<Pair<String, String>>) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    fun isTableSeparator(value: String): Boolean {
        val cells = value.trim().trim('|').split('|').map(String::trim)
        return cells.isNotEmpty() && cells.all { it.matches(Regex(":?-{3,}:?")) }
    }
    fun tableCells(value: String): List<String> = value.trim().trim('|').split('|').map(String::trim)
    fun listItem(value: String): Pair<String, String>? {
        val match = Regex("^\\s*([-*+] |(\\d+)[.)] )(.*)$").find(value) ?: return null
        val ordered = match.groupValues[2]
        val marker = if (ordered.isNotEmpty()) "$ordered." else "•"
        return marker to match.groupValues[3].trim()
    }
    fun startsSpecial(position: Int): Boolean {
        if (position !in lines.indices) return false
        val line = lines[position]
        val trimmed = line.trim()
        return trimmed.isBlank() ||
            Regex("^#{1,3}\\s+").containsMatchIn(trimmed) ||
            trimmed == "---" || trimmed == "***" ||
            trimmed.startsWith(">") || listItem(line) != null ||
            (position + 1 < lines.size && line.contains('|') && isTableSeparator(lines[position + 1]))
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.isBlank()) {
            index++
            continue
        }

        val heading = Regex("^(#{1,3})\\s+(.+)$").find(trimmed)
        if (heading != null) {
            blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            index++
            continue
        }

        if (trimmed == "---" || trimmed == "***") {
            blocks += MarkdownBlock.Rule
            index++
            continue
        }

        if (trimmed.startsWith(">")) {
            val quote = mutableListOf<String>()
            while (index < lines.size && lines[index].trim().startsWith(">")) {
                quote += lines[index].trim().removePrefix(">").trimStart()
                index++
            }
            blocks += MarkdownBlock.Quote(quote.joinToString("\n"))
            continue
        }

        val firstListItem = listItem(line)
        if (firstListItem != null) {
            val items = mutableListOf<Pair<String, String>>()
            while (index < lines.size) {
                val item = listItem(lines[index]) ?: break
                items += item
                index++
            }
            blocks += MarkdownBlock.ListItems(items)
            continue
        }

        if (index + 1 < lines.size && line.contains('|') && isTableSeparator(lines[index + 1])) {
            val header = tableCells(line)
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                rows += tableCells(lines[index])
                index++
            }
            blocks += MarkdownBlock.Table(header, rows)
            continue
        }

        val paragraph = mutableListOf(trimmed)
        index++
        while (index < lines.size && !startsSpecial(index)) {
            paragraph += lines[index].trim()
            index++
        }
        blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
    }
    return blocks
}

@Composable
internal fun RichMarkdownBody(markdown: String, contentColor: Color) {
    val blocks = parseMarkdownBlocks(markdown)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.Heading -> RichInlineText(
                    text = block.text,
                    color = contentColor,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp)
                )
                is MarkdownBlock.Paragraph -> RichInlineText(
                    text = block.text,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                is MarkdownBlock.Quote -> MarkdownQuote(block.text, contentColor)
                is MarkdownBlock.ListItems -> MarkdownList(block.items, contentColor)
                is MarkdownBlock.Table -> MarkdownTable(block, contentColor)
                MarkdownBlock.Rule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun MarkdownQuote(text: String, contentColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
            .padding(end = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(3.dp)
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f))
        )
        RichInlineText(
            text = text,
            color = contentColor.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun MarkdownList(items: List<Pair<String, String>>, contentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items.forEach { (marker, text) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    marker,
                    modifier = Modifier.width(30.dp).padding(top = 1.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                RichInlineText(
                    text = text,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table, contentColor: Color) {
    val columnCount = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f))
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            TableRow(table.header, columnCount, contentColor, header = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f))
            table.rows.forEachIndexed { index, row ->
                TableRow(row, columnCount, contentColor, header = false)
                if (index != table.rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, columnCount: Int, contentColor: Color, header: Boolean) {
    Row {
        repeat(columnCount) { index ->
            RichInlineText(
                text = cells.getOrNull(index).orEmpty(),
                color = if (header) MaterialTheme.colorScheme.onSurface else contentColor,
                style = if (header) {
                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                modifier = Modifier.width(156.dp).padding(horizontal = 12.dp, vertical = 11.dp)
            )
        }
    }
}

/**
 * Talk provides the current answer's citation URLs here, so "[1]" in the answer renders as
 * a yellow superscript that opens source 1. Empty (the default) keeps "[1]" as plain text,
 * which is how every other screen still renders it.
 */
val LocalCitationUrls = staticCompositionLocalOf<List<String>> { emptyList() }

/** Appended to a streaming answer by Talk; drawn as the yellow block caret. */
val STREAMING_CARET: String = Char(0xE000).toString()

private val CITATION_MARK = Regex("\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)](?!\\()")

/** The citation numbers an answer actually uses, e.g. "x [1], y [2, 4]" -> {1, 2, 4}. */
internal fun citedNumbers(markdown: String): Set<Int> =
    CITATION_MARK.findAll(markdown)
        .flatMap { match -> match.groupValues[1].split(',').mapNotNull { it.trim().toIntOrNull() } }
        .toSet()

@Composable
private fun RichInlineText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val citationUrls = LocalCitationUrls.current
    val accent = DotTheme.c.accentText
    // Phase D: the caret blinks in hard steps. Only the paragraph holding it blinks, so a
    // long answer is not rebuilt on every blink.
    val caretOn = if (STREAMING_CARET in text) {
        rememberDotBlink(onMs = DotSteps.CARET_ON, offMs = DotSteps.CARET_OFF).value
    } else true
    val caretColor = if (caretOn) accent else Color.Transparent
    val annotated = remember(text, linkColor, citationUrls, accent, caretColor) {
        richAnnotatedMarkdown(text, linkColor, DataMonoFamily, citationUrls, accent, caretColor)
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.merge(TextStyle(color = color)),
        onClick = { offset -> openAnnotatedUrl(context, annotated, offset) }
    )
}

private fun openAnnotatedUrl(context: Context, text: AnnotatedString, offset: Int) {
    text.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item))) }
    }
}

internal fun richAnnotatedMarkdown(
    input: String,
    linkColor: Color,
    codeFontFamily: FontFamily = FontFamily.Monospace,
    citationUrls: List<String> = emptyList(),
    accent: Color = linkColor,
    caretColor: Color = accent
): AnnotatedString = buildAnnotatedString {
    val token = Regex(
        "\\[([^]]+)]\\((https?://[^)]+)\\)|\\*\\*([^*]+)\\*\\*|__([^_]+)__|~~([^~]+)~~|(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)|`([^`]+)`|" +
            CITATION_MARK.pattern
    )
    fun appendPlain(text: String) {
        // Talk's streaming caret: a short yellow block where the text currently ends.
        val parts = text.split(STREAMING_CARET)
        parts.forEachIndexed { index, part ->
            append(part)
            if (index < parts.lastIndex) {
                append("\u00A0")
                withStyle(SpanStyle(background = caretColor)) { append("\u00A0\u00A0") }
            }
        }
    }
    fun convertsToCitation(match: MatchResult): Boolean {
        if (match.groupValues[8].isEmpty() || citationUrls.isEmpty()) return false
        val numbers = match.groupValues[8].split(',').mapNotNull { it.trim().toIntOrNull() }
        return numbers.isNotEmpty() && numbers.all { it in 1..citationUrls.size }
    }
    var cursor = 0
    token.findAll(input).forEach { match ->
        val before = input.substring(cursor, match.range.first)
        // "lowercase [1]," reads as "lowercase¹,": the numeral sits right against the word.
        appendPlain(if (convertsToCitation(match)) before.removeSuffix(" ") else before)
        when {
            match.groupValues[1].isNotEmpty() -> {
                pushStringAnnotation("URL", match.groupValues[2])
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                append(match.groupValues[1])
                pop()
                pop()
            }
            match.groupValues[3].isNotEmpty() -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(match.groupValues[3]); pop()
            }
            match.groupValues[4].isNotEmpty() -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(match.groupValues[4]); pop()
            }
            match.groupValues[5].isNotEmpty() -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)); append(match.groupValues[5]); pop()
            }
            match.groupValues[6].isNotEmpty() -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(match.groupValues[6]); pop()
            }
            match.groupValues[7].isNotEmpty() -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = codeFontFamily,
                        background = linkColor.copy(alpha = 0.10f)
                    )
                )
                append(match.groupValues[7])
                pop()
            }
            else -> {
                // "[1]" or "[1, 3]": yellow superscript numerals that open their source.
                // Without citations (or with a number out of range) it stays plain text.
                if (!convertsToCitation(match)) {
                    appendPlain(match.value)
                } else {
                    val numbers = match.groupValues[8].split(',').map { it.trim().toInt() }
                    numbers.forEachIndexed { index, number ->
                        if (index > 0) withStyle(citationSuperscriptStyle(accent)) { append(",") }
                        pushStringAnnotation("URL", citationUrls[number - 1])
                        withStyle(citationSuperscriptStyle(accent)) { append(number.toString()) }
                        pop()
                    }
                }
            }
        }
        cursor = match.range.last + 1
    }
    appendPlain(input.substring(cursor))
}
