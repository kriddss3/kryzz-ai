package ai.daylight.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import ai.daylight.assistant.data.ConversationRepository
import ai.daylight.assistant.data.ConversationTitleGenerator
import ai.daylight.assistant.data.AttachmentStore
import ai.daylight.assistant.data.CronNotifications
import ai.daylight.assistant.data.CronScheduler
import ai.daylight.assistant.data.LocationProvider
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.GeneratedOutputStore
import ai.daylight.assistant.data.preferences.AppPreferences
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.data.SkillRepository
import ai.daylight.assistant.data.MemoryRepository
import ai.daylight.assistant.data.remote.OpenRouterClient
import ai.daylight.assistant.data.remote.ParallelClient
import ai.daylight.assistant.data.remote.SpeechGenerationRequest
import ai.daylight.assistant.data.remote.TextProviderClient
import ai.daylight.assistant.data.remote.defaultHttpClient
import ai.daylight.assistant.data.remote.FishAudioClient
import ai.daylight.assistant.data.remote.MiniMaxClient
import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ErrorKind
import ai.daylight.assistant.data.remote.PublicWebClient
import ai.daylight.assistant.domain.AgentExecutor
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.domain.SwarmOrchestrator
import ai.daylight.assistant.security.SecureCredentialStore
import ai.daylight.assistant.voice.TtsProvider
import ai.daylight.assistant.voice.VoiceConfig
import ai.daylight.assistant.voice.VoicePlayer
import ai.daylight.assistant.voice.VoiceRecorder
import ai.daylight.assistant.voice.audioMimeType
import ai.daylight.assistant.voice.coerceVoiceSttModel
import ai.daylight.assistant.voice.trimWavSilence
import ai.daylight.assistant.voice.STT_PROMPT_HINTS
import ai.daylight.assistant.voice.forFishSpeech
import ai.daylight.assistant.voice.withFishEmotionTags
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class DaylightApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CronNotifications.CHANNEL_ID,
                "Cron runs",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

