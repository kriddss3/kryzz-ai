package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpokenReplySegmenterTest {
    @Test fun firstSentenceIsReleasedAsSoonAsItEnds() {
        val segmenter = SpokenReplySegmenter()
        assertThat(segmenter.onText("r1", "[happy] Sure thing")).isEmpty()
        assertThat(segmenter.onText("r1", "[happy] Sure thing. The ca")).containsExactly("[happy] Sure thing.")
    }

    @Test fun laterSentencesWaitForTheEndOfAShortReply() {
        val segmenter = SpokenReplySegmenter()
        assertThat(segmenter.onText("r1", "Paris is the capital. It has about two million people. It sits"))
            .containsExactly("Paris is the capital.")
        assertThat(segmenter.onText("r1", "Paris is the capital. It has about two million people. It sits on the Seine."))
            .isEmpty()
        assertThat(segmenter.finish())
            .containsExactly("It has about two million people. It sits on the Seine.")
    }

    @Test fun longRepliesReleaseBatchesWhileStreaming() {
        val segmenter = SpokenReplySegmenter(batchChars = 40)
        assertThat(segmenter.onText("r1", "First one. Second sentence is here. Third sentence is also here. Fou"))
            .containsExactly("First one.", "Second sentence is here. Third sentence is also here.")
            .inOrder()
        assertThat(segmenter.onText("r1", "First one. Second sentence is here. Third sentence is also here. Fourth.")).isEmpty()
        assertThat(segmenter.finish()).containsExactly("Fourth.")
    }

    @Test fun aNewRoundStartsOverWithoutRepeatingWhatWasSpoken() {
        val segmenter = SpokenReplySegmenter()
        assertThat(segmenter.onText("tool-round", "Let me check that. Searching")).containsExactly("Let me check that.")
        assertThat(segmenter.onText("answer", "It is sunny today. Highs near")).containsExactly("It is sunny today.")
        assertThat(segmenter.onText("answer", "It is sunny today. Highs near twenty.")).isEmpty()
        assertThat(segmenter.finish()).containsExactly("Highs near twenty.")
    }

    @Test fun finishWithNothingLeftReleasesNothing() {
        val segmenter = SpokenReplySegmenter()
        assertThat(segmenter.onText("r1", "All done. ")).containsExactly("All done.")
        assertThat(segmenter.finish()).isEmpty()
    }

    @Test fun abbreviationsDecimalsAndInitialsDoNotEndASentence() {
        assertThat(SpokenReplySegmenter.sentenceEnds("Dr. Smith said 3.5 is fine. Next", 0)).hasSize(1)
        assertThat(SpokenReplySegmenter.sentenceEnds("J. K. Rowling wrote it. Then", 0)).hasSize(1)
        assertThat(SpokenReplySegmenter.sentenceEnds("Use a tool, e.g. a hammer. Then", 0)).hasSize(1)
    }

    @Test fun anOpenCodeFenceHoldsTheCut() {
        assertThat(SpokenReplySegmenter.sentenceEnds("Here ```val x = 1. More``` Done. Next", 0)).hasSize(1)
    }

    @Test fun closingQuotesAndLineBreaksEndSentences() {
        assertThat(SpokenReplySegmenter.sentenceEnds("He said \"hi.\" Then left", 0)).hasSize(1)
        assertThat(SpokenReplySegmenter.sentenceEnds("A line\nAnother", 0)).hasSize(1)
    }
}
