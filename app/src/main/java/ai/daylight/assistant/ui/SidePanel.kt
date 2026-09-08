package ai.daylight.assistant.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.daylight.assistant.BuildConfig
import ai.daylight.assistant.data.local.ConversationFolderEntity
import ai.daylight.assistant.data.local.ConversationSummary
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
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
    return when (route) {
        "boot", "onboarding", "settings" -> true
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
                        var totalDx = 0f
                        var totalDy = 0f
                        var dragProgress = progress.value
                        val dragStartProgress = dragProgress
                        try {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Final)
                                val ch = ev.changes.firstOrNull { it.id == downId } ?: break
                                if (!ch.pressed) break
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

@Composable
fun KryzzLibraryPanel(
    vm: ConversationListViewModel,
    onOpen: (String) -> Unit,
    onNewChat: (AssistantMode) -> Unit,
    onNewVoice: () -> Unit,
    onCron: () -> Unit = {},
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    onDeleteConversation: (String) -> Unit = vm::delete
) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var backupMenu by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf("") }
    var renameConversation by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleteConversation by remember { mutableStateOf<ConversationSummary?>(null) }
    var assignConversation by remember { mutableStateOf<ConversationSummary?>(null) }
    var selectedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<ConversationFolderEntity?>(null) }
    var deleteFolder by remember { mutableStateOf<ConversationFolderEntity?>(null) }
    var logoTaps by remember { mutableStateOf(LogoTapProgress()) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

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
    val grouped = remember(visibleConversations) {
        visibleConversations.groupBy { conversationDateSection(it.updatedAt) }
    }
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
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 20.dp
    ) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val outcome = registerLogoTap(logoTaps, SystemClock.elapsedRealtime())
                            logoTaps = outcome.progress
                            if (outcome.openAbout) showAbout = true
                        }
                        .semantics { contentDescription = "Kryzz AI logo" }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KryzzMark(Modifier.size(32.dp), contentDescription = null)
                    Text("KRYZZ AI", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(
                        onClick = { backupMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.MoreVert, "Backup and restore")
                    }
                    DropdownMenu(expanded = backupMenu, onDismissRequest = { backupMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export chat backup") },
                            leadingIcon = { Icon(Icons.Outlined.Download, null) },
                            onClick = {
                                backupMenu = false
                                vm.export { json ->
                                    pendingExport = json
                                    createBackup.launch("kryzz-ai-chat-backup.json")
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import chat backup") },
                            leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                            onClick = {
                                backupMenu = false
                                openBackup.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }
                if (onClose != null) {
                    IconButton(onClick = { hostClose(); onClose?.invoke() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Close, "Close menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    PanelModeButton("Chat", Icons.Outlined.ChatBubbleOutline, Modifier.testTag("library_new_chat")) {
                        closePanelThen { onNewChat(AssistantMode.CHAT) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    PanelModeButton("Agent", Icons.Outlined.AutoAwesome, Modifier.testTag("library_new_agent")) {
                        closePanelThen { onNewChat(AssistantMode.AGENT) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    PanelModeButton("Voice", Icons.Outlined.Mic, Modifier.testTag("library_new_voice")) {
                        closePanelThen(onNewVoice)
                    }
                }
                Box(Modifier.weight(1f)) {
                    PanelModeButton("Cron", Icons.Outlined.Schedule, Modifier.testTag("library_cron")) {
                        closePanelThen(onCron)
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Search chats") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            )

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 8.dp, bottom = 12.dp)
            ) {
                item(key = "folders_header") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "FOLDERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (selectedFolder != null) {
                            IconButton(onClick = { renameFolder = selectedFolder }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Outlined.Edit, "Rename ${selectedFolder.name}", Modifier.size(18.dp))
                            }
                            IconButton(onClick = { deleteFolder = selectedFolder }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Outlined.Delete, "Delete ${selectedFolder.name}", Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { createFolder = true }, modifier = Modifier.size(48.dp).testTag("create_folder")) {
                            Icon(Icons.Outlined.Add, "Create folder", Modifier.size(20.dp))
                        }
                    }
                }

                item(key = "folder_list") {
                    if (folders.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { createFolder = true }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Create a folder to organise related chats.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(folders, key = { it.id }) { folder ->
                                FilterChip(
                                    selected = selectedFolderId == folder.id,
                                    onClick = {
                                        selectedFolderId = folder.id
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(16.dp)) },
                                    label = { Text(folder.name, maxLines = 1) }
                                )
                            }
                        }
                    }
                }

                if (visibleConversations.isEmpty()) {
                    item(key = "chat_panel_empty_$selectedFolderId") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                when {
                                    query.isNotBlank() -> "No results"
                                    selectedFolder != null -> "${selectedFolder.name} is empty"
                                    else -> "Start your first chat"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                when {
                                    query.isNotBlank() -> "Try a different title or phrase."
                                    selectedFolder != null -> "Move a chat here from its actions menu."
                                    else -> "New conversations will appear here."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LibraryDateSection.entries.forEach { section ->
                        val sectionItems = grouped[section].orEmpty()
                        if (sectionItems.isNotEmpty()) {
                            item(key = "chat_panel_section_${section.name}") {
                                Text(
                                    section.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, bottom = 2.dp)
                                )
                            }
                            items(sectionItems, key = { it.id }) { item ->
                                ConversationRow(
                                    item = item,
                                    section = section,
                                    onOpen = { closePanelThen { onOpen(item.id) } },
                                    onPin = { vm.pin(item.id, !item.pinned) },
                                    onRename = { renameConversation = item },
                                    onMove = { assignConversation = item },
                                    onDelete = { deleteConversation = item },
                                    horizontalPadding = 0.dp
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            PanelFooterButton(
                Icons.Outlined.Settings,
                "Settings",
                { closePanelThen(onSettings) },
                Modifier.testTag("library_settings")
            )
        }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 72.dp)
            )
        }
    }

    renameConversation?.let { item ->
        RenameDialog(initial = item.title, onDismiss = { renameConversation = null }) { newTitle ->
            vm.rename(item.id, newTitle)
            renameConversation = null
        }
    }
    deleteConversation?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteConversation = null },
            title = { Text("Delete conversation?") },
            text = { Text("This removes “${item.title}” and its messages from this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversation(item.id)
                    deleteConversation = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConversation = null }) { Text("Cancel") } }
        )
    }
    if (createFolder) {
        FolderNameDialog(title = "Create folder", onDismiss = { createFolder = false }) { name ->
            vm.createFolder(name)
            createFolder = false
        }
    }
    renameFolder?.let { folder ->
        FolderNameDialog(title = "Rename folder", initial = folder.name, onDismiss = { renameFolder = null }) { name ->
            vm.renameFolder(folder.id, name)
            renameFolder = null
        }
    }
    deleteFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text("Delete ${folder.name}?") },
            text = { Text("Chats in this folder will return to All. No conversations will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder.id)
                    selectedFolderId = null
                    deleteFolder = null
                }) { Text("Delete folder") }
            },
            dismissButton = { TextButton(onClick = { deleteFolder = null }) { Text("Cancel") } }
        )
    }
    assignConversation?.let { conversation ->
        FolderAssignmentDialog(
            conversation = conversation,
            folders = folders,
            onDismiss = { assignConversation = null }
        ) { folderId ->
            vm.assignFolder(conversation.id, folderId)
            assignConversation = null
        }
    }
    if (showAbout) GalacticAboutDialog(onDismiss = { showAbout = false })
}

