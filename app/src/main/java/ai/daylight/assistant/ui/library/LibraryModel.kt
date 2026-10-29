package ai.daylight.assistant.ui.library

import ai.daylight.assistant.data.local.ConversationSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One block of the chat library: a header ("PINNED", "TODAY", "( 23 ) OCT") and its chats. */
data class LibraryGroup(val key: String, val label: String, val items: List<ConversationSummary>)

/**
 * The chat library's words (components.md section 11), kept pure so they are unit tested:
 * date headers in the kit's bracket style, a mono caption per row ("2H AGO · RUN") and the
 * empty-state words.
 */
object LibraryModel {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    /** Within this many hours a row says "2H AGO"; later on the same day it shows the clock. */
    private const val RELATIVE_HOURS = 6

    private fun localDate(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    /**
     * The arm a chat belongs to: CREW for a bot's chat, MAKE for an image or clip render,
     * RUN for agent work, otherwise TALK (voice sessions are Talk chats).
     */
    fun armLabel(item: ConversationSummary): String = when {
        item.botName != null -> "CREW"
        item.lastMode == "AGENT" && item.lastCapability in MEDIA_CAPABILITIES -> "MAKE"
        item.lastMode == "AGENT" -> "RUN"
        else -> "TALK"
    }

    private val MEDIA_CAPABILITIES = setOf("IMAGE", "VIDEO")

    /** A date header: "TODAY", "YESTERDAY", "( 23 ) OCT", or "( 23 ) OCT 2025" in another year. */
    fun dayLabel(at: Long, now: Long, zone: ZoneId): String {
        val then = localDate(at, zone)
        val today = localDate(now, zone)
        val days = ChronoUnit.DAYS.between(then, today)
        return when {
            days <= 0 -> "TODAY"
            days == 1L -> "YESTERDAY"
            then.year == today.year -> "( ${then.dayOfMonth} ) ${MONTH.format(then).uppercase(Locale.ENGLISH)}"
            else -> "( ${then.dayOfMonth} ) ${MONTH_YEAR.format(then).uppercase(Locale.ENGLISH)}"
        }
    }

    /**
     * When a row was last active. Today it reads "JUST NOW", "5 MIN AGO" or "2H AGO", then
     * the clock. On an older day it is the clock, because the header above holds the date;
     * [withDate] (pinned rows, which have no date header) gives "YESTERDAY" or "26 OCT".
     */
    fun timeLabel(at: Long, now: Long, zone: ZoneId, withDate: Boolean = false): String {
        val then = Instant.ofEpochMilli(at).atZone(zone)
        val today = localDate(now, zone)
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        val minutes = ((now - at) / 60_000L).coerceAtLeast(0L)
        return when {
            days <= 0 && minutes < 1 -> "JUST NOW"
            days <= 0 && minutes < 60 -> "$minutes MIN AGO"
            days <= 0 && minutes < RELATIVE_HOURS * 60 -> "${minutes / 60}H AGO"
            days <= 0 || !withDate -> CLOCK.format(then)
            days == 1L -> "YESTERDAY"
            then.year == today.year -> DAY_MONTH.format(then).uppercase(Locale.ENGLISH)
            else -> MONTH_YEAR.format(then).uppercase(Locale.ENGLISH)
        }
    }

    /** The mono caption under a row's title: "2H AGO · RUN". */
    fun caption(item: ConversationSummary, now: Long, zone: ZoneId, withDate: Boolean = false): String =
        "${timeLabel(item.updatedAt, now, zone, withDate)} · ${armLabel(item)}"

    /**
     * Pinned chats first under PINNED, then the rest newest first, one block per day. The
     * order inside a block is kept as given (newest first).
     */
    fun groups(items: List<ConversationSummary>, now: Long, zone: ZoneId): List<LibraryGroup> {
        val out = ArrayList<LibraryGroup>()
        val pinned = items.filter { it.pinned }
        if (pinned.isNotEmpty()) out += LibraryGroup("pinned", "PINNED", pinned)
        items.filterNot { it.pinned }
            .sortedByDescending { it.updatedAt }
            .groupBy { localDate(it.updatedAt, zone) }
            .forEach { (day, chats) ->
                out += LibraryGroup("day-$day", dayLabel(chats.first().updatedAt, now, zone), chats)
            }
        return out
    }

    /** The count beside the CHATS title in the kit's bracket style, "( 24 )"; nothing when empty. */
    fun countCaption(count: Int): String = if (count <= 0) "" else "( $count )"

    /** The empty state's dot words and sentence: no matches, an empty folder, or no chats at all. */
    fun emptyWords(query: String, folderName: String?): Pair<String, String> = when {
        query.isNotBlank() -> "No matches" to "Try another word from the title or the chat."
        folderName != null -> "Empty folder" to "Move a chat here from its actions."
        else -> "No chats yet" to "New conversations land here, newest first."
    }
}
