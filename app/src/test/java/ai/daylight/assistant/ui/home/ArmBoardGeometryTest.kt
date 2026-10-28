package ai.daylight.assistant.ui.home

import ai.daylight.assistant.ui.shell.Arm
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Board geometry against kit/screens/src/s_home.py (ox 20, oy 74, pitch 7, ports from 258, rows 66). */
class ArmBoardGeometryTest {
    private val full = BoardGeometry.compute(width = 372f, viewportHeight = 2_000f)

    @Test fun fullSizeMatchesTheMockup() {
        assertThat(full.pitch).isEqualTo(7f)
        assertThat(full.rowHeight).isEqualTo(66f)
        // Mockup y is measured from the screen top; the board starts 54dp lower (under the strip).
        assertThat(full.squidTop + 54f).isEqualTo(74f)
        assertThat(full.portsTop + 54f).isEqualTo(258f)
        // Mockup x is measured from the screen edge; the board starts 20dp in.
        assertThat(full.nodeX + 20f).isEqualTo(184f)
        assertThat(full.portTextX + 20f).isEqualTo(198f)
        assertThat(full.sideX + 20f).isEqualTo(206f)
    }

    @Test fun rightmostArmFeedsTheTopPortSoCablesNeverCross() {
        val columns = Arm.entries.indices.map { full.cableColumn(it) }
        assertThat(columns).containsExactly(18, 16, 14, 12, 10, 8, 6, 4).inOrder()
        // Lower ports turn right later, from further left: no two cables share a dot.
        val all = Arm.entries.indices.flatMap { full.cableDots(it) }
        assertThat(all.toSet().size).isEqualTo(all.size)
    }

    @Test fun eachCableRunsDownThenAcrossToItsNode() {
        Arm.entries.indices.forEach { i ->
            val dots = full.cableDots(i)
            val corner = dots.indexOfFirst { it.y == full.portCentre(i) }
            assertThat(corner).isGreaterThan(0)
            dots.take(corner).forEach { assertThat(it.x).isEqualTo(full.cableX(i)) }
            dots.drop(corner).forEach { assertThat(it.y).isEqualTo(full.portCentre(i)) }
            assertThat(dots.first().y).isEqualTo(full.cableTop)
            assertThat(dots.last().x).isAtMost(full.cableEndX)
            assertThat(dots.last().x).isLessThan(full.nodeX)
        }
    }

    @Test fun busyCablesLightThreeDotsInNine() {
        val lit = (0 until 18).filter { BoardGeometry.busyDotLit(it) }
        assertThat(lit).containsExactly(0, 1, 2, 9, 10, 11).inOrder()
        assertThat((0 until 9).count { BoardGeometry.busyDotLit(it, phase = 4) }).isEqualTo(3)
    }

    @Test fun narrowScreensScaleTheSquidDownToAFloor() {
        val narrow = BoardGeometry.compute(width = 316f, viewportHeight = 2_000f)
        assertThat(narrow.pitch).isLessThan(7f)
        assertThat(narrow.pitch).isAtLeast(BoardGeometry.MIN_PITCH)
        assertThat(BoardGeometry.compute(width = 200f, viewportHeight = 2_000f).pitch).isEqualTo(BoardGeometry.MIN_PITCH)
        assertThat(BoardGeometry.compute(width = 600f, viewportHeight = 2_000f).pitch).isEqualTo(7f)
    }

    @Test fun portContentFitsEveryRowHeight() {
        // 1dp rule + top space + name (26) + gap (3) + caption (15).
        assertThat(portContentTop(BoardGeometry.ROW)).isEqualTo(11f)
        listOf(BoardGeometry.MIN_ROW, 60f, BoardGeometry.ROW).forEach { row ->
            assertThat(1f + portContentTop(row) + 26f + 3f + 15f).isAtMost(row)
            assertThat(portContentTop(row)).isAtLeast(4f)
        }
    }

    @Test fun shortScreensShrinkRowsThenScroll() {
        val tall = BoardGeometry.compute(372f, 900f)
        assertThat(tall.rowHeight).isEqualTo(66f)
        val short = BoardGeometry.compute(372f, 700f)
        assertThat(short.rowHeight).isLessThan(66f)
        assertThat(short.rowHeight).isAtLeast(BoardGeometry.MIN_ROW)
        assertThat(short.height).isAtMost(700f + 0.5f)
        val tiny = BoardGeometry.compute(372f, 400f)
        assertThat(tiny.rowHeight).isEqualTo(BoardGeometry.MIN_ROW)
        assertThat(tiny.height).isGreaterThan(400f) // the board scrolls
    }
}
