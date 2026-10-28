package ai.daylight.assistant.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.daylight.assistant.data.ConversationRepository
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.AgentModelChoice
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.AgentPlan
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.ConversationUsage
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.ReasoningEffort
import ai.daylight.assistant.domain.SwarmPhase
import ai.daylight.assistant.domain.SwarmStatus
import androidx.compose.ui.res.painterResource
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotColors
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.talk.FollowUpAction
import ai.daylight.assistant.ui.talk.FollowUpQueue
import ai.daylight.assistant.ui.talk.ReplyLine
import ai.daylight.assistant.ui.talk.SourceReadoutBuilder
import ai.daylight.assistant.ui.talk.TalkCallbacks
import ai.daylight.assistant.ui.talk.TalkOptionsSheet
import ai.daylight.assistant.ui.talk.TalkPane
import ai.daylight.assistant.ui.talk.NoteRow
import ai.daylight.assistant.ui.run.QualitySheet
import ai.daylight.assistant.ui.run.QuestionCard
import ai.daylight.assistant.ui.run.RunActions
import ai.daylight.assistant.ui.run.RunHeader
import ai.daylight.assistant.ui.run.RunOptionsSheet
import ai.daylight.assistant.ui.run.RunPane
import ai.daylight.assistant.ui.run.WorkChips
import ai.daylight.assistant.ui.home.HomeBoard
import ai.daylight.assistant.ui.shell.InkLine
import ai.daylight.assistant.ui.shell.LocalInkStrip
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.dot.DotColorsLight
import ai.daylight.assistant.ui.dot.DataMonoFamily
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.agent.BotMood
import ai.daylight.assistant.ui.agent.MascotState
import ai.daylight.assistant.voice.VoicePhase
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun agentPrimaryColor(darkBackground: Boolean): Color =
    if (darkBackground) DotColors.TextPrimary else DotColorsLight.TextPrimary

internal fun agentOnPrimaryColor(darkBackground: Boolean): Color =
    if (darkBackground) DotColors.Bg else DotColorsLight.Surface

internal fun agentBotMood(
    mode: AssistantMode,
    generating: Boolean,
    error: String?,
    activities: List<String>,
    swarmStatus: SwarmStatus?,
    streamingHasText: Boolean = false
): BotMood {
    if (mode != AssistantMode.AGENT) return BotMood.IDLE
    if (error != null || swarmStatus?.phase == SwarmPhase.FAILED) return BotMood.ERROR
    if (!generating && swarmStatus?.phase == SwarmPhase.DONE) return BotMood.SUCCESS
    val swarmActive = swarmStatus?.phase in setOf(SwarmPhase.PLANNING, SwarmPhase.RUNNING, SwarmPhase.SYNTHESIZING)
    if (generating && (activities.isNotEmpty() || swarmActive)) return BotMood.TOOLING
    // Once the model is actively streaming words into the bubble it is "speaking";
    // before any text lands (or while waiting on the first token) it is "thinking".
    if (generating && streamingHasText) return BotMood.SPEAKING
    return if (generating) BotMood.THINKING else BotMood.IDLE
}

/**
 * Picks the mascot GIF state for a single agent-mode assistant bubble, based on the row's
 * lifecycle: thinking before any text lands (or while tools run), speaking while the answer
 * streams out, and standby once the row is finished.
 */
internal fun agentMessageMascotState(message: MessageEntity): MascotState = when {
    message.role != "ASSISTANT" -> MascotState.STANDBY
    message.status == MessageStatus.STREAMING.name && message.content.isBlank() -> MascotState.THINKING
    message.status == MessageStatus.STREAMING.name -> MascotState.SPEAKING
    message.status == MessageStatus.ERROR.name -> MascotState.THINKING
    else -> MascotState.STANDBY
}

internal fun nextTypeOnLength(current: Int, target: Int): Int =
    minOf(target, current + 8)

