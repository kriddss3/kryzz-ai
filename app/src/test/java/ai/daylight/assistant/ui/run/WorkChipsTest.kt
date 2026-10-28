package ai.daylight.assistant.ui.run

import ai.daylight.assistant.domain.WorkStep
import ai.daylight.assistant.domain.WorkStepKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkChipsTest {
    private fun step(kind: WorkStepKind, title: String = "t", detail: String? = null, failed: Boolean = false) =
        WorkStep(kind, title, detail, failed = failed)

    @Test fun doneStepsFoldByKindInOrderAndFilesGetOneChipEach() {
        val chips = WorkChips.done(
            listOf(
                step(WorkStepKind.SEARCH, "pixel 10"),
                step(WorkStepKind.PAGE),
                step(WorkStepKind.SEARCH, "pixel 10 battery"),
                step(WorkStepKind.PAGE),
                step(WorkStepKind.PAGE),
                step(WorkStepKind.FILE, "Report", "report.docx"),
                step(WorkStepKind.FILE, "Table"),
                step(WorkStepKind.CALCULATION),
                step(WorkStepKind.SEARCH, "failed one", failed = true),
            )
        )
        assertThat(chips.map { it.label }).containsExactly(
            "Search, 2", "Read, 3 pages", "File, report.docx", "File, Table", "Calculate, 1", "Failed, 1"
        ).inOrder()
        assertThat(chips.dropLast(1).all { it.glyph == ChipGlyph.CHECK && !it.running && !it.failed }).isTrue()
        assertThat(chips.last().failed).isTrue()
        assertThat(chips.last().glyph).isEqualTo(ChipGlyph.FAILED)
    }

    @Test fun countsThatMeanNothingAreDroppedForASingleStep() {
        assertThat(WorkChips.foldedLabel(WorkStepKind.TIME, 1)).isEqualTo("Time")
        assertThat(WorkChips.foldedLabel(WorkStepKind.TIME, 2)).isEqualTo("Time, 2")
        assertThat(WorkChips.foldedLabel(WorkStepKind.PAGE, 1)).isEqualTo("Read, 1 page")
        assertThat(WorkChips.foldedLabel(WorkStepKind.SEARCH, 1)).isEqualTo("Search, 1")
        assertThat(WorkChips.foldedLabel(WorkStepKind.QUESTION, 1)).isEqualTo("Asked you")
    }

    @Test fun noStepsNoChips() {
        assertThat(WorkChips.done(emptyList())).isEmpty()
    }

    @Test fun runningChipsHideTheWholeRunMarkerAndPlanUpdates() {
        val chips = WorkChips.running(listOf("Agent at work", "Updating the plan", "Searching: pixel 10", "Reading the page…"), generating = true)
        assertThat(chips.map { it.label }).containsExactly("Searching: pixel 10", "Reading the page").inOrder()
        assertThat(chips.all { it.running }).isTrue()
        assertThat(chips.map { it.glyph }).containsExactly(ChipGlyph.SEARCH, ChipGlyph.WEB).inOrder()
    }

    @Test fun betweenToolsOneThinkingChipSaysItIsStillWorking() {
        val chips = WorkChips.running(listOf("Agent at work"), generating = true)
        assertThat(chips).containsExactly(WorkChip("Thinking", null, running = true))
        assertThat(WorkChips.running(emptyList(), generating = true).single().label).isEqualTo("Thinking")
    }

    @Test fun nothingRunsWhenTheRunHasEnded() {
        assertThat(WorkChips.running(listOf("Searching the web"), generating = false)).isEmpty()
    }

    @Test fun thePlanSubLineIsTheNewestRealActivity() {
        assertThat(WorkChips.latestActivity(listOf("Searching the web", "Agent at work"))).isEqualTo("Searching the web")
        assertThat(WorkChips.latestActivity(listOf("Agent at work", "Updating the plan"))).isNull()
        assertThat(WorkChips.latestActivity(listOf("Reading the page…"))).isEqualTo("Reading the page")
    }

    @Test fun glyphsFollowWhatTheActivitySays() {
        assertThat(WorkChips.glyphFor("Creating your image")).isEqualTo(ChipGlyph.IMAGE)
        assertThat(WorkChips.glyphFor("Generating your video")).isEqualTo(ChipGlyph.VIDEO)
        assertThat(WorkChips.glyphFor("Creating your audio")).isEqualTo(ChipGlyph.AUDIO)
        assertThat(WorkChips.glyphFor("Creating your file")).isEqualTo(ChipGlyph.FILE)
        assertThat(WorkChips.glyphFor("Searching past chats")).isEqualTo(ChipGlyph.SEARCH)
        assertThat(WorkChips.glyphFor("Looking up memories")).isEqualTo(ChipGlyph.MEMORY)
        assertThat(WorkChips.glyphFor("Waiting for your answer")).isEqualTo(ChipGlyph.QUESTION)
        assertThat(WorkChips.glyphFor("Calculating")).isEqualTo(ChipGlyph.TOOLS)
    }
}
