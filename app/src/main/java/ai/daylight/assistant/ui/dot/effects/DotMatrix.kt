package ai.daylight.assistant.ui.dot.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotTheme
import kotlin.random.Random

/**
 * Original 5x7 dot-matrix glyphs (bit 4 = leftmost column): A-Z, 0-9 and 13 punctuation
 * marks, 49 glyphs in all. Ported from kit/effects/compose/DotMatrix.kt.
 *
 * Fix over the kit: a character outside the font draws as a blank cell instead of a "?",
 * so an unexpected character never looks like an error on the strip.
 */
object DotFont5x7 {
    const val ROWS = 7
    const val GLYPH_COLS = 5

    /** One glyph plus a one-column gap. */
    const val CELL_COLS = 6

    val glyphs: Map<Char, IntArray> = mapOf(
        'A' to intArrayOf(14, 17, 17, 31, 17, 17, 17),
        'B' to intArrayOf(30, 17, 17, 30, 17, 17, 30),
        'C' to intArrayOf(14, 17, 16, 16, 16, 17, 14),
        'D' to intArrayOf(30, 17, 17, 17, 17, 17, 30),
        'E' to intArrayOf(31, 16, 16, 30, 16, 16, 31),
        'F' to intArrayOf(31, 16, 16, 30, 16, 16, 16),
        'G' to intArrayOf(14, 17, 16, 23, 17, 17, 15),
        'H' to intArrayOf(17, 17, 17, 31, 17, 17, 17),
        'I' to intArrayOf(14, 4, 4, 4, 4, 4, 14),
        'J' to intArrayOf(7, 2, 2, 2, 2, 18, 12),
        'K' to intArrayOf(17, 18, 20, 24, 20, 18, 17),
        'L' to intArrayOf(16, 16, 16, 16, 16, 16, 31),
        'M' to intArrayOf(17, 27, 21, 21, 17, 17, 17),
        'N' to intArrayOf(17, 17, 25, 21, 19, 17, 17),
        'O' to intArrayOf(14, 17, 17, 17, 17, 17, 14),
        'P' to intArrayOf(30, 17, 17, 30, 16, 16, 16),
        'Q' to intArrayOf(14, 17, 17, 17, 21, 18, 13),
        'R' to intArrayOf(30, 17, 17, 30, 20, 18, 17),
        'S' to intArrayOf(15, 16, 16, 14, 1, 1, 30),
        'T' to intArrayOf(31, 4, 4, 4, 4, 4, 4),
        'U' to intArrayOf(17, 17, 17, 17, 17, 17, 14),
        'V' to intArrayOf(17, 17, 17, 17, 17, 10, 4),
        'W' to intArrayOf(17, 17, 17, 21, 21, 21, 10),
        'X' to intArrayOf(17, 17, 10, 4, 10, 17, 17),
        'Y' to intArrayOf(17, 17, 10, 4, 4, 4, 4),
        'Z' to intArrayOf(31, 1, 2, 4, 8, 16, 31),
        '0' to intArrayOf(14, 17, 19, 21, 25, 17, 14),
        '1' to intArrayOf(4, 12, 4, 4, 4, 4, 14),
        '2' to intArrayOf(14, 17, 1, 2, 4, 8, 31),
        '3' to intArrayOf(31, 2, 4, 2, 1, 17, 14),
        '4' to intArrayOf(2, 6, 10, 18, 31, 2, 2),
        '5' to intArrayOf(31, 16, 30, 1, 1, 17, 14),
        '6' to intArrayOf(6, 8, 16, 30, 17, 17, 14),
        '7' to intArrayOf(31, 1, 2, 4, 8, 8, 8),
        '8' to intArrayOf(14, 17, 17, 14, 17, 17, 14),
        '9' to intArrayOf(14, 17, 17, 15, 1, 2, 12),
        ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
        '.' to intArrayOf(0, 0, 0, 0, 0, 12, 12),
        ':' to intArrayOf(0, 12, 12, 0, 12, 12, 0),
        '-' to intArrayOf(0, 0, 0, 31, 0, 0, 0),
        '%' to intArrayOf(24, 25, 2, 4, 8, 19, 3),
        '/' to intArrayOf(0, 1, 2, 4, 8, 16, 0),
        '(' to intArrayOf(2, 4, 8, 8, 8, 4, 2),
        ')' to intArrayOf(8, 4, 2, 2, 2, 4, 8),
        '!' to intArrayOf(4, 4, 4, 4, 4, 0, 4),
        '?' to intArrayOf(14, 17, 1, 2, 4, 0, 4),
        '$' to intArrayOf(4, 15, 20, 14, 5, 30, 4),
        '+' to intArrayOf(0, 4, 4, 31, 4, 4, 0),
        '#' to intArrayOf(10, 10, 31, 10, 31, 10, 10)
    )

