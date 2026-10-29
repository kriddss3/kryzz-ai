package ai.daylight.assistant.data.remote

import ai.daylight.assistant.BuildConfig
import ai.daylight.assistant.domain.FindFilters
import ai.daylight.assistant.domain.FindKind
import ai.daylight.assistant.domain.FindPage
import ai.daylight.assistant.domain.FindSize
import ai.daylight.assistant.domain.FoundImage
import ai.daylight.assistant.domain.ImageLicence
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Find's only network destination (C8): the Wikimedia Commons API, which needs no key.
 * It searches the File namespace and returns each image with its author, licence and
 * source. Images come from Wikimedia's own media hosts only, over https.
 *
 * Wikimedia refuses generic HTTP clients (a bare "okhttp" user agent gets 403), so every
 * request carries [USER_AGENT], which names the app and where to reach its author.
 */
class CommonsImageClient(
    private val http: OkHttpClient = PublicWebClient.defaultClient(),
    private val json: Json = DEFAULT_JSON,
) {
    /**
     * The client for thumbnails and downloads: redirects are allowed, but every hop must
     * be https on a Wikimedia host, and the user agent is added to each request.
     */
    val mediaHttp: OkHttpClient by lazy {
        http.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(UserAgent)
            .addNetworkInterceptor(MediaHostGuard)
            .build()
    }

    private val downloadHttp: OkHttpClient by lazy {
        mediaHttp.newBuilder().callTimeout(3, TimeUnit.MINUTES).build()
    }

    /** One page of results. Throws [IOException] without a connection and [AssistantApiException] on an HTTP error. */
    fun search(query: String, filters: FindFilters, offset: Int = 0, limit: Int = PAGE_SIZE): FindPage {
        val request = Request.Builder()
            .url(searchUrl(query, filters, offset, limit))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code, "Commons search")
            val body = response.body?.string().orEmpty()
            if (body.length > MAX_BODY_CHARS) {
                throw AssistantApiException(ErrorKind.VALIDATION, "Commons sent more than Find can read.")
            }
            return parseSearch(body, json)
        }
    }

    /**
     * Downloads a Wikimedia media [url] into [target], at most [maxBytes]. Returns the
     * response's media type ("image/jpeg"), or null when the server named none.
     */
    fun download(url: String, target: File, maxBytes: Long = MAX_DOWNLOAD_BYTES): String? {
        val safe = requireMediaUrl(url)
        val request = Request.Builder().url(safe).get().build()
        downloadHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpError(response.code, "The download")
            val body = response.body ?: throw AssistantApiException(ErrorKind.NETWORK, "The download was empty.")
            if (body.contentLength() > maxBytes) {
                throw AssistantApiException(ErrorKind.VALIDATION, "The image is too large to save.")
            }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw AssistantApiException(ErrorKind.VALIDATION, "The image is too large to save.")
                        output.write(buffer, 0, read)
                    }
                }
            }
            return body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }
        }
    }

    private object UserAgent : Interceptor {
        override fun intercept(chain: Interceptor.Chain) =
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
    }

    /** Each hop, redirects included, must stay on a Wikimedia host over https. */
    private object MediaHostGuard : Interceptor {
        override fun intercept(chain: Interceptor.Chain) = chain.request().url.let { url ->
            if (!isMediaUrl(url)) throw IOException("Blocked a request outside Wikimedia: ${url.host}")
            chain.proceed(chain.request())
        }
    }

    companion object {
        const val API_HOST = "commons.wikimedia.org"
        const val PAGE_SIZE = 24
        const val THUMB_WIDTH = 500
        const val VIEW_WIDTH = 1280
        const val DRAWING_SAVE_WIDTH = 1920

        /** Wikimedia serves thumbnails at these widths only; any other width is refused (HTTP 400). */
        val STANDARD_WIDTHS = listOf(500, 960, 1280, 1920, 3840)

        /** Originals up to this size are saved as they are; larger ones as the biggest standard rendition. */
        const val MAX_ORIGINAL_BYTES = 25L * 1024 * 1024
        const val MAX_DOWNLOAD_BYTES = 40L * 1024 * 1024
        private const val MAX_BODY_CHARS = 2_000_000

        val USER_AGENT = "KryzzAI/${BuildConfig.VERSION_NAME} (https://github.com/kriddss3/kryzz-ai; Android app) okhttp/4.12"

        private val DEFAULT_JSON = Json { ignoreUnknownKeys = true; isLenient = true }
        private val METADATA = listOf(
            "ObjectName", "Artist", "Attribution", "LicenseShortName", "License", "LicenseUrl", "AttributionRequired"
        )
        private val DIRECT_MIMES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        private const val SVG = "image/svg+xml"
        private val WIDTH_IN_NAME = Regex("(?:^|-)(\\d+)px-")
        private val TAGS = Regex("(?s)<[^>]*>")
        private val BREAKS = Regex("(?i)<br\\s*/?>|</p>|</div>|</li>")
        private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
        private val SPACES = Regex("\\s+")
        private val EXTENSION = Regex("\\.[A-Za-z0-9]{2,5}$")

        /** The CirrusSearch text: the words plus the file type and size keywords. */
        fun searchText(query: String, filters: FindFilters): String {
            val words = SPACES.replace(query, " ").trim()
            val type = when (filters.kind) {
                FindKind.IMAGES -> "filetype:bitmap"
                FindKind.DRAWINGS -> "filetype:drawing"
            }
            val size = when (filters.size) {
                FindSize.ANY -> null
                FindSize.MEDIUM -> "fileres:1000,2000"
                FindSize.LARGE -> "fileres:>2000"
            }
            return listOfNotNull(words.takeIf { it.isNotEmpty() }, type, size).joinToString(" ")
        }

        fun searchUrl(query: String, filters: FindFilters, offset: Int = 0, limit: Int = PAGE_SIZE): HttpUrl =
            HttpUrl.Builder()
                .scheme("https")
                .host(API_HOST)
                .addPathSegments("w/api.php")
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2")
                .addQueryParameter("generator", "search")
                .addQueryParameter("gsrnamespace", "6")
                .addQueryParameter("gsrsearch", searchText(query, filters))
                .addQueryParameter("gsrlimit", limit.coerceIn(1, 50).toString())
                .apply { if (offset > 0) addQueryParameter("gsroffset", offset.toString()) }
                .addQueryParameter("prop", "imageinfo")
                .addQueryParameter("iiprop", "url|size|mime|extmetadata")
                .addQueryParameter("iiurlwidth", THUMB_WIDTH.toString())
                .addQueryParameter("iiextmetadatafilter", METADATA.joinToString("|"))
                .addQueryParameter("iiextmetadatalanguage", "en")
                .build()

        /** Parses one API response. Results keep Commons' ranking; anything not on a Wikimedia host is dropped. */
        fun parseSearch(body: String, json: Json = DEFAULT_JSON): FindPage {
            val root = json.parseToJsonElement(body) as? JsonObject
                ?: throw AssistantApiException(ErrorKind.UNKNOWN, "Commons sent an answer Find can't read.")
            (root["error"] as? JsonObject)?.let { error ->
                val info = error.string("info") ?: error.string("code") ?: "unknown error"
                throw AssistantApiException(ErrorKind.VALIDATION, "Commons couldn't search: $info")
            }
            val next = ((root["continue"] as? JsonObject)?.get("gsroffset") as? JsonPrimitive)?.intOrNull
            val pages = ((root["query"] as? JsonObject)?.get("pages") as? JsonArray) ?: return FindPage(emptyList(), next)
            val ranked = pages.mapNotNull { page -> (page as? JsonObject)?.let(::parsePage) }
            return FindPage(ranked.sortedBy { it.first }.map { it.second }, next)
        }

        private fun parsePage(page: JsonObject): Pair<Int, FoundImage>? {
            val fileTitle = page.string("title") ?: return null
            val id = (page["pageid"] as? JsonPrimitive)?.longOrNull ?: return null
            val rank = (page["index"] as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE
            val info = (page["imageinfo"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
            val original = info.string("url")?.takeIf(::isMediaUrl) ?: return null
            val thumb = info.string("thumburl")?.takeIf(::isMediaUrl) ?: original
            val mime = info.string("mime").orEmpty().lowercase()
            val width = info.int("width")
            val height = info.int("height")
            val bytes = (info["size"] as? JsonPrimitive)?.longOrNull ?: 0L
            val meta = info["extmetadata"] as? JsonObject ?: JsonObject(emptyMap())
            fun field(key: String): String? = ((meta[key] as? JsonObject)?.get("value") as? JsonPrimitive)?.contentOrNull

            val pageUrl = info.string("descriptionurl")?.takeIf(::isCommonsPage) ?: commonsPage(fileTitle)
            val title = plainText(field("ObjectName")).ifEmpty { titleFromFile(fileTitle) }
            val licence = ImageLicence.of(
                shortName = plainText(field("LicenseShortName")),
                code = field("License"),
                url = field("LicenseUrl"),
                attributionRequired = field("AttributionRequired")
            )
            val image = FoundImage(
                id = id,
                fileTitle = fileTitle,
                title = title,
                author = plainText(field("Artist")).ifEmpty { null },
                attribution = plainText(field("Attribution")).ifEmpty { null },
                licence = licence,
                pageUrl = pageUrl,
                originalUrl = original,
                thumbUrl = thumb,
                viewUrl = viewUrl(thumb, original, mime, width),
                saveUrl = saveUrl(thumb, original, mime, width, bytes),
                thumbWidth = info.int("thumbwidth"),
                thumbHeight = info.int("thumbheight"),
                width = width,
                height = height,
                bytes = bytes,
                mime = mime
            )
            return rank to image
        }

        /** The large view: the 1280px rendition, or the original when it is smaller and a phone can show it. */
        fun viewUrl(thumb: String, original: String, mime: String, width: Int): String = when {
            mime == SVG || width > VIEW_WIDTH -> widthUrl(thumb, VIEW_WIDTH) ?: thumb
            mime in DIRECT_MIMES -> original
            else -> thumb
        }

        /**
         * What Save and Share download: the original when it is a JPEG, PNG, WebP or GIF of
         * at most [MAX_ORIGINAL_BYTES], a drawing as a 1920px PNG, otherwise the largest
         * standard rendition narrower than the original.
         */
        fun saveUrl(thumb: String, original: String, mime: String, width: Int, bytes: Long): String = when {
            mime in DIRECT_MIMES && bytes in 1..MAX_ORIGINAL_BYTES -> original
            mime == SVG -> widthUrl(thumb, DRAWING_SAVE_WIDTH) ?: thumb
            else -> STANDARD_WIDTHS.filter { it < width }.maxOrNull()?.let { widthUrl(thumb, it) } ?: thumb
        }

        /**
         * The same thumbnail at another width: ".../thumb/a/ab/F.jpg/500px-F.jpg" becomes
         * ".../1280px-F.jpg". Null for a URL that isn't a thumbnail.
         */
        fun widthUrl(thumb: String, width: Int): String? {
            if (!thumb.contains("/thumb/")) return null
            val end = thumb.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) thumb.length else it }
            val start = thumb.lastIndexOf('/', end - 1) + 1
            if (start <= 0 || start >= end) return null
            // The width leads the file segment ("500px-F.jpg") or follows a prefix ("lossy-page1-500px-F.tif.jpg").
            val match = WIDTH_IN_NAME.find(thumb.substring(start, end)) ?: return null
            val range = match.groups[1]?.range ?: return null
            return thumb.replaceRange(start + range.first, start + range.last + 1, width.toString())
        }

        /** https on a Wikimedia host (upload, thumb, commons). Everything else is refused. */
        fun isMediaUrl(raw: String): Boolean = raw.trim().toHttpUrlOrNull()?.let(::isMediaUrl) ?: false

        fun isMediaUrl(url: HttpUrl): Boolean {
            val host = url.host.lowercase()
            return url.scheme == "https" && (host == "wikimedia.org" || host.endsWith(".wikimedia.org")) &&
                !PublicWebClient.isPrivateHost(host)
        }

        private fun isCommonsPage(raw: String): Boolean {
            val url = raw.trim().toHttpUrlOrNull() ?: return false
            return url.scheme == "https" && url.host.equals(API_HOST, ignoreCase = true)
        }

        private fun requireMediaUrl(raw: String): HttpUrl {
            val url = PublicWebClient.requireSafeHttpUrl(raw)
            if (!isMediaUrl(url)) throw AssistantApiException(ErrorKind.VALIDATION, "Find only downloads from Wikimedia.")
            return url
        }

        /** The description page for "File:Some name.jpg". */
        fun commonsPage(fileTitle: String): String = HttpUrl.Builder()
            .scheme("https")
            .host(API_HOST)
            .addPathSegment("wiki")
            .addPathSegment(fileTitle.trim().replace(' ', '_'))
            .build()
            .toString()

        /** "File:Tayid_Lighthouse at Night.jpg" -> "Tayid Lighthouse at Night". */
        fun titleFromFile(fileTitle: String): String {
            val name = fileTitle.substringAfter(':', fileTitle).replace('_', ' ')
            return SPACES.replace(EXTENSION.replace(name, ""), " ").trim()
        }

        /** Commons sends author and title fields as HTML; this keeps the words only. */
        fun plainText(html: String?): String {
            if (html.isNullOrBlank()) return ""
            val noTags = TAGS.replace(BREAKS.replace(html, " "), "")
            return SPACES.replace(decodeEntities(noTags), " ").trim()
        }

        private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
            val name = match.groupValues[1]
            when {
                name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED_ENTITIES[name.lowercase()]
            } ?: match.value
        }

        private fun codePoint(code: Int): String? =
            if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else null

        private val NAMED_ENTITIES = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
            "ndash" to "-", "mdash" to "-", "copy" to "(c)", "hellip" to "..."
        )

        private fun httpError(code: Int, what: String): AssistantApiException = when (code) {
            429 -> AssistantApiException(ErrorKind.RATE_LIMIT, "Wikimedia Commons is busy. Try again in a minute.", code)
            403 -> AssistantApiException(ErrorKind.NETWORK, "Wikimedia Commons refused the request (HTTP 403).", code)
            in 500..599 -> AssistantApiException(ErrorKind.NETWORK, "Wikimedia Commons is having trouble (HTTP $code). Try again soon.", code)
            else -> AssistantApiException(ErrorKind.NETWORK, "$what failed (HTTP $code).", code)
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
    }
}
