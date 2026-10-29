package ai.daylight.assistant.ui.inkwell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.RecentOutput
import ai.daylight.assistant.ui.dot.DotButtonStyle
import ai.daylight.assistant.ui.dot.DotIconButton
import ai.daylight.assistant.ui.dot.DotPillButton
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.make.MakeMedia
import ai.daylight.assistant.ui.run.FileCardModel
import ai.daylight.assistant.ui.talk.DotSheet
import ai.daylight.assistant.ui.talk.OptionRow
import ai.daylight.assistant.ui.talk.SheetLabel
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The Inkwell is not designed in the kit, so it is built from kit parts: Tune's depth-page
 * header, the segmented control, mono date headers, Make's recent-ink tiles grown to two a
 * row, Run's 72dp file card, and the kit sheet for one piece with its actions.
 */

/** A day header in the feed ("TODAY", "( 23 ) OCT"). */
@Composable
internal fun InkDayHeader(label: String) {
    Text(
        label,
        Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp)
            .semantics { heading() },
        style = DotType.Label,
        color = DotTheme.c.textTertiary
    )
}

/** A small black pill with NEW in white: a piece made since the last visit. Never yellow. */
@Composable
private fun NewTag(modifier: Modifier = Modifier) {
    val c = DotTheme.c
    Box(
        modifier
            .height(20.dp)
            .background(c.bg, DotRadius.Pill)
            .border(1.dp, c.borderStrong, DotRadius.Pill)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("NEW", style = DotType.Label.copy(fontSize = 10.sp), color = c.textPrimary)
    }
}

/** The thumbnail of an image or the first second of a clip, decoded off the main thread. */
@Composable
private fun rememberThumb(item: RecentOutput, sidePx: Int): ImageBitmap? {
    var thumb by remember(item.output.id, sidePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.output.id, sidePx) {
        thumb = withContext(Dispatchers.IO) { MakeMedia.thumbnail(item.output, sidePx) }
    }
    return thumb
}

/**
 * An image or clip: a square tile (the picture, or its dot icon while it loads or when the
 * file is gone), then the request on one line and "09:12 · IMAGE". Tap opens its sheet.
 */
@Composable
internal fun InkTile(item: RecentOutput, fresh: Boolean, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DotTheme.c
    val output = item.output
    val title = InkwellModel.title(item)
    val noun = if (output.kind == OutputKind.VIDEO) "Clip" else "Image"
    Column(
        modifier
            .clip(DotRadius.Tile)
            .clickable(onClickLabel = "Show", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$noun: $title" }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(DotRadius.Tile)
                .background(c.surfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            val px = with(LocalDensity.current) { maxWidth.roundToPx() }
            val thumb = rememberThumb(item, px)
            if (thumb != null) {
                Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(painterResource(FileCardModel.icon(output)), null, Modifier.size(40.dp), tint = c.textTertiary)
            }
            if (output.kind == OutputKind.VIDEO) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = .55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_kz_play), null, Modifier.size(14.dp), tint = Color.White)
                }
            }
            if (fresh) NewTag(Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Text(
            title,
            Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
            style = DotType.BodySmall,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            InkwellModel.caption(item, zone),
            Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp, bottom = 4.dp),
            style = DotType.Caption,
            color = c.textTertiary,
            maxLines = 1
        )
    }
}

/** A file's size on the phone (or of its text), read off the main thread. Null = unknown. */
@Composable
private fun rememberFileSize(item: RecentOutput): Long? {
    var size by remember(item.output.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(item.output.id) {
        size = withContext(Dispatchers.IO) {
            item.output.localPath?.let { path -> File(path).takeIf(File::isFile)?.length() }
                ?: item.output.content?.toByteArray(Charsets.UTF_8)?.size?.toLong()
        }
    }
    return size
}

/**
 * A document, sheet, code or audio file: Run's 72dp file card with the time in its caption
 * ("DOCX · 24 KB · 09:12"). Tap opens its sheet; the arrow saves it straight away.
 */
@Composable
internal fun InkFileRow(item: RecentOutput, fresh: Boolean, zone: ZoneId, onClick: () -> Unit, onSave: () -> Unit) {
    val c = DotTheme.c
    val output = item.output
    val size = rememberFileSize(item)
    val clock = InkwellModel.caption(item, zone).substringBefore(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(DotRadius.Tile)
            .background(c.surface)
            .border(1.dp, c.border, DotRadius.Tile)
            .clickable(onClickLabel = "Show", onClick = onClick)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).background(c.surfaceHigh, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(FileCardModel.icon(output)), null, Modifier.size(26.dp), tint = c.textPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    InkwellModel.title(item),
                    Modifier.weight(1f, fill = false),
                    style = DotType.Title.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (fresh) {
                    Spacer(Modifier.width(8.dp))
                    NewTag()
                }
            }
            Text(
                "${FileCardModel.caption(output, size)} · $clock",
                Modifier.padding(top = 3.dp),
                style = DotType.Caption,
                color = c.textTertiary,
                maxLines = 1
            )
        }
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(onClick = onSave)
                .semantics { contentDescription = "Save ${output.fileName}"; role = Role.Button },
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_kz_download), null, Modifier.size(22.dp), tint = c.textSecondary)
        }
    }
}

