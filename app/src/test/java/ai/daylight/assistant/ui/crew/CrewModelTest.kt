package ai.daylight.assistant.ui.crew

import ai.daylight.assistant.data.CronRecurrence
import ai.daylight.assistant.data.RoutineDraft
import ai.daylight.assistant.data.local.BotEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.domain.BotWork
import ai.daylight.assistant.domain.BotWorkKind
import ai.daylight.assistant.domain.MessageStatus
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

class CrewModelTest {
    private val zone = ZoneId.of("UTC")
    private val savedZone = TimeZone.getDefault()

    // Next runs are computed on the phone's clock (CronSchedulePresets uses Calendar), so the
    // test pins the default zone to the one its times are written in.
    @Before fun pinZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    @After fun restoreZone() = TimeZone.setDefault(savedZone)
    private fun at(day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val now = at(29, 10, 24) // Thu 29 Oct 2026, 10:24

    private fun routine(
        id: String,
        recurrence: CronRecurrence = CronRecurrence.DAILY,
        hour: Int = 7,
        days: Set<Int> = emptySet(),
        botId: String? = "scout",
        prompt: String = "Read my feeds and write the brief.",
        enabled: Boolean = true,
        lastRunAt: Long? = null,
    ): ScheduledTaskEntity =
        RoutineDraft("Brief $id", prompt, recurrence, hour, 0, days).toTask(id, "chat", botId, now = 1)
            .copy(enabled = enabled, lastRunAt = lastRunAt)

    private fun msg(id: String, role: String, text: String, time: Long, status: MessageStatus = MessageStatus.COMPLETE) =
        MessageEntity(id, "chat", role, text, time, status = status.name)

    @Test fun statusSaysWorkFirstThenNextRunThenLastRun() {
        val daily = routine("r1")
        val routineWork = BotWork("scout", BotWorkKind.ROUTINE, now, "r1", "Morning brief")
        assertThat(CrewModel.status(listOf(daily), routineWork, now, zone)).isEqualTo(BotStatus(BotStatusKind.RUNNING, "Running Morning brief"))
        assertThat(CrewModel.status(listOf(daily), BotWork("scout", BotWorkKind.REPLY, now), now, zone).text).isEqualTo("Writing a reply")
        // 07:00 already passed today, so the next run is tomorrow.
        assertThat(CrewModel.status(listOf(daily), null, now, zone)).isEqualTo(BotStatus(BotStatusKind.NEXT, "Next run Fri 07:00"))
        assertThat(CrewModel.status(listOf(routine("r2", hour = 18)), null, now, zone).text).isEqualTo("Next run 18:00")
        val paused = routine("r3", enabled = false, lastRunAt = at(29, 9, 40))
        assertThat(CrewModel.status(listOf(paused), null, now, zone)).isEqualTo(BotStatus(BotStatusKind.IDLE, "Last run 09:40"))
        assertThat(CrewModel.status(listOf(routine("r4", enabled = false)), null, now, zone).text).isEqualTo("Routines paused")
        assertThat(CrewModel.status(emptyList(), null, now, zone).text).isEqualTo("Chat only, no routine")
    }

    @Test fun routinePillsReadLikeTheKit() {
        assertThat(CrewModel.routinePill(routine("a"))).isEqualTo("Daily 07:00")
        assertThat(CrewModel.routinePill(routine("b", CronRecurrence.WEEKLY, 18, setOf(7)))).isEqualTo("Weekly, Sun 18:00")
        assertThat(CrewModel.routinePill(routine("c", CronRecurrence.WEEKLY, 8, setOf(1, 2, 3, 4, 5)))).isEqualTo("Weekdays 08:00")
        assertThat(CrewModel.routinePill(routine("d", CronRecurrence.WEEKLY, 9, setOf(1, 3)))).isEqualTo("Mon, Wed 09:00")
        assertThat(CrewModel.routinePill(routine("e", CronRecurrence.WEEKLY, 9, setOf(6, 7)))).isEqualTo("Weekends 09:00")
        assertThat(CrewModel.routinePill(routine("f", CronRecurrence.WEEKLY, 9, (1..7).toSet()))).isEqualTo("Daily 09:00")
        // A routine from before 4.7.0 has no time, only an interval.
        val legacy = ScheduledTaskEntity("old", "Old", "p", 60, "chat", createdAt = 1)
        assertThat(CrewModel.routinePill(legacy)).isEqualTo("Every hour")
    }

    @Test fun runsAreTheRoutinesPromptsWithTheirReplies() {
        val r = routine("r1")
        val messages = listOf(
            msg("u0", "USER", "hello", at(28, 6, 0)),
            msg("a0", "ASSISTANT", "Hi.", at(28, 6, 1)),
            msg("u1", "USER", r.prompt, at(28, 7, 0)),
            msg("a1", "ASSISTANT", "Two feeds done. **One** item [1].", at(28, 7, 1)),
            msg("u2", "USER", r.prompt, at(29, 7, 0)),
            msg("a2", "ASSISTANT", "No reply", at(29, 7, 1), MessageStatus.ERROR),
        )
        val runs = CrewModel.runs(messages, listOf(r), busy = false)
        assertThat(runs.map { it.askedId }).containsExactly("u2", "u1").inOrder()
        assertThat(runs[0].state).isEqualTo(RunState.FAILED)
        assertThat(runs[1].state).isEqualTo(RunState.DONE)
        assertThat(runs[1].answerId).isEqualTo("a1")
        assertThat(runs[1].routineTitle).isEqualTo("Brief r1")
        assertThat(CrewModel.gist(runs[1].answer)).isEqualTo("Two feeds done. One item.")
    }

    @Test fun theNewestRunIsLiveOnlyWhileTheBotWorks() {
        val r = routine("r1")
        val asked = listOf(msg("u1", "USER", r.prompt, at(29, 7, 0)))
        assertThat(CrewModel.runs(asked, listOf(r), busy = true).single().state).isEqualTo(RunState.RUNNING)
        assertThat(CrewModel.runs(asked, listOf(r), busy = false).single().state).isEqualTo(RunState.FAILED)
        val streaming = asked + msg("a1", "ASSISTANT", "Reading", at(29, 7, 1), MessageStatus.STREAMING)
        assertThat(CrewModel.runs(streaming, listOf(r), busy = true).single().state).isEqualTo(RunState.RUNNING)
        // Left half-written after a stop.
        assertThat(CrewModel.runs(streaming, listOf(r), busy = false).single().state).isEqualTo(RunState.STOPPED)
    }

    @Test fun typedMessagesAreNotRunsButRoutinePromptsAreMarked() {
        val r = routine("r1")
        val typed = msg("u1", "USER", "What did you find?", 1)
        assertThat(CrewModel.routineFor(typed, listOf(r))).isNull()
        assertThat(CrewModel.routineFor(msg("u2", "USER", "  ${r.prompt} ", 2), listOf(r))).isEqualTo(r)
        assertThat(CrewModel.routineFor(msg("a1", "ASSISTANT", r.prompt, 3), listOf(r))).isNull()
    }

    @Test fun runHeadingsAndTags() {
        assertThat(CrewModel.runHeading(at(29, 7, 0), now, zone)).isEqualTo("Today, 07:00 run")
        assertThat(CrewModel.runHeading(at(28, 7, 0), now, zone)).isEqualTo("Yesterday, 07:00 run")
        assertThat(CrewModel.runHeading(at(26, 7, 0), now, zone)).isEqualTo("Mon 26 Oct, 07:00 run")
        assertThat(CrewModel.runTag(RunState.RUNNING)).isEqualTo("[ LIVE ]")
        assertThat(CrewModel.runTag(RunState.DONE)).isEqualTo("[ DONE ]")
    }

    @Test fun whenLabelsWorkBothWays() {
        assertThat(CrewModel.whenLabel(at(29, 18, 0), now, zone)).isEqualTo("18:00")
        assertThat(CrewModel.whenLabel(at(1, 18, 0) + 31L * 86_400_000L, now, zone)).isEqualTo("Sun 18:00")
        assertThat(CrewModel.whenLabel(at(27, 9, 40), now, zone)).isEqualTo("Tue 09:40")
        assertThat(CrewModel.whenLabel(at(10, 9, 40), now, zone)).isEqualTo("10 Oct 09:40")
    }

    @Test fun routineTitlesComeFromTheFirstSentence() {
        assertThat(CrewModel.routineTitle("Read my feeds. Then write the brief.")).isEqualTo("Read my feeds")
        assertThat(CrewModel.routineTitle("Summarise the AI news from the last day into five plain lines"))
            .isEqualTo("Summarise the AI news from the…")
        assertThat(CrewModel.routineTitle("  ")).isEmpty()
    }

    @Test fun theWorkingBotIsFeaturedAndTheStripSaysWho() {
        val scout = BotEntity("scout", "Scout", "", 4, "c1", 1, 1)
        val pilot = BotEntity("pilot", "Pilot", "", 5, "c2", 2, 2)
        val bots = listOf(scout, pilot)
        assertThat(CrewModel.featured(bots, emptyMap())).isNull()
        val work = mapOf("pilot" to BotWork("pilot", BotWorkKind.ROUTINE, 1, "r", "Plan"))
        assertThat(CrewModel.featured(bots, work)).isEqualTo(pilot)
        assertThat(CrewModel.crewLine(bots, emptyMap()).text).isEqualTo("CREW READY")
        assertThat(CrewModel.crewLine(bots, work)).isEqualTo(ai.daylight.assistant.ui.shell.InkLine("CREW Pilot RUNNING", live = true))
        val both = work + ("scout" to BotWork("scout", BotWorkKind.REPLY, 1))
        assertThat(CrewModel.crewLine(bots, both).text).isEqualTo("CREW 2 BOTS WORKING")
        assertThat(CrewModel.botLine("Scout", null).text).isEqualTo("Scout READY")
        assertThat(CrewModel.botLine("Scout", both["scout"]).text).isEqualTo("Scout WRITING")
        assertThat(CrewModel.countCaption(0)).isEmpty()
        assertThat(CrewModel.countCaption(1)).isEqualTo("1 bot")
        assertThat(CrewModel.countCaption(4)).isEqualTo("4 bots")
    }

    @Test fun otherRoutinesAreTheOnesWithNoLivingBot() {
        val plain = routine("p", botId = null)
        val mine = routine("m", botId = "scout")
        val orphan = routine("o", botId = "deleted")
        assertThat(CrewModel.otherRoutines(listOf(plain, mine, orphan), setOf("scout"))).containsExactly(plain, orphan)
        assertThat(CrewModel.routinesOf("scout", listOf(plain, mine, orphan))).containsExactly(mine)
    }
}
