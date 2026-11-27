package ai.daylight.assistant.ui.run

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import ai.daylight.assistant.domain.CalendarEvents
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.PhoneCalendarEvent
import java.io.File

/**
 * Opens a calendar file in the phone's calendar editor, prefilled, so saving it
 * adds the event. Viewing the .ics is only the fallback: most calendar apps do
 * not import a content URI of type text/calendar, which is why the card used to
 * do nothing.
 */
internal fun openCalendarOnPhone(context: Context, output: GeneratedOutput): Boolean {
    val text = output.content?.takeIf { it.isNotBlank() }
        ?: output.localPath?.let { path -> runCatching { File(path).takeIf(File::isFile)?.readText() }.getOrNull() }
    val events = text?.let { CalendarEvents.parse(it) }.orEmpty()
    if (events.isNotEmpty()) {
        val first = events.first()
        val shown = if (events.size == 1) first else first.copy(
            description = buildString {
                if (first.description.isNotEmpty()) {
                    append(first.description)
                    append("\n\n")
                }
                append("This file also has: ")
                append(events.drop(1).joinToString(", ") { it.title })
            }
        )
        if (insertEvent(context, shown)) return true
    }
    return viewCalendarFile(context, output)
}

private fun insertEvent(context: Context, event: PhoneCalendarEvent): Boolean {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, event.title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (event.description.isNotEmpty()) intent.putExtra(CalendarContract.Events.DESCRIPTION, event.description)
    if (event.location.isNotEmpty()) intent.putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
    if (event.allDay) {
        intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        intent.putExtra(CalendarContract.Events.ALL_DAY, 1)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun viewCalendarFile(context: Context, output: GeneratedOutput): Boolean {
    val uri = output.localPath?.let { path ->
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", File(path)) }.getOrNull()
    } ?: output.remoteUrl?.let(Uri::parse) ?: return false
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply { clipData = ClipData.newRawUri(output.fileName, uri) }
    if (runCatching { context.startActivity(view) }.isSuccess) return true
    return runCatching {
        context.startActivity(Intent.createChooser(view, output.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
