package ai.daylight.assistant.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.ui.Routes
import ai.daylight.assistant.ui.dot.effects.DotFont5x7
import ai.daylight.assistant.voice.VoicePhase
import java.text.Normalizer

/** One line on the ink strip. [live] lights the two-dot yellow mark. */
data class InkLine(val text: String, val live: Boolean = false) {
    companion object {
        /** Characters that separate words; they become a space instead of vanishing. */
        private val SEPARATORS = setOf(',', ';', '_', '|', '•', '·', '–', '—', '\n', '\t')
        private val COMBINING_MARKS = Regex("\\p{Mn}+")
        private val SPACES = Regex(" +")

        /**
         * Makes [raw] drawable by the 5x7 font: accents are stripped, letters uppercased,
         * separators turned into spaces, and any other unsupported character dropped.
         * The result is trimmed, single-spaced and at most [maxChars] long.
         */
        fun sanitize(raw: String, maxChars: Int = 40): String {
            val plain = COMBINING_MARKS.replace(Normalizer.normalize(raw, Normalizer.Form.NFD), "")
            val out = StringBuilder(plain.length)
            for (ch in plain) {
                val up = ch.uppercaseChar()
                when {
                    ch in SEPARATORS || ch.isWhitespace() -> out.append(' ')
                    DotFont5x7.supports(up) -> out.append(up)
                }
            }
            return SPACES.replace(out.toString(), " ").trim().take(maxChars.coerceAtLeast(0)).trimEnd()
        }
    }
}

/** The strip messages, derived from state the screens already collect. */
object InkLines {
    /** "Searching: pixel 10" / "Reading the page" -> "SEARCHING" / "READING". */
    internal fun activityVerb(activity: String?): String? {
        val first = activity?.trim()?.split(' ', ':', '…')?.firstOrNull()?.trim().orEmpty()
        if (first.length !in 3..12 || !first.all { it.isLetter() }) return null
        return InkLine.sanitize(first).takeIf { it.isNotEmpty() }
    }

    /**
     * Talk's line. While a page is being read and the source readout knows the count, it
     * reads like the mockup: "TALK READING 3 OF 5" ([step] of [of]).
     */
    fun talk(generating: Boolean, latestActivity: String? = null, step: Int = 0, of: Int = 0): InkLine {
        if (!generating) return InkLine("TALK READY")
        val verb = activityVerb(latestActivity) ?: "THINKING"
        return if (verb == "READING" && of > 0) {
            InkLine("TALK READING ${step.coerceIn(1, of)} OF $of", live = true)
        } else {
            InkLine("TALK $verb", live = true)
        }
    }

    fun run(generating: Boolean, doneSteps: Int = 0, totalSteps: Int = 0, latestActivity: String? = null): InkLine = when {
        generating && totalSteps > 0 ->
            InkLine("RUN STEP ${(doneSteps + 1).coerceIn(1, totalSteps)} OF $totalSteps", live = true)
        generating -> InkLine("RUN ${activityVerb(latestActivity) ?: "THINKING"}", live = true)
        totalSteps > 0 && doneSteps >= totalSteps -> InkLine("RUN DONE")
        else -> InkLine("RUN READY")
    }

    fun voice(phase: VoicePhase, searching: Boolean = false): InkLine = when (phase) {
        VoicePhase.IDLE -> InkLine("VOICE READY")
        VoicePhase.LISTENING -> InkLine("VOICE LISTENING", live = true)
        VoicePhase.PROCESSING -> InkLine(if (searching) "VOICE SEARCHING" else "VOICE THINKING", live = true)
        VoicePhase.SPEAKING -> InkLine("VOICE SPEAKING", live = true)
    }

    fun tune(quality: AgentQuality): InkLine = InkLine("TUNE ${quality.label}".uppercase())

    /** Shown briefly when an unlit arm is tapped: unlit means "not yet". */
    fun notYet(arm: Arm): InkLine = InkLine("${arm.label} NOT YET".uppercase())
}

