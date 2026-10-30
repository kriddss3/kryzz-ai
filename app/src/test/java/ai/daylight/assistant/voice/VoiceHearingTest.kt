package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceHearingTest {
    @Test fun geminiChatModelsHearAudio() {
        assertThat(VoiceHearing.hearsAudio(VoiceConfig.DEFAULT_LLM_MODEL)).isTrue()
        assertThat(VoiceHearing.hearsAudio("google/gemini-2.5-flash")).isTrue()
        assertThat(VoiceHearing.hearsAudio("google/gemini-2.5-flash-image")).isFalse()
        assertThat(VoiceHearing.hearsAudio("sao10k/l3-lunaris-8b")).isFalse()
        assertThat(VoiceHearing.hearsAudio("openai/gpt-4o-mini")).isFalse()
        assertThat(VoiceHearing.hearsAudio("")).isFalse()
    }

    @Test fun heardWordsAndReplySplitOnceTheTagCloses() {
        val split = VoiceHearing.split("<heard>What's the weather tomorrow?</heard> [calm] Cold and clear, about four degrees.")
        assertThat(split.settled).isTrue()
        assertThat(split.heard).isEqualTo("What's the weather tomorrow?")
        assertThat(split.reply).isEqualTo("[calm] Cold and clear, about four degrees.")
        assertThat(split.noSpeech).isFalse()
    }

    @Test fun anOpenTagHoldsTheReplyBack() {
        listOf("", " ", "<", "<he", "<heard", "<heard>What's the wea").forEach { partial ->
            val split = VoiceHearing.split(partial)
            assertThat(split.settled).isFalse()
            assertThat(split.reply).isEmpty()
            assertThat(split.heard).isNull()
        }
    }

    @Test fun anEmptyTagMeansNoSpeech() {
        assertThat(VoiceHearing.split("<heard></heard>").noSpeech).isTrue()
        assertThat(VoiceHearing.split("  <heard> </heard>\n").noSpeech).isTrue()
        assertThat(VoiceHearing.split("<heard/>").noSpeech).isTrue()
    }

    @Test fun aReplyWithoutTheTagIsSpokenWhole() {
        val split = VoiceHearing.split("Sure, here you go.")
        assertThat(split.settled).isTrue()
        assertThat(split.heard).isNull()
        assertThat(split.reply).isEqualTo("Sure, here you go.")
        assertThat(split.noSpeech).isFalse()
    }

    @Test fun aRepeatedTagIsNeverReadAloud() {
        val split = VoiceHearing.split("<heard>Hi</heard> Hello! <heard>Hi</heard> How are you?")
        assertThat(split.heard).isEqualTo("Hi")
        assertThat(split.reply).doesNotContain("heard")
        assertThat(split.reply).contains("How are you?")
    }

    @Test fun heardWordsAreOneTidyLine() {
        assertThat(VoiceHearing.split("<heard>  set a timer\n for ten  minutes </heard>ok").heard)
            .isEqualTo("set a timer for ten minutes")
    }
}
