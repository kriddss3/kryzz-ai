package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentPlanParserTest {

    @Test fun parsesTheSchemaShape() {
        val steps = AgentPlanParser.parse(
            """{"steps":[{"title":"Search reviews","status":"done"},{"title":"Read top 2","status":"in_progress"},{"title":"Write summary","status":"pending"}]}"""
        )
        assertThat(steps).containsExactly(
            PlanStep("Search reviews", PlanStepStatus.DONE),
            PlanStep("Read top 2", PlanStepStatus.IN_PROGRESS),
            PlanStep("Write summary", PlanStepStatus.PENDING)
        ).inOrder()
        assertThat(AgentPlan("c", steps!!).doneCount).isEqualTo(1)
    }

    @Test fun acceptsStatusSynonymsAndPlainStrings() {
        val steps = AgentPlanParser.parse(
            """{"plan":["Outline", {"step":"Draft","status":"In Progress"}, {"text":"Check","status":"completed"}, {"title":"Ship","status":"??"}]}"""
        )!!
        assertThat(steps.map { it.status }).containsExactly(
            PlanStepStatus.PENDING,
            PlanStepStatus.IN_PROGRESS,
            PlanStepStatus.DONE,
            PlanStepStatus.PENDING
        ).inOrder()
    }

    @Test fun trimsDedupesAndCaps() {
        val many = (1..12).joinToString(",") { """{"title":"  Step   $it  ","status":"pending"}""" }
        val steps = AgentPlanParser.parse("""{"steps":[$many,{"title":"step 1","status":"done"}]}""")!!
        assertThat(steps).hasSize(AgentPlanParser.MAX_STEPS)
        assertThat(steps.first().title).isEqualTo("Step 1")
        val long = AgentPlanParser.parse("""{"steps":["${"x".repeat(200)}"]}""")!!
        assertThat(long.single().title).hasLength(AgentPlanParser.MAX_TITLE_CHARS)
    }

    @Test fun rejectsArgumentsWithNoUsableStep() {
        assertThat(AgentPlanParser.parse("")).isNull()
        assertThat(AgentPlanParser.parse("not json")).isNull()
        assertThat(AgentPlanParser.parse("""{"steps":[]}""")).isNull()
        assertThat(AgentPlanParser.parse("""{"steps":[{"title":"  "}]}""")).isNull()
        assertThat(AgentPlanParser.parse("""{"other":1}""")).isNull()
    }
}
