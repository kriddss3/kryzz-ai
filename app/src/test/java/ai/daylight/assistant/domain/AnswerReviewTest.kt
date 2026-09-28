package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pins when the v5.9 review pass runs and how the reviewer's reply is read. */
class AnswerReviewTest {

    private fun input(
        agentMode: Boolean = true,
        voiceMode: Boolean = false,
        enabled: Boolean = true,
        capability: AgentCapability = AgentCapability.AUTO,
        didSearch: Boolean = false,
        didFetch: Boolean = false,
        outputKinds: Set<OutputKind> = emptySet(),
        draft: String = "Here is what I found [1].",
        alreadyReviewed: Boolean = false,
        costCapped: Boolean = false
    ) = AnswerReview.Input(
        agentMode, voiceMode, enabled, capability, didSearch, didFetch, outputKinds, draft, alreadyReviewed, costCapped
    )

    @Test fun reviewsTurnsThatDidRealWork() {
        assertThat(AnswerReview.shouldReview(input(didSearch = true))).isTrue()
        assertThat(AnswerReview.shouldReview(input(didFetch = true))).isTrue()
        assertThat(AnswerReview.shouldReview(input(outputKinds = setOf(OutputKind.SPREADSHEET)))).isTrue()
        assertThat(AnswerReview.shouldReview(input(outputKinds = setOf(OutputKind.CODE)))).isTrue()
        assertThat(AnswerReview.shouldReview(input(capability = AgentCapability.DEEP_RESEARCH))).isTrue()
        assertThat(AnswerReview.shouldReview(input(capability = AgentCapability.DOCUMENT))).isTrue()
    }

    @Test fun skipsTrivialTurns() {
        assertThat(AnswerReview.shouldReview(input())).isFalse()
        // An image is not a file or code deliverable.
        assertThat(AnswerReview.shouldReview(input(outputKinds = setOf(OutputKind.IMAGE)))).isFalse()
    }

    @Test fun skipsWhenOffVoiceChatOrBlank() {
        assertThat(AnswerReview.shouldReview(input(didSearch = true, enabled = false))).isFalse()
        assertThat(AnswerReview.shouldReview(input(didSearch = true, agentMode = false))).isFalse()
        assertThat(AnswerReview.shouldReview(input(didSearch = true, voiceMode = true))).isFalse()
        assertThat(AnswerReview.shouldReview(input(didSearch = true, draft = "  "))).isFalse()
    }

    @Test fun reviewsAtMostOncePerTurnAndNeverPastTheCostCap() {
        assertThat(AnswerReview.shouldReview(input(didSearch = true, alreadyReviewed = true))).isFalse()
        assertThat(AnswerReview.shouldReview(input(didSearch = true, costCapped = true))).isFalse()
    }

    @Test fun directiveNamesSentinelAndOffersFileOnlyWhenOneExists() {
        assertThat(AnswerReview.directive(offerArtifact = false)).contains(AnswerReview.SENTINEL)
        assertThat(AnswerReview.directive(offerArtifact = false)).doesNotContain("create_artifact")
        assertThat(AnswerReview.directive(offerArtifact = true)).contains("create_artifact")
        assertThat(AnswerReview.offersArtifact(setOf(OutputKind.DOCUMENT))).isTrue()
        assertThat(AnswerReview.offersArtifact(setOf(OutputKind.CODE))).isFalse()
    }

    @Test fun sentinelKeepsTheDraft() {
        assertThat(AnswerReview.classify("REVIEW_OK", complete = true)).isEqualTo(AnswerReview.Verdict.APPROVED)
        assertThat(AnswerReview.classify("  **REVIEW_OK**\n", complete = true)).isEqualTo(AnswerReview.Verdict.APPROVED)
        assertThat(AnswerReview.classify("`review_ok`.", complete = true)).isEqualTo(AnswerReview.Verdict.APPROVED)
        assertThat(AnswerReview.classify("", complete = true)).isEqualTo(AnswerReview.Verdict.APPROVED)
    }

    @Test fun partialSentinelWaitsBeforeReplacingTheDraft() {
        assertThat(AnswerReview.classify("", complete = false)).isEqualTo(AnswerReview.Verdict.UNDECIDED)
        assertThat(AnswerReview.classify("REV", complete = false)).isEqualTo(AnswerReview.Verdict.UNDECIDED)
        assertThat(AnswerReview.classify("REVIEW_", complete = false)).isEqualTo(AnswerReview.Verdict.UNDECIDED)
    }

    @Test fun anythingElseIsARevision() {
        assertThat(AnswerReview.classify("Rem", complete = false)).isEqualTo(AnswerReview.Verdict.REVISION)
        assertThat(AnswerReview.classify("Here is the corrected answer.", complete = false))
            .isEqualTo(AnswerReview.Verdict.REVISION)
        assertThat(AnswerReview.classify("Re", complete = true)).isEqualTo(AnswerReview.Verdict.REVISION)
    }
}
