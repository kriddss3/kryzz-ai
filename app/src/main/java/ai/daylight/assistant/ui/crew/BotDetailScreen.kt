package ai.daylight.assistant.ui.crew

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.data.RoutineDraft
import ai.daylight.assistant.data.local.BotEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.domain.BotWork
import ai.daylight.assistant.domain.BotWorkKind
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.ui.AddCronTaskDialog
import ai.daylight.assistant.ui.LocalCitationUrls
import ai.daylight.assistant.ui.MarkdownText
import ai.daylight.assistant.ui.STREAMING_CARET
import ai.daylight.assistant.ui.dot.DotAvatar
import ai.daylight.assistant.ui.dot.DotConfirmDialog
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTag
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.effects.ThinkingDots
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkLine
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.shell.LocalInkStrip
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.NoteRow
import ai.daylight.assistant.ui.talk.OptionRow
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.tune.DotSegments
import ai.daylight.assistant.ui.tune.SegmentOption
import ai.daylight.assistant.ui.tune.TuneEmpty
import java.time.ZoneId
import java.util.Locale

private const val TAB_CHAT = 0
private const val TAB_ROUTINES = 1
private const val TAB_RUNS = 2

/**
 * A bot's page (kit `bot-detail`, C9): its face, name and job, then Chat / Routines / Runs.
 * Chat is the bot's own conversation in ledger style (the bot's lines under a mini face,
 * yours with the yellow dot, a routine's prompts marked as such) with the latest run on a
 * card above it. Routines are its scheduled jobs; Runs lists every time one fired.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BotDetailScreen(vm: BotViewModel, onBack: () -> Unit) {
    val c = DotTheme.c
    val context = LocalContext.current
    val strip = LocalInkStrip.current
    val bot by vm.bot.collectAsStateWithLifecycle()
    val gone by vm.gone.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val work by vm.work.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val now = rememberMinuteClock()
    val zone = remember { ZoneId.systemDefault() }

    LaunchedEffect(gone) { if (gone) onBack() }
    val current = bot ?: return

    var tab by rememberSaveable { mutableIntStateOf(TAB_CHAT) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var addingRoutine by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteRoutine by remember { mutableStateOf<ScheduledTaskEntity?>(null) }
    var personaOpen by rememberSaveable { mutableStateOf(false) }

    InkStripEffect(CrewModel.botLine(current.name, work), arm = Arm.CREW)

    val runs = remember(messages, routines, work) { CrewModel.runs(messages, routines, work != null) }
    val imeOpen = WindowInsets.isImeVisible

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DotIconButton(R.drawable.ic_kz_back, "Back to Crew", onBack)
            Spacer(Modifier.width(4.dp))
            Text("CREW", style = DotType.Caption, color = c.textTertiary)
            Spacer(Modifier.weight(1f))
            DotIconButton(R.drawable.ic_kz_more_vertical, "${current.name} options", { showMenu = true }, Modifier.testTag("bot_menu"))
        }
        AnimatedVisibility(visible = !imeOpen) {
            BotHeader(
                bot = current,
                live = work != null,
                personaOpen = personaOpen,
                onPersona = { personaOpen = !personaOpen }
            )
        }
        DotSegments(
            options = listOf(
                SegmentOption("Chat", tag = "bot_tab_chat"),
                SegmentOption(if (routines.isEmpty()) "Routines" else "Routines ${routines.size}", tag = "bot_tab_routines"),
                SegmentOption("Runs", tag = "bot_tab_runs")
            ),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = if (imeOpen) 4.dp else 16.dp, bottom = 12.dp)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                TAB_CHAT -> BotChat(
                    bot = current,
                    messages = messages,
                    routines = routines,
                    latestRun = runs.firstOrNull(),
                    busy = work != null,
                    now = now,
                    zone = zone,
                    citationUrls = vm::citationUrls,
                    onRun = { tab = TAB_RUNS },
                    onCopied = { strip?.flash(InkLine("${current.name} COPIED")) }
                )
                TAB_ROUTINES -> BotRoutines(
                    bot = current,
                    routines = routines,
                    work = work,
                    now = now,
                    zone = zone,
                    notice = notice,
                    onAdd = { addingRoutine = true },
                    onEnabled = { task, on ->
                        if (on) ensureNotificationPermission()
                        vm.setRoutineEnabled(task.id, on)
                    },
                    onRunNow = { vm.runNow(it.id) },
                    onDelete = { deleteRoutine = it }
                )
                else -> BotRuns(runs = runs, now = now, zone = zone, routinesEmpty = routines.isEmpty(), onRoutines = { tab = TAB_ROUTINES })
            }
        }
        if (tab == TAB_CHAT) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
                // The agent usually leaves its failure as the bot's last line; the note is only
                // for failures that never reached the chat (no key, no network before the send).
                val failureInChat = messages.lastOrNull()?.status == MessageStatus.ERROR.name
                error?.takeIf { !failureInChat }?.let {
                    NoteRow(label = "FAILED", text = it, action = "Dismiss", onAction = vm::dismissError, accent = false)
                }
                when {
                    work?.kind == BotWorkKind.ROUTINE -> Text(
                        "On a run: ${work?.routineTitle.orEmpty()}. Send when it is done.".uppercase(Locale.US),
                        Modifier.padding(bottom = 8.dp),
                        style = DotType.Caption.copy(fontSize = 10.sp),
                        color = c.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    notice != null && work == null -> Text(
                        notice.orEmpty().uppercase(Locale.US),
                        Modifier.padding(bottom = 8.dp),
                        style = DotType.Caption.copy(fontSize = 10.sp),
                        color = c.textTertiary
                    )
                }
                BotComposer(
                    value = draft,
                    onValueChange = { vm.draft.value = it },
                    placeholder = "Message ${current.name}",
                    replying = work?.kind == BotWorkKind.REPLY,
                    canSend = draft.isNotBlank() && work == null,
                    onSend = { vm.send() },
                    onStop = vm::stop
                )
            }
        }
    }

    if (showMenu) {
        DotSheet(onDismiss = { showMenu = false }) {
            SheetLabel(current.name)
            OptionRow(R.drawable.ic_kz_edit, "Edit name and job", onClick = { showMenu = false; editing = true })
            OptionRow(R.drawable.ic_kz_routine, "New routine", onClick = { showMenu = false; addingRoutine = true })
            // Handing work to another bot is not built yet: shown unlit, per the kit's rule.
            OptionRow(
                R.drawable.ic_kz_arrow_right,
                "Hand off to another bot",
                onClick = { showMenu = false; strip?.flash(InkLine("HANDOFFS NOT YET")) },
                trailing = { DotTag("Later", color = c.textTertiary) }
            )
            OptionRow(R.drawable.ic_kz_trash, "Delete bot", onClick = { showMenu = false; confirmDelete = true })
        }
    }
    if (editing) {
        NewBotSheet(
            onDismiss = { editing = false },
            onSave = { result -> vm.update(result.name, result.persona, result.seed) { editing = false } },
            editing = true,
            initialName = current.name,
            initialPersona = current.persona,
            initialSeed = current.avatarSeed
        )
    }
    if (addingRoutine) {
        AddCronTaskDialog(
            onDismiss = { addingRoutine = false },
            onAdd = { title, prompt, recurrence, hour, minute, days ->
                ensureNotificationPermission()
                vm.addRoutine(RoutineDraft(title, prompt, recurrence, hour, minute, days)) { saved ->
                    if (saved) {
                        addingRoutine = false
                        tab = TAB_ROUTINES
                    }
                }
            },
            heading = "New routine"
        )
    }
    if (confirmDelete) {
        DotConfirmDialog(
            title = "Delete ${current.name}?",
            body = "Its chat, routines and runs are removed. This cannot be undone.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.delete(onBack)
            },
            onDismiss = { confirmDelete = false }
        )
    }
    deleteRoutine?.let { task ->
        DotConfirmDialog(
            title = "Delete “${task.title}”?",
            body = "The schedule stops. Its past runs stay in ${current.name}'s chat.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                vm.deleteRoutine(task.id)
                deleteRoutine = null
            },
            onDismiss = { deleteRoutine = null }
        )
    }
}

/** Face (77dp), the name in dot text and the job. Long jobs fold to three lines; tap opens them. */
@Composable
private fun BotHeader(bot: BotEntity, live: Boolean, personaOpen: Boolean, onPersona: () -> Unit) {
    val c = DotTheme.c
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
        DotAvatar(bot.name, bot.avatarSeed, pitch = 11.dp, live = live, animate = true)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            val title = bot.name.uppercase(Locale.US)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // 4.2dp like the kit, smaller for a long name so it always fits one line.
                val cols = DotFont5x7.columns(title).coerceAtLeast(1)
                val pitch = (maxWidth / cols).coerceIn(2.dp, 4.2.dp)
                DotMatrixText(
                    title,
                    Modifier.semantics { heading() },
                    pitch = pitch,
                    on = c.textPrimary,
                    off = Color.Transparent,
                    contentDescription = bot.name
                )
            }
            if (bot.persona.isNotBlank()) {
                // No clip: a rounded tap shape cut the first letters at the left edge.
                Text(
                    bot.persona,
                    Modifier
                        .padding(top = 10.dp)
                        .clickable(onClickLabel = if (personaOpen) "Fold" else "Show all", onClick = onPersona),
                    style = DotType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = c.textSecondary,
                    maxLines = if (personaOpen) 20 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The bot's chat as a ledger, with its latest run on a card at the top. */
@Composable
private fun BotChat(
    bot: BotEntity,
    messages: List<MessageEntity>,
    routines: List<ScheduledTaskEntity>,
    latestRun: BotRun?,
    busy: Boolean,
    now: Long,
    zone: ZoneId,
    citationUrls: (MessageEntity) -> List<String>,
    onRun: () -> Unit,
    onCopied: () -> Unit,
) {
    val c = DotTheme.c
    val state = rememberLazyListState()
    val last = messages.lastOrNull()
    // The list is laid out from the bottom, like a chat: the newest line stays in place when
    // the header comes back after the keyboard closes, and a growing reply grows upward.
    // A new line brings you back down to it.
    LaunchedEffect(last?.id) {
        if (last != null) state.scrollToItem(0)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        reverseLayout = true,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.Top)
    ) {
        // Emitted newest first: in a reversed list the first item sits at the bottom.
        items(messages.asReversed(), key = { it.id }) { message ->
            val routine = CrewModel.routineFor(message, routines)
            when {
                routine != null -> RoutineTurn(routine.title, message, now, zone)
                message.role == "USER" -> UserTurn(message, now, zone)
                else -> BotTurn(bot, message, busy && message.id == last?.id, now, zone, citationUrls, onCopied)
            }
        }
        if (messages.isEmpty() && latestRun == null) {
            item(key = "hello") {
                Text(
                    "Say hello to ${bot.name}. It answers in the role above, and its routines report here.",
                    Modifier.padding(top = 8.dp),
                    style = DotType.BodySmall,
                    color = c.textTertiary
                )
            }
        }
        if (latestRun != null) {
            item(key = "run") { RunCard(latestRun, now, zone, onRun) }
        }
    }
}

/** The latest run: "TODAY, 07:00 RUN" with its state, the routine and the gist of its answer. */
@Composable
private fun RunCard(run: BotRun, now: Long, zone: ZoneId, onOpen: () -> Unit) {
    val c = DotTheme.c
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DotRadius.Card)
            .background(c.surface, DotRadius.Card)
            .border(1.dp, c.border, DotRadius.Card)
            .clickable(onClickLabel = "Show runs", onClick = onOpen)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
            .testTag("bot_run_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                CrewModel.runHeading(run.at, now, zone).uppercase(Locale.US),
                Modifier.weight(1f),
                style = DotType.Label,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                CrewModel.runTag(run.state),
                style = DotType.Caption,
                color = if (run.state == RunState.RUNNING) c.accent else c.textTertiary
            )
        }
        Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            RunMark(run.state)
            Spacer(Modifier.width(12.dp))
            Text(
                run.routineTitle,
                style = DotType.Body.copy(fontSize = 14.sp),
                color = if (run.state == RunState.RUNNING) c.textPrimary else c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val gist = CrewModel.gist(run.answer)
        if (gist.isNotEmpty()) {
            Text(
                gist,
                Modifier.padding(start = 30.dp, bottom = 6.dp),
                style = DotType.BodySmall,
                color = c.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RunMark(state: RunState) {
    val c = DotTheme.c
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        when (state) {
            RunState.RUNNING -> LiveRing(size = 12.dp)
            RunState.DONE -> Icon(painterResource(R.drawable.ic_kz_step_done), "Done", Modifier.size(18.dp), tint = c.textSecondary)
            RunState.FAILED -> Icon(painterResource(R.drawable.ic_kz_step_failed), "Failed", Modifier.size(18.dp), tint = c.error)
            RunState.STOPPED -> Icon(painterResource(R.drawable.ic_kz_step_pending), "Stopped", Modifier.size(18.dp), tint = c.textDisabled)
        }
    }
}

/** Your line: the yellow dot, the words, then "YOU, 07:05". */
@Composable
private fun UserTurn(message: MessageEntity, now: Long, zone: ZoneId) {
    val c = DotTheme.c
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(top = 8.dp).size(7.dp).background(c.accent, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(message.content, style = DotType.Title.copy(lineHeight = 23.sp), color = c.textPrimary)
            Text(
                "You, ${CrewModel.whenLabel(message.createdAt, now, zone)}".uppercase(Locale.US),
                Modifier.padding(top = 5.dp),
                style = DotType.Caption,
                color = c.textTertiary
            )
        }
    }
}

/** A routine's prompt: marked as the routine's, not as something you typed. */
@Composable
private fun RoutineTurn(title: String, message: MessageEntity, now: Long, zone: ZoneId) {
    val c = DotTheme.c
    Row(Modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.ic_kz_routine), null, Modifier.padding(top = 1.dp).size(14.dp), tint = c.textTertiary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Routine: $title, ${CrewModel.whenLabel(message.createdAt, now, zone)}".uppercase(Locale.US),
                style = DotType.Caption,
                color = c.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                message.content,
                Modifier.padding(top = 4.dp),
                style = DotType.BodySmall,
                color = c.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The bot's line: its mini face and "SCOUT, 07:04", then the answer. Long-press copies it. */
@Composable
private fun BotTurn(
    bot: BotEntity,
    message: MessageEntity,
    live: Boolean,
    now: Long,
    zone: ZoneId,
    citationUrls: (MessageEntity) -> List<String>,
    onCopied: () -> Unit,
) {
    val c = DotTheme.c
    val clipboard = LocalClipboardManager.current
    val streaming = message.status == MessageStatus.STREAMING.name
    val failed = message.status == MessageStatus.ERROR.name
    val stopped = streaming && !live
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(message.id, message.content) {
                detectTapGestures(onLongPress = {
                    if (message.content.isNotBlank()) {
                        clipboard.setText(AnnotatedString(message.content))
                        onCopied()
                    }
                })
            }
            .semantics { contentDescription = "${bot.name} said" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotAvatar(bot.name, bot.avatarSeed, pitch = 3.dp, off = Color.Transparent, live = live)
            Spacer(Modifier.width(8.dp))
            val caption = "${bot.name}, ${CrewModel.whenLabel(message.createdAt, now, zone)}" +
                when {
                    failed -> ", failed"
                    stopped -> ", stopped"
                    else -> ""
                }
            Text(
                caption.uppercase(Locale.US),
                style = DotType.Caption,
                color = if (failed) c.error else c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.fillMaxWidth().padding(start = 29.dp, top = 8.dp)) {
            when {
                message.content.isBlank() && live -> ThinkingDots(Modifier.padding(vertical = 6.dp))
                message.content.isBlank() -> Unit
                else -> CompositionLocalProvider(LocalCitationUrls provides citationUrls(message)) {
                    MaterialTheme(typography = MaterialTheme.typography.copy(bodyLarge = DotType.Body.copy(fontSize = 15.sp, lineHeight = 22.sp))) {
                        MarkdownText(
                            if (streaming && live) message.content + STREAMING_CARET else message.content,
                            contentColor = if (failed) c.textSecondary else c.textPrimary
                        )
                    }
                }
            }
        }
    }
}

/** The bot's routines: rows like Tune's Routines page, and New routine. */
@Composable
private fun BotRoutines(
    bot: BotEntity,
    routines: List<ScheduledTaskEntity>,
    work: BotWork?,
    now: Long,
    zone: ZoneId,
    notice: String?,
    onAdd: () -> Unit,
    onEnabled: (ScheduledTaskEntity, Boolean) -> Unit,
    onRunNow: (ScheduledTaskEntity) -> Unit,
    onDelete: (ScheduledTaskEntity) -> Unit,
) {
    val c = DotTheme.c
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        if (routines.isEmpty()) {
            item(key = "empty") {
                TuneEmpty(
                    dotIcon = R.drawable.ic_kz_timer_dot,
                    words = "No routines",
                    body = "A routine sends ${bot.name} a job on a schedule. Each run lands in its chat and under Runs.",
                    modifier = Modifier.padding(top = 24.dp),
                    action = "New routine",
                    onAction = onAdd,
                    actionModifier = Modifier.testTag("bot_routine_add_empty")
                )
            }
        } else {
            items(routines, key = { it.id }) { task ->
                BotRoutineRow(
                    task = task,
                    zone = zone,
                    now = now,
                    onEnabled = { onEnabled(task, it) },
                    onRunNow = { onRunNow(task) },
                    onDelete = { onDelete(task) },
                    running = work?.routineId == task.id
                )
            }
            item(key = "end") {
                CrewRule()
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    DotPillButton("New routine", onAdd, Modifier.testTag("bot_routine_add"), icon = R.drawable.ic_kz_plus)
                }
            }
        }
        notice?.let { text ->
            item(key = "notice") {
                Text(text, Modifier.padding(top = 12.dp), style = DotType.BodySmall, color = c.textTertiary)
            }
        }
    }
}

