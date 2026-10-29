package ai.daylight.assistant.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.ConversationFolderEntity
import ai.daylight.assistant.data.local.ConversationSummary
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.ui.crew.rememberMinuteClock
import ai.daylight.assistant.ui.dot.DotConfirmDialog
import ai.daylight.assistant.ui.dot.DotField
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotSnackbarHost
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.library.BackupSheet
import ai.daylight.assistant.ui.library.ChatActionsSheet
import ai.daylight.assistant.ui.library.FolderPickSheet
import ai.daylight.assistant.ui.library.FolderStrip
import ai.daylight.assistant.ui.library.LibraryEmpty
import ai.daylight.assistant.ui.library.LibraryGroupHeader
import ai.daylight.assistant.ui.library.LibraryHeader
import ai.daylight.assistant.ui.library.LibraryModel
import ai.daylight.assistant.ui.library.LibraryRow
import ai.daylight.assistant.ui.library.NameDialog
import ai.daylight.assistant.ui.library.NewChatPill
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The drawer is intentionally solid so text and folders remain readable over every background. */
internal fun panelSurfaceOpacity(@Suppress("UNUSED_PARAMETER") preferred: Float): Float = 1f

internal data class LogoTapProgress(val count: Int = 0, val lastTapAtMs: Long = 0L)
internal data class LogoTapOutcome(val progress: LogoTapProgress, val openAbout: Boolean)

internal fun registerLogoTap(
    progress: LogoTapProgress,
    nowMs: Long,
    timeoutMs: Long = 650L
): LogoTapOutcome {
    val continued = progress.lastTapAtMs > 0L && nowMs >= progress.lastTapAtMs &&
        nowMs - progress.lastTapAtMs <= timeoutMs
    val count = if (continued) progress.count + 1 else 1
    return if (count >= 3) {
        LogoTapOutcome(LogoTapProgress(), openAbout = true)
    } else {
        LogoTapOutcome(LogoTapProgress(count, nowMs), openAbout = false)
    }
}

internal const val PANEL_SETTLE_FRACTION = 0.05f

/**
 * Whether the side-panel open/close swipe should engage on the given nav route.
 * Top-level surfaces (chat, settings) allow the swipe; any depth destination
 * (a settings subpage such as `settings/voice`, `settings/appearance`,
 * `models/...`, `skills`, `llms`, `memory`, `cron`, etc.) suppresses it so the
 * panel cannot be opened from inside a submenu — the user must back out to
 * the parent first.
 */
internal fun panelSwipeEnabledForRoute(route: String?): Boolean {
    if (route == null) return false
    if (route.startsWith("chat/")) return true
    if (route == Routes.MAKE || route == Routes.FIND || route == Routes.CREW) return true
    return when (route) {
        "home", "boot", "onboarding", "settings" -> true
        else -> false
    }
}

/** True when the side panel is allowed to be opened at all on the given route. */
internal fun sidePanelOpenAllowedForRoute(route: String?): Boolean {
    // Once the user is inside a settings subpage (or any other depth
    // destination) the panel is locked: it must be closed before the route
    // changes back, and the only way out is the back arrow. This also makes
    // it impossible for `openLibrary` to slip the panel open from the
    // subpage's menu button.
    return panelSwipeEnabledForRoute(route)
}

internal fun panelDragIsMeaningful(
    deltaX: Float,
    progress: Float,
    downX: Float,
    leftEdgePx: Float
): Boolean {
    val goingRight = deltaX > 0f
    val goingLeft = deltaX < 0f
    val fromLeftEdge = downX <= leftEdgePx
    val panelVisible = progress > 0.001f
    return (goingRight && progress < 1f && (fromLeftEdge || panelVisible)) ||
        (goingLeft && progress > 0f)
}

/**
 * A child that already took this drag (Tune's cost slider, a sideways-scrolling row) keeps
 * it: once a move was consumed below the drawer before the drawer engaged, the drawer stays
 * out of that gesture. A drag nothing else claims still opens the drawer as before.
 */
internal fun panelYieldsToChild(engaged: Boolean, moveConsumedByChild: Boolean): Boolean =
    !engaged && moveConsumedByChild

