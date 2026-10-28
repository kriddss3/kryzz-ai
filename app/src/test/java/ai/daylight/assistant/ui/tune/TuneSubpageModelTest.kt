package ai.daylight.assistant.ui.tune

import ai.daylight.assistant.domain.AgentTurnPolicy
import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TuneSubpageModelTest {
    @Test fun textSizeKeepsTheOldSlidersElevenSteps() {
        val stops = TuneSubpageModel.FONT_SCALE_STOPS
        assertThat(stops).hasSize(11)
        assertThat(stops.first()).isWithin(1e-6f).of(0.85f)
        assertThat(stops.last()).isWithin(1e-6f).of(1.35f)
        assertThat(stops).isInStrictOrder()
        assertThat(stops[TuneSubpageModel.nearest(stops, 1f)]).isWithin(1e-6f).of(1f)
        // A float saved by the old Slider lands on its dot.
        assertThat(TuneSubpageModel.nearest(stops, 0.90000004f)).isEqualTo(1)
        assertThat(TuneSubpageModel.nearest(stops, 9f)).isEqualTo(stops.lastIndex)
    }

    @Test fun speakingSpeedRunsFromHalfToDoubleInTenths() {
        val stops = TuneSubpageModel.SPEECH_SPEED_STOPS
        assertThat(stops).hasSize(16)
        assertThat(stops.first()).isWithin(1e-6f).of(0.5f)
        assertThat(stops.last()).isWithin(1e-6f).of(2f)
        assertThat(stops[TuneSubpageModel.nearest(stops, 1f)]).isWithin(1e-6f).of(1f)
        assertThat(TuneSubpageModel.speed(1f)).isEqualTo("1.0X")
        assertThat(TuneSubpageModel.speed(1.5f)).isEqualTo("1.5X")
        assertThat(TuneSubpageModel.speedSpoken(0.5f)).isEqualTo("0.5 times")
    }

    @Test fun searchContextUsesTwentyFiveDotsAndKeepsTheDefault() {
        val stops = TuneSubpageModel.SEARCH_CHAR_STOPS
        assertThat(stops).hasSize(25)
        assertThat(stops.first()).isEqualTo(2_000)
        assertThat(stops.last()).isEqualTo(50_000)
        // The 12k default sits exactly on a dot.
        assertThat(stops[TuneSubpageModel.nearestInt(stops, 12_000)]).isEqualTo(12_000)
        // An odd value from the old 1k slider shows at the lower of two equal neighbours.
        assertThat(stops[TuneSubpageModel.nearestInt(stops, 7_000)]).isEqualTo(6_000)
        assertThat(TuneSubpageModel.chars(12_000)).isEqualTo("12K")
    }

    @Test fun stepBudgetOffersEveryAllowedValue() {
        val stops = TuneSubpageModel.STEP_BUDGET_STOPS
        assertThat(stops.first()).isEqualTo(AgentTurnPolicy.MIN_STEP_BUDGET)
        assertThat(stops.last()).isEqualTo(AgentTurnPolicy.MAX_STEP_BUDGET)
        assertThat(stops).hasSize(AgentTurnPolicy.MAX_STEP_BUDGET - AgentTurnPolicy.MIN_STEP_BUDGET + 1)
        assertThat(stops[TuneSubpageModel.nearestInt(stops, AgentTurnPolicy.DEFAULT_STEP_BUDGET)])
            .isEqualTo(AgentTurnPolicy.DEFAULT_STEP_BUDGET)
    }

    @Test fun aStoredKeyShowsOnlyDotsAndItsLastFour() {
        assertThat(TuneSubpageModel.maskedKey(stored = false, tail = "9F2A")).isNull()
        assertThat(TuneSubpageModel.maskedKey(stored = true, tail = "9F2A")).isEqualTo("•••• •••• 9F2A")
        // A key too short to show its end is all dots.
        assertThat(TuneSubpageModel.maskedKey(stored = true, tail = null)).isEqualTo("•••• •••• ••••")
        assertThat(TuneSubpageModel.keyRowCaption(stored = false, tail = null)).isEqualTo("No key stored")
        assertThat(TuneSubpageModel.keyRowCaption(stored = true, tail = "9F2A")).isEqualTo("Ends 9F2A, encrypted")
        assertThat(TuneSubpageModel.keyRowCaption(stored = true, tail = null)).isEqualTo("Stored, encrypted")
    }

    @Test fun everySliderValueIsDrawableInDotText() {
        val values = TuneSubpageModel.FONT_SCALE_STOPS.map(TuneSubpageModel::percent) +
            TuneSubpageModel.SPEECH_SPEED_STOPS.map(TuneSubpageModel::speed) +
            TuneSubpageModel.SEARCH_CHAR_STOPS.map(TuneSubpageModel::chars) +
            TuneSubpageModel.STEP_BUDGET_STOPS.map(Int::toString) +
            TuneModel.CAP_STOPS.map(TuneModel::capValue)
        values.forEach { value ->
            value.uppercase().forEach { ch ->
                assertThat(DotFont5x7.glyphs.keys).contains(ch)
            }
        }
        assertThat(TuneSubpageModel.percent(1f)).isEqualTo("100%")
        assertThat(TuneSubpageModel.percent(0.85f)).isEqualTo("85%")
    }
}
