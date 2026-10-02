package ai.daylight.assistant.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ai.daylight.assistant.AppContainer
import ai.daylight.assistant.data.CronRecurrence
import ai.daylight.assistant.data.CronSchedulePresets
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.local.MemoryEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.data.remote.ModelBenchmark
import ai.daylight.assistant.data.remote.BenchmarkMeta
import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ErrorKind
import ai.daylight.assistant.domain.AgentCapability
import ai.daylight.assistant.domain.AgentExecutor
import ai.daylight.assistant.domain.AgentModelChoice
import ai.daylight.assistant.domain.AgentQuality
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.AssistantPreset
import ai.daylight.assistant.domain.MemoryEngine
import ai.daylight.assistant.domain.ModelPurpose
import ai.daylight.assistant.domain.MessageStatus
import ai.daylight.assistant.domain.SwarmStatus
import ai.daylight.assistant.domain.ReasoningEffort
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.domain.AppPalette
import ai.daylight.assistant.domain.BackgroundStyle
import ai.daylight.assistant.domain.ChatAttachment
import ai.daylight.assistant.domain.ChatDensity
import ai.daylight.assistant.domain.ChatProvider
import ai.daylight.assistant.domain.ConversationUsage
import ai.daylight.assistant.domain.FontStyle
import ai.daylight.assistant.domain.GradientPalette
import ai.daylight.assistant.domain.InlineQuestionProtocol
import ai.daylight.assistant.domain.TextPalette
import ai.daylight.assistant.domain.ActivityChipCounter
import ai.daylight.assistant.domain.ToolActivityDetail
import ai.daylight.assistant.domain.WorkLog
import ai.daylight.assistant.domain.WorkLogBuilder
import ai.daylight.assistant.domain.WorkLogRow
import ai.daylight.assistant.domain.VoiceReplyListener
import ai.daylight.assistant.voice.TtsProvider
import ai.daylight.assistant.voice.AdaptiveEndOfSpeechDetector
import ai.daylight.assistant.voice.VoiceConfig
import ai.daylight.assistant.voice.VoicePhase
import ai.daylight.assistant.voice.SpeechRunTracker
import ai.daylight.assistant.voice.SpokenReplySegmenter
import ai.daylight.assistant.voice.VOICE_BYTES_PER_MS
import ai.daylight.assistant.voice.VoiceActivitySample
import ai.daylight.assistant.voice.dropWavPrefix
import ai.daylight.assistant.voice.snapshotWav
import ai.daylight.assistant.voice.toSpeechText
import java.io.File
import android.os.SystemClock
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data class Success(val message: String) : CheckState
    data class Error(val message: String) : CheckState
}

private data class ChatDraftSnapshot(
    val composer: String = "",
    val editingFrom: Long? = null,
    val attachments: List<ChatAttachment> = emptyList()
)

private data class VoicePlaybackPlan(
    val settings: SettingsState,
    val provider: TtsProvider,
    val credentialed: Boolean
)

/** One sentence of a spoken reply, ready to play: a rendered clip, or a Fish PCM stream still downloading. */
private sealed interface ReplyAudio {
    class Clip(val file: File) : ReplyAudio
    class Stream(val text: String, val pcm: Channel<ByteArray>) : ReplyAudio

    /** Releases audio that will never be played. */
    fun discard() {
        when (this) {
            is Clip -> file.delete()
            is Stream -> pcm.cancel()
        }
    }
}

private object ChatDraftStore {
    private val drafts = ConcurrentHashMap<String, ChatDraftSnapshot>()

    fun read(conversationId: String): ChatDraftSnapshot = drafts[conversationId] ?: ChatDraftSnapshot()

    fun write(conversationId: String, draft: ChatDraftSnapshot) {
        if (draft.composer.isBlank() && draft.editingFrom == null && draft.attachments.isEmpty()) {
            drafts.remove(conversationId)
        } else {
            drafts[conversationId] = draft
        }
    }

    fun move(fromConversationId: String, toConversationId: String) {
        drafts.remove(fromConversationId)?.let { drafts[toConversationId] = it }
    }

    fun contains(conversationId: String): Boolean = drafts.containsKey(conversationId)

    fun conversationIds(): Set<String> = drafts.keys.toSet()
}

internal fun transferChatDraft(fromConversationId: String, toConversationId: String) {
    ChatDraftStore.move(fromConversationId, toConversationId)
}

internal fun hasChatDraft(conversationId: String): Boolean = ChatDraftStore.contains(conversationId)

internal fun chatDraftConversationIds(): Set<String> = ChatDraftStore.conversationIds()

private fun toolDisplayLabel(tool: String): String = when (tool) {
    "parallel_search" -> "Searching the web"
    "create_artifact" -> "Creating your file"
    "create_skill" -> "Building your skill"
    "create_code_project" -> "Writing your project"
    "get_current_time" -> "Checking the time"
    "calculate" -> "Calculating"
    "get_weather" -> "Checking the weather"
    "fetch_url" -> "Reading the page"
    "remember_fact" -> "Saving a memory"
    "recall_memories" -> "Looking up memories"
    "schedule_task" -> "Scheduling a reminder"
    "ask_user" -> "Waiting for your answer"
    "update_plan" -> "Updating the plan"
    AgentExecutor.TOOL_MEMORY_SAVE -> "Remembering that"
    AgentExecutor.TOOL_SEARCH_PAST_CHATS -> "Searching past chats"
    AgentExecutor.TOOL_AGENT_RUNNING -> "Agent at work"
    AgentExecutor.TOOL_REVIEWING -> "Reviewing the answer"
    AgentExecutor.TOOL_IMAGE_CREATION -> "Creating your image"
    AgentExecutor.TOOL_VIDEO_CREATION -> "Generating your video"
    AgentExecutor.TOOL_AUDIO_CREATION -> "Creating your audio"
    else -> tool.replace('_', ' ').replace('-', ' ')
}

