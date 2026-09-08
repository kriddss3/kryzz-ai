package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReasoningScrubTest {
    @Test fun noTagsPassThroughUnchanged() {
        assertThat("Hello world.".scrubThinkTags()).isEqualTo("Hello world.")
    }

    @Test fun completeThinkBlockIsRemoved() {
        val input = "Let me work this out. <think>secret reasoning here</think> The answer is 4."
        assertThat(input.scrubThinkTags()).isEqualTo("Let me work this out.  The answer is 4.")
    }

    @Test fun unclosedThinkBlockHidesTheRest() {
        val input = "Visible prefix. <think>still streaming reasoning"
        assertThat(input.scrubThinkTags()).isEqualTo("Visible prefix.")
    }

    @Test fun multipleCompleteBlocksAreAllRemoved() {
        val input = "<think>a</think> first <think>b</think> second"
        assertThat(input.scrubThinkTags()).isEqualTo("first  second")
    }

    @Test fun thinkAfterNewlineDoesNotLeaveLeadingBlankNoise() {
        val input = "Line one.\n<think>hidden</think>\nLine two."
        assertThat(input.scrubThinkTags()).isEqualTo("Line one.\n\nLine two.")
    }

    @Test fun emptyStringStaysEmpty() {
        assertThat("".scrubThinkTags()).isEqualTo("")
    }
}
