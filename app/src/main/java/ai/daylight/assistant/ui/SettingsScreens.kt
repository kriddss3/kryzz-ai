package ai.daylight.assistant.ui

import androidx.biometric.BiometricManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.AssistantPreset
import ai.daylight.assistant.domain.ChatDensity
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotConfirmDialog
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotNotice
import ai.daylight.assistant.ui.dot.DotPill
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotTag
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.home.HomeBoard
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.OptionRow
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.tune.DotSegments
import ai.daylight.assistant.ui.tune.SegmentOption
import ai.daylight.assistant.ui.tune.ThemeSegments
import ai.daylight.assistant.ui.tune.ThinkingModeTiles
import ai.daylight.assistant.ui.tune.TuneActions
import ai.daylight.assistant.ui.tune.TuneChoiceRow
import ai.daylight.assistant.ui.tune.TuneDetail
import ai.daylight.assistant.ui.tune.TuneModel
import ai.daylight.assistant.ui.tune.TunePage
import ai.daylight.assistant.ui.tune.TuneParagraph
import ai.daylight.assistant.ui.tune.TuneRow
import ai.daylight.assistant.ui.tune.TuneScreen
import ai.daylight.assistant.ui.tune.TuneSection
import ai.daylight.assistant.ui.tune.TuneSlider
import ai.daylight.assistant.ui.tune.TuneState
import ai.daylight.assistant.ui.tune.TuneSubpageModel
import ai.daylight.assistant.ui.tune.TuneTrailing
import ai.daylight.assistant.voice.FishEngines
import ai.daylight.assistant.voice.FishVoices
import ai.daylight.assistant.voice.VoiceConfig
import ai.daylight.assistant.voice.VoiceReplyModels
import ai.daylight.assistant.voice.VoiceSttModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The Tune arm (kit settings.png), drawn by [TuneScreen]. It is stateless: [TuneRoute] passes
 * the [state] and the [actions]. [onChats] opens the chat library from the TUNE title,
 * [onLlms] is the model catalog and [onOpen] navigates to a settings route.
 */
@Composable
fun SettingsHomeScreen(
    onChats: () -> Unit,
    onLlms: () -> Unit,
    onSkills: () -> Unit,
    onOpen: (String) -> Unit,
    state: TuneState = TuneState(),
    actions: TuneActions = TuneActions()
) {
    TuneScreen(
        state = state,
        actions = actions,
        onOpen = onOpen,
        onModelCatalog = onLlms,
        onSkills = onSkills,
        onLibrary = onChats
    )
}

/**
 * Binds Tune to [SettingsViewModel]: every control saves at once, location asks for its
 * permission first, and Backup runs the same file pickers as the chat library.
 */
@Composable
fun TuneRoute(
    vm: SettingsViewModel,
    memoryCount: Int,
    onChats: () -> Unit,
    onLlms: () -> Unit,
    onSkills: () -> Unit,
    onOpen: (String) -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val keys by vm.keyTails.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    // Re-read the key endings each time Tune is shown (a key may have been saved in API keys).
    LaunchedEffect(Unit) { vm.refreshKeyTails() }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(8_000)
            vm.notice.value = null
        }
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setLocationEnabled(granted) }
    var pendingExport by remember { mutableStateOf("") }
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val stream = requireNotNull(context.contentResolver.openOutputStream(uri)) {
                            "The selected file could not be opened."
                        }
                        stream.bufferedWriter().use { it.write(pendingExport) }
                    }
                }.onSuccess {
                    vm.notice.value = "Chat backup saved."
                }.onFailure {
                    vm.notice.value = it.message ?: "The backup could not be saved."
                }
                pendingExport = ""
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val content = runCatching {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "The selected file could not be opened."
                        }.bufferedReader().use { it.readText() }
                    }
                }.onFailure {
                    vm.notice.value = it.message ?: "The backup could not be opened."
                }.getOrNull()
                if (content != null) vm.importChats(content)
            }
        }
    }
    val actions = remember(vm) {
        TuneActions(
            onQuality = vm::setAgentQuality,
            onCostCap = vm::setCostCapCents,
            onSearch = vm::setSearch,
            onMemory = vm::setMemoryEnabled,
            onLocation = { wanted ->
                if (wanted && !vm.hasLocationPermission()) {
                    locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                } else {
                    vm.setLocationEnabled(wanted)
                }
            },
            onBiometric = vm::setBiometric,
            onTheme = vm::setTheme,
            onDotBackground = vm::setDotBackground,
            onExportChats = {
                vm.exportChats { json ->
                    pendingExport = json
                    createBackup.launch("kryzz-ai-chat-backup.json")
                }
            },
            onImportChats = { openBackup.launch(arrayOf("application/json")) }
        )
    }
    SettingsHomeScreen(
        onChats = onChats,
        onLlms = onLlms,
        onSkills = onSkills,
        onOpen = onOpen,
        state = TuneState(
            settings = settings,
            keys = keys,
            memoryCount = memoryCount,
            biometricAvailable = biometricAvailable,
            backupNotice = notice
        ),
        actions = actions
    )
}