/**
 * The line a route shows when its screen sets none of its own (depth pages keep the
 * arm they belong to). Onboarding shows no strip at all.
 */
fun defaultInkLine(route: String?): InkLine {
    val r = route.orEmpty()
    return when {
        r == Routes.BOOT || r == Routes.CHAT || r.startsWith("chat/") -> InkLines.talk(generating = false)
        r == Routes.SETTINGS -> InkLine("TUNE")
        r == Routes.API -> InkLine("TUNE KEYS")
        r == Routes.PERSONA -> InkLine("TUNE PERSONA")
        r == Routes.TOOLS -> InkLine("TUNE TOOLS")
        r == Routes.PRIVACY -> InkLine("TUNE PRIVACY")
        r == Routes.APPEARANCE -> InkLine("TUNE APPEARANCE")
        r == Routes.VOICE -> InkLine("TUNE VOICE")
        r == Routes.MEMORY -> InkLine("TUNE MEMORY")
        r == Routes.CRON -> InkLine("TUNE ROUTINES")
        r == Routes.SKILLS -> InkLine("TUNE SKILLS")
        r == Routes.LLMS || r.startsWith("models/") -> InkLine("TUNE MODELS")
        else -> InkLine("")
    }
}

/**
 * Shared state for the shell chrome. Screens report their line (and, for the chat screen,
 * which arm it is showing) through [InkStripEffect]; the newest screen to join wins, so
 * during a navigation transition the incoming screen owns the strip. Background work
 * reports busy arms with [setBusy], keyed by whatever owns the work.
 */
@Stable
class InkStripState {
    private class Slot(val owner: Any) {
        var line by mutableStateOf(InkLine(""))
        var arm by mutableStateOf<Arm?>(null)
    }

    private val slots = mutableStateListOf<Slot>()
    private val busyByKey = mutableStateMapOf<String, Arm>()
    private var flashSerial = 0

    /** A short message that overrides the screen's line, e.g. after tapping an unlit arm. */
    var flash by mutableStateOf<Pair<Int, InkLine>?>(null)
        private set

    val line: InkLine? get() = slots.lastOrNull()?.line
    val reportedArm: Arm? get() = slots.lastOrNull()?.arm
    val busyArms: Set<Arm> get() = busyByKey.values.toSet()

    fun isBusy(key: String?): Boolean = key != null && busyByKey.containsKey(key)

    fun setBusy(key: String, arm: Arm?) {
        if (arm == null) busyByKey.remove(key) else if (busyByKey[key] != arm) busyByKey[key] = arm
    }

    fun update(owner: Any, line: InkLine, arm: Arm?) {
        val slot = slots.firstOrNull { it.owner === owner } ?: Slot(owner).also { slots.add(it) }
        if (slot.line != line) slot.line = line
        if (slot.arm != arm) slot.arm = arm
    }

    fun release(owner: Any) {
        slots.removeAll { it.owner === owner }
    }

    fun flash(line: InkLine) {
        flashSerial += 1
        flash = flashSerial to line
    }

    fun clearFlash(serial: Int) {
        if (flash?.first == serial) flash = null
    }
}

val LocalInkStrip = staticCompositionLocalOf<InkStripState?> { null }

/**
 * Sets this screen's line on the ink strip while the screen is shown, e.g.
 * `InkStripEffect("TALK READY")`. [arm] tells the rail which arm the screen is showing
 * when its route alone can't (the chat screen switches between Talk, Run and Voice).
 */
@Composable
fun InkStripEffect(text: String, live: Boolean = false, arm: Arm? = null) {
    val state = LocalInkStrip.current ?: return
    val owner = remember { Any() }
    DisposableEffect(state, owner) {
        onDispose { state.release(owner) }
    }
    SideEffect { state.update(owner, InkLine(text, live), arm) }
}

@Composable
fun InkStripEffect(line: InkLine, arm: Arm? = null) = InkStripEffect(line.text, line.live, arm)
