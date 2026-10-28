package ai.daylight.assistant.ui

import ai.daylight.assistant.data.preferences.SettingsState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DotBackgroundTest {
    @Test fun theDotBackgroundIsOffUntilTurnedOnInTune() {
        // Additive: an upgrade keeps today's plain background.
        assertThat(SettingsState().dotBackground).isFalse()
    }

    @Test fun theGridShowsOnHomeTalkAndRunOnly() {
        assertThat(dotGridForRoute(Routes.HOME)).isTrue()
        assertThat(dotGridForRoute(Routes.CHAT)).isTrue()
        assertThat(dotGridForRoute(Routes.chat("c1"))).isTrue()
        assertThat(dotGridForRoute(Routes.SETTINGS)).isFalse()
        assertThat(dotGridForRoute(Routes.API)).isFalse()
        assertThat(dotGridForRoute(Routes.LINK)).isFalse()
        assertThat(dotGridForRoute(Routes.ONBOARDING)).isFalse()
        assertThat(dotGridForRoute(null)).isFalse()
    }

    @Test fun theGridIsFaint() {
        // components.md section 1: 6% behind text.
        assertThat(DOT_GRID_ALPHA).isWithin(0.0001f).of(0.06f)
    }
}
