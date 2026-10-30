package ai.daylight.assistant.ui.shell

import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Phase D: the strip's column wipe and ticker. */
class InkMotionTest {
    @Test fun wipeStartsAtTheFirstCharacterThatChanged() {
        assertThat(InkMotion.firstChange("TALK THINKING", "TALK READING 3 OF 5")).isEqualTo(5 * DotFont5x7.CELL_COLS)
        assertThat(InkMotion.firstChange("RUN STEP 3 OF 5", "RUN STEP 4 OF 5")).isEqualTo(9 * DotFont5x7.CELL_COLS)
        assertThat(InkMotion.firstChange("", "KRYZZ READY")).isEqualTo(0)
    }

    @Test fun wipeRunsToTheEndOfTheLongerLine() {
        val span = InkMotion.wipeSpan("TALK READING 3 OF 5", "TALK READY")
        // "TALK READ" (9 characters) is shared.
        assertThat(span.first).isEqualTo(9 * DotFont5x7.CELL_COLS)
        assertThat(span.last).isEqualTo(DotFont5x7.columns("TALK READING 3 OF 5") - 1)
        assertThat(InkMotion.wipeSpan("SAME", "SAME").isEmpty()).isTrue()
    }

    @Test fun shortChangesMoveOneColumnPerStep() {
        val fronts = InkMotion.wipeFronts("RUN STEP 3", "RUN STEP 4")
        assertThat(fronts).containsExactly(54, 55, 56, 57, 58).inOrder()
    }

    @Test fun longChangesNeverTakeMoreThanTheMaxSteps() {
        val fronts = InkMotion.wipeFronts("", "X".repeat(26))
        assertThat(fronts.size).isAtMost(InkMotion.WIPE_MAX_STEPS)
        assertThat(fronts.first()).isEqualTo(0)
        assertThat(fronts.last()).isLessThan(DotFont5x7.columns("X".repeat(26)))
        assertThat(fronts).isInStrictOrder()
        assertThat(fronts.size * InkMotion.WIPE_STEP_MS).isAtMost(650)
        assertThat(InkMotion.wipeFronts("SAME", "SAME")).isEmpty()
    }

    @Test fun duringAWipeNewIsLeftOfTheFrontAndOldIsRight() {
        val old = "AAAA"
        val new = "TTTT"
        val front = 12
        // The front column is a grey bar on every row.
        (0 until DotFont5x7.ROWS).forEach { row ->
            assertThat(InkMotion.dot(front, row, new, old, front, 0, false, 160)).isEqualTo(InkMotion.Dot.FRONT)
        }
        // T's top row is fully lit and A's top row is not (A = 01110), so column 0 differs.
        assertThat(InkMotion.dot(0, 0, new, old, front, 0, false, 160)).isEqualTo(InkMotion.Dot.LIT)
        assertThat(InkMotion.dot(18, 0, new, old, front, 0, false, 160)).isEqualTo(InkMotion.Dot.OFF)
        assertThat(InkMotion.dot(18, 3, new, old, front, 0, false, 160)).isEqualTo(InkMotion.Dot.LIT) // A's bar
    }

    @Test fun withNoWipeOnlyWholeCharactersShow() {
        // 11 text columns fit one 5-column character plus a gap and 5 more columns: two characters.
        assertThat(InkMotion.dot(6, 0, "TT", "", InkMotion.NO_WIPE, 0, false, 11)).isEqualTo(InkMotion.Dot.LIT)
        // 10 columns fit only one: the second T is not drawn half.
        assertThat(InkMotion.dot(6, 0, "TT", "", InkMotion.NO_WIPE, 0, false, 10)).isEqualTo(InkMotion.Dot.OFF)
    }

    @Test fun onlyLinesWiderThanTheStripTick() {
        val area = InkMotion.areaColumns(169)
        assertThat(area).isEqualTo(169 - InkStripSpec.TEXT_START_COL - 1)
        val fits = "X".repeat(inkStripCapacity(169))
        assertThat(InkMotion.overflows(fits, area)).isFalse()
        assertThat(InkMotion.overflows(fits + "X", area)).isTrue()
        assertThat(InkMotion.overflows("ANY", 0)).isFalse()
    }

    @Test fun tickerScrollsOneColumnPerStepAndComesRound() {
        val text = "INKWELL 12 IMAGES AND CLIPS"
        val loop = InkMotion.loopColumns(text)
        assertThat(loop).isEqualTo((text.length + InkMotion.TICKER_GAP) * DotFont5x7.CELL_COLS)
        assertThat(InkMotion.sourceColumn(0, 0, loop)).isEqualTo(0)
        assertThat(InkMotion.sourceColumn(0, 1, loop)).isEqualTo(1)
        assertThat(InkMotion.sourceColumn(3, loop, loop)).isEqualTo(3)
        assertThat(InkMotion.sourceColumn(loop - 1, 2, loop)).isEqualTo(1)
        // One step later, every column shows what its right-hand neighbour showed.
        (0 until 40).forEach { col ->
            (0 until DotFont5x7.ROWS).forEach { row ->
                assertThat(InkMotion.dot(col, row, text, "", InkMotion.NO_WIPE, 1, true, 150))
                    .isEqualTo(InkMotion.dot(col + 1, row, text, "", InkMotion.NO_WIPE, 0, true, 150))
            }
        }
    }
}
