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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
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

@Composable
private fun RichInlineText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { richAnnotatedMarkdown(text, linkColor) }
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

internal fun richAnnotatedMarkdown(input: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val token = Regex(
        "\\[([^]]+)]\\((https?://[^)]+)\\)|\\*\\*([^*]+)\\*\\*|__([^_]+)__|~~([^~]+)~~|(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)|`([^`]+)`"
    )
    var cursor = 0
    token.findAll(input).forEach { match ->
        append(input.substring(cursor, match.range.first))
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
            else -> {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = linkColor.copy(alpha = 0.10f)
                    )
                )
                append(match.groupValues[7])
                pop()
            }
        }
        cursor = match.range.last + 1
    }
    append(input.substring(cursor))
}
