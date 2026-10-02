package ai.daylight.assistant.voice

import kotlin.math.max
import kotlin.math.sqrt

/** Why an automatic voice recording ended. Manual taps are handled by the caller. */
internal enum class VoiceStopReason {
    END_OF_SPEECH,
    TOO_BRIEF,
    MAX_DURATION
}

/** Result of one microphone-amplitude observation. */
internal data class VoiceActivitySample(
    val level: Float,
    val isVoiceActive: Boolean,
    val hasSpeech: Boolean,
    val voicedDurationMs: Long,
    val activationThreshold: Int,
    val noiseFloor: Int,
    val stopReason: VoiceStopReason? = null
)

/**
 * Lightweight, adaptive end-of-speech detection for [android.media.MediaRecorder.maxAmplitude].
 *
 * MediaRecorder exposes a peak amplitude rather than raw PCM, so this detector deliberately
 * combines several conservative signals instead of treating a single sample as speech:
 *
 *  * a short settling window learns the room's background level;
 *  * the speech gate floats above that noise floor and uses a lower release gate (hysteresis);
 *  * speech must be sustained before it is confirmed, rejecting taps and isolated spikes;
 *  * only accumulated voiced time counts toward the minimum utterance length; and
 *  * a full trailing-silence window is required before the clip is submitted.
 *
 * The class is Android-free so turn-taking behaviour can be covered by fast unit tests.
 */
