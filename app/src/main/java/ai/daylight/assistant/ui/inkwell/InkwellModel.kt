package ai.daylight.assistant.ui.inkwell

import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.RecentOutput
import ai.daylight.assistant.ui.library.LibraryModel
import ai.daylight.assistant.ui.run.FileCardModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Inkwell's filter segments. [noun] names one piece on the ink strip ("9 IMAGES"). */
enum class InkFilter(val label: String, val noun: String) {
    ALL("All", "PIECE"),
    IMAGES("Images", "IMAGE"),
    CLIPS("Clips", "CLIP"),
    FILES("Files", "FILE");

    fun matches(output: GeneratedOutput): Boolean = when (this) {
        ALL -> true
        IMAGES -> output.kind == OutputKind.IMAGE
        CLIPS -> output.kind == OutputKind.VIDEO
        FILES -> output.kind != OutputKind.IMAGE && output.kind != OutputKind.VIDEO
    }
}

/** One line of the Inkwell feed: a day header, one or two media tiles side by side, or a file. */
sealed interface InkRow {
    val key: String

    data class Header(val label: String, override val key: String) : InkRow
    data class Media(val items: List<RecentOutput>, override val key: String) : InkRow
    data class File(val item: RecentOutput, override val key: String) : InkRow
}

/**
 * The Inkwell (C10): one time-ordered feed of everything Kryzz has made, across all arms.
 * Pure, so the grouping, captions and the Home badge are unit tested.
 */
object InkwellModel {
    /** Answers read for the feed. Each can carry several files; older ink stays in its chat. */
    const val ANSWER_LIMIT = 500

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** A stable key for one file of one answer (an answer can carry several files). */
    fun key(item: RecentOutput): String = "${item.messageId}/${item.output.id}"

    /** Images and clips that exist as a file or a link are drawn as tiles; the rest as file rows. */
    fun isTile(output: GeneratedOutput): Boolean =
        (output.kind == OutputKind.IMAGE || output.kind == OutputKind.VIDEO) &&
            (output.localPath != null || output.remoteUrl != null)

    /**
     * The feed for [filter]: a header per day ("TODAY", "( 23 ) OCT"), then the day's ink in
     * order, newest first. Runs of images and clips pair up two to a row; files take a row.
     */
    fun rows(items: List<RecentOutput>, filter: InkFilter, now: Long, zone: ZoneId): List<InkRow> {
        val out = ArrayList<InkRow>()
        items.filter { filter.matches(it.output) }
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .forEach { (day, list) ->
                out += InkRow.Header(LibraryModel.dayLabel(list.first().createdAt, now, zone), "h-$day")
                var pending: RecentOutput? = null
                fun flush() {
                    pending?.let { out += InkRow.Media(listOf(it), "m-${key(it)}") }
                    pending = null
                }
                for (item in list) {
                    if (isTile(item.output)) {
                        val first = pending
                        if (first == null) {
                            pending = item
                        } else {
                            out += InkRow.Media(listOf(first, item), "m-${key(first)}")
                            pending = null
                        }
                    } else {
                        flush()
                        out += InkRow.File(item, "f-${key(item)}")
                    }
                }
                flush()
            }
        return out
    }

    /** "IMAGE", "CLIP", or the file type ("DOCX"). */
    fun kindLabel(output: GeneratedOutput): String = when (output.kind) {
        OutputKind.IMAGE -> "IMAGE"
        OutputKind.VIDEO -> "CLIP"
        else -> FileCardModel.extension(output)
    }

    /** A tile's caption: "09:12 · IMAGE" (the day is in the header above). */
    fun caption(item: RecentOutput, zone: ZoneId): String =
        "${CLOCK.format(Instant.ofEpochMilli(item.createdAt).atZone(zone))} · ${kindLabel(item.output)}"

    /** The sheet's label: "IMAGE · 26 OCT 09:12". */
    fun sheetLabel(item: RecentOutput, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(item.createdAt).atZone(zone)
        return "${kindLabel(item.output)} · ${DAY_MONTH.format(at).uppercase(Locale.ENGLISH)} ${CLOCK.format(at)}"
    }

    /**
     * What a piece is called: the request for an image or clip (what the user asked for),
     * the file's own title for a document.
     */
    fun title(item: RecentOutput): String {
        val output = item.output
        val own = output.title.trim().ifBlank { output.fileName }
        return if (output.kind == OutputKind.IMAGE || output.kind == OutputKind.VIDEO) item.prompt ?: own else own
    }

    /** "( 18 ) · 9 IMAGES · 2 CLIPS · 7 FILES", skipping kinds there are none of. */
    fun summary(items: List<RecentOutput>): String {
        val images = items.count { it.output.kind == OutputKind.IMAGE }
        val clips = items.count { it.output.kind == OutputKind.VIDEO }
        val files = items.size - images - clips
        val parts = listOfNotNull(
            plural(images, "IMAGE").takeIf { images > 0 },
            plural(clips, "CLIP").takeIf { clips > 0 },
            plural(files, "FILE").takeIf { files > 0 },
        )
        return (listOf("( ${items.size} )") + parts).joinToString(" · ")
    }

    private fun plural(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}S"

    /** The newest piece's time, which becomes the seen mark once the Inkwell has shown it. */
    fun newest(items: List<RecentOutput>): Long? = items.maxOfOrNull { it.createdAt }

    /** Pieces made after the seen mark: Home's badge. Never opened (0) counts everything. */
    fun newCount(items: List<RecentOutput>, seenAt: Long): Int = items.count { it.createdAt > seenAt }

    /** The badge text: nothing at 0, then 1 to 9, then "9+". */
    fun badge(count: Int): String = when {
        count <= 0 -> ""
        count > 9 -> "9+"
        else -> count.toString()
    }

    /**
     * A NEW tag on pieces made since the last visit. On the first visit ([seenAtOnOpen] 0)
     * nothing is tagged, or every piece would be.
     */
    fun isNew(item: RecentOutput, seenAtOnOpen: Long?): Boolean =
        seenAtOnOpen != null && seenAtOnOpen > 0L && item.createdAt > seenAtOnOpen

    /** The empty state's dot words and sentence for a filter. */
    fun emptyWords(filter: InkFilter): Pair<String, String> = when (filter) {
        InkFilter.ALL -> "No ink yet" to "Images, clips and files Kryzz makes land here, newest first."
        InkFilter.IMAGES -> "No images" to "Images from Make and Run show up here."
        InkFilter.CLIPS -> "No clips" to "Clips from Make show up here."
        InkFilter.FILES -> "No files" to "Documents, sheets and code from Run show up here."
    }
}
