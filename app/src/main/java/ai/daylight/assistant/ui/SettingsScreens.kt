package ai.daylight.assistant.ui

import androidx.biometric.BiometricManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.BuildConfig
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.AgentTurnPolicy
import ai.daylight.assistant.domain.AssistantPreset
import ai.daylight.assistant.domain.ChatDensity
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.ui.theme.LocalGlassOpacity
import ai.daylight.assistant.voice.FishEngines
import ai.daylight.assistant.voice.FishVoices
import ai.daylight.assistant.voice.VoiceConfig
import ai.daylight.assistant.voice.VoiceReplyModels
import ai.daylight.assistant.voice.VoiceSttModels
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onChats: () -> Unit,
    onLlms: () -> Unit,
    onSkills: () -> Unit,
    onOpen: (String) -> Unit,
    onChat: () -> Unit = onChats,
    onOpenLibrary: () -> Unit = onChats,
    memoryCount: Int = 0
) {
    var advancedExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier,
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenLibrary, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Menu, "Open menu")
                    }
                },
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassOpacity.current),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KryzzMark(Modifier.size(54.dp), contentDescription = null)
                Text("KRYZZ AI", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Private workspace · local history",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsGroup(title = "Appearance", compact = true) {
                SettingsLink(
                    icon = Icons.Outlined.Palette,
                    title = "Theme and display"
                ) { onOpen(Routes.APPEARANCE) }
            }

            SettingsGroup(title = "AI and models", compact = true) {
                SettingsLink(
                    icon = Icons.Outlined.SmartToy,
                    title = "Default models"
                ) { onOpen(Routes.models()) }
                SettingsDivider()
                SettingsLink(
                    icon = Icons.Outlined.MenuBook,
                    title = "Model catalog"
                ) { onOpen(Routes.LLMS) }
                SettingsDivider()
                SettingsLink(
                    icon = Icons.Outlined.Person,
                    title = "Agent & persona"
                ) { onOpen(Routes.PERSONA) }
            }

            SettingsGroup(title = "Voice chat", compact = true) {
                SettingsLink(
                    icon = Icons.Outlined.RecordVoiceOver,
                    title = "Voice chat"
                ) { onOpen(Routes.VOICE) }
            }

            SettingsGroup(title = "Memory", compact = true) {
                // The Chat memory toggle lives only on the dedicated Memory screen.
                SettingsLink(
                    icon = Icons.Outlined.Psychology,
                    title = "Manage memories",
                    onClick = { onOpen(Routes.MEMORY) }
                )
            }

            SettingsGroup(title = "Tools and connections", compact = true) {
                SettingsLink(
                    icon = Icons.Outlined.Extension,
                    title = "Skills & tools"
                ) { onOpen(Routes.SKILLS) }
                SettingsDivider()
                SettingsLink(
                    icon = Icons.AutoMirrored.Outlined.ManageSearch,
                    title = "Search & tools"
                ) { onOpen(Routes.TOOLS) }
            }

            SettingsGroup(title = "Data and privacy", compact = true) {
                SettingsLink(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Data, privacy & usage"
                ) { onOpen(Routes.PRIVACY) }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassOpacity.current),
                tonalElevation = 1.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { advancedExpanded = !advancedExpanded }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Advanced",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (advancedExpanded) "Hide" else "Show",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = if (advancedExpanded) "Collapse Advanced" else "Expand Advanced",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (advancedExpanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsLink(
                            icon = Icons.Outlined.Key,
                            title = "API setup",
                            subtitle = "Provider API keys"
                        ) { onOpen(Routes.API) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.padding(12.dp)) {
                            ProviderNotice()
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} · created and coded by Kryzz",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "© ${java.time.Year.now().value} Kryzz AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var fontScale by remember(settings.fontScale) { mutableStateOf(settings.fontScale) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { BackBar("Appearance", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroup(title = "Theme") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == settings.themeMode,
                            onClick = { vm.setTheme(mode) },
                            label = { Text(mode.displayName()) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            SettingsGroup(
                title = "Background",
                description = "The focused Kryzz visual identity, tuned for legibility in light and dark themes."
            ) {
                SettingsInfoRow(
                    icon = Icons.Outlined.Palette,
                    title = "Obsidian Constellation",
                    subtitle = "Ink-black depth, restrained stars, and quiet constellation lines"
                )
            }

            SettingsGroup(
                title = "Chat density",
                description = "Adjust message spacing and composer padding without changing text size."
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatDensity.entries.forEach { density ->
                        FilterChip(
                            selected = density == settings.chatDensity,
                            onClick = { vm.setChatDensity(density) },
                            label = { Text(density.displayName()) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("chat_density_${density.name.lowercase()}")
                        )
                    }
                }
            }

            SettingsGroup(title = "Motion") {
                SettingSwitch(
                    title = "Animations",
                    subtitle = "Use short transitions and subtle signal motion. Disable for a still interface.",
                    checked = settings.animationsEnabled,
                    onChecked = vm::setAnimationsEnabled,
                    modifier = Modifier.testTag("animations_toggle")
                )
            }

            SettingsGroup(title = "Text size") {
                SliderSetting(
                    title = "Font size · ${(fontScale * 100).roundToInt()}%",
                    subtitle = "Scales text throughout Kryzz AI while preserving phone-safe spacing.",
                    value = fontScale,
                    onValueChange = { fontScale = it },
                    onValueChangeFinished = { vm.setFontScale(fontScale) },
                    valueRange = 0.85f..1.35f,
                    steps = 9
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val open by vm.openRouterKey.collectAsStateWithLifecycle()
    val parallel by vm.parallelKey.collectAsStateWithLifecycle()
    val minimax by vm.minimaxKey.collectAsStateWithLifecycle()
    val openState by vm.openRouterCheck.collectAsStateWithLifecycle()
    val parallelState by vm.parallelCheck.collectAsStateWithLifecycle()
    val minimaxState by vm.minimaxCheck.collectAsStateWithLifecycle()
    val fish by vm.fishKey.collectAsStateWithLifecycle()
    val fishState by vm.fishCheck.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { BackBar("API setup", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProviderNotice()

            SettingsGroup(
                title = "Chat provider",
                description = "Controls which provider supplies chat, agent, research, and media model choices."
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChatProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = settings.chatProvider == provider,
                                onClick = { vm.setChatProvider(provider) },
                                label = { Text(provider.label) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    Text(
                        "Changing provider refreshes the catalog and resets unavailable model selections to valid choices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val apiProviders = remember {
                listOf(
                    ApiProviderEntry("openrouter", "OpenRouter", "LLM, image, video, audio, and TTS routing"),
                    ApiProviderEntry("minimax", "MiniMax", "Image, video, and audio generation"),
                    ApiProviderEntry("parallel", "Parallel Search", "Web search for research agent"),
                    ApiProviderEntry("fish", "Fish Audio", "Voice synthesis for spoken replies")
                )
            }
            var selectedProvider by rememberSaveable { mutableStateOf("openrouter") }
            var providerMenuExpanded by remember { mutableStateOf(false) }
            val selected = apiProviders.first { it.id == selectedProvider }
            LaunchedEffect(settings.chatProvider) {
                if (selectedProvider == "openrouter" || selectedProvider == "minimax") {
                    selectedProvider = settings.chatProvider.name.lowercase()
                }
            }

            SettingsGroup(title = "Provider") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = providerMenuExpanded,
                        onExpandedChange = { providerMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth().testTag("api_provider_picker")
                    ) {
                        OutlinedTextField(
                            value = selected.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select provider") },
                            supportingText = { Text(selected.detail) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false }
                        ) {
                            apiProviders.forEach { provider ->
                                DropdownMenuItem(
                                    text = {
                                        Column(Modifier.padding(end = 8.dp)) {
                                            Text(provider.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                provider.detail,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    leadingIcon = if (provider.id == selectedProvider) {
                                        { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = {
                                        selectedProvider = provider.id
                                        providerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            when (selectedProvider) {
                "openrouter" -> KeyEditor(
                    title = "OpenRouter",
                    state = if (vm.hasOpenRouter()) "A key is stored securely" else "No key stored",
                    value = open,
                    onValue = { vm.openRouterKey.value = it },
                    check = openState,
                    onTest = vm::saveAndTestOpenRouter
                )
                "minimax" -> KeyEditor(
                    title = "MiniMax",
                    state = if (vm.hasMinimax()) "A key is stored securely" else "No key stored",
                    value = minimax,
                    onValue = { vm.minimaxKey.value = it },
                    check = minimaxState,
                    onTest = vm::saveAndTestMiniMax
                )
                "parallel" -> KeyEditor(
                    title = "Parallel Search",
                    state = if (vm.hasParallel()) "A key is stored securely" else "No key stored",
                    value = parallel,
                    onValue = { vm.parallelKey.value = it },
                    check = parallelState,
                    onTest = vm::saveAndTestParallel
                )
                "fish" -> KeyEditor(
                    title = "Fish Audio",
                    state = if (vm.hasFish()) "A key is stored securely" else "No key stored",
                    value = fish,
                    onValue = { vm.fishKey.value = it },
                    check = fishState,
                    onTest = vm::saveAndTestFish
                )
            }
            SettingsGroup(title = "Credential privacy") {
                Text(
                    text = "Testing a provider performs one small API call. Credentials are encrypted on-device, excluded from backup, and never sent to the other provider.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ApiProviderEntry(val id: String, val label: String, val detail: String)

/** Short spoken expressions used to audition a voice beyond a plain "hello" preview. */
private val voiceExpressions = listOf("Mmm", "Oh", "Hmm", "Ah", "Wow", "Hmm, okay")

@Composable
private fun KeyEditor(
    title: String,
    state: String,
    value: String,
    onValue: (String) -> Unit,
    check: CheckState,
    onTest: () -> Unit
) {
    SettingsGroup(title = title, description = state) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text("New API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
            CheckLabel(check)
            Button(
                onClick = onTest,
                enabled = value.isNotBlank() && check !is CheckState.Checking,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
            ) {
                if (check is CheckState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Test & save")
                }
            }
        }
    }
}

@Composable
private fun CheckLabel(check: CheckState) {
    val text = when (check) {
        CheckState.Idle -> ""
        CheckState.Checking -> "Checking…"
        is CheckState.Success -> check.message
        is CheckState.Error -> check.message
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = if (check is CheckState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val voiceTest by vm.voiceTest.collectAsStateWithLifecycle()
    var voiceId by remember(settings.fishVoiceId) { mutableStateOf(settings.fishVoiceId) }
    var speed by remember(settings.fishSpeed) { mutableStateOf(settings.fishSpeed) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { BackBar("Voice chat", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroup(
                title = "How it works",
                description = "In a chat with nothing typed, the send button becomes a microphone. Tap it, speak, and Kryzz answers out loud — your words and the reply still appear in the chat as text."
            ) {
                Text(
                    text = "Pipeline: the transcriber you pick hears you · a fast or normal model replies · Fish Audio renders the spoken reply.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsGroup(
                title = "Voice",
                description = "The voice model and pace used for spoken replies."
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    var voiceMenuExpanded by remember { mutableStateOf(false) }
                    var showCustomVoice by remember { mutableStateOf(false) }
                    var customVoiceName by remember { mutableStateOf("") }
                    val selectedPreset = FishVoices.presets.firstOrNull { it.referenceId == settings.fishVoiceId }
                    val selectedSaved = settings.fishCustomVoices.firstOrNull { it.referenceId == settings.fishVoiceId }
                    val customSelected = selectedPreset == null && settings.fishVoiceId.isNotBlank()
                    ExposedDropdownMenuBox(
                        expanded = voiceMenuExpanded,
                        onExpandedChange = { voiceMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth().testTag("voice_picker")
                    ) {
                        OutlinedTextField(
                            value = selectedPreset?.name ?: selectedSaved?.name
                                ?: if (customSelected) "Custom (${settings.fishVoiceId})" else "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Choose a voice") },
                            label = { Text("Voice") },
                            supportingText = { Text("Spoken replies use the voice you pick here.") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = voiceMenuExpanded,
                            onDismissRequest = { voiceMenuExpanded = false }
                        ) {
                            FishVoices.presets.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = if (voice.referenceId == settings.fishVoiceId) {
                                        { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = {
                                        vm.setFishVoiceId(voice.referenceId)
                                        voiceMenuExpanded = false
                                    }
                                )
                            }
                            if (settings.fishCustomVoices.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                settings.fishCustomVoices.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingIcon = if (voice.referenceId == settings.fishVoiceId) {
                                            { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
                                        } else null,
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { vm.deleteFishCustomVoice(voice.referenceId) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Outlined.DeleteForever, "Delete saved voice", Modifier.size(16.dp))
                                            }
                                        },
                                        onClick = {
                                            vm.setFishVoiceId(voice.referenceId)
                                            voiceMenuExpanded = false
                                        }
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DropdownMenuItem(
                                text = { Text("Custom voice ID…") },
                                onClick = {
                                    showCustomVoice = true
                                    voiceMenuExpanded = false
                                }
                            )
                        }
                    }
                    if (showCustomVoice || customSelected) {
                        OutlinedTextField(
                            value = voiceId,
                            onValueChange = {
                                voiceId = it
                                vm.setFishVoiceId(it)
                            },
                            label = { Text("Voice ID") },
                            placeholder = { Text("Paste here the voice ID from fishaudio") },
                            supportingText = { Text("Paste any voice model ID from the Fish Audio library.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customVoiceName,
                            onValueChange = { customVoiceName = it },
                            label = { Text("Voice name") },
                            supportingText = { Text("Save this voice under a name to keep it in the list above.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                vm.saveFishCustomVoice(customVoiceName, voiceId)
                                customVoiceName = ""
                            },
                            enabled = customVoiceName.isNotBlank() && voiceId.isNotBlank(),
                            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                        ) {
                            Text("Save voice")
                        }
                    }
                    SliderSetting(
                        title = "Speaking speed · ${String.format(Locale.US, "%.1f", speed)}×",
                        subtitle = "Pace of the spoken reply; 1.0 matches the voice's natural speed.",
                        value = speed,
                        onValueChange = { speed = it },
                        onValueChangeFinished = { vm.setFishSpeed(speed) },
                        valueRange = 0.5f..2f,
                        steps = 14
                    )
                    Button(
                        onClick = vm::testVoice,
                        enabled = voiceTest !is CheckState.Checking,
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                    ) {
                        if (voiceTest is CheckState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Preview voice")
                        }
                    }
                    CheckLabel(voiceTest)
                    // Quick expression previews: audition how the selected voice sounds
                    // saying the small interjections that make a voice chat feel human, so
                    // the user doesn't have to wait for a full reply to judge the voice.
                    Text(
                        text = "Try expressions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voiceExpressions.forEach { line ->
                            FilterChip(
                                selected = false,
                                onClick = { vm.playExpression(line) },
                                label = { Text(line) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("voice_expression_${line}")
                            )
                        }
                    }
                }
            }

            SettingsGroup(
                title = "Fish Audio engine",
                description = "The engine version behind the spoken replies; the current free tier is included."
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    var engineMenuExpanded by remember { mutableStateOf(false) }
                    val selectedEngine = FishEngines.options.firstOrNull { it.id == settings.fishModel }
                        ?: FishEngines.options.first()
                    ExposedDropdownMenuBox(
                        expanded = engineMenuExpanded,
                        onExpandedChange = { engineMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth().testTag("fish_model_picker")
                    ) {
                        OutlinedTextField(
                            value = "${selectedEngine.name} · ${selectedEngine.detail}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Engine") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = engineMenuExpanded,
                            onDismissRequest = { engineMenuExpanded = false }
                        ) {
                            FishEngines.options.forEach { engine ->
                                DropdownMenuItem(
                                    text = {
                                        Column(Modifier.padding(end = 8.dp)) {
                                            Text(engine.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                engine.detail,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    leadingIcon = if (engine.id == settings.fishModel) {
                                        { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = {
                                        vm.setFishModel(engine.id)
                                        engineMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            SettingsGroup(
                title = "Emotions",
                description = "Whether spoken replies carry Fish emotion tags. Official tags such as [happy] stay in the spoken text; they are no longer rewritten to [calm]."
            ) {
                SettingSwitch(
                    title = "Emotions in replies",
                    subtitle = "Tagged speech such as [happy] or [calm] is applied to spoken answers; the voice model is told to tag sentences when a feeling is clear.",
                    checked = settings.voiceEmotions,
                    onChecked = vm::setVoiceEmotions,
                    modifier = Modifier.testTag("voice_emotions_toggle")
                )
            }
            SettingsGroup(
                title = "Fish streaming",
                description = "Starts the reply as soon as the first PCM arrives. Turn this off to wait for a complete MP3."
            ) {
                SettingSwitch(
                    title = "Stream Fish replies",
                    subtitle = "On: play PCM as Fish generates it. Off: wait for the full MP3 (more reliable). Applies only to Fish Audio.",
                    checked = settings.voiceFishStreaming,
                    onChecked = vm::setVoiceFishStreaming,
                    modifier = Modifier.testTag("voice_fish_streaming_toggle")
                )
            }
            SettingsGroup(
                title = "Listening sounds",
                description = "While you talk for a while, Kryzz drops a short \"mm-hmm\" or \"yeah\" in your pauses, like a person on a call, and waits a moment longer so you can carry on."
            ) {
                SettingSwitch(
                    title = "Say \"mm-hmm\" while I talk",
                    subtitle = "Rendered once in the reply voice and kept on this phone. Never sent to the transcriber.",
                    checked = settings.voiceBackchannels,
                    onChecked = vm::setVoiceBackchannels,
                    modifier = Modifier.testTag("voice_backchannels_toggle")
                )
            }
            SettingsGroup(
                title = "Speech to text",
                description = "Which model transcribes what you say in voice chat and dictation. Voice chat starts transcribing during the pause at the end of your sentence, which hides most of the transcription time."
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    VoiceSttModels.options.forEachIndexed { index, choice ->
                        SelectionSettingRow(
                            title = choice.name,
                            subtitle = choice.detail,
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
                        if (index != VoiceSttModels.options.lastIndex) SettingsDivider()
                    }
                }
            }
            SettingsGroup(
                title = "Reply model",
                description = "Which model writes the spoken answer. OpenRouter always routes the request to the provider with the lowest latency."
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    VoiceReplyModels.fast.forEach { choice ->
                        SelectionSettingRow(
                            title = choice.name,
                            subtitle = choice.detail,
                            selected = settings.voiceReplyModel == choice.id,
                            onClick = { vm.setVoiceReplyModel(choice.id) }
                        )
                        SettingsDivider()
                    }
                    SelectionSettingRow(
                        title = "My everyday chat model",
                        subtitle = "Uses your Default models → Chat choice",
                        selected = settings.voiceReplyModel.isBlank(),
                        onClick = { vm.setVoiceReplyModel("") }
                    )
                }
            }

            SettingsGroup(title = "Privacy note") {
                Text(
                    text = "Voice messages are recorded only while the bubble is open, sent to OpenRouter (the transcriber you picked), and deleted from this phone afterwards. Reply audio is generated by Fish Audio and deleted after playback. No recordings are stored in chat history.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(settings.presetId) }
    var custom by remember { mutableStateOf(settings.customPrompt) }

    LaunchedEffect(settings.presetId, settings.customPrompt) {
        selected = settings.presetId
        custom = settings.customPrompt
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { BackBar("Agent & persona", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroup(
                title = "Assistant behavior",
                description = "Presets change only the system instruction. They never expose credentials or bypass confirmation for external actions."
            ) {
                AssistantPreset.all.forEachIndexed { index, preset ->
                    SelectionSettingRow(
                        title = preset.title,
                        subtitle = presetDescription(preset.id),
                        selected = selected == preset.id,
                        onClick = { selected = preset.id }
                    )
                    if (index != AssistantPreset.all.lastIndex) SettingsDivider()
                }
            }

            if (selected == "custom") {
                SettingsGroup(title = "Custom instructions") {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = { Text("Custom system instruction") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                    )
                }
            }

            Button(
                onClick = { vm.setPersona(selected, custom) },
                enabled = selected != "custom" || custom.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("Save persona")
            }
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

/** v5.9: cost cap choices in US cents; 0 is Off. */
private val costCapOptionsCents = listOf(0, 10, 25, 50, 100, 200)

private fun costCapLabel(cents: Int): String =
    if (cents <= 0) "Off" else "$" + String.format(java.util.Locale.US, "%.2f", cents / 100.0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { BackBar("Search & tools", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroup(title = "Web search") {
                SettingSwitch(
                    title = "Parallel web search",
                    subtitle = "Allow the model to request current web information. Search itself does not require confirmation.",
                    checked = settings.searchEnabled,
                    onChecked = vm::setSearch
                )
            }

            SettingsGroup(title = "Search context") {
                SliderSetting(
                    title = "Maximum context · ${settings.maxSearchChars / 1_000}k characters",
                    subtitle = "A larger context can improve coverage but consumes more input tokens.",
                    value = settings.maxSearchChars.toFloat(),
                    onValueChange = { vm.setSearchChars((it / 1_000).roundToInt() * 1_000) },
                    valueRange = 2_000f..50_000f,
                    steps = 47,
                    enabled = settings.searchEnabled
                )
            }

            // v5.10: Agent mode quality preset, also switchable from the Agent composer.
            SettingsGroup(title = "Agent quality") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Quality preset · ${settings.agentQuality.label}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        settings.agentQuality.summary + " Fast and Max adjust the limits below for Agent mode; a reasoning level you picked for a model always wins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AgentQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = quality == settings.agentQuality,
                            onClick = { vm.setAgentQuality(quality) },
                            label = { Text(quality.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            SettingsGroup(title = "Agent limits") {
                SliderSetting(
                    title = "Step budget · ${settings.stepBudget} tool rounds",
                    subtitle = "How many rounds of tool use one agent turn may take. When the budget runs out, the agent writes its answer from what it found.",
                    value = settings.stepBudget.toFloat(),
                    onValueChange = { vm.setStepBudget(it.roundToInt()) },
                    valueRange = AgentTurnPolicy.MIN_STEP_BUDGET.toFloat()..AgentTurnPolicy.MAX_STEP_BUDGET.toFloat(),
                    steps = AgentTurnPolicy.MAX_STEP_BUDGET - AgentTurnPolicy.MIN_STEP_BUDGET - 1
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Cost cap per turn", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Stops tool use once a turn has spent this much and writes the answer. Uses the cost OpenRouter reports; for MiniMax it counts tokens instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    costCapOptionsCents.forEach { cents ->
                        FilterChip(
                            selected = cents == settings.costCapCents,
                            onClick = { vm.setCostCapCents(cents) },
                            label = { Text(costCapLabel(cents)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingSwitch(
                    title = "Review answers before sending",
                    subtitle = "After research or file work, the agent checks its draft against your request and the sources once, and fixes what it finds.",
                    checked = settings.reviewAnswers,
                    onChecked = vm::setReviewAnswers
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS
    var confirmClear by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setLocationEnabled(granted) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { BackBar("Data, privacy & usage", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroup(title = "Device security") {
                SettingSwitch(
                    title = "Biometric lock",
                    subtitle = if (biometricAvailable) {
                        "Require biometric authentication when the app is reopened."
                    } else {
                        "No enrolled biometric is available on this device."
                    },
                    checked = settings.biometricLock,
                    onChecked = vm::setBiometric,
                    enabled = biometricAvailable
                )
            }

            SettingsGroup(title = "Location") {
                SettingSwitch(
                    title = "Approximate location",
                    subtitle = if (settings.locationEnabled && settings.locationLabel.isNotBlank()) {
                        "On · ${settings.locationLabel}. Coarse location gives the assistant regional context."
                    } else {
                        "Off by default. Coarse location lets Kryzz answer local questions about your region."
                    },
                    checked = settings.locationEnabled,
                    onChecked = { wanted ->
                        if (wanted && !vm.hasLocationPermission()) {
                            locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            vm.setLocationEnabled(wanted)
                        }
                    }
                )
            }

            SettingsGroup(title = "Local-first data") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Conversations, reusable skills, citations, token usage, and returned cost are stored in the local Room database. Generated documents, databases, media, and code ZIPs stay in private app storage. Preferences stay in DataStore. API keys are separately encrypted with Android Keystore. Android cloud backup and device transfer are disabled for all app data.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "There is no analytics, advertising identifier, telemetry SDK, or HTTP logging. Conversation exports never include credentials.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            SettingsGroup(title = "Provider usage") {
                Text(
                    text = "Token counts and estimated cost appear beneath assistant messages when OpenRouter returns them. To name a new chat, Kryzz also sends up to the first 1,500 characters of its first message through OpenRouter to the low-cost Llama 3.2 1B model using price-first routing; a local title is used if that request is unavailable. Provider dashboards remain the authoritative billing record.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SettingsGroup(title = "Device data") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                        Text(" Clear all local data")
                    }
                    notice?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all local data?") },
            text = {
                Text("This permanently deletes conversations, reusable skills, generated outputs, settings, and both encrypted API credentials from this device.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearAll()
                }) { Text("Clear everything") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    description: String? = null,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp),
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        description?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = LocalGlassOpacity.current),
            tonalElevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectionSettingRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChecked
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@Composable
private fun SettingsDivider(indented: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(start = if (indented) 52.dp else 0.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

private fun ThemeMode.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun ChatDensity.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
