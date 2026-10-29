package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** C9: a bot's role joins the system prompt in its own conversation, after the user's persona. */
class BotPersonaPromptTest {
    private val base = "You are Kryzz, a calm assistant."

    @Test fun theUsersPersonaStaysFirstAndTheBotsRoleFollows() {
        val prompt = BotPersona.systemPrompt(base, "Scout", "Reads my three feeds and writes a five-line brief.")
        assertThat(prompt).startsWith(base)
        assertThat(prompt).contains("\"Scout\"")
        assertThat(prompt).contains("Reads my three feeds and writes a five-line brief.")
        assertThat(prompt.indexOf("Scout")).isGreaterThan(prompt.indexOf(base) + base.length - 1)
    }

    @Test fun itExplainsRoutineMessagesSoRunsAreDoneNotDiscussed() {
        val prompt = BotPersona.systemPrompt(base, "Pilot", "Plans my week.")
        assertThat(prompt).contains("routines on a schedule")
    }

    @Test fun aBlankNameLeavesThePromptAlone() {
        assertThat(BotPersona.systemPrompt(base, "   ", "Anything")).isEqualTo(base)
    }

    @Test fun aBotWithNoJobStillGetsItsName() {
        val prompt = BotPersona.systemPrompt(base, "Tally", "")
        assertThat(prompt).contains("\"Tally\"")
        assertThat(prompt).doesNotContain("describes your job")
    }

    @Test fun namesAndJobsAreCleanedAndCapped() {
        assertThat(BotPersona.cleanName("  Scout \n the   bot  ")).isEqualTo("Scout the bot")
        assertThat(BotPersona.cleanName("x".repeat(80))).hasLength(BotPersona.NAME_MAX)
        assertThat(BotPersona.cleanPersona("y".repeat(2_000))).hasLength(BotPersona.PERSONA_MAX)
    }

    @Test fun itIsStableSoPromptCachingKeepsWorking() {
        val a = BotPersona.systemPrompt(base, "Scout", "Reads feeds.")
        val b = BotPersona.systemPrompt(base, "Scout", "Reads feeds.")
        assertThat(a).isEqualTo(b)
    }

    @Test fun theAgentAddsItWhereThePersonaIsAssembled() {
        // The hook sits on the base prompt, so voice keeps its own prompt and every mode gets it.
        val source = File("src/main/java/ai/daylight/assistant/domain/AgentExecutor.kt").readText()
        assertThat(source).contains("dao.botForConversation(conversationId)")
        assertThat(source).contains("BotPersona.systemPrompt(base, bot.name, bot.persona)")
    }
}
