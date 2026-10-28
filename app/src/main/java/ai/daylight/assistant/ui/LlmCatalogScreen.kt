package ai.daylight.assistant.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.data.remote.ModelBenchmark
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPill
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTag
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotProgress
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.tune.TuneCaption
import ai.daylight.assistant.ui.tune.TuneChoiceRow
import ai.daylight.assistant.ui.tune.TuneDetail
import ai.daylight.assistant.ui.tune.TuneEmpty
import ai.daylight.assistant.ui.tune.TunePage
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class LlmSort(val label: String) { COMPANY("Company"), INTELLIGENCE("Intelligence"), ALPHABETICAL("A–Z") }

/**
 * The model catalog: every text model the key can use, searchable, sortable by company,
 * intelligence index or name, and filterable by company. Tap a card to unfold its details.
 */
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

    TunePage(
        "Catalog",
        onChats,
        scrollable = false,
        actions = {
            DotIconButton(R.drawable.ic_kz_retry, "Refresh model catalog", vm::refresh, tint = DotTheme.c.textSecondary)
        }
    ) {
        TuneCaption("${visible.size} of ${models.size} models via ${settings.chatProvider.label}", Modifier.padding(bottom = 10.dp))
        DotField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = "Search models or companies",
            leadingIcon = R.drawable.ic_kz_search
        )
        LazyRow(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (companies.isNotEmpty()) {
                item { CompanyFilter(companies, companyFilter) { companyFilter = it } }
            }
            items(LlmSort.entries) { option ->
                DotPill(option.label, selected = sort == option, onClick = { sortName = option.name }, role = Role.Tab)
            }
        }
        benchmarkNotice?.let { TuneDetail(it, Modifier.padding(top = 2.dp)) }
        if (sort == LlmSort.INTELLIGENCE && benchmarks.isNotEmpty()) {
            TuneDetail(
                "Official Artificial Analysis scores via OpenRouter${meta?.asOf?.let { ", as of ${it.take(10)}" }.orEmpty()}.",
                Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                loading && models.isEmpty() -> ModelsLoading()
                error != null && models.isEmpty() -> ModelsEmpty(error.orEmpty(), onRetry = vm::refresh)
                visible.isEmpty() -> ModelsEmpty("No model matches that search or filter.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
    val c = DotTheme.c
    val motionEnabled = LocalKryzzMotionEnabled.current
    val provider = providerBrandFor(model.id)
    ModelCardFrame(
        selected = false,
        onClick = onToggle,
        modifier = Modifier
            .animateContentSize(if (motionEnabled) tween(170) else snap())
            .semantics { stateDescription = if (expanded) "Details shown" else "Details hidden" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderMark(provider)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ModelName(model.name)
                Text(provider.name.uppercase(Locale.US), style = DotType.Caption, color = c.textTertiary, maxLines = 1)
            }
            benchmark?.intelligenceIndex?.let { DotTag("IQ ${it.oneDecimal()}", color = c.textPrimary) }
            Icon(
                painterResource(if (expanded) R.drawable.ic_kz_chevron_up else R.drawable.ic_kz_chevron_down),
                if (expanded) "Collapse details" else "Expand details",
                Modifier.padding(start = 8.dp).size(18.dp),
                tint = c.textTertiary
            )
        }
        SpecLine(
            "IN ${model.pricing?.prompt.tokenPrice()}",
            "OUT ${model.pricing?.completion.tokenPrice()}",
            "CTX ${model.contextLength.compactTokens()}"
        )
        if (expanded) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = c.border)
            if (model.description.isNotBlank()) Text(model.description, style = DotType.BodySmall, color = c.textSecondary)
            DetailLine("Model ID", model.id)
            DetailLine("Company", provider.name)
            model.canonicalSlug?.takeIf { it != model.id }?.let { DetailLine("Permanent slug", it) }
            model.created?.let { DetailLine("Added", DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1_000))) }
            model.knowledgeCutoff?.let { DetailLine("Knowledge cutoff", it) }
            model.expirationDate?.let { DetailLine("Expiration", it) }
            benchmark?.let {
                DetailHeading("Benchmarks")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    it.intelligenceIndex?.let { score -> DotTag("Intelligence ${score.oneDecimal()}") }
                    it.codingIndex?.let { score -> DotTag("Coding ${score.oneDecimal()}") }
                    it.agenticIndex?.let { score -> DotTag("Agentic ${score.oneDecimal()}") }
                }
                Text("Source: ${it.source ?: "OpenRouter benchmark catalog"}", style = DotType.BodySmall, color = c.textTertiary)
            }
            DetailHeading("Architecture and capabilities")
            model.architecture?.let { architecture ->
                architecture.modality?.let { DetailLine("Modality", it) }
                architecture.tokenizer?.let { DetailLine("Tokenizer", it) }
                architecture.instructType?.let { DetailLine("Instruction format", it) }
                DetailLine("Input", architecture.inputModalities.ifEmpty { listOf("text") }.joinToString())
                DetailLine("Output", architecture.outputModalities.ifEmpty { listOf("text") }.joinToString())
            }
            if (model.supportedParameters.isNotEmpty()) DetailLine("Parameters", model.supportedParameters.joinToString())
            model.topProvider?.let { top ->
                top.maxCompletionTokens?.let { DetailLine("Maximum output", "${it.compactTokens()} tokens") }
                top.isModerated?.let { DetailLine("Provider moderation", if (it) "Yes" else "No") }
            }
            DetailHeading("Pricing")
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

