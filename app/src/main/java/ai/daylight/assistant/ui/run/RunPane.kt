package ai.daylight.assistant.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.AgentPlan
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.ChatAttachment
import ai.daylight.assistant.domain.Citation
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.domain.SwarmStatus
import ai.daylight.assistant.domain.WorkLog
import ai.daylight.assistant.ui.citedNumbers
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.dashedBorder
import ai.daylight.assistant.ui.talk.ActionTarget
import ai.daylight.assistant.ui.talk.AnswerBody
import ai.daylight.assistant.ui.talk.DepartureRow
import ai.daylight.assistant.ui.talk.FileChip
import ai.daylight.assistant.ui.talk.MessageActionsSheet
import ai.daylight.assistant.ui.talk.SourceReadout
import ai.daylight.assistant.ui.talk.SourceReadoutBuilder
import ai.daylight.assistant.ui.talk.TalkCallbacks
import ai.daylight.assistant.ui.talk.TalkExchange
import ai.daylight.assistant.ui.talk.boardTimeLabel
import ai.daylight.assistant.ui.talk.openUrl
import ai.daylight.assistant.ui.talk.talkExchanges
import ai.daylight.assistant.ui.talk.talkLongPress
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import java.util.Locale

/** Which skill action a run's answer offers. Pure, so it is unit tested. */
internal object RunSkill {
    /**
     * null hides "Make skill"; false offers it; true shows it saved. Only the newest finished
     * agent answer can be saved, and only once nothing is running.
     */
    fun state(answer: MessageEntity, lastAnswerId: String?, generating: Boolean, saved: Set<String>): Boolean? = when {
        answer.id in saved -> true
        answer.id == lastAnswerId && !generating && answer.mode == AssistantMode.AGENT.name &&
            answer.status == MessageStatus.COMPLETE.name -> false
        else -> null
    }
}

/**
 * Run, the agent screen (kit agent-run). Earlier runs in the conversation fold into
 * departure-board rows; the current run shows its goal, the plan card, the work chips, the
 * answer and its files, and the cost line. Long-press the goal or the answer for actions.
 */
