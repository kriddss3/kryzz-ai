package ai.daylight.assistant.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotPalette
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import ai.daylight.assistant.ui.dot.effects.dotTextSpoken
import kotlinx.coroutines.delay

/** Strip geometry from SCREENS.md "Shared chrome". */
internal object InkStripSpec {
    val Height = 22.dp
    val Pitch = 2.2.dp
    const val DOT_FRACTION = 0.68f // r = 0.75dp at a 2.2dp pitch
    const val TEXT_START_COL = 4 // columns 0-1 hold the live mark, 2-3 are a gap
    val LIVE_COLS = 0..1
    val LIVE_ROWS = 2..4
    const val FLASH_MS = 1600L
}

/** What TalkBack says a tap on the strip does. */
private const val INK_STRIP_CLICK_LABEL = "Open the Inkwell"

/** Unlit strip dots sit one step above the background (#151515 snapped to surface). */
internal fun inkUnlitColor(c: DotPalette): Color = if (c.dark) c.surface else c.surfaceRaised

/** How many characters fit on a strip [cols] dots wide. */
internal fun inkStripCapacity(cols: Int): Int =
    // Text runs from TEXT_START_COL to cols - 2 (the last column stays dark); n characters
    // take n * 6 - 1 columns.
    ((cols - InkStripSpec.TEXT_START_COL) / DotFont5x7.CELL_COLS).coerceAtLeast(0)

/**
 * The LED ink strip: one line of 5x7 dot text on a field of unlit dots, under the status
 * bar on every screen. A two-dot yellow mark at the start means something is live, and a
 * tap opens the Inkwell (C10).
 * The text is static in this phase; the column wipe and the ticker arrive in phase D.
 */
@Composable
fun InkStrip(state: InkStripState, fallback: InkLine, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val flash = state.flash
    LaunchedEffect(flash?.first) {
        val serial = flash?.first ?: return@LaunchedEffect
        delay(InkStripSpec.FLASH_MS)
        state.clearFlash(serial)
    }
    val screenLine = state.line ?: fallback
    val shown = flash?.second ?: screenLine
    val live = shown.live || state.busyArms.isNotEmpty()
    InkStripLine(shown.text, live, modifier, onClick)
}

/** One strip line. With [onClick] the whole strip is a button (it opens the Inkwell). */
@Composable
fun InkStripLine(text: String, live: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = DotTheme.c
    val clean = remember(text) { InkLine.sanitize(text, maxChars = 64) }
    val spoken = remember(clean, live) {
        val words = dotTextSpoken(clean)
        if (live && words.isNotEmpty()) "$words, live" else words
    }
    val unlit = inkUnlitColor(c)
    val tap = if (onClick != null) Modifier.clickable(onClickLabel = INK_STRIP_CLICK_LABEL, onClick = onClick) else Modifier
    Canvas(
        modifier
            .fillMaxWidth()
            .height(InkStripSpec.Height)
            .then(tap)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (onClick != null) {
                    role = Role.Button
                    // `this.` picks the semantics action, not the onClick parameter.
                    this.onClick(label = INK_STRIP_CLICK_LABEL, action = { onClick(); true })
                }
            }
    ) {
        drawInkLine(clean, live, on = c.textPrimary, off = unlit, accent = c.accent)
    }
}

private fun DrawScope.drawInkLine(text: String, live: Boolean, on: Color, off: Color, accent: Color) {
    val p = InkStripSpec.Pitch.toPx()
    val cols = (size.width / p).toInt()
    if (cols <= 0) return
    val top = (size.height - DotFont5x7.ROWS * p) / 2f
    val visible = text.take(inkStripCapacity(cols))
    val lit = ArrayList<Offset>()
    val dark = ArrayList<Offset>(cols * DotFont5x7.ROWS)
    val mark = ArrayList<Offset>(6)
    for (col in 0 until cols) for (row in 0 until DotFont5x7.ROWS) {
        val o = Offset(col * p + p / 2f, top + row * p + p / 2f)
        when {
            live && col in InkStripSpec.LIVE_COLS && row in InkStripSpec.LIVE_ROWS -> mark += o
            col < cols - 1 && DotFont5x7.litAt(visible, col - InkStripSpec.TEXT_START_COL, row) -> lit += o
            else -> dark += o
        }
    }
    // Round-capped points draw as dots; three calls instead of ~1,200 circles.
    val d = p * InkStripSpec.DOT_FRACTION
    drawPoints(dark, PointMode.Points, off, strokeWidth = d, cap = StrokeCap.Round)
    drawPoints(lit, PointMode.Points, on, strokeWidth = d, cap = StrokeCap.Round)
    if (mark.isNotEmpty()) drawPoints(mark, PointMode.Points, accent, strokeWidth = d, cap = StrokeCap.Round)
}
