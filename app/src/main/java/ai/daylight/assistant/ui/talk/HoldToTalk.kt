package ai.daylight.assistant.ui.talk

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import kotlin.math.abs

/** The rules of hold-to-talk on a text field (v2.1). Pure, so they are unit tested. */
internal object HoldToTalkModel {
    /** How long a still press must last to start dictation (a little under the system long-press). */
    const val HOLD_MS = 300L

    /** How far (dp) a held finger slides away before letting go drops the recording. */
    const val CANCEL_DP = 72f

    /** A hold that slid up past [cancelPx], or far sideways, cancels. */
    fun cancels(dx: Float, dy: Float, cancelPx: Float): Boolean = -dy > cancelPx || abs(dx) > cancelPx * 1.5f

    /** The line above the reply rule while a hold or a dictation is going on, or null. */
    fun note(holding: Boolean, cancelZone: Boolean, dictating: Boolean, transcribing: Boolean): Pair<String, String>? = when {
        holding && cancelZone -> "CANCEL" to "Let go to drop it."
        holding && dictating -> "LISTENING" to "Let go to add it. Slide up to cancel."
        transcribing -> "WRITING" to "Turning your words into text…"
        else -> null
    }
}

private enum class PressEnd { TAP, MOVED }

/**
 * Lies over a text field while the field is not focused: a tap focuses it (the keyboard
 * comes up as before), a still press of [HoldToTalkModel.HOLD_MS] starts dictation until the
 * finger lifts. Sliding away first cancels. It has no semantics of its own, so TalkBack and
 * tests still reach the field underneath; the mic button is the accessible way to dictate.
 */
@Composable
internal fun HoldToTalkSurface(
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onCancelZone: (Boolean) -> Unit,
    onHoldEnd: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val start by rememberUpdatedState(onHoldStart)
    val zone by rememberUpdatedState(onCancelZone)
    val end by rememberUpdatedState(onHoldEnd)
    Box(
        modifier.pointerInput(Unit) {
            val cancelPx = HoldToTalkModel.CANCEL_DP.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                val early: PressEnd? = withTimeoutOrNull(HoldToTalkModel.HOLD_MS) {
                    var result: PressEnd? = null
                    while (result == null) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        result = when {
                            change == null -> PressEnd.MOVED
                            !change.pressed -> PressEnd.TAP
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> PressEnd.MOVED
                            else -> null
                        }
                    }
                    result
                }
                when (early) {
                    PressEnd.TAP -> tap()
                    PressEnd.MOVED -> Unit // a swipe that began here (the library drawer): not ours
                    null -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        start()
                        var cancelled = false
                        var ended = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                change.consume()
                                val moved = change.position - down.position
                                val nowCancel = HoldToTalkModel.cancels(moved.x, moved.y, cancelPx)
                                if (nowCancel != cancelled) {
                                    cancelled = nowCancel
                                    zone(cancelled)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            ended = true
                            end(cancelled)
                        } finally {
                            // The gesture was taken away (the screen changed): drop the recording.
                            if (!ended) end(true)
                        }
                    }
                }
            }
        }
    )
}

/** The line above the reply rule during a hold: a yellow (or grey) mono label and a hint. */
@Composable
internal fun HoldNote(label: String, text: String, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = DotType.Label, color = if (label == "CANCEL") c.textTertiary else c.accentText)
        Spacer(Modifier.width(10.dp))
        Text(text, style = DotType.BodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
