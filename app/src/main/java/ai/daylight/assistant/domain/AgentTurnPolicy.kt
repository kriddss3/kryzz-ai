package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ToolDefinition

/**
 * v5.7 agent-loop redesign — the AUTO orchestration policy as a pure state machine.
 *
 * Up to 5.6.12 the loop decided what to do next by regex-matching the model's *prose*
 * (the old AgentLoopPolicy decision tree): "let me search…" invented a search the model
 * never asked for, and one blank/short round after a tool call permanently stripped every
 * tool (FORCE_FINAL_ANSWER), which is exactly why Auto could not chain multiple steps.
 *
 * The new contract is behavioural, not textual — the model drives the loop:
 *
 *  - model returned tool calls  -> run them, feed results back, offer tools again
 *  - model returned text        -> that text IS the final answer (no completeness regex)
 *  - model narrated a tool on round 0 without calling it -> ONE re-ask ([NUDGE_DIRECTIVE])
 *  - tool budget exhausted, or blank text after tool rounds -> exactly ONE terminal
 *    no-tools round so the turn still ends with a written answer instead of a limit notice
 *
 * v5.8 tool offering: cheap tools whose effects stay on the phone (time, calculate,
 * weather, create_artifact, update_plan) are offered every agent round instead of only when
 * a keyword matched, and fetch_url is offered as soon as any readable URL is known (see
 * [FetchAllowlist]), so a search can be followed by reading its results. Keyword gates
 * remain only for slow or costly tools (image / video / music) and tools with side effects
 * (schedule, skill, code). A forced first search no longer withholds the other tools: the
 * executor forces it with a named tool_choice, so offering them does not dilute the call.
 *
 * v5.9 room to work: the small round cap (6, hard maximum 8) became a step budget
 * (default [DEFAULT_STEP_BUDGET], range [MIN_STEP_BUDGET]..[MAX_STEP_BUDGET]) plus a per-turn
 * cost cap ([costCapReached]). Hitting either one moves the turn into the same terminal
 * no-tools round, so it still ends with a written answer.
 *
 * Everything here is pure and unit-tested; [AgentExecutor] only maps the planned tool
 * names to its [ToolDefinition] builders and executes the transitions.
 */
internal object AgentTurnPolicy {

    const val SEARCH_PAST_CHATS = "search_past_chats"
    const val PARALLEL_SEARCH = "parallel_search"
    const val CREATE_ARTIFACT = "create_artifact"
    const val CREATE_SKILL = "create_skill"
    const val CREATE_CODE_PROJECT = "create_code_project"
    const val GENERATE_IMAGE = "generate_image"
    const val GENERATE_VIDEO = "generate_video"
    const val GENERATE_AUDIO = "generate_audio"
    const val GET_CURRENT_TIME = "get_current_time"
    const val CALCULATE = "calculate"
    const val GET_WEATHER = "get_weather"
    const val FETCH_URL = "fetch_url"
    const val REMEMBER_FACT = "remember_fact"
    const val RECALL_MEMORIES = "recall_memories"
    const val SCHEDULE_TASK = "schedule_task"
    const val ASK_USER = "ask_user"
    const val UPDATE_PLAN = "update_plan"

    /**
     * Rounds whose only calls are update_plan do not spend the tool budget, up to this many
     * per turn: keeping the checklist current must not starve the real work of rounds. Past
     * the cap they count normally, so a model stuck re-planning still hits the terminal round.
     */
    const val FREE_PLAN_ROUNDS = 3

    /** v5.9: tool rounds per turn (the "step budget"), set in Settings within this range. */
    const val DEFAULT_STEP_BUDGET = 16
    const val MIN_STEP_BUDGET = 4
    const val MAX_STEP_BUDGET = 24

    /** The pre-5.9 default of the old "Maximum tool rounds" slider. */
    const val LEGACY_DEFAULT_TOOL_ROUNDS = 6

    /** v5.9: default per-turn cost cap in US cents; 0 means the cap is off. */
    const val DEFAULT_COST_CAP_CENTS = 25

    /**
     * Cost cap fallback for providers that report tokens but no cost (MiniMax today): the cap
     * is converted to a token allowance at this rate, which assumes a blended price of
     * $0.50 per million tokens. The default $0.25 cap therefore allows 500k tokens per turn.
     * Prompt tokens are billed again every round, so the running total tracks spend closely.
     */
    const val TOKENS_PER_USD_WITHOUT_COST = 2_000_000L

