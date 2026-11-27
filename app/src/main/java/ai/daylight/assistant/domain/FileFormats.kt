package ai.daylight.assistant.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * v5.13.7: what `create_artifact` makes from a file name, the way a command line writes files.
 *
 * Up to 5.13.6 the tool took a `type` out of five (document, spreadsheet, database, pdf,
 * calendar), so a request for slides, a web page, a JSON export or a Python script had no
 * file to land in, and the model told the user it "can't make that kind of file". Now the
 * model names the file and the extension decides:
 *
 *  - [Converted]: text the phone turns into a real binary file. Markdown becomes .pdf, .docx
 *    or .pptx, CSV becomes .xlsx, SQL becomes .sqlite, and iCalendar text becomes an .ics.
 *  - [Text]: any other extension is written exactly as given (.html, .json, .py, .svg, .vcf…).
 *  - [Zip]: a .zip built from a list of files, each of them converted by the same rules.
 *  - Pictures, sound, video and other binaries cannot be written from text; [refusal] says
 *    which tool or text format to use instead, and the model gets that as the tool error.
 *
 * Pure, so the rules are unit tested (FileFormatsTest).
 */
internal object FileFormats {

    const val MAX_CONTENT_CHARS = 500_000
    const val MAX_ZIP_FILES = 60
    const val MAX_ZIP_CHARS = 1_500_000
    /** Files one turn may produce through create_artifact; one more is refused. */
    const val MAX_FILES_PER_TURN = 8
    private const val MAX_STEM_CHARS = 80
    private const val MAX_PATH_CHARS = 180
    private val BOM = Char(0xFEFF)

    sealed interface Format {
        val extension: String
        val mime: String
    }

    /** The phone renders the text into a binary file (or, for calendars, normalises it). */
    data class Converted(val kind: OutputKind, override val extension: String, override val mime: String) : Format

    /** Saved byte for byte as UTF-8 text. */
    data class Text(override val extension: String, override val mime: String) : Format

    data object Zip : Format {
        override val extension = "zip"
        override val mime = "application/zip"
    }

    /** One checked file request, ready for GeneratedOutputStore.writeFile. */
    data class FileRequest(
        val fileName: String,
        val title: String,
        val format: Format,
        /** The normalised text to write or convert; for a zip, a one-line description. */
        val content: String,
        val entries: List<ZipEntryRequest> = emptyList()
    ) {
        val kind: OutputKind get() = kindOf(format)
    }

    data class ZipEntryRequest(val path: String, val format: Format, val content: String)

