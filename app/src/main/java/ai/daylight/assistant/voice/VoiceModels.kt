package ai.daylight.assistant.voice

import kotlinx.serialization.Serializable

/** Live state of a voice chat session shown by the centre bubble overlay. */
enum class VoicePhase {
    /** No session: the composer shows the normal send button. */
    IDLE,

    /** The microphone is open and the bubble grows with the user's voice. */
    LISTENING,

    /** Audio was sent; STT and LLM are running; the bubble pulses quietly. */
    PROCESSING,

    /** The AI reply is being spoken out loud. */
    SPEAKING
}

/** Fixed wiring for the voice chat mode: STT and TTS engines, timing and the spoken-style prompt. */
object VoiceConfig {
    /**
     * Speech-to-text default: Whisper large-v3-turbo (Groq via OpenRouter), chosen for
     * transcription quality. Grok STT was the default only for being ~100 ms faster; early
     * transcription during the closing silence ([SPECULATIVE_STT_SILENCE_MS]) now hides more
     * than that. A choice saved in Settings is kept; only installs still on the default move.
     */
    const val DEFAULT_STT_MODEL = "openai/whisper-large-v3-turbo"

    /** ISO-639-1 hint so Groq / Grok skips language detection. Empty string = auto-detect. */
    const val DEFAULT_STT_LANGUAGE = "en"

    /** Whisper large-v3-turbo via OpenRouter (Groq-hosted). */
    const val WHISPER_STT_MODEL = "openai/whisper-large-v3-turbo"

    /** Grok STT 1.0 through OpenRouter POST /api/v1/audio/transcriptions. */
    const val GROK_STT_MODEL = "x-ai/grok-stt-1.0"

    /** Older DataStore id from when Grok STT hit api.x.ai directly. */
    const val GROK_STT_MODEL_LEGACY = "xai/grok-stt"

    /** NVIDIA Nemotron 3.5 ASR Streaming Multilingual through OpenRouter's /audio/transcriptions endpoint. */
    const val NEMOTRON_STT_MODEL = "nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b"

    /** Gemini 2.5 Flash Lite is the default voice-chat reply brain (lightweight, low-latency). */
    const val DEFAULT_LLM_MODEL = "google/gemini-2.5-flash-lite"

    /** Fish Audio S2.1 Pro (paid tier, best quality on the current S2.1 generation). */
    const val FISH_MODEL = "s2.1-pro"

    /** Fish Audio free developer tier of the current S2.1 generation; the default engine. */
    const val FISH_MODEL_FREE = "s2.1-pro-free"

    /** Fish Audio latency mode. Official values are "balanced" (~300 ms) and "normal". */
    const val FISH_LATENCY = "balanced"

    /** Text segment size for Fish processing; the minimum (100) starts audio the soonest. */
    const val FISH_CHUNK_LENGTH = 100

    /** PCM sample rate for streamed Fish voice replies. Fish's PCM default is 44.1 kHz. */
    const val FISH_PCM_SAMPLE_RATE = 44_100

    /** Default Qwen voice ID accepted by OpenRouter's qwen-audio-3.0-tts models. */
    const val OPENROUTER_TTS_VOICE = "longanlingxi"

    /** Longest single recording before the session auto-closes. */
    const val MAX_RECORD_MS = 30_000L

    /** Trailing silence after confirmed speech that ends the recording automatically. */
    const val SILENCE_STOP_MS = 520L

    /**
     * Silence after confirmed speech at which transcription starts early, while the
     * [SILENCE_STOP_MS] window is still running. If the user keeps talking the early
     * transcript is thrown away; otherwise it is ready (or nearly) when the turn ends.
     */
    const val SPECULATIVE_STT_SILENCE_MS = 220L

    /**
     * Continuous speech, louder than the reply's echo, that counts as the user talking over
     * Kryzz. Measured as one unbroken run (see [SpeechRunTracker]), not summed across the reply.
     */
    const val BARGE_TRIGGER_MS = 200L

    /** Minimum accumulated voiced audio before silence is allowed to auto-stop. */
    const val MIN_SPEECH_MS = 180L

    /** Sustained audio required before a spike is accepted as the start of speech. */
    const val SPEECH_CONFIRM_MS = 80L

    /** Brief microphone settling window used to learn the room's background level. */
    const val NOISE_CALIBRATION_MS = 300L

    /** Lowest adaptive speech gate (MediaRecorder.maxAmplitude is 0..32767). */
    const val SPEECH_THRESHOLD = 800

    /**
     * Short listening sounds Kryzz drops into the user's pauses while they talk for a while.
     * Rendered once in the reply voice and kept on disk (see BackchannelPolicy for timing).
     */
    val BACKCHANNEL_PHRASES = listOf("Mm-hmm.", "Mhm.", "Yeah.", "Right.")

