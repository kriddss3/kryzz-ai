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
        stepBudget: Int = AgentTurnPolicy.DEFAULT_STEP_BUDGET,
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
        searchRoundCap = if (voiceMode) 1 else stepBudget,
        stepBudget = stepBudget,
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

    @Test fun terminalRoundKeepsTheToolListButCallsNothing() {
        // v5.10: the terminal round resends the turn's tools (tool_choice "none" on
        // OpenRouter) so its request keeps the cached prefix; terminal means no call runs.
        val first = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext())
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(terminalStarted = true),
            autoContext()
        )
        assertThat(plan.terminal).isTrue()
        assertThat(plan.forcedTool).isNull()
        assertThat(plan.toolNames).containsExactlyElementsIn(first.toolNames).inOrder()
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
        val plan = AgentTurnPolicy.planRound(exhausted, autoContext(stepBudget = 6))
        assertThat(plan.terminal).isTrue()
        assertThat(plan.toolNames).isEqualTo(
            AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext(stepBudget = 6)).toolNames
        )
        assertThat(plan.forcedTool).isNull()
        assertThat(plan.terminalDirective).isEqualTo(AgentTurnPolicy.TERMINAL_DIRECTIVE)
        // The directive is attached once: re-planning while terminal adds nothing.
        val again = AgentTurnPolicy.planRound(exhausted.copy(terminalStarted = true), autoContext(stepBudget = 6))
        assertThat(again.terminal).isTrue()
        assertThat(again.terminalDirective).isNull()
    }

    // ---------- v5.9 step budget and cost cap ----------

    @Test fun defaultStepBudgetLeavesRoomForLongRuns() {
        val progress = AgentTurnPolicy.TurnProgress(toolRounds = 8, searchRounds = 4, didSearch = true)
        val plan = AgentTurnPolicy.planRound(progress, autoContext())
        assertThat(plan.terminal).isFalse()
        val spent = progress.copy(toolRounds = AgentTurnPolicy.DEFAULT_STEP_BUDGET)
        assertThat(AgentTurnPolicy.planRound(spent, autoContext()).terminal).isTrue()
    }

    @Test fun costCapSwitchesToTerminalRoundWithItsOwnDirective() {
        val capped = AgentTurnPolicy.TurnProgress(toolRounds = 2, searchRounds = 2, didSearch = true, costCapped = true)
        val plan = AgentTurnPolicy.planRound(capped, autoContext())
        assertThat(plan.terminal).isTrue()
        assertThat(plan.forcedTool).isNull()
        assertThat(plan.terminalDirective).isEqualTo(AgentTurnPolicy.COST_CAP_DIRECTIVE)
        val again = AgentTurnPolicy.planRound(capped.copy(terminalStarted = true), autoContext())
        assertThat(again.terminalDirective).isNull()
    }

    @Test fun costCapUsesReportedCostWhenKnown() {
        assertThat(AgentTurnPolicy.costCapReached(0.24, 10_000_000, 0.25)).isFalse()
        assertThat(AgentTurnPolicy.costCapReached(0.25, 0, 0.25)).isTrue()
        assertThat(AgentTurnPolicy.costCapReached(1.5, 0, 0.25)).isTrue()
    }

    @Test fun costCapFallsBackToTokensWhenCostIsUnknown() {
        // MiniMax reports tokens only: $0.25 allows 500k tokens.
        assertThat(AgentTurnPolicy.costCapReached(null, 499_999, 0.25)).isFalse()
        assertThat(AgentTurnPolicy.costCapReached(null, 500_000, 0.25)).isTrue()
        // No usage reported at all: the cap cannot trip.
        assertThat(AgentTurnPolicy.costCapReached(null, 0, 0.25)).isFalse()
    }

    @Test fun costCapOffNeverTrips() {
        assertThat(AgentTurnPolicy.costCapReached(99.0, 99_000_000, 0.0)).isFalse()
    }

    @Test fun costCapNoteNamesTheCap() {
        assertThat(AgentTurnPolicy.costCapNote(0.25)).contains("$0.25")
        assertThat(AgentTurnPolicy.costCapNote(0.25)).contains("cost cap")
    }

    @Test fun stepBudgetMigratesLegacyToolRounds() {
        // Never set anywhere: the new default.
        assertThat(AgentTurnPolicy.stepBudgetFromStored(null, null)).isEqualTo(16)
        // The old default of 6 moves to the new default.
        assertThat(AgentTurnPolicy.stepBudgetFromStored(null, 6)).isEqualTo(16)
        // Other old choices are kept, clamped into 4..24.
        assertThat(AgentTurnPolicy.stepBudgetFromStored(null, 8)).isEqualTo(8)
        assertThat(AgentTurnPolicy.stepBudgetFromStored(null, 2)).isEqualTo(4)
        // A value saved under the new key wins, including an explicit 6.
        assertThat(AgentTurnPolicy.stepBudgetFromStored(6, 8)).isEqualTo(6)
        assertThat(AgentTurnPolicy.stepBudgetFromStored(99, null)).isEqualTo(24)
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
        // v5.10: still offered (stable list); a second call replaces the file.
        assertThat(plan.toolNames).contains(AgentTurnPolicy.CREATE_ARTIFACT)
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

    @Test fun fetchIsOfferedFromTheFirstRoundWhenAUrlCanBecomeReadable() {
        // v5.10: a search this turn can make result URLs readable, so fetch_url is part of
        // the turn's list from round 0 instead of appearing after the first search.
        val withSearch = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext(offerFetch = false))
        assertThat(withSearch.toolNames).contains(AgentTurnPolicy.FETCH_URL)
        // No search and no known URL: nothing could ever be fetched this turn.
        val nothing = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(),
            autoContext(offerFetch = false, searchAvailable = false)
        )
        assertThat(nothing.toolNames).doesNotContain(AgentTurnPolicy.FETCH_URL)
        val pastedLink = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(),
            autoContext(offerFetch = true, searchAvailable = false)
        )
        assertThat(pastedLink.toolNames).contains(AgentTurnPolicy.FETCH_URL)
    }

    @Test fun withoutThePublicWebClientNeitherWeatherNorFetchIsOffered() {
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(),
            autoContext(offerFetch = true, publicWebAvailable = false)
        )
        assertThat(plan.toolNames).containsNoneOf(AgentTurnPolicy.GET_WEATHER, AgentTurnPolicy.FETCH_URL)
    }

    @Test fun autoCreateArtifactStaysOfferedAfterAFileExists() {
        // v5.10: a second create_artifact replaces the file instead of the tool vanishing.
        val plan = AgentTurnPolicy.planRound(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, outputKinds = setOf(OutputKind.SPREADSHEET)),
            autoContext()
        )
        assertThat(plan.toolNames).contains(AgentTurnPolicy.CREATE_ARTIFACT)
        assertThat(plan.forcedTool).isNull()
    }

    // ---------- v5.10 stable tool list ----------

    @Test fun toolListIsStableAcrossEveryRoundOfATurn() {
        // The tool list is part of the provider's cached prefix: any change between rounds
        // makes the whole history full price again.
        val contexts = listOf(
            autoContext(),
            autoContext(forceSearchFirst = true, stepBudget = 4),
            autoContext(offerFetch = true, memoryEnabled = false),
            autoContext().copy(artifactCapability = true),
            autoContext().copy(skillCapability = true),
            autoContext().copy(codeCapability = true),
            autoContext().copy(deepResearch = true, searchRoundCap = 2),
            autoContext().copy(offerMediaImage = true, offerMediaVideo = true, offerSchedule = true),
            autoContext().copy(agentMode = false, capabilityAuto = false) // chat mode
        )
        val progressions = listOf(
            AgentTurnPolicy.TurnProgress(toolRounds = 1, searchRounds = 1, didSearch = true),
            AgentTurnPolicy.TurnProgress(toolRounds = 3, searchRounds = 5, didSearch = true),
            AgentTurnPolicy.TurnProgress(toolRounds = 2, outputKinds = setOf(OutputKind.DOCUMENT, OutputKind.IMAGE)),
            AgentTurnPolicy.TurnProgress(toolRounds = 1, outputKinds = setOf(OutputKind.CODE), skillCreated = true),
            AgentTurnPolicy.TurnProgress(toolRounds = 2, nudged = true, costCapped = true),
            AgentTurnPolicy.TurnProgress(toolRounds = 30, terminalStarted = true)
        )
        contexts.forEach { ctx ->
            val first = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), ctx).toolNames
            progressions.forEach { progress ->
                assertThat(AgentTurnPolicy.planRound(progress, ctx).toolNames).containsExactlyElementsIn(first).inOrder()
            }
        }
    }

    @Test fun callsPastATurnLimitAreRefusedInsteadOfHidden() {
        val ctx = autoContext(stepBudget = 16).copy(searchRoundCap = 2, offerMediaImage = true)
        val fresh = AgentTurnPolicy.TurnProgress()
        assertThat(AgentTurnPolicy.refusal(AgentTurnPolicy.PARALLEL_SEARCH, fresh, ctx)).isNull()
        val searched = AgentTurnPolicy.TurnProgress(toolRounds = 2, searchRounds = 2, didSearch = true)
        assertThat(AgentTurnPolicy.refusal(AgentTurnPolicy.PARALLEL_SEARCH, searched, ctx))
            .isEqualTo(AgentTurnPolicy.SEARCH_LIMIT_REFUSAL)
        // One image per turn, as when the tool used to disappear after the first one.
        assertThat(AgentTurnPolicy.refusal(AgentTurnPolicy.GENERATE_IMAGE, fresh, ctx)).isNull()
        val imaged = AgentTurnPolicy.TurnProgress(toolRounds = 1, outputKinds = setOf(OutputKind.IMAGE))
        assertThat(AgentTurnPolicy.refusal(AgentTurnPolicy.GENERATE_IMAGE, imaged, ctx))
            .isEqualTo(AgentTurnPolicy.MEDIA_REPEAT_REFUSAL)
        assertThat(AgentTurnPolicy.refusal(AgentTurnPolicy.GENERATE_VIDEO, imaged, ctx)).isNull()
        assertThat(
            AgentTurnPolicy.refusal(AgentTurnPolicy.CREATE_SKILL, AgentTurnPolicy.TurnProgress(skillCreated = true), ctx)
        ).isEqualTo(AgentTurnPolicy.SKILL_REPEAT_REFUSAL)
        // Files and code projects are replaced, never refused.
        val filed = AgentTurnPolicy.TurnProgress(toolRounds = 1, outputKinds = setOf(OutputKind.DOCUMENT, OutputKind.CODE))
        assertThat(AgentTurnPolicy.refusal(AgentTurnPolicy.CREATE_ARTIFACT, filed, ctx)).isNull()
        assertThat(AgentTurnPolicy.refusal(AgentTurnPolicy.CREATE_CODE_PROJECT, filed, ctx)).isNull()
    }

    @Test fun onlyOpenRouterKeepsToolsOnTheTerminalRound() {
        assertThat(AgentTurnPolicy.keepsToolsOnTerminalRound(ChatProvider.OPENROUTER)).isTrue()
        assertThat(AgentTurnPolicy.keepsToolsOnTerminalRound(ChatProvider.MINIMAX)).isFalse()
    }

    @Test fun dedicatedMakersOfferOnlyTheirOwnTool() {
        val skill = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext().copy(skillCapability = true))
        assertThat(skill.toolNames).containsExactly(AgentTurnPolicy.CREATE_SKILL)
        val code = AgentTurnPolicy.planRound(AgentTurnPolicy.TurnProgress(), autoContext().copy(codeCapability = true))
        assertThat(code.toolNames).containsExactly(AgentTurnPolicy.CREATE_CODE_PROJECT)
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
