package ai.daylight.assistant.ui.tune

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotTheme
import kotlin.math.roundToInt

/** Pure geometry for [DotSlider]: where each dot sits and which dot a touch lands on. */
internal object DotSliderMath {
    enum class Dot { Set, Thumb, Off }

    /** Centre of dot [index] when [count] dots span [width], [inset] in from each end. */
    fun centre(index: Int, count: Int, width: Float, inset: Float): Float =
        if (count <= 1) width / 2f else inset + index * (width - 2f * inset) / (count - 1)

    /** The dot nearest to [x]; touches past either end clamp to the first or last dot. */
    fun indexAt(x: Float, count: Int, width: Float, inset: Float): Int {
        if (count <= 1) return 0
        val step = (width - 2f * inset) / (count - 1)
        if (step <= 0f) return 0
        return ((x - inset) / step).roundToInt().coerceIn(0, count - 1)
    }

    /** What the slider does with a move: drag the thumb, hold the gesture, wait, or let it go. */
    enum class Claim { Drag, Hold, Wait, Release }

    /**
     * [total] is how far the finger has moved since touching down. Once dragging, every move
     * drags. Before that, a mostly sideways move is held (consumed, so the drawer keeps out)
     * and becomes a drag past [slop]. A mostly vertical move is only watched, never consumed,
     * because consuming it would cancel the page's scroll; past [slop] it is released.
     */
    fun claim(total: Offset, slop: Float, dragging: Boolean): Claim {
        if (dragging) return Claim.Drag
        val dx = kotlin.math.abs(total.x)
        val dy = kotlin.math.abs(total.y)
        return when {
            dx >= dy && dx > slop -> Claim.Drag
            dx >= dy -> Claim.Hold
            dy > slop -> Claim.Release
            else -> Claim.Wait
        }
    }

    /** Dots before the thumb are set (white), the thumb covers its own dot, the rest are off. */
    fun dot(index: Int, selected: Int): Dot = when {
        index < selected -> Dot.Set
        index == selected -> Dot.Thumb
        else -> Dot.Off
    }
}

/**
 * Kit slider (settings.png "Monthly cost cap"): a row of [count] 6dp dots with a 20dp white
 * thumb. Tap or drag picks a dot, with a haptic tick per dot. [onIndexChange] fires live
 * while dragging; [onChangeFinished] fires once on release, which is where to save.
 * TalkBack reads [valueLabel] and can step it with the volume keys.
 */
@Composable
fun DotSlider(
    index: Int,
    count: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
    label: String = "",
    valueLabel: String = "",
    enabled: Boolean = true,
) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(index)
    val change by rememberUpdatedState(onIndexChange)
    val finish by rememberUpdatedState(onChangeFinished)
    fun pick(i: Int) {
        if (i != current) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            change(i)
        }
    }
    val dotRadius = 3.dp
    val inset = dotRadius
    // One gesture loop for tap and drag. A sideways move is claimed from its first pixel, so
    // the chat drawer (which engages after 4dp, from the parent) sees it consumed and keeps
    // out; a move that starts mostly vertical is left alone so the page still scrolls.
    val gestures = if (!enabled) Modifier else Modifier.pointerInput(count) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val w = size.width.toFloat()
            var total = Offset.Zero
            var dragging = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    // Released: a tap picks the dot under the finger.
                    if (!dragging && !change.isConsumed) pick(DotSliderMath.indexAt(change.position.x, count, w, inset.toPx()))
                    finish()
                    break
                }
                if (change.isConsumed) break
                total += change.position - change.previousPosition
                when (DotSliderMath.claim(total, slop, dragging)) {
                    DotSliderMath.Claim.Drag -> {
                        dragging = true
                        change.consume()
                        pick(DotSliderMath.indexAt(change.position.x, count, w, inset.toPx()))
                    }
                    DotSliderMath.Claim.Hold -> change.consume()
                    DotSliderMath.Claim.Wait -> Unit
                    DotSliderMath.Claim.Release -> break
                }
            }
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                if (label.isNotEmpty()) contentDescription = label
                stateDescription = valueLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    index.toFloat(), 0f..(count - 1).coerceAtLeast(1).toFloat(), steps = (count - 2).coerceAtLeast(0)
                )
                if (enabled) {
                    setProgress { target ->
                        val i = target.roundToInt().coerceIn(0, count - 1)
                        if (i != current) {
                            change(i)
                            finish()
                        }
                        true
                    }
                } else {
                    disabled()
                }
            }
            .then(gestures)
    ) {
        val w = size.width
        val cy = size.height / 2f
        val r = dotRadius.toPx()
        val selected = index.coerceIn(0, (count - 1).coerceAtLeast(0))
        for (i in 0 until count) {
            val colour = when (DotSliderMath.dot(i, selected)) {
                DotSliderMath.Dot.Set -> if (enabled) c.textPrimary else c.textDisabled
                DotSliderMath.Dot.Off -> c.dotOff
                DotSliderMath.Dot.Thumb -> null
            } ?: continue
            drawCircle(colour, r, Offset(DotSliderMath.centre(i, count, w, inset.toPx()), cy))
        }
        // The thumb: 20dp, a 14dp white disc in a 3dp ring of the background colour so it
        // stands off its neighbours.
        val tx = DotSliderMath.centre(selected, count, w, inset.toPx())
        drawCircle(c.bgAlt, 10.dp.toPx(), Offset(tx, cy))
        drawCircle(if (enabled) c.textPrimary else c.textDisabled, 7.dp.toPx(), Offset(tx, cy))
    }
}
