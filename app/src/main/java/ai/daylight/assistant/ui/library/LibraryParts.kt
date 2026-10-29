package ai.daylight.assistant.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.ConversationFolderEntity
import ai.daylight.assistant.data.local.ConversationSummary
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotDialog
import ai.daylight.assistant.ui.dot.DotDialogTitle
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPill
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.OptionRow
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.tune.TuneChoiceRow
import ai.daylight.assistant.ui.tune.TuneEmpty

/*
 * The chat library drawer (components.md section 11): CHATS in dot text, a search field,
 * a NEW CHAT pill, folders, then 64dp rows under date headers. The open chat has a 2dp
 * yellow bar at the start edge, the only yellow in the list.
 */

/**
 * The drawer's title row: CHATS in dot text with the count, the backup menu and close.
 * Three quick taps on the title open the About card, as the old logo did; a plain tap
 * detector adds no click to the semantics, so TalkBack reads a heading only.
 */
@Composable
internal fun LibraryHeader(count: Int, onTitleTap: () -> Unit, onBackup: () -> Unit, onClose: (() -> Unit)?) {
    val c = DotTheme.c
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .pointerInput(Unit) { detectTapGestures { onTitleTap() } }
                .semantics(mergeDescendants = true) { heading() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            DotMatrixText("CHATS", pitch = 3.6.dp, on = c.textPrimary, off = Color.Transparent, contentDescription = "Chats")
            val caption = LibraryModel.countCaption(count)
            if (caption.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text(caption, style = DotType.Caption, color = c.textTertiary, maxLines = 1, softWrap = false)
            }
        }
        DotIconButton(R.drawable.ic_kz_more_vertical, "Backup and restore", onBackup, tint = c.textSecondary)
        if (onClose != null) DotIconButton(R.drawable.ic_kz_close, "Close menu", onClose, tint = c.textSecondary)
    }
}

/** The kit's NewChatButton: a full-width surfaceHigh pill with the chat-new icon. */
@Composable
internal fun NewChatPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(DotRadius.Pill)
            .background(c.surfaceHigh)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(R.drawable.ic_kz_chat_new), null, Modifier.size(18.dp), tint = c.textPrimary)
        Spacer(Modifier.width(10.dp))
        Text("NEW CHAT", style = DotType.Label.copy(fontSize = 12.sp), color = c.textPrimary)
    }
}

/**
 * Folders: a FOLDERS label with create (and rename and delete for the chosen folder),
 * then the folder pills. The pills wrap instead of scrolling sideways, so a swipe over
 * them still closes the drawer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FolderStrip(
    folders: List<ConversationFolderEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onRename: (ConversationFolderEntity) -> Unit,
    onDelete: (ConversationFolderEntity) -> Unit,
) {
    val c = DotTheme.c
    val selected = folders.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("FOLDERS", Modifier.weight(1f).semantics { heading() }, style = DotType.Label, color = c.textSecondary)
            if (selected != null) {
                DotIconButton(R.drawable.ic_kz_edit, "Rename ${selected.name}", { onRename(selected) }, tint = c.textSecondary, iconSize = 18.dp)
                DotIconButton(R.drawable.ic_kz_trash, "Delete ${selected.name}", { onDelete(selected) }, tint = c.textSecondary, iconSize = 18.dp)
            }
            DotIconButton(R.drawable.ic_kz_plus, "Create folder", onCreate, Modifier.testTag("create_folder"), tint = c.textSecondary, iconSize = 20.dp)
        }
        if (folders.isEmpty()) {
            Text(
                "NONE YET. + MAKES ONE",
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                style = DotType.Caption,
                color = c.textTertiary
            )
        } else {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DotPill("All", selected = selectedId == null, onClick = { onSelect(null) })
                folders.forEach { folder ->
                    DotPill(
                        folder.name,
                        selected = folder.id == selectedId,
                        onClick = { onSelect(if (folder.id == selectedId) null else folder.id) },
                        icon = R.drawable.ic_kz_folder
                    )
                }
            }
        }
    }
}

/** A date header: Space Mono caps in textTertiary ("TODAY", "( 23 ) OCT"). */
@Composable
internal fun LibraryGroupHeader(label: String) {
    Text(
        label,
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
        style = DotType.Label,
        color = DotTheme.c.textTertiary
    )
}