/** Text and motion: theme, chat density, text size, animations and the dot background. */
@Composable
fun AppearanceSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stops = TuneSubpageModel.FONT_SCALE_STOPS
    var scaleIndex by remember(settings.fontScale) { mutableIntStateOf(TuneSubpageModel.nearest(stops, settings.fontScale)) }

    TunePage("Appearance", onBack) {
        TuneSection("Theme")
        ThemeSegments(settings.themeMode, vm::setTheme)
        Spacer(Modifier.height(28.dp))

        TuneSection("Chat density")
        DotSegments(
            options = ChatDensity.entries.map {
                SegmentOption(it.displayName(), tag = "chat_density_${it.name.lowercase(Locale.US)}")
            },
            selected = ChatDensity.entries.indexOf(settings.chatDensity).coerceAtLeast(0),
            onSelect = { vm.setChatDensity(ChatDensity.entries[it]) }
        )
        TuneDetail("Spacing between messages and around the reply line. Text size stays the same.", Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(28.dp))

        val scale = stops[scaleIndex]
        TuneSlider(
            title = "Text size",
            value = TuneSubpageModel.percent(scale),
            spoken = "${TuneSubpageModel.percent(scale)} text size",
            index = scaleIndex,
            count = stops.size,
            onIndexChange = { scaleIndex = it },
            onFinished = { vm.setFontScale(stops[scaleIndex]) },
            caption = "Scales text across Kryzz AI and keeps spacing phone-safe."
        )
        Spacer(Modifier.height(28.dp))

        TuneSection("Motion and background")
        TuneRow(
            R.drawable.ic_kz_bolt, "Animations",
            if (settings.animationsEnabled) "Short transitions and LED motion" else "Everything stays still",
            TuneTrailing.Switch(settings.animationsEnabled, vm::setAnimationsEnabled),
            modifier = Modifier.testTag("animations_toggle")
        )
        TuneRow(
            R.drawable.ic_kz_grid, "Dot background", "Talk, Run and Home",
            TuneTrailing.Switch(settings.dotBackground, vm::setDotBackground)
        )
    }
}

/** The four keys Kryzz can hold. Each is tested with one small call before it is saved. */
private enum class KeyProvider(val id: String, val label: String, val detail: String) {
    OPENROUTER("openrouter", "OpenRouter", "Chat, image, video, audio and speech models."),
    MINIMAX("minimax", "MiniMax", "Chat, image, video and audio from MiniMax."),
    PARALLEL("parallel", "Parallel Search", "Web search for Run and research."),
    FISH("fish", "Fish Audio", "The voice of spoken replies."),
}

/**
 * Keys (kit §8 KeyEditor): the chat provider, then one row per key. A stored key shows as
 * dots and its last four characters with an ENCRYPTED tag; the key itself never leaves the
 * Keystore. Opening a row shows the field for a new key with an eye toggle.
 */
