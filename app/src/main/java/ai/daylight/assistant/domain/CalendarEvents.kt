package ai.daylight.assistant.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One VEVENT from an iCalendar file, ready for the phone's calendar editor.
 * Times are epoch millis. An all-day event starts at local midnight and its end
 * is exclusive, matching both iCalendar and the calendar app.
 */
internal data class PhoneCalendarEvent(
    val title: String,
    val description: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
)

/**
 * Reads the first events out of an iCalendar body. Floating times (no Z and no
 * TZID) are interpreted in [zone], which on the phone is the device zone.
 */
internal object CalendarEvents {
    private val eventBlock = Regex("BEGIN:VEVENT(.*?)END:VEVENT", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tzidParam = Regex("""TZID="?([^";:]+)"?""", RegexOption.IGNORE_CASE)
    private val offsetSuffix = Regex("""([+-])(\d{2})(\d{2})$""")

    fun parse(ics: String, zone: ZoneId = ZoneId.systemDefault()): List<PhoneCalendarEvent> {
        val unfolded = unfold(ics)
        return eventBlock.findAll(unfolded).mapNotNull { match -> event(match.groupValues[1], zone) }.toList()
    }

    /** Joins RFC 5545 folded lines (a break followed by a space or tab). */
    fun unfold(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').replace(Regex("\n[ \t]"), "")

    private fun event(body: String, zone: ZoneId): PhoneCalendarEvent? {
        val props = properties(body)
        val title = unescape(props["SUMMARY"]?.value.orEmpty()).trim()
        val start = props["DTSTART"]?.let { parseStamp(it, zone) } ?: return null
        if (title.isEmpty()) return null
        val end = props["DTEND"]?.let { parseStamp(it, zone) }
        val endMillis = when {
            end != null && end.millis > start.millis -> end.millis
            start.allDay -> start.millis + 86_400_000L
            else -> start.millis + 3_600_000L
        }
        return PhoneCalendarEvent(
            title = title,
            description = unescape(props["DESCRIPTION"]?.value.orEmpty()).trim(),
            location = unescape(props["LOCATION"]?.value.orEmpty()).trim(),
            startMillis = start.millis,
            endMillis = endMillis,
            allDay = start.allDay,
        )
    }

    private data class Prop(val params: String, val value: String)

    private fun properties(body: String): Map<String, Prop> {
        val props = linkedMapOf<String, Prop>()
        body.lineSequence().forEach { raw ->
            val line = raw.trim()
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val head = line.substring(0, colon)
            val name = head.substringBefore(';').uppercase()
            if (name.isEmpty() || name == "BEGIN" || name == "END") return@forEach
            props[name] = Prop(params = head.substringAfter(';', ""), value = line.substring(colon + 1).trim())
        }
        return props
    }

    private data class Stamp(val millis: Long, val allDay: Boolean)

    private fun parseStamp(prop: Prop, zone: ZoneId): Stamp? {
        val compact = prop.value.replace("-", "").replace(":", "")
        val utc = compact.endsWith("Z", ignoreCase = true)
        val offset = if (utc) null else offsetSuffix.find(compact)
        val body = when {
            utc -> compact.dropLast(1)
            offset != null -> compact.removeRange(offset.range)
            else -> compact
        }
        val datePart = body.substringBefore('T')
        if (datePart.length != 8) return null
        val year = datePart.substring(0, 4).toIntOrNull() ?: return null
        val month = datePart.substring(4, 6).toIntOrNull() ?: return null
        val day = datePart.substring(6, 8).toIntOrNull() ?: return null
        val params = prop.params.uppercase()
        val allDay = "DATE-TIME" !in params && ("VALUE=DATE" in params || !body.contains('T'))
        val stampZone = when {
            allDay -> zone
            utc -> ZoneOffset.UTC
            offset != null -> {
                val sign = if (offset.groupValues[1] == "-") -1 else 1
                ZoneOffset.ofTotalSeconds(sign * (offset.groupValues[2].toInt() * 3600 + offset.groupValues[3].toInt() * 60))
            }
            else -> tzidParam.find(prop.params)?.groupValues?.get(1)?.trim()?.let { id ->
                runCatching { ZoneId.of(id) }.getOrNull()
            } ?: zone
        }
        return try {
            if (allDay) {
                Stamp(LocalDate.of(year, month, day).atStartOfDay(stampZone).toInstant().toEpochMilli(), true)
            } else {
                val time = body.substringAfter('T')
                if (time.length < 4) return null
                val hour = time.substring(0, 2).toInt()
                val minute = time.substring(2, 4).toInt()
                val second = if (time.length >= 6) time.substring(4, 6).toInt() else 0
                Stamp(
                    LocalDateTime.of(year, month, day, hour, minute, second).atZone(stampZone).toInstant().toEpochMilli(),
                    false,
                )
            }
        } catch (_: DateTimeException) {
            null
        }
    }

    /** Turns iCalendar escapes (`\n`, `\,`, `\;`, `\\`) back into text. */
    fun unescape(value: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < value.length) {
            if (value[index] == '\\' && index + 1 < value.length) {
                out.append(when (value[index + 1]) {
                    'n', 'N' -> '\n'
                    else -> value[index + 1]
                })
                index += 2
            } else {
                out.append(value[index])
                index++
            }
        }
        return out.toString()
    }
}
