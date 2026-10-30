package ai.daylight.assistant.ui.make

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.MakeKind
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.RecentOutput
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.dot.dashedBorder
import ai.daylight.assistant.ui.dot.effects.DotDevelopImage
import ai.daylight.assistant.ui.dot.effects.DotMatrixText
import ai.daylight.assistant.ui.dot.effects.DotProgress
import ai.daylight.assistant.ui.run.FileCardModel
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.SheetLabel
import ai.daylight.assistant.ui.tune.TuneChoiceRow
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The kit prompt card: a 24dp-radius surface with a mono "PROMPT" label and an edit icon.
 * While [editable] it is the text field itself; otherwise it shows the prompt with the
 * result's spec pills under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PromptCard(
    text: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onText: (String) -> Unit = {},
    placeholder: String = "",
    label: Boolean = true,
    specs: List<String> = emptyList(),
    focusRequester: FocusRequester? = null,
    onEdit: (() -> Unit)? = null,
) {
    val c = DotTheme.c
    val body = DotType.Body.copy(fontSize = 16.sp, lineHeight = 22.sp)
    // While writing, a tap anywhere on the card (the PROMPT label, the margins) puts the caret in the field.
    val focusCard = if (editable && focusRequester != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            runCatching { focusRequester.requestFocus() }
        }
    } else {
        Modifier
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(DotRadius.Card)
            .background(c.surface, DotRadius.Card)
            .border(1.dp, c.border, DotRadius.Card)
            .then(focusCard)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = if (onEdit != null) 48.dp else 16.dp, bottom = 16.dp)) {
            if (label) {
                Text("PROMPT", Modifier.padding(bottom = 6.dp), style = DotType.Label, color = c.textTertiary)
            }
            if (editable) {
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .semantics { contentDescription = "Prompt" },
                    textStyle = body.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.accentText),
                    minLines = 2,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(placeholder, style = body, color = c.textTertiary)
                        inner()
                    }
                )
            } else {
                Text(text, style = body, color = c.textPrimary)
            }
            if (specs.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    specs.forEach { SpecPill(it) }
                }
            }
        }
        if (onEdit != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = null, onClick = onEdit)
                    .semantics { contentDescription = "Edit prompt" },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_kz_edit), null, Modifier.size(18.dp), tint = c.textTertiary)
            }
        }
    }
}

/** A spec on a result ("6.0 S", "16:9", "720P"): a 28dp mono pill. A fact, not a control. */
@Composable
internal fun SpecPill(text: String) {
    val c = DotTheme.c
    Box(
        Modifier
            .height(28.dp)
            .background(c.surfaceRaised, DotRadius.Pill)
            .border(1.dp, c.border, DotRadius.Pill)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(Locale.US), style = DotType.Label, color = c.textSecondary, maxLines = 1)
    }
}

/**
 * The render frame (radius 16): unlit dots on a 6dp grid in the shape of the chosen ratio,
 * with the ratio written in the middle. The dot develop effect arrives with phase D.
 */
@Composable
internal fun RenderFrame(aspect: Float, caption: String?, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Box(
        modifier
            .aspectRatio(aspect)
            .clip(DotRadius.Tile)
            .background(c.surface)
            .semantics { contentDescription = "Frame" + (caption?.let { ", $it" } ?: "") },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val pitch = 6.dp.toPx()
            val r = 0.9.dp.toPx()
            val cols = (size.width / pitch).toInt()
            val rows = (size.height / pitch).toInt()
            val ox = (size.width - (cols - 1) * pitch) / 2f
            val oy = (size.height - (rows - 1) * pitch) / 2f
            for (j in 0 until rows) for (i in 0 until cols) {
                drawCircle(c.dotOff, r, Offset(ox + i * pitch, oy + j * pitch))
            }
        }
        if (caption != null) {
            Text(
                caption.uppercase(Locale.US),
                Modifier.background(c.surface, DotRadius.Pill).padding(horizontal = 8.dp, vertical = 2.dp),
                style = DotType.Label,
                color = c.textDisabled
            )
        }
    }
}

