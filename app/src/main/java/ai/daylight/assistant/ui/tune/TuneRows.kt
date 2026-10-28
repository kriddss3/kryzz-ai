package ai.daylight.assistant.ui.tune

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotToggle
import java.util.Locale

/** A section heading: Space Mono caps in textSecondary, 30dp tall (kit `.sh`). */
@Composable
fun TuneSection(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(Locale.US),
            Modifier.weight(1f).semantics { heading() },
            style = DotType.Label,
            color = DotTheme.c.textSecondary
        )
        trailing?.invoke()
    }
}

/** A mono caption line under a control (kit `.cap`). */
@Composable
fun TuneCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.US),
        modifier,
        style = DotType.Caption,
        color = DotTheme.c.textTertiary
    )
}

/** What sits at the end of a [TuneRow]. */
sealed interface TuneTrailing {
    /** Opens a page: a chevron. */
    data object Chevron : TuneTrailing

    /** A switch; the whole row toggles it. */
    data class Switch(val checked: Boolean, val onChange: (Boolean) -> Unit, val enabled: Boolean = true) : TuneTrailing

    /** A fact: nothing to tap. */
    data object None : TuneTrailing
}

/**
 * A ruled Tune row (kit `.rw`): 1dp top rule, 20dp outline icon, Geist 15 title, an optional
 * mono caption, then a chevron or a switch. At least 52dp tall, so it is a full touch target.
 */
@Composable
fun TuneRow(
    @DrawableRes icon: Int,
    title: String,
    caption: String? = null,
    trailing: TuneTrailing = TuneTrailing.Chevron,
    onClick: (() -> Unit)? = null,
) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    val action = when (trailing) {
        is TuneTrailing.Switch -> Modifier.toggleable(
            value = trailing.checked,
            enabled = trailing.enabled,
            role = Role.Switch
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            trailing.onChange(it)
        }
        else -> if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    }
    val dim = trailing is TuneTrailing.Switch && !trailing.enabled
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(action)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = if (dim) c.textDisabled else c.textSecondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = DotType.Body.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    color = if (dim) c.textTertiary else c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        caption.uppercase(Locale.US),
                        Modifier.padding(top = 1.dp),
                        style = DotType.Caption.copy(fontSize = 10.sp, lineHeight = 14.sp),
                        color = c.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when (trailing) {
                TuneTrailing.Chevron -> {
                    Spacer(Modifier.width(10.dp))
                    Icon(painterResource(R.drawable.ic_kz_forward), null, Modifier.size(18.dp), tint = c.textDisabled)
                }
                is TuneTrailing.Switch -> {
                    Spacer(Modifier.width(12.dp))
                    DotToggle(checked = trailing.checked, onChange = null, enabled = trailing.enabled)
                }
                TuneTrailing.None -> Unit
            }
        }
    }
}

/** One option of a [DotSegments] control. */
data class SegmentOption(val label: String, @DrawableRes val icon: Int? = null)

/**
 * Kit segmented control (`.seg`): a raised pill track, the chosen segment white with black
 * text. 48dp tall so every segment is a full touch target.
 */
@Composable
fun DotSegments(options: List<SegmentOption>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.surfaceRaised, DotRadius.Pill)
            .border(1.dp, c.border, DotRadius.Pill)
            .padding(3.dp)
            .selectableGroup()
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            val fg = if (chosen) c.bg else c.textSecondary
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(DotRadius.Pill)
                    .background(if (chosen) c.textPrimary else c.surfaceRaised)
                    .selectable(selected = chosen, role = Role.RadioButton) {
                        if (!chosen) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (option.icon != null) Icon(painterResource(option.icon), null, Modifier.size(14.dp), tint = fg)
                    Text(option.label.uppercase(Locale.US), style = DotType.Label, color = fg, maxLines = 1)
                }
            }
        }
    }
}
