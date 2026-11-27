package ai.daylight.assistant.ui.make

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.MINIMAX_VIDEO_RESOLUTION
import ai.daylight.assistant.domain.MINIMAX_VIDEO_SECONDS
import ai.daylight.assistant.domain.MakeKind
import ai.daylight.assistant.domain.MakeOutcome
import ai.daylight.assistant.domain.MakeRender
import ai.daylight.assistant.domain.MakeSources
import ai.daylight.assistant.domain.MakeStyle
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.RecentOutput
import ai.daylight.assistant.domain.mediaUsesMiniMax
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotNotice
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkLine
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.shell.LocalInkStrip
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.tune.DotSegments
import ai.daylight.assistant.ui.tune.SegmentOption
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Make (kit create-image and create-video, C7). Three states on one page:
 *  - writing: the prompt card is the text field, photos or clips can be added to edit or to
 *    start from, the frame takes the chosen ratio, and the dials show only what the request
 *    really carries (Ratio when the provider takes one, Model, and the fixed MiniMax video facts);
 *  - rendering: indeterminate dots, the elapsed time and the job's real status (video), with
 *    Cancel and Run in background (the render runs in the app, not in this screen);
 *  - a result: the image, or the clip with a dot scrubber and a filmstrip, with Save to the
 *    gallery, Variant (the same prompt again) and Share.
 * Recent ink lists the newest made files from every conversation.
 */