    /** One-shot re-ask when the model described a tool call instead of invoking it. */
    const val NUDGE_DIRECTIVE =
        "You described a tool call in prose but did not invoke it. Call the tool now through " +
            "the function-calling interface. Do not say you will call it — call it."

    /**
     * One-shot re-ask when the user asked for a file (document / spreadsheet / database /
     * PDF) and the model answered with prose instead of calling create_artifact. Without
     * this, a capable model that simply wrote the table out in chat ended the turn with
     * no deliverable — the "Auto can't make Excel/Word/PDF files" report.
     */
    const val ARTIFACT_NUDGE_DIRECTIVE =
        "The user asked for a file deliverable and none was created. Call create_artifact " +
            "now with the complete content through the function-calling interface. Do not " +
            "paste the document, table, or SQL into the chat as text — return it via the tool."

    /** Appended once before the terminal no-tools round so the turn ends with a real answer. */
    const val TERMINAL_DIRECTIVE =
        "You just ran tools and have the results above. Now answer the user's original question " +
            "in full using those results. Do not call any more tools, do not say you cannot " +
            "answer, and do not repeat the tool output verbatim — write a clear, complete reply."

    /**
     * v5.9: the terminal directive used when the per-turn cost cap stopped the tools. The
     * executor appends [costCapNote] to the answer itself, so the model is not asked to.
     */
    const val COST_CAP_DIRECTIVE =
        "The per-turn cost cap in Settings has been reached, so no more tools can run. Answer " +
            "the user's original question now in full, using the results above. Where a part of " +
            "the request still needed more lookups, say briefly what is missing. Do not call any tools."

    /** Turn-fixed facts about the request and settings (computed once per turn). */
    data class ToolContext(
        val agentMode: Boolean,
        val voiceMode: Boolean,
        val capabilityAuto: Boolean,
        val artifactCapability: Boolean,
        val skillCapability: Boolean,
        val codeCapability: Boolean,
        val deepResearch: Boolean,
        val wideSearch: Boolean,
        val searchDepth: Int,
        /** Settings allow web search and a Parallel key exists. */
        val searchAvailable: Boolean,
        val searchRoundCap: Int,
        /** v5.9: tool rounds allowed this turn (Settings step budget). */
        val stepBudget: Int,
        /** AUTO fresh-info gate: force the first parallel_search round. */
        val forceSearchFirst: Boolean,
        val offerMediaImage: Boolean,
        val offerMediaVideo: Boolean,
        val offerMediaAudio: Boolean,
        val offerSkillAuto: Boolean,
        val offerCodeAuto: Boolean,
        val memoryEnabled: Boolean,
        /** The key-free public web client (weather, page fetch) is wired in. */
        val publicWebAvailable: Boolean,
        /** At least one URL is readable under [FetchAllowlist], or the user asked to read a page. */
        val offerFetch: Boolean,
        val offerSchedule: Boolean
    )

    /** Mutable per-turn position, rebuilt from the executor's bookkeeping each round. */
    data class TurnProgress(
        val toolRounds: Int = 0,
        val searchRounds: Int = 0,
        val nudged: Boolean = false,
        val terminalStarted: Boolean = false,
        val outputKinds: Set<OutputKind> = emptySet(),
        val skillCreated: Boolean = false,
        val didSearch: Boolean = false,
        /** v5.9: the per-turn cost cap was reached; the next round is the terminal one. */
        val costCapped: Boolean = false
    )

    data class RoundPlan(
        val toolNames: List<String>,
        /** Non-null when the model must call this tool (named choice on MiniMax, REQUIRED elsewhere). */
        val forcedTool: String?,
        /** Terminal round: no tools offered; whatever text comes back ends the turn. */
        val terminal: Boolean,
        /** Set only on the transition INTO the terminal round; null while already terminal. */
        val terminalDirective: String?
    )

    enum class After {
        /** Execute the returned calls and loop. */
        RUN_CALLS,
        /** Model narrated a tool on round 0 without calling it: re-ask once. */
        NUDGE_ONCE,
        /** Blank text after tool work: run one terminal no-tools round for the answer. */
        START_TERMINAL,
        /** Accept the streamed text (or the executor's fallback copy) as the answer. */
        FINISH
    }

