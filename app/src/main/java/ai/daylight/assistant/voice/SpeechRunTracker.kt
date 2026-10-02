package ai.daylight.assistant.voice

/**
 * Tracks one unbroken run of "the user is talking" polls while Kryzz speaks.
 *
 * Dips between syllables up to [gapToleranceMs] keep the run alive; a longer quiet ends
 * it. Isolated echo or noise spikes spread across a reply therefore never add up to an
 * interruption, while real speech is measured from its first syllable ([onsetAtMs]).
 */
internal class SpeechRunTracker(private val gapToleranceMs: Long = DEFAULT_GAP_TOLERANCE_MS) {
    /** When the current run started, or null when there is no run. */
    var onsetAtMs: Long? = null
        private set

    private var lastActiveAtMs: Long? = null

    fun reset() {
        onsetAtMs = null
        lastActiveAtMs = null
    }

    /** Feeds one poll and returns how long the current run has lasted, 0 when there is none. */
    fun observe(active: Boolean, nowMs: Long): Long {
        val last = lastActiveAtMs
        if (active) {
            if (onsetAtMs == null || last == null || nowMs - last > gapToleranceMs) onsetAtMs = nowMs
            lastActiveAtMs = nowMs
        } else if (last != null && nowMs - last > gapToleranceMs) {
            reset()
        }
        val onset = onsetAtMs ?: return 0L
        return (lastActiveAtMs ?: onset) - onset
    }

    companion object {
        /** Longer than the gaps between words in one phrase, shorter than a pause between phrases. */
        const val DEFAULT_GAP_TOLERANCE_MS = 220L
    }
}
