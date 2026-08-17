package ai.daylight.assistant.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SidePanelSwipeTest {
    @Test
    fun rightwardFromLeftEdgeOpensWhileClosed() {
        assertThat(panelDragIsMeaningful(deltaX = 24f, progress = 0f, downX = 40f, leftEdgePx = 320f)).isTrue()
    }

    @Test
    fun rightwardFromMidScreenDoesNotOpenWhileClosed() {
        assertThat(panelDragIsMeaningful(deltaX = 24f, progress = 0f, downX = 400f, leftEdgePx = 320f)).isFalse()
    }

    @Test
    fun leftwardDoesNotCloseWhileClosed() {
        assertThat(panelDragIsMeaningful(deltaX = -24f, progress = 0f, downX = 40f, leftEdgePx = 320f)).isFalse()
    }

    @Test
    fun leftwardClosesWhileOpen() {
        assertThat(panelDragIsMeaningful(deltaX = -24f, progress = 1f, downX = 500f, leftEdgePx = 320f)).isTrue()
    }

    @Test
    fun rightwardFromAnywhereIsIgnoredWhenAlreadyOpen() {
        assertThat(panelDragIsMeaningful(deltaX = 24f, progress = 1f, downX = 40f, leftEdgePx = 320f)).isFalse()
    }

    @Test
    fun settleUsesSameFivePercentDistanceInEitherDirection() {
        assertThat(panelShouldSettleOpen(0f, 0.04f)).isFalse()
        assertThat(panelShouldSettleOpen(0f, 0.05f)).isTrue()
        assertThat(panelShouldSettleOpen(1f, 0.96f)).isTrue()
        assertThat(panelShouldSettleOpen(1f, 0.95f)).isFalse()
        assertThat(panelShouldSettleOpen(1f, 0.94f)).isFalse()
    }

    @Test
    fun openSwipeProgressStillUsesAccumulatedDistance() {
        val dragged = panelProgressAfterDrag(progress = 0f, deltaX = 32f, panelWidthPx = 320f)

        assertThat(dragged).isEqualTo(0.1f)
        assertThat(panelShouldSettleOpen(0f, dragged)).isTrue()
    }

    @Test
    fun dragProgressIsClampedToPanelBounds() {
        assertThat(panelProgressAfterDrag(0.95f, 64f, 320f)).isEqualTo(1f)
        assertThat(panelProgressAfterDrag(0.05f, -64f, 320f)).isEqualTo(0f)
    }

    @Test
    fun sidePanelAllowedOnTopLevelRoutes() {
        assertThat(sidePanelOpenAllowedForRoute("settings")).isTrue()
        assertThat(sidePanelOpenAllowedForRoute("chat/{conversationId}?mode={mode}&capability={capability}&voice={voice}")).isTrue()
        assertThat(sidePanelOpenAllowedForRoute("onboarding")).isTrue()
        assertThat(sidePanelOpenAllowedForRoute("boot")).isTrue()
    }

    @Test
    fun sidePanelBlockedOnSettingsSubpages() {
        assertThat(sidePanelOpenAllowedForRoute("settings/api")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("settings/voice")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("settings/appearance")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("settings/persona")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("settings/tools")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("settings/privacy")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("settings/memory")).isFalse()
    }

    @Test
    fun sidePanelBlockedOnOtherDepthRoutes() {
        assertThat(sidePanelOpenAllowedForRoute("models/CHAT")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("skills")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("llms")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("cron")).isFalse()
        assertThat(sidePanelOpenAllowedForRoute(null)).isFalse()
        assertThat(sidePanelOpenAllowedForRoute("")).isFalse()
    }
 }
