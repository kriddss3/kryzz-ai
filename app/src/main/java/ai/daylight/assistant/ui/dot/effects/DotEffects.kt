package ai.daylight.assistant.ui.dot.effects

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotMotion
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Kit effects, ported from kit/effects/compose/DotEffects.kt. Changes from the kit:
 *  - colours come from DotTheme (no hard-coded hex)
 *  - haptics are a tick (TextHandleMove), never LongPress
 *  - the failed PlanStepMark draws both strokes of its X
 *  - DotDissolve uses hashed per-cell noise, so the pattern never repeats on large screens
 *  - DotToggle matches the Tune mockup (white track, black knob) and is a real switch for
 *    accessibility
 *  - phase D: every effect holds still when motion is off (the app setting or the system
 *    animator scale), and the stepped ones run on the DotClock ticker instead of an
 *    infinite transition, so they stop with their screen and redraw only their canvas
 */

/** Indeterminate progress: lit dots of the 3-dot comet whose head is at [head] (tail first). */
internal fun cometDot(i: Int, head: Int): Boolean = i in (head - 3) until head

/* EFFECT 2: dot progress. Determinate: lit dots = progress * count.
 * Indeterminate (progress = null): a 3-dot comet stepping across, 45ms a dot. With motion
 * off the comet stands still in the middle. */
@Composable
fun DotProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    count: Int = 24,
    dot: Dp = 4.dp,
    gap: Dp = 3.dp,
    on: Color = DotTheme.c.accent,
    off: Color = DotTheme.c.dotOff,
) {
    val motion = LocalKryzzMotionEnabled.current
    val tick = rememberDotTicker(45, running = progress == null)
    Canvas(modifier.height(dot).width((dot + gap) * count - gap)) {
        val d = dot.toPx(); val g = gap.toPx()
        val head = if (motion) tick.intValue % (count + 3) else (count + 3) / 2 + 1
        for (i in 0 until count) {
            val lit = if (progress != null) i < (progress.coerceIn(0f, 1f) * count).roundToInt() else cometDot(i, head)
            val tail = progress == null && motion && i == head - 3
            drawCircle(if (lit) on.copy(alpha = if (tail) .45f else 1f) else off, d / 2, Offset(i * (d + g) + d / 2, d / 2))
        }
    }
}

/* EFFECT 3: glyph pulse, a breathing LED dot with expanding halo rings (still when motion is off). */
@Composable
fun GlyphPulse(modifier: Modifier = Modifier, size: Dp = 48.dp, color: Color = DotTheme.c.accent, periodMs: Int = DotMotion.Pulse) {
    val p = if (LocalKryzzMotionEnabled.current) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "p").value
    } else 0f
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val breathe = 0.55f + 0.45f * (0.5f + 0.5f * cos(2 * PI.toFloat() * p))
        for (k in 0..1) {
            val q = (p + k * .5f) % 1f
            drawCircle(color.copy(alpha = (1 - q) * .35f), r * (.25f + .75f * q), style = Stroke(1.dp.toPx()))
        }
        drawCircle(color.copy(alpha = breathe), r * .22f)
    }
}

/* EFFECT 4: recording dot, a hard blink (600 on / 400 off) plus a mono timer. */
@Composable
fun RecordingIndicator(elapsedSec: Int, modifier: Modifier = Modifier, color: Color = DotTheme.c.recording) {
    val lit = rememberDotBlink()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).graphicsLayer { alpha = if (lit.value) 1f else .15f }.background(color, DotRadius.Pill))
        Spacer(Modifier.width(8.dp))
        Text(
            String.format(Locale.US, "REC  %02d:%02d", elapsedSec / 60, elapsedSec % 60),
            style = DotType.Label,
            color = DotTheme.c.textPrimary
        )
    }
}

/* EFFECT 5: voice dot waveform. Columns of stacked dots, lit count = amplitude.
 * The newest level is the right-hand column. With [cols] set (the kit's 44 x 15 voice field)
 * the dots scale to fill the width; otherwise the column count follows [dot] + [gap].
 * [peakFrom] colours every lit dot at least that many rows from the centre line as a peak;
 * without it only the end dots of a full-height column are peaks. */
