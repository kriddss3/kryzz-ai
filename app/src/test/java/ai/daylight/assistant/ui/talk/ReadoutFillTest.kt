package ai.daylight.assistant.ui.talk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Phase D: the live source segment fills one dot per step, holds, and starts again. */
class ReadoutFillTest {
    @Test fun fillsOneDotPerStepThenHolds() {
        val cycle = 12 + ReadoutFill.HOLD_STEPS
        val lit = (0 until cycle).map(ReadoutFill::lit)
        assertThat(lit.take(12)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12).inOrder()
        assertThat(lit.drop(12).toSet()).containsExactly(12)
        assertThat(ReadoutFill.lit(cycle)).isEqualTo(1)
    }

    @Test fun stepsAtTheKitDotStep() {
        assertThat(ReadoutFill.STEP_MS).isEqualTo(28)
        assertThat(ReadoutFill.STILL).isIn(1..12)
    }
}
