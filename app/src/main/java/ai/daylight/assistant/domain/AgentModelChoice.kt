package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.OpenRouterModel

/**
 * v5.10 default models for the agent, research and Max slots, and the tool-aware choice
 * that replaces a slot's model when the live catalog no longer lists it.
 *
 * Defaults chosen on 2026-09-28 from OpenRouter's model pages (prices per million tokens,
 * input / output; seen through search summaries because openrouter.ai was not reachable
 * from the build environment, so re-check them against /api/v1/models before a release):
 *
 *  - Agent and research: google/gemini-3.8-flash, $0.75 / $3.75, cache reads $0.075,
 *    1M context, "tools" and "tool_choice" listed. Gemini Flash has a long record of
 *    reliable multi-step function calling, the long context suits research turns, and
 *    Gemini 2.5+ caches repeated prefixes on its own. The old default openai/gpt-4o-mini
 *    ($0.15 / $0.60) is cheaper per token but often stops after one tool call.
 *  - Max: anthropic/claude-sonnet-5, $2 / $10, cache reads $0.20, 1M context, "tools" and
 *    "tool_choice" listed. Strong at long agentic runs, and the cache_control breakpoints
 *    in [PromptLayout] make its repeated prefix cost a tenth of the input price.
 *
 * Chat stays on openai/gpt-4o-mini: everyday chat offers no tools, so its weakness at
 * multi-step tool use does not matter there. MiniMax keeps MiniMax-M3 for every slot.
 */
internal object AgentModelChoice {

    const val OPENROUTER_AGENT_DEFAULT = "google/gemini-3.8-flash"
    const val OPENROUTER_RESEARCH_DEFAULT = "google/gemini-3.8-flash"
    const val OPENROUTER_MAX_DEFAULT = "anthropic/claude-sonnet-5"

    /** The pre-5.10 default for every OpenRouter slot; weak at chaining tool calls. */
    const val LEGACY_AGENT_MODEL = "openai/gpt-4o-mini"

    /** The tool parameter name in OpenRouter's supported_parameters. */
    const val TOOLS_PARAMETER = "tools"

    /**
     * Known-good tool-capable models, tried in order when a slot's configured default is
     * missing from the live catalog. Each still has to be in the catalog (and list "tools"
     * when the catalog reports parameters), so an ID that disappears is simply skipped.
     */
    val TOOL_CAPABLE_FALLBACKS = listOf(
        "google/gemini-3.8-flash",
        "anthropic/claude-haiku-4.5",
        "openai/gpt-5-mini",
        "moonshotai/kimi-k2.6",
        "deepseek/deepseek-v3.2"
    )

    /** Max slot: the strongest known picks first, then the everyday tool-capable list. */
    val MAX_FALLBACKS = listOf(
        "anthropic/claude-sonnet-5",
        "openai/gpt-6-sol"
    ) + TOOL_CAPABLE_FALLBACKS

    /**
     * IDs in [models] whose supported parameters include "tools", or null when the catalog
     * reports no parameters at all (MiniMax's /v1/models): then tool support is unknown and
     * the choice falls back to the pre-5.10 rule instead of treating every model as unusable.
     */
    fun toolCapableIds(models: List<OpenRouterModel>): Set<String>? {
        if (models.none { it.supportedParameters.isNotEmpty() }) return null
        return models.filter { TOOLS_PARAMETER in it.supportedParameters }.mapTo(mutableSetOf()) { it.id }
    }

    /**
     * Picks the model for a slot from the live catalog [available]:
     *
     *  1. [current], when the catalog still lists it. A stored choice is never replaced
     *     just because it lacks tools; the Agent screen warns about that instead.
     *  2. [preferred] (the slot's configured default).
     *  3. With [toolCapable] known: the first of [candidates] that is listed and tool-capable,
     *     then the alphabetically first tool-capable model in the catalog.
     *  4. The alphabetically first model, so a catalog with no tool metadata (or no
     *     tool-capable model at all) still yields a working selection.
     *
     * Returns null for an empty catalog, which is not authoritative (a network failure must
     * not erase a working preference).
     */
    fun choose(
        current: String?,
        available: Set<String>,
        preferred: String,
        toolCapable: Set<String>? = null,
        candidates: List<String> = emptyList()
    ): String? {
        if (available.isEmpty()) return null
        current?.takeIf { it in available }?.let { return it }
        fun usable(id: String) = id in available && (toolCapable == null || id in toolCapable)
        preferred.takeIf { it.isNotBlank() && usable(it) }?.let { return it }
        if (toolCapable != null) {
            candidates.firstOrNull(::usable)?.let { return it }
            available.filter { it in toolCapable }.minOrNull()?.let { return it }
        }
        return preferred.takeIf { it in available } ?: available.minOrNull()
    }

    /**
     * True only when the catalog positively says [modelId] lacks tool calling: the model is
     * listed, the catalog reports parameters, and "tools" is not among them. Unknown models
     * and catalogs without parameter data give false, so the warning never guesses.
     */
    fun knownToLackTools(modelId: String, catalog: List<OpenRouterModel>): Boolean {
        val capable = toolCapableIds(catalog) ?: return false
        return catalog.any { it.id == modelId } && modelId !in capable
    }

    /**
     * The one-time "switch to a stronger agent model" suggestion: OpenRouter only, the stored
     * agent model is still exactly the old default, and the user has not answered it yet.
     */
    fun shouldSuggestUpgrade(provider: ChatProvider, agentModel: String, answered: Boolean): Boolean =
        !answered && provider == ChatProvider.OPENROUTER && agentModel == LEGACY_AGENT_MODEL
}
