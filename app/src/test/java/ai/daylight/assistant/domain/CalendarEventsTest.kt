package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class CalendarEventsTest {
    private val oslo = ZoneId.of("Europe/Oslo")

    @Test fun utcEventBecomesAnInstant() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Dentist
            DTSTART:20261102T140000Z
            DTEND:20261102T150000Z
            LOCATION:Clinic
            DESCRIPTION:Bring forms
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val event = CalendarEvents.parse(ics, oslo).single()
        assertThat(event.title).isEqualTo("Dentist")
        assertThat(event.location).isEqualTo("Clinic")
        assertThat(event.description).isEqualTo("Bring forms")
        assertThat(event.allDay).isFalse()
        assertThat(event.startMillis).isEqualTo(instant(2026, 11, 2, 14, 0))
        assertThat(event.endMillis).isEqualTo(instant(2026, 11, 2, 15, 0))
    }

    @Test fun hyphenatedUtcAndMissingEndStillOpen() {
        val ics = """
            BEGIN:VEVENT
            SUMMARY:Call
            DTSTART:2026-11-02T15:30:00Z
            END:VEVENT
        """.trimIndent()
        val event = CalendarEvents.parse(ics, oslo).single()
        assertThat(event.startMillis).isEqualTo(instant(2026, 11, 2, 15, 30))
        assertThat(event.endMillis).isEqualTo(instant(2026, 11, 2, 16, 30))
    }

    @Test fun zonedAndAllDayEventsUseTheDeviceZone() {
        val ics = """
            BEGIN:VEVENT
            SUMMARY:Standup
            DTSTART;TZID=Europe/Oslo:20261102T090000
            DTEND;TZID=Europe/Oslo:20261102T093000
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Holiday
            DTSTART;VALUE=DATE:20261103
            END:VEVENT
        """.trimIndent()
        val events = CalendarEvents.parse(ics, oslo)
        assertThat(events).hasSize(2)
        assertThat(events[0].startMillis).isEqualTo(
            LocalDateTime.of(2026, 11, 2, 9, 0).atZone(oslo).toInstant().toEpochMilli()
        )
        assertThat(events[1].allDay).isTrue()
        assertThat(events[1].startMillis).isEqualTo(
            LocalDate.of(2026, 11, 3).atStartOfDay(oslo).toInstant().toEpochMilli()
        )
        assertThat(events[1].endMillis - events[1].startMillis).isEqualTo(86_400_000L)
    }

    @Test fun foldedDescriptionIsUnescaped() {
        val ics = "BEGIN:VEVENT\nSUMMARY:Notes\nDESCRIPTION:Line one\\nLine\\, two \n continues\nDTSTART:20261102T140000Z\nEND:VEVENT"
        val event = CalendarEvents.parse(ics, ZoneOffset.UTC).single()
        assertThat(event.description).isEqualTo("Line one\nLine, two continues")
    }

    @Test fun offsetStampKeepsItsZone() {
        val ics = """
            BEGIN:VEVENT
            SUMMARY:Offset
            DTSTART:20261102T150000+0100
            DTEND:20261102T160000+0100
            END:VEVENT
        """.trimIndent()
        val event = CalendarEvents.parse(ics, oslo).single()
        assertThat(event.startMillis).isEqualTo(instant(2026, 11, 2, 14, 0))
        assertThat(event.endMillis).isEqualTo(instant(2026, 11, 2, 15, 0))
    }

    @Test fun proseIsNotAnEvent() {
        assertThat(CalendarEvents.parse("Dentist at 3 tomorrow", oslo)).isEmpty()
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
}