@Composable
fun MakeScreen(
    vm: MakeViewModel,
    routeKind: MakeKind?,
    onModels: (ModelPurpose) -> Unit,
    onLibrary: () -> Unit,
    onBackground: () -> Unit,
    onInkwell: () -> Unit,
) {
    val c = DotTheme.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strip = LocalInkStrip.current
    val motion = LocalKryzzMotionEnabled.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // The kind in the route (the Home composer's "make an image instead") applies once.
    var routeKindApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!routeKindApplied) {
            routeKind?.let(vm::setKind)
            routeKindApplied = true
        }
    }

    val composeReq by vm.composeRequests.collectAsStateWithLifecycle()
    var handledComposeReq by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(composeReq) {
        if (composeReq > handledComposeReq) {
            handledComposeReq = composeReq
            vm.enterComposeFromExternal()
        }
    }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val render by vm.render.collectAsStateWithLifecycle()
    val jobStatus by vm.jobStatus.collectAsStateWithLifecycle()
    val recentOrNull by vm.recent.collectAsStateWithLifecycle()
    val kindPref by vm.kind.collectAsStateWithLifecycle()
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val ratios by vm.ratios.collectAsStateWithLifecycle()
    val sources by vm.sources.collectAsStateWithLifecycle()
    val sourceNote by vm.sourceNote.collectAsStateWithLifecycle()
    val openRouterVideo by vm.openRouterVideoModels.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val composing by vm.composing.collectAsStateWithLifecycle()
    val dismissed by vm.dismissedSerial.collectAsStateWithLifecycle()
    val saved by vm.savedIds.collectAsStateWithLifecycle()
    val recent = recentOrNull.orEmpty()

    val running: MakeRender? = render?.takeIf { it.running }
    val shown: RecentOutput? = if (running != null || composing) null else selected ?: MakeModel.resultFor(render, recent)
    // Phase D: a render that finishes while Make is open develops in as dots. One that
    // finished while you were away (or one picked from recent ink) just shows.
    var watchedSerial by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(running?.serial) { running?.serial?.let { watchedSerial = it } }
    val developId = render?.takeIf { it.serial == watchedSerial && selected == null }
        ?.let { MakeModel.resultFor(it, recent)?.output?.id }
    // A finished render or a picked file is about to show; don't flash the writing state first.
    val waitingForRecent = recentOrNull == null && running == null && !composing &&
        (selected != null || render?.outcome == MakeOutcome.DONE)
    val kind = running?.kind ?: shown?.let { MakeModel.kindOf(it.output) } ?: kindPref
    val notice = render?.takeIf {
        !it.running && shown == null && it.serial != dismissed && (it.outcome == MakeOutcome.FAILED || it.outcome == MakeOutcome.STOPPED)
    }

    // What the request carries: the model, its provider and the ratios it takes.
    val model = running?.model ?: settings.makeModel(kind)
    val miniMax = mediaUsesMiniMax(kind == MakeKind.VIDEO, settings.chatProvider, model)
    LaunchedEffect(kind, miniMax) { if (kind == MakeKind.VIDEO && !miniMax) vm.loadVideoModels() }
    val catalog = when {
        miniMax -> if (kind == MakeKind.IMAGE) vm.miniMaxImageModels else vm.miniMaxVideoModels
        kind == MakeKind.VIDEO -> openRouterVideo
        else -> null
    }
    val choices = remember(kind, miniMax, model, catalog) { MakeModel.supportedRatios(kind, miniMax, model, catalog) }
    // A render sent without a ratio (from the Home composer) uses the default one.
    val ratio = if (running != null) running.ratio ?: MakeModel.defaultRatio(choices) else MakeModel.ratioFor(ratios[kind], choices)

    // Facts read from the file on show (pixel size, a clip's length).
    val shownOutput = shown?.output
    var facts by remember(shownOutput?.id) { mutableStateOf(MediaFacts()) }
    LaunchedEffect(shownOutput?.id) {
        if (shownOutput != null) facts = withContext(Dispatchers.IO) { MakeMedia.facts(shownOutput) }
    }

    // The elapsed clock ticks once a second while a render runs.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running?.serial) {
        while (running != null) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }

    val liveStatus = jobStatus?.takeIf { running != null && it.conversationId == running.conversationId }?.status
    InkStripEffect(MakeModel.inkLine(running, liveStatus, shownOutput, facts.durationMs, notice?.outcome), arm = Arm.MAKE)

    var ratioSheet by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    var focusAsk by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusAsk) {
        if (focusAsk == 0) return@LaunchedEffect
        // The field appears a frame after the state flips to writing.
        repeat(3) {
            withFrameNanos { }
            if (runCatching { focus.requestFocus() }.isSuccess) {
                keyboard?.show()
                return@LaunchedEffect
            }
        }
    }

    // Android 9 has no permission-free gallery insert, so it saves through the file picker.
    var pendingSave by remember { mutableStateOf<GeneratedOutput?>(null) }
    val pickSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val output = pendingSave
        pendingSave = null
        if (uri != null && output != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { MakeMedia.writeTo(context, uri, output) }
                if (ok) vm.markSaved(output.id)
                strip?.flash(InkLine(if (ok) "MAKE SAVED" else "MAKE SAVE FAILED"))
            }
        }
    }
    val pickSources = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MakeSources.MAX)) { uris ->
        if (uris.isNotEmpty()) vm.addMedia(uris)
    }
    fun addSources() {
        pickSources.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    fun save(output: GeneratedOutput) {
        if (MakeMedia.canSaveToGallery) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { MakeMedia.saveToGallery(context.applicationContext, output) }
                if (ok) vm.markSaved(output.id)
                strip?.flash(InkLine(if (ok) "MAKE SAVED" else "MAKE SAVE FAILED"))
            }
        } else {
            pendingSave = output
            runCatching { pickSave.launch(MakeMedia.galleryName(output)) }
        }
    }

    val noun = if (kind == MakeKind.VIDEO) "clip" else "image"
    val dials = buildList {
        if (ratio != null) {
            val fromPhoto = kind == MakeKind.VIDEO && miniMax && MakeSources.frameSetsShape(sources)
            add(
                DialSpec(
                    "Ratio",
                    if (fromPhoto) "From photo" else ratio,
                    onClick = if (running == null && choices.size > 1 && !fromPhoto) ({ ratioSheet = true }) else null,
                    ratio = ratio.takeUnless { fromPhoto },
                )
            )
        }
        add(
            DialSpec(
                "Model",
                model.substringAfter('/').ifBlank { "Not set" },
                onClick = if (running == null) ({ onModels(kind.purpose) }) else null,
                share = 1.6f
            )
        )
        if (kind == MakeKind.VIDEO && miniMax) {
            add(DialSpec("Length", "$MINIMAX_VIDEO_SECONDS s"))
            add(DialSpec("Quality", MINIMAX_VIDEO_RESOLUTION))
        }
        add(DialSpec("Via", MakeModel.providerName(miniMax)))
    }
    val aspect = MakeModel.frameAspect(kind, ratio)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 24.dp)
    ) {
        // Title (opens the chat library, as on Talk, Run and Tune) and the Image/Video switch.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(DotRadius.Tile)
                    .clickable(onClickLabel = "Open chat library", onClick = onLibrary)
                    .clearAndSetSemantics {
                        contentDescription = "Make. Open chat library"
                        onClick(label = "Open chat library") { onLibrary(); true }
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                DotMatrixText("MAKE", pitch = 4.2.dp, on = c.textPrimary, off = Color.Transparent)
            }
            Spacer(Modifier.width(16.dp))
            DotSegments(
                options = listOf(
                    SegmentOption("Image", R.drawable.ic_kz_image, tag = "make_kind_image"),
                    SegmentOption("Video", R.drawable.ic_kz_video, tag = "make_kind_video")
                ),
                selected = kind.ordinal,
                onSelect = { index ->
                    val target = MakeKind.entries[index]
                    when {
                        target == kind -> Unit
                        running != null -> strip?.flash(InkLine("MAKE BUSY"))
                        shown != null -> {
                            vm.edit(shown, target)
                            focusAsk++
                        }
                        else -> vm.setKind(target)
                    }
                },
                // v2.1: fills the row (it was a fixed 176dp, tight with larger text).
                modifier = Modifier.weight(1f).widthIn(max = 260.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            waitingForRecent -> Spacer(Modifier.height(240.dp))
            running != null -> {
                PromptCard(
                    running.prompt,
                    specs = MakeModel.styleSpecs(running.style) + MakeSources.labels(sources),
                )
                Spacer(Modifier.height(20.dp))
                FrameWithDials(aspect, ratio, dials)
                Spacer(Modifier.height(18.dp))
                RenderProgress(
                    elapsed = MakeModel.elapsedLabel(now - running.startedAt),
                    status = "${MakeModel.statusWord(running.kind, liveStatus)} at ${MakeModel.providerName(miniMax)}",
                    detail = model.substringAfter('/')
                )
                Spacer(Modifier.height(20.dp))
                // v2.1: one button a row; side by side "Run in background" lost its last words.
                DotPillButton("Run in background", onBackground, Modifier.fillMaxWidth(), style = DotButtonStyle.White)
                Spacer(Modifier.height(10.dp))
                DotPillButton("Cancel", { vm.cancel() }, Modifier.fillMaxWidth(), style = DotButtonStyle.Ghost, icon = R.drawable.ic_kz_close)
            }
            shown != null -> ResultBody(
                shown = shown,
                facts = facts,
                develop = motion && shown.output.id == developId,
                saved = shown.output.id in saved,
                onSave = { save(it) },
                onVariant = {
                    val same = MakeModel.ratioLabel(facts.width, facts.height)?.takeIf { it in choices }
                    vm.variant(shown, same ?: ratio)
                },
                onEdit = {
                    vm.edit(shown)
                    focusAsk++
                },
                onOpen = { MakeMedia.open(context, shown.output) },
                onShare = { MakeMedia.share(context, shown.output) },
                onUse = {
                    vm.useMade(shown)
                    focusAsk++
                },
            )
            else -> {
                PromptCard(
                    text = prompt,
                    editable = true,
                    onText = vm::setPrompt,
                    placeholder = if (sources.isEmpty()) "Describe the $noun" else "Say what to change or make",
                    focusRequester = focus,
                    onEdit = { focusAsk++ }
                )
                Spacer(Modifier.height(16.dp))
                SourceStrip(
                    sources = sources,
                    caption = MakeSources.caption(kind, sources, miniMax),
                    onAdd = ::addSources,
                    onRemove = vm::removeSource,
                )
                sourceNote?.let { note ->
                    Spacer(Modifier.height(12.dp))
                    DotNotice(note, icon = R.drawable.ic_kz_info)
                }
                Spacer(Modifier.height(16.dp))
                StylePicker(settings.makeStyle, vm::setStyle)
                Spacer(Modifier.height(20.dp))
                FrameWithDials(aspect, ratio, dials)
                Spacer(Modifier.height(20.dp))
                if (notice != null) {
                    DotNotice(
                        when {
                            notice.outcome == MakeOutcome.STOPPED && notice.kind == MakeKind.VIDEO ->
                                "Stopped. The provider may still finish the job it was sent."
                            notice.outcome == MakeOutcome.STOPPED -> "Stopped."
                            else -> notice.error ?: "The render failed."
                        },
                        icon = if (notice.outcome == MakeOutcome.FAILED) R.drawable.ic_kz_warning else R.drawable.ic_kz_info
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (model.isBlank()) {
                    DotNotice(if (kind == MakeKind.VIDEO) "Choose a video model first: tap Model." else "Choose an image model first: tap Model.")
                    Spacer(Modifier.height(12.dp))
                }
                DotPillButton(
                    label = if (kind == MakeKind.VIDEO) "Make clip" else "Make image",
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        vm.run(ratio)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = DotButtonStyle.Accent,
                    enabled = prompt.isNotBlank() && model.isNotBlank(),
                    icon = if (kind == MakeKind.VIDEO) R.drawable.ic_kz_video else R.drawable.ic_kz_image
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        RecentInk(
            recent = recent,
            shownId = shownOutput?.id,
            onTile = { item ->
                // While a render runs the frame is busy, so a tile opens in the phone's viewer.
                if (running != null || !MakeModel.isMedia(item.output)) MakeMedia.open(context, item.output) else vm.show(item)
            },
            onMore = onInkwell
        )
    }

    if (ratioSheet && choices.isNotEmpty()) {
        RatioSheet(
            kind = kind,
            choices = choices,
            selected = ratio,
            onPick = {
                vm.setRatio(kind, it)
                ratioSheet = false
            },
            onDismiss = { ratioSheet = false }
        )
    }
}

/** A made image or clip, what it is, its prompt, and Save / Variant / Share. */
@Composable
private fun ResultBody(
    shown: RecentOutput,
    facts: MediaFacts,
    develop: Boolean,
    saved: Boolean,
    onSave: (GeneratedOutput) -> Unit,
    onVariant: () -> Unit,
    onEdit: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onUse: () -> Unit,
) {
    val output = shown.output
    val kind = MakeModel.kindOf(output) ?: MakeKind.IMAGE
    val shownAt = remember(output.id) { System.currentTimeMillis() }
    val zone = remember { ZoneId.systemDefault() }
    val aspect = if (facts.width > 0 && facts.height > 0) facts.width.toFloat() / facts.height else null
    if (output.kind == OutputKind.VIDEO) {
        val source = output.localPath ?: output.remoteUrl.orEmpty()
        key(source) {
            val player = rememberMakePlayer(source)
            MakeVideoFrame(
                player,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio((aspect ?: (16f / 9f)).coerceIn(0.5f, 2.4f))
                    .clip(DotRadius.Tile)
                    .background(Color.Black)
            )
            val duration = player.durationMs.takeIf { it > 0 } ?: facts.durationMs ?: 0L
            DotScrubber(player.positionMs, duration, player::seekTo, Modifier.padding(top = 10.dp))
            PlayerControls(player, Modifier.padding(top = 2.dp))
            output.localPath?.let { path ->
                Filmstrip(path, duration, player.positionMs, player::seekTo, Modifier.padding(top = 12.dp))
            }
        }
    } else {
        ImageResult(output.localPath.orEmpty(), aspect, shown.prompt ?: output.title, onOpen, develop = develop)
    }
    ReadyRow(
        MakeModel.readyCaption(kind),
        MakeModel.madeCaption(shown.createdAt, shownAt, zone, facts.durationMs.takeIf { kind == MakeKind.VIDEO }),
        Modifier.padding(top = 18.dp)
    )
    PromptCard(
        text = shown.prompt ?: output.title,
        modifier = Modifier.padding(top = 14.dp),
        label = false,
        specs = MakeModel.specs(kind, facts.durationMs, facts.width, facts.height),
        onEdit = onEdit
    )
    // v2.1: Save and Share on one row, Variant on its own, so no label is cut short.
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotPillButton(
            label = if (saved) "Saved to phone" else "Save to phone",
            onClick = { onSave(output) },
            modifier = Modifier.weight(1f),
            style = DotButtonStyle.Accent,
            enabled = output.localPath != null,
            icon = if (saved) R.drawable.ic_kz_check else R.drawable.ic_kz_download
        )
        RoundIconButton(R.drawable.ic_kz_share, "Share", onShare)
    }
    DotPillButton(
        label = if (kind == MakeKind.VIDEO) "Use this clip" else "Use this image",
        onClick = onUse,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        style = DotButtonStyle.Ghost,
        enabled = output.localPath != null,
        icon = R.drawable.ic_kz_plus,
    )
    DotPillButton(
        label = "Make a variant",
        onClick = onVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        style = DotButtonStyle.Ghost,
        enabled = shown.prompt != null,
        icon = R.drawable.ic_kz_retry
    )
}

/**
 * v2.1: Make's look. Nothing dresses the prompt in the Nothing OS style (black, white and one
 * red, dot matrix, Glyph light); Plain sends the prompt as written. It stays as set.
 */
@Composable
private fun StylePicker(style: MakeStyle, onStyle: (MakeStyle) -> Unit) {
    val c = DotTheme.c
    Column(Modifier.fillMaxWidth()) {
        Text("STYLE", Modifier.padding(bottom = 8.dp), style = DotType.Label, color = c.textTertiary)
        DotSegments(
            options = listOf(
                SegmentOption(MakeStyle.PLAIN.label, R.drawable.ic_kz_image, tag = "make_style_plain"),
                SegmentOption(MakeStyle.NOTHING.label, R.drawable.ic_kz_grid, tag = "make_style_nothing")
            ),
            selected = style.ordinal,
            onSelect = { onStyle(MakeStyle.entries[it]) }
        )
        Text(style.caption, Modifier.padding(top = 8.dp), style = DotType.BodySmall, color = c.textTertiary)
    }
}