class RootViewModel(container: AppContainer) : ViewModel() {
    val settings = container.preferences.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())
}

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {
    val openRouterKey = MutableStateFlow("")
    val parallelKey = MutableStateFlow("")
    val fishKey = MutableStateFlow("")
    val memoryEnabled = MutableStateFlow(false)
    val locationEnabled = MutableStateFlow(false)
    val openRouterCheck = MutableStateFlow<CheckState>(CheckState.Idle)
    val parallelCheck = MutableStateFlow<CheckState>(CheckState.Idle)
    val fishCheck = MutableStateFlow<CheckState>(CheckState.Idle)

    /** Called with the system permission result from the onboarding location card. */
    fun onLocationPermissionResult(granted: Boolean) = viewModelScope.launch {
        locationEnabled.value = granted
        if (granted) runCatching { container.location.refresh() }
    }

    fun testOpenRouter() = viewModelScope.launch {
        val key = openRouterKey.value.trim()
        if (key.isEmpty()) { openRouterCheck.value = CheckState.Error("Enter an OpenRouter key first."); return@launch }
        openRouterCheck.value = CheckState.Checking
        container.openRouter.testKey(key).fold(
            onSuccess = { container.credentials.setOpenRouterKey(key); openRouterCheck.value = CheckState.Success("OpenRouter key verified and stored securely.") },
            onFailure = { openRouterCheck.value = CheckState.Error(it.message ?: "OpenRouter key test failed.") }
        )
    }

    fun testParallel() = viewModelScope.launch {
        val key = parallelKey.value.trim()
        if (key.isEmpty()) { parallelCheck.value = CheckState.Error("Enter a Parallel key first."); return@launch }
        parallelCheck.value = CheckState.Checking
        container.parallel.testKey(key).fold(
            onSuccess = { container.credentials.setParallelKey(key); parallelCheck.value = CheckState.Success("Parallel key verified and stored securely.") },
            onFailure = { parallelCheck.value = CheckState.Error(it.message ?: "Parallel key test failed.") }
        )
    }

    fun testFish() = viewModelScope.launch {
        val key = fishKey.value.trim()
        if (key.isEmpty()) { fishCheck.value = CheckState.Error("Enter a Fish Audio key first."); return@launch }
        fishCheck.value = CheckState.Checking
        container.fish.testKey(key, voiceId = null, model = VoiceConfig.FISH_MODEL_FREE).fold(
            onSuccess = { container.credentials.setFishKey(key); fishCheck.value = CheckState.Success("Fish Audio key verified and stored securely.") },
            onFailure = { fishCheck.value = CheckState.Error(it.message ?: "Fish Audio key test failed.") }
        )
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        val open = openRouterKey.value.trim()
        val parallel = parallelKey.value.trim()
        val fish = fishKey.value.trim()
        if (open.isNotEmpty()) container.credentials.setOpenRouterKey(open)
        if (parallel.isNotEmpty()) container.credentials.setParallelKey(parallel)
        if (fish.isNotEmpty()) container.credentials.setFishKey(fish)
        container.preferences.setMemoryEnabled(memoryEnabled.value)
        container.preferences.setLocationEnabled(locationEnabled.value && container.location.hasPermission())
        container.preferences.completeOnboarding()
        onDone()
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ConversationListViewModel(private val container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val conversations = query
        .flatMapLatest { q -> container.conversations.conversations(archived = false, query = q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folders = container.conversations.folders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notice = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            container.conversations.deleteEmptyExcept(chatDraftConversationIds())
        }
    }

    fun create(onCreated: (String) -> Unit) = viewModelScope.launch { onCreated(container.conversations.createConversation()) }
    fun rename(id: String, title: String) = viewModelScope.launch { container.conversations.rename(id, title) }
    fun pin(id: String, value: Boolean) = viewModelScope.launch { container.conversations.pin(id, value) }
    fun createFolder(name: String) = viewModelScope.launch {
        runCatching { container.conversations.createFolder(name) }
            .onFailure { notice.value = it.message ?: "The folder could not be created." }
    }
    fun renameFolder(id: String, name: String) = viewModelScope.launch {
        runCatching { container.conversations.renameFolder(id, name) }
            .onFailure { notice.value = it.message ?: "The folder could not be renamed." }
    }
    fun deleteFolder(id: String) = viewModelScope.launch { container.conversations.deleteFolder(id) }
    fun assignFolder(conversationId: String, folderId: String?) = viewModelScope.launch {
        container.conversations.assignFolder(conversationId, folderId)
    }
    fun delete(id: String) = viewModelScope.launch { container.conversations.delete(id) }
    fun export(onReady: (String) -> Unit) = viewModelScope.launch {
        runCatching { container.conversations.exportJson() }
            .onSuccess(onReady)
            .onFailure { notice.value = it.message ?: "The chat backup could not be created." }
    }
    fun import(content: String) = viewModelScope.launch {
        runCatching { container.conversations.importJson(content) }
            .onSuccess { notice.value = "Imported $it conversation${if (it == 1) "" else "s"}." }
            .onFailure { notice.value = "Import failed: ${it.message ?: "invalid file"}" }
    }
}

class ChatViewModel(private val container: AppContainer, val conversationId: String) : ViewModel() {
    private val restoredDraft = ChatDraftStore.read(conversationId)
    val settings = container.preferences.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())
    val messages: StateFlow<List<MessageEntity>> = container.conversations.messages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val usage: StateFlow<ConversationUsage> = messages.map { entries ->
        val input = entries.sumOf { (it.promptTokens ?: 0).toLong() }
        val cacheHit = entries.sumOf { (it.cachedInputTokens ?: 0).toLong() }.coerceAtMost(input)
        val output = entries.sumOf { (it.completionTokens ?: 0).toLong() }
        ConversationUsage(
            totalTokens = entries.sumOf { (it.totalTokens ?: ((it.promptTokens ?: 0) + (it.completionTokens ?: 0))).toLong() },
            inputTokens = input,
            cacheHitInputTokens = cacheHit,
            cacheMissInputTokens = (input - cacheHit).coerceAtLeast(0),
            outputTokens = output,
            cost = entries.sumOf { it.cost ?: 0.0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUsage())
    val title = MutableStateFlow("Conversation")
    val composer = MutableStateFlow(restoredDraft.composer)
    val editingFrom = MutableStateFlow(restoredDraft.editingFrom)
    val generating = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val mode = MutableStateFlow(AssistantMode.CHAT)
    val capability = MutableStateFlow(AgentCapability.AUTO)
    val models = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val imageModels = MutableStateFlow<List<MediaModel>>(emptyList())
    val videoModels = MutableStateFlow<List<MediaModel>>(emptyList())
    val audioModels = MutableStateFlow<List<MediaModel>>(emptyList())
    val modelsLoading = MutableStateFlow(false)
    val modelsError = MutableStateFlow<String?>(null)
    val mediaModelsError = MutableStateFlow<String?>(null)
    val skillCreatedFor = MutableStateFlow<Set<String>>(emptySet())
    val pendingAttachments = MutableStateFlow(restoredDraft.attachments)
    val researchDepth = MutableStateFlow(2)
    val researchWidth = MutableStateFlow(3)
    /** Live activity chips: labels of concurrent work the agent is doing (tools, memory, media). */
    val activities = MutableStateFlow<List<String>>(emptyList())
    /**
     * v5.11: per-answer work logs keyed by assistant message id, built from the stored tool
     * rows. Tool rows never change after they are written, so they are parsed again only
     * when the set of rows changes, not on every streamed token.
     */
    val workLogs: StateFlow<Map<String, WorkLog>> = combine(
        messages,
        container.conversations.toolMessages(conversationId)
            .distinctUntilChanged { old, new -> old.map { it.id } == new.map { it.id } }
            .map { rows -> rows.map { WorkLogRow(it.createdAt, WorkLogBuilder.steps(it.toolName, it.content)) } }
    ) { visible, rows -> WorkLogBuilder.build(visible, rows) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val swarmStatus: StateFlow<SwarmStatus?> = container.swarm.status
    private var generation: Job? = null
    private var modelRefreshJob: Job? = null
    private var modelRefreshGeneration = 0

    init {
        viewModelScope.launch {
            combine(composer, editingFrom, pendingAttachments) { text, editing, attachments ->
                ChatDraftSnapshot(text, editing, attachments)
            }.collect { ChatDraftStore.write(conversationId, it) }
        }
        viewModelScope.launch {
            container.conversations.observeConversation(conversationId).collect { conversation ->
                if (conversation == null) {
                    title.value = "Conversation"
                } else {
                    title.value = conversation.title
                    researchDepth.value = conversation.searchDepth.coerceIn(1, 3)
                    researchWidth.value = conversation.searchWidth.coerceIn(1, 5)
                }
            }
        }
        viewModelScope.launch {
            // Calls of the same tool can run together (two searches in one round). v5.11: a
            // call with a detail gets its own chip ("Searching: pixel 10 battery test");
            // calls with the same label share one, removed when the last of them finishes.
            val chips = ActivityChipCounter()
            container.agent.toolActivity.collect { activity ->
                if (activity.conversationId != conversationId) return@collect
                val label = ToolActivityDetail.chipLabel(activity.toolName, activity.detail)
                    ?: toolDisplayLabel(activity.toolName)
                if (activity.toolName == AgentExecutor.TOOL_MEMORY_SAVE) {
                    // A saved memory is a short confirmation, not a persistent task chip.
                    if (!activity.started) return@collect
                    if (label !in activities.value) activities.value = activities.value + label
                    launch {
                        delay(MEMORY_CHIP_MS)
                        activities.value = activities.value - label
                    }
                    return@collect
                }
                activities.value = chips.update(activities.value, label, activity.started)
            }
        }
        viewModelScope.launch {
            container.preferences.state
                .map { it.chatProvider }
                .distinctUntilChanged()
                .collect { refreshModels() }
        }
    }

    fun refreshModels() {
        val refreshGeneration = ++modelRefreshGeneration
        modelRefreshJob?.cancel()
        modelRefreshJob = viewModelScope.launch {
            modelsLoading.value = true
            modelsError.value = null
            mediaModelsError.value = null
            models.value = emptyList()
            imageModels.value = emptyList()
            videoModels.value = emptyList()
            audioModels.value = emptyList()
            val provider = container.preferences.state.first().chatProvider
            try {
                supervisorScope {
                    val text = async { runCatching { container.textProvider(provider).models(container.textProviderKey(provider)) } }
                    val image = async { runCatching {
                        if (provider == ChatProvider.MINIMAX) container.minimax.imageModels() else container.openRouter.imageModels(container.credentials.openRouterKey())
                    } }
                    val video = async { runCatching {
                        if (provider == ChatProvider.MINIMAX) container.minimax.videoModels() else container.openRouter.videoModels(container.credentials.openRouterKey())
                    } }
                    val audio = async { runCatching {
                        if (provider == ChatProvider.MINIMAX) container.minimax.audioModels() else container.openRouter.audioModels(container.credentials.openRouterKey())
                    } }

                    val textResult = text.await()
                    currentCoroutineContext().ensureActive()
                    textResult.getOrNull()?.let { available ->
                        models.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            text = available.map { it.id }.toSet(),
                            // v5.10: tool-aware fallback for the agent, research and Max slots.
                            toolCapable = AgentModelChoice.toolCapableIds(available)
                        )
                    } ?: textResult.exceptionOrNull()?.let { failure ->
                        modelsError.value = failure.message ?: "Could not load models."
                    }

                    val imageResult = image.await()
                    currentCoroutineContext().ensureActive()
                    imageResult.getOrNull()?.let { available ->
                        imageModels.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            image = available.map { it.id }.toSet()
                        )
                    } ?: imageResult.exceptionOrNull()?.let { failure ->
                        mediaModelsError.value = failure.message ?: "Could not load image models."
                    }

                    val videoResult = video.await()
                    currentCoroutineContext().ensureActive()
                    videoResult.getOrNull()?.let { available ->
                        videoModels.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            video = available.map { it.id }.toSet()
                        )
                    } ?: videoResult.exceptionOrNull()?.let { failure ->
                        mediaModelsError.value = failure.message ?: "Could not load video models."
                    }

                    val audioResult = audio.await()
                    currentCoroutineContext().ensureActive()
                    audioResult.getOrNull()?.let { available ->
                        audioModels.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            audio = available.map { it.id }.toSet()
                        )
                    } ?: audioResult.exceptionOrNull()?.let { failure ->
                        mediaModelsError.value = failure.message ?: "Could not load audio models."
                    }
                }
            } finally {
                if (refreshGeneration == modelRefreshGeneration) modelsLoading.value = false
            }
        }
    }

    fun selectModel(id: String, purpose: ModelPurpose) = viewModelScope.launch { container.preferences.setModel(id, purpose) }
    fun setReasoning(id: String, effort: ReasoningEffort) = viewModelScope.launch { container.preferences.setReasoning(id, effort) }
    /** v5.10: Agent mode quality preset from the composer. */
    fun setAgentQuality(value: AgentQuality) = viewModelScope.launch { container.preferences.setAgentQuality(value) }
    /** v5.10: the one-time agent model suggestion; Switch moves to the new default, Keep changes nothing. */
    fun answerAgentModelSuggestion(switchModel: Boolean) = viewModelScope.launch {
        container.preferences.answerAgentModelSuggestion(if (switchModel) AgentModelChoice.OPENROUTER_AGENT_DEFAULT else null)
    }

    fun updateComposer(value: String) {
        composer.value = value
        persistDraft()
    }

    private fun persistDraft() {
        ChatDraftStore.write(
            conversationId,
            ChatDraftSnapshot(composer.value, editingFrom.value, pendingAttachments.value)
        )
    }

    fun attach(uris: List<Uri>) = viewModelScope.launch {
        val remaining = 8 - pendingAttachments.value.size
        if (remaining <= 0 || uris.size > remaining) {
            error.value = "Attach up to 8 files at a time."
            return@launch
        }
        runCatching { container.attachments.importUris(uris) }
            .onSuccess { imported ->
                pendingAttachments.value = (pendingAttachments.value + imported).distinctBy { it.id }
                persistDraft()
            }
            .onFailure { error.value = it.message ?: "The selected file could not be attached." }
    }

    fun removeAttachment(id: String) {
        pendingAttachments.value = pendingAttachments.value.filterNot { it.id == id }
        persistDraft()
    }

    fun setResearchTuning(depth: Int = researchDepth.value, width: Int = researchWidth.value) {
        researchDepth.value = depth.coerceIn(1, 3)
        researchWidth.value = width.coerceIn(1, 5)
        viewModelScope.launch { container.conversations.setResearchTuning(conversationId, researchDepth.value, researchWidth.value) }
    }

    fun createSkillFromConversation(answer: MessageEntity) = viewModelScope.launch {
        if (answer.id in skillCreatedFor.value || answer.role != "ASSISTANT" ||
            answer.mode != AssistantMode.AGENT.name || answer.status != MessageStatus.COMPLETE.name
        ) return@launch
        val userPrompt = messages.value.takeWhile { it.id != answer.id }.lastOrNull { it.role == "USER" }?.content.orEmpty()
        val baseName = title.value.ifBlank { userPrompt.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Agent workflow" }
        runCatching {
            container.skills.save(
                name = "${baseName.take(48).trim()} · ${answer.id.take(4)}",
                description = userPrompt.ifBlank { "Reusable workflow created from an Agent response." },
                instructions = buildString {
                    appendLine("Use this reusable workflow when the user asks for a similar outcome.")
                    if (userPrompt.isNotBlank()) appendLine("Original goal: $userPrompt")
                    appendLine("Reference result and working approach:")
                    append(answer.content)
                },
                examplePrompts = listOfNotNull(userPrompt.takeIf(String::isNotBlank))
            )
        }.onSuccess {
            skillCreatedFor.value = skillCreatedFor.value + answer.id
        }.onFailure {
            error.value = it.message ?: "The skill could not be saved locally."
        }
    }

    fun send() {
        val files = pendingAttachments.value
        val text = composer.value.trim().ifBlank { if (files.isNotEmpty()) "Please analyse the attached file${if (files.size == 1) "" else "s"}." else "" }
        if (text.isEmpty() || generating.value) return
        val editTimestamp = editingFrom.value
        composer.value = ""
        pendingAttachments.value = emptyList()
        editingFrom.value = null
        persistDraft()
        startGeneration {
            if (editTimestamp != null) container.conversations.deleteFrom(conversationId, editTimestamp)
            // The swarm only applies to text agent work: media capabilities and sends
            // with attachments keep the single-agent path.
            val useSwarm = mode.value == AssistantMode.AGENT && files.isEmpty() &&
                capability.value !in setOf(AgentCapability.IMAGE, AgentCapability.VIDEO, AgentCapability.AUDIO) &&
                container.preferences.state.first().agentSwarmEnabled
            if (useSwarm) {
                container.swarm.run(conversationId, text, capability.value)
            } else {
                container.agent.send(conversationId, text, mode.value, capability.value, files)
            }
            title.value = container.conversations.conversation(conversationId)?.title ?: title.value
        }
    }

    fun regenerate() {
        if (generating.value) return
        val last = messages.value.lastOrNull { it.role == "ASSISTANT" } ?: return
        // v5.11: the replaced answer's tool rows go too, so the new answer's work log shows
        // only the new attempt.
        val turnStart = messages.value.lastOrNull { it.role == "USER" && it.createdAt <= last.createdAt }?.createdAt
        startGeneration {
            container.conversations.deleteMessage(last.id)
            turnStart?.let { container.conversations.deleteToolRowsFrom(conversationId, it) }
            container.agent.generateReply(conversationId)
        }
    }

    // ── Interactive question cards (v5.7.2) ──────────────────────────────────
    /**
     * The agent's pending `ask_user` card, if any. The chat screen renders it as a
     * floating card; [answerPendingQuestion] delivers the result back to the suspended
     * tool call. Filtered to this conversation.
     */
    val pendingQuestion = container.agent.pendingQuestion

    /** The agent's live `update_plan` checklist; the screen shows it for this conversation only. */
    val agentPlan = container.agent.agentPlan

    /** Answer (or dismiss, with null) the agent's pending question card. */
    fun answerPendingQuestion(answer: String?) {
        container.agent.answerPendingQuestion(conversationId, answer)
    }

    /**
     * Sends the user's choice from an inline chat-mode question card as the next user
     * message and starts the reply — same path as [send], but the text is composed
     * from the card instead of the composer.
     */
    fun sendQuestionAnswer(question: String, answer: String) {
        if (generating.value) return
        val text = InlineQuestionProtocol.formatAnswerEcho(question, answer)
        startGeneration {
            container.agent.send(conversationId, text, mode.value, capability.value, emptyList())
            title.value = container.conversations.conversation(conversationId)?.title ?: title.value
        }
    }

    fun startEditing(message: MessageEntity) {
        composer.value = message.content
        pendingAttachments.value = container.conversations.attachments(message)
        editingFrom.value = message.createdAt
        persistDraft()
    }

    fun cancelEditing() {
        composer.value = ""
        pendingAttachments.value = emptyList()
        editingFrom.value = null
        persistDraft()
    }
    fun stop() { generation?.cancel(); generation = null; generating.value = false }

    // ── Voice chat ─────────────────────────────────────────────────────────────
    val voicePhase = MutableStateFlow(VoicePhase.IDLE)
    val voiceLevel = MutableStateFlow(0f)
    val voiceTranscript = MutableStateFlow("")
    /**
     * True while a web search is running during a voice turn. The overlay switches to a
     * "Checking online" state with an internet icon and the search cue plays, so the user
     * is never sitting in silence wondering whether anything is happening.
     */
    val voiceSearching = MutableStateFlow(false)
    /**
     * True while the user has muted themselves in the voice overlay. While muted the
     * AudioRecord capture loop writes zeros instead of the captured samples, so Kryzz
     * never hears anything until the user unmutes. The phase stays at LISTENING so the
     * session keeps its place — unmute is seamless and there is no auto-stop.
     */
    val voiceMicMuted = MutableStateFlow(false)
    private var voiceJob: Job? = null
    private var voicePipelineJob: Job? = null
    private var voiceFillerJob: Job? = null
    /** Set before deliberately cancelling the reply pipeline so its tail never reports a failure. */
    private var intentionalVoiceCancel = false
    private val voiceEndDetector = AdaptiveEndOfSpeechDetector()
    private var voiceStartedAt = 0L
    /**
     * Bumped whenever a voice turn is abandoned (barge-in, bubble tap, close). A turn whose
     * number no longer matches is stale: its late cancellation or failure must not reset the
     * phase of the session that moved on.
     */
    private var voiceTurn = 0L
    /**
     * PCM bytes at the start of the current recording to drop before transcription. After a
     * barge-in the recording also holds the reply-time audio from before the user spoke.
     */
    private var bargeTrimBytes = 0L
    /** Transcript started during the closing silence of the current utterance, if any. */
    private var speculativeStt: Deferred<String>? = null

    // ── Per-message read aloud (Fish Audio, cached per message) ──────────────
    /** Message whose cached/generated audio is currently playing, if any. */
    val speakingMessageId = MutableStateFlow<String?>(null)
    /** Message whose audio is currently being synthesized, if any. */
    val synthesizingMessageId = MutableStateFlow<String?>(null)
    private var speakMessageJob: Job? = null

    /** Opens the microphone. The session auto-closes after speech + silence or the record cap.
     *  When [preserveMute] is true (re-arm after a reply), an existing mic mute is kept so the
     *  user's mute choice survives across reply cycles; a fresh start always begins unmuted. */
    fun beginVoice(preserveMute: Boolean = false) {
        if (voicePhase.value != VoicePhase.IDLE || generating.value) return
        if (!preserveMute) voiceMicMuted.value = false
        voiceTranscript.value = ""
        voiceLevel.value = 0f
        cancelSpeculativeStt()
        bargeTrimBytes = 0L
        voiceStartedAt = SystemClock.elapsedRealtime()
        voiceEndDetector.reset(voiceStartedAt)
        // Speech-to-text, the reply model and the TTS host all get their TLS session while the
        // user is still talking, so no step of the turn pays a handshake.
        viewModelScope.launch(Dispatchers.IO) { runCatching { container.warmVoiceConnections() } }
        container.voiceRecorder.start().onFailure {
            error.value = it.message ?: "Could not open the microphone."
            return
        }
        // start() always opens unmuted; a mute carried over from the last reply must hold.
        container.voiceRecorder.muted = voiceMicMuted.value
        voicePhase.value = VoicePhase.LISTENING
        voiceJob = viewModelScope.launch {
            while (voicePhase.value == VoicePhase.LISTENING) {
                val level = if (voiceMicMuted.value) 0 else container.voiceRecorder.level()
                val now = SystemClock.elapsedRealtime()
                if (voiceMicMuted.value) {
                    // Keep the end-of-speech detector fed with silence so a stale high level
                    // from before the mute doesn't immediately count as speech the moment the
                    // user unmutes.
                    voiceEndDetector.observe(0, now)
                    voiceLevel.value = 0f
                } else {
                    val activity = voiceEndDetector.observe(level, now)
                    voiceLevel.value = activity.level
                    if (activity.stopReason != null) {
                        stopVoiceRecording(automatic = true)
                        return@launch
                    }
                    updateSpeculativeStt(activity, now)
                }
                delay(VOICE_POLL_MS)
            }
        }
    }

    /** Bubble tap: end listening / skip the current step, depending on the phase. */
    fun tapVoiceBubble() {
        when (voicePhase.value) {
            VoicePhase.LISTENING -> stopVoiceRecording()
            VoicePhase.PROCESSING -> cancelVoice()
            // Interrupting the reply also discards the barge-in mic session and, if the model
            // is still writing, the rest of that reply; the session then listens from scratch.
            VoicePhase.SPEAKING -> {
                voiceJob?.cancel()
                voiceJob = null
                intentionalVoiceCancel = true
                voicePipelineJob?.cancel()
                voicePipelineJob = null
                abandonVoiceTurn()
                container.voicePlayer.stop()
                container.voiceRecorder.cancel()
                rearmVoice()
            }
            VoicePhase.IDLE -> {}
        }
    }

    /**
     * Toggles whether the user's microphone is muted inside an active voice session.
     * While muted, the recorder still drains the AudioRecord buffer (so the OS echo
     * canceller keeps working during the reply) but writes silence instead of captured
     * samples — Kryzz hears nothing, the end-of-speech detector stays idle, and the
     * session stays open. Tapping the same button again unmutes; unmute is seamless
     * because the WAV length keeps growing the whole time.
     */
    fun setVoiceMuted(muted: Boolean) {
        if (voicePhase.value == VoicePhase.IDLE) return
        voiceMicMuted.value = muted
        container.voiceRecorder.muted = muted
        if (muted) voiceLevel.value = 0f
    }

    fun stopVoiceRecording(automatic: Boolean = false) {
        if (voicePhase.value != VoicePhase.LISTENING) return
        voiceJob?.cancel()
        voiceJob = null
        val file = container.voiceRecorder.stop()
        // After a barge-in the reply-time audio before the user spoke is dropped before upload;
        // judge the clip by what will actually be sent.
        val trimBytes = bargeTrimBytes
        bargeTrimBytes = 0L
        val sentBytes = (file?.length() ?: 0L) - trimBytes
        val duration = SystemClock.elapsedRealtime() - voiceStartedAt
        val noConfirmedSpeech = !voiceEndDetector.hasUsableSpeech
        // Automatic submission requires confirmed speech. An explicit orb tap remains the
        // escape hatch for very soft/brief speech that peak-amplitude detection may miss.
        val unusable = sentBytes < 2_000 || duration < 400L ||
            (automatic && noConfirmedSpeech && (duration < 900L || sentBytes < 6_000))
        if (file == null || unusable) {
            cancelSpeculativeStt()
            file?.delete()
            // Nothing usable was captured: stay in voice mode and keep listening.
            rearmVoice()
            return
        }
        val early = speculativeStt
        speculativeStt = null
        voiceLevel.value = 0f
        voicePhase.value = VoicePhase.PROCESSING
        processVoiceClip(file, trimBytes, early)
    }

    /**
     * Starts transcribing the current utterance once the user has been quiet for
     * [VoiceConfig.SPECULATIVE_STT_SILENCE_MS], while the end-of-speech window still runs.
     * Most of the speech-to-text round trip then overlaps that wait instead of following it,
     * which hides the extra latency of a slower model such as Whisper. Speech resuming
     * discards the early transcript and the finished clip is used instead.
     */
    private fun updateSpeculativeStt(activity: VoiceActivitySample, now: Long) {
        if (activity.isVoiceActive) {
            cancelSpeculativeStt()
            return
        }
        if (speculativeStt != null || !voiceEndDetector.hasUsableSpeech) return
        if (voiceEndDetector.silenceMs(now) < VoiceConfig.SPECULATIVE_STT_SILENCE_MS) return
        val source = container.voiceRecorder.currentFile() ?: return
        val skip = bargeTrimBytes
        speculativeStt = viewModelScope.async(Dispatchers.IO) {
            val snapshot = File(source.parentFile, "${source.nameWithoutExtension}-early-$now.wav")
            try {
                snapshotWav(source, snapshot, skip)
                container.transcribeVoice(snapshot, container.preferences.state.first().voiceSttModel)
            } finally {
                snapshot.delete()
            }
        }
    }

    private fun cancelSpeculativeStt() {
        speculativeStt?.cancel()
        speculativeStt = null
    }

    // ── Dictation ─────────────────────────────────────────────────────────────
    val dictating = MutableStateFlow(false)
    val dictationLevel = MutableStateFlow(0f)
    private var dictationJob: Job? = null

    fun beginDictation() {
        if (voicePhase.value != VoicePhase.IDLE || dictating.value) return
        dictationLevel.value = 0f
        container.warmOpenRouter()
        container.voiceRecorder.start().onFailure {
            error.value = it.message ?: "Could not open the microphone."
            return
        }
        dictating.value = true
        dictationJob = viewModelScope.launch {
            while (dictating.value) {
                dictationLevel.value = container.voiceRecorder.level().toFloat() / 32_767f
                delay(40)
            }
        }
    }

    fun stopDictation() {
        if (!dictating.value) return
        dictationJob?.cancel()
        dictationJob = null
        dictating.value = false
        dictationLevel.value = 0f
        val file = container.voiceRecorder.stop()
        if (file == null || file.length() < 2_000) {
            file?.delete()
            return
        }
        viewModelScope.launch {
            try {
                val settings = container.preferences.state.first()
                val text = container.transcribeVoice(file, settings.voiceSttModel).trim()
                file.delete()
                if (text.isNotBlank()) {
                    composer.value = (composer.value.trimEnd() + " " + text).trimStart()
                    persistDraft()
                } else {
                    error.value = "Could not hear anything clear. Try dictation again."
                }
            } catch (t: Throwable) {
                file.delete()
                error.value = t.message ?: "Dictation failed."
            }
        }
    }

    fun cancelVoice() {
        voiceJob?.cancel()
        voiceJob = null
        intentionalVoiceCancel = true
        voicePipelineJob?.cancel()
        voicePipelineJob = null
        cancelSpeculativeStt()
        bargeTrimBytes = 0L
        voiceMicMuted.value = false
        when (voicePhase.value) {
            VoicePhase.LISTENING -> container.voiceRecorder.cancel()
            VoicePhase.PROCESSING -> Unit
            VoicePhase.SPEAKING -> {
                container.voicePlayer.stop()
                container.voiceRecorder.cancel()
            }
            VoicePhase.IDLE -> {}
        }
        // A reply still being transcribed or written for this session is dropped too. In IDLE
        // the running generation (if any) is not a live voice turn, so it is left alone.
        if (voicePhase.value != VoicePhase.IDLE) abandonVoiceTurn()
        voicePhase.value = VoicePhase.IDLE
        voiceLevel.value = 0f
        voiceSearching.value = false
    }

    /**
     * Drops the voice turn in flight: its late failure or cancellation is ignored, and a
     * reply the model is still writing is stopped (it stays in the chat as interrupted).
     */
    private fun abandonVoiceTurn() {
        voiceTurn++
        voiceFillerJob?.cancel()
        voiceFillerJob = null
        if (generating.value) {
            generation?.cancel()
            generation = null
            generating.value = false
        }
    }

    /** Stops the reply's playback, rendering and barge-in mic when its turn fails mid-reply. */
    private fun haltVoiceOutput() {
        voiceJob?.cancel()
        voiceJob = null
        intentionalVoiceCancel = true
        voicePipelineJob?.cancel()
        voicePipelineJob = null
        voiceFillerJob?.cancel()
        voiceFillerJob = null
        runCatching { container.voicePlayer.stop() }
        container.voiceRecorder.cancel()
    }

    /**
     * Hands the session back to listening after a spoken reply. The TTS turns itself
     * off the moment it finishes (or is interrupted); voice chat never drops back to
     * the normal composer on its own — only the close button or back gesture exits.
     */
    private fun rearmVoice() {
        voiceJob?.cancel()
        voiceJob = null
        intentionalVoiceCancel = true
        voicePipelineJob?.cancel()
        voicePipelineJob = null
        // The mic stays open while the reply plays (so the user can talk over it);
        // close that session before re-arming a fresh one.
        container.voiceRecorder.stop()?.delete()
        voiceLevel.value = 0f
        voiceSearching.value = false
        voicePhase.value = VoicePhase.IDLE
        // Preserve voiceMicMuted: if the user muted during the reply, the next listening
        // turn stays muted until they explicitly unmute or end the session.
        beginVoice(preserveMute = true)
    }

    private fun processVoiceClip(file: File, trimBytes: Long, early: Deferred<String>?) {
        val turn = ++voiceTurn
        generation = viewModelScope.launch {
            generating.value = true
            var replySegments: Channel<String>? = null
            try {
                // Credential decryption and voice-output readiness are independent of STT/LLM.
                // Resolve them on IO while the remote pipeline does its work, then reuse the
                // settings snapshot instead of waiting on the preferences flow again for TTS.
                val settings = container.preferences.state.first()
                val provider = TtsProvider.from(settings.ttsProvider)
                val playbackReady = async(Dispatchers.IO) {
                    when (provider) {
                        TtsProvider.FISH -> !container.credentials.fishKey().isNullOrBlank() && settings.fishVoiceId.isNotBlank()
                        TtsProvider.OPENROUTER -> !container.credentials.openRouterKey().isNullOrBlank()
                    }
                }
                if (trimBytes > 0L) withContext(Dispatchers.IO) { runCatching { dropWavPrefix(file, trimBytes) } }
                val text = transcribeVoiceTurn(file, settings.voiceSttModel, early).trim()
                file.delete()
                if (text.isBlank()) {
                    // Nothing intelligible was captured (e.g. an accidental barge-in):
                    // stay in the voice session and listen again instead of ending it.
                    error.value = "Could not hear anything clear. Try again and speak a little closer to the phone."
                    generating.value = false
                    rearmVoice()
                    return@launch
                }
                voiceTranscript.value = text.take(200)
                val plan = VoicePlaybackPlan(settings, provider, playbackReady.await())
                // Sentences go to speech as the model writes them, so Kryzz starts talking
                // after its first sentence instead of after the whole answer.
                val segments = Channel<String>(Channel.UNLIMITED).also { replySegments = it }
                val segmenter = SpokenReplySegmenter()
                if (plan.credentialed) speakAnswer(segments, plan)
                // While the agent runs (and may invoke parallel_search), play quick filler
                // phrases ("Checking online, give me a sec") so the user isn't sitting in
                // silence during the search round-trip. The reply pipeline stops it before
                // the first spoken sentence.
                val fillerJob = startVoiceFillerListener(settings, provider)
                voiceFillerJob = fillerJob
                val answer = try {
                    container.agent.send(
                        conversationId,
                        text,
                        AssistantMode.CHAT,
                        AgentCapability.AUTO,
                        voiceMode = true,
                        onVoiceText = VoiceReplyListener { round, value, done ->
                            segmenter.onText(round, value).forEach { segments.trySend(it) }
                            if (done) segmenter.finish().forEach { segments.trySend(it) }
                        }
                    )
                } finally {
                    voiceSearching.value = false
                    fillerJob.cancel()
                }
                if (answer == null) {
                    if (turn == voiceTurn) {
                        haltVoiceOutput()
                        voicePhase.value = VoicePhase.IDLE
                        error.value = "The model returned no usable reply. Try again."
                    }
                    return@launch
                }
                title.value = container.conversations.conversation(conversationId)?.title ?: title.value
                if (!plan.credentialed && turn == voiceTurn) {
                    error.value = when (provider) {
                        TtsProvider.FISH -> "The reply is in the chat. Add a Fish Audio key in Settings → Voice chat and pick a voice to hear responses."
                        TtsProvider.OPENROUTER -> "The reply is in the chat. Add an OpenRouter API key in Settings → API keys to hear voice replies."
                    }
                    container.voiceRecorder.stop()?.delete()
                    voicePhase.value = VoicePhase.IDLE
                }
            } catch (cancelled: CancellationException) {
                file.delete()
                if (turn == voiceTurn) {
                    haltVoiceOutput()
                    voicePhase.value = VoicePhase.IDLE
                }
            } catch (t: Throwable) {
                file.delete()
                if (turn == voiceTurn) {
                    haltVoiceOutput()
                    voicePhase.value = VoicePhase.IDLE
                    error.value = t.message ?: "Voice chat failed."
                }
            } finally {
                // Clear the flag before ending the reply stream: the reply pipeline re-arms the
                // mic when the stream ends, and beginVoice refuses to start while a turn runs.
                if (turn == voiceTurn) generating.value = false
                replySegments?.close()
            }
        }
    }

    /** The early transcript when it finished cleanly, otherwise a transcription of the whole clip. */
    private suspend fun transcribeVoiceTurn(file: File, sttModel: String, early: Deferred<String>?): String {
        val earlyText = early?.let { pending ->
            try {
                pending.await()
            } catch (_: Throwable) {
                // A failed early pass falls back to the full clip; only our own cancellation propagates.
                currentCoroutineContext().ensureActive()
                null
            }
        }
        return earlyText?.takeIf { it.isNotBlank() } ?: container.transcribeVoice(file, sttModel)
    }

    /**
     * Subscribes to [container.agent]'s filler request flow and synthesises a short spoken
     * phrase ("Let me search that up", "Checking", etc.) whenever a long-running tool starts
     * during voice chat. A short tone also plays so the user is audibly assured the search
     * is in progress. Returns the collector job so the caller can cancel it once the real
     * reply is ready.
     */
    private fun startVoiceFillerListener(
        @Suppress("UNUSED_PARAMETER") settings: SettingsState,
        @Suppress("UNUSED_PARAMETER") provider: TtsProvider
    ): Job {
        // Always start the filler listener regardless of TTS provider or Fish streaming
        // toggle. speakFiller uses synthesizeVoice which works for both Fish and OpenRouter
        // TTS, so OpenRouter TTS users also hear the "checking online" cue.
        return viewModelScope.launch {
            try {
                container.agent.voiceFillerRequest.collect { label ->
                    voiceSearching.value = true
                    // Play a short two-note ascending cue to signal the search is in progress.
                    runCatching { container.voicePlayer.playSearchTone() }
                    // Once part of the answer is being spoken, a filler phrase would talk over it.
                    if (voicePhase.value != VoicePhase.PROCESSING) return@collect
                    val phrase = when (label) {
                        "checking online" -> listOf(
                            "Let me search that up.",
                            "Checking.",
                            "Let me look that up.",
                            "Give me a second."
                        ).random()
                        else -> "One moment."
                    }
                    speakFiller(phrase, settings, provider)
                }
            } catch (_: CancellationException) {
                // Normal shutdown once the real reply starts.
            }
        }
    }

    /** Synthesises one short filler phrase and plays it. Errors are swallowed — filler is best-effort. */
    private suspend fun speakFiller(
        phrase: String,
        settings: SettingsState,
        provider: TtsProvider
    ) {
        runCatching {
            val clip = container.newVoiceFile()
            container.synthesizeVoice(
                text = phrase.toSpeechText(),
                destination = clip,
                speed = settings.fishSpeed.toDouble(),
                provider = provider,
                fishModel = settings.fishModel,
                fishVoiceId = settings.fishVoiceId.ifBlank { null },
                openRouterModel = settings.openRouterTtsModel,
                openRouterVoice = settings.openRouterTtsVoice,
                emotions = false
            )
            // The answer may have started while the phrase rendered; never cut into it.
            if (voicePhase.value != VoicePhase.PROCESSING) {
                clip.delete()
                return@runCatching
            }
            // Play and wait for completion (or cancellation) so two fillers don't overlap.
            suspendCancellableCoroutine<Unit> { cont ->
                container.voicePlayer.play(clip, {}, { failure ->
                    runCatching { clip.delete() }
                    if (cont.isActive) {
                        if (failure == null) cont.resume(Unit)
                        else cont.resumeWithException(failure)
                    }
                })
                cont.invokeOnCancellation {
                    // Cancelled when the answer is about to speak: silence the filler first.
                    container.voicePlayer.stop()
                    runCatching { clip.delete() }
                }
            }
        }
    }

    /**
     * Speaks the reply while the model is still writing it. [segments] delivers sentences as
     * they complete (see [SpokenReplySegmenter]); each is rendered as soon as it arrives and
     * played in order, the next one rendering while the current one plays. Fish takes one
     * request at a time, OpenRouter two.
     *
     * While the reply plays, the microphone stays open: sustained speech from the user ducks
     * and then stops the TTS and hands the session back to listening (barge-in), so the user
     * can talk over Kryzz and the next thing they say is sent as a new prompt.
     */
    private fun speakAnswer(segments: ReceiveChannel<String>, plan: VoicePlaybackPlan) {
        voicePipelineJob = viewModelScope.launch {
            intentionalVoiceCancel = false
            val settings = plan.settings
            val provider = plan.provider
            val fishVoiceId = settings.fishVoiceId.ifBlank { null }
            // Fish PCM streaming is optional: on some devices/tiers the HTTP PCM body ends
            // after the first ~100 ms, so the full-MP3 path stays available in Settings.
            val useFishStream = provider == TtsProvider.FISH && settings.voiceFishStreaming
            // Parallel Fish requests risk the per-key concurrency limit. One at a time still
            // renders the next sentence during playback, because Fish renders faster than speech.
            val synthSemaphore = Semaphore(if (provider == TtsProvider.FISH) 1 else 2)
            val ready = Channel<Deferred<ReplyAudio?>>(Channel.UNLIMITED)
            val rendered = mutableListOf<File>()
            var firstError: Throwable? = null
            val producer = launch {
                try {
                    for (segment in segments) {
                        val spoken = segment.toSpeechText()
                        if (spoken.isBlank()) continue
                        val audio: Deferred<ReplyAudio?> = if (useFishStream) {
                            val pcm = Channel<ByteArray>(Channel.UNLIMITED)
                            // Undispatched, so sentences queue for the render slot in order; the
                            // download itself then continues on IO.
                            launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    synthSemaphore.withPermit {
                                        container.synthesizeVoiceStream(
                                            text = spoken,
                                            speed = settings.fishSpeed.toDouble(),
                                            fishModel = settings.fishModel,
                                            fishVoiceId = fishVoiceId,
                                            emotions = settings.voiceEmotions
                                        ).collect { pcm.send(it) }
                                    }
                                    pcm.close()
                                } catch (cancelled: CancellationException) {
                                    pcm.cancel()
                                    throw cancelled
                                } catch (t: Throwable) {
                                    pcm.close(t)
                                }
                            }
                            CompletableDeferred(ReplyAudio.Stream(spoken, pcm))
                        } else {
                            async {
                                synthSemaphore.withPermit {
                                    val clip = container.newVoiceFile()
                                    try {
                                        container.synthesizeVoice(
                                            text = spoken,
                                            destination = clip,
                                            speed = settings.fishSpeed.toDouble(),
                                            provider = provider,
                                            fishModel = settings.fishModel,
                                            fishVoiceId = fishVoiceId,
                                            openRouterModel = settings.openRouterTtsModel,
                                            openRouterVoice = settings.openRouterTtsVoice,
                                            emotions = settings.voiceEmotions
                                        )
                                        rendered += clip
                                        ReplyAudio.Clip(clip)
                                    } catch (cancelled: CancellationException) {
                                        clip.delete()
                                        throw cancelled
                                    } catch (t: Throwable) {
                                        clip.delete()
                                        if (firstError == null) firstError = t
                                        null
                                    }
                                }
                            }
                        }
                        ready.send(audio)
                    }
                } finally {
                    ready.close()
                }
            }
            var playedAny = false
            var playbackStarted = false
            var segmentCount = 0
            try {
                for (pending in ready) {
                    if (voicePhase.value != VoicePhase.PROCESSING && voicePhase.value != VoicePhase.SPEAKING) break
                    if (segmentCount++ == 0) {
                        // A search filler must not talk over the answer.
                        voiceFillerJob?.cancel()
                        voiceFillerJob = null
                        // Arm barge-in while the first sentence is still rendering and before any
                        // AudioTrack starts (opening the mic after a PCM track started cut it off
                        // on some devices), so the user can talk over Kryzz from its first word.
                        voicePhase.value = VoicePhase.SPEAKING
                        startVoicePlaybackMonitor()
                    }
                    val audio = pending.await() ?: continue
                    if (voicePhase.value != VoicePhase.SPEAKING) {
                        audio.discard()
                        break
                    }
                    try {
                        when (audio) {
                            is ReplyAudio.Clip -> playVoiceClipAwait(audio.file) { playbackStarted = true }
                            is ReplyAudio.Stream -> playFishStreamAwait(audio, settings, fishVoiceId) { playbackStarted = true }
                        }
                        playedAny = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        // One sentence failing to play does not end the reply; the next one still plays.
                        if (firstError == null) firstError = t
                    }
                }
            } catch (_: CancellationException) {
                // Barge-in or a manual stop: the pipeline is gone; audio that was rendered but
                // never heard is deleted below.
            }
            producer.cancel()
            runCatching { producer.join() }
            while (true) {
                val leftover = ready.tryReceive().getOrNull() ?: break
                leftover.cancel()
            }
            rendered.forEach { it.delete() }
            val heard = playedAny || playbackStarted
            // Whoever stops the reply on purpose (barge-in, bubble tap, close, a failed turn) sets
            // intentionalVoiceCancel first, and this cleanup can run inside their cancel() call.
            // Re-arming then would open a fresh mic and throw away the user's barge-in words.
            if (!intentionalVoiceCancel && voicePhase.value == VoicePhase.SPEAKING && heard) {
                // The TTS switches itself off as soon as the reply ends; the session then
                // re-arms the microphone instead of dropping back to normal chatting.
                error.value = null
                rearmVoice()
            } else if (!intentionalVoiceCancel && !heard &&
                (voicePhase.value == VoicePhase.PROCESSING || voicePhase.value == VoicePhase.SPEAKING)
            ) {
                // Nothing was ever heard: report only genuine generation failures here.
                // Intentional interrupts (barge-in, orb tap, close) skip this branch, and
                // playback errors after audio started are not generation failures.
                voiceJob?.cancel()
                voiceJob = null
                container.voiceRecorder.stop()?.delete()
                voicePhase.value = VoicePhase.IDLE
                error.value = if (segmentCount == 0) {
                    "The voice reply did not contain anything that could be spoken."
                } else {
                    firstError?.message ?: "The voice reply could not be generated."
                }
            }
        }
    }

    /**
     * Watches reply playback for the user talking over it. The recording that starts here keeps
     * running through a barge-in and becomes the user's next utterance, so nothing they say
     * while the reply stops is lost; only the reply-time audio from before they started
     * talking is cut off before upload (see [bargeTrimBytes]).
     */
    private fun startVoicePlaybackMonitor() {
        val recorderLive = container.voiceRecorder.start().isSuccess
        // start() always opens unmuted; a user who muted must stay unheard during the reply.
        if (recorderLive) container.voiceRecorder.muted = voiceMicMuted.value
        bargeTrimBytes = 0L
        // Two barge-in gates, each measured as one unbroken run of speech:
        //   1. "outshouts reply": mic > player * margin + floor. Fast (BARGE_TRIGGER_MS).
        //      Catches normal voices on a quiet phone speaker.
        //   2. "soft": mic sustained above an absolute floor for longer. Catches a loud
        //      speaker the user cannot outshout after echo cancellation.
        val hardRun = SpeechRunTracker()
        val softRun = SpeechRunTracker()
        voiceJob = viewModelScope.launch {
            var bargeArmed = false
            var ducked = false
            while (true) {
                val phaseNow = voicePhase.value
                if (phaseNow != VoicePhase.SPEAKING && !(bargeArmed && phaseNow == VoicePhase.LISTENING)) break
                val now = SystemClock.elapsedRealtime()
                val playerLevel = container.voicePlayer.level()
                val micRaw = if (recorderLive) container.voiceRecorder.level() else 0
                val micLevel = (micRaw / 32_767f).coerceIn(0f, 1f)

                if (phaseNow == VoicePhase.SPEAKING) {
                    val micOutshoutsReply = recorderLive &&
                        micLevel > maxOf(playerLevel * BARGE_MIC_MARGIN + BARGE_MIC_FLOOR, BARGE_MIC_MIN)
                    val micSustainedSoft = recorderLive && micLevel >= BARGE_SOFT_MIN
                    val hardMs = hardRun.observe(micOutshoutsReply, now)
                    val softMs = softRun.observe(micSustainedSoft, now)
                    voiceLevel.value = maxOf(playerLevel, if (micSustainedSoft) sqrt(micLevel) else 0f)
                    // Duck the reply as soon as the user starts talking: they hear they were
                    // noticed and keep going, and the quieter speaker leaks less echo into the
                    // mic while the barge-in confirms. Re-applied every poll because each new
                    // sentence starts a fresh player at full volume.
                    val userTalking = hardMs >= BARGE_DUCK_MS || softMs >= BARGE_SOFT_DUCK_MS
                    if (userTalking || ducked) {
                        container.voicePlayer.setVolume(if (userTalking) BARGE_DUCK_VOLUME else 1f)
                        ducked = userTalking
                    }
                    if (!bargeArmed && (hardMs >= VoiceConfig.BARGE_TRIGGER_MS || softMs >= BARGE_SOFT_TRIGGER_MS)) {
                        bargeArmed = true
                        val onsetAt = softRun.onsetAtMs ?: hardRun.onsetAtMs ?: now
                        // Hand the session to listening before stopping anything: the reply's
                        // cleanup can run inside the cancel() below, and it must already see that
                        // the stop is intentional and the mic now belongs to the user.
                        intentionalVoiceCancel = true
                        voicePhase.value = VoicePhase.LISTENING
                        // The utterance started at the barge onset, so silence detection treats
                        // the barge and the user's continuation as one turn.
                        voiceStartedAt = onsetAt
                        voiceEndDetector.reset(onsetAt)
                        voiceEndDetector.seedSpeech(now, now - onsetAt)
                        fadePlayerOut(from = if (ducked) BARGE_DUCK_VOLUME else 1f)
                        container.voicePlayer.stop()
                        voicePipelineJob?.cancel()
                        voicePipelineJob = null
                        // The model may still be writing the reply the user just talked over.
                        abandonVoiceTurn()
                        // Keep recording. Reopening the mic here used to drop the words spoken
                        // while it restarted, which is why a lead-in "uhh" was needed. Mark where
                        // the user's speech began instead, with a pre-roll for a soft first sound.
                        val keepMs = SystemClock.elapsedRealtime() - onsetAt + BARGE_PREROLL_MS
                        bargeTrimBytes = (container.voiceRecorder.capturedBytes() - keepMs * VOICE_BYTES_PER_MS)
                            .coerceAtLeast(0L)
                    }
                } else {
                    val activity = voiceEndDetector.observe(micRaw, now)
                    voiceLevel.value = activity.level
                    if (activity.stopReason != null) {
                        stopVoiceRecording(automatic = true)
                        break
                    }
                    updateSpeculativeStt(activity, now)
                }
                delay(VOICE_POLL_MS)
            }
        }
    }

    /** Smoothly lowers the TTS volume from [from] to silence so the user's voice wins the channel. */
    private suspend fun fadePlayerOut(from: Float = 1f) {
        repeat(4) { step ->
            container.voicePlayer.setVolume(from * (1f - (step + 1) / 4f))
            delay(12)
        }
        container.voicePlayer.setVolume(0f)
    }

    /** Plays [clip] to completion, deleting it afterwards, and cancels cleanly with the caller. */
    private suspend fun playVoiceClipAwait(
        clip: File,
        onStarted: () -> Unit = {}
    ) = suspendCancellableCoroutine { continuation ->
        container.voicePlayer.play(clip, onStarted) { failure ->
            clip.delete()
            if (continuation.isActive) {
                if (failure == null) continuation.resume(Unit)
                else continuation.resumeWithException(failure)
            }
        }
        continuation.invokeOnCancellation {
            container.voicePlayer.stop()
            clip.delete()
        }
    }

    /** Plays one sentence's Fish PCM stream as it downloads, falling back to an MP3 render. */
    private suspend fun playFishStreamAwait(
        audio: ReplyAudio.Stream,
        settings: SettingsState,
        fishVoiceId: String?,
        onStarted: () -> Unit
    ) {
        var started = false
        try {
            container.voicePlayer.playPcm(
                chunks = audio.pcm.consumeAsFlow(),
                onStarted = {
                    started = true
                    onStarted()
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            // Only fall back when nothing was heard. Restarting mid-clip would repeat
            // the opening words; a stream that already played stays a hard failure.
            if (started || intentionalVoiceCancel) throw t
            val clip = container.newVoiceFile()
            try {
                container.synthesizeVoice(
                    text = audio.text,
                    destination = clip,
                    speed = settings.fishSpeed.toDouble(),
                    provider = TtsProvider.FISH,
                    fishModel = settings.fishModel,
                    fishVoiceId = fishVoiceId,
                    openRouterModel = settings.openRouterTtsModel,
                    openRouterVoice = settings.openRouterTtsVoice,
                    emotions = settings.voiceEmotions
                )
                playVoiceClipAwait(clip, onStarted)
            } catch (fallback: Throwable) {
                clip.delete()
                throw fallback
            }
        } finally {
            // Cancellation of the voice pipeline job must cut PCM playback immediately.
            if (intentionalVoiceCancel) container.voicePlayer.stop()
        }
    }

    /**
     * Speaks an already-sent assistant message through Fish Audio. The rendered MP3 is
     * cached per message (voice-cache/<messageId>.mp3), so replaying it costs no new TTS
     * call. Tapping again while the same message is synthesizing or playing stops it.
     */
    fun speakMessage(messageId: String) {
        if (speakingMessageId.value == messageId || synthesizingMessageId.value == messageId) {
            speakMessageJob?.cancel()
            speakMessageJob = null
            container.voicePlayer.stop()
            speakingMessageId.value = null
            synthesizingMessageId.value = null
            return
        }
        val message = messages.value.firstOrNull { it.id == messageId } ?: return
        // Only one message can sound at a time; starting one stops any other.
        speakMessageJob?.cancel()
        container.voicePlayer.stop()
        speakingMessageId.value = null
        synthesizingMessageId.value = null
        speakMessageJob = viewModelScope.launch {
            val clip = container.voiceCacheFile(messageId)
            var synthesisComplete = clip.isFile && clip.length() > 0L
            try {
                if (!synthesisComplete) {
                    val key = withContext(Dispatchers.IO) { container.credentials.fishKey() }
                    if (key.isNullOrBlank()) {
                        error.value = "Set up FishAudio in Voice settings to hear replies."
                        return@launch
                    }
                    val text = message.content.toSpeechText()
                    if (text.isBlank()) {
                        error.value = "There is nothing in this message that could be spoken."
                        return@launch
                    }
                    synthesizingMessageId.value = messageId
                    val settings = container.preferences.state.first()
                    container.synthesizeVoice(
                        text = text,
                        destination = clip,
                        speed = settings.fishSpeed.toDouble(),
                        provider = TtsProvider.FISH,
                        fishModel = settings.fishModel,
                        fishVoiceId = settings.fishVoiceId.ifBlank { null },
                        openRouterModel = settings.openRouterTtsModel,
                        openRouterVoice = settings.openRouterTtsVoice,
                        emotions = settings.voiceEmotions
                    )
                    synthesisComplete = true
                    synthesizingMessageId.value = null
                }
                speakingMessageId.value = messageId
                playCachedVoiceClipAwait(clip)
            } catch (cancelled: CancellationException) {
                // A stopped synthesis may have left a partial MP3 behind; finished clips stay cached.
                if (!synthesisComplete) clip.delete()
                throw cancelled
            } catch (t: Throwable) {
                if (!synthesisComplete) clip.delete()
                error.value = t.message ?: "The voice reply could not be generated."
            } finally {
                if (speakingMessageId.value == messageId) speakingMessageId.value = null
                if (synthesizingMessageId.value == messageId) synthesizingMessageId.value = null
            }
        }
    }

    /** Plays a cached clip to completion without deleting it; cancels cleanly with the caller. */
    private suspend fun playCachedVoiceClipAwait(clip: File) = suspendCancellableCoroutine { continuation ->
        container.voicePlayer.play(clip) { failure ->
            if (continuation.isActive) {
                if (failure == null) continuation.resume(Unit)
                else continuation.resumeWithException(failure)
            }
        }
        continuation.invokeOnCancellation { container.voicePlayer.stop() }
    }

    override fun onCleared() {
        voiceJob?.cancel()
        voicePipelineJob?.cancel()
        voicePipelineJob = null
        voiceFillerJob?.cancel()
        voiceFillerJob = null
        cancelSpeculativeStt()
        speakMessageJob?.cancel()
        speakMessageJob = null
        container.voiceRecorder.cancel()
        container.voicePlayer.stop()
        super.onCleared()
    }

    private companion object {
        const val VOICE_POLL_MS = 30L

        /** How long the "Remembering that…" chip stays visible after a memory is saved. */
        const val MEMORY_CHIP_MS = 2_800L

        /**
         * Barge-in fires when the microphone level is at least this fraction of the speaker level.
         * 0.7 was too aggressive in practice — with the device speaker at typical speech levels
         * (PCM 0.4-0.7) and Android's echo canceller suppressing speaker leakage, the user's
         * normal conversational voice never reached 1.0× of the player, so they had to talk very
         * loudly. 0.5f means "the user's voice is clearly present", which matches the cue we want.
         */
        const val BARGE_MIC_MARGIN = 0.5f

        /** Added to the scaled playback level so quiet replies still need a real voice. */
        const val BARGE_MIC_FLOOR = 0.04f

        /** Absolute mic floor (0..1) for the "must outshout reply" gate. */
        const val BARGE_MIC_MIN = 0.05f

        /**
         * Soft barge-in: when the speaker is loud and the user can't outshout it, accept voice
         * above [BARGE_SOFT_MIN] held for [BARGE_SOFT_TRIGGER_MS] as one unbroken run
         * ([SpeechRunTracker]). The old 700 ms was summed over the whole reply, so scattered
         * echo could add up while real speech still waited; a continuous run rejects the
         * scattered noise, which allows a shorter window. A TV syllable won't arm; a phrase will.
         */
        const val BARGE_SOFT_MIN = 0.04f
        const val BARGE_SOFT_TRIGGER_MS = 550L

        /** Run lengths (outshouting / soft) after which the reply ducks while a barge-in confirms. */
        const val BARGE_DUCK_MS = 90L
        const val BARGE_SOFT_DUCK_MS = 240L

        /** Reply volume while ducked under the user's voice. */
        const val BARGE_DUCK_VOLUME = 0.35f

        /** Audio kept from before the detected start of a barge-in, so a soft first sound survives. */
        const val BARGE_PREROLL_MS = 500L
    }

    fun setMemoryEnabled(value: Boolean) = viewModelScope.launch { container.preferences.setMemoryEnabled(value) }
    fun setAgentSwarmEnabled(value: Boolean) = viewModelScope.launch { container.preferences.setAgentSwarmEnabled(value) }

    private fun startGeneration(block: suspend () -> Unit) {
        error.value = null
        generation = viewModelScope.launch {
            generating.value = true
            try { block() }
            catch (_: CancellationException) { }
            catch (t: Throwable) { error.value = t.message ?: "Generation failed." }
            finally { generating.value = false }
        }
    }
}

class ModelsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.preferences.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())
    val models = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val imageModels = MutableStateFlow<List<MediaModel>>(emptyList())
    val videoModels = MutableStateFlow<List<MediaModel>>(emptyList())
    val audioModels = MutableStateFlow<List<MediaModel>>(emptyList())
    val purpose = MutableStateFlow(ModelPurpose.CHAT)
    val query = MutableStateFlow("")
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val mediaError = MutableStateFlow<String?>(null)

    private var refreshJob: Job? = null
    private var refreshGeneration = 0

    init {
        viewModelScope.launch {
            container.preferences.state
                .map { it.chatProvider }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    fun refresh() {
        val currentGeneration = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            mediaError.value = null
            models.value = emptyList()
            imageModels.value = emptyList()
            videoModels.value = emptyList()
            audioModels.value = emptyList()
            val provider = container.preferences.state.first().chatProvider
            try {
                supervisorScope {
                    val chat = async { runCatching { container.textProvider(provider).models(container.textProviderKey(provider)) } }
                    val image = async { runCatching {
                        if (provider == ChatProvider.MINIMAX) container.minimax.imageModels() else container.openRouter.imageModels(container.credentials.openRouterKey())
                    } }
                    val video = async { runCatching {
                        if (provider == ChatProvider.MINIMAX) container.minimax.videoModels() else container.openRouter.videoModels(container.credentials.openRouterKey())
                    } }
                    val audio = async { runCatching {
                        if (provider == ChatProvider.MINIMAX) container.minimax.audioModels() else container.openRouter.audioModels(container.credentials.openRouterKey())
                    } }

                    val chatResult = chat.await()
                    currentCoroutineContext().ensureActive()
                    chatResult.getOrNull()?.let { available ->
                        models.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            text = available.map { it.id }.toSet(),
                            // v5.10: tool-aware fallback for the agent, research and Max slots.
                            toolCapable = AgentModelChoice.toolCapableIds(available)
                        )
                    } ?: chatResult.exceptionOrNull()?.let { failure ->
                        error.value = failure.message ?: "Could not load chat models."
                    }

                    val imageResult = image.await()
                    currentCoroutineContext().ensureActive()
                    imageResult.getOrNull()?.let { available ->
                        imageModels.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            image = available.map { it.id }.toSet()
                        )
                    } ?: imageResult.exceptionOrNull()?.let { failure ->
                        mediaError.value = failure.message ?: "Could not load image models."
                    }

                    val videoResult = video.await()
                    currentCoroutineContext().ensureActive()
                    videoResult.getOrNull()?.let { available ->
                        videoModels.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            video = available.map { it.id }.toSet()
                        )
                    } ?: videoResult.exceptionOrNull()?.let { failure ->
                        mediaError.value = failure.message ?: "Could not load video models."
                    }

                    val audioResult = audio.await()
                    currentCoroutineContext().ensureActive()
                    audioResult.getOrNull()?.let { available ->
                        audioModels.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            audio = available.map { it.id }.toSet()
                        )
                    } ?: audioResult.exceptionOrNull()?.let { failure ->
                        mediaError.value = failure.message ?: "Could not load audio models."
                    }
                }
            } finally {
                if (currentGeneration == refreshGeneration) loading.value = false
            }
        }
    }
    fun select(id: String) = viewModelScope.launch { container.preferences.setModel(id, purpose.value) }
    fun favorite(id: String) = viewModelScope.launch { container.preferences.toggleFavorite(id) }
    fun setReasoning(id: String, effort: ReasoningEffort) = viewModelScope.launch { container.preferences.setReasoning(id, effort) }
}

class LlmCatalogViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.preferences.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())
    val models = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val benchmarks = MutableStateFlow<Map<String, ModelBenchmark>>(emptyMap())
    val benchmarkMeta = MutableStateFlow<BenchmarkMeta?>(null)
    val query = MutableStateFlow("")
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val benchmarkNotice = MutableStateFlow<String?>(null)

    private var refreshJob: Job? = null
    private var refreshGeneration = 0

    init {
        viewModelScope.launch {
            container.preferences.state
                .map { it.chatProvider }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    fun refresh() {
        val currentGeneration = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            benchmarkNotice.value = null
            models.value = emptyList()
            benchmarks.value = emptyMap()
            benchmarkMeta.value = null
            val provider = container.preferences.state.first().chatProvider
            val key = container.textProviderKey(provider)
            try {
                supervisorScope {
                    val catalog = async { runCatching { container.textProvider(provider).models(key) } }
                    val catalogResult = catalog.await()
                    currentCoroutineContext().ensureActive()
                    catalogResult.getOrNull()?.let { available ->
                        models.value = available
                        container.preferences.reconcileAvailableModels(
                            provider = provider,
                            text = available.map { it.id }.toSet(),
                            // v5.10: tool-aware fallback for the agent, research and Max slots.
                            toolCapable = AgentModelChoice.toolCapableIds(available)
                        )
                    } ?: catalogResult.exceptionOrNull()?.let { failure ->
                        error.value = failure.message ?: "Could not load the ${provider.label} model catalog."
                    }
                    if (provider == ChatProvider.OPENROUTER) {
                        val scores = async { runCatching { container.openRouter.benchmarks(key) } }
                        val scoreResult = scores.await()
                        currentCoroutineContext().ensureActive()
                        scoreResult.getOrNull()?.let { envelope ->
                            benchmarks.value = envelope.data.associateBy { it.modelPermaslug }
                            benchmarkMeta.value = envelope.meta
                        } ?: scoreResult.exceptionOrNull()?.let {
                            benchmarkNotice.value = "Intelligence scores are temporarily unavailable; the rest of the catalog is still current."
                        }
                    }
                }
            } finally {
                if (currentGeneration == refreshGeneration) loading.value = false
            }
        }
    }
}