/** The ratio drawn as its own outline: a 1.5dp white rectangle in a 16dp box. */
@Composable
internal fun RatioGlyph(ratio: String?, modifier: Modifier = Modifier, color: Color = DotTheme.c.textPrimary) {
    val shape = MakeModel.ratioShape(ratio) ?: return
    Canvas(modifier.size(18.dp)) {
        val stroke = 1.5.dp.toPx()
        val box = size.minDimension - stroke
        val (w, h) = shape
        val scale = box / maxOf(w, h)
        val rw = w * scale
        val rh = h * scale
        drawRoundRect(
            color,
            topLeft = Offset((size.width - rw) / 2f, (size.height - rh) / 2f),
            size = Size(rw, rh),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke)
        )
    }
}

/** One dial from the kit's dial column: a mono caption over a Geist 17 value, with a rule under it. */
@Composable
internal fun Dial(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    lit: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = DotTheme.c
    val spoken = "$label, $value"
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .clearAndSetSemantics {
                    contentDescription = spoken
                    if (onClick != null) {
                        role = Role.Button
                        onClick(label = "Change $label") { onClick(); true }
                    }
                }
                .padding(top = 10.dp, bottom = 9.dp)
        ) {
            Text(label.uppercase(Locale.US), style = DotType.Label, color = c.textSecondary, maxLines = 1)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                leading?.invoke()
                Text(
                    value,
                    style = DotType.Title.copy(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
                    color = if (lit || onClick != null) c.textPrimary else c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = c.surfaceRaised)
    }
}

/** What a dial shows; [onClick] null makes it a plain fact. [share] is its width in a row of dials. */
internal data class DialSpec(
    val label: String,
    val value: String,
    val onClick: (() -> Unit)? = null,
    val ratio: String? = null,
    val share: Float = 1f,
)

/**
 * The frame and its dials: a tall or square frame sits left with the dials in a column on
 * the right (the kit layout); a wide one takes the full width with the dials in a row below.
 */
@Composable
internal fun FrameWithDials(aspect: Float, caption: String?, dials: List<DialSpec>, modifier: Modifier = Modifier) {
    @Composable
    fun DialFor(spec: DialSpec, m: Modifier) {
        Dial(
            spec.label, spec.value, m, spec.onClick, lit = spec.ratio != null,
            leading = spec.ratio?.let { r -> { RatioGlyph(r) } }
        )
    }
    if (MakeModel.wideFrame(aspect)) {
        // v2.1: under a wide frame the model gets a row of its own and the other dials go two
        // a row. Five dials in one row (MiniMax video) left each about 44dp and cut their words.
        val (wide, small) = dials.partition { it.share > 1f }
        Column(modifier.fillMaxWidth()) {
            RenderFrame(aspect, caption, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            wide.forEach { DialFor(it, Modifier.fillMaxWidth()) }
            small.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { DialFor(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Row(modifier.fillMaxWidth()) {
            RenderFrame(aspect, caption, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                dials.forEach { DialFor(it, Modifier) }
            }
        }
    }
}

/**
 * Progress while rendering: a row of 8dp dots (indeterminate, since no provider reports a
 * percentage), the elapsed time in Doto, and two mono lines saying where the job is.
 */
@Composable
internal fun RenderProgress(elapsed: String, status: String, detail: String, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val count = ((maxWidth + 6.dp) / 14.dp).toInt().coerceIn(6, 24)
            // DotProgress holds a still comet when motion is off.
            DotProgress(progress = null, count = count, dot = 8.dp, gap = 6.dp, on = c.accentText, off = c.dotOff)
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // The 5x7 dot face: Doto has no clean colon for the clock.
            DotMatrixText(elapsed, pitch = 3.4.dp, on = c.textPrimary, off = Color.Transparent, contentDescription = "Running for $elapsed")
            Spacer(Modifier.width(16.dp))
            Column {
                Text(status.uppercase(Locale.US), style = DotType.Caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail.uppercase(Locale.US), style = DotType.Caption, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "✓ IMAGE READY ........ MADE 10:21" above a result. */
@Composable
internal fun ReadyRow(ready: String, made: String, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_kz_check), null, Modifier.size(16.dp), tint = c.textPrimary)
        Spacer(Modifier.width(8.dp))
        Text(ready.uppercase(Locale.US), style = DotType.Caption, color = c.textPrimary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(made.uppercase(Locale.US), style = DotType.Caption, color = c.textTertiary, maxLines = 1)
    }
}

/**
 * A made image at full width, its own shape, radius 16. Tap opens it in the phone's viewer.
 * With [develop] (it just finished while Make was open) it develops as dots, then dissolves
 * into the photo (phase D).
 */
@Composable
internal fun ImageResult(
    path: String,
    aspect: Float?,
    description: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    develop: Boolean = false,
) {
    val c = DotTheme.c
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) { bitmap = withContext(Dispatchers.IO) { MakeMedia.decodeImage(path, 1440) } }
    val shape = aspect ?: bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1f
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(shape.coerceIn(0.5f, 2.4f))
            .clip(DotRadius.Tile)
            .background(c.surface)
            .clickable(onClickLabel = "Open image", onClick = onOpen)
    ) {
        bitmap?.let { DotDevelopImage(it, description, develop, Modifier.fillMaxSize()) }
    }
}

/** A 48dp round icon button on a raised fill, like the kit's share button. */
@Composable
internal fun RoundIconButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = DotTheme.c
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) c.surfaceRaised else Color.Transparent)
            .then(if (enabled) Modifier.border(1.dp, c.border, CircleShape) else Modifier.dashedBorder(c.borderStrong, radius = 24.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = if (enabled) c.textPrimary else c.textDisabled)
    }
}

