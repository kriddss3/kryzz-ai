package ai.daylight.assistant.ui.dot.effects

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ai.daylight.assistant.ui.dot.DotMotion
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot

/*
 * Phase D: the clock behind every stepped (LED) effect. Dot things snap from one state to
 * the next on a fixed step instead of easing, so they are driven by a counter, not by an
 * animation curve. The counter only runs while its screen is started (a screen under
 * another one, or the app in the background, costs nothing) and while motion is on.
 * Read the returned state inside a draw block so only the canvas redraws, never the
 * screen around it.
 */

/** Pure timing maths for the stepped effects (unit tested). */
object DotSteps {
    /** The REC dot and the strip's live mark: 600ms on, 400ms off (EFFECTS.md #4). */
    const val BLINK_ON = 600
    const val BLINK_OFF = 400

    /** The streaming caret: an even text-cursor blink. */
    const val CARET_ON = 530
    const val CARET_OFF = 530

    /** Whether a blink of [onMs] on then [offMs] off is lit at [timeMs]. */
    fun blinkOn(timeMs: Long, onMs: Int, offMs: Int): Boolean {
        val period = (onMs + offMs).toLong().coerceAtLeast(1L)
        return Math.floorMod(timeMs, period) < onMs
    }

    /** Milliseconds from [timeMs] until that blink next turns on or off (at least 1). */
    fun msToNextEdge(timeMs: Long, onMs: Int, offMs: Int): Long {
        val period = (onMs + offMs).toLong().coerceAtLeast(1L)
        val p = Math.floorMod(timeMs, period)
        return (if (p < onMs) onMs - p else period - p).coerceAtLeast(1L)
    }

    /** One move towards [target]: the rail's bar travels a slot at a time. */
    fun stepToward(current: Int, target: Int): Int = when {
        current < target -> current + 1
        current > target -> current - 1
        else -> current
    }
}

/**
 * A counter that goes up by one every [stepMs] while [running] and motion is on, and holds
 * its value otherwise. It pauses with the lifecycle of the screen it is on.
 */
@Composable
fun rememberDotTicker(stepMs: Int, running: Boolean = true): IntState {
    val motion = LocalKryzzMotionEnabled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tick = remember { mutableIntStateOf(0) }
    val active = running && motion
    LaunchedEffect(stepMs, active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(stepMs.toLong().coerceAtLeast(1L))
                tick.intValue += 1
            }
        }
    }
    return tick
}

/**
 * A hard on/off blink read from the uptime clock, so every LED using the same timing blinks
 * together (the REC dot and the strip's live mark flash as one signal). Lit and still when
 * [running] is false or motion is off.
 */
@Composable
fun rememberDotBlink(
    running: Boolean = true,
    onMs: Int = DotSteps.BLINK_ON,
    offMs: Int = DotSteps.BLINK_OFF,
): State<Boolean> {
    val motion = LocalKryzzMotionEnabled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lit = remember { mutableStateOf(true) }
    val active = running && motion
    LaunchedEffect(onMs, offMs, active, lifecycle) {
        if (!active) {
            lit.value = true
            return@LaunchedEffect
        }
        try {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val now = SystemClock.uptimeMillis()
                    lit.value = DotSteps.blinkOn(now, onMs, offMs)
                    delay(DotSteps.msToNextEdge(now, onMs, offMs))
                }
            }
        } finally {
            lit.value = true
        }
    }
    return lit
}

/**
 * The haptic for a step that settles: CONFIRM when it worked and REJECT when it failed
 * (API 30+), a text-handle tick before that. Never LongPress.
 */
@Composable
fun rememberStepHaptic(): (ok: Boolean) -> Unit {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    return remember(view, haptic) {
        { ok ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(if (ok) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

/**
 * EFFECT 14 as a modifier: each time [trigger] goes up, a ring of accent dots spreads from
 * [origin] (given the node size) and fades, drawn behind the content. Used on send. Nothing
 * is drawn when motion is off, and it never takes touches.
 */
fun Modifier.dotRipple(
    trigger: Int,
    origin: (Size) -> Offset,
    pitch: Dp = 10.dp,
    color: Color? = null,
): Modifier = composed {
    val motion = LocalKryzzMotionEnabled.current
    val accent = color ?: DotTheme.c.accentText
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && motion) {
            p.snapTo(0f)
            p.animateTo(1f, tween(RIPPLE_MS, easing = DotMotion.Decel))
        }
    }
    drawBehind { drawRippleDots(p.value, origin(size), pitch.toPx(), accent) }
}

internal const val RIPPLE_MS = 700

/** The dots of a ripple at [progress] (1 = finished, nothing drawn). */
internal fun DrawScope.drawRippleDots(progress: Float, origin: Offset, pitchPx: Float, color: Color) {
    if (progress >= 1f || pitchPx <= 0f) return
    val maxR = hypot(size.width, size.height)
    val ringR = progress * maxR
    val band = pitchPx * 3
    var y = pitchPx / 2
    while (y < size.height) {
        var x = pitchPx / 2
        while (x < size.width) {
            val k = 1f - abs(hypot(x - origin.x, y - origin.y) - ringR) / band
            if (k > 0f) drawCircle(color.copy(alpha = k * (1f - progress)), pitchPx * .18f * (0.6f + k * .6f), Offset(x, y))
            x += pitchPx
        }
        y += pitchPx
    }
}
