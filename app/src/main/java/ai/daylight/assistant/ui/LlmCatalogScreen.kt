package ai.daylight.assistant.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.data.remote.ModelBenchmark
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class LlmSort(val label: String) { COMPANY("Company"), INTELLIGENCE("Intelligence index"), ALPHABETICAL("A–Z") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmCatalogScreen(
    vm: LlmCatalogViewModel,
    onChats: () -> Unit,
    onSkills: () -> Unit,
    onSettings: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val benchmarks by vm.benchmarks.collectAsStateWithLifecycle()
    val meta by vm.benchmarkMeta.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val benchmarkNotice by vm.benchmarkNotice.collectAsStateWithLifecycle()
    var sortName by rememberSaveable { mutableStateOf(LlmSort.ALPHABETICAL.name) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var companyFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val sort = runCatching { LlmSort.valueOf(sortName) }.getOrDefault(LlmSort.ALPHABETICAL)
    val companies = remember(models) {
        models.map { providerBrandFor(it.id).name }.distinct().sorted()
    }
    val visible = filterCatalog(models, query, companyFilter)
        .let { sequence ->
            when (sort) {
                LlmSort.COMPANY -> sequence.sortedWith(compareBy<OpenRouterModel> { it.company().lowercase() }.thenBy { it.name.lowercase() })
                LlmSort.INTELLIGENCE -> sequence.sortedWith(
                    compareByDescending<OpenRouterModel> { modelBenchmark(it, benchmarks)?.intelligenceIndex ?: Double.NEGATIVE_INFINITY }
                        .thenBy { it.name.lowercase() }
                )
                LlmSort.ALPHABETICAL -> sequence.sortedBy { it.name.lowercase() }
            }
        }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Model catalog")
                        Text("${visible.size} of ${models.size} models via ${settings.chatProvider.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onChats) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, "Refresh model catalog") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("Search models or companies") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LlmSort.entries) { option ->
                    val icon = when (option) {
                        LlmSort.COMPANY -> Icons.Outlined.Business
                        LlmSort.INTELLIGENCE -> Icons.Outlined.Psychology
                        LlmSort.ALPHABETICAL -> Icons.Outlined.SortByAlpha
                    }
                    FilterChip(
                        selected = sort == option,
                        onClick = { sortName = option.name },
                        label = { Text(option.label) },
                        leadingIcon = { Icon(icon, null, Modifier.size(17.dp)) }
                    )
                }
            }
            if (companies.isNotEmpty()) {
                var companyMenuExpanded by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
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
            benchmarkNotice?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (sort == LlmSort.INTELLIGENCE && benchmarks.isNotEmpty()) {
                Text(
                    "Official Artificial Analysis scores via OpenRouter${meta?.asOf?.let { " · as of ${it.take(10)}" }.orEmpty()}",
                    Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                loading && models.isEmpty() -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null && models.isEmpty() -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                visible.isEmpty() -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) { Text("No matching models.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visible, key = { it.id }) { model ->
                        LlmCard(
                            model = model,
                            benchmark = modelBenchmark(model, benchmarks),
                            expanded = expandedId == model.id,
                            onToggle = { expandedId = if (expandedId == model.id) null else model.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LlmCard(model: OpenRouterModel, benchmark: ModelBenchmark?, expanded: Boolean, onToggle: () -> Unit) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val provider = providerBrandFor(model.id)
    Card(
        Modifier
            .fillMaxWidth()
            .animateContentSize(if (motionEnabled) tween(170) else snap())
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f) else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (expanded) 0.92f else 0.42f))
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ProviderMark(provider)
                Column(Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(provider.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                benchmark?.intelligenceIndex?.let { ScoreBadge("IQ ${it.oneDecimal()}") }
                Icon(Icons.Outlined.ExpandMore, if (expanded) "Collapse details" else "Expand details", Modifier.size(20.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriceSummary("Input", model.pricing?.prompt.tokenPrice())
                PriceSummary("Output", model.pricing?.completion.tokenPrice())
                PriceSummary("Context", model.contextLength.compactTokens())
            }
            if (expanded) {
                HorizontalDivider()
                if (model.description.isNotBlank()) Text(model.description, style = MaterialTheme.typography.bodySmall)
                DetailLine("Model ID", model.id)
                DetailLine("Company", provider.name)
                model.canonicalSlug?.takeIf { it != model.id }?.let { DetailLine("Permanent slug", it) }
                model.created?.let { DetailLine("Added", DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1_000))) }
                model.knowledgeCutoff?.let { DetailLine("Knowledge cutoff", it) }
                model.expirationDate?.let { DetailLine("Expiration", it) }
                benchmark?.let {
                    Text("Benchmarks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        it.intelligenceIndex?.let { score -> ScoreBadge("Intelligence ${score.oneDecimal()}") }
                        it.codingIndex?.let { score -> ScoreBadge("Coding ${score.oneDecimal()}") }
                        it.agenticIndex?.let { score -> ScoreBadge("Agentic ${score.oneDecimal()}") }
                    }
                    Text("Source: ${it.source ?: "OpenRouter benchmark catalog"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Architecture & capabilities", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                model.architecture?.let { architecture ->
                    architecture.modality?.let { DetailLine("Modality", it) }
                    architecture.tokenizer?.let { DetailLine("Tokenizer", it) }
                    architecture.instructType?.let { DetailLine("Instruction format", it) }
                    DetailLine("Input", architecture.inputModalities.ifEmpty { listOf("text") }.joinToString())
                    DetailLine("Output", architecture.outputModalities.ifEmpty { listOf("text") }.joinToString())
                }
                if (model.supportedParameters.isNotEmpty()) DetailLine("Parameters", model.supportedParameters.joinToString())
                model.topProvider?.let { provider ->
                    provider.maxCompletionTokens?.let { DetailLine("Maximum output", "${it.compactTokens()} tokens") }
                    provider.isModerated?.let { DetailLine("Provider moderation", if (it) "Yes" else "No") }
                }
                Text("Pricing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                DetailLine("Input tokens", model.pricing?.prompt.tokenPrice())
                DetailLine("Output tokens", model.pricing?.completion.tokenPrice())
                model.pricing?.inputCacheRead?.let { DetailLine("Cache read", it.tokenPrice()) }
                model.pricing?.inputCacheWrite?.let { DetailLine("Cache write", it.tokenPrice()) }
                model.pricing?.internalReasoning?.let { DetailLine("Internal reasoning", it.tokenPrice()) }
                model.pricing?.request?.let { DetailLine("Per request", it.unitPrice("request")) }
                model.pricing?.image?.let { DetailLine("Image", it.unitPrice("image")) }
                model.pricing?.audio?.let { DetailLine("Audio", it.unitPrice("unit")) }
                model.pricing?.webSearch?.let { DetailLine("Web search", it.unitPrice("request")) }
            }
        }
    }
}

internal data class ProviderBrand(val name: String, val monogram: String, val color: Color)

internal fun providerBrandFor(modelId: String): ProviderBrand {
    val key = modelId.substringBefore('/').trim().lowercase()
    return when (key) {
        "openai" -> ProviderBrand("OpenAI", "OA", Color(0xFF087F63))
        "anthropic" -> ProviderBrand("Anthropic", "AN", Color(0xFFA94F35))
        "google", "google-ai" -> ProviderBrand("Google", "G", Color(0xFF3367D6))
        "meta-llama", "meta" -> ProviderBrand("Meta", "M", Color(0xFF1769E0))
        "mistralai", "mistral" -> ProviderBrand("Mistral AI", "MI", Color(0xFFD95520))
        "qwen", "alibaba" -> ProviderBrand("Qwen", "QW", Color(0xFF514BC7))
        "deepseek" -> ProviderBrand("DeepSeek", "DS", Color(0xFF3456D1))
        "x-ai", "xai" -> ProviderBrand("xAI", "xAI", Color(0xFF34383F))
        "microsoft" -> ProviderBrand("Microsoft", "MS", Color(0xFF006F9E))
        "amazon", "amazon-nova" -> ProviderBrand("Amazon", "AWS", Color(0xFFF09A16))
        "cohere" -> ProviderBrand("Cohere", "CO", Color(0xFF31574D))
        "nvidia" -> ProviderBrand("NVIDIA", "NV", Color(0xFF4D7F00))
        "perplexity" -> ProviderBrand("Perplexity", "PX", Color(0xFF137B88))
        "nousresearch", "nous-research" -> ProviderBrand("Nous Research", "NR", Color(0xFF7141B1))
        "moonshotai", "moonshot" -> ProviderBrand("Moonshot AI", "KS", Color(0xFF273047))
        "minimax" -> ProviderBrand("MiniMax", "MM", Color(0xFFC23D47))
        "z-ai", "zhipu" -> ProviderBrand("Z.ai", "Z", Color(0xFF2868C7))
        "tencent" -> ProviderBrand("Tencent", "TC", Color(0xFF0052D9))
        "baidu" -> ProviderBrand("Baidu", "BD", Color(0xFF2932E1))
        "iflytek" -> ProviderBrand("iFlytek", "IF", Color(0xFF1A73E8))
        "bytedance", "byteplus", "doubao" -> ProviderBrand("Doubao", "DB", Color(0xFF3B82F6))
        else -> {
            val name = key
                .split('-', '_')
                .filter(String::isNotBlank)
                .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                .ifBlank { "Independent" }
            val monogram = name.split(' ')
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
                .ifBlank { "AI" }
            ProviderBrand(name, monogram, Color(0xFF525866))
        }
    }
}

@Composable
internal fun ProviderMark(brand: ProviderBrand, modifier: Modifier = Modifier) {
    val foreground = providerMarkForeground(brand.color)
    Surface(
        modifier = modifier
            .size(40.dp)
            .clearAndSetSemantics { contentDescription = "${brand.name} provider" },
        shape = RoundedCornerShape(12.dp),
        color = brand.color,
        contentColor = foreground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                brand.monogram,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/** Selects whichever monochrome foreground has the stronger WCAG contrast ratio. */
internal fun providerMarkForeground(background: Color): Color {
    val luminance = background.luminance().toDouble()
    val blackContrast = (luminance + 0.05) / 0.05
    val whiteContrast = 1.05 / (luminance + 0.05)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

@Composable
private fun PriceSummary(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScoreBadge(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(0.34f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.66f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun OpenRouterModel.company(): String = providerBrandFor(id).name

internal fun filterCatalog(
    models: List<OpenRouterModel>,
    query: String,
    company: String?
): List<OpenRouterModel> = models.filter { model ->
    val brand = providerBrandFor(model.id)
    val matchesQuery = query.isBlank() ||
        model.name.contains(query, true) ||
        model.id.contains(query, true) ||
        brand.name.contains(query, true)
    val matchesCompany = company == null || brand.name == company
    matchesQuery && matchesCompany
}

private fun modelBenchmark(model: OpenRouterModel, values: Map<String, ModelBenchmark>): ModelBenchmark? =
    model.canonicalSlug?.let(values::get) ?: values[model.id] ?: values[model.id.substringBefore(':')]

private fun Int?.compactTokens(): String = when {
    this == null -> "Not listed"
    this >= 1_000_000 -> String.format(Locale.US, "%.1fM", this / 1_000_000.0)
    this >= 1_000 -> "${this / 1_000}k"
    else -> toString()
}

private fun String?.tokenPrice(): String {
    val perMillion = this?.toDoubleOrNull()?.times(1_000_000) ?: return "Not listed"
    return if (perMillion == 0.0) "Free" else if (perMillion < 0.01) String.format(Locale.US, "$%.4f/M", perMillion) else String.format(Locale.US, "$%.2f/M", perMillion)
}

private fun String.unitPrice(unit: String): String {
    val amount = toDoubleOrNull() ?: return this
    return if (amount == 0.0) "Free" else String.format(Locale.US, "$%.4f/%s", amount, unit)
}

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