/**
 * Kit model card (§12): surface, 24dp corners, 16dp padding, a 1dp border that turns white
 * with a check at the top right when the model is the selected one.
 */
@Composable
internal fun ModelCardFrame(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DotTheme.c
    Box(
        modifier
            .fillMaxWidth()
            .clip(DotRadius.Card)
            .background(c.surface)
            .border(1.dp, if (selected) c.textPrimary else c.border, DotRadius.Card)
            .clickable(role = role, onClick = onClick)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        if (selected) {
            Icon(
                painterResource(R.drawable.ic_kz_check), "Selected",
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(18.dp),
                tint = c.textPrimary
            )
        }
    }
}

/** A model's name in a card: Geist 500 16. */
@Composable
internal fun ModelName(name: String, modifier: Modifier = Modifier) {
    Text(
        name,
        modifier,
        style = DotType.Title.copy(fontSize = 16.sp, lineHeight = 21.sp),
        color = DotTheme.c.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** The kit spec row: mono stat pairs joined by dots, `CTX 128K · IN $0.15 · OUT $0.60`. */
@Composable
internal fun SpecLine(vararg parts: String) {
    val text = parts.filter { it.isNotBlank() }.joinToString("  ·  ")
    if (text.isNotEmpty()) Text(text.uppercase(Locale.US), style = DotType.Data, color = DotTheme.c.textSecondary)
}

/**
 * The company filter: a pill naming the current filter that opens a sheet of companies.
 * The test tags are the ones the old dropdown had.
 */
@Composable
internal fun CompanyFilter(companies: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val c = DotTheme.c
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .testTag("company_filter_button")
            .clip(DotRadius.Pill)
            .clickable(role = Role.Button, onClickLabel = "Filter by company") { open = true }
            .height(40.dp)
            .border(1.dp, if (selected != null) c.textPrimary else c.border, DotRadius.Pill)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(painterResource(R.drawable.ic_kz_filter), null, Modifier.size(16.dp), tint = c.textSecondary)
        Text(
            (selected ?: "All companies").uppercase(Locale.US),
            Modifier.semantics { contentDescription = "Filter: ${selected ?: "all companies"}" },
            style = DotType.Label,
            color = if (selected != null) c.textPrimary else c.textSecondary,
            maxLines = 1
        )
        Icon(painterResource(R.drawable.ic_kz_chevron_down), null, Modifier.size(14.dp), tint = c.textTertiary)
    }
    if (open) {
        DotSheet(onDismiss = { open = false }) {
            SheetLabel("Company")
            TuneChoiceRow("All companies", null, selected == null, {
                onSelect(null)
                open = false
            }, Modifier.testTag("company_filter_all"))
            companies.forEach { company ->
                TuneChoiceRow(company, null, selected == company, {
                    onSelect(company)
                    open = false
                }, Modifier.testTag("company_filter_$company"))
            }
        }
    }
}

/** Indeterminate dots while the catalog loads (no fake percentage). */
@Composable
internal fun ModelsLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DotProgress(progress = null, count = 12)
            TuneCaption("Loading models", Modifier.padding(top = 12.dp))
        }
    }
}

