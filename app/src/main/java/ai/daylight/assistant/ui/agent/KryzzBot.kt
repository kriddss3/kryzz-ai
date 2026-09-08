package ai.daylight.assistant.ui.agent

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlin.math.sin

enum class BotMood { IDLE, THINKING, TOOLING, SPEAKING, SUCCESS, ERROR, LISTENING }

enum class BotSize(val dp: Dp) {
    SMALL(30.dp),
    MEDIUM(52.dp),
    HERO(118.dp)
}

/** A tiny code-drawn helper that makes Agent mode feel alive without another image asset. */
@Composable
fun KryzzBot(
    mood: BotMood = BotMood.IDLE,
    size: BotSize = BotSize.MEDIUM,
    modifier: Modifier = Modifier
) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val transition = rememberInfiniteTransition(label = "kryzz-bot-motion")
    val phase by if (motionEnabled) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "kryzz-bot-phase"
        )
    } else remember { mutableStateOf(0f) }
    val pulse by if (motionEnabled) {
        transition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(820, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "kryzz-bot-pulse"
        )
    } else remember { mutableStateOf(1f) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val errorColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val cycle = phase * 6.283185f
        val bob = if (motionEnabled && mood != BotMood.ERROR) sin(cycle) * h * 0.035f else 0f
        val hop = if (mood == BotMood.SUCCESS && motionEnabled && sin(cycle * 2f) > 0f) -h * 0.075f else 0f
        val wobble = if (mood == BotMood.ERROR && motionEnabled) sin(cycle * 3f) * w * 0.035f else 0f
        val dy = bob + hop

        if (mood == BotMood.TOOLING || mood == BotMood.LISTENING) {
            val ringAlpha = if (mood == BotMood.LISTENING) 0.2f else 0.13f
            drawCircle(
                color = secondary.copy(alpha = ringAlpha * pulse),
                radius = w * (0.42f + 0.045f * pulse),
                center = Offset(w * 0.5f, h * 0.47f + dy),
                style = Stroke(width = w * 0.018f)
            )
        }

        drawOval(
            color = Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(w * 0.2f + wobble, h * 0.87f + dy),
            size = Size(w * 0.6f, h * 0.075f)
        )

        val bodyWidth = w * 0.6f
        val bodyHeight = h * 0.43f
        val bodyLeft = (w - bodyWidth) * 0.5f + wobble
        val bodyTop = h * 0.37f + dy
        drawRoundRect(
            color = primary,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(bodyWidth * 0.24f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(bodyLeft + bodyWidth * 0.1f, bodyTop + bodyHeight * 0.09f),
            size = Size(bodyWidth * 0.8f, bodyHeight * 0.18f),
            cornerRadius = CornerRadius(bodyWidth * 0.08f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.52f),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(bodyWidth * 0.24f),
            style = Stroke(width = w * 0.012f)
        )

        val screenWidth = bodyWidth * 0.48f
        val screenHeight = bodyHeight * 0.28f
        val screenLeft = bodyLeft + (bodyWidth - screenWidth) * 0.5f
        val screenTop = bodyTop + bodyHeight * 0.38f
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = Offset(screenLeft, screenTop),
            size = Size(screenWidth, screenHeight),
            cornerRadius = CornerRadius(screenHeight * 0.32f)
        )
        val light = when (mood) {
            BotMood.ERROR -> errorColor
            BotMood.TOOLING -> Color(0xFFFFD84D)
            BotMood.SUCCESS -> Color(0xFFB8F7C4)
            else -> Color.White
        }
        drawCircle(light.copy(alpha = 0.9f), radius = screenHeight * 0.12f, center = Offset(screenLeft + screenWidth * 0.3f, screenTop + screenHeight * 0.5f))
        drawCircle(light.copy(alpha = 0.62f), radius = screenHeight * 0.12f, center = Offset(screenLeft + screenWidth * 0.5f, screenTop + screenHeight * 0.5f))
        drawCircle(light.copy(alpha = 0.36f), radius = screenHeight * 0.12f, center = Offset(screenLeft + screenWidth * 0.7f, screenTop + screenHeight * 0.5f))

        val headWidth = w * 0.68f
        val headHeight = h * 0.3f
        val headLeft = (w - headWidth) * 0.5f + wobble
        val headTop = h * 0.1f + dy
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(headWidth * 0.32f)
        )
        drawRoundRect(
            color = surfaceVariant.copy(alpha = 0.9f),
            topLeft = Offset(headLeft, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(headWidth * 0.32f),
            style = Stroke(width = w * 0.014f)
        )

        val visorWidth = headWidth * 0.78f
        val visorHeight = headHeight * 0.54f
        val visorLeft = headLeft + (headWidth - visorWidth) * 0.5f
        val visorTop = headTop + headHeight * 0.22f
        drawRoundRect(
            color = primary.copy(alpha = 0.95f),
            topLeft = Offset(visorLeft, visorTop),
            size = Size(visorWidth, visorHeight),
            cornerRadius = CornerRadius(visorWidth * 0.22f)
        )

        val eyeWidth = visorWidth * 0.21f
        val eyeHeight = visorHeight * 0.43f
        val eyeTop = visorTop + visorHeight * 0.33f
        val eyeHeightNow = if (mood == BotMood.THINKING) eyeHeight * 0.55f else eyeHeight
        val leftEye = headLeft + headWidth * 0.22f
        val rightEye = headLeft + headWidth * 0.57f
        val pupilShift = if (mood == BotMood.TOOLING) sin(cycle) * eyeWidth * 0.18f else 0f
        val sleepyOffset = if (mood == BotMood.THINKING) eyeHeight * 0.18f else 0f
        drawOval(Color.White, Offset(leftEye, eyeTop + sleepyOffset), Size(eyeWidth, eyeHeightNow))
        drawOval(Color.White, Offset(rightEye, eyeTop + sleepyOffset), Size(eyeWidth, eyeHeightNow))
        val pupilWidth = eyeWidth * 0.42f
        val pupilHeight = eyeHeightNow * 0.52f
        if (mood == BotMood.ERROR) {
            drawLine(Color(0xFF5A2032), Offset(leftEye, eyeTop), Offset(leftEye + eyeWidth, eyeTop + eyeHeight), strokeWidth = w * 0.018f)
            drawLine(Color(0xFF5A2032), Offset(leftEye + eyeWidth, eyeTop), Offset(leftEye, eyeTop + eyeHeight), strokeWidth = w * 0.018f)
            drawLine(Color(0xFF5A2032), Offset(rightEye, eyeTop), Offset(rightEye + eyeWidth, eyeTop + eyeHeight), strokeWidth = w * 0.018f)
            drawLine(Color(0xFF5A2032), Offset(rightEye + eyeWidth, eyeTop), Offset(rightEye, eyeTop + eyeHeight), strokeWidth = w * 0.018f)
        } else {
            drawOval(
                Color(0xFF1A1A1A),
                Offset(leftEye + (eyeWidth - pupilWidth) * 0.5f + pupilShift, eyeTop + (eyeHeightNow - pupilHeight) * 0.5f + sleepyOffset),
                Size(pupilWidth, pupilHeight)
            )
            drawOval(
                Color(0xFF1A1A1A),
                Offset(rightEye + (eyeWidth - pupilWidth) * 0.5f - pupilShift, eyeTop + (eyeHeightNow - pupilHeight) * 0.5f + sleepyOffset),
                Size(pupilWidth, pupilHeight)
            )
            drawCircle(Color.White, radius = pupilWidth * 0.22f, center = Offset(leftEye + eyeWidth * 0.63f + pupilShift, eyeTop + eyeHeightNow * 0.35f + sleepyOffset))
            drawCircle(Color.White, radius = pupilWidth * 0.22f, center = Offset(rightEye + eyeWidth * 0.63f - pupilShift, eyeTop + eyeHeightNow * 0.35f + sleepyOffset))
        }

        val blushAlpha = if (mood == BotMood.SUCCESS) 0.64f else 0.24f
        drawOval(Color(0xFFFF8EB3).copy(alpha = blushAlpha), Offset(headLeft + headWidth * 0.04f, headTop + headHeight * 0.62f), Size(headWidth * 0.14f, headHeight * 0.2f))
        drawOval(Color(0xFFFF8EB3).copy(alpha = blushAlpha), Offset(headLeft + headWidth * 0.82f, headTop + headHeight * 0.62f), Size(headWidth * 0.14f, headHeight * 0.2f))

        val smileLeft = headLeft + headWidth * 0.37f
        val smileTop = headTop + headHeight * 0.76f
        when (mood) {
            BotMood.ERROR -> drawArc(primary, 20f, 140f, false, Offset(smileLeft, smileTop - 2f), Size(headWidth * 0.25f, headHeight * 0.17f), style = Stroke(width = w * 0.014f))
            BotMood.SUCCESS -> drawRoundRect(primary, Offset(smileLeft, smileTop), Size(headWidth * 0.26f, headHeight * 0.1f), CornerRadius(headHeight * 0.05f))
            else -> drawArc(primary, 0f, 180f, false, Offset(smileLeft, smileTop), Size(headWidth * 0.28f, headHeight * 0.16f), style = Stroke(width = w * 0.015f))
        }

        val antennaX = w * 0.5f + wobble
        val antennaBase = Offset(antennaX, headTop + headHeight * 0.03f)
        val antennaLean = if (mood == BotMood.ERROR) w * 0.07f else 0f
        drawLine(primary.copy(alpha = 0.9f), antennaBase, Offset(antennaX + antennaLean, h * 0.02f + dy), strokeWidth = w * 0.018f)
        val orbRadius = w * 0.06f * pulse
        val orbCenter = Offset(antennaX + antennaLean, h * 0.015f + dy)
        drawCircle(primary.copy(alpha = 0.2f), radius = orbRadius * 1.6f, center = orbCenter)
        drawCircle(Color.White, radius = orbRadius, center = orbCenter)
        drawCircle(primary, radius = orbRadius * 0.62f, center = orbCenter)

        val armTop = bodyTop + bodyHeight * 0.27f
        val armWidth = bodyWidth * 0.16f
        val armHeight = bodyHeight * 0.23f
        val wave = if (mood == BotMood.SUCCESS && motionEnabled) sin(cycle * 2f) * armHeight * 0.28f else 0f
        drawRoundRect(Color.White, Offset(bodyLeft - armWidth * 0.42f, armTop + wave), Size(armWidth, armHeight), CornerRadius(armWidth * 0.5f))
        drawRoundRect(Color.White, Offset(bodyLeft + bodyWidth - armWidth * 0.58f, armTop - wave), Size(armWidth, armHeight), CornerRadius(armWidth * 0.5f))

        val legTop = bodyTop + bodyHeight - 1f
        val legWidth = bodyWidth * 0.18f
        val legHeight = h * 0.09f
        drawRoundRect(surfaceVariant, Offset(bodyLeft + bodyWidth * 0.18f, legTop), Size(legWidth, legHeight), CornerRadius(legHeight * 0.45f))
        drawRoundRect(surfaceVariant, Offset(bodyLeft + bodyWidth * 0.64f, legTop), Size(legWidth, legHeight), CornerRadius(legHeight * 0.45f))

        if (mood == BotMood.TOOLING && motionEnabled) {
            val spark = (phase * 2f) % 1f
            drawCircle(Color(0xFFFFD84D), radius = w * 0.014f, center = Offset(w * (0.76f + 0.1f * sin(spark * 6.283185f)), h * (0.2f + 0.08f * spark)))
            drawCircle(Color(0xFFFFD84D).copy(alpha = 0.72f), radius = w * 0.01f, center = Offset(w * 0.16f, h * (0.23f + 0.07f * spark)))
        }
        if (mood == BotMood.LISTENING && motionEnabled) {
            val listen = phase
            drawCircle(primary.copy(alpha = 0.24f - listen * 0.14f), radius = w * (0.08f + listen * 0.13f), center = orbCenter, style = Stroke(width = w * 0.012f))
        }
    }
}
