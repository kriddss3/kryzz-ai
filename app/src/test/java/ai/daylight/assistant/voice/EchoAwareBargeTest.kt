package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EchoAwareBargeTest {
    @Test
    fun speakerLeakageNeverCountsAsTheUser() {
        val gate = EchoAwareBarge()
        val kinds = poll(gate, times = 40, mic = 0.22f, player = 0.55f, playing = true, voiceLike = true)
        assertThat(kinds).doesNotContain(BargeKind.OUTSHOUT)
        assertThat(kinds).doesNotContain(BargeKind.SOFT)
    }

    @Test
    fun leakLouderThanThePriorIsStillEcho() {
        val gate = EchoAwareBarge()
        val kinds = poll(gate, times = 40, mic = 0.48f, player = 0.40f, playing = true, voiceLike = true)
        assertThat(kinds.toSet()).containsExactly(BargeKind.NONE)
    }

    @Test
    fun aVoiceThatRisesAboveTheLeakCounts() {
        val gate = EchoAwareBarge()
        poll(gate, times = 20, mic = 0.18f, player = 0.50f, playing = true, voiceLike = true)
        val heard = poll(gate, times = 8, mic = 0.72f, player = 0.50f, playing = true, voiceLike = true)
        assertThat(heard).contains(BargeKind.OUTSHOUT)
    }

    @Test
    fun echoCancellationLetsANormalVoiceThroughTheSoftPath() {
        val gate = EchoAwareBarge()
        poll(gate, times = 24, mic = 0.02f, player = 0.60f, playing = true, voiceLike = false)
        val heard = poll(gate, times = 6, mic = 0.12f, player = 0.60f, playing = true, voiceLike = true)
        assertThat(heard.last()).isEqualTo(BargeKind.SOFT)
    }

    @Test
    fun perfectEchoCancellationWithSilentMicStillLearnsLowCouplingAndAllowsNormalVoice() {
        val gate = EchoAwareBarge()
        poll(gate, times = 15, mic = 0.002f, player = 0.60f, playing = true, voiceLike = false)
        val heard = gate.classify(micLevel = 0.15f, playerLevel = 0.60f, playing = true, voiceLike = true)
        assertThat(heard).isAnyOf(BargeKind.SOFT, BargeKind.OUTSHOUT)
    }

    @Test
    fun aPauseDoesNotMakeTheNextWordLookLikeTheUser() {
        val gate = EchoAwareBarge()
        poll(gate, times = 16, mic = 0.22f, player = 0.55f, playing = true, voiceLike = true)
        poll(gate, times = 10, mic = 0.02f, player = 0.55f, playing = true, voiceLike = false)
        val again = poll(gate, times = 12, mic = 0.22f, player = 0.55f, playing = true, voiceLike = true)
        assertThat(again).doesNotContain(BargeKind.OUTSHOUT)
        assertThat(again).doesNotContain(BargeKind.SOFT)
    }

    @Test
    fun unvoicedNoiseNeverBarges() {
        val gate = EchoAwareBarge()
        val kind = gate.classify(0.9f, 0.2f, playing = true, voiceLike = false)
        assertThat(kind).isEqualTo(BargeKind.NONE)
    }

    @Test
    fun aDeadPlaybackMeterIgnoresOrdinaryLeakage() {
        val gate = EchoAwareBarge()
        val leak = poll(gate, times = 30, mic = 0.16f, player = 0f, playing = true, voiceLike = true)
        assertThat(leak.toSet()).containsExactly(BargeKind.NONE)
        val shout = gate.classify(0.55f, 0f, playing = true, voiceLike = true)
        assertThat(shout).isEqualTo(BargeKind.OUTSHOUT)
    }

    @Test
    fun theUserCanCutInBeforePlaybackStarts() {
        val gate = EchoAwareBarge()
        val kind = gate.classify(0.20f, 0f, playing = false, voiceLike = true)
        assertThat(kind).isEqualTo(BargeKind.OUTSHOUT)
    }

    @Test
    fun theTailAfterPlaybackDoesNotReplayTheEcho() {
        val gate = EchoAwareBarge()
        poll(gate, times = 20, mic = 0.22f, player = 0.55f, playing = true, voiceLike = true)
        val tail = poll(gate, times = 8, mic = 0.20f, player = 0f, playing = false, voiceLike = true)
        assertThat(tail.toSet()).containsExactly(BargeKind.NONE)
        val user = gate.classify(0.55f, 0f, playing = false, voiceLike = true)
        assertThat(user).isEqualTo(BargeKind.OUTSHOUT)
    }

    @Test
    fun visualizerSilenceIsQuiet() {
        val silence = ByteArray(32) { 128.toByte() }
        assertThat(visualizerWaveformLevel(silence)).isEqualTo(0f)
    }

    @Test
    fun visualizerFullScaleIsLoud() {
        val swing = ByteArray(32) { index -> if (index % 2 == 0) 0 else 255.toByte() }
        assertThat(visualizerWaveformLevel(swing)).isGreaterThan(0.9f)
    }

    private fun poll(
        gate: EchoAwareBarge,
        times: Int,
        mic: Float,
        player: Float,
        playing: Boolean,
        voiceLike: Boolean
    ): List<BargeKind> {
        val kinds = ArrayList<BargeKind>(times)
        repeat(times) {
            kinds += gate.classify(
                micLevel = mic,
                playerLevel = player,
                playing = playing,
                voiceLike = voiceLike
            )
        }
        return kinds
    }
}