/**
 * One piece of ink in the kit sheet: the picture (or the file's dot icon), what was asked,
 * the file and its size, then Save, Share and Open, and a way back to the chat it came from.
 */
@Composable
internal fun InkSheet(
    item: RecentOutput,
    saved: Boolean,
    zone: ZoneId,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = DotTheme.c
    val output = item.output
    val size = rememberFileSize(item)
    val openable = FileCardModel.canOpen(output)
    DotSheet(onDismiss) {
        SheetLabel(InkwellModel.sheetLabel(item, zone))
        InkPreview(item, onOpen = if (openable) onOpen else null)
        Text(
            InkwellModel.title(item),
            Modifier.padding(top = 16.dp),
            style = DotType.Body.copy(fontSize = 17.sp, lineHeight = 24.sp),
            color = c.textPrimary,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(output.fileName, size?.let(FileCardModel::sizeLabel)).joinToString(" · ").uppercase(),
            Modifier.padding(top = 6.dp),
            style = DotType.Caption,
            color = c.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DotPillButton(
                if (saved) "Saved" else "Save",
                onSave,
                Modifier.weight(1f),
                style = DotButtonStyle.White,
                icon = if (saved) R.drawable.ic_kz_check else R.drawable.ic_kz_download
            )
            RoundAction(R.drawable.ic_kz_share, "Share", onShare)
            if (openable) RoundAction(R.drawable.ic_kz_external, "Open", onOpen)
        }
        OptionRow(R.drawable.ic_kz_chat, "Open the chat", onClick = { onDismiss(); onOpenChat() })
    }
}

/** A 48dp outlined round action next to the sheet's Save. */
@Composable
private fun RoundAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    val c = DotTheme.c
    Box(Modifier.border(1.dp, c.borderStrong, CircleShape)) {
        DotIconButton(icon, label, onClick, iconSize = 20.dp)
    }
}

/**
 * The sheet's preview. An image or a clip's frame fills the width (cropped to between 4:5
 * and 2.4:1); tapping it opens the phone's viewer. Other files show their dot icon.
 */
@Composable
private fun InkPreview(item: RecentOutput, onOpen: (() -> Unit)?) {
    val c = DotTheme.c
    val output = item.output
    val media = output.kind == OutputKind.IMAGE || output.kind == OutputKind.VIDEO
    var bitmap by remember(output.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(output.id) {
        if (media) {
            bitmap = withContext(Dispatchers.IO) {
                val path = output.localPath?.takeIf { File(it).isFile } ?: return@withContext null
                if (output.kind == OutputKind.VIDEO) MakeMedia.frameAt(path, 1_000L, 1440) else MakeMedia.decodeImage(path, 1440)
            }
        }
    }
    val image = bitmap
    val tap = if (onOpen != null) Modifier.clickable(onClickLabel = "Open", onClick = onOpen) else Modifier
    if (image != null) {
        val ratio = (image.width.toFloat() / image.height.coerceAtLeast(1)).coerceIn(0.8f, 2.4f)
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(DotRadius.Tile).then(tap)) {
            Image(image, InkwellModel.title(item), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (output.kind == OutputKind.VIDEO) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = .55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_kz_play), null, Modifier.size(24.dp), tint = Color.White)
                }
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(DotRadius.Tile)
                .background(c.surfaceRaised)
                .then(tap),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(FileCardModel.icon(output)), null, Modifier.size(72.dp), tint = c.textSecondary)
        }
    }
}
