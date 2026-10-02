package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdaptiveEndOfSpeechDetectorTest {
    @Test
    fun confirmedSpeechStopsAfterTrailingSilence() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        feed(detector, 0L, 300L, 50L, 100)
        feed(detector, 350L, 750L, 50L, 3_000)
        val lastVoice = 750L

        val beforeSilenceDeadline = detector.observe(100, lastVoice + VoiceConfig.SILENCE_STOP_MS - 50L)
        val atSilenceDeadline = detector.observe(100, lastVoice + VoiceConfig.SILENCE_STOP_MS)

        assertThat(beforeSilenceDeadline.hasSpeech).isTrue()
        assertThat(beforeSilenceDeadline.stopReason).isNull()
        assertThat(atSilenceDeadline.stopReason).isEqualTo(VoiceStopReason.END_OF_SPEECH)
    }

    @Test
    fun isolatedAmplitudeSpikeDoesNotBecomeSpeech() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        feed(detector, 0L, 300L, 50L, 90)
        detector.observe(5_000, 350L)
        val afterSpike = detector.observe(90, 400L)
        val muchLater = detector.observe(90, 2_000L)

        assertThat(afterSpike.hasSpeech).isFalse()
        assertThat(muchLater.hasSpeech).isFalse()
        assertThat(muchLater.stopReason).isNull()
    }

    @Test
    fun confirmedButTooBriefNoiseIsDiscardedAfterTrailingSilence() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        feed(detector, 0L, 300L, 50L, 80)
        feed(detector, 350L, 450L, 50L, 4_000)
        val lastVoice = 450L

        val afterTrailingSilence = detector.observe(80, lastVoice + VoiceConfig.SILENCE_STOP_MS)

        assertThat(afterTrailingSilence.hasSpeech).isTrue()
        assertThat(afterTrailingSilence.voicedDurationMs).isLessThan(VoiceConfig.MIN_SPEECH_MS)
        assertThat(afterTrailingSilence.stopReason).isEqualTo(VoiceStopReason.TOO_BRIEF)
        assertThat(detector.hasUsableSpeech).isFalse()
    }

    @Test
    fun naturalMidSentencePauseDoesNotCutOffTheTurn() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        feed(detector, 0L, 300L, 50L, 100)
        val firstSpeechEnd = 700L
        feed(detector, 350L, firstSpeechEnd, 50L, 3_200)

        val pauseEnd = firstSpeechEnd + VoiceConfig.SILENCE_STOP_MS - 80L
        val midPause = feed(detector, firstSpeechEnd + 50L, pauseEnd, 50L, 100)
        assertThat(midPause.stopReason).isNull()

        val secondSpeechEnd = pauseEnd + 300L
        feed(detector, pauseEnd + 50L, secondSpeechEnd, 50L, 2_800)
        val finalSilence = detector.observe(100, secondSpeechEnd + VoiceConfig.SILENCE_STOP_MS)
        assertThat(finalSilence.stopReason).isEqualTo(VoiceStopReason.END_OF_SPEECH)
    }

    @Test
    fun learnsSteadyBackgroundNoiseThenRecognisesSpeechAboveIt() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        val room = feed(detector, 0L, 450L, 50L, 900)

        assertThat(room.hasSpeech).isFalse()
        assertThat(room.activationThreshold).isGreaterThan(900)

        val speechEnd = 900L
        val speech = feed(detector, 500L, speechEnd, 50L, 3_500)
        val ended = detector.observe(900, speechEnd + VoiceConfig.SILENCE_STOP_MS)
        assertThat(speech.hasSpeech).isTrue()
        assertThat(ended.stopReason).isEqualTo(VoiceStopReason.END_OF_SPEECH)
    }

    @Test
    fun loudSpeechAtStartupIsNotLostDuringNoiseCalibration() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)

        val speech = feed(detector, 0L, 450L, 50L, 5_000)

        assertThat(speech.hasSpeech).isTrue()
        assertThat(speech.voicedDurationMs).isAtLeast(VoiceConfig.MIN_SPEECH_MS)
    }

    @Test
    fun bargeInCalibrationLearnsLoudSteadySpeakerLeakage() {
        val detector = AdaptiveEndOfSpeechDetector(allowImmediateSpeechDuringCalibration = false)
        detector.reset(0L)

        val speakerLeakage = feed(detector, 0L, 600L, 50L, 5_000)

        assertThat(speakerLeakage.activationThreshold).isGreaterThan(5_000)
        assertThat(speakerLeakage.hasSpeech).isFalse()
    }

    @Test
    fun maximumDurationStopsSilenceWithoutPretendingItWasSpeech() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        feed(detector, 0L, 1_000L, 50L, 80)

        val capped = detector.observe(80, VoiceConfig.MAX_RECORD_MS)

        assertThat(capped.hasSpeech).isFalse()
        assertThat(capped.stopReason).isEqualTo(VoiceStopReason.MAX_DURATION)
    }

    @Test
    fun seededBargeInKeepsTheTriggeringSpeechInTheUtterance() {
        val detector = AdaptiveEndOfSpeechDetector()
        val start = 10_000L
        detector.reset(start)
        detector.seedSpeech(nowMs = start + 300L, confirmedSpeechMs = 300L)

        val waiting = detector.observe(100, start + 300L + VoiceConfig.SILENCE_STOP_MS - 50L)
        val ended = detector.observe(100, start + 300L + VoiceConfig.SILENCE_STOP_MS)

        assertThat(waiting.hasSpeech).isTrue()
        assertThat(waiting.stopReason).isNull()
        assertThat(ended.stopReason).isEqualTo(VoiceStopReason.END_OF_SPEECH)
    }

    @Test
    fun silenceMsCountsFromTheLastVoicedSample() {
        val detector = AdaptiveEndOfSpeechDetector()
        detector.reset(0L)
        assertThat(detector.silenceMs(100L)).isEqualTo(0L)
        feed(detector, 0L, 300L, 50L, 100)
        feed(detector, 350L, 750L, 50L, 3_000)
        detector.observe(100, 800L)

        assertThat(detector.silenceMs(1_000L)).isEqualTo(250L)
    }

    private fun feed(
        detector: AdaptiveEndOfSpeechDetector,
        startMs: Long,
        endMs: Long,
        stepMs: Long,
        amplitude: Int
    ): VoiceActivitySample {
        var sample = detector.observe(amplitude, startMs)
        var time = startMs + stepMs
        while (time <= endMs) {
            sample = detector.observe(amplitude, time)
            time += stepMs
        }
        return sample
    }
}
