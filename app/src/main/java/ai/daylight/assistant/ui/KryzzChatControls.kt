package ai.daylight.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.ReasoningEffort
import ai.daylight.assistant.domain.availableReasoningEfforts
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPill
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.tune.DotSegments
import ai.daylight.assistant.ui.tune.SegmentOption
import ai.daylight.assistant.ui.tune.TuneCaption
import ai.daylight.assistant.ui.tune.TuneChoiceRow
import ai.daylight.assistant.ui.tune.TuneDetail
import ai.daylight.assistant.ui.tune.TuneRow
import ai.daylight.assistant.ui.tune.TuneSection
import ai.daylight.assistant.ui.tune.TuneTrailing
import kotlinx.coroutines.delay

private data class KryzzChatModelOption(
    val id: String,
    val name: String,
    val description: String,
    val textModel: OpenRouterModel? = null
)

/**
 * AI controls for the next reply (kit sheet, C11): the model as a ruled row that unfolds into
 * a search field and a radio list, the catalog, then Advanced with reasoning pills, research
 * depth and width as segments, and the memory and swarm switches.
 */
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
    val c = DotTheme.c
    val options = remember(purpose, textModels, imageModels, videoModels, audioModels, selectedModelId) {
        when (purpose) {
            ModelPurpose.IMAGE -> imageModels.map { KryzzChatModelOption(it.id, it.name, it.description) }
            ModelPurpose.VIDEO -> videoModels.map { KryzzChatModelOption(it.id, it.name, it.description) }
            ModelPurpose.AUDIO -> audioModels.map { KryzzChatModelOption(it.id, it.name, it.description) }
            else -> textModels.map { KryzzChatModelOption(it.id, it.name, it.description, it) }
        }
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
    var modelPickerExpanded by remember { mutableStateOf(false) }
    val modelSearchFocus = remember { FocusRequester() }
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

    DotSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI controls", Modifier.semantics { heading() }, style = DotType.Headline, color = c.textPrimary)
                Text(
                    "Tune the next reply without crowding the chat.",
                    Modifier.padding(top = 2.dp),
                    style = DotType.BodySmall,
                    color = c.textTertiary
                )
            }
            DotIconButton(R.drawable.ic_kz_close, "Close AI controls", onDismiss, tint = c.textSecondary)
        }
        Spacer(Modifier.height(18.dp))

        TuneSection(purpose.kryzzPickerLabel())
        TuneRow(
            purpose.controlGlyph(),
            activeModel?.name ?: activeModelId.kryzzCompactModel(),
            when {
                modelsLoading && options.isEmpty() -> "Loading models"
                listError != null && options.isEmpty() -> listError
                else -> "${activeModelId.kryzzProviderLabel()} · Reasoning ${activeReasoning.label}"
            },
            TuneTrailing.Expand(modelPickerExpanded),
            onClick = { modelPickerExpanded = !modelPickerExpanded },
            modifier = Modifier.testTag("llm_dropdown")
        )
        AnimatedVisibility(
            visible = modelPickerExpanded,
            enter = if (motionEnabled) expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (motionEnabled) shrinkVertically() + fadeOut() else ExitTransition.None
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
                // The search field and list live inside the sheet window (not a popup), so
                // the keyboard opens the normal way and typing works on the first tap.
                DotField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search models…",
                    leadingIcon = R.drawable.kz_led_web_search,
                    trailing = if (query.isNotEmpty()) {
                        { DotIconButton(R.drawable.ic_kz_close, "Clear search", { query = "" }, tint = c.textTertiary, iconSize = 18.dp) }
                    } else null,
                    fieldModifier = Modifier
                        .focusRequester(modelSearchFocus)
                        .onFocusChanged { if (it.isFocused) keyboard?.show() }
                )
                LaunchedEffect(modelPickerExpanded) {
                    if (modelPickerExpanded) {
                        delay(80)
                        modelSearchFocus.requestFocus()
                        keyboard?.show()
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    modelsLoading && options.isEmpty() -> TuneDetail("Loading models…", Modifier.padding(vertical = 10.dp))
                    filteredModels.isEmpty() -> TuneDetail(
                        if (query.isNotBlank()) "No models match “$query”" else (listError ?: "No matching models"),
                        Modifier.padding(vertical = 10.dp)
                    )
                    else -> Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .selectableGroup()
                    ) {
                        filteredModels.take(40).forEach { model ->
                            TuneChoiceRow(
                                title = model.name,
                                detail = model.id.kryzzCompactModel(),
                                selected = model.id == activeModelId,
                                onClick = {
                                    activeModelId = model.id
                                    activeReasoning = ReasoningEffort.AUTO
                                    onSelectModel(model.id)
                                    modelPickerExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
        TuneRow(
            R.drawable.kz_led_model_catalog,
            "Open catalog",
            "${settings.chatProvider.label} · every model your key can use",
            onClick = { onDismiss(); onOpenModels() }
        )
        TuneRow(
            R.drawable.kz_led_settings,
            "Advanced",
            "Depth, width, context and tool bounds",
            TuneTrailing.Expand(showAdvanced),
            onClick = { showAdvanced = !showAdvanced }
        )
        AnimatedVisibility(
            visible = showAdvanced,
            enter = if (motionEnabled) expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (motionEnabled) shrinkVertically() + fadeOut() else ExitTransition.None
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                if (reasoningOptions.size > 1) {
                    TuneSection("Reasoning")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        reasoningOptions.forEach { effort ->
                            DotPill(
                                effort.label,
                                selected = effort == activeReasoning,
                                onClick = {
                                    activeReasoning = effort
                                    onReasoning(activeModelId, effort)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                TuneSection("Research depth")
                DotSegments(
                    options = (1..3).map { SegmentOption("$it") },
                    selected = researchDepth.coerceIn(1, 3) - 1,
                    onSelect = { onResearchTuning(it + 1, researchWidth) }
                )
                Spacer(Modifier.height(16.dp))
                TuneSection("Research width")
                DotSegments(
                    options = (1..5).map { SegmentOption("$it") },
                    selected = researchWidth.coerceIn(1, 5) - 1,
                    onSelect = { onResearchTuning(researchDepth, it + 1) }
                )
                TuneCaption(
                    "${settings.maxSearchChars / 1_000}k search context, ${settings.stepBudget}-step budget",
                    Modifier.padding(top = 10.dp, bottom = 14.dp)
                )
                TuneRow(
                    R.drawable.kz_led_chat_memory,
                    "Chat memory",
                    "Remembers facts you share across chats",
                    TuneTrailing.Switch(memoryEnabled, onMemoryEnabled)
                )
                TuneRow(
                    R.drawable.kz_led_agent,
                    "Agent swarm",
                    "A planner and helpers split big tasks",
                    TuneTrailing.Switch(agentSwarmEnabled, onAgentSwarmEnabled)
                )
            }
        }
    }
}

/** v5.10: what the Agent composer says about the model the next turn will run on. */
internal data class KryzzAgentModelNotice(
    /** The one-time suggestion to leave the old gpt-4o-mini default. */
    val suggestUpgrade: Boolean,
    /** Display name of the model the suggestion switches to. */
    val suggestedName: String,
    /** The catalog lists the active model without tool calling. */
    val lacksTools: Boolean
) {
    val visible: Boolean get() = suggestUpgrade || lacksTools
}

/**
 * Above Run's reply line: a red line when the model cannot call tools, and the one-time
 * offer to leave the old default model, with Keep and Switch as text pills.
 */
@Composable
internal fun KryzzAgentModelNoticeCard(
    notice: KryzzAgentModelNotice,
    onSwitch: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = DotTheme.c
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (notice.lacksTools) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.ic_kz_warning), null, Modifier.padding(top = 2.dp).size(14.dp), tint = c.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "This model has no tool calling, so the agent cannot search, read pages or create files with it. Pick a model with the Tool calling badge.",
                    style = DotType.BodySmall,
                    color = c.error
                )
            }
        }
        if (notice.suggestUpgrade) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DotRadius.Tile)
                    .background(c.surfaceRaised)
                    .border(1.dp, c.border, DotRadius.Tile)
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp)
            ) {
                Text(
                    "This model is weak at multi-step tool use. Switch to ${notice.suggestedName}?",
                    style = DotType.BodySmall,
                    color = c.textPrimary
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NoticeAction("Keep", onKeep)
                    NoticeAction("Switch", onSwitch)
                }
            }
        }
    }
}

@Composable
private fun NoticeAction(label: String, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        Modifier
            .clip(DotRadius.Pill)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        style = DotType.Label,
        color = DotTheme.c.textPrimary
    )
}

private fun ModelPurpose.controlGlyph(): Int = when (this) {
    ModelPurpose.CHAT -> R.drawable.kz_led_chat_model
    ModelPurpose.AGENT -> R.drawable.kz_led_agent
    ModelPurpose.RESEARCH -> R.drawable.kz_led_research
    ModelPurpose.MAX -> R.drawable.kz_led_max
    ModelPurpose.IMAGE -> R.drawable.kz_led_image
    ModelPurpose.VIDEO -> R.drawable.kz_led_video
    ModelPurpose.AUDIO -> R.drawable.kz_led_audio
}

private fun ModelPurpose.kryzzPickerLabel(): String = when (this) {
    ModelPurpose.CHAT -> "Everyday model"
    ModelPurpose.AGENT -> "Agent model"
    ModelPurpose.RESEARCH -> "Research model"
    ModelPurpose.MAX -> "Max model"
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