    /**
     * Spoken-style instruction for voice chat. Kept short and example-first so small
     * models (Granite 8B and similar) actually emit Fish tags instead of ignoring a long list.
     */
    const val VOICE_SYSTEM_PROMPT =
        "You are Kryzz AI. Everything you say is read out loud, like a person on a phone call. " +
            "Answer in one or two short sentences when that is enough. Stay under forty words unless they asked for detail. " +
            "No markdown, headings, lists, code, or links. Round numbers so they are easy to say. Stay warm and direct. " +
            "Never claim an action was performed unless you verified it. " +
            "Put one Fish tag at the START of a sentence when the feeling or sound is clear. Use these exact tags: " +
            "[angry] [sad] [embarrassed] [emphasis] [whispering] [soft] [breathy] [excited] " +
            "[laughing] [chuckling] [moaning] [clear throat] [sobbing] [crying loudly] " +
            "[sighing] [panting] [groaning] [crowd laughing] [background laughter] [audience laughing] " +
            "[pause] [long pause] [happy] [calm]. " +
            "Examples: [excited] That is great news. [whispering] Come closer. [laughing] I cannot believe that. " +
            "[sighing] Okay, we can try again. [pause] Give me a second. [angry] That is not okay. " +
            "Use a tag on most sentences. One tag per sentence. Never invent other brackets. Never use markdown."

    /** Same spoken-style instructions without the emotion-tag request, used when emotions are turned off. */
    const val VOICE_SYSTEM_PROMPT_NO_EMOTIONS =
        "You are Kryzz AI. Everything you say is read out loud, like a person on a phone call. " +
            "Answer in one or two short sentences when that is enough. Stay under forty words unless they asked for detail. " +
            "No markdown, headings, lists, code, or links. Round numbers so they are easy to say. Stay warm and direct. " +
            "Never claim an action was performed unless you verified it. Never use markdown."

    /** Selects the voice system instruction for the current emotions setting. */
    fun voiceSystemPrompt(emotions: Boolean): String =
        if (emotions) VOICE_SYSTEM_PROMPT else VOICE_SYSTEM_PROMPT_NO_EMOTIONS

    /**
     * Spoken-style lab prompt: no warm/kind persona, so angry and sad Fish tags can be tested.
     * Unlocked when the user says they are the developer talking; locked with "end developer mode".
     */
    const val VOICE_SYSTEM_PROMPT_DEVELOPER =
        "You are Kryzz AI in a voice TTS lab. The user is the developer testing spoken emotion. " +
            "Drop the usual warm, kind, or cheerful persona. Match the mood they ask for. " +
            "Put one Fish tag at the START of each sentence. Use these exact tags: " +
            "[angry] [sad] [embarrassed] [emphasis] [whispering] [soft] [breathy] [excited] " +
            "[laughing] [chuckling] [moaning] [clear throat] [sobbing] [crying loudly] " +
            "[sighing] [panting] [groaning] [crowd laughing] [background laughter] [audience laughing] " +
            "[pause] [long pause] [happy] [calm]. " +
            "If they name a mood, use that tag. One or two short sentences, under forty words. No markdown. " +
            "Never claim an action was performed unless you verified it."

    const val VOICE_SYSTEM_PROMPT_DEVELOPER_NO_EMOTIONS =
        "You are Kryzz AI in a voice TTS lab. The user is the developer testing spoken delivery. " +
            "Drop the usual warm, kind, or cheerful persona. Speak in the mood they ask for, including angry or sad. " +
            "One or two short sentences, under forty words. No markdown. " +
            "Never claim an action was performed unless you verified it."

    fun voiceSystemPrompt(emotions: Boolean, developerLab: Boolean): String = when {
        developerLab && emotions -> VOICE_SYSTEM_PROMPT_DEVELOPER
        developerLab -> VOICE_SYSTEM_PROMPT_DEVELOPER_NO_EMOTIONS
        else -> voiceSystemPrompt(emotions)
    }

    fun isDeveloperUnlock(text: String): Boolean {
        val n = text.lowercase()
        return n.contains("developer talking") ||
            n.contains("i'm the developer") ||
            n.contains("i am the developer") ||
            n.contains("this is the developer") ||
            Regex("\\bdeveloper mode\\b").containsMatchIn(n)
    }

    fun isDeveloperLock(text: String): Boolean {
        val n = text.lowercase()
        return n.contains("end developer mode") ||
            n.contains("stop developer mode") ||
            n.contains("exit developer mode") ||
            n.contains("developer mode off")
    }

