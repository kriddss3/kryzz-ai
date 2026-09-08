package ai.daylight.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.ReasoningEffort
import ai.daylight.assistant.domain.availableReasoningEfforts
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.delay

private data class KryzzChatModelOption(
    val id: String,
    val name: String,
    val description: String,
    val textModel: OpenRouterModel? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KryzzChatAiControlsSheet(
    purpose: ModelPurpose,
    selectedModelId: String,
    selectedReasoning: ReasoningEffort,
    settings: SettingsState,
    textModels: List<OpenRouterModel>,
    imageModels: List<MediaModel>,
    videoModels: List<MediaModel>,
    audioModels: List<MediaModel>,
    modelsLoading: Boolean,
    modelsError: String?,
    mediaModelsError: String?,
    researchDepth: Int,
    researchWidth: Int,
    memoryEnabled: Boolean,
    agentSwarmEnabled: Boolean,
    onDismiss: () -> Unit,
    onSelectModel: (String) -> Unit,
    onReasoning: (String, ReasoningEffort) -> Unit,
    onResearchTuning: (Int, Int) -> Unit,
    onMemoryEnabled: (Boolean) -> Unit,
    onAgentSwarmEnabled: (Boolean) -> Unit,
    onOpenModels: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = remember(purpose, textModels, imageModels, videoModels, audioModels, selectedModelId) {
        val available = when (purpose) {
            ModelPurpose.IMAGE -> imageModels.map { KryzzChatModelOption(it.id, it.name, it.description) }
            ModelPurpose.VIDEO -> videoModels.map { KryzzChatModelOption(it.id, it.name, it.description) }
            ModelPurpose.AUDIO -> audioModels.map { KryzzChatModelOption(it.id, it.name, it.description) }
            else -> textModels.map { KryzzChatModelOption(it.id, it.name, it.description, it) }
        }
        available
    }
    var query by remember(purpose) { mutableStateOf("") }
    var activeModelId by remember(purpose, selectedModelId, options) {
        mutableStateOf(
            selectedModelId.takeIf { id -> options.any { it.id == id } }
                ?: options.firstOrNull()?.id.orEmpty()
        )
    }
    var activeReasoning by remember(purpose, activeModelId, selectedReasoning) {
        mutableStateOf(if (activeModelId == selectedModelId) selectedReasoning else ReasoningEffort.AUTO)
    }
    var showAdvanced by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val motionEnabled = LocalKryzzMotionEnabled.current
    // Media lists fail independently of the chat catalog; surface that error here too.
    val listError = when (purpose) {
        ModelPurpose.IMAGE, ModelPurpose.VIDEO, ModelPurpose.AUDIO -> mediaModelsError ?: modelsError
        else -> modelsError
    }
    val activeModel = options.firstOrNull { it.id == activeModelId }
    val filteredModels = remember(options, query) {
        options.filter { option ->
            query.isBlank() || option.name.contains(query, ignoreCase = true) ||
                option.id.contains(query, ignoreCase = true) || option.description.contains(query, ignoreCase = true)
        }
    }
    val reasoningOptions = activeModel?.textModel?.availableReasoningEfforts().orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AI controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tune the next response without crowding the chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Close, "Close AI controls")
                    }
                }

                Text("Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                var modelPickerExpanded by remember { mutableStateOf(false) }
                val modelSearchFocus = remember { FocusRequester() }
                Surface(
                    onClick = { modelPickerExpanded = !modelPickerExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("llm_dropdown"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                activeModel?.name ?: activeModelId.kryzzCompactModel(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Outlined.ExpandMore,
                                if (modelPickerExpanded) "Collapse model list" else "Expand model list",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            when {
                                modelsLoading && options.isEmpty() -> "Loading models…"
                                listError != null && options.isEmpty() -> listError
                                else -> "${purpose.kryzzPickerLabel()} · Reasoning: ${activeReasoning.label}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                AnimatedVisibility(
                    visible = modelPickerExpanded,
                    enter = if (motionEnabled) expandVertically() + fadeIn() else EnterTransition.None,
                    exit = if (motionEnabled) shrinkVertically() + fadeOut() else ExitTransition.None
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // The search field and list live inside the sheet window (not a
                        // popup), so the IME opens the normal way and typing works on the
                        // first tap.
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(modelSearchFocus)
                                .onFocusChanged { if (it.isFocused) keyboard?.show() },
                            placeholder = { Text("Search models…") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        LaunchedEffect(modelPickerExpanded) {
                            if (modelPickerExpanded) {
                                delay(80)
                                modelSearchFocus.requestFocus()
                                keyboard?.show()
                            }
                        }
                        when {
                            modelsLoading && options.isEmpty() -> {
                                Text(
                                    "Loading models…",
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            filteredModels.isEmpty() -> {
                                Text(
                                    if (query.isNotBlank()) "No models match “$query”" else (listError ?: "No matching models"),
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            else -> Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                filteredModels.take(40).forEach { model ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .selectable(
                                                selected = model.id == activeModelId,
                                                role = Role.RadioButton,
                                                onClick = {
                                                    activeModelId = model.id
                                                    activeReasoning = ReasoningEffort.AUTO
                                                    onSelectModel(model.id)
                                                    modelPickerExpanded = false
                                                }
                                            )
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (model.id == activeModelId) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        } else Color.Transparent
                                    ) {
                                        Column {
                                            Text(model.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                model.id.kryzzCompactModel(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${settings.chatProvider.label} · ${activeModelId.kryzzProviderLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { onDismiss(); onOpenModels() }) { Text("Open catalog") }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showAdvanced = !showAdvanced },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Advanced", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Depth, width, context, and tool bounds",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Outlined.ExpandMore, if (showAdvanced) "Collapse advanced controls" else "Expand advanced controls")
                    }
                }
                AnimatedVisibility(
                    visible = showAdvanced,
                    enter = if (motionEnabled) fadeIn() else EnterTransition.None,
                    exit = if (motionEnabled) fadeOut() else ExitTransition.None
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (reasoningOptions.size > 1) {
                            Text("Reasoning", style = MaterialTheme.typography.labelLarge)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                reasoningOptions.forEach { effort ->
                                    KryzzChatChoice(
                                        label = effort.label,
                                        selected = effort == activeReasoning,
                                        onClick = {
                                            activeReasoning = effort
                                            onReasoning(activeModelId, effort)
                                        }
                                    )
                                }
                            }
                        }
                        Text("Research depth · $researchDepth", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = researchDepth.toFloat(),
                            onValueChange = { onResearchTuning(it.toInt().coerceIn(1, 3), researchWidth) },
                            valueRange = 1f..3f,
                            steps = 1
                        )
                        Text("Research width · $researchWidth", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = researchWidth.toFloat(),
                            onValueChange = { onResearchTuning(researchDepth, it.toInt().coerceIn(1, 5)) },
                            valueRange = 1f..5f,
                            steps = 3
                        )
                        Text(
                            "${settings.maxSearchChars / 1_000}k search context · ${settings.maxToolRounds} tool round${if (settings.maxToolRounds == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Chat memory", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Remembers facts you share across chats",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = memoryEnabled, onCheckedChange = onMemoryEnabled)
                        }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Agent swarm", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Planner + subagents split big tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = agentSwarmEnabled, onCheckedChange = onAgentSwarmEnabled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KryzzChatChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            icon?.let {
                Icon(it, null, Modifier.size(17.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(7.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KryzzChatStatusPill(label: String) {
    Surface(
        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ModelPurpose.kryzzPickerLabel(): String = when (this) {
    ModelPurpose.CHAT -> "Everyday model"
    ModelPurpose.AGENT -> "Agent model"
    ModelPurpose.RESEARCH -> "Research model"
    ModelPurpose.IMAGE -> "Image model"
    ModelPurpose.VIDEO -> "Video model"
    ModelPurpose.AUDIO -> "Audio model"
}

private fun String.kryzzCompactModel(): String = substringAfter('/').ifBlank { "Select model" }

private fun String.kryzzProviderLabel(): String {
    val provider = substringBefore('/').ifBlank { "OpenRouter" }
    return when (provider.lowercase()) {
        "openai" -> "OpenAI"
        "anthropic" -> "Anthropic"
        "google" -> "Google"
        "meta-llama" -> "Meta"
        "mistralai" -> "Mistral AI"
        "x-ai" -> "xAI"
        else -> provider.replace('-', ' ').split(' ').joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
