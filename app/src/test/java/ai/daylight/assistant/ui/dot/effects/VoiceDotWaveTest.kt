package ai.daylight.assistant.ui.dot.effects

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceDotWaveTest {
    private fun column(level: Float, rows: Int = 15, peakFrom: Int? = null) =
        (0 until rows).map { waveDot(level, it, rows, peakFrom) }

    @Test fun silenceIsAFlatCentreLine() {
        val col = column(0f)
        assertThat(col.count { it != WaveDot.OFF }).isEqualTo(1)
        assertThat(col[7]).isEqualTo(WaveDot.ON)
    }

    @Test fun theColumnGrowsEvenlyFromTheCentre() {
        // Half of the 7-row half-height: 4 rows either side of the centre.
        val col = column(0.5f)
        assertThat(col.indices.filter { col[it] != WaveDot.OFF }).containsExactlyElementsIn(3..11)
    }

    @Test fun withoutPeakFromOnlyAFullColumnsEndsArePeaks() {
        assertThat(column(0.8f)).doesNotContain(WaveDot.PEAK)
        val full = column(1f)
        assertThat(full.indices.filter { full[it] == WaveDot.PEAK }).containsExactly(0, 14)
    }

    @Test fun peakFromColoursTheOuterRows() {
        // Kit voice field: lit dots 5 or more rows from the centre are yellow.
        val col = column(1f, peakFrom = 5)
        assertThat(col.indices.filter { col[it] == WaveDot.PEAK }).containsExactly(0, 1, 2, 12, 13, 14)
        assertThat(column(0.5f, peakFrom = 5)).doesNotContain(WaveDot.PEAK)
    }

    @Test fun badLevelsAreClampedOrSilent() {
        assertThat(column(Float.NaN)).isEqualTo(column(0f))
        assertThat(column(-2f)).isEqualTo(column(0f))
        assertThat(column(3f)).isEqualTo(column(1f))
    }
}
