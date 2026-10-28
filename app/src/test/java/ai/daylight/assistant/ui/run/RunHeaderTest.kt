package ai.daylight.assistant.ui.run

import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.ui.talk.replyPlaceholder
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RunHeaderTest {
    @Test fun theSignalCountsUpAndOnlyMaxIsYellow() {
        assertThat(AgentQuality.entries.map(RunQuality::signal)).containsExactly(1, 2, 3).inOrder()
        assertThat(AgentQuality.entries.filter(RunQuality::accentSignal)).containsExactly(AgentQuality.MAX)
        assertThat(RunQuality.icon(AgentQuality.FAST)).isEqualTo(R.drawable.ic_kz_bolt)
        assertThat(RunQuality.icon(AgentQuality.BALANCED)).isEqualTo(R.drawable.ic_kz_balance)
        assertThat(RunQuality.icon(AgentQuality.MAX)).isEqualTo(R.drawable.ic_kz_max)
    }

    @Test fun theCapLineShowsWhatEachModeReallyRunsWith() {
        assertThat(RunQuality.capLine(AgentQuality.BALANCED, 25)).isEqualTo("Cap $0.25 a run")
        // Max raises a lower cap to $1.00 (AgentQualityPolicy), so the sheet says so.
        assertThat(RunQuality.capLine(AgentQuality.MAX, 25)).isEqualTo("Cap $1.00 a run")
        assertThat(RunQuality.capLine(AgentQuality.MAX, 0)).isEqualTo("No cap")
        assertThat(RunQuality.capLine(AgentQuality.FAST, 0)).isEqualTo("No cap")
    }

    private fun answer(
        id: String = "a",
        mode: AssistantMode = AssistantMode.AGENT,
        status: MessageStatus = MessageStatus.COMPLETE
    ) = MessageEntity(id = id, conversationId = "c", role = "ASSISTANT", content = "done", createdAt = 1, status = status.name, mode = mode.name)

    @Test fun onlyTheNewestFinishedRunCanBeSavedAsASkill() {
        assertThat(RunSkill.state(answer(), lastAnswerId = "a", generating = false, saved = emptySet())).isFalse()
        assertThat(RunSkill.state(answer(), lastAnswerId = "a", generating = false, saved = setOf("a"))).isTrue()
        assertThat(RunSkill.state(answer(), lastAnswerId = "a", generating = true, saved = emptySet())).isNull()
        assertThat(RunSkill.state(answer(), lastAnswerId = "b", generating = false, saved = emptySet())).isNull()
        assertThat(RunSkill.state(answer(mode = AssistantMode.CHAT), lastAnswerId = "a", generating = false, saved = emptySet())).isNull()
        assertThat(RunSkill.state(answer(status = MessageStatus.CANCELLED), lastAnswerId = "a", generating = false, saved = emptySet())).isNull()
    }

    @Test fun runsReplyLineHasItsOwnIdleWords() {
        assertThat(replyPlaceholder(generating = false, editing = false, dictating = false, idle = "Give Kryzz a task"))
            .isEqualTo("Give Kryzz a task")
        assertThat(replyPlaceholder(generating = false, editing = true, dictating = false, idle = "Follow up"))
            .isEqualTo("Edit and resend")
        assertThat(replyPlaceholder(generating = false, editing = false, dictating = false)).isEqualTo("Reply")
    }
}
