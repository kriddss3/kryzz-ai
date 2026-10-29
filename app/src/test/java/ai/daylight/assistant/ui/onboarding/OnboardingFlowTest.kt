package ai.daylight.assistant.ui.onboarding

import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnboardingFlowTest {
    @Test fun fourStepsInTheKitOrder() {
        assertThat(OnboardingFlow.steps).containsExactly(
            OnboardingStep.LOCAL, OnboardingStep.KEYS, OnboardingStep.MEMORY, OnboardingStep.PRIVACY
        ).inOrder()
        assertThat(OnboardingFlow.count).isEqualTo(4)
    }

    @Test fun counterReadsLikeTheKit() {
        assertThat(OnboardingFlow.counter(0)).isEqualTo("( 1 / 4 )")
        assertThat(OnboardingFlow.counter(3)).isEqualTo("( 4 / 4 )")
        // Out of range clamps instead of showing "( 5 / 4 )".
        assertThat(OnboardingFlow.counter(9)).isEqualTo("( 4 / 4 )")
        assertThat(OnboardingFlow.counter(-1)).isEqualTo("( 1 / 4 )")
    }

    @Test fun titlesAreAtMostTwoWordsTheDotFaceCanDraw() {
        OnboardingStep.entries.forEach { step ->
            assertThat(step.title.split(' ').size).isAtMost(2)
            assertThat(step.title).isEqualTo(step.title.uppercase())
            step.title.forEach { ch -> assertThat(DotFont5x7.supports(ch)).isTrue() }
        }
    }

    @Test fun bodiesFitThreeLines() {
        // Geist 15 on a 364dp column holds about 45 characters a line.
        OnboardingStep.entries.forEach { assertThat(it.body.length).isAtMost(135) }
    }

    @Test fun continueAndBackWalkTheSteps() {
        assertThat(OnboardingFlow.next(0)).isEqualTo(1)
        assertThat(OnboardingFlow.next(3)).isEqualTo(3)
        assertThat(OnboardingFlow.back(0)).isNull()
        assertThat(OnboardingFlow.back(2)).isEqualTo(1)
        assertThat(OnboardingFlow.isLast(3)).isTrue()
        assertThat(OnboardingFlow.isLast(2)).isFalse()
        assertThat(OnboardingFlow.primaryLabel(0)).isEqualTo("Continue")
        assertThat(OnboardingFlow.primaryLabel(3)).isEqualTo("Start")
    }

    @Test fun theKeysStepNeedsAnOpenRouterKey() {
        val keys = OnboardingStep.entries.indexOf(OnboardingStep.KEYS)
        assertThat(OnboardingFlow.canContinue(keys, "", openRouterVerified = false)).isFalse()
        assertThat(OnboardingFlow.canContinue(keys, "   ", openRouterVerified = false)).isFalse()
        assertThat(OnboardingFlow.canContinue(keys, "sk-or-1", openRouterVerified = false)).isTrue()
        // A key tested and stored counts even after the field was cleared.
        assertThat(OnboardingFlow.canContinue(keys, "", openRouterVerified = true)).isTrue()
        // The other steps never wait for a key.
        listOf(0, 2, 3).forEach { assertThat(OnboardingFlow.canContinue(it, "", false)).isTrue() }
    }

    @Test fun skipFinishesFromTheKeysAndMemorySteps() {
        assertThat((0..3).filter { OnboardingFlow.showsSkip(it) }).containsExactly(1, 2)
        assertThat(OnboardingFlow.canSkip(1, "", false)).isFalse()
        assertThat(OnboardingFlow.canSkip(1, "sk-or-1", false)).isTrue()
        assertThat(OnboardingFlow.canSkip(2, "sk-or-1", false)).isTrue()
        assertThat(OnboardingFlow.canSkip(0, "sk-or-1", false)).isFalse()
        assertThat(OnboardingFlow.canSkip(3, "sk-or-1", false)).isFalse()
    }

    @Test fun keyTagsAndIdleLine() {
        assertThat(OnboardingFlow.requirement(true)).isEqualTo("Required")
        assertThat(OnboardingFlow.requirement(false)).isEqualTo("Optional")
        assertThat(OnboardingFlow.keyIdle("")).isEmpty()
        assertThat(OnboardingFlow.keyIdle("x")).contains("Continue saves it")
    }
}