    fun planRound(p: TurnProgress, c: ToolContext): RoundPlan {
        val withinLimit = ToolRoundLimiter.canRun(p.toolRounds, c.stepBudget)
        if (p.terminalStarted || !withinLimit || p.costCapped) {
            return RoundPlan(
                toolNames = emptyList(),
                forcedTool = null,
                terminal = true,
                terminalDirective = when {
                    p.terminalStarted -> null
                    p.costCapped -> COST_CAP_DIRECTIVE
                    else -> TERMINAL_DIRECTIVE
                }
            )
        }
        val needsArtifact = c.artifactCapability && p.outputKinds.isEmpty()
        val needsSkill = c.skillCapability && !p.skillCreated
        val needsCode = c.codeCapability && OutputKind.CODE !in p.outputKinds
        val allowSearch = c.searchAvailable &&
            p.searchRounds < c.searchRoundCap.coerceIn(1, ToolRoundLimiter.HARD_MAXIMUM)
        // AUTO's fresh-info gate: force the first parallel_search round so news, prices and
        // scores are not answered from stale memory. First round only (didSearch flips).
        val forceSearch = c.forceSearchFirst && !p.didSearch && !needsSkill && !needsCode && allowSearch
        val needsDeepPass = allowSearch && c.deepResearch &&
            DeepSearchPolicy.shouldForceAnotherPass(p.searchRounds, c.searchDepth)
        val forceWideFirst = allowSearch && c.wideSearch && !p.didSearch

        val names = buildList {
            if (!c.voiceMode && !needsSkill && !needsCode) add(SEARCH_PAST_CHATS)
            if (allowSearch && !needsSkill && !needsCode) add(PARALLEL_SEARCH)
            if (needsArtifact) add(CREATE_ARTIFACT)
            if (needsSkill) add(CREATE_SKILL)
            if (needsCode) add(CREATE_CODE_PROJECT)
            // AUTO media tools: keyword-gated in the context so generate_video is never
            // offered on a plain text question (it would poll the provider for minutes).
            val autoMedia = c.agentMode && c.capabilityAuto
            if (autoMedia && c.offerMediaImage && OutputKind.IMAGE !in p.outputKinds) add(GENERATE_IMAGE)
            if (autoMedia && c.offerMediaVideo && OutputKind.VIDEO !in p.outputKinds) add(GENERATE_VIDEO)
            if (autoMedia && c.offerMediaAudio && OutputKind.AUDIO !in p.outputKinds) add(GENERATE_AUDIO)
            val utility = c.agentMode && !needsSkill && !needsCode && !c.voiceMode
            if (utility) {
                add(UPDATE_PLAN)
                add(GET_CURRENT_TIME)
                add(CALCULATE)
                if (c.memoryEnabled) {
                    add(REMEMBER_FACT)
                    add(RECALL_MEMORIES)
                }
                if (c.publicWebAvailable) add(GET_WEATHER)
                if (c.publicWebAvailable && c.offerFetch) add(FETCH_URL)
                if (c.offerSchedule) add(SCHEDULE_TASK)
                add(ASK_USER)
                if (p.outputKinds.none { it in ArtifactKinds.fileKinds }) add(CREATE_ARTIFACT)
                if (c.offerSkillAuto && !p.skillCreated) add(CREATE_SKILL)
                if (c.offerCodeAuto && OutputKind.CODE !in p.outputKinds) add(CREATE_CODE_PROJECT)
            }
        }.distinct()
        val forcedTool = when {
            forceSearch -> PARALLEL_SEARCH
            needsSkill -> CREATE_SKILL
            needsCode -> CREATE_CODE_PROJECT
            needsArtifact -> CREATE_ARTIFACT
            needsDeepPass || forceWideFirst -> PARALLEL_SEARCH
            else -> null
            // Never force a tool that is not actually offered this round.
        }?.takeIf { it in names }
        return RoundPlan(toolNames = names, forcedTool = forcedTool, terminal = false, terminalDirective = null)
    }

    /**
     * v5.9 step budget from Settings. [stored] is the new "agent_step_budget" value; when it
     * was never written, [legacyRounds] from the old "max_tool_rounds" slider is migrated:
     * the old default of 6 (a user who never raised the cap, or moved the slider back to it)
     * becomes the new default, and any other choice is kept and clamped into the new range,
     * so a user who picked 8 gets 8 and a user who picked 2 gets the minimum of 4. The new
     * key is separate so an explicit choice of 6 after the upgrade is not migrated again.
     */
    fun stepBudgetFromStored(stored: Int?, legacyRounds: Int?): Int = when {
        stored != null -> stored.coerceIn(MIN_STEP_BUDGET, MAX_STEP_BUDGET)
        legacyRounds == null || legacyRounds == LEGACY_DEFAULT_TOOL_ROUNDS -> DEFAULT_STEP_BUDGET
        else -> legacyRounds.coerceIn(MIN_STEP_BUDGET, MAX_STEP_BUDGET)
    }

