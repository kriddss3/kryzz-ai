package ai.daylight.assistant.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTag
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotDissolve
import ai.daylight.assistant.ui.shell.ScreenBackHandler
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import java.util.Locale

/**
 * v2.1: the first-run tour (TourPage). It sits where Home's board is, under the ink strip,
 * until it is finished or skipped; Back steps back a page and closes it from the first.
 */
@Composable
fun TourScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = TourFlow.page(index)
    ScreenBackHandler(enabled = true) {
        val back = TourFlow.back(index)
        if (back != null) index = back else onDone()
    }
    Column(modifier.fillMaxSize().navigationBarsPadding().testTag("tour")) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = if (index > 0) 0.dp else 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (index > 0) {
                DotIconButton(R.drawable.ic_kz_back, "Back", { TourFlow.back(index)?.let { index = it } })
                Spacer(Modifier.width(4.dp))
            }
            Text(
                TourFlow.counter(index),
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = "Tour, page ${index + 1} of ${TourFlow.count}" },
                style = DotType.Label,
                color = c.textTertiary
            )
            if (!TourFlow.isLast(index)) {
                Text(
                    "SKIP",
                    Modifier
                        .clip(DotRadius.Pill)
                        .clickable(onClickLabel = "Skip the tour", onClick = onDone)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 16.dp)
                        .testTag("tour_skip"),
                    style = DotType.Label,
                    color = c.textSecondary
                )
            }
        }
        val scroll = rememberScrollState()
        LaunchedEffect(index) { scroll.scrollTo(0) }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .verticalScroll(scroll)
                .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)
        ) {
            key(page) { TourHero(page) }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(TourFlow.count) { i ->
                    val current = i == index
                    Box(Modifier.size(if (current) 8.dp else 6.dp).background(if (current) c.accentText else c.dotOff, CircleShape))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                page.title,
                Modifier.clearAndSetSemantics {
                    heading()
                    contentDescription = page.title.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
                },
                style = DotType.DotDisplay,
                color = c.textPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(page.body, style = DotType.Body, color = c.textSecondary)
            if (page == TourPage.KEYS) {
                Spacer(Modifier.height(20.dp))
                TourPage.keys.forEach { tourKey -> KeyLinkRow(tourKey) }
            }
        }
        DotPillButton(
            TourFlow.primaryLabel(index),
            onClick = { if (TourFlow.isLast(index)) onDone() else index = TourFlow.next(index) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
                .testTag("tour_next"),
            icon = if (TourFlow.isLast(index)) R.drawable.ic_kz_check else R.drawable.ic_kz_arrow_right
        )
    }
}

@Composable
private fun TourHero(page: TourPage) {
    val motion = LocalKryzzMotionEnabled.current
    var shown by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { shown = true }
    DotDissolve(visible = shown, cover = DotTheme.c.bg) {
        Icon(painterResource(page.icon), null, Modifier.size(88.dp), tint = DotTheme.c.textPrimary)
    }
}

/** A provider with what its key is for, REQUIRED/OPTIONAL, and a row that opens its key page. */
@Composable
internal fun KeyLinkRow(tourKey: TourKey, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Column(modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tourKey.name.uppercase(Locale.US), Modifier.weight(1f), style = DotType.Label, color = c.textSecondary)
            DotTag(if (tourKey.required) "Required" else "Optional", color = if (tourKey.required) c.textPrimary else c.textTertiary)
        }
        Text(tourKey.purpose, Modifier.padding(top = 4.dp), style = DotType.BodySmall, color = c.textTertiary)
        GetKeyLink(tourKey.url, Modifier.padding(top = 6.dp))
        HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 1.dp, color = c.border)
    }
}

/** "GET A KEY  openrouter.ai/settings/keys ↗": opens the provider's key page in the browser. */
@Composable
internal fun GetKeyLink(url: String, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val context = LocalContext.current
    val shown = KeyLinks.display(url)
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(DotRadius.Pill)
            .clickable { openLink(context, url) }
            .clearAndSetSemantics {
                contentDescription = "Get a key at $shown"
                role = Role.Button
                onClick(label = "Open $shown") { openLink(context, url); true }
            }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("GET A KEY", style = DotType.Label, color = c.accentText)
        Spacer(Modifier.width(10.dp))
        Text(shown, Modifier.weight(1f, fill = false), style = DotType.Caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Icon(painterResource(R.drawable.ic_kz_external), null, Modifier.size(14.dp), tint = c.textSecondary)
    }
}

private fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