internal fun panelShouldSettleOpen(
    startProgress: Float,
    endProgress: Float,
    settleFraction: Float = PANEL_SETTLE_FRACTION
): Boolean {
    val moved = kotlin.math.abs(endProgress - startProgress)
    if (moved < settleFraction) return startProgress >= 0.5f
    return endProgress > startProgress
}
internal fun panelProgressAfterDrag(
    progress: Float,
    deltaX: Float,
    panelWidthPx: Float
): Float = (progress + deltaX / panelWidthPx).coerceIn(0f, 1f)

/**
 * Host-provided close that the panel content (chat rows, Settings/New-chat footer, X
 * button) invokes in addition to the caller's [KryzzSidePanelHost.onClose]. It clears a
 * lingering drag settlement target and drives the slide animation to closed directly,
 * so the panel always visually closes when an item is selected — even when the panel
 * was opened by a left-to-right swipe whose `open` state never caught up to `true`
 * (the case where flipping `panelOpen` to `false` is a no-op and could otherwise leave
 * the drawer wedged open over the new screen).
 */
internal val LocalSidePanelClose = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KryzzSidePanelHost(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    panelWidth: Dp = 320.dp,
    swipeEnabled: Boolean = true,
    panel: @Composable () -> Unit,
    onOpen: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val panelWidthPx = with(density) { panelWidth.toPx() }
    val panelEngagePx = with(density) { PANEL_ENGAGE_DP.toPx() }
    val progress = remember { Animatable(if (open) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var settleTarget by remember { mutableStateOf<Float?>(null) }
    // Tracks the previous value of `open` so the settle effect can tell a fresh
    // swipe-to-open (open still false for one frame) apart from a stale opening
    // target left behind after the caller closed the drawer mid-animation.
    var prevOpen by remember { mutableStateOf(open) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnClose by rememberUpdatedState(onClose)
    val dragScope = rememberCoroutineScope()
    // Force-close: drop any stale drag settlement and animate the panel closed. Used by
    // the panel content via [LocalSidePanelClose] so a selection always closes the drawer.
    val forceClose: () -> Unit = {
        settleTarget = null
        dragScope.launch {
            progress.animateTo(
                0f,
                animationSpec = tween(PANEL_CLOSE_MS, easing = PanelEmphasizedEasing)
            )
        }
    }
    BackHandler(enabled = open || progress.value > 0.02f) { forceClose(); currentOnClose() }
    // Opening the panel from any path dismisses the composer keyboard.
    LaunchedEffect(open) {
        if (open) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(open, dragging, motionEnabled, settleTarget) {
        if (dragging) return@LaunchedEffect
        // Drop a stale opening target the instant the caller closes the panel
        // (open true → false). This is what stops a swipe-opened drawer from
        // resurrecting after a scrim/back/conversation tap that follows it.
        // The one-frame window where a fresh swipe-to-open still has
        // `open == false` is safe: `prevOpen` is false there too, so the target
        // survives until `open` catches up to true.
        if (shouldClearStaleOpenTarget(open, prevOpen, settleTarget)) {
            settleTarget = null
        }
        prevOpen = open
        val target = panelAnimationTarget(open, settleTarget)
        if (motionEnabled) {
            progress.animateTo(
                target,
                animationSpec = tween(
                    durationMillis = if (target > 0f) PANEL_OPEN_MS else PANEL_CLOSE_MS,
                    easing = PanelEmphasizedEasing
                )
            )
        } else {
            progress.snapTo(target)
        }
        // Keep a drag settlement target authoritative until the caller's open state
        // catches up. This prevents the old `open = false` effect run from snapping
        // a visibly opening panel closed on the release frame.
        if (settleTarget != null && settleTarget == target && ((target == 1f && open) || (target == 0f && !open))) {
            settleTarget = null
        }
    }

    val shown = progress.value > 0.001f || dragging

    // Gesture lives on the PARENT of content + scrim + panel — never a sibling overlay.
    // Children win hit-testing so buttons keep working; we only consume after a real
    // horizontal swipe. The handler stays mounted when dragging starts, so it cannot
    // cancel itself and leave `dragging` stuck (that froze a full-screen scrim in 5.1.5).
    Box(
        modifier
            .pointerInput(panelWidthPx, panelEngagePx, swipeEnabled) {
                // When swipeEnabled is false (e.g. on a settings subpage) we
                // still want children to receive a clean pointer stream, so
                // we drain every gesture and never set `engaged = true`. The
                // visual panel can still be controlled by its X button /
                // BackHandler / caller — the open/close swipe is the only
                // thing we suppress.
                if (!swipeEnabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent(PointerEventPass.Final)
                                .changes.firstOrNull { it.pressed } ?: continue
                            val downId = down.id
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Final)
                                val ch = ev.changes.firstOrNull { it.id == downId } ?: break
                                if (!ch.pressed) break
                            }
                        }
                    }
                    return@pointerInput
                }
                 // CRITICAL: run the entire gesture handler on the Final pointer-event pass
                // controls (notably the voice overlay's close button in the top-right
                // corner). Children see the down/release on the Initial pass first, fire
                // their onClick, and the parent observes the same events afterwards
                // without interfering.
                awaitPointerEventScope {
                    while (true) {
                        // Wait for the first down event on the Final pass.
                        val down = awaitPointerEvent(PointerEventPass.Final)
                            .changes.firstOrNull { it.pressed }
                            ?: continue
                        val downId = down.id
                        val downX = down.position.x
                        // While the panel is closed, only engage gestures that start inside the
                        // left edge strip. For taps anywhere else (the voice overlay's close
                        // button sits in the top-right corner), don't engage at all so
                        // descendants receive a clean pointer stream. While the panel is
                        // partly open, any horizontal drag is fair game.
                        if (progress.value <= 0.001f && downX > panelWidthPx) {
                            // Drain pointer events until this pointer is released so we don't
                            // miss the consumption decision on the next gesture.
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Final)
                                val ch = ev.changes.firstOrNull { it.id == downId } ?: break
                                if (!ch.pressed) break
                            }
                            continue
                        }
                        var engaged = false
                        var yielded = false
                        var totalDx = 0f
                        var totalDy = 0f
                        var dragProgress = progress.value
                        val dragStartProgress = dragProgress
                        try {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Final)
                                val ch = ev.changes.firstOrNull { it.id == downId } ?: break
                                if (!ch.pressed) break
                                if (yielded) continue
                                val childMoved = ch.isConsumed && ch.position != ch.previousPosition
                                if (panelYieldsToChild(engaged, childMoved)) {
                                    yielded = true
                                    continue
                                }
                                val dx = ch.position.x - ch.previousPosition.x
                                val dy = ch.position.y - ch.previousPosition.y
                                totalDx += dx
                                totalDy += dy
                                // Only treat the gesture as a horizontal panel swipe when
                                // horizontal movement is dominant over vertical. This locks
                                // the scroll inside the panel to the vertical axis so a
                                // vertical list scroll can't accidentally close the panel
                                // through a small horizontal jitter.
                                val horizontalDominant = kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)
                                if (horizontalDominant && panelDragIsMeaningful(dx, dragProgress, downX, panelWidthPx)) {
                                    dragProgress = panelProgressAfterDrag(dragProgress, dx, panelWidthPx)
                                    if (!engaged && kotlin.math.abs(totalDx) > panelEngagePx) {
                                        engaged = true
                                        dragging = true
                                    }
                                    if (engaged) {
                                        // Claim the swipe, so a full-width row under the finger
                                        // (a Tune row, a board row) does not also take the lift
                                        // as a tap. Taps check for this on the Final pass, which
                                        // reaches this parent first.
                                        ch.consume()
                                        val frameProgress = dragProgress
                                        dragScope.launch {
                                            progress.snapTo(frameProgress)
                                        }
                                    }
                                }
                            }
                        } finally {
                            if (engaged) {
                                val shouldOpen = panelShouldSettleOpen(dragStartProgress, dragProgress)
                                settleTarget = if (shouldOpen) 1f else 0f
                                if (shouldOpen) currentOnOpen() else currentOnClose()
                                dragging = false
                            } else {
                                dragging = false
                            }
                        }
                    }
                }
            }
    ) {
        Box(Modifier.fillMaxSize()) { content() }

        if (shown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f * progress.value.coerceIn(0f, 1f)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { forceClose(); currentOnClose() }
                    )
                    .testTag("side_panel_scrim")
            )

            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(panelWidth)
                    .offset { IntOffset(((progress.value - 1f) * panelWidthPx).roundToInt(), 0) }
            ) {
                CompositionLocalProvider(LocalSidePanelClose provides forceClose) {
                    panel()
                }
            }

        }
    }
}

