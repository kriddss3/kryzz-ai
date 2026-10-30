package ai.daylight.assistant.ui.dot.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.effects.rememberDotTicker
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled

/** Dot radius as a share of the pitch, from the kit's dots_svg (r = pitch * 0.36). */
internal const val SQUID_DOT_FRACTION = 0.72f

/**
 * Draws the squid sprite with its top-left corner at [origin]. Lit cells use [on], the eyes
 * [eye], and the unlit LED field [off] (null leaves it out). Three batched point calls, so
 * a whole board redraw stays cheap.
 */
internal fun DrawScope.drawSquid(
    origin: Offset,
    pitchPx: Float,
    grid: BooleanArray,
    on: Color,
    eye: Color,
    off: Color?
) {
    val lit = ArrayList<Offset>(160)
    val eyes = ArrayList<Offset>(8)
    val dark = if (off != null) ArrayList<Offset>(SquidSprite.COLS * SquidSprite.ROWS) else null
    for (r in 0 until SquidSprite.ROWS) for (c in 0 until SquidSprite.COLS) {
        val o = Offset(origin.x + c * pitchPx + pitchPx / 2f, origin.y + r * pitchPx + pitchPx / 2f)
        when {
            SquidSprite.isEye(r, c) -> eyes += o
            grid[r * SquidSprite.COLS + c] -> lit += o
            else -> dark?.add(o)
        }
    }
    val d = pitchPx * SQUID_DOT_FRACTION
    if (dark != null && off != null) drawPoints(dark, PointMode.Points, off, strokeWidth = d, cap = StrokeCap.Round)
    drawPoints(lit, PointMode.Points, on, strokeWidth = d, cap = StrokeCap.Round)
    drawPoints(eyes, PointMode.Points, eye, strokeWidth = d, cap = StrokeCap.Round)
}

/**
 * KryzzBot, the dot-matrix squid: white LEDs, yellow eyes, on a field of unlit dots.
 * [pitch] is the LED spacing; the sprite is 23 x 22 pitches. Pass [contentDescription]
 * when the squid means something on its own; by default it is decorative. With [waving]
 * the arms step through [SquidWave] instead of holding [pose] (still when motion is off).
 */
@Composable
fun DotSquid(
    modifier: Modifier = Modifier,
    pose: SquidPose = SquidPose.STANDBY,
    pitch: Dp = 7.dp,
    showUnlit: Boolean = pitch >= 3.dp,
    contentDescription: String? = null,
    waving: Boolean = false
) {
    val c = DotTheme.c
    val wave = rememberDotTicker(SquidWave.FRAME_MS, running = waving)
    val animate = waving && LocalKryzzMotionEnabled.current
    Canvas(
        modifier
            .size(pitch * SquidSprite.COLS, pitch * SquidSprite.ROWS)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription }
    ) {
        drawSquid(
            origin = Offset.Zero,
            pitchPx = pitch.toPx(),
            grid = if (animate) SquidWave.frame(wave.intValue) else pose.grid,
            on = c.textPrimary,
            eye = c.accent,
            off = if (showUnlit) c.dotOff else null
        )
    }
}

/** The pitch that fits the squid inside a [size] square (it is a little wider than tall). */
fun squidPitchFor(size: Dp): Dp = size / SquidSprite.COLS
