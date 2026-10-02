package ai.daylight.assistant.domain

import ai.daylight.assistant.data.AttachmentStore
import ai.daylight.assistant.data.ConversationRepository
import ai.daylight.assistant.data.CronRecurrence
import ai.daylight.assistant.data.CronSchedulePresets
import ai.daylight.assistant.data.CronScheduler
import ai.daylight.assistant.data.GeneratedOutputStore
import ai.daylight.assistant.data.CodeProjectGenerator
import ai.daylight.assistant.data.LocationProvider
import ai.daylight.assistant.data.MemoryRepository
import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.data.preferences.AppPreferences
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.data.preferences.agentQualityInputs
import ai.daylight.assistant.data.remote.ApiMessage
import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.CalculateArgs
import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.CreateArtifactArgs
import ai.daylight.assistant.data.remote.CreateCodeProjectArgs
import ai.daylight.assistant.data.remote.CreateSkillArgs
import ai.daylight.assistant.data.remote.ErrorKind
import ai.daylight.assistant.data.remote.FetchUrlArgs
import ai.daylight.assistant.data.remote.FetchedPage
import ai.daylight.assistant.data.remote.FunctionCall
import ai.daylight.assistant.data.remote.FunctionDefinition
import ai.daylight.assistant.data.remote.ImageGenerationRequest
import ai.daylight.assistant.data.remote.MiniMaxClient
import ai.daylight.assistant.data.remote.MiniMaxImagePayload
import ai.daylight.assistant.data.remote.MiniMaxMusicRequest
import ai.daylight.assistant.data.remote.MiniMaxTtsRequest
import ai.daylight.assistant.data.remote.MiniMaxVideoContentItem
import ai.daylight.assistant.data.remote.MiniMaxVideoRequest
import ai.daylight.assistant.data.remote.bareMiniMaxModel
import ai.daylight.assistant.data.remote.isMiniMaxImageModel
import ai.daylight.assistant.data.remote.isMiniMaxVideoModel
import ai.daylight.assistant.data.remote.miniMaxImageRequest
import ai.daylight.assistant.data.remote.requireImagePayload
import ai.daylight.assistant.data.remote.throwIfFailed
import ai.daylight.assistant.data.remote.OpenRouterClient
import ai.daylight.assistant.data.remote.ParallelClient
import ai.daylight.assistant.data.remote.ParallelSearchArgs
import ai.daylight.assistant.data.remote.ParallelSearchRequest
import ai.daylight.assistant.data.remote.ParallelSearchResponse
import ai.daylight.assistant.data.remote.PromptTokensDetails
import ai.daylight.assistant.data.remote.ProviderPreferences
import ai.daylight.assistant.data.remote.PublicWebClient
import ai.daylight.assistant.data.remote.ReasoningConfig
import ai.daylight.assistant.data.remote.RecallMemoriesArgs
import ai.daylight.assistant.data.remote.RememberFactArgs
import ai.daylight.assistant.data.remote.ScheduleTaskArgs
import ai.daylight.assistant.data.remote.SearchPastChatsArgs
import ai.daylight.assistant.data.remote.SpeechGenerationRequest
import ai.daylight.assistant.data.remote.StreamEvent
import ai.daylight.assistant.data.remote.TextProviderClient
import ai.daylight.assistant.data.remote.ToolCall
import ai.daylight.assistant.data.remote.ToolChoice
import ai.daylight.assistant.data.remote.ToolDefinition
import ai.daylight.assistant.data.remote.Usage
import ai.daylight.assistant.data.remote.VideoGenerationRequest
import ai.daylight.assistant.data.remote.WeatherArgs
import ai.daylight.assistant.data.remote.WeatherReport
import ai.daylight.assistant.data.remote.accumulateStreamText
import ai.daylight.assistant.security.SecureCredentialStore
import ai.daylight.assistant.data.local.SkillEntity
import ai.daylight.assistant.voice.VoiceConfig
import android.os.SystemClock
import android.util.Base64
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AgentExecutor(
    private val dao: AssistantDao,
    private val preferences: AppPreferences,
    private val credentials: SecureCredentialStore,
    private val openRouter: OpenRouterClient,
    private val minimax: MiniMaxClient,
    private val parallel: ParallelClient,
    private val outputStore: GeneratedOutputStore,
    private val attachmentStore: AttachmentStore,
    private val memoryRepository: MemoryRepository,
    private val json: Json,
    private val locationProvider: LocationProvider? = null,
    private val publicWeb: PublicWebClient? = null,
    private val cronScheduler: CronScheduler? = null,
    private val conversations: ConversationRepository? = null,
    /**
     * Live tool activity so the UI can show what the agent is currently running. Sized for
     * a round that starts several lookups at once: tryEmit drops events once the buffer is
     * full, and a dropped "finished" event would leave a chip spinning.
     */
    val toolActivity: MutableSharedFlow<ToolActivity> = MutableSharedFlow(extraBufferCapacity = 64),
    /**
     * Emits a short label whenever the agent starts a long-running tool call during voice
     * chat — e.g. "checking online" when parallel_search fires. The voice UI plays a quick
     * filler phrase so the user isn't sitting in silence while the round-trip completes.
     */
    val voiceFillerRequest: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 4),
    private val onFirstUserMessage: (conversationId: String, message: String) -> Unit = { _, _ -> }
) {
    /**
     * The interactive question card currently waiting for the user (agent `ask_user` tool),
     * or null. The chat UI renders this as a floating card; answering it completes the
     * deferred and the tool call returns the answer to the model. Single slot: a second
     * concurrent ask_user call is rejected with a tool error instead of queueing.
     */
    val pendingQuestion: MutableStateFlow<PendingQuestion?> = MutableStateFlow(null)

    /**
     * The checklist the agent maintains through `update_plan` during the current turn, or
     * null. Cleared when the turn ends; the chat renders it as a live card.
     */
    val agentPlan: MutableStateFlow<AgentPlan?> = MutableStateFlow(null)

    /**
     * Delivers the user's answer to the pending question card. [answer] null (or blank)
     * means the user dismissed the card — the model is told to continue with its best
     * judgment instead of hanging.
     */
    fun answerPendingQuestion(conversationId: String, answer: String?) {
        val pending = pendingQuestion.value ?: return
        if (pending.conversationId != conversationId) return
        pending.deferred.complete(answer?.trim()?.takeIf { it.isNotBlank() }?.take(500))
    }


    suspend fun send(
        conversationId: String,
        text: String,
        mode: AssistantMode = AssistantMode.CHAT,
        capability: AgentCapability = AgentCapability.AUTO,
        attachments: List<ChatAttachment> = emptyList(),
        voiceMode: Boolean = false,
        onVoiceText: VoiceReplyListener? = null
    ): String? {
        val clean = text.trim()
        require(clean.isNotEmpty())
        val now = System.currentTimeMillis()
        // A chat can outlive its row (e.g. pruned while covered by Settings); restore it
        // so the message insert below never fails on the conversation foreign key.
        if (dao.conversation(conversationId) == null) {
            dao.upsertConversation(ConversationEntity(conversationId, "New conversation", now, now))
        }
        val existing = dao.messages(conversationId)
        val savedMode = if (voiceMode) AssistantMode.CHAT else mode
        dao.upsertMessage(
            MessageEntity(
                UUID.randomUUID().toString(), conversationId, "USER", clean, now,
                mode = savedMode.name,
                capability = if (savedMode == AssistantMode.AGENT) capability.name else null,
                attachmentsJson = json.encodeToString(attachments)
            )
        )
        dao.touchConversation(conversationId, now)
        if (existing.none { it.role == "USER" }) onFirstUserMessage(conversationId, clean)
        if (preferences.state.first().memoryEnabled && memoryRepository.ingest(clean, conversationId)) {
            // A new fact was saved: let the chat show the brief "Remembering that…" chip.
            toolActivity.tryEmit(ToolActivity(conversationId, TOOL_MEMORY_SAVE, started = true))
        }
        return when {
            voiceMode -> generateReply(conversationId, AssistantMode.CHAT, AgentCapability.AUTO, voiceMode = true, onVoiceText = onVoiceText)
            savedMode == AssistantMode.AGENT && capability == AgentCapability.IMAGE -> { generateImage(conversationId, clean); null }
            savedMode == AssistantMode.AGENT && capability == AgentCapability.VIDEO -> { generateVideo(conversationId, clean); null }
            savedMode == AssistantMode.AGENT && capability == AgentCapability.AUDIO -> { generateAudio(conversationId, clean); null }
            else -> generateReply(conversationId, savedMode, capability)
        }
    }

    suspend fun generateReply(
        conversationId: String,
        requestedMode: AssistantMode? = null,
        requestedCapability: AgentCapability? = null,
        voiceMode: Boolean = false,
        onVoiceText: VoiceReplyListener? = null
    ): String? {
        val settings = preferences.state.first()
        val provider = if (voiceMode) ChatProvider.OPENROUTER else settings.chatProvider
        val key = when (provider) {
            ChatProvider.OPENROUTER -> credentials.openRouterKey()
            ChatProvider.MINIMAX -> credentials.minimaxKey()
        } ?: throw AssistantApiException(
            ErrorKind.INVALID_KEY,
            if (provider == ChatProvider.MINIMAX) "Add a MiniMax API key in Settings before sending a message."
            else "Add an OpenRouter API key in Settings before sending a message."
        )
        val client: TextProviderClient = if (provider == ChatProvider.MINIMAX) minimax else openRouter
        val conversation = dao.conversation(conversationId) ?: return null
        val allMessages = dao.messages(conversationId)
        val lastUser = allMessages.lastOrNull { it.role == "USER" }
        val mode = requestedMode ?: lastUser?.mode?.let { runCatching { AssistantMode.valueOf(it) }.getOrNull() } ?: AssistantMode.CHAT
        val capability = requestedCapability ?: lastUser?.capability?.let { runCatching { AgentCapability.valueOf(it) }.getOrNull() } ?: AgentCapability.AUTO
        val history = allMessages.filter { it.role != "TOOL" && it.status != MessageStatus.ERROR.name }
        val preset = AssistantPreset.byId(settings.presetId)
        val voiceLab = voiceMode && VoiceConfig.developerLabActive(
            history.filter { it.role == "USER" }.map { it.content }
        )
        val basePrompt = when {
            voiceMode -> VoiceConfig.voiceSystemPrompt(settings.voiceEmotions, developerLab = voiceLab)
            preset.id == "custom" -> settings.customPrompt.ifBlank { AssistantPreset.balanced.prompt }
            else -> preset.prompt
        }
        val enabledSkills = if (!voiceMode && mode == AssistantMode.AGENT && capability != AgentCapability.SKILL_MAKER) dao.enabledSkills().take(16) else emptyList()
        val memoryContext = if (settings.memoryEnabled && !voiceMode) memoryRepository.buildMemoryContext(lastUser?.content.orEmpty()) else ""
        val memoryInstruction = if (settings.memoryEnabled && !voiceMode) {
            "Long-term memory is active: facts the user shares about themselves (name, age, gender, work, interests, hobbies, ethnicity, where they live) and anything they ask you to remember are saved automatically. When you notice a new fact was shared, briefly acknowledge that you'll remember it."
        } else ""
        val pastChatContext = buildPastChatContext(conversationId, lastUser?.content.orEmpty(), voiceMode)
        val pastChatInstruction = if (!voiceMode) {
            "You can look up earlier conversations on this phone with search_past_chats when the user refers to something from a past chat and the pasted snippets are not enough. Use a short keyword query. Never invent past chats."
        } else ""
        // Voice chat also gets the user's region: spoken answers to "what's the weather",
        // "any pharmacies nearby", etc. need local context just as much as text ones.
        val locationContext = runCatching { locationProvider?.promptContext().orEmpty() }.getOrDefault("")
        // Chat mode has no tool calling, so interactive question cards travel as a fenced
        // kryzz-question block in the reply text (see InlineQuestionProtocol). Agent mode
        // gets the real ask_user tool instead, and voice stays text-only.
        val questionCardInstruction = if (!voiceMode && mode == AssistantMode.CHAT) {
            "\n\n${InlineQuestionProtocol.CHAT_PROTOCOL_PROMPT}"
        } else ""
        // v5.10: the Agent quality preset decides the model, reasoning, step budget, review
        // and cost cap of agent turns (AgentQualityPolicy). Chat and voice turns resolve as
        // Balanced, which is exactly the user's own settings, and keep their own models below.
        val agentTurn = mode == AssistantMode.AGENT && !voiceMode
        val turnSettings = AgentQualityPolicy.resolve(
            quality = if (agentTurn) settings.agentQuality else AgentQuality.BALANCED,
            user = settings.agentQualityInputs(),
            research = capability in setOf(AgentCapability.DEEP_RESEARCH, AgentCapability.WIDE_SEARCH)
        )
        val model = when {
            voiceMode -> settings.voiceReplyModel.ifBlank {
                if (settings.chatProvider == ChatProvider.OPENROUTER) settings.defaultModel else VoiceConfig.DEFAULT_LLM_MODEL
            }.ifBlank { VoiceConfig.DEFAULT_LLM_MODEL }
            mode == AssistantMode.CHAT -> settings.defaultModel
            else -> turnSettings.model
        }
        val reasoningEffort = when {
            voiceMode || provider == ChatProvider.MINIMAX -> null
            agentTurn -> turnSettings.reasoning.apiValue
            else -> settings.modelReasoning[model]?.apiValue
        }
        // v5.10 prompt caching (PromptLayout): the system prompt holds only turn-stable text,
        // and what was retrieved for the latest message travels in its own message right
        // before it, so the system prompt and the history stay a cacheable prefix.
        val systemPrompt = buildModePrompt(basePrompt, mode, capability) + questionCardInstruction +
            (if (memoryInstruction.isNotBlank()) "\n\n$memoryInstruction" else "") +
            (if (pastChatInstruction.isNotBlank()) "\n\n$pastChatInstruction" else "")
        val turnContext = PromptLayout.turnContext(
            listOf(enabledSkillsPrompt(enabledSkills, lastUser?.content.orEmpty()), memoryContext, pastChatContext, locationContext)
        )
        // The latest replies carry their sources and files back into context, so "open
        // source 3" or "add a column to that sheet" still has something to refer to.
        val evidenceReplies = if (voiceMode) emptySet() else history
            .filter { it.role == "ASSISTANT" && (it.citationsJson != "[]" || it.outputsJson != "[]") }
            .takeLast(TurnEvidence.MAX_ANNOTATED_REPLIES)
            .mapTo(mutableSetOf()) { it.id }
        val working = PromptLayout.build(
            system = systemPrompt,
            history = history.map { apiMessageFor(it, withEvidence = it.id in evidenceReplies) },
            turnContext = turnContext,
            cacheBreakpoints = provider == ChatProvider.OPENROUTER && PromptLayout.usesCacheBreakpoints(model)
        ).toMutableList()
        // fetch_url may open what the user shared or what a search returned (see FetchAllowlist).
        val userTexts = allMessages.filter { it.role == "USER" }.map { it.content }
        val conversationUrls = userTexts.flatMap(FetchAllowlist::urlsIn) +
            allMessages.filter { it.role == "ASSISTANT" }.flatMap { decodeCitations(it).map(Citation::url) }
        val citations = mutableListOf<Citation>()
        val outputs = mutableListOf<GeneratedOutput>()
        var sessionId = conversation.searchSessionId
        var completedToolRounds = 0
        var freePlanRounds = 0
        var completedSearchRounds = 0
        var didSearch = false
        var didFetch = false
        var skillCreated = false
        // v5.7 agent-loop redesign: continuation is decided from what the model DID
        // (returned tool calls or not) via AgentTurnPolicy, not by regex-matching its
        // prose. The only per-turn flags left are the one-shot round-0 nudge and the
        // single terminal no-tools round that guarantees the turn ends with an answer.
        var nudgedForTools = false
        var terminalRoundStarted = false
        // v5.9: at most one review pass per turn; the cost cap moves the turn to its terminal
        // round, and the answer then says so (costCapNote).
        var reviewed = false
        var costCapped = false
        var stoppedByCostCap = false
        val costCapUsd = if (voiceMode) 0.0 else turnSettings.costCapCents / 100.0
        var activeId: String? = null
        var activeText = ""
        var requestPromptTokens = 0L
        var requestCompletionTokens = 0L
        var requestTotalTokens = 0L
        var requestCachedInputTokens = 0L
        var requestCost = 0.0
        var hasUsage = false
        var hasCost = false
        var firstTokenLatencyMs: Long? = null
        val generationStartedAt = SystemClock.elapsedRealtime()
        fun addUsage(usage: Usage?) {
            if (usage == null) return
            hasUsage = true
            requestPromptTokens += usage.promptTokens ?: 0
            requestCompletionTokens += usage.completionTokens ?: 0
            requestTotalTokens += usage.totalTokens ?: ((usage.promptTokens ?: 0) + (usage.completionTokens ?: 0))
            requestCachedInputTokens += usage.promptTokensDetails?.cachedTokens ?: 0
            usage.cost?.let { cost -> hasCost = true; requestCost += cost }
        }
        // v5.10: [noToolCalls] keeps the tools in the request (and in the cached prefix) but
        // sends tool_choice "none", for the terminal round and a review that needs no tools.
        fun chatRequest(tools: List<ToolDefinition>, forcedTool: String?, noToolCalls: Boolean = false) = ChatRequest(
            model = model,
            messages = working,
            tools = tools.ifEmpty { null },
            toolChoice = when {
                tools.isEmpty() -> null
                noToolCalls -> ToolChoice.NONE
                // Pin the named function on every provider: MiniMax rejects the
                // string "required" (status 2013), and elsewhere "required" would let
                // the model satisfy a forced search with any other offered tool.
                forcedTool != null -> ToolChoice.named(forcedTool)
                else -> ToolChoice.AUTO
            },
            reasoning = reasoningEffort?.let { effort -> ReasoningConfig(effort = effort, exclude = true) },
            // Voice chat always routes to the lowest-latency provider serving this model.
            provider = if (voiceMode && provider == ChatProvider.OPENROUTER) ProviderPreferences() else null,
            // MiniMax thinking + a tool call can exceed a tiny default completion budget.
            maxTokens = if (provider == ChatProvider.MINIMAX) MINIMAX_MAX_COMPLETION_TOKENS else null
        )
        val agentRunningActivity = agentTurn
        // v5.10: the turn's tool list as last sent, reused by the review pass so its request
        // shares the cached prefix too.
        var turnTools: List<ToolDefinition> = emptyList()

        /**
         * v5.9 review pass (see [AnswerReview]). The draft stays on screen while the reviewer
         * runs; a revision replaces it in the same message as it streams, the sentinel keeps
         * it. Any failure other than cancellation keeps the draft, and a cancelled review
         * leaves the draft in the message instead of a half-written revision.
         */
        suspend fun reviewDraft(messageId: String, draft: String): String {
            val offerArtifact = AnswerReview.offersArtifact(outputs.map { it.kind }.toSet())
            // v5.10: on OpenRouter the review resends the turn's tools (cache hit on the whole
            // turn) with tool_choice "none", or "auto" when a flawed file may be replaced; only
            // create_artifact calls are acted on. Otherwise it offers create_artifact alone.
            val reuseTurnTools = AgentTurnPolicy.keepsToolsOnTerminalRound(provider) && turnTools.isNotEmpty() &&
                (!offerArtifact || turnTools.any { it.function.name == AgentTurnPolicy.CREATE_ARTIFACT })
            val reviewTools = when {
                reuseTurnTools -> turnTools
                offerArtifact -> listOf(if (capability.outputKind() != null) artifactTool(capability) else artifactToolAuto())
                else -> emptyList()
            }
            working += ApiMessage("assistant", draft)
            working += ApiMessage("system", AnswerReview.directive(offerArtifact))
            toolActivity.tryEmit(ToolActivity(conversationId, TOOL_REVIEWING, started = true))
            try {
                var revising = false
                val review = collectWithRetry(
                    client = client,
                    key = key,
                    request = chatRequest(reviewTools, forcedTool = null, noToolCalls = reuseTurnTools && !offerArtifact),
                    messageId = messageId,
                    allowEmpty = true,
                    recoverInlineToolCalls = offerArtifact,
                    // The draft is on screen; retry notices would overwrite it.
                    showRetryNotices = false,
                    onText = { value ->
                        val visible = ToolCallXml.scrubToolBlocks(value.scrubThinkTags())
                        if (!revising && AnswerReview.classify(visible, complete = false) == AnswerReview.Verdict.REVISION) {
                            revising = true
                        }
                        if (revising) {
                            activeText = visible
                            dao.updateMessageContent(messageId, visible, MessageStatus.STREAMING.name)
                        }
                    }
                )
                addUsage(review.usage)
                // A corrected file replaces the earlier one; a failed replacement keeps it.
                review.toolCalls.lastOrNull { it.function.name == AgentTurnPolicy.CREATE_ARTIFACT }?.let { call ->
                    val detail = ToolActivityDetail.of(call.function.name, call.function.arguments)
                    toolActivity.tryEmit(ToolActivity(conversationId, call.function.name, started = true, detail))
                    try {
                        createArtifact(conversationId, call, capability, outputs)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        Unit
                    } finally {
                        toolActivity.tryEmit(ToolActivity(conversationId, call.function.name, started = false, detail))
                    }
                }
                val revision = review.text.trim()
                return if (AnswerReview.classify(revision, complete = true) == AnswerReview.Verdict.REVISION) revision else {
                    if (revising) dao.updateMessageContent(messageId, draft, MessageStatus.STREAMING.name)
                    activeText = draft
                    draft
                }
            } catch (cancelled: CancellationException) {
                activeText = draft
                throw cancelled
            } catch (_: Throwable) {
                activeText = draft
                dao.updateMessageContent(messageId, draft, MessageStatus.STREAMING.name)
                return draft
            } finally {
                toolActivity.tryEmit(ToolActivity(conversationId, TOOL_REVIEWING, started = false))
            }
        }
        val lastUserText = lastUser?.content.orEmpty()
        // Turn-fixed context + per-round progress feed the pure planner in AgentTurnPolicy,
        // which decides what to offer and what to force. v5.10: built once per turn so the
        // offered tool list (part of the cached prefix) cannot drift between rounds.
        val toolContext = AgentTurnPolicy.ToolContext(
            agentMode = mode == AssistantMode.AGENT,
            voiceMode = voiceMode,
            capabilityAuto = capability == AgentCapability.AUTO,
            artifactCapability = mode == AssistantMode.AGENT && capability.outputKind() != null,
            skillCapability = mode == AssistantMode.AGENT && capability == AgentCapability.SKILL_MAKER,
            codeCapability = mode == AssistantMode.AGENT && capability == AgentCapability.CODE,
            deepResearch = capability == AgentCapability.DEEP_RESEARCH,
            wideSearch = capability == AgentCapability.WIDE_SEARCH,
            searchDepth = conversation.searchDepth.coerceIn(1, 3),
            searchAvailable = settings.searchEnabled && credentials.hasParallelKey(),
            // Voice chat keeps web search on but caps it at one round per turn so the
            // spoken reply stays responsive — anything heavier goes to text mode.
            searchRoundCap = when {
                voiceMode -> 1
                capability == AgentCapability.AUTO -> turnSettings.stepBudget.coerceIn(1, ToolRoundLimiter.HARD_MAXIMUM)
                else -> conversation.searchDepth.coerceIn(1, 3)
            },
            stepBudget = turnSettings.stepBudget,
            // AUTO does not force a tool call on every turn (that would fire a web
            // search on "hi" or "write me a poem"). But when the user's message
            // plausibly depends on fresh / time-sensitive / source-backed facts, AUTO
            // proactively forces the first `parallel_search` round instead of waiting
            // for the model to decide — which it often skips, answering from stale
            // memory. The model can still search on its own for anything this gate
            // misses because `tool_choice` stays "auto" otherwise.
            forceSearchFirst = mode == AssistantMode.AGENT && capability == AgentCapability.AUTO &&
                settings.searchEnabled && credentials.hasParallelKey() && messageLikelyNeedsSearch(lastUserText),
            // AUTO gains media generation tools so it can produce images / video /
            // music on its own. They are gated on keyword detection in the user's
            // latest message so the model is only offered `generate_image` when the
            // user actually asked for an image, etc. — this keeps AUTO from calling
            // `generate_video` on a plain text question (which would hang for minutes
            // on the provider poll). The dedicated Image / Video / Music capabilities
            // keep their direct, no-tool path.
            offerMediaImage = settings.imageModel.isNotBlank() && userWantsImage(lastUserText),
            offerMediaVideo = settings.videoModel.isNotBlank() && userWantsVideo(lastUserText),
            offerMediaAudio = settings.audioModel.isNotBlank() && userWantsAudio(lastUserText),
            offerSkillAuto = userWantsSkill(lastUserText),
            offerCodeAuto = userWantsCodeProject(lastUserText),
            memoryEnabled = settings.memoryEnabled,
            publicWebAvailable = publicWeb != null,
            offerFetch = FetchAllowlist(conversationUrls, userTexts).hasKnownUrls || userWantsFetch(lastUserText),
            offerSchedule = userWantsSchedule(lastUserText) && cronScheduler != null && conversations != null
        )
        if (agentRunningActivity) {
            toolActivity.tryEmit(ToolActivity(conversationId, TOOL_AGENT_RUNNING, started = true))
        }

        try {
            while (true) {
                val messageId = UUID.randomUUID().toString()
                activeId = messageId
                activeText = ""
                dao.upsertMessage(
                    MessageEntity(
                        messageId, conversationId, "ASSISTANT", "", System.currentTimeMillis(), MessageStatus.STREAMING.name,
                        mode = mode.name,
                        capability = if (mode == AssistantMode.AGENT) capability.name else null
                    )
                )

                // Rebuilt every round: URLs a search returned this turn become readable.
                val fetchAllowlist = FetchAllowlist(conversationUrls + citations.map(Citation::url), userTexts)
                val turn = AgentTurnPolicy.TurnProgress(
                    toolRounds = completedToolRounds,
                    searchRounds = completedSearchRounds,
                    nudged = nudgedForTools,
                    terminalStarted = terminalRoundStarted,
                    outputKinds = outputs.map { it.kind }.toSet(),
                    skillCreated = skillCreated,
                    didSearch = didSearch,
                    costCapped = costCapped
                )
                val plan = AgentTurnPolicy.planRound(turn, toolContext)
                if (plan.terminal && !terminalRoundStarted) {
                    // The tool budget or the cost cap ran out: exactly one terminal round without
                    // tool calls so the turn still ends with a written answer instead of a limit notice.
                    terminalRoundStarted = true
                    stoppedByCostCap = plan.terminalDirective == AgentTurnPolicy.COST_CAP_DIRECTIVE
                    plan.terminalDirective?.let { working += ApiMessage("system", it) }
                }
                // v5.9: shrink older search and page results once they crowd the context.
                val compacted = ContextCompactor.compact(working)
                if (compacted !== working) {
                    working.clear()
                    working.addAll(compacted)
                }
                // v5.10: the terminal round keeps the turn's tools and sends tool_choice "none"
                // where the provider accepts it, so its request still hits the cached prefix.
                val sendTools = !plan.terminal || AgentTurnPolicy.keepsToolsOnTerminalRound(provider)
                val tools = if (!sendTools) emptyList() else plan.toolNames.mapNotNull { name ->
                    when (name) {
                        AgentTurnPolicy.SEARCH_PAST_CHATS -> pastChatTool()
                        AgentTurnPolicy.PARALLEL_SEARCH -> parallelTool(conversation.searchWidth.coerceIn(1, 5))
                        AgentTurnPolicy.CREATE_ARTIFACT ->
                            if (capability.outputKind() != null) artifactTool(capability) else artifactToolAuto()
                        AgentTurnPolicy.CREATE_SKILL -> skillTool()
                        AgentTurnPolicy.CREATE_CODE_PROJECT -> codeProjectTool()
                        AgentTurnPolicy.GENERATE_IMAGE -> generateImageTool()
                        AgentTurnPolicy.GENERATE_VIDEO -> generateVideoTool()
                        AgentTurnPolicy.GENERATE_AUDIO -> generateAudioTool()
                        AgentTurnPolicy.GET_CURRENT_TIME -> datetimeTool()
                        AgentTurnPolicy.CALCULATE -> calculateTool()
                        AgentTurnPolicy.REMEMBER_FACT -> rememberFactTool()
                        AgentTurnPolicy.RECALL_MEMORIES -> recallMemoriesTool()
                        AgentTurnPolicy.GET_WEATHER -> weatherTool()
                        AgentTurnPolicy.FETCH_URL -> fetchUrlTool()
                        AgentTurnPolicy.SCHEDULE_TASK -> scheduleTaskTool()
                        AgentTurnPolicy.ASK_USER -> askUserTool()
                        AgentTurnPolicy.UPDATE_PLAN -> updatePlanTool()
                        else -> null
                    }
                }
                if (tools.isNotEmpty()) turnTools = tools
                val request = chatRequest(tools, plan.forcedTool, noToolCalls = plan.terminal)
                val allowEmptyRound = outputs.isNotEmpty() || skillCreated || completedToolRounds > 0 ||
                    freePlanRounds > 0 || plan.terminal
                val onStreamText: suspend (String) -> Unit = { value ->
                    if (firstTokenLatencyMs == null && value.isNotBlank()) {
                        firstTokenLatencyMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)
                    }
                    val visible = ToolCallXml.scrubToolBlocks(value.scrubThinkTags())
                    activeText = visible
                    // Voice: hand the text to speech before the database write, so the first
                    // sentence can start rendering while the rest still streams.
                    if (voiceMode) onVoiceText?.onText(messageId, visible, done = false)
                    dao.updateMessageContent(messageId, visible, MessageStatus.STREAMING.name)
                }
                // Keep tools on the first validation retry (drop only a rejected
                // tool_choice). Stripping tools immediately made Auto narrate
                // "I'll search" and then freeze with nothing actually running.
                var attemptRequest = request
                var streamed: StreamedResult? = null
                var streamError: Throwable? = null
                var streamAttempts = 0
                while (streamed == null && streamAttempts < 3) {
                    streamAttempts++
                    try {
                        streamed = collectWithRetry(
                            client = client,
                            key = key,
                            request = attemptRequest,
                            messageId = messageId,
                            allowEmpty = allowEmptyRound,
                            // The terminal round allows no tool calls; recovering inline XML
                            // tool calls there could resurrect a call the budget already ruled
                            // out and loop forever, so recovery is off for that one round.
                            recoverInlineToolCalls = !plan.terminal,
                            onText = onStreamText
                        )
                    } catch (error: Throwable) {
                        streamError = error
                        val apiError = error as? AssistantApiException
                        if (attemptRequest.tools == null || apiError?.kind != ErrorKind.VALIDATION) throw error
                        dao.updateMessageContent(messageId, "", MessageStatus.STREAMING.name)
                        activeText = ""
                        attemptRequest = MiniMaxToolSafety.nextValidationRetry(attemptRequest)
                    }
                }
                val resolved = streamed ?: throw (streamError ?: AssistantApiException(ErrorKind.UNKNOWN, "The model returned no response."))
                val historyText = resolved.rawText.ifBlank { resolved.text }
                addUsage(resolved.usage)
                // v5.9: once spend reaches the cap, the next planned round is the terminal one.
                if (!costCapped) {
                    costCapped = AgentTurnPolicy.costCapReached(requestCost.takeIf { hasCost }, requestTotalTokens, costCapUsd)
                }

                val calls = resolved.toolCalls
                // v5.7: the model drives the loop. Calls returned -> run them and offer
                // tools again. Text without calls -> that IS the final answer. The only
                // prose check left is one high-precision nudge when the FIRST round
                // narrates a tool without invoking it; and blank text after tool work
                // gets exactly one terminal no-tools round to write the answer.
                // v5.7.1: when the user clearly asked for a file to be created (the
                // high-precision artifact-create gate fired, not merely a format
                // mention) a prose-only first answer is nudged once towards
                // create_artifact instead of ending the turn with no deliverable.
                // Gated on the plan actually offering the tool, so the nudge can
                // never ask for a call the provider cannot see.
                val artifactExpected = userWantsArtifactCreated(lastUserText) &&
                    AgentTurnPolicy.CREATE_ARTIFACT in plan.toolNames &&
                    turn.outputKinds.none { it in ArtifactKinds.fileKinds }
                val toolsCallable = tools.isNotEmpty() && !plan.terminal
                when (AgentTurnPolicy.afterModel(turn, activeText, calls.size, toolsCallable, artifactExpected)) {
                    AgentTurnPolicy.After.NUDGE_ONCE -> {
                        nudgedForTools = true
                        dao.deleteMessage(messageId)
                        activeId = null
                        val directive = if (artifactExpected && !AgentTurnPolicy.strictToolPromise(activeText)) {
                            AgentTurnPolicy.ARTIFACT_NUDGE_DIRECTIVE
                        } else {
                            AgentTurnPolicy.NUDGE_DIRECTIVE
                        }
                        working += ApiMessage("system", directive)
                        continue
                    }
                    AgentTurnPolicy.After.START_TERMINAL -> {
                        terminalRoundStarted = true
                        dao.deleteMessage(messageId)
                        activeId = null
                        working += ApiMessage("system", AgentTurnPolicy.TERMINAL_DIRECTIVE)
                        continue
                    }
                    AgentTurnPolicy.After.FINISH -> {
                        var finalText = activeText.ifBlank {
                            when {
                                outputs.isNotEmpty() -> "Created ${outputs.first().title}. Use the output card below to save or open it."
                                skillCreated -> "The reusable skill was created and saved locally."
                                completedToolRounds > 0 -> {
                                    if (didSearch) {
                                        "I ran the searches above but the model did not stream a summary back. Tap regenerate, or ask me to summarise the sources I found."
                                    } else {
                                        "I finished the tool work above but the model did not stream a summary back. Tap regenerate, or ask me to continue."
                                    }
                                }
                                else -> throw AssistantApiException(
                                    ErrorKind.MODEL_UNAVAILABLE,
                                    "The selected model returned no text. Try a different model or lower its reasoning effort."
                                )
                            }
                        }
                        val reviewInput = AnswerReview.Input(
                            agentMode = mode == AssistantMode.AGENT,
                            voiceMode = voiceMode,
                            enabled = turnSettings.review,
                            capability = capability,
                            didSearch = didSearch,
                            didFetch = didFetch,
                            outputKinds = outputs.map { it.kind }.toSet(),
                            draft = activeText,
                            alreadyReviewed = reviewed,
                            costCapped = costCapped
                        )
                        if (AnswerReview.shouldReview(reviewInput)) {
                            reviewed = true
                            finalText = reviewDraft(messageId, activeText)
                        }
                        if (stoppedByCostCap) finalText += "\n\n" + AgentTurnPolicy.costCapNote(costCapUsd)
                        if (voiceMode) onVoiceText?.onText(messageId, finalText, done = true)
                        val totalGenerationTimeMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)
                        dao.finishMessage(
                            messageId, finalText, MessageStatus.COMPLETE.name, json.encodeToString(citations),
                            requestPromptTokens.takeIf { hasUsage }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            requestCompletionTokens.takeIf { hasUsage }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            requestTotalTokens.takeIf { hasUsage }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            requestCost.takeIf { hasCost },
                            json.encodeToString(outputs),
                            cachedInputTokens = requestCachedInputTokens.takeIf { hasUsage }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            firstTokenLatencyMs = firstTokenLatencyMs,
                            totalGenerationTimeMs = totalGenerationTimeMs
                        )
                        dao.touchConversation(conversationId, System.currentTimeMillis())
                        return finalText
                    }
                    AgentTurnPolicy.After.RUN_CALLS -> Unit // fall through to tool execution
                }

                dao.deleteMessage(messageId)
                activeId = null
                if (!ToolRoundLimiter.canRun(completedToolRounds, turnSettings.stepBudget)) {
                    // Paranoia: the planner goes terminal at the budget, so calls here mean
                    // a recovered inline call slipped through. Answer with what we have
                    // instead of executing past the configured limit.
                    terminalRoundStarted = true
                    working += ApiMessage("system", AgentTurnPolicy.TERMINAL_DIRECTIVE)
                    continue
                }
                if (AgentTurnPolicy.roundCountsTowardBudget(calls.map { it.function.name }, freePlanRounds)) {
                    completedToolRounds++
                } else {
                    freePlanRounds++
                }
                working += ApiMessage("assistant", historyText.ifBlank { null }, toolCalls = calls)

                coroutineScope {
                // Network lookups (search, page reads, weather) start together on the IO
                // dispatcher: a search plus two page reads cost one wait instead of three, and
                // blocking HTTP never runs on the main thread. Their results are applied below
                // strictly in call order, which keeps citation numbers, tool rows and the
                // history deterministic.
                val searchSession = sessionId
                // v5.10: tools stay offered for the whole turn, so calls past a per-turn limit
                // (search cap, a second image of one kind, a second skill) are refused here.
                val refusals = calls.map { AgentTurnPolicy.refusal(it.function.name, turn, toolContext) }
                // v5.11: chip detail per call (first query, host and path, place), sent with
                // both the start and the stop event so concurrent calls keep separate chips.
                val details = calls.map { ToolActivityDetail.of(it.function.name, it.function.arguments) }
                val lookups: List<Deferred<Result<Lookup>>?> = calls.mapIndexed { index, call ->
                    if (call.function.name !in LOOKUP_TOOLS || refusals[index] != null) return@mapIndexed null
                    toolActivity.tryEmit(ToolActivity(conversationId, call.function.name, started = true, details[index]))
                    // The voice UI plays a filler phrase so the user hears that a search is running.
                    if (voiceMode && call.function.name == AgentTurnPolicy.PARALLEL_SEARCH) {
                        voiceFillerRequest.tryEmit("checking online")
                    }
                    async(Dispatchers.IO) {
                        runCatching {
                            lookup(call, conversation.searchWidth.coerceIn(1, 5), searchSession, model, settings, fetchAllowlist)
                        }
                    }
                }
                val finished = BooleanArray(calls.size)
                try {
                for ((index, call) in calls.withIndex()) {
                    val pendingLookup = lookups[index]
                    if (pendingLookup == null) {
                        toolActivity.tryEmit(ToolActivity(conversationId, call.function.name, started = true, details[index]))
                    }
                    try {
                        refusals[index]?.let { reason -> throw AssistantApiException(ErrorKind.VALIDATION, reason) }
                        when (call.function.name) {
                        TOOL_SEARCH_PAST_CHATS -> {
                            val args = validatePastChatArgs(call.function.arguments)
                            val hits = searchPastChats(conversationId, args.query, limit = 6)
                            val structured = buildJsonObject {
                                put("query", args.query)
                                put("match_count", hits.size)
                                putJsonArray("matches") {
                                    hits.forEach { hit ->
                                        add(buildJsonObject {
                                            put("chat", hit.conversationTitle)
                                            put("role", hit.role)
                                            put("snippet", hit.snippet)
                                        })
                                    }
                                }
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "parallel_search" -> {
                            val search = pendingLookup.awaitLookup() as Lookup.Search
                            val result = search.response
                            didSearch = true
                            completedSearchRounds++
                            sessionId = result.sessionId
                            dao.updateSearchSession(conversationId, sessionId)
                            val mapped = CitationMapper.map(result.results)
                            mapped.forEach { candidate -> if (citations.none { it.url == candidate.url }) citations += candidate }
                            val structured = buildJsonObject {
                                put("search_id", result.searchId)
                                put("session_id", result.sessionId)
                                // v5.11: the queries (and objective) are kept for the work log;
                                // the model sees them too, which is harmless.
                                putJsonArray("queries") { search.queries.forEach { add(JsonPrimitive(it)) } }
                                search.objective?.let { put("objective", it.take(300)) }
                                putJsonArray("sources") {
                                    result.results.forEach { source ->
                                        val citationNumber = citations.indexOfFirst { it.url == source.url }.takeIf { it >= 0 }?.plus(1)
                                        add(buildJsonObject {
                                            citationNumber?.let { put("citation", it) }
                                            put("title", source.title)
                                            put("url", source.url)
                                            source.publishDate?.let { put("publish_date", it) }
                                            put("excerpts", buildJsonArray { source.excerpts.forEach { add(JsonPrimitive(it)) } })
                                        })
                                    }
                                }
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "create_artifact" -> {
                            val result = createArtifact(conversationId, call, capability, outputs)
                            working += ApiMessage("tool", result, call.id, call.function.name)
                        }

                        "create_skill" -> {
                            val args = validateSkill(call.function.arguments)
                            val now = System.currentTimeMillis()
                            val skill = SkillEntity(
                                id = UUID.randomUUID().toString(),
                                name = args.name,
                                description = args.description,
                                instructions = args.instructions,
                                examplePrompts = args.examplePrompts.joinToString("\n"),
                                enabled = true,
                                createdAt = now,
                                updatedAt = now
                            )
                            dao.upsertSkill(skill)
                            skillCreated = true
                            val result = buildJsonObject {
                                put("status", "saved_locally")
                                put("skill_id", skill.id)
                                put("name", skill.name)
                                put("enabled", true)
                            }.toString()
                            saveToolMessage(conversationId, call, result)
                            working += ApiMessage("tool", result, call.id, call.function.name)
                        }

                        "create_code_project" -> {
                            val args = validateCodeProject(call.function.arguments)
                            val file = CodeProjectGenerator.write(outputStore.newFile("zip"), args.files)
                            val title = args.title.trim().take(120)
                            val stem = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifBlank { "kryzz-app" }
                            val output = GeneratedOutput(
                                UUID.randomUUID().toString(), OutputKind.CODE, title, "$stem.zip", "application/zip",
                                content = args.description.trim().take(600).ifBlank { "Complete multi-file code project" },
                                localPath = file.absolutePath
                            )
                            outputs.clear()
                            outputs += output
                            val result = buildJsonObject {
                                put("status", "created_locally")
                                put("title", title)
                                put("file_name", output.fileName)
                                put("file_count", args.files.size)
                            }.toString()
                            saveToolMessage(conversationId, call, result)
                            working += ApiMessage("tool", result, call.id, call.function.name)
                        }

                        "generate_image" -> {
                            val args = validateSimplePromptArgs(call.function.arguments, "generate_image")
                            val output = produceImageOutput(args.prompt)
                            outputs += output
                            val result = buildJsonObject {
                                put("status", "created_locally")
                                put("type", "image")
                                put("title", output.title)
                                put("file_name", output.fileName)
                            }.toString()
                            saveToolMessage(conversationId, call, result)
                            working += ApiMessage("tool", result, call.id, call.function.name)
                        }

                        "generate_video" -> {
                            val args = validateSimplePromptArgs(call.function.arguments, "generate_video")
                            // Video generation polls the provider for up to several minutes.
                            // Keep a streaming placeholder alive so the UI shows the
                            // ThinkingIndicator instead of an empty chat while it runs.
                            val heartbeatId = UUID.randomUUID().toString()
                            dao.upsertMessage(
                                MessageEntity(heartbeatId, conversationId, "ASSISTANT", "", System.currentTimeMillis(), MessageStatus.STREAMING.name, mode = mode.name, capability = if (mode == AssistantMode.AGENT) capability.name else null)
                            )
                            try {
                                val output = produceVideoOutput(args.prompt, heartbeatId)
                                outputs += output
                                val result = buildJsonObject {
                                    put("status", "created_locally")
                                    put("type", "video")
                                    put("title", output.title)
                                    put("file_name", output.fileName)
                                }.toString()
                                saveToolMessage(conversationId, call, result)
                                working += ApiMessage("tool", result, call.id, call.function.name)
                            } finally {
                                dao.deleteMessage(heartbeatId)
                            }
                        }

                        "generate_audio" -> {
                            val args = validateSimplePromptArgs(call.function.arguments, "generate_audio")
                            // Music / speech generation can poll for up to a few minutes.
                            // Keep a streaming placeholder alive so the UI shows progress.
                            val heartbeatId = UUID.randomUUID().toString()
                            dao.upsertMessage(
                                MessageEntity(heartbeatId, conversationId, "ASSISTANT", "", System.currentTimeMillis(), MessageStatus.STREAMING.name, mode = mode.name, capability = if (mode == AssistantMode.AGENT) capability.name else null)
                            )
                            try {
                                val output = produceAudioOutput(args.prompt, heartbeatId)
                                outputs += output
                                val result = buildJsonObject {
                                    put("status", "created_locally")
                                    put("type", "audio")
                                    put("title", output.title)
                                    put("file_name", output.fileName)
                                }.toString()
                                saveToolMessage(conversationId, call, result)
                                working += ApiMessage("tool", result, call.id, call.function.name)
                            } finally {
                                dao.deleteMessage(heartbeatId)
                            }
                        }

                        "get_current_time" -> {
                            val zoneArg = MiniMaxToolSafety.timezoneArg(call.function.arguments)
                            val zone = if (zoneArg.equals("local", ignoreCase = true)) {
                                java.time.ZoneId.systemDefault()
                            } else {
                                runCatching { java.time.ZoneId.of(zoneArg) }.getOrElse { java.time.ZoneId.systemDefault() }
                            }
                            val clock = LocalClock.snapshot(zone = zone)
                            val structured = buildJsonObject {
                                put("iso_date", clock.isoDate)
                                put("iso_time", clock.isoTime)
                                put("weekday", clock.weekday)
                                put("timezone", clock.timezone)
                                put("utc_offset", clock.utcOffset)
                                put("unix_ms", clock.unixMs)
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "calculate" -> {
                            val args = validateCalculateArgs(call.function.arguments)
                            val value = LocalCalculator.evaluate(args.expression)
                            val formatted = if (value % 1.0 == 0.0 && value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
                                value.toLong().toString()
                            } else {
                                value.toString()
                            }
                            val structured = buildJsonObject {
                                put("expression", args.expression)
                                put("result", formatted)
                                put("number", value)
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "get_weather" -> {
                            val report = (pendingLookup.awaitLookup() as Lookup.Weather).report
                            val structured = buildJsonObject {
                                put("place", report.place)
                                put("latitude", report.latitude)
                                put("longitude", report.longitude)
                                put("timezone", report.timezone)
                                put("current", report.current)
                                put("days", buildJsonArray { report.days.forEach { add(JsonPrimitive(it)) } })
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "fetch_url" -> {
                            val page = (pendingLookup.awaitLookup() as Lookup.Page).page
                            didFetch = true
                            val structured = buildJsonObject {
                                put("url", page.url)
                                put("title", page.title)
                                put("truncated", page.truncated)
                                put("text", page.text)
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "remember_fact" -> {
                            if (!settings.memoryEnabled) {
                                throw AssistantApiException(ErrorKind.VALIDATION, "Memory is turned off in Settings.")
                            }
                            val args = validateRememberArgs(call.function.arguments)
                            val saved = memoryRepository.add(args.fact, args.category)
                            val structured = buildJsonObject {
                                put("status", if (saved) "saved" else "not_saved")
                                put("fact", args.fact)
                                put("category", args.category.name)
                                if (!saved) put("reason", "duplicate or invalid length")
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "recall_memories" -> {
                            val args = validateRecallArgs(call.function.arguments)
                            val hits = MemoryEngine.retrieve(args.query, memoryRepository.all(), limit = 6)
                            val structured = buildJsonObject {
                                put("query", args.query)
                                put("match_count", hits.size)
                                putJsonArray("memories") {
                                    hits.forEach { memory ->
                                        add(buildJsonObject {
                                            put("content", memory.content)
                                            put("category", memory.category)
                                        })
                                    }
                                }
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "schedule_task" -> {
                            val scheduler = cronScheduler
                            val repo = conversations
                            if (scheduler == null || repo == null) {
                                throw AssistantApiException(ErrorKind.VALIDATION, "Scheduling is not available.")
                            }
                            val args = validateScheduleArgs(call.function.arguments)
                            val conversationForTask = repo.createConversation()
                            repo.rename(conversationForTask, "⏰ ${args.title}")
                            val encodedDays = if (args.recurrence == CronRecurrence.WEEKLY) {
                                CronSchedulePresets.encodeDays(args.daysOfWeek)
                            } else {
                                null
                            }
                            val task = ScheduledTaskEntity(
                                id = UUID.randomUUID().toString(),
                                title = args.title,
                                prompt = args.prompt,
                                intervalMinutes = 1_440L,
                                conversationId = conversationForTask,
                                createdAt = System.currentTimeMillis(),
                                recurrence = args.recurrence.name,
                                hourOfDay = args.hour,
                                minuteOfHour = args.minute,
                                dayOfWeek = encodedDays?.split(',')?.firstOrNull()?.toIntOrNull(),
                                daysOfWeek = encodedDays
                            )
                            dao.upsertScheduledTask(task)
                            scheduler.schedule(task)
                            val next = CronSchedulePresets.nextRunAt(task)
                            val structured = buildJsonObject {
                                put("status", "scheduled")
                                put("title", task.title)
                                put("schedule", CronSchedulePresets.label(task))
                                put("conversation_id", conversationForTask)
                                if (next != null) put("next_run_unix_ms", next)
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        "ask_user" -> {
                            val question = validateAskUser(call.function.arguments)
                            if (pendingQuestion.value != null) {
                                throw AssistantApiException(
                                    ErrorKind.VALIDATION,
                                    "Another question card is already waiting for the user. Continue without asking, or ask again after it is answered."
                                )
                            }
                            val deferred = CompletableDeferred<String?>()
                            pendingQuestion.value = PendingQuestion(conversationId, question, deferred)
                            // Suspend the tool call until the UI delivers the answer (or the
                            // user dismisses the card). Cancellation of the generation job
                            // propagates through await() and clears the card via finally.
                            val answer = try {
                                deferred.await()
                            } finally {
                                pendingQuestion.value = null
                            }
                            val structured = if (answer != null) {
                                buildJsonObject {
                                    put("status", "answered")
                                    put("question", question.question)
                                    put("answer", answer)
                                }.toString()
                            } else {
                                buildJsonObject {
                                    put("status", "skipped")
                                    put("question", question.question)
                                    put("note", "The user dismissed the question without answering. Continue with your best judgment and do not ask the same question again.")
                                }.toString()
                            }
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        AgentTurnPolicy.UPDATE_PLAN -> {
                            val steps = AgentPlanParser.parse(call.function.arguments)
                                ?: throw AssistantApiException(
                                    ErrorKind.VALIDATION,
                                    "update_plan needs a steps array of 1-${AgentPlanParser.MAX_STEPS} items, each with a short title and a status of pending, in_progress, or done."
                                )
                            val updated = AgentPlan(conversationId, steps)
                            agentPlan.value = updated
                            val structured = buildJsonObject {
                                put("status", "plan_updated")
                                put("done", updated.doneCount)
                                put("remaining", steps.size - updated.doneCount)
                            }.toString()
                            saveToolMessage(conversationId, call, structured)
                            working += ApiMessage("tool", structured, call.id, call.function.name)
                        }

                        else -> {
                            val unsupported = "Unsupported tool: ${call.function.name}"
                            working += ApiMessage("tool", unsupported, call.id, call.function.name)
                        }
                    }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (toolError: Throwable) {
                        // A tool failed (bad args from the model, network blip, provider
                        // timeout, missing key). Feed the failure back to the model as the
                        // tool result so the agent can recover — retry with different
                        // arguments, fall back to memory, or tell the user it could not
                        // complete that step — instead of aborting the whole turn. Before
                        // this, any tool throw erased the just-streamed bubble and left the
                        // chat with only a transient error banner, which made Agent mode
                        // feel like it "can't call tools".
                        val friendly = when (toolError) {
                            is AssistantApiException -> toolError.message
                            is IOException -> "Network error during ${call.function.name}: ${toolError.message ?: toolError.javaClass.simpleName}"
                            else -> null
                        } ?: "Tool ${call.function.name} failed."
                        val errorResult = buildJsonObject {
                            put("status", "error")
                            put("error", friendly.take(500))
                            // v5.11: what the call was about, for the answer's work log.
                            details[index]?.let { put("detail", it) }
                        }.toString()
                        saveToolMessage(conversationId, call, errorResult)
                        working += ApiMessage("tool", errorResult, call.id, call.function.name)
                    } finally {
                        finished[index] = true
                        toolActivity.tryEmit(ToolActivity(conversationId, call.function.name, started = false, details[index]))
                    }
                }
                } finally {
                    // A cancelled turn can leave lookups that were started but never applied;
                    // close their chips so nothing keeps spinning.
                    lookups.forEachIndexed { index, deferred ->
                        if (deferred != null && !finished[index]) {
                            toolActivity.tryEmit(ToolActivity(conversationId, calls[index].function.name, started = false, details[index]))
                        }
                    }
                }
                }
            }
        } catch (cancelled: CancellationException) {
            // NonCancellable: this coroutine is already cancelled, so a plain suspend write would
            // throw at once and leave the bubble stuck as streaming (common when a voice reply is
            // interrupted while the model is still writing).
            activeId?.let { withContext(NonCancellable) { dao.updateMessageContent(it, activeText, MessageStatus.CANCELLED.name) } }
            throw cancelled
        } catch (error: Throwable) {
            val friendly = (error as? AssistantApiException)?.message ?: "Something went wrong. Check your connection and try again."
            // activeId is null after the streaming placeholder was deleted for a tool
            // round. Don't let that swallow the error: restore a visible error row so the
            // user sees what happened instead of a chat that just stops mid-turn.
            val targetId = activeId ?: UUID.randomUUID().toString()
            val content = if (activeText.isBlank()) friendly else "$activeText\n\n$friendly"
            dao.finishMessage(targetId, content, MessageStatus.ERROR.name, "[]", null, null, null, null)
            throw error
        } finally {
            if (agentRunningActivity) {
                toolActivity.tryEmit(ToolActivity(conversationId, TOOL_AGENT_RUNNING, started = false))
            }
            if (agentPlan.value?.conversationId == conversationId) agentPlan.value = null
        }
    }

    private suspend fun generateImage(conversationId: String, prompt: String) {
        val id = UUID.randomUUID().toString()
        dao.upsertMessage(
            MessageEntity(id, conversationId, "ASSISTANT", "", System.currentTimeMillis(), MessageStatus.STREAMING.name, mode = AssistantMode.AGENT.name, capability = AgentCapability.IMAGE.name)
        )
        toolActivity.tryEmit(ToolActivity(conversationId, TOOL_IMAGE_CREATION, started = true))
        try {
            val output = produceImageOutput(prompt)
            dao.finishMessage(
                id, "Created your image with ${preferences.state.first().imageModel}.", MessageStatus.COMPLETE.name, "[]",
                null, null, null, null,
                json.encodeToString(listOf(output))
            )
            dao.touchConversation(conversationId, System.currentTimeMillis())
        } catch (cancelled: CancellationException) {
            dao.updateMessageContent(id, "Image generation stopped.", MessageStatus.CANCELLED.name)
            throw cancelled
        } catch (error: Throwable) {
            failDirectGeneration(id, error)
            throw error
        } finally {
            toolActivity.tryEmit(ToolActivity(conversationId, TOOL_IMAGE_CREATION, started = false))
        }
    }

    /** Core image generation, factored out so the AUTO agent's `generate_image` tool and the
     *  dedicated Image capability share one path. Returns the materialised output; the caller
     *  is responsible for attaching it to a message. */
    private suspend fun produceImageOutput(prompt: String): GeneratedOutput {
        val settings = preferences.state.first()
        val model = settings.imageModel
        if (model.isBlank()) throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "Choose an Image model on the Models screen before using Image generation.")
        val useMiniMax = settings.chatProvider == ChatProvider.MINIMAX || isMiniMaxImageModel(model)
        val key = (if (useMiniMax) credentials.minimaxKey() else credentials.openRouterKey())
            ?: throw AssistantApiException(
                ErrorKind.INVALID_KEY,
                if (useMiniMax) "Add a MiniMax API key in Settings before generating an image."
                else "Add an OpenRouter API key in Settings before generating an image."
            )
        val (file, mime, extension) = if (useMiniMax) {
            val result = minimax.generateImage(key, miniMaxImageRequest(model, prompt))
            when (val payload = result.requireImagePayload()) {
                is MiniMaxImagePayload.Base64 -> {
                    val bytes = Base64.decode(payload.value, Base64.DEFAULT)
                    val out = outputStore.newFile("jpg")
                    out.writeBytes(bytes)
                    Triple(out, "image/jpeg", "jpg")
                }
                is MiniMaxImagePayload.Url -> {
                    val out = outputStore.newFile("jpg")
                    val downloadedMime = try {
                        minimax.downloadVideo(payload.url, out)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        out.delete()
                        throw AssistantApiException(ErrorKind.NETWORK, "The MiniMax image finished, but it could not be downloaded.")
                    }
                    val imgMime = downloadedMime.substringBefore(';').ifBlank { "image/jpeg" }
                    val ext = when (imgMime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                    Triple(out, imgMime, ext)
                }
            }
        } else {
            val result = openRouter.generateImage(key, ImageGenerationRequest(model, prompt))
            val image = result.data.firstOrNull() ?: throw AssistantApiException(ErrorKind.UNKNOWN, "The image model returned no image.")
            val imgMime = image.mediaType ?: "image/png"
            val ext = when (imgMime) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/svg+xml" -> "svg"; else -> "png" }
            Triple(outputStore.saveBase64(image.base64, ext), imgMime, ext)
        }
        return GeneratedOutput(UUID.randomUUID().toString(), OutputKind.IMAGE, "Generated image", "kryzz-image.$extension", mime, localPath = file.absolutePath)
    }

    private suspend fun generateVideo(conversationId: String, prompt: String) {
        val id = UUID.randomUUID().toString()
        dao.upsertMessage(
            MessageEntity(id, conversationId, "ASSISTANT", "", System.currentTimeMillis(), MessageStatus.STREAMING.name, mode = AssistantMode.AGENT.name, capability = AgentCapability.VIDEO.name)
        )
        toolActivity.tryEmit(ToolActivity(conversationId, TOOL_VIDEO_CREATION, started = true))
        try {
            val output = produceVideoOutput(prompt, id)
            dao.finishMessage(
                id, "Created your video with ${preferences.state.first().videoModel}.", MessageStatus.COMPLETE.name, "[]",
                null, null, null, null,
                json.encodeToString(listOf(output))
            )
            dao.touchConversation(conversationId, System.currentTimeMillis())
        } catch (cancelled: CancellationException) {
            dao.updateMessageContent(id, "Video generation stopped. The provider may still be processing the submitted job.", MessageStatus.CANCELLED.name)
            throw cancelled
        } catch (error: Throwable) {
            failDirectGeneration(id, error)
            throw error
        } finally {
            toolActivity.tryEmit(ToolActivity(conversationId, TOOL_VIDEO_CREATION, started = false))
        }
    }

    /** Core video generation. [heartbeatMessageId] is kept streaming so the UI shows the
     *  ThinkingIndicator while the provider polls; pass null when called from a tool. */
    private suspend fun produceVideoOutput(prompt: String, heartbeatMessageId: String? = null): GeneratedOutput {
        val settings = preferences.state.first()
        val model = settings.videoModel
        if (model.isBlank()) throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "Choose a Video model on the Models screen before using Video generation.")
        val useMiniMax = settings.chatProvider == ChatProvider.MINIMAX || isMiniMaxVideoModel(model)
        val key = (if (useMiniMax) credentials.minimaxKey() else credentials.openRouterKey())
            ?: throw AssistantApiException(
                ErrorKind.INVALID_KEY,
                if (useMiniMax) "Add a MiniMax API key in Settings before generating a video."
                else "Add an OpenRouter API key in Settings before generating a video."
            )
        val (localPath, mime, remote) = if (useMiniMax) {
            val submit = minimax.submitVideo(
                key,
                MiniMaxVideoRequest(
                    model = bareMiniMaxModel(model),
                    content = listOf(MiniMaxVideoContentItem(type = "text", text = prompt)),
                    duration = 5,
                    resolution = "2K",
                    ratio = "16:9"
                )
            )
            val taskId = submit.taskId ?: throw AssistantApiException(ErrorKind.UNKNOWN, "MiniMax did not return a video task ID.")
            val startedAt = System.currentTimeMillis()
            var task: ai.daylight.assistant.data.remote.MiniMaxVideoTask? = null
            while (true) {
                if (System.currentTimeMillis() - startedAt > 10 * 60_000L) {
                    throw AssistantApiException(ErrorKind.TIMEOUT, "Video generation is still processing after 10 minutes. Check your MiniMax activity before retrying.")
                }
                // Keep content empty so the ThinkingIndicator animation shows
                // instead of a static "Generating video…" string.
                heartbeatMessageId?.let { dao.updateMessageContent(it, "", MessageStatus.STREAMING.name) }
                delay(4_000)
                val status = minimax.queryVideo(key, taskId)
                status.baseResp.throwIfFailed("The MiniMax video task failed.")
                task = status.task
                val s = task?.status?.lowercase().orEmpty()
                if (s in setOf("succeeded", "failed", "cancelled")) break
            }
            if (task?.status?.lowercase() != "succeeded") {
                throw AssistantApiException(ErrorKind.UNKNOWN, task?.error ?: "The MiniMax video provider could not complete this generation.")
            }
            val url = task.content?.url
                ?: throw AssistantApiException(ErrorKind.UNKNOWN, "The MiniMax video task succeeded but returned no download URL.")
            val file = outputStore.newFile("mp4")
            val downloadedMime = try {
                minimax.downloadVideo(url, file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                file.delete()
                null
            }
            Triple(file.absolutePath, downloadedMime ?: "video/mp4", url)
        } else {
            var job = openRouter.submitVideo(key, VideoGenerationRequest(model, prompt))
            val startedAt = System.currentTimeMillis()
            while (job.status.lowercase() !in setOf("completed", "failed", "cancelled")) {
                if (System.currentTimeMillis() - startedAt > 10 * 60_000L) {
                    throw AssistantApiException(ErrorKind.TIMEOUT, "Video generation is still processing after 10 minutes. Check your OpenRouter activity before retrying.")
                }
                heartbeatMessageId?.let { dao.updateMessageContent(it, "", MessageStatus.STREAMING.name) }
                delay(4_000)
                job = openRouter.videoStatus(key, job.id)
            }
            if (job.status.lowercase() != "completed") {
                throw AssistantApiException(ErrorKind.UNKNOWN, job.error ?: "The video provider could not complete this generation.")
            }
            val file = outputStore.newFile("mp4")
            val (path, downloadedMime) = try {
                file.absolutePath to openRouter.downloadVideo(key, job.id, file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                file.delete()
                null to "video/mp4"
            }
            Triple(path, downloadedMime, job.unsignedUrls.firstOrNull())
        }
        if (localPath == null && remote == null) throw AssistantApiException(ErrorKind.NETWORK, "The video finished, but it could not be downloaded.")
        return GeneratedOutput(UUID.randomUUID().toString(), OutputKind.VIDEO, "Generated video", "kryzz-video.mp4", mime, localPath = localPath, remoteUrl = remote)
    }

    private suspend fun generateAudio(conversationId: String, input: String) {
        val id = UUID.randomUUID().toString()
        dao.upsertMessage(
            MessageEntity(id, conversationId, "ASSISTANT", "", System.currentTimeMillis(), MessageStatus.STREAMING.name, mode = AssistantMode.AGENT.name, capability = AgentCapability.AUDIO.name)
        )
        toolActivity.tryEmit(ToolActivity(conversationId, TOOL_AUDIO_CREATION, started = true))
        try {
            val output = produceAudioOutput(input, id)
            dao.finishMessage(
                id, "Created your audio with ${preferences.state.first().audioModel}.", MessageStatus.COMPLETE.name, "[]",
                null, null, null, null, json.encodeToString(listOf(output))
            )
            dao.touchConversation(conversationId, System.currentTimeMillis())
        } catch (cancelled: CancellationException) {
            dao.updateMessageContent(id, "Audio creation stopped.", MessageStatus.CANCELLED.name)
            throw cancelled
        } catch (error: Throwable) {
            failDirectGeneration(id, error)
            throw error
        } finally {
            toolActivity.tryEmit(ToolActivity(conversationId, TOOL_AUDIO_CREATION, started = false))
        }
    }

    /** Core audio/music generation. MiniMax music is asynchronous (submit → poll
     *  /v1/query/music_generation), so we poll until the audio URL is ready or the task
     *  fails / times out. [heartbeatMessageId] keeps the UI ThinkingIndicator alive while
     *  polling; pass null when called from a tool. */
    private suspend fun produceAudioOutput(input: String, heartbeatMessageId: String? = null): GeneratedOutput {
        val settings = preferences.state.first()
        val model = settings.audioModel
        if (model.isBlank()) throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "Choose an Audio model on the Models screen before using Audio creation.")
        val useMiniMax = model.startsWith("music-", ignoreCase = true) || model.startsWith("speech-", ignoreCase = true)
        val key = (if (useMiniMax) credentials.minimaxKey() else credentials.openRouterKey())
            ?: throw AssistantApiException(
                ErrorKind.INVALID_KEY,
                if (useMiniMax) "Add a MiniMax API key in Settings before creating audio."
                else "Add an OpenRouter API key in Settings before creating audio."
            )
        val file = outputStore.newFile("mp3")
        val mime = if (useMiniMax) {
            if (model.startsWith("music-", ignoreCase = true)) {
                produceMiniMaxMusic(key, model, input, file, heartbeatMessageId)
            } else {
                val result = minimax.generateSpeech(
                    key,
                    MiniMaxTtsRequest(model = model, text = input.take(10_000))
                )
                val url = result.data?.audioUrl
                val base64 = result.data?.audioBase64
                when {
                    url != null -> minimax.downloadVideo(url, file)
                    base64 != null -> file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
                    else -> throw AssistantApiException(ErrorKind.UNKNOWN, "The MiniMax speech model returned no audio.")
                }
            }
            "audio/mpeg"
        } else {
            openRouter.generateSpeech(
                key,
                SpeechGenerationRequest(model = model, input = input.take(4_096), voice = "nova", responseFormat = "mp3", speed = 1.0),
                file
            )
        }
        return GeneratedOutput(
            UUID.randomUUID().toString(), OutputKind.AUDIO, "Generated audio", "kryzz-audio.mp3", mime,
            localPath = file.absolutePath
        )
    }

    /**
     * MiniMax music generation is async: /v1/music_generation returns a task id (in
     * `data.audio`) rather than a finished URL. Poll /v1/query/music_generation until the
     * `audio_url` is present, the task reports failure, or a 5-minute timeout is hit.
     * Falls back to a synchronous audio URL if the provider already returned one.
     */
    private suspend fun produceMiniMaxMusic(
        key: String,
        model: String,
        prompt: String,
        destination: File,
        heartbeatMessageId: String?
    ): String {
        val result = minimax.generateMusic(
            key,
            MiniMaxMusicRequest(
                model = model,
                prompt = prompt.take(2_000),
                isInstrumental = true,
                outputFormat = "url"
            )
        )
        // Synchronous fast path: some MiniMax music models return the URL immediately.
        result.data?.audioUrl?.takeIf { it.isNotBlank() }?.let { url ->
            return minimax.downloadVideo(url, destination)
        }
        // Async path: poll the query endpoint with the task id from `data.audio`.
        val taskId = result.data?.audio?.takeIf { it.isNotBlank() }
            ?: throw AssistantApiException(ErrorKind.UNKNOWN, "The MiniMax music model returned no task id.")
        val startedAt = System.currentTimeMillis()
        while (true) {
            if (System.currentTimeMillis() - startedAt > 5 * 60_000L) {
                throw AssistantApiException(ErrorKind.TIMEOUT, "Music generation is still processing after 5 minutes. Check your MiniMax activity before retrying.")
            }
            heartbeatMessageId?.let { dao.updateMessageContent(it, "", MessageStatus.STREAMING.name) }
            delay(4_000)
            val status = minimax.queryMusic(key, taskId)
            val base = status.baseResp
            val code = base?.statusCode ?: 0
            if (code != 0 && code != 1) {
                throw AssistantApiException(ErrorKind.UNKNOWN, base?.statusMsg ?: "The MiniMax music task failed ($code).")
            }
            val url = status.data?.audioUrl?.takeIf { it.isNotBlank() }
            if (url != null) return minimax.downloadVideo(url, destination)
            // Still processing; keep polling.
        }
    }

    private suspend fun failDirectGeneration(messageId: String, error: Throwable) {
        val friendly = (error as? AssistantApiException)?.message ?: "Generation failed. Check the selected model and provider balance."
        dao.finishMessage(messageId, friendly, MessageStatus.ERROR.name, "[]", null, null, null, null)
    }

    private suspend fun collectWithRetry(
        client: TextProviderClient,
        key: String,
        request: ChatRequest,
        messageId: String,
        allowEmpty: Boolean = false,
        recoverInlineToolCalls: Boolean = true,
        showRetryNotices: Boolean = true,
        onText: suspend (String) -> Unit
    ): StreamedResult {
        var attempt = 0
        var accumulatedUsage: Usage? = null
        while (true) {
            val calls = mutableMapOf<Int, MutableCall>()
            var text = ""
            var usage: Usage? = null
            var failure: AssistantApiException? = null
            client.stream(key, request).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        text = accumulateStreamText(text, event.text)
                        event.toolCalls.forEach { delta ->
                            val call = calls.getOrPut(delta.index) { MutableCall() }
                            delta.id?.let { call.id = it }
                            delta.function?.name?.let { call.name += it }
                            delta.function?.arguments?.let { call.arguments += it }
                        }
                        if (event.text.isNotEmpty()) onText(text)
                    }
                    is StreamEvent.UsageUpdate -> usage = event.usage
                    is StreamEvent.Failure -> failure = event.error
                    StreamEvent.Done -> Unit
                }
            }
            accumulatedUsage = mergeUsage(accumulatedUsage, usage)
            val error = failure
            val wait = error?.retryAfterSeconds
            if (error?.kind == ErrorKind.RATE_LIMIT && text.isEmpty() && attempt == 0 && wait != null && wait <= 60) {
                if (showRetryNotices) dao.updateMessageContent(messageId, "Waiting ${wait}s for the provider rate limit…", MessageStatus.STREAMING.name)
                delay(wait * 1_000)
                if (showRetryNotices) dao.updateMessageContent(messageId, "", MessageStatus.STREAMING.name)
                attempt++
                continue
            }
            if (error != null && text.isBlank() && calls.isEmpty()) throw error
            if (text.isBlank() && calls.isEmpty() && !allowEmpty) {
                if (shouldRetryEmptyResponse(attempt, text.isNotBlank(), calls.isNotEmpty(), allowEmpty)) {
                    dao.updateMessageContent(messageId, "The provider returned no text. Retrying once…", MessageStatus.STREAMING.name)
                    delay(750)
                    dao.updateMessageContent(messageId, "", MessageStatus.STREAMING.name)
                    attempt++
                    continue
                }
                throw AssistantApiException(
                    ErrorKind.MODEL_UNAVAILABLE,
                    "The selected model returned no text after an automatic retry. Try a different model or lower its reasoning effort."
                )
            }
            // Some models emit their tool calls inline as <tools>/<tool_call>/<tool>
            // blocks in the content stream instead of through the native tool_calls
            // field. Recover them here so the same execution loop runs them and the
            // user actually gets the result (e.g. the weather they asked for).
            // Disabled on the terminal no-tools round: recovering a call there would
            // resurrect work the loop already ruled out and could spin forever.
            if (recoverInlineToolCalls) {
                val existingNames = calls.values.map { it.name }.toSet()
                var nextIndex = (calls.keys.maxOrNull() ?: -1) + 1
                ToolCallXml.parseToolCallXml(text).forEach { parsed ->
                    if (parsed.name.isNotBlank() && parsed.name !in existingNames) {
                        calls[nextIndex++] = MutableCall(
                            id = parsed.id,
                            name = parsed.name,
                            arguments = parsed.arguments
                        )
                    }
                }
            }
            return StreamedResult(
                rawText = text,
                text = ToolCallXml.scrubToolBlocks(text.scrubThinkTags()),
                toolCalls = calls.toSortedMap().values.map {
                    ToolCall(it.id.ifBlank { "call_${UUID.randomUUID()}" }, function = FunctionCall(it.name, it.arguments))
                },
                usage = accumulatedUsage
            )
        }
    }

    private fun mergeUsage(first: Usage?, second: Usage?): Usage? {
        if (first == null) return second
        if (second == null) return first
        fun sum(a: Int?, b: Int?): Int? = if (a == null && b == null) null else
            ((a ?: 0).toLong() + (b ?: 0).toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val cached = sum(first.promptTokensDetails?.cachedTokens, second.promptTokensDetails?.cachedTokens)
        return Usage(
            promptTokens = sum(first.promptTokens, second.promptTokens),
            completionTokens = sum(first.completionTokens, second.completionTokens),
            totalTokens = sum(first.totalTokens, second.totalTokens),
            promptTokensDetails = cached?.let(::PromptTokensDetails),
            cost = if (first.cost == null && second.cost == null) null else (first.cost ?: 0.0) + (second.cost ?: 0.0)
        )
    }

    private fun decodeCitations(message: MessageEntity): List<Citation> =
        runCatching { json.decodeFromString<List<Citation>>(message.citationsJson) }.getOrDefault(emptyList())

    private fun decodeOutputs(message: MessageEntity): List<GeneratedOutput> =
        runCatching { json.decodeFromString<List<GeneratedOutput>>(message.outputsJson) }.getOrDefault(emptyList())

    private suspend fun apiMessageFor(message: MessageEntity, withEvidence: Boolean = false): ApiMessage {
        if (withEvidence && message.role == "ASSISTANT") {
            return ApiMessage("assistant", TurnEvidence.annotate(message.content, decodeCitations(message), decodeOutputs(message)))
        }
        val attachments = runCatching { json.decodeFromString<List<ChatAttachment>>(message.attachmentsJson) }.getOrDefault(emptyList())
        if (message.role != "USER" || attachments.isEmpty()) return ApiMessage(message.role.lowercase(), message.content)
        val parts = buildJsonArray {
            add(buildJsonObject { put("type", "text"); put("text", message.content) })
            attachments.forEach { attachment ->
                val encoded = attachmentStore.base64(attachment)
                val mime = attachment.mimeType.lowercase()
                when {
                    mime.startsWith("image/") -> add(buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") { put("url", "data:$mime;base64,$encoded") }
                    })
                    mime.startsWith("video/") -> add(buildJsonObject {
                        put("type", "video_url")
                        putJsonObject("video_url") { put("url", "data:$mime;base64,$encoded") }
                    })
                    mime.startsWith("audio/") -> add(buildJsonObject {
                        put("type", "input_audio")
                        putJsonObject("input_audio") {
                            put("data", encoded)
                            put("format", attachment.audioFormat())
                        }
                    })
                    else -> add(buildJsonObject {
                        put("type", "file")
                        putJsonObject("file") {
                            put("filename", attachment.name)
                            put("file_data", "data:$mime;base64,$encoded")
                        }
                    })
                }
            }
        }
        return ApiMessage(role = message.role.lowercase(), content = parts)
    }

    private fun ChatAttachment.audioFormat(): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "wav", "mp3", "aiff", "aac", "ogg", "flac", "m4a", "pcm16", "pcm24" -> extension
            else -> when (mimeType.lowercase()) {
                "audio/mpeg" -> "mp3"
                "audio/mp4" -> "m4a"
                "audio/x-wav", "audio/wav" -> "wav"
                else -> "mp3"
            }
        }
    }

    private fun buildModePrompt(base: String, mode: AssistantMode, capability: AgentCapability): String = when (mode) {
        AssistantMode.CHAT -> "$base You are in Chat mode: optimize for fluid everyday conversation, answer directly, and avoid elaborate workflows unless they are genuinely needed."
        AssistantMode.AGENT -> buildString {
            append(base)
            append(" You are in Agent mode: take ownership of multi-step work, use tools when they materially improve the result, verify important details, and return a finished deliverable. Keep hidden reasoning private and present conclusions, evidence, and useful progress only. ")
            append(capability.instruction)
            if (capability.outputKind() != null) append(" You must call create_artifact exactly once with the completed deliverable; after it succeeds, give a short summary and do not call it again.")
            if (capability == AgentCapability.AUTO) append(" When a web search would materially improve the answer, call parallel_search immediately — never say you will search, look something up, or check online without actually invoking the tool. Multi-step work is expected: after each tool result either call the next needed tool or write the complete final answer. Keep going across several tool rounds when the task needs more than one lookup, then finish with one complete answer.")
        }
    }

    private suspend fun buildPastChatContext(conversationId: String, query: String, voiceMode: Boolean): String {
        val limit = if (voiceMode) 2 else ChatHistorySearch.DEFAULT_LIMIT
        return ChatHistorySearch.formatContext(searchPastChats(conversationId, query, limit))
    }

    private suspend fun searchPastChats(
        conversationId: String,
        query: String,
        limit: Int
    ): List<ChatHistorySearch.Hit> {
        val rows = dao.recentMessagesOutside(conversationId, ChatHistorySearch.DEFAULT_SCAN)
        val messages = rows.map { row ->
            ChatHistorySearch.Message(
                conversationId = row.conversationId,
                conversationTitle = row.conversationTitle,
                role = row.role,
                content = row.content,
                createdAt = row.createdAt
            )
        }
        return ChatHistorySearch.retrieve(query, messages, limit = limit)
    }

    private fun validatePastChatArgs(arguments: String): SearchPastChatsArgs {
        val raw = runCatching { json.decodeFromString<SearchPastChatsArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied invalid past-chat search arguments.") }
        val query = raw.query.trim()
        if (query.length !in 2..160) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied invalid past-chat search arguments.")
        }
        return SearchPastChatsArgs(query)
    }

    private fun pastChatTool() = ToolDefinition(
        function = FunctionDefinition(
            name = TOOL_SEARCH_PAST_CHATS,
            description = "Search earlier chats on this phone when the user refers to something you discussed before. Use a short keyword query.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", "string"); put("description", "Short keywords from the earlier chat") }
                }
                put("required", buildJsonArray { add(JsonPrimitive("query")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun validateSearchArgs(arguments: String, width: Int): ParallelSearchArgs {
        val raw = runCatching { json.decodeFromString<ParallelSearchArgs>(arguments) }.getOrNull()
        val objective = raw?.objective?.trim().orEmpty().takeIf { it.length in 1..5_000 }
        val queries = (raw?.searchQueries?.takeIf { it.isNotEmpty() } ?: AgentLoopPolicy.parseSearchQueries(arguments))
            .map(String::trim).filter(String::isNotBlank).distinct().take(width.coerceIn(1, 5))
        // Parallel wants 3-200 chars per query (concise keyword queries). Reject anything that
        // looks like a full sentence: a model that hands us a 160-char sentence used to slip
        // through and the server returned "invalid request".
        if (queries.isEmpty() || queries.any { it.length !in 3..200 }) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied invalid web-search arguments.")
        }
        return ParallelSearchArgs(objective = objective, searchQueries = queries, mode = raw?.mode?.trim()?.takeIf { it.isNotBlank() })
    }

    private fun validateArtifact(arguments: String, capability: AgentCapability): GeneratedOutput {
        val raw = runCatching { json.decodeFromString<CreateArtifactArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid artifact.") }
        // v5.7.1: accept the type names models actually use. The strict enum match
        // rejected "excel" / "xlsx" / "csv" / "docx" / "pdf", the tool call bounced,
        // and Auto burned its tool rounds retrying until the turn died fileless.
        val requested = ArtifactKinds.kindFor(raw.type)
        val expected = capability.outputKind()
        val kind = when {
            expected != null && requested == expected -> expected
            expected == null && requested != null && requested in ArtifactKinds.fileKinds -> requested
            else -> null
        }
        val content = if (kind != null) ArtifactKinds.normalizeContent(kind, raw.content) else raw.content.trim()
        if (kind == null || raw.title.isBlank() || content.isBlank() || content.length > 500_000) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid or oversized artifact.")
        }
        val (extension, mime) = when (kind) {
            OutputKind.DOCUMENT -> "md" to "text/markdown"
            OutputKind.SPREADSHEET -> "csv" to "text/csv"
            OutputKind.DATABASE -> "sql" to "application/sql"
            OutputKind.PDF -> "txt" to "text/plain"
            else -> throw AssistantApiException(ErrorKind.VALIDATION, "Unsupported local artifact type.")
        }
        val title = raw.title.trim().take(120)
        val stem = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifBlank { "kryzz-output" }
        return GeneratedOutput(UUID.randomUUID().toString(), kind, title, "$stem.$extension", mime, content = content)
    }

    private fun validateSkill(arguments: String): CreateSkillArgs {
        val raw = runCatching { json.decodeFromString<CreateSkillArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid skill definition.") }
        val name = raw.name.trim()
        val description = raw.description.trim()
        val instructions = raw.instructions.trim()
        val examples = raw.examplePrompts.map(String::trim).filter(String::isNotBlank).distinct().take(6)
        if (name.length !in 3..60 || description.length !in 8..240 || instructions.length !in 20..8_000) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The generated skill was incomplete or oversized.")
        }
        return CreateSkillArgs(name, description, instructions, examples)
    }

    private fun validateCodeProject(arguments: String): CreateCodeProjectArgs {
        val raw = runCatching { json.decodeFromString<CreateCodeProjectArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid code project.") }
        val title = raw.title.trim()
        if (title.length !in 3..120 || raw.description.length > 2_000) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The generated code project metadata was invalid.")
        }
        val files = runCatching { CodeProjectGenerator.validate(raw.files) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, it.message ?: "The generated code project was invalid.") }
        return CreateCodeProjectArgs(title, raw.description.trim(), files)
    }

    private fun parallelTool(width: Int) = ToolDefinition(
        function = FunctionDefinition(
            name = "parallel_search",
            description = "Search the current web when the answer depends on fresh, niche, uncertain, or source-backed facts. Provide up to ${width.coerceIn(1, 5)} distinct concise query angles (3-6 keyword words; never full sentences).",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("objective") {
                        put("type", "string")
                        put("description", "Optional natural-language description of the research goal (up to 5000 chars). Omit when only keyword queries are needed.")
                    }
                    putJsonObject("search_queries") {
                        put("type", "array"); put("minItems", 1); put("maxItems", width.coerceIn(1, 5))
                        putJsonObject("items") {
                            put("type", "string")
                            put("description", "Concise 3-6 word keyword query. NEVER write a sentence.")
                        }
                    }
                    putJsonObject("mode") {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("turbo"))
                            add(JsonPrimitive("fast"))
                            add(JsonPrimitive("basic"))
                            add(JsonPrimitive("advanced"))
                        })
                        put("description", "Optional quality/latency preset. Defaults to advanced on the server.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("search_queries")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun artifactTool(capability: AgentCapability) = ToolDefinition(
        function = FunctionDefinition(
            name = "create_artifact",
            description = "Return the finished ${capability.shortLabel.lowercase()} deliverable for local export.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("type") { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive(capability.outputKind()!!.name.lowercase())) }) }
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("content") { put("type", "string"); put("description", capability.artifactContentDescription()) }
                }
                put("required", buildJsonArray { add(JsonPrimitive("type")); add(JsonPrimitive("title")); add(JsonPrimitive("content")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun skillTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "create_skill",
            description = "Save one reusable assistant workflow locally on this phone. Skills contain instructions only and never credentials or executable actions.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("name") { put("type", "string"); put("description", "Short unique skill name") }
                    putJsonObject("description") { put("type", "string"); put("description", "What the skill helps with") }
                    putJsonObject("instructions") { put("type", "string"); put("description", "Reusable, self-contained assistant instructions") }
                    putJsonObject("example_prompts") {
                        put("type", "array"); put("maxItems", 6)
                        putJsonObject("items") { put("type", "string") }
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("name")); add(JsonPrimitive("description")); add(JsonPrimitive("instructions")); add(JsonPrimitive("example_prompts")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun codeProjectTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "create_code_project",
            description = "Create a complete UTF-8 multi-file code project as a ZIP stored locally on the phone. Include README.md and every source/config file needed to continue development; never include real secrets.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("description") { put("type", "string") }
                    putJsonObject("files") {
                        put("type", "array"); put("minItems", 2); put("maxItems", CodeProjectGenerator.MAX_FILES)
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("path") { put("type", "string"); put("description", "Portable relative path such as frontend/src/App.kt") }
                                putJsonObject("content") { put("type", "string"); put("description", "Complete UTF-8 file contents") }
                            }
                            put("required", buildJsonArray { add(JsonPrimitive("path")); add(JsonPrimitive("content")) })
                            put("additionalProperties", false)
                        }
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("title")); add(JsonPrimitive("description")); add(JsonPrimitive("files")) })
                put("additionalProperties", false)
            }
        )
    )

    /** AUTO agent tool: generate an image with the configured provider image model. */
    private fun generateImageTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "generate_image",
            description = "Generate an image from a text prompt using the configured image model and attach it to the reply. Use this whenever the user asks for a picture, illustration, or photo instead of describing how to make one.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("prompt") { put("type", "string"); put("description", "Detailed image prompt: subject, style, lighting, composition."); put("maxLength", 2_000) }
                }
                put("required", buildJsonArray { add(JsonPrimitive("prompt")) })
                put("additionalProperties", false)
            }
        )
    )

    /** AUTO agent tool: generate a video with the configured provider video model. */
    private fun generateVideoTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "generate_video",
            description = "Generate a short video from a text prompt using the configured video model and attach it to the reply. Use this when the user asks for a video clip or animation instead of describing how to make one.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("prompt") { put("type", "string"); put("description", "Detailed video prompt: subject, action, setting, camera."); put("maxLength", 2_000) }
                }
                put("required", buildJsonArray { add(JsonPrimitive("prompt")) })
                put("additionalProperties", false)
            }
        )
    )

    /** AUTO agent tool: generate audio or music with the configured provider audio model. */
    private fun generateAudioTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "generate_audio",
            description = "Generate audio or music from a text prompt using the configured audio model and attach it to the reply. Describe genre, mood, instrumentation, and tempo for music, or the spoken content for speech. Use this when the user asks for a track, jingle, or sound clip instead of describing how to make one.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("prompt") { put("type", "string"); put("description", "Audio/music prompt: genre, mood, instrumentation, tempo, or spoken content."); put("maxLength", 2_000) }
                }
                put("required", buildJsonArray { add(JsonPrimitive("prompt")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun artifactToolAuto() = ToolDefinition(
        function = FunctionDefinition(
            name = "create_artifact",
            description = "Return a finished local file the user can save on this phone: a document (Markdown, saved as DOCX), spreadsheet (CSV, saved as XLSX), database (SQL, saved as SQLite), or pdf (Markdown, saved as PDF). Use it only when the user wants a file, download, or export, not for ordinary answers. Call it exactly once with the complete deliverable. $ARTIFACT_FORMAT_GUIDE",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("type") {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("document"))
                            add(JsonPrimitive("spreadsheet"))
                            add(JsonPrimitive("database"))
                            add(JsonPrimitive("pdf"))
                        })
                    }
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "The complete deliverable with no code fences: Markdown for document/pdf, CSV with a header row (never a Markdown table) for spreadsheet, or SQL for database. For several spreadsheet sheets, start each sheet with a line `### Sheet: Name`.")
                    }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("type"))
                    add(JsonPrimitive("title"))
                    add(JsonPrimitive("content"))
                })
                put("additionalProperties", false)
            }
        )
    )

    private fun datetimeTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "get_current_time",
            description = "Get the current local date, time, weekday, and timezone on this phone. Call this before answering anything that depends on today, now, or a schedule.",
            parameters = MiniMaxToolSafety.datetimeParameters()
        )
    )

    private fun calculateTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "calculate",
            description = "Evaluate a precise arithmetic expression. Use instead of guessing. Supports + - * / % ^, parentheses, unary minus, pi, e, sqrt, abs, min, max, round.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("expression") {
                        put("type", "string")
                        put("description", "Math expression such as (19.6-17.4)/17.4 or sqrt(2^10)")
                        put("maxLength", LocalCalculator.MAX_EXPRESSION_CHARS)
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("expression")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun weatherTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "get_weather",
            description = "Get a live 3-day forecast from Open-Meteo. Pass a place name, or omit it to use the phone's saved location.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("place") {
                        put("type", "string")
                        put("description", "City or town, e.g. Gilly Switzerland. Omit to use the device location.")
                    }
                }
                put("additionalProperties", false)
            }
        )
    )

    private fun fetchUrlTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "fetch_url",
            description = "Download a public http(s) page and return its readable text (up to ${PublicWebClient.MAX_TEXT_CHARS} characters). Use it to read a link the user shared, or to read a result URL returned by parallel_search when its excerpts are not enough. Pass the URL exactly as given; other URLs are refused.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("url") { put("type", "string"); put("description", "Public http or https URL") }
                }
                put("required", buildJsonArray { add(JsonPrimitive("url")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun rememberFactTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "remember_fact",
            description = "Save one durable personal fact to local memory. Never store secrets, passwords, or one-off tasks.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("fact") { put("type", "string"); put("description", "One sentence in the user's words") }
                    putJsonObject("category") {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("USER"))
                            add(JsonPrimitive("PREFERENCE"))
                            add(JsonPrimitive("GOAL"))
                            add(JsonPrimitive("PROJECT"))
                            add(JsonPrimitive("OTHER"))
                        })
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("fact")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun recallMemoriesTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "recall_memories",
            description = "Search stored local memories with a short keyword query.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", "string"); put("description", "Short keywords from the fact to recall") }
                }
                put("required", buildJsonArray { add(JsonPrimitive("query")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun scheduleTaskTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "schedule_task",
            description = "Create a daily or weekly local reminder. The prompt is what Kryzz should do when it fires. Confirm the time with get_current_time first.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("prompt") { put("type", "string"); put("description", "What the agent should do when the task runs") }
                    putJsonObject("hour") { put("type", "integer"); put("minimum", 0); put("maximum", 23) }
                    putJsonObject("minute") { put("type", "integer"); put("minimum", 0); put("maximum", 59) }
                    putJsonObject("recurrence") {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("daily"))
                            add(JsonPrimitive("weekly"))
                        })
                    }
                    putJsonObject("days_of_week") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "integer"); put("minimum", 1); put("maximum", 7) }
                        put("description", "ISO weekdays for weekly tasks: 1=Monday … 7=Sunday")
                    }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("title"))
                    add(JsonPrimitive("prompt"))
                    add(JsonPrimitive("hour"))
                })
                put("additionalProperties", false)
            }
        )
    )

    private fun askUserTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "ask_user",
            description = "Ask the user an interactive question with tappable options and a free-text box, then wait for their answer. Use when a choice genuinely matters (preferences, priorities, which option to build). Keep options short (2-6). Never use for rhetorical questions or facts the user already gave you.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("question") {
                        put("type", "string")
                        put("description", "The question shown at the top of the card")
                        put("maxLength", InlineQuestion.MAX_QUESTION_CHARS)
                    }
                    putJsonObject("options") {
                        put("type", "array"); put("maxItems", InlineQuestion.MAX_OPTIONS)
                        putJsonObject("items") { put("type", "string"); put("maxLength", InlineQuestion.MAX_OPTION_CHARS) }
                        put("description", "Short tappable answer options. Omit for a pure free-text question.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("question")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun updatePlanTool() = ToolDefinition(
        function = FunctionDefinition(
            name = AgentTurnPolicy.UPDATE_PLAN,
            description = "Show the user a live checklist of your plan for this task and keep it current. Call it first for work with three or more distinct steps, then again as steps start and finish; send the whole list every time. It can be called in the same round as other tools. Skip it for simple questions.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("steps") {
                        put("type", "array"); put("minItems", 1); put("maxItems", AgentPlanParser.MAX_STEPS)
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("title") {
                                    put("type", "string")
                                    put("description", "Short step, e.g. Compare battery tests")
                                    put("maxLength", AgentPlanParser.MAX_TITLE_CHARS)
                                }
                                putJsonObject("status") {
                                    put("type", "string")
                                    put("enum", buildJsonArray {
                                        add(JsonPrimitive("pending"))
                                        add(JsonPrimitive("in_progress"))
                                        add(JsonPrimitive("done"))
                                    })
                                }
                            }
                            put("required", buildJsonArray { add(JsonPrimitive("title")); add(JsonPrimitive("status")) })
                            put("additionalProperties", false)
                        }
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("steps")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun validateAskUser(arguments: String): InlineQuestion {
        val raw = runCatching { json.decodeFromString(InlineQuestion.serializer(), arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid user question.") }
        return raw.sanitised()
            ?: throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid user question.")
    }

    private data class ValidatedRemember(val fact: String, val category: MemoryEngine.Category)
    private data class ValidatedSchedule(
        val title: String,
        val prompt: String,
        val hour: Int,
        val minute: Int,
        val recurrence: CronRecurrence,
        val daysOfWeek: List<Int>
    )

    private fun validateCalculateArgs(arguments: String): CalculateArgs {
        val raw = runCatching { json.decodeFromString<CalculateArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid calculate expression.") }
        val expression = raw.expression.trim()
        if (expression.isEmpty() || expression.length > LocalCalculator.MAX_EXPRESSION_CHARS) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid calculate expression.")
        }
        return CalculateArgs(expression)
    }

    private fun validateWeatherArgs(arguments: String): WeatherArgs {
        if (arguments.isBlank() || arguments == "{}") return WeatherArgs(null)
        val raw = runCatching { json.decodeFromString<WeatherArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied invalid weather arguments.") }
        val place = raw.place?.trim()?.takeIf { it.isNotBlank() }
        if (place != null && place.length !in 2..80) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid place name.")
        }
        return WeatherArgs(place)
    }

    private fun validateFetchArgs(arguments: String, allowlist: FetchAllowlist): FetchUrlArgs {
        val raw = runCatching { json.decodeFromString<FetchUrlArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid URL.") }
        val url = raw.url.trim()
        if (url.length !in 8..2_000) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid URL.")
        }
        PublicWebClient.requireSafeHttpUrl(url)
        if (!allowlist.permits(url)) throw AssistantApiException(ErrorKind.VALIDATION, FetchAllowlist.REFUSAL)
        return FetchUrlArgs(url)
    }

    /** Result of a network lookup tool, produced off the main thread by [lookup]. */
    private sealed interface Lookup {
        data class Search(val response: ParallelSearchResponse, val queries: List<String>, val objective: String?) : Lookup
        data class Page(val page: FetchedPage) : Lookup
        data class Weather(val report: WeatherReport) : Lookup
    }

    /**
     * The network half of the lookup tools. Runs on the IO dispatcher, possibly alongside
     * other lookups from the same round, so it validates and fetches but never touches turn
     * state (citations, session id, history); the caller applies results in call order.
     * The blocking public-web calls are interruptible so a cancelled turn stops waiting.
     */
    private suspend fun lookup(
        call: ToolCall,
        searchWidth: Int,
        searchSession: String?,
        model: String,
        settings: SettingsState,
        allowlist: FetchAllowlist
    ): Lookup = when (call.function.name) {
        AgentTurnPolicy.PARALLEL_SEARCH -> {
            val args = validateSearchArgs(call.function.arguments, searchWidth)
            val parallelKey = credentials.parallelKey()
                ?: throw AssistantApiException(ErrorKind.INVALID_KEY, "Parallel Search is enabled but its API key is missing.")
            val response = parallel.search(
                parallelKey,
                ParallelSearchRequest(
                    objective = args.objective,
                    searchQueries = args.searchQueries,
                    mode = args.mode,
                    maxCharsTotal = settings.maxSearchChars,
                    sessionId = searchSession,
                    clientModel = model
                )
            )
            Lookup.Search(response, args.searchQueries, args.objective)
        }
        AgentTurnPolicy.FETCH_URL -> {
            val client = publicWeb ?: throw AssistantApiException(ErrorKind.VALIDATION, "Page fetch is not available.")
            val url = validateFetchArgs(call.function.arguments, allowlist).url
            Lookup.Page(runInterruptible { client.fetchPage(url) })
        }
        AgentTurnPolicy.GET_WEATHER -> {
            val client = publicWeb ?: throw AssistantApiException(ErrorKind.VALIDATION, "Weather is not available.")
            val args = validateWeatherArgs(call.function.arguments)
            val lat = settings.locationLat.takeIf { settings.locationEnabled }
            val lon = settings.locationLon.takeIf { settings.locationEnabled }
            Lookup.Weather(runInterruptible { client.weather(args.place, lat, lon) })
        }
        else -> error("${call.function.name} is not a lookup tool")
    }

    private suspend fun Deferred<Result<Lookup>>?.awaitLookup(): Lookup =
        checkNotNull(this) { "Lookup was not started for this call." }.await().getOrThrow()

    private fun validateRememberArgs(arguments: String): ValidatedRemember {
        val raw = runCatching { json.decodeFromString<RememberFactArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid memory.") }
        val fact = MemoryEngine.compactSentence(raw.fact)
        if (fact.length !in MemoryEngine.MIN_MEMORY_CHARS..MemoryEngine.MAX_MEMORY_CHARS) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The memory was too short or too long.")
        }
        val category = raw.category?.trim()?.let { runCatching { MemoryEngine.Category.valueOf(it.uppercase()) }.getOrNull() }
            ?: MemoryEngine.Category.OTHER
        return ValidatedRemember(fact, category)
    }

    private fun validateRecallArgs(arguments: String): RecallMemoriesArgs {
        val raw = runCatching { json.decodeFromString<RecallMemoriesArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid recall query.") }
        val query = raw.query.trim()
        if (query.length !in 2..160) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid recall query.")
        }
        return RecallMemoriesArgs(query)
    }

    private fun validateScheduleArgs(arguments: String): ValidatedSchedule {
        val raw = runCatching { json.decodeFromString<ScheduleTaskArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied invalid schedule arguments.") }
        val title = raw.title.trim().take(80)
        val prompt = raw.prompt.trim()
        if (title.length < 3 || prompt.length !in 8..2_000) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The scheduled task needs a short title and a prompt.")
        }
        val recurrence = when (raw.recurrence.trim().lowercase()) {
            "weekly" -> CronRecurrence.WEEKLY
            else -> CronRecurrence.DAILY
        }
        val days = raw.daysOfWeek.filter { it in 1..7 }.distinct().sorted()
        if (recurrence == CronRecurrence.WEEKLY && days.isEmpty()) {
            throw AssistantApiException(ErrorKind.VALIDATION, "Weekly tasks need at least one day of the week (1=Monday).")
        }
        return ValidatedSchedule(
            title = title,
            prompt = prompt,
            hour = raw.hour.coerceIn(0, 23),
            minute = raw.minute.coerceIn(0, 59),
            recurrence = recurrence,
            daysOfWeek = days
        )
    }

    private data class SimplePromptArgs(val prompt: String)

    private fun validateSimplePromptArgs(arguments: String, toolName: String): SimplePromptArgs {
        val raw = runCatching { json.decodeFromString<SimplePromptArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied invalid $toolName arguments.") }
        val prompt = raw.prompt.trim()
        if (prompt.length !in 3..2_000) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid $toolName prompt.")
        }
        return SimplePromptArgs(prompt)
    }
    /**
     * Runs a create_artifact call: materialises the file, makes it the turn's only file output
     * (a new call replaces the earlier file) and saves the tool row. Returns the tool result.
     */
    private suspend fun createArtifact(
        conversationId: String,
        call: ToolCall,
        capability: AgentCapability,
        outputs: MutableList<GeneratedOutput>
    ): String {
        val output = outputStore.materialize(validateArtifact(call.function.arguments, capability))
        outputs.clear()
        outputs += output
        val result = buildJsonObject {
            put("status", "created_locally")
            put("type", output.kind.name.lowercase())
            put("title", output.title)
            put("file_name", output.fileName)
        }.toString()
        saveToolMessage(conversationId, call, result)
        return result
    }

    private suspend fun saveToolMessage(conversationId: String, call: ToolCall, content: String) {
        dao.upsertMessage(
            MessageEntity(
                UUID.randomUUID().toString(), conversationId, "TOOL", content, System.currentTimeMillis(),
                toolCallId = call.id, toolName = call.function.name,
                mode = AssistantMode.AGENT.name
            )
        )
    }

    private fun AgentCapability.outputKind(): OutputKind? = when (this) {
        AgentCapability.DOCUMENT -> OutputKind.DOCUMENT
        AgentCapability.SPREADSHEET -> OutputKind.SPREADSHEET
        AgentCapability.DATABASE -> OutputKind.DATABASE
        else -> null
    }

    private fun AgentCapability.artifactContentDescription(): String = when (this) {
        AgentCapability.DOCUMENT -> "Complete Markdown document. $DOCUMENT_FORMAT_GUIDE"
        AgentCapability.SPREADSHEET -> "Valid UTF-8 CSV including a header row. $SPREADSHEET_FORMAT_GUIDE"
        AgentCapability.DATABASE -> "Complete portable SQL schema and starter queries"
        else -> "Complete artifact content"
    }

    private fun enabledSkillsPrompt(skills: List<SkillEntity>, userText: String): String {
        if (skills.isEmpty()) return ""
        val selected = AgentLoopPolicy.selectActiveSkills(
            skills.map { SkillSummary(it.name, it.description, it.instructions, it.examplePrompts) },
            userText
        )
        return AgentLoopPolicy.formatActiveSkillsPrompt(selected)
    }

    private data class MutableCall(var id: String = "", var name: String = "", var arguments: String = "")
    private data class StreamedResult(val rawText: String, val text: String, val toolCalls: List<ToolCall>, val usage: Usage?)

    companion object {
        /** Pseudo-tool names reported through [toolActivity] for non-tool work in the chat. */
        const val TOOL_MEMORY_SAVE = "memory_save"
        const val TOOL_SEARCH_PAST_CHATS = "search_past_chats"
        const val TOOL_AGENT_RUNNING = "agent_running"
        /** v5.9: the review pass over a draft answer (see [AnswerReview]). */
        const val TOOL_REVIEWING = "reviewing_answer"
        const val TOOL_IMAGE_CREATION = "image_creation"
        const val TOOL_VIDEO_CREATION = "video_creation"
        const val TOOL_AUDIO_CREATION = "audio_creation"
        const val MINIMAX_MAX_COMPLETION_TOKENS = 8_192

        /**
         * v5.11: how create_artifact content is rendered, told to the model so it writes
         * structure the generators turn into real styles, lists, tables and sheets.
         */
        private const val DOCUMENT_FORMAT_GUIDE = "Headings (#, ##, ###), **bold**, *italic*, `code`, [links](https://...), " +
            "- bullets and 1. numbered lists (indent two spaces for one sub-level), | tables | with a header row, " +
            "``` code blocks, > quotes and --- rules all become real formatting."
        private const val SPREADSHEET_FORMAT_GUIDE = "Plain numbers (1234.5), percentages (12%), TRUE/FALSE and formulas (=SUM(B2:B9)) " +
            "become typed cells; keep IDs with leading zeros as they are. For several sheets, start each sheet with a line " +
            "`### Sheet: Name` followed by its own header row."
        private const val ARTIFACT_FORMAT_GUIDE = "Documents and PDFs: $DOCUMENT_FORMAT_GUIDE Spreadsheets: $SPREADSHEET_FORMAT_GUIDE"

        /** Network-bound tools whose calls in one round run concurrently (see [lookup]). */
        private val LOOKUP_TOOLS = setOf(
            AgentTurnPolicy.PARALLEL_SEARCH,
            AgentTurnPolicy.FETCH_URL,
            AgentTurnPolicy.GET_WEATHER
        )
    }
}

internal fun shouldRetryEmptyResponse(
    attempt: Int,
    hasText: Boolean,
    hasToolCalls: Boolean,
    allowEmpty: Boolean
): Boolean = attempt == 0 && !hasText && !hasToolCalls && !allowEmpty

/**
 * Fired when a tool call starts (started=true) and finishes (started=false). v5.11: [detail]
 * is the short argument text the chip shows (ToolActivityDetail); a stop event carries the
 * same detail as its start so the chat removes the matching chip.
 */
data class ToolActivity(val conversationId: String, val toolName: String, val started: Boolean, val detail: String? = null)

/**
 * Receives a voice reply's visible text while the model streams it, so speech can start on
 * the first sentence. [round] changes when the agent drops a round's text for a tool round;
 * [done] marks the final answer of the turn, delivered once.
 */
fun interface VoiceReplyListener {
    fun onText(round: String, text: String, done: Boolean)
}

/**
 * An agent `ask_user` call waiting on the UI. The chat screen renders [question] as an
 * interactive card; answering (or dismissing) completes [deferred], which unblocks the
 * suspended tool call inside [AgentExecutor].
 */
class PendingQuestion(
    val conversationId: String,
    val question: InlineQuestion,
    internal val deferred: CompletableDeferred<String?>
)
