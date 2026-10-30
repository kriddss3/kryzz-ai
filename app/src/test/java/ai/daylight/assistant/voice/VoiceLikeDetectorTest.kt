package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** v2.1: loudness alone no longer opens a voice turn; the mic must also sound like a voice. */
class VoiceLikeDetectorTest {
    @Test fun loudSoundThatIsNotAVoiceNeverBecomesSpeech() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        var sample = detector.observe(100, 0L)
        var t = 30L
        while (t <= 2_000L) {
            sample = detector.observe(9_000, t, voiceLike = false)
            t += 30L
        }
        assertThat(sample.hasSpeech).isFalse()
        assertThat(sample.stopReason).isNull()
        // The wind taught it a higher floor, so the gate rose with it.
        assertThat(sample.activationThreshold).isGreaterThan(VoiceConfig.SPEECH_THRESHOLD)
    }

    @Test fun windAfterSpeechCountsAsSilenceSoTheTurnStillEnds() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        feed(detector, 0L, 300L, 100, voiceLike = false)
        feed(detector, 330L, 900L, 3_500, voiceLike = true)
        val windEnd = 900L + VoiceConfig.SILENCE_STOP_MS + 60L
        val last = feed(detector, 930L, windEnd, 6_000, voiceLike = false)
        assertThat(last.stopReason).isEqualTo(VoiceStopReason.END_OF_SPEECH)
    }

    @Test fun lowSensitivityNeedsLongerVoiceToStartAndToSend() {
        val strict = AdaptiveEndOfSpeechDetector().apply { configure(SensitivityProfile.of(VoiceSensitivity.LOW)) }
        strict.reset(0L)
        feed(strict, 0L, 450L, 100, voiceLike = false)
        // 150ms of voice: enough for the old 80ms confirm, not for Low's 260ms.
        val brief = feed(strict, 480L, 630L, 4_000, voiceLike = true)
        assertThat(brief.hasSpeech).isFalse()

        val relaxed = AdaptiveEndOfSpeechDetector().apply { configure(SensitivityProfile.of(VoiceSensitivity.HIGH)) }
        relaxed.reset(0L)
        feed(relaxed, 0L, 450L, 100, voiceLike = false)
        val same = feed(relaxed, 480L, 630L, 4_000, voiceLike = true)
        assertThat(same.hasSpeech).isTrue()
    }

    @Test fun lowSensitivityRaisesTheGate() {
        val normal = AdaptiveEndOfSpeechDetector().apply { configure(SensitivityProfile.of(VoiceSensitivity.NORMAL)) }
        val low = AdaptiveEndOfSpeechDetector().apply { configure(SensitivityProfile.of(VoiceSensitivity.LOW)) }
        normal.reset(0L)
        low.reset(0L)
        val a = feed(normal, 0L, 450L, 300, voiceLike = false)
        val b = feed(low, 0L, 450L, 300, voiceLike = false)
        assertThat(b.activationThreshold).isGreaterThan(a.activationThreshold)
    }

    private fun feed(
        detector: AdaptiveEndOfSpeechDetector,
        startMs: Long,
        endMs: Long,
        amplitude: Int,
        voiceLike: Boolean,
        stepMs: Long = 30L
    ): VoiceActivitySample {
        var sample = detector.observe(amplitude, startMs, voiceLike)
        var time = startMs + stepMs
        while (time <= endMs) {
            sample = detector.observe(amplitude, time, voiceLike)
            time += stepMs
        }
        return sample
    }
}
