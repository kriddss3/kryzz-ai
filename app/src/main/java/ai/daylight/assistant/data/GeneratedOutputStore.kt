package ai.daylight.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import ai.daylight.assistant.domain.FileFormats
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeneratedOutputStore(context: Context) {
    private val root = File(context.filesDir, "generated_outputs")

    fun saveBase64(encoded: String, extension: String): File {
        val file = newFile(extension)
        file.outputStream().use { it.write(Base64.decode(encoded, Base64.DEFAULT)) }
        return file
    }

    /**
     * Converts the model's text deliverable into the real binary file (DOCX / XLSX /
     * PDF / SQLite). v5.7.1: runs on the IO dispatcher (the agent loop calls this on
     * the main thread otherwise) and never throws the deliverable away — if binary
     * conversion fails, the raw content is saved with its plain-text extension so the
     * user still gets a file instead of a dead turn.
     */
    suspend fun materialize(output: GeneratedOutput): GeneratedOutput = withContext(Dispatchers.IO) {
        val content = output.content ?: return@withContext output
        when (output.kind) {
            OutputKind.DOCUMENT -> materializeOffice(
                output, content, "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "md", "text/markdown"
            ) { OfficeFileGenerator.document(it) }
            OutputKind.SPREADSHEET -> materializeOffice(
                output, content, "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "csv", "text/csv"
            ) { OfficeFileGenerator.spreadsheet(it) }
            // v5.11: PdfDocument with system fonts first (full Unicode); the standard-14
            // writer is the fallback, and the raw text after that.
            OutputKind.PDF -> materializeOffice(
                output, content, "pdf", "application/pdf",
                "txt", "text/plain"
            ) { markdown -> runCatching { PdfDocumentRenderer.render(markdown) }.getOrElse { OfficeFileGenerator.pdf(markdown) } }
            OutputKind.DATABASE -> materializeDatabase(output, content)
            OutputKind.CALENDAR -> {
                val crlf = content.replace("\r\n", "\n").replace("\n", "\r\n")
                val file = saveBytes(crlf.toByteArray(Charsets.UTF_8), "ics")
                val stem = output.fileName.substringBeforeLast('.')
                output.copy(fileName = "$stem.ics", mimeType = "text/calendar", localPath = file.absolutePath)
            }
            else -> output
        }
    }

    /**
     * v5.13.7: writes a create_artifact file under its own name (generated_outputs/<id>/<name>),
     * so the name survives opening and sharing. Office, PDF and slide files fall back to their
     * source text if a generator fails; SQL that SQLite rejects is an [IllegalArgumentException]
     * the model can fix. Each file inside a .zip goes through the same conversion.
     */
    internal suspend fun writeFile(
        request: FileFormats.FileRequest,
        images: Map<String, ByteArray> = emptyMap()
    ): GeneratedOutput = withContext(Dispatchers.IO) {
        val dir = newDir()
        if (request.format == FileFormats.Zip) {
            val file = File(dir, request.fileName)
            val written = mutableListOf<String>()
            try {
                ZipOutputStream(file.outputStream().buffered()).use { zip ->
                    request.entries.forEach { entry ->
                        val rendered = runCatching { render(entry.format, entry.content, entry.path.substringAfterLast('/'), images) }
                            .getOrElse { sourceFallback(entry.format, entry.content) }
                        val path = if (rendered.extension != entry.format.extension) {
                            entry.path.substringBeforeLast('.') + "." + rendered.extension
                        } else entry.path
                        zip.putNextEntry(ZipEntry(path))
                        zip.write(rendered.bytes)
                        zip.closeEntry()
                        written += path
                    }
                }
            } catch (error: Throwable) {
                dir.deleteRecursively()
                throw error
            }
            val listing = buildString {
                if (request.content.isNotBlank()) append(request.content.trim()).append("\n")
                append("Contains ${written.size} file${if (written.size == 1) "" else "s"}:")
                written.take(80).forEach { append("\n- ").append(it) }
            }
            return@withContext GeneratedOutput(
                UUID.randomUUID().toString(), OutputKind.ARCHIVE, request.title, request.fileName, FileFormats.Zip.mime,
                content = listing, localPath = file.absolutePath
            )
        }
        val rendered = try {
            render(request.format, request.content, request.title, images)
        } catch (sql: IllegalArgumentException) {
            dir.deleteRecursively()
            throw sql
        } catch (_: Throwable) {
            sourceFallback(request.format, request.content)
        }
        val name = if (rendered.extension != request.format.extension) {
            request.fileName.substringBeforeLast('.') + "." + rendered.extension
        } else request.fileName
        val file = File(dir, name)
        file.outputStream().use { it.write(rendered.bytes) }
        GeneratedOutput(
            UUID.randomUUID().toString(), rendered.kind, request.title, name, rendered.mime,
            // The source rides along for backups and follow-up edits, capped so one chat row
            // stays well under the 2 MB a database cursor window can hold.
            content = request.content.take(STORED_SOURCE_CHARS),
            localPath = file.absolutePath
        )
    }

    private class Rendered(val bytes: ByteArray, val extension: String, val mime: String, val kind: OutputKind)

    private fun render(format: FileFormats.Format, content: String, title: String, images: Map<String, ByteArray>): Rendered = when (format) {
        is FileFormats.Text -> Rendered(FileFormats.bytesOf(format, content), format.extension, format.mime, OutputKind.TEXT)
        is FileFormats.Converted -> Rendered(convert(format.kind, content, title, images), format.extension, format.mime, format.kind)
        FileFormats.Zip -> throw IllegalStateException("A zip cannot be rendered inside another zip.")
    }

    private fun convert(kind: OutputKind, content: String, title: String, images: Map<String, ByteArray>): ByteArray = when (kind) {
        OutputKind.DOCUMENT -> OfficeFileGenerator.document(content)
        OutputKind.SPREADSHEET -> OfficeFileGenerator.spreadsheet(content)
        OutputKind.PDF -> runCatching { PdfDocumentRenderer.render(content, images) }.getOrElse { OfficeFileGenerator.pdf(content) }
        OutputKind.PRESENTATION -> PresentationGenerator.presentation(content, title)
        OutputKind.DATABASE -> databaseBytes(content)
        OutputKind.CALENDAR -> content.replace("\r\n", "\n").replace("\n", "\r\n").toByteArray(Charsets.UTF_8)
        else -> throw IllegalStateException("No converter for $kind.")
    }

    /** The model's source text, saved with its plain extension, when a generator failed. */
    private fun sourceFallback(format: FileFormats.Format, content: String): Rendered {
        val kind = FileFormats.kindOf(format)
        val (extension, mime) = when (kind) {
            OutputKind.DOCUMENT, OutputKind.PRESENTATION -> "md" to "text/markdown"
            OutputKind.SPREADSHEET -> "csv" to "text/csv"
            OutputKind.DATABASE -> "sql" to "application/sql"
            else -> "txt" to "text/plain"
        }
        return Rendered(content.toByteArray(Charsets.UTF_8), extension, mime, kind)
    }

    /** Runs the SQL in a scratch SQLite file and returns its bytes. */
    private fun databaseBytes(sql: String): ByteArray {
        val file = newFile("sqlite")
        try {
            val statements = sqlStatements(sql).filter { statement ->
                val normalized = statement.trimStart().uppercase()
                DATABASE_STATEMENTS.any(normalized::startsWith)
            }
            require(statements.any { it.trimStart().startsWith("CREATE TABLE", ignoreCase = true) }) {
                "The SQL has no CREATE TABLE statement."
            }
            try {
                SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                    db.beginTransaction()
                    try {
                        statements.forEach(db::execSQL)
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            } catch (error: android.database.SQLException) {
                throw IllegalArgumentException("SQLite rejected the SQL: ${error.message?.take(300)}. Fix it and call create_artifact again.")
            }
            return file.readBytes()
        } finally {
            file.delete()
            File(file.path + "-journal").delete()
        }
    }

    private fun newDir(): File {
        val dir = File(root, UUID.randomUUID().toString())
        check(dir.mkdirs() || dir.isDirectory) { "Could not create the private output directory." }
        return dir
    }

    private fun materializeOffice(
        output: GeneratedOutput,
        content: String,
        extension: String,
        mime: String,
        fallbackExtension: String,
        fallbackMime: String,
        generate: (String) -> ByteArray
    ): GeneratedOutput {
        val stem = output.fileName.substringBeforeLast('.')
        val bytes = runCatching { generate(content) }.getOrNull()
        return if (bytes != null) {
            val file = saveBytes(bytes, extension)
            output.copy(fileName = "$stem.$extension", mimeType = mime, localPath = file.absolutePath)
        } else {
            val file = saveBytes(content.toByteArray(Charsets.UTF_8), fallbackExtension)
            output.copy(fileName = "$stem.$fallbackExtension", mimeType = fallbackMime, localPath = file.absolutePath)
        }
    }

    private fun materializeDatabase(output: GeneratedOutput, sql: String): GeneratedOutput {
        val file = newFile("sqlite")
        val statements = sqlStatements(sql).filter { statement ->
            val normalized = statement.trimStart().uppercase()
            normalized.startsWith("CREATE TABLE") || normalized.startsWith("CREATE INDEX") ||
                normalized.startsWith("CREATE UNIQUE INDEX") || normalized.startsWith("INSERT INTO")
        }
        if (statements.none { it.trimStart().startsWith("CREATE TABLE", ignoreCase = true) }) return output
        return try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.beginTransaction()
                try {
                    statements.forEach(db::execSQL)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            output.copy(fileName = output.fileName.substringBeforeLast('.') + ".sqlite", mimeType = "application/vnd.sqlite3", localPath = file.absolutePath)
        } catch (_: Throwable) {
            file.delete()
            output
        }
    }

    private fun saveBytes(bytes: ByteArray, extension: String): File = newFile(extension).also { file ->
        file.outputStream().use { it.write(bytes) }
    }

    private fun sqlStatements(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var lineComment = false
        var index = 0
        while (index < sql.length) {
            val char = sql[index]
            val next = sql.getOrNull(index + 1)
            if (lineComment) {
                if (char == '\n') lineComment = false
            } else if (quote != null) {
                current.append(char)
                if (char == quote && next == quote) { current.append(next); index++ }
                else if (char == quote) quote = null
            } else when {
                char == '-' && next == '-' -> { lineComment = true; index++ }
                char == '\'' || char == '"' -> { quote = char; current.append(char) }
                char == ';' -> { current.toString().trim().takeIf(String::isNotEmpty)?.let(statements::add); current.clear() }
                else -> current.append(char)
            }
            index++
        }
        current.toString().trim().takeIf(String::isNotEmpty)?.let(statements::add)
        return statements
    }

    fun newFile(extension: String): File {
        if (!root.exists()) check(root.mkdirs() || root.exists()) { "Could not create the private output directory." }
        val cleanExtension = extension.lowercase().filter(Char::isLetterOrDigit).take(8).ifBlank { "bin" }
        return File(root, "${UUID.randomUUID()}.$cleanExtension")
    }

    fun clear() {
        if (root.exists()) root.deleteRecursively()
    }

    private companion object {
        /** Source text kept on a file output (backups, follow-up edits); the file itself is complete. */
        const val STORED_SOURCE_CHARS = 60_000
        val DATABASE_STATEMENTS = listOf(
            "CREATE TABLE", "CREATE INDEX", "CREATE UNIQUE INDEX", "CREATE VIEW", "INSERT INTO", "INSERT OR "
        )
    }
}
