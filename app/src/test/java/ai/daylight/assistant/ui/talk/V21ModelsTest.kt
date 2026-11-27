package ai.daylight.assistant.ui.talk

import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.MakeStyle
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.ThoughtKind
import ai.daylight.assistant.domain.ThoughtStep
import ai.daylight.assistant.ui.DictationSession
import ai.daylight.assistant.ui.home.HomeBoard
import ai.daylight.assistant.ui.make.MakeModel
import ai.daylight.assistant.ui.onboarding.KeyLinks
import ai.daylight.assistant.ui.onboarding.TourFlow
import ai.daylight.assistant.ui.onboarding.TourPage
import ai.daylight.assistant.ui.run.FileCardModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** v2.1's pure UI rules: the thinking row, hold-to-talk, saving, Make's style, the tour. */
class V21ModelsTest {
    @Test fun thinkingRowTitles() {
        val steps = listOf(
            ThoughtStep(ThoughtKind.THINK, "hmm"),
            ThoughtStep(ThoughtKind.TOOL, "parallel_search", "q"),
            ThoughtStep(ThoughtKind.SAY, "Looking.")
        )
        assertThat(ThinkingRowModel.title(emptyList(), live = true, writing = false, elapsedMs = 4_200)).isEqualTo("THINKING · 4S")
        assertThat(ThinkingRowModel.title(steps, live = true, writing = true, elapsedMs = 9_000)).isEqualTo("WRITING · 2 STEPS")
        assertThat(ThinkingRowModel.title(steps, live = false, writing = false, elapsedMs = 12_000)).isEqualTo("THOUGHT FOR 12S · 2 STEPS")
        assertThat(ThinkingRowModel.title(steps.take(1), live = false, writing = false, elapsedMs = null)).isEqualTo("THOUGHT · 1 STEP")
        assertThat(ThinkingRowModel.shows(emptyList(), live = false)).isFalse()
        assertThat(ThinkingRowModel.shows(emptyList(), live = true)).isTrue()
        assertThat(ThinkingRowModel.shows(steps, live = false)).isTrue()
    }

    @Test fun holdToTalkCancelsWhenSlidUpOrFarAside() {
        val px = 200f
        assertThat(HoldToTalkModel.cancels(dx = 0f, dy = -250f, cancelPx = px)).isTrue()
        assertThat(HoldToTalkModel.cancels(dx = 0f, dy = -150f, cancelPx = px)).isFalse()
        assertThat(HoldToTalkModel.cancels(dx = 0f, dy = 400f, cancelPx = px)).isFalse()
        assertThat(HoldToTalkModel.cancels(dx = 250f, dy = 0f, cancelPx = px)).isTrue()
        assertThat(HoldToTalkModel.cancels(dx = 150f, dy = 0f, cancelPx = px)).isFalse()
        assertThat(HoldToTalkModel.cancels(dx = -320f, dy = 0f, cancelPx = px)).isTrue()
        assertThat(HoldToTalkModel.cancels(dx = -250f, dy = 0f, cancelPx = px)).isFalse()
        assertThat(HoldToTalkModel.cancelProgress(dx = 0f, dy = 0f, cancelPx = px)).isEqualTo(0f)
        assertThat(HoldToTalkModel.cancelProgress(dx = -100f, dy = 50f, cancelPx = px)).isEqualTo(0f)
        assertThat(HoldToTalkModel.cancelProgress(dx = 100f, dy = 0f, cancelPx = px)).isEqualTo(0.5f)
        assertThat(HoldToTalkModel.cancelProgress(dx = 400f, dy = 0f, cancelPx = px)).isEqualTo(1f)
        assertThat(HoldToTalkModel.cancelProgress(dx = 50f, dy = -300f, cancelPx = px)).isEqualTo(1f)
        assertThat(HoldToTalkModel.cancelProgress(dx = 100f, dy = 0f, cancelPx = 0f)).isEqualTo(0f)
        assertThat(HoldToTalkModel.HOLD_MS).isLessThan(400L)
    }