@Composable
fun VoiceDotWave(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    rows: Int = 9,
    dot: Dp = 4.dp,
    gap: Dp = 3.dp,
    cols: Int? = null,
    peakFrom: Int? = null,
    on: Color = DotTheme.c.textPrimary,
    peak: Color = DotTheme.c.accent,
    off: Color = DotTheme.c.dotOff
) = VoiceDotWave({ levels }, modifier, rows, dot, gap, cols, peakFrom, on, peak, off)

/** The same wave, reading [levels] while drawing: a fast-changing level only redraws the canvas. */
@Composable
fun VoiceDotWave(
    levels: () -> List<Float>,
    modifier: Modifier = Modifier,
    rows: Int = 9,
    dot: Dp = 4.dp,
    gap: Dp = 3.dp,
    cols: Int? = null,
    peakFrom: Int? = null,
    on: Color = DotTheme.c.textPrimary,
    peak: Color = DotTheme.c.accent,
    off: Color = DotTheme.c.dotOff
) {
    val sized = if (cols != null) modifier.fillMaxWidth().aspectRatio(cols.toFloat() / rows)
        else modifier.height((dot + gap) * rows - gap).fillMaxWidth()
    Canvas(sized) {
        val pitch: Float
        val d: Float
        val inset: Float
        if (cols != null) {
            pitch = size.width / cols
            d = pitch * dot.value / (dot.value + gap.value)
            inset = pitch / 2
        } else {
            d = dot.toPx()
            pitch = d + gap.toPx()
            inset = d / 2
        }
        val count = cols ?: (size.width / pitch).toInt()
        val values = levels()
        val lit = ArrayList<Offset>(count * rows)
        val peaks = ArrayList<Offset>()
        val dark = ArrayList<Offset>(count * rows)
        for (c in 0 until count) {
            val lv = values.getOrElse(values.size - count + c) { 0f }
            for (r in 0 until rows) {
                val o = Offset(c * pitch + inset, r * pitch + inset)
                when (waveDot(lv, r, rows, peakFrom)) {
                    WaveDot.OFF -> dark += o
                    WaveDot.ON -> lit += o
                    WaveDot.PEAK -> peaks += o
                }
            }
        }
        drawPoints(dark, PointMode.Points, off, strokeWidth = d, cap = StrokeCap.Round)
        drawPoints(lit, PointMode.Points, on, strokeWidth = d, cap = StrokeCap.Round)
        drawPoints(peaks, PointMode.Points, peak, strokeWidth = d, cap = StrokeCap.Round)
    }
}

internal enum class WaveDot { OFF, ON, PEAK }

/**
 * One dot of [VoiceDotWave]: a column lights [level] of its half-height above and below the
 * centre row (the centre row always shows). A NaN level reads as silence.
 */
internal fun waveDot(level: Float, row: Int, rows: Int, peakFrom: Int?): WaveDot {
    val mid = rows / 2
    val lv = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
    val half = (lv * (mid + 0.49f)).roundToInt()
    val dist = abs(row - mid)
    return when {
        dist > half -> WaveDot.OFF
        peakFrom != null -> if (dist >= peakFrom) WaveDot.PEAK else WaveDot.ON
        dist == half && half >= mid -> WaveDot.PEAK
        else -> WaveDot.ON
    }
}

/** Thinking dots lit at [tick]: 0, 1, 2, then 3, and round again. */
internal fun thinkingLit(tick: Int): Int = Math.floorMod(tick, 4)

/* EFFECT 6: thinking dots. Three dots, one more lights every 180ms, then reset (stepped).
 * With motion off all three stay lit. */
@Composable
fun ThinkingDots(
    modifier: Modifier = Modifier,
    dot: Dp = 6.dp,
    on: Color = DotTheme.c.textPrimary,
    off: Color = DotTheme.c.dotOff
) {
    val motion = LocalKryzzMotionEnabled.current
    val tick = rememberDotTicker(180)
    Canvas(modifier.size(dot * 5, dot)) {
        val d = dot.toPx()
        val lit = if (motion) thinkingLit(tick.intValue) else 3
        for (k in 0 until 3) drawCircle(if (k < lit) on else off, d / 2, Offset(k * 2 * d + d / 2, d / 2))
    }
}

