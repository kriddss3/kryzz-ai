package ai.daylight.assistant.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class ApiMessage(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
) {
    constructor(
        role: String,
        text: String?,
        toolCallId: String? = null,
        name: String? = null,
        toolCalls: List<ToolCall>? = null
    ) : this(role, text?.let(::JsonPrimitive), toolCallId, name, toolCalls)
}

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val reasoning: ReasoningConfig? = null,
    val provider: ProviderPreferences? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

/**
 * OpenRouter provider routing overrides. sort = "latency" makes OpenRouter pick the
 * lowest-latency provider serving the model; fallbacks keep the request working when
 * the fastest route is down or rate-limited.
 */
@Serializable
data class ProviderPreferences(
    val sort: String = "latency",
    @SerialName("allow_fallbacks") val allowFallbacks: Boolean = true
)

@Serializable
data class ReasoningConfig(
    val effort: String? = null,
    val enabled: Boolean? = null,
    val exclude: Boolean? = null
)

@Serializable
data class ToolDefinition(val type: String = "function", val function: FunctionDefinition)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(val name: String, val arguments: String)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val function: FunctionCallDelta? = null
)

@Serializable
data class FunctionCallDelta(val name: String? = null, val arguments: String? = null)

@Serializable
data class ChatChunk(
    val choices: List<ChunkChoice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null,
    val model: String? = null
)

@Serializable
data class ChunkChoice(
    val delta: ChunkDelta = ChunkDelta(),
    val message: ChunkMessage? = null,
    val text: JsonElement? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    val error: ApiError? = null
)

@Serializable
data class ChunkDelta(val content: JsonElement? = null, @SerialName("tool_calls") val toolCalls: List<ToolCallDelta>? = null)

@Serializable
data class ChunkMessage(
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null,
    val cost: Double? = null
)

@Serializable
data class PromptTokensDetails(
    @SerialName("cached_tokens") val cachedTokens: Int? = null
)

@Serializable
data class ApiError(val code: Int? = null, val message: String = "Unknown service error")

@Serializable
data class ErrorEnvelope(val error: ApiError? = null)

@Serializable
data class ModelsEnvelope(val data: List<OpenRouterModel> = emptyList())

@Serializable
data class BenchmarksEnvelope(
    val data: List<ModelBenchmark> = emptyList(),
    val meta: BenchmarkMeta? = null
)

@Serializable
data class ModelBenchmark(
    @SerialName("model_permaslug") val modelPermaslug: String,
    @SerialName("display_name") val displayName: String = modelPermaslug,
    @SerialName("intelligence_index") val intelligenceIndex: Double? = null,
    @SerialName("coding_index") val codingIndex: Double? = null,
    @SerialName("agentic_index") val agenticIndex: Double? = null,
    val source: String? = null
)

@Serializable
data class BenchmarkMeta(
    @SerialName("as_of") val asOf: String? = null,
    val citation: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null
)

@Serializable
data class OpenRouterModel(
    val id: String,
    @SerialName("canonical_slug") val canonicalSlug: String? = null,
    val name: String = id,
    val description: String = "",
    val created: Long? = null,
    @SerialName("knowledge_cutoff") val knowledgeCutoff: String? = null,
    @SerialName("expiration_date") val expirationDate: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    val pricing: ModelPricing? = null,
    val architecture: ModelArchitecture? = null,
    @SerialName("top_provider") val topProvider: TopProvider? = null,
    val reasoning: ModelReasoning? = null,
    @SerialName("supported_parameters") val supportedParameters: List<String> = emptyList()
)

@Serializable
data class ModelArchitecture(
    val modality: String? = null,
    val tokenizer: String? = null,
    @SerialName("instruct_type") val instructType: String? = null,
    @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    @SerialName("output_modalities") val outputModalities: List<String> = emptyList()
)

@Serializable
data class TopProvider(
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    @SerialName("is_moderated") val isModerated: Boolean? = null
)

@Serializable
data class ModelReasoning(
    @SerialName("supported_efforts") val supportedEfforts: List<String>? = null,
    @SerialName("default_effort") val defaultEffort: String? = null,
    @SerialName("default_enabled") val defaultEnabled: Boolean? = null,
    @SerialName("supports_max_tokens") val supportsMaxTokens: Boolean = false,
    val mandatory: Boolean = false
)

@Serializable
data class ModelPricing(
    val prompt: String? = null,
    val completion: String? = null,
    val request: String? = null,
    val image: String? = null,
    val audio: String? = null,
    @SerialName("web_search") val webSearch: String? = null,
    @SerialName("input_cache_read") val inputCacheRead: String? = null,
    @SerialName("input_cache_write") val inputCacheWrite: String? = null,
    @SerialName("internal_reasoning") val internalReasoning: String? = null
)

@Serializable
data class MediaModelsEnvelope(val data: List<MediaModel> = emptyList())

