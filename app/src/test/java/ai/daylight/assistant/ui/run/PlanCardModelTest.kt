package ai.daylight.assistant.ui.run

import ai.daylight.assistant.domain.AgentPlan
import ai.daylight.assistant.domain.PlanStep
import ai.daylight.assistant.domain.PlanStepStatus
import ai.daylight.assistant.domain.PlanStepStatus.DONE
import ai.daylight.assistant.domain.PlanStepStatus.IN_PROGRESS
import ai.daylight.assistant.domain.PlanStepStatus.PENDING
import ai.daylight.assistant.domain.SubagentState
import ai.daylight.assistant.domain.SubagentStatus
import ai.daylight.assistant.domain.SwarmPhase
import ai.daylight.assistant.domain.SwarmStatus
import ai.daylight.assistant.ui.dot.effects.StepState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlanCardModelTest {
    private fun plan(vararg statuses: PlanStepStatus) =
        AgentPlan("c", statuses.mapIndexed { i, s -> PlanStep("Step $i", s) })

    private val mockup = plan(DONE, DONE, IN_PROGRESS, PENDING, PENDING)

    @Test fun theCounterReadsLikeTheStripWhileLive() {
        assertThat(PlanCardModel.counter(mockup, live = true)).isEqualTo("[ 3/5 ]")
        assertThat(PlanCardModel.counter(mockup, live = false)).isEqualTo("[ 2/5 ]")
        assertThat(PlanCardModel.counter(plan(DONE, DONE), live = true)).isEqualTo("[ 2/2 ]")
    }

    @Test fun progressDotsCountHalfOfTheStepInProgress() {
        // The kit mockup: step 3 of 5 running lights 12 of 24 dots.
        assertThat(PlanCardModel.litDots(mockup, live = true)).isEqualTo(12)
        assertThat(PlanCardModel.litDots(mockup, live = false)).isEqualTo(10)
        assertThat(PlanCardModel.litDots(plan(DONE, DONE, DONE), live = false)).isEqualTo(24)
        assertThat(PlanCardModel.litDots(AgentPlan("c", emptyList()), live = true)).isEqualTo(0)
    }

    @Test fun aStepInProgressOnlyChasesWhileTheRunIsLive() {
        assertThat(PlanCardModel.stepState(IN_PROGRESS, live = true)).isEqualTo(StepState.Active)
        assertThat(PlanCardModel.stepState(IN_PROGRESS, live = false)).isEqualTo(StepState.Pending)
        assertThat(PlanCardModel.stepState(DONE, live = true)).isEqualTo(StepState.Done)
        assertThat(PlanCardModel.stepState(PENDING, live = true)).isEqualTo(StepState.Pending)
    }

    @Test fun longPlansFoldToTheCurrentStepAndItsNeighbours() {
        assertThat(PlanCardModel.visibleSteps(mockup.steps.map { it.status }, unfolded = false)).isEqualTo(0..4)
        val long = listOf(DONE, DONE, DONE, DONE, IN_PROGRESS, PENDING, PENDING, PENDING)
        assertThat(PlanCardModel.visibleSteps(long, unfolded = false)).isEqualTo(3..5)
        assertThat(PlanCardModel.visibleSteps(long, unfolded = true)).isEqualTo(0..7)
        assertThat(PlanCardModel.visibleSteps(List(8) { PENDING }, unfolded = false)).isEqualTo(0..2)
        assertThat(PlanCardModel.visibleSteps(List(8) { DONE }, unfolded = false)).isEqualTo(5..7)
    }

    @Test fun swarmCaptionsAndMarks() {
        val running = SwarmStatus(
            SwarmPhase.RUNNING,
            listOf(
                SubagentStatus(0, "a", SubagentState.RUNNING),
                SubagentStatus(1, "b", SubagentState.RUNNING),
                SubagentStatus(2, "c", SubagentState.PENDING)
            )
        )
        assertThat(PlanCardModel.swarmCaption(running)).isEqualTo("2 working")
        assertThat(PlanCardModel.swarmCaption(SwarmStatus(SwarmPhase.RUNNING))).isEqualTo("Queued")
        assertThat(PlanCardModel.swarmCaption(SwarmStatus(SwarmPhase.SYNTHESIZING))).isEqualTo("Merging")
        assertThat(PlanCardModel.swarmActive(running)).isTrue()
        assertThat(PlanCardModel.swarmActive(SwarmStatus(SwarmPhase.DONE))).isFalse()
        assertThat(PlanCardModel.subagentState(SubagentState.FAILED)).isEqualTo(StepState.Failed)
        assertThat(PlanCardModel.subagentState(SubagentState.RUNNING)).isEqualTo(StepState.Active)
    }
}
