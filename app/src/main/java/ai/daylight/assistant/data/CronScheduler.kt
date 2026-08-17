package ai.daylight.assistant.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** How often a wall-clock cron task repeats. */
enum class CronRecurrence(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    /** Kept so existing monthly jobs still run; new jobs can no longer pick it. */
    MONTHLY("Monthly");

    companion object {
        val selectable = listOf(DAILY, WEEKLY)

        fun from(value: String?): CronRecurrence =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DAILY
    }
}

/** Weekday labels are ISO-aligned: index 0 = Monday … index 6 = Sunday. */
val CRON_DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/**
 * Schedule helpers for cron tasks.
 *
 * Tasks created by 4.7.0 carry an exact time of day (hour/minute) plus a daily,
 * weekly, or monthly recurrence. Tasks created before 4.7.0 have no time and keep
 * their old "every N minutes" behaviour ([CronSchedulePresets.isLegacy]).
 */
object CronSchedulePresets {

    /** Labels for the old pre-4.7.0 interval schedules, kept so legacy rows still read clearly. */
    fun label(minutes: Long): String = when (minutes) {
        15L -> "Every 15 minutes"
        60L -> "Every hour"
        360L -> "Every 6 hours"
        1440L -> "Every 24 hours"
        else -> "Every $minutes minutes"
    }

    fun isLegacy(task: ScheduledTaskEntity): Boolean = task.hourOfDay == null

    fun recurrenceOf(task: ScheduledTaskEntity): CronRecurrence = CronRecurrence.from(task.recurrence)

    fun encodeDays(days: Collection<Int>): String =
        days.filter { it in 1..7 }.distinct().sorted().joinToString(",")

    /** ISO weekdays (1 = Monday … 7 = Sunday) a weekly task should fire on. */
    fun daysOfWeek(task: ScheduledTaskEntity): Set<Int> {
        val encoded = task.daysOfWeek
        if (!encoded.isNullOrBlank()) {
            return encoded.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
        }
        return task.dayOfWeek?.takeIf { it in 1..7 }?.let { setOf(it) } ?: emptySet()
    }

    fun weekdayLabel(days: Collection<Int>): String {
        val sorted = days.filter { it in 1..7 }.distinct().sorted()
        if (sorted.isEmpty()) return "Every week"
        if (sorted.size == 7) return "Every day"
        return sorted.joinToString(", ") { CRON_DAY_NAMES[it - 1] }
    }

    /** Human schedule line for a task, e.g. "Daily at 07:30" or "Monday, Wednesday at 07:30". */
    fun label(task: ScheduledTaskEntity): String {
        if (isLegacy(task)) return label(task.intervalMinutes)
        val time = String.format(Locale.getDefault(), "%02d:%02d", task.hourOfDay ?: 0, task.minuteOfHour ?: 0)
        return when (recurrenceOf(task)) {
            CronRecurrence.DAILY -> "Daily at $time"
            CronRecurrence.WEEKLY -> {
                val day = weekdayLabel(daysOfWeek(task))
                "$day at $time"
            }
            CronRecurrence.MONTHLY -> {
                val day = task.dayOfMonth?.coerceIn(1, 31) ?: 1
                "On day $day at $time, every month"
            }
        }
    }

    /**
     * The next wall-clock run time for a time-of-day task (strictly after [fromMillis]),
     * or null for legacy interval tasks.
     */
    fun nextRunAt(task: ScheduledTaskEntity, fromMillis: Long = System.currentTimeMillis()): Long? {
        if (task.hourOfDay == null) return null
        val hour = task.hourOfDay.coerceIn(0, 23)
        val minute = (task.minuteOfHour ?: 0).coerceIn(0, 59)
        val recurrence = recurrenceOf(task)
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Walk day by day (bounded) until the next matching time-of-day occurrence.
        repeat(370 * 2) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            if (cal.timeInMillis <= fromMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                return@repeat
            }
            val matches = when (recurrence) {
                CronRecurrence.DAILY -> true
                CronRecurrence.WEEKLY -> {
                    val days = daysOfWeek(task)
                    val iso = isoDayOfWeek(cal)
                    if (days.isEmpty()) iso == (task.dayOfWeek ?: iso) else iso in days
                }
                CronRecurrence.MONTHLY -> cal.get(Calendar.DAY_OF_MONTH) == (task.dayOfMonth ?: cal.get(Calendar.DAY_OF_MONTH))
            }
            if (matches) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    /** Converts Calendar.DAY_OF_WEEK (1 = Sunday) to ISO (1 = Monday … 7 = Sunday). */
    fun isoDayOfWeek(cal: Calendar): Int = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
}

/**
 * Enqueues and cancels the unique work behind each scheduled task. Work is named per
 * task so updates replace the old request instead of stacking duplicates.
 *
 * Wall-clock tasks use a self-rescheduling one-time chain: the next occurrence is
 * computed, scheduled with an exact initial delay, and [CronWorker] re-arms the one
 * after that when it fires. Legacy interval tasks keep WorkManager periodic chains.
 */
class CronScheduler(private val context: Context) {

    fun schedule(task: ScheduledTaskEntity) {
        if (CronSchedulePresets.isLegacy(task)) {
            scheduleLegacy(task)
        } else {
            scheduleNextOccurrence(task)
        }
    }

    /**
     * Enqueues the next wall-clock occurrence of [task]. Called by the worker after a
     * run, and by the UI whenever a task is created or enabled.
     */
    fun scheduleNextOccurrence(task: ScheduledTaskEntity) {
        val next = CronSchedulePresets.nextRunAt(task) ?: run {
            // No computable occurrence (malformed task); fall back to the old periodic path.
            scheduleLegacy(task)
            return
        }
        val delay = (next - System.currentTimeMillis()).coerceAtLeast(MIN_INITIAL_DELAY_MS)
        val request = OneTimeWorkRequestBuilder<CronWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(CronWorker.KEY_TASK_ID to task.id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(task.id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleLegacy(task: ScheduledTaskEntity) {
        val request = PeriodicWorkRequestBuilder<CronWorker>(
            task.intervalMinutes.coerceAtLeast(15L), TimeUnit.MINUTES
        )
            .setInputData(workDataOf(CronWorker.KEY_TASK_ID to task.id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(workName(task.id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
    }

    /** One-off run from the "Run now" button; does not disturb the scheduled chain. */
    fun runNow(taskId: String) {
        val request = OneTimeWorkRequestBuilder<CronWorker>()
            .setInputData(workDataOf(CronWorker.KEY_TASK_ID to taskId))
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    private fun workName(taskId: String) = "cron_$taskId"

    companion object {
        const val WORK_TAG = "cron"

        /** Guards against an immediately re-firing chain when a run completes on its own minute. */
        const val MIN_INITIAL_DELAY_MS = 1_000L
    }
}
