package ai.daylight.assistant.ui.home

import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.AgentPlan
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.AgentQualityPolicy
import ai.daylight.assistant.domain.MakeKind
import ai.daylight.assistant.domain.userWantsImage
import ai.daylight.assistant.domain.userWantsVideo
import ai.daylight.assistant.ui.shell.Arm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How a port is drawn: a white idle node, a yellow busy cable, or unlit dots. */
enum class PortState { IDLE, BUSY, UNLIT }

/**
 * One port on the arm board. [caption] is in sentence case (the screen sets it in
 * uppercase mono; screen readers get the words). [later] shows the dashed LATER tag.
 */
data class ArmPort(val arm: Arm, val caption: String, val state: PortState, val later: Boolean = false)

/** Everything the board says, read from real state. Nothing here is invented. */
data class HomeSnapshot(
    /** Conversations that have content (the same list the chat library shows). */
    val threadCount: Int = 0,
    val lastThreadAt: Long? = null,
    /** Arms with work running now (the rail's busy dots). */
    val busyArms: Set<Arm> = emptySet(),
    /** The agent's live plan, only when its run is the one running. */
    val runPlan: AgentPlan? = null,
    val quality: AgentQuality = AgentQuality.DEFAULT,
    val costCapCents: Int = 0,
    /** What Make is rendering now, when its port is busy. */
    val makeKind: MakeKind? = null,
    /** The newest image or clip in the history, and when it was made. */
    val lastMakeKind: MakeKind? = null,
    val lastMakeAt: Long? = null,
    val now: Long = 0L,
    val zone: ZoneId = ZoneId.systemDefault()
)

/** Captions, the status sentence and the strip line for Home (SCREENS.md section 1). */
object HomeBoard {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val DATE_TITLE = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)

    fun ports(s: HomeSnapshot): List<ArmPort> = Arm.entries.map { port(it, s) }

    fun port(arm: Arm, s: HomeSnapshot): ArmPort {
        val state = when {
            !arm.live -> PortState.UNLIT
            arm in s.busyArms -> PortState.BUSY
            else -> PortState.IDLE
        }
        return ArmPort(arm, caption(arm, s), state, later = arm == Arm.LINK)
    }

    fun caption(arm: Arm, s: HomeSnapshot): String {
        val busy = arm in s.busyArms
        return when (arm) {
            Arm.TALK -> when {
                busy -> "Writing a reply"
                s.threadCount <= 0 -> "No threads yet"
                else -> {
                    val noun = if (s.threadCount == 1) "thread" else "threads"
                    val last = s.lastThreadAt?.let { ", last ${timeLabel(it, s.now, s.zone)}" }.orEmpty()
                    "${s.threadCount} $noun$last"
                }
            }
            Arm.MAKE -> when {
                busy -> when (s.makeKind) {
                    MakeKind.IMAGE -> "Rendering an image"
                    MakeKind.VIDEO -> "Rendering a clip"
                    null -> "Rendering"
                }
                s.lastMakeAt != null -> {
                    val noun = if (s.lastMakeKind == MakeKind.VIDEO) "clip" else "image"
                    "Last $noun ${timeLabel(s.lastMakeAt, s.now, s.zone)}"
                }
                else -> "Images and clips"
            }
            Arm.FIND -> "Web images, not yet"
            Arm.CREW -> "Personal bots, not yet"
            Arm.RUN -> when {
                busy && s.runPlan != null && s.runPlan.steps.isNotEmpty() -> stepText(s.runPlan)
                busy -> "Working on a task"
                else -> "No plan running"
            }
            Arm.VOICE -> "Tap to talk"
            Arm.LINK -> "Essential, coming later"
            Arm.TUNE -> "${s.quality.label}, cap ${capLabel(effectiveCapCents(s.quality, s.costCapCents))}"
        }
    }

    /**
     * The sentence beside the squid: what is running, in plain words. When nothing is,
     * it says so and points at the last thread. It is never a greeting.
     */
    fun status(s: HomeSnapshot): String {
        val running = buildList {
            if (Arm.RUN in s.busyArms) {
                add(s.runPlan?.takeIf { it.steps.isNotEmpty() }?.let { "Run is on ${stepText(it).lowercase()}." }
                    ?: "Run is working on a task.")
            }
            if (Arm.TALK in s.busyArms) add("Talk is writing a reply.")
            if (Arm.MAKE in s.busyArms) {
                add(
                    when (s.makeKind) {
                        MakeKind.VIDEO -> "Make is rendering a clip."
                        MakeKind.IMAGE -> "Make is rendering an image."
                        null -> "Make is rendering."
                    }
                )
            }
            if (Arm.VOICE in s.busyArms) add("Voice is listening.")
        }
        if (running.isNotEmpty()) return running.joinToString(" ")
        val last = s.lastThreadAt
        return if (s.threadCount <= 0 || last == null) {
            "Nothing is running. Ask anything below to start."
        } else {
            "Nothing is running. Last thread ${timePhrase(last, s.now, s.zone)}."
        }
    }

    /** "STEP 3 OF 5": the step in progress, never past the last one. */
    private fun stepText(plan: AgentPlan): String {
        val total = plan.steps.size
        return "Step ${(plan.doneCount + 1).coerceIn(1, total)} of $total"
    }

    /** Max raises a lower cap to $1.00 for its turns; a cap switched off stays off. */
    fun effectiveCapCents(quality: AgentQuality, capCents: Int): Int = when {
        capCents <= 0 -> 0
        quality == AgentQuality.MAX -> maxOf(capCents, AgentQualityPolicy.MAX_COST_CAP_CENTS)
        else -> capCents
    }

    fun capLabel(cents: Int): String =
        if (cents <= 0) "off" else "$" + String.format(Locale.US, "%.2f", cents / 100.0)

    /** "09:12" today, "yesterday", "Mon" within the week, then "26 Oct". */
    fun timeLabel(at: Long, now: Long, zone: ZoneId): String {
        val then = Instant.ofEpochMilli(at).atZone(zone)
        // LocalDate.ofInstant needs API 34; go through ZonedDateTime instead.
        val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), today)
        return when {
            days <= 0 -> CLOCK.format(then)
            days == 1L -> "yesterday"
            days < 7 -> WEEKDAY.format(then)
            else -> DAY_MONTH.format(then)
        }
    }

    /** [timeLabel] as it reads inside a sentence: "at 09:12", "yesterday", "on Mon", "on 26 Oct". */
    fun timePhrase(at: Long, now: Long, zone: ZoneId): String {
        val label = timeLabel(at, now, zone)
        return when {
            label.firstOrNull()?.isDigit() == true && label.contains(':') -> "at $label"
            label == "yesterday" -> label
            else -> "on $label"
        }
    }

    /**
     * The Home composer's one-tap switch: a request that reads like "make a video" or
     * "a picture of" is offered to the generator. Video wins when both match ("an
     * animated logo"). Null means plain Talk. The offer never sends on its own.
     */
    fun makeOffer(text: String): AgentCapability? = when {
        text.isBlank() -> null
        userWantsVideo(text) -> AgentCapability.VIDEO
        userWantsImage(text) -> AgentCapability.IMAGE
        else -> null
    }

    /** The Doto date block: "MON 26" over "OCTOBER". */
    fun dateTitle(now: Long, zone: ZoneId): String = DATE_TITLE.format(Instant.ofEpochMilli(now).atZone(zone)).uppercase(Locale.ENGLISH)

    fun monthCaption(now: Long, zone: ZoneId): String = MONTH.format(Instant.ofEpochMilli(now).atZone(zone))
}