@Composable
fun RunPane(
    conversationId: String,
    messages: List<MessageEntity>,
    generating: Boolean,
    error: String?,
    activities: List<String>,
    workLogs: Map<String, WorkLog>,
    plan: AgentPlan?,
    swarm: SwarmStatus?,
    capability: AgentCapability,
    capCents: Int,
    citations: (MessageEntity) -> List<Citation>,
    outputs: (MessageEntity) -> List<GeneratedOutput>,
    attachments: (MessageEntity) -> List<ChatAttachment>,
    speakingMessageId: String?,
    synthesizingMessageId: String?,
    skillCreatedFor: Set<String>,
    callbacks: TalkCallbacks,
    onWorkflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    val context = LocalContext.current
    val motion = LocalKryzzMotionEnabled.current
    val exchanges = remember(messages) { talkExchanges(messages) }
    val past = if (exchanges.isEmpty()) emptyList() else exchanges.dropLast(1)
    val current = exchanges.lastOrNull()
    val now = remember(messages.size) { System.currentTimeMillis() }
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    var unfolded by remember(conversationId) { mutableStateOf(setOf<String>()) }
    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var selecting by remember(conversationId) { mutableStateOf<String?>(null) }
    var openLog by remember { mutableStateOf<WorkLog?>(null) }
    val lastAnswerId = messages.lastOrNull { it.role == "ASSISTANT" }?.id
    // A new run puts the newest earlier run's row and the new goal at the top. The list starts
    // there, so a page the pager composes while it measures never has to scroll.
    val runTarget = (past.size - 1).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = runTarget)

    // The answer row a run's log hangs on is replaced between tool rounds, so hold on to the
    // last log seen for the run in progress instead of letting its chips blink out.
    val currentLog = current?.answers?.lastOrNull()?.id?.let { workLogs[it] }
    var heldLog by remember(current?.id) { mutableStateOf<WorkLog?>(null) }
    LaunchedEffect(currentLog) { if (currentLog != null) heldLog = currentLog }
    LaunchedEffect(generating) { if (generating) heldLog = null }
    val focalLog = currentLog ?: heldLog

    LaunchedEffect(conversationId, exchanges.size) {
        val there = listState.firstVisibleItemIndex == runTarget && listState.firstVisibleItemScrollOffset == 0
        if (exchanges.isNotEmpty() && !there) listState.scrollToItem(runTarget)
    }

    @Composable
    fun Exchange(exchange: TalkExchange, focal: Boolean) {
        val live = focal && generating
        val answers = exchange.answers
        val lastAnswer = answers.lastOrNull()
        val log = if (focal) focalLog else lastAnswer?.let { workLogs[it.id] }
        var goalOpen by remember(exchange.id) { mutableStateOf(false) }
        var goalOverflows by remember(exchange.id) { mutableStateOf(false) }
        var sourcesOpen by remember(exchange.id) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth()) {
            exchange.question?.let { q ->
                Text(
                    q.content,
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = goalOverflows || goalOpen,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = if (goalOpen) "Fold the task" else "Show the whole task"
                        ) { goalOpen = !goalOpen }
                        .talkLongPress("Task actions") { actionTarget = ActionTarget.Question(q) },
                    style = if (focal) DotType.Goal else DotType.Title,
                    color = if (focal) c.textPrimary else c.textSecondary,
                    maxLines = if (goalOpen) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!goalOpen) goalOverflows = it.hasVisualOverflow }
                )
                val files = remember(q.id, q.attachmentsJson) { attachments(q) }
                if (files.isNotEmpty()) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        files.take(4).forEach { FileChip(it.name) }
                    }
                }
            }
            if (focal && plan != null) {
                Spacer(Modifier.height(24.dp))
                PlanCard(plan, live = live, activity = if (live) WorkChips.latestActivity(activities) else null)
            }
            if (focal && swarm != null) {
                Spacer(Modifier.height(16.dp))
                SwarmCard(swarm)
            }
            val chips = WorkChips.done(log?.steps.orEmpty()) + WorkChips.running(activities, live)
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(if (focal && plan != null) 20.dp else 24.dp))
                WorkChipRow(chips, onOpenLog = log?.takeIf { it.steps.isNotEmpty() }?.let { l -> { openLog = l } })
            }
            val cites = remember(lastAnswer?.id, lastAnswer?.citationsJson) { lastAnswer?.let(citations).orEmpty() }
            if (cites.isNotEmpty() && !live && lastAnswer != null) {
                // Citations only: the chips above already say what the agent did.
                SourceReadoutBuilder.build(cites, workLog = null)?.let { readout ->
                    Spacer(Modifier.height(16.dp))
                    SourceReadout(
                        readout = readout,
                        expanded = sourcesOpen,
                        onToggle = { sourcesOpen = !sourcesOpen },
                        citedNumbers = citedNumbers(lastAnswer.content),
                        onOpenUrl = { openUrl(context, it) },
                        motionEnabled = motion
                    )
                }
            }
            if (answers.isNotEmpty()) Spacer(Modifier.height(20.dp))
            // A run stopped before its first words leaves no answer, or an empty row still
            // marked as streaming. Once nothing runs here any more (no live agent activity
            // either, so a routine running in this chat is not mislabelled), say it stopped.
            val nothingSaid = answers.all { it.status == MessageStatus.STREAMING.name && it.content.isBlank() }
            val idle = !focal || (!generating && activities.isEmpty())
            if (nothingSaid && idle && exchange.question != null) {
                Text(
                    "STOPPED",
                    Modifier.padding(top = if (answers.isEmpty()) 20.dp else 0.dp),
                    style = DotType.Label,
                    color = c.textTertiary
                )
            }
            answers.forEach { answer ->
                AnswerBody(
                    message = answer,
                    citations = remember(answer.id, answer.citationsJson) { citations(answer) },
                    outputs = remember(answer.id, answer.outputsJson) { outputs(answer) },
                    selecting = selecting == answer.id,
                    onDoneSelecting = { selecting = null },
                    onLongPress = {
                        actionTarget = ActionTarget.Answer(
                            answer,
                            canRegenerate = answer.id == lastAnswerId && !generating,
                            skill = RunSkill.state(answer, lastAnswerId, generating, skillCreatedFor)
                        )
                    },
                    callbacks = callbacks,
                    working = focal && (generating || activities.isNotEmpty())
                )
            }
            val cost = RunCost.readout(
                costUsd = answers.mapNotNull { it.cost }.takeIf { it.isNotEmpty() }?.sum(),
                totalTokens = answers.mapNotNull { it.totalTokens }.takeIf { it.isNotEmpty() }?.sum(),
                capCents = capCents,
                running = live,
                finished = lastAnswer?.status == MessageStatus.COMPLETE.name,
                latest = focal
            )
            if (cost != null) {
                Spacer(Modifier.height(16.dp))
                CostLine(cost)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 24.dp)
    ) {
        if (exchanges.isEmpty()) {
            item(key = "run-empty") { EmptyRun(capability, onWorkflow) }
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
                Column(Modifier.padding(top = if (past.isEmpty()) 8.dp else 40.dp)) { Exchange(current, focal = true) }
            }
        }
        val errorShownInAnswer = current?.answers.orEmpty().any {
            it.status == MessageStatus.ERROR.name && it.content.trim() == error?.trim()
        }
        if (error != null && !errorShownInAnswer) {
            item(key = "run-error") {
                Text(error, Modifier.padding(top = 16.dp), style = DotType.BodySmall, color = c.error)
            }
        }
    }

    actionTarget?.let { target ->
        MessageActionsSheet(
            target = target,
            generating = generating,
            speakingMessageId = speakingMessageId,
            synthesizingMessageId = synthesizingMessageId,
            callbacks = callbacks,
            onSelectText = { selecting = it },
            onDismiss = { actionTarget = null }
        )
    }
    openLog?.let { log -> WorkLogSheet(log, onDismiss = { openLog = null }) }
}

