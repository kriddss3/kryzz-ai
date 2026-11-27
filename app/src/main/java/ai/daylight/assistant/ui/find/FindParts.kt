package ai.daylight.assistant.ui.find

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.FindFilters
import ai.daylight.assistant.domain.FindKind
import ai.daylight.assistant.domain.FindSize
import ai.daylight.assistant.domain.FindWebSource
import ai.daylight.assistant.domain.FindWebState
import ai.daylight.assistant.domain.FindWebStatus
import ai.daylight.assistant.domain.FoundImage
import ai.daylight.assistant.domain.LicenceFilter
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotColors
import ai.daylight.assistant.ui.dot.DotColorsLight
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotSheetHandle
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.effects.ThinkingDots
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.tune.TuneChoiceRow
import ai.daylight.assistant.ui.tune.TuneDetail
import ai.daylight.assistant.ui.tune.TuneEmpty
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale

/** The kit's image tile radius (web-images: 12). */
private val TileShape = RoundedCornerShape(12.dp)

/**
 * What shows behind an image. Drawings are often black lines on nothing, which vanish on a
 * dark tile, so they sit on light grey the way Commons shows them.
 */
@Composable
private fun backdrop(image: FoundImage): Color = if (image.isDrawing) DotColorsLight.SurfaceRaised else DotTheme.c.surfaceRaised

/** A tile's request. The plain URL is the memory key, so the large view can show it while it loads. */
@Composable
private fun rememberThumbRequest(image: FoundImage): ImageRequest {
    val context = LocalContext.current
    return remember(image.thumbUrl) {
        ImageRequest.Builder(context).data(image.thumbUrl).memoryCacheKey(image.thumbUrl).build()
    }
}

/**
 * The query field (kit: 48dp, surface, 1dp border, search icon, clear X). The keyboard's
 * Search key runs the search.
 */