internal fun formatLatencyMs(value: Long?): String =
    value?.let { String.format(Locale.US, "%.2f s", it / 1_000.0) } ?: "Not available"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    repository: ConversationRepository,
    initialMode: AssistantMode = AssistantMode.CHAT,
    initialCapability: AgentCapability = AgentCapability.AUTO,
    initialVoice: Boolean = false,
    onBack: () -> Unit,
    onModels: (ModelPurpose) -> Unit,
    onOpenLibrary: () -> Unit = {},
    onStartAgent: () -> Boolean = { false },
    onNewChat: (AssistantMode) -> Unit = {},
    onComposerFocusChanged: (Boolean) -> Unit = {},
    onArmShown: (Arm) -> Unit = {},
    onWebSearch: (Boolean) -> Unit = {},
    rootNavVisible: Boolean = true
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val composer by vm.composer.collectAsStateWithLifecycle()
    val editing by vm.editingFrom.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val selectedCapability by vm.capability.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val imageModels by vm.imageModels.collectAsStateWithLifecycle()
    val videoModels by vm.videoModels.collectAsStateWithLifecycle()
    val audioModels by vm.audioModels.collectAsStateWithLifecycle()
    val skillCreatedFor by vm.skillCreatedFor.collectAsStateWithLifecycle()
    val pendingAttachments by vm.pendingAttachments.collectAsStateWithLifecycle()
    val researchDepth by vm.researchDepth.collectAsStateWithLifecycle()
    val researchWidth by vm.researchWidth.collectAsStateWithLifecycle()
    val modelsLoading by vm.modelsLoading.collectAsStateWithLifecycle()
    val modelsError by vm.modelsError.collectAsStateWithLifecycle()
    val mediaModelsError by vm.mediaModelsError.collectAsStateWithLifecycle()
    val activities by vm.activities.collectAsStateWithLifecycle()
    val swarmStatus by vm.swarmStatus.collectAsStateWithLifecycle()
    val pendingQuestion by vm.pendingQuestion.collectAsStateWithLifecycle()
    val agentPlan by vm.agentPlan.collectAsStateWithLifecycle()
    val workLogs by vm.workLogs.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = initialMode.ordinal, pageCount = { AssistantMode.entries.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingOutput by remember { mutableStateOf<GeneratedOutput?>(null) }
    var usageMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showAiControls by remember(vm.conversationId) { mutableStateOf(false) }
    var showCapabilityPicker by remember(vm.conversationId) { mutableStateOf(false) }
    val voicePhase by vm.voicePhase.collectAsStateWithLifecycle()
    val voiceLevel by vm.voiceLevel.collectAsStateWithLifecycle()
    val voiceTranscript by vm.voiceTranscript.collectAsStateWithLifecycle()
    val dictating by vm.dictating.collectAsStateWithLifecycle()
    val dictationLevel by vm.dictationLevel.collectAsStateWithLifecycle()
    val speakingMessageId by vm.speakingMessageId.collectAsStateWithLifecycle()
    val synthesizingMessageId by vm.synthesizingMessageId.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.beginVoice() else vm.error.value = "Microphone permission is needed for voice chat."
    }
