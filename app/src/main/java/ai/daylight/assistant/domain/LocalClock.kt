package ai.daylight.assistant.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class ClockSnapshot(
    val isoDate: String,
    val isoTime: String,
    val weekday: String,
    val timezone: String,
    val utcOffset: String,
    val unixMs: Long
)

object LocalClock {
    fun snapshot(
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ClockSnapshot {
        val zdt = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return ClockSnapshot(
            isoDate = zdt.toLocalDate().toString(),
            isoTime = DateTimeFormatter.ISO_LOCAL_TIME.format(zdt.toLocalTime().truncatedTo(ChronoUnit.SECONDS)),
            weekday = zdt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            timezone = zone.id,
            utcOffset = zdt.offset.id,
            unixMs = nowMillis
        )
    }
}
