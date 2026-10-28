package ai.daylight.assistant.ui.run

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RunCostTest {
    @Test fun dollarsKeepSmallCostsReadable() {
        assertThat(RunCost.usd(0.0)).isEqualTo("$0.00")
        assertThat(RunCost.usd(0.0004)).isEqualTo("<$0.001")
        assertThat(RunCost.usd(0.004)).isEqualTo("$0.004")
        assertThat(RunCost.usd(0.21)).isEqualTo("$0.21")
        assertThat(RunCost.usd(1.234)).isEqualTo("$1.23")
    }

    @Test fun aFinishedRunComparesItsCostWithTheCap() {
        val r = RunCost.readout(costUsd = 0.21, totalTokens = 9000, capCents = 50, running = false, finished = true, latest = true)!!
        assertThat(r.text).isEqualTo("This run: $0.21 of $0.50")
        assertThat(r.showBar).isTrue()
        assertThat(r.lit).isEqualTo(5) // 0.42 of 12 dots, as in the kit mockup
        assertThat(r.warn).isFalse()
    }

    @Test fun theBarTurnsYellowNearTheCapAndNeverOverflows() {
        assertThat(RunCost.readout(0.45, null, 50, running = false, finished = true, latest = true)!!.let { it.lit to it.warn })
            .isEqualTo(11 to true)
        assertThat(RunCost.readout(0.90, null, 50, running = false, finished = true, latest = true)!!.lit).isEqualTo(RunCost.DOTS)
        // Any spend lights at least one dot.
        assertThat(RunCost.readout(0.0001, null, 50, running = false, finished = true, latest = true)!!.lit).isEqualTo(1)
    }

    @Test fun aRunningRunShowsItsCapNotAMadeUpCost() {
        val capped = RunCost.readout(null, null, 50, running = true, finished = false, latest = true)!!
        assertThat(capped.text).isEqualTo("Cap $0.50, cost when done")
        assertThat(capped.showBar).isTrue()
        assertThat(capped.lit).isEqualTo(0)
        val open = RunCost.readout(null, null, 0, running = true, finished = false, latest = true)!!
        assertThat(open.text).isEqualTo("No cap, cost when done")
        assertThat(open.showBar).isFalse()
    }

    @Test fun capOffStatesTheCostOnly() {
        val r = RunCost.readout(0.21, null, 0, running = false, finished = true, latest = true)!!
        assertThat(r.text).isEqualTo("This run: $0.21, no cap")
        assertThat(r.showBar).isFalse()
    }

    @Test fun noReportedCostFallsBackToTokensOrSaysSo() {
        assertThat(RunCost.readout(null, 12400, 50, running = false, finished = true, latest = true)!!.text)
            .isEqualTo("This run: 12,400 tok, no cost reported")
        assertThat(RunCost.readout(null, null, 50, running = false, finished = true, latest = true)!!.text)
            .isEqualTo("This run: no cost reported")
        assertThat(RunCost.readout(null, null, 50, running = false, finished = true, latest = false)).isNull()
    }

    @Test fun anEarlierRunOnlyStatesItsCost() {
        val r = RunCost.readout(0.04, null, 50, running = false, finished = true, latest = false)!!
        assertThat(r.text).isEqualTo("Run cost $0.04")
        assertThat(r.showBar).isFalse()
    }

    @Test fun aStoppedRunSavesNoCostSoTheLineIsHidden() {
        assertThat(RunCost.readout(null, null, 50, running = false, finished = false, latest = true)).isNull()
    }
}
