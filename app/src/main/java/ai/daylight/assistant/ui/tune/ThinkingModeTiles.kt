package ai.daylight.assistant.ui.tune

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.ui.dot.DotGrey
import ai.daylight.assistant.ui.dot.KryzzGlyph
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.run.RunQuality

/**
 * Kit settings.png "Thinking mode": three 52dp tiles, Fast / Balanced / Max, each with its
 * icon and a 1/2/3 dot signal. The chosen tile is inverted white. Only Max's signal is
 * yellow, because it is the one that costs more; on the white tile it turns black.
 */
@Composable
fun ThinkingModeTiles(selected: AgentQuality, onSelect: (AgentQuality) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AgentQuality.entries.forEach { quality ->
            // Balanced has the longest word, so its tile gets a little more room (as in the mockup).
            val weight = if (quality == AgentQuality.BALANCED) 1.25f else 1f
            ModeTile(quality, quality == selected, { onSelect(quality) }, Modifier.weight(weight))
        }
    }
}

@Composable
private fun ModeTile(quality: AgentQuality, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    val fg = if (chosen) c.bg else c.textSecondary
    Row(
        modifier
            .height(52.dp)
            .clip(DotRadius.Tile)
            .background(if (chosen) c.textPrimary else c.surface)
            .border(1.dp, if (chosen) c.textPrimary else c.border, DotRadius.Tile)
            .selectable(selected = chosen, role = Role.RadioButton) {
                if (!chosen) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KryzzGlyph(RunQuality.icon(quality), Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(
            quality.label,
            Modifier.weight(1f),
            style = DotType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = if (chosen) c.bg else c.textSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(6.dp))
        Signal(
            lit = RunQuality.signal(quality),
            on = when {
                chosen -> c.bg
                RunQuality.accentSignal(quality) -> c.accentText
                else -> c.textPrimary
            },
            // Unlit dots: #3C3C3C on the dark tile, a light grey on the white one (mockup #C9C9C9).
            off = when {
                !chosen -> c.borderStrong
                c.dark -> DotGrey.G300
                else -> DotGrey.G800
            }
        )
    }
}

@Composable
private fun Signal(lit: Int, on: androidx.compose.ui.graphics.Color, off: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i -> Box(Modifier.size(4.dp).background(if (i < lit) on else off, CircleShape)) }
    }
}
