package ai.daylight.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotMotion
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.effects.DotReveal
import ai.daylight.assistant.ui.dot.effects.dotGrid
import ai.daylight.assistant.ui.dot.effects.flickerIn
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.delay

/**
 * App background: the kit's flat field (no gradients, no glass, no blur). Saved
 * background, blur and gradient settings from before the redesign are ignored.
 *
 * [dotGrid] adds the kit's dot grid (components.md section 1: text colour at 6%, 8dp pitch,
 * 1dp dots). Tune's "Dot background" switch turns it on for [dotGridForRoute].
 */
@Composable
fun DaylightBackground(
    modifier: Modifier = Modifier,
    dotGrid: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val grid = if (dotGrid) Modifier.dotGrid(DotTheme.c.textPrimary.copy(alpha = DOT_GRID_ALPHA)) else Modifier
    Box(modifier.fillMaxSize().background(scheme.background).then(grid)) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

/** The kit's dot-grid strength behind text (components.md section 1). */
internal const val DOT_GRID_ALPHA = 0.06f

/** Where the dot background shows: Home and the chat arms (Talk, Run). Voice draws its own. */
internal fun dotGridForRoute(route: String?): Boolean =
    route == Routes.HOME || route == Routes.CHAT || route?.startsWith("chat/") == true

/** Launch intro timing: the dot reveal, then a short hold on the full word (900ms in all). */
internal object LaunchIntroSpec {
    const val REVEAL_MS = DotMotion.Reveal
    const val HOLD_MS = 300L
    const val TOTAL_MS = REVEAL_MS + HOLD_MS
}

/**
 * Launch intro (phase D): "KRYZZ" in 5x7 dot text that powers on with a random dot reveal
 * and the kit's flicker, then hands over. With motion off it hands over at once.
 */
@Composable
fun KryzzLaunchIntro(onFinished: () -> Unit) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        if (motionEnabled) delay(LaunchIntroSpec.TOTAL_MS)
        currentOnFinished()
    }

    val c = DotTheme.c
    Box(
        Modifier.fillMaxSize().background(c.bgAlt),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp).flickerIn()
        ) {
            DotMatrixText(
                "KRYZZ",
                pitch = 9.dp,
                on = c.textPrimary,
                off = c.dotOff,
                reveal = DotReveal.Random,
                durationMs = LaunchIntroSpec.REVEAL_MS,
                contentDescription = "Kryzz AI"
            )
            Text(
                text = "PRIVATE  /  LOCAL-FIRST",
                modifier = Modifier.padding(top = 20.dp),
                color = c.textTertiary,
                style = DotType.Label
            )
        }
    }
}