/** The kit empty state for a model list, with Retry when it can help. */
@Composable
internal fun ModelsEmpty(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TuneEmpty(
            dotIcon = R.drawable.ic_kz_model_dot,
            words = "No models",
            body = message,
            action = if (onRetry != null) "Retry" else null,
            onAction = onRetry,
            actionIcon = R.drawable.ic_kz_retry
        )
    }
}

/** Provider identity: name and monogram only. Marks are monochrome (kit rule: yellow is the only accent). */
internal data class ProviderBrand(val name: String, val monogram: String)

internal fun providerBrandFor(modelId: String): ProviderBrand {
    val key = modelId.substringBefore('/').trim().lowercase()
    return when (key) {
        "openai" -> ProviderBrand("OpenAI", "OA")
        "anthropic" -> ProviderBrand("Anthropic", "AN")
        "google", "google-ai" -> ProviderBrand("Google", "G")
        "meta-llama", "meta" -> ProviderBrand("Meta", "M")
        "mistralai", "mistral" -> ProviderBrand("Mistral AI", "MI")
        "qwen", "alibaba" -> ProviderBrand("Qwen", "QW")
        "deepseek" -> ProviderBrand("DeepSeek", "DS")
        "x-ai", "xai" -> ProviderBrand("xAI", "xAI")
        "microsoft" -> ProviderBrand("Microsoft", "MS")
        "amazon", "amazon-nova" -> ProviderBrand("Amazon", "AWS")
        "cohere" -> ProviderBrand("Cohere", "CO")
        "nvidia" -> ProviderBrand("NVIDIA", "NV")
        "perplexity" -> ProviderBrand("Perplexity", "PX")
        "nousresearch", "nous-research" -> ProviderBrand("Nous Research", "NR")
        "moonshotai", "moonshot" -> ProviderBrand("Moonshot AI", "KS")
        "minimax" -> ProviderBrand("MiniMax", "MM")
        "z-ai", "zhipu" -> ProviderBrand("Z.ai", "Z")
        "tencent" -> ProviderBrand("Tencent", "TC")
        "baidu" -> ProviderBrand("Baidu", "BD")
        "iflytek" -> ProviderBrand("iFlytek", "IF")
        "bytedance", "byteplus", "doubao" -> ProviderBrand("Doubao", "DB")
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
            ProviderBrand(name, monogram)
        }
    }
}

/** A provider's monogram in a 40dp raised tile with a 1dp border, Space Mono bold. */
@Composable
internal fun ProviderMark(brand: ProviderBrand, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Box(
        modifier
            .size(40.dp)
            .background(c.surfaceRaised, RoundedCornerShape(12.dp))
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .clearAndSetSemantics { contentDescription = "${brand.name} provider" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            brand.monogram,
            style = DotType.Label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = c.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun DetailHeading(text: String) {
    Text(text.uppercase(Locale.US), Modifier.padding(top = 6.dp), style = DotType.Label, color = DotTheme.c.textSecondary)
}

@Composable
private fun DetailLine(label: String, value: String) {
    val c = DotTheme.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label.uppercase(Locale.US), Modifier.weight(0.36f), style = DotType.Caption.copy(fontSize = 10.sp), color = c.textTertiary)
        Text(value, Modifier.weight(0.64f), style = DotType.BodySmall, color = c.textPrimary)
    }
}

/** A capability label as a mono tag; "Tool calling" gets the tools icon. */
@Composable
internal fun CapabilityTag(label: String) {
    if (label == "Tool calling") {
        DotTag("Tools", icon = R.drawable.ic_kz_tools)
    } else {
        DotTag(label)
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

internal fun Int?.compactTokens(): String = when {
    this == null -> "Not listed"
    this >= 1_000_000 -> String.format(Locale.US, "%.1fM", this / 1_000_000.0)
    this >= 1_000 -> "${this / 1_000}k"
    else -> toString()
}

internal fun String?.tokenPrice(): String {
    val perMillion = this?.toDoubleOrNull()?.times(1_000_000) ?: return "Not listed"
    return if (perMillion == 0.0) "Free" else if (perMillion < 0.01) String.format(Locale.US, "$%.4f/M", perMillion) else String.format(Locale.US, "$%.2f/M", perMillion)
}

private fun String.unitPrice(unit: String): String {
    val amount = toDoubleOrNull() ?: return this
    return if (amount == 0.0) "Free" else String.format(Locale.US, "$%.4f/%s", amount, unit)
}

private fun Double.oneDecimal(): String = String.format(Locale.US, "%.1f", this)
