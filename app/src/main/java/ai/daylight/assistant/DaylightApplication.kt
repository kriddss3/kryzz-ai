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
import ai.daylight.assistant.data.BotRepository
import ai.daylight.assistant.data.CronScheduler
import ai.daylight.assistant.data.LocationProvider
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.GeneratedOutputStore
import ai.daylight.assistant.data.preferences.AppPreferences
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
import ai.daylight.assistant.data.remote.CommonsImageClient
import ai.daylight.assistant.domain.AgentExecutor
import ai.daylight.assistant.domain.CrewDesk
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.domain.FindSession
import ai.daylight.assistant.domain.MakeStudio
import ai.daylight.assistant.domain.SwarmOrchestrator
import ai.daylight.assistant.security.SecureCredentialStore
import ai.daylight.assistant.ui.find.findImageLoader
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import coil.ImageLoader

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
            AssistantDatabase.MIGRATION_11_12,
            AssistantDatabase.MIGRATION_12_13,
            AssistantDatabase.MIGRATION_13_14
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

    /** Make's renders (C7) run here, in the app's scope, so leaving the screen doesn't stop them. */
    val make = MakeStudio(
        scope = applicationScope,
        newConversation = conversations::createConversation,
        render = { id, prompt, kind, options ->
            agent.send(id, prompt, AssistantMode.AGENT, kind.capability, media = options)
        }
    )

    /** Find (C8): Wikimedia Commons, key-free. Its search runs here so switching arms keeps it. */
    val commons = CommonsImageClient()
    val find = FindSession(
        scope = applicationScope,
        search = { query, filters, offset -> commons.search(query, filters, offset) }
    )

    /** Crew (C9): personal bots. Their routines are cron tasks that run in the bot's chat. */
    val bots = BotRepository(database.dao(), schedule = cron::schedule, cancel = cron::cancel)

    /** The bots' work desk: one job per bot, replies kept running when the screen closes. */
    val crew = CrewDesk(
        scope = applicationScope,
        prepare = bots::ensureConversation,
        send = { conversationId, text -> agent.send(conversationId, text) }
    )

    /** Find's thumbnails: Wikimedia hosts only, with Wikimedia's required user agent. */
    val findImages: ImageLoader by lazy { findImageLoader(application, commons.mediaHttp) }

    init {
        applicationScope.launch {
            runCatching { skills.seedStarterSkills() }
            if (preferences.state.first().locationEnabled) runCatching { location.refresh() }
        }
    }

    /** Fresh temp file for a TTS clip; the caller owns deletion after playback. */
    fun newVoiceFile(): File = File(application.cacheDir, "kryzz-reply-${System.currentTimeMillis()}.mp3")

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

    /** Fire-and-forget TLS/HTTP warm-up so the first STT POST does not pay DNS + handshake. */
    fun warmOpenRouter() {
        val key = credentials.openRouterKey() ?: return
        openRouter.warmConnection(key)
    }

    suspend fun transcribeVoice(file: File, sttModel: String): String {
        val model = coerceVoiceSttModel(sttModel)
        val key = credentials.openRouterKey() ?: throw AssistantApiException(
            ErrorKind.INVALID_KEY,
            "Add an OpenRouter API key in Settings before using voice chat."
        )
        val prepared = runCatching { trimWavSilence(file) }.getOrDefault(file)
        val prompt = STT_PROMPT_HINTS
        return if (model == VoiceConfig.NEMOTRON_STT_MODEL) {
            // Streaming path: split into 4 s chunks, transcribe each in parallel, concatenate.
            // The last chunk is allowed to be shorter. Worst case is a per-chunk timeout —
            // we still get a best-effort concatenation back instead of a hard failure.
            val chunks = ai.daylight.assistant.voice.splitWavChunks(prepared)
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
    }
}
