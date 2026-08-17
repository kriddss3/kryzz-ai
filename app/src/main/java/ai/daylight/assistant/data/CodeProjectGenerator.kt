package ai.daylight.assistant.data

import ai.daylight.assistant.data.remote.CodeProjectFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object CodeProjectGenerator {
    const val MAX_FILES = 120
    const val MAX_TOTAL_CHARS = 1_500_000

    fun validate(files: List<CodeProjectFile>): List<CodeProjectFile> {
        require(files.size in 2..MAX_FILES) { "A code project must contain between 2 and $MAX_FILES files." }
        require(files.sumOf { it.content.length } <= MAX_TOTAL_CHARS) { "The generated code project is too large." }
        val clean = files.map { file ->
            val path = file.path.trim().replace('\\', '/')
            require(path.isNotBlank() && path.length <= 180) { "A generated file path is invalid." }
            require(!path.startsWith('/') && !path.contains(':')) { "Absolute generated file paths are not allowed." }
            require(path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Generated file paths cannot traverse directories." }
            require('\u0000' !in file.content) { "Binary generated files are not supported." }
            CodeProjectFile(path, file.content)
        }
        require(clean.map { it.path.lowercase() }.distinct().size == clean.size) { "Generated file paths must be unique." }
        require(clean.any { it.path.substringAfterLast('/').equals("README.md", true) }) { "A complete code project must include README.md." }
        return clean
    }

    fun write(destination: File, files: List<CodeProjectFile>): File {
        val clean = validate(files)
        destination.parentFile?.mkdirs()
        ZipOutputStream(destination.outputStream().buffered()).use { zip ->
            clean.sortedBy { it.path }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.path))
                zip.write(file.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        ZipFile(destination).use { archive ->
            require(archive.entries().asSequence().count() == clean.size) { "The code project archive could not be verified." }
        }
        return destination
    }
}
