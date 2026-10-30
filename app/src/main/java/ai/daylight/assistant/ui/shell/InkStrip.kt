package ai.daylight.assistant.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ai.daylight.assistant.ui.dot.DotMotion
import ai.daylight.assistant.ui.dot.DotPalette
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import ai.daylight.assistant.ui.dot.effects.dotTextSpoken
import ai.daylight.assistant.ui.dot.effects.rememberDotBlink
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
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

/**
 * The strip's stepped motion (phase D), pure so it is unit tested:
 *  - a new line wipes in from the first character that changed, one dot column per 28ms
 *    step; a long change moves several columns a step so no wipe takes longer than ~620ms
 *  - a line too long for the strip ticks left one dot column per step after a hold, and
 *    comes round again after a gap
 */
internal object InkMotion {
    const val WIPE_STEP_MS = DotMotion.DotStep
    const val WIPE_MAX_STEPS = 22
    const val TICKER_STEP_MS = DotMotion.DotStep * 2
    const val TICKER_HOLD_MS = 1_200L

    /** Blank characters between the end of a ticking line and its start coming round. */
    const val TICKER_GAP = 4
    const val NO_WIPE = -1

    /** The dot column (of the text) where [new] first differs from [old], on a character boundary. */
    fun firstChange(old: String, new: String): Int {
        val n = minOf(old.length, new.length)
        var i = 0
        while (i < n && old[i] == new[i]) i++
        return i * DotFont5x7.CELL_COLS
    }

    /** The columns a wipe sweeps: from the first change to the end of the longer line. */
    fun wipeSpan(old: String, new: String): IntRange {
        if (old == new) return IntRange.EMPTY
        val end = maxOf(DotFont5x7.columns(old), DotFont5x7.columns(new))
        return firstChange(old, new) until end
    }

    /** Columns the front moves each step: one, or more for a long change. */
    fun wipeStride(span: Int): Int = ((span + WIPE_MAX_STEPS - 1) / WIPE_MAX_STEPS).coerceAtLeast(1)

    /** Where the wipe front stands at each step, in order. Empty when nothing changed. */
    fun wipeFronts(old: String, new: String): List<Int> {
        val span = wipeSpan(old, new)
        if (span.isEmpty()) return emptyList()
        return (span.first..span.last step wipeStride(span.last - span.first + 1)).toList()
    }

    /** Text columns on a strip [cols] dots wide (the last column stays dark). */
    fun areaColumns(cols: Int): Int = (cols - InkStripSpec.TEXT_START_COL - 1).coerceAtLeast(0)

    /** Whether [text] is wider than [areaCols] and needs the ticker. */
    fun overflows(text: String, areaCols: Int): Boolean = areaCols > 0 && DotFont5x7.columns(text) > areaCols

    /** The text a ticker scrolls through: the line and its gap, repeating. */
    fun tickerLoop(text: String): String = text + " ".repeat(TICKER_GAP)

    fun loopColumns(text: String): Int = tickerLoop(text).length * DotFont5x7.CELL_COLS

    /** Which column of the loop shows at text column [col] after [offset] steps. */
    fun sourceColumn(col: Int, offset: Int, loopCols: Int): Int =
        if (loopCols <= 0) col else Math.floorMod(col + offset, loopCols)

    /** What one dot of the text area shows. */
    enum class Dot { OFF, LIT, FRONT }