@Composable
internal fun FindQueryField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Search free images",
    semanticsLabel: String = "Search Wikimedia Commons",
) {
    val c = DotTheme.c
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.surface, DotRadius.Pill)
            .border(1.dp, if (focused) c.borderStrong else c.border, DotRadius.Pill)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_kz_search), null, Modifier.size(18.dp), tint = if (enabled) c.textPrimary else c.textDisabled)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    // A hardware Enter searches on key-up and both halves are kept here; otherwise the
                    // key-up landed on whatever took focus next (the FIND title, which opens the library).
                    .onPreviewKeyEvent { event ->
                        if (event.key != Key.Enter && event.key != Key.NumPadEnter) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) onSearch()
                        true
                    }
                    .semantics { contentDescription = semanticsLabel },
                enabled = enabled,
                singleLine = true,
                textStyle = DotType.Body.copy(fontSize = 16.sp, color = if (enabled) c.textPrimary else c.textTertiary),
                cursorBrush = SolidColor(c.accentText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                interactionSource = source,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = DotType.Body.copy(fontSize = 16.sp), color = c.textTertiary, maxLines = 1)
                    }
                    inner()
                }
            )
        }
        if (value.isNotEmpty()) {
            DotIconButton(R.drawable.ic_kz_close, "Clear search", onClear, tint = c.textTertiary, iconSize = 18.dp)
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/**
 * A filter pill (kit: 28dp, raised, mono caps). [on] gives it the yellow outline the mockup
 * gives the file-type pill. It opens that filter's choices.
 */
@Composable
internal fun FilterPill(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clip(DotRadius.Pill)
            .clickable(role = Role.Button, onClickLabel = "Change") {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .clearAndSetSemantics {
                contentDescription = description
                onClick(label = "Change") { onClick(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .height(28.dp)
                .background(c.surfaceRaised, DotRadius.Pill)
                .border(1.dp, if (on) c.accentText else c.border, DotRadius.Pill)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) Icon(painterResource(icon), null, Modifier.size(14.dp), tint = if (on) c.accentText else c.textSecondary)
            Text(
                label.uppercase(Locale.US),
                style = DotType.Caption,
                color = if (on) c.textPrimary else c.textSecondary,
                maxLines = 1
            )
        }
    }
}

/** The kit's selected-tile mark: a 2dp outline 2dp outside the tile. */
private fun Modifier.selectionOutline(on: Boolean, color: Color): Modifier = if (!on) this else drawWithContent {
    drawContent()
    val stroke = 2.dp.toPx()
    val reach = 2.dp.toPx() + stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(-reach, -reach),
        size = Size(size.width + reach * 2f, size.height + reach * 2f),
        cornerRadius = CornerRadius(12.dp.toPx() + reach),
        style = Stroke(stroke)
    )
}

/**
 * One result: the image at its own shape and, under it, where it is from and its licence
 * ("COMMONS, CC BY-SA 4.0"). An image is never shown without both.
 */
@Composable
internal fun FindTile(
    image: FoundImage,
    selected: Boolean,
    loader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    var failed by remember(image.id) { mutableStateOf(false) }
    val request = rememberThumbRequest(image)
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = FindModel.tileDescription(image)
                this.selected = selected
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(image.aspect)
                .selectionOutline(selected, c.textPrimary)
                .clip(TileShape)
                .background(backdrop(image))
                .clickable(onClickLabel = "Show details", onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                imageLoader = loader,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { failed = false },
                onError = { failed = true }
            )
            if (failed) Icon(painterResource(R.drawable.ic_kz_image), null, Modifier.size(22.dp), tint = c.textDisabled)
        }
        Text(
            FindModel.tileCaption(image).uppercase(Locale.US),
            Modifier.padding(top = 6.dp),
            style = DotType.Caption.copy(fontSize = 10.sp),
            color = c.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The viewer sheet (kit web-images): a preview, title, author, licence and source, then
 * Save, Source and Share, plus "Use in Make" to stage the image into Make's composer.
 * It sits inside Find, so tapping another tile swaps what it shows.
 * Drag it down, tap its handle or press Back to close it.
 */
@Composable
internal fun FindViewerSheet(
    image: FoundImage,
    loader: ImageLoader,
    saved: Boolean,
    saving: Boolean,
    sharing: Boolean,
    onClose: () -> Unit,
    onView: () -> Unit,
    onSave: () -> Unit,
    onSource: () -> Unit,
    onShare: () -> Unit,
    onLicence: (() -> Unit)?,
    onUseInMake: () -> Unit,
    modifier: Modifier = Modifier,
    usingInMake: Boolean = false,
) {
    val c = DotTheme.c
    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(0f) }
    val dismissAt = with(density) { 96.dp.toPx() }
    val request = rememberThumbRequest(image)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = drag }
            .clip(DotRadius.Sheet)
            .background(c.surface)
            .border(1.dp, c.border, DotRadius.Sheet)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag > dismissAt) onClose() else drag = 0f },
                    onDragCancel = { drag = 0f }
                ) { change, amount ->
                    change.consume()
                    drag = (drag + amount).coerceAtLeast(0f)
                }
            }
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable(role = Role.Button, onClickLabel = "Close details", onClick = onClose)
                .semantics { contentDescription = "Close details" },
            contentAlignment = Alignment.TopCenter
        ) {
            DotSheetHandle()
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
            Box(
                Modifier
                    .size(150.dp, 112.dp)
                    .clip(TileShape)
                    .background(backdrop(image))
                    .clickable(onClickLabel = "View larger", onClick = onView)
                    .semantics { contentDescription = "View larger" }
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    imageLoader = loader,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    image.title,
                    style = DotType.Title.copy(fontSize = 16.sp, lineHeight = 21.sp),
                    color = c.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    (image.attribution ?: image.author ?: "Author not stated").uppercase(Locale.US),
                    Modifier.padding(top = 8.dp),
                    style = DotType.Caption,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    image.licence.shortName.uppercase(Locale.US),
                    Modifier
                        .padding(top = 2.dp)
                        .then(
                            if (onLicence != null) {
                                Modifier.clickable(role = Role.Button, onClickLabel = "Read the licence", onClick = onLicence)
                            } else {
                                Modifier
                            }
                        ),
                    style = DotType.Caption,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    image.domain.uppercase(Locale.US),
                    Modifier.padding(top = 2.dp),
                    style = DotType.Caption,
                    color = c.textTertiary,
                    maxLines = 1
                )
                ActionChip(
                    label = if (usingInMake) "Staging…" else "Use in Make",
                    icon = R.drawable.ic_kz_image,
                    busy = usingInMake,
                    onClick = onUseInMake,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DotPillButton(
                label = if (saved) "Saved" else "Save",
                onClick = onSave,
                modifier = Modifier.weight(1f),
                style = DotButtonStyle.White,
                busy = saving,
                icon = if (saved) R.drawable.ic_kz_check else R.drawable.ic_kz_download
            )
            DotPillButton(
                label = "Source",
                onClick = onSource,
                modifier = Modifier.weight(1f),
                style = DotButtonStyle.Ghost,
                icon = R.drawable.ic_kz_external
            )
            RoundBusyButton(R.drawable.ic_kz_share, "Share with credit", sharing, onShare)
        }
        Text(
            "Credit line is copied with every save or share".uppercase(Locale.US),
            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            style = DotType.Caption.copy(fontSize = 10.sp),
            color = c.textTertiary
        )
    }
}

