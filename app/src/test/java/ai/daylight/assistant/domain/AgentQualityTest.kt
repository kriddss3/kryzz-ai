package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** v5.10: what each Agent quality preset runs a turn with. */
class AgentQualityTest {

    private val user = AgentQualityPolicy.UserSettings(
        agentModel = "google/agent",
        researchModel = "google/research",
        maxModel = "anthropic/max",
        modelReasoning = emptyMap(),
        stepBudget = 16,
        reviewAnswers = true,
        costCapCents = 25
    )

    @Test fun balancedIsExactlyTheUsersOwnSettings() {
        val turn = AgentQualityPolicy.resolve(AgentQuality.BALANCED, user, research = false)
        assertThat(turn).isEqualTo(
            AgentQualityPolicy.TurnSettings(
                model = "google/agent",
                reasoning = ReasoningEffort.AUTO,
                stepBudget = 16,
                review = true,
                costCapCents = 25
            )
        )
        val withoutReview = AgentQualityPolicy.resolve(AgentQuality.BALANCED, user.copy(reviewAnswers = false), research = true)
        assertThat(withoutReview.model).isEqualTo("google/research")
        assertThat(withoutReview.review).isFalse()
    }

    @Test fun fastLowersReasoningBudgetAndSkipsReview() {
        val turn = AgentQualityPolicy.resolve(AgentQuality.FAST, user, research = false)
        assertThat(turn.model).isEqualTo("google/agent")
        assertThat(turn.reasoning).isEqualTo(ReasoningEffort.LOW)
        assertThat(turn.stepBudget).isEqualTo(AgentQualityPolicy.FAST_STEP_BUDGET)
        assertThat(turn.review).isFalse()
        assertThat(turn.costCapCents).isEqualTo(25)
        // A smaller budget of the user's own is kept.
        assertThat(AgentQualityPolicy.resolve(AgentQuality.FAST, user.copy(stepBudget = 5), research = false).stepBudget)
            .isEqualTo(5)
        assertThat(AgentQualityPolicy.resolve(AgentQuality.FAST, user, research = true).model).isEqualTo("google/research")
    }

    @Test fun maxUsesTheMaxSlotWithFullBudgetReviewAndARaisedCap() {
        val turn = AgentQualityPolicy.resolve(AgentQuality.MAX, user.copy(reviewAnswers = false, stepBudget = 6), research = false)
        assertThat(turn.model).isEqualTo("anthropic/max")
        assertThat(turn.reasoning).isEqualTo(ReasoningEffort.HIGH)
        assertThat(turn.stepBudget).isEqualTo(24)
        assertThat(turn.review).isTrue()
        assertThat(turn.costCapCents).isEqualTo(100)
        // Research turns run on the Max slot too.
        assertThat(AgentQualityPolicy.resolve(AgentQuality.MAX, user, research = true).model).isEqualTo("anthropic/max")
        // A higher cap is kept, and a cap switched Off stays Off.
        assertThat(AgentQualityPolicy.resolve(AgentQuality.MAX, user.copy(costCapCents = 200), research = false).costCapCents)
            .isEqualTo(200)
        assertThat(AgentQualityPolicy.resolve(AgentQuality.MAX, user.copy(costCapCents = 0), research = false).costCapCents)
            .isEqualTo(0)
    }

    @Test fun blankMaxSlotFallsBackToTheAgentModel() {
        val blank = user.copy(maxModel = "  ")
        assertThat(AgentQualityPolicy.resolve(AgentQuality.MAX, blank, research = false).model).isEqualTo("google/agent")
        assertThat(AgentQualityPolicy.resolve(AgentQuality.MAX, blank, research = true).model).isEqualTo("google/agent")
    }

    @Test fun anExplicitPerModelReasoningChoiceWinsOverThePreset() {
        val chosen = user.copy(
            modelReasoning = mapOf("google/agent" to ReasoningEffort.MEDIUM, "anthropic/max" to ReasoningEffort.NONE)
        )
        assertThat(AgentQualityPolicy.resolve(AgentQuality.FAST, chosen, research = false).reasoning)
            .isEqualTo(ReasoningEffort.MEDIUM)
        assertThat(AgentQualityPolicy.resolve(AgentQuality.BALANCED, chosen, research = false).reasoning)
            .isEqualTo(ReasoningEffort.MEDIUM)
        // An explicit Off on the Max model is honoured too.
        assertThat(AgentQualityPolicy.resolve(AgentQuality.MAX, chosen, research = false).reasoning)
            .isEqualTo(ReasoningEffort.NONE)
    }

    @Test fun presetsUseEffortsEveryReasoningModelAccepts() {
        // LOW and HIGH are sent as effort values; AUTO sends no reasoning field at all.
        assertThat(ReasoningEffort.LOW.apiValue).isEqualTo("low")
        assertThat(ReasoningEffort.HIGH.apiValue).isEqualTo("high")
        assertThat(ReasoningEffort.AUTO.apiValue).isNull()
    }

    @Test fun storedValuesParseWithBalancedAsTheDefault() {
        assertThat(AgentQuality.from("MAX")).isEqualTo(AgentQuality.MAX)
        assertThat(AgentQuality.from("fast")).isEqualTo(AgentQuality.FAST)
        assertThat(AgentQuality.from(null)).isEqualTo(AgentQuality.BALANCED)
        assertThat(AgentQuality.from("turbo")).isEqualTo(AgentQuality.BALANCED)
    }
}