@Composable
fun ApiSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tails by vm.keyTails.collectAsStateWithLifecycle()
    val open by vm.openRouterKey.collectAsStateWithLifecycle()
    val parallel by vm.parallelKey.collectAsStateWithLifecycle()
    val minimax by vm.minimaxKey.collectAsStateWithLifecycle()
    val fish by vm.fishKey.collectAsStateWithLifecycle()
    val openState by vm.openRouterCheck.collectAsStateWithLifecycle()
    val parallelState by vm.parallelCheck.collectAsStateWithLifecycle()
    val minimaxState by vm.minimaxCheck.collectAsStateWithLifecycle()
    val fishState by vm.fishCheck.collectAsStateWithLifecycle()
    // Re-read the key endings on entry and after every check (a saved key changes them).
    LaunchedEffect(openState, parallelState, minimaxState, fishState) { vm.refreshKeyTails() }

    var expanded by rememberSaveable { mutableStateOf(KeyProvider.OPENROUTER.id) }
    LaunchedEffect(settings.chatProvider) {
        if (expanded == KeyProvider.OPENROUTER.id || expanded == KeyProvider.MINIMAX.id) {
            expanded = settings.chatProvider.name.lowercase(Locale.US)
        }
    }

    TunePage("Keys", onBack) {
        DotNotice(
            "Chat requests go to your chosen provider (OpenRouter or MiniMax) and the model's own provider. Web search goes separately to Parallel. Voice chat always uses OpenRouter. Check each provider's terms.",
            Modifier.padding(bottom = 24.dp),
            icon = R.drawable.ic_kz_shield
        )
        TuneSection("Chat provider")
        DotSegments(
            options = ChatProvider.entries.map { SegmentOption(it.label) },
            selected = ChatProvider.entries.indexOf(settings.chatProvider).coerceAtLeast(0),
            onSelect = { vm.setChatProvider(ChatProvider.entries[it]) }
        )
        TuneDetail(
            "Who answers in Talk and Run. Switching refreshes the model catalog and resets models the new provider lacks.",
            Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(28.dp))

        TuneSection("Keys")
        KeyProvider.entries.forEach { provider ->
            val stored = when (provider) {
                KeyProvider.OPENROUTER -> vm.hasOpenRouter()
                KeyProvider.MINIMAX -> vm.hasMinimax()
                KeyProvider.PARALLEL -> vm.hasParallel()
                KeyProvider.FISH -> vm.hasFish()
            }
            val tail = when (provider) {
                KeyProvider.OPENROUTER -> tails.openRouterTail
                KeyProvider.MINIMAX -> tails.minimaxTail
                KeyProvider.PARALLEL -> tails.parallelTail
                KeyProvider.FISH -> tails.fishTail
            }
            KeyEditor(
                provider = provider,
                stored = stored,
                tail = tail,
                expanded = expanded == provider.id,
                onToggle = { expanded = if (expanded == provider.id) "" else provider.id },
                value = when (provider) {
                    KeyProvider.OPENROUTER -> open
                    KeyProvider.MINIMAX -> minimax
                    KeyProvider.PARALLEL -> parallel
                    KeyProvider.FISH -> fish
                },
                onValue = {
                    when (provider) {
                        KeyProvider.OPENROUTER -> vm.openRouterKey.value = it
                        KeyProvider.MINIMAX -> vm.minimaxKey.value = it
                        KeyProvider.PARALLEL -> vm.parallelKey.value = it
                        KeyProvider.FISH -> vm.fishKey.value = it
                    }
                },
                check = when (provider) {
                    KeyProvider.OPENROUTER -> openState
                    KeyProvider.MINIMAX -> minimaxState
                    KeyProvider.PARALLEL -> parallelState
                    KeyProvider.FISH -> fishState
                },
                onTest = {
                    when (provider) {
                        KeyProvider.OPENROUTER -> vm.saveAndTestOpenRouter()
                        KeyProvider.MINIMAX -> vm.saveAndTestMiniMax()
                        KeyProvider.PARALLEL -> vm.saveAndTestParallel()
                        KeyProvider.FISH -> vm.saveAndTestFish()
                    }
                }
            )
        }
        Spacer(Modifier.height(24.dp))
        DotNotice(
            "Testing a key makes one small call to that provider. Keys are encrypted with Android Keystore, left out of backups, and never sent to another provider.",
            icon = R.drawable.ic_kz_lock
        )
    }
}

