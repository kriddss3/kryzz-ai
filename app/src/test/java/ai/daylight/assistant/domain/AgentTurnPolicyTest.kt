package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the v5.7 agent-loop contract: the model drives the turn. Calls returned -> run
 * them and continue; text without calls -> that is the final answer; one high-precision
 * nudge on round 0; one terminal no-tools round when the budget runs out or the model
 * goes quiet after tool work. These tests exist because 5.6.x decided continuation by
 * regex-matching prose, which broke multi-step Auto runs.
 */
class AgentTurnPolicyTest {

    private fun autoContext(
        forceSearchFirst: Boolean = false,
        searchAvailable: Boolean = true,
        maxToolRounds: Int = 6,
        voiceMode: Boolean = false,
        offerFetch: Boolean = false,
        publicWebAvailable: Boolean = true,
        memoryEnabled: Boolean = true
    ) = AgentTurnPolicy.ToolContext(
        agentMode = true,
        voiceMode = voiceMode,
        capabilityAuto = true,
        artifactCapability = false,
        skillCapability = false,
        codeCapability = false,
        deepResearch = false,
        wideSearch = false,
        searchDepth = 2,
        searchAvailable = searchAvailable,
        searchRoundCap = if (voiceMode) 1 else maxToolRounds,
        maxToolRounds = maxToolRounds,
        forceSearchFirst = forceSearchFirst,
        offerMediaImage = false,
        offerMediaVideo = false,
        offerMediaAudio = false,
        offerSkillAuto = false,
        offerCodeAuto = false,
        memoryEnabled = memoryEnabled,
        publicWebAvailable = publicWebAvailable,
        offerFetch = offerFetch,
        offerSchedule = false
    )

    // ---------- planRound ----------

