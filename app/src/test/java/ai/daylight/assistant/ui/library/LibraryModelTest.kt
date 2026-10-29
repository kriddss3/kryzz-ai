package ai.daylight.assistant.ui.library

import ai.daylight.assistant.data.local.ConversationSummary
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** C10: the chat library's date headers, row captions and grouping. */
class LibraryModelTest {
    private val zone = ZoneId.of("Europe/Riga")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private val now = at(2026, 10, 29, 18, 0)

    private fun chat(
        id: String,
        updatedAt: Long,
        pinned: Boolean = false,
        mode: String? = "CHAT",
        capability: String? = null,
        bot: String? = null,
    ) = ConversationSummary(
        id = id, title = "Chat $id", createdAt = updatedAt, updatedAt = updatedAt, archived = false,
        searchSessionId = null, searchDepth = 0, searchWidth = 0, preview = null, pinned = pinned,
        lastMode = mode, lastCapability = capability, botName = bot
    )

    @Test fun dayHeadersUseTheKitBracketStyle() {
        assertThat(LibraryModel.dayLabel(at(2026, 10, 29, 0, 5), now, zone)).isEqualTo("TODAY")
        assertThat(LibraryModel.dayLabel(at(2026, 10, 28, 23, 59), now, zone)).isEqualTo("YESTERDAY")
        assertThat(LibraryModel.dayLabel(at(2026, 10, 23), now, zone)).isEqualTo("( 23 ) OCT")
        assertThat(LibraryModel.dayLabel(at(2026, 1, 2), now, zone)).isEqualTo("( 2 ) JAN")
        assertThat(LibraryModel.dayLabel(at(2025, 10, 23), now, zone)).isEqualTo("( 23 ) OCT 2025")
        // A clock running slightly ahead still counts as today.
        assertThat(LibraryModel.dayLabel(now + 60_000, now, zone)).isEqualTo("TODAY")
    }

    @Test fun rowTimesAreRelativeTodayThenTheClock() {
        assertThat(LibraryModel.timeLabel(now - 20_000, now, zone)).isEqualTo("JUST NOW")
        assertThat(LibraryModel.timeLabel(now - 5 * 60_000, now, zone)).isEqualTo("5 MIN AGO")
        assertThat(LibraryModel.timeLabel(now - 2 * 3_600_000 - 60_000, now, zone)).isEqualTo("2H AGO")
        assertThat(LibraryModel.timeLabel(at(2026, 10, 29, 9, 12), now, zone)).isEqualTo("09:12")
        // An older day shows the clock: its date is in the header above.
        assertThat(LibraryModel.timeLabel(at(2026, 10, 23, 7, 5), now, zone)).isEqualTo("07:05")
    }

    @Test fun pinnedRowsCarryTheirDate() {
        assertThat(LibraryModel.timeLabel(at(2026, 10, 28, 7, 5), now, zone, withDate = true)).isEqualTo("YESTERDAY")
        assertThat(LibraryModel.timeLabel(at(2026, 10, 26), now, zone, withDate = true)).isEqualTo("26 OCT")
        assertThat(LibraryModel.timeLabel(at(2025, 3, 26), now, zone, withDate = true)).isEqualTo("MAR 2025")
        assertThat(LibraryModel.timeLabel(now - 5 * 60_000, now, zone, withDate = true)).isEqualTo("5 MIN AGO")
    }

    @Test fun captionNamesTheArm() {
        assertThat(LibraryModel.caption(chat("a", now - 2 * 3_600_000), now, zone)).isEqualTo("2H AGO · TALK")
        assertThat(LibraryModel.armLabel(chat("b", now, mode = "AGENT", capability = "AUTO"))).isEqualTo("RUN")
        assertThat(LibraryModel.armLabel(chat("c", now, mode = "AGENT", capability = "IMAGE"))).isEqualTo("MAKE")
        assertThat(LibraryModel.armLabel(chat("d", now, mode = "AGENT", capability = "VIDEO"))).isEqualTo("MAKE")
        assertThat(LibraryModel.armLabel(chat("e", now, mode = "CHAT", bot = "Scout"))).isEqualTo("CREW")
        // A chat with no question yet (or an old row) reads as Talk.
        assertThat(LibraryModel.armLabel(chat("f", now, mode = null))).isEqualTo("TALK")
    }

    @Test fun pinnedFirstThenOneBlockPerDayNewestFirst() {
        val list = listOf(
            chat("pin-old", at(2026, 9, 1), pinned = true),
            chat("today-2", at(2026, 10, 29, 17)),
            chat("today-1", at(2026, 10, 29, 9)),
            chat("yesterday", at(2026, 10, 28, 20)),
            chat("oct-23", at(2026, 10, 23)),
        )
        val groups = LibraryModel.groups(list, now, zone)
        assertThat(groups.map { it.label }).containsExactly("PINNED", "TODAY", "YESTERDAY", "( 23 ) OCT").inOrder()
        assertThat(groups.map { g -> g.items.map { it.id } }).containsExactly(
            listOf("pin-old"), listOf("today-2", "today-1"), listOf("yesterday"), listOf("oct-23")
        ).inOrder()
        // Every chat appears exactly once, and every block has its own key.
        assertThat(groups.flatMap { it.items }.map { it.id }).containsNoDuplicates()
        assertThat(groups.map { it.key }).containsNoDuplicates()
        assertThat(LibraryModel.groups(emptyList(), now, zone)).isEmpty()
    }

    @Test fun countAndEmptyWords() {
        assertThat(LibraryModel.countCaption(0)).isEmpty()
        assertThat(LibraryModel.countCaption(1)).isEqualTo("( 1 )")
        assertThat(LibraryModel.countCaption(24)).isEqualTo("( 24 )")
        assertThat(LibraryModel.emptyWords("ferry", null).first).isEqualTo("No matches")
        assertThat(LibraryModel.emptyWords("", "Work").first).isEqualTo("Empty folder")
        assertThat(LibraryModel.emptyWords("", null).first).isEqualTo("No chats yet")
    }
}
