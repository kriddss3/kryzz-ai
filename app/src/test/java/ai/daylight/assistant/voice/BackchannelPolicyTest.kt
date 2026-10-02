package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class BackchannelPolicyTest {
    private fun steadyPolicy() = BackchannelPolicy(random = Random(1), talkJitterMs = 0L)

    @Test fun staysQuietUntilTheUserHasTalkedForAWhile() {
        val policy = steadyPolicy()
        assertThat(policy.shouldSpeak(voicedMs = 2_000L, silenceMs = 300L)).isFalse()
        assertThat(policy.shouldSpeak(voicedMs = 3_500L, silenceMs = 300L)).isTrue()
    }

    @Test fun onlySpeaksInAShortPauseBetweenPhrases() {
        val policy = steadyPolicy()
        assertThat(policy.shouldSpeak(5_000L, 0L)).isFalse()
        assertThat(policy.shouldSpeak(5_000L, 200L)).isFalse()
        assertThat(policy.shouldSpeak(5_000L, 500L)).isFalse()
        assertThat(policy.shouldSpeak(5_000L, 300L)).isTrue()
    }

    @Test fun needsAnotherStretchOfTalkBeforeTheNextSound() {
        val policy = steadyPolicy()
        assertThat(policy.shouldSpeak(4_000L, 300L)).isTrue()
        // Next poll of the same pause.
        assertThat(policy.shouldSpeak(4_100L, 330L)).isFalse()
        assertThat(policy.shouldSpeak(7_000L, 300L)).isFalse()
        assertThat(policy.shouldSpeak(7_500L, 300L)).isTrue()
    }

    @Test fun capsTheSoundsPerUtteranceUntilReset() {
        val policy = steadyPolicy()
        var voiced = 0L
        repeat(BackchannelPolicy.MAX_PER_UTTERANCE) {
            voiced += 4_000L
            assertThat(policy.shouldSpeak(voiced, 300L)).isTrue()
        }
        assertThat(policy.shouldSpeak(voiced + 10_000L, 300L)).isFalse()
        policy.reset()
        assertThat(policy.shouldSpeak(4_000L, 300L)).isTrue()
    }

    @Test fun jitteredStretchStaysWithinItsRange() {
        val policy = BackchannelPolicy(random = Random(7))
        assertThat(policy.shouldSpeak(BackchannelPolicy.MIN_TALK_MS - 1L, 300L)).isFalse()
        assertThat(policy.shouldSpeak(BackchannelPolicy.MIN_TALK_MS + BackchannelPolicy.TALK_JITTER_MS, 300L)).isTrue()
    }

    @Test fun pauseWindowClosesBeforeTheTurnEnds() {
        assertThat(BackchannelPolicy.PAUSE_MIN_MS).isAtLeast(VoiceConfig.SPECULATIVE_STT_SILENCE_MS)
        assertThat(BackchannelPolicy.PAUSE_MAX_MS).isLessThan(VoiceConfig.SILENCE_STOP_MS)
    }
}