/**
 * One chat: 64dp, the title in Body over a mono caption ("2H AGO · RUN"), a pin when
 * pinned, and the actions button. Long-press opens the same actions. The open chat gets
 * a 2dp yellow bar at the start edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryRow(
    item: ConversationSummary,
    caption: String,
    active: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit,
) {
    val c = DotTheme.c
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                .testTag("library_conversation_row")
                .combinedClickable(
                    onClickLabel = "Open",
                    onLongClickLabel = "Chat actions",
                    onClick = onOpen,
                    onLongClick = onActions
                )
                .semantics { if (active) stateDescription = "Open now" }
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.pinned) {
                        Icon(painterResource(R.drawable.ic_kz_pin), "Pinned", Modifier.size(14.dp), tint = c.textTertiary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(item.title, style = DotType.Body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    caption,
                    Modifier.padding(top = 3.dp),
                    style = DotType.Caption,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DotIconButton(R.drawable.ic_kz_more_vertical, "Actions for ${item.title}", onActions, tint = c.textTertiary, iconSize = 20.dp)
        }
        if (active) {
            Box(Modifier.align(Alignment.CenterStart).width(2.dp).height(32.dp).background(c.accent))
        }
        HorizontalDivider(
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp),
            thickness = 1.dp,
            color = c.surfaceRaised
        )
    }
}

/** The kit empty state (section 10) sized for the drawer. */
@Composable
internal fun LibraryEmpty(@DrawableRes dotIcon: Int, words: String, body: String) {
    TuneEmpty(dotIcon = dotIcon, words = words, body = body, modifier = Modifier.padding(top = 36.dp, bottom = 24.dp))
}

/** A chat's actions, from its actions button or a long-press. */
@Composable
internal fun ChatActionsSheet(
    item: ConversationSummary,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    DotSheet(onDismiss) {
        SheetLabel(item.title)
        OptionRow(R.drawable.ic_kz_pin, if (item.pinned) "Unpin" else "Pin", onClick = { onDismiss(); onPin() })
        OptionRow(R.drawable.ic_kz_edit, "Rename", onClick = { onDismiss(); onRename() })
        OptionRow(R.drawable.ic_kz_folder, "Move to folder", onClick = { onDismiss(); onMove() })
        OptionRow(R.drawable.ic_kz_trash, "Delete", onClick = { onDismiss(); onDelete() })
    }
}

/** Export or import a chat backup (a JSON file the user picks). */
@Composable
internal fun BackupSheet(onExport: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    val c = DotTheme.c
    DotSheet(onDismiss) {
        SheetLabel("Backup and restore")
        OptionRow(R.drawable.ic_kz_export, "Export chat backup", onClick = { onDismiss(); onExport() })
        OptionRow(R.drawable.ic_kz_download, "Import chat backup", onClick = { onDismiss(); onImport() })
        Text(
            "A backup holds your chats, folders and bots. It never holds your keys or routines.",
            Modifier.padding(top = 14.dp),
            style = DotType.BodySmall,
            color = c.textTertiary
        )
    }
}

/**
 * Pick a chat's folder: No folder or one of the folders, as kit radio rows, and New folder
 * (which creates one and moves the chat into it).
 */
@Composable
internal fun FolderPickSheet(
    currentFolderId: String?,
    folders: List<ConversationFolderEntity>,
    onPick: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    DotSheet(onDismiss) {
        SheetLabel("Move to folder")
        TuneChoiceRow("No folder", null, selected = currentFolderId == null, onClick = { onPick(null) })
        folders.forEach { folder ->
            TuneChoiceRow(folder.name, null, selected = folder.id == currentFolderId, onClick = { onPick(folder.id) })
        }
        OptionRow(R.drawable.ic_kz_plus, "New folder", onClick = { onDismiss(); onNewFolder() })
    }
}

/**
 * A one-field naming dialog (rename a chat, name a folder): the kit dialog with a focused
 * field, Cancel and Save. [maxLength] caps the text and shows a count.
 */
@Composable
internal fun NameDialog(
    title: String,
    label: String,
    initial: String = "",
    maxLength: Int? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun save() {
        if (value.isNotBlank()) onSave(value.trim())
    }
    DotDialog(onDismiss) {
        DotDialogTitle(title)
        DotField(
            value = value,
            onValueChange = { value = if (maxLength != null) it.take(maxLength) else it },
            modifier = Modifier.padding(top = 20.dp),
            label = label,
            supportingEnd = maxLength?.let { "${value.length}/$it" },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            fieldModifier = Modifier.focusRequester(focus)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            DotPillButton("Cancel", onDismiss, style = DotButtonStyle.Ghost)
            DotPillButton("Save", ::save, enabled = value.isNotBlank())
        }
    }
}
