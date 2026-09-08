package ai.daylight.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import java.io.File
import java.util.UUID
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
            OutputKind.PDF -> materializeOffice(
                output, content, "pdf", "application/pdf",
                "txt", "text/plain"
            ) { OfficeFileGenerator.pdf(it) }
            OutputKind.DATABASE -> materializeDatabase(output, content)
            else -> output
        }
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
}