    val PDF = Converted(OutputKind.PDF, "pdf", "application/pdf")
    val DOCX = Converted(OutputKind.DOCUMENT, "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    val XLSX = Converted(OutputKind.SPREADSHEET, "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    val PPTX = Converted(OutputKind.PRESENTATION, "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    val SQLITE = Converted(OutputKind.DATABASE, "sqlite", "application/vnd.sqlite3")
    val ICS = Converted(OutputKind.CALENDAR, "ics", "text/calendar")

    private val converted: Map<String, Converted> = mapOf(
        "pdf" to PDF,
        "docx" to DOCX, "doc" to DOCX,
        "xlsx" to XLSX, "xls" to XLSX, "xlsm" to XLSX,
        "pptx" to PPTX, "ppt" to PPTX,
        "sqlite" to SQLITE, "sqlite3" to SQLITE, "db" to SQLITE,
        "ics" to ICS, "ical" to ICS, "ifb" to ICS
    )

    /** Text MIME types the phone knows; anything else is text/plain. */
    private val textMimes: Map<String, String> = mapOf(
        "txt" to "text/plain", "text" to "text/plain", "log" to "text/plain",
        "md" to "text/markdown", "markdown" to "text/markdown",
        "html" to "text/html", "htm" to "text/html", "xhtml" to "application/xhtml+xml",
        "css" to "text/css",
        "js" to "text/javascript", "mjs" to "text/javascript", "cjs" to "text/javascript",
        "json" to "application/json", "geojson" to "application/geo+json",
        "xml" to "text/xml", "rss" to "application/rss+xml", "atom" to "application/atom+xml",
        "svg" to "image/svg+xml",
        "csv" to "text/csv", "tsv" to "text/tab-separated-values",
        "yaml" to "application/yaml", "yml" to "application/yaml",
        "vcf" to "text/x-vcard", "vcard" to "text/x-vcard",
        "gpx" to "application/gpx+xml",
        "kml" to "application/vnd.google-earth.kml+xml",
        "rtf" to "application/rtf",
        "tex" to "application/x-tex",
        "srt" to "application/x-subrip", "vtt" to "text/vtt",
        "sql" to "application/sql",
        "sh" to "application/x-sh",
        "py" to "text/x-python"
    )

    private val imageExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff", "heic", "heif", "avif", "ico", "psd", "raw", "dng"
    )
    private val audioExtensions = setOf(
        "mp3", "wav", "m4a", "aac", "ogg", "oga", "flac", "opus", "wma", "amr", "aiff", "mid", "midi"
    )
    private val videoExtensions = setOf("mp4", "m4v", "mov", "avi", "mkv", "webm", "3gp", "wmv", "flv", "mpg", "mpeg")
    private val archiveExtensions = setOf("7z", "rar", "gz", "tgz", "tar", "bz2", "xz", "zst", "jar", "war")
    private val openDocumentExtensions = mapOf(
        "odt" to "docx", "pages" to "docx", "ods" to "xlsx", "numbers" to "xlsx", "odp" to "pptx", "key" to "pptx"
    )
    private val binaryExtensions = setOf(
        "exe", "dll", "so", "bin", "msi", "dmg", "iso", "img", "deb", "rpm", "apk", "aab", "ipa", "class", "o", "a",
        "lib", "pyc", "wasm", "ttf", "otf", "woff", "woff2", "eot", "glb", "fbx", "blend", "sketch", "fig", "epub", "mobi"
    )

    /** The extension in lower case without the dot ("" when there is none, as in Makefile or .env). */
    fun extensionOf(fileName: String): String {
        val name = fileName.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** Why [extension] cannot be written from text, with what to use instead; null when it can. */
    fun refusal(extension: String): String? {
        val ext = extension.lowercase()
        return when {
            ext in imageExtensions -> "A .$ext picture cannot be written as text, and retrying it will fail again. " +
                "Call generate_image if it is offered, then put the returned embed_in_pdf line on its own line in the PDF. " +
                "For an icon, logo, diagram or chart write an .svg file instead (it opens in any browser)."
            ext in audioExtensions -> "A .$ext sound file cannot be written as text. Call generate_audio if it is offered, " +
                "or write the lyrics, script or notes as a text file."
            ext in videoExtensions -> "A .$ext video cannot be written as text. Call generate_video if it is offered, " +
                "or write the script or storyboard as a document."
            ext in archiveExtensions -> "Use a .zip file with the files list instead of .$ext."
            ext in openDocumentExtensions -> "Use .${openDocumentExtensions.getValue(ext)} instead of .$ext; " +
                "LibreOffice, Pages, Numbers and Keynote all open it."
            ext in binaryExtensions -> "A .$ext file is binary and cannot be written as text. Write the source " +
                "(for example .py, .kt, .html or .md) or a .zip of the project instead."
            else -> null
        }
    }

    /** The format for [extension]; null when [refusal] has a reason. */
    fun formatFor(extension: String): Format? {
        val ext = extension.lowercase()
        if (refusal(ext) != null) return null
        if (ext == "zip") return Zip
        converted[ext]?.let { return it }
        return Text(ext, textMimes[ext] ?: "text/plain")
    }

    fun kindOf(format: Format): OutputKind = when (format) {
        is Converted -> format.kind
        is Text -> OutputKind.TEXT
        Zip -> OutputKind.ARCHIVE
    }

    /**
     * A safe single file name: the last path segment, unsafe characters replaced, a lower-case
     * extension, and the converted extension (".doc" becomes ".docx"). [title] names a file
     * the model left unnamed; [defaultExtension] is used when the name has none.
     */
    fun cleanFileName(raw: String?, title: String?, defaultExtension: String? = null): String {
        val segment = raw.orEmpty().trim().replace('\\', '/').substringAfterLast('/').trim()
        val ext = extensionOf(segment)
        var stem = if (ext.isEmpty()) segment else segment.dropLast(ext.length + 1)
        stem = safeStem(stem)
        val dotFile = ext.isEmpty() && segment.startsWith('.') && stem.isNotEmpty()
        if (stem.isEmpty() || stem.all { it == '.' || it == '-' }) stem = safeStem(title.orEmpty()).ifEmpty { "kryzz-file" }
        val finalExt = (ext.ifEmpty { if (dotFile) "" else defaultExtension.orEmpty() })
            .lowercase().filter(Char::isLetterOrDigit).take(10)
        val normalisedExt = (converted[finalExt]?.extension ?: finalExt)
        return if (normalisedExt.isEmpty()) stem else "$stem.$normalisedExt"
    }

    private fun safeStem(value: String): String = value
        .replace(Regex("[\\u0000-\\u001F/\\\\:*?\"<>|]"), "-")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('-', ' ')
        .take(MAX_STEM_CHARS)
        .trim()

    private val extensionlessNames = setOf(
        "makefile", "dockerfile", "license", "licence", "readme", "procfile", "gemfile", "rakefile",
        "jenkinsfile", "vagrantfile", "codeowners", "authors", "changelog", "notice", "brewfile"
    )

    /** Makefile, LICENSE and dot files (.env, .gitignore) are named without an extension. */
    fun keepsNoExtension(name: String): Boolean {
        val last = name.trim().replace('\\', '/').substringAfterLast('/')
        return last.startsWith('.') || last.lowercase() in extensionlessNames
    }

    /** Default extension for the old `type` argument, so an old-style call still lands. */
    fun extensionForType(type: String?): String? = when (ArtifactKinds.kindFor(type)) {
        OutputKind.DOCUMENT -> "docx"
        OutputKind.SPREADSHEET -> "xlsx"
        OutputKind.DATABASE -> "sqlite"
        OutputKind.PDF -> "pdf"
        OutputKind.CALENDAR -> "ics"
        OutputKind.PRESENTATION -> "pptx"
        else -> when (type?.trim()?.lowercase()) {
            null, "" -> null
            "html", "web page", "webpage", "website" -> "html"
            "json" -> "json"
            "svg" -> "svg"
            "zip", "archive" -> "zip"
            "code", "script" -> "txt"
            else -> type.trim().lowercase().takeIf { it.length <= 10 && it.all(Char::isLetterOrDigit) }
        }
    }

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Checks a create_artifact call in the file-name form and returns the request, or throws
     * [IllegalArgumentException] with a message the model can act on.
     */
    fun request(arguments: String): FileRequest {
        val args = runCatching { lenientJson.parseToJsonElement(arguments) as JsonObject }
            .getOrElse { throw IllegalArgumentException("create_artifact needs a JSON object with file_name and content.") }
        val title = args.text("title")?.trim()?.take(120)
        val files = (args["files"] as? JsonArray).orEmpty().mapNotNull { element ->
            val file = element as? JsonObject ?: return@mapNotNull null
            val path = file.text("path") ?: file.text("file_name") ?: file.text("name") ?: return@mapNotNull null
            path to (file.text("content") ?: "")
        }
        val rawName = args.text("file_name") ?: args.text("filename") ?: args.text("name")
        val defaultExtension = when {
            rawName != null && (extensionOf(rawName).isNotEmpty() || keepsNoExtension(rawName)) -> null
            files.isNotEmpty() -> "zip"
            else -> extensionForType(args.text("type")) ?: "txt"
        }
        val fileName = cleanFileName(rawName, title, defaultExtension)
        val extension = extensionOf(fileName)
        refusal(extension)?.let { throw IllegalArgumentException(it) }
        val format = formatFor(extension) ?: throw IllegalArgumentException("Kryzz cannot write .$extension files.")
        val displayTitle = title?.takeIf(String::isNotBlank) ?: fileName.substringBeforeLast('.').ifBlank { fileName }
        val content = args.text("content").orEmpty()
        if (format == Zip) {
            require(files.isNotEmpty()) { "A .zip needs the files list: each entry has a path (with its extension) and its content." }
            return FileRequest(fileName, displayTitle, Zip, content.trim().take(600), zipEntries(files))
        }
        require(content.isNotBlank()) { "The file $fileName has no content. Send the complete file in content." }
        require(content.length <= MAX_CONTENT_CHARS) { "The file $fileName is too large (over $MAX_CONTENT_CHARS characters). Split it into smaller files." }
        return FileRequest(fileName, displayTitle, format, normalize(format, content, fileName))
    }

    private fun zipEntries(files: List<Pair<String, String>>): List<ZipEntryRequest> {
        require(files.size <= MAX_ZIP_FILES) { "A .zip can hold at most $MAX_ZIP_FILES files." }
        require(files.sumOf { it.second.length } <= MAX_ZIP_CHARS) { "The .zip content is too large. Split it into smaller files." }
        val entries = files.map { (rawPath, content) ->
            val path = rawPath.trim().replace('\\', '/').trim('/')
            require(path.isNotBlank() && path.length <= MAX_PATH_CHARS) { "A file path in the .zip is empty or too long: $rawPath" }
            require(!path.contains(':')) { "Paths in a .zip must be relative: $rawPath" }
            val segments = path.split('/')
            require(segments.none { it.isBlank() || it == "." || it == ".." }) { "Paths in a .zip cannot leave the folder: $rawPath" }
            val name = cleanFileName(segments.last(), null)
            val ext = extensionOf(name)
            refusal(ext)?.let { throw IllegalArgumentException("$path: $it") }
            val format = formatFor(ext) ?: throw IllegalArgumentException("$path: Kryzz cannot write .$ext files.")
            require(format != Zip) { "$path: a .zip cannot contain another .zip." }
            require('\u0000' !in content) { "$path: binary content is not supported." }
            val cleanPath = (segments.dropLast(1) + name).joinToString("/")
            ZipEntryRequest(cleanPath, format, normalize(format, content, cleanPath))
        }
        require(entries.map { it.path.lowercase() }.distinct().size == entries.size) { "File paths in a .zip must be unique." }
        return entries
    }

    /** Strips a wrapping code fence, converts Markdown tables in CSV and checks a few formats. */
    fun normalize(format: Format, content: String, fileName: String): String = when (format) {
        is Converted -> {
            val text = ArtifactKinds.normalizeContent(format.kind, content)
            require(format.kind != OutputKind.CALENDAR || ArtifactKinds.calendarIsUsable(text)) {
                "$fileName: a calendar file needs BEGIN:VCALENDAR, a VEVENT with SUMMARY and DTSTART, and END:VCALENDAR."
            }
            require(format.kind != OutputKind.DATABASE || Regex("(?i)\\bCREATE\\s+TABLE\\b").containsMatchIn(text)) {
                "$fileName: a SQLite database needs at least one CREATE TABLE statement."
            }
            text
        }
        is Text -> {
            var text = ArtifactKinds.stripOuterFence(content.trim(BOM).trimEnd()).trimStart('\n', '\r')
            when (format.extension) {
                "csv" -> text = ArtifactKinds.normalizeContent(OutputKind.SPREADSHEET, text)
                "svg" -> require(text.contains("<svg", ignoreCase = true)) {
                    "$fileName: an .svg must be SVG markup with an <svg> element."
                }
            }
            text
        }
        Zip -> content
    }

    /** A note for the model when a text file does not parse as its format (the file is still saved). */
    fun warning(format: Format, content: String): String? {
        if (format !is Text) return null
        return when (format.extension) {
            "json", "geojson" -> if (runCatching { Json.parseToJsonElement(content) }.isSuccess) null else
                "The content is not valid JSON; fix it with another create_artifact call using the same file_name."
            else -> null
        }
    }

    /** Line endings some formats require: vCards and Windows scripts use CRLF. */
    fun bytesOf(format: Text, content: String): ByteArray {
        val text = when (format.extension) {
            "vcf", "vcard", "bat", "cmd" -> content.replace("\r\n", "\n").replace("\n", "\r\n")
            else -> content
        }
        val bytes = text.toByteArray(Charsets.UTF_8)
        // Excel reads a CSV without a byte-order mark as ANSI, which garbles accents.
        val needsBom = format.extension in setOf("csv", "tsv") && text.any { it.code > 127 }
        return if (needsBom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes else bytes
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
}
