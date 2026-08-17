package ai.daylight.assistant.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.Citation
import ai.daylight.assistant.domain.ChatAttachment
import ai.daylight.assistant.domain.ChatDensity
import ai.daylight.assistant.domain.ConversationUsage
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.GradientPalette
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.ReasoningEffort
import ai.daylight.assistant.domain.SubagentState
import ai.daylight.assistant.domain.SwarmPhase
import ai.daylight.assistant.domain.SwarmStatus
import ai.daylight.assistant.ui.theme.LocalGlassOpacity
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.voice.VoicePhase
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun agentPrimaryColor(darkBackground: Boolean): Color =
    if (darkBackground) Color(0xFFF4F4F1) else Color(0xFF1A1A1D)

internal fun agentOnPrimaryColor(darkBackground: Boolean): Color =
    if (darkBackground) Color(0xFF050506) else Color.White

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
    rootNavVisible: Boolean = true
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
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
    // speak: the mic session starts once, on the first composition.
    var initialVoiceConsumed by rememberSaveable(vm.conversationId) { mutableStateOf(false) }
    LaunchedEffect(vm.conversationId, initialVoice, initialVoiceConsumed) {
        if (initialVoice && !initialVoiceConsumed) {
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
    val activePurpose = activeModelPurpose(currentMode, selectedCapability)
    val activeModelId = settings.modelFor(activePurpose)
    val activeReasoning = settings.modelReasoning[activeModelId] ?: ReasoningEffort.AUTO
    val baseColors = MaterialTheme.colorScheme
    val darkBackground = baseColors.background.luminance() < 0.45f
    val modeColors = if (currentMode == AssistantMode.AGENT) {
        baseColors.copy(
            primary = agentPrimaryColor(darkBackground),
            onPrimary = agentOnPrimaryColor(darkBackground),
            primaryContainer = if (darkBackground) Color(0xFF25262A) else Color(0xFFE7E6E2),
            onPrimaryContainer = if (darkBackground) Color(0xFFF4F4F1) else Color(0xFF1B1B1E)
        )
    } else baseColors

    MaterialTheme(colorScheme = modeColors) {
        Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                ChatHeader(
                            title = title,
                            modelId = activeModelId,
                            mode = currentMode,
                            modeLocked = modeLocked,
                            onModeSwitch = ::requestMode,
                            onNewChat = onNewChat,
                            onOpenLibrary = onOpenLibrary,
                            onModelClick = { showAiControls = true }
                        )
                    },
                    bottomBar = {
                        Composer(
                            value = composer,
                            density = settings.chatDensity,
                            onValue = vm::updateComposer,
                            generating = generating,
                            editing = editing != null,
                            mode = currentMode,
                            capability = selectedCapability,
                            attachments = pendingAttachments,
                            rootNavVisible = rootNavVisible,
                            onPickImage = {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onPickCamera = {
                                cameraFile.value = File(context.cacheDir, "kryzz-camera-${System.currentTimeMillis()}.jpg")
                                captureImage.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile.value!!))
                            },
                            onPickFile = { pickAttachments.launch(arrayOf("*/*")) },
onRemoveAttachment = vm::removeAttachment,
                            dictating = dictating,
                            dictationLevel = dictationLevel,
                            onDictation = ::requestDictation,
                            onCapability = { showCapabilityPicker = true },
                            onSend = vm::send,
                            onStop = vm::stop,
                            onVoice = ::requestVoice,
                            onCancelEdit = vm::cancelEditing,
                            onComposerFocusChanged = onComposerFocusChanged
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
                        ChatPane(
                            mode = mode,
                            density = settings.chatDensity,
                            messages = messages,
                            error = error,
                            generating = generating,
                            capability = selectedCapability,
                            citations = repository::citations,
                            outputs = repository::outputs,
                            attachments = repository::attachments,
                            activities = activities,
                            swarmStatus = swarmStatus,
                            onRegenerate = vm::regenerate,
                            onEdit = vm::startEditing,
                            onSaveOutput = {
                                pendingOutput = it
                                saveOutput.launch(it.fileName)
                            },
                            onOpenOutput = { openOutput(context, it) },
                            onSpeak = vm::speakMessage,
                            speakingMessageId = speakingMessageId,
                            synthesizingMessageId = synthesizingMessageId,
                            onShowUsage = { usageMessage = it },
                            skillCreatedFor = skillCreatedFor,
                            onCreateSkill = vm::createSkillFromConversation
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
                        muted = vm.voiceMicMuted.collectAsStateWithLifecycle().value,
                        onTap = vm::tapVoiceBubble,
                        onMuteToggle = { vm.setVoiceMuted(!vm.voiceMicMuted.value) },
                        onClose = vm::cancelVoice
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHeader(
    title: String,
    modelId: String,
    mode: AssistantMode,
    modeLocked: Boolean,
    onModeSwitch: (AssistantMode) -> Unit,
    onNewChat: (AssistantMode) -> Unit,
    onOpenLibrary: () -> Unit,
    onModelClick: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassOpacity.current),
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
            // Conversation navigation lives in the left side panel.
            IconButton(onClick = onOpenLibrary, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Menu, "Open menu")
            }
        },
        title = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onModelClick)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                KryzzWordmark(
                    Modifier.size(width = 70.dp, height = 22.dp),
                    contentDescription = null
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        modelId.compactModel(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
actions = {
            val motionEnabled = LocalKryzzMotionEnabled.current
            AnimatedContent(
                targetState = modeLocked,
                transitionSpec = {
                    if (motionEnabled) {
                        (scaleIn(initialScale = 0.8f) + fadeIn(tween(160)))
                            .togetherWith(scaleOut(targetScale = 0.8f) + fadeOut(tween(130)))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "mode lock to new chat"
            ) { locked ->
                if (locked) {
                    // The conversation has a first message: the switcher becomes a
                    // single button that starts a fresh conversation in this mode.
                    NewChatButton(mode, onNewChat, Modifier.padding(end = 6.dp))
                } else {
                    ModeSwitcher(mode, onModeSwitch, Modifier.padding(end = 6.dp))
                }
            }
        }
    )
}

@Composable
private fun ModeSwitcher(
    current: AssistantMode,
    onSwitch: (AssistantMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 38.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeSwitcherSegment(
                label = "Chat",
                icon = Icons.Outlined.ChatBubbleOutline,
                selected = current == AssistantMode.CHAT,
                onClick = { onSwitch(AssistantMode.CHAT) }
            )
            ModeSwitcherSegment(
                label = "Agent",
                icon = Icons.Outlined.AutoAwesome,
                selected = current == AssistantMode.AGENT,
                onClick = { onSwitch(AssistantMode.AGENT) }
            )
        }
    }
}

@Composable
private fun ModeSwitcherSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val description = "$label mode"
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .semantics { contentDescription = description }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, Modifier.size(15.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

/**
 * What the mode switcher becomes once a conversation has its first message: one button
 * that starts a fresh conversation in the current (locked) mode.
 */
@Composable
private fun NewChatButton(
    mode: AssistantMode,
    onNewChat: (AssistantMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onNewChat(mode) },
        modifier = modifier
            .heightIn(min = 38.dp)
            .semantics { contentDescription = "New chat" },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
            Text(
                "New chat",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChatPane(
    mode: AssistantMode,
    density: ChatDensity,
    messages: List<MessageEntity>,
    error: String?,
    generating: Boolean,
    capability: AgentCapability,
    citations: (MessageEntity) -> List<Citation>,
    outputs: (MessageEntity) -> List<GeneratedOutput>,
    attachments: (MessageEntity) -> List<ChatAttachment>,
    onRegenerate: () -> Unit,
    onEdit: (MessageEntity) -> Unit,
    onSaveOutput: (GeneratedOutput) -> Unit,
    onOpenOutput: (GeneratedOutput) -> Unit,
    onSpeak: (String) -> Unit,
    speakingMessageId: String?,
    synthesizingMessageId: String?,
    onShowUsage: (MessageEntity) -> Unit,
    skillCreatedFor: Set<String>,
    onCreateSkill: (MessageEntity) -> Unit,
    activities: List<String> = emptyList(),
    swarmStatus: SwarmStatus? = null
) {
    MessageList(
        mode, messages, error, generating, capability, density, citations, outputs, attachments,
        onRegenerate, onEdit, onSaveOutput, onOpenOutput, onSpeak,
        speakingMessageId, synthesizingMessageId,
        onShowUsage,
        skillCreatedFor, onCreateSkill, activities, swarmStatus, Modifier.fillMaxSize()
    )
}

@Composable
private fun MessageList(
    mode: AssistantMode,
    messages: List<MessageEntity>,
    error: String?,
    generating: Boolean,
    capability: AgentCapability,
    density: ChatDensity,
    citations: (MessageEntity) -> List<Citation>,
    outputs: (MessageEntity) -> List<GeneratedOutput>,
    attachments: (MessageEntity) -> List<ChatAttachment>,
    onRegenerate: () -> Unit,
    onEdit: (MessageEntity) -> Unit,
    onSaveOutput: (GeneratedOutput) -> Unit,
    onOpenOutput: (GeneratedOutput) -> Unit,
    onSpeak: (String) -> Unit,
    speakingMessageId: String?,
    synthesizingMessageId: String?,
    onShowUsage: (MessageEntity) -> Unit,
    skillCreatedFor: Set<String>,
    onCreateSkill: (MessageEntity) -> Unit,
    activities: List<String> = emptyList(),
    swarmStatus: SwarmStatus? = null,
    modifier: Modifier = Modifier
) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val listState = rememberLazyListState()
    var followNewest by remember { mutableStateOf(true) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val lastAssistant = messages.indexOfLast { it.role == "ASSISTANT" }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            Triple(listState.isScrollInProgress, lastVisible, info.totalItemsCount)
        }.collect { (scrolling, lastVisible, totalItems) ->
            if (scrolling && !programmaticScroll) {
                followNewest = totalItems == 0 || lastVisible >= totalItems - 1
            }
        }
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty() && followNewest) {
            programmaticScroll = true
            listState.scrollToItem(messages.lastIndex)
            programmaticScroll = false
        }
    }
    if (messages.isEmpty()) {
        EmptyWorkspace(mode, capability, modifier)
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(density.messageSpacing())
        ) {
            itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                val messageCitations = remember(message.id, message.citationsJson) { citations(message) }
                val messageOutputs = remember(message.id, message.outputsJson) { outputs(message) }
                val messageAttachments = remember(message.id, message.attachmentsJson) { attachments(message) }
                AnimatedVisibility(
                    visible = true,
                    enter = if (motionEnabled) {
                        fadeIn(tween(130)) + slideInVertically(tween(150)) { it / 5 }
                    } else {
                        EnterTransition.None
                    }
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        MessageItem(
                            message,
                            citations = messageCitations,
                            outputs = messageOutputs,
                            attachments = messageAttachments,
                            canRegenerate = index == lastAssistant && !generating,
                            canCreateSkill = index == lastAssistant && !generating &&
                                message.role == "ASSISTANT" && message.mode == AssistantMode.AGENT.name &&
                                message.status == MessageStatus.COMPLETE.name,
                            skillCreated = message.id in skillCreatedFor,
                            onRegenerate = onRegenerate,
                            onEdit = { onEdit(message) },
                            onSaveOutput = onSaveOutput,
                            onOpenOutput = onOpenOutput,
                            onSpeak = { onSpeak(message.id) },
                            speaking = message.id == speakingMessageId,
                            synthesizing = message.id == synthesizingMessageId,
                            onShowUsage = { onShowUsage(message) },
                            onCreateSkill = { onCreateSkill(message) },
                            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()
                        )
                    }
                }
            }
            activities.forEach { tool ->
                item(key = "tool-activity-$tool") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            Text("$tool…", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            swarmStatus?.let { swarm ->
                item(key = "swarm-status") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        SwarmStatusCard(swarm, motionEnabled, Modifier.widthIn(max = 860.dp).fillMaxWidth())
                    }
                }
            }
            error?.let {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.widthIn(max = 860.dp).padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SwarmStatusCard(
    status: SwarmStatus,
    motionEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val glow = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "swarm-glow")
        transition.animateFloat(
            initialValue = 0.22f,
            targetValue = 0.62f,
            animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
            label = "swarm-glow-alpha"
        ).value
    } else 0.4f
    val active = status.phase == SwarmPhase.PLANNING || status.phase == SwarmPhase.RUNNING ||
        status.phase == SwarmPhase.SYNTHESIZING
    val runningCount = status.subagents.count { it.state == SubagentState.RUNNING }
    val phaseLabel = when (status.phase) {
        SwarmPhase.PLANNING -> "Planning the swarm…"
        SwarmPhase.RUNNING -> if (runningCount > 0) {
            "$runningCount subagent${if (runningCount == 1) "" else "s"} working…"
        } else "Subagents queued…"
        SwarmPhase.SYNTHESIZING -> "Merging results…"
        SwarmPhase.DONE -> "Swarm complete"
        SwarmPhase.FAILED -> "Swarm stopped"
    }
    AnimatedVisibility(
        visible = true,
        enter = if (motionEnabled) fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 4 } else EnterTransition.None,
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (active) glow else 0.3f))
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when {
                        active -> CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        status.phase == SwarmPhase.DONE -> Icon(
                            Icons.Outlined.CheckCircle, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        else -> Icon(
                            Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    AnimatedContent(
                        targetState = phaseLabel,
                        transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(140)) },
                        label = "swarm-phase"
                    ) { label ->
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                status.subagents.forEach { sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (sub.state) {
                            SubagentState.RUNNING -> CircularProgressIndicator(
                                modifier = Modifier.size(13.dp).graphicsLayer { alpha = glow.coerceIn(0.35f, 1f) + 0.35f },
                                strokeWidth = 2.dp
                            )
                            SubagentState.DONE -> Icon(
                                Icons.Outlined.CheckCircle, "Subagent finished", Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            SubagentState.FAILED -> Icon(
                                Icons.Outlined.ErrorOutline, "Subagent failed", Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            SubagentState.PENDING -> Icon(
                                Icons.Outlined.Schedule, "Subagent queued", Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            sub.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (sub.state == SubagentState.PENDING) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWorkspace(
    mode: AssistantMode,
    capability: AgentCapability,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (mode == AssistantMode.CHAT) "How can I help?" else capability.title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (mode == AssistantMode.CHAT) {
                "Ask anything. Your conversations stay on this device."
            } else {
                "${capability.shortLabel} workflow selected. Tap the workflow badge below to change it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    value: String,
    density: ChatDensity,
    onValue: (String) -> Unit,
    generating: Boolean,
    editing: Boolean,
    mode: AssistantMode,
    capability: AgentCapability,
    attachments: List<ChatAttachment>,
    rootNavVisible: Boolean,
    onPickImage: () -> Unit,
    onPickCamera: () -> Unit,
    onPickFile: () -> Unit,
onRemoveAttachment: (String) -> Unit,
    dictating: Boolean,
    dictationLevel: Float,
    onDictation: () -> Unit,
    onCapability: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit,
    onCancelEdit: () -> Unit,
    onComposerFocusChanged: (Boolean) -> Unit
) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    var attachMenu by remember { mutableStateOf(false) }
    // With nothing typed and no attachments, the primary action is voice chat:
    // the mic button replaces the send arrow until the user starts typing.
    val showVoiceButton = !generating && !editing && value.isBlank() && attachments.isEmpty()
    val insetModifier = if (rootNavVisible) {
        Modifier.imePadding()
    } else {
        Modifier.navigationBarsPadding().imePadding()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .then(insetModifier)
            .padding(horizontal = 12.dp, vertical = density.composerVerticalPadding())
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = 860.dp).fillMaxWidth()
        ) {
        if (editing) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Edit, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Text(" Editing an earlier message", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onCancelEdit) { Text("Cancel") }
        }
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = (LocalGlassOpacity.current + 0.16f).coerceAtMost(0.96f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f)),
            shadowElevation = 10.dp
        ) {
            Column(Modifier.padding(start = 6.dp, end = 7.dp, top = 4.dp, bottom = 6.dp)) {
                if (mode == AssistantMode.AGENT) {
                    Surface(
                        onClick = onCapability,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Workflow · ${capability.shortLabel}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (attachments.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(attachments, key = { it.id }) { attachment ->
                            Surface(
                                shape = RoundedCornerShape(13.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.InsertDriveFile, null, Modifier.size(15.dp))
                                    Text(" ${attachment.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(max = 150.dp))
                                    IconButton(onClick = { onRemoveAttachment(attachment.id) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Outlined.Close, "Remove ${attachment.name}", Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { attachMenu = true }, enabled = !generating, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.AttachFile, "Attach images, videos, or files", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Image") },
                                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                onClick = { attachMenu = false; onPickImage() }
                            )
                            DropdownMenuItem(
                                text = { Text("Camera") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                                onClick = { attachMenu = false; onPickCamera() }
                            )
                            DropdownMenuItem(
                                text = { Text("File") },
                                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                onClick = { attachMenu = false; onPickFile() }
                            )
                        }
                    }
                    Box(Modifier.size(48.dp)) {
                        DictationMic(level = dictationLevel, active = dictating, onClick = onDictation, modifier = Modifier.fillMaxSize())
                    }
                    TextField(
                        value = value,
                        onValueChange = onValue,
                        placeholder = { Text(when {
                            dictating -> "Listening…"
                            editing -> "Edit and resend"
                            mode == AssistantMode.CHAT -> "Message Kryzz…"
                            else -> "Give Kryzz a task…"
                        }) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp, max = 180.dp)
                            .onFocusChanged { onComposerFocusChanged(it.isFocused) },
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (!generating) onSend() })
                    )
                    FilledIconButton(
                        onClick = if (generating) onStop else if (showVoiceButton) onVoice else onSend,
                        enabled = generating || value.isNotBlank() || attachments.isNotEmpty() || showVoiceButton,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (motionEnabled) {
                            AnimatedContent(
                                targetState = ComposerAction.from(generating, showVoiceButton),
                                transitionSpec = { (scaleIn(tween(80)) + fadeIn(tween(80))).togetherWith(scaleOut(tween(80)) + fadeOut(tween(80))) },
                                label = "send voice stop"
                            ) { action ->
                                when (action) {
                                    ComposerAction.STOP -> Icon(Icons.Outlined.Stop, "Stop generation", Modifier.size(20.dp))
                                    ComposerAction.VOICE -> Icon(Icons.Outlined.RecordVoiceOver, "Voice chat", Modifier.size(20.dp))
                                    ComposerAction.SEND -> Icon(Icons.AutoMirrored.Rounded.Send, "Send", Modifier.size(20.dp))
                                }
                            }
                        } else {
                            when {
                                generating -> Icon(Icons.Outlined.Stop, "Stop generation", Modifier.size(20.dp))
                                showVoiceButton -> Icon(Icons.Outlined.RecordVoiceOver, "Voice chat", Modifier.size(20.dp))
                                else -> Icon(Icons.AutoMirrored.Rounded.Send, "Send", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        Text(
            if (mode == AssistantMode.CHAT) "Chat can make mistakes. Check important information."
            else "Agent tools may use paid provider requests. Review generated files before use.",
            Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }
}

@Composable
private fun MessageItem(
    message: MessageEntity,
    citations: List<Citation>,
    outputs: List<GeneratedOutput>,
    attachments: List<ChatAttachment>,
    canRegenerate: Boolean,
    canCreateSkill: Boolean,
    skillCreated: Boolean,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onSaveOutput: (GeneratedOutput) -> Unit,
    onOpenOutput: (GeneratedOutput) -> Unit,
    onSpeak: () -> Unit,
    speaking: Boolean,
    synthesizing: Boolean,
    onShowUsage: () -> Unit,
    onCreateSkill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "USER"
    val context = LocalContext.current
    val motionEnabled = LocalKryzzMotionEnabled.current
    val startedStreaming = remember(message.id) { message.status == MessageStatus.STREAMING.name }
    var revealedLength by remember(message.id) {
        mutableIntStateOf(if (startedStreaming && motionEnabled) 0 else message.content.length)
    }
    LaunchedEffect(message.id, message.content, message.status, motionEnabled) {
        if (!motionEnabled || !startedStreaming) {
            revealedLength = message.content.length
            return@LaunchedEffect
        }
        while (revealedLength < message.content.length) {
            delay(24)
            revealedLength = (revealedLength + 6).coerceAtMost(message.content.length)
            if (revealedLength >= message.content.length - 3) { revealedLength = message.content.length; break }
        }
        if (message.status != MessageStatus.STREAMING.name) {
            revealedLength = message.content.length
        }
    }
    val visibleContent = if (!isUser && startedStreaming && motionEnabled) {
        message.content.take(revealedLength.coerceIn(0, message.content.length))
    } else {
        message.content
    }
    val messageContent: @Composable () -> Unit = {
        if (visibleContent.isBlank() && message.status == MessageStatus.STREAMING.name) {
            ThinkingIndicator()
        } else SelectionContainer {
            MarkdownText(
                markdown = visibleContent,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (!isUser) {
            Row(Modifier.padding(start = 2.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (message.mode == AssistantMode.AGENT.name) Icons.Outlined.AutoAwesome else Icons.Outlined.SmartToy,
                            null,
                            Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    if (message.mode == AssistantMode.AGENT.name) "  Kryzz · Agent" else "  Kryzz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isUser) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) { messageContent() }
            }
        } else {
            Box(Modifier.widthIn(max = 780.dp).fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)) {
                messageContent()
            }
        }

        outputs.forEach { output -> GeneratedOutputCard(output, onSaveOutput, onOpenOutput) }
        if (citations.isNotEmpty()) {
            Row(
                Modifier.padding(top = 14.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Outlined.Public, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Sources", style = MaterialTheme.typography.labelLarge)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(citations.size) { index -> CitationCard(index + 1, citations[index]) }
            }
        }
        if (attachments.isNotEmpty()) {
            Column(
                Modifier.widthIn(max = 420.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { attachment ->
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                        shadowElevation = 1.dp
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.InsertDriveFile, null, Modifier.size(18.dp))
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                Text("${attachment.mimeType} · ${attachment.sizeBytes.fileSizeLabel()}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        if (message.status == MessageStatus.CANCELLED.name) {
            Text("Stopped", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Kryzz AI message", message.content)) },
                modifier = Modifier.size(48.dp)
            ) { Icon(Icons.Outlined.ContentCopy, "Copy", Modifier.size(18.dp)) }
            if (message.content.isNotBlank()) {
                IconButton(
                    onClick = { shareMessage(context, message.content) },
                    modifier = Modifier.size(48.dp)
                ) { Icon(Icons.Outlined.Share, "Share message", Modifier.size(18.dp)) }
            }
            if (isUser) IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Edit, "Edit and resend", Modifier.size(18.dp)) }
            if (!isUser && message.content.isNotBlank()) IconButton(onClick = onSpeak, modifier = Modifier.size(48.dp)) {
                when {
                    synthesizing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    speaking -> Icon(Icons.Outlined.Stop, "Stop reading aloud", Modifier.size(18.dp))
                    else -> Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Read aloud", Modifier.size(18.dp))
                }
            }
            if (canRegenerate) IconButton(onClick = onRegenerate, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Refresh, "Regenerate", Modifier.size(18.dp)) }
            if (!isUser) IconButton(onClick = onShowUsage, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Info, "Conversation usage", Modifier.size(18.dp)) }
            if (canCreateSkill || skillCreated) TextButton(
                onClick = onCreateSkill,
                enabled = !skillCreated,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Outlined.Extension, null, Modifier.size(17.dp))
                Text(if (skillCreated) " Skill saved" else " Make skill", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (message.totalTokens != null || message.cost != null) {
            Text(
                listOfNotNull(message.totalTokens?.let { "$it tokens" }, message.cost?.let { "≈ $${"%.6f".format(it)}" }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun ThinkingIndicator() {
    val alpha = if (LocalKryzzMotionEnabled.current) {
        val transition = rememberInfiniteTransition(label = "thinking")
        val animated by transition.animateFloat(
            initialValue = 0.38f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_050), RepeatMode.Reverse),
            label = "thinking pulse"
        )
        animated
    } else {
        1f
    }
    Row(
        Modifier.padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).graphicsLayer { this.alpha = alpha }.background(MaterialTheme.colorScheme.primary, CircleShape))
        Text("Thinking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GeneratedOutputCard(output: GeneratedOutput, onSave: (GeneratedOutput) -> Unit, onOpen: (GeneratedOutput) -> Unit) {
    val icon = when (output.kind) {
        OutputKind.DOCUMENT -> Icons.Outlined.Description
        OutputKind.SPREADSHEET -> Icons.Outlined.TableChart
        OutputKind.DATABASE -> Icons.Outlined.Storage
        OutputKind.CODE -> Icons.Outlined.Code
        OutputKind.IMAGE -> Icons.Outlined.Photo
        OutputKind.VIDEO -> Icons.Outlined.Movie
        OutputKind.AUDIO -> Icons.Outlined.GraphicEq
    }
    Card(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (output.kind == OutputKind.IMAGE && output.localPath != null) GeneratedImagePreview(output.localPath)
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(icon, null, Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(output.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${output.kind.name.lowercase().replaceFirstChar(Char::uppercase)} · ${output.fileName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (output.localPath != null || output.remoteUrl != null) IconButton(onClick = { onOpen(output) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open output") }
            IconButton(onClick = { onSave(output) }) { Icon(Icons.Outlined.Download, "Save output") }
        }
        output.content?.lineSequence()?.take(3)?.joinToString("\n")?.takeIf(String::isNotBlank)?.let {
            Text(it, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontFamily = if (output.kind == OutputKind.DOCUMENT) FontFamily.SansSerif else FontFamily.Monospace)
        }
    }
}

@Composable
private fun GeneratedImagePreview(path: String) {
    var bitmap by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(path) { bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() } }
    bitmap?.let {
        Image(it, "Generated image", Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun CitationCard(index: Int, citation: Citation) {
    val context = LocalContext.current
    val host = runCatching { Uri.parse(citation.url).host.orEmpty() }.getOrDefault("")
    Card(
        Modifier.width(276.dp).clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(citation.url))) } },
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(index.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(citation.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(host, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            citation.publishDate?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            if (citation.excerpt.isNotBlank()) {
                Text(citation.excerpt, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Code, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    language.ifBlank { "Code" }.uppercase(),
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f))
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp)) {
                Text(content, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
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
            else -> { pushStyle(SpanStyle(fontFamily = FontFamily.Monospace)); append(match.groupValues[4]); pop() }
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

private fun shareMessage(context: Context, content: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Kryzz AI")
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(share, "Share Kryzz response"))
}

private fun activeModelPurpose(mode: AssistantMode, capability: AgentCapability): ModelPurpose = when {
    mode == AssistantMode.CHAT -> ModelPurpose.CHAT
    capability == AgentCapability.IMAGE -> ModelPurpose.IMAGE
    capability == AgentCapability.VIDEO -> ModelPurpose.VIDEO
    capability == AgentCapability.AUDIO -> ModelPurpose.AUDIO
    capability in setOf(AgentCapability.DEEP_RESEARCH, AgentCapability.WIDE_SEARCH) -> ModelPurpose.RESEARCH
    else -> ModelPurpose.AGENT
}

private fun SettingsState.modelFor(purpose: ModelPurpose): String = when (purpose) {
    ModelPurpose.CHAT -> defaultModel
    ModelPurpose.AGENT -> agentModel
    ModelPurpose.RESEARCH -> researchModel
    ModelPurpose.IMAGE -> imageModel
    ModelPurpose.VIDEO -> videoModel
    ModelPurpose.AUDIO -> audioModel
}

private fun String.compactModel(): String = substringAfter('/').ifBlank { "Select model" }

private fun Long.fileSizeLabel(): String = when {
    this >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", this / 1_048_576.0)
    this >= 1_024 -> String.format(java.util.Locale.US, "%.1f KB", this / 1_024.0)
    else -> "$this B"
}

private enum class ComposerAction {
    STOP, VOICE, SEND;

    companion object {
        fun from(generating: Boolean, voice: Boolean) = when {
            generating -> STOP
            voice -> VOICE
            else -> SEND
        }
    }
}

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
                    shadowElevation = 24.dp
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
                                Icon(
                                    Icons.Outlined.AutoAwesome,
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
                text = voicePhaseTitle(phase),
                style = MaterialTheme.typography.headlineSmall,
                color = baseColors.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = voicePhaseHint(phase, muted),
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
    VoicePhase.PROCESSING -> "Transcribing and preparing your spoken reply"
    VoicePhase.SPEAKING -> if (muted) "Microphone muted · tap the mic to unmute" else "Start speaking to interrupt · or tap the orb"
    VoicePhase.IDLE -> ""
}

internal fun voiceOverlayDescription(phase: VoicePhase): String = when (phase) {
    VoicePhase.LISTENING -> "Voice chat listening. Recording sends automatically after you finish. Tap to send now."
    VoicePhase.PROCESSING -> "Voice chat processing. Tap to cancel."
    VoicePhase.SPEAKING -> "Voice chat speaking. Tap to interrupt."
    VoicePhase.IDLE -> "Voice chat"
}

@Composable
private fun DictationMic(level: Float, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val pulse = rememberInfiniteTransition(label = "dictation pulse")
    val idleScale by pulse.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "idle scale"
    )
    val ringScale = if (active) (1f + level.coerceIn(0f, 1f) * 1.1f).coerceAtLeast(1.15f) else idleScale
    val ringAlpha = if (active) (0.18f + level.coerceIn(0f, 1f) * 0.55f) else 0.16f
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = if (active) "Stop dictation" else "Dictation" }) {
        Box(contentAlignment = Alignment.Center) {
            if (motionEnabled) {
                Surface(
                    modifier = Modifier.size(34.dp).graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha },
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                ) {}
            }
            Icon(
                if (active) Icons.Outlined.Mic else Icons.Outlined.MicNone,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            )
        }
    }
}
