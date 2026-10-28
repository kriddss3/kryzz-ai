package ai.daylight.assistant.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PanelYieldTest {
    @Test fun aDragAChildAlreadyTookStaysWithTheChild() {
        // Tune's cost slider consumed the move before the drawer engaged: the drawer keeps out.
        assertThat(panelYieldsToChild(engaged = false, moveConsumedByChild = true)).isTrue()
    }

    @Test fun anUnclaimedDragStillOpensTheDrawer() {
        assertThat(panelYieldsToChild(engaged = false, moveConsumedByChild = false)).isFalse()
    }

    @Test fun anEngagedDrawerKeepsItsDrag() {
        assertThat(panelYieldsToChild(engaged = true, moveConsumedByChild = true)).isFalse()
    }

    @Test fun backClosesAnOpenDrawerBeforeLeavingTheScreen() {
        // The NavHost's own pop would otherwise win and leave Tune or Talk with the drawer open.
        assertThat(backClosesDrawerFirst(panelOpen = true)).isTrue()
        assertThat(backClosesDrawerFirst(panelOpen = false)).isFalse()
    }
}
