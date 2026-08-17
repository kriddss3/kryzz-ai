package ai.daylight.assistant.ui

import ai.daylight.assistant.voice.VoicePhase
import ai.daylight.assistant.voice.toSpeechText
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class VoiceTextTest {
    @Test fun stripsMarkdownAndRawLinksBeforeSpeaking() {
        val speech = "**Answer** [OpenRouter](https://openrouter.ai) ```kotlin\nval x = 1\n```".toSpeechText()
        assertThat(speech).isEqualTo("Answer OpenRouter Code block omitted.")
    }

    @Test fun toSpeechTextKeepsEmotionBrackets() {
        assertThat("[happy] **Yes**".toSpeechText()).isEqualTo("[happy] Yes")
    }

    @Test fun voiceOrbSemanticsDescribeTheActionForEveryLivePhase() {
        assertThat(voiceOverlayDescription(VoicePhase.LISTENING)).contains("sends automatically")
        assertThat(voiceOverlayDescription(VoicePhase.LISTENING)).contains("Tap to send now")
        assertThat(voiceOverlayDescription(VoicePhase.PROCESSING)).contains("Tap to cancel")
        assertThat(voiceOverlayDescription(VoicePhase.SPEAKING)).contains("Tap to interrupt")
    }

    @Test fun listeningHintExplainsAutomaticTurnTaking() {
        assertThat(voicePhaseTitle(VoicePhase.LISTENING)).isEqualTo("Listening")
        assertThat(voicePhaseHint(VoicePhase.LISTENING)).contains("sends when you finish")
        assertThat(voicePhaseHint(VoicePhase.SPEAKING)).contains("interrupt")
    }

    @Test fun fullScreenVoiceUsesTheOrbAsItsPrimaryBrandFreeAction() {
        val source = File("src/main/java/ai/daylight/assistant/ui/ChatScreen.kt").readText()
        val overlay = source.substringAfter("private fun VoiceChatOverlay(")
            .substringBefore("internal fun voicePhaseTitle")

        assertThat(overlay).contains("testTag(\"voice_primary_action\")")
        assertThat(overlay).contains("testTag(\"voice_orbital_field\")")
        assertThat(overlay).contains("if (motionEnabled) level.coerceIn(0f, 1f) else 0f")
        assertThat(overlay).doesNotContain("KryzzMark(")
    }
}