/**
 * Recent ink: the newest made files from every conversation as 56dp tiles (images and clips
 * as thumbnails, other files as their dot icon), then a "+N" tile that opens the Inkwell.
 */
@Composable
internal fun RecentInk(
    recent: List<RecentOutput>,
    shownId: String?,
    onTile: (RecentOutput) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (tiles, more) = MakeModel.recentTiles(recent)
    if (tiles.isEmpty()) return
    val c = DotTheme.c
    Column(modifier.fillMaxWidth()) {
        Text("RECENT INK", style = DotType.Label, color = c.textSecondary)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEach { item -> RecentTile(item, item.output.id == shownId) { onTile(item) } }
            if (more > 0) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surfaceRaised)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onMore)
                        .semantics { contentDescription = "$more more in the Inkwell" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ $more", style = DotType.Label, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun RecentTile(item: RecentOutput, current: Boolean, onClick: () -> Unit) {
    val c = DotTheme.c
    val output = item.output
    val side = 56.dp
    val px = with(LocalDensity.current) { (side * 2).roundToPx() }
    val media = output.kind == OutputKind.IMAGE || output.kind == OutputKind.VIDEO
    var thumb by remember(output.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(output.id) {
        if (media) thumb = withContext(Dispatchers.IO) { MakeMedia.thumbnail(output, px) }
    }
    val noun = when (output.kind) {
        OutputKind.IMAGE -> "Image"
        OutputKind.VIDEO -> "Clip"
        else -> FileCardModel.extension(output)
    }
    val tileShape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(side)
            .then(if (current) Modifier.border(2.dp, c.textPrimary, RoundedCornerShape(14.dp)).padding(3.dp) else Modifier)
            .clip(tileShape)
            .background(c.surfaceRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$noun: ${item.prompt?.take(80) ?: output.fileName}" },
        contentAlignment = Alignment.Center
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(painterResource(FileCardModel.icon(output)), null, Modifier.size(22.dp), tint = c.textPrimary)
        }
        if (output.kind == OutputKind.VIDEO) {
            Icon(
                painterResource(R.drawable.ic_kz_video),
                null,
                Modifier.align(Alignment.BottomStart).padding(5.dp).size(14.dp),
                tint = Color.White
            )
        }
    }
}

/** The Ratio dial's sheet: each ratio the model takes, drawn as its own shape. */
@Composable
internal fun RatioSheet(kind: MakeKind, choices: List<String>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    DotSheet(onDismiss = onDismiss) {
        SheetLabel(if (kind == MakeKind.VIDEO) "Clip ratio" else "Image ratio")
        choices.forEach { ratio ->
            val shape = MakeModel.ratioShape(ratio)
            val detail = when {
                shape == null -> null
                shape.first == shape.second -> "Square"
                shape.first > shape.second -> "Wide"
                else -> "Tall"
            }
            TuneChoiceRow(
                title = ratio,
                detail = detail,
                selected = ratio == selected,
                onClick = { onPick(ratio) },
                trailing = { RatioGlyph(ratio, Modifier.padding(end = 4.dp)) }
            )
        }
    }
}
