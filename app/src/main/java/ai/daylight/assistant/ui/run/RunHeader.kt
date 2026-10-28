package ai.daylight.assistant.ui.run

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.home.HomeBoard
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel
import java.util.Locale

/** The quality pill's words and signal. Pure, so it is unit tested. */
object RunQuality {
    /** Lit signal dots: Fast 1, Balanced 2, Max 3. */
    fun signal(quality: AgentQuality): Int = quality.ordinal + 1

    /** Only Max may show yellow signal dots: it is the one that costs more (components.md section 6). */
    fun accentSignal(quality: AgentQuality): Boolean = quality == AgentQuality.MAX

    @DrawableRes
    fun icon(quality: AgentQuality): Int = when (quality) {
        AgentQuality.FAST -> R.drawable.ic_kz_bolt
        AgentQuality.BALANCED -> R.drawable.ic_kz_balance
        AgentQuality.MAX -> R.drawable.ic_kz_max
    }

    /** "Cap $0.50 a run", with Max's raised floor; "No cap" when the cap is off. */
    fun capLine(quality: AgentQuality, capCents: Int): String {
        val cents = HomeBoard.effectiveCapCents(quality, capCents)
        return if (cents <= 0) "No cap" else "Cap ${HomeBoard.capLabel(cents)} a run"
    }
}

/**
 * The top of Run (kit agent-run): RUN in dot text on the left, which opens the chat library
 * like Talk's title does, and the thinking-mode pill on the right.
 */
@Composable
fun RunHeader(quality: AgentQuality, onQuality: () -> Unit, onOpenLibrary: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(DotRadius.Tile)
                .clickable(onClickLabel = "Open chat library", onClick = onOpenLibrary)
                .clearAndSetSemantics {
                    contentDescription = "Run. Open chat library"
                    onClick(label = "Open chat library") { onOpenLibrary(); true }
                }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            DotMatrixText("RUN", pitch = 4.2.dp, on = c.textPrimary, off = Color.Transparent)
        }
        Spacer(Modifier.weight(1f))
        QualityPill(quality, onQuality)
    }
}

/** A 32dp pill (48dp to touch): mode icon, FAST / BALANCED / MAX and a three-dot signal. */
@Composable
fun QualityPill(quality: AgentQuality, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(DotRadius.Pill)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Thinking mode ${quality.label}. Change"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .height(32.dp)
                .background(c.surfaceRaised, DotRadius.Pill)
                .border(1.dp, c.border, DotRadius.Pill)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(RunQuality.icon(quality)), null, Modifier.size(14.dp), tint = c.textPrimary)
            Text(quality.label.uppercase(Locale.US), style = DotType.Label, color = c.textSecondary)
            SignalDots(RunQuality.signal(quality), if (RunQuality.accentSignal(quality)) c.accent else c.textPrimary)
        }
    }
}

@Composable
private fun SignalDots(lit: Int, on: Color) {
    val c = DotTheme.c
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i -> Box(Modifier.size(4.dp).background(if (i < lit) on else c.dotOff, CircleShape)) }
    }
}

/** Fast / Balanced / Max as 64dp rows; the chosen one has a white outline and a filled radio. */
@Composable
fun QualitySheet(selected: AgentQuality, capCents: Int, onSelect: (AgentQuality) -> Unit, onDismiss: () -> Unit) {
    val c = DotTheme.c
    DotSheet(onDismiss) {
        SheetLabel("Thinking mode")
        AgentQuality.entries.forEach { quality ->
            val chosen = quality == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .heightIn(min = 64.dp)
                    .clip(DotRadius.Tile)
                    .border(1.dp, if (chosen) c.textPrimary else c.border, DotRadius.Tile)
                    .clickable(role = Role.RadioButton) {
                        onSelect(quality)
                        onDismiss()
                    }
                    .semantics { this.selected = chosen }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(RunQuality.icon(quality)), null, Modifier.size(22.dp), tint = c.textPrimary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(quality.label, style = DotType.Body.copy(fontSize = 16.sp), color = c.textPrimary)
                    Text(quality.summary, Modifier.padding(top = 2.dp), style = DotType.BodySmall, color = c.textSecondary)
                    Text(
                        RunQuality.capLine(quality, capCents).uppercase(Locale.US),
                        Modifier.padding(top = 6.dp),
                        style = DotType.Caption,
                        color = c.textTertiary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(20.dp).border(1.5.dp, if (chosen) c.textPrimary else c.borderStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (chosen) Box(Modifier.size(10.dp).background(c.textPrimary, CircleShape))
                }
            }
        }
        Text(
            "Change the cap in Tune, Search & tools".uppercase(Locale.US),
            Modifier.padding(top = 4.dp),
            style = DotType.Caption,
            color = c.textTertiary
        )
    }
}
