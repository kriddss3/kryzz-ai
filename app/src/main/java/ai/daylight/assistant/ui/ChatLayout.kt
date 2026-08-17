package ai.daylight.assistant.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.domain.ChatDensity

internal fun ChatDensity.messageSpacing(): Dp = when (this) {
    ChatDensity.COMPACT -> 8.dp
    ChatDensity.COMFORTABLE -> 12.dp
    ChatDensity.SPACIOUS -> 16.dp
}

internal fun ChatDensity.composerVerticalPadding(): Dp = when (this) {
    ChatDensity.COMPACT -> 4.dp
    ChatDensity.COMFORTABLE -> 8.dp
    ChatDensity.SPACIOUS -> 12.dp
}