@Composable
private fun FolderNameDialog(
    title: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(48) },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FolderAssignmentDialog(
    conversation: ConversationSummary,
    folders: List<ConversationFolderEntity>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                item(key = "no_folder") {
                    DropdownMenuItem(
                        text = { Text("No folder") },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                        trailingIcon = {
                            if (conversation.folderId == null) Text("Current", style = MaterialTheme.typography.labelSmall)
                        },
                        onClick = { onSelect(null) }
                    )
                }
                items(folders, key = { it.id }) { folder ->
                    DropdownMenuItem(
                        text = { Text(folder.name) },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                        trailingIcon = {
                            if (conversation.folderId == folder.id) Text("Current", style = MaterialTheme.typography.labelSmall)
                        },
                        onClick = { onSelect(folder.id) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GalacticAboutDialog(onDismiss: () -> Unit) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val galacticMotion = rememberInfiniteTransition(label = "about galaxy")
    val orbitProgress by galacticMotion.animateFloat(
        initialValue = 0f,
        targetValue = if (motionEnabled) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(10_000), RepeatMode.Restart),
        label = "about orbit"
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 390.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF08090B),
                contentColor = Color(0xFFF4F4F1),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                shadowElevation = 28.dp
            ) {
                Box {
                    GalacticAboutStars(orbitProgress, Modifier.matchParentSize())
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(132.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val pulse = if (motionEnabled) {
                                    1f + kotlin.math.sin(orbitProgress * 2f * kotlin.math.PI.toFloat()) * 0.025f
                                } else 1f
                                KryzzMark(
                                    Modifier
                                        .size(96.dp)
                                        .graphicsLayer {
                                            scaleX = pulse
                                            scaleY = pulse
                                            rotationZ = if (motionEnabled) orbitProgress * 2.5f else 0f
                                        },
                                    contentDescription = "Kryzz AI"
                                )
                            }
                        }
                        Text("KRYZZ AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "OBSIDIAN CONSTELLATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.62f)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                "Version ${BuildConfig.VERSION_NAME}",
                                Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        HorizontalDivider(
                            Modifier.padding(vertical = 8.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        )
                        Text("Created and coded by Kryzz", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Built with Kotlin and Jetpack Compose\nAI routing by OpenRouter · Search by Parallel\nVoice by Fish Audio and OpenRouter",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.66f),
                            textAlign = TextAlign.Center
                        )
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp)
                        ) {
                            Text("Return to the stars")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalacticAboutStars(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stars = listOf(
            Offset(0.08f, 0.13f), Offset(0.23f, 0.08f), Offset(0.42f, 0.17f),
            Offset(0.72f, 0.09f), Offset(0.90f, 0.20f), Offset(0.16f, 0.36f),
            Offset(0.84f, 0.40f), Offset(0.10f, 0.69f), Offset(0.30f, 0.83f),
            Offset(0.62f, 0.76f), Offset(0.88f, 0.88f), Offset(0.74f, 0.58f)
        )
        val phase = progress * 2f * kotlin.math.PI.toFloat()
        val points = stars.mapIndexed { index, star ->
            val drift = kotlin.math.sin(phase + index * 0.77f) * 3.dp.toPx()
            Offset(
                star.x * size.width + drift,
                star.y * size.height + kotlin.math.cos(phase + index * 0.61f) * 2.dp.toPx()
            )
        }
        listOf(0 to 1, 1 to 2, 3 to 4, 5 to 0, 7 to 8, 8 to 9, 9 to 10, 11 to 6).forEach { (from, to) ->
            drawLine(
                color = Color.White.copy(alpha = 0.07f),
                start = points[from],
                end = points[to],
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        points.forEachIndexed { index, point ->
            val shimmer = 0.5f + 0.5f * kotlin.math.sin(phase * 1.7f + index * 0.91f)
            drawCircle(
                color = Color.White.copy(alpha = (if (index % 4 == 0) 0.32f else 0.14f) + shimmer * 0.28f),
                radius = (if (index % 4 == 0) 1.25f + shimmer * 0.75f else 0.75f + shimmer * 0.45f).dp.toPx(),
                center = point
            )
        }
        repeat(5) { index ->
            val angle = phase * (if (index % 2 == 0) 1f else -0.72f) + index * 1.256f
            val radius = size.minDimension * (0.24f + index * 0.035f)
            drawCircle(
                color = Color.White.copy(alpha = 0.18f + (index % 2) * 0.14f),
                radius = (1.1f + (index % 3) * 0.45f).dp.toPx(),
                center = Offset(
                    center.x + kotlin.math.cos(angle) * radius,
                    center.y + kotlin.math.sin(angle) * radius
                )
            )
        }
    }
}

@Composable
private fun PanelModeButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PanelFooterButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