    /**
     * Text column [col], [row] of the strip: the new line left of the wipe front, the old
     * line right of it, the front itself as a grey bar; with no wipe, the line (ticking by
     * [offset] when [ticking]) or as many whole characters as fit.
     */
    fun dot(
        col: Int,
        row: Int,
        text: String,
        old: String,
        front: Int,
        offset: Int,
        ticking: Boolean,
        areaCols: Int,
    ): Dot {
        val lit = when {
            front != NO_WIPE && col == front -> return Dot.FRONT
            front != NO_WIPE && col < front -> DotFont5x7.litAt(text, col, row)
            front != NO_WIPE -> DotFont5x7.litAt(old, col, row)
            ticking -> {
                val loop = tickerLoop(text)
                DotFont5x7.litAt(loop, sourceColumn(col, offset, loopColumns(text)), row)
            }
            // n characters take n * 6 - 1 columns, so (area + 1) / 6 whole characters fit.
            else -> DotFont5x7.litAt(text.take((areaCols + 1) / DotFont5x7.CELL_COLS), col, row)
        }
        return if (lit) Dot.LIT else Dot.OFF
    }
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
 * bar on every screen. A two-dot yellow mark at the start blinks while something is live,
 * a new line wipes in column by column, and a tap opens the Inkwell (C10).
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

/** What the strip is drawing right now; read while drawing, so a step redraws only the strip. */
@Stable
private class InkStripMotionState(initial: String) {
    var text by mutableStateOf(initial)
    var old by mutableStateOf(initial)
    val front = mutableIntStateOf(InkMotion.NO_WIPE)
    val offset = mutableIntStateOf(0)
}

/** One strip line. With [onClick] the whole strip is a button (it opens the Inkwell). */
@Composable
fun InkStripLine(text: String, live: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = DotTheme.c
    val motion = LocalKryzzMotionEnabled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clean = remember(text) { InkLine.sanitize(text, maxChars = 64) }
    val spoken = remember(clean, live) {
        val words = dotTextSpoken(clean)
        if (live && words.isNotEmpty()) "$words, live" else words
    }
    // With motion on the very first line wipes in from blank, like the strip powering on.
    val anim = remember { InkStripMotionState(if (motion) "" else clean) }
    var areaCols by remember { mutableIntStateOf(0) }
    val pitchPx = with(LocalDensity.current) { InkStripSpec.Pitch.toPx() }
    val markOn = rememberDotBlink(running = live)

    LaunchedEffect(clean, motion) {
        val previous = anim.text
        anim.old = previous
        anim.text = clean
        if (motion) {
            for (front in InkMotion.wipeFronts(previous, clean)) {
                anim.front.intValue = front
                delay(InkMotion.WIPE_STEP_MS.toLong())
            }
        }
        anim.front.intValue = InkMotion.NO_WIPE
    }
    val ticking = motion && InkMotion.overflows(clean, areaCols)
    LaunchedEffect(clean, ticking) {
        anim.offset.intValue = 0
        if (!ticking) return@LaunchedEffect
        val loopCols = InkMotion.loopColumns(clean)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            anim.offset.intValue = 0
            while (true) {
                // Hold at the start of every pass (the first hold also covers the wipe).
                delay(InkMotion.TICKER_HOLD_MS)
                repeat(loopCols) {
                    delay(InkMotion.TICKER_STEP_MS.toLong())
                    anim.offset.intValue += 1
                }
                anim.offset.intValue = 0
            }
        }
    }

    val unlit = inkUnlitColor(c)
    val tap = if (onClick != null) Modifier.clickable(onClickLabel = INK_STRIP_CLICK_LABEL, onClick = onClick) else Modifier
    Spacer(
        modifier
            .fillMaxWidth()
            .height(InkStripSpec.Height)
            .onSizeChanged { areaCols = InkMotion.areaColumns((it.width / pitchPx).toInt()) }
            .then(tap)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (onClick != null) {
                    role = Role.Button
                    // `this.` picks the semantics action, not the onClick parameter.
                    this.onClick(label = INK_STRIP_CLICK_LABEL, action = { onClick(); true })
                }
            }
            .drawWithCache {
                val p = pitchPx
                val cols = (size.width / p).toInt()
                val top = (size.height - DotFont5x7.ROWS * p) / 2f
                val area = InkMotion.areaColumns(cols)
                // The unlit field never changes for a size: lit dots are drawn over it.
                val field = ArrayList<Offset>(cols * DotFont5x7.ROWS)
                for (col in 0 until cols) for (row in 0 until DotFont5x7.ROWS) {
                    field += Offset(col * p + p / 2f, top + row * p + p / 2f)
                }
                val d = p * InkStripSpec.DOT_FRACTION
                onDrawBehind {
                    if (cols <= 0) return@onDrawBehind
                    drawPoints(field, PointMode.Points, unlit, strokeWidth = d, cap = StrokeCap.Round)
                    val shownText = anim.text
                    val oldText = anim.old
                    val front = anim.front.intValue
                    val offset = anim.offset.intValue
                    val lit = ArrayList<Offset>(256)
                    val bar = ArrayList<Offset>(DotFont5x7.ROWS)
                    for (tc in 0 until area) {
                        val x = (tc + InkStripSpec.TEXT_START_COL) * p + p / 2f
                        for (row in 0 until DotFont5x7.ROWS) {
                            when (InkMotion.dot(tc, row, shownText, oldText, front, offset, ticking, area)) {
                                InkMotion.Dot.LIT -> lit += Offset(x, top + row * p + p / 2f)
                                InkMotion.Dot.FRONT -> bar += Offset(x, top + row * p + p / 2f)
                                InkMotion.Dot.OFF -> Unit
                            }
                        }
                    }
                    // Round-capped points draw as dots: a handful of calls, not ~1,200 circles.
                    if (lit.isNotEmpty()) drawPoints(lit, PointMode.Points, c.textPrimary, strokeWidth = d, cap = StrokeCap.Round)
                    if (bar.isNotEmpty()) drawPoints(bar, PointMode.Points, c.textTertiary, strokeWidth = d, cap = StrokeCap.Round)
                    if (live && markOn.value) {
                        val mark = ArrayList<Offset>(6)
                        for (col in InkStripSpec.LIVE_COLS) for (row in InkStripSpec.LIVE_ROWS) {
                            mark += Offset(col * p + p / 2f, top + row * p + p / 2f)
                        }
                        drawPoints(mark, PointMode.Points, c.accentText, strokeWidth = d, cap = StrokeCap.Round)
                    }
                }
            }
    )
}