    /** True when [c] (in either case) has a glyph. */
    fun supports(c: Char): Boolean = glyphs.containsKey(c.uppercaseChar())

    /** Whether the dot at glyph column [col] (0..4) and [row] (0..6) of [c] is lit. */
    fun lit(c: Char, col: Int, row: Int): Boolean {
        if (col !in 0 until GLYPH_COLS || row !in 0 until ROWS) return false
        val g = glyphs[c.uppercaseChar()] ?: return false
        return (g[row] shr (GLYPH_COLS - 1 - col)) and 1 == 1
    }

    /** Matrix width of [text] in dot columns. An empty string is zero columns wide. */
    fun columns(text: String): Int = if (text.isEmpty()) 0 else text.length * CELL_COLS - 1

    /** Whether matrix column [col] and [row] of the whole [text] is lit (gap columns never are). */
    fun litAt(text: String, col: Int, row: Int): Boolean {
        if (col < 0) return false
        val index = col / CELL_COLS
        if (index >= text.length) return false
        return lit(text[index], col % CELL_COLS, row)
    }
}

enum class DotReveal { None, LeftToRight, Random, Scan }

/** "TALK READY" -> "Talk ready", so screen readers say words instead of spelling capitals. */
internal fun dotTextSpoken(text: String): String =
    text.trim().lowercase().replaceFirstChar { it.uppercaseChar() }

/**
 * EFFECT 1: dot-matrix text. Renders [text] as LED dots, unlit dots drawn in [off].
 *
 * Fixes over the kit: an empty string draws nothing instead of crashing, the text is
 * exposed to screen readers, colours come from the theme, and there is no reveal
 * animation unless one is asked for.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    pitch: Dp = 6.dp,
    dotFraction: Float = 0.72f,
    on: Color = DotTheme.c.textPrimary,
    off: Color = DotTheme.c.dotOff,
    reveal: DotReveal = DotReveal.None,
    durationMs: Int = 600,
    contentDescription: String = dotTextSpoken(text),
    key: Any = text,
) {
    val cols = DotFont5x7.columns(text)
    val progress = remember(key) { Animatable(if (reveal == DotReveal.None) 1f else 0f) }
    LaunchedEffect(key) {
        if (reveal != DotReveal.None) progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    val seeds = remember(key, cols) {
        val r = Random(text.hashCode())
        FloatArray(cols * DotFont5x7.ROWS) { r.nextFloat() }
    }
    Canvas(
        modifier
            .size(pitch * cols, pitch * DotFont5x7.ROWS)
            .clearAndSetSemantics { this.contentDescription = contentDescription }
    ) {
        if (cols == 0) return@Canvas
        val p = pitch.toPx()
        val rad = p * dotFraction / 2f
        val prog = progress.value
        for (x in 0 until cols) for (row in 0 until DotFont5x7.ROWS) {
            val shown = when (reveal) {
                DotReveal.None -> true
                DotReveal.LeftToRight -> x.toFloat() / cols <= prog
                DotReveal.Random -> seeds[x * DotFont5x7.ROWS + row] <= prog
                DotReveal.Scan -> row.toFloat() / DotFont5x7.ROWS <= prog
            }
            val lit = shown && DotFont5x7.litAt(text, x, row)
            drawCircle(if (lit) on else off, rad, Offset(x * p + p / 2, row * p + p / 2))
        }
    }
}
