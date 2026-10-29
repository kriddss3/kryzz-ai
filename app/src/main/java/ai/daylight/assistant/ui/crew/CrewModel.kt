package ai.daylight.assistant.ui.crew

import ai.daylight.assistant.data.CronRecurrence
import ai.daylight.assistant.data.CronSchedulePresets
import ai.daylight.assistant.data.local.BotEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.domain.BotWork
import ai.daylight.assistant.domain.BotWorkKind
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.ui.shell.InkLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How a bot's status line is marked (SCREENS.md 7a): yellow live ring, routine icon, hollow dot. */
enum class BotStatusKind { RUNNING, NEXT, IDLE }

/** One status line, in sentence case (the screen sets it in uppercase mono). */
data class BotStatus(val kind: BotStatusKind, val text: String)

/** How a run of a routine ended, read from the reply it left in the bot's chat. */
enum class RunState { RUNNING, DONE, FAILED, STOPPED }

/**
 * One routine run: the prompt the routine sent ([askedId]) and the reply under it. Runs are
 * found in the bot's own conversation, where routines send their prompt.
 */
data class BotRun(
    val askedId: String,
    val routineId: String,
    val routineTitle: String,
    val at: Long,
    val state: RunState,
    val answer: String?,
    val answerId: String?
)

/** Captions, status lines and runs for Crew and the bot page. Nothing here is invented. */
object CrewModel {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val SHORT_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun routinesOf(botId: String, all: List<ScheduledTaskEntity>): List<ScheduledTaskEntity> =
        all.filter { it.botId == botId }.sortedBy { it.createdAt }

    /** Routines with no bot (the ones made before Crew, or in Tune), plus any whose bot is gone. */
    fun otherRoutines(all: List<ScheduledTaskEntity>, botIds: Set<String>): List<ScheduledTaskEntity> =
        all.filter { it.botId == null || it.botId !in botIds }

    /**
     * The bot's status line: what it is doing now, else its next run, else its last run.
     * A bot with no routine is a chat-only bot and says so.
     */
    fun status(routines: List<ScheduledTaskEntity>, work: BotWork?, now: Long, zone: ZoneId): BotStatus {
        if (work != null) {
            return when (work.kind) {
                BotWorkKind.ROUTINE -> BotStatus(BotStatusKind.RUNNING, "Running ${work.routineTitle?.takeIf(String::isNotBlank) ?: "a routine"}")
                BotWorkKind.REPLY -> BotStatus(BotStatusKind.RUNNING, "Writing a reply")
            }
        }
        val next = routines.filter { it.enabled }.mapNotNull { CronSchedulePresets.nextRunAt(it, now) }.minOrNull()
        if (next != null) return BotStatus(BotStatusKind.NEXT, "Next run ${whenLabel(next, now, zone)}")
        val last = routines.mapNotNull { it.lastRunAt }.maxOrNull()
        return when {
            last != null -> BotStatus(BotStatusKind.IDLE, "Last run ${whenLabel(last, now, zone)}")
            routines.isEmpty() -> BotStatus(BotStatusKind.IDLE, "Chat only, no routine")
            else -> BotStatus(BotStatusKind.IDLE, "Routines paused")
        }
    }

    /** "07:00" today, "Sun 18:00" within the week, then "2 Nov 18:00". */
    fun whenLabel(at: Long, now: Long, zone: ZoneId): String {
        val then = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = kotlin.math.abs(ChronoUnit.DAYS.between(today, then.toLocalDate()))
        val time = CLOCK.format(then)
        return when {
            days == 0L -> time
            days < 7 -> "${WEEKDAY.format(then)} $time"
            else -> "${DAY_MONTH.format(then)} $time"
        }
    }

    /** A routine's pill: "Daily 07:00", "Weekly, Sun 18:00", "Mon, Wed 07:30", "Weekdays 08:00". */
    fun routinePill(task: ScheduledTaskEntity): String {
        if (CronSchedulePresets.isLegacy(task)) return CronSchedulePresets.label(task.intervalMinutes)
        val time = String.format(Locale.US, "%02d:%02d", task.hourOfDay ?: 0, task.minuteOfHour ?: 0)
        return when (CronSchedulePresets.recurrenceOf(task)) {
            CronRecurrence.DAILY -> "Daily $time"
            CronRecurrence.WEEKLY -> {
                val days = CronSchedulePresets.daysOfWeek(task).sorted()
                when {
                    days.isEmpty() -> "Weekly $time"
                    days.size == 7 -> "Daily $time"
                    days == listOf(1, 2, 3, 4, 5) -> "Weekdays $time"
                    days == listOf(6, 7) -> "Weekends $time"
                    days.size == 1 -> "Weekly, ${SHORT_DAYS[days[0] - 1]} $time"
                    else -> days.joinToString(", ") { SHORT_DAYS[it - 1] } + " $time"
                }
            }
            CronRecurrence.MONTHLY -> "Monthly, day ${task.dayOfMonth?.coerceIn(1, 31) ?: 1} $time"
        }
    }

