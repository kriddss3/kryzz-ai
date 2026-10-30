package ai.daylight.assistant.ui.voice

import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.ui.dot.mascot.SquidPose
import ai.daylight.assistant.ui.voicePhaseTitle
import ai.daylight.assistant.voice.VoicePhase
import ai.daylight.assistant.voice.toSpeechText
import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.dp
import java.util.Locale

/** The kit's voice LED field: 44 columns by 15 rows, 9 rows on short screens. */
internal const val VOICE_WAVE_COLS = 44
internal const val VOICE_WAVE_ROWS = 15
internal const val VOICE_WAVE_ROWS_COMPACT = 9

/** One new column per tick, so the 44 columns hold about three seconds of sound. */
internal const val VOICE_WAVE_TICK_MS = 70L

/** Below this height (short phones, landscape) the voice screen shrinks and scrolls. */
internal val VOICE_COMPACT_HEIGHT = 560.dp

/**
 * Feeds the voice waveform. The level arrives every 30ms; each tick pushes the loudest level
 * seen since the last tick as the newest column, so short peaks are not lost between ticks.
 */
internal class VoiceWaveHistory(private val cols: Int) {
    private val columns = ArrayDeque<Float>(cols)
    private var latest = 0f
    private var peak = 0f

    fun offer(level: Float) {
        val lv = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
        latest = lv
        peak = maxOf(peak, lv)
    }

    /** Pushes one column and returns the columns, oldest first. */
    fun tick(): List<Float> {
        columns.addLast(peak)
        while (columns.size > cols) columns.removeFirst()
        // A level that holds steady is not offered again, so the next column starts from it.
        peak = latest
        return columns.toList()
    }

    fun clear() {
        columns.clear()
        latest = 0f
        peak = 0f
    }
}

/**
 * Lit dots at least this many rows from the centre turn yellow, but only while the user is
 * speaking (kit: the outer 3 rows of 15). Kryzz's own voice stays white.
 */
internal fun voiceWavePeakFrom(phase: VoicePhase, muted: Boolean, rows: Int): Int? {
    if (phase != VoicePhase.LISTENING || muted) return null
    val mid = rows / 2
    return (mid * 5 + 6) / 7
}

/** One spoken turn, cleaned for display. */
internal data class VoiceTurn(val fromUser: Boolean, val text: String)

/**
 * The transcript: [previous] is drawn dim above [current]. When nothing new has been said
 * yet, [placeholder] stands in for the current line in grey.
 */
internal data class VoiceLines(
    val previous: String?,
    val current: String?,
    val placeholder: String? = null
)

/** The last few spoken turns of the conversation, oldest first. */
internal fun voiceTurns(messages: List<MessageEntity>, limit: Int = 4): List<VoiceTurn> =
    messages.asReversed().asSequence()
        .filter { it.role == "USER" || it.role == "ASSISTANT" }
        // v2.1: a direct-hearing turn reads "Voice message" until Gemini says what it heard.
        .filterNot { it.role == "USER" && it.content == ai.daylight.assistant.voice.VoiceHearing.PLACEHOLDER }
        .map { VoiceTurn(fromUser = it.role == "USER", text = voiceDisplayText(it.content)) }
        .filter { it.text.isNotBlank() }
        .take(limit)
        .toList()
        .asReversed()

/**
 * What the transcript shows. The app has no live speech-to-text, so the words heard appear
 * once the clip is transcribed:
 * - Listening: Kryzz's last line dim, then "Your turn." in grey.
 * - Thinking: Kryzz's last line dim, then the words just heard.
 * - Speaking: the words heard dim, then Kryzz's reply.
 */