/** Short spoken expressions used to audition a voice beyond a plain "hello" preview. */
private val voiceExpressions = listOf("Mmm", "Oh", "Hmm", "Ah", "Wow", "Hmm, okay")

@Composable
private fun KeyEditor(
    provider: KeyProvider,
    stored: Boolean,
    tail: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    value: String,
    onValue: (String) -> Unit,
    check: CheckState,
    onTest: () -> Unit
) {
    val c = DotTheme.c
    TuneRow(
        R.drawable.ic_kz_key,
        provider.label,
        TuneSubpageModel.keyRowCaption(stored, tail),
        TuneTrailing.Expand(expanded),
        onClick = onToggle
    )
    if (!expanded) return
    var reveal by rememberSaveable(provider) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 34.dp, bottom = 20.dp)) {
        Text(provider.detail, style = DotType.BodySmall, color = c.textTertiary)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                TuneSubpageModel.maskedKey(stored, tail) ?: "No key stored",
                Modifier.weight(1f),
                style = DotType.Data.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = if (stored) c.textPrimary else c.textTertiary
            )
            if (stored) DotTag("Encrypted", icon = R.drawable.ic_kz_lock)
        }
        DotField(
            value = value,
            onValueChange = onValue,
            label = if (stored) "Replace key" else "New key",
            placeholder = "Paste your ${provider.label} key",
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank() && check !is CheckState.Checking) onTest() }),
            trailing = {
                DotIconButton(
                    if (reveal) R.drawable.ic_kz_eye_off else R.drawable.ic_kz_eye,
                    if (reveal) "Hide key" else "Show key",
                    { reveal = !reveal },
                    tint = c.textSecondary,
                    iconSize = 20.dp
                )
            },
            fieldModifier = Modifier.testTag("api_key_${provider.id}")
        )
        CheckLine(check)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            DotPillButton(
                "Test and save",
                onTest,
                enabled = value.isNotBlank(),
                busy = check is CheckState.Checking
            )
        }
    }
}

/** The result of a key or voice check: white when it worked, red when it failed. */
@Composable
private fun CheckLine(check: CheckState, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val text = when (check) {
        CheckState.Idle -> ""
        CheckState.Checking -> "Checking…"
        is CheckState.Success -> check.message
        is CheckState.Error -> check.message
    }
    if (text.isEmpty()) return
    val colour = when (check) {
        is CheckState.Error -> c.error
        is CheckState.Success -> c.success
        else -> c.textTertiary
    }
    Row(modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        when (check) {
            is CheckState.Error -> R.drawable.ic_kz_warning
            is CheckState.Success -> R.drawable.ic_kz_check
            else -> null
        }?.let {
            Icon(painterResource(it), null, Modifier.padding(top = 1.dp, end = 8.dp).size(16.dp), tint = colour)
        }
        Text(text, style = DotType.BodySmall, color = colour)
    }
}

