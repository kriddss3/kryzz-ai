package ai.daylight.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.ReasoningEffort
import ai.daylight.assistant.domain.availableReasoningEfforts
import ai.daylight.assistant.domain.capabilityLabels
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPill
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTag
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.tune.TuneCaption
import ai.daylight.assistant.ui.tune.TuneChoiceRow
import ai.daylight.assistant.ui.tune.TunePage
import java.util.Locale

/**
 * Models: pick the model for each purpose (chat, Run, research, Max, image, video, audio).
 * Text models carry a favourite heart and a per-model thinking level; the selected card has
 * a white border and a check (kit §12).
 */
@Composable
fun ModelsScreen(vm: ModelsViewModel, initialPurpose: ModelPurpose, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val imageModels by vm.imageModels.collectAsStateWithLifecycle()
    val videoModels by vm.videoModels.collectAsStateWithLifecycle()
    val audioModels by vm.audioModels.collectAsStateWithLifecycle()
    val purpose by vm.purpose.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val mediaError by vm.mediaError.collectAsStateWithLifecycle()
    var companyFilter by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialPurpose) { vm.purpose.value = initialPurpose }

    val currentId = when (purpose) {
        ModelPurpose.CHAT -> settings.defaultModel
        ModelPurpose.AGENT -> settings.agentModel
        ModelPurpose.RESEARCH -> settings.researchModel
        ModelPurpose.MAX -> settings.maxModel
        ModelPurpose.IMAGE -> settings.imageModel
        ModelPurpose.VIDEO -> settings.videoModel
        ModelPurpose.AUDIO -> settings.audioModel
    }
    val chatFiltered = remember(models, query, settings.favoriteModels, companyFilter) {
        filterCatalog(models, query, companyFilter)
            .sortedWith(compareByDescending<OpenRouterModel> { it.id in settings.favoriteModels }.thenBy { it.name.lowercase() })
    }
    val mediaFiltered = remember(purpose, imageModels, videoModels, audioModels, query, companyFilter) {
        (when (purpose) {
            ModelPurpose.IMAGE -> imageModels
            ModelPurpose.VIDEO -> videoModels
            ModelPurpose.AUDIO -> audioModels
            else -> emptyList()
        }).filter {
            val matchesQuery = query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true)
            val matchesCompany = companyFilter == null || providerBrandFor(it.id).name == companyFilter
            matchesQuery && matchesCompany
        }
    }
    val companies = remember(purpose, models, imageModels, videoModels, audioModels) {
        val source = when (purpose) {
            ModelPurpose.IMAGE -> imageModels.map { it.id }
            ModelPurpose.VIDEO -> videoModels.map { it.id }
            ModelPurpose.AUDIO -> audioModels.map { it.id }
            else -> models.map { it.id }
        }
        source.map { providerBrandFor(it).name }.distinct().sorted()
    }
    val textPurposes = setOf(ModelPurpose.CHAT, ModelPurpose.AGENT, ModelPurpose.RESEARCH, ModelPurpose.MAX)

    TunePage(
        "Models",
        onBack,
        scrollable = false,
        actions = {
            DotIconButton(R.drawable.ic_kz_retry, "Refresh models", vm::refresh, tint = DotTheme.c.textSecondary)
        }
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ModelPurpose.entries) { item ->
                DotPill(item.heading(), selected = purpose == item, onClick = { vm.purpose.value = item }, icon = item.icon(), role = Role.Tab)
            }
        }
        Spacer(Modifier.height(4.dp))
        DotField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = "Search ${purpose.heading().lowercase(Locale.US)} models",
            leadingIcon = R.drawable.ic_kz_search
        )
        if (companies.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp)) { CompanyFilter(companies, companyFilter) { companyFilter = it } }
        }
        Spacer(Modifier.height(8.dp))
        val motionEnabled = LocalKryzzMotionEnabled.current
        AnimatedContent(
            targetState = purpose,
            transitionSpec = {
                if (motionEnabled) fadeIn().togetherWith(fadeOut())
                else EnterTransition.None.togetherWith(ExitTransition.None)
            },
            label = "model catalog",
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { target ->
            when {
                loading && models.isEmpty() && imageModels.isEmpty() && videoModels.isEmpty() && audioModels.isEmpty() -> ModelsLoading()
                target in textPurposes && chatFiltered.isEmpty() -> ModelsEmpty(error ?: "No text model matches.")
                target == ModelPurpose.IMAGE && mediaFiltered.isEmpty() -> ModelsEmpty(
                    mediaError?.let { "Image models couldn't load: $it" }
                        ?: "No image model matches. Add or check your OpenRouter key, then refresh.",
                    onRetry = vm::refresh
                )
                target == ModelPurpose.VIDEO && mediaFiltered.isEmpty() -> ModelsEmpty(
                    mediaError?.let { "Video models couldn't load: $it" }
                        ?: "No video model matches. Add or check your OpenRouter key, then refresh.",
                    onRetry = vm::refresh
                )
                target == ModelPurpose.AUDIO && mediaFiltered.isEmpty() -> ModelsEmpty(
                    mediaError?.let { "Speech models couldn't load: $it" }
                        ?: "No speech model matches. Add or check your OpenRouter key, then refresh.",
                    onRetry = vm::refresh
                )
                target in textPurposes -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatFiltered, key = { it.id }) { model ->
                        TextModelCard(
                            model = model,
                            selected = model.id == currentId,
                            favorite = model.id in settings.favoriteModels,
                            reasoningEffort = settings.modelReasoning[model.id] ?: ReasoningEffort.AUTO,
                            onSelect = { vm.select(model.id) },
                            onFavorite = { vm.favorite(model.id) },
                            onReasoning = { vm.setReasoning(model.id, it) }
                        )
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mediaFiltered, key = { it.id }) { model -> MediaModelCard(model, purpose, model.id == currentId) { vm.select(model.id) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextModelCard(
    model: OpenRouterModel,
    selected: Boolean,
    favorite: Boolean,
    reasoningEffort: ReasoningEffort,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
    onReasoning: (ReasoningEffort) -> Unit
) {
    val c = DotTheme.c
    val provider = providerBrandFor(model.id)
    ModelCardFrame(
        selected = selected,
        onClick = onSelect,
        role = Role.RadioButton,
        modifier = Modifier.semantics { this.selected = selected }
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ProviderMark(provider)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ModelName(model.name)
                Text(
                    "${provider.name} · ${model.id}",
                    style = DotType.Data.copy(fontSize = 11.sp),
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DotIconButton(
                if (favorite) R.drawable.ic_kz_heart_filled else R.drawable.ic_kz_heart,
                if (favorite) "Remove favorite" else "Favorite",
                onFavorite,
                Modifier.padding(end = if (selected) 22.dp else 0.dp),
                tint = if (favorite) c.textPrimary else c.textTertiary,
                iconSize = 18.dp
            )
        }
        if (model.description.isNotBlank()) {
            Text(model.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = DotType.BodySmall, color = c.textSecondary)
        }
        SpecLine(
            model.contextLength?.let { "CTX ${it.compactTokens()}" }.orEmpty(),
            model.pricing?.prompt?.let { "IN ${it.tokenPrice()}" }.orEmpty(),
            model.pricing?.completion?.let { "OUT ${it.tokenPrice()}" }.orEmpty()
        )
        val labels = model.capabilityLabels()
        if (labels.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach { CapabilityTag(it) }
            }
        }
        ReasoningSelector(model, reasoningEffort, onReasoning)
    }
}

/** The per-model thinking level: a pill that opens a sheet, or a fixed note. */
@Composable
private fun ReasoningSelector(model: OpenRouterModel, selected: ReasoningEffort, onSelect: (ReasoningEffort) -> Unit) {
    val c = DotTheme.c
    val available = model.availableReasoningEfforts()
    if (available.size == 1) {
        TuneCaption("Thinking fixed by the model")
        return
    }
    var open by remember { mutableStateOf(false) }
    val defaultSuffix = if (selected == ReasoningEffort.AUTO) {
        model.reasoning?.defaultEffort?.let { ", ${it.replaceFirstChar(Char::uppercase)}" }.orEmpty()
    } else ""
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(DotRadius.Pill)
            .clickable(role = Role.Button, onClickLabel = "Change thinking level") { open = true }
            .height(36.dp)
            .border(1.dp, c.borderStrong, DotRadius.Pill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("THINKING: ${selected.label}$defaultSuffix".uppercase(Locale.US), style = DotType.Label, color = c.textPrimary, maxLines = 1)
        Icon(painterResource(R.drawable.ic_kz_chevron_down), null, Modifier.padding(start = 6.dp).size(14.dp), tint = c.textTertiary)
    }
    if (open) {
        DotSheet(onDismiss = { open = false }) {
            SheetLabel("Thinking for ${model.name}")
            available.forEach { effort ->
                TuneChoiceRow(
                    effort.label,
                    if (effort == ReasoningEffort.AUTO) "Use this model's provider default" else null,
                    effort == selected,
                    {
                        open = false
                        onSelect(effort)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaModelCard(model: MediaModel, purpose: ModelPurpose, selected: Boolean, onSelect: () -> Unit) {
    val c = DotTheme.c
    val provider = providerBrandFor(model.id)
    ModelCardFrame(
        selected = selected,
        onClick = onSelect,
        role = Role.RadioButton,
        modifier = Modifier.semantics { this.selected = selected }
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ProviderMark(provider)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = if (selected) 24.dp else 0.dp)) {
                ModelName(model.name)
                Text(
                    "${provider.name} · ${model.id}",
                    style = DotType.Data.copy(fontSize = 11.sp),
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (model.description.isNotBlank()) {
            Text(model.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = DotType.BodySmall, color = c.textSecondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DotTag("Text prompt")
            DotTag(
                when (purpose) {
                    ModelPurpose.IMAGE -> "Image output"
                    ModelPurpose.VIDEO -> "Video output"
                    ModelPurpose.AUDIO -> "Audio output"
                    else -> "Media output"
                }
            )
        }
        SpecLine(
            model.supportedResolutions.takeIf { it.isNotEmpty() }?.joinToString(" / ").orEmpty(),
            model.supportedAspectRatios.takeIf { it.isNotEmpty() }?.joinToString(" / ").orEmpty(),
            if (model.supportsStreaming) "Streaming" else ""
        )
    }
}

private fun ModelPurpose.heading(): String = when (this) {
    ModelPurpose.CHAT -> "Everyday chat"
    ModelPurpose.AGENT -> "Agent"
    ModelPurpose.RESEARCH -> "Research"
    ModelPurpose.MAX -> "Max"
    ModelPurpose.IMAGE -> "Image"
    ModelPurpose.VIDEO -> "Video"
    ModelPurpose.AUDIO -> "Audio"
}

private fun ModelPurpose.icon(): Int = when (this) {
    ModelPurpose.CHAT -> R.drawable.ic_kz_chat
    ModelPurpose.AGENT -> R.drawable.ic_kz_agent
    ModelPurpose.RESEARCH -> R.drawable.ic_kz_search
    ModelPurpose.MAX -> R.drawable.ic_kz_max
    ModelPurpose.IMAGE -> R.drawable.ic_kz_image
    ModelPurpose.VIDEO -> R.drawable.ic_kz_video
    ModelPurpose.AUDIO -> R.drawable.ic_kz_voice_wave
}
