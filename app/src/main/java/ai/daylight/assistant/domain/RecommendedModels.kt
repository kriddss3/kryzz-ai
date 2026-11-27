package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.OpenRouterModel

/**
 * The six models pinned at the top of the Models board. Each slot is the newest match
 * in the live catalog, so a newer Sonnet or Gemini Flash takes the row when it appears.
 * A slot with no match is left out rather than filled with a neighbouring model.
 */
internal object RecommendedModels {
    fun pick(models: List<OpenRouterModel>): List<OpenRouterModel> =
        Family.entries.mapNotNull { family -> latestOf(models.filter { family.matches(it) }) }

    private enum class Family {
        DEEPSEEK_FLASH,
        SONNET,
        HAIKU,
        OPUS,
        MIMO_FLASH,
        GEMINI_FLASH;

        fun matches(model: OpenRouterModel): Boolean {
            if (!model.speaksText()) return false
            val hay = "${model.id} ${model.name}".lowercase()
            if (isSideModel(hay)) return false
            return when (this) {
                DEEPSEEK_FLASH -> "deepseek" in hay && "flash" in hay
                SONNET -> "sonnet" in hay && ("claude" in hay || "anthropic" in hay)
                HAIKU -> "haiku" in hay && ("claude" in hay || "anthropic" in hay)
                OPUS -> "opus" in hay && ("claude" in hay || "anthropic" in hay)
                MIMO_FLASH -> "mimo" in hay && "flash" in hay
                GEMINI_FLASH -> "gemini" in hay && "flash" in hay && "lite" !in hay
            }
        }
    }
}

/**
 * Recommended, then saved, then everything else. A model is listed once: a favourite
 * that is also recommended stays in the recommended block.
 */
internal data class TextModelBoard(
    val recommended: List<OpenRouterModel>,
    val saved: List<OpenRouterModel>,
    val rest: List<OpenRouterModel>,
)

internal fun textModelBoard(models: List<OpenRouterModel>, favorites: Set<String>): TextModelBoard {
    val recommended = RecommendedModels.pick(models)
    val recommendedIds = recommended.mapTo(mutableSetOf()) { it.id }
    val saved = models
        .filter { it.id in favorites && it.id !in recommendedIds }
        .sortedBy { it.name.lowercase() }
    val savedIds = saved.mapTo(mutableSetOf()) { it.id }
    val rest = models
        .filter { it.id !in recommendedIds && it.id !in savedIds }
        .sortedBy { it.name.lowercase() }
    return TextModelBoard(recommended, saved, rest)
}

private fun latestOf(models: List<OpenRouterModel>): OpenRouterModel? {
    if (models.isEmpty()) return null
    val representatives = models
        .groupBy { it.id.substringBefore(':') }
        .values
        .map { variants ->
            val baseId = variants.first().id.substringBefore(':')
            variants.firstOrNull { it.id == baseId } ?: variants.reduce(::newer)
        }
    return representatives.reduce(::newer)
}

/** Higher generation first. Same generation prefers the plain id over a dated pin, a preview, or a route suffix. */
private fun newer(a: OpenRouterModel, b: OpenRouterModel): OpenRouterModel {
    val version = compareVersions(modelVersion(a), modelVersion(b))
    if (version != 0) return if (version > 0) a else b
    val canon = canonicalScore(a.id).compareTo(canonicalScore(b.id))
    if (canon != 0) return if (canon < 0) a else b
    val length = a.id.length.compareTo(b.id.length)
    if (length != 0) return if (length < 0) a else b
    return if ((a.created ?: 0L) >= (b.created ?: 0L)) a else b
}

private fun compareVersions(a: List<Int>, b: List<Int>): Int {
    val width = maxOf(a.size, b.size)
    for (index in 0 until width) {
        val difference = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
        if (difference != 0) return difference
    }
    return 0
}

private fun modelVersion(model: OpenRouterModel): List<Int> {
    var slug = "${model.id} ${model.name}".lowercase()
    slug = slug.replace(Regex("-?20\\d{6}\\b"), "")
    slug = Regex("(?<!\\d)(\\d{1,2})-(\\d{1,2})(?!\\d)").replace(slug) { "${it.groupValues[1]}.${it.groupValues[2]}" }
    val dotted = Regex("(?<!\\d)(\\d{1,2}(?:\\.\\d{1,2})+)(?!\\d)").find(slug)?.value
    val raw = dotted ?: Regex("(?<!\\d)(\\d{1,2})(?!\\d)").find(slug)?.value
    return raw?.split('.')?.map(String::toInt).orEmpty()
}

private fun canonicalScore(id: String): Int {
    val slug = id.lowercase()
    var score = 0
    if (':' in slug) score += 4
    if (Regex("20\\d{6}").containsMatchIn(slug)) score += 2
    if ("preview" in slug || "-exp" in slug || "experimental" in slug) score += 1
    if ("lite" in slug) score += 1
    return score
}

private fun isSideModel(hay: String): Boolean {
    val blocked = listOf("image", "tts", "embed", "audio", "speech", "transcrib", "moderation", "whisper")
    return blocked.any { it in hay }
}

private fun OpenRouterModel.speaksText(): Boolean {
    val outputs = architecture?.outputModalities.orEmpty()
    return outputs.isEmpty() || outputs.any { it.equals("text", ignoreCase = true) }
}
