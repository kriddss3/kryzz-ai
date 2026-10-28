package ai.daylight.assistant.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ai.daylight.assistant.ui.dot.mascot.DotSquid
import ai.daylight.assistant.ui.dot.mascot.SquidPose
import ai.daylight.assistant.ui.dot.mascot.squidPitchFor

/**
 * The mascot's three states across the app:
 *  - [STANDBY]: resting
 *  - [THINKING]: the model is reasoning or running tools
 *  - [SPEAKING]: a streamed answer is being written out
 */
enum class MascotState { STANDBY, THINKING, SPEAKING }

/** Maps the legacy [BotMood] used by older call sites onto a mascot state. */
internal fun BotMood.toMascotState(): MascotState = when (this) {
    BotMood.THINKING, BotMood.TOOLING -> MascotState.THINKING
    BotMood.SPEAKING -> MascotState.SPEAKING
    else -> MascotState.STANDBY
}

/** The squid pose that shows a mascot state. */
internal val MascotState.pose: SquidPose
    get() = when (this) {
        MascotState.STANDBY -> SquidPose.STANDBY
        MascotState.THINKING -> SquidPose.THINKING
        MascotState.SPEAKING -> SquidPose.SPEAKING
    }

/** The squid mascot in a square of [size], centred. */
@Composable
fun KryzzMascot(
    state: MascotState,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        DotSquid(pose = state.pose, pitch = squidPitchFor(size))
    }
}

/**
 * Drop-in for the old code-drawn bot: maps the legacy [BotMood] onto a squid pose, so
 * every call site that showed the robot (and later the GIF) now shows the dot squid.
 */
@Composable
fun KryzzMascot(
    mood: BotMood = BotMood.IDLE,
    size: BotSize = BotSize.MEDIUM,
    modifier: Modifier = Modifier
) {
    KryzzMascot(state = mood.toMascotState(), size = size.dp, modifier = modifier)
}
