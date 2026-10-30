package ai.daylight.assistant.ui.dot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.effects.DotSteps
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.delay

/** Kit sheet handle: a 32x4dp bar in borderStrong, 10dp from the top (screens/src/common.py). */
@Composable
fun DotSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(32.dp, 4.dp)
            .background(DotTheme.c.borderStrong, RoundedCornerShape(2.dp))
    )
}

/**
 * A row of [count] dots with the first [lit] in [on]: determinate progress and meters (the
 * plan card's 24 dots, the cost line's 12). It opens already filled, so a finished card
 * costs no frames. With [stepMs] above 0 a later change of [lit] lights (or clears) one
 * dot per step instead of jumping (phase D). If the row is wider than the space it gets,
 * the dots close up.
 */
@Composable
fun DotBar(
    count: Int,
    lit: Int,
    on: Color,
    off: Color,
    modifier: Modifier = Modifier,
    dot: Dp = 4.dp,
    gap: Dp = 3.dp,
    stepMs: Int = 0,
) {
    if (count <= 0) return
    val motion = LocalKryzzMotionEnabled.current
    val shown = remember { mutableIntStateOf(lit) }
    LaunchedEffect(lit, stepMs, motion) {
        if (stepMs <= 0 || !motion) {
            shown.intValue = lit
            return@LaunchedEffect
        }
        while (shown.intValue != lit) {
            delay(stepMs.toLong())
            shown.intValue = DotSteps.stepToward(shown.intValue, lit)
        }
    }
    Canvas(modifier.widthIn(max = dot * count + gap * (count - 1)).fillMaxWidth().height(dot)) {
        val d = dot.toPx()
        val natural = d + gap.toPx()
        val pitch = if (count == 1) natural else minOf(natural, (size.width - d) / (count - 1))
        val n = shown.intValue
        for (i in 0 until count) {
            drawCircle(if (i < n) on else off, d / 2, Offset(i * pitch + d / 2, d / 2))
        }
    }
}

/** The kit's dotted vertical connector between plan steps: round 1.5dp dots every 4.5dp. */
@Composable
fun DottedConnector(color: Color, modifier: Modifier = Modifier, width: Dp = 1.5.dp) {
    Canvas(modifier.width(width)) {
        val w = width.toPx()
        drawLine(
            color = color,
            start = Offset(size.width / 2, w / 2),
            end = Offset(size.width / 2, size.height - w / 2),
            strokeWidth = w,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, w * 3f))
        )
    }
}

/**
 * The kit's "unlit, not yet" outline: a dashed border drawn inside the bounds.
 * A [radius] of half the height or more makes a pill or a circle.
 */
fun Modifier.dashedBorder(
    color: Color,
    width: Dp = 1.dp,
    radius: Dp = 0.dp,
    dash: Dp = 3.dp,
    gap: Dp = 3.dp
): Modifier = drawBehind {
    val stroke = width.toPx()
    val inset = stroke / 2f
    val r = (radius.toPx() - inset).coerceAtLeast(0f)
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())))
    )
}
