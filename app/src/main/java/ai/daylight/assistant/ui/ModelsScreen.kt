package ai.daylight.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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
import ai.daylight.assistant.domain.textModelBoard
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.KryzzGlyph
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.tune.TuneCaption
import ai.daylight.assistant.ui.tune.TuneChoiceRow
import ai.daylight.assistant.ui.tune.TunePage
import ai.daylight.assistant.ui.tune.TuneSection
import java.util.Locale

/**
 * Models: pick the model for each purpose. The list is a Tune board: a word index for the
 * purpose, then recommended, saved, and the rest as hairline rows. The selected row carries
 * the yellow dot, the favourite, and the thinking level.
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
    val chatFiltered = remember(models, query, companyFilter) {
        filterCatalog(models, query, companyFilter)
    }
    val board = remember(chatFiltered, settings.favoriteModels) {
        textModelBoard(chatFiltered, settings.favoriteModels)
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
        }.sortedBy { it.name.lowercase() }
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
    val inUse = inUseName(
        currentId,
        if (purpose in textPurposes) models else emptyList(),
        when (purpose) {
            ModelPurpose.IMAGE -> imageModels
            ModelPurpose.VIDEO -> videoModels
            ModelPurpose.AUDIO -> audioModels
            else -> emptyList()
        },
    )

    TunePage(
        "Models",
        onBack,
        scrollable = false,
        mark = R.drawable.kz_led_model,
        actions = {
            DotIconButton(R.drawable.ic_kz_retry, "Refresh models", vm::refresh, tint = DotTheme.c.textSecondary)
        }
    ) {
        TuneCaption("In use  $inUse")
        PurposeIndex(purpose) { vm.purpose.value = it }
        Spacer(Modifier.height(4.dp))
        DotField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = "Search ${purpose.word().lowercase(Locale.US)} models",
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
                target in textPurposes -> TextModelBoardList(
                    recommended = board.recommended,
                    saved = board.saved,
                    rest = board.rest,
                    currentId = currentId,
                    favorites = settings.favoriteModels,
                    reasoning = settings.modelReasoning,
                    onSelect = vm::select,
                    onFavorite = vm::favorite,
                    onReasoning = vm::setReasoning,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(mediaFiltered, key = { it.id }) { model ->
                        BoardRow(
                            title = model.name,
                            caption = mediaCaption(model),
                            icon = target.icon(),
                            selected = model.id == currentId,
                            onClick = { vm.select(model.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PurposeIndex(selected: ModelPurpose, onSelect: (ModelPurpose) -> Unit) {
    val c = DotTheme.c
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelPurpose.entries.forEach { item ->
            val on = item == selected
            Text(
                item.word().uppercase(Locale.US),
                Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Tab, onClickLabel = item.word()) { onSelect(item) }
                    .semantics { this.selected = on }
                    .padding(vertical = 8.dp),
                style = DotType.Label,
                color = if (on) c.textPrimary else c.textTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TextModelBoardList(
    recommended: List<OpenRouterModel>,
    saved: List<OpenRouterModel>,
    rest: List<OpenRouterModel>,
    currentId: String,
    favorites: Set<String>,
    reasoning: Map<String, ReasoningEffort>,
    onSelect: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onReasoning: (String, ReasoningEffort) -> Unit,
) {
    val showAll = recommended.isNotEmpty() || saved.isNotEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (recommended.isNotEmpty()) {
            item(key = "section-recommended") { BoardHeading("Recommended", first = true) }
            items(recommended, key = { "rec-${it.id}" }) { model ->
                TextBoardRow(model, model.id == currentId, model.id in favorites, reasoning[model.id] ?: ReasoningEffort.AUTO, onSelect, onFavorite, onReasoning)
            }
        }
        if (saved.isNotEmpty()) {
            item(key = "section-saved") { BoardHeading("Saved", first = recommended.isEmpty()) }
            items(saved, key = { "saved-${it.id}" }) { model ->
                TextBoardRow(model, model.id == currentId, favorite = true, reasoning[model.id] ?: ReasoningEffort.AUTO, onSelect, onFavorite, onReasoning)
            }
        }
        if (rest.isNotEmpty()) {
            if (showAll) item(key = "section-all") { BoardHeading("All", first = false) }
            items(rest, key = { it.id }) { model ->
                TextBoardRow(model, model.id == currentId, model.id in favorites, reasoning[model.id] ?: ReasoningEffort.AUTO, onSelect, onFavorite, onReasoning)
            }
        }
    }
}

@Composable
private fun BoardHeading(title: String, first: Boolean) {
    TuneSection(title, Modifier.padding(top = if (first) 4.dp else 18.dp))
}

@Composable
private fun TextBoardRow(
    model: OpenRouterModel,
    selected: Boolean,
    favorite: Boolean,
    reasoningEffort: ReasoningEffort,
    onSelect: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onReasoning: (String, ReasoningEffort) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        BoardRow(
            title = model.name,
            caption = textCaption(model),
            icon = R.drawable.kz_led_model,
            selected = selected,
            onClick = { onSelect(model.id) },
            trailing = {
                DotIconButton(
                    R.drawable.kz_led_favorite,
                    if (favorite) "Remove favorite" else "Favorite",
                    { onFavorite(model.id) },
                    tint = if (favorite) DotTheme.c.textPrimary else DotTheme.c.textTertiary,
                    iconSize = 18.dp,
                    accent = if (favorite) DotTheme.c.accent else DotTheme.c.textTertiary,
                )
            },
        )
        if (selected) {
            ReasoningSelector(model, reasoningEffort) { onReasoning(model.id, it) }
        }
    }
}

/** One hairline row. The yellow dot is the only mark that this row is in use. */
@Composable
private fun BoardRow(
    title: String,
    caption: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = DotTheme.c
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics { this.selected = selected }
                .clickable(role = Role.RadioButton, onClickLabel = "Select $title", onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KryzzGlyph(icon, Modifier.size(20.dp), tint = if (selected) c.textPrimary else c.textSecondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = DotType.Body.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (caption.isNotBlank()) {
                    Text(
                        caption.uppercase(Locale.US),
                        Modifier.padding(top = 1.dp),
                        style = DotType.Caption.copy(fontSize = 10.sp, lineHeight = 14.sp),
                        color = c.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) trailing()
            if (selected) {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                )
            }
        }
    }
}

