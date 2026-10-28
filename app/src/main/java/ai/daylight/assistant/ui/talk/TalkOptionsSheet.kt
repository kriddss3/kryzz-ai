package ai.daylight.assistant.ui.talk

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotSheetHandle
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotToggle

/**
 * Talk options, opened from the "+" on the reply line. Holds what used to be header and
 * composer chrome: the Talk/Run switch (fresh chats only), attach, web search, the model
 * and AI controls, voice chat, the chat library and New chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkOptionsSheet(
    modeLocked: Boolean,
    webSearch: Boolean,
    modelLabel: String,
    onRun: () -> Unit,
    onWebSearch: (Boolean) -> Unit,
    onModel: () -> Unit,
    onPickImage: () -> Unit,
    onPickCamera: () -> Unit,
    onPickFile: () -> Unit,
    onVoice: () -> Unit,
    onChats: () -> Unit,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    DotSheet(onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            if (!modeLocked) {
                SheetLabel("Mode")
                Segment(
                    left = "Talk",
                    right = "Run",
                    rightSelected = false,
                    onLeft = onDismiss,
                    onRight = onRun
                )
                Spacer(Modifier.height(20.dp))
            }
            SheetLabel("Attach")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AttachTile(R.drawable.ic_kz_image, "Image", onPickImage, Modifier.weight(1f))
                AttachTile(R.drawable.ic_kz_camera, "Camera", onPickCamera, Modifier.weight(1f))
                AttachTile(R.drawable.ic_kz_file, "File", onPickFile, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            OptionRow(R.drawable.ic_kz_web, "Web search", onClick = { onWebSearch(!webSearch) }) {
                DotToggle(checked = webSearch, onChange = onWebSearch)
            }
            OptionRow(R.drawable.ic_kz_model, "Model", value = modelLabel, onClick = onModel)
            OptionRow(R.drawable.ic_kz_voice_wave, "Voice chat", value = "In this chat", onClick = onVoice)
            OptionRow(R.drawable.ic_kz_history, "Chats", onClick = onChats)
            OptionRow(R.drawable.ic_kz_chat_new, "New chat", onClick = onNewChat)
        }
    }
}

/**
 * The kit sheet every Talk and Run sheet uses: 32dp top corners, surface, the 32x4 handle,
 * 20dp side gutters. The content scrolls when it is taller than the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DotSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = DotTheme.c
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = DotRadius.Sheet,
        containerColor = c.surface,
        contentColor = c.textPrimary,
        tonalElevation = 0.dp,
        dragHandle = { DotSheetHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            content = content
        )
    }
}

/** One action in a long-press sheet. */
data class TalkAction(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

/** The long-press sheet on a question or an answer: copy, edit, regenerate and so on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkActionsSheet(title: String, actions: List<TalkAction>, onDismiss: () -> Unit) {
    DotSheet(onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            SheetLabel(title)
            actions.forEach { action ->
                OptionRow(action.icon, action.label, onClick = { onDismiss(); action.onClick() })
            }
        }
    }
}

@Composable
internal fun SheetLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(bottom = 10.dp),
        style = DotType.Label,
        color = DotTheme.c.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Kit segmented control: raised track, the selected segment is white with black text. */
@Composable
internal fun Segment(left: String, right: String, rightSelected: Boolean, onLeft: () -> Unit, onRight: () -> Unit) {
    val c = DotTheme.c
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(c.surfaceRaised, DotRadius.Pill)
            .border(1.dp, c.border, DotRadius.Pill)
            .padding(3.dp)
    ) {
        listOf(left to !rightSelected, right to rightSelected).forEachIndexed { index, (label, selected) ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(DotRadius.Pill)
                    .background(if (selected) c.textPrimary else c.surfaceRaised)
                    .clickable(onClick = if (index == 0) onLeft else onRight)
                    .semantics { role = Role.Tab; this.selected = selected },
                contentAlignment = Alignment.Center
            ) {
                Text(label.uppercase(), style = DotType.Label, color = if (selected) c.bg else c.textSecondary)
            }
        }
    }
}

@Composable
internal fun AttachTile(@DrawableRes icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Column(
        modifier
            .height(72.dp)
            .clip(DotRadius.Tile)
            .background(c.surfaceRaised)
            .border(1.dp, c.border, DotRadius.Tile)
            .clickable(onClickLabel = "Attach ${label.lowercase()}", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(label.uppercase(), style = DotType.Label, color = c.textSecondary)
    }
}

/** A ruled 52dp row: icon, title, optional value and trailing control. */
@Composable
internal fun OptionRow(
    @DrawableRes icon: Int,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = DotTheme.c
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = c.textPrimary)
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), style = DotType.Body.copy(fontSize = 16.sp), color = c.textPrimary)
            if (value != null) {
                Text(
                    value.uppercase(),
                    Modifier.widthIn(max = 160.dp),
                    style = DotType.Caption,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}