internal class AdaptiveEndOfSpeechDetector(
    private val maxRecordMs: Long = VoiceConfig.MAX_RECORD_MS,
    private val trailingSilenceMs: Long = VoiceConfig.SILENCE_STOP_MS,
    private val minVoicedMs: Long = VoiceConfig.MIN_SPEECH_MS,
    private val speechConfirmMs: Long = VoiceConfig.SPEECH_CONFIRM_MS,
    private val calibrationMs: Long = VoiceConfig.NOISE_CALIBRATION_MS,
    private val minimumGate: Int = VoiceConfig.SPEECH_THRESHOLD,
    private val allowImmediateSpeechDuringCalibration: Boolean = true
) {
    var hasSpeech: Boolean = false
        private set

    var voicedDurationMs: Long = 0L
        private set

    val hasUsableSpeech: Boolean
        get() = hasSpeech && voicedDurationMs >= minVoicedMs

    private var startedAtMs: Long? = null
    private var lastSampleAtMs: Long? = null
    private var candidateStartedAtMs: Long? = null
    private var lastVoiceAtMs: Long? = null
    private var noiseFloor = INITIAL_NOISE_FLOOR

    fun reset(startedAtMs: Long) {
        this.startedAtMs = startedAtMs
        lastSampleAtMs = startedAtMs
        candidateStartedAtMs = null
        lastVoiceAtMs = null
        noiseFloor = INITIAL_NOISE_FLOOR
        hasSpeech = false
        voicedDurationMs = 0L
    }

    /** How long the user has been quiet since the last voiced sample; 0 before any speech. */
    fun silenceMs(nowMs: Long): Long = lastVoiceAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L

    /** Seeds a recording that already contains the speech used to trigger barge-in. */
    fun seedSpeech(nowMs: Long, confirmedSpeechMs: Long) {
        if (startedAtMs == null) reset(nowMs - confirmedSpeechMs.coerceAtLeast(0L))
        hasSpeech = true
        voicedDurationMs = confirmedSpeechMs.coerceAtLeast(0L)
        lastVoiceAtMs = nowMs
        lastSampleAtMs = nowMs
        candidateStartedAtMs = null
    }

    fun observe(amplitude: Int, nowMs: Long): VoiceActivitySample {
        if (startedAtMs == null) reset(nowMs)
        val startedAt = checkNotNull(startedAtMs)
        val previousSampleAt = lastSampleAtMs ?: nowMs
        val sampleDuration = (nowMs - previousSampleAt).coerceIn(0L, MAX_SAMPLE_GAP_MS)
        lastSampleAtMs = nowMs

        val safeAmplitude = amplitude.coerceIn(0, MAX_AMPLITUDE)
        var activationGate = activationGate()
        val releaseGate = releaseGate()
        val calibrating = !hasSpeech && nowMs - startedAt <= calibrationMs
        val immediateSpeech = safeAmplitude >= (activationGate * IMMEDIATE_SPEECH_MULTIPLIER).toInt()
        var voiceActive = if (hasSpeech) safeAmplitude >= releaseGate else safeAmplitude >= activationGate

        if (!hasSpeech) {
            if (voiceActive && (!calibrating || (allowImmediateSpeechDuringCalibration && immediateSpeech))) {
                val candidateAt = candidateStartedAtMs ?: nowMs.also { candidateStartedAtMs = it }
                if (nowMs - candidateAt >= speechConfirmMs) {
                    hasSpeech = true
                    voicedDurationMs = (nowMs - candidateAt).coerceAtLeast(sampleDuration)
                    lastVoiceAtMs = nowMs
                }
            } else {
                candidateStartedAtMs = null
                updateNoiseFloor(safeAmplitude)
                activationGate = activationGate()
                voiceActive = false
            }
        } else if (voiceActive) {
            voicedDurationMs += sampleDuration
            lastVoiceAtMs = nowMs
        }

        val elapsed = nowMs - startedAt
        val stopReason = when {
            elapsed >= maxRecordMs -> VoiceStopReason.MAX_DURATION
            hasSpeech && nowMs - checkNotNull(lastVoiceAtMs) >= trailingSilenceMs ->
                if (voicedDurationMs >= minVoicedMs) VoiceStopReason.END_OF_SPEECH else VoiceStopReason.TOO_BRIEF
            else -> null
        }

        val visualScale = max(MIN_VISUAL_RANGE, activationGate * VISUAL_GATE_MULTIPLIER)
        val relativeLevel = ((safeAmplitude - noiseFloor).coerceAtLeast(0f) / visualScale).coerceIn(0f, 1f)
        return VoiceActivitySample(
            level = sqrt(relativeLevel),
            isVoiceActive = voiceActive,
            hasSpeech = hasSpeech,
            voicedDurationMs = voicedDurationMs,
            activationThreshold = activationGate,
            noiseFloor = noiseFloor.toInt(),
            stopReason = stopReason
        )
    }

    private fun activationGate(): Int =
        max(minimumGate.toFloat(), noiseFloor * ACTIVATION_NOISE_RATIO + ACTIVATION_HEADROOM)
            .coerceAtMost(MAX_ADAPTIVE_GATE)
            .toInt()

    private fun releaseGate(): Int =
        max(minimumGate * RELEASE_MINIMUM_RATIO, noiseFloor * RELEASE_NOISE_RATIO + RELEASE_HEADROOM)
            .coerceAtMost(MAX_ADAPTIVE_GATE)
            .toInt()

    private fun updateNoiseFloor(amplitude: Int) {
        // Upward adaptation is quick enough for fans/traffic; downward adaptation is gentler
        // so one quiet sample cannot suddenly make background noise look like speech.
        val bounded = amplitude.toFloat().coerceAtMost(noiseFloor * MAX_NOISE_STEP_RATIO + minimumGate)
        val rate = if (bounded > noiseFloor) NOISE_RISE_RATE else NOISE_FALL_RATE
        noiseFloor += (bounded - noiseFloor) * rate
        noiseFloor = noiseFloor.coerceIn(MIN_NOISE_FLOOR, MAX_NOISE_FLOOR)
    }

    private companion object {
        const val MAX_AMPLITUDE = 32_767
        const val MAX_SAMPLE_GAP_MS = 200L
        const val INITIAL_NOISE_FLOOR = 180f
        const val MIN_NOISE_FLOOR = 40f
        const val MAX_NOISE_FLOOR = 2_800f
        const val ACTIVATION_NOISE_RATIO = 2.15f
        const val RELEASE_NOISE_RATIO = 1.45f
        const val ACTIVATION_HEADROOM = 180f
        const val RELEASE_HEADROOM = 100f
        const val RELEASE_MINIMUM_RATIO = 0.70f
        const val IMMEDIATE_SPEECH_MULTIPLIER = 2.6f
        const val MAX_ADAPTIVE_GATE = 6_500f
        const val MAX_NOISE_STEP_RATIO = 1.8f
        const val NOISE_RISE_RATE = 0.22f
        const val NOISE_FALL_RATE = 0.10f
        const val MIN_VISUAL_RANGE = 6_000f
        const val VISUAL_GATE_MULTIPLIER = 5f
    }
}