class SkillsToolsViewModel(private val container: AppContainer) : ViewModel() {
    val skills = container.skills.skills.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { container.skills.setEnabled(id, enabled) }
    fun delete(id: String) = viewModelScope.launch { container.skills.delete(id) }
    fun createConversation(onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(container.conversations.createConversation())
    }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.preferences.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())
    val openRouterKey = MutableStateFlow("")
    val parallelKey = MutableStateFlow("")
    val minimaxKey = MutableStateFlow("")
    val openRouterCheck = MutableStateFlow<CheckState>(CheckState.Idle)
    val parallelCheck = MutableStateFlow<CheckState>(CheckState.Idle)
    val minimaxCheck = MutableStateFlow<CheckState>(CheckState.Idle)
    val notice = MutableStateFlow<String?>(null)

    // Voice chat
    val fishKey = MutableStateFlow("")
    val fishCheck = MutableStateFlow<CheckState>(CheckState.Idle)
    val voiceTest = MutableStateFlow<CheckState>(CheckState.Idle)

    fun hasFish() = container.credentials.hasFishKey()

    fun saveAndTestFish() = viewModelScope.launch {
        val key = fishKey.value.trim()
        if (key.isEmpty()) { fishCheck.value = CheckState.Error("Enter a Fish Audio key first."); return@launch }
        fishCheck.value = CheckState.Checking
        val voiceId = settings.value.fishVoiceId.ifBlank { null }
        container.fish.testKey(key, voiceId, settings.value.fishModel).fold(
            onSuccess = {
                container.credentials.setFishKey(key)
                fishKey.value = ""
                fishCheck.value = CheckState.Success("Fish Audio key verified and stored securely.")
            },
            onFailure = { fishCheck.value = CheckState.Error(it.message ?: "Key test failed.") }
        )
    }

    fun setFishModel(value: String) = viewModelScope.launch { container.preferences.setFishModel(value) }
    fun setVoiceReplyModel(value: String) = viewModelScope.launch { container.preferences.setVoiceReplyModel(value) }
    fun setVoiceSttModel(value: String) = viewModelScope.launch { container.preferences.setVoiceSttModel(value) }
    fun setFishVoiceId(value: String) = viewModelScope.launch { container.preferences.setFishVoiceId(value) }
    fun saveFishCustomVoice(name: String, referenceId: String) = viewModelScope.launch { container.preferences.saveFishCustomVoice(name, referenceId) }
    fun deleteFishCustomVoice(referenceId: String) = viewModelScope.launch { container.preferences.deleteFishCustomVoice(referenceId) }
    fun setFishSpeed(value: Float) = viewModelScope.launch { container.preferences.setFishSpeed(value) }
    fun setVoiceEmotions(value: Boolean) = viewModelScope.launch { container.preferences.setVoiceEmotions(value) }
    fun setVoiceFishStreaming(value: Boolean) = viewModelScope.launch { container.preferences.setVoiceFishStreaming(value) }

    fun testVoice() = viewModelScope.launch {
        val current = settings.value
        val provider = TtsProvider.from(current.ttsProvider)
        when (provider) {
            TtsProvider.FISH -> {
                val key = container.credentials.fishKey()
                val voiceId = current.fishVoiceId.ifBlank { null }
                if (key.isNullOrBlank()) { voiceTest.value = CheckState.Error("Save a Fish Audio key first."); return@launch }
                if (voiceId.isNullOrBlank()) { voiceTest.value = CheckState.Error("Pick a voice first."); return@launch }
                voiceTest.value = CheckState.Checking
                val clip = container.newVoiceFile()
                runCatching {
                    container.synthesizeVoice(
                        text = "Hello! This is Kryzz speaking.",
                        destination = clip,
                        speed = current.fishSpeed.toDouble(),
                        provider = provider,
                        fishModel = current.fishModel,
                        fishVoiceId = voiceId,
                        openRouterModel = current.openRouterTtsModel,
                        openRouterVoice = current.openRouterTtsVoice,
                        emotions = current.voiceEmotions
                    )
                }.onSuccess {
                    container.voicePlayer.play(
                        clip,
                        onStarted = { voiceTest.value = CheckState.Success("Preview playing…") }
                    ) { failure ->
                        clip.delete()
                        if (failure != null) voiceTest.value = CheckState.Error(failure.message ?: "Preview failed.")
                    }
                }.onFailure {
                    clip.delete()
                    voiceTest.value = CheckState.Error(it.message ?: "Preview failed.")
                }
            }
            TtsProvider.OPENROUTER -> {
                val key = container.credentials.openRouterKey()
                if (key.isNullOrBlank()) { voiceTest.value = CheckState.Error("Save an OpenRouter API key first."); return@launch }
                voiceTest.value = CheckState.Checking
                val clip = container.newVoiceFile()
                runCatching {
                    container.synthesizeVoice(
                        text = "Hello! This is Kryzz speaking.",
                        destination = clip,
                        speed = current.fishSpeed.toDouble(),
                        provider = provider,
                        fishModel = current.fishModel,
                        fishVoiceId = current.fishVoiceId.ifBlank { null },
                        openRouterModel = current.openRouterTtsModel,
                        openRouterVoice = current.openRouterTtsVoice,
                        emotions = current.voiceEmotions
                    )
                }.onSuccess {
                    container.voicePlayer.play(
                        clip,
                        onStarted = { voiceTest.value = CheckState.Success("Preview playing…") }
                    ) { failure ->
                        clip.delete()
                        if (failure != null) voiceTest.value = CheckState.Error(failure.message ?: "Preview failed.")
                    }
                }.onFailure {
                    clip.delete()
                    voiceTest.value = CheckState.Error(it.message ?: "Preview failed.")
                }
            }
        }
    }

    fun setTtsProvider(value: String) = viewModelScope.launch { container.preferences.setTtsProvider(value) }
    fun setOpenRouterTtsModel(value: String) = viewModelScope.launch { container.preferences.setOpenRouterTtsModel(value) }
    fun setOpenRouterTtsVoice(value: String) = viewModelScope.launch { container.preferences.setOpenRouterTtsVoice(value) }

    /**
     * Synthesises and plays one short expression ("Mmm", "Oh", …) with the currently
     * selected voice so the user can audition how it sounds beyond a plain hello. The
     * [voiceTest] state is reused for the inline status chip. Errors are surfaced
     * rather than swallowed so a missing key / voice is obvious.
     */
    fun playExpression(line: String) = viewModelScope.launch {
        val current = settings.value
        val provider = TtsProvider.from(current.ttsProvider)
        when (provider) {
            TtsProvider.FISH -> {
                val key = container.credentials.fishKey()
                val voiceId = current.fishVoiceId.ifBlank { null }
                if (key.isNullOrBlank()) { voiceTest.value = CheckState.Error("Save a Fish Audio key first."); return@launch }
                if (voiceId.isNullOrBlank()) { voiceTest.value = CheckState.Error("Pick a voice first."); return@launch }
                voiceTest.value = CheckState.Checking
                val clip = container.newVoiceFile()
                runCatching {
                    container.synthesizeVoice(
                        text = line,
                        destination = clip,
                        speed = current.fishSpeed.toDouble(),
                        provider = provider,
                        fishModel = current.fishModel,
                        fishVoiceId = voiceId,
                        openRouterModel = current.openRouterTtsModel,
                        openRouterVoice = current.openRouterTtsVoice,
                        emotions = current.voiceEmotions
                    )
                }.onSuccess {
                    container.voicePlayer.play(
                        clip,
                        onStarted = { voiceTest.value = CheckState.Success("Playing \"$line\"…") }
                    ) { failure ->
                        clip.delete()
                        if (failure != null) voiceTest.value = CheckState.Error(failure.message ?: "Preview failed.")
                    }
                }.onFailure {
                    clip.delete()
                    voiceTest.value = CheckState.Error(it.message ?: "Preview failed.")
                }
            }
            TtsProvider.OPENROUTER -> {
                val key = container.credentials.openRouterKey()
                if (key.isNullOrBlank()) { voiceTest.value = CheckState.Error("Save an OpenRouter API key first."); return@launch }
                voiceTest.value = CheckState.Checking
                val clip = container.newVoiceFile()
                runCatching {
                    container.synthesizeVoice(
                        text = line,
                        destination = clip,
                        speed = current.fishSpeed.toDouble(),
                        provider = provider,
                        fishModel = current.fishModel,
                        fishVoiceId = current.fishVoiceId.ifBlank { null },
                        openRouterModel = current.openRouterTtsModel,
                        openRouterVoice = current.openRouterTtsVoice,
                        emotions = current.voiceEmotions
                    )
                }.onSuccess {
                    container.voicePlayer.play(
                        clip,
                        onStarted = { voiceTest.value = CheckState.Success("Playing \"$line\"…") }
                    ) { failure ->
                        clip.delete()
                        if (failure != null) voiceTest.value = CheckState.Error(failure.message ?: "Preview failed.")
                    }
                }.onFailure {
                    clip.delete()
                    voiceTest.value = CheckState.Error(it.message ?: "Preview failed.")
                }
            }
        }
    }

    fun hasOpenRouter() = container.credentials.hasOpenRouterKey()
    fun hasParallel() = container.credentials.hasParallelKey()
    fun hasMinimax() = container.credentials.hasMinimaxKey()
    fun setChatProvider(value: ChatProvider) = viewModelScope.launch { container.preferences.setChatProvider(value) }
    fun saveAndTestOpenRouter() = viewModelScope.launch {
        val key = openRouterKey.value.trim()
        if (key.isEmpty()) { openRouterCheck.value = CheckState.Error("Enter a key first."); return@launch }
        openRouterCheck.value = CheckState.Checking
        container.openRouter.testKey(key).fold(
            { container.credentials.setOpenRouterKey(key); openRouterKey.value = ""; openRouterCheck.value = CheckState.Success("Verified and saved.") },
            { openRouterCheck.value = CheckState.Error(it.message ?: "Key test failed.") }
        )
    }
    fun saveAndTestParallel() = viewModelScope.launch {
        val key = parallelKey.value.trim()
        if (key.isEmpty()) { parallelCheck.value = CheckState.Error("Enter a key first."); return@launch }
        parallelCheck.value = CheckState.Checking
        container.parallel.testKey(key).fold(
            { container.credentials.setParallelKey(key); parallelKey.value = ""; parallelCheck.value = CheckState.Success("Verified and saved.") },
            { parallelCheck.value = CheckState.Error(it.message ?: "Key test failed.") }
        )
    }
    fun saveAndTestMiniMax() = viewModelScope.launch {
        val key = minimaxKey.value.trim()
        if (key.isEmpty()) { minimaxCheck.value = CheckState.Error("Enter a key first."); return@launch }
        minimaxCheck.value = CheckState.Checking
        container.minimax.testKey(key).fold(
            { container.credentials.setMinimaxKey(key); minimaxKey.value = ""; minimaxCheck.value = CheckState.Success("Verified and saved.") },
            { minimaxCheck.value = CheckState.Error(it.message ?: "Key test failed.") }
        )
    }
    fun setPersona(id: String, custom: String) = viewModelScope.launch { container.preferences.setPersona(id, custom) }
    fun setSearch(value: Boolean) = viewModelScope.launch { container.preferences.setSearchEnabled(value) }
    fun setSearchChars(value: Int) = viewModelScope.launch { container.preferences.setMaxSearchChars(value) }
    fun setStepBudget(value: Int) = viewModelScope.launch { container.preferences.setStepBudget(value) }
    fun setCostCapCents(value: Int) = viewModelScope.launch { container.preferences.setCostCapCents(value) }
    fun setReviewAnswers(value: Boolean) = viewModelScope.launch { container.preferences.setReviewAnswers(value) }
    fun setAgentQuality(value: AgentQuality) = viewModelScope.launch { container.preferences.setAgentQuality(value) }
    fun setTheme(value: ThemeMode) = viewModelScope.launch { container.preferences.setTheme(value) }
    fun setBackgroundStyle(value: BackgroundStyle) = viewModelScope.launch { container.preferences.setBackgroundStyle(value) }
    fun setColouredGradient(value: GradientPalette) = viewModelScope.launch { container.preferences.setColouredGradient(value) }
    fun setAppPalette(value: AppPalette) = viewModelScope.launch { container.preferences.setAppPalette(value) }
    fun setTextPalette(value: TextPalette) = viewModelScope.launch { container.preferences.setTextPalette(value) }
    fun setFontStyle(value: FontStyle) = viewModelScope.launch { container.preferences.setFontStyle(value) }
    fun setFontScale(value: Float) = viewModelScope.launch { container.preferences.setFontScale(value) }
    fun setChatDensity(value: ChatDensity) = viewModelScope.launch { container.preferences.setChatDensity(value) }
    fun setAnimationsEnabled(value: Boolean) = viewModelScope.launch { container.preferences.setAnimationsEnabled(value) }
    fun setSurfaceOpacity(value: Float) = viewModelScope.launch { container.preferences.setSurfaceOpacity(value) }
    fun setBackgroundBlur(value: Float) = viewModelScope.launch { container.preferences.setBackgroundBlur(value) }
    fun setBiometric(value: Boolean) = viewModelScope.launch { container.preferences.setBiometricLock(value) }
    fun setLocationEnabled(value: Boolean) = viewModelScope.launch {
        container.preferences.setLocationEnabled(value)
        if (value) runCatching { container.location.refresh() }
    }
    fun hasLocationPermission() = container.location.hasPermission()
    fun setMemoryEnabled(value: Boolean) = viewModelScope.launch { container.preferences.setMemoryEnabled(value) }
    fun clearAll() = viewModelScope.launch { container.clearAllLocalData(); notice.value = "All local conversations, settings, and credentials were cleared." }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MemoryViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.preferences.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())
    val query = MutableStateFlow("")
    val memories: StateFlow<List<MemoryEntity>> = query
        .flatMapLatest { container.memories.observe(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notice = MutableStateFlow<String?>(null)

    fun setQuery(value: String) { query.value = value }

    fun setMemoryEnabled(value: Boolean) = viewModelScope.launch { container.preferences.setMemoryEnabled(value) }

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        container.memories.setEnabled(id, enabled)
        notice.value = if (enabled) "Memory enabled." else "Memory paused."
    }

    fun togglePinned(id: String, pinned: Boolean) = viewModelScope.launch {
        container.memories.setPinned(id, pinned)
    }

    fun delete(id: String) = viewModelScope.launch { container.memories.delete(id) }

    fun add(
        content: String,
        category: MemoryEngine.Category,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val saved = container.memories.add(content, category)
            notice.value = if (saved) "Memory saved." else "Not saved — duplicate or too short."
            onResult(saved)
        }
    }

    fun clearAll() = viewModelScope.launch {
        container.memories.clearAll()
        notice.value = "All memories were cleared."
    }
}

class CronViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.dao()
    val tasks: StateFlow<List<ScheduledTaskEntity>> = dao.observeScheduledTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notice = MutableStateFlow<String?>(null)

    fun createTask(
        title: String,
        prompt: String,
        recurrence: CronRecurrence,
        hourOfDay: Int,
        minuteOfHour: Int,
        dayOfWeek: Int? = null,
        dayOfMonth: Int? = null,
        daysOfWeek: Collection<Int>? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val cleanTitle = title.trim().take(80)
            val cleanPrompt = prompt.trim()
            if (cleanTitle.isEmpty() || cleanPrompt.isEmpty()) {
                notice.value = "Enter a name and a prompt."
                onResult(false)
                return@launch
            }
            // Every task talks to the AI in its own conversation so runs stay out of
            // the user's regular chats.
            val conversationId = container.conversations.createConversation()
            container.conversations.rename(conversationId, "⏰ $cleanTitle")
            val encodedDays = if (recurrence == CronRecurrence.WEEKLY) {
                CronSchedulePresets.encodeDays(daysOfWeek ?: listOfNotNull(dayOfWeek))
            } else {
                null
            }
            val task = ScheduledTaskEntity(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                prompt = cleanPrompt,
                // Kept for legacy compatibility; wall-clock tasks ignore it.
                intervalMinutes = 1_440L,
                conversationId = conversationId,
                createdAt = System.currentTimeMillis(),
                recurrence = recurrence.name,
                hourOfDay = hourOfDay.coerceIn(0, 23),
                minuteOfHour = minuteOfHour.coerceIn(0, 59),
                dayOfWeek = encodedDays?.split(',')?.firstOrNull()?.toIntOrNull() ?: dayOfWeek?.coerceIn(1, 7),
                dayOfMonth = if (recurrence == CronRecurrence.MONTHLY) dayOfMonth?.coerceIn(1, 31) else null,
                daysOfWeek = encodedDays
            )
            dao.upsertScheduledTask(task)
            container.cron.schedule(task)
            notice.value = "Cron task scheduled."
            onResult(true)
        }
    }

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        dao.setScheduledTaskEnabled(id, enabled)
        val task = dao.scheduledTask(id)
        if (enabled && task != null) container.cron.schedule(task) else container.cron.cancel(id)
        notice.value = if (enabled) "Cron task enabled." else "Cron task paused."
    }

    fun runNow(id: String) {
        container.cron.runNow(id)
        notice.value = "Cron task queued to run now."
    }

    fun delete(id: String) = viewModelScope.launch {
        container.cron.cancel(id)
        val task = dao.scheduledTask(id)
        dao.deleteScheduledTask(id)
        task?.let { container.conversations.delete(it.conversationId) }
        notice.value = "Cron task deleted."
    }
}

class DaylightViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        RootViewModel::class.java -> RootViewModel(container)
        OnboardingViewModel::class.java -> OnboardingViewModel(container)
        ConversationListViewModel::class.java -> ConversationListViewModel(container)
        ModelsViewModel::class.java -> ModelsViewModel(container)
        LlmCatalogViewModel::class.java -> LlmCatalogViewModel(container)
        SkillsToolsViewModel::class.java -> SkillsToolsViewModel(container)
        SettingsViewModel::class.java -> SettingsViewModel(container)
        MemoryViewModel::class.java -> MemoryViewModel(container)
        CronViewModel::class.java -> CronViewModel(container)
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    } as T
}
