package ai.daylight.assistant.domain

/**
 * v5.10 quality presets for Agent mode. One persisted choice trades speed and spend for
 * depth without the user tuning model, reasoning, step budget, review and cost cap one by
 * one. Chat mode and voice chat never read it.
 */
enum class AgentQuality(val label: String, val summary: String) {
    FAST("Fast", "Quick answers: low reasoning, up to 8 tool rounds, no review pass."),
    BALANCED("Balanced", "Your agent model with your own reasoning, step budget and review settings."),
    MAX("Max", "Your Max model, high reasoning, 24 tool rounds and a review pass. Costs more per turn.");

    companion object {
        val DEFAULT = BALANCED
        fun from(value: String?): AgentQuality = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * v5.10: maps an [AgentQuality] and the user's settings to what one agent turn runs with.
 * Pure; [AgentExecutor] and [SwarmOrchestrator] only read the result.
 */
internal object AgentQualityPolicy {

    /** Fast caps the step budget here (or lower, when the user's own budget is lower). */
    const val FAST_STEP_BUDGET = 8

    /** Max always gets the full budget. */
    const val MAX_STEP_BUDGET = AgentTurnPolicy.MAX_STEP_BUDGET

    /** Max raises a lower cost cap to this (US cents); a cap switched Off stays Off. */
    const val MAX_COST_CAP_CENTS = 100

    data class UserSettings(
        val agentModel: String,
        val researchModel: String,
        /** The optional Max slot; blank falls back to the agent model. */
        val maxModel: String,
        /** Explicit per-model reasoning choices (AUTO entries are never stored). */
        val modelReasoning: Map<String, ReasoningEffort>,
        val stepBudget: Int,
        val reviewAnswers: Boolean,
        /** 0 means the cap is off. */
        val costCapCents: Int
    )

    data class TurnSettings(
        val model: String,
        /** AUTO means "send no reasoning field" and leaves the provider default in place. */
        val reasoning: ReasoningEffort,
        val stepBudget: Int,
        val review: Boolean,
        val costCapCents: Int
    )

    /**
     * [research] selects the research slot (Deep search, Wide search) for Fast and Balanced.
     * Max uses the Max slot for every agent turn, research included, because it is the user's
     * strongest pick; a blank Max slot falls back to the agent model, which is also what the
     * header and AI controls show for it.
     *
     * A reasoning level the user chose for the resolved model (Models screen or AI controls)
     * always wins over the preset's level, including an explicit Off. The preset levels are
     * LOW and HIGH, the two efforts every reasoning model on OpenRouter accepts or maps to its
     * nearest level; OpenRouter ignores the field for models without reasoning, so a preset
     * never makes a request fail.
     */
    fun resolve(quality: AgentQuality, user: UserSettings, research: Boolean): TurnSettings {
        val baseModel = if (research) user.researchModel else user.agentModel
        val budget = user.stepBudget.coerceIn(AgentTurnPolicy.MIN_STEP_BUDGET, AgentTurnPolicy.MAX_STEP_BUDGET)
        val cap = user.costCapCents.coerceAtLeast(0)
        return when (quality) {
            AgentQuality.FAST -> TurnSettings(
                model = baseModel,
                reasoning = user.modelReasoning[baseModel] ?: ReasoningEffort.LOW,
                stepBudget = minOf(budget, FAST_STEP_BUDGET),
                review = false,
                costCapCents = cap
            )
            AgentQuality.BALANCED -> TurnSettings(
                model = baseModel,
                reasoning = user.modelReasoning[baseModel] ?: ReasoningEffort.AUTO,
                stepBudget = budget,
                review = user.reviewAnswers,
                costCapCents = cap
            )
            AgentQuality.MAX -> {
                val model = user.maxModel.trim().ifBlank { user.agentModel }
                TurnSettings(
                    model = model,
                    reasoning = user.modelReasoning[model] ?: ReasoningEffort.HIGH,
                    stepBudget = MAX_STEP_BUDGET,
                    review = true,
                    costCapCents = if (cap == 0) 0 else maxOf(cap, MAX_COST_CAP_CENTS)
                )
            }
        }
    }
}
