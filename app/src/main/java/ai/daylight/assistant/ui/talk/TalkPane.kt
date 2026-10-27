package ai.daylight.assistant.ui.talk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.ChatAttachment
import ai.daylight.assistant.domain.Citation
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.InlineQuestionProtocol
import ai.daylight.assistant.domain.MessageSegment
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.domain.WorkLog
import ai.daylight.assistant.ui.GeneratedOutputCard
import ai.daylight.assistant.ui.LocalCitationUrls
import ai.daylight.assistant.ui.MarkdownText
import ai.daylight.assistant.ui.QuestionCard
import ai.daylight.assistant.ui.STREAMING_CARET
import ai.daylight.assistant.ui.citedNumbers
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.effects.ThinkingDots
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.delay

/** Everything the Talk pane needs from the chat screen, as plain values and callbacks. */
class TalkCallbacks(
    val onOpenLibrary: () -> Unit,
    val onEdit: (MessageEntity) -> Unit,
    val onRegenerate: () -> Unit,
    val onSpeak: (String) -> Unit,
    val onShowUsage: (MessageEntity) -> Unit,
    val onSaveOutput: (GeneratedOutput) -> Unit,
    val onOpenOutput: (GeneratedOutput) -> Unit,
    val onQuestionAnswer: (question: String, answer: String) -> Unit,
)

/** What a long-press opened its action sheet on. */
private sealed interface ActionTarget {
    data class Question(val message: MessageEntity) : ActionTarget
    data class Answer(val message: MessageEntity, val canRegenerate: Boolean) : ActionTarget
}

