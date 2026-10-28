package ai.daylight.assistant.ui.tune

import ai.daylight.assistant.domain.AgentTurnPolicy
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Words and numbers for the Tune subpages (Text and motion, Keys, Tools, Voice). Pure, so
 * it is unit tested. Every slider is a row of dots, so each one has a fixed list of stops.
 */
object TuneSubpageModel {

    /** Text size: 85% to 135% in 5% steps (the old slider's 11 values). */
    val FONT_SCALE_STOPS: List<Float> = (0..10).map { (85 + it * 5) / 100f }

    /** Speaking speed: 0.5x to 2.0x in 0.1 steps (the old slider's 16 values). */
    val SPEECH_SPEED_STOPS: List<Float> = (5..20).map { it / 10f }

    /**
     * Search context: 2k to 50k characters in 2k steps. The old slider had 1k steps (49
     * values), too many dots for one row; a stored odd value shows at the nearest dot and
     * only changes if the slider is moved.
     */
    val SEARCH_CHAR_STOPS: List<Int> = (1..25).map { it * 2_000 }

    /** Agent step budget: every whole value the policy allows. */
    val STEP_BUDGET_STOPS: List<Int> = (AgentTurnPolicy.MIN_STEP_BUDGET..AgentTurnPolicy.MAX_STEP_BUDGET).toList()

    /** The stop nearest to [value]; a tie goes to the lower stop. */
    fun nearest(stops: List<Float>, value: Float): Int {
        var best = 0
        for (i in stops.indices) if (abs(stops[i] - value) < abs(stops[best] - value) - 1e-4f) best = i
        return best
    }

    /** The stop nearest to [value]; a tie goes to the lower stop. */
    fun nearestInt(stops: List<Int>, value: Int): Int {
        var best = 0
        for (i in stops.indices) if (abs(stops[i] - value) < abs(stops[best] - value)) best = i
        return best
    }

    fun percent(scale: Float): String = "${(scale * 100).roundToInt()}%"

    fun speed(value: Float): String = String.format(Locale.US, "%.1fX", value)

    fun speedSpoken(value: Float): String = String.format(Locale.US, "%.1f times", value)

    fun chars(value: Int): String = "${value / 1_000}K"

    /**
     * How a stored key is shown (kit §8): dots, then the last four characters. A key too
     * short to show its end is all dots. Null means no key is stored.
     */
    fun maskedKey(stored: Boolean, tail: String?): String? = when {
        !stored -> null
        tail != null -> "•••• •••• $tail"
        else -> "•••• •••• ••••"
    }

    /** The mono caption on a key row. */
    fun keyRowCaption(stored: Boolean, tail: String?): String = when {
        !stored -> "No key stored"
        tail != null -> "Ends $tail, encrypted"
        else -> "Stored, encrypted"
    }
}