@Serializable
data class MediaModel(
    val id: String,
    val name: String = id,
    val description: String = "",
    @SerialName("supports_streaming") val supportsStreaming: Boolean = false,
    @Serializable(with = NullAsEmptyStringListSerializer::class)
    @SerialName("supported_resolutions") val supportedResolutions: List<String> = emptyList(),
    @Serializable(with = NullAsEmptyStringListSerializer::class)
    @SerialName("supported_aspect_ratios") val supportedAspectRatios: List<String> = emptyList()
)

/** OpenRouter sometimes sends JSON null for these arrays; treat that as empty. */
internal object NullAsEmptyStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(delegate)
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return emptyList()
        return jsonDecoder.json.decodeFromJsonElement(delegate, element)
    }
    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

@Serializable
data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    @SerialName("output_format") val outputFormat: String = "png"
)

@Serializable
data class ImageGenerationResponse(
    val data: List<GeneratedImageData> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class GeneratedImageData(
    @SerialName("b64_json") val base64: String,
    @SerialName("media_type") val mediaType: String? = null
)

@Serializable
data class VideoGenerationRequest(
    val model: String,
    val prompt: String,
    @SerialName("aspect_ratio") val aspectRatio: String = "16:9",
    val duration: Int = 5,
    val resolution: String = "720p"
)

@Serializable
data class SpeechGenerationRequest(
    val model: String,
    val input: String,
    val voice: String,
    @SerialName("response_format") val responseFormat: String,
    val speed: Double
)

@Serializable
data class SttResponse(val text: String = "", val usage: SttUsage? = null)

@Serializable
data class SttUsage(
    val seconds: Double? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    val cost: Double? = null
)

@Serializable
data class FishTtsRequest(
    val text: String,
    @SerialName("reference_id") val referenceId: String? = null,
    val format: String = "mp3",
    @SerialName("chunk_length") val chunkLength: Int = 200,
    val latency: String = "normal",
    val prosody: FishProsody = FishProsody(),
    val normalize: Boolean = true,
    @SerialName("sample_rate") val sampleRate: Int? = null
)

@Serializable
data class FishProsody(
    val speed: Double = 1.0,
    @SerialName("normalize_loudness") val normalizeLoudness: Boolean = true
)

@Serializable
data class FishErrorEnvelope(val status: Int? = null, val message: String? = null)

@Serializable
data class VideoJobResponse(
    val id: String,
    val status: String = "pending",
    @SerialName("polling_url") val pollingUrl: String? = null,
    @SerialName("generation_id") val generationId: String? = null,
    @SerialName("unsigned_urls") val unsignedUrls: List<String> = emptyList(),
    val error: String? = null,
    val usage: Usage? = null
)

@Serializable
data class ParallelSearchRequest(
    val objective: String? = null,
    @SerialName("search_queries") val searchQueries: List<String>,
    val mode: String? = null,
    @SerialName("max_chars_total") val maxCharsTotal: Int,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("client_model") val clientModel: String? = null
)

@Serializable
data class ParallelSearchResponse(
    @SerialName("search_id") val searchId: String,
    val results: List<ParallelResult> = emptyList(),
    @SerialName("session_id") val sessionId: String,
    val warnings: List<ParallelWarning>? = null
)

@Serializable
data class ParallelResult(
    val url: String,
    val title: String,
    @SerialName("publish_date") val publishDate: String? = null,
    val excerpts: List<String> = emptyList()
)

@Serializable
data class ParallelWarning(val message: String = "")

@Serializable
data class ParallelErrorEnvelope(val type: String? = null, val error: ParallelError? = null)

@Serializable
data class ParallelError(@SerialName("ref_id") val refId: String? = null, val message: String = "Search failed")

@Serializable
data class ParallelSearchArgs(
    val objective: String? = null,
    @SerialName("search_queries") val searchQueries: List<String>,
    val mode: String? = null
)

@Serializable
data class CreateArtifactArgs(val type: String, val title: String, val content: String)

@Serializable
data class CreateSkillArgs(
    val name: String,
    val description: String,
    val instructions: String,
    @SerialName("example_prompts") val examplePrompts: List<String> = emptyList()
)

@Serializable
data class CodeProjectFile(val path: String, val content: String)

@Serializable
data class CreateCodeProjectArgs(
    val title: String,
    val description: String = "",
    val files: List<CodeProjectFile>
)

@Serializable
data class SearchPastChatsArgs(val query: String)

sealed interface StreamEvent {
    data class Delta(val text: String, val toolCalls: List<ToolCallDelta> = emptyList()) : StreamEvent
    data class UsageUpdate(val usage: Usage) : StreamEvent
    data class Failure(val error: AssistantApiException) : StreamEvent
    data object Done : StreamEvent
}

class AssistantApiException(
    val kind: ErrorKind,
    override val message: String,
    val statusCode: Int? = null,
    val retryAfterSeconds: Long? = null
) : Exception(message)

enum class ErrorKind { INVALID_KEY, INSUFFICIENT_CREDITS, RATE_LIMIT, TIMEOUT, MODEL_UNAVAILABLE, CONTEXT_LENGTH, VALIDATION, NETWORK, CANCELLED, UNKNOWN }
