package ai.daylight.assistant.ui.agent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Drop-in replacement for the code-drawn [KryzzBot]. It maps the legacy [BotMood] onto a
 * mascot GIF state and renders the matching animation, so every existing call site that
 * showed the old vector bot now shows the mascot GIF instead.
 */
@Composable
fun KryzzMascot(
    mood: BotMood = BotMood.IDLE,
    size: BotSize = BotSize.MEDIUM,
    modifier: Modifier = Modifier
) {
    MascotGif(
        state = mood.toMascotState(),
        size = size.dp,
        modifier = modifier
    )
}