    /**
     * v5.9 per-turn cost cap. [capUsd] <= 0 means the cap is off. When the provider reported
     * a cost ([spentUsd] non-null) that decides; otherwise the cap falls back to a token
     * allowance ([TOKENS_PER_USD_WITHOUT_COST]) measured on [totalTokens]. With neither
     * cost nor usage reported, [totalTokens] stays 0 and the cap never trips; the step
     * budget still bounds the turn.
     */
    fun costCapReached(spentUsd: Double?, totalTokens: Long, capUsd: Double): Boolean {
        if (capUsd <= 0.0) return false
        if (spentUsd != null) return spentUsd >= capUsd
        return totalTokens >= (capUsd * TOKENS_PER_USD_WITHOUT_COST).toLong()
    }

    /** Appended to an answer whose tools were stopped by the cost cap. */
    fun costCapNote(capUsd: Double): String =
        "_Further tool use stopped at the per-turn cost cap of $${"%.2f".format(java.util.Locale.US, capUsd)} " +
            "(Settings > Search & tools)._"

    /**
     * Whether a round that returned [callNames] spends the tool budget. Plan-only rounds are
     * free until [FREE_PLAN_ROUNDS] of them have been used this turn.
     */
    fun roundCountsTowardBudget(callNames: List<String>, freePlanRoundsUsed: Int): Boolean {
        val planOnly = callNames.isNotEmpty() && callNames.all { it == UPDATE_PLAN }
        return !planOnly || freePlanRoundsUsed >= FREE_PLAN_ROUNDS
    }

    /**
     * What to do after a model round. Driven by behaviour, not prose:
     * calls returned -> run them; text returned -> it is the answer. The only text
     * inspection left is a single high-precision nudge when the FIRST round narrates a
     * tool call without making one — or when the user's request clearly asked for a file
     * ([artifactExpected]) and the model answered in prose without creating one.
     */
    fun afterModel(
        p: TurnProgress,
        text: String,
        callCount: Int,
        toolsOffered: Boolean,
        artifactExpected: Boolean = false
    ): After {
        if (callCount > 0) return if (p.terminalStarted) After.FINISH else After.RUN_CALLS
        if (text.isNotBlank()) {
            if (!p.terminalStarted && !p.nudged && p.toolRounds == 0 && toolsOffered &&
                (artifactExpected || strictToolPromise(text))
            ) {
                return After.NUDGE_ONCE
            }
            return After.FINISH
        }
        return if (p.terminalStarted) After.FINISH else After.START_TERMINAL
    }

    // High-precision "the model said it would call a tool but didn't" detector. Deliberately
    // much stricter than the pre-5.7 regex set: single-word verbs only in first-person
    // intent phrases, plus a length cap — a long answer that mentions searching is an
    // answer, not a promise.
    private val firstPersonIntent = Regex(
        """\b(let me|i'll|i will|i'm going to|i am going to|i need to)\b.{0,48}""" +
            """\b(search|look(?:\s+\w+){0,2}\s+up|check\s+(?:online|the\s+web|that\s+online)|google|browse|fetch|call|run)\b""",
        RegexOption.IGNORE_CASE
    )
    private val progressiveIntent = Regex(
        """^\W*(searching|checking online|looking (that|this|it) up|browsing the web|fetching)\b""",
        RegexOption.IGNORE_CASE
    )
    private val namedTool = Regex(
        """\b(parallel_search|search_past_chats|create_artifact|create_skill|create_code_project|""" +
            """generate_image|generate_video|generate_audio|get_current_time|calculate|get_weather|""" +
            """fetch_url|remember_fact|recall_memories|schedule_task|ask_user|update_plan)\b"""
    )

    fun strictToolPromise(text: String): Boolean {
        if (text.isBlank() || text.length > 600) return false
        return namedTool.containsMatchIn(text) ||
            firstPersonIntent.containsMatchIn(text) ||
            progressiveIntent.containsMatchIn(text)
    }
}
