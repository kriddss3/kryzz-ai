package ai.daylight.assistant.ui.talk

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import ai.daylight.assistant.ui.LocalSidePanelHoldSuppress
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlin.math.abs

/** The rules of hold-to-talk on a text field (v2.1). Pure, so they are unit tested. */
internal object HoldToTalkModel {
    /** How long a still press must last to start dictation (a little under the system long-press). */
    const val HOLD_MS = 300L

    /** How far (dp) a held finger slides away before letting go drops the recording. */
    const val CANCEL_DP = 72f

    /** A hold that slid right or up past [cancelPx], or far sideways, cancels. */
    fun cancels(dx: Float, dy: Float, cancelPx: Float): Boolean =
        dx > cancelPx || -dy > cancelPx || abs(dx) > cancelPx * 1.5f

    /** Normalized 0..1 progress toward the swipe-right or slide-up cancel threshold. */
    fun cancelProgress(dx: Float, dy: Float, cancelPx: Float): Float =
        if (cancelPx <= 0f) 0f else maxOf((dx / cancelPx).coerceIn(0f, 1f), (-dy / cancelPx).coerceIn(0f, 1f))

    /** The line above the reply rule while a hold or a dictation is going on, or null. */
    fun note(holding: Boolean, cancelZone: Boolean, dictating: Boolean, transcribing: Boolean): Pair<String, String>? = when {
        holding && cancelZone -> "CANCEL" to "Let go to drop it."
        holding && dictating -> "LISTENING" to "Let go to add it. Slide right to cancel."
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
    onDragProgress: (dxPx: Float, progress: Float) -> Unit = { _, _ -> },
) {
    val haptic = LocalHapticFeedback.current
    val holdSuppress = LocalSidePanelHoldSuppress.current
    val tap by rememberUpdatedState(onTap)
    val start by rememberUpdatedState(onHoldStart)
    val zone by rememberUpdatedState(onCancelZone)
    val end by rememberUpdatedState(onHoldEnd)
    val drag by rememberUpdatedState(onDragProgress)
    Box(
        modifier.pointerInput(Unit) {
            val cancelPx = HoldToTalkModel.CANCEL_DP.dp.toPx()
            val holdSlop = maxOf(viewConfiguration.touchSlop * 1.25f, 12.dp.toPx())
            awaitEachGesture {
                val down = awaitFirstDown()
                holdSuppress?.active = true
                try {
                    val early: PressEnd? = withTimeoutOrNull(HoldToTalkModel.HOLD_MS) {
                        var result: PressEnd? = null
                        while (result == null) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            result = when {
                                change == null -> PressEnd.MOVED
                                !change.pressed -> PressEnd.TAP
                                (change.position - down.position).getDistance() > holdSlop -> PressEnd.MOVED
                                else -> null
                            }
                        }
                        result
                    }
                    when (early) {
                        PressEnd.TAP -> tap()
                        PressEnd.MOVED -> {
                            // A swipe that began here (e.g. the library drawer): release hold suppression.
                            holdSuppress?.active = false
                        }
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
                                    drag(moved.x.coerceAtLeast(0f), HoldToTalkModel.cancelProgress(moved.x, moved.y, cancelPx))
                                    val nowCancel = HoldToTalkModel.cancels(moved.x, moved.y, cancelPx)
                                    if (nowCancel != cancelled) {
                                        cancelled = nowCancel
                                        zone(cancelled)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                drag(0f, 0f)
                                ended = true
                                end(cancelled)
                            } finally {
                                // The gesture was taken away (the screen changed): drop the recording.
                                if (!ended) {
                                    drag(0f, 0f)
                                    end(true)
                                }
                            }
                        }
                    }
                } finally {
                    holdSuppress?.active = false
                }
            }
        }
    )
}

/** The line above the reply rule during a hold: a yellow (or red) mono label and a hint. */
@Composable
internal fun HoldNote(label: String, text: String, modifier: Modifier = Modifier, cancelProgress: Float = 0f) {
    val c = DotTheme.c
    val motion = LocalKryzzMotionEnabled.current
    val shift = if (motion && (label == "LISTENING" || label == "CANCEL")) (cancelProgress.coerceIn(0f, 1f) * 18f).dp else 0.dp
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .offset(x = shift)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = DotType.Label, color = if (label == "CANCEL") c.error else c.accentText)
        Spacer(Modifier.width(10.dp))
        Text(text, style = DotType.BodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
