package ai.daylight.assistant.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How readily the voice mic takes a sound for speech (v2.1, Tune > Voice). Low ignores wind,
 * traffic and voices further away; High hears soft speech in a quiet room.
 */
enum class VoiceSensitivity(val label: String, val detail: String) {
    LOW("Low", "Outdoors, wind, busy rooms"),
    NORMAL("Normal", "Most places"),
    HIGH("High", "Quiet rooms, soft voices");

    companion object {
        fun from(value: String?): VoiceSensitivity = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
    }
}

/**
 * The numbers one [VoiceSensitivity] stands for. [gateScale] lifts the adaptive loudness gate,
 * [minPeriodicity] is how clearly pitched a frame must be to count as voice, and the times say
 * how long voice has to last before it starts a turn, is sent, or talks over a reply.
 */
internal data class SensitivityProfile(
    val gateScale: Float,
    val minPeriodicity: Float,
    val confirmMs: Long,
    val minSpeechMs: Long,
    val bargeTriggerMs: Long,
    val bargeSoftTriggerMs: Long,
) {
    companion object {
        fun of(sensitivity: VoiceSensitivity): SensitivityProfile = when (sensitivity) {
            VoiceSensitivity.LOW -> SensitivityProfile(1.6f, 0.58f, 260L, 450L, 300L, 600L)
            VoiceSensitivity.NORMAL -> SensitivityProfile(1.15f, 0.48f, 180L, 320L, 200L, 420L)
            VoiceSensitivity.HIGH -> SensitivityProfile(0.85f, 0.38f, 100L, 220L, 140L, 300L)
        }
    }
}

/** What the mic heard since the last poll: the loudest high-passed sample and how many 20ms frames sounded like a voice. */
internal data class MicReading(val peak: Int, val voicedFrames: Int, val frames: Int) {
    /** At least one frame since the last poll was voiced (or inside a voiced word's hangover). */
    val voiceLike: Boolean get() = voicedFrames > 0

    companion object {
        val SILENT = MicReading(0, 0, 0)
    }
}

/**
 * Tells speech from noise in raw 16 kHz PCM16, frame by frame (v2.1).
 *
 * The old detector only watched peak loudness, so a gust of wind, a door or a passing car
 * looked exactly like someone talking. Two cheap checks fix most of that:
 *
 *  * a 200 Hz high-pass filter runs before anything is measured. Wind and handling rumble
 *    sit mostly below it, while speech lives above it;
 *  * a voiced frame must be *periodic*: the normalised autocorrelation of a 40 ms window has
 *    a clear peak at a pitch period between 70 and 400 Hz. Vowels have one, wind, hiss,
 *    rustle and clicks do not.
 *
 * A short hangover keeps a word voiced across its unvoiced consonants ("s", "f", "t").
 * The analysis runs at 8 kHz (pairs of samples averaged), about 25k multiply-adds per frame.
 * Android-free, so it is unit tested with synthetic signals.
 */
