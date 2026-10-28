package ai.daylight.assistant.ui.run

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotBar
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import java.util.Locale

/** What the cost line says and how full its 12-dot bar is ([lit] of [RunCost.DOTS]). */
data class CostReadout(val text: String, val lit: Int = 0, val showBar: Boolean = false, val warn: Boolean = false)

/**
 * The cost line under a run: "THIS RUN: $0.21 OF $0.50" with a 12-dot bar, from the cost the
 * provider reported for the turn and the per-run cap. The agent only knows the cost once
 * the turn ends, so a running run shows its cap instead of a made-up figure.
 */
object RunCost {
    const val DOTS = 12

    /** At this share of the cap the bar turns yellow (components.md section 4). */
    const val WARN_SHARE = 0.8

    fun usd(value: Double): String = when {
        value <= 0.0 -> "$0.00"
        value < 0.001 -> "<$0.001"
        value < 0.01 -> "$" + String.format(Locale.US, "%.3f", value)
        else -> "$" + String.format(Locale.US, "%.2f", value)
    }

    /**
     * [capCents] is the cap the run had (0 = off). [latest] runs compare against it; an
     * earlier run only states its cost, because the cap may have changed since. Returns
     * null when there is nothing true to say (a stopped run saves no cost).
     */
    fun readout(
        costUsd: Double?,
        totalTokens: Int?,
        capCents: Int,
        running: Boolean,
        finished: Boolean,
        latest: Boolean,
    ): CostReadout? {
        val capUsd = capCents.coerceAtLeast(0) / 100.0
        if (running) {
            return if (capCents > 0) CostReadout("Cap ${usd(capUsd)}, cost when done", lit = 0, showBar = true)
            else CostReadout("No cap, cost when done")
        }
        if (!finished) return null
        if (costUsd == null) {
            val tokens = totalTokens?.takeIf { it > 0 }?.let { String.format(Locale.US, "%,d tok", it) }
            return when {
                tokens != null -> CostReadout(if (latest) "This run: $tokens, no cost reported" else "Run: $tokens")
                latest -> CostReadout("This run: no cost reported")
                else -> null
            }
        }
        if (!latest) return CostReadout("Run cost ${usd(costUsd)}")
        if (capCents <= 0) return CostReadout("This run: ${usd(costUsd)}, no cap")
        val share = costUsd / capUsd
        val lit = (share * DOTS).let { kotlin.math.round(it).toInt() }
            .coerceIn(if (costUsd > 0) 1 else 0, DOTS)
        return CostReadout("This run: ${usd(costUsd)} of ${usd(capUsd)}", lit = lit, showBar = true, warn = share >= WARN_SHARE)
    }
}

@Composable
fun CostLine(readout: CostReadout, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = readout.text },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            readout.text.uppercase(Locale.US),
            Modifier.weight(1f),
            style = DotType.Caption,
            color = c.textTertiary,
            maxLines = 2
        )
        if (readout.showBar) {
            Spacer(Modifier.width(12.dp))
            DotBar(
                count = RunCost.DOTS,
                lit = readout.lit,
                on = if (readout.warn) c.accent else c.textPrimary,
                off = c.dotOff,
                modifier = Modifier.width(81.dp)
            )
        }
    }
}