/** Active 28dp pill action chip inside [FindViewerSheet] with a full 48dp touch target. */
@Composable
private fun ActionChip(
    label: String,
    @DrawableRes icon: Int,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clip(DotRadius.Pill)
            .clickable(enabled = !busy, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .clearAndSetSemantics {
                contentDescription = if (busy) "$label, working" else label
                if (!busy) onClick { onClick(); true }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .height(28.dp)
                .background(c.surfaceRaised, DotRadius.Pill)
                .border(1.dp, if (busy) c.accentText else c.borderStrong, DotRadius.Pill)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (busy) {
                ThinkingDots(dot = 3.5.dp, on = c.accentText, off = c.accentText.copy(alpha = .3f))
            } else {
                Icon(painterResource(icon), null, Modifier.size(14.dp), tint = c.textPrimary)
            }
            Text(
                label.uppercase(Locale.US),
                style = DotType.Caption.copy(fontSize = 10.sp),
                color = if (busy) c.accentText else c.textPrimary,
                maxLines = 1
            )
        }
    }
}

/** A 48dp round icon button that shows stepping dots while its work runs. */
@Composable
private fun RoundBusyButton(@DrawableRes icon: Int, label: String, busy: Boolean, onClick: () -> Unit) {
    val c = DotTheme.c
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(c.surfaceRaised)
            .border(1.dp, c.border, CircleShape)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (busy) "$label, working" else label },
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            ThinkingDots(dot = 4.dp, on = c.textPrimary, off = c.textPrimary.copy(alpha = .3f))
        } else {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = c.textPrimary)
        }
    }
}

/**
 * The image large, on black: pinch or double-tap to zoom, Back or X to close. The tile's
 * thumbnail shows while the 1280px view loads. The credit stays on screen.
 */
