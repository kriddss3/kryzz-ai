package ai.daylight.assistant.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.domain.AppPalette
import ai.daylight.assistant.domain.BackgroundStyle
import ai.daylight.assistant.domain.ChatDensity
import ai.daylight.assistant.domain.FontStyle
import ai.daylight.assistant.domain.GradientPalette
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.ReasoningEffort
import ai.daylight.assistant.domain.TextPalette
import ai.daylight.assistant.voice.TtsProvider
import ai.daylight.assistant.voice.VoiceConfig
import ai.daylight.assistant.voice.VoiceReplyModels
import ai.daylight.assistant.voice.FishCustomVoice
import ai.daylight.assistant.voice.FishEngines
import ai.daylight.assistant.voice.OpenRouterTtsModels
import ai.daylight.assistant.voice.coerceVoiceSttModel
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("daylight_preferences")

data class SettingsState(
    val onboardingComplete: Boolean = false,
    val defaultModel: String = "openai/gpt-4o-mini",
    val agentModel: String = "openai/gpt-4o-mini",
    val researchModel: String = "openai/gpt-4o-mini",
    val imageModel: String = "",
    val videoModel: String = "",
    val audioModel: String = "openai/gpt-4o-mini-tts-2025-12-15",
    val favoriteModels: Set<String> = setOf("openai/gpt-4o-mini"),
    val chatProvider: ChatProvider = ChatProvider.OPENROUTER,
    val modelReasoning: Map<String, ReasoningEffort> = emptyMap(),
    val presetId: String = "balanced",
    val customPrompt: String = "",
    val searchEnabled: Boolean = true,
    val maxSearchChars: Int = 12_000,
    val maxToolRounds: Int = 6,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.CONSTELLATION,
    val colouredGradient: GradientPalette = GradientPalette.MONOCHROME,
    val appPalette: AppPalette = AppPalette.MONOCHROME,
    val textPalette: TextPalette = TextPalette.ADAPTIVE,
    val fontStyle: FontStyle = FontStyle.MODERN,
    val fontScale: Float = 1f,
    val chatDensity: ChatDensity = ChatDensity.COMFORTABLE,
    val animationsEnabled: Boolean = true,
    val surfaceOpacity: Float = 0.84f,
    val backgroundBlur: Float = 0f,
    val biometricLock: Boolean = false,
    val memoryEnabled: Boolean = false,
    val agentSwarmEnabled: Boolean = false,
    val locationEnabled: Boolean = false,
    val locationLabel: String = "",
    val locationLat: Double = 0.0,
    val locationLon: Double = 0.0,
    val voiceReplyModel: String = VoiceConfig.DEFAULT_LLM_MODEL,
    val voiceSttModel: String = VoiceConfig.DEFAULT_STT_MODEL,
    val ttsProvider: String = TtsProvider.FISH.name,
    val openRouterTtsModel: String = "qwen/qwen-audio-3.0-tts-flash",
    val openRouterTtsVoice: String = VoiceConfig.OPENROUTER_TTS_VOICE,
    val fishModel: String = VoiceConfig.FISH_MODEL_FREE,
    val fishVoiceId: String = "",
    val fishCustomVoices: List<FishCustomVoice> = emptyList(),
    val fishSpeed: Float = 1f,
    val voiceEmotions: Boolean = true,
    val voiceFishStreaming: Boolean = true
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val model = stringPreferencesKey("default_model")
        val agentModel = stringPreferencesKey("agent_model")
        val researchModel = stringPreferencesKey("research_model")
        val imageModel = stringPreferencesKey("image_model")
        val videoModel = stringPreferencesKey("video_model")
        val audioModel = stringPreferencesKey("audio_model")
        val favorites = stringPreferencesKey("favorite_models")
        val chatProvider = stringPreferencesKey("chat_provider")
        val reasoning = stringPreferencesKey("model_reasoning")
        val preset = stringPreferencesKey("preset_id")
        val customPrompt = stringPreferencesKey("custom_prompt")
        val search = booleanPreferencesKey("search_enabled")
        val searchChars = intPreferencesKey("max_search_chars")
        val toolRounds = intPreferencesKey("max_tool_rounds")
        val theme = stringPreferencesKey("theme")
        val backgroundStyle = stringPreferencesKey("background_style")
        val colouredGradient = stringPreferencesKey("coloured_gradient")
        val appPalette = stringPreferencesKey("app_palette")
        val textPalette = stringPreferencesKey("text_palette")
        val fontStyle = stringPreferencesKey("font_style")
        val fontScale = floatPreferencesKey("font_scale")
        val chatDensity = stringPreferencesKey("chat_density")
        val animationsEnabled = booleanPreferencesKey("animations_enabled")
        val surfaceOpacity = floatPreferencesKey("surface_opacity")
        val backgroundBlur = floatPreferencesKey("background_blur")
        val biometric = booleanPreferencesKey("biometric_lock")
        val memoryEnabled = booleanPreferencesKey("memory_enabled")
        val agentSwarmEnabled = booleanPreferencesKey("agent_swarm_enabled")
        val locationEnabled = booleanPreferencesKey("location_enabled")
        val locationLabel = stringPreferencesKey("location_label")
        val locationLat = stringPreferencesKey("location_lat")
        val locationLon = stringPreferencesKey("location_lon")
        // Legacy key kept for migration only: the reply model is now a string choice.
        val voiceFastReplies = booleanPreferencesKey("voice_fast_replies")
        val voiceReplyModel = stringPreferencesKey("voice_reply_model")
        val voiceSttModel = stringPreferencesKey("voice_stt_model")
        val ttsProvider = stringPreferencesKey("tts_provider")
        val openRouterTtsModel = stringPreferencesKey("openrouter_tts_model")
        val openRouterTtsVoice = stringPreferencesKey("openrouter_tts_voice")
        val fishModel = stringPreferencesKey("fish_model")
        val fishVoiceId = stringPreferencesKey("fish_voice_id")
        val fishCustomVoices = stringPreferencesKey("fish_custom_voices")
        val fishSpeed = floatPreferencesKey("fish_speed")
        val voiceEmotions = booleanPreferencesKey("voice_emotions")
        val voiceFishStreaming = booleanPreferencesKey("voice_fish_streaming")
    }

    val state: Flow<SettingsState> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            SettingsState(
                onboardingComplete = p[Keys.onboarding] ?: false,
                defaultModel = p[Keys.model] ?: "openai/gpt-4o-mini",
                agentModel = p[Keys.agentModel] ?: (p[Keys.model] ?: "openai/gpt-4o-mini"),
                researchModel = p[Keys.researchModel] ?: (p[Keys.agentModel] ?: p[Keys.model] ?: "openai/gpt-4o-mini"),
                imageModel = p[Keys.imageModel].orEmpty(),
                videoModel = p[Keys.videoModel].orEmpty(),
                audioModel = p[Keys.audioModel] ?: "openai/gpt-4o-mini-tts-2025-12-15",
                favoriteModels = p[Keys.favorites]?.split('|')?.filter(String::isNotBlank)?.toSet()
                    ?: setOf("openai/gpt-4o-mini"),
                chatProvider = runCatching { ChatProvider.valueOf(p[Keys.chatProvider] ?: "OPENROUTER") }.getOrDefault(ChatProvider.OPENROUTER),
                modelReasoning = decodeReasoning(p[Keys.reasoning]),
                presetId = p[Keys.preset] ?: "balanced",
                customPrompt = p[Keys.customPrompt] ?: "",
                searchEnabled = p[Keys.search] ?: true,
                maxSearchChars = (p[Keys.searchChars] ?: 12_000).coerceIn(2_000, 50_000),
                maxToolRounds = (p[Keys.toolRounds] ?: 6).coerceIn(1, 8),
                themeMode = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
                // 4.0.1 has one supported visual identity. Keep the legacy key and
                // enum values so old DataStore files remain readable, but never let
                // an upgraded install restore an unsupported background.
                backgroundStyle = coerceSupportedBackgroundStyle(p[Keys.backgroundStyle]),
                colouredGradient = runCatching { GradientPalette.valueOf(p[Keys.colouredGradient] ?: "MONOCHROME") }.getOrDefault(GradientPalette.MONOCHROME),
                // Removed appearance controls are normalized as well; otherwise an
                // upgraded user could be stuck with a hidden legacy palette/effect.
                appPalette = AppPalette.MONOCHROME,
                textPalette = TextPalette.ADAPTIVE,
                fontStyle = FontStyle.MODERN,
                fontScale = (p[Keys.fontScale] ?: 1f).coerceIn(0.85f, 1.35f),
                chatDensity = runCatching { ChatDensity.valueOf(p[Keys.chatDensity] ?: "COMFORTABLE") }.getOrDefault(ChatDensity.COMFORTABLE),
                animationsEnabled = p[Keys.animationsEnabled] ?: true,
                surfaceOpacity = 0.84f,
                backgroundBlur = 0f,
                biometricLock = p[Keys.biometric] ?: false,
                memoryEnabled = p[Keys.memoryEnabled] ?: false,
                agentSwarmEnabled = p[Keys.agentSwarmEnabled] ?: false,
                locationEnabled = p[Keys.locationEnabled] ?: false,
                locationLabel = p[Keys.locationLabel] ?: "",
                locationLat = p[Keys.locationLat]?.toDoubleOrNull() ?: 0.0,
                locationLon = p[Keys.locationLon]?.toDoubleOrNull() ?: 0.0,
                voiceReplyModel = coerceVoiceReplyModel(
                    p[Keys.voiceReplyModel]
                        ?: if (p[Keys.voiceFastReplies] == false) "" else VoiceConfig.DEFAULT_LLM_MODEL
                ),
                voiceSttModel = coerceVoiceSttModel(p[Keys.voiceSttModel]),
                ttsProvider = p[Keys.ttsProvider] ?: TtsProvider.FISH.name,
                openRouterTtsModel = coerceOpenRouterTtsModel(p[Keys.openRouterTtsModel]),
                openRouterTtsVoice = p[Keys.openRouterTtsVoice] ?: VoiceConfig.OPENROUTER_TTS_VOICE,
                fishModel = coerceFishModel(p[Keys.fishModel]),
                fishVoiceId = p[Keys.fishVoiceId] ?: "",
                fishCustomVoices = decodeFishCustomVoices(p[Keys.fishCustomVoices]),
                fishSpeed = (p[Keys.fishSpeed] ?: 1f).coerceIn(0.5f, 2f),
                voiceEmotions = p[Keys.voiceEmotions] ?: true,
                voiceFishStreaming = p[Keys.voiceFishStreaming] ?: true
            )
        }

    suspend fun completeOnboarding() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setDefaultModel(value: String) = context.dataStore.edit { it[Keys.model] = value.trim() }
    suspend fun setModel(value: String, purpose: ModelPurpose) = context.dataStore.edit {
        val clean = value.trim()
        when (purpose) {
            ModelPurpose.CHAT -> it[Keys.model] = clean
            ModelPurpose.AGENT -> it[Keys.agentModel] = clean
            ModelPurpose.RESEARCH -> it[Keys.researchModel] = clean
            ModelPurpose.IMAGE -> it[Keys.imageModel] = clean
            ModelPurpose.VIDEO -> it[Keys.videoModel] = clean
            ModelPurpose.AUDIO -> it[Keys.audioModel] = clean
        }
    }
    suspend fun toggleFavorite(value: String) = context.dataStore.edit {
        val values = it[Keys.favorites]?.split('|')?.filter(String::isNotBlank)?.toMutableSet() ?: mutableSetOf()
        if (!values.add(value)) values.remove(value)
        it[Keys.favorites] = values.sorted().joinToString("|")
    }
    suspend fun setChatProvider(value: ChatProvider) = context.dataStore.edit {
        val provider = ChatProvider.from(value.name)
        it[Keys.chatProvider] = provider.name
        val defaults = modelDefaults(provider)
        it[Keys.model] = defaults.default
        it[Keys.agentModel] = defaults.agent
        it[Keys.researchModel] = defaults.research
        it[Keys.imageModel] = defaults.image
        it[Keys.videoModel] = defaults.video
        it[Keys.audioModel] = defaults.audio
        it[Keys.favorites] = defaults.default
    }

    /**
     * Drops model IDs that the active provider did not return and chooses a valid
     * provider default when one is available. Empty catalogs are not authoritative:
     * a transient network failure must not erase a working preference.
     */
    suspend fun reconcileAvailableModels(
        provider: ChatProvider,
        text: Set<String> = emptySet(),
        image: Set<String> = emptySet(),
        video: Set<String> = emptySet(),
        audio: Set<String> = emptySet()
    ) = context.dataStore.edit {
        if (ChatProvider.from(it[Keys.chatProvider].orEmpty()) != provider) return@edit
        val defaults = modelDefaults(provider)
        fun reconcile(key: Preferences.Key<String>, available: Set<String>, preferred: String) {
            if (available.isEmpty()) return
            chooseAvailableModel(it[key], available, preferred)?.let { selected -> it[key] = selected }
        }
        reconcile(Keys.model, text, defaults.default)
        reconcile(Keys.agentModel, text, defaults.agent)
        reconcile(Keys.researchModel, text, defaults.research)
        reconcile(Keys.imageModel, image, defaults.image)
        reconcile(Keys.videoModel, video, defaults.video)
        reconcile(Keys.audioModel, audio, defaults.audio)
        if (text.isNotEmpty()) {
            val favorites = it[Keys.favorites].orEmpty()
                .split('|')
                .filter { favorite -> favorite in text }
                .toMutableSet()
            chooseAvailableModel(null, text, defaults.default)?.let(favorites::add)
            it[Keys.favorites] = favorites.sorted().joinToString("|")
        }
    }

    private data class ModelDefaults(
        val default: String,
        val agent: String,
        val research: String,
        val image: String,
        val video: String,
        val audio: String
    )

    private fun modelDefaults(provider: ChatProvider): ModelDefaults = when (provider) {
        ChatProvider.OPENROUTER -> ModelDefaults(
            default = "openai/gpt-4o-mini",
            agent = "openai/gpt-4o-mini",
            research = "openai/gpt-4o-mini",
            image = "",
            video = "",
            audio = "openai/gpt-4o-mini-tts-2025-12-15"
        )
        ChatProvider.MINIMAX -> ModelDefaults(
            default = "MiniMax-M3",
            agent = "MiniMax-M3",
            research = "MiniMax-M3",
            image = "image-01",
            video = "MiniMax-H3",
            audio = "music-01-free"
        )
    }
    suspend fun setReasoning(modelId: String, effort: ReasoningEffort) = context.dataStore.edit { preferences ->
        val values = decodeReasoning(preferences[Keys.reasoning]).toMutableMap()
        if (effort == ReasoningEffort.AUTO) values.remove(modelId) else values[modelId] = effort
        preferences[Keys.reasoning] = values.entries.sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value.name}" }
    }
    suspend fun setPersona(presetId: String, customPrompt: String) = context.dataStore.edit {
        it[Keys.preset] = presetId
        it[Keys.customPrompt] = customPrompt
    }
    suspend fun setSearchEnabled(value: Boolean) = context.dataStore.edit { it[Keys.search] = value }
    suspend fun setMaxSearchChars(value: Int) = context.dataStore.edit { it[Keys.searchChars] = value.coerceIn(2_000, 50_000) }
    suspend fun setMaxToolRounds(value: Int) = context.dataStore.edit { it[Keys.toolRounds] = value.coerceIn(1, 8) }
    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setBackgroundStyle(value: BackgroundStyle) = context.dataStore.edit { it[Keys.backgroundStyle] = value.name }
    suspend fun setColouredGradient(value: GradientPalette) = context.dataStore.edit { it[Keys.colouredGradient] = value.name }
    suspend fun setAppPalette(value: AppPalette) = context.dataStore.edit { it[Keys.appPalette] = value.name }
    suspend fun setTextPalette(value: TextPalette) = context.dataStore.edit { it[Keys.textPalette] = value.name }
    suspend fun setFontStyle(value: FontStyle) = context.dataStore.edit { it[Keys.fontStyle] = value.name }
    suspend fun setFontScale(value: Float) = context.dataStore.edit { it[Keys.fontScale] = value.coerceIn(0.85f, 1.35f) }
    suspend fun setChatDensity(value: ChatDensity) = context.dataStore.edit { it[Keys.chatDensity] = value.name }
    suspend fun setAnimationsEnabled(value: Boolean) = context.dataStore.edit { it[Keys.animationsEnabled] = value }
    suspend fun setSurfaceOpacity(value: Float) = context.dataStore.edit { it[Keys.surfaceOpacity] = value.coerceIn(0.42f, 0.96f) }
    suspend fun setBackgroundBlur(value: Float) = context.dataStore.edit { it[Keys.backgroundBlur] = value.coerceIn(0f, 28f) }
    suspend fun setBiometricLock(value: Boolean) = context.dataStore.edit { it[Keys.biometric] = value }
    suspend fun setMemoryEnabled(value: Boolean) = context.dataStore.edit { it[Keys.memoryEnabled] = value }
    suspend fun setAgentSwarmEnabled(value: Boolean) = context.dataStore.edit { it[Keys.agentSwarmEnabled] = value }
    suspend fun setLocationEnabled(value: Boolean) = context.dataStore.edit { it[Keys.locationEnabled] = value }
    suspend fun setCachedLocation(label: String, lat: Double, lon: Double) = context.dataStore.edit {
        it[Keys.locationLabel] = label
        it[Keys.locationLat] = lat.toString()
        it[Keys.locationLon] = lon.toString()
    }
    /** Selects which model writes the spoken reply. Empty string = the user's everyday chat model. */
    suspend fun setVoiceReplyModel(value: String) = context.dataStore.edit { it[Keys.voiceReplyModel] = value.trim() }
    suspend fun setVoiceSttModel(value: String) = context.dataStore.edit { it[Keys.voiceSttModel] = value.trim().ifBlank { VoiceConfig.DEFAULT_STT_MODEL } }
    /** Selects which TTS backend renders spoken replies: Fish Audio (direct) or OpenRouter (Qwen3 TTS). */
    suspend fun setTtsProvider(value: String) = context.dataStore.edit { it[Keys.ttsProvider] = TtsProvider.from(value).name }
    suspend fun setOpenRouterTtsModel(value: String) = context.dataStore.edit { it[Keys.openRouterTtsModel] = value.trim().ifBlank { "qwen/qwen-audio-3.0-tts-flash" } }
    suspend fun setOpenRouterTtsVoice(value: String) = context.dataStore.edit { it[Keys.openRouterTtsVoice] = value.trim().ifBlank { VoiceConfig.OPENROUTER_TTS_VOICE } }
    suspend fun setFishModel(value: String) = context.dataStore.edit { it[Keys.fishModel] = value.trim().ifBlank { VoiceConfig.FISH_MODEL_FREE } }
    suspend fun setFishVoiceId(value: String) = context.dataStore.edit { it[Keys.fishVoiceId] = value.trim() }
    /** Adds (or replaces by reference ID) a user-named Fish Audio voice. Blank names/IDs are ignored. */
    suspend fun saveFishCustomVoice(name: String, referenceId: String) {
        val cleanName = name.trim()
        val cleanId = referenceId.trim()
        if (cleanName.isBlank() || cleanId.isBlank()) return
        context.dataStore.edit { preferences ->
            val voices = decodeFishCustomVoices(preferences[Keys.fishCustomVoices])
                .filterNot { it.referenceId == cleanId } + FishCustomVoice(cleanName, cleanId)
            preferences[Keys.fishCustomVoices] = json.encodeToString(voices)
        }
    }
    suspend fun deleteFishCustomVoice(referenceId: String) = context.dataStore.edit { preferences ->
        val voices = decodeFishCustomVoices(preferences[Keys.fishCustomVoices])
            .filterNot { it.referenceId == referenceId.trim() }
        preferences[Keys.fishCustomVoices] = json.encodeToString(voices)
    }
    suspend fun setFishSpeed(value: Float) = context.dataStore.edit { it[Keys.fishSpeed] = value.coerceIn(0.5f, 2f) }
    suspend fun setVoiceEmotions(value: Boolean) = context.dataStore.edit { it[Keys.voiceEmotions] = value }
    suspend fun setVoiceFishStreaming(value: Boolean) = context.dataStore.edit { it[Keys.voiceFishStreaming] = value }
    suspend fun clear() = context.dataStore.edit { it.clear() }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun decodeReasoning(raw: String?): Map<String, ReasoningEffort> = raw.orEmpty()
            .split('|')
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('=')
                if (separator <= 0) return@mapNotNull null
                val id = entry.substring(0, separator)
                val effort = runCatching { ReasoningEffort.valueOf(entry.substring(separator + 1)) }.getOrNull()
                effort?.let { id to it }
            }.toMap()

        /** Saved custom voices; malformed JSON degrades to an empty list instead of breaking settings. */
        fun decodeFishCustomVoices(raw: String?): List<FishCustomVoice> =
            raw?.takeIf { it.isNotBlank() }
                ?.let { runCatching { json.decodeFromString<List<FishCustomVoice>>(it) }.getOrNull() }
                .orEmpty()
                .filter { it.name.isNotBlank() && it.referenceId.isNotBlank() }
    }
}