    fun developerLabActive(userMessages: List<String>): Boolean {
        var on = false
        for (message in userMessages) {
            if (isDeveloperUnlock(message)) on = true
            if (isDeveloperLock(message)) on = false
        }
        return on
    }
}

/** One selectable LLM that writes the spoken reply during voice chat. */
data class VoiceReplyModel(val id: String, val name: String, val detail: String)

/**
 * Fast LLM choices for the voice chat reply. OpenRouter picks the provider with the
 * lowest latency for whichever of these is selected.
 */
object VoiceReplyModels {
    // DeepSeek V4 Flash was removed as a voice reply option per the 5.6.3 cut. The default
    // brain is now Gemini 2.5 Flash Lite; existing installs that had DeepSeek selected are
    // migrated to the new default by coerceVoiceReplyModel (the id is no longer in `fast`).
    val fast = listOf(
        VoiceReplyModel(VoiceConfig.DEFAULT_LLM_MODEL, "Gemini 2.5 Flash Lite", "Fast, lightweight Gemini (default)"),
        VoiceReplyModel("sao10k/l3-lunaris-8b", "Llama 3 8B Lunaris", "Expressive generalist tuned for natural conversation")
    )

    fun nameFor(id: String): String? = fast.firstOrNull { it.id == id }?.name
}

/** One selectable voice from the Fish Audio library, identified by its public model ID. */
data class FishVoice(val name: String, val referenceId: String)

/** One selectable Fish Audio TTS engine version. */
data class FishEngine(val id: String, val name: String, val detail: String)

/** Which backend renders the spoken reply. */
enum class TtsProvider(val label: String) {
    FISH("Fish Audio"),
    OPENROUTER("OpenRouter");

    companion object {
        fun from(value: String): TtsProvider = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FISH
    }
}

/** One selectable OpenRouter TTS model (Qwen-Audio-3.0 family). */
data class OpenRouterTtsModel(val id: String, val name: String, val detail: String)

object OpenRouterTtsModels {
    val options = listOf(
        OpenRouterTtsModel("qwen/qwen-audio-3.0-tts-flash", "Qwen3 TTS Flash", "Fastest spoken replies"),
        OpenRouterTtsModel("qwen/qwen-audio-3.0-tts-plus", "Qwen3 TTS Plus", "Higher-quality voice")
    )

    fun nameFor(id: String): String? = options.firstOrNull { it.id == id }?.name
}

object FishEngines {
    /** Engine choices; the current free tier is offered alongside the paid default. */
    val options = listOf(
        FishEngine(VoiceConfig.FISH_MODEL, "S2.1 Pro", "Paid — best quality"),
        FishEngine(VoiceConfig.FISH_MODEL_FREE, "S2.1 Pro Free", "Current free developer tier"),
        FishEngine("s2-pro", "S2 Pro", "Previous generation"),
        FishEngine("s1", "S1", "Older engine")
    )

    fun nameFor(id: String): String = options.firstOrNull { it.id == id }?.name ?: id
}

/**
 * Curated public Fish Audio voices. Each URL of the form fish.audio/m/<id> is backed by a
 * public voice model whose ID is passed straight to the TTS API as `reference_id`.
 */
object FishVoices {
    val presets = listOf(
        FishVoice("Egirl", "ca3007f96ae7499ab87d27ea3599956a"),
        FishVoice("Miku", "acc8237220d8470985ec9be6c4c480a9"),
        FishVoice("Teto", "a3b3f0a9c49340bd8fa722d83c81cb08"),
        FishVoice("Rick", "d2e75a3e3fd6419893057c02a375a113"),
        FishVoice("Mommy", "5233336f5f44460ea0902b0802375451"),
        FishVoice("Almight", "6390dc68391f45cdae685849e9c05aaa"),
        FishVoice("Peter", "d75c270eaee14c8aa1e9e980cc37cf1b")
    )

    /** Display name for a stored reference ID, or null when it is not one of the presets. */
    fun nameFor(referenceId: String?): String? = presets.firstOrNull { it.referenceId == referenceId }?.name
}

/** A Fish Audio voice the user saved by hand, shown alongside the curated presets. */
@Serializable
data class FishCustomVoice(val name: String, val referenceId: String)

/**
 * Cleans a markdown chat reply into speakable plain text: code blocks are announced
 * instead of read, links collapse to their label, and formatting marks are dropped.
 */