/**
 * Voice: how voice chat works, the Fish voice and pace with a preview, the engine, emotion
 * tags and streaming, the transcriber, and the model that writes spoken replies.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val c = DotTheme.c
    val settings by vm.settings.collectAsStateWithLifecycle()
    val voiceTest by vm.voiceTest.collectAsStateWithLifecycle()
    var voiceId by remember(settings.fishVoiceId) { mutableStateOf(settings.fishVoiceId) }
    val speeds = TuneSubpageModel.SPEECH_SPEED_STOPS
    var speedIndex by remember(settings.fishSpeed) { mutableIntStateOf(TuneSubpageModel.nearest(speeds, settings.fishSpeed)) }
    var showVoices by rememberSaveable { mutableStateOf(false) }
    var showEngines by rememberSaveable { mutableStateOf(false) }
    var showCustomVoice by rememberSaveable { mutableStateOf(false) }
    var customVoiceName by rememberSaveable { mutableStateOf("") }
    val selectedPreset = FishVoices.presets.firstOrNull { it.referenceId == settings.fishVoiceId }
    val selectedSaved = settings.fishCustomVoices.firstOrNull { it.referenceId == settings.fishVoiceId }
    val customSelected = selectedPreset == null && settings.fishVoiceId.isNotBlank()
    val voiceName = selectedPreset?.name ?: selectedSaved?.name
        ?: if (customSelected) "Custom voice" else "Choose a voice"
    val engine = FishEngines.options.firstOrNull { it.id == settings.fishModel } ?: FishEngines.options.first()

    TunePage("Voice", onBack) {
        DotNotice(
            "In a chat with nothing typed, the send button becomes a microphone. Tap it, speak, and Kryzz answers out loud; your words and the reply still appear in the chat. The transcriber hears you, the reply model answers, and Fish Audio speaks."
        )
        Spacer(Modifier.height(28.dp))

        TuneSection("Voice")
        TuneRow(
            R.drawable.ic_kz_user, "Voice", voiceName,
            onClick = { showVoices = true },
            modifier = Modifier.testTag("voice_picker")
        )
        if (showCustomVoice || customSelected) {
            Spacer(Modifier.height(12.dp))
            DotField(
                value = voiceId,
                onValueChange = {
                    voiceId = it
                    vm.setFishVoiceId(it)
                },
                label = "Voice ID",
                placeholder = "Paste a voice ID from Fish Audio",
                supporting = "Any voice model ID from the Fish Audio library."
            )
            Spacer(Modifier.height(14.dp))
            DotField(
                value = customVoiceName,
                onValueChange = { customVoiceName = it },
                label = "Voice name",
                placeholder = "My voice",
                supporting = "Save it under a name to keep it in the voice list."
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                DotPillButton(
                    "Save voice",
                    {
                        vm.saveFishCustomVoice(customVoiceName, voiceId)
                        customVoiceName = ""
                    },
                    style = DotButtonStyle.Ghost,
                    enabled = customVoiceName.isNotBlank() && voiceId.isNotBlank()
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        val speed = speeds[speedIndex]
        TuneSlider(
            title = "Speaking speed",
            value = TuneSubpageModel.speed(speed),
            spoken = TuneSubpageModel.speedSpoken(speed),
            index = speedIndex,
            count = speeds.size,
            onIndexChange = { speedIndex = it },
            onFinished = { vm.setFishSpeed(speeds[speedIndex]) },
            caption = "1.0X is the voice's natural pace."
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            DotPillButton(
                "Preview voice",
                vm::testVoice,
                style = DotButtonStyle.White,
                busy = voiceTest is CheckState.Checking,
                icon = R.drawable.ic_kz_play
            )
        }
        CheckLine(voiceTest)
        Spacer(Modifier.height(20.dp))
        TuneSection("Try expressions")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            voiceExpressions.forEach { line ->
                DotPill(
                    line,
                    selected = false,
                    onClick = { vm.playExpression(line) },
                    role = Role.Button,
                    modifier = Modifier.testTag("voice_expression_${line}")
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        TuneSection("Speech")
        TuneRow(
            R.drawable.ic_kz_voice_wave, "Fish Audio engine", "${engine.name}, ${engine.detail}",
            onClick = { showEngines = true },
            modifier = Modifier.testTag("fish_model_picker")
        )
        TuneRow(
            R.drawable.ic_kz_quote, "Emotions in replies",
            detail = "Tags like [happy] or [calm] shape spoken answers. The voice model tags a sentence when a feeling is clear.",
            trailing = TuneTrailing.Switch(settings.voiceEmotions, vm::setVoiceEmotions),
            modifier = Modifier.testTag("voice_emotions_toggle")
        )
        TuneRow(
            R.drawable.ic_kz_bolt, "Stream Fish replies",
            detail = "On: play the reply as Fish makes it. Off: wait for the whole MP3, which is more reliable.",
            trailing = TuneTrailing.Switch(settings.voiceFishStreaming, vm::setVoiceFishStreaming),
            modifier = Modifier.testTag("voice_fish_streaming_toggle")
        )
        Spacer(Modifier.height(28.dp))

        TuneSection("Transcriber")
        TuneDetail("Which model hears you. All run through OpenRouter.", Modifier.padding(bottom = 10.dp))
        VoiceSttModels.options.forEach { choice ->
            TuneChoiceRow(
                title = choice.name,
                detail = choice.detail,
                selected = settings.voiceSttModel == choice.id,
                onClick = { vm.setVoiceSttModel(choice.id) },
                modifier = Modifier.testTag(
                    when (choice.id) {
                        VoiceConfig.GROK_STT_MODEL -> "stt_grok"
                        VoiceConfig.NEMOTRON_STT_MODEL -> "stt_nemotron"
                        else -> "stt_whisper"
                    }
                )
            )
        }
        Spacer(Modifier.height(28.dp))

        TuneSection("Reply model")
        TuneDetail(
            "Which model writes the spoken answer. OpenRouter routes it to the fastest provider.",
            Modifier.padding(bottom = 10.dp)
        )
        VoiceReplyModels.fast.forEach { choice ->
            TuneChoiceRow(
                title = choice.name,
                detail = choice.detail,
                selected = settings.voiceReplyModel == choice.id,
                onClick = { vm.setVoiceReplyModel(choice.id) }
            )
        }
        TuneChoiceRow(
            title = "My everyday chat model",
            detail = "Uses your chat model choice",
            selected = settings.voiceReplyModel.isBlank(),
            onClick = { vm.setVoiceReplyModel("") }
        )
        Spacer(Modifier.height(28.dp))
        DotNotice(
            "Your voice is recorded only while voice chat is open, sent to OpenRouter for the transcriber you picked, then deleted from this phone. Fish Audio makes the reply audio, which is deleted after playback. No recordings are kept in chat history.",
            icon = R.drawable.ic_kz_shield
        )
    }

    if (showVoices) {
        DotSheet(onDismiss = { showVoices = false }) {
            SheetLabel("Voice")
            FishVoices.presets.forEach { voice ->
                TuneChoiceRow(voice.name, null, voice.referenceId == settings.fishVoiceId, {
                    vm.setFishVoiceId(voice.referenceId)
                    showVoices = false
                })
            }
            settings.fishCustomVoices.forEach { voice ->
                TuneChoiceRow(
                    voice.name, "Saved voice", voice.referenceId == settings.fishVoiceId,
                    onClick = {
                        vm.setFishVoiceId(voice.referenceId)
                        showVoices = false
                    },
                    trailing = {
                        DotIconButton(
                            R.drawable.ic_kz_trash, "Delete saved voice ${voice.name}",
                            { vm.deleteFishCustomVoice(voice.referenceId) },
                            tint = c.textTertiary, iconSize = 18.dp
                        )
                    }
                )
            }
            OptionRow(R.drawable.ic_kz_edit, "Custom voice ID", onClick = {
                showCustomVoice = true
                showVoices = false
            })
        }
    }
    if (showEngines) {
        DotSheet(onDismiss = { showEngines = false }) {
            SheetLabel("Fish Audio engine")
            FishEngines.options.forEach { option ->
                TuneChoiceRow(option.name, option.detail, option.id == settings.fishModel, {
                    vm.setFishModel(option.id)
                    showEngines = false
                })
            }
        }
    }
}

/** Persona: the preset that shapes Kryzz's system instruction, or your own words. */
@Composable
fun PersonaSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(settings.presetId) }
    var custom by remember { mutableStateOf(settings.customPrompt) }

    LaunchedEffect(settings.presetId, settings.customPrompt) {
        selected = settings.presetId
        custom = settings.customPrompt
    }
    val changed = selected != settings.presetId || (selected == "custom" && custom != settings.customPrompt)

    TunePage("Persona", onBack) {
        TuneSection("How Kryzz answers")
        TuneDetail(
            "A preset changes only the system instruction. It never shows your keys or skips a confirmation for outside actions.",
            Modifier.padding(bottom = 10.dp)
        )
        AssistantPreset.all.forEach { preset ->
            TuneChoiceRow(
                title = preset.title,
                detail = presetDescription(preset.id),
                selected = selected == preset.id,
                onClick = { selected = preset.id }
            )
        }
        if (selected == "custom") {
            Spacer(Modifier.height(16.dp))
            DotField(
                value = custom,
                onValueChange = { custom = it },
                label = "Custom instructions",
                placeholder = "Tone, priorities, how long answers should be",
                singleLine = false,
                minLines = 5
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
            DotPillButton(
                if (changed) "Save persona" else "Saved",
                { vm.setPersona(selected, custom) },
                enabled = changed && (selected != "custom" || custom.isNotBlank())
            )
        }
    }
}

