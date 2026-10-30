package ai.daylight.assistant.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Phase D: the launch intro is a dot reveal plus a short hold, 900ms or less. */
class LaunchIntroTest {
    @Test fun introIsNoLongerThan900ms() {
        assertThat(LaunchIntroSpec.REVEAL_MS).isEqualTo(600)
        assertThat(LaunchIntroSpec.TOTAL_MS).isAtMost(900L)
    }
}
