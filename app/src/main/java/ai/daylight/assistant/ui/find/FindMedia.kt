package ai.daylight.assistant.ui.find

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import ai.daylight.assistant.data.remote.CommonsImageClient
import ai.daylight.assistant.domain.FoundImage
import ai.daylight.assistant.domain.MakeSources
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** A Find image downloaded for Save or Share, with the name the gallery gets. */
internal data class FindFile(val file: File, val mime: String, val displayName: String)

/**
 * Files for Find (C8). Save and Share download the image once into the app's cache, with
 * the credit line stamped into it (EXIF for JPEG and PNG), then:
 *  - Save copies it to Pictures/Kryzz with the credit as the file's description, and puts
 *    the credit line on the clipboard;
 *  - Share sends the file with the credit line as its text.
 * Every call that touches the network or a file runs off the main thread.
 */
internal object FindMedia {
    private const val FOLDER = "find"
    private const val MAKE_SOURCES_FOLDER = "make-sources"
    private const val KEEP_FILES = 12
    private const val KEEP_MAKE_SOURCES = 30

    /** True when the gallery takes the file directly (Android 10+). Android 9 saves through the file picker. */
    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Downloads [image] for saving or sharing, once per image (the cache keeps the newest few). */
    fun fetch(context: Context, client: CommonsImageClient, image: FoundImage): FindFile {
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        val base = "commons-${image.id}-${Integer.toHexString(image.saveUrl.hashCode())}"
        folder.listFiles()
            ?.firstOrNull { it.name.startsWith("$base.") && !it.name.endsWith(".part") && it.length() > 0 }
            ?.let { done ->
                done.setLastModified(System.currentTimeMillis())
                return FindFile(done, FindModel.mimeForExtension(done.extension), FindModel.displayName(image, done.extension))
            }
        val part = File(folder, "$base.part")
        try {
            val served = client.download(image.saveUrl, part)
            val mime = FindModel.savedMime(served, image)
            val ext = FindModel.extensionFor(mime)
            val out = File(folder, "$base.$ext")
            if (!part.renameTo(out)) {
                part.copyTo(out, overwrite = true)
            }
            if (mime == "image/jpeg" || mime == "image/png") stampCredit(out, image)
            prune(folder)
            return FindFile(out, mime, FindModel.displayName(image, ext))
        } finally {
            if (part.exists()) part.delete()
        }
    }

    /** The credit, licence and source go into the file's own EXIF (JPEG and PNG), so they travel with it. */
    private fun stampCredit(file: File, image: FoundImage) {
        runCatching {
            val exif = ExifInterface(file.path)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, FindModel.asciiFold(image.credit))
            image.author?.let(FindModel::asciiFold)?.takeIf(FindModel::readableAfterFold)?.let {
                exif.setAttribute(ExifInterface.TAG_ARTIST, it)
            }
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, FindModel.asciiFold("${image.licence.shortName}, ${image.pageUrl}"))
            exif.saveAttributes()
        }
    }

    private fun prune(folder: File) {
        val files = folder.listFiles { f -> f.isFile && !f.name.endsWith(".part") }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(KEEP_FILES).forEach { it.delete() }
    }

    /** Copies the file into Pictures/Kryzz through MediaStore, with the credit as its description. */
    fun saveToGallery(context: Context, file: FindFile, credit: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Kryzz")
            put(MediaStore.Images.ImageColumns.DESCRIPTION, credit)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        return try {
            resolver.openOutputStream(target)?.use { out -> file.file.inputStream().use { it.copyTo(out) } }
                ?: error("No output stream")
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (_: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            false
        }
    }

    /** Writes the file to a place the user picked (the Android 9 save path). */
    fun writeTo(context: Context, uri: Uri, file: FindFile): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out -> file.file.inputStream().use { it.copyTo(out) } } != null
    }.getOrDefault(false)

    /** The share sheet with the image and its credit line as the text. */
    fun share(context: Context, file: FindFile, image: FoundImage): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(file.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, image.credit)
            .putExtra(Intent.EXTRA_SUBJECT, image.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(file.displayName, uri) }
        context.startActivity(Intent.createChooser(send, image.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Puts the credit line on the clipboard. */
    fun copyCredit(context: Context, credit: String) = copyText(context, "Credit line", credit)

    /** Puts plain text on the clipboard. */
    fun copyText(context: Context, label: String, text: String) {
        runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    /**
     * Downloads [image] into durable app storage for Make, using a deterministic name so
     * repeated "Use in Make" taps deduplicate cleanly. Verifies the file fits [MakeSources.MAX_BYTES].
     */
    suspend fun stageForMake(context: Context, image: FoundImage, commons: CommonsImageClient): File =
        withContext(Dispatchers.IO) {
            val url = if (image.bytes in 1..MakeSources.MAX_BYTES && !image.mime.contains("svg", ignoreCase = true)) {
                image.saveUrl
            } else {
                image.viewUrl
            }
            val folder = File(context.applicationContext.filesDir, MAKE_SOURCES_FOLDER).apply { mkdirs() }
            val base = "commons-${image.id}-${Integer.toHexString(url.hashCode())}"
            folder.listFiles()
                ?.firstOrNull {
                    it.isFile && it.name.startsWith("$base.") && !it.name.endsWith(".part") &&
                        it.length() in 1..MakeSources.MAX_BYTES
                }
                ?.let { existing ->
                    existing.setLastModified(System.currentTimeMillis())
                    return@withContext existing
                }

            val part = File(folder, "$base.part")
            try {
                val served = commons.download(url, part, maxBytes = MakeSources.MAX_BYTES)
                val mime = FindModel.savedMime(served, image)
                val ext = FindModel.extensionFor(mime)
                val out = File(folder, "$base.$ext")
                if (!part.renameTo(out)) {
                    part.copyTo(out, overwrite = true)
                }
                if (out.length() !in 1..MakeSources.MAX_BYTES) {
                    out.delete()
                    throw IllegalStateException("Image exceeds Make's 20 MB limit or is empty.")
                }
                val files = folder.listFiles { f -> f.isFile && !f.name.endsWith(".part") }
                    ?.sortedByDescending { it.lastModified() }
                files?.drop(KEEP_MAKE_SOURCES)?.forEach { it.delete() }
                out
            } finally {
                if (part.exists()) part.delete()
            }
        }

    /** Opens a web page (the file's Commons page, or its licence) in the browser. */
    fun openPage(context: Context, url: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/**
 * The image loader for Find's thumbnails: the Commons media client (Wikimedia hosts only,
 * with the user agent Wikimedia asks for), a small memory cache and a disk cache in the
 * app's cache folder. AppContainer keeps one for the whole app.
 */
internal fun findImageLoader(context: Context, http: OkHttpClient): ImageLoader = ImageLoader.Builder(context)
    .okHttpClient(http)
    .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.15).build() }
    .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("find_thumbs")).maxSizeBytes(64L * 1024 * 1024).build() }
    .respectCacheHeaders(false)
    .crossfade(false)
    .build()
