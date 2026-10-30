package ai.daylight.assistant.ui.dot.effects

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.createComposeRule
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase D: with motion off (the app setting or the system animator scale) nothing steps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DotMotionGateTest {
    @get:Rule val rule = createComposeRule()

    private fun ticker(motion: Boolean, running: Boolean = true): IntState {
        var state: IntState? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalKryzzMotionEnabled provides motion) {
                state = rememberDotTicker(28, running = running)
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        return checkNotNull(state)
    }

    @Test fun tickerCountsWhileMotionIsOn() {
        assertThat(ticker(motion = true).intValue).isAtLeast(10)
    }

    @Test fun tickerHoldsStillWhenMotionIsOff() {
        assertThat(ticker(motion = false).intValue).isEqualTo(0)
    }

    @Test fun tickerHoldsStillWhenNothingRuns() {
        assertThat(ticker(motion = true, running = false).intValue).isEqualTo(0)
    }

    @Test fun blinkStaysLitWhenMotionIsOff() {
        var lit: State<Boolean>? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalKryzzMotionEnabled provides false) {
                lit = rememberDotBlink()
            }
        }
        repeat(10) {
            rule.mainClock.advanceTimeBy(137)
            assertThat(checkNotNull(lit).value).isTrue()
        }
    }
}