fun requestVoice() {
        if (voicePhase != VoicePhase.IDLE) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            keyboard?.hide()
            vm.beginVoice()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val dictationMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.beginDictation() else vm.error.value = "Microphone permission is needed for dictation."
    }
    fun requestDictation() {
        if (vm.dictating.value) {
            vm.stopDictation()
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            keyboard?.hide()
            vm.beginDictation()
        } else {
            dictationMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // The "Voice" entry in the chat menu opens a fresh conversation ready to
    // speak: the mic session starts once, on the first composition. This is also
    // the path used when the side panel's "Voice" button creates a brand-new
    // conversation and navigates here with voice=true. Starting the mic from a
    // raw first-composition LaunchedEffect is racy there: the navigation
    // transition is still settling, the side-panel close animation is still
    // running, and (when the mic permission has not been granted yet) the
    // permission launcher is asked to pop before the new back-stack entry is
    // fully RESUMED — so the system can drop the request and the session never
    // starts. Tapping the mic inside an existing chat never hit this because a
    // real user tap already implies the activity is resumed. We therefore wait
    // until this screen's lifecycle is at least RESUMED before kicking off the
    // voice session, and re-evaluate whenever the lifecycle or the consumed
    // flag flips so a brief pause/transition does not swallow the start.
    var initialVoiceConsumed by rememberSaveable(vm.conversationId) { mutableStateOf(false) }
    val voiceStartLifecycleOwner = LocalLifecycleOwner.current
    var voiceStartResumed by remember(voiceStartLifecycleOwner) {
        mutableStateOf(voiceStartLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(voiceStartLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> voiceStartResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> voiceStartResumed = false
                else -> {}
            }
        }
        voiceStartLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { voiceStartLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(vm.conversationId, initialVoice, initialVoiceConsumed, voiceStartResumed) {
        if (initialVoice && !initialVoiceConsumed && voiceStartResumed) {
            initialVoiceConsumed = true
            requestVoice()
        }
    }
    val saveOutput = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val output = pendingOutput
        if (uri != null && output != null) scope.launch(Dispatchers.IO) { writeOutput(context, uri, output) }
    }
    val pickAttachments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.attach(uris)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.attach(listOf(uri))
    }
    val cameraFile = remember { mutableStateOf<File?>(null) }
    val captureImage = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraFile.value
        if (saved && file != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            vm.attach(listOf(uri))
        } else {
            file?.delete()
        }
        cameraFile.value = null
    }

    // Mode switching is restored: the header switcher (and a horizontal swipe on
    // the pager) moves between the Chat and Agent pages. Switching modes happens
    // in place — the single settle effect below updates the VM state once — and
    // deliberately does NOT navigate to a new conversation, so no second screen
    // transition plays over the pager animation.
    fun requestMode(target: AssistantMode) {
        if (target.ordinal == pager.currentPage) return
        scope.launch { pager.animateScrollToPage(target.ordinal) }
    }
    LaunchedEffect(pager.settledPage) {
        vm.mode.value = AssistantMode.entries[pager.settledPage]
    }
    LaunchedEffect(initialCapability) { vm.capability.value = initialCapability }
    // Opened from the Home composer: the question is already in the composer, send it
    // once. Mode and capability are set here too so the send can't race the effects above.
    LaunchedEffect(vm.conversationId) {
        if (consumeChatAutoSend(vm.conversationId)) {
            vm.mode.value = initialMode
            vm.capability.value = initialCapability
            vm.send()
        }
    }
    DisposableEffect(Unit) { onDispose { onComposerFocusChanged(false) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.cancelVoice()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.cancelVoice()
        }
    }

    val currentMode = AssistantMode.entries[pager.currentPage]
    // A conversation locks into its mode once the first message is sent: the header
    // switcher then transforms into a New chat button and the pager stops swiping.
    val modeLocked = messages.any { it.role == "USER" }
    val activePurpose = activeModelPurpose(currentMode, selectedCapability, settings.agentQuality)
    val activeModelId = settings.modelFor(activePurpose)
    // v5.10: the one-time gpt-4o-mini suggestion and the missing tool calling warning.
    val agentModelNotice = KryzzAgentModelNotice(
        suggestUpgrade = activePurpose == ModelPurpose.AGENT &&
            AgentModelChoice.shouldSuggestUpgrade(settings.chatProvider, settings.agentModel, settings.agentModelSuggestionAnswered),
        suggestedName = models.firstOrNull { it.id == AgentModelChoice.OPENROUTER_AGENT_DEFAULT }?.name
            ?: AgentModelChoice.OPENROUTER_AGENT_DEFAULT.compactModel(),
        lacksTools = activePurpose in setOf(ModelPurpose.AGENT, ModelPurpose.RESEARCH, ModelPurpose.MAX) &&
            AgentModelChoice.knownToLackTools(activeModelId, models)
    )
    val activeReasoning = settings.modelReasoning[activeModelId] ?: ReasoningEffort.AUTO
    // Eight Arms: this one screen is Talk, Run or Voice. Tell the rail which, and put the
    // matching line on the ink strip.
    val voiceSearching by vm.voiceSearching.collectAsStateWithLifecycle()
    val shownArm = when {
        voicePhase != VoicePhase.IDLE -> Arm.VOICE
        currentMode == AssistantMode.AGENT -> Arm.RUN
        else -> Arm.TALK
    }
    LaunchedEffect(shownArm) { onArmShown(shownArm) }
    val conversationPlan = agentPlan?.takeIf { it.conversationId == vm.conversationId }
    // The live plan is cleared when a run ends. Keep the last one for this run, so the finished
    // card stays on screen (and the strip says RUN DONE) until a new run starts.
    val currentRunId = messages.lastOrNull { it.role == "USER" }?.id
    var heldPlan by remember(vm.conversationId) { mutableStateOf<Pair<String?, AgentPlan>?>(null) }
    LaunchedEffect(conversationPlan, currentRunId) {
        if (conversationPlan != null) heldPlan = currentRunId to conversationPlan
    }
    LaunchedEffect(generating) { if (generating && vm.agentPlan.value == null) heldPlan = null }
    val runPlan = conversationPlan ?: heldPlan?.takeIf { it.first == currentRunId }?.second
    val inkLine = when (shownArm) {
        Arm.VOICE -> InkLines.voice(voicePhase, voiceSearching)
        Arm.RUN -> InkLines.run(
            generating = generating,
            doneSteps = runPlan?.doneCount ?: 0,
            totalSteps = runPlan?.steps?.size ?: 0,
            // Not "Agent at work": that marker is up for the whole run and would read RUN AGENT.
            latestActivity = WorkChips.latestActivity(activities)
        )
        else -> {
            // "TALK READING 3 OF 5": the same counts the source readout shows.
            val lastAnswer = messages.lastOrNull()?.takeIf { it.role == "ASSISTANT" }
            val readout = lastAnswer?.let {
                SourceReadoutBuilder.build(
                    citations = repository.citations(it),
                    workLog = workLogs[it.id],
                    activities = if (generating) activities else emptyList(),
                    streaming = generating
                )
            }
            InkLines.talk(
                generating = generating,
                latestActivity = activities.lastOrNull(),
                step = readout?.let { it.done + 1 } ?: 0,
                of = readout?.total ?: 0
            )
        }
    }
    InkStripEffect(inkLine, arm = shownArm)

    // Talk: "Queue a follow-up" while a reply streams. One slot, held here in the UI and
    // sent when the reply finishes normally (FollowUpQueue decides; the VM is unchanged).
    var followUp by remember(vm.conversationId) { mutableStateOf(FollowUpQueue()) }
    var stopRequested by remember(vm.conversationId) { mutableStateOf(false) }
    var sawGenerating by remember(vm.conversationId) { mutableStateOf(false) }
    LaunchedEffect(generating) {
        if (generating) {
            sawGenerating = true
            stopRequested = false
            return@LaunchedEffect
        }
        if (!sawGenerating) return@LaunchedEffect
        sawGenerating = false
        val (next, action) = followUp.onGenerationEnded(
            stoppedByUser = stopRequested,
            failed = vm.error.value != null,
            editing = vm.editingFrom.value != null
        )
        followUp = next
        stopRequested = false
        when (action) {
            is FollowUpAction.Send -> {
                val draft = vm.composer.value
                vm.updateComposer(action.text)
                vm.send()
                if (draft.isNotBlank()) vm.updateComposer(draft)
            }
            is FollowUpAction.Restore -> vm.updateComposer(FollowUpQueue.mergeIntoDraft(action.text, vm.composer.value))
            FollowUpAction.None -> Unit
        }
    }
    var showTalkOptions by remember(vm.conversationId) { mutableStateOf(false) }
    var showRunOptions by remember(vm.conversationId) { mutableStateOf(false) }
    var showQualitySheet by remember(vm.conversationId) { mutableStateOf(false) }
    val inkStrip = LocalInkStrip.current
    fun launchImagePicker() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun launchCamera() {
        cameraFile.value = File(context.cacheDir, "kryzz-camera-${System.currentTimeMillis()}.jpg")
        captureImage.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile.value!!))
    }
    fun launchFilePicker() = pickAttachments.launch(arrayOf("*/*"))
    val paneCallbacks = TalkCallbacks(
        onOpenLibrary = onOpenLibrary,
        onEdit = vm::startEditing,
        onRegenerate = vm::regenerate,
        onSpeak = vm::speakMessage,
        onShowUsage = { usageMessage = it },
        onSaveOutput = {
            pendingOutput = it
            saveOutput.launch(it.fileName)
        },
        onOpenOutput = { openOutput(context, it) },
        onQuestionAnswer = vm::sendQuestionAnswer,
        onCreateSkill = vm::createSkillFromConversation
    )
    val baseColors = MaterialTheme.colorScheme
    val darkBackground = baseColors.background.luminance() < 0.45f
    val modeColors = if (currentMode == AssistantMode.AGENT) {
        baseColors.copy(
            primary = agentPrimaryColor(darkBackground),
            onPrimary = agentOnPrimaryColor(darkBackground),
            primaryContainer = if (darkBackground) DotColors.SurfaceHigh else DotColorsLight.SurfaceRaised,
            onPrimaryContainer = if (darkBackground) DotColors.TextPrimary else DotColorsLight.TextPrimary
        )
    } else baseColors

    MaterialTheme(colorScheme = modeColors) {
        Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        // Talk has no top bar (kit chat-b): the departure board starts right
                        // under the ink strip. Run has RUN and the thinking-mode pill (agent-run).
                        if (currentMode == AssistantMode.AGENT) RunHeader(
                            quality = settings.agentQuality,
                            onQuality = { showQualitySheet = true },
                            onOpenLibrary = onOpenLibrary
                        )
                    },
                    bottomBar = {
                        if (currentMode == AssistantMode.CHAT) ReplyLine(
                            value = composer,
                            onValueChange = vm::updateComposer,
                            generating = generating,
                            editing = editing != null,
                            queued = followUp.text,
                            attachments = pendingAttachments,
                            dictating = dictating,
                            dictationLevel = dictationLevel,
                            verticalPadding = settings.chatDensity.composerVerticalPadding(),
                            onSend = vm::send,
                            onQueue = {
                                followUp = followUp.add(composer)
                                vm.updateComposer("")
                            },
                            onStop = {
                                stopRequested = true
                                vm.stop()
                            },
                            onDictation = ::requestDictation,
                            onOptions = {
                                keyboard?.hide()
                                showTalkOptions = true
                            },
                            onCancelEdit = vm::cancelEditing,
                            onUnqueue = {
                                vm.updateComposer(FollowUpQueue.mergeIntoDraft(followUp.text.orEmpty(), composer))
                                followUp = followUp.clear()
                            },
                            onRemoveAttachment = vm::removeAttachment,
                            onFocusChanged = onComposerFocusChanged
                        ) else if (generating) RunActions(
                            // The agent has no pause, so Pause only says it is not here yet.
                            onPause = { inkStrip?.flash(InkLine("PAUSE NOT YET")) },
                            onStop = {
                                stopRequested = true
                                vm.stop()
                            }
                        ) else ReplyLine(
                            value = composer,
                            onValueChange = vm::updateComposer,
                            generating = false,
                            editing = editing != null,
                            queued = null,
                            attachments = pendingAttachments,
                            dictating = dictating,
                            dictationLevel = dictationLevel,
                            verticalPadding = settings.chatDensity.composerVerticalPadding(),
                            onSend = vm::send,
                            onQueue = {},
                            onStop = vm::stop,
                            onDictation = ::requestDictation,
                            onOptions = {
                                keyboard?.hide()
                                showRunOptions = true
                            },
                            onCancelEdit = vm::cancelEditing,
                            onUnqueue = {},
                            onRemoveAttachment = vm::removeAttachment,
                            onFocusChanged = onComposerFocusChanged,
                            idlePlaceholder = if (messages.isEmpty()) "Give Kryzz a task" else "Follow up",
                            optionsLabel = "Run options",
                            notes = {
                                if (agentModelNotice.visible) {
                                    KryzzAgentModelNoticeCard(
                                        notice = agentModelNotice,
                                        onSwitch = { vm.answerAgentModelSuggestion(true) },
                                        onKeep = { vm.answerAgentModelSuggestion(false) },
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                if (selectedCapability != AgentCapability.AUTO) {
                                    NoteRow(
                                        label = "WORKFLOW",
                                        text = selectedCapability.shortLabel,
                                        action = "Change",
                                        onAction = { showCapabilityPicker = true },
                                        accent = false
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        // The pager only animates from the header button now: swiping
                        // belongs to the explicit menu action in the header.
                        userScrollEnabled = false
                    ) { page ->
                        val mode = AssistantMode.entries[page]
                        if (mode == AssistantMode.CHAT) TalkPane(
                            conversationId = vm.conversationId,
                            messages = messages,
                            generating = generating,
                            error = error,
                            activities = activities,
                            workLogs = workLogs,
                            citations = repository::citations,
                            outputs = repository::outputs,
                            attachments = repository::attachments,
                            speakingMessageId = speakingMessageId,
                            synthesizingMessageId = synthesizingMessageId,
                            callbacks = paneCallbacks
                        ) else RunPane(
                            conversationId = vm.conversationId,
                            messages = messages,
                            generating = generating,
                            error = error,
                            activities = activities,
                            workLogs = workLogs,
                            plan = runPlan,
                            swarm = swarmStatus,
                            capability = selectedCapability,
                            capCents = HomeBoard.effectiveCapCents(settings.agentQuality, settings.costCapCents),
                            citations = repository::citations,
                            outputs = repository::outputs,
                            attachments = repository::attachments,
                            speakingMessageId = speakingMessageId,
                            synthesizingMessageId = synthesizingMessageId,
                            skillCreatedFor = skillCreatedFor,
                            callbacks = paneCallbacks,
                            onWorkflow = { showCapabilityPicker = true }
                        )
                    }
                }
                usageMessage?.let { selected ->
                    ConversationUsageDialog(
                        usage = usage,
                        message = selected,
                        onDismiss = { usageMessage = null }
                    )
                }
                if (showAiControls) {
                    KryzzChatAiControlsSheet(
                        purpose = activePurpose,
                        selectedModelId = activeModelId,
                        selectedReasoning = activeReasoning,
                        settings = settings,
                        textModels = models,
                        imageModels = imageModels,
                        videoModels = videoModels,
                        audioModels = audioModels,
                        modelsLoading = modelsLoading,
                        modelsError = modelsError,
                        mediaModelsError = mediaModelsError,
                        researchDepth = researchDepth,
                        researchWidth = researchWidth,
                        memoryEnabled = settings.memoryEnabled,
                        agentSwarmEnabled = settings.agentSwarmEnabled,
                        onDismiss = { showAiControls = false },
                        onSelectModel = { vm.selectModel(it, activePurpose) },
                        onReasoning = vm::setReasoning,
                        onResearchTuning = vm::setResearchTuning,
                        onMemoryEnabled = vm::setMemoryEnabled,
                        onAgentSwarmEnabled = vm::setAgentSwarmEnabled,
                        onOpenModels = { onModels(activePurpose) }
                    )
                }
                if (showTalkOptions) {
                    TalkOptionsSheet(
                        modeLocked = modeLocked,
                        webSearch = settings.searchEnabled,
                        modelLabel = activeModelId.compactModel(),
                        onRun = {
                            showTalkOptions = false
                            requestMode(AssistantMode.AGENT)
                        },
                        onWebSearch = onWebSearch,
                        onModel = {
                            showTalkOptions = false
                            showAiControls = true
                        },
                        onPickImage = { showTalkOptions = false; launchImagePicker() },
                        onPickCamera = { showTalkOptions = false; launchCamera() },
                        onPickFile = { showTalkOptions = false; launchFilePicker() },
                        onVoice = { showTalkOptions = false; requestVoice() },
                        onChats = { showTalkOptions = false; onOpenLibrary() },
                        onNewChat = { showTalkOptions = false; onNewChat(AssistantMode.CHAT) },
                        onDismiss = { showTalkOptions = false }
                    )
                }
                if (showRunOptions) {
                    RunOptionsSheet(
                        modeLocked = modeLocked,
                        workflowLabel = selectedCapability.shortLabel,
                        webSearch = settings.searchEnabled,
                        modelLabel = activeModelId.compactModel(),
                        onTalk = {
                            showRunOptions = false
                            requestMode(AssistantMode.CHAT)
                        },
                        onWorkflow = { showRunOptions = false; showCapabilityPicker = true },
                        onWebSearch = onWebSearch,
                        onModel = { showRunOptions = false; showAiControls = true },
                        onPickImage = { showRunOptions = false; launchImagePicker() },
                        onPickCamera = { showRunOptions = false; launchCamera() },
                        onPickFile = { showRunOptions = false; launchFilePicker() },
                        onVoice = { showRunOptions = false; requestVoice() },
                        onChats = { showRunOptions = false; onOpenLibrary() },
                        onNewRun = { showRunOptions = false; onNewChat(AssistantMode.AGENT) },
                        onDismiss = { showRunOptions = false }
                    )
                }
                if (showQualitySheet) {
                    QualitySheet(
                        selected = settings.agentQuality,
                        capCents = settings.costCapCents,
                        onSelect = vm::setAgentQuality,
                        onDismiss = { showQualitySheet = false }
                    )
                }
                if (showCapabilityPicker) {
                    AgentCapabilitySheet(
                        selected = selectedCapability,
                        onSelect = {
                            vm.capability.value = it
                            showCapabilityPicker = false
                        },
                        onDismiss = { showCapabilityPicker = false }
                    )
                }
                if (voicePhase != VoicePhase.IDLE) {
                    VoiceChatOverlay(
                        phase = voicePhase,
                        level = voiceLevel,
                        transcript = voiceTranscript,
                        searching = vm.voiceSearching.collectAsStateWithLifecycle().value,
                        muted = vm.voiceMicMuted.collectAsStateWithLifecycle().value,
                        onTap = vm::tapVoiceBubble,
                        onMuteToggle = { vm.setVoiceMuted(!vm.voiceMicMuted.value) },
                        onClose = vm::cancelVoice
                    )
                }
                // The agent's ask_user tool suspends until this card is answered or
                // dismissed (X → "skipped" goes back to the model as the tool result).
                pendingQuestion?.takeIf { it.conversationId == vm.conversationId }?.let { pending ->
                    Box(
                        Modifier.fillMaxSize().imePadding().navigationBarsPadding(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        QuestionCard(
                            question = pending.question,
                            onAnswer = { vm.answerPendingQuestion(it) },
                            onDismiss = { vm.answerPendingQuestion(null) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                        )
                    }
                }
        }
    }
}

@Composable
private fun ConversationUsageDialog(
    usage: ConversationUsage,
    message: MessageEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversation usage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UsageLine("Total tokens", usage.totalTokens.toString())
                UsageLine("Input tokens", usage.inputTokens.toString())
                UsageLine("Cache hit", usage.cacheHitInputTokens.toString())
                UsageLine("Cache miss", usage.cacheMissInputTokens.toString())
                UsageLine("Output tokens", usage.outputTokens.toString())
                UsageLine("Total estimated cost", String.format(Locale.US, "$%.6f", usage.cost))
                UsageLine("First token latency", formatLatencyMs(message.firstTokenLatencyMs))
                UsageLine("Total response time", formatLatencyMs(message.totalGenerationTimeMs))
                Text(
                    "Cache hits are provider-reported. Input tokens without cache details are counted as cache misses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun UsageLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val code = Regex("```([A-Za-z0-9_+.-]*)[\\t ]*\\n?([\\s\\S]*?)```", RegexOption.MULTILINE)
    val matches = remember(markdown) { code.findAll(markdown).toList() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var cursor = 0
        matches.forEach { match ->
            val before = markdown.substring(cursor, match.range.first)
            if (before.isNotBlank()) RichMarkdownBody(before.trim(), contentColor)
            CodeBlock(
                language = match.groupValues[1],
                content = match.groupValues[2].trimEnd()
            )
            cursor = match.range.last + 1
        }
        val rest = markdown.substring(cursor)
        if (rest.isNotBlank()) RichMarkdownBody(rest.trim(), contentColor)
    }
}

@Composable
private fun CodeBlock(language: String, content: String) {
    val context = LocalContext.current
    // Kit code card: surface with a 1dp border, radius 16, Geist Mono, mono caption header.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_kz_code), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    language.ifBlank { "Code" }.uppercase(),
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = DotType.Label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Kryzz AI code", content))
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, "Copy code", Modifier.size(17.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp)) {
                Text(content, style = DotType.Code, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun MarkdownParagraph(text: String, contentColor: Color) {
    val context = LocalContext.current
    val annotated = annotatedMarkdown(text, MaterialTheme.colorScheme.primary)
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = contentColor)),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item))) } }
        }
    )
}

