package ai.daylight.assistant.ui.dot.effects

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class DotFont5x7Test {
    @Test fun fontHasTheKitsFortyNineGlyphs() {
        assertThat(DotFont5x7.glyphs).hasSize(49)
        ('A'..'Z').forEach { assertThat(DotFont5x7.supports(it)).isTrue() }
        ('0'..'9').forEach { assertThat(DotFont5x7.supports(it)).isTrue() }
        " .:-%/()!?$+#".forEach { assertThat(DotFont5x7.supports(it)).isTrue() }
    }

    @Test fun everyGlyphIsSevenRowsOfFiveBits() {
        DotFont5x7.glyphs.forEach { (c, rows) ->
            assertWithMessage("rows of '$c'").that(rows.size).isEqualTo(7)
            rows.forEach { assertWithMessage("row of '$c'").that(it).isIn(0..31) }
        }
    }

    @Test fun spaceIsBlankAndEveryOtherGlyphLightsSomething() {
        DotFont5x7.glyphs.forEach { (c, rows) ->
            if (c == ' ') {
                assertThat(rows.all { it == 0 }).isTrue()
            } else {
                assertWithMessage("'$c' lights a dot").that(rows.any { it != 0 }).isTrue()
            }
        }
    }

    @Test fun litReadsBitFourAsTheLeftColumn() {
        // 'T' = 31,4,4,4,4,4,4: a full top bar, then only the middle column.
        (0..4).forEach { assertThat(DotFont5x7.lit('T', it, 0)).isTrue() }
        assertThat(DotFont5x7.lit('T', 2, 3)).isTrue()
        assertThat(DotFont5x7.lit('T', 0, 3)).isFalse()
        assertThat(DotFont5x7.lit('T', 4, 3)).isFalse()
        // 'L' = left column plus a bottom bar.
        assertThat(DotFont5x7.lit('L', 0, 0)).isTrue()
        assertThat(DotFont5x7.lit('L', 4, 0)).isFalse()
        assertThat(DotFont5x7.lit('L', 4, 6)).isTrue()
    }

    @Test fun lowercaseUsesTheUppercaseGlyph() {
        for (col in 0..4) for (row in 0..6) {
            assertThat(DotFont5x7.lit('k', col, row)).isEqualTo(DotFont5x7.lit('K', col, row))
        }
    }

    @Test fun charactersOutsideTheFontDrawBlankInsteadOfQuestionMarks() {
        // e-acute, euro, at, ampersand, comma, a lone emoji surrogate, NUL
        listOf('é', '€', '@', '&', ',', '\uD83E', '\u0000').forEach { c ->
            assertThat(DotFont5x7.supports(c)).isFalse()
            for (col in 0..4) for (row in 0..6) assertThat(DotFont5x7.lit(c, col, row)).isFalse()
        }
    }

    @Test fun outOfRangeCellsAreUnlit() {
        assertThat(DotFont5x7.lit('8', -1, 0)).isFalse()
        assertThat(DotFont5x7.lit('8', 5, 0)).isFalse()
        assertThat(DotFont5x7.lit('8', 0, -1)).isFalse()
        assertThat(DotFont5x7.lit('8', 0, 7)).isFalse()
    }

    @Test fun columnsHandleEmptyAndMultiCharacterText() {
        assertThat(DotFont5x7.columns("")).isEqualTo(0)
        assertThat(DotFont5x7.columns("A")).isEqualTo(5)
        assertThat(DotFont5x7.columns("READY")).isEqualTo(29)
    }

    @Test fun litAtSkipsTheGapColumnAndRunsOffTheEndSafely() {
        val text = "HH"
        // 'H' row 3 is 31 (full bar); column 5 is the gap between the two letters.
        (0..4).forEach { assertThat(DotFont5x7.litAt(text, it, 3)).isTrue() }
        assertThat(DotFont5x7.litAt(text, 5, 3)).isFalse()
        (6..10).forEach { assertThat(DotFont5x7.litAt(text, it, 3)).isTrue() }
        assertThat(DotFont5x7.litAt(text, 11, 3)).isFalse()
        assertThat(DotFont5x7.litAt(text, 400, 3)).isFalse()
        assertThat(DotFont5x7.litAt(text, -1, 3)).isFalse()
        assertThat(DotFont5x7.litAt("", 0, 3)).isFalse()
    }

    @Test fun spokenTextIsSentenceCase() {
        assertThat(dotTextSpoken("TALK READY")).isEqualTo("Talk ready")
        assertThat(dotTextSpoken("")).isEmpty()
    }

    @Test fun dissolveNoiseStaysInRangeAndDoesNotTileOnLargeScreens() {
        // The kit's 4096-entry table repeated every 4096 cells; a phone screen at 6dp is
        // over 10,000 cells. Hashed noise must differ between a cell and the cell 4096
        // positions later.
        var differences = 0
        for (j in 0 until 40) for (i in 0 until 70) {
            val n = dissolveNoise(i, j)
            assertThat(n).isAtLeast(0f)
            assertThat(n).isLessThan(1f)
            val k = j * 70 + i + 4096
            if (dissolveNoise(k % 70, k / 70) != n) differences++
        }
        assertThat(differences).isGreaterThan(2_700)
    }
}
