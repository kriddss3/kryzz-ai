package ai.daylight.assistant.ui.dot.effects

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Phase D: the stepped timing behind the LED effects. */
class DotClockTest {
    @Test fun blinkIsOnForItsOnTimeThenOff() {
        val on = (0L until 1_000L).count { DotSteps.blinkOn(it, DotSteps.BLINK_ON, DotSteps.BLINK_OFF) }
        assertThat(on).isEqualTo(600)
        assertThat(DotSteps.blinkOn(0, 600, 400)).isTrue()
        assertThat(DotSteps.blinkOn(599, 600, 400)).isTrue()
        assertThat(DotSteps.blinkOn(600, 600, 400)).isFalse()
        assertThat(DotSteps.blinkOn(999, 600, 400)).isFalse()
        assertThat(DotSteps.blinkOn(1_000, 600, 400)).isTrue()
    }

    @Test fun blinksOnTheSameClockAreInStep() {
        // Two LEDs reading the same uptime agree, whenever each one started.
        (0L..5_000L step 37).forEach { t ->
            assertThat(DotSteps.blinkOn(t, 600, 400)).isEqualTo(DotSteps.blinkOn(t + 10_000, 600, 400))
        }
    }

    @Test fun nextEdgeLandsExactlyOnTheChange() {
        listOf(0L, 1L, 599L, 600L, 750L, 999L, 12_345L).forEach { t ->
            val wait = DotSteps.msToNextEdge(t, 600, 400)
            assertThat(wait).isAtLeast(1L)
            assertThat(DotSteps.blinkOn(t + wait, 600, 400)).isNotEqualTo(DotSteps.blinkOn(t + wait - 1, 600, 400))
        }
    }

    @Test fun barMovesOneSlotAtATimeTowardsItsTarget() {
        var at = 0
        val path = mutableListOf(at)
        while (at != 7) {
            at = DotSteps.stepToward(at, 7)
            path += at
        }
        assertThat(path).containsExactly(0, 1, 2, 3, 4, 5, 6, 7).inOrder()
        assertThat(DotSteps.stepToward(5, 2)).isEqualTo(4)
        assertThat(DotSteps.stepToward(3, 3)).isEqualTo(3)
    }

    @Test fun thinkingDotsLightOneMoreEachStepThenReset() {
        assertThat((0 until 8).map(::thinkingLit)).containsExactly(0, 1, 2, 3, 0, 1, 2, 3).inOrder()
    }

    @Test fun indeterminateProgressIsAThreeDotComet() {
        assertThat((0 until 24).filter { cometDot(it, head = 5) }).containsExactly(2, 3, 4).inOrder()
        assertThat((0 until 24).count { cometDot(it, head = 1) }).isEqualTo(1) // entering from the left
    }
}
