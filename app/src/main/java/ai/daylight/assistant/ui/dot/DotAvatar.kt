package ai.daylight.assistant.ui.dot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import java.security.MessageDigest

/**
 * C9 Crew: a bot's face, a 7x7 mirrored dot identicon. An exact port of `identicon()` in
 * kit/screens/src/sprites.py: SHA-1 of the key, one bit per cell read row by row from the
 * next byte, mirrored left to right. The key is the bot's name followed by its seed
 * ("Scout4"), so the user can shuffle a face without renaming the bot.
 */
object Identicon {
    const val SIZE = 7

    fun key(name: String, seed: Int): String = "$name$seed"

    /** Rows of on/off cells, [n] by [n]. */
    fun grid(key: String, n: Int = SIZE): List<List<Boolean>> {
        val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
        val cells = Array(n) { BooleanArray(n) }
        val half = (n + 1) / 2
        var k = 0
        for (row in 0 until n) {
            for (col in 0 until half) {
                // Python bytes are 0..255; Kotlin's are signed, so mask first.
                val on = ((hash[k % hash.size].toInt() and 0xFF) shr (row % 8)) and 1 == 1
                k++
                cells[row][col] = on
                cells[row][n - 1 - col] = on
            }
        }
        return cells.map { it.toList() }
    }

    /** The kit's live rule (s_crew.py): while a bot works, its lit dots on every third diagonal turn yellow. */
    fun liveDot(x: Int, y: Int): Boolean = (x + y) % 3 == 0
}

/**
 * Draws a bot's identicon: lit dots white, unlit dots [off] (none when transparent). The kit
 * uses a 9dp pitch for the featured card, 7dp in rows, 11dp on the bot's page and 3dp beside
 * its chat lines. [live] turns some lit dots yellow while the bot works. Decorative: the
 * bot's name is always next to it.
 */
@Composable
fun DotAvatar(
    name: String,
    seed: Int,
    pitch: Dp,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    off: Color = DotTheme.c.surfaceRaised,
) {
    val c = DotTheme.c
    val grid = remember(name, seed) { Identicon.grid(Identicon.key(name, seed)) }
    Canvas(modifier.size(pitch * Identicon.SIZE).clearAndSetSemantics { }) {
        val p = pitch.toPx()
        val radius = p * 0.36f
        grid.forEachIndexed { y, row ->
            row.forEachIndexed { x, on ->
                val colour = when {
                    on && live && Identicon.liveDot(x, y) -> c.accent
                    on -> c.textPrimary
                    else -> off
                }
                if (colour.alpha > 0f) drawCircle(colour, radius, Offset(x * p + p / 2f, y * p + p / 2f))
            }
        }
    }
}
