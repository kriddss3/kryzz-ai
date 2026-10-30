package ai.daylight.assistant.ui.dot.mascot

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Phase D: the squid's arm wave as frame swaps. */
class SquidWaveTest {
    @Test fun waveLeavesFromAndSettlesOnTheRestingPose() {
        assertThat(SquidWave.frames).hasSize(SquidWave.COUNT)
        assertThat(SquidWave.frame(0).contentEquals(SquidPose.STANDBY.grid)).isTrue()
        assertThat(SquidWave.frame(SquidWave.COUNT).contentEquals(SquidPose.STANDBY.grid)).isTrue()
        assertThat(SquidWave.frame(-1).contentEquals(SquidWave.frame(SquidWave.COUNT - 1))).isTrue()
    }

    @Test fun onlyTheArmsMove() {
        val head = 0 until 14 * SquidSprite.COLS
        SquidWave.frames.forEach { frame ->
            head.forEach { i -> assertThat(frame[i]).isEqualTo(SquidPose.STANDBY.grid[i]) }
        }
        // The arms really do move: most frames differ from the resting pose.
        val moving = SquidWave.frames.count { !it.contentEquals(SquidPose.STANDBY.grid) }
        assertThat(moving).isAtLeast(SquidWave.COUNT / 2)
    }
}
