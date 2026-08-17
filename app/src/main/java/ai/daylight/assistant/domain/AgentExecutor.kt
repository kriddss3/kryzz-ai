package ai.daylight.assistant.domain

import ai.daylight.assistant.data.AttachmentStore
import ai.daylight.assistant.data.GeneratedOutputStore
import ai.daylight.assistant.data.CodeProjectGenerator
import ai.daylight.assistant.data.LocationProvider
import ai.daylight.assistant.data.MemoryRepository
import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.preferences.AppPreferences
import ai.daylight.assistant.data.remote.ApiMessage
import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.CreateArtifactArgs
import ai.daylight.assistant.data.remote.CreateCodeProjectArgs
import ai.daylight.assistant.data.remote.CreateSkillArgs
import ai.daylight.assistant.data.remote.ErrorKind
import ai.daylight.assistant.data.remote.FunctionCall
import ai.daylight.assistant.data.remote.FunctionDefinition
import ai.daylight.assistant.data.remote.ImageGenerationRequest
import ai.daylight.assistant.data.remote.OpenRouterClient
import ai.daylight.assistant.data.remote.ParallelClient
import ai.daylight.assistant.data.remote.ParallelSearchArgs
import ai.daylight.assistant.data.remote.ParallelSearchRequest
import ai.daylight.assistant.data.remote.PromptTokensDetails
import ai.daylight.assistant.data.remote.ProviderPreferences
import ai.daylight.assistant.data.remote.ReasoningConfig
import ai.daylight.assistant.data.remote.SearchPastChatsArgs
import ai.daylight.assistant.data.remote.SpeechGenerationRequest
import ai.daylight.assistant.data.remote.StreamEvent
import ai.daylight.assistant.data.remote.ToolCall
import ai.daylight.assistant.data.remote.ToolDefinition
import ai.daylight.assistant.data.remote.Usage
import ai.daylight.assistant.data.remote.VideoGenerationRequest
import ai.daylight.assistant.security.SecureCredentialStore
import ai.daylight.assistant.data.local.SkillEntity
import ai.daylight.assistant.voice.VoiceConfig
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val parallel: ParallelClient,
    private val outputStore: GeneratedOutputStore,
    private val attachmentStore: AttachmentStore,
    private val memoryRepository: MemoryRepository,
    private val json: Json,
    private val locationProvider: LocationProvider? = null,
    /** Live tool activity so the UI can show what the agent is currently running. */
    val toolActivity: MutableSharedFlow<ToolActivity> = MutableSharedFlow(extraBufferCapacity = 8),
    /**
     * Emits a short label whenever the agent starts a long-running tool call during voice
     * chat — e.g. "checking online" when parallel_search fires. The voice UI plays a quick
     * filler phrase so the user isn't sitting in silence while the round-trip completes.
     */
    val voiceFillerRequest: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 4),
    private val onFirstUserMessage: (conversationId: String, message: String) -> Unit = { _, _ -> }
) {
    suspend fun send(
        conversationId: String,
        text: String,
        mode: AssistantMode = AssistantMode.CHAT,
        capability: AgentCapability = AgentCapability.AUTO,
        attachments: List<ChatAttachment> = emptyList(),
        voiceMode: Boolean = false
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
            voiceMode -> generateReply(conversationId, AssistantMode.CHAT, AgentCapability.AUTO, voiceMode = true)
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
        voiceMode: Boolean = false
    ): String? {
        val key = credentials.openRouterKey()
            ?: throw AssistantApiException(ErrorKind.INVALID_KEY, "Add an OpenRouter API key in Settings before sending a message.")
        val settings = preferences.state.first()
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
        val enabledSkills = if (!voiceMode && mode == AssistantMode.AGENT && capability != AgentCapability.SKILL_MAKER) dao.enabledSkills().take(12) else emptyList()
        val memoryContext = if (settings.memoryEnabled && !voiceMode) memoryRepository.buildMemoryContext(lastUser?.content.orEmpty()) else ""
        val memoryInstruction = if (settings.memoryEnabled && !voiceMode) {
            "Long-term memory is active: facts the user shares about themselves (name, age, gender, work, interests, hobbies, ethnicity, where they live) and anything they ask you to remember are saved automatically. When you notice a new fact was shared, briefly acknowledge that you'll remember it."
        } else ""
        val pastChatContext = buildPastChatContext(conversationId, lastUser?.content.orEmpty(), voiceMode)
        val pastChatInstruction = if (!voiceMode) {
            "You can look up earlier conversations on this phone with search_past_chats when the user refers to something from a past chat and the pasted snippets are not enough. Use a short keyword query. Never invent past chats."
        } else ""
        val locationContext = if (voiceMode) "" else runCatching { locationProvider?.promptContext().orEmpty() }.getOrDefault("")
        val prompt = buildModePrompt(basePrompt, mode, capability) + enabledSkillsPrompt(enabledSkills) +
            (if (memoryContext.isNotBlank()) "\n\n$memoryContext" else "") +
            (if (memoryInstruction.isNotBlank()) "\n\n$memoryInstruction" else "") +
            (if (pastChatContext.isNotBlank()) "\n\n$pastChatContext" else "") +
            (if (pastChatInstruction.isNotBlank()) "\n\n$pastChatInstruction" else "") +
            (if (locationContext.isNotBlank()) "\n\n$locationContext" else "")
        val working = mutableListOf(ApiMessage("system", prompt))
        for (message in history) working += apiMessageFor(message)
        val citations = mutableListOf<Citation>()
        val outputs = mutableListOf<GeneratedOutput>()
        var sessionId = conversation.searchSessionId
        var completedToolRounds = 0
        var completedSearchRounds = 0
        var didSearch = false
        var skillCreated = false
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
        val model = when {
            voiceMode -> settings.voiceReplyModel.ifBlank { settings.defaultModel }.ifBlank { VoiceConfig.DEFAULT_LLM_MODEL }
            mode == AssistantMode.CHAT -> settings.defaultModel
            capability in setOf(AgentCapability.DEEP_RESEARCH, AgentCapability.WIDE_SEARCH) -> settings.researchModel
            else -> settings.agentModel
        }
        val agentRunningActivity = mode == AssistantMode.AGENT && !voiceMode
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

                val withinLimit = ToolRoundLimiter.canRun(completedToolRounds, settings.maxToolRounds)
                // Voice chat keeps web search on but caps it at one round per turn so the
                // spoken reply stays responsive — anything heavier goes to text mode.
                val searchRoundCap = if (voiceMode) 1 else conversation.searchDepth.coerceIn(1, 3)
                val allowSearch = withinLimit && completedSearchRounds < searchRoundCap &&
                    settings.searchEnabled && credentials.hasParallelKey()
                val needsArtifact = withinLimit && mode == AssistantMode.AGENT && capability.outputKind() != null && outputs.isEmpty()
                val needsSkill = withinLimit && mode == AssistantMode.AGENT && capability == AgentCapability.SKILL_MAKER && !skillCreated
                val needsCode = withinLimit && mode == AssistantMode.AGENT && capability == AgentCapability.CODE && outputs.none { it.kind == OutputKind.CODE }
                val tools = buildList {
                    if (!voiceMode && withinLimit && !needsSkill && !needsCode) add(pastChatTool())
                    if (allowSearch && !needsSkill && !needsCode) add(parallelTool(conversation.searchWidth.coerceIn(1, 5)))
                    if (needsArtifact) add(artifactTool(capability))
                    if (needsSkill) add(skillTool())
                    if (needsCode) add(codeProjectTool())
                }
                val needsAnotherDeepPass = allowSearch && capability == AgentCapability.DEEP_RESEARCH &&
                    DeepSearchPolicy.shouldForceAnotherPass(completedSearchRounds, conversation.searchDepth.coerceIn(1, 3))
                val forceTool = needsArtifact || needsSkill || needsCode || needsAnotherDeepPass ||
                    (allowSearch && !didSearch && capability == AgentCapability.WIDE_SEARCH)
                val request = ChatRequest(
                    model = model,
                    messages = working,
                    tools = tools.ifEmpty { null },
                    toolChoice = when {
                        tools.isEmpty() -> null
                        forceTool -> "required"
                        else -> "auto"
                    },
                    reasoning = if (voiceMode) null else settings.modelReasoning[model]?.apiValue?.let { effort ->
                        ReasoningConfig(effort = effort, exclude = true)
                    },
                    // Voice chat always routes to the lowest-latency provider serving this model.
                    provider = if (voiceMode) ProviderPreferences() else null
                )
                val streamed = collectWithRetry(
                    key = key,
                    request = request,
                    messageId = messageId,
                    allowEmpty = outputs.isNotEmpty() || skillCreated
                ) { value ->
                    if (firstTokenLatencyMs == null && value.isNotBlank()) {
                        firstTokenLatencyMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)
                    }
                    activeText = value
                    dao.updateMessageContent(messageId, value, MessageStatus.STREAMING.name)
                }
                streamed.usage?.let { usage ->
                    hasUsage = true
                    requestPromptTokens += usage.promptTokens ?: 0
                    requestCompletionTokens += usage.completionTokens ?: 0
                    requestTotalTokens += usage.totalTokens ?: ((usage.promptTokens ?: 0) + (usage.completionTokens ?: 0))
                    requestCachedInputTokens += usage.promptTokensDetails?.cachedTokens ?: 0
                    usage.cost?.let { cost -> hasCost = true; requestCost += cost }
                }

                if (streamed.toolCalls.isEmpty()) {
                    val finalText = activeText.ifBlank {
                        when {
                            outputs.isNotEmpty() -> "Created ${outputs.first().title}. Use the output card below to save or open it."
                            skillCreated -> "The reusable skill was created and saved locally."
                            else -> throw AssistantApiException(
                                ErrorKind.MODEL_UNAVAILABLE,
                                "The selected model returned no text. Try a different model or lower its reasoning effort."
                            )
                        }
                    }
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

                dao.deleteMessage(messageId)
                activeId = null
                if (!ToolRoundLimiter.canRun(completedToolRounds, settings.maxToolRounds)) {
                    saveLimitMessage(conversationId, mode, capability)
                    return null
                }
                completedToolRounds++
                working += ApiMessage("assistant", activeText.ifBlank { null }, toolCalls = streamed.toolCalls)

                for (call in streamed.toolCalls) {
                    toolActivity.tryEmit(ToolActivity(conversationId, call.function.name, started = true))
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
                            // Tell the voice UI to play a quick "checking online" filler so the
                            // user isn't sitting in silence while the search round-trip happens.
                            if (voiceMode) voiceFillerRequest.tryEmit("checking online")
                            val args = validateSearchArgs(call.function.arguments, conversation.searchWidth.coerceIn(1, 5))
                            val parallelKey = credentials.parallelKey()
                                ?: throw AssistantApiException(ErrorKind.INVALID_KEY, "Parallel Search is enabled but its API key is missing.")
                            val result = parallel.search(
                                parallelKey,
                                ParallelSearchRequest(
                                    objective = args.objective,
                                    searchQueries = args.searchQueries,
                                    mode = args.mode,
                                    maxCharsTotal = settings.maxSearchChars,
                                    sessionId = sessionId,
                                    clientModel = model
                                )
                            )
                            didSearch = true
                            completedSearchRounds++
                            sessionId = result.sessionId
                            dao.updateSearchSession(conversationId, sessionId)
                            val mapped = CitationMapper.map(result.results)
                            mapped.forEach { candidate -> if (citations.none { it.url == candidate.url }) citations += candidate }
                            val structured = buildJsonObject {
                                put("search_id", result.searchId)
                                put("session_id", result.sessionId)
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

                        else -> {
                            val unsupported = "Unsupported tool: ${call.function.name}"
                            working += ApiMessage("tool", unsupported, call.id, call.function.name)
                        }
                    }
                    toolActivity.tryEmit(ToolActivity(conversationId, call.function.name, started = false))
                }
            }
        } catch (cancelled: CancellationException) {
            activeId?.let { dao.updateMessageContent(it, activeText, MessageStatus.CANCELLED.name) }
            throw cancelled
        } catch (error: Throwable) {
            val friendly = (error as? AssistantApiException)?.message ?: "Something went wrong. Check your connection and try again."
            activeId?.let {
                val content = if (activeText.isBlank()) friendly else "$activeText\n\n$friendly"
                dao.finishMessage(it, content, MessageStatus.ERROR.name, "[]", null, null, null, null)
            }
            throw error
        } finally {
            if (agentRunningActivity) {
                toolActivity.tryEmit(ToolActivity(conversationId, TOOL_AGENT_RUNNING, started = false))
            }
        }
    }

    private suspend fun generateImage(conversationId: String, prompt: String) {
        val key = credentials.openRouterKey()
            ?: throw AssistantApiException(ErrorKind.INVALID_KEY, "Add an OpenRouter API key in Settings before generating an image.")
        val model = preferences.state.first().imageModel
        if (model.isBlank()) throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "Choose an Image model on the Models screen before using Image generation.")
        val id = UUID.randomUUID().toString()
        dao.upsertMessage(
            MessageEntity(id, conversationId, "ASSISTANT", "Creating your image…", System.currentTimeMillis(), MessageStatus.STREAMING.name, mode = AssistantMode.AGENT.name, capability = AgentCapability.IMAGE.name)
        )
        toolActivity.tryEmit(ToolActivity(conversationId, TOOL_IMAGE_CREATION, started = true))
        try {
            val result = openRouter.generateImage(key, ImageGenerationRequest(model, prompt))
            val image = result.data.firstOrNull() ?: throw AssistantApiException(ErrorKind.UNKNOWN, "The image model returned no image.")
            val mime = image.mediaType ?: "image/png"
            val extension = when (mime) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/svg+xml" -> "svg"; else -> "png" }
            val file = outputStore.saveBase64(image.base64, extension)
            val output = GeneratedOutput(UUID.randomUUID().toString(), OutputKind.IMAGE, "Generated image", "kryzz-image.$extension", mime, localPath = file.absolutePath)
            dao.finishMessage(
                id, "Created your image with $model.", MessageStatus.COMPLETE.name, "[]",
                result.usage?.promptTokens, result.usage?.completionTokens, result.usage?.totalTokens, result.usage?.cost,
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

    private suspend fun generateVideo(conversationId: String, prompt: String) {
        val key = credentials.openRouterKey()
            ?: throw AssistantApiException(ErrorKind.INVALID_KEY, "Add an OpenRouter API key in Settings before generating a video.")
        val model = preferences.state.first().videoModel
        if (model.isBlank()) throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "Choose a Video model on the Models screen before using Video generation.")
        val id = UUID.randomUUID().toString()
        dao.upsertMessage(
            MessageEntity(id, conversationId, "ASSISTANT", "Starting video generation…", System.currentTimeMillis(), MessageStatus.STREAMING.name, mode = AssistantMode.AGENT.name, capability = AgentCapability.VIDEO.name)
        )
        toolActivity.tryEmit(ToolActivity(conversationId, TOOL_VIDEO_CREATION, started = true))
        try {
            var job = openRouter.submitVideo(key, VideoGenerationRequest(model, prompt))
            val startedAt = System.currentTimeMillis()
            var pollCount = 0
            while (job.status.lowercase() !in setOf("completed", "failed", "cancelled")) {
                if (System.currentTimeMillis() - startedAt > 10 * 60_000L) {
                    throw AssistantApiException(ErrorKind.TIMEOUT, "Video generation is still processing after 10 minutes. Check your OpenRouter activity before retrying.")
                }
                pollCount++
                dao.updateMessageContent(id, "Generating video${".".repeat((pollCount % 3) + 1)}", MessageStatus.STREAMING.name)
                delay(4_000)
                job = openRouter.videoStatus(key, job.id)
            }
            if (job.status.lowercase() != "completed") {
                throw AssistantApiException(ErrorKind.UNKNOWN, job.error ?: "The video provider could not complete this generation.")
            }
            val file = outputStore.newFile("mp4")
            val (localPath, mime) = try {
                file.absolutePath to openRouter.downloadVideo(key, job.id, file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                file.delete()
                null to "video/mp4"
            }
            val remote = job.unsignedUrls.firstOrNull()
            if (localPath == null && remote == null) throw AssistantApiException(ErrorKind.NETWORK, "The video finished, but it could not be downloaded.")
            val output = GeneratedOutput(UUID.randomUUID().toString(), OutputKind.VIDEO, "Generated video", "kryzz-video.mp4", mime, localPath = localPath, remoteUrl = remote)
            dao.finishMessage(
                id, "Created your video with $model.", MessageStatus.COMPLETE.name, "[]",
                job.usage?.promptTokens, job.usage?.completionTokens, job.usage?.totalTokens, job.usage?.cost,
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

    private suspend fun generateAudio(conversationId: String, input: String) {
        val key = credentials.openRouterKey()
            ?: throw AssistantApiException(ErrorKind.INVALID_KEY, "Add an OpenRouter API key in Settings before creating audio.")
        val model = preferences.state.first().audioModel
        if (model.isBlank()) throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "Choose an Audio model on the Models screen before using Audio creation.")
        val id = UUID.randomUUID().toString()
        dao.upsertMessage(
            MessageEntity(id, conversationId, "ASSISTANT", "Creating your audio…", System.currentTimeMillis(), MessageStatus.STREAMING.name, mode = AssistantMode.AGENT.name, capability = AgentCapability.AUDIO.name)
        )
        val file = outputStore.newFile("mp3")
        toolActivity.tryEmit(ToolActivity(conversationId, TOOL_AUDIO_CREATION, started = true))
        try {
            val mime = openRouter.generateSpeech(
                key,
                SpeechGenerationRequest(model = model, input = input.take(4_096), voice = "nova", responseFormat = "mp3", speed = 1.0),
                file
            )
            val output = GeneratedOutput(
                UUID.randomUUID().toString(), OutputKind.AUDIO, "Generated audio", "kryzz-audio.mp3", mime,
                localPath = file.absolutePath
            )
            dao.finishMessage(
                id, "Created your audio with $model.", MessageStatus.COMPLETE.name, "[]",
                null, null, null, null, json.encodeToString(listOf(output))
            )
            dao.touchConversation(conversationId, System.currentTimeMillis())
        } catch (cancelled: CancellationException) {
            file.delete()
            dao.updateMessageContent(id, "Audio creation stopped.", MessageStatus.CANCELLED.name)
            throw cancelled
        } catch (error: Throwable) {
            file.delete()
            failDirectGeneration(id, error)
            throw error
        } finally {
            toolActivity.tryEmit(ToolActivity(conversationId, TOOL_AUDIO_CREATION, started = false))
        }
    }

    private suspend fun failDirectGeneration(messageId: String, error: Throwable) {
        val friendly = (error as? AssistantApiException)?.message ?: "Generation failed. Check the selected model and provider balance."
        dao.finishMessage(messageId, friendly, MessageStatus.ERROR.name, "[]", null, null, null, null)
    }

    private suspend fun collectWithRetry(
        key: String,
        request: ChatRequest,
        messageId: String,
        allowEmpty: Boolean = false,
        onText: suspend (String) -> Unit
    ): StreamedResult {
        var attempt = 0
        var accumulatedUsage: Usage? = null
        while (true) {
            val calls = mutableMapOf<Int, MutableCall>()
            var text = ""
            var usage: Usage? = null
            var failure: AssistantApiException? = null
            openRouter.stream(key, request).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        text += event.text
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
                dao.updateMessageContent(messageId, "Waiting ${wait}s for the provider rate limit…", MessageStatus.STREAMING.name)
                delay(wait * 1_000)
                dao.updateMessageContent(messageId, "", MessageStatus.STREAMING.name)
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
            return StreamedResult(text, calls.toSortedMap().values.map {
                ToolCall(it.id.ifBlank { "call_${UUID.randomUUID()}" }, function = FunctionCall(it.name, it.arguments))
            }, accumulatedUsage)
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

    private suspend fun apiMessageFor(message: MessageEntity): ApiMessage {
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
        val raw = runCatching { json.decodeFromString<ParallelSearchArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied invalid web-search arguments.") }
        // Parallel Search allows objective to be omitted, but when present it must be 1..5000 chars
        // (server side). We accept empty/null and just don't send it.
        val objective = raw.objective?.trim().orEmpty().takeIf { it.length in 1..5_000 }
        val queries = raw.searchQueries.map(String::trim).filter(String::isNotBlank).distinct().take(width.coerceIn(1, 5))
        // Parallel wants 3-200 chars per query (concise keyword queries). Reject anything that
        // looks like a full sentence: a model that hands us a 160-char sentence used to slip
        // through and the server returned "invalid request".
        if (queries.isEmpty() || queries.any { it.length !in 3..200 }) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied invalid web-search arguments.")
        }
        return ParallelSearchArgs(objective = objective, searchQueries = queries, mode = raw.mode?.trim()?.takeIf { it.isNotBlank() })
    }

    private fun validateArtifact(arguments: String, capability: AgentCapability): GeneratedOutput {
        val expected = capability.outputKind()
            ?: throw AssistantApiException(ErrorKind.VALIDATION, "This workspace did not request a local artifact.")
        val raw = runCatching { json.decodeFromString<CreateArtifactArgs>(arguments) }
            .getOrElse { throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid artifact.") }
        val requested = runCatching { OutputKind.valueOf(raw.type.trim().uppercase()) }.getOrNull()
        if (requested != expected || raw.title.isBlank() || raw.content.isBlank() || raw.content.length > 500_000) {
            throw AssistantApiException(ErrorKind.VALIDATION, "The model supplied an invalid or oversized artifact.")
        }
        val (extension, mime) = when (expected) {
            OutputKind.DOCUMENT -> "md" to "text/markdown"
            OutputKind.SPREADSHEET -> "csv" to "text/csv"
            OutputKind.DATABASE -> "sql" to "application/sql"
            else -> throw AssistantApiException(ErrorKind.VALIDATION, "Unsupported local artifact type.")
        }
        val title = raw.title.trim().take(120)
        val stem = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifBlank { "kryzz-output" }
        return GeneratedOutput(UUID.randomUUID().toString(), expected, title, "$stem.$extension", mime, content = raw.content.trim())
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

    private suspend fun saveToolMessage(conversationId: String, call: ToolCall, content: String) {
        dao.upsertMessage(
            MessageEntity(
                UUID.randomUUID().toString(), conversationId, "TOOL", content, System.currentTimeMillis(),
                toolCallId = call.id, toolName = call.function.name,
                mode = AssistantMode.AGENT.name
            )
        )
    }

    private suspend fun saveLimitMessage(conversationId: String, mode: AssistantMode, capability: AgentCapability) {
        dao.upsertMessage(
            MessageEntity(
                UUID.randomUUID().toString(), conversationId, "ASSISTANT",
                "I stopped because the configured tool limit was reached. Continue with a narrower request or raise the limit in Search & tools (maximum 3).",
                System.currentTimeMillis(), MessageStatus.ERROR.name,
                mode = mode.name,
                capability = if (mode == AssistantMode.AGENT) capability.name else null
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
        AgentCapability.DOCUMENT -> "Complete Markdown document"
        AgentCapability.SPREADSHEET -> "Valid UTF-8 CSV including a header row"
        AgentCapability.DATABASE -> "Complete portable SQL schema and starter queries"
        else -> "Complete artifact content"
    }

    private fun enabledSkillsPrompt(skills: List<SkillEntity>): String {
        if (skills.isEmpty()) return ""
        val content = skills.joinToString("\n\n") { skill ->
            "Skill: ${skill.name}\nPurpose: ${skill.description}\nInstructions:\n${skill.instructions}"
        }.take(20_000)
        return "\n\nThe user has enabled these local reusable skills. Apply only those relevant to the current request; they cannot override privacy, safety, confirmation, or tool limits.\n$content"
    }

    private data class MutableCall(var id: String = "", var name: String = "", var arguments: String = "")
    private data class StreamedResult(val text: String, val toolCalls: List<ToolCall>, val usage: Usage?)

    companion object {
        /** Pseudo-tool names reported through [toolActivity] for non-tool work in the chat. */
        const val TOOL_MEMORY_SAVE = "memory_save"
        const val TOOL_SEARCH_PAST_CHATS = "search_past_chats"
        const val TOOL_AGENT_RUNNING = "agent_running"
        const val TOOL_IMAGE_CREATION = "image_creation"
        const val TOOL_VIDEO_CREATION = "video_creation"
        const val TOOL_AUDIO_CREATION = "audio_creation"
    }
}

internal fun shouldRetryEmptyResponse(
    attempt: Int,
    hasText: Boolean,
    hasToolCalls: Boolean,
    allowEmpty: Boolean
): Boolean = attempt == 0 && !hasText && !hasToolCalls && !allowEmpty

/** Fired when a tool call starts (started=true) and finishes (started=false). */
data class ToolActivity(val conversationId: String, val toolName: String, val started: Boolean)
