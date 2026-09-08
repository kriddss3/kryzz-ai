package ai.daylight.assistant.ui

import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.SwarmPhase
import ai.daylight.assistant.domain.SwarmStatus
import ai.daylight.assistant.ui.agent.BotMood
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentBotMoodTest {
    @Test
    fun activeToolsMakeTheBotLookBusy() {
        assertThat(
            agentBotMood(
                mode = AssistantMode.AGENT,
                generating = true,
                error = null,
                activities = listOf("searching"),
                swarmStatus = null
            )
        ).isEqualTo(BotMood.TOOLING)
    }

    @Test
    fun aFinishedSwarmMakesTheBotCelebrate() {
        assertThat(
            agentBotMood(
                mode = AssistantMode.AGENT,
                generating = false,
                error = null,
                activities = emptyList(),
                swarmStatus = SwarmStatus(SwarmPhase.DONE)
            )
        ).isEqualTo(BotMood.SUCCESS)
    }

    @Test
    fun chatModeNeverLeaksAgentMoodIntoTheChatSurface() {
        assertThat(
            agentBotMood(
                mode = AssistantMode.CHAT,
                generating = true,
                error = "failure",
                activities = listOf("searching"),
                swarmStatus = SwarmStatus(SwarmPhase.FAILED)
            )
        ).isEqualTo(BotMood.IDLE)
    }
}
