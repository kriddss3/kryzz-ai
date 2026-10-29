package ai.daylight.assistant.ui.make

import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.MakeKind
import ai.daylight.assistant.domain.MakeOutcome
import ai.daylight.assistant.domain.MakeRender
import ai.daylight.assistant.domain.MediaJobStatus
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.RecentOutput
import ai.daylight.assistant.domain.mediaUsesMiniMax
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class MakeParamsTest {
    private val miniMaxImages = listOf(
        MediaModel("image-01", supportedAspectRatios = listOf("1:1", "16:9", "9:16", "4:3", "3:4")),
        MediaModel("image-01-live", supportedAspectRatios = listOf("1:1", "16:9"))
    )
    private val miniMaxVideo = listOf(MediaModel("MiniMax-H3", supportedAspectRatios = listOf("16:9", "9:16", "1:1", "4:3", "adaptive")))
    private val openRouterVideo = listOf(
        MediaModel("google/veo-3", supportedAspectRatios = listOf("16:9", "9:16")),
        MediaModel("other/clip", supportedAspectRatios = emptyList())
    )

    private fun output(kind: OutputKind, id: String = "o1", path: String? = "/x/$id") =
        GeneratedOutput(id, kind, "Generated", "kryzz.${if (kind == OutputKind.VIDEO) "mp4" else "jpg"}", "image/jpeg", localPath = path)

    private fun recent(kind: OutputKind, conversation: String, at: Long, id: String = "o-$at") =
        RecentOutput(output(kind, id), "m-$at", conversation, at, "a prompt")

    @Test fun onlyShapeRatiosAreOfferedAndSixteenNineStaysTheDefault() {
        assertThat(MakeModel.ratioChoices(listOf("16:9", " 9:16", "adaptive", "16:9", "0:1", "x"))).containsExactly("16:9", "9:16").inOrder()
        assertThat(MakeModel.defaultRatio(listOf("1:1", "16:9"))).isEqualTo("16:9")
        assertThat(MakeModel.defaultRatio(listOf("1:1", "4:3"))).isEqualTo("1:1")
        assertThat(MakeModel.defaultRatio(emptyList())).isNull()
        assertThat(MakeModel.ratioFor("3:4", listOf("1:1", "3:4"))).isEqualTo("3:4")
        // A ratio the newly chosen model doesn't take falls back to the default.
        assertThat(MakeModel.ratioFor("3:4", listOf("1:1", "16:9"))).isEqualTo("16:9")
        assertThat(MakeModel.ratioFor("3:4", emptyList())).isNull()
    }

    @Test fun ratioDialAppearsOnlyWhenTheRequestCarriesARatio() {
        // MiniMax images: the built-in list, also for a "minimax/" prefixed id.
        assertThat(MakeModel.supportedRatios(MakeKind.IMAGE, miniMax = true, "minimax/image-01", miniMaxImages))
            .containsExactly("1:1", "16:9", "9:16", "4:3", "3:4").inOrder()
        // An id the list doesn't know still takes a ratio (it is an API field there).
        assertThat(MakeModel.supportedRatios(MakeKind.IMAGE, miniMax = true, "image-02", miniMaxImages))
            .containsExactly("1:1", "16:9", "9:16", "4:3", "3:4").inOrder()
        // MiniMax video drops "adaptive".
        assertThat(MakeModel.supportedRatios(MakeKind.VIDEO, miniMax = true, "MiniMax-H3", miniMaxVideo))
            .containsExactly("16:9", "9:16", "1:1", "4:3").inOrder()
        // OpenRouter video: what the catalogue reports, nothing for an unknown model.
        assertThat(MakeModel.supportedRatios(MakeKind.VIDEO, miniMax = false, "google/veo-3", openRouterVideo))
            .containsExactly("16:9", "9:16").inOrder()
        assertThat(MakeModel.supportedRatios(MakeKind.VIDEO, miniMax = false, "unknown/model", openRouterVideo)).isEmpty()
        assertThat(MakeModel.supportedRatios(MakeKind.VIDEO, miniMax = false, "google/veo-3", null)).isEmpty()
        // OpenRouter's image request has no ratio field.
        assertThat(MakeModel.supportedRatios(MakeKind.IMAGE, miniMax = false, "openai/gpt-image-1", openRouterVideo)).isEmpty()
        assertThat(MakeModel.supportedRatios(MakeKind.IMAGE, miniMax = true, "", miniMaxImages)).isEmpty()
    }

    @Test fun providerRuleMatchesTheAgent() {
        assertThat(mediaUsesMiniMax(video = false, ChatProvider.OPENROUTER, "image-01")).isTrue()
        assertThat(mediaUsesMiniMax(video = false, ChatProvider.OPENROUTER, "openai/gpt-image-1")).isFalse()
        assertThat(mediaUsesMiniMax(video = false, ChatProvider.MINIMAX, "openai/gpt-image-1")).isTrue()
        assertThat(mediaUsesMiniMax(video = true, ChatProvider.OPENROUTER, "MiniMax-H3")).isTrue()
        assertThat(mediaUsesMiniMax(video = true, ChatProvider.OPENROUTER, "google/veo-3")).isFalse()
    }

    @Test fun frameTakesTheRatioShape() {
        assertThat(MakeModel.frameAspect(MakeKind.IMAGE, "3:4")).isWithin(1e-6f).of(0.75f)
        assertThat(MakeModel.frameAspect(MakeKind.IMAGE, null)).isEqualTo(1f)
        assertThat(MakeModel.frameAspect(MakeKind.VIDEO, null)).isWithin(1e-6f).of(16f / 9f)
        assertThat(MakeModel.wideFrame(16f / 9f)).isTrue()
        assertThat(MakeModel.wideFrame(1f)).isFalse()
        assertThat(MakeModel.wideFrame(0.75f)).isFalse()
    }

    @Test fun madeFilesGetCommonRatioNames() {
        assertThat(MakeModel.ratioLabel(1280, 720)).isEqualTo("16:9")
        assertThat(MakeModel.ratioLabel(1366, 768)).isEqualTo("16:9")
        assertThat(MakeModel.ratioLabel(768, 1024)).isEqualTo("3:4")
        assertThat(MakeModel.ratioLabel(1024, 1024)).isEqualTo("1:1")
        assertThat(MakeModel.ratioLabel(1000, 700)).isEqualTo("10:7")
        assertThat(MakeModel.ratioLabel(1001, 703)).isEqualTo("1.42:1")
        assertThat(MakeModel.ratioLabel(0, 720)).isNull()
    }

    @Test fun videoStatusUsesTheJobsOwnWordAndImagesJustRender() {
        assertThat(MakeModel.statusWord(MakeKind.IMAGE, "queued")).isEqualTo("Rendering")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, null)).isEqualTo("Sending")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, MediaJobStatus.SENDING)).isEqualTo("Sending")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, "Queueing")).isEqualTo("Queued")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, "pending")).isEqualTo("Queued")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, "in_progress")).isEqualTo("Processing")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, "running")).isEqualTo("Processing")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, MediaJobStatus.DOWNLOADING)).isEqualTo("Downloading")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, "upscaling")).isEqualTo("Upscaling")
        assertThat(MakeModel.statusWord(MakeKind.VIDEO, "a_very_long_unknown_state_name")).isEqualTo("Processing")
        assertThat(MakeModel.elapsedLabel(7_400)).isEqualTo("0:07")
        assertThat(MakeModel.elapsedLabel(65_000)).isEqualTo("1:05")
        assertThat(MakeModel.elapsedLabel(-5)).isEqualTo("0:00")
    }

    @Test fun stripLinesFollowTheKit() {
        val running = MakeRender(1, MakeKind.VIDEO, "ferry", "16:9", "MiniMax-H3", 0L)
        assertThat(MakeModel.inkLine(running, "queued", null, null, null).text).isEqualTo("MAKE QUEUED")
        assertThat(MakeModel.inkLine(running, "queued", null, null, null).live).isTrue()
        assertThat(MakeModel.inkLine(running.copy(kind = MakeKind.IMAGE), null, null, null, null).text).isEqualTo("MAKE RENDERING")
        assertThat(MakeModel.inkLine(null, null, output(OutputKind.VIDEO), 6_000, null).text).isEqualTo("MAKE CLIP READY 6S")
        assertThat(MakeModel.inkLine(null, null, output(OutputKind.VIDEO), null, null).text).isEqualTo("MAKE CLIP READY")
        assertThat(MakeModel.inkLine(null, null, output(OutputKind.IMAGE), null, null).text).isEqualTo("MAKE IMAGE READY")
        assertThat(MakeModel.inkLine(null, null, null, null, MakeOutcome.FAILED).text).isEqualTo("MAKE FAILED")
        assertThat(MakeModel.inkLine(null, null, null, null, MakeOutcome.STOPPED).text).isEqualTo("MAKE STOPPED")
        assertThat(MakeModel.inkLine(null, null, null, null, null).text).isEqualTo("MAKE READY")
    }

    @Test fun aFinishedRenderShowsTheFileItMade() {
        val done = MakeRender(2, MakeKind.IMAGE, "lantern", "3:4", "image-01", startedAt = 100, conversationId = "c1", outcome = MakeOutcome.DONE)
        val list = listOf(
            recent(OutputKind.IMAGE, "c2", 300),
            recent(OutputKind.VIDEO, "c1", 250),
            recent(OutputKind.IMAGE, "c1", 200, id = "mine"),
            recent(OutputKind.IMAGE, "c1", 50)
        )
        assertThat(MakeModel.resultFor(done, list)?.output?.id).isEqualTo("mine")
        assertThat(MakeModel.resultFor(done.copy(outcome = MakeOutcome.RUNNING), list)).isNull()
        assertThat(MakeModel.resultFor(done.copy(outcome = MakeOutcome.FAILED), list)).isNull()
        assertThat(MakeModel.resultFor(done.copy(conversationId = null), list)).isNull()
        assertThat(MakeModel.resultFor(null, list)).isNull()
    }

    @Test fun resultCaptionsAndSpecsComeFromTheFile() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 10, 29, 10, 24, 0, 0, zone).toInstant().toEpochMilli()
        val made = ZonedDateTime.of(2026, 10, 29, 10, 21, 0, 0, zone).toInstant().toEpochMilli()
        assertThat(MakeModel.madeCaption(made, now, zone, 6_000)).isEqualTo("Made 10:21, 6.0 s")
        assertThat(MakeModel.madeCaption(made, now, zone)).isEqualTo("Made 10:21")
        assertThat(MakeModel.specs(MakeKind.VIDEO, 6_000, 1280, 720)).containsExactly("6.0 s", "16:9", "720p").inOrder()
        assertThat(MakeModel.specs(MakeKind.IMAGE, null, 768, 1024)).containsExactly("3:4", "768 x 1024").inOrder()
        assertThat(MakeModel.specs(MakeKind.VIDEO, null, 0, 0)).isEmpty()
        assertThat(MakeModel.readyCaption(MakeKind.VIDEO)).isEqualTo("Clip ready")
    }

    @Test fun scrubberAndFilmstripMath() {
        assertThat(MakeModel.clockLabel(2_345)).isEqualTo("00:02.3")
        assertThat(MakeModel.clockLabel(66_000)).isEqualTo("01:06.0")
        assertThat(MakeModel.scrubIndex(0, 6_000, 44)).isEqualTo(0)
        assertThat(MakeModel.scrubIndex(6_000, 6_000, 44)).isEqualTo(43)
        assertThat(MakeModel.scrubIndex(2_300, 6_000, 44)).isEqualTo(16)
        assertThat(MakeModel.scrubIndex(100, 0, 44)).isEqualTo(0)
        assertThat(MakeModel.seekPosition(50f, 100f, 6_000)).isEqualTo(3_000)
        assertThat(MakeModel.seekPosition(-10f, 100f, 6_000)).isEqualTo(0)
        assertThat(MakeModel.seekPosition(500f, 100f, 6_000)).isEqualTo(6_000)
        assertThat(MakeModel.filmstripTimes(6_000, 6)).containsExactly(500L, 1_500L, 2_500L, 3_500L, 4_500L, 5_500L).inOrder()
        assertThat(MakeModel.filmstripTimes(0, 6)).isEmpty()
        assertThat(MakeModel.currentFrame(2_300, 6_000, 6)).isEqualTo(2)
        assertThat(MakeModel.currentFrame(6_000, 6_000, 6)).isEqualTo(5)
    }

    @Test fun recentInkShowsFourTilesThenACount() {
        val list = (1..7).map { recent(OutputKind.IMAGE, "c", it.toLong()) } +
            RecentOutput(GeneratedOutput("t", OutputKind.DOCUMENT, "Notes", "notes.md", "text/markdown", content = "# hi"), "m", "c", 0, null)
        val (tiles, more) = MakeModel.recentTiles(list)
        assertThat(tiles).hasSize(4)
        // A text-only result has no file to open, so it is not a tile.
        assertThat(more).isEqualTo(3)
        assertThat(MakeModel.recentTiles(list.take(2)).second).isEqualTo(0)
        assertThat(MakeModel.isMedia(output(OutputKind.IMAGE))).isTrue()
        assertThat(MakeModel.isMedia(output(OutputKind.IMAGE, path = null))).isFalse()
        assertThat(MakeModel.isMedia(output(OutputKind.DOCUMENT))).isFalse()
    }
}
