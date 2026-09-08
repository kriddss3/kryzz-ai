package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentLoopPolicyTest {

    @Test fun parseSearchQueriesAcceptsCamelCaseAndAliases() {
        assertThat(AgentLoopPolicy.parseSearchQueries("""{"search_queries":["weather geneva"]}"""))
            .containsExactly("weather geneva")
        assertThat(AgentLoopPolicy.parseSearchQueries("""{"searchQueries":["bitcoin price"]}"""))
            .containsExactly("bitcoin price")
        assertThat(AgentLoopPolicy.parseSearchQueries("""{"query":"latest kotlin version"}"""))
            .containsExactly("latest kotlin version")
        assertThat(AgentLoopPolicy.parseSearchQueries("""{"queries":["a","b"]}"""))
            .containsExactly("a", "b")
    }

    @Test fun parseSearchQueriesIgnoresJunk() {
        assertThat(AgentLoopPolicy.parseSearchQueries("")).isEmpty()
        assertThat(AgentLoopPolicy.parseSearchQueries("not json")).isEmpty()
        assertThat(AgentLoopPolicy.parseSearchQueries("""{"other":1}""")).isEmpty()
    }

    @Test fun matchingSkillsBecomePrimaryAndOthersStayActive() {
        val weather = SkillSummary("Weather brief", "Local forecast summary", "Always search first, then give highs and lows.")
        val recipe = SkillSummary("Recipe card", "Cooking steps", "Return ingredients then method.")
        val selected = AgentLoopPolicy.selectActiveSkills(listOf(weather, recipe), "what's the weather in Gilly")
        assertThat(selected.primary.map { it.name }).contains("Weather brief")
        assertThat(selected.alsoActive.map { it.name }).contains("Recipe card")
        val prompt = AgentLoopPolicy.formatActiveSkillsPrompt(selected)
        assertThat(prompt).contains("ACTIVE")
        assertThat(prompt).contains("do not ask whether to use them")
        assertThat(prompt).contains("Weather brief")
        assertThat(prompt).contains("Recipe card")
        assertThat(prompt).contains("Always search first")
        assertThat(prompt).doesNotContain("Return ingredients then method")
    }

    @Test fun unmatchedEnabledSkillsStillActivate() {
        val skill = SkillSummary("IB notes", "Study summaries", "Always use heading then bullets.")
        val selected = AgentLoopPolicy.selectActiveSkills(listOf(skill), "write a haiku about snow")
        assertThat(selected.primary).hasSize(1)
        assertThat(selected.alsoActive).isEmpty()
    }

    @Test fun knownToolNamesCoverEveryPlannedTool() {
        // The inline-XML recovery trusts this registry; every tool the planner can offer
        // must be in it or a model emitting XML tool calls would silently no-op.
        val planned = setOf(
            AgentTurnPolicy.SEARCH_PAST_CHATS, AgentTurnPolicy.PARALLEL_SEARCH,
            AgentTurnPolicy.CREATE_ARTIFACT, AgentTurnPolicy.CREATE_SKILL,
            AgentTurnPolicy.CREATE_CODE_PROJECT, AgentTurnPolicy.GENERATE_IMAGE,
            AgentTurnPolicy.GENERATE_VIDEO, AgentTurnPolicy.GENERATE_AUDIO,
            AgentTurnPolicy.GET_CURRENT_TIME, AgentTurnPolicy.CALCULATE,
            AgentTurnPolicy.GET_WEATHER, AgentTurnPolicy.FETCH_URL,
            AgentTurnPolicy.REMEMBER_FACT, AgentTurnPolicy.RECALL_MEMORIES,
            AgentTurnPolicy.SCHEDULE_TASK
        )
        assertThat(AgentLoopPolicy.knownToolNames).containsAtLeastElementsIn(planned)
    }
}
