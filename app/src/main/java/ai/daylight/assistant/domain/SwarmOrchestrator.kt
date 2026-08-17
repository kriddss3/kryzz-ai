package ai.daylight.assistant.domain

import ai.daylight.assistant.data.MemoryRepository
import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.preferences.AppPreferences
import ai.daylight.assistant.data.remote.ApiMessage
import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.ErrorKind
import ai.daylight.assistant.data.remote.OpenRouterClient
import ai.daylight.assistant.data.remote.ReasoningConfig
import ai.daylight.assistant.data.remote.StreamEvent
import ai.daylight.assistant.data.remote.Usage
import ai.daylight.assistant.security.SecureCredentialStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SwarmPhase { PLANNING, RUNNING, SYNTHESIZING, DONE, FAILED }
enum class SubagentState { PENDING, RUNNING, DONE, FAILED }

data class SubagentStatus(val index: Int, val title: String, val state: SubagentState)
data class SwarmStatus(val phase: SwarmPhase, val subagents: List<SubagentStatus> = emptyList())

@Serializable
data class SwarmSubtask(val title: String, val instruction: String)

@Serializable
private data class SwarmPlanEnvelope(val subtasks: List<SwarmSubtask> = emptyList())

private const val MAX_SUBTASKS = 4
private const val MAX_CONCURRENT_SUBAGENTS = 3

/**
 * Lenient planner-output parser: tolerates markdown fences and surrounding prose by
 * decoding the first {...} block, keeps at most [MAX_SUBTASKS] usable subtasks, and
 * falls back to a single subtask covering the whole task when nothing parses.
 */
internal fun parseSwarmPlan(raw: String, task: String, json: Json = Json { ignoreUnknownKeys = true }): List<SwarmSubtask> {
    val fallback = SwarmSubtask(
        title = task.lineSequence().firstOrNull()?.trim()?.take(60)?.ifBlank { null } ?: "Full task",
        instruction = task
    )
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return listOf(fallback)
    val parsed = runCatching { json.decodeFromString<SwarmPlanEnvelope>(raw.substring(start, end + 1)) }.getOrNull()
        ?: return listOf(fallback)
    val subtasks = parsed.subtasks
        .map { it.copy(title = it.title.trim().take(60), instruction = it.instruction.trim()) }
        .filter { it.title.isNotBlank() && it.instruction.isNotBlank() }
        .take(MAX_SUBTASKS)
    return subtasks.ifEmpty { listOf(fallback) }
}

/**
 * Agent-swarm orchestrator: a planner splits the task into 2–4 subtasks, subagents
 * execute them in parallel (capped by a semaphore) with their streamed output visible
 * as separate chat messages, and a final synthesis call merges the results into the
 * normal assistant reply. Cancelling [run] cancels every phase; in-flight subagent
 * messages are marked CANCELLED.
 */
