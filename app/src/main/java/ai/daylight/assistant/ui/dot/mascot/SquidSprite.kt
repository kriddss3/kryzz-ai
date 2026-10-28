package ai.daylight.assistant.ui.dot.mascot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sin

/**
 * The KryzzBot squid as a 23x22 LED sprite, ported from kit/screens/src/sprites.py
 * `squid(arm_phase)`. Rows 0-11 are the mantle, row 12 the head band, row 13 the arm
 * roots, and the eight arms trail down from row 14 with a sine wiggle set by the arm phase.
 *
 * The maths matches the Python exactly, including its rounding (round half to even), so
 * the phase-0 grid is the sprite in the kit mockups.
 */
object SquidSprite {
    const val COLS = 23
    const val ROWS = 22

    private val MANTLE_WIDTHS = intArrayOf(3, 5, 7, 9, 11, 11, 13, 13, 13, 13, 13, 11)
    private const val CENTRE_COL = 11

    private val ARM_BASES = intArrayOf(4, 6, 8, 10, 12, 14, 16, 18)

    /** The column each arm leaves the head from, left to right. */
    val armBaseColumns: List<Int> = ARM_BASES.toList()
    private val ARM_LENGTHS = intArrayOf(6, 8, 9, 10, 10, 9, 8, 6)

    /** The eye cells (row to column). They are holes in the mantle, drawn in the accent. */
    private val EYES: Set<Long> = setOf(8 to 8, 8 to 9, 9 to 8, 9 to 9, 8 to 13, 8 to 14, 9 to 13, 9 to 14)
        .map { (r, c) -> key(r, c) }.toSet()

    private fun key(row: Int, col: Int): Long = row.toLong() * COLS + col

    fun isEye(row: Int, col: Int): Boolean = key(row, col) in EYES

    /** The sprite at [armPhase], row-major: `grid[row * COLS + col]`. */
    fun grid(armPhase: Double = 0.0): BooleanArray {
        val g = BooleanArray(COLS * ROWS)
        fun set(r: Int, c: Int, v: Boolean) { g[r * COLS + c] = v }
        MANTLE_WIDTHS.forEachIndexed { r, w ->
            val start = (COLS - w) / 2
            for (c in start until start + w) set(r, c, true)
        }
        for (r in 0 until ROWS) for (c in 0 until COLS) if (isEye(r, c)) set(r, c, false)
        set(3, CENTRE_COL, false)
        set(5, CENTRE_COL, false) // mantle seam
        for (c in 5 until 18) set(12, c, true)
        ARM_BASES.forEach { set(13, it, true) }
        ARM_BASES.forEachIndexed { i, base ->
            val side = if (base < CENTRE_COL) -1 else 1
            val len = ARM_LENGTHS[i]
            for (k in 1 until len) {
                val r = 13 + k
                if (r >= ROWS) break
                val out = side * (abs(base - CENTRE_COL) / 7.0) * (k * k) / 22.0
                val wig = sin(k * 0.8 + armPhase) * 0.9 * (k.toDouble() / len)
                val c = round(base + out + wig).toInt()
                if (c in 0 until COLS) set(r, c, true)
            }
        }
        return g
    }

    /** The grid as text, '#' for lit and '.' for dark, one line per row (for tests and logs). */
    fun render(grid: BooleanArray): String = buildString {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) append(if (grid[r * COLS + c]) '#' else '.')
            if (r < ROWS - 1) append('\n')
        }
    }
}

/**
 * The squid's poses. Each is one arm phase; phase D turns them into stepped frame swaps.
 * THINKING uses the voice mockup's mid-wave phase (1.2).
 */
enum class SquidPose(val armPhase: Double) {
    STANDBY(0.0),
    THINKING(1.2),
    SPEAKING(PI);

    /** Cached grid: the sprite is pure, so each pose is computed once. */
    val grid: BooleanArray by lazy { SquidSprite.grid(armPhase) }
}
