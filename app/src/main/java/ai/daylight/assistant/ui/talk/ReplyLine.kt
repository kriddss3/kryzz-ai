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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.ChatAttachment
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.dotRipple
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled

/** What the reply line's placeholder says right now. */
internal fun replyPlaceholder(generating: Boolean, editing: Boolean, dictating: Boolean, idle: String = "Reply"): String = when {
    dictating -> "Listening…"
    editing -> "Edit and resend"
    generating -> "Queue a follow-up"
    else -> idle
}

/** v2.1: the idle placeholder says the field can be held to talk. */
internal fun holdPlaceholder(idle: String): String = "$idle, or hold to talk"

/**
 * Talk's reply line: no box, just a hairline and the text. Idle it shows "Reply" and the
 * dictation mic; while a reply streams it reads "Queue a follow-up" and shows a white stop
 * button. The "+" opens the Talk options sheet (attach, web, model, voice, chats).
 * Run uses the same line with its own [idlePlaceholder], [optionsLabel] and [notes]
 * (rows drawn above the rule, such as the chosen workflow).
 */
@Composable
fun ReplyLine(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    editing: Boolean,
    queued: String?,
    attachments: List<ChatAttachment>,
    dictating: Boolean,
    dictationLevel: Float,
    verticalPadding: Dp,
    onSend: () -> Unit,
    onQueue: () -> Unit,
    onStop: () -> Unit,
    onDictation: () -> Unit,
    onOptions: () -> Unit,
    onCancelEdit: () -> Unit,
    onUnqueue: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    idlePlaceholder: String = "Reply",
    optionsLabel: String = "Talk options",
    notes: @Composable ColumnScope.() -> Unit = {},
    transcribing: Boolean = false,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: ((cancelled: Boolean) -> Unit)? = null,
) {
    val c = DotTheme.c
    val hasText = value.isNotBlank()
    val placeholder = replyPlaceholder(generating, editing, dictating, idlePlaceholder)
    // v2.1 hold-to-talk: while the field is not focused a hold dictates into it.
    val ownRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownRequester
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var cancelZone by remember { mutableStateOf(false) }
    val holdEnabled = onHoldStart != null && onHoldEnd != null
    val canSend = !generating && (hasText || attachments.isNotEmpty())
    // Phase D: a send ripples yellow dots out from the send button along the line.
    var ripple by remember { mutableIntStateOf(0) }
    val sendInset = with(LocalDensity.current) { Offset(24.dp.toPx(), 28.dp.toPx()) }
    val send = {
        ripple++
        onSend()
    }
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 20.dp, end = 8.dp, bottom = verticalPadding)
            .dotRipple(ripple, origin = { size -> Offset(size.width - sendInset.x, size.height - sendInset.y) })
    ) {
        notes()
        if (!queued.isNullOrBlank()) {
            NoteRow(label = "QUEUED", text = queued, action = "Cancel", onAction = onUnqueue)
        }
        if (editing) {
            NoteRow(label = "EDITING", text = "An earlier message", action = "Cancel", onAction = onCancelEdit)
        }
        HoldToTalkModel.note(holding, cancelZone, dictating, transcribing)?.let { (label, hint) -> HoldNote(label, hint) }
        if (attachments.isNotEmpty()) {
            LazyRow(
                Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(attachments, key = { it.id }) { attachment ->
                    AttachmentPill(attachment.name, onRemove = { onRemoveAttachment(attachment.id) })
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.border)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(R.drawable.ic_kz_plus, optionsLabel, onOptions, filled = false)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChanged(it.isFocused)
                        }
                        .semantics { contentDescription = placeholder },
                    textStyle = DotType.Answer.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.accentText),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (generating) onQueue() else if (canSend) send() }),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                if (holdEnabled && !focused && !dictating && !editing && !generating) holdPlaceholder(idlePlaceholder) else placeholder,
                                style = DotType.Answer,
                                color = c.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                )
                if (holdEnabled && !focused) {
                    HoldToTalkSurface(
                        onTap = {
                            runCatching { requester.requestFocus() }
                            keyboard?.show()
                        },
                        onHoldStart = {
                            holding = true
                            cancelZone = false
                            onHoldStart?.invoke()
                        },
                        onCancelZone = { cancelZone = it },
                        onHoldEnd = { cancelled ->
                            holding = false
                            cancelZone = false
                            onHoldEnd?.invoke(cancelled)
                        },
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            when {
                dictating -> ReplyMic(active = true, level = dictationLevel, onClick = onDictation)
                generating -> {
                    if (hasText) {
                        RoundIcon(R.drawable.ic_kz_forward, "Queue follow-up", onQueue, filled = false)
                        Spacer(Modifier.width(6.dp))
                    }
                    StopButton(onStop)
                }
                canSend -> RoundIcon(R.drawable.ic_kz_send, "Send", send, filled = true)
                else -> ReplyMic(active = dictating, level = dictationLevel, onClick = onDictation)
            }
        }
    }
}

/** A note above the reply line: a yellow mono label, a line of text and a text action. */
@Composable
internal fun NoteRow(label: String, text: String, action: String, onAction: () -> Unit, accent: Boolean = true) {
    val c = DotTheme.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = DotType.Label, color = if (accent) c.accentText else c.textTertiary)
        Spacer(Modifier.width(10.dp))
        Text(
            text.replace('\n', ' '),
            Modifier.weight(1f),
            style = DotType.BodySmall,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            action.uppercase(),
            Modifier
                .clip(DotRadius.Pill)
                .clickable(onClick = onAction)
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            style = DotType.Label,
            color = c.textPrimary
        )
    }
}

@Composable
private fun AttachmentPill(name: String, onRemove: () -> Unit) {
    val c = DotTheme.c
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .background(c.surfaceRaised, DotRadius.Pill)
            .border(1.dp, c.border, DotRadius.Pill)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_kz_file), null, Modifier.size(14.dp), tint = c.textSecondary)
        Text(
            " $name",
            Modifier.widthIn(max = 160.dp),
            style = DotType.Caption,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Remove $name", onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_kz_close), "Remove $name", Modifier.size(14.dp), tint = c.textSecondary)
        }
    }
}

/** A 40dp round button with a 48dp touch target. Filled = white circle with a black icon. */
@Composable
internal fun RoundIcon(@DrawableRes icon: Int, description: String, onClick: () -> Unit, filled: Boolean) {
    val c = DotTheme.c
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(40.dp)
                .then(if (filled) Modifier.background(c.textPrimary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = if (filled) c.bg else c.textPrimary)
        }
    }
}

/** The kit's stop: a 40dp white circle with a black rounded square. */
@Composable
private fun StopButton(onClick: () -> Unit) {
    val c = DotTheme.c
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Stop generation"; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(40.dp).background(c.textPrimary, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).background(c.bg, RoundedCornerShape(2.dp)))
        }
    }
}

/** Dictation mic. While listening a white ring grows with the voice level. */
@Composable
internal fun ReplyMic(active: Boolean, level: Float, onClick: () -> Unit) {
    val c = DotTheme.c
    val motion = LocalKryzzMotionEnabled.current
    val ring = if (active && motion) 1f + level.coerceIn(0f, 1f) * 0.35f else 1f
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (active) "Stop dictation" else "Dictation"; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            Box(
                Modifier
                    .size(36.dp)
                    .graphicsLayer { scaleX = ring; scaleY = ring }
                    .border(1.5.dp, c.textPrimary, CircleShape)
            )
        }
        Icon(painterResource(R.drawable.ic_kz_mic), null, Modifier.size(22.dp), tint = c.textPrimary)
    }
}
