package ai.daylight.assistant.ui.talk

import ai.daylight.assistant.domain.Citation
import ai.daylight.assistant.domain.WorkLog
import ai.daylight.assistant.domain.WorkStep
import ai.daylight.assistant.domain.WorkStepKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SourceReadoutTest {
    private val citations = listOf(
        Citation("Pixel and dot-matrix category", "https://www.fonts.google.com/?category=pixel"),
        Citation("Open-licensed bitmap font repos", "https://github.com/topics/bitmap-font"),
    )

    @Test fun hostDropsSchemeWwwAndPath() {
        assertThat(SourceReadoutBuilder.host("https://www.Fontsource.org/fonts/x?y=1")).isEqualTo("fontsource.org")
        assertThat(SourceReadoutBuilder.host("rtings.com/laptop")).isEqualTo("rtings.com")
        assertThat(SourceReadoutBuilder.host(null)).isEmpty()
        assertThat(SourceReadoutBuilder.host("")).isEmpty()
    }

    @Test fun noSourcesAndNoToolsMeansNoReadout() {
        assertThat(SourceReadoutBuilder.build(emptyList(), null)).isNull()
        assertThat(SourceReadoutBuilder.build(emptyList(), WorkLog(emptyList(), null))).isNull()
    }

    @Test fun citationsAreNumberedReadSegments() {
        val readout = SourceReadoutBuilder.build(citations, null)!!
        assertThat(readout.items.map { it.label }).containsExactly("fonts.google.com", "github.com").inOrder()
        assertThat(readout.items.map { it.cite }).containsExactly(1, 2).inOrder()
        assertThat(readout.items.all { it.state == SourceState.READ }).isTrue()
        assertThat(readout.countLabel).isEqualTo("2/2")
        assertThat(readout.caption).isEqualTo("Sources")
    }

    @Test fun fetchedPagesAreAddedOnceAndSearchesHideBehindSources() {
        val log = WorkLog(
            listOf(
                WorkStep(WorkStepKind.SEARCH, "open pixel fonts"),
                WorkStep(WorkStepKind.PAGE, "GitHub topic", url = "https://github.com/topics/bitmap-font/"),
                WorkStep(WorkStepKind.PAGE, "Fontsource packages", url = "https://fontsource.org/"),
                WorkStep(WorkStepKind.CALCULATION, "12 x 7"),
            ),
            durationMs = 4_200
        )
        val readout = SourceReadoutBuilder.build(citations, log)!!
        assertThat(readout.items.map { it.label })
            .containsExactly("fonts.google.com", "github.com", "fontsource.org", "calculation").inOrder()
        assertThat(readout.items[2].cite).isNull()
        assertThat(readout.caption).isEqualTo(log.summary.replaceFirstChar { it.uppercaseChar() })
    }

    @Test fun searchesShowWhenNothingWasRead() {
        val log = WorkLog(listOf(WorkStep(WorkStepKind.SEARCH, "kryzz squid")), durationMs = null)
        val readout = SourceReadoutBuilder.build(emptyList(), log)!!
        assertThat(readout.items.single().label).isEqualTo("search")
        assertThat(readout.items.single().title).isEqualTo("kryzz squid")
    }

    @Test fun liveActivityAddsALiveSegmentAndTheSentence() {
        val readout = SourceReadoutBuilder.build(
            citations, null,
            activities = listOf("Reading: www.fontsource.org/fonts/pixel"),
            streaming = true
        )!!
        assertThat(readout.total).isEqualTo(3)
        assertThat(readout.done).isEqualTo(2)
        assertThat(readout.live?.label).isEqualTo("fontsource.org")
        assertThat(readout.caption).isEqualTo("Reading fontsource.org")
        assertThat(readout.countLabel).isEqualTo("2/3")
    }

    @Test fun activitiesAreIgnoredOnceTheAnswerIsDone() {
        val readout = SourceReadoutBuilder.build(citations, null, activities = listOf("Reading: x.com"), streaming = false)!!
        assertThat(readout.live).isNull()
        assertThat(readout.total).isEqualTo(2)
    }

    @Test fun liveItemsReadTheActivityChips() {
        assertThat(SourceReadoutBuilder.liveItem("Searching: pixel 10 battery test")!!.let(SourceReadoutBuilder::liveCaption))
            .isEqualTo("Searching pixel 10 battery test")
        assertThat(SourceReadoutBuilder.liveItem("Searching the web…")!!.let(SourceReadoutBuilder::liveCaption))
            .isEqualTo("Searching the web")
        assertThat(SourceReadoutBuilder.liveItem("Generating the image…")!!.label).isEqualTo("generating")
        assertThat(SourceReadoutBuilder.liveItem("   ")).isNull()
    }
}