/** Every routine run, newest first. Tap a run to read its whole answer. */
@Composable
private fun BotRuns(runs: List<BotRun>, now: Long, zone: ZoneId, routinesEmpty: Boolean, onRoutines: () -> Unit) {
    val c = DotTheme.c
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        if (runs.isEmpty()) {
            item(key = "empty") {
                TuneEmpty(
                    dotIcon = R.drawable.ic_kz_history_dot,
                    words = "No runs yet",
                    body = if (routinesEmpty) "Runs appear here once the bot has a routine and it fires."
                    else "Runs appear here when a routine fires. Run now on a routine starts one.",
                    modifier = Modifier.padding(top = 24.dp),
                    action = if (routinesEmpty) "Add a routine" else "Routines",
                    onAction = onRoutines,
                    actionIcon = if (routinesEmpty) R.drawable.ic_kz_plus else R.drawable.ic_kz_routine
                )
            }
        }
        items(runs, key = { it.askedId }) { run ->
            val expanded = open == run.askedId
            Column(Modifier.fillMaxWidth()) {
                CrewRule()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = if (expanded) "Fold" else "Read the answer") {
                            open = if (expanded) null else run.askedId
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    RunMark(run.state)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(run.routineTitle, style = DotType.Body, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${CrewModel.whenLabel(run.at, now, zone)} ${CrewModel.runTag(run.state)}".uppercase(Locale.US),
                            Modifier.padding(top = 2.dp),
                            style = DotType.Caption.copy(fontSize = 10.sp),
                            color = if (run.state == RunState.RUNNING) c.accent else c.textTertiary
                        )
                        val answer = run.answer
                        if (answer != null) {
                            if (expanded) {
                                Box(Modifier.padding(top = 8.dp)) {
                                    MaterialTheme(typography = MaterialTheme.typography.copy(bodyLarge = DotType.Body)) {
                                        MarkdownText(answer, contentColor = c.textPrimary)
                                    }
                                }
                            } else {
                                Text(
                                    CrewModel.gist(answer),
                                    Modifier.padding(top = 6.dp),
                                    style = DotType.BodySmall,
                                    color = c.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        if (runs.isNotEmpty()) item(key = "rule") { CrewRule() }
    }
}

/**
 * The bot's message pill (kit `.inp`): 56dp, the text, then a yellow send circle, or a
 * white stop circle while the bot is answering. Multi-line text grows the pill.
 */
@Composable
private fun BotComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    replying: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val c = DotTheme.c
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(DotRadius.Card)
            .background(c.surface, DotRadius.Card)
            .border(1.dp, c.border, DotRadius.Card)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().testTag("bot_message").semantics { contentDescription = placeholder },
                textStyle = DotType.Body.copy(fontSize = 16.sp, color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                maxLines = 5,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = DotType.Body.copy(fontSize = 16.sp), color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            )
        }
        Spacer(Modifier.width(10.dp))
        if (replying) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.textPrimary, CircleShape)
                    .clickable(role = Role.Button, onClick = onStop)
                    .semantics { contentDescription = "Stop the reply" }
                    .testTag("bot_stop"),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_kz_stop), null, Modifier.size(18.dp), tint = c.bg)
            }
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canSend) c.accent else c.surfaceRaised, CircleShape)
                    .clickable(enabled = canSend, role = Role.Button, onClick = onSend)
                    .semantics { contentDescription = "Send" }
                    .testTag("bot_send"),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_kz_send), null, Modifier.size(20.dp), tint = if (canSend) c.onAccent else c.textDisabled)
            }
        }
    }
}
