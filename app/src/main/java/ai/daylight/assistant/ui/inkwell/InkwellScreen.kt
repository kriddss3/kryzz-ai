package ai.daylight.assistant.ui.inkwell

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.RecentOutput
import ai.daylight.assistant.ui.crew.rememberMinuteClock
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.make.MakeMedia
import ai.daylight.assistant.ui.shell.InkLine
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.shell.LocalInkStrip
import ai.daylight.assistant.ui.tune.DotSegments
import ai.daylight.assistant.ui.tune.SegmentOption
import ai.daylight.assistant.ui.tune.TuneEmpty
import ai.daylight.assistant.ui.tune.TunePageHeader
import ai.daylight.assistant.ui.writeOutput
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Inkwell (C10): everything Kryzz has made, across all arms, in one time-ordered feed.
 * Images and clips are tiles two to a row, files are Run's file cards, under day headers.
 * A tap opens the piece's sheet (Save, Share, Open, Open the chat). Opening the Inkwell
 * clears Home's badge; pieces made since the last visit carry a NEW tag.
 */
@Composable
fun InkwellScreen(
    vm: InkwellViewModel,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onMake: () -> Unit,
) {
    val c = DotTheme.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strip = LocalInkStrip.current
    val loaded by vm.items.collectAsStateWithLifecycle()
    val seenAtOnOpen by vm.seenAtOnOpen.collectAsStateWithLifecycle()
    val savedKeys by vm.savedKeys.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(InkFilter.ALL) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val items = loaded.orEmpty()
    val now = rememberMinuteClock()
    val zone = remember { ZoneId.systemDefault() }
    val rows = remember(items, filter, now) { InkwellModel.rows(items, filter, now, zone) }
    val shownCount = remember(items, filter) { items.count { filter.matches(it.output) } }

    InkStripEffect(if (loaded == null) InkLine("INKWELL") else InkLines.inkwell(shownCount, filter.noun))

    // Everything the feed holds has now been shown, so Home's badge stops counting it.
    val newest = InkwellModel.newest(items)
    LaunchedEffect(newest) { newest?.let(vm::markSeen) }

    fun flash(ok: Boolean) = strip?.flash(InkLine(if (ok) "INKWELL SAVED" else "INKWELL SAVE FAILED"))

    // Files (and images on Android 9) are saved where the user picks.
    var pendingSave by remember { mutableStateOf<RecentOutput?>(null) }
    val pickSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val item = pendingSave
        pendingSave = null
        if (uri != null && item != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { writeOutput(context, uri, item.output) }.isSuccess }
                if (ok) vm.markSaved(InkwellModel.key(item))
                flash(ok)
            }
        }
    }
    fun save(item: RecentOutput) {
        val output = item.output
        val tile = InkwellModel.isTile(output)
        when {
            // Images and clips go to the gallery (Pictures/Kryzz, Movies/Kryzz), as in Make.
            tile && output.localPath != null && MakeMedia.canSaveToGallery -> scope.launch {
                val ok = withContext(Dispatchers.IO) { MakeMedia.saveToGallery(context.applicationContext, output) }
                if (ok) vm.markSaved(InkwellModel.key(item))
                flash(ok)
            }
            // A picture that exists only as a link: the browser or viewer saves it.
            tile && output.localPath == null -> MakeMedia.open(context, output)
            else -> {
                pendingSave = item
                runCatching { pickSave.launch(if (tile) MakeMedia.galleryName(output) else output.fileName) }
            }
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TunePageHeader("Inkwell", onBack, mark = R.drawable.kz_led_inkwell)
        if (items.isNotEmpty()) {
            Text(
                InkwellModel.summary(items),
                Modifier.padding(start = 20.dp, end = 20.dp),
                style = DotType.Caption,
                color = c.textTertiary
            )
            DotSegments(
                options = InkFilter.entries.map { SegmentOption(it.label, tag = "ink_filter_${it.name.lowercase()}") },
                selected = filter.ordinal,
                onSelect = { filter = InkFilter.entries[it] },
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp)
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp).testTag("inkwell_feed"),
            contentPadding = PaddingValues(start = 20.dp, end = 8.dp, bottom = 24.dp)
        ) {
            when {
                loaded == null -> Unit
                rows.isEmpty() -> item(key = "empty") {
                    val none = items.isEmpty()
                    val (words, body) = InkwellModel.emptyWords(if (none) InkFilter.ALL else filter)
                    TuneEmpty(
                        dotIcon = R.drawable.ic_kz_ink_dot,
                        words = words,
                        body = body,
                        modifier = Modifier.padding(top = 56.dp),
                        action = if (none) "Make an image" else null,
                        onAction = if (none) onMake else null,
                        actionIcon = R.drawable.ic_kz_image
                    )
                }
                else -> items(rows, key = { it.key }, contentType = { it::class }) { row ->
                    when (row) {
                        is InkRow.Header -> InkDayHeader(row.label)
                        is InkRow.Media -> Row(
                            Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.items.forEach { item ->
                                InkTile(
                                    item = item,
                                    fresh = InkwellModel.isNew(item, seenAtOnOpen),
                                    zone = zone,
                                    onClick = { openKey = InkwellModel.key(item) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.items.size == 1) Spacer(Modifier.weight(1f))
                        }
                        is InkRow.File -> Box(Modifier.padding(bottom = 10.dp)) {
                            InkFileRow(
                                item = row.item,
                                fresh = InkwellModel.isNew(row.item, seenAtOnOpen),
                                zone = zone,
                                onClick = { openKey = InkwellModel.key(row.item) },
                                onSave = { save(row.item) }
                            )
                        }
                    }
                }
            }
        }
    }

    val openItem = openKey?.let { key -> items.firstOrNull { InkwellModel.key(it) == key } }
    if (openItem != null) {
        InkSheet(
            item = openItem,
            saved = InkwellModel.key(openItem) in savedKeys,
            zone = zone,
            onSave = { save(openItem) },
            onShare = { MakeMedia.share(context, openItem.output) },
            onOpen = { MakeMedia.open(context, openItem.output) },
            onOpenChat = { onOpenConversation(openItem.conversationId) },
            onDismiss = { openKey = null }
        )
    }
}