internal class SpeechFrameAnalyzer(
    private val sampleRate: Int = VOICE_CAPTURE_SAMPLE_RATE,
    var minPeriodicity: Float = SensitivityProfile.of(VoiceSensitivity.NORMAL).minPeriodicity,
    private val minRms: Float = MIN_VOICED_RMS,
    hangoverMs: Int = 160,
    private val onFrame: (peak: Int, voiced: Boolean) -> Unit,
) {
    private val frameSize = sampleRate * FRAME_MS / 1_000
    private val hangoverFrames = (hangoverMs / FRAME_MS).coerceAtLeast(0)

    // Biquad high-pass (RBJ cookbook, Butterworth Q), coefficients normalised by a0.
    private val b0: Float
    private val b1: Float
    private val b2: Float
    private val a1: Float
    private val a2: Float
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    private val frame = FloatArray(frameSize)
    private var filled = 0
    private var framePeak = 0f

    /** The previous and current frame at 8 kHz, so the autocorrelation sees 40 ms. */
    private val window = FloatArray(frameSize) // 2 frames of frameSize/2 decimated samples
    private val emphasised = FloatArray(frameSize)
    private var haveHistory = false
    private var hangover = 0

    init {
        val w0 = 2.0 * PI * HIGH_PASS_HZ / sampleRate
        val alpha = sin(w0) / (2.0 * 0.7071)
        val cosW = cos(w0)
        val a0 = 1.0 + alpha
        b0 = (((1.0 + cosW) / 2.0) / a0).toFloat()
        b1 = ((-(1.0 + cosW)) / a0).toFloat()
        b2 = (((1.0 + cosW) / 2.0) / a0).toFloat()
        a1 = ((-2.0 * cosW) / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    /** Feeds little-endian PCM16 bytes; [onFrame] runs once per finished 20 ms frame. */
    fun accept(data: ByteArray, length: Int = data.size) {
        val limit = length.coerceIn(0, data.size) and 1.inv()
        var i = 0
        while (i < limit) {
            val raw = ((data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)).toShort().toFloat()
            acceptSample(raw)
            i += 2
        }
    }

    /** Feeds samples already on the PCM16 scale (-32768..32767). Used by the tests. */
    fun acceptSamples(samples: FloatArray) {
        for (s in samples) acceptSample(s)
    }

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        filled = 0
        framePeak = 0f
        haveHistory = false
        hangover = 0
    }

    private fun acceptSample(x: Float) {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        frame[filled++] = y
        val magnitude = abs(y)
        if (magnitude > framePeak) framePeak = magnitude
        if (filled == frameSize) {
            finishFrame()
            filled = 0
            framePeak = 0f
        }
    }

    private fun finishFrame() {
        val half = frameSize / 2
        // Slide the 8 kHz window: the old current frame becomes the history.
        System.arraycopy(window, half, window, 0, half)
        var energy = 0.0
        for (k in 0 until half) {
            val a = frame[2 * k]
            val b = frame[2 * k + 1]
            window[half + k] = (a + b) * 0.5f
            energy += (a * a + b * b).toDouble()
        }
        val rms = sqrt(energy / frameSize).toFloat()
        val periodic = haveHistory && rms >= minRms && periodicity(preEmphasis(window, emphasised)) >= minPeriodicity
        haveHistory = true
        val voiced = when {
            periodic -> {
                hangover = hangoverFrames
                true
            }
            hangover > 0 && rms >= minRms * 0.5f -> {
                hangover--
                true
            }
            else -> {
                hangover = 0
                false
            }
        }
        onFrame(framePeak.toInt().coerceAtMost(32_767), voiced)
    }

    companion object {
        const val FRAME_MS = 20
        const val HIGH_PASS_HZ = 200.0

        /** About -48 dBFS after the high-pass: quieter frames are never voiced. */
        const val MIN_VOICED_RMS = 130f

        /** Pitch search range at 8 kHz: 400 Hz (lag 20) down to 70 Hz (lag 114). */
        private const val MIN_LAG = 20
        private const val MAX_LAG = 114

        /**
         * y[n] = x[n] - 0.9 x[n-1] into [out]. It flattens the downward tilt that wind and
         * rumble keep even after the high-pass (their samples stay alike over a few ms, which
         * the autocorrelation would read as pitch), while a voice's pitch pulses survive it.
         */
        fun preEmphasis(x: FloatArray, out: FloatArray): FloatArray {
            if (x.isEmpty()) return out
            out[0] = x[0] * 0.1f
            for (i in 1 until x.size) out[i] = x[i] - 0.9f * x[i - 1]
            return out
        }

        /**
         * The highest normalised autocorrelation of [x] over the pitch lags, 0..1. A pitched
         * voice scores 0.6-0.95; wind, hiss and clicks stay well under 0.4.
         */
        fun periodicity(x: FloatArray): Float {
            val n = x.size
            if (n <= MAX_LAG + 8) return 0f
            var best = 0f
            for (lag in MIN_LAG..MAX_LAG) {
                var cross = 0.0
                var e1 = 0.0
                var e2 = 0.0
                val count = n - lag
                for (i in 0 until count) {
                    val a = x[i]
                    val b = x[i + lag]
                    cross += a * b
                    e1 += a * a
                    e2 += b * b
                }
                if (e1 <= 0.0 || e2 <= 0.0) continue
                val score = (cross / sqrt(e1 * e2)).toFloat()
                if (score > best) best = score
            }
            return best.coerceIn(0f, 1f)
        }
    }
}
