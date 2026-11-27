package ai.daylight.assistant.ui.find

import ai.daylight.assistant.domain.FindSize
import ai.daylight.assistant.domain.FindState
import ai.daylight.assistant.domain.FindStatus
import ai.daylight.assistant.domain.FindWebState
import ai.daylight.assistant.domain.FindWebStatus
import ai.daylight.assistant.domain.FoundImage
import ai.daylight.assistant.domain.LicenceFilter
import ai.daylight.assistant.ui.shell.InkLine
import java.text.Normalizer

/** Find's words, kept out of the composables so they can be tested (C8). */
internal object FindModel {
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NOT_NAME = Regex("[^A-Za-z0-9]+")

    /** The strip: "FIND 12 IMAGES" like the mockup, "FIND SEARCHING" while a search runs. */
    fun inkLine(state: FindState, off: Boolean): InkLine = when {
        off -> InkLine("FIND OFF")
        state.status == FindStatus.SEARCHING -> InkLine("FIND SEARCHING", live = true)
        state.status == FindStatus.FAILED -> InkLine(if (state.offline) "FIND OFFLINE" else "FIND FAILED")
        state.status == FindStatus.DONE && state.images.isEmpty() -> InkLine("FIND NO MATCHES")
        state.status == FindStatus.DONE -> {
            val count = state.images.size
            InkLine("FIND $count ${state.filters.kind.noun(count)}".uppercase(), live = state.loadingMore)
        }
        else -> InkLine("FIND READY")
    }

    /** The strip for Web browsing mode. */
    fun webInkLine(state: FindWebState, off: Boolean): InkLine = when {
        off -> InkLine("FIND OFF")
        state.status == FindWebStatus.SEARCHING -> InkLine("FIND SEARCHING", live = true)
        state.status == FindWebStatus.SYNTHESIZING -> InkLine("FIND SYNTHESIZING", live = true)
        state.status == FindWebStatus.FAILED -> InkLine("FIND FAILED")
        state.status == FindWebStatus.DONE -> {
            val count = state.result?.sources?.size ?: 0
            if (count == 0) InkLine("FIND NO SOURCES")
            else InkLine("FIND $count ${if (count == 1) "SOURCE" else "SOURCES"}")
        }
        else -> InkLine("FIND WEB READY")
    }

    /** "12 shown" beside the filters (set in mono caps). */
    fun countCaption(count: Int): String = "$count shown"

    /** Every tile names its source and licence: "Commons, CC BY-SA 4.0". */
    fun tileCaption(image: FoundImage): String = "Commons, ${image.licence.shortName}"

    /** What TalkBack reads for a tile. */
    fun tileDescription(image: FoundImage): String = buildString {
        append(image.title)
        image.author?.let { append(", by ").append(it) }
        append(", ").append(image.licence.shortName).append(", from Wikimedia Commons")
    }

    /** Why a search came back empty, and what to change. */
    fun emptyBody(state: FindState): String {
        val words = "“${state.query}”"
        return when {
            state.filters.licence == LicenceFilter.REUSABLE && state.filteredOut > 0 ->
                "The first results for $words all use other licences. Choose Any licence to see them."
            state.filters.size != FindSize.ANY ->
                "Nothing on Commons matches $words at this size. Try Any size, or fewer words."
            else -> "Nothing on Commons matches $words. Try fewer or different words."
        }
    }

    /** The media type a saved file really has: the server's word, else what the file name says. */
    fun savedMime(served: String?, image: FoundImage): String {
        val clean = served?.substringBefore(';')?.trim()?.lowercase()
        return when {
            clean != null && clean.startsWith("image/") && clean != "image/svg+xml" -> clean
            image.mime == "image/svg+xml" -> "image/png"
            image.mime.startsWith("image/") -> image.mime
            else -> "image/jpeg"
        }
    }

    fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/tiff" -> "tif"
        else -> "jpg"
    }

    fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "tif", "tiff" -> "image/tiff"
        else -> "image/jpeg"
    }

    /** A gallery file name from the title: "Tayid-Lighthouse-at-Night.jpg". */
    fun displayName(image: FoundImage, ext: String): String {
        val plain = COMBINING_MARKS.replace(Normalizer.normalize(image.title, Normalizer.Form.NFD), "")
        val stem = NOT_NAME.replace(plain, "-").trim('-').take(60).trimEnd('-').ifEmpty { "commons-${image.id}" }
        return "$stem.$ext"
    }

    /**
     * Text EXIF can hold (ASCII only): accents are dropped, quotes and dashes made plain,
     * and anything else becomes "?". Used for the credit stamped into a saved JPEG.
     */
    fun asciiFold(text: String): String {
        val plain = COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        val out = StringBuilder(plain.length)
        for (ch in plain) {
            when (ch) {
                '“', '”', '„', '«', '»' -> out.append('"')
                '‘', '’', '‚' -> out.append('\'')
                '–', '—', '−' -> out.append('-')
                '…' -> out.append("...")
                '©' -> out.append("(c)")
                '\u00A0' -> out.append(' ')
                else -> out.append(if (ch.code in 32..126) ch else '?')
            }
        }
        return out.toString()
    }

    /** True when folding left the words readable (at most a fifth turned into "?"). */
    fun readableAfterFold(folded: String): Boolean {
        val letters = folded.count { !it.isWhitespace() }
        return letters > 0 && folded.count { it == '?' } * 5 <= letters
    }
}
