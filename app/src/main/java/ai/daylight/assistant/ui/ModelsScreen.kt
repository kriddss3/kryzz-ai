package ai.daylight.assistant.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.ReasoningEffort
import ai.daylight.assistant.domain.availableReasoningEfforts
import ai.daylight.assistant.domain.capabilityLabels

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Models") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, "Refresh models") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ModelPurpose.entries) { item -> PurposeChip(item, purpose == item) { vm.purpose.value = item } }
            }
            OutlinedTextField(
                query,
                { vm.query.value = it },
                label = { Text("Search ${purpose.heading().lowercase()}") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            if (companies.isNotEmpty()) {
                var companyMenuExpanded by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedButton(
                        onClick = { companyMenuExpanded = true },
                        modifier = Modifier.testTag("company_filter_button")
                    ) {
                        Icon(Icons.Outlined.FilterList, null, Modifier.size(18.dp))
                        Text(if (companyFilter == null) " Filter: all companies" else " Filter: $companyFilter")
                        Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = companyMenuExpanded,
                        onDismissRequest = { companyMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All companies") },
                            leadingIcon = if (companyFilter == null) {
                                { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
                            } else null,
                            onClick = {
                                companyFilter = null
                                companyMenuExpanded = false
                            },
                            modifier = Modifier.testTag("company_filter_all")
                        )
                        companies.forEach { company ->
                            DropdownMenuItem(
                                text = { Text(company, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = if (companyFilter == company) {
                                    { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
                                } else null,
                                onClick = {
                                    companyFilter = company
                                    companyMenuExpanded = false
                                },
                                modifier = Modifier.testTag("company_filter_$company")
                            )
                        }
                    }
                }
            }
            val motionEnabled = LocalKryzzMotionEnabled.current
            AnimatedContent(
                targetState = purpose,
                transitionSpec = {
                    if (motionEnabled) fadeIn().togetherWith(fadeOut())
                    else EnterTransition.None.togetherWith(ExitTransition.None)
                },
                label = "model catalog",
                modifier = Modifier.weight(1f)
            ) { target ->
                when {
                    loading && models.isEmpty() && imageModels.isEmpty() && videoModels.isEmpty() && audioModels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    target in setOf(ModelPurpose.CHAT, ModelPurpose.AGENT, ModelPurpose.RESEARCH) && chatFiltered.isEmpty() -> EmptyModels(error ?: "No matching text models were returned.")
                    target == ModelPurpose.IMAGE && mediaFiltered.isEmpty() -> EmptyModels(
                        mediaError?.let { "Image models couldn't load: $it" }
                            ?: "No matching image models were returned. Add or verify your OpenRouter key, then refresh.",
                        onRetry = vm::refresh
                    )
                    target == ModelPurpose.VIDEO && mediaFiltered.isEmpty() -> EmptyModels(
                        mediaError?.let { "Video models couldn't load: $it" }
                            ?: "No matching video models were returned. Add or verify your OpenRouter key, then refresh.",
                        onRetry = vm::refresh
                    )
                    target == ModelPurpose.AUDIO && mediaFiltered.isEmpty() -> EmptyModels(
                        mediaError?.let { "Speech models couldn't load: $it" }
                            ?: "No matching speech models were returned. Add or verify your OpenRouter key, then refresh.",
                        onRetry = vm::refresh
                    )
                    target in setOf(ModelPurpose.CHAT, ModelPurpose.AGENT, ModelPurpose.RESEARCH) -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(mediaFiltered, key = { it.id }) { model -> MediaModelCard(model, purpose, model.id == currentId) { vm.select(model.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurposeChip(purpose: ModelPurpose, selected: Boolean, onClick: () -> Unit) {
    val icon = when (purpose) {
        ModelPurpose.CHAT -> Icons.Outlined.SmartToy
        ModelPurpose.AGENT -> Icons.Outlined.AutoAwesome
        ModelPurpose.RESEARCH -> Icons.AutoMirrored.Outlined.ManageSearch
        ModelPurpose.IMAGE -> Icons.Outlined.Image
        ModelPurpose.VIDEO -> Icons.Outlined.Movie
        ModelPurpose.AUDIO -> Icons.Outlined.GraphicEq
    }
    Surface(
        Modifier.heightIn(min = 48.dp).selectable(selected = selected, onClick = onClick, role = Role.Tab),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null)
            Text(purpose.heading(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyModels(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
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
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val provider = providerBrandFor(model.id)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected, onClick = onSelect)
            ProviderMark(provider)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleSmall, color = content)
                Text("${provider.name} · ${model.id}", style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.78f))
                if (model.description.isNotBlank()) Text(model.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.88f))
                val context = model.contextLength?.let { "${it / 1_000}k context" }
                val prompt = model.pricing?.prompt?.toDoubleOrNull()?.let { "$${"%.2f".format(it * 1_000_000)}/M input" }
                val completion = model.pricing?.completion?.toDoubleOrNull()?.let { "$${"%.2f".format(it * 1_000_000)}/M output" }
                Text(listOfNotNull(context, prompt, completion).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    model.capabilityLabels().forEach { label ->
                        if (label == "Tool calling") ToolCallingBadge(selected) else CapabilityBadge(label, selected)
                    }
                }
                ReasoningSelector(model, reasoningEffort, onReasoning)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (favorite) "Remove favorite" else "Favorite", tint = if (favorite) MaterialTheme.colorScheme.primary else content.copy(alpha = 0.72f))
            }
        }
    }
}

@Composable
private fun CapabilityBadge(label: String, selectedCard: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selectedCard) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selectedCard) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/** "Tool calling" is shown as an icon rather than a text badge. */
@Composable
private fun ToolCallingBadge(selectedCard: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selectedCard) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selectedCard) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Handyman, "Tool calling", Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ReasoningSelector(model: OpenRouterModel, selected: ReasoningEffort, onSelect: (ReasoningEffort) -> Unit) {
    val available = model.availableReasoningEfforts()
    if (available.size == 1) {
        Text("Thinking · fixed by model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val defaultSuffix = if (selected == ReasoningEffort.AUTO) model.reasoning?.defaultEffort?.let { " · ${it.replaceFirstChar(Char::uppercase)}" }.orEmpty() else ""
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text("Thinking: ${selected.label}$defaultSuffix", style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 5.dp).size(17.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            available.forEach { effort ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(effort.label, color = MaterialTheme.colorScheme.onSurface)
                            if (effort == ReasoningEffort.AUTO) Text("Use this model's provider default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { expanded = false; onSelect(effort) }
                )
            }
        }
    }
}

@Composable
private fun MediaModelCard(model: MediaModel, purpose: ModelPurpose, selected: Boolean, onSelect: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val provider = providerBrandFor(model.id)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected, onClick = onSelect)
            ProviderMark(provider)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleSmall, color = content)
                Text("${provider.name} · ${model.id}", style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.78f))
                if (model.description.isNotBlank()) Text(model.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.88f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CapabilityBadge("Text prompt", selected)
                    CapabilityBadge(when (purpose) {
                        ModelPurpose.IMAGE -> "Image output"
                        ModelPurpose.VIDEO -> "Video output"
                        ModelPurpose.AUDIO -> "Audio output"
                        else -> "Media output"
                    }, selected)
                }
                val details = listOfNotNull(
                    model.supportedResolutions.takeIf { it.isNotEmpty() }?.joinToString(" / "),
                    model.supportedAspectRatios.takeIf { it.isNotEmpty() }?.joinToString(" / "),
                    if (model.supportsStreaming) "streaming" else null
                )
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun ModelPurpose.heading(): String = when (this) {
    ModelPurpose.CHAT -> "Everyday chat"
    ModelPurpose.AGENT -> "Agent"
    ModelPurpose.RESEARCH -> "Research"
    ModelPurpose.IMAGE -> "Image"
    ModelPurpose.VIDEO -> "Video"
    ModelPurpose.AUDIO -> "Audio"
}
