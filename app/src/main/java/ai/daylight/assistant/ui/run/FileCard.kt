package ai.daylight.assistant.ui.run

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.ui.dot.DotRadius
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The words and the icon on a file card. Pure, so the captions are unit tested. */
object FileCardModel {
    /** "DOCX", from the file name; the output kind when the name has no extension. */
    fun extension(output: GeneratedOutput): String =
        output.fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 6 }?.uppercase(Locale.ROOT)
            ?: output.kind.name

    /** "DOCX · 24 KB", or just the type when the size is unknown. */
    fun caption(output: GeneratedOutput, sizeBytes: Long?): String =
        listOfNotNull(extension(output), sizeBytes?.takeIf { it >= 0 }?.let(::sizeLabel)).joinToString(" · ")

    fun sizeLabel(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> "${(bytes + 512) / 1_024} KB"
        else -> "$bytes B"
    }

    /** Only files on the phone or at a link can be opened; text-only results are saved instead. */
    fun canOpen(output: GeneratedOutput): Boolean = output.localPath != null || output.remoteUrl != null

    /** The kit's dot file icons. A deliverable that fell back to plain text gets the plain file. */
    @DrawableRes
    fun icon(output: GeneratedOutput): Int = when (extension(output)) {
        "DOCX", "DOC" -> R.drawable.ic_kz_file_docx_dot
        "XLSX", "XLS" -> R.drawable.ic_kz_file_xlsx_dot
        "PDF" -> R.drawable.ic_kz_file_pdf_dot
        "ZIP", "KT", "JS", "TS", "PY", "HTML", "JSON" -> R.drawable.ic_kz_file_code_dot
        "SQLITE", "DB", "SQL" -> R.drawable.ic_kz_grid_dot
        "MD", "TXT", "CSV" -> R.drawable.ic_kz_file_dot
        else -> when (output.kind) {
            OutputKind.DOCUMENT -> R.drawable.ic_kz_file_docx_dot
            OutputKind.SPREADSHEET -> R.drawable.ic_kz_file_xlsx_dot
            OutputKind.PDF -> R.drawable.ic_kz_file_pdf_dot
            OutputKind.CODE -> R.drawable.ic_kz_file_code_dot
            OutputKind.DATABASE -> R.drawable.ic_kz_grid_dot
            OutputKind.IMAGE -> R.drawable.ic_kz_image_dot
            OutputKind.VIDEO -> R.drawable.ic_kz_video_dot
            OutputKind.AUDIO -> R.drawable.ic_kz_voice_wave_dot
        }
    }
}

/**
 * The kit file card (components.md section 5): 72dp, a 48dp tile with the dot file icon,
 * the file name over a mono caption, and Save on the right. Tapping the card opens the file;
 * a result that exists only as text is saved instead. Images show a preview above.
 */
@Composable
fun FileCard(
    output: GeneratedOutput,
    onSave: (GeneratedOutput) -> Unit,
    onOpen: (GeneratedOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DotTheme.c
    var size by remember(output.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(output.localPath, output.content) {
        size = withContext(Dispatchers.IO) {
            output.localPath?.let { path -> File(path).takeIf(File::isFile)?.length() }
                ?: output.content?.toByteArray(Charsets.UTF_8)?.size?.toLong()
        }
    }
    val openable = FileCardModel.canOpen(output)
    Column(modifier.fillMaxWidth().padding(top = 10.dp)) {
        if (output.kind == OutputKind.IMAGE && output.localPath != null) {
            ImagePreview(output.localPath, output.title) { onOpen(output) }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(DotRadius.Tile)
                .background(c.surface)
                .border(1.dp, c.border, DotRadius.Tile)
                .clickable(onClickLabel = if (openable) "Open" else "Save") {
                    if (openable) onOpen(output) else onSave(output)
                }
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
                Text(
                    output.fileName,
                    style = DotType.Title.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    FileCardModel.caption(output, size),
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
                    .clickable { onSave(output) }
                    .semantics { contentDescription = "Save ${output.fileName}"; role = Role.Button },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_kz_download), null, Modifier.size(22.dp), tint = c.textSecondary)
            }
        }
    }
}

/** A generated image, radius 16, no border. Decoded off the main thread at screen size. */
@Composable
private fun ImagePreview(path: String, title: String, onOpen: () -> Unit) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) { bitmap = withContext(Dispatchers.IO) { decodeScaled(path, maxSide = 1440) } }
    bitmap?.let {
        Image(
            it,
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(DotRadius.Tile)
                .clickable(onClickLabel = "Open image", onClick = onOpen),
            contentScale = ContentScale.Crop
        )
    }
}

private fun decodeScaled(path: String, maxSide: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