    @Test fun autoFirstRoundOffersSearchAndUtilityTools() {
        val plan = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext())
        assertThat(plan.terminal).isFalse()
        assertThat(plan.forcedTool).isNull()
        assertThat(plan.toolNames).containsAtLeast(
            AgentTurnPolicy.SEARCH_PAST_CHATS,
            AgentTurnPolicy.PARALLEL_SEARCH,
            AgentTurnPolicy.GET_CURRENT_TIME,
            AgentTurnPolicy.CALCULATE,
            AgentTurnPolicy.REMEMBER_FACT,
            AgentTurnPolicy.RECALL_MEMORIES
        )
    }

    @Test fun askUserIsOfferedToTheAutoAgent() {
        // v5.7.2: the interactive question card is a standard AUTO utility tool so the
        // agent can ask the user with tappable options instead of guessing.
        val plan = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext())
        assertThat(plan.toolNames).contains(AgentTurnPolicy.ASK_USER)
        // It is never a forced call — asking must be the model's own choice.
        assertThat(plan.forcedTool).isNotEqualTo(AgentTurnPolicy.ASK_USER)
    }

    @Test fun askUserIsNotOfferedInVoiceMode() {
        // Voice turns keep tools minimal so the spoken reply stays responsive.
        val voicePlan = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext(voiceMode = true))
        assertThat(voicePlan.toolNames).doesNotContain(AgentTurnPolicy.ASK_USER)
        assertThat(voicePlan.toolNames).doesNotContain(AgentTurnPolicy.UPDATE_PLAN)
    }

    @Test fun askUserIsNotOfferedOnTheTerminalRound() {
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(terminalStarted = true),
            autoContext()
        )
        assertThat(plan.terminal).isTrue()
        assertThat(plan.toolNames).doesNotContain(AgentTurnPolicy.ASK_USER)
    }

    @Test fun freshInfoForcesTheFirstWebSearch() {
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(),
            autoContext(forceSearchFirst = true)
        )
        assertThat(plan.forcedTool).isEqualTo(AgentTurnPolicy.PARALLEL_SEARCH)
        assertThat(plan.toolNames).contains(AgentTurnPolicy.PARALLEL_SEARCH)
        // v5.8: the executor pins the search with a named tool_choice, so the other tools
        // stay offered instead of vanishing for the round.
        assertThat(plan.toolNames).containsAtLeast(
            AgentTurnPolicy.SEARCH_PAST_CHATS,
            AgentTurnPolicy.CALCULATE,
            AgentTurnPolicy.UPDATE_PLAN
        )
    }

    @Test fun forcedSearchStopsOnceASearchRan() {
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, searchRounds = 1, didSearch = true),
            autoContext(forceSearchFirst = true)
        )
        assertThat(plan.forcedTool).isNull()
        assertThat(plan.toolNames).contains(AgentTurnPolicy.SEARCH_PAST_CHATS)
        assertThat(plan.toolNames).contains(AgentTurnPolicy.CALCULATE)
    }

    @Test fun afterASearchRoundTheModelCanChainMoreTools() {
        // THE multi-step regression test: after one search the next round must still
        // offer tools (fetch / weather / memory / another search) — 5.6.x stripped them
        // via FORCE_FINAL_ANSWER whenever the follow-up text looked short or promised
        // another step.
        val progress = AgentTurnPolicy.TurnProgress(toolRounds = 1, searchRounds = 1, didSearch = true)
        val plan = AgentTurnPolicy.planRound(progress, autoContext(offerFetch = true))
        assertThat(plan.terminal).isFalse()
        assertThat(plan.toolNames).containsAtLeast(
            AgentTurnPolicy.PARALLEL_SEARCH,
            AgentTurnPolicy.FETCH_URL,
            AgentTurnPolicy.GET_WEATHER,
            AgentTurnPolicy.CALCULATE
        )
    }

    @Test fun budgetExhaustionPlansOneTerminalRoundThenStops() {
        val exhausted = AgentTurnPolicy.TurnProgress(toolRounds = 6, searchRounds = 2, didSearch = true)
        val plan = AgentTurnPolicy.planRound(exhausted, autoContext(maxToolRounds = 6))
        assertThat(plan.terminal).isTrue()
        assertThat(plan.toolNames).isEmpty()
        assertThat(plan.forcedTool).isNull()
        assertThat(plan.terminalDirective).isEqualTo(AgentTurnPolicy.TERMINAL_DIRECTIVE)
        // The directive is attached once: re-planning while terminal adds nothing.
        val again = AgentTurnPolicy.planRound(exhausted.copy(terminalStarted = true), autoContext(maxToolRounds = 6))
        assertThat(again.terminal).isTrue()
        assertThat(again.terminalDirective).isNull()
    }

    @Test fun forcedToolIsAlwaysOffered() {
        // Forcing a tool the provider was not offered is a wire error (MiniMax 2013).
        val contexts = listOf(
            autoContext(forceSearchFirst = true),
            autoContext().copy(artifactCapability = true),
            autoContext().copy(skillCapability = true),
            autoContext().copy(codeCapability = true),
            autoContext().copy(wideSearch = true)
        )
        contexts.forEach { ctx ->
            val plan = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), ctx)
            if (plan.forcedTool != null) assertThat(plan.toolNames).contains(plan.forcedTool)
        }
    }

    @Test fun dedicatedCapabilitiesForceTheirTool() {
        assertThat(
            AgentTurnPolicy.planRound(
                AgentTurnPolicy.TurnProgress(),
                autoContext().copy(artifactCapability = true)
            ).forcedTool
        ).isEqualTo(AgentTurnPolicy.CREATE_ARTIFACT)
        assertThat(
            AgentTurnPolicy.planRound(
                AgentTurnPolicy.TurnProgress(),
                autoContext().copy(skillCapability = true)
            ).forcedTool
        ).isEqualTo(AgentTurnPolicy.CREATE_SKILL)
        assertThat(
            AgentTurnPolicy.planRound(
                AgentTurnPolicy.TurnProgress(),
                autoContext().copy(wideSearch = true)
            ).forcedTool
        ).isEqualTo(AgentTurnPolicy.PARALLEL_SEARCH)
    }

    @Test fun artifactStopsBeingForcedOnceProduced() {
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, outputKinds = setOf(OutputKind.DOCUMENT)),
            autoContext().copy(artifactCapability = true)
        )
        assertThat(plan.forcedTool).isNull()
        assertThat(plan.toolNames).doesNotContain(AgentTurnPolicy.CREATE_ARTIFACT)
    }

    @Test fun voiceModeOffersSearchOnlyAndCapsAtOneRound() {
        val ctx = autoContext(voiceMode = true).copy(agentMode = false, capabilityAuto = false)
        val first = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), ctx)
        assertThat(first.toolNames).containsExactly(AgentTurnPolicy.PARALLEL_SEARCH)
        val afterSearch = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, searchRounds = 1, didSearch = true), ctx
        )
        assertThat(afterSearch.toolNames).isEmpty()
        assertThat(afterSearch.terminal).isFalse() // tools off, but not a terminal round
    }

    @Test fun withoutASearchKeyNoSearchToolsAreOffered() {
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(),
            autoContext(searchAvailable = false, forceSearchFirst = true)
        )
        assertThat(plan.toolNames).doesNotContain(AgentTurnPolicy.PARALLEL_SEARCH)
        assertThat(plan.forcedTool).isNull()
    }

    @Test fun deepResearchForcesAnotherPassWithinDepth() {
        val ctx = autoContext().copy(deepResearch = true, searchDepth = 2)
        val first = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), ctx)
        assertThat(first.forcedTool).isEqualTo(AgentTurnPolicy.PARALLEL_SEARCH)
        val second = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, searchRounds = 1, didSearch = true), ctx
        )
        assertThat(second.forcedTool).isEqualTo(AgentTurnPolicy.PARALLEL_SEARCH)
        val third = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 2, searchRounds = 2, didSearch = true), ctx
        )
        assertThat(third.forcedTool).isNull()
    }

    @Test fun cheapToolsAreOfferedEveryAgentRoundWithoutKeywords() {
        // v5.8: weather, files, planning, time and arithmetic no longer hide behind keyword
        // gates; "put it in a table I can download" used to get no create_artifact at all.
        val plan = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext())
        assertThat(plan.toolNames).containsAtLeast(
            AgentTurnPolicy.GET_WEATHER,
            AgentTurnPolicy.CREATE_ARTIFACT,
            AgentTurnPolicy.UPDATE_PLAN,
            AgentTurnPolicy.GET_CURRENT_TIME,
            AgentTurnPolicy.CALCULATE,
            AgentTurnPolicy.ASK_USER
        )
        // Slow, costly or side-effecting tools stay gated.
        assertThat(plan.toolNames).containsNoneOf(
            AgentTurnPolicy.GENERATE_IMAGE,
            AgentTurnPolicy.GENERATE_VIDEO,
            AgentTurnPolicy.GENERATE_AUDIO,
            AgentTurnPolicy.SCHEDULE_TASK,
            AgentTurnPolicy.CREATE_SKILL,
            AgentTurnPolicy.CREATE_CODE_PROJECT
        )
    }

    @Test fun fetchIsOfferedOnceAUrlIsReadable() {
        val noUrls = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext(offerFetch = false))
        assertThat(noUrls.toolNames).doesNotContain(AgentTurnPolicy.FETCH_URL)
        val afterSearch = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, searchRounds = 1, didSearch = true),
            autoContext(offerFetch = true)
        )
        assertThat(afterSearch.toolNames).contains(AgentTurnPolicy.FETCH_URL)
    }

    @Test fun withoutThePublicWebClientNeitherWeatherNorFetchIsOffered() {
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(),
            autoContext(offerFetch = true, publicWebAvailable = false)
        )
        assertThat(plan.toolNames).containsNoneOf(AgentTurnPolicy.GET_WEATHER, AgentTurnPolicy.FETCH_URL)
    }

    @Test fun autoCreateArtifactIsOfferedUntilAFileExists() {
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, outputKinds = setOf(OutputKind.SPREADSHEET)),
            autoContext()
        )
        assertThat(plan.toolNames).doesNotContain(AgentTurnPolicy.CREATE_ARTIFACT)
        // An image is not a file deliverable, so a document can still follow it.
        val afterImage = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, outputKinds = setOf(OutputKind.IMAGE)),
            autoContext()
        )
        assertThat(afterImage.toolNames).contains(AgentTurnPolicy.CREATE_ARTIFACT)
    }

    @Test fun offeredToolNamesAreNeverDuplicated() {
        // A dedicated Document run both needs create_artifact and gets the utility copy;
        // a duplicate definition is rejected by some providers.
        val contexts = listOf(
            autoContext(),
            autoContext(forceSearchFirst = true, offerFetch = true),
            autoContext().copy(artifactCapability = true),
            autoContext().copy(deepResearch = true)
        )
        contexts.forEach { ctx ->
            val names = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), ctx).toolNames
            assertThat(names).containsNoDuplicates()
        }
    }

    @Test fun planOnlyRoundsAreFreeUpToTheCap() {
        val planOnly = listOf(AgentTurnPolicy.UPDATE_PLAN)
        assertThat(AgentTurnPolicy.roundCountsTowardBudget(planOnly, freePlanRoundsUsed = 0)).isFalse()
        assertThat(
            AgentTurnPolicy.roundCountsTowardBudget(planOnly, AgentTurnPolicy.FREE_PLAN_ROUNDS - 1)
        ).isFalse()
        // Past the cap a model stuck re-planning spends budget and still reaches the terminal round.
        assertThat(
            AgentTurnPolicy.roundCountsTowardBudget(planOnly, AgentTurnPolicy.FREE_PLAN_ROUNDS)
        ).isTrue()
        // Mixed rounds and ordinary rounds always count.
        assertThat(
            AgentTurnPolicy.roundCountsTowardBudget(listOf(AgentTurnPolicy.UPDATE_PLAN, AgentTurnPolicy.PARALLEL_SEARCH), 0)
        ).isTrue()
        assertThat(AgentTurnPolicy.roundCountsTowardBudget(listOf(AgentTurnPolicy.CALCULATE), 0)).isTrue()
        assertThat(AgentTurnPolicy.roundCountsTowardBudget(emptyList(), 0)).isTrue()
    }

    // ---------- afterModel ----------

    @Test fun returnedCallsAlwaysRun() {
        assertThat(
            AgentTurnPolicy.afterModel(AgentTurnPolicy.TurnProgress(), "checking that", callCount = 1, toolsOffered = true)
        ).isEqualTo(AgentTurnPolicy.After.RUN_CALLS)
        // Even with answer-looking text alongside, the calls win.
        assertThat(
            AgentTurnPolicy.afterModel(AgentTurnPolicy.TurnProgress(), "Here is a full answer with plenty of detail.", 2, true)
        ).isEqualTo(AgentTurnPolicy.After.RUN_CALLS)
    }

    @Test fun plainTextAnswerFinishesWithoutProseJudgement() {
        // Short answers, answers under 80 chars, answers mentioning searching — all are
        // final now. The 5.6.x "completeness" regex used to overrule the model here.
        val short = AgentTurnPolicy.afterModel(AgentTurnPolicy.TurnProgress(), "Sure — 42.", 0, true)
        assertThat(short).isEqualTo(AgentTurnPolicy.After.FINISH)
    }

    @Test fun narratedToolOnRoundZeroNudgesExactlyOnce() {
        val start = AgentTurnPolicy.TurnProgress()
        assertThat(
            AgentTurnPolicy.afterModel(start, "Let me check online for that.", 0, toolsOffered = true)
        ).isEqualTo(AgentTurnPolicy.After.NUDGE_ONCE)
        // After the nudge, whatever the model writes is accepted.
        assertThat(
            AgentTurnPolicy.afterModel(start.copy(nudged = true), "Let me check online for that.", 0, toolsOffered = true)
        ).isEqualTo(AgentTurnPolicy.After.FINISH)
    }

    @Test fun artifactGateNudgesAProseOnlyFirstAnswer() {
        // v5.7.1: "make an excel file" fired the artifact keyword gate, but the model
        // wrote the table out as chat text instead of calling create_artifact. That
        // used to end the turn with no file at all; now it is re-asked exactly once.
        val start = AgentTurnPolicy.TurnProgress()
        assertThat(
            AgentTurnPolicy.afterModel(start, "Here is your budget table: …", 0, toolsOffered = true, artifactExpected = true)
        ).isEqualTo(AgentTurnPolicy.After.NUDGE_ONCE)
        // One nudge only: a second prose answer is accepted as the reply.
        assertThat(
            AgentTurnPolicy.afterModel(start.copy(nudged = true), "Here is your budget table: …", 0, toolsOffered = true, artifactExpected = true)
        ).isEqualTo(AgentTurnPolicy.After.FINISH)
        // Without the artifact gate the same prose is just an answer (no behaviour change).
        assertThat(
            AgentTurnPolicy.afterModel(start, "Here is your budget table: …", 0, toolsOffered = true)
        ).isEqualTo(AgentTurnPolicy.After.FINISH)
        // Calls always win over the nudge.
        assertThat(
            AgentTurnPolicy.afterModel(start, "Creating it now.", 1, toolsOffered = true, artifactExpected = true)
        ).isEqualTo(AgentTurnPolicy.After.RUN_CALLS)
    }

    @Test fun noNudgeWithoutToolsOrAfterRoundZero() {
        val start = AgentTurnPolicy.TurnProgress()
        // No tools offered (e.g. chat without a search key): a promise is just text.
        assertThat(
            AgentTurnPolicy.afterModel(start, "Let me check online for that.", 0, toolsOffered = false)
        ).isEqualTo(AgentTurnPolicy.After.FINISH)
        // Mid-turn text is never nudged — multi-step continuation is the model's call.
        assertThat(
            AgentTurnPolicy.afterModel(start.copy(toolRounds = 1, didSearch = true), "Let me check online for that.", 0, true)
        ).isEqualTo(AgentTurnPolicy.After.FINISH)
    }

    @Test fun longAnswerMentioningSearchIsNotAPromise() {
        val answer = "I searched the web earlier in this conversation. " +
            "Here is a detailed summary of everything the sources said about the topic, ".repeat(6)
        assertThat(AgentTurnPolicy.strictToolPromise(answer)).isFalse()
        assertThat(
            AgentTurnPolicy.afterModel(AgentTurnPolicy.TurnProgress(), answer, 0, toolsOffered = true)
        ).isEqualTo(AgentTurnPolicy.After.FINISH)
    }

    @Test fun blankAfterToolWorkStartsOneTerminalRound() {
        val afterTools = AgentTurnPolicy.TurnProgress(toolRounds = 1, searchRounds = 1, didSearch = true)
        assertThat(
            AgentTurnPolicy.afterModel(afterTools, "", 0, toolsOffered = true)
        ).isEqualTo(AgentTurnPolicy.After.START_TERMINAL)
        // The terminal round accepts whatever comes back, even blank (fallback copy).
        assertThat(
            AgentTurnPolicy.afterModel(afterTools.copy(terminalStarted = true), "", 0, toolsOffered = false)
        ).isEqualTo(AgentTurnPolicy.After.FINISH)
    }

    @Test fun terminalRoundNeverRunsCalls() {
        val terminal = AgentTurnPolicy.TurnProgress(toolRounds = 2, terminalStarted = true)
        assertThat(
            AgentTurnPolicy.afterModel(terminal, "answer", callCount = 1, toolsOffered = false)
        ).isEqualTo(AgentTurnPolicy.After.FINISH)
    }

    // ---------- strictToolPromise ----------

    @Test fun strictPromiseMatchesRealNarration() {
        assertThat(AgentTurnPolicy.strictToolPromise("Let me search the weather in Geneva.")).isTrue()
        assertThat(AgentTurnPolicy.strictToolPromise("I'll look that up for you.")).isTrue()
        assertThat(AgentTurnPolicy.strictToolPromise("Checking online now.")).isTrue()
        assertThat(AgentTurnPolicy.strictToolPromise("One moment — calling parallel_search.")).isTrue()
        assertThat(AgentTurnPolicy.strictToolPromise("I need to fetch that page first.")).isTrue()
    }

    @Test fun strictPromiseIgnoresOrdinaryAnswers() {
        assertThat(AgentTurnPolicy.strictToolPromise("")).isFalse()
        assertThat(AgentTurnPolicy.strictToolPromise("Geneva is usually cool in spring.")).isFalse()
        assertThat(AgentTurnPolicy.strictToolPromise("Let me explain how photosynthesis works.")).isFalse()
        assertThat(AgentTurnPolicy.strictToolPromise("You can search the web for more detail if you like.")).isFalse()
        assertThat(AgentTurnPolicy.strictToolPromise("Running late is my biggest fear.")).isFalse()
    }
}