private fun annotatedMarkdown(input: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val token = Regex("\\[([^]]+)]\\((https?://[^)]+)\\)|\\*\\*([^*]+)\\*\\*|`([^`]+)`")
    var cursor = 0
    token.findAll(input).forEach { match ->
        append(input.substring(cursor, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() -> {
                pushStringAnnotation("URL", match.groupValues[2]); pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                append(match.groupValues[1]); pop(); pop()
            }
            match.groupValues[3].isNotEmpty() -> { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(match.groupValues[3]); pop() }
            else -> { pushStyle(SpanStyle(fontFamily = DataMonoFamily)); append(match.groupValues[4]); pop() }
        }
        cursor = match.range.last + 1
    }
    append(input.substring(cursor))
}

private suspend fun writeOutput(context: Context, uri: Uri, output: GeneratedOutput) {
    context.contentResolver.openOutputStream(uri)?.use { destination ->
        when {
            output.localPath != null -> File(output.localPath).inputStream().use { it.copyTo(destination) }
            output.content != null -> destination.write(output.content.toByteArray())
            else -> output.remoteUrl?.let { destination.write(it.toByteArray()) }
        }
    }
}

private fun openOutput(context: Context, output: GeneratedOutput) {
    val uri = output.localPath?.let { path ->
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", File(path)) }.getOrNull()
    } ?: output.remoteUrl?.let(Uri::parse) ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, output.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// v5.10: under the Max quality preset, agent and research turns run on the Max slot, so the
// header, AI controls and Models shortcut show and edit that slot.
private fun activeModelPurpose(mode: AssistantMode, capability: AgentCapability, quality: AgentQuality): ModelPurpose = when {
    mode == AssistantMode.CHAT -> ModelPurpose.CHAT
    capability == AgentCapability.IMAGE -> ModelPurpose.IMAGE
    capability == AgentCapability.VIDEO -> ModelPurpose.VIDEO
    capability == AgentCapability.AUDIO -> ModelPurpose.AUDIO
    quality == AgentQuality.MAX -> ModelPurpose.MAX
    capability in setOf(AgentCapability.DEEP_RESEARCH, AgentCapability.WIDE_SEARCH) -> ModelPurpose.RESEARCH
    else -> ModelPurpose.AGENT
}

private fun SettingsState.modelFor(purpose: ModelPurpose): String = when (purpose) {
    ModelPurpose.CHAT -> defaultModel
    ModelPurpose.AGENT -> agentModel
    ModelPurpose.RESEARCH -> researchModel
    ModelPurpose.MAX -> maxModel.ifBlank { agentModel }
    ModelPurpose.IMAGE -> imageModel
    ModelPurpose.VIDEO -> videoModel
    ModelPurpose.AUDIO -> audioModel
}

private fun String.compactModel(): String = substringAfter('/').ifBlank { "Select model" }

/**
 * Full-screen voice chat session: an opaque surface, a live bubble in the centre whose
 * icon and circle grow and shrink with the real audio — the microphone level while the
 * user speaks, the TTS playback level while Kryzz replies — and a quiet fixed state
 * while the answer is prepared. The session stays in voice mode: after a reply the mic
 * re-arms automatically. Tap the bubble to stop listening or skip the reply; use the
 * close button to leave.
 */
@Composable
private fun VoiceChatOverlay(
    phase: VoicePhase,
    level: Float,
    transcript: String,
    searching: Boolean,
    muted: Boolean,
    onTap: () -> Unit,
    onMuteToggle: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(enabled = true, onBack = onClose)
    val motionEnabled = LocalKryzzMotionEnabled.current
    val baseColors = MaterialTheme.colorScheme
    val bubbleColor = baseColors.onBackground
    val onBubble = baseColors.background
    // Voice chat is hands-free and the user is rarely looking at the phone — the
    // screen must stay awake for the whole session. Toggling the property via a
    // DisposableEffect (rather than FLAG_KEEP_SCREEN_ON on the window) means the
    // assignment is automatically reverted the instant the overlay leaves the tree,
    // so normal chat immediately goes back to honouring the user's screen timeout.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val reactiveLevel = if (motionEnabled) level.coerceIn(0f, 1f) else 0f
    val baseSize = 132.dp
    val coreSize: Dp = when (phase) {
        // The circle breathes with real microphone/playback energy when motion is enabled.
        VoicePhase.LISTENING -> baseSize + (44.dp * reactiveLevel)
        VoicePhase.PROCESSING -> 124.dp
        VoicePhase.SPEAKING -> baseSize + (44.dp * reactiveLevel)
        VoicePhase.IDLE -> 0.dp
    }
    val animatedCore by animateDpAsState(
        targetValue = coreSize,
        animationSpec = if (motionEnabled) tween(120) else snap(),
        label = "voice core"
    )
    val orbitalMotion = rememberInfiniteTransition(label = "voice orbital field")
    val orbitProgress by orbitalMotion.animateFloat(
        initialValue = 0f,
        targetValue = if (motionEnabled) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(12_000), RepeatMode.Restart),
        label = "voice orbit progress"
    )

    Box(
        Modifier
            .fillMaxSize()
            // Fully opaque: normal chatting is not visible behind the voice session.
            .background(baseColors.background)
            .testTag("voice_overlay")
            .safeDrawingPadding(),
            // The voice overlay is a descendant of KryzzSidePanelHost, whose parent Box
            // runs a `pointerInput` for the edge-swipe gesture. That handler only consumes
            // after a real horizontal swipe past slop — it does NOT swallow the down event.
            // For the same reason we DO NOT swallow the down event here: the close
            // IconButton (top-right) and the bottom action buttons must receive their
            // presses. The previous "consume every pointer" overlay ate every click
            // (5.1.6 buttons-broken bug — see VoiceChatOverlay history). We leave gesture
            // handling to the side-panel swipe handler and let children receive all events.
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(54) { index ->
                val x = size.width * (((index * 71 + 17) % 101) / 101f)
                val y = size.height * (((index * 41 + 23) % 103) / 103f)
                val shimmer = if (motionEnabled) {
                    0.5f + 0.5f * kotlin.math.sin(
                        (orbitProgress * 2f * kotlin.math.PI + index * 0.73f).toDouble()
                    ).toFloat()
                } else 0.5f
                drawCircle(
                    color = baseColors.onBackground.copy(
                        alpha = 0.035f + (index % 4) * 0.012f + shimmer * 0.035f
                    ),
                    radius = (0.55f + (index % 3) * 0.28f).dp.toPx(),
                    center = Offset(x + shimmer * 1.2.dp.toPx(), y)
                )
            }
        }

        Text(
            "VOICE SESSION",
            modifier = Modifier.align(Alignment.TopStart).padding(top = 24.dp, start = 24.dp),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = baseColors.onSurfaceVariant
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                // Extra bottom padding so the scrollable content clears the bottom
                // mute toggle (~80.dp from the safe-drawing bottom) without overlapping it.
                .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 96.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 288.dp)
                    .height(288.dp)
                    .testTag("voice_orbital_field"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val centre = center
                    val coreRadius = animatedCore.toPx() * 0.5f
                    val innerOrbit = coreRadius + 22.dp.toPx()
                    val outerOrbit = coreRadius + 48.dp.toPx()
                    drawCircle(
                        color = bubbleColor.copy(alpha = 0.035f + reactiveLevel * 0.055f),
                        radius = coreRadius + 12.dp.toPx(),
                        center = centre
                    )
                    drawCircle(
                        color = baseColors.onBackground.copy(alpha = 0.13f),
                        radius = innerOrbit,
                        center = centre,
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                    drawCircle(
                        color = baseColors.onBackground.copy(alpha = 0.06f),
                        radius = outerOrbit,
                        center = centre,
                        style = Stroke(width = 0.65.dp.toPx())
                    )
                    repeat(18) { index ->
                        val direction = if (index % 2 == 0) 1f else -0.62f
                        val angle = index * 2.399963f + orbitProgress * 2f * kotlin.math.PI.toFloat() * direction
                        val wave = kotlin.math.sin(
                            (orbitProgress * 2f * kotlin.math.PI + index * 0.81f).toDouble()
                        ).toFloat()
                        val radius = innerOrbit + (index % 4) * 9.dp.toPx() + wave * 3.dp.toPx()
                        val particle = Offset(
                            centre.x + kotlin.math.cos(angle.toDouble()).toFloat() * radius,
                            centre.y + kotlin.math.sin(angle.toDouble()).toFloat() * radius
                        )
                        val bright = index % 6 == 0
                        drawCircle(
                            color = baseColors.onBackground.copy(alpha = if (bright) 0.72f else 0.25f + (index % 3) * 0.08f),
                            radius = if (bright) 2.dp.toPx() else (0.85f + (index % 3) * 0.3f).dp.toPx(),
                            center = particle
                        )
                    }
                }
                Surface(
                    onClick = onTap,
                    modifier = Modifier
                        .size(animatedCore)
                        .clip(CircleShape)
                        .testTag("voice_primary_action")
                        .semantics { contentDescription = voiceOverlayDescription(phase) },
                    shape = CircleShape,
                    color = bubbleColor,
                    contentColor = onBubble,
                    shadowElevation = 0.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (phase) {
                            VoicePhase.LISTENING -> Icon(
                                Icons.Outlined.RecordVoiceOver,
                                null,
                                Modifier.size(animatedCore * 0.4f),
                                tint = onBubble
                            )
                            VoicePhase.PROCESSING -> {
                                val processIcon = if (searching) Icons.Outlined.Public else Icons.Outlined.AutoAwesome
                                Icon(
                                    processIcon,
                                    null,
                                    Modifier
                                        .size(animatedCore * 0.36f)
                                        .graphicsLayer { rotationZ = if (motionEnabled) orbitProgress * 1_440f else 0f },
                                    tint = onBubble
                                )
                            }
                            VoicePhase.SPEAKING -> Icon(
                                Icons.AutoMirrored.Outlined.VolumeUp,
                                null,
                                Modifier.size(animatedCore * 0.4f),
                                tint = onBubble
                            )
                            VoicePhase.IDLE -> {}
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            if (transcript.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = baseColors.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = baseColors.onSurface,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Text(
                        transcript,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = if (searching) "Checking online" else voicePhaseTitle(phase),
                style = MaterialTheme.typography.headlineSmall,
                color = baseColors.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (searching) "Searching the web for fresh facts"
                    else voicePhaseHint(phase, muted),
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = baseColors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Mute toggle. Promoted to its own row, pinned to the very bottom of the screen
        // (lowered by an extra 24.dp so it sits in the natural thumb-reach zone on tall
        // phones) and always visible while the session is active — including PROCESSING,
        // where the user previously had no recourse when they wanted to cut the mic before
        // the next reply. The button is large (72.dp), shows a visible "Mute"/"Unmute"
        // label, and is visually inverted (error-tinted) when active so the muted state is
        // unmissable.
        if (phase != VoicePhase.IDLE) {
            Surface(
                onClick = onMuteToggle,
                shape = RoundedCornerShape(36.dp),
                color = if (muted) baseColors.errorContainer
                    else baseColors.surfaceVariant.copy(alpha = 0.7f),
                contentColor = if (muted) baseColors.onErrorContainer
                    else baseColors.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .height(56.dp)
                    .widthIn(min = 180.dp)
                    .testTag("voice_mute_toggle")
                    .semantics {
                        contentDescription = if (muted) "Unmute microphone" else "Mute microphone"
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (muted) Icons.Outlined.MicOff else Icons.Outlined.MicNone,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (muted) "Unmute" else "Mute",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        // Close (quit) button. Drawn AFTER the Column and the Mute Surface so it stacks
        // on top of them in the z-order. The Column has fillMaxHeight() and would otherwise
        // sit on top of the close button area at top-right, silently blocking taps.
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(52.dp)
                .testTag("voice_close_button")
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "End voice chat")
        }
    }
}

internal fun voicePhaseTitle(phase: VoicePhase): String = when (phase) {
    VoicePhase.LISTENING -> "Listening"
    VoicePhase.PROCESSING -> "Thinking"
    VoicePhase.SPEAKING -> "Speaking"
    VoicePhase.IDLE -> ""
}

internal fun voicePhaseHint(phase: VoicePhase, muted: Boolean = false): String = when (phase) {
    VoicePhase.LISTENING -> if (muted) "Microphone muted · tap the mic to unmute" else "Speak naturally · sends when you finish · tap to send now"
    VoicePhase.PROCESSING -> "Thinking · searching if needed"
    VoicePhase.SPEAKING -> if (muted) "Microphone muted · tap the mic to unmute" else "Start speaking to interrupt · or tap the orb"
    VoicePhase.IDLE -> ""
}

internal fun voiceOverlayDescription(phase: VoicePhase): String = when (phase) {
    VoicePhase.LISTENING -> "Voice chat listening. Recording sends automatically after you finish. Tap to send now."
    VoicePhase.PROCESSING -> "Voice chat processing. Tap to cancel."
    VoicePhase.SPEAKING -> "Voice chat speaking. Tap to interrupt."
    VoicePhase.IDLE -> "Voice chat"
}
