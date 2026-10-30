package ai.daylight.assistant.ui.dot.effects

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Phase D: dot develop, against kit/screens/src/dotimg.py. */
class DotDevelopTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun lumaUsesRec709Weights() {
        assertThat(DotDevelopModel.luma(argb(0, 0, 0))).isEqualTo(0f)
        assertThat(DotDevelopModel.luma(argb(255, 255, 255))).isWithin(1e-4f).of(1f)
        assertThat(DotDevelopModel.luma(argb(0, 255, 0))).isWithin(1e-4f).of(0.7152f)
    }

    @Test fun brightenMatchesTheKit() {
        // dotimg.py: min(255, int(v * 1.8 + 12)) per channel.
        assertThat(DotDevelopModel.brighten(argb(100, 0, 200))).isEqualTo(argb(192, 12, 255))
        assertThat(DotDevelopModel.brighten(argb(0, 0, 0)) ushr 24).isEqualTo(0xFF)
    }

    @Test fun dotsGrowWithBrightnessFromPoint8To3Point3() {
        assertThat(DotDevelopModel.radiusDp(0f)).isWithin(1e-4f).of(0.8f)
        assertThat(DotDevelopModel.radiusDp(1f)).isWithin(1e-3f).of(0.8f + 6f * 0.42f)
        // Above 1/1.8 brightness the dot is already full size.
        assertThat(DotDevelopModel.radiusDp(0.6f)).isEqualTo(DotDevelopModel.radiusDp(1f))
        assertThat(DotDevelopModel.radiusDp(0.2f)).isLessThan(DotDevelopModel.radiusDp(0.4f))
    }

    @Test fun cellsDevelopBehindARaggedFrontMovingDown() {
        assertThat(DotDevelopModel.cell(0.5f, 0.4f)).isEqualTo(DotDevelopModel.Cell.HIDDEN)
        assertThat(DotDevelopModel.cell(0.5f, 0.55f)).isEqualTo(DotDevelopModel.Cell.FRONT)
        assertThat(DotDevelopModel.cell(0.5f, 0.6f)).isEqualTo(DotDevelopModel.Cell.SHOWN)
        // Lower rows develop later; noise adds at most a quarter.
        assertThat(DotDevelopModel.threshold(0, 10, 0f)).isEqualTo(0f)
        assertThat(DotDevelopModel.threshold(9, 10, 0f)).isGreaterThan(DotDevelopModel.threshold(2, 10, 0f))
        assertThat(DotDevelopModel.threshold(9, 10, 1f)).isAtMost(1f)
    }

    @Test fun progressEndsWithEveryCellShown() {
        assertThat(DotDevelopModel.progressAt(0)).isEqualTo(0f)
        val end = DotDevelopModel.progressAt(DotDevelopModel.DEVELOP_STEPS)
        assertThat(end).isEqualTo(DotDevelopModel.END)
        // The latest any cell can develop is threshold 1 (last row, full noise).
        assertThat(DotDevelopModel.cell(1f - 1e-6f, end)).isEqualTo(DotDevelopModel.Cell.SHOWN)
        assertThat(DotDevelopModel.DEVELOP_STEPS * DotDevelopModel.STEP_MS).isAtMost(1_500)
    }

    @Test fun centreCropFillsTheFrame() {
        // A wide image in a square frame loses its sides.
        assertThat(DotDevelopModel.centreCrop(1600, 900, 300f, 300f).toList()).containsExactly(350, 0, 900, 900).inOrder()
        // A tall image in a 16:9 frame loses top and bottom.
        assertThat(DotDevelopModel.centreCrop(900, 1600, 1600f, 900f).toList()).containsExactly(0, 547, 900, 506).inOrder()
        assertThat(DotDevelopModel.centreCrop(800, 600, 400f, 300f).toList()).containsExactly(0, 0, 800, 600).inOrder()
    }

    @Test fun gridHasOneCellPerSample() {
        val pixels = IntArray(6) { argb(it * 40, it * 40, it * 40) }
        val grid = DotDevelopModel.grid(pixels, cols = 3, rows = 2)
        assertThat(grid.colors.size).isEqualTo(6)
        assertThat(grid.radii[5]).isGreaterThan(grid.radii[0])
        // Row 1 starts at 0.375 (1/2 * 0.75), row 0 never passes 0.25.
        (0 until 3).forEach { assertThat(grid.thresholds[it]).isAtMost(0.25f) }
        (3 until 6).forEach { assertThat(grid.thresholds[it]).isAtLeast(0.375f) }
        // The same image always develops the same way.
        assertThat(DotDevelopModel.grid(pixels, 3, 2).thresholds.toList()).isEqualTo(grid.thresholds.toList())
    }
}