    /**
     * A routine's name when the New bot sheet makes one: the first sentence of its prompt,
     * cut at a word to about 32 characters ("Read my feeds and write today's brief").
     */
    fun routineTitle(prompt: String): String {
        val one = prompt.replace(Regex("\\s+"), " ").trim()
        val sentence = one.split(Regex("(?<=[.!?])\\s")).firstOrNull().orEmpty().trimEnd('.', '!', '?', ' ')
        if (sentence.length <= 32) return sentence
        val cut = sentence.take(32)
        val space = cut.lastIndexOf(' ')
        return (if (space >= 12) cut.take(space) else cut).trimEnd(',', ';', ':', ' ') + "…"
    }

    /** The routine that sent [message], if it was a routine's prompt. */
    fun routineFor(message: MessageEntity, routines: List<ScheduledTaskEntity>): ScheduledTaskEntity? {
        if (message.role != "USER") return null
        val text = message.content.trim()
        if (text.isEmpty()) return null
        return routines.firstOrNull { it.prompt.trim() == text }
    }

    /**
     * Every routine run in the bot's chat, newest first. A run is a user message that is one
     * of the bot's routine prompts, with the last reply before the next user message.
     * [busy] says the bot is working now, so the newest run without a finished reply is live.
     */
    fun runs(messages: List<MessageEntity>, routines: List<ScheduledTaskEntity>, busy: Boolean): List<BotRun> {
        val sorted = messages.sortedBy { it.createdAt }
        val lastUserIndex = sorted.indexOfLast { it.role == "USER" }
        val runs = mutableListOf<BotRun>()
        sorted.forEachIndexed { index, message ->
            val routine = routineFor(message, routines) ?: return@forEachIndexed
            val reply = sorted.drop(index + 1)
                .takeWhile { it.role != "USER" }
                .lastOrNull { it.role == "ASSISTANT" }
            val newest = index == lastUserIndex
            val state = when {
                reply == null -> if (busy && newest) RunState.RUNNING else RunState.FAILED
                reply.status == MessageStatus.STREAMING.name -> if (busy && newest) RunState.RUNNING else RunState.STOPPED
                reply.status == MessageStatus.ERROR.name -> RunState.FAILED
                else -> RunState.DONE
            }
            runs += BotRun(
                askedId = message.id,
                routineId = routine.id,
                routineTitle = routine.title,
                at = message.createdAt,
                state = state,
                answer = reply?.content?.trim()?.takeIf(String::isNotEmpty),
                answerId = reply?.id
            )
        }
        return runs.asReversed()
    }

    /** "Today, 07:00 run", "Yesterday, 07:00 run", "Mon 27 Oct, 07:00 run". */
    fun runHeading(at: Long, now: Long, zone: ZoneId): String {
        val then = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = CLOCK.format(then)
        return when (ChronoUnit.DAYS.between(then.toLocalDate(), today)) {
            0L -> "Today, $time run"
            1L -> "Yesterday, $time run"
            else -> "${DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH).format(then)}, $time run"
        }
    }

    /** The run's mark on the card: "[ DONE ]". */
    fun runTag(state: RunState): String = when (state) {
        RunState.RUNNING -> "[ LIVE ]"
        RunState.DONE -> "[ DONE ]"
        RunState.FAILED -> "[ FAILED ]"
        RunState.STOPPED -> "[ STOPPED ]"
    }

    /** One plain line from a markdown answer, for gists in cards and rows. */
    fun gist(markdown: String?): String {
        if (markdown.isNullOrBlank()) return ""
        return markdown
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("\\s*\\[(\\d+)]"), "")
            .replace(Regex("[*_#>`|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** The Crew title's caption: "3 bots". Empty with no bots (the empty state says it). */
    fun countCaption(count: Int): String = when {
        count <= 0 -> ""
        count == 1 -> "1 bot"
        else -> "$count bots"
    }

    /** The bot to feature in the framed card: the first one working, else none. */
    fun featured(bots: List<BotEntity>, work: Map<String, BotWork>): BotEntity? = bots.firstOrNull { it.id in work }

    /** Crew's strip line: "CREW SCOUT RUNNING", "CREW 2 BOTS WORKING", or "CREW READY". */
    fun crewLine(bots: List<BotEntity>, work: Map<String, BotWork>): InkLine {
        val working = bots.filter { it.id in work }
        return when (working.size) {
            0 -> InkLine("CREW READY")
            1 -> {
                val bot = working.first()
                val verb = if (work[bot.id]?.kind == BotWorkKind.REPLY) "WRITING" else "RUNNING"
                InkLine("CREW ${bot.name} $verb", live = true)
            }
            else -> InkLine("CREW ${working.size} BOTS WORKING", live = true)
        }
    }

    /** A bot page's strip line: "SCOUT RUNNING", "SCOUT WRITING" or "SCOUT READY". */
    fun botLine(name: String, work: BotWork?): InkLine = when (work?.kind) {
        BotWorkKind.ROUTINE -> InkLine("$name RUNNING", live = true)
        BotWorkKind.REPLY -> InkLine("$name WRITING", live = true)
        null -> InkLine("$name READY")
    }
}
