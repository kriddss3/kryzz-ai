package ai.daylight.assistant.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType

/**
 * The lock screen, in onboarding's layout: the 96dp lock dot icon, LOCKED in the dot face, a
 * sentence on how to open the app, the last prompt error in red, and the yellow Unlock button
 * at the bottom where the thumb is.
 */
@Composable
fun LockScreen(
    method: UnlockMethod,
    message: String?,
    onUnlock: () -> Unit,
    onSecuritySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(painterResource(R.drawable.ic_kz_lock_dot), null, Modifier.size(96.dp), tint = c.textPrimary)
            Spacer(Modifier.height(28.dp))
            Text(
                "LOCKED",
                Modifier.clearAndSetSemantics {
                    heading()
                    contentDescription = "Kryzz AI is locked"
                },
                style = DotType.DotDisplay,
                color = c.textPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(LockModel.body(method), style = DotType.Body, color = c.textSecondary)
            if (!message.isNullOrBlank()) {
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        painterResource(R.drawable.ic_kz_warning), null,
                        Modifier.padding(top = 1.dp, end = 8.dp).size(16.dp),
                        tint = c.error
                    )
                    Text(message, style = DotType.BodySmall, color = c.error)
                }
            }
        }
        DotPillButton(
            LockModel.button(method),
            onClick = if (method == UnlockMethod.NONE) onSecuritySettings else onUnlock,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)
                .testTag("unlock_button"),
            icon = if (method == UnlockMethod.NONE) R.drawable.ic_kz_settings else R.drawable.ic_kz_unlock
        )
    }
}
