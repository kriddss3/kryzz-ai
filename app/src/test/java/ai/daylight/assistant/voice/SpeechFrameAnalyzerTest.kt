package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Test

/**
 * v2.1: the analyser has to tell a voice from wind, hiss and silence. The signals are made
 * here: a voice is a pitched pulse train through two formant resonators, wind is low-passed
 * noise with gusts, hiss is white noise.
 */
class SpeechFrameAnalyzerTest {
    private val fs = VOICE_CAPTURE_SAMPLE_RATE

    @Test fun pitchedVoiceIsVoicedInNearlyEveryFrame() {
        val (voiced, frames) = run(voice(f0 = 130.0, amp = 6_000f))
        assertThat(frames).isAtLeast(45)
        assertThat(voiced.toFloat() / frames).isAtLeast(0.9f)
    }

    @Test fun softAndHighVoicesStillCount() {
        val soft = run(voice(f0 = 140.0, amp = 1_500f))
        val high = run(voice(f0 = 230.0, amp = 5_000f))
        assertThat(soft.first.toFloat() / soft.second).isAtLeast(0.85f)
        assertThat(high.first.toFloat() / high.second).isAtLeast(0.9f)
    }

    @Test fun windIsNeverVoicedHoweverLoud() {
        assertThat(run(wind(amp = 4_000f, cutoffHz = 100.0)).first).isEqualTo(0)
        assertThat(run(wind(amp = 20_000f, cutoffHz = 60.0)).first).isEqualTo(0)
        assertThat(run(wind(amp = 8_000f, cutoffHz = 300.0)).first).isEqualTo(0)
    }

    @Test fun hissAndSilenceAreNotVoiced() {
        val rng = Random(5)
        val hiss = FloatArray(fs) { (rng.nextFloat() * 2f - 1f) * 1_500f }
        assertThat(run(hiss).first).isEqualTo(0)
        assertThat(run(FloatArray(fs)).first).isEqualTo(0)
    }

    @Test fun lowRumbleBarelyMovesThePeakAfterTheHighPass() {
        // A 50 Hz hum at full scale: the 200 Hz high-pass takes most of it away.
        val hum = FloatArray(fs) { i -> (sin(2 * PI * 50 * i / fs) * 20_000).toFloat() }
        var peak = 0
        SpeechFrameAnalyzer { framePeak, _ -> peak = maxOf(peak, framePeak) }.acceptSamples(hum)
        assertThat(peak).isLessThan(2_500)
    }

    @Test fun strictLowSensitivityStillHearsAClearVoice() {
        val low = SensitivityProfile.of(VoiceSensitivity.LOW).minPeriodicity
        val (voiced, frames) = run(voice(f0 = 130.0, amp = 6_000f), minPeriodicity = low)
        assertThat(voiced.toFloat() / frames).isAtLeast(0.85f)
    }

    @Test fun pcmBytesAreReadLittleEndian() {
        val samples = voice(f0 = 150.0, amp = 6_000f)
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            val v = s.toInt().coerceIn(-32_768, 32_767)
            bytes[2 * i] = (v and 0xFF).toByte()
            bytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        var voiced = 0
        var frames = 0
        SpeechFrameAnalyzer { _, v -> frames++; if (v) voiced++ }.accept(bytes, bytes.size)
        assertThat(frames).isEqualTo(samples.size / (fs * SpeechFrameAnalyzer.FRAME_MS / 1_000))
        assertThat(voiced.toFloat() / frames).isAtLeast(0.9f)
    }

    @Test fun sensitivityProfilesGetStricterTowardsLow() {
        val low = SensitivityProfile.of(VoiceSensitivity.LOW)
        val normal = SensitivityProfile.of(VoiceSensitivity.NORMAL)
        val high = SensitivityProfile.of(VoiceSensitivity.HIGH)
        assertThat(low.gateScale).isGreaterThan(normal.gateScale)
        assertThat(normal.gateScale).isGreaterThan(high.gateScale)
        assertThat(low.minPeriodicity).isGreaterThan(high.minPeriodicity)
        assertThat(low.confirmMs).isGreaterThan(high.confirmMs)
        assertThat(low.minSpeechMs).isGreaterThan(high.minSpeechMs)
        assertThat(low.bargeSoftTriggerMs).isGreaterThan(high.bargeSoftTriggerMs)
        assertThat(VoiceSensitivity.from("low")).isEqualTo(VoiceSensitivity.LOW)
        assertThat(VoiceSensitivity.from("nonsense")).isEqualTo(VoiceSensitivity.NORMAL)
    }

    private fun run(samples: FloatArray, minPeriodicity: Float = SensitivityProfile.of(VoiceSensitivity.NORMAL).minPeriodicity): Pair<Int, Int> {
        var voiced = 0
        var frames = 0
        SpeechFrameAnalyzer(minPeriodicity = minPeriodicity) { _, v ->
            frames++
            if (v) voiced++
        }.acceptSamples(samples)
        // The first frame has no history to compare with.
        return voiced to (frames - 1)
    }

    private fun voice(f0: Double, amp: Float, seconds: Double = 1.0): FloatArray {
        val n = (fs * seconds).toInt()
        val rng = Random(3)
        val pulses = DoubleArray(n)
        var t = 0.0
        while (t < n) {
            pulses[t.toInt()] = 1.0
            t += fs / (f0 * (1 + 0.01 * (rng.nextDouble() * 2 - 1)))
        }
        val shaped = resonate(resonate(pulses, 700.0, 110.0), 1_200.0, 120.0)
        val max = shaped.maxOf { abs(it) }.coerceAtLeast(1e-9)
        return FloatArray(n) { i -> (shaped[i] / max * amp + (rng.nextDouble() * 2 - 1) * 80).toFloat() }
    }

    private fun resonate(x: DoubleArray, freq: Double, bandwidth: Double): DoubleArray {
        val r = exp(-PI * bandwidth / fs)
        val theta = 2 * PI * freq / fs
        val y = DoubleArray(x.size)
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val out = x[i] + 2 * r * cos(theta) * y1 - r * r * y2
            y2 = y1
            y1 = out
            y[i] = out
        }
        return y
    }

    private fun wind(amp: Float, cutoffHz: Double): FloatArray {
        val rng = Random(7)
        val a = exp(-2 * PI * cutoffHz / fs)
        val y = DoubleArray(fs)
        var p = 0.0
        for (i in 0 until fs) {
            p = a * p + (1 - a) * (rng.nextDouble() * 2 - 1)
            y[i] = p * (1 + 0.8 * sin(6.0 * i / fs))
        }
        val max = y.maxOf { abs(it) }.coerceAtLeast(1e-9)
        return FloatArray(fs) { i -> (y[i] / max * amp).toFloat() }
    }
}
