package ai.daylight.assistant.ui.voice

import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.ui.dot.mascot.SquidPose
import ai.daylight.assistant.voice.VoicePhase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceSessionTest {
    private fun msg(role: String, content: String, n: Int) =
        MessageEntity(id = "m$n", conversationId = "c", role = role, content = content, createdAt = n.toLong())

    private fun user(text: String) = VoiceTurn(fromUser = true, text = text)
    private fun kryzz(text: String) = VoiceTurn(fromUser = false, text = text)

    // --- waveform history ---------------------------------------------------------------

    @Test fun waveHistoryKeepsTheNewestColumnsOldestFirst() {
        val wave = VoiceWaveHistory(cols = 3)
        listOf(0.1f, 0.2f, 0.3f, 0.4f).forEach { wave.offer(it); wave.tick() }
        wave.offer(0.5f)
        assertThat(wave.tick()).containsExactly(0.3f, 0.4f, 0.5f).inOrder()
    }

    @Test fun waveHistoryHoldsTheLoudestLevelBetweenTicks() {
        val wave = VoiceWaveHistory(cols = 4)
        wave.offer(0.2f); wave.offer(0.9f); wave.offer(0.3f)
        assertThat(wave.tick()).containsExactly(0.9f)
    }

    @Test fun aSteadyLevelKeepsFillingColumns() {
        // A StateFlow does not repeat an unchanged value, so the next tick starts from the latest one.
        val wave = VoiceWaveHistory(cols = 4)
        wave.offer(0.6f)
        wave.tick()
        assertThat(wave.tick()).containsExactly(0.6f, 0.6f).inOrder()
    }

    @Test fun waveHistoryClampsAndIgnoresNaN() {
        val wave = VoiceWaveHistory(cols = 3)
        wave.offer(4f); wave.tick()
        wave.clear()
        wave.offer(Float.NaN)
        assertThat(wave.tick()).containsExactly(0f)
        wave.offer(-1f)
        assertThat(wave.tick()).containsExactly(0f, 0f)
    }

    @Test fun peaksAreYellowOnlyWhileTheUserSpeaks() {
        // Kit: of 15 rows, the outer 3 on each side (distance 5 to 7 from the centre).
        assertThat(voiceWavePeakFrom(VoicePhase.LISTENING, muted = false, rows = 15)).isEqualTo(5)
        assertThat(voiceWavePeakFrom(VoicePhase.LISTENING, muted = false, rows = 9)).isEqualTo(3)
        assertThat(voiceWavePeakFrom(VoicePhase.LISTENING, muted = true, rows = 15)).isNull()
        assertThat(voiceWavePeakFrom(VoicePhase.SPEAKING, muted = false, rows = 15)).isNull()
        assertThat(voiceWavePeakFrom(VoicePhase.PROCESSING, muted = false, rows = 15)).isNull()
    }

    // --- transcript ---------------------------------------------------------------------

    @Test fun turnsSkipToolRowsAndCleanText() {
        val turns = voiceTurns(
            listOf(
                msg("USER", "hello", 1),
                msg("TOOL", "{\"result\":1}", 2),
                msg("ASSISTANT", "[happy] **Hi** there, see [the docs](https://x.io)", 3),
                msg("ASSISTANT", "   ", 4)
            )
        )
        assertThat(turns).containsExactly(user("hello"), kryzz("Hi there, see the docs")).inOrder()
    }

    @Test fun turnsKeepOnlyTheLastFew() {
        val messages = (1..9).map { msg(if (it % 2 == 1) "USER" else "ASSISTANT", "line $it", it) }
        assertThat(voiceTurns(messages).map { it.text }).containsExactly("line 6", "line 7", "line 8", "line 9").inOrder()
    }

    @Test fun listeningShowsKryzzsLastLineThenYourTurn() {
        val lines = voiceLines(VoicePhase.LISTENING, "", muted = false, turns = listOf(user("hi"), kryzz("Hello!")))
        assertThat(lines).isEqualTo(VoiceLines(previous = "Hello!", current = null, placeholder = "Your turn."))
        assertThat(voiceLines(VoicePhase.LISTENING, "", muted = true, turns = emptyList()))
            .isEqualTo(VoiceLines(previous = null, current = null, placeholder = "Mic muted."))
    }

    @Test fun thinkingShowsTheWordsHeardUnderTheLastReply() {
        val before = listOf(user("hi"), kryzz("Hello!"))
        // Transcribed, not saved yet.
        assertThat(voiceLines(VoicePhase.PROCESSING, "what's the weather", false, before))
            .isEqualTo(VoiceLines(previous = "Hello!", current = "what's the weather"))
        // Saved, with the new reply already streaming in.
        val saved = before + user("what's the weather") + kryzz("It is sun")
        assertThat(voiceLines(VoicePhase.PROCESSING, "what's the weather", false, saved))
            .isEqualTo(VoiceLines(previous = "Hello!", current = "what's the weather"))
        // An earlier, shorter "yes" is not the turn just heard.
        val yes = listOf(user("yes"), kryzz("Sure."))
        assertThat(voiceLines(VoicePhase.PROCESSING, "yes please", false, yes))
            .isEqualTo(VoiceLines(previous = "Sure.", current = "yes please"))
        // Still transcribing: only the last line.
        assertThat(voiceLines(VoicePhase.PROCESSING, "", false, before))
            .isEqualTo(VoiceLines(previous = "Hello!", current = null))
    }

    @Test fun speakingShowsTheReplyUnderTheWordsHeard() {
        val turns = listOf(user("hi"), kryzz("Hello!"), user("what's the weather"), kryzz("Sunny, 21 degrees."))
        assertThat(voiceLines(VoicePhase.SPEAKING, "what's the weather", false, turns))
            .isEqualTo(VoiceLines(previous = "what's the weather", current = "Sunny, 21 degrees."))
        // The reply row has not landed yet: no stale reply as the current line.
        val pending = listOf(user("hi"), kryzz("Hello!"), user("what's the weather"))
        assertThat(voiceLines(VoicePhase.SPEAKING, "what's the weather", false, pending))
            .isEqualTo(VoiceLines(previous = "what's the weather", current = null))
    }

    @Test fun theOverlaysShortTranscriptStillMatchesTheSavedRow() {
        val long = "tell me ".repeat(40).trim()
        val turns = listOf(user(long), kryzz("Okay."))
        val lines = voiceLines(VoicePhase.SPEAKING, long.take(200), false, turns)
        assertThat(lines.current).isEqualTo("Okay.")
    }

    @Test fun displayTextDropsEmotionTagsAndCode() {
        assertThat(voiceDisplayText("[excited] Done! ```kotlin\nval x = 1\n``` [1]")).isEqualTo("Done! Code block omitted.")
    }

    @Test fun longRepliesStepDownASize() {
        assertThat(voiceTextSize(40)).isEqualTo(VoiceTextSize.LARGE)
        assertThat(voiceTextSize(110)).isEqualTo(VoiceTextSize.LARGE)
        assertThat(voiceTextSize(200)).isEqualTo(VoiceTextSize.MEDIUM)
        assertThat(voiceTextSize(600)).isEqualTo(VoiceTextSize.SMALL)
    }

    // --- header and button ----------------------------------------------------------------

    @Test fun captionShowsThePhaseAndItsClock() {
        assertThat(voiceStatusCaption(VoicePhase.LISTENING, false, false, 14)).isEqualTo("LISTENING, 00:14")
        assertThat(voiceStatusCaption(VoicePhase.SPEAKING, false, false, 75)).isEqualTo("SPEAKING, 01:15")
        assertThat(voiceStatusCaption(VoicePhase.PROCESSING, false, false, 3)).isEqualTo("THINKING, 00:03")
        assertThat(voiceStatusCaption(VoicePhase.PROCESSING, true, false, 3)).isEqualTo("CHECKING ONLINE, 00:03")
        assertThat(voiceStatusCaption(VoicePhase.LISTENING, false, true, 0)).isEqualTo("MUTED, 00:00")
    }

    @Test fun theRedDotMeansTheMicIsOpen() {
        assertThat(voiceMicLive(VoicePhase.LISTENING, muted = false)).isTrue()
        // Barge-in: the mic stays open while Kryzz speaks.
        assertThat(voiceMicLive(VoicePhase.SPEAKING, muted = false)).isTrue()
        assertThat(voiceMicLive(VoicePhase.PROCESSING, muted = false)).isFalse()
        assertThat(voiceMicLive(VoicePhase.LISTENING, muted = true)).isFalse()
        assertThat(voiceMicLive(VoicePhase.IDLE, muted = false)).isFalse()
    }

    @Test fun coreIconShowsWhatATapDoes() {
        assertThat(voiceCoreIcon(VoicePhase.LISTENING, muted = false)).isEqualTo(R.drawable.ic_kz_mic)
        assertThat(voiceCoreIcon(VoicePhase.LISTENING, muted = true)).isEqualTo(R.drawable.ic_kz_mic_off)
        assertThat(voiceCoreIcon(VoicePhase.PROCESSING, muted = false)).isEqualTo(R.drawable.ic_kz_stop)
        assertThat(voiceCoreIcon(VoicePhase.SPEAKING, muted = false)).isEqualTo(R.drawable.ic_kz_stop)
    }

    @Test fun squidWavesWhileListeningAndSpeaks() {
        assertThat(voiceSquidPose(VoicePhase.LISTENING)).isEqualTo(SquidPose.THINKING)
        assertThat(voiceSquidPose(VoicePhase.PROCESSING)).isEqualTo(SquidPose.THINKING)
        assertThat(voiceSquidPose(VoicePhase.SPEAKING)).isEqualTo(SquidPose.SPEAKING)
    }
}
