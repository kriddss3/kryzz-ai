package ai.daylight.assistant.ui.find

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.FindKind
import ai.daylight.assistant.domain.FindMode
import ai.daylight.assistant.domain.FindStatus
import ai.daylight.assistant.domain.FoundImage
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.effects.ThinkingDots
import ai.daylight.assistant.ui.find.FindViewModel.UseInMakeOutcome
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkLine
import ai.daylight.assistant.ui.shell.InkStripEffect
import ai.daylight.assistant.ui.shell.LocalInkStrip
import ai.daylight.assistant.ui.shell.ScreenBackHandler
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.tune.DotSegments
import ai.daylight.assistant.ui.tune.SegmentOption
import ai.daylight.assistant.ui.tune.TuneEmpty
import coil.ImageLoader
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Find (kit web-images, C8): free images from Wikimedia Commons and live Web browsing via
 * Parallel Search. A query field, Images/Web mode switcher, image filter pills, and either
 * the two-column image masonry or synthesized web results.
 */
@Composable
fun FindScreen(vm: FindViewModel, onLibrary: () -> Unit, onUseInMake: () -> Unit = {}) {
    val c = DotTheme.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strip = LocalInkStrip.current
    val motion = LocalKryzzMotionEnabled.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val mode by vm.mode.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val webState by vm.webState.collectAsStateWithLifecycle()
    val webDraft by vm.webDraft.collectAsStateWithLifecycle()
    val enabled by vm.searchEnabled.collectAsStateWithLifecycle()
    val saved by vm.savedIds.collectAsStateWithLifecycle()
    val loader = vm.imageLoader
    val off = enabled == false

    // Switching web search off stops Find and drops what it found.
    LaunchedEffect(off) {
        if (off) {
            vm.stop()
            vm.stopWeb()
        }
    }

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected: FoundImage? = state.images.firstOrNull { it.id == selectedId }
    // The sheet keeps showing the last image while it slides away.
    val lastShown = remember { arrayOfNulls<FoundImage>(1) }
    if (selected != null) lastShown[0] = selected
    val sheetImage = selected ?: lastShown[0]
    var viewing by rememberSaveable { mutableStateOf(false) }
    var filterChoice by rememberSaveable { mutableStateOf<FindFilterChoice?>(null) }
    var savingId by remember { mutableStateOf<Long?>(null) }
    var sharingId by remember { mutableStateOf<Long?>(null) }
    var usingInMakeId by remember { mutableStateOf<Long?>(null) }
    var sheetHeight by remember { mutableIntStateOf(0) }

    val stripLine = if (mode == FindMode.WEB) {
        FindModel.webInkLine(webState, off)
    } else {
        FindModel.inkLine(state, off)
    }
    InkStripEffect(stripLine, arm = Arm.FIND)
    ScreenBackHandler(enabled = mode == FindMode.IMAGES && selected != null) { selectedId = null }

    val focus = remember { FocusRequester() }
    fun closeKeyboard() {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    fun runImageSearch() {
        closeKeyboard()
        selectedId = null
        vm.search()
    }
    fun runWebSearch() {
        closeKeyboard()
        vm.searchWeb()
    }

    // Android 9 has no permission-free gallery insert, so it saves through the file picker.
    var pendingSave by remember { mutableStateOf<Pair<FindFile, FoundImage>?>(null) }
    val pickSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        val pending = pendingSave
        pendingSave = null
        if (uri != null && pending != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { FindMedia.writeTo(context, uri, pending.first) }
                if (ok) {
                    vm.markSaved(pending.second.id)
                    FindMedia.copyCredit(context, pending.second.credit)
                }
                strip?.flash(InkLine(if (ok) "FIND SAVED CREDIT COPIED" else "FIND SAVE FAILED"))
            }
        }
    }
    fun save(image: FoundImage) {
        if (savingId != null) return
        savingId = image.id
        scope.launch {
            try {
                val file = vm.fileOrNull(context, image)
                when {
                    file == null -> strip?.flash(InkLine("FIND DOWNLOAD FAILED"))
                    FindMedia.canSaveToGallery -> {
                        val ok = withContext(Dispatchers.IO) { FindMedia.saveToGallery(context.applicationContext, file, image.credit) }
                        if (ok) {
                            vm.markSaved(image.id)
                            FindMedia.copyCredit(context, image.credit)
                        }
                        strip?.flash(InkLine(if (ok) "FIND SAVED CREDIT COPIED" else "FIND SAVE FAILED"))
                    }
                    else -> {
                        pendingSave = file to image
                        runCatching { pickSave.launch(file.displayName) }
                    }
                }
            } finally {
                savingId = null
            }
        }
    }
    fun share(image: FoundImage) {
        if (sharingId != null) return
        sharingId = image.id
        scope.launch {
            try {
                val file = vm.fileOrNull(context, image)
                if (file == null || !FindMedia.share(context, file, image)) strip?.flash(InkLine("FIND SHARE FAILED"))
            } finally {
                sharingId = null
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp)) {
                // Title: opens the chat library, as on the other arms.
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(DotRadius.Tile)
                        .clickable(onClickLabel = "Open chat library", onClick = onLibrary)
                        .clearAndSetSemantics {
                            contentDescription = "Find. Open chat library"
                            onClick(label = "Open chat library") { onLibrary(); true }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    DotMatrixText("FIND", pitch = 4.2.dp, on = c.textPrimary, off = Color.Transparent)
                }
                Spacer(Modifier.height(6.dp))
                DotSegments(
                    options = listOf(
                        SegmentOption(FindMode.IMAGES.label, R.drawable.ic_kz_image, tag = "find_mode_images"),
                        SegmentOption(FindMode.WEB.label, R.drawable.ic_kz_web, tag = "find_mode_web")
                    ),
                    selected = mode.ordinal,
                    onSelect = { index ->
                        val next = FindMode.entries[index]
                        if (next != mode) {
                            closeKeyboard()
                            selectedId = null
                            vm.setMode(next)
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                if (mode == FindMode.WEB) {
                    FindQueryField(
                        value = webDraft,
                        onValueChange = vm::setWebDraft,
                        onSearch = ::runWebSearch,
                        onClear = {
                            vm.clearWeb()
                            runCatching { focus.requestFocus() }
                            keyboard?.show()
                        },
                        focusRequester = focus,
                        enabled = !off,
                        placeholder = "Search the web",
                        semanticsLabel = "Search the web"
                    )
                } else {
                    FindQueryField(
                        value = draft,
                        onValueChange = vm::setDraft,
                        onSearch = ::runImageSearch,
                        onClear = {
                            selectedId = null
                            vm.clear()
                            runCatching { focus.requestFocus() }
                            keyboard?.show()
                        },
                        focusRequester = focus,
                        enabled = !off
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filters = state.filters
                        FilterPill(
                            label = filters.kind.label,
                            description = "File type: ${filters.kind.label}",
                            onClick = { closeKeyboard(); filterChoice = FindFilterChoice.KIND },
                            on = true,
                            icon = if (filters.kind == FindKind.IMAGES) R.drawable.ic_kz_image else R.drawable.ic_kz_grid
                        )
                        FilterPill(
                            label = filters.licence.label,
                            description = "Licence: ${filters.licence.label}",
                            onClick = { closeKeyboard(); filterChoice = FindFilterChoice.LICENCE }
                        )
                        FilterPill(
                            label = filters.size.label,
                            description = "Size: ${filters.size.label}",
                            onClick = { closeKeyboard(); filterChoice = FindFilterChoice.SIZE }
                        )
                        if (state.status == FindStatus.DONE && state.images.isNotEmpty() && !off) {
                            Text(
                                FindModel.countCaption(state.images.size).uppercase(Locale.US),
                                Modifier.weight(1f),
                                style = DotType.Caption,
                                color = c.textTertiary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (mode == FindMode.WEB) {
                FindWebResultsBody(
                    state = webState,
                    off = off,
                    onAllowSearch = vm::allowSearch,
                    onRetry = vm::retryWeb,
                    onCopyAnswer = { answer ->
                        FindMedia.copyText(context, "Answer", answer)
                        strip?.flash(InkLine("ANSWER COPIED"))
                    },
                    onOpenUrl = { url ->
                        if (!FindMedia.openPage(context, url)) strip?.flash(InkLine("FIND NO BROWSER"))
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                when {
                    off -> FindMessage {
                        TuneEmpty(
                            dotIcon = R.drawable.ic_kz_web_dot,
                            words = "Search off",
                            body = "Find looks up free images on Wikimedia Commons, so it needs web search. " +
                                "It is switched off in Tune.",
                            action = "Allow web search",
                            onAction = vm::allowSearch,
                            actionIcon = R.drawable.ic_kz_web
                        )
                    }
                    state.status == FindStatus.IDLE -> FindMessage {
                        TuneEmpty(
                            dotIcon = R.drawable.ic_kz_search_dot,
                            words = "Free images",
                            body = "Search Wikimedia Commons. Every image shows its author and licence, and " +
                                "Save or Share always carries the credit."
                        )
                    }
                    state.status == FindStatus.SEARCHING -> FindMessage {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ThinkingDots(dot = 6.dp)
                            Text(
                                "Searching Commons".uppercase(Locale.US),
                                Modifier.padding(top = 14.dp),
                                style = DotType.Caption,
                                color = c.textTertiary
                            )
                        }
                    }
                    state.status == FindStatus.FAILED -> FindMessage {
                        TuneEmpty(
                            dotIcon = if (state.offline) R.drawable.ic_kz_offline_dot else R.drawable.ic_kz_warning_dot,
                            words = if (state.offline) "Offline" else "No answer",
                            body = state.error ?: "The search failed.",
                            action = "Try again",
                            onAction = vm::retry,
                            actionIcon = R.drawable.ic_kz_retry
                        )
                    }
                    state.images.isEmpty() -> FindMessage {
                        TuneEmpty(
                            dotIcon = R.drawable.ic_kz_search_dot,
                            words = "No matches",
                            body = FindModel.emptyBody(state)
                        )
                    }
                    else -> FindGrid(
                        images = state.images,
                        selectedId = selectedId,
                        loader = loader,
                        loadingMore = state.loadingMore,
                        moreError = state.moreError,
                        end = state.nextOffset == null,
                        bottomPadding = if (selected != null) with(density) { sheetHeight.toDp() } else 0.dp,
                        serial = state.serial,
                        onTile = { image ->
                            closeKeyboard()
                            selectedId = if (selectedId == image.id) null else image.id
                        },
                        onLoadMore = vm::loadMore,
                        onRetryMore = vm::retry,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = mode == FindMode.IMAGES && selected != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (motion) slideInVertically { it } else EnterTransition.None,
            exit = if (motion) slideOutVertically { it } else ExitTransition.None
        ) {
            sheetImage?.let { image ->
                FindViewerSheet(
                    image = image,
                    loader = loader,
                    saved = image.id in saved,
                    saving = savingId == image.id,
                    sharing = sharingId == image.id,
                    usingInMake = usingInMakeId == image.id,
                    onClose = { selectedId = null },
                    onView = { viewing = true },
                    onSave = { save(image) },
                    onSource = {
                        if (!FindMedia.openPage(context, image.pageUrl)) strip?.flash(InkLine("FIND NO BROWSER"))
                    },
                    onShare = { share(image) },
                    onLicence = image.licence.url?.let { url -> { FindMedia.openPage(context, url); Unit } },
                    onUseInMake = {
                        if (usingInMakeId == null) {
                            usingInMakeId = image.id
                            scope.launch {
                                try {
                                    when (vm.useInMake(context, image)) {
                                        UseInMakeOutcome.ADDED, UseInMakeOutcome.DUPLICATE -> {
                                            selectedId = null
                                            onUseInMake()
                                        }
                                        UseInMakeOutcome.BUSY -> strip?.flash(InkLine("MAKE IS BUSY"))
                                        UseInMakeOutcome.FULL -> strip?.flash(InkLine("MAKE ALREADY HAS 4 IMAGES"))
                                        UseInMakeOutcome.FAILED -> strip?.flash(InkLine("COULD NOT LOAD IMAGE"))
                                    }
                                } finally {
                                    usingInMakeId = null
                                }
                            }
                        }
                    },
                    modifier = Modifier.onSizeChanged { sheetHeight = it.height }
                )
            }
        }
    }

    LaunchedEffect(selected == null) { if (selected == null) viewing = false }
    if (viewing && selected != null && mode == FindMode.IMAGES) {
        FindImageViewer(selected, loader, onDismiss = { viewing = false })
    }

    if (mode == FindMode.IMAGES) {
        filterChoice?.let { choice ->
            FindFilterSheet(
                choice = choice,
                filters = state.filters,
                onKind = { kind ->
                    filterChoice = null
                    selectedId = null
                    vm.setKind(kind)
                },
                onLicence = { licence ->
                    filterChoice = null
                    selectedId = null
                    vm.setLicence(licence)
                },
                onSize = { size ->
                    filterChoice = null
                    selectedId = null
                    vm.setSize(size)
                },
                onDismiss = { filterChoice = null }
            )
        }
    }
}

/** A centred message in the space the grid would take (scrolls on short screens). */
@Composable
private fun FindMessage(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 8.dp, top = 32.dp, bottom = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        content()
    }
}

/**
 * The two-column masonry (kit: 8dp between columns, 10dp between rows). It asks for the
 * next page when the last few tiles come into view, and a new search starts at the top.
 */
@Composable
private fun FindGrid(
    images: List<FoundImage>,
    selectedId: Long?,
    loader: ImageLoader,
    loadingMore: Boolean,
    moreError: String?,
    end: Boolean,
    bottomPadding: Dp,
    serial: Int,
    onTile: (FoundImage) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    val grid = rememberLazyStaggeredGridState()
    val count by rememberUpdatedState(images.size)
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(serial) { grid.scrollToItem(0) }
    // A tile picked low on the screen would sit behind the viewer sheet: lift it clear.
    val motion = LocalKryzzMotionEnabled.current
    val sheetPx = with(LocalDensity.current) { bottomPadding.toPx() }
    val gapPx = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(selectedId, sheetPx) {
        if (selectedId == null || sheetPx <= 0f) return@LaunchedEffect
        val info = grid.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == selectedId } ?: return@LaunchedEffect
        val hiddenBy = item.offset.y + item.size.height - (info.viewportEndOffset - sheetPx) + gapPx
        if (hiddenBy > 0f) {
            val step = hiddenBy.coerceAtMost((item.offset.y - info.viewportStartOffset).toFloat().coerceAtLeast(0f))
            if (motion) grid.animateScrollBy(step) else grid.scrollBy(step)
        }
    }
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last -> if (last >= 0 && last >= count - 6) loadMore() }
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = grid,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 24.dp + bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 12.dp
    ) {
        items(images, key = { it.id }) { image ->
            FindTile(image, selected = image.id == selectedId, loader = loader, onClick = { onTile(image) })
        }
        item(key = "footer", span = StaggeredGridItemSpan.FullLine) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
                when {
                    loadingMore -> ThinkingDots(dot = 5.dp)
                    moreError != null -> Text(
                        "Couldn't load more. Tap to try again".uppercase(Locale.US),
                        Modifier.clickable(onClick = onRetryMore).padding(12.dp),
                        style = DotType.Caption,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    end -> Text(
                        "End of results".uppercase(Locale.US),
                        style = DotType.Caption,
                        color = c.textDisabled
                    )
                    // Normally the next page loads on its own; this covers a run of hidden results.
                    else -> Text(
                        "Load more".uppercase(Locale.US),
                        Modifier.clickable(onClick = onLoadMore).padding(12.dp),
                        style = DotType.Caption,
                        color = c.textSecondary
                    )
                }
            }
        }
    }
}
