package ai.daylight.assistant.ui.dot

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.effects.ThinkingDots
import java.util.Locale

/*
 * Kit controls for pages that have no mockup (settings subpages, models, memory, cron).
 * They follow components.md §8, §10, §12 and §14 and reuse the Tune look: ruled rows,
 * white-on-black selection, mono caps labels, and yellow only on the one primary action.
 */

/** How a [DotPillButton] is filled. */
enum class DotButtonStyle {
    /** Yellow with black text: the one primary action on a screen. */
    Accent,

    /** White with black text, like Run's "Stop run". */
    White,

    /** A 1dp outline: secondary actions. */
    Ghost,

    /** Red with white text: a destructive confirm. Deleting is not a "live" yellow action. */
    Danger,
}

/**
 * A 48dp pill button. Disabled draws the kit's "unlit" dashed outline. While [busy] it shows
 * stepping dots instead of the label and ignores taps.
 */
@Composable
fun DotPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: DotButtonStyle = DotButtonStyle.Accent,
    enabled: Boolean = true,
    busy: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val c = DotTheme.c
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(DotMotion.Instant), label = "press")
    val live = enabled || busy
    val bg = when {
        !live -> Color.Transparent
        style == DotButtonStyle.Accent -> if (pressed) c.accentPressed else c.accent
        style == DotButtonStyle.White -> c.textPrimary
        style == DotButtonStyle.Danger -> c.error
        pressed -> c.surfaceHigh
        else -> Color.Transparent
    }
    val fg = when {
        !live -> c.textDisabled
        style == DotButtonStyle.Ghost -> c.textPrimary
        style == DotButtonStyle.Accent -> c.onAccent
        // White on #D71920 is 5.0:1 in both themes.
        style == DotButtonStyle.Danger -> DotColors.TextPrimary
        else -> c.bg
    }
    val outline = when {
        !live -> Modifier.dashedBorder(c.borderStrong, radius = 24.dp)
        style == DotButtonStyle.Ghost -> Modifier.border(1.dp, c.borderStrong, DotRadius.Pill)
        else -> Modifier
    }
    Box(
        modifier
            .scale(scale)
            .heightIn(min = 48.dp)
            .clip(DotRadius.Pill)
            .background(bg, DotRadius.Pill)
            .then(outline)
            .clickable(src, null, enabled = enabled && !busy, role = Role.Button, onClickLabel = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { if (busy) contentDescription = "$label, working" }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            ThinkingDots(dot = 6.dp, on = fg, off = fg.copy(alpha = .3f))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(painterResource(icon), null, Modifier.size(18.dp), tint = fg)
                Text(label, style = DotType.Button, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A 48dp round icon button with a 22dp outline icon. [label] is what TalkBack reads. */
@Composable
fun DotIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = DotTheme.c.textPrimary,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), null, Modifier.size(iconSize), tint = if (enabled) tint else DotTheme.c.textDisabled)
    }
}

/**
 * A kit text field: an optional mono caps [label] above, a 52dp surface box with a 1dp
 * border (stronger while focused, red on error), Geist 15 text and a yellow caret. The
 * [supporting] line sits under it, red when [isError], with an optional count at the end.
 * [fieldModifier] goes on the text field itself (test tags, focus requesters).
 */
@Composable
fun DotField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    @DrawableRes leadingIcon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    supportingEnd: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldModifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val border = when {
        isError -> c.error
        focused -> c.textSecondary
        else -> c.border
    }
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label.uppercase(Locale.US),
                Modifier.padding(bottom = 8.dp),
                style = DotType.Label,
                color = c.textSecondary
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(c.surface, DotRadius.Tile)
                .border(1.dp, border, DotRadius.Tile)
                .padding(start = if (leadingIcon != null) 14.dp else 16.dp, end = if (trailing != null) 4.dp else 16.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
        ) {
            if (leadingIcon != null) {
                Icon(
                    painterResource(leadingIcon), null,
                    Modifier.padding(top = if (singleLine) 0.dp else 15.dp).size(20.dp),
                    tint = c.textTertiary
                )
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = fieldModifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = DotType.Body.copy(color = if (enabled) c.textPrimary else c.textTertiary),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    interactionSource = src,
                    decorationBox = { inner ->
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, style = DotType.Body, color = c.textTertiary, maxLines = if (singleLine) 1 else 3)
                        }
                        inner()
                    }
                )
            }
            if (trailing != null) {
                Box(Modifier.padding(top = if (singleLine) 0.dp else 2.dp)) { trailing() }
            }
        }
        if (supporting != null || supportingEnd != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp)) {
                Text(
                    supporting.orEmpty(),
                    Modifier.weight(1f),
                    style = DotType.BodySmall,
                    color = if (isError) c.error else c.textTertiary
                )
                if (supportingEnd != null) {
                    Text(supportingEnd, style = DotType.Data, color = c.textTertiary)
                }
            }
        }
    }
}

/**
 * A kit pill chip, 40dp tall inside a 48dp touch target. Selected is white with black
 * text, otherwise a 1dp outline. [toggle] makes it a checkbox (several can be on), and
 * [Role.Button] makes it a plain action.
 */
@Composable
fun DotPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    toggle: Boolean = false,
    role: Role = Role.RadioButton,
) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    val fg = if (selected) c.bg else c.textSecondary
    val action = if (toggle) {
        Modifier.toggleable(value = selected, role = Role.Checkbox) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    } else if (role == Role.Button) {
        Modifier.clickable(role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    } else {
        Modifier.selectable(selected = selected, role = role) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    }
    Box(modifier.minimumInteractiveComponentSize().then(action), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .height(40.dp)
                .background(if (selected) c.textPrimary else Color.Transparent, DotRadius.Pill)
                .border(1.dp, if (selected) c.textPrimary else c.border, DotRadius.Pill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (icon != null) Icon(painterResource(icon), null, Modifier.size(16.dp), tint = fg)
            Text(label.uppercase(Locale.US), style = DotType.Label, color = fg, maxLines = 1)
        }
    }
}

/** A small mono outline tag ("STARTER", "IQ 62.1", "ENCRYPTED"); it is never a control. */
@Composable
fun DotTag(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, color: Color = DotTheme.c.textSecondary) {
    val c = DotTheme.c
    Row(
        modifier
            .height(24.dp)
            .border(1.dp, c.border, DotRadius.Pill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(12.dp), tint = color)
        Text(text.uppercase(Locale.US), style = DotType.Label.copy(fontSize = 10.sp), color = color, maxLines = 1)
    }
}

/** The kit radio: a 1.5dp ring, filled with a white dot when chosen. Draw only. */
@Composable
fun DotRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Canvas(modifier.size(20.dp)) {
        val stroke = 1.5.dp.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            if (selected) c.textPrimary else c.borderStrong,
            radius = size.minDimension / 2f - stroke / 2f,
            center = centre,
            style = Stroke(stroke)
        )
        if (selected) drawCircle(c.textPrimary, 4.5.dp.toPx(), centre)
    }
}

/** Kit notice card (§12): an info icon and a short Body Small note on surfaceRaised. */
@Composable
fun DotNotice(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int = R.drawable.ic_kz_info) {
    val c = DotTheme.c
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surfaceRaised, DotRadius.Tile)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(painterResource(icon), null, Modifier.padding(top = 1.dp).size(18.dp), tint = c.textSecondary)
        Text(text, Modifier.weight(1f), style = DotType.BodySmall, color = c.textSecondary)
    }
}
