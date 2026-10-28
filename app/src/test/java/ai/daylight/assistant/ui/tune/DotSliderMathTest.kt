package ai.daylight.assistant.ui.tune

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DotSliderMathTest {
    private val width = 360f
    private val inset = 3f

    @Test fun dotsSpanTheWidthInsideTheInset() {
        assertThat(DotSliderMath.centre(0, 24, width, inset)).isWithin(0.001f).of(3f)
        assertThat(DotSliderMath.centre(23, 24, width, inset)).isWithin(0.001f).of(357f)
        // One dot sits in the middle.
        assertThat(DotSliderMath.centre(0, 1, width, inset)).isWithin(0.001f).of(180f)
    }

    @Test fun aTouchPicksTheNearestDot() {
        for (i in 0 until 24) {
            val x = DotSliderMath.centre(i, 24, width, inset)
            assertThat(DotSliderMath.indexAt(x, 24, width, inset)).isEqualTo(i)
            assertThat(DotSliderMath.indexAt(x + 4f, 24, width, inset)).isEqualTo(i)
        }
    }

    @Test fun touchesPastTheEndsClamp() {
        assertThat(DotSliderMath.indexAt(-50f, 24, width, inset)).isEqualTo(0)
        assertThat(DotSliderMath.indexAt(width + 50f, 24, width, inset)).isEqualTo(23)
        assertThat(DotSliderMath.indexAt(100f, 1, width, inset)).isEqualTo(0)
        // A slider squeezed to nothing never divides by zero.
        assertThat(DotSliderMath.indexAt(1f, 24, 6f, inset)).isEqualTo(0)
    }

    @Test fun aSidewaysMoveIsClaimedFromItsFirstPixel() {
        val slop = 20f
        // Held (consumed) before the slop, so the chat drawer, which engages after 4dp, keeps out.
        assertThat(DotSliderMath.claim(Offset(3f, 1f), slop, dragging = false)).isEqualTo(DotSliderMath.Claim.Hold)
        assertThat(DotSliderMath.claim(Offset(-3f, 0f), slop, dragging = false)).isEqualTo(DotSliderMath.Claim.Hold)
        assertThat(DotSliderMath.claim(Offset(25f, 4f), slop, dragging = false)).isEqualTo(DotSliderMath.Claim.Drag)
        assertThat(DotSliderMath.claim(Offset(0f, 300f), slop, dragging = true)).isEqualTo(DotSliderMath.Claim.Drag)
    }

    @Test fun aVerticalMoveIsLeftToThePageScroll() {
        val slop = 20f
        // Never consumed, which would cancel the scroll; released once it passes the slop.
        assertThat(DotSliderMath.claim(Offset(2f, 6f), slop, dragging = false)).isEqualTo(DotSliderMath.Claim.Wait)
        assertThat(DotSliderMath.claim(Offset(4f, 30f), slop, dragging = false)).isEqualTo(DotSliderMath.Claim.Release)
    }

    @Test fun dotsBeforeTheThumbAreSetAndAfterItOff() {
        assertThat((0 until 5).map { DotSliderMath.dot(it, 2) }).containsExactly(
            DotSliderMath.Dot.Set, DotSliderMath.Dot.Set, DotSliderMath.Dot.Thumb, DotSliderMath.Dot.Off, DotSliderMath.Dot.Off
        ).inOrder()
        // Off (dot 0): the thumb sits on the first dot and nothing is set.
        assertThat(DotSliderMath.dot(0, 0)).isEqualTo(DotSliderMath.Dot.Thumb)
    }
}
