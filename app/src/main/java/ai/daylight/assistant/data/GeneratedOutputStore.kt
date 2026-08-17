package ai.daylight.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import java.io.File
import java.util.UUID

class GeneratedOutputStore(context: Context) {
    private val root = File(context.filesDir, "generated_outputs")

    fun saveBase64(encoded: String, extension: String): File {
        val file = newFile(extension)
        file.outputStream().use { it.write(Base64.decode(encoded, Base64.DEFAULT)) }
        return file
    }

    fun materialize(output: GeneratedOutput): GeneratedOutput {
        val content = output.content ?: return output
        return when (output.kind) {
            OutputKind.DOCUMENT -> {
                val file = saveBytes(OfficeFileGenerator.document(content), "docx")
                output.copy(fileName = output.fileName.substringBeforeLast('.') + ".docx", mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document", localPath = file.absolutePath)
            }
            OutputKind.SPREADSHEET -> {
                val file = saveBytes(OfficeFileGenerator.spreadsheet(content), "xlsx")
                output.copy(fileName = output.fileName.substringBeforeLast('.') + ".xlsx", mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", localPath = file.absolutePath)
            }
            OutputKind.DATABASE -> materializeDatabase(output, content)
            else -> output
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