/** A plain-text preview of an answer: markdown marks and citation numbers removed. */
internal fun answerPreview(markdown: String): String =
    markdown
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)](?!\\()"), "")
        .replace(Regex("\\[([^]]+)]\\((https?://[^)]+)\\)"), "$1")
        .replace(Regex("[*_~`#>|]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Talk, the departure board (kit chat-b / chat-b-sources). Finished exchanges collapse to one
 * dot-text row each; the current question is the single focal point, then the source
 * readout, then the answer with no bubble. Long-press the question or the answer for its
 * actions (copy, edit, regenerate, read aloud, usage).
 */
@Composable
fun TalkPane(
    conversationId: String,
    messages: List<MessageEntity>,
    generating: Boolean,
    error: String?,
    activities: List<String>,
    workLogs: Map<String, WorkLog>,
    citations: (MessageEntity) -> List<Citation>,
    outputs: (MessageEntity) -> List<GeneratedOutput>,
    attachments: (MessageEntity) -> List<ChatAttachment>,
    speakingMessageId: String?,
    synthesizingMessageId: String?,
    callbacks: TalkCallbacks,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    val context = LocalContext.current
    val exchanges = remember(messages) { talkExchanges(messages) }
    val past = if (exchanges.isEmpty()) emptyList() else exchanges.dropLast(1)
    val current = exchanges.lastOrNull()
    val now = remember(messages.size) { System.currentTimeMillis() }
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    var unfolded by remember(conversationId) { mutableStateOf(setOf<String>()) }
    var sourcesOpen by remember(current?.id) { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var selecting by remember(conversationId) { mutableStateOf<String?>(null) }
    val lastAnswerId = messages.lastOrNull { it.role == "ASSISTANT" }?.id
    val listState = rememberLazyListState()

    // A new question puts the last three board rows and the question on screen.
    LaunchedEffect(conversationId, exchanges.size) {
        if (exchanges.isNotEmpty()) listState.scrollToItem((past.size - 3).coerceAtLeast(0))
    }

    @Composable
    fun Exchange(exchange: TalkExchange, focal: Boolean) {
        var localSources by remember(exchange.id) { mutableStateOf(false) }
        val showSources = if (focal) sourcesOpen else localSources
        val lastAnswer = exchange.answers.lastOrNull()
        val streaming = focal && generating && exchange.streaming
        val readout = lastAnswer?.let {
            SourceReadoutBuilder.build(
                citations = citations(it),
                workLog = workLogs[it.id],
                activities = if (streaming) activities else emptyList(),
                streaming = streaming
            )
        }
        Column(Modifier.fillMaxWidth()) {
            exchange.question?.let { q ->
                Text(
                    q.content,
                    Modifier
                        .fillMaxWidth()
                        .talkLongPress("Question actions") { actionTarget = ActionTarget.Question(q) },
                    style = if (focal) DotType.Question else DotType.Title,
                    color = if (focal) c.textPrimary else c.textSecondary
                )
                val questionFiles = remember(q.id, q.attachmentsJson) { attachments(q) }
                if (questionFiles.isNotEmpty()) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        questionFiles.take(4).forEach { file -> FileChip(file.name) }
                    }
                }
            }
            if (readout != null) {
                Spacer(Modifier.height(if (focal) 24.dp else 12.dp))
                SourceReadout(
                    readout = readout,
                    expanded = showSources,
                    onToggle = { if (focal) sourcesOpen = !sourcesOpen else localSources = !localSources },
                    citedNumbers = citedNumbers(lastAnswer?.content.orEmpty()),
                    onOpenUrl = { openUrl(context, it) },
                    motionEnabled = LocalKryzzMotionEnabled.current
                )
            }
            Spacer(Modifier.height(if (focal) 18.dp else 10.dp))
            if (showSources && lastAnswer != null) {
                Text(
                    answerPreview(lastAnswer.content),
                    style = DotType.Answer,
                    color = c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                exchange.answers.forEach { answer ->
                    Answer(
                        message = answer,
                        citations = remember(answer.id, answer.citationsJson) { citations(answer) },
                        outputs = remember(answer.id, answer.outputsJson) { outputs(answer) },
                        selecting = selecting == answer.id,
                        onDoneSelecting = { selecting = null },
                        onLongPress = {
                            actionTarget = ActionTarget.Answer(answer, canRegenerate = answer.id == lastAnswerId && !generating)
                        },
                        callbacks = callbacks
                    )
                }
                if (focal && generating && exchange.answers.isEmpty()) ThinkingDots(Modifier.padding(vertical = 8.dp))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
    ) {
        if (exchanges.isEmpty()) {
            item(key = "talk-empty") { EmptyBoard(callbacks.onOpenLibrary) }
        }
        itemsIndexed(past, key = { _, ex -> "row-${ex.id}" }) { index, exchange ->
            val open = exchange.id in unfolded
            Column(Modifier.fillMaxWidth()) {
                DepartureRow(
                    gist = exchange.gist,
                    time = boardTimeLabel(exchange.createdAt, now, is24Hour = is24Hour),
                    question = exchange.question?.content.orEmpty(),
                    newest = index == past.lastIndex,
                    expanded = open,
                    showRule = index != past.lastIndex || open,
                    onToggle = { unfolded = if (open) unfolded - exchange.id else unfolded + exchange.id }
                )
                if (open) {
                    Column(Modifier.padding(top = 14.dp, bottom = 18.dp)) { Exchange(exchange, focal = false) }
                }
            }
        }
        if (current != null) {
            item(key = "current-${current.id}") {
                Column(Modifier.padding(top = if (past.isEmpty()) 16.dp else 48.dp)) { Exchange(current, focal = true) }
            }
        }
        // A failed answer already shows the error in its own text; don't repeat it below.
        val errorShownInAnswer = current?.answers.orEmpty().any {
            it.status == MessageStatus.ERROR.name && it.content.trim() == error?.trim()
        }
        if (error != null && !errorShownInAnswer) {
            item(key = "talk-error") {
                Text(error, Modifier.padding(top = 16.dp), style = DotType.BodySmall, color = c.error)
            }
        }
    }

    when (val target = actionTarget) {
        is ActionTarget.Question -> TalkActionsSheet(
            title = "Question",
            actions = buildList {
                add(TalkAction(R.drawable.ic_kz_copy, "Copy") { copyText(context, target.message.content) })
                if (!generating) add(TalkAction(R.drawable.ic_kz_edit, "Edit and resend") { callbacks.onEdit(target.message) })
                add(TalkAction(R.drawable.ic_kz_share, "Share") { shareText(context, target.message.content) })
            },
            onDismiss = { actionTarget = null }
        )
        is ActionTarget.Answer -> {
            val id = target.message.id
            TalkActionsSheet(
                title = "Answer",
                actions = buildList {
                    add(TalkAction(R.drawable.ic_kz_copy, "Copy") { copyText(context, target.message.content) })
                    add(TalkAction(R.drawable.ic_kz_quote, "Select text") { selecting = id })
                    add(TalkAction(R.drawable.ic_kz_share, "Share") { shareText(context, target.message.content) })
                    if (target.message.content.isNotBlank()) {
                        val label = when (id) {
                            synthesizingMessageId -> "Preparing audio…"
                            speakingMessageId -> "Stop reading aloud"
                            else -> "Read aloud"
                        }
                        add(TalkAction(R.drawable.ic_kz_voice_wave, label) { callbacks.onSpeak(id) })
                    }
                    if (target.canRegenerate) add(TalkAction(R.drawable.ic_kz_retry, "Regenerate") { callbacks.onRegenerate() })
                    add(TalkAction(R.drawable.ic_kz_info, "Usage") { callbacks.onShowUsage(target.message) })
                },
                onDismiss = { actionTarget = null }
            )
        }
        null -> Unit
    }
}

@Composable
private fun EmptyBoard(onOpenLibrary: () -> Unit) {
    val c = DotTheme.c
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(DotRadius.Tile)
            .clickable(onClickLabel = "Open chat library", onClick = onOpenLibrary)
            .clearAndSetSemantics {
                contentDescription = "Talk. Open chat library"
                onClick(label = "Open chat library") { onOpenLibrary(); true }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        DotMatrixText("TALK", pitch = 4.2.dp, on = c.textPrimary, off = Color.Transparent)
        Spacer(Modifier.width(12.dp))
        Text("CHATS", style = DotType.Caption, color = c.textTertiary)
    }
}

@Composable
private fun FileChip(name: String) {
    val c = DotTheme.c
    Text(
        name,
        Modifier
            .background(c.surfaceRaised, DotRadius.Pill)
            .border(1.dp, c.border, DotRadius.Pill)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = DotType.Caption,
        color = c.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun Answer(
    message: MessageEntity,
    citations: List<Citation>,
    outputs: List<GeneratedOutput>,
    selecting: Boolean,
    onDoneSelecting: () -> Unit,
    onLongPress: () -> Unit,
    callbacks: TalkCallbacks,
) {
    val c = DotTheme.c
    val motion = LocalKryzzMotionEnabled.current
    val streaming = message.status == MessageStatus.STREAMING.name
    // The same type-on reveal the chat had: fast, monotonic, never past the real text.
    val startedStreaming = remember(message.id) { streaming }
    var revealed by remember(message.id) {
        mutableIntStateOf(if (startedStreaming && motion) 0 else message.content.length)
    }
    LaunchedEffect(message.id, message.content, message.status, motion) {
        if (!motion || !startedStreaming) {
            revealed = message.content.length
            return@LaunchedEffect
        }
        while (revealed < message.content.length) {
            delay(24)
            revealed = (revealed + 6).coerceAtMost(message.content.length)
            if (revealed >= message.content.length - 3) { revealed = message.content.length; break }
        }
        if (!streaming) revealed = message.content.length
    }
    val visible = if (startedStreaming && motion) message.content.take(revealed.coerceIn(0, message.content.length)) else message.content
    val segments = remember(visible) {
        if (visible.isBlank()) emptyList() else InlineQuestionProtocol.parse(visible)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .talkLongPress("Answer actions", enabled = !selecting, onLongPress = onLongPress)
    ) {
        if (segments.isEmpty() && streaming) {
            ThinkingDots(Modifier.padding(vertical = 8.dp))
        } else {
            CompositionLocalProvider(LocalCitationUrls provides citations.map { it.url }) {
                MaterialTheme(typography = MaterialTheme.typography.copy(bodyLarge = DotType.Answer)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        segments.forEachIndexed { index, segment ->
                            when (segment) {
                                is MessageSegment.Markdown -> {
                                    val text = if (streaming && index == segments.lastIndex) segment.text + STREAMING_CARET else segment.text
                                    if (selecting) {
                                        SelectionContainer { MarkdownText(text, contentColor = c.textPrimary) }
                                    } else {
                                        MarkdownText(text, contentColor = c.textPrimary)
                                    }
                                }
                                is MessageSegment.Question -> InlineQuestion(segment, message, callbacks)
                            }
                        }
                    }
                }
            }
        }
        if (selecting) {
            Text(
                "DONE SELECTING",
                Modifier
                    .padding(top = 8.dp)
                    .clip(DotRadius.Pill)
                    .clickable(onClick = onDoneSelecting)
                    .border(1.dp, c.borderStrong, DotRadius.Pill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                style = DotType.Label,
                color = c.textPrimary
            )
        }
        outputs.forEach { GeneratedOutputCard(it, callbacks.onSaveOutput, callbacks.onOpenOutput) }
        when (message.status) {
            MessageStatus.CANCELLED.name -> Text("STOPPED", Modifier.padding(top = 10.dp), style = DotType.Label, color = c.textTertiary)
            MessageStatus.ERROR.name -> Text("FAILED", Modifier.padding(top = 10.dp), style = DotType.Label, color = c.error)
        }
    }
}

@Composable
private fun InlineQuestion(segment: MessageSegment.Question, message: MessageEntity, callbacks: TalkCallbacks) {
    var answered by remember(segment.question.question) { mutableStateOf<String?>(null) }
    var hidden by remember(segment.question.question) { mutableStateOf(false) }
    if (hidden) return
    QuestionCard(
        question = segment.question,
        answeredWith = answered,
        enabled = message.status == MessageStatus.COMPLETE.name && answered == null,
        onAnswer = { choice ->
            answered = choice
            callbacks.onQuestionAnswer(segment.question.question, choice)
        },
        onDismiss = { hidden = true },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Long-press that coexists with tappable text inside (links, citation numerals): it
 * watches the Initial pass without consuming, so a plain tap still reaches the text, and
 * only takes over once the press has lasted the long-press timeout without moving. A tick
 * of haptics confirms it. The latest [onLongPress] is always used.
 */
private fun Modifier.talkLongPress(label: String, enabled: Boolean = true, onLongPress: () -> Unit): Modifier =
    if (!enabled) this else composed {
        val haptic = LocalHapticFeedback.current
        val current by rememberUpdatedState(onLongPress)
        semantics { onLongClick(label = label) { current(); true } }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var ended = false
                        while (!ended) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            ended = change == null || !change.pressed || change.isConsumed ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        }
                        false
                    } ?: true
                    if (longPressed) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        current()
                        // Swallow the rest of this press so the text underneath does not see a tap.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
            }
    }

private fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("Kryzz AI message", text))
}

private fun shareText(context: Context, text: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Kryzz AI")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(share, "Share Kryzz response")) }
}
