package ai.daylight.assistant.ui.dot

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties

/** Kit dialog shape (components.md §14): 32dp corners. */
val DotDialogShape = RoundedCornerShape(32.dp)

/**
 * Kit dialog (components.md §14): surfaceRaised, 32dp corners, a 1dp border, 24dp padding,
 * at most 560dp wide. It keeps clear of the system bars and the keyboard. [dismissible]
 * false keeps it open on Back and outside taps (while something is saving).
 */
@Composable
fun DotDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DotTheme.c
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Compose measures the content at full screen height, but the platform places the
        // dialog window below the status bar, so the bottom of a tall dialog fell off the
        // screen (and under the keyboard). Let the window cover the whole screen; the content
        // below pads itself for the system bars and the keyboard.
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val attributes = window.attributes
                if (attributes.fitInsetsTypes != 0) {
                    attributes.fitInsetsTypes = 0
                    window.attributes = attributes
                }
            }
        }
        // The content fills the window, so the platform never sees an "outside" tap: the
        // backdrop handles it, and the card swallows taps that land on its own background.
        // Plain tap detectors add no semantics, so TalkBack does not offer the backdrop.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(dismissible) { detectTapGestures { if (dismissible) onDismiss() } }
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .background(c.surfaceRaised, DotDialogShape)
                    .border(1.dp, c.border, DotDialogShape)
                    .then(if (padded) Modifier.padding(24.dp) else Modifier),
                content = content
            )
        }
    }
}

/** A dialog title: Geist Headline (§14). */
@Composable
fun DotDialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = DotType.Headline, color = DotTheme.c.textPrimary)
}

/**
 * A yes/no dialog: title, body, then right-aligned pills (a ghost Cancel and the confirm).
 * A [destructive] confirm is red, because deleting is not a "live" yellow action.
 */
@Composable
fun DotConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismiss: String = "Cancel",
) {
    DotDialog(onDismiss) {
        DotDialogTitle(title)
        Text(body, Modifier.padding(top = 12.dp), style = DotType.Body, color = DotTheme.c.textSecondary)
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            DotPillButton(dismiss, onDismiss, style = DotButtonStyle.Ghost)
            DotPillButton(confirm, onConfirm, style = if (destructive) DotButtonStyle.Danger else DotButtonStyle.Accent)
        }
    }
}