/** A fresh run: a grey prompt where the goal will stand, and the workflow it will use. */
@Composable
private fun EmptyRun(capability: AgentCapability, onWorkflow: () -> Unit) {
    val c = DotTheme.c
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            "Give Kryzz a task. It plans the steps, uses its tools and makes the files.",
            style = DotType.Goal,
            color = c.textTertiary
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clip(DotRadius.Pill)
                .clickable(onClick = onWorkflow)
                .clearAndSetSemantics {
                    contentDescription = "Workflow ${capability.shortLabel}. Change"
                    role = Role.Button
                    onClick(label = "Change workflow") { onWorkflow(); true }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .height(32.dp)
                    .background(c.surfaceRaised, DotRadius.Pill)
                    .border(1.dp, c.border, DotRadius.Pill)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_kz_tools), null, Modifier.size(14.dp), tint = c.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text("WORKFLOW", style = DotType.Label, color = c.textTertiary)
                Spacer(Modifier.width(8.dp))
                Text(capability.shortLabel.uppercase(Locale.getDefault()), style = DotType.Label, color = c.textPrimary)
            }
        }
    }
}

/**
 * The bottom of a run in progress (kit agent-run): Pause and a white Stop run. The agent
 * cannot pause, so Pause is drawn unlit (dashed) and only says so on the ink strip.
 */
@Composable
fun RunActions(onPause: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(DotRadius.Pill)
                .dashedBorder(c.borderStrong, radius = 24.dp)
                .clickable(onClick = onPause)
                .clearAndSetSemantics {
                    contentDescription = "Pause, not available yet"
                    role = Role.Button
                    onClick { onPause(); true }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_kz_pause), null, Modifier.size(18.dp), tint = c.textDisabled)
            Spacer(Modifier.width(8.dp))
            Text("Pause", style = DotType.Button, color = c.textDisabled)
        }
        Row(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(DotRadius.Pill)
                .background(c.textPrimary)
                .clickable(onClick = onStop)
                .clearAndSetSemantics {
                    contentDescription = "Stop run"
                    role = Role.Button
                    onClick { onStop(); true }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_kz_stop), null, Modifier.size(18.dp), tint = c.bg)
            Spacer(Modifier.width(8.dp))
            Text("Stop run", style = DotType.Button, color = c.bg)
        }
    }
}