/** The per-model thinking level, shown only on the row that is in use. */
@Composable
private fun ReasoningSelector(model: OpenRouterModel, selected: ReasoningEffort, onSelect: (ReasoningEffort) -> Unit) {
    val c = DotTheme.c
    val available = model.availableReasoningEfforts()
    if (available.size == 1) {
        TuneCaption("Thinking fixed by the model", Modifier.padding(start = 34.dp, bottom = 8.dp))
        return
    }
    var open by remember { mutableStateOf(false) }
    val defaultSuffix = if (selected == ReasoningEffort.AUTO) {
        model.reasoning?.defaultEffort?.let { ", ${it.replaceFirstChar(Char::uppercase)}" }.orEmpty()
    } else ""
    Row(
        Modifier
            .padding(start = 34.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Change thinking level") { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KryzzGlyph(R.drawable.kz_led_thinking, Modifier.padding(end = 8.dp).size(16.dp), tint = c.textSecondary)
        Text(
            "Thinking  ${selected.label}$defaultSuffix".uppercase(Locale.US),
            style = DotType.Label,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painterResource(R.drawable.ic_kz_chevron_down),
            null,
            Modifier.padding(start = 6.dp).size(14.dp),
            tint = c.textTertiary,
        )
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
                    },
                    icon = effort.ledIcon(),
                )
            }
        }
    }
}

private fun inUseName(id: String, text: List<OpenRouterModel>, media: List<MediaModel>): String {
    text.find { it.id == id }?.name?.takeIf { it.isNotBlank() }?.let { return it }
    media.find { it.id == id }?.name?.takeIf { it.isNotBlank() }?.let { return it }
    return id.substringAfter('/').ifBlank { "None" }
}

private fun textCaption(model: OpenRouterModel): String = listOf(
    providerBrandFor(model.id).name,
    "${model.pricing?.prompt.tokenPrice()} in",
    "${model.pricing?.completion.tokenPrice()} out",
    model.contextLength.compactTokens(),
).joinToString("   ")

private fun mediaCaption(model: MediaModel): String = listOfNotNull(
    providerBrandFor(model.id).name.takeIf { it.isNotBlank() },
    model.supportedResolutions.takeIf { it.isNotEmpty() }?.joinToString(" / "),
    model.supportedAspectRatios.takeIf { it.isNotEmpty() }?.joinToString(" / "),
    "Streaming".takeIf { model.supportsStreaming },
).joinToString("   ")

private fun ModelPurpose.word(): String = when (this) {
    ModelPurpose.CHAT -> "Everyday"
    ModelPurpose.AGENT -> "Agent"
    ModelPurpose.RESEARCH -> "Research"
    ModelPurpose.MAX -> "Max"
    ModelPurpose.IMAGE -> "Image"
    ModelPurpose.VIDEO -> "Video"
    ModelPurpose.AUDIO -> "Audio"
}

private fun ModelPurpose.icon(): Int = when (this) {
    ModelPurpose.CHAT -> R.drawable.kz_led_everyday_chat
    ModelPurpose.AGENT -> R.drawable.kz_led_agent
    ModelPurpose.RESEARCH -> R.drawable.kz_led_research
    ModelPurpose.MAX -> R.drawable.kz_led_max
    ModelPurpose.IMAGE -> R.drawable.kz_led_image
    ModelPurpose.VIDEO -> R.drawable.kz_led_video
    ModelPurpose.AUDIO -> R.drawable.kz_led_audio
}

private fun ReasoningEffort.ledIcon(): Int = when (this) {
    ReasoningEffort.AUTO -> R.drawable.kz_led_model_default
    ReasoningEffort.NONE -> R.drawable.kz_led_thinking_off
    ReasoningEffort.MINIMAL -> R.drawable.kz_led_minimal
    ReasoningEffort.LOW -> R.drawable.kz_led_low
    ReasoningEffort.MEDIUM -> R.drawable.kz_led_medium
    ReasoningEffort.HIGH -> R.drawable.kz_led_high
    ReasoningEffort.XHIGH -> R.drawable.kz_led_extra_high
    ReasoningEffort.MAX -> R.drawable.kz_led_maximum
}
