package ai.daylight.assistant.ui.dot

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Phase D: a working bot's yellow diagonals step across its face. */
class IdenticonLiveTest {
    @Test fun phaseZeroIsTheKitRule() {
        (0 until 7).forEach { x ->
            (0 until 7).forEach { y ->
                assertThat(Identicon.liveDot(x, y, phase = 0)).isEqualTo((x + y) % 3 == 0)
                assertThat(Identicon.liveDot(x, y)).isEqualTo((x + y) % 3 == 0)
            }
        }
    }

    @Test fun eachStepMovesTheDiagonalsAndThreeStepsComeRound() {
        (0 until 7).forEach { x ->
            (0 until 7).forEach { y ->
                assertThat(Identicon.liveDot(x + 1, y, phase = 1)).isEqualTo(Identicon.liveDot(x, y, phase = 0))
                assertThat(Identicon.liveDot(x, y, phase = 3)).isEqualTo(Identicon.liveDot(x, y, phase = 0))
            }
        }
        // Every cell is yellow on exactly one of three steps.
        assertThat((0 until 3).count { Identicon.liveDot(2, 5, it) }).isEqualTo(1)
    }
}
