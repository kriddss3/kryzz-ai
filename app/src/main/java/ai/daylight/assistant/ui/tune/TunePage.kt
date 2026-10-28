package ai.daylight.assistant.ui.tune

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadio
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import java.util.Locale

/**
 * A Tune depth page (settings subpages, models, memory, cron): a back button and the page
 * name in dot text, optional [actions] at the end, then the content in Tune's gutters.
 * A [scrollable] page scrolls as one column; otherwise the content brings its own list.
 * The page shrinks above the keyboard so a focused field is never hidden.
 */
@Composable
fun TunePage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().imePadding()) {
        TunePageHeader(title, onBack, actions)
        if (scrollable) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
                content = content
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 8.dp),
                content = content
            )
        }
    }
}

/** The depth-page header: back, the dot title at a 3dp pitch (Tune's own is 4.2dp), actions. */
@Composable
fun TunePageHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val c = DotTheme.c
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotIconButton(R.drawable.ic_kz_back, "Back", onBack)
        Spacer(Modifier.width(4.dp))
        DotMatrixText(
            title.uppercase(Locale.US),
            Modifier.semantics { heading() },
            pitch = 3.dp,
            on = c.textPrimary,
            off = Color.Transparent,
            contentDescription = title
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/**
 * A slider block like Tune's cost cap: the section title with the value in dot text at
 * the end, the dot slider, then an optional caption.
 */
@Composable
fun TuneSlider(
    title: String,
    value: String,
    spoken: String,
    index: Int,
    count: Int,
    onIndexChange: (Int) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
) {
    val c = DotTheme.c
    Column(modifier.fillMaxWidth()) {
        TuneSection(title) {
            DotMatrixText(
                value.uppercase(Locale.US),
                pitch = 3.dp,
                on = if (enabled) c.textPrimary else c.textDisabled,
                off = Color.Transparent,
                contentDescription = spoken
            )
        }
        DotSlider(
            index = index,
            count = count,
            onIndexChange = onIndexChange,
            onChangeFinished = onFinished,
            label = title,
            valueLabel = spoken,
            enabled = enabled
        )
        if (caption != null) TuneDetail(caption)
    }
}

/** A sentence under a control: Geist 13 in textTertiary, for text too long for mono caps. */
@Composable
fun TuneDetail(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 2.dp), style = DotType.BodySmall, color = DotTheme.c.textTertiary)
}

/** A plain paragraph on a Tune page. */
@Composable
fun TuneParagraph(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().padding(bottom = 10.dp), style = DotType.Body, color = DotTheme.c.textSecondary)
}

/** A ruled single-choice row: the kit radio, a title and an optional sentence. */
@Composable
fun TuneChoiceRow(
    title: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = selected, role = Role.RadioButton) {
                    if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DotRadio(selected)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = DotType.Body.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    color = if (selected) c.textPrimary else c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!detail.isNullOrBlank()) {
                    Text(detail, Modifier.padding(top = 2.dp), style = DotType.BodySmall, color = c.textTertiary)
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * Kit empty state (§10): a 96dp dot icon, one or two words in dot text, one line of
 * Body Small and one ghost button.
 */
@Composable
fun TuneEmpty(
    @DrawableRes dotIcon: Int,
    words: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
    @DrawableRes actionIcon: Int? = R.drawable.ic_kz_plus,
) {
    val c = DotTheme.c
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(dotIcon), null, Modifier.size(96.dp), tint = c.textSecondary)
        Spacer(Modifier.height(20.dp))
        DotMatrixText(words.uppercase(Locale.US), pitch = 3.6.dp, on = c.textPrimary, off = Color.Transparent)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            Modifier.widthIn(max = 320.dp),
            style = DotType.BodySmall,
            color = c.textSecondary,
            textAlign = TextAlign.Center
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            DotPillButton(action, onAction, actionModifier, style = DotButtonStyle.Ghost, icon = actionIcon)
        }
    }
}
