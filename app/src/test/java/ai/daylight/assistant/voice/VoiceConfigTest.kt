package ai.daylight.assistant.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceConfigTest {
    @Test fun defaultsPinTheAgreedLowLatencyPipeline() {
        // Default switched to Grok STT in v5.1.3 (~100 ms faster than Whisper-large on the same
        // /audio/transcriptions endpoint); users on the previous default keep their saved choice.
        assertThat(VoiceConfig.DEFAULT_STT_MODEL).isEqualTo("x-ai/grok-stt-1.0")
        assertThat(VoiceConfig.DEFAULT_STT_LANGUAGE).isEqualTo("en")
        assertThat(VoiceConfig.DEFAULT_LLM_MODEL).isEqualTo("deepseek/deepseek-v4-flash")
        assertThat(VoiceConfig.FISH_MODEL).isEqualTo("s2.1-pro")
        assertThat(VoiceConfig.FISH_LATENCY).isEqualTo("balanced")
        assertThat(VoiceConfig.FISH_PCM_SAMPLE_RATE).isEqualTo(44_100)
    }

    @Test fun voicePromptForcesSpokenStyleReplies() {
        val prompt = VoiceConfig.VOICE_SYSTEM_PROMPT
        assertThat(prompt.lowercase()).contains("read out loud")
        assertThat(prompt.lowercase()).contains("no markdown")
        assertThat(prompt.lowercase()).contains("short")
        assertThat(prompt).doesNotContain("parallel_search")
        assertThat(prompt).contains("[happy]")
        assertThat(prompt).contains("[calm]")
        assertThat(prompt).contains("[whispering]")
        assertThat(prompt).contains("[clear throat]")
        assertThat(prompt).contains("[long pause]")
        assertThat(prompt).contains("Use a tag on most sentences")
    }

    @Test fun recordingLimitsKeepMultipartWellUnderTheUploadCap() {
        // 30 s of 16 kHz PCM16 WAV ≈ 960 KB, far below OpenRouter's 25 MB limit.
        val estimatedBytes = VoiceConfig.MAX_RECORD_MS / 1_000L * 16_000L * 2L
        assertThat(estimatedBytes).isLessThan(25 * 1_024 * 1_024)
        // Confirmed speech gets a short natural pause without making turns feel sluggish.
        assertThat(VoiceConfig.SILENCE_STOP_MS).isAtLeast(450L)
        assertThat(VoiceConfig.SILENCE_STOP_MS).isAtMost(600L)
        assertThat(VoiceConfig.MIN_SPEECH_MS).isAtLeast(150L)
        assertThat(VoiceConfig.SPEECH_CONFIRM_MS).isLessThan(VoiceConfig.MIN_SPEECH_MS)
        // Barge-in triggers on sustained speech faster than the end-of-utterance silence.
        assertThat(VoiceConfig.BARGE_TRIGGER_MS).isLessThan(VoiceConfig.SILENCE_STOP_MS)
    }

    @Test fun curatedVoicePresetsAreUniqueAndWellFormed() {
        val ids = FishVoices.presets.map { it.referenceId }
        assertThat(FishVoices.presets).hasSize(7)
        assertThat(ids).containsNoDuplicates()
        ids.forEach { id -> assertThat(id).matches("[0-9a-f]{32}") }
        assertThat(FishVoices.presets.map { it.name }).containsExactly(
            "Egirl", "Miku", "Teto", "Rick", "Mommy", "Almight", "Peter"
        ).inOrder()
        assertThat(FishVoices.nameFor("ca3007f96ae7499ab87d27ea3599956a")).isEqualTo("Egirl")
        assertThat(FishVoices.nameFor("not-a-preset")).isNull()
    }

    @Test fun fishEnginesIncludeTheCurrentFreeTierAndDefaultToPro() {
        assertThat(FishEngines.options.map { it.id }).containsExactly(
            "s2.1-pro", "s2.1-pro-free", "s2-pro", "s1"
        ).inOrder()
        assertThat(VoiceConfig.FISH_MODEL).isEqualTo("s2.1-pro")
        assertThat(VoiceConfig.FISH_MODEL_FREE).isEqualTo("s2.1-pro-free")
        assertThat(FishEngines.nameFor("s2.1-pro-free")).isEqualTo("S2.1 Pro Free")
        assertThat(FishEngines.nameFor("unknown-engine")).isEqualTo("unknown-engine")
    }

    @Test fun officialEmotionAllowlistCoversS21BasicSet() {
        assertThat(FishEmotions.basic).containsAtLeast("happy", "sad", "calm", "excited", "curious")
        assertThat(FishEmotions.isOfficial("whispering")).isTrue()
        assertThat(FishEmotions.isOfficial("clear throat")).isTrue()
        assertThat(FishEmotions.isOfficial("long pause")).isTrue()
        assertThat(FishEmotions.isOfficial("not-a-mood")).isFalse()
    }

    @Test fun untaggedSpeechIsLeftAlone() {
        assertThat("Hello there.".withFishEmotionTags()).isEqualTo("Hello there.")
    }

    @Test fun existingOfficialTagIsKept() {
        assertThat("[happy] We did it.".withFishEmotionTags()).isEqualTo("[happy] We did it.")
    }

    @Test fun naturalLanguageTagsAreKeptAndCitationsAreStripped() {
        assertThat("[warm and happy] We did it.".withFishEmotionTags()).isEqualTo("[warm and happy] We did it.")
        assertThat("See this [1] later.".withFishEmotionTags()).isEqualTo("See this later.")
        assertThat(FishEmotions.keep("slightly sad")).isTrue()
        assertThat(FishEmotions.keep("1")).isFalse()
    }

    @Test fun disabledEmotionsStripAllTagsAndNeverPrefix() {
        assertThat("[happy] We did it.".withFishEmotionTags(enabled = false)).isEqualTo("We did it.")
        assertThat("Hello there.".withFishEmotionTags(enabled = false)).isEqualTo("Hello there.")
        assertThat("[calm] [happy] Two tags.".withFishEmotionTags(enabled = false)).isEqualTo("Two tags.")
        assertThat("[happy] We did it.".forFishSpeech(emotions = false, model = "s2.1-pro-free")).isEqualTo("We did it.")
        assertThat("Hello there.".forFishSpeech(emotions = true, model = "s2.1-pro-free")).isEqualTo("Hello there.")
        assertThat("[happy] Hello there.".forFishSpeech(emotions = true, model = "s2.1-pro-free")).isEqualTo("[happy] Hello there.")
    }

    @Test fun noEmotionsPromptOmitsTheTagInstruction() {
        val prompt = VoiceConfig.voiceSystemPrompt(emotions = false)
        assertThat(prompt.lowercase()).contains("read out loud")
        assertThat(prompt).doesNotContain("[happy]")
        assertThat(prompt).doesNotContain("Fish tag")
        assertThat(VoiceConfig.voiceSystemPrompt(emotions = true)).contains("[happy]")
    }

    @Test fun developerPhraseUnlocksTheLabPromptAndLockTurnsItOff() {
        assertThat(VoiceConfig.isDeveloperUnlock("it's the developer talking")).isTrue()
        assertThat(VoiceConfig.isDeveloperUnlock("I am the developer, sound angry")).isTrue()
        assertThat(VoiceConfig.isDeveloperLock("end developer mode")).isTrue()
        assertThat(VoiceConfig.developerLabActive(listOf("hello", "developer talking"))).isTrue()
        assertThat(VoiceConfig.developerLabActive(listOf("developer talking", "end developer mode"))).isFalse()
        val lab = VoiceConfig.voiceSystemPrompt(emotions = true, developerLab = true)
        assertThat(lab).contains("[angry]")
        assertThat(lab).contains("[sad]")
        assertThat(lab).doesNotContain("Stay warm")
    }

    @Test fun s1EngineUsesParentheses() {
        assertThat("[happy] Hello.".forFishEngine("s1")).isEqualTo("(happy) Hello.")
    }

    @Test fun coerceVoiceSttModelAcceptsGrokAndNemotronAndFallsBack() {
        assertThat(coerceVoiceSttModel("x-ai/grok-stt-1.0")).isEqualTo("x-ai/grok-stt-1.0")
        assertThat(coerceVoiceSttModel("xai/grok-stt")).isEqualTo(VoiceConfig.GROK_STT_MODEL)
        assertThat(coerceVoiceSttModel(VoiceConfig.NEMOTRON_STT_MODEL)).isEqualTo(VoiceConfig.NEMOTRON_STT_MODEL)
        assertThat(coerceVoiceSttModel("nope")).isEqualTo(VoiceConfig.DEFAULT_STT_MODEL)
    }
}