internal fun voiceLines(
    phase: VoicePhase,
    transcript: String,
    muted: Boolean,
    turns: List<VoiceTurn>
): VoiceLines {
    val heard = voiceDisplayText(transcript).ifBlank { null }
    val heardAt = heard?.let { words -> turns.indexOfLast { it.fromUser && sameWords(it.text, words) } } ?: -1
    return when (phase) {
        VoicePhase.IDLE -> VoiceLines(null, null)
        VoicePhase.LISTENING -> VoiceLines(
            previous = turns.lastOrNull()?.text,
            current = null,
            placeholder = if (muted) "Mic muted." else "Your turn."
        )
        VoicePhase.PROCESSING -> VoiceLines(
            previous = replyBefore(turns, if (heardAt >= 0) heardAt else turns.size),
            current = heard
        )
        VoicePhase.SPEAKING -> {
            val reply = if (heardAt >= 0) turns.drop(heardAt + 1).lastOrNull { !it.fromUser }?.text
                else turns.lastOrNull()?.takeIf { !it.fromUser }?.text
            VoiceLines(
                previous = heard ?: turns.lastOrNull { it.fromUser }?.text,
                current = reply
            )
        }
    }
}

/** Kryzz's most recent line before index [end]. */
private fun replyBefore(turns: List<VoiceTurn>, end: Int): String? =
    turns.take(end).lastOrNull { !it.fromUser }?.text

/**
 * The chat row holds the whole transcript; the overlay keeps its first 200 characters. So a
 * row matches when it equals what was heard, or, for a long transcript, starts with it.
 */
private fun sameWords(saved: String, heard: String): Boolean =
    saved.equals(heard, ignoreCase = true) ||
        (heard.length >= LONG_TRANSCRIPT && saved.regionMatches(0, heard, 0, LONG_TRANSCRIPT, ignoreCase = true))

private const val LONG_TRANSCRIPT = 150

/** Message text as speech: no markdown, links or code, and no [emotion] tags. */
internal fun voiceDisplayText(raw: String): String = raw.toSpeechText()
    .replace(EMOTION_TAG, "")
    .replace(SPACES, " ")
    .trim()

private val EMOTION_TAG = Regex("""\[[^\[\]]{1,24}]\s*""")
private val SPACES = Regex("""\s+""")

/** Long replies step down a size so they fit between the header and the waveform. */
internal enum class VoiceTextSize { LARGE, MEDIUM, SMALL }

internal fun voiceTextSize(length: Int): VoiceTextSize = when {
    length <= 110 -> VoiceTextSize.LARGE
    length <= 280 -> VoiceTextSize.MEDIUM
    else -> VoiceTextSize.SMALL
}

/** The mic is open while listening and while Kryzz speaks (for barge-in), unless muted. */
internal fun voiceMicLive(phase: VoicePhase, muted: Boolean): Boolean =
    !muted && (phase == VoicePhase.LISTENING || phase == VoicePhase.SPEAKING)

/** "LISTENING, 00:14": the phase and how long it has lasted. */
internal fun voiceStatusCaption(phase: VoicePhase, searching: Boolean, muted: Boolean, elapsedSec: Long): String {
    val label = when {
        phase == VoicePhase.PROCESSING && searching -> "Checking online"
        muted && phase == VoicePhase.LISTENING -> "Muted"
        else -> voicePhaseTitle(phase)
    }
    val sec = elapsedSec.coerceAtLeast(0)
    return String.format(Locale.US, "%s, %02d:%02d", label, sec / 60, sec % 60).uppercase(Locale.US)
}

internal fun voiceSquidPose(phase: VoicePhase): SquidPose = when (phase) {
    VoicePhase.IDLE -> SquidPose.STANDBY
    VoicePhase.LISTENING, VoicePhase.PROCESSING -> SquidPose.THINKING
    VoicePhase.SPEAKING -> SquidPose.SPEAKING
}

/** The core button shows what a tap does: a mic while listening, stop while thinking or speaking. */
@DrawableRes
internal fun voiceCoreIcon(phase: VoicePhase, muted: Boolean): Int = when (phase) {
    VoicePhase.LISTENING, VoicePhase.IDLE -> if (muted) R.drawable.ic_kz_mic_off else R.drawable.ic_kz_mic
    VoicePhase.PROCESSING, VoicePhase.SPEAKING -> R.drawable.ic_kz_stop
}