private fun presetDescription(id: String): String? = when (id) {
    "balanced" -> "Warm, practical answers with enough context to act confidently."
    "concise" -> "Short, direct answers focused on the detail you need right now."
    "thoughtful" -> "Careful reasoning that explores ambiguity, alternatives, and tradeoffs."
    "proactive" -> "Anticipates useful next steps and flags likely pitfalls early."
    "uwu" -> "A cute, playful anime style for casual fun; serious topics stay clear and sober."
    "custom" -> "Write your own instructions for tone, priorities, and response style."
    else -> null
}

/**
 * Search and run limits: web search and how much it reads, the thinking mode, the step
 * budget, the per-run cost cap (the same 24 dots as Tune) and the answer review.
 */
@Composable
fun ToolSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val c = DotTheme.c
    val settings by vm.settings.collectAsStateWithLifecycle()
    val charStops = TuneSubpageModel.SEARCH_CHAR_STOPS
    val budgetStops = TuneSubpageModel.STEP_BUDGET_STOPS
    var charsIndex by remember(settings.maxSearchChars) {
        mutableIntStateOf(TuneSubpageModel.nearestInt(charStops, settings.maxSearchChars))
    }
    var budgetIndex by remember(settings.stepBudget) {
        mutableIntStateOf(TuneSubpageModel.nearestInt(budgetStops, settings.stepBudget))
    }
    var capIndex by remember(settings.costCapCents) { mutableIntStateOf(TuneModel.capIndex(settings.costCapCents)) }

    TunePage("Tools", onBack) {
        TuneSection("Web search")
        TuneRow(
            R.drawable.ic_kz_web, "Parallel web search",
            detail = "Lets the model look up current information. Searching never needs a confirmation. " +
                "The same switch lets Find search Wikimedia Commons for free images.",
            trailing = TuneTrailing.Switch(settings.searchEnabled, vm::setSearch)
        )
        Spacer(Modifier.height(16.dp))
        val chars = charStops[charsIndex]
        TuneSlider(
            title = "Search context",
            value = TuneSubpageModel.chars(chars),
            spoken = "${chars / 1_000} thousand characters",
            index = charsIndex,
            count = charStops.size,
            onIndexChange = { charsIndex = it },
            onFinished = { vm.setSearchChars(charStops[charsIndex]) },
            caption = "More context can cover more ground but uses more input tokens.",
            enabled = settings.searchEnabled
        )
        Spacer(Modifier.height(28.dp))

        TuneSection("Thinking mode")
        ThinkingModeTiles(settings.agentQuality, vm::setAgentQuality)
        TuneDetail(
            settings.agentQuality.summary + " Fast and Max adjust the limits below for Run; a reasoning level you picked for a model always wins.",
            Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(28.dp))

        val budget = budgetStops[budgetIndex]
        TuneSlider(
            title = "Step budget",
            value = budget.toString(),
            spoken = "$budget tool rounds",
            index = budgetIndex,
            count = budgetStops.size,
            onIndexChange = { budgetIndex = it },
            onFinished = { vm.setStepBudget(budgetStops[budgetIndex]) },
            caption = "Tool rounds one Run turn may take. When they run out, Kryzz answers from what it found."
        )
        Spacer(Modifier.height(28.dp))

        val capCents = TuneModel.capCents(capIndex)
        TuneSlider(
            title = "Cost cap per run",
            value = TuneModel.capValue(capCents),
            spoken = if (capCents <= 0) "Off" else HomeBoard.capLabel(capCents),
            index = capIndex,
            count = TuneModel.CAP_STOPS.size,
            onIndexChange = { capIndex = it },
            onFinished = { vm.setCostCapCents(TuneModel.capCents(capIndex)) },
            caption = "Stops tool use once a turn has spent this much, then answers. Uses the cost OpenRouter reports; MiniMax counts tokens instead."
        )
        Spacer(Modifier.height(24.dp))
        TuneRow(
            R.drawable.ic_kz_check, "Review answers before sending",
            detail = "After research or file work, Kryzz checks its draft against your request and the sources once, and fixes what it finds.",
            trailing = TuneTrailing.Switch(settings.reviewAnswers, vm::setReviewAnswers)
        )
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
    }
}

