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
import ai.daylight.assistant.voice.TtsProvider
import ai.daylight.assistant.voice.AdaptiveEndOfSpeechDetector
import ai.daylight.assistant.voice.VoiceConfig
import ai.daylight.assistant.voice.VoicePhase
import ai.daylight.assistant.voice.patchWavHeader
import ai.daylight.assistant.voice.toSpeechText
import ai.daylight.assistant.voice.toVoiceSegments
import java.io.File
import java.io.FileOutputStream
import android.os.SystemClock
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
            // Calls of the same tool can now run together (two searches in one round) and
            // share one chip, so it is removed only when the last of them finishes.
            val running = mutableMapOf<String, Int>()
            container.agent.toolActivity.collect { activity ->
                if (activity.conversationId != conversationId) return@collect
                val label = toolDisplayLabel(activity.toolName)
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
                val count = (running[label] ?: 0) + if (activity.started) 1 else -1
                if (count > 0) running[label] = count else running.remove(label)
                val current = activities.value
                activities.value = when {
                    count <= 0 -> current - label
                    label in current -> current
                    else -> current + label
                }
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
        startGeneration {
            container.conversations.deleteMessage(last.id)
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
    /** Set before deliberately cancelling the reply pipeline so its tail never reports a failure. */
    private var intentionalVoiceCancel = false
    private val voiceEndDetector = AdaptiveEndOfSpeechDetector()
    private var voiceStartedAt = 0L
    /**
     * Detectors for the two barge-in gates. Promoted to fields so the LISTENING branch after
     * a barge can keep using the same instance (already seeded), and so the soft-barge detector
     * survives between barge-in attempts.
     */
    private val bargeDetector = AdaptiveEndOfSpeechDetector(allowImmediateSpeechDuringCalibration = false)
    private val softBargeDetector = AdaptiveEndOfSpeechDetector(allowImmediateSpeechDuringCalibration = false)
    /** File captured during the SPEAKING monitor phase that holds the user's barge-in words. */
    private var pendingBargePrefix: java.io.File? = null
    /** Length in bytes of [pendingBargePrefix]; the WAV header is patched in [stopVoiceRecording]. */
    private var pendingBargePrefixLength: Long = 0L

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
        voiceStartedAt = SystemClock.elapsedRealtime()
        voiceEndDetector.reset(voiceStartedAt)
        container.warmOpenRouter()
        container.voiceRecorder.start().onFailure {
            error.value = it.message ?: "Could not open the microphone."
            return
        }
        voicePhase.value = VoicePhase.LISTENING
        if (!preserveMute) voiceMicMuted.value = false
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
            // Interrupting the reply also discards the barge-in mic session; the
            // session then carries on listening from scratch.
            VoicePhase.SPEAKING -> {
                voiceJob?.cancel()
                voiceJob = null
                intentionalVoiceCancel = true
                voicePipelineJob?.cancel()
                voicePipelineJob = null
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
        val rawFile = container.voiceRecorder.stop()
        // After a barge-in, the monitor captured the user's cut-off speech in a separate WAV.
        // Concatenate it with whatever was recorded after the barge so STT hears the whole
        // utterance, then patch the WAV header to reflect the combined size.
        val file = mergeBargePrefix(rawFile)
        val duration = SystemClock.elapsedRealtime() - voiceStartedAt
        val noConfirmedSpeech = !voiceEndDetector.hasUsableSpeech
        // Automatic submission requires confirmed speech. An explicit orb tap remains the
        // escape hatch for very soft/brief speech that peak-amplitude detection may miss.
        val unusable = file == null || file.length() < 2_000 || duration < 400L ||
            (automatic && noConfirmedSpeech && (duration < 900L || file.length() < 6_000))
        if (unusable) {
            file?.delete()
            // Nothing usable was captured: stay in voice mode and keep listening.
            rearmVoice()
            return
        }
voiceLevel.value = 0f
        voicePhase.value = VoicePhase.PROCESSING
        processVoiceClip(file)
    }

    /**
     * Joins the barge-in prefix (the WAV captured while Kryzz was still speaking) with the
     * WAV captured after the barge fired, returning a single combined file ready for upload.
     * Returns [newClip] unchanged when there is no prefix.
     */
    private fun mergeBargePrefix(newClip: File?): File? {
        val prefix = pendingBargePrefix
        pendingBargePrefix = null
        pendingBargePrefixLength = 0L
        if (prefix == null || !prefix.isFile || prefix.length() <= 44) {
            prefix?.delete()
            return newClip
        }
        if (newClip == null || !newClip.isFile || newClip.length() <= 44) {
            // No continuation was captured: the prefix alone is the user's barge-in words.
            runCatching { patchWavHeader(prefix) }
            return prefix
        }
        // Concatenate the prefix's PCM body (skip its 44-byte header) with the new clip's PCM body.
        val combined = try {
            val out = File(prefix.parentFile, prefix.nameWithoutExtension + "-barged.wav")
            val prefixIn = prefix.inputStream()
            val newIn = newClip.inputStream()
            val fos = FileOutputStream(out, false)
            try {
                val head = ByteArray(44)
                val headRead = prefixIn.read(head)
                check(headRead == 44) { "Barge prefix WAV header is incomplete." }
                fos.write(head) // placeholder header — patchWavHeader overwrites it with the true size
                prefixIn.copyTo(fos) // append the prefix body
                val skip = ByteArray(44)
                val tailHeaderRead = newIn.read(skip)
                check(tailHeaderRead == 44) { "Continuation WAV header is incomplete." }
                newIn.copyTo(fos) // append the new clip body
            } finally {
                runCatching { prefixIn.close() }
                runCatching { newIn.close() }
                runCatching { fos.close() }
            }
            runCatching { patchWavHeader(out) }
            prefix.delete()
            newClip.delete()
            out
        } catch (t: Throwable) {
            // Fall back to the new clip alone — STT will at least hear the post-barge continuation.
            prefix.delete()
            runCatching { patchWavHeader(newClip) }
            newClip
        }
        return combined
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
        // Drop any barge prefix we were holding — the user is closing the session.
        pendingBargePrefix?.delete()
        pendingBargePrefix = null
        pendingBargePrefixLength = 0L
        voiceMicMuted.value = false
        when (voicePhase.value) {
            VoicePhase.LISTENING -> container.voiceRecorder.cancel()
            VoicePhase.PROCESSING -> {
                generation?.cancel()
                generation = null
                generating.value = false
            }
            VoicePhase.SPEAKING -> {
                container.voicePlayer.stop()
                container.voiceRecorder.cancel()
            }
            VoicePhase.IDLE -> {}
        }
        voicePhase.value = VoicePhase.IDLE
        voiceLevel.value = 0f
        voiceSearching.value = false
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
        // Drop any barge prefix we were holding — the previous turn is closed.
        pendingBargePrefix?.delete()
        pendingBargePrefix = null
        pendingBargePrefixLength = 0L
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

    private fun processVoiceClip(file: File) {
        generation = viewModelScope.launch {
            generating.value = true
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
                val text = container.transcribeVoice(file, settings.voiceSttModel).trim()
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
                // While the agent runs (and may invoke parallel_search), play quick filler
                // phrases ("Checking online, give me a sec") so the user isn't sitting in
                // silence during the search round-trip. The job is cancelled once the real
                // reply is ready so speakAnswer starts cleanly.
                val fillerJob = startVoiceFillerListener(settings, provider)
                val answer = try {
                    container.agent.send(
                        conversationId,
                        text,
                        AssistantMode.CHAT,
                        AgentCapability.AUTO,
                        voiceMode = true
                    )
                } finally {
                    voiceSearching.value = false
                    fillerJob.cancel()
                } ?: run {
                    runCatching { container.voicePlayer.stop() }
                    voicePhase.value = VoicePhase.IDLE
                    error.value = "The model returned no usable reply. Try again."
                    return@launch
                }
                title.value = container.conversations.conversation(conversationId)?.title ?: title.value
                speakAnswer(
                    answer,
                    VoicePlaybackPlan(settings, provider, playbackReady.await())
                )
            } catch (cancelled: CancellationException) {
                file.delete()
                runCatching { container.voicePlayer.stop() }
                voicePhase.value = VoicePhase.IDLE
            } catch (t: Throwable) {
                file.delete()
                runCatching { container.voicePlayer.stop() }
                voicePhase.value = VoicePhase.IDLE
                error.value = t.message ?: "Voice chat failed."
            } finally {
                generating.value = false
            }
        }
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
            // Play and wait for completion (or cancellation) so two fillers don't overlap.
            suspendCancellableCoroutine<Unit> { cont ->
                container.voicePlayer.play(clip, {}, { failure ->
                    runCatching { clip.delete() }
                    if (cont.isActive) {
                        if (failure == null) cont.resume(Unit)
                        else cont.resumeWithException(failure)
                    }
                })
                cont.invokeOnCancellation { runCatching { clip.delete() } }
            }
        }
    }

    /**
     * Speaks the reply with a pipelined TTS pipeline: the first sentence is synthesised
     * alone so playback starts after a single sentence, while the remaining segments are
     * synthesised in the background and queued after it. Fish runs in its low-latency mode.
     *
     * While the reply plays, the microphone stays open: sustained speech from the user
     * fades the TTS out and hands the session back to listening (barge-in), so the user
     * can talk over Kryzz and the next thing they say is sent as a new prompt.
     */
    private fun speakAnswer(answer: String, plan: VoicePlaybackPlan) {
        voicePipelineJob = viewModelScope.launch {
            intentionalVoiceCancel = false
            val settings = plan.settings
            val provider = plan.provider
            if (!plan.credentialed) {
                error.value = when (plan.provider) {
                    TtsProvider.FISH -> "The reply is in the chat. Add a Fish Audio key in Settings → Voice chat and pick a voice to hear responses."
                    TtsProvider.OPENROUTER -> "The reply is in the chat. Add an OpenRouter API key in Settings → API keys to hear voice replies."
                }
                container.voiceRecorder.stop()?.delete()
                voicePhase.value = VoicePhase.IDLE
                return@launch
            }
            val fishVoiceId = settings.fishVoiceId.ifBlank { null }
            val spoken = answer.toSpeechText()
            if (spoken.isBlank()) {
                voicePhase.value = VoicePhase.IDLE
                error.value = "The voice reply did not contain anything that could be spoken."
                return@launch
            }
            // Fish PCM streaming is optional: on some devices/tiers the HTTP PCM
            // body ends after the first ~100 ms. Default is a single full MP3.
            val useFishStream = provider == TtsProvider.FISH && settings.voiceFishStreaming
            val remaining = when {
                useFishStream -> emptyList()
                provider == TtsProvider.FISH -> listOf(spoken)
                else -> spoken.toVoiceSegments()
            }
            val channel = Channel<File>(capacity = remaining.size.coerceAtLeast(1))
            var firstSegmentError: Throwable? = null
            val producer = launch {
                try {
                    val synthSemaphore = Semaphore(2)
                    val pending = remaining.mapIndexed { index, segment ->
                        async {
                            synthSemaphore.withPermit {
                                val clip = container.newVoiceFile()
                                val ok = try {
                                    container.synthesizeVoice(
                                        text = segment,
                                        destination = clip,
                                        speed = settings.fishSpeed.toDouble(),
                                        provider = provider,
                                        fishModel = settings.fishModel,
                                        fishVoiceId = fishVoiceId,
                                        openRouterModel = settings.openRouterTtsModel,
                                        openRouterVoice = settings.openRouterTtsVoice,
                                        emotions = settings.voiceEmotions
                                    )
                                    true
                                } catch (cancelled: CancellationException) {
                                    clip.delete()
                                    throw cancelled
                                } catch (t: Throwable) {
                                    clip.delete()
                                    if (index == 0 && provider != TtsProvider.FISH) firstSegmentError = t
                                    false
                                }
                                if (ok) clip else null
                            }
                        }
                    }
                    for ((index, result) in pending.withIndex()) {
                        val clip = result.await()
                        if (clip != null) {
                            try {
                                channel.send(clip)
                            } catch (cancelled: CancellationException) {
                                clip.delete()
                                throw cancelled
                            }
                        } else if (index == 0 && provider != TtsProvider.FISH) {
                            break
                        }
                    }
                } catch (_: CancellationException) {
                } finally {
                    channel.close()
                }
            }
            var playedAny = false
            var playbackStarted = false
            try {
                if (useFishStream) {
                    voicePhase.value = VoicePhase.SPEAKING
                    try {
                        // Arm the barge-in mic before AudioTrack starts. Starting
                        // MediaRecorder in onStarted() was interrupting the PCM track
                        // after the first ~100 ms on this device.
                        startVoicePlaybackMonitor()
                        playFishStreamAwait(spoken, settings, fishVoiceId) {
                            playbackStarted = true
                        }
                        playedAny = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (t: Throwable) {
                        firstSegmentError = t
                    }
                } else {
                    // Arm barge-in while the first clip is still synthesising so the user
                    // can talk over Kryzz the moment it starts talking — not only after the
                    // first TTS request returns. The monitor stays armed across segments
                    // because phase remains SPEAKING until playback ends.
                    voicePhase.value = VoicePhase.SPEAKING
                    startVoicePlaybackMonitor()
                }
                for (clip in channel) {
                    if (voicePhase.value != VoicePhase.PROCESSING && voicePhase.value != VoicePhase.SPEAKING) {
                        clip.delete()
                        break
                    }
                    // Monitor is armed in the branch above (Fish stream) or in the else block
                    // (segmented synthesis), so barge-in works from the first audible chunk.
                    playVoiceClipAwait(clip) {
                        playbackStarted = true
                    }
                    playedAny = true
                }
            } catch (_: CancellationException) {
                // Barge-in or a manual stop: the pipeline is gone; any clips that were
                // synthesised but never heard are deleted below.
            } catch (t: Throwable) {
                firstSegmentError = t
            }
            producer.cancel()
            runCatching { producer.join() }
            while (true) {
                val pendingClip = channel.tryReceive()
                if (!pendingClip.isSuccess) break
                pendingClip.getOrNull()?.delete()
            }
            val heard = playedAny || playbackStarted
            if (voicePhase.value == VoicePhase.SPEAKING && heard) {
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
                container.voiceRecorder.stop()?.delete()
                voicePhase.value = VoicePhase.IDLE
                error.value = firstSegmentError?.message ?: "The voice reply could not be generated."
            }
        }
    }

    /** Watches reply playback for adaptive, sustained user speech and continues that turn. */
    private fun startVoicePlaybackMonitor() {
        val recorderLive = container.voiceRecorder.start().isSuccess
        // Reset both barge detectors so a previous barge doesn't leak into this reply.
        bargeDetector.reset(SystemClock.elapsedRealtime())
        softBargeDetector.reset(SystemClock.elapsedRealtime())
        pendingBargePrefix = null
        pendingBargePrefixLength = 0L
        voiceJob = viewModelScope.launch {
            var bargeArmed = false
            while (true) {
                val phaseNow = voicePhase.value
                if (phaseNow != VoicePhase.SPEAKING && !(bargeArmed && phaseNow == VoicePhase.LISTENING)) break
                val now = SystemClock.elapsedRealtime()
                val playerLevel = container.voicePlayer.level()
                val micRaw = if (recorderLive) container.voiceRecorder.level() else 0
                val micLevel = (micRaw / 32_767f).coerceIn(0f, 1f)

                if (phaseNow == VoicePhase.SPEAKING) {
                    // Two barge-in gates run in parallel:
                    //   1. "outshouts reply" — mic > player * margin + floor. Fast (BARGE_TRIGGER_MS).
                    //      Catches normal voices on a quiet phone speaker.
                    //   2. "soft barge" — mic sustains above absolute floor for longer. Catches the
                    //      case where the speaker is loud and the user's voice can't outshout it
                    //      after echo cancellation (the previously broken case).
                    val micOutshoutsReply = recorderLive &&
                        micLevel > maxOf(playerLevel * BARGE_MIC_MARGIN + BARGE_MIC_FLOOR, BARGE_MIC_MIN)
                    val micSustainedSoft = recorderLive && micLevel >= BARGE_SOFT_MIN
                    val activity = when {
                        micOutshoutsReply -> bargeDetector.observe(micRaw, now)
                        micSustainedSoft -> softBargeDetector.observe(micRaw, now)
                        else -> null
                    }
                    voiceLevel.value = maxOf(playerLevel, activity?.level ?: 0f)
                    val hardTrigger = activity != null && activity.hasSpeech &&
                        activity.voicedDurationMs >= VoiceConfig.BARGE_TRIGGER_MS
                    val softTrigger = micSustainedSoft &&
                        activity != null && activity.hasSpeech &&
                        activity.voicedDurationMs >= BARGE_SOFT_TRIGGER_MS
                    if (!bargeArmed && (hardTrigger || softTrigger)) {
                        bargeArmed = true
                        val voicedMs = activity?.voicedDurationMs ?: 0L
                        fadePlayerOut()
                        container.voicePlayer.setVolume(0f)
                        container.voicePlayer.stop()
                        intentionalVoiceCancel = true
                        voicePipelineJob?.cancel()
                        voicePipelineJob = null

                        // KEEP the monitor file: it contains the user's cut-off speech up to this
                        // point. Discarding it (the old behaviour) meant STT never heard the words
                        // that triggered the barge. We rename it so a subsequent stop() doesn't
                        // delete it, and we re-use it as the start of the new LISTENING clip.
                        val bargeFile = container.voiceRecorder.stop()
                        val bargeStartAt = now - voicedMs
                        // Start a fresh recorder so additional speech appends to a new clip,
                        // then concatenate the barge prefix + new clip when the user finishes.
                        val newClip = container.newVoiceFile()
                        val prefixLength = bargeFile?.length() ?: 0L
                        if (container.voiceRecorder.start().isFailure) {
                            bargeFile?.delete()
                            voicePhase.value = VoicePhase.IDLE
                            error.value = "The microphone could not restart after interruption."
                            break
                        }
                        pendingBargePrefix = bargeFile
                        pendingBargePrefixLength = prefixLength
                        voiceStartedAt = bargeStartAt
                        // Seed the end-of-speech detector with the barge-spoken time so silence
                        // detection treats the barge and the user's continuation as one utterance.
                        voiceEndDetector.reset(voiceStartedAt)
                        voiceEndDetector.seedSpeech(now, voicedMs)
                        softBargeDetector.reset(now)
                        bargeDetector.reset(now)
                        voicePhase.value = VoicePhase.LISTENING
                    }
                } else {
                    val activity = voiceEndDetector.observe(micRaw, now)
                    voiceLevel.value = activity.level
                    if (activity.stopReason != null) {
                        stopVoiceRecording(automatic = true)
                        break
                    }
                }
                delay(VOICE_POLL_MS)
            }
        }
    }

    /** Smoothly lowers the TTS volume so the user's voice wins the channel. */
    private suspend fun fadePlayerOut() {
        repeat(4) { step ->
            container.voicePlayer.setVolume(1f - (step + 1) / 4f)
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

    private suspend fun playFishStreamAwait(
        segment: String,
        settings: SettingsState,
        fishVoiceId: String?,
        onStarted: () -> Unit
    ) {
        var started = false
        try {
            container.voicePlayer.playPcm(
                chunks = container.synthesizeVoiceStream(
                    text = segment,
                    speed = settings.fishSpeed.toDouble(),
                    fishModel = settings.fishModel,
                    fishVoiceId = fishVoiceId,
                    emotions = settings.voiceEmotions
                ),
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
                    text = segment,
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
         * Soft barge-in: when the speaker is loud and the user can't outshout it, accept any
         * sustained voice above [BARGE_SOFT_MIN] for [BARGE_SOFT_TRIGGER_MS]. The longer window
         * is the "noise floor" defence — a single TV syllable won't arm; a sentence will.
         */
        const val BARGE_SOFT_MIN = 0.04f
        const val BARGE_SOFT_TRIGGER_MS = 700L
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
