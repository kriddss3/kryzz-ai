package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeechRunTrackerTest {
    @Test fun continuousSpeechIsMeasuredFromItsFirstPoll() {
        val run = SpeechRunTracker(gapToleranceMs = 220L)
        var duration = 0L
        for (t in 0L..300L step 30L) duration = run.observe(true, 1_000L + t)
        assertThat(run.onsetAtMs).isEqualTo(1_000L)
        assertThat(duration).isEqualTo(300L)
    }

    @Test fun shortDipsBetweenSyllablesKeepTheRunAlive() {
        val run = SpeechRunTracker(gapToleranceMs = 220L)
        run.observe(true, 0L)
        run.observe(false, 100L)
        assertThat(run.observe(true, 200L)).isEqualTo(200L)
        assertThat(run.onsetAtMs).isEqualTo(0L)
    }

    @Test fun aLongerQuietEndsTheRun() {
        val run = SpeechRunTracker(gapToleranceMs = 220L)
        run.observe(true, 0L)
        run.observe(true, 100L)
        assertThat(run.observe(false, 400L)).isEqualTo(0L)
        assertThat(run.onsetAtMs).isNull()
        assertThat(run.observe(true, 500L)).isEqualTo(0L)
        assertThat(run.onsetAtMs).isEqualTo(500L)
    }

    @Test fun scatteredSpikesAcrossAReplyNeverAddUp() {
        val run = SpeechRunTracker(gapToleranceMs = 220L)
        var longest = 0L
        for (i in 0 until 20) {
            longest = maxOf(longest, run.observe(true, i * 500L))
            longest = maxOf(longest, run.observe(false, i * 500L + 30L))
        }
        assertThat(longest).isLessThan(VoiceConfig.BARGE_TRIGGER_MS)
    }
}
