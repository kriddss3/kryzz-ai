package ai.daylight.assistant.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogoTapTest {
    @Test
    fun threeQuickTapsOpenAboutAndResetProgress() {
        val first = registerLogoTap(LogoTapProgress(), 1_000)
        val second = registerLogoTap(first.progress, 1_300)
        val third = registerLogoTap(second.progress, 1_600)

        assertThat(first.openAbout).isFalse()
        assertThat(second.openAbout).isFalse()
        assertThat(third.openAbout).isTrue()
        assertThat(third.progress).isEqualTo(LogoTapProgress())
    }

    @Test
    fun slowTapStartsANewSequence() {
        val first = registerLogoTap(LogoTapProgress(), 1_000)
        val late = registerLogoTap(first.progress, 2_000)

        assertThat(late.openAbout).isFalse()
        assertThat(late.progress.count).isEqualTo(1)
    }
}