/** Official Fish S2.1 Pro emotion / delivery tags accepted in spoken text. */
object FishEmotions {
    val basic = setOf(
        "happy", "sad", "angry", "excited", "calm", "nervous", "confident", "surprised",
        "satisfied", "delighted", "scared", "worried", "upset", "frustrated", "depressed",
        "empathetic", "embarrassed", "disgusted", "moved", "proud", "relaxed", "grateful",
        "curious", "sarcastic"
    )
    val extra = setOf(
        "whispering", "laughing", "chuckling", "sighing", "emphasis", "break",
        "hopeful", "determined", "confused", "disappointed",
        "soft", "breathy", "moaning", "clear throat", "sobbing", "crying loudly",
        "panting", "groaning", "crowd laughing", "background laughter", "audience laughing",
        "pause", "long pause"
    )
    fun isOfficial(tag: String): Boolean {
        val key = tag.trim().lowercase()
        return key in basic || key in extra
    }

    /**
     * Fish S2 accepts official tags and free-form cues such as [warm and happy].
     * Citation leftovers like [1] are not emotions and must not be sent to TTS —
     * they are what used to get rewritten into a forced [calm] that flattened
     * the line and sometimes cut the rest of the clip short.
     */
    fun keep(tag: String): Boolean {
        val key = tag.trim().lowercase()
        if (key.isEmpty()) return false
        if (isOfficial(key)) return true
        return NATURAL_CUE.matches(key)
    }

    private val NATURAL_CUE = Regex("[a-z][a-z0-9 ,-]{0,40}")
}

private val emotionTag = Regex("\\[([^]]+)]\\s*")

/**
 * When [enabled], keeps official / natural-language Fish tags in place and
 * strips citation-like brackets. Does **not** inject [calm] — a forced default
 * tag made every untagged sentence sound flat and could truncate Fish audio.
 * When [enabled] is false every tag is stripped so nothing is read aloud.
 */
internal fun String.withFishEmotionTags(enabled: Boolean = true): String {
    val text = trim()
    if (text.isBlank()) return text
    if (!enabled) {
        return emotionTag.replace(text, " ").replace(Regex("\\s+"), " ").trim()
    }
    return emotionTag.replace(text) { match ->
        if (FishEmotions.keep(match.groupValues[1])) match.value else " "
    }.replace(Regex("\\s+"), " ").trim()
}

/** Prepares spoken text for a Fish engine: keeps official / natural tags when enabled, strips them otherwise. */
internal fun String.forFishSpeech(emotions: Boolean, model: String): String =
    withFishEmotionTags(enabled = emotions).forFishEngine(model)

/** S1 uses (parentheses); S2 / S2.1 use [brackets]. */
internal fun String.forFishEngine(model: String): String =
    if (model == "s1") replace(Regex("\\[([^]]+)]")) { "(${it.groupValues[1]})" } else this

/** One selectable speech-to-text backend for voice chat and dictation. */
data class VoiceSttModel(val id: String, val name: String, val detail: String)

object VoiceSttModels {
    val options = listOf(
        // The default now lives first so Settings shows the same model that ships by default.
        VoiceSttModel(VoiceConfig.WHISPER_STT_MODEL, "Whisper large-v3-turbo", "Groq via OpenRouter (default)"),
        VoiceSttModel(VoiceConfig.GROK_STT_MODEL, "Grok STT", "xAI via OpenRouter"),
        VoiceSttModel(VoiceConfig.NEMOTRON_STT_MODEL, "Nemotron 3.5 ASR", "NVIDIA via OpenRouter · streaming")
    )
    val ids = options.map { it.id }.toSet()
    fun nameFor(id: String): String? = options.firstOrNull { it.id == id }?.name
}

internal fun coerceVoiceSttModel(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value == VoiceConfig.GROK_STT_MODEL_LEGACY) return VoiceConfig.GROK_STT_MODEL
    return if (value in VoiceSttModels.ids) value else VoiceConfig.DEFAULT_STT_MODEL
}

/**
 * Vocabulary hint passed to every STT call as the OpenAI-style `prompt` field. Small ASR
 * models lean on this heavily for proper-noun accuracy and rare words, and the gain is
 * especially visible on Grok STT and Nemotron — neither has a great default bias for
 * product names, voice command words, or short multilingual replies.
 */
const val STT_PROMPT_HINTS: String =
    "Kryzz AI, Grok, Whisper, Nemotron, Fish Audio, OpenRouter, DeepSeek, Gemini, " +
        "Qwen, Llama, Granite, agent mode, voice chat, voice session, dictate, " +
        "yeah, nope, uh-huh, hmm, bye, hello, thanks, please, sorry"

internal fun String.toSpeechText(): String = this
    .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
    .replace(Regex("\\[([^]]+)]\\((https?://[^)]+)\\)"), "$1")
    .replace(Regex("https?://\\S+"), " link ")
    .replace(Regex("[*_#>`]"), "")
    .replace(Regex("\\s+"), " ")
    .trim()
