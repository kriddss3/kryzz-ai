package ai.daylight.assistant.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import ai.daylight.assistant.domain.ChatAttachment
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AttachmentStore(private val context: Context) {
    private val directory = File(context.filesDir, "chat-attachments").apply { mkdirs() }

    suspend fun importUris(uris: List<Uri>): List<ChatAttachment> = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "Choose at least one file." }
        require(uris.size <= MAX_ATTACHMENTS) { "Attach up to $MAX_ATTACHMENTS files at a time." }
        var total = 0L
        val imported = mutableListOf<ChatAttachment>()
        try {
            uris.distinct().forEach { uri ->
                currentCoroutineContext().ensureActive()
                val metadata = metadata(uri)
                if (metadata.size != null && metadata.size > MAX_FILE_BYTES) {
                    throw IllegalArgumentException("${metadata.name} is larger than ${MAX_FILE_BYTES / MIB} MB.")
                }
                val id = UUID.randomUUID().toString()
                val extension = metadata.name.substringAfterLast('.', "").take(10).filter { it.isLetterOrDigit() }
                val destination = File(directory, id + extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty())
                var copied = 0L
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > MAX_FILE_BYTES || total + copied > MAX_TOTAL_BYTES) {
                                throw IllegalArgumentException("Attachments are limited to ${MAX_FILE_BYTES / MIB} MB each and ${MAX_TOTAL_BYTES / MIB} MB total.")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw IllegalArgumentException("Android could not open ${metadata.name}.")
                total += copied
                imported += ChatAttachment(id, metadata.name, metadata.mimeType, copied, destination.absolutePath)
            }
            imported
        } catch (failure: Throwable) {
            imported.forEach { it.localPath?.let(::File)?.delete() }
            throw failure
        }
    }

    fun clear() {
        directory.listFiles()?.forEach(File::delete)
    }

    suspend fun base64(attachment: ChatAttachment): String = withContext(Dispatchers.IO) {
        val path = attachment.localPath ?: throw IllegalArgumentException("${attachment.name} is no longer available on this device.")
        val file = File(path)
        require(file.isFile && file.length() <= MAX_FILE_BYTES) { "${attachment.name} is unavailable or too large." }
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }

    private fun metadata(uri: Uri): Metadata {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val cleanName = name.orEmpty().trim().take(160).ifBlank { uri.lastPathSegment?.substringAfterLast('/').orEmpty().take(160).ifBlank { "attachment" } }
        val mime = context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase().orEmpty()
            .ifBlank { mimeFromName(cleanName) }
        return Metadata(cleanName, mime, size)
    }

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    private data class Metadata(val name: String, val mimeType: String, val size: Long?)

    companion object {
        private const val MIB = 1_048_576L
        const val MAX_ATTACHMENTS = 8
        const val MAX_FILE_BYTES = 32 * MIB
        const val MAX_TOTAL_BYTES = 64 * MIB
    }
}
