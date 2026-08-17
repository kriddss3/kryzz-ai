package ai.daylight.assistant.data

import ai.daylight.assistant.data.local.ScheduledTaskEntity
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CronSchedulePresetsTest {

    private fun task(
        recurrence: CronRecurrence = CronRecurrence.DAILY,
        hour: Int? = 9,
        minute: Int? = 30,
        dayOfWeek: Int? = null,
        dayOfMonth: Int? = null,
        daysOfWeek: String? = null
    ) = ScheduledTaskEntity(
        id = "t", title = "T", prompt = "P", intervalMinutes = 1_440L,
        conversationId = "c", createdAt = 1L,
        recurrence = recurrence.name, hourOfDay = hour, minuteOfHour = minute,
        dayOfWeek = dayOfWeek, dayOfMonth = dayOfMonth, daysOfWeek = daysOfWeek
    )

    @Test
    fun legacyIntervalLabelsArePreserved() {
        assertEquals("Every 15 minutes", CronSchedulePresets.label(15L))
        assertEquals("Every hour", CronSchedulePresets.label(60L))
        assertEquals("Every 6 hours", CronSchedulePresets.label(360L))
        assertEquals("Every 24 hours", CronSchedulePresets.label(1440L))
        assertEquals("Every 45 minutes", CronSchedulePresets.label(45L))
    }

    @Test
    fun legacyTasksStayOnTheIntervalPath() {
        val legacy = ScheduledTaskEntity(
            id = "t", title = "T", prompt = "P", intervalMinutes = 60L,
            conversationId = "c", createdAt = 1L
        )
        assertTrue(CronSchedulePresets.isLegacy(legacy))
        assertNull(CronSchedulePresets.nextRunAt(legacy))
        assertEquals("Every hour", CronSchedulePresets.label(legacy))
    }

    @Test
    fun wallClockTasksAreNotLegacy() {
        assertTrue(!CronSchedulePresets.isLegacy(task()))
        assertEquals(CronRecurrence.DAILY, CronSchedulePresets.recurrenceOf(task()))
        assertEquals(CronRecurrence.WEEKLY, CronSchedulePresets.recurrenceOf(task(recurrence = CronRecurrence.WEEKLY)))
        assertEquals(CronRecurrence.MONTHLY, CronSchedulePresets.recurrenceOf(task(recurrence = CronRecurrence.MONTHLY)))
    }

    @Test
    fun scheduleLabelsReadClearly() {
        assertEquals("Daily at 09:30", CronSchedulePresets.label(task()))
        assertEquals("Monday at 09:30", CronSchedulePresets.label(task(CronRecurrence.WEEKLY, dayOfWeek = 1)))
        assertEquals("Sunday at 09:30", CronSchedulePresets.label(task(CronRecurrence.WEEKLY, dayOfWeek = 7)))
        assertEquals(
            "Monday, Wednesday, Friday at 09:30",
            CronSchedulePresets.label(task(CronRecurrence.WEEKLY, daysOfWeek = "1,3,5"))
        )
        assertEquals("On day 15 at 09:30, every month", CronSchedulePresets.label(task(CronRecurrence.MONTHLY, dayOfMonth = 15)))
        assertEquals(listOf(CronRecurrence.DAILY, CronRecurrence.WEEKLY), CronRecurrence.selectable)
    }

    @Test
    fun dailyNextRunIsTomorrowWhenTodayAlreadyPassed() {
        // From a wall-clock time later than the task time, the next run is tomorrow.
        val from = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 14, 11, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val next = CronSchedulePresets.nextRunAt(task(hour = 9, minute = 30), from)
        val cal = Calendar.getInstance().apply { timeInMillis = checkNotNull(next) }
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun dailyNextRunIsTodayWhenTimeStillAhead() {
        val from = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 14, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val next = CronSchedulePresets.nextRunAt(task(hour = 9, minute = 30), from)
        val cal = Calendar.getInstance().apply { timeInMillis = checkNotNull(next) }
        assertEquals(14, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun weeklyNextRunLandsOnTheChosenDay() {
        val from = Calendar.getInstance().apply {
            // Friday 2026-08-14
            set(2026, Calendar.AUGUST, 14, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // Monday (iso 1) after Friday is 2026-08-17.
        val next = CronSchedulePresets.nextRunAt(task(CronRecurrence.WEEKLY, dayOfWeek = 1), from)
        val cal = Calendar.getInstance().apply { timeInMillis = checkNotNull(next) }
        assertEquals(17, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun weeklyMultiDayPicksTheSoonestSelectedWeekday() {
        val from = Calendar.getInstance().apply {
            // Friday 2026-08-14 10:00 — Friday already passed the 09:30 slot.
            set(2026, Calendar.AUGUST, 14, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // Monday + Wednesday → next is Monday 17th.
        val next = CronSchedulePresets.nextRunAt(
            task(CronRecurrence.WEEKLY, daysOfWeek = "1,3"),
            from
        )
        val cal = Calendar.getInstance().apply { timeInMillis = checkNotNull(next) }
        assertEquals(17, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun monthlyNextRunLandsOnTheChosenDay() {
        val from = Calendar.getInstance().apply {
            // Friday 2026-08-14
            set(2026, Calendar.AUGUST, 14, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // Next day-of-month 20 after Aug 14 is Aug 20.
        val next = CronSchedulePresets.nextRunAt(task(CronRecurrence.MONTHLY, dayOfMonth = 20), from)
        val cal = Calendar.getInstance().apply { timeInMillis = checkNotNull(next) }
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    }
}
