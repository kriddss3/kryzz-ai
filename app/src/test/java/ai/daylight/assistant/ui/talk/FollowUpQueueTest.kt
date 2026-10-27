package ai.daylight.assistant.ui.talk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FollowUpQueueTest {
    @Test fun startsEmpty() {
        assertThat(FollowUpQueue().isEmpty).isTrue()
        assertThat(FollowUpQueue().text).isNull()
    }

    @Test fun queueingTrimsAndIgnoresBlankText() {
        assertThat(FollowUpQueue().add("  and in Latvian?  ").text).isEqualTo("and in Latvian?")
        assertThat(FollowUpQueue().add("   ").isEmpty).isTrue()
        assertThat(FollowUpQueue().add("").isEmpty).isTrue()
    }

    @Test fun queueingTwiceAddsToTheSameSlot() {
        val queue = FollowUpQueue().add("first").add("second")
        assertThat(queue.text).isEqualTo("first\nsecond")
    }

    @Test fun normalFinishSendsTheQueuedTextAndEmptiesTheSlot() {
        val (next, action) = FollowUpQueue().add("follow up").onGenerationEnded(stoppedByUser = false, failed = false, editing = false)
        assertThat(action).isEqualTo(FollowUpAction.Send("follow up"))
        assertThat(next.isEmpty).isTrue()
    }

    @Test fun stopFailureOrEditingRestoresInsteadOfSending() {
        val queue = FollowUpQueue().add("follow up")
        listOf(
            Triple(true, false, false),
            Triple(false, true, false),
            Triple(false, false, true),
            Triple(true, true, true),
        ).forEach { (stopped, failed, editing) ->
            val (next, action) = queue.onGenerationEnded(stopped, failed, editing)
            assertThat(action).isEqualTo(FollowUpAction.Restore("follow up"))
            assertThat(next.isEmpty).isTrue()
        }
    }

    @Test fun nothingQueuedDoesNothing() {
        val (next, action) = FollowUpQueue().onGenerationEnded(stoppedByUser = false, failed = false, editing = false)
        assertThat(action).isEqualTo(FollowUpAction.None)
        assertThat(next.isEmpty).isTrue()
        assertThat(FollowUpQueue("   ").onGenerationEnded(false, false, false).second).isEqualTo(FollowUpAction.None)
    }

    @Test fun restoredTextGoesInFrontOfTheDraft() {
        assertThat(FollowUpQueue.mergeIntoDraft("queued", "typed")).isEqualTo("queued\ntyped")
        assertThat(FollowUpQueue.mergeIntoDraft("queued", "  ")).isEqualTo("queued")
        assertThat(FollowUpQueue.mergeIntoDraft("  ", "typed")).isEqualTo("typed")
        assertThat(FollowUpQueue.mergeIntoDraft("", "")).isEmpty()
    }

    @Test fun clearEmpties() {
        assertThat(FollowUpQueue().add("x").clear().isEmpty).isTrue()
    }
}