internal fun panelAnimationTarget(open: Boolean, settleTarget: Float?): Float =
    settleTarget ?: if (open) 1f else 0f

/**
 * When the caller flips `open` from true to false (scrim tap, back button, or a
 * conversation/settings selection) while a drag's opening settlement is still
 * pending, that stale `1f` target must be dropped so the drawer cannot
 * resurrect after the caller already closed it. The fresh swipe-to-open case
 * is preserved because there `prevOpen` is still `false` (the panel was closed
 * before the drag), so the target is honoured for the frame until `open`
 * catches up.
 */
internal fun shouldClearStaleOpenTarget(
    open: Boolean,
    prevOpen: Boolean,
    settleTarget: Float?
): Boolean = !open && prevOpen && settleTarget == 1f

private val PanelEmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private const val PANEL_OPEN_MS = 360
private const val PANEL_CLOSE_MS = 300
private val PANEL_ENGAGE_DP = 4.dp

/**
 * The chat library drawer (components.md section 11, C10). CHATS in dot text with the count
 * and the backup menu, a search field, the NEW CHAT pill, folders, then the chats: pinned
 * first, then one block per day, each row 64dp with a mono caption. The open chat
 * ([activeConversationId]) has a 2dp yellow bar. Starting Run, Voice or Routines lives on the
 * rail and Home now, so the drawer only holds chats.
 */
