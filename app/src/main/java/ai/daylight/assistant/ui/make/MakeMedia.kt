package ai.daylight.assistant.ui.make

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.ui.run.FileCardModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a made file is, read from the file: pixel size and, for a clip, its length. */
data class MediaFacts(val width: Int = 0, val height: Int = 0, val durationMs: Long? = null)

/**
 * Files on the phone for Make: gallery saves, sharing, opening, thumbnails and facts. All
 * calls that touch a file run off the main thread (the callers use Dispatchers.IO).
 */
internal object MakeMedia {
    // The Inkwell shows tiles a third of the screen wide, so the cache holds a screenful
    // or two of those: an eighth of the app's memory, between 8 and 32 MB.
    private val thumbs = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L * 1024 * 1024, 32L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun fileUri(context: Context, output: GeneratedOutput): Uri? = output.localPath?.let { path ->
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", File(path)) }.getOrNull()
    }

    /** True when the gallery can take the file directly (Android 10+). Older phones use the file picker. */
    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** A file name for the gallery: "kryzz-image-20261029-1021.jpg". */
    fun galleryName(output: GeneratedOutput, now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val ext = output.fileName.substringAfterLast('.', "").ifBlank { if (output.kind == OutputKind.VIDEO) "mp4" else "jpg" }
        val noun = if (output.kind == OutputKind.VIDEO) "clip" else "image"
        return "kryzz-$noun-$stamp.$ext"
    }

    /**
     * Copies the file into the shared gallery (Pictures/Kryzz or Movies/Kryzz) through
     * MediaStore, which needs no permission on Android 10+. Returns false when it couldn't.
     */
    fun saveToGallery(context: Context, output: GeneratedOutput): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val source = output.localPath?.let(::File)?.takeIf(File::isFile) ?: return false
        val video = output.kind == OutputKind.VIDEO
        val collection = if (video) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, galleryName(output))
            put(MediaStore.MediaColumns.MIME_TYPE, output.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Kryzz"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        return try {
            resolver.openOutputStream(target)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: error("No output stream")
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (_: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            false
        }
    }

    /** Writes the file to a place the user picked (the Android 9 save path). */
    fun writeTo(context: Context, uri: Uri, output: GeneratedOutput): Boolean = runCatching {
        val source = output.localPath?.let(::File)?.takeIf(File::isFile) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } != null
    }.getOrDefault(false)

    /** The share sheet with the file itself, or its link (or text) when there is no file. */
    fun share(context: Context, output: GeneratedOutput) {
        val uri = fileUri(context, output)
        val send = if (uri != null) {
            Intent(Intent.ACTION_SEND)
                .setType(output.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri(output.fileName, uri) }
        } else {
            // Only a link, or a result that exists only as text (the Inkwell shares those too).
            val text = output.remoteUrl ?: output.content ?: return
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Opens the file in the phone's own viewer (zoom for images, any file type). */
    fun open(context: Context, output: GeneratedOutput) {
        val uri = fileUri(context, output) ?: output.remoteUrl?.let(Uri::parse) ?: return
        fun view(type: String) = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        // v5.13.7: code and data files without a viewer of their own open as plain text.
        val opened = view(output.mimeType) || (FileCardModel.opensAsText(output) && view("text/plain"))
        if (!opened) {
            android.widget.Toast.makeText(context, FileCardModel.noViewerMessage(output), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** An image decoded no larger than [maxSide] pixels on its long side. */
    fun decodeImage(path: String, maxSide: Int): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()

    /** A small square-ish thumbnail for recent ink, cached across screens. */
    fun thumbnail(output: GeneratedOutput, sidePx: Int): ImageBitmap? {
        val path = output.localPath ?: return null
        val key = "$path@$sidePx"
        thumbs.get(key)?.let { return it }
        val bitmap = when (output.kind) {
            OutputKind.IMAGE -> decodeImage(path, sidePx)
            OutputKind.VIDEO -> frameAt(path, 1_000L, sidePx)
            else -> null
        } ?: return null
        thumbs.put(key, bitmap)
        return bitmap
    }

    /** A thumbnail of a photo or clip the user added, cached with the recent-ink tiles. */
    fun preview(path: String, video: Boolean, sidePx: Int): ImageBitmap? {
        val key = "src:$video:$path@$sidePx"
        thumbs.get(key)?.let { return it }
        val bitmap = if (video) frameAt(path, 0L, sidePx) else decodeImage(path, sidePx)
        if (bitmap == null) return null
        thumbs.put(key, bitmap)
        return bitmap
    }

    /** A clip's frame near [timeMs], scaled to fit [maxSide]. */
    fun frameAt(path: String, timeMs: Long, maxSide: Int): ImageBitmap? = withRetriever(path) { r ->
        r.getScaledFrameAtTime(timeMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)?.asImageBitmap()
    }

    /** Several frames from one opening of the file (the filmstrip). Missing frames are null. */
    fun frames(path: String, timesMs: List<Long>, maxSide: Int): List<ImageBitmap?> = withRetriever(path) { r ->
        timesMs.map { t ->
            runCatching {
                r.getScaledFrameAtTime(t * 1_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)?.asImageBitmap()
            }.getOrNull()
        }
    } ?: timesMs.map { null }

    /** Pixel size (and a clip's length) read from the file. */
    fun facts(output: GeneratedOutput): MediaFacts {
        val path = output.localPath?.takeIf { File(it).isFile } ?: return MediaFacts()
        return when (output.kind) {
            OutputKind.IMAGE -> runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                MediaFacts(bounds.outWidth.coerceAtLeast(0), bounds.outHeight.coerceAtLeast(0))
            }.getOrDefault(MediaFacts())
            OutputKind.VIDEO -> withRetriever(path) { r ->
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (rotation == 90 || rotation == 270) MediaFacts(h, w, duration) else MediaFacts(w, h, duration)
            } ?: MediaFacts()
            else -> MediaFacts()
        }
    }

    private fun <T> withRetriever(path: String, block: (MediaMetadataRetriever) -> T): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            block(retriever)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
