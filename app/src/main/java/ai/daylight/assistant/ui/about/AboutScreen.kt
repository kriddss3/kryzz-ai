package ai.daylight.assistant.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.BuildConfig
import ai.daylight.assistant.R
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.mascot.DotSquid
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.tune.TuneCaption
import ai.daylight.assistant.ui.tune.TunePage
import ai.daylight.assistant.ui.tune.TuneRow
import ai.daylight.assistant.ui.tune.TuneSection
import ai.daylight.assistant.ui.tune.TuneTrailing
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * About (C11): a Tune depth page. The squid on its LED field next to KRYZZ AI and the
 * version, then ruled facts: who made it, what powers it, what stays private, and the font
 * licences, which open in a sheet. Opens from Tune's About row and three taps on CHATS.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = DotTheme.c
    var showLicences by rememberSaveable { mutableStateOf(false) }
    TunePage("About", onBack) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 28.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            DotSquid(pitch = 4.dp, contentDescription = "Kryzz, the dot squid")
            Spacer(Modifier.width(18.dp))
            Column(Modifier.padding(bottom = 4.dp)) {
                DotMatrixText(
                    "KRYZZ AI",
                    Modifier.semantics { heading() },
                    pitch = 3.4.dp,
                    on = c.textPrimary,
                    off = Color.Transparent,
                    contentDescription = "Kryzz AI"
                )
                Spacer(Modifier.height(10.dp))
                TuneCaption(AboutModel.versionLine(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            }
        }

        TuneSection("Made by")
        AboutModel.madeWith.forEach { TuneRow(it.icon, it.title, it.caption, TuneTrailing.None) }
        Spacer(Modifier.height(24.dp))

        TuneSection("Powered by")
        AboutModel.poweredBy.forEach { TuneRow(it.icon, it.title, it.caption, TuneTrailing.None) }
        Spacer(Modifier.height(24.dp))

        TuneSection("Privacy")
        AboutModel.privacy.forEach { TuneRow(it.icon, it.title, it.caption, TuneTrailing.None) }
        Spacer(Modifier.height(24.dp))

        TuneSection("Licences")
        TuneRow(
            R.drawable.ic_kz_file,
            "Fonts",
            "${AboutModel.FONTS}, ${AboutModel.FONT_LICENCE}",
            onClick = { showLicences = true },
            modifier = Modifier.testTag("about_licences")
        )
    }
    if (showLicences) FontLicencesSheet(onDismiss = { showLicences = false })
}

/** The OFL texts shipped in assets/licenses, one after another in mono. */
@Composable
private fun FontLicencesSheet(onDismiss: () -> Unit) {
    val c = DotTheme.c
    val context = LocalContext.current
    var texts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        texts = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.list(AboutModel.LICENCE_FOLDER).orEmpty().sorted().map { file ->
                    AboutModel.licenceTitle(file) to AboutModel.reflow(
                        context.assets.open("${AboutModel.LICENCE_FOLDER}/$file").bufferedReader().use { it.readText() }
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
    // The texts are long: they scroll inside a capped box so the sheet stops well below the
    // status bar instead of running up under it.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    DotSheet(onDismiss) {
        SheetLabel("Font licences")
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            texts.forEach { (title, body) ->
                Text(
                    title.uppercase(Locale.US),
                    Modifier.padding(top = 14.dp, bottom = 8.dp).semantics { heading() },
                    style = DotType.Label,
                    color = c.textSecondary
                )
                Text(body, style = DotType.Code.copy(fontSize = 12.sp, lineHeight = 18.sp), color = c.textTertiary)
            }
        }
    }
}
