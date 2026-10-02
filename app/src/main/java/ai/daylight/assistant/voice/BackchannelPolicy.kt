package ai.daylight.assistant.voice

import kotlin.random.Random

/**
 * Decides when Kryzz says a short "mm-hmm" while the user is talking, like a person on a
 * call. One plays only after the user has talked for a while, only in a short pause between
 * phrases (before the end-of-turn silence), and the next one needs another stretch of talk.
 * The stretch is jittered so the sounds do not land on a fixed rhythm.
 */
internal class BackchannelPolicy(
    private val random: Random = Random.Default,
    private val minTalkMs: Long = MIN_TALK_MS,
    private val talkJitterMs: Long = TALK_JITTER_MS,
    private val pauseMinMs: Long = PAUSE_MIN_MS,
    private val pauseMaxMs: Long = PAUSE_MAX_MS,
    private val maxPerUtterance: Int = MAX_PER_UTTERANCE
) {
    private var voicedAtLast = 0L
    private var count = 0
    private var needed = nextStretch()

    /** Starts a new utterance. */
    fun reset() {
        voicedAtLast = 0L
        count = 0
        needed = nextStretch()
    }

    /**
     * Called once per listening poll with the utterance's voiced time so far and the current
     * pause. Returns true when a sound should play now, and counts it.
     */
    fun shouldSpeak(voicedMs: Long, silenceMs: Long): Boolean {
        if (count >= maxPerUtterance) return false
        if (voicedMs - voicedAtLast < needed) return false
        if (silenceMs < pauseMinMs || silenceMs > pauseMaxMs) return false
        voicedAtLast = voicedMs
        count++
        needed = nextStretch()
        return true
    }

    private fun nextStretch(): Long = minTalkMs + if (talkJitterMs > 0L) random.nextLong(talkJitterMs + 1) else 0L

    companion object {
        /** Voiced speech (pauses excluded) before the first sound; about five seconds of talking. */
        const val MIN_TALK_MS = 3_500L

        /** Up to this much extra talk is required, chosen at random each time. */
        const val TALK_JITTER_MS = 2_500L

        /**
         * The pause window. It opens after early transcription has started and closes well
         * before the end-of-turn silence, so the sound lands between phrases.
         */
        const val PAUSE_MIN_MS = 260L
        const val PAUSE_MAX_MS = 400L

        const val MAX_PER_UTTERANCE = 3
    }
}