@Composable
internal fun FindImageViewer(image: FoundImage, loader: ImageLoader, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // As in DotDialog: let the window cover the whole screen and pad for the bars here.
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val attributes = window.attributes
                if (attributes.fitInsetsTypes != 0) {
                    attributes.fitInsetsTypes = 0
                    window.attributes = attributes
                }
            }
        }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val request = remember(image.viewUrl) {
            ImageRequest.Builder(context).data(image.viewUrl).placeholderMemoryCacheKey(image.thumbUrl).build()
        }
        Box(Modifier.fillMaxSize().background(if (image.isDrawing) DotColorsLight.SurfaceRaised else Color.Black)) {
            AsyncImage(
                model = request,
                contentDescription = FindModel.tileDescription(image),
                imageLoader = loader,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            DotIconButton(
                R.drawable.ic_kz_close,
                "Close",
                onDismiss,
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                tint = if (image.isDrawing) DotColorsLight.TextPrimary else DotColors.TextPrimary
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .6f))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    image.title,
                    style = DotType.Title.copy(fontSize = 16.sp, lineHeight = 21.sp),
                    color = DotColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(image.attribution ?: image.author, image.licence.shortName, "Wikimedia Commons")
                        .joinToString(", ")
                        .uppercase(Locale.US),
                    Modifier.padding(top = 6.dp),
                    style = DotType.Caption,
                    color = DotColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Which filter's choices are open. */
internal enum class FindFilterChoice { KIND, LICENCE, SIZE }

/** The choices for one filter, as a kit sheet of radio rows. */
@Composable
internal fun FindFilterSheet(
    choice: FindFilterChoice,
    filters: FindFilters,
    onKind: (FindKind) -> Unit,
    onLicence: (LicenceFilter) -> Unit,
    onSize: (FindSize) -> Unit,
    onDismiss: () -> Unit,
) {
    DotSheet(onDismiss = onDismiss) {
        when (choice) {
            FindFilterChoice.KIND -> {
                SheetLabel("Find")
                FindKind.entries.forEach { kind ->
                    TuneChoiceRow(kind.label, kind.detail, kind == filters.kind, onClick = { onKind(kind) })
                }
            }
            FindFilterChoice.LICENCE -> {
                SheetLabel("Licence")
                LicenceFilter.entries.forEach { licence ->
                    TuneChoiceRow(licence.label, licence.detail, licence == filters.licence, onClick = { onLicence(licence) })
                }
                TuneDetail(
                    "Commons only keeps free files. Reusable keeps to licences that ask for a credit at most; " +
                        "Find adds that credit to every save and share.",
                    Modifier.padding(top = 12.dp)
                )
            }
            FindFilterChoice.SIZE -> {
                SheetLabel("Size")
                FindSize.entries.forEach { size ->
                    TuneChoiceRow(size.label, size.detail, size == filters.size, onClick = { onSize(size) })
                }
            }
        }
    }
}

/** Body for Find's Web browsing mode: loading states, error/retry, synthesized answer card, and clickable sources. */
@Composable
internal fun FindWebResultsBody(
    state: FindWebState,
    off: Boolean,
    onAllowSearch: () -> Unit,
    onRetry: () -> Unit,
    onCopyAnswer: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    when {
        off -> FindWebMessage(modifier) {
            TuneEmpty(
                dotIcon = R.drawable.ic_kz_web_dot,
                words = "Search off",
                body = "Find needs web search to browse the web. It is switched off in Tune.",
                action = "Allow web search",
                onAction = onAllowSearch,
                actionIcon = R.drawable.ic_kz_web
            )
        }
        state.status == FindWebStatus.IDLE -> FindWebMessage(modifier) {
            TuneEmpty(
                dotIcon = R.drawable.ic_kz_web_dot,
                words = "Web search",
                body = "Search the live web with Parallel Search and get a quick sourced summary."
            )
        }
        state.status == FindWebStatus.SEARCHING || state.status == FindWebStatus.SYNTHESIZING -> FindWebMessage(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ThinkingDots(dot = 6.dp)
                Text(
                    if (state.status == FindWebStatus.SYNTHESIZING) {
                        "SUMMARIZING SOURCES"
                    } else {
                        "SEARCHING THE WEB"
                    },
                    Modifier.padding(top = 14.dp),
                    style = DotType.Caption,
                    color = c.textTertiary
                )
            }
        }
        state.status == FindWebStatus.FAILED -> FindWebMessage(modifier) {
            TuneEmpty(
                dotIcon = R.drawable.ic_kz_warning_dot,
                words = "Search failed",
                body = state.error ?: "The web search failed.",
                action = "Try again",
                onAction = onRetry,
                actionIcon = R.drawable.ic_kz_retry
            )
        }
        else -> {
            val result = state.result
            if (result == null) {
                FindWebMessage(modifier) {
                    TuneEmpty(
                        dotIcon = R.drawable.ic_kz_web_dot,
                        words = "No results",
                        body = "Try fewer or different words."
                    )
                }
            } else {
                LazyColumn(
                    modifier = modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "answer") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(DotRadius.Card)
                                .background(c.surface, DotRadius.Card)
                                .border(1.dp, c.border, DotRadius.Card)
                                .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ANSWER", style = DotType.Label, color = c.textTertiary)
                                result.modelLabel?.let { label ->
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        label.uppercase(Locale.US),
                                        Modifier
                                            .weight(1f, fill = false)
                                            .background(c.surfaceRaised, DotRadius.Pill)
                                            .border(1.dp, c.border, DotRadius.Pill)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = DotType.Caption.copy(fontSize = 10.sp),
                                        color = c.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                DotIconButton(
                                    icon = R.drawable.ic_kz_copy,
                                    label = "Copy answer",
                                    onClick = { onCopyAnswer(result.answer) },
                                    tint = c.textSecondary,
                                    iconSize = 18.dp
                                )
                            }
                            Text(
                                result.answer,
                                Modifier.padding(top = 4.dp, end = 12.dp),
                                style = DotType.Body,
                                color = c.textPrimary
                            )
                        }
                    }

                    item(key = "sources_header") {
                        Text(
                            if (result.sources.isEmpty()) "NO SOURCES FOUND" else "SOURCES (${result.sources.size})",
                            Modifier.padding(top = 4.dp),
                            style = DotType.Label,
                            color = c.textSecondary
                        )
                    }

                    itemsIndexed(result.sources, key = { index, source -> "${index}_${source.url}" }) { _, source ->
                        WebSourceRow(source = source, onClick = { onOpenUrl(source.url) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FindWebMessage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 8.dp, top = 32.dp, bottom = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        content()
    }
}

@Composable
private fun WebSourceRow(source: FindWebSource, onClick: () -> Unit) {
    val c = DotTheme.c
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DotRadius.Tile)
            .background(c.surface, DotRadius.Tile)
            .border(1.dp, c.border, DotRadius.Tile)
            .clickable(role = Role.Button, onClickLabel = "Open source", onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                source.domain.ifBlank { source.url }.uppercase(Locale.US),
                Modifier.weight(1f, fill = false),
                style = DotType.Caption.copy(fontSize = 10.sp),
                color = c.accentText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            source.publishDate?.let { date ->
                Text(
                    date.uppercase(Locale.US),
                    style = DotType.Caption.copy(fontSize = 10.sp),
                    color = c.textTertiary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_kz_external), null, Modifier.size(14.dp), tint = c.textTertiary)
        }
        Text(
            source.title,
            style = DotType.Title.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (source.snippet.isNotBlank()) {
            Text(
                source.snippet,
                style = DotType.BodySmall,
                color = c.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
