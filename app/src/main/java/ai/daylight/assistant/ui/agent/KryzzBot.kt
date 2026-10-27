package ai.daylight.assistant.ui.agent

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Agent mood and size used by the mascot. The old code-drawn robot was removed in the
 * Dot redesign; the squid mascot is drawn from these states.
 */
enum class BotMood { IDLE, THINKING, TOOLING, SPEAKING, SUCCESS, ERROR, LISTENING }

enum class BotSize(val dp: Dp) {
    SMALL(30.dp),
    MEDIUM(52.dp),
    HERO(118.dp)
}