/**
 * Data and privacy (kit §8): the LOCAL hero, the lock and location switches, what is stored
 * where, what providers see, and the red Clear all local data row.
 */
@Composable
fun PrivacySettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val c = DotTheme.c
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    var confirmClear by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setLocationEnabled(granted) }

    TunePage("Privacy", onBack) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp)) {
            Icon(painterResource(R.drawable.ic_kz_shield_dot), null, Modifier.size(64.dp), tint = c.textPrimary)
            Spacer(Modifier.height(18.dp))
            DotMatrixText("LOCAL", pitch = 4.2.dp, on = c.textPrimary, off = Color.Transparent, contentDescription = "Local")
            Text(
                "No telemetry. No analytics. Your keys stay on this device.",
                Modifier.padding(top = 12.dp),
                style = DotType.Body,
                color = c.textSecondary
            )
        }

        TuneSection("On this phone")
        TuneRow(
            R.drawable.ic_kz_lock, "Lock the app", TuneModel.lockCaption(biometricAvailable),
            TuneTrailing.Switch(settings.biometricLock, vm::setBiometric, enabled = biometricAvailable || settings.biometricLock)
        )
        TuneRow(
            R.drawable.ic_kz_location, "Approximate location",
            TuneModel.locationCaption(settings.locationEnabled, settings.locationLabel),
            TuneTrailing.Switch(settings.locationEnabled, { wanted ->
                if (wanted && !vm.hasLocationPermission()) {
                    locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                } else {
                    vm.setLocationEnabled(wanted)
                }
            }),
            detail = "Coarse location gives Kryzz your region for local questions. Off by default."
        )
        Spacer(Modifier.height(28.dp))

        TuneSection("Where your data lives")
        TuneParagraph(
            "Conversations, skills, citations, token counts and returned cost live in the app's local database. Generated documents, databases, media and code ZIPs stay in private app storage, and settings in DataStore. API keys are encrypted separately with Android Keystore. Android cloud backup and device transfer are off for all app data."
        )
        TuneParagraph(
            "There is no analytics, advertising ID, telemetry SDK or HTTP logging. Chat exports never include keys."
        )
        Spacer(Modifier.height(12.dp))

        TuneSection("What providers see")
        TuneParagraph(
            "Token counts and an estimated cost appear under answers when OpenRouter returns them. To name a new chat, Kryzz sends up to the first 1,500 characters of its first message through OpenRouter to the low-cost Llama 3.2 1B model; if that fails, a local title is used. Your provider's dashboard is the real billing record."
        )
        Spacer(Modifier.height(12.dp))

        TuneSection("Device data")
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button) { confirmClear = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_kz_trash), null, Modifier.size(20.dp), tint = c.error)
            Spacer(Modifier.width(14.dp))
            Text("Clear all local data", style = DotType.Body.copy(fontSize = 15.sp), color = c.error)
        }
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        notice?.let { TuneDetail(it, Modifier.padding(top = 10.dp)) }
    }

    if (confirmClear) {
        DotConfirmDialog(
            title = "Clear all local data?",
            body = "This permanently deletes conversations, skills, generated files, settings and every stored API key from this phone.",
            confirm = "Clear everything",
            destructive = true,
            onConfirm = {
                confirmClear = false
                vm.clearAll()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

private fun ChatDensity.displayName(): String = name.lowercase(Locale.US).replaceFirstChar(Char::uppercase)