internal fun chooseAvailableModel(current: String?, available: Set<String>, preferred: String): String? {
    if (available.isEmpty()) return null
    return current?.takeIf { it in available }
        ?: preferred.takeIf { it in available }
        ?: available.minOrNull()
}

internal fun coerceSupportedBackgroundStyle(@Suppress("UNUSED_PARAMETER") storedValue: String?): BackgroundStyle =
    BackgroundStyle.CONSTELLATION

internal fun coerceVoiceReplyModel(storedValue: String?): String {
    val value = storedValue?.trim().orEmpty()
    if (value.isBlank()) return ""
    return value.takeIf { candidate -> VoiceReplyModels.fast.any { it.id == candidate } }
        ?: VoiceConfig.DEFAULT_LLM_MODEL
}

internal fun coerceFishModel(storedValue: String?): String {
    val value = storedValue?.trim().orEmpty()
    return value.takeIf { candidate -> FishEngines.options.any { it.id == candidate } }
        ?: VoiceConfig.FISH_MODEL_FREE
}

internal fun coerceOpenRouterTtsModel(storedValue: String?): String {
    val value = storedValue?.trim().orEmpty()
    return value.takeIf { candidate -> OpenRouterTtsModels.options.any { it.id == candidate } }
        ?: OpenRouterTtsModels.options.first().id
}