@Composable
fun KryzzLibraryPanel(
    vm: ConversationListViewModel,
    onOpen: (String) -> Unit,
    onNewChat: (AssistantMode) -> Unit,
    modifier: Modifier = Modifier,
    activeConversationId: String? = null,
    onClose: (() -> Unit)? = null,
    onDeleteConversation: (String) -> Unit = vm::delete,
    onAbout: (() -> Unit)? = null
) {
    val c = DotTheme.c
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var backupSheet by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<ConversationSummary?>(null) }
    var renameConversation by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleteConversation by remember { mutableStateOf<ConversationSummary?>(null) }
    var assignConversation by remember { mutableStateOf<ConversationSummary?>(null) }
    var selectedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    // A new folder, optionally for a chat that moves into it ("" = no chat).
    var createFolderFor by remember { mutableStateOf<String?>(null) }
    var renameFolder by remember { mutableStateOf<ConversationFolderEntity?>(null) }
    var deleteFolder by remember { mutableStateOf<ConversationFolderEntity?>(null) }
    var logoTaps by remember { mutableStateOf(LogoTapProgress()) }
    val now = rememberMinuteClock()
    val zone = remember { ZoneId.systemDefault() }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val stream = requireNotNull(context.contentResolver.openOutputStream(uri)) {
                            "The selected file could not be opened."
                        }
                        stream.bufferedWriter().use { it.write(pendingExport) }
                    }
                }.onSuccess {
                    vm.notice.value = "Chat backup saved."
                }.onFailure {
                    vm.notice.value = it.message ?: "The backup could not be saved."
                }
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val content = runCatching {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "The selected file could not be opened."
                        }.bufferedReader().use { it.readText() }
                    }
                }.onFailure {
                    vm.notice.value = it.message ?: "The backup could not be opened."
                }.getOrNull()
                if (content != null) vm.import(content)
            }
        }
    }

    LaunchedEffect(folders, selectedFolderId) {
        if (selectedFolderId != null && folders.none { it.id == selectedFolderId }) selectedFolderId = null
    }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.notice.value = null
        }
    }

    val visibleConversations = remember(conversations, selectedFolderId) {
        conversations.filter { conversation ->
            selectedFolderId == null || conversation.folderId == selectedFolderId
        }
    }
    val groups = remember(visibleConversations, now) { LibraryModel.groups(visibleConversations, now, zone) }
    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }
    val hostClose = LocalSidePanelClose.current
    fun closePanelThen(action: () -> Unit) {
        hostClose()
        onClose?.invoke()
        action()
    }

    Surface(
        modifier = modifier.fillMaxHeight().testTag("library_panel"),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        color = c.surface,
        contentColor = c.textPrimary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, c.border)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                LibraryHeader(
                    count = conversations.size.takeIf { query.isBlank() } ?: 0,
                    onTitleTap = {
                        val outcome = registerLogoTap(logoTaps, SystemClock.elapsedRealtime())
                        logoTaps = outcome.progress
                        if (outcome.openAbout) onAbout?.let { closePanelThen(it) }
                    },
                    onBackup = { backupSheet = true },
                    onClose = if (onClose != null) ({ hostClose(); onClose() }) else null
                )
                DotField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                    placeholder = "Search chats",
                    leadingIcon = R.drawable.ic_kz_search,
                    trailing = if (query.isNotEmpty()) {
                        { DotIconButton(R.drawable.ic_kz_close, "Clear search", { vm.query.value = "" }, tint = c.textTertiary, iconSize = 18.dp) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    fieldModifier = Modifier.testTag("library_search")
                )
                NewChatPill(
                    onClick = { closePanelThen { onNewChat(AssistantMode.CHAT) } },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp).testTag("library_new_chat")
                )

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item(key = "folders") {
                        FolderStrip(
                            folders = folders,
                            selectedId = selectedFolderId,
                            onSelect = { selectedFolderId = it },
                            onCreate = { createFolderFor = "" },
                            onRename = { renameFolder = it },
                            onDelete = { deleteFolder = it }
                        )
                    }
                    if (visibleConversations.isEmpty()) {
                        item(key = "chat_panel_empty_$selectedFolderId") {
                            val (words, body) = LibraryModel.emptyWords(query, selectedFolder?.name)
                            val icon = when {
                                query.isNotBlank() -> R.drawable.ic_kz_search_dot
                                selectedFolder != null -> R.drawable.ic_kz_folder_dot
                                else -> R.drawable.ic_kz_history_dot
                            }
                            LibraryEmpty(icon, words, body)
                        }
                    } else {
                        groups.forEach { group ->
                            item(key = "chat_panel_group_${group.key}") { LibraryGroupHeader(group.label) }
                            items(group.items, key = { it.id }) { item ->
                                LibraryRow(
                                    item = item,
                                    caption = LibraryModel.caption(item, now, zone, withDate = item.pinned),
                                    active = item.id == activeConversationId,
                                    onOpen = { closePanelThen { onOpen(item.id) } },
                                    onActions = { actionsFor = item }
                                )
                            }
                        }
                    }
                }
            }
            DotSnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }

    actionsFor?.let { item ->
        ChatActionsSheet(
            item = item,
            onPin = { vm.pin(item.id, !item.pinned) },
            onRename = { renameConversation = item },
            onMove = { assignConversation = item },
            onDelete = { deleteConversation = item },
            onDismiss = { actionsFor = null }
        )
    }
    if (backupSheet) {
        BackupSheet(
            onExport = {
                vm.export { json ->
                    pendingExport = json
                    createBackup.launch("kryzz-ai-chat-backup.json")
                }
            },
            onImport = { openBackup.launch(arrayOf("application/json")) },
            onDismiss = { backupSheet = false }
        )
    }
    renameConversation?.let { item ->
        NameDialog(
            title = "Rename chat",
            label = "Title",
            initial = item.title,
            onDismiss = { renameConversation = null }
        ) { newTitle ->
            vm.rename(item.id, newTitle)
            renameConversation = null
        }
    }
    deleteConversation?.let { item ->
        DotConfirmDialog(
            title = "Delete this chat?",
            body = "This removes “${item.title}” and its messages from this phone. It cannot be undone.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                onDeleteConversation(item.id)
                deleteConversation = null
            },
            onDismiss = { deleteConversation = null }
        )
    }
    createFolderFor?.let { forChat ->
        NameDialog(
            title = "New folder",
            label = "Folder name",
            maxLength = 48,
            onDismiss = { createFolderFor = null }
        ) { name ->
            vm.createFolder(name, moveConversationId = forChat.ifEmpty { null })
            createFolderFor = null
        }
    }
    renameFolder?.let { folder ->
        NameDialog(
            title = "Rename folder",
            label = "Folder name",
            initial = folder.name,
            maxLength = 48,
            onDismiss = { renameFolder = null }
        ) { name ->
            vm.renameFolder(folder.id, name)
            renameFolder = null
        }
    }
    deleteFolder?.let { folder ->
        DotConfirmDialog(
            title = "Delete ${folder.name}?",
            body = "Its chats go back to All. No chat is deleted.",
            confirm = "Delete folder",
            destructive = true,
            onConfirm = {
                vm.deleteFolder(folder.id)
                selectedFolderId = null
                deleteFolder = null
            },
            onDismiss = { deleteFolder = null }
        )
    }
    assignConversation?.let { conversation ->
        FolderPickSheet(
            currentFolderId = conversation.folderId,
            folders = folders,
            onPick = { folderId ->
                vm.assignFolder(conversation.id, folderId)
                assignConversation = null
            },
            onNewFolder = { createFolderFor = conversation.id },
            onDismiss = { assignConversation = null }
        )
    }
}
