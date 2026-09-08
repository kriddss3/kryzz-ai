package ai.daylight.assistant.ui.agent

import android.graphics.Movie
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlin.math.min

/**
 * The three animated mascot GIF states used across Agent mode.
 *
 *  - [STANDBY]   : the resting pose (the default / "online" look)
 *  - [THINKING]  : shown while the model is reasoning or running tools
 *  - [SPEAKING]  : shown while a streamed answer is being written out
 */
enum class MascotState { STANDBY, THINKING, SPEAKING }

private fun MascotState.rawRes(): Int = when (this) {
    MascotState.STANDBY -> R.raw.mascot_standby
    MascotState.THINKING -> R.raw.mascot_thinking
    MascotState.SPEAKING -> R.raw.mascot_speaking
}

/** Maps the legacy [BotMood] used by older call sites onto a mascot GIF state. */
internal fun BotMood.toMascotState(): MascotState = when (this) {
    BotMood.THINKING, BotMood.TOOLING -> MascotState.THINKING
    BotMood.SPEAKING -> MascotState.SPEAKING
    else -> MascotState.STANDBY
}

/**
 * Renders one of the mascot GIFs from `res/raw`, animated frame-by-frame on a Compose
 * [Canvas]. The decoding uses the platform [Movie] decoder so no extra image library is
 * required. When motion is disabled only the first frame is drawn.
 */
@Composable
fun MascotGif(
    state: MascotState,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val motionEnabled = LocalKryzzMotionEnabled.current
    val resId = state.rawRes()
    val movie = remember(resId) {
        runCatching {
            context.resources.openRawResource(resId).use { stream ->
                Movie.decodeStream(stream)
            }
        }.getOrNull()
    }
    var tickMs by remember(resId) { mutableLongStateOf(0L) }

    LaunchedEffect(resId, motionEnabled) {
        if (!motionEnabled || movie == null || movie.duration() <= 0) {
            tickMs = 0L
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val elapsed = ((nowNanos - startNanos) / 1_000_000L).coerceAtLeast(0L)
            tickMs = elapsed
        }
    }

    val duration = (movie?.duration() ?: 0).coerceAtLeast(1)
    val relMs = if (motionEnabled && movie != null) (tickMs % duration).toInt() else 0

    Canvas(modifier = modifier.then(Modifier.size(size))) {
        val movieObj = movie ?: return@Canvas
        val w = this.size.width
        val h = this.size.height
        if (w <= 0f || h <= 0f) return@Canvas
        movieObj.setTime(relMs)
        val mw = movieObj.width().coerceAtLeast(1)
        val mh = movieObj.height().coerceAtLeast(1)
        val scale = min(w / mw, h / mh)
        val dx = (w - mw * scale) * 0.5f
        val dy = (h - mh * scale) * 0.5f
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val saveCount = native.save()
            native.scale(scale, scale)
            native.translate(dx / scale, dy / scale)
            movieObj.draw(native, 0f, 0f)
            native.restoreToCount(saveCount)
        }
    }
}