    @Test fun holdNotes() {
        val listening = HoldToTalkModel.note(holding = true, cancelZone = false, dictating = true, transcribing = false)
        assertThat(listening?.first).isEqualTo("LISTENING")
        assertThat(listening?.second).contains("Slide right to cancel")
        assertThat(HoldToTalkModel.note(holding = true, cancelZone = true, dictating = true, transcribing = false)?.first).isEqualTo("CANCEL")
        assertThat(HoldToTalkModel.note(holding = false, cancelZone = false, dictating = false, transcribing = true)?.first).isEqualTo("WRITING")
        assertThat(HoldToTalkModel.note(holding = false, cancelZone = false, dictating = true, transcribing = false)).isNull()
        assertThat(holdPlaceholder("Reply")).isEqualTo("Reply, or hold to talk")
    }

    @Test fun dictatedWordsGoAfterTheDraft() {
        assertThat(DictationSession.append("", " hello ")).isEqualTo("hello")
        assertThat(DictationSession.append("Remind me ", "to call mum")).isEqualTo("Remind me to call mum")
        assertThat(HomeBoard.dictationProblem(DictationSession.TOO_SHORT)).isEqualTo("HOLD LONGER TO TALK")
        assertThat(HomeBoard.dictationProblem(DictationSession.NOTHING_HEARD)).isEqualTo("NOTHING HEARD")
        assertThat(HomeBoard.dictationProblem("boom")).isEqualTo("DICTATION FAILED")
    }

    @Test fun madeImagesAndClipsSaveToTheGallery() {
        fun out(kind: OutputKind, path: String?) = GeneratedOutput("id", kind, "t", "f.png", "image/png", localPath = path)
        assertThat(FileCardModel.savesToGallery(out(OutputKind.IMAGE, "/x.png"))).isTrue()
        assertThat(FileCardModel.savesToGallery(out(OutputKind.VIDEO, "/x.mp4"))).isTrue()
        assertThat(FileCardModel.savesToGallery(out(OutputKind.IMAGE, null))).isFalse()
        assertThat(FileCardModel.savesToGallery(out(OutputKind.DOCUMENT, "/x.docx"))).isFalse()
        assertThat(FileCardModel.saveLabel(false)).isEqualTo("Save to phone")
        assertThat(FileCardModel.saveLabel(true)).isEqualTo("Saved to phone")
    }

    @Test fun makeShowsTheStyleOnARunningRender() {
        assertThat(MakeModel.styleSpecs(MakeStyle.PLAIN)).isEmpty()
        assertThat(MakeModel.styleSpecs(MakeStyle.NOTHING)).containsExactly("Nothing style")
    }

    @Test fun tourPagesAndKeyLinks() {
        assertThat(TourFlow.count).isEqualTo(7)
        assertThat(TourFlow.counter(0)).isEqualTo("( 1 / 7 )")
        assertThat(TourFlow.page(99)).isEqualTo(TourPage.KEYS)
        assertThat(TourFlow.back(0)).isNull()
        assertThat(TourFlow.next(TourFlow.count - 1)).isEqualTo(TourFlow.count - 1)
        assertThat(TourFlow.primaryLabel(0)).isEqualTo("Next")
        assertThat(TourFlow.primaryLabel(TourFlow.count - 1)).isEqualTo("Start")
        TourPage.entries.forEach { page ->
            assertThat(page.title.split(' ').size).isAtMost(2)
            assertThat(page.body.length).isAtMost(220)
        }
        assertThat(TourPage.keys.map { it.url }).containsExactly(KeyLinks.OPENROUTER, KeyLinks.FISH_AUDIO, KeyLinks.PARALLEL).inOrder()
        assertThat(TourPage.keys.single { it.required }.name).isEqualTo("OpenRouter")
        assertThat(KeyLinks.display(KeyLinks.OPENROUTER)).isEqualTo("openrouter.ai/settings/keys")
        assertThat(KeyLinks.display(KeyLinks.FISH_AUDIO)).isEqualTo("fish.audio/app/api-keys")
        assertThat(KeyLinks.OPENROUTER).startsWith("https://")
        assertThat(KeyLinks.FISH_AUDIO).startsWith("https://")
    }
}