/* EFFECT 7a: scanlines overlay. Keep alpha <= .08 in the app. */
fun Modifier.scanlines(color: Color, alpha: Float = .06f, pitch: Dp = 3.dp) = drawWithContent {
    drawContent()
    val p = pitch.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(color.copy(alpha = alpha), Offset(0f, y), Size(size.width, p / 2))
        y += p
    }
}

/* EFFECT 7b: power-on flicker, alpha 0 -> 1 -> .3 -> 1 -> .7 -> 1 over 360ms, once
 * (none when motion is off). */
@Composable
fun Modifier.flickerIn(key: Any = Unit): Modifier {
    val motion = LocalKryzzMotionEnabled.current
    val a = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key) {
        if (!motion) return@LaunchedEffect
        a.animateTo(1f, keyframes {
            durationMillis = DotMotion.Slow
            0f at 0; 1f at 60; .3f at 110; 1f at 170; .7f at 230; 1f at 360
        })
    }
    return graphicsLayer { alpha = a.value }
}

/* EFFECT 8: dotted grid background. Use 6-8% alpha behind text. */
fun Modifier.dotGrid(color: Color, pitch: Dp = 8.dp, radius: Dp = 1.dp) = drawBehind {
    val p = pitch.toPx(); val r = radius.toPx()
    var y = p / 2
    while (y < size.height) {
        var x = p / 2
        while (x < size.width) {
            drawCircle(color, r, Offset(x, y)); x += p
        }
        y += p
    }
}

/* EFFECT 9: ticker, an uppercase mono marquee for status lines (cut off, still, when motion is off). */
@Composable
fun Ticker(text: String, modifier: Modifier = Modifier) {
    val marquee = if (LocalKryzzMotionEnabled.current) {
        Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp, initialDelayMillis = 800)
    } else Modifier
    Text(
        text.uppercase(),
        style = DotType.Label,
        color = DotTheme.c.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(marquee)
    )
}

/** Four discrete steps: the toggle thumb moves mechanically. */
private val FourSteps = Easing { (it * 4).toInt() / 4f }

/*
 * EFFECT 10: toggle, 44x26 as in the Tune mockup. ON = white track + black knob; OFF = a
 * 1.5dp outline + grey knob; disabled = a dashed outline + dim knob. With [onChange] null it
 * only draws, for a row that is itself the switch (the row then carries the semantics).
 */
@Composable
fun DotToggle(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    val x by animateFloatAsState(
        if (checked) 1f else 0f,
        if (LocalKryzzMotionEnabled.current) tween(DotMotion.Fast, easing = FourSteps) else snap(),
        label = "x"
    )
    val interactive = if (onChange == null) Modifier else Modifier.toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onChange(it)
    }
    Canvas(modifier.size(44.dp, 26.dp).then(interactive)) {
        val stroke = 1.5.dp.toPx()
        val radius = CornerRadius(size.height / 2f)
        val on = checked && enabled
        if (on) {
            drawRoundRect(c.textPrimary, cornerRadius = radius)
        } else {
            val dashed = if (enabled) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            drawRoundRect(
                if (enabled) c.borderStrong else c.border,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(size.height / 2f - stroke / 2f),
                style = Stroke(stroke, pathEffect = dashed)
            )
        }
        // Knob: 13dp, 6.5dp in from the outline at either end.
        val knob = 6.5.dp.toPx()
        val start = 13.dp.toPx()
        val end = size.width - 13.dp.toPx()
        val knobColour = when {
            on -> c.bg
            !enabled -> c.dotOff
            else -> c.textDisabled
        }
        drawCircle(knobColour, knob, Offset(start + x * (end - start), size.height / 2f))
    }
}

