package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SwarmPlanParsingTest {
    private val task = "Research solid-state batteries and write a summary"

    @Test fun plainJsonPlanParsesIntoSubtasks() {
        val raw = """{"subtasks":[{"title":"Research","instruction":"Find recent solid-state battery advances"},{"title":"Summarize","instruction":"Write a concise summary"}]}"""
        val subtasks = parseSwarmPlan(raw, task)
        assertThat(subtasks).hasSize(2)
        assertThat(subtasks[0].title).isEqualTo("Research")
        assertThat(subtasks[1].instruction).contains("summary")
    }

    @Test fun fencedJsonBlockIsTolerated() {
        val raw = "Here is the plan:\n```json\n{\"subtasks\":[{\"title\":\"Only step\",\"instruction\":\"Do everything\"}]}\n```\nHope that helps"
        val subtasks = parseSwarmPlan(raw, task)
        assertThat(subtasks).hasSize(1)
        assertThat(subtasks.single().title).isEqualTo("Only step")
    }

    @Test fun plannerOverflowIsCappedAtFourSubtasks() {
        val entries = (1..6).joinToString(",") { """{"title":"Step $it","instruction":"Instruction $it"}""" }
        val subtasks = parseSwarmPlan("""{"subtasks":[$entries]}""", task)
        assertThat(subtasks).hasSize(4)
        assertThat(subtasks.last().title).isEqualTo("Step 4")
    }

    @Test fun blankOrIncompleteSubtasksAreDropped() {
        val raw = """{"subtasks":[{"title":"  ","instruction":"x"},{"title":"Valid","instruction":""},{"title":"Valid","instruction":"Do it"}]}"""
        val subtasks = parseSwarmPlan(raw, task)
        assertThat(subtasks).hasSize(1)
        assertThat(subtasks.single().instruction).isEqualTo("Do it")
    }

    @Test fun garbageFallsBackToSingleWholeTaskSubtask() {
        val subtasks = parseSwarmPlan("not json at all", task)
        assertThat(subtasks).hasSize(1)
        assertThat(subtasks.single().instruction).isEqualTo(task)
    }

    @Test fun emptySubtaskArrayFallsBackToSingleWholeTaskSubtask() {
        val subtasks = parseSwarmPlan("""{"subtasks":[]}""", task)
        assertThat(subtasks).hasSize(1)
        assertThat(subtasks.single().instruction).isEqualTo(task)
    }
}
