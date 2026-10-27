package ai.daylight.assistant.ui.talk

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.Citation
import ai.daylight.assistant.domain.WorkLog
import ai.daylight.assistant.domain.WorkStepKind
import ai.daylight.assistant.ui.dot.DotPalette
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.MonoFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A source (or other step) behind the current answer. */
enum class SourceState { READ, LIVE, QUEUED }

data class ReadoutItem(
    /** Domain for pages, or a short kind word ("search", "weather") for other steps. */
    val label: String,
    val title: String,
    val url: String? = null,
    val state: SourceState = SourceState.READ,
    /** The citation number the answer uses for this source ([1] -> 1), if any. */
    val cite: Int? = null,
)

/** The whole search UI for one answer: one LED segment per item plus one plain sentence. */
data class Readout(val items: List<ReadoutItem>, val caption: String) {
    val total: Int get() = items.size
    val done: Int get() = items.count { it.state == SourceState.READ }
    val live: ReadoutItem? get() = items.firstOrNull { it.state == SourceState.LIVE }
    val countLabel: String get() = "$done/$total"
}

object SourceReadoutBuilder {
    /** "https://www.fontsource.org/x" -> "fontsource.org". */
    fun host(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val raw = runCatching { java.net.URI(url.trim()).host }.getOrNull()
            ?: url.substringAfter("://").substringBefore('/').substringBefore('?')
        return raw.orEmpty().removePrefix("www.").lowercase()
    }

    private fun sameUrl(a: String, b: String) = a.trim().trimEnd('/') == b.trim().trimEnd('/')

    /**
     * A live activity chip as a readout item. "Reading: rtings.com/laptop/x" reads a page,
     * "Searching: pixel 10" runs a search, anything else is shown by its own words.
     */
    fun liveItem(activity: String): ReadoutItem? {
        val clean = activity.trim().trimEnd('…', '.').trim()
        if (clean.isEmpty()) return null
        val verb = clean.substringBefore(':').trim()
        val detail = clean.substringAfter(':', "").trim()
        return when {
            verb.equals("Reading", ignoreCase = true) && detail.isNotEmpty() -> {
                val url = if ("://" in detail) detail else "https://$detail"
                ReadoutItem(label = host(url).ifEmpty { detail }, title = detail, url = null, state = SourceState.LIVE)
            }
            verb.equals("Searching", ignoreCase = true) && detail.isNotEmpty() ->
                ReadoutItem(label = "search", title = detail, state = SourceState.LIVE)
            else -> ReadoutItem(label = clean.substringBefore(' ').lowercase(), title = clean, state = SourceState.LIVE)
        }
    }

    /** The plain sentence under the bar while something is live: "Reading fontsource.org". */
    fun liveCaption(item: ReadoutItem): String = when (item.label) {
        "search" -> "Searching ${item.title}"
        else -> if (item.title.contains(':') || item.label.contains('.')) "Reading ${item.label}" else item.title
    }

    /**
     * Builds the readout from what the app really knows: the answer's citations (numbered as
     * the answer cites them), pages and other tool steps from its work log, and, while it
     * streams, the live activity chips. Returns null when the answer used no sources or tools.
     */
    fun build(
        citations: List<Citation>,
        workLog: WorkLog?,
        activities: List<String> = emptyList(),
        streaming: Boolean = false,
    ): Readout? {
        val items = mutableListOf<ReadoutItem>()
        citations.forEachIndexed { index, c ->
            val host = host(c.url)
            items += ReadoutItem(label = host.ifEmpty { "source" }, title = c.title.ifBlank { host }, url = c.url, cite = index + 1)
        }
        val steps = workLog?.steps.orEmpty()
        steps.filter { it.kind == WorkStepKind.PAGE && !it.url.isNullOrBlank() }.forEach { step ->
            val url = step.url!!
            if (items.none { it.url != null && sameUrl(it.url, url) }) {
                items += ReadoutItem(label = host(url), title = step.title.ifBlank { host(url) }, url = url)
            }
        }
        val hasSources = items.isNotEmpty()
        steps.filter { it.kind != WorkStepKind.PAGE && (it.kind != WorkStepKind.SEARCH || !hasSources) }.forEach { step ->
            val title = if (step.failed) "${step.title} (failed)" else step.title
            items += ReadoutItem(label = step.kind.name.lowercase().replace('_', ' '), title = title, url = step.url)
        }
        if (streaming) activities.mapNotNullTo(items, ::liveItem)
        if (items.isEmpty()) return null
        val live = items.firstOrNull { it.state == SourceState.LIVE }
        val caption = when {
            live != null -> liveCaption(live)
            workLog != null && workLog.summary.isNotBlank() -> workLog.summary.replaceFirstChar { it.uppercaseChar() }
            else -> "Sources"
        }
        return Readout(items, caption)
    }
}

/** Readout geometry from SCREENS.md section 2. */
private object ReadoutSpec {
    val Pitch = 5.dp
    const val DOT_FRACTION = 0.68f // r = 1.7dp
    const val SEGMENT = 12
    const val GAP = 2
    const val LIVE_LIT = 7 // static in C1; the stepped fill arrives with phase D
    val MiniWidth = 62.dp
}