/* EFFECT 11: button press, 80ms scale to .96 + fill inverts while pressed + haptic tick. */
@Composable
fun DotButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val c = DotTheme.c
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val s by animateFloatAsState(if (pressed) .96f else 1f, tween(DotMotion.Instant), label = "s")
    val bg = when {
        primary && pressed -> c.textPrimary
        primary -> c.accent
        pressed -> c.surfaceHigh
        else -> Color.Transparent
    }
    val fg = if (primary) c.onAccent else c.textPrimary
    Box(
        modifier
            .scale(s)
            .heightIn(min = 48.dp)
            .background(bg, DotRadius.Pill)
            .then(if (primary) Modifier else Modifier.border(1.dp, c.borderStrong, DotRadius.Pill))
            .clickable(src, null, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = DotType.Button, color = fg)
    }
}

/* EFFECT 12: page transitions. Enter: fade 140ms + a rise of 1/40 of the height. Exit: fade
 * 80ms. Going back, the page underneath only fades in (it was already there). */
val DotEnter: EnterTransition = fadeIn(tween(DotMotion.Fast, easing = DotMotion.Decel)) +
    slideInVertically(tween(DotMotion.Base, easing = DotMotion.Decel)) { it / 40 }
val DotExit: ExitTransition = fadeOut(tween(DotMotion.Instant))
val DotPopEnter: EnterTransition = fadeIn(tween(DotMotion.Fast, easing = DotMotion.Decel))