class SwarmOrchestrator(
    private val dao: AssistantDao,
    private val preferences: AppPreferences,
    private val credentials: SecureCredentialStore,
    private val openRouter: OpenRouterClient,
    private val memoryRepository: MemoryRepository,
    private val json: Json,
    /** Live swarm progress so the UI can render the planner/subagent/synthesis phases. */
    val status: MutableStateFlow<SwarmStatus?> = MutableStateFlow(null),
    private val onFirstUserMessage: (conversationId: String, message: String) -> Unit = { _, _ -> }
) {
    suspend fun run(
        conversationId: String,
        task: String,
        capability: AgentCapability = AgentCapability.AUTO
    ) {
        val clean = task.trim()
        require(clean.isNotEmpty())
        val key = credentials.openRouterKey()
            ?: throw AssistantApiException(ErrorKind.INVALID_KEY, "Add an OpenRouter API key in Settings before sending a message.")
        val settings = preferences.state.first()
        val now = System.currentTimeMillis()
        if (dao.conversation(conversationId) == null) {
            dao.upsertConversation(ConversationEntity(conversationId, "New conversation", now, now))
        }
        val existing = dao.messages(conversationId)
        dao.upsertMessage(
            MessageEntity(
                UUID.randomUUID().toString(), conversationId, "USER", clean, now,
                mode = AssistantMode.AGENT.name,
                capability = capability.name
            )
        )
        dao.touchConversation(conversationId, now)
        if (existing.none { it.role == "USER" }) onFirstUserMessage(conversationId, clean)
        if (settings.memoryEnabled) memoryRepository.ingest(clean, conversationId)

        val model = when (capability) {
            AgentCapability.DEEP_RESEARCH, AgentCapability.WIDE_SEARCH -> settings.researchModel
            else -> settings.agentModel
        }.ifBlank { settings.defaultModel }
        val reasoning = settings.modelReasoning[model]?.apiValue?.let { ReasoningConfig(effort = it, exclude = true) }
        val preset = AssistantPreset.byId(settings.presetId)
        val basePrompt = if (preset.id == "custom") settings.customPrompt.ifBlank { AssistantPreset.balanced.prompt } else preset.prompt

        try {
            // ── Plan ────────────────────────────────────────────────────────
            status.value = SwarmStatus(SwarmPhase.PLANNING)
            val planText = collectText(
                key,
                ChatRequest(
                    model = model,
                    messages = listOf(
                        ApiMessage(
                            "system",
                            "You are the planner of an agent swarm. Split the user's task into 2 to 4 parallel " +
                                "subtasks that different subagents can complete independently. Reply with ONLY JSON " +
                                "in exactly this shape, no markdown and no commentary: " +
                                "{\"subtasks\":[{\"title\":\"short label\",\"instruction\":\"self-contained instructions\"}]}. " +
                                "Every instruction must stand alone without the other subtasks. Capability guidance: ${capability.instruction}"
                        ),
                        ApiMessage("user", clean)
                    ),
                    reasoning = reasoning,
                    maxTokens = 1_200
                )
            )
            val subtasks = parseSwarmPlan(planText, clean, json)

            // ── Parallel subagents ──────────────────────────────────────────
            status.value = SwarmStatus(
                SwarmPhase.RUNNING,
                subtasks.mapIndexed { index, subtask -> SubagentStatus(index, subtask.title, SubagentState.PENDING) }
            )
            val semaphore = Semaphore(MAX_CONCURRENT_SUBAGENTS)
            val results: List<String?> = coroutineScope {
                subtasks.mapIndexed { index, subtask ->
                    async {
                        semaphore.withPermit {
                            runSubagent(key, model, reasoning, conversationId, index, subtasks.size, clean, subtask, capability, basePrompt)
                        }
                    }
                }.awaitAll()
            }
            val failures = subtasks.indices.count { results[it] == null }
            if (failures == subtasks.size) {
                throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "Every swarm subagent failed. Check the selected agent model and try again.")
            }

            // ── Synthesis ───────────────────────────────────────────────────
            status.update { it?.copy(phase = SwarmPhase.SYNTHESIZING) }
            synthesize(key, model, reasoning, conversationId, clean, capability, basePrompt, subtasks, results)
            status.update { it?.copy(phase = SwarmPhase.DONE) }
        } catch (cancelled: CancellationException) {
            status.update { it?.copy(phase = SwarmPhase.FAILED) }
            throw cancelled
        } catch (error: Throwable) {
            status.update { current ->
                current?.copy(
                    phase = SwarmPhase.FAILED,
                    subagents = current.subagents.map {
                        if (it.state == SubagentState.RUNNING || it.state == SubagentState.PENDING) it.copy(state = SubagentState.FAILED) else it
                    }
                )
            }
            throw error
        } finally {
            status.value = null
        }
    }

    private suspend fun runSubagent(
        key: String,
        model: String,
        reasoning: ReasoningConfig?,
        conversationId: String,
        index: Int,
        total: Int,
        task: String,
        subtask: SwarmSubtask,
        capability: AgentCapability,
        basePrompt: String
    ): String? {
        val header = "⚡ Subagent ${index + 1} — ${subtask.title}"
        val messageId = UUID.randomUUID().toString()
        dao.upsertMessage(
            MessageEntity(
                messageId, conversationId, "ASSISTANT", "$header\n\n", System.currentTimeMillis() + index,
                MessageStatus.STREAMING.name,
                mode = AssistantMode.AGENT.name,
                capability = capability.name
            )
        )
        setSubagentState(index, SubagentState.RUNNING)
        var text = ""
        try {
            val request = ChatRequest(
                model = model,
                messages = listOf(
                    ApiMessage(
                        "system",
                        "$basePrompt ${capability.instruction} You are subagent ${index + 1} of $total in an agent " +
                            "swarm. Complete only your assigned subtask; a coordinator merges every subtask result " +
                            "into the final answer. Return finished content for your part with no meta commentary."
                    ),
                    ApiMessage("user", "Overall task: $task\n\nYour subtask (${subtask.title}): ${subtask.instruction}")
                ),
                reasoning = reasoning
            )
            var failure: AssistantApiException? = null
            openRouter.stream(key, request).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        text += event.text
                        if (event.text.isNotEmpty()) dao.updateMessageContent(messageId, "$header\n\n$text", MessageStatus.STREAMING.name)
                    }
                    is StreamEvent.UsageUpdate -> Unit
                    is StreamEvent.Failure -> failure = event.error
                    StreamEvent.Done -> Unit
                }
            }
            failure?.let { throw it }
            if (text.isBlank()) {
                throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "Subagent ${index + 1} returned no text.")
            }
            dao.updateMessageContent(messageId, "$header\n\n$text", MessageStatus.COMPLETE.name)
            setSubagentState(index, SubagentState.DONE)
            return text
        } catch (cancelled: CancellationException) {
            dao.updateMessageContent(messageId, "$header\n\n$text", MessageStatus.CANCELLED.name)
            setSubagentState(index, SubagentState.FAILED)
            throw cancelled
        } catch (error: Throwable) {
            val friendly = (error as? AssistantApiException)?.message ?: "This subagent failed."
            val content = if (text.isBlank()) "$header\n\n$friendly" else "$header\n\n$text\n\n$friendly"
            dao.updateMessageContent(messageId, content, MessageStatus.ERROR.name)
            setSubagentState(index, SubagentState.FAILED)
            return null
        }
    }

    private suspend fun synthesize(
        key: String,
        model: String,
        reasoning: ReasoningConfig?,
        conversationId: String,
        task: String,
        capability: AgentCapability,
        basePrompt: String,
        subtasks: List<SwarmSubtask>,
        results: List<String?>
    ) {
        val messageId = UUID.randomUUID().toString()
        dao.upsertMessage(
            MessageEntity(
                messageId, conversationId, "ASSISTANT", "", System.currentTimeMillis(), MessageStatus.STREAMING.name,
                mode = AssistantMode.AGENT.name,
                capability = capability.name
            )
        )
        val briefing = buildString {
            append("Original task: ").append(task).append("\n\n")
            subtasks.forEachIndexed { index, subtask ->
                val result = results[index]
                if (result != null) {
                    append("## Part ${index + 1}: ${subtask.title}\n").append(result).append("\n\n")
                } else {
                    append("## Part ${index + 1}: ${subtask.title}\n(this part failed; work around the gap)\n\n")
                }
            }
        }
        val request = ChatRequest(
            model = model,
            messages = listOf(
                ApiMessage(
                    "system",
                    "$basePrompt ${capability.instruction} You are the coordinator of an agent swarm. Subagents have " +
                        "finished parts of the user's task, provided below. Merge them into one coherent, finished " +
                        "reply that fully answers the original task. Do not describe the swarm process itself."
                ),
                ApiMessage("user", briefing)
            ),
            reasoning = reasoning
        )
        var text = ""
        var usage: Usage? = null
        var failure: AssistantApiException? = null
        try {
            openRouter.stream(key, request).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        text += event.text
                        if (event.text.isNotEmpty()) dao.updateMessageContent(messageId, text, MessageStatus.STREAMING.name)
                    }
                    is StreamEvent.UsageUpdate -> usage = event.usage
                    is StreamEvent.Failure -> failure = event.error
                    StreamEvent.Done -> Unit
                }
            }
            failure?.let { throw it }
            if (text.isBlank()) {
                throw AssistantApiException(
                    ErrorKind.MODEL_UNAVAILABLE,
                    "The selected model returned no text. Try a different model or lower its reasoning effort."
                )
            }
            dao.finishMessage(
                messageId, text, MessageStatus.COMPLETE.name, "[]",
                usage?.promptTokens, usage?.completionTokens, usage?.totalTokens, usage?.cost
            )
            dao.touchConversation(conversationId, System.currentTimeMillis())
        } catch (cancelled: CancellationException) {
            dao.updateMessageContent(messageId, text, MessageStatus.CANCELLED.name)
            throw cancelled
        } catch (error: Throwable) {
            val friendly = (error as? AssistantApiException)?.message ?: "Something went wrong. Check your connection and try again."
            val content = if (text.isBlank()) friendly else "$text\n\n$friendly"
            dao.finishMessage(messageId, content, MessageStatus.ERROR.name, "[]", null, null, null, null)
            throw error
        }
    }

    /** Streams one request to completion and returns only the accumulated text. */
    private suspend fun collectText(key: String, request: ChatRequest): String {
        var text = ""
        var failure: AssistantApiException? = null
        openRouter.stream(key, request).collect { event ->
            when (event) {
                is StreamEvent.Delta -> text += event.text
                is StreamEvent.UsageUpdate -> Unit
                is StreamEvent.Failure -> failure = event.error
                StreamEvent.Done -> Unit
            }
        }
        failure?.let { throw it }
        return text
    }

    private fun setSubagentState(index: Int, state: SubagentState) {
        status.update { current ->
            current?.copy(
                subagents = current.subagents.map { if (it.index == index) it.copy(state = state) else it }
            )
        }
    }
}
