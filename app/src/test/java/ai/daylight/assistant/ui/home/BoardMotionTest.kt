package ai.daylight.assistant.ui.home

import ai.daylight.assistant.ui.shell.Arm
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Phase D: lit cables, the waving squid and the finished-port flash. */
class BoardMotionTest {
    @Test fun litRunsMoveFromTheSquidTowardsThePort() {
        val at0 = (0 until 18).filter { BoardGeometry.busyDotLit(it, phase = 0) }
        val at1 = (0 until 18).filter { BoardGeometry.busyDotLit(it, phase = 1) }
        assertThat(at0).containsExactly(0, 1, 2, 9, 10, 11).inOrder()
        // Dot 0 sits under the squid: one step later every run is one dot nearer the port.
        assertThat(at1).containsExactly(1, 2, 3, 10, 11, 12).inOrder()
        // After nine steps the pattern is back where it started.
        assertThat((0 until 18).filter { BoardGeometry.busyDotLit(it, phase = 9) }).isEqualTo(at0)
    }

    @Test fun oneClockDrivesCablesAndSquid() {
        assertThat(BoardMotion.STEP_MS).isEqualTo(28)
        assertThat(BoardMotion.SQUID_EVERY).isEqualTo(5)
        assertThat((0 until 15).map(BoardMotion::squidStep)).containsExactly(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2).inOrder()
    }

    @Test fun aPortFlashesOnlyWhenItsWorkFinishes() {
        val before = mapOf(Arm.TALK to PortState.BUSY, Arm.RUN to PortState.BUSY, Arm.MAKE to PortState.IDLE)
        val now = listOf(
            ArmPort(Arm.TALK, "", PortState.IDLE),
            ArmPort(Arm.RUN, "", PortState.BUSY),
            ArmPort(Arm.MAKE, "", PortState.IDLE),
            ArmPort(Arm.LINK, "", PortState.UNLIT),
        )
        assertThat(BoardMotion.finished(before, now)).containsExactly(Arm.TALK)
        // Opening Home (nothing known before) never flashes.
        assertThat(BoardMotion.finished(emptyMap(), now)).isEmpty()
        assertThat(BoardMotion.FLASH_MS).isAtMost(400L)
    }
}
