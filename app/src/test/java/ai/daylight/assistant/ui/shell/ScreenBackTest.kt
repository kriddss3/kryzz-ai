package ai.daylight.assistant.ui.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** C8: the shell's Back asks the screen first (Find's viewer sheet), in any callback order. */
class ScreenBackTest {
    @Test fun withNothingRegisteredTheShellKeepsItsOwnBack() {
        val state = ScreenBackState()
        assertThat(state.active).isFalse()
        assertThat(state.handle()).isFalse()
    }

    @Test fun theNewestScreenActionRunsAndLeavesWhenRemoved() {
        val state = ScreenBackState()
        val ran = mutableListOf<String>()
        val sheet = state.add { ran += "sheet" }
        val viewer = state.add { ran += "viewer" }
        assertThat(state.active).isTrue()
        assertThat(state.handle()).isTrue()
        assertThat(ran).containsExactly("viewer")
        state.remove(viewer)
        assertThat(state.handle()).isTrue()
        assertThat(ran).containsExactly("viewer", "sheet").inOrder()
        state.remove(sheet)
        assertThat(state.active).isFalse()
        assertThat(state.handle()).isFalse()
    }
}
