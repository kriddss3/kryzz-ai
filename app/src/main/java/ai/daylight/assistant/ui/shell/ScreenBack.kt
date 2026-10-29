package ai.daylight.assistant.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Back presses a screen wants before the shell's own Back (which leaves the arm for Home),
 * such as closing Find's viewer sheet.
 *
 * A screen-level BackHandler isn't enough: when the app comes back from another app (the
 * browser, the share sheet), Android re-adds back callbacks in a new order and the shell's
 * handler can end up first, so Back left the arm with the sheet still open. The shell asks
 * this registry first instead, the same way it closes the drawer first.
 */
@Stable
class ScreenBackState {
    private class Entry(val onBack: () -> Unit)

    private val entries = mutableStateListOf<Any>()

    /** True while a screen wants the next Back. */
    val active: Boolean get() = entries.isNotEmpty()

    /** Runs the newest screen's Back. Returns false when no screen wants it. */
    fun handle(): Boolean {
        val entry = entries.lastOrNull() as? Entry ?: return false
        entry.onBack()
        return true
    }

    internal fun add(onBack: () -> Unit): Any = Entry(onBack).also { entries.add(it) }

    internal fun remove(token: Any) {
        entries.remove(token)
    }
}

val LocalScreenBack = staticCompositionLocalOf<ScreenBackState?> { null }

/**
 * Like BackHandler, but handled by the shell before it leaves the arm (see [ScreenBackState]).
 * Outside the shell (previews, tests) it falls back to a plain BackHandler.
 */
@Composable
fun ScreenBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val state = LocalScreenBack.current
    if (state == null) {
        BackHandler(enabled = enabled, onBack = onBack)
        return
    }
    val current by rememberUpdatedState(onBack)
    val action = remember { { current() } }
    DisposableEffect(state, enabled) {
        val token = if (enabled) state.add(action) else null
        onDispose { token?.let(state::remove) }
    }
}