/** Deterministic 0..1 noise per cell, so a dissolve never shows a repeating tile. */
internal fun dissolveNoise(col: Int, row: Int): Float {
    var h = col * 374761393 + row * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

@Composable
fun DotDissolve(
    visible: Boolean,
    modifier: Modifier = Modifier,
    cell: Dp = 6.dp,
    cover: Color = DotTheme.c.bg,
    content: @Composable () -> Unit
) {
    val prog by animateFloatAsState(
        if (visible) 1f else 0f,
        if (LocalKryzzMotionEnabled.current) tween(DotMotion.Reveal, easing = LinearEasing) else snap(),
        label = "dis"
    )
    Box(modifier.drawWithContent {
        drawContent()
        if (prog >= 1f) return@drawWithContent
        val c = cell.toPx()
        val cols = ceil(size.width / c).toInt()
        val rows = ceil(size.height / c).toInt()
        for (j in 0 until rows) for (i in 0 until cols) {
            if (dissolveNoise(i, j) > prog) drawRect(cover, Offset(i * c, j * c), Size(c + .5f, c + .5f))
        }
    }) { content() }
}

/* EFFECT 13: plan step state.
 * PENDING: ring of 10 dim dots. ACTIVE: three accent dots chase (stepped).
 * DONE: the ring draws in, then the check. FAILED: an error-red ring and an X. */
enum class StepState { Pending, Active, Done, Failed, Skipped }

/** Draws the first [fraction] of [path] (one contour). */
private fun DrawScope.drawTrimmed(path: Path, fraction: Float, color: Color, strokeWidth: Float) {
    if (fraction <= 0f) return
    val pm = PathMeasure().apply { setPath(path, false) }
    val seg = Path()
    pm.getSegment(0f, pm.length * fraction, seg, true)
    drawPath(seg, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
}

/*
 * Changes from the kit: the chase only runs while a step is active (an infinite transition
 * on every mark kept the frame clock busy for finished cards), and a step only draws in and
 * ticks when it changes while you watch. A finished plan opens already drawn and silent.
 */
@Composable
fun PlanStepMark(state: StepState, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val first = remember { state }
    if (state == StepState.Active) ActiveStepMark(modifier, size)
    else SettledStepMark(state, animate = state != first, modifier = modifier, size = size)
}

/** Ten dots on a ring; three accent dots chase round it, 70ms a dot (still when motion is off). */
@Composable
private fun ActiveStepMark(modifier: Modifier, size: Dp) {
    val c = DotTheme.c
    val motion = LocalKryzzMotionEnabled.current
    val tick = rememberDotTicker(70)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2 - 1.dp.toPx()
        val head = if (motion) tick.intValue % 10 else 3
        for (k in 0 until 10) {
            val a = 2 * PI * k / 10 - PI / 2
            val lit = ((k - head + 10) % 10) < 3
            drawCircle(
                if (lit) c.accent else c.textTertiary.copy(alpha = .35f),
                1.3.dp.toPx(),
                Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            )
        }
    }
}

@Composable
private fun SettledStepMark(state: StepState, animate: Boolean, modifier: Modifier, size: Dp) {
    val c = DotTheme.c
    val haptic = rememberStepHaptic()
    val motion = LocalKryzzMotionEnabled.current
    val drawn = state == StepState.Done || state == StepState.Failed
    val draw = remember(state) { Animatable(if (drawn && animate && motion) 0f else 1f) }
    LaunchedEffect(state) {
        if (!drawn || !animate) return@LaunchedEffect
        if (motion) draw.animateTo(1f, tween(DotMotion.Base * 2, easing = DotMotion.Standard))
        haptic(state == StepState.Done)
    }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2 - 1.dp.toPx()
        val ctr = center
        val sw = 1.5.dp.toPx()
        when (state) {
            StepState.Pending, StepState.Active -> for (k in 0 until 10) {
                val a = 2 * PI * k / 10 - PI / 2
                drawCircle(
                    c.textTertiary.copy(alpha = .6f),
                    1.3.dp.toPx(),
                    Offset(ctr.x + r * cos(a).toFloat(), ctr.y + r * sin(a).toFloat())
                )
            }
            StepState.Skipped -> {
                drawCircle(c.textTertiary, r, style = Stroke(sw))
                drawLine(c.textTertiary, Offset(ctr.x - r * .45f, ctr.y), Offset(ctr.x + r * .45f, ctr.y), sw, StrokeCap.Round)
            }
            StepState.Done, StepState.Failed -> {
                // Grey like the agent-run mockup: a finished step steps back, the live one leads.
                val col = if (state == StepState.Done) c.textSecondary else c.error
                val ring = (draw.value * 2).coerceAtMost(1f)
                val mark = ((draw.value - .5f) * 2).coerceIn(0f, 1f)
                drawArc(
                    col, -90f, 360f * ring, false,
                    style = Stroke(sw, cap = StrokeCap.Round),
                    topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(2 * r, 2 * r)
                )
                if (state == StepState.Done) {
                    val check = Path().apply {
                        moveTo(ctr.x - r * .42f, ctr.y + r * .02f)
                        lineTo(ctr.x - r * .1f, ctr.y + r * .35f)
                        lineTo(ctr.x + r * .45f, ctr.y - r * .3f)
                    }
                    drawTrimmed(check, mark, col, sw)
                } else {
                    // Kit bug: one Path with two moveTo calls is two contours, and PathMeasure
                    // only trims the first, so only one stroke of the X was drawn.
                    val a = Path().apply { moveTo(ctr.x - r * .35f, ctr.y - r * .35f); lineTo(ctr.x + r * .35f, ctr.y + r * .35f) }
                    val b = Path().apply { moveTo(ctr.x + r * .35f, ctr.y - r * .35f); lineTo(ctr.x - r * .35f, ctr.y + r * .35f) }
                    drawTrimmed(a, (mark * 2).coerceAtMost(1f), col, sw)
                    drawTrimmed(b, (mark * 2 - 1f).coerceIn(0f, 1f), col, sw)
                }
            }
        }
    }
}

/* EFFECT 14: dot ripple. Dots light up as a ring passes their distance from the origin.
 * Nothing runs when motion is off. [Modifier.dotRipple] is the same ring behind content. */
@Composable
fun DotRipple(trigger: Int, modifier: Modifier = Modifier, origin: Offset? = null, pitch: Dp = 10.dp, color: Color = DotTheme.c.accent) {
    val motion = LocalKryzzMotionEnabled.current
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && motion) {
            p.snapTo(0f); p.animateTo(1f, tween(RIPPLE_MS, easing = DotMotion.Decel))
        }
    }
    Canvas(modifier) { drawRippleDots(p.value, origin ?: center, pitch.toPx(), color) }
}