class AppContainer(private val application: Application) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    val database = Room.databaseBuilder(application, AssistantDatabase::class.java, "daylight.db")
        .addMigrations(
            AssistantDatabase.MIGRATION_1_2,
            AssistantDatabase.MIGRATION_2_3,
            AssistantDatabase.MIGRATION_3_4,
            AssistantDatabase.MIGRATION_4_5,
            AssistantDatabase.MIGRATION_5_6,
            AssistantDatabase.MIGRATION_6_7,
            AssistantDatabase.MIGRATION_7_8,
            AssistantDatabase.MIGRATION_8_9,
            AssistantDatabase.MIGRATION_9_10,
            AssistantDatabase.MIGRATION_10_11,
            AssistantDatabase.MIGRATION_11_12
        )
        .build()
    val preferences = AppPreferences(application)
    val credentials = SecureCredentialStore(application)
    private val http = defaultHttpClient()
    val openRouter = OpenRouterClient(http, json)
    val minimax = MiniMaxClient(http, json)
    val parallel = ParallelClient(http, json)
    val fish = FishAudioClient(http, json)
    val voiceRecorder = VoiceRecorder(application)
    val voicePlayer = VoicePlayer(application)
    val conversations = ConversationRepository(database.dao(), json)
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val conversationTitles = ConversationTitleGenerator(conversations, openRouter, credentials, applicationScope)
    val skills = SkillRepository(database.dao())
    val memories = MemoryRepository(database.dao())
    val cron = CronScheduler(application)
    val location = LocationProvider(application, preferences)
    val outputs = GeneratedOutputStore(application)
    val attachments = AttachmentStore(application)
    fun textProvider(provider: ChatProvider): TextProviderClient =
        if (provider == ChatProvider.MINIMAX) minimax else openRouter

    fun textProviderKey(provider: ChatProvider): String? = when (provider) {
        ChatProvider.OPENROUTER -> credentials.openRouterKey()
        ChatProvider.MINIMAX -> credentials.minimaxKey()
    }

    fun mediaProvider(modelId: String): MediaProvider = when {
        modelId.startsWith("image-", ignoreCase = true) -> MediaProvider.MINIMAX
        modelId.startsWith("MiniMax-H", ignoreCase = true) || modelId.startsWith("Hailuo", ignoreCase = true) -> MediaProvider.MINIMAX
        modelId.startsWith("music-", ignoreCase = true) || modelId.startsWith("speech-", ignoreCase = true) -> MediaProvider.MINIMAX
        else -> MediaProvider.OPENROUTER
    }

    enum class MediaProvider { OPENROUTER, MINIMAX }

    val agent = AgentExecutor(
        database.dao(), preferences, credentials, openRouter, minimax, parallel, outputs, attachments, memories, json,
        locationProvider = location,
        publicWeb = PublicWebClient(),
        cronScheduler = cron,
        conversations = conversations,
        onFirstUserMessage = conversationTitles::request
    )
    val swarm = SwarmOrchestrator(
        database.dao(), preferences, credentials, openRouter, minimax, memories, json,
        onFirstUserMessage = conversationTitles::request
    )

    init {
        applicationScope.launch {
            runCatching { skills.seedStarterSkills() }
            if (preferences.state.first().locationEnabled) runCatching { location.refresh() }
        }
    }

    /**
     * Fresh temp file for a TTS clip; the caller owns deletion after playback. Unique even when
     * two sentences start rendering in the same millisecond.
     */
    fun newVoiceFile(): File = File.createTempFile("kryzz-reply-", ".mp3", application.cacheDir)

    /** Cached spoken rendering of a chat message, kept so replaying it needs no new TTS call. */
    fun voiceCacheFile(messageId: String): File =
        File(File(application.filesDir, "voice-cache").apply { mkdirs() }, "$messageId.mp3")

    /**
     * Renders [text] to [destination] with the selected TTS provider. Fish Audio routes
     * through its direct API; OpenRouter uses its OpenAI-compatible /audio/speech endpoint
     * with a Qwen3 TTS model. Returns the MIME type.
     */
    suspend fun synthesizeVoice(
        text: String,
        destination: File,
        speed: Double,
        provider: TtsProvider,
        fishModel: String,
        fishVoiceId: String?,
        openRouterModel: String,
        openRouterVoice: String,
        emotions: Boolean = true
    ): String = when (provider) {
        TtsProvider.FISH -> fish.synthesize(
            key = requireNotNull(credentials.fishKey()) { "Fish Audio key is missing" },
            text = text.forFishSpeech(emotions, fishModel),
            destination = destination,
            voiceId = fishVoiceId,
            speed = speed,
            model = fishModel
        )
        TtsProvider.OPENROUTER -> openRouter.generateSpeech(
            key = requireNotNull(credentials.openRouterKey()) { "OpenRouter key is missing" },
            request = SpeechGenerationRequest(
                model = openRouterModel.ifBlank { "qwen/qwen-audio-3.0-tts-flash" },
                input = (if (emotions) text else text.withFishEmotionTags(enabled = false)).take(4_096),
                voice = openRouterVoice.ifBlank { VoiceConfig.OPENROUTER_TTS_VOICE },
                responseFormat = "mp3",
                speed = speed
            ),
            destination = destination
        )
    }

    fun synthesizeVoiceStream(
        text: String,
        speed: Double,
        fishModel: String,
        fishVoiceId: String?,
        emotions: Boolean = true
    ): Flow<ByteArray> = fish.synthesizeStream(
        key = requireNotNull(credentials.fishKey()) { "Fish Audio key is missing" },
        text = text.forFishSpeech(emotions, fishModel),
        voiceId = fishVoiceId,
        speed = speed,
        model = fishModel
    )

    /**
     * The listening sounds ("Mm-hmm", "Yeah") in the current reply voice. Each is rendered
     * once per voice setup and kept in app storage, so playing one mid-sentence costs no
     * network time; a voice change renders a fresh set and drops the old one. [beforeRender]
     * runs before each render, so the caller can hold it until a quiet moment. Returns the
     * clips that are ready, or none when the reply voice is not set up.
     */
    suspend fun backchannelClips(
        settings: SettingsState,
        beforeRender: suspend () -> Unit = {}
    ): List<File> = withContext(Dispatchers.IO) {
        val provider = TtsProvider.from(settings.ttsProvider)
        val credentialed = when (provider) {
            TtsProvider.FISH -> credentials.hasFishKey() && settings.fishVoiceId.isNotBlank()
            TtsProvider.OPENROUTER -> credentials.hasOpenRouterKey()
        }
        if (!credentialed) return@withContext emptyList()
        val voiceKey = listOf(
            provider.name, settings.fishModel, settings.fishVoiceId,
            settings.openRouterTtsModel, settings.openRouterTtsVoice, settings.fishSpeed
        ).joinToString("|").hashCode().toUInt().toString(16)
        val root = File(application.filesDir, "voice-backchannel")
        val dir = File(root, voiceKey)
        root.listFiles()?.filter { it.name != voiceKey }?.forEach { it.deleteRecursively() }
        dir.mkdirs()
        VoiceConfig.BACKCHANNEL_PHRASES.mapIndexedNotNull { index, phrase ->
            val clip = File(dir, "$index.mp3")
            if (clip.isFile && clip.length() > 0L) return@mapIndexedNotNull clip
            // Render to a side file so a half-written clip is never mistaken for a finished one.
            val partial = File(dir, "$index.part")
            try {
                beforeRender()
                synthesizeVoice(
                    text = phrase,
                    destination = partial,
                    speed = settings.fishSpeed.toDouble(),
                    provider = provider,
                    fishModel = settings.fishModel,
                    fishVoiceId = settings.fishVoiceId.ifBlank { null },
                    openRouterModel = settings.openRouterTtsModel,
                    openRouterVoice = settings.openRouterTtsVoice,
                    emotions = false
                )
                if (partial.length() > 0L && partial.renameTo(clip)) clip else null
            } catch (cancelled: CancellationException) {
                partial.delete()
                throw cancelled
            } catch (_: Throwable) {
                partial.delete()
                null
            }
        }
    }

    /** Fire-and-forget TLS/HTTP warm-up so the first STT POST does not pay DNS + handshake. */
    fun warmOpenRouter() {
        val key = credentials.openRouterKey() ?: return
        openRouter.warmConnection(key)
    }

    /**
     * Warms the hosts a voice turn talks to while the user is still speaking: OpenRouter for
     * transcription and the reply, and Fish Audio when it renders the spoken answer.
     */
    suspend fun warmVoiceConnections() {
        warmOpenRouter()
        val provider = TtsProvider.from(preferences.state.first().ttsProvider)
        if (provider == TtsProvider.FISH && credentials.hasFishKey()) fish.warmConnection()
    }

    suspend fun transcribeVoice(file: File, sttModel: String): String {
        val model = coerceVoiceSttModel(sttModel)
        val key = credentials.openRouterKey() ?: throw AssistantApiException(
            ErrorKind.INVALID_KEY,
            "Add an OpenRouter API key in Settings before using voice chat."
        )
        // File work stays off the main thread; voice turns call this from the UI scope.
        val prepared = withContext(Dispatchers.IO) { runCatching { trimWavSilence(file) }.getOrDefault(file) }
        val prompt = STT_PROMPT_HINTS
        return if (model == VoiceConfig.NEMOTRON_STT_MODEL) {
            // Streaming path: split into 4 s chunks, transcribe each in parallel, concatenate.
            // The last chunk is allowed to be shorter. Worst case is a per-chunk timeout —
            // we still get a best-effort concatenation back instead of a hard failure.
            val chunks = withContext(Dispatchers.IO) { ai.daylight.assistant.voice.splitWavChunks(prepared) }
            try {
                openRouter.transcribeChunked(
                    key = key,
                    audioFile = prepared,
                    chunks = chunks,
                    model = model,
                    mimeType = audioMimeType(prepared),
                    language = VoiceConfig.DEFAULT_STT_LANGUAGE,
                    prompt = prompt
                )
            } finally {
                ai.daylight.assistant.voice.deleteWavChunks(chunks)
            }
        } else {
            openRouter.transcribe(
                key = key,
                audioFile = prepared,
                model = model,
                mimeType = audioMimeType(prepared),
                language = VoiceConfig.DEFAULT_STT_LANGUAGE,
                prompt = prompt
            )
        }
    }

    suspend fun clearAllLocalData() {
        cron.cancelAll()
        conversations.clear()
        preferences.clear()
        credentials.clear()
        outputs.clear()
        attachments.clear()
        memories.clearAll()
        File(application.filesDir, "voice-cache").deleteRecursively()
        File(application.filesDir, "voice-backchannel").deleteRecursively()
    }
}
