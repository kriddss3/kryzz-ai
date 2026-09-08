package ai.daylight.assistant.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SidePanelStateTest {
    @Test
    fun freshSwipeOpenTargetIsHonouredWhileCallerCatchesUp() {
        // A swipe-to-open leaves `open` false for one frame until the caller
        // state flips to true. The drag target must survive that frame so the
        // drawer actually opens instead of snapping back closed.
        assertThat(panelAnimationTarget(open = false, settleTarget = 1f)).isEqualTo(1f)
        assertThat(shouldClearStaleOpenTarget(open = false, prevOpen = false, settleTarget = 1f)).isFalse()
    }

    @Test
    fun callerCloseWinsOverAStaleOpeningSettlement() {
        // Once the caller has flipped `open` true → false (scrim/back/conversation
        // tap) while a stale `1f` opening target lingers, the target is cleared
        // so the drawer cannot resurrect after it was just closed.
        assertThat(shouldClearStaleOpenTarget(open = false, prevOpen = true, settleTarget = 1f)).isTrue()
        // With the stale target cleared, the effect falls back to following `open`.
        assertThat(panelAnimationTarget(open = false, settleTarget = null)).isEqualTo(0f)
    }

    @Test
    fun openStateUsesTheLatestDragSettlement() {
        assertThat(panelAnimationTarget(open = true, settleTarget = 0f)).isEqualTo(0f)
        assertThat(panelAnimationTarget(open = true, settleTarget = null)).isEqualTo(1f)
    }

    @Test
    fun closeSettlementIsNeverTreatedAsStaleOpenTarget() {
        // A drag-to-close sets settleTarget = 0f, which must not be dropped by
        // the stale-open-target guard regardless of the open transition.
        assertThat(shouldClearStaleOpenTarget(open = false, prevOpen = true, settleTarget = 0f)).isFalse()
        assertThat(shouldClearStaleOpenTarget(open = false, prevOpen = false, settleTarget = 0f)).isFalse()
    }
}
