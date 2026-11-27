package ai.daylight.assistant.voice

/** Why a mic poll during a spoken reply counts as the user, if it does. */
internal enum class BargeKind {
    NONE,
    OUTSHOUT,
    SOFT
}

/**
 * Tells the user's voice from Kryzz's own voice leaking back into the microphone.
 *
 * Speaker bleed is pitched speech, so "this frame sounds like a voice" cannot separate
 * them. A gate that accepts any sustained voice therefore cuts the reply off by itself.
 * This keeps a running picture of how loud that leak is compared with playback, and only
 * counts energy that rises clearly above it. When echo cancellation has made the leak
 * quiet, a normal voice is allowed through the slower soft path.
 */
internal class EchoAwareBarge {
    var coupling: Float = PRIOR_COUPLING
        private set

    private val ratios = ArrayDeque<Float>()
    private var primed = 0
    private var heardPlayback = false
    private var lastEcho = 0f

    init {
        reset()
    }

    fun reset() {
        ratios.clear()
        repeat(WINDOW) { ratios.addLast(PRIOR_COUPLING) }
        primed = 0
        coupling = PRIOR_COUPLING
        heardPlayback = false
        lastEcho = 0f
    }

    fun classify(
        micLevel: Float,
        playerLevel: Float,
        playing: Boolean,
        voiceLike: Boolean
    ): BargeKind {
        val mic = micLevel.coerceIn(0f, 1.5f)
        val player = playerLevel.coerceIn(0f, 1f)
        val audible = playing && player >= AUDIBLE_PLAYER
        if (audible) {
            heardPlayback = true
            note(mic, player)
            lastEcho = player * coupling
        }

        if (!voiceLike) return BargeKind.NONE
        if (audible) {
            val predicted = player * coupling
            val line = maxOf(
                player * (coupling + RATIO_MARGIN) + ABS_MARGIN,
                player * OUTSHOUT_PLAYER_FRACTION + OUTSHOUT_FLOOR,
                OUTSHOUT_MIN
            )
            if (mic > line) return BargeKind.OUTSHOUT
            if (
                coupling <= SOFT_COUPLING_MAX &&
                predicted <= SOFT_ECHO_MAX &&
                mic >= SOFT_MIN &&
                mic > predicted + SOFT_EXCESS
            ) {
                return BargeKind.SOFT
            }
            return BargeKind.NONE
        }
        if (playing) {
            // Playback has started but the level meter is not reporting yet. Ordinary
            // speaker bleed sits well under a shout, so it must not open the gate.
            heardPlayback = true
            return if (mic >= UNKNOWN_PLAYER_MIC) BargeKind.OUTSHOUT else BargeKind.NONE
        }
        if (!heardPlayback) {
            // The reply has not reached the speaker yet. Someone talking now is the user
            // cutting in before the first word.
            return if (mic >= OUTSHOUT_MIN) BargeKind.OUTSHOUT else BargeKind.NONE
        }
        // Between clips, or in the tail after the player stops. Bleed hangs on briefly;
        // only a voice clearly above that leftover level counts.
        return if (mic >= GAP_MIN && mic > lastEcho + GAP_EXCESS) BargeKind.OUTSHOUT else BargeKind.NONE
    }

    private fun note(mic: Float, player: Float) {
        val ratio = (mic / player).coerceIn(0f, MAX_RATIO)
        // The opening of the reply is the speaker. Learn it before deciding that a
        // louder sample is the user, so a leak above the prior cannot trip the gate.
        if (primed < WINDOW) {
            ratios.removeFirst()
            ratios.addLast(ratio)
            coupling = lowPercentile(ratios).coerceIn(0.02f, MAX_RATIO)
            primed++
            return
        }
        val quiet = mic < QUIET_MIC && ratio + 0.02f < coupling
        val aboveLeak = ratio > coupling + SHOUT_SLACK
        when {
            quiet -> coupling += (ratio - coupling) * QUIET_DECAY
            aboveLeak -> Unit
            else -> {
                ratios.removeFirst()
                ratios.addLast(ratio)
                coupling = lowPercentile(ratios)
            }
        }
        coupling = coupling.coerceIn(0.02f, MAX_RATIO)
    }

    private companion object {
        const val WINDOW = 10
        const val PRIOR_COUPLING = 0.85f
        const val MAX_RATIO = 1.5f
        const val AUDIBLE_PLAYER = 0.08f
        const val QUIET_MIC = 0.055f
        const val QUIET_DECAY = 0.07f
        const val SHOUT_SLACK = 0.18f
        const val RATIO_MARGIN = 0.22f
        const val ABS_MARGIN = 0.06f
        const val OUTSHOUT_PLAYER_FRACTION = 0.22f
        const val OUTSHOUT_FLOOR = 0.04f
        const val OUTSHOUT_MIN = 0.05f
        const val SOFT_COUPLING_MAX = 0.18f
        const val SOFT_ECHO_MAX = 0.085f
        const val SOFT_MIN = 0.05f
        const val SOFT_EXCESS = 0.035f
        const val UNKNOWN_PLAYER_MIC = 0.30f
        const val GAP_MIN = 0.12f
        const val GAP_EXCESS = 0.12f
        const val LOW_PERCENTILE = 0.35f

        /** Low percentile so one quiet frame cannot pretend the speaker is silent. */
        fun lowPercentile(values: ArrayDeque<Float>): Float {
            if (values.isEmpty()) return PRIOR_COUPLING
            val sorted = values.sorted()
            val index = ((sorted.size - 1) * LOW_PERCENTILE).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }
    }
}
