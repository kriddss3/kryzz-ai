package ai.daylight.assistant.ui.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.ui.dot.DotGrey
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.MonoFamily
import ai.daylight.assistant.ui.dot.effects.DotSteps
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Rail geometry from SCREENS.md "Shared chrome" (v1.2, the quieter rail). */
internal object ArmRailSpec {
    /** The rail's column next to the screen. Screens keep their own 16dp padding inside. */
    val Width = 24.dp
    val Dot = 4.dp

    /** 4dp dot + 22dp gap. Eight slots make 208dp; the dots span 196dp with the bar. */
    val Pitch = 26.dp
    val BarHeight = 14.dp

    /** Dot centre from the right edge: the kit's 6dp inset plus half a dot. */
    val EndInset = 8.dp
    val LabelGap = 3.dp

    /** The active bar moves one slot per step (Talk to Tune is 7 steps, about 280ms). */
    const val BAR_STEP_MS = 40L
}

/**
 * The 8-dot arm rail on the right edge. The current arm is a yellow bar, a busy arm a
 * grey dot, an unlit arm a hollow ring. Tap a dot to open its arm, or press and drag to
 * scrub: each dot passed gives a haptic tick and shows the arm's name, and the arm under
 * the finger opens on release (so scrubbing past Voice never starts the microphone).
 *
 * The rail is laid out next to the NavHost, never on top of it, and it opts out of the
 * system back gesture so a drag on it can't trigger Back.
 */
@Composable
fun ArmRail(
    active: Arm?,
    busy: Set<Arm>,
    onOpen: (Arm) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = DotTheme.c
    val arms = Arm.entries
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val currentOnOpen by rememberUpdatedState(onOpen)
    var hover by remember { mutableStateOf<Int?>(null) }
    val labelStyle = remember {
        TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.12.em)
    }
    val pitchPx = with(density) { ArmRailSpec.Pitch.toPx() }
    // Phase D: the yellow bar travels to a new arm one slot per step instead of jumping.
    val motion = LocalKryzzMotionEnabled.current
    val target = active?.ordinal ?: -1
    val barAt = remember { mutableIntStateOf(target) }
    LaunchedEffect(target, motion) {
        if (target < 0 || barAt.intValue < 0 || !motion) {
            barAt.intValue = target
            return@LaunchedEffect
        }
        while (barAt.intValue != target) {
            delay(ArmRailSpec.BAR_STEP_MS)
            barAt.intValue = DotSteps.stepToward(barAt.intValue, target)
        }
    }

    Box(modifier.width(ArmRailSpec.Width).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(ArmRailSpec.Width)
                .systemGestureExclusion { coords ->
                    // Only the dots' own span (196dp), which keeps us under Android's 200dp
                    // per-edge exclusion limit.
                    val inset = (pitchPx - with(density) { ArmRailSpec.BarHeight.toPx() }) / 2f
                    Rect(0f, inset, coords.size.width.toFloat(), coords.size.height - inset)
                }
                .pointerInput(arms.size) {
                    val firstCenter = pitchPx / 2f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = railIndexAt(down.position.y, firstCenter, pitchPx, arms.size)
                        var index = start
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                hover = null
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnOpen(arms[if (dragging) index else start])
                                return@awaitEachGesture
                            }
                            if (!dragging && abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) {
                                dragging = true
                                hover = index
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (dragging) {
                                change.consume()
                                val next = railIndexAt(change.position.y, firstCenter, pitchPx, arms.size)
                                if (next != index) {
                                    index = next
                                    hover = next
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        // The gesture was cancelled (another pointer took over): open nothing.
                        hover = null
                    }
                }
                .drawBehind {
                    val r = ArmRailSpec.Dot.toPx() / 2f
                    val barHeight = ArmRailSpec.BarHeight.toPx()
                    val cx = size.width - ArmRailSpec.EndInset.toPx()
                    val ring = 1.dp.toPx()
                    val bar = barAt.intValue
                    arms.forEachIndexed { i, arm ->
                        val cy = pitchPx / 2f + i * pitchPx
                        val hovered = hover == i
                        when {
                            i == bar -> drawRoundRect(
                                c.accentText,
                                topLeft = Offset(cx - r, cy - barHeight / 2f),
                                size = Size(r * 2f, barHeight),
                                cornerRadius = CornerRadius(r)
                            )
                            !arm.live -> drawCircle(
                                if (hovered) c.textTertiary else c.borderStrong,
                                r - ring / 2f,
                                Offset(cx, cy),
                                style = Stroke(ring)
                            )
                            hovered -> drawCircle(c.textPrimary, r, Offset(cx, cy))
                            arm in busy -> drawCircle(DotGrey.G600, r, Offset(cx, cy))
                            else -> drawCircle(c.border, r, Offset(cx, cy))
                        }
                    }
                    val shownIndex = hover ?: return@drawBehind
                    val arm = arms[shownIndex]
                    val layout = measurer.measure(
                        arm.label.uppercase(),
                        labelStyle.copy(color = if (arm.live) c.accentText else c.textTertiary)
                    )
                    val w = layout.size.width.toFloat()
                    val h = layout.size.height.toFloat()
                    val cy = pitchPx / 2f + shownIndex * pitchPx
                    val lx = cx - r - ArmRailSpec.LabelGap.toPx() - h / 2f
                    // Vertical, reading bottom to top, centred on the dot.
                    rotate(-90f, pivot = Offset(lx, cy)) {
                        drawText(layout, topLeft = Offset(lx - w / 2f, cy - h / 2f))
                    }
                }
        ) {
            arms.forEach { arm ->
                Box(
                    Modifier
                        .size(ArmRailSpec.Width, ArmRailSpec.Pitch)
                        .semantics {
                            contentDescription = arm.label
                            role = Role.Tab
                            selected = arm == active
                            stateDescription = when {
                                arm == active -> "Open"
                                !arm.live -> "Not yet"
                                arm in busy -> "Working"
                                else -> ""
                            }
                            onClick(label = "Open ${arm.label}") {
                                currentOnOpen(arm)
                                true
                            }
                        }
                )
            }
        }
    }
}
