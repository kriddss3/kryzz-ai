package ai.daylight.assistant.ui.dot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