private fun DotPalette.segmentColor(state: SourceState, dot: Int): Color = when (state) {
    SourceState.READ -> textPrimary
    SourceState.LIVE -> if (dot < ReadoutSpec.LIVE_LIT) accent else borderStrong
    SourceState.QUEUED -> border
}

/** Two rows of dots: one segment per item, segments shrink to fit the width. */
private fun DrawScope.drawSegments(states: List<SourceState>, palette: DotPalette) {
    if (states.isEmpty()) return
    val p = ReadoutSpec.Pitch.toPx()
    val cols = (size.width / p).toInt()
    val n = states.size
    val seg = ((cols - ReadoutSpec.GAP * (n - 1)) / n).coerceIn(1, ReadoutSpec.SEGMENT)
    val d = p * ReadoutSpec.DOT_FRACTION
    val byColor = LinkedHashMap<Color, MutableList<Offset>>()
    var col = 0
    for (state in states) {
        for (j in 0 until seg) {
            if (col >= cols) break
            val color = palette.segmentColor(state, j * ReadoutSpec.SEGMENT / seg)
            val list = byColor.getOrPut(color) { mutableListOf() }
            for (row in 0..1) list += Offset(col * p + p / 2f, row * p + p / 2f)
            col++
        }
        col += ReadoutSpec.GAP
    }
    byColor.forEach { (color, points) -> drawPoints(points, PointMode.Points, color, strokeWidth = d, cap = StrokeCap.Round) }
}

@Composable
private fun SegmentBar(states: List<SourceState>, modifier: Modifier = Modifier, width: Dp? = null) {
    val c = DotTheme.c
    val m = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Canvas(m.height(ReadoutSpec.Pitch * 2)) { drawSegments(states, c) }
}

/**
 * The source readout: one LED line with a segment per source, a sentence saying what is
 * happening and the count. Tap it to unroll the source list; the caller folds the answer
 * while it is open. Tapping a source opens it in the browser.
 */
@Composable
fun SourceReadout(
    readout: Readout,
    expanded: Boolean,
    onToggle: () -> Unit,
    citedNumbers: Set<Int>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
) {
    val c = DotTheme.c
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = if (expanded) "Hide sources" else "Show sources", onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "${readout.caption}. ${readout.done} of ${readout.total} sources"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            verticalArrangement = Arrangement.Center
        ) {
            SegmentBar(readout.items.map { it.state })
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expanded) "Sources" else readout.caption,
                    Modifier.weight(1f),
                    style = DotType.Body.copy(fontSize = 14.sp),
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(readout.countLabel, style = DotType.Data.copy(fontSize = 13.sp), color = c.textTertiary)
                if (expanded) {
                    Spacer(Modifier.width(6.dp))
                    Icon(painterResource(R.drawable.ic_kz_chevron_up), null, Modifier.size(12.dp), tint = c.textTertiary)
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (motionEnabled) expandVertically(tween(220)) + fadeIn(tween(140)) else fadeIn(tween(0)),
            exit = if (motionEnabled) shrinkVertically(tween(180)) + fadeOut(tween(80)) else fadeOut(tween(0))
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                readout.items.forEach { item -> SourceRow(item, item.cite?.takeIf { it in citedNumbers }, onOpenUrl) }
            }
        }
    }
}

@Composable
private fun SourceRow(item: ReadoutItem, cite: Int?, onOpenUrl: (String) -> Unit) {
    val c = DotTheme.c
    val url = item.url
    val labelColor = when (item.state) {
        SourceState.READ -> c.textTertiary
        SourceState.LIVE -> c.accent
        SourceState.QUEUED -> c.textDisabled
    }
    val titleColor = if (item.state == SourceState.QUEUED) c.textDisabled else c.textPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (url != null) Modifier.clickable(onClickLabel = "Open source") { onOpenUrl(url) } else Modifier)
            .padding(vertical = 9.dp)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(item.title, item.label, cite?.let { "cited as $it" }).joinToString(", ")
                if (url != null) role = Role.Button
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SegmentBar(listOf(item.state), Modifier.padding(top = 5.dp), width = ReadoutSpec.MiniWidth)
        Column(Modifier.weight(1f)) {
            Text(item.label, style = DotType.Data, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.title.isNotBlank() && item.title != item.label) {
                Text(
                    buildAnnotatedString {
                        append(item.title)
                        if (cite != null) {
                            withStyle(citationSuperscriptStyle(c.accent)) { append(cite.toString()) }
                        }
                    },
                    Modifier.padding(top = 2.dp),
                    style = DotType.Body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Citation numerals: Space Mono Bold 11, yellow, raised (SCREENS.md section 2). */
fun citationSuperscriptStyle(accent: Color): SpanStyle = SpanStyle(
    color = accent,
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    baselineShift = BaselineShift.Superscript,
)

/** Opens a URL in the browser, ignoring malformed ones. */
internal fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
