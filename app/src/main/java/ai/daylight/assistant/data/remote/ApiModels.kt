package ai.daylight.assistant.data.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    // MiniMax defaults stream=false when the key is omitted. encodeDefaults=false
    // would drop this and the UI would only paint after the full completion.
    @EncodeDefault val stream: Boolean = true,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
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

/** OpenAI-style tool_choice values, including MiniMax's named-function object. */
object ToolChoice {
    val AUTO: JsonElement = JsonPrimitive("auto")
    val REQUIRED: JsonElement = JsonPrimitive("required")
    fun named(name: String): JsonElement = buildJsonObject {
        put("type", "function")
        putJsonObject("function") { put("name", name) }
    }
}

@Serializable
data class ReasoningConfig(
    val effort: String? = null,
    val enabled: Boolean? = null,
    val exclude: Boolean? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ToolDefinition(
    // MiniMax 2013 "invalid tool type" when this key is omitted (encodeDefaults=false).
    @EncodeDefault val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ToolCall(
    val id: String,
    @EncodeDefault val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    @Serializable(with = JsonStringOrObjectSerializer::class) val arguments: String
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val function: FunctionCallDelta? = null
)

@Serializable
data class FunctionCallDelta(
    val name: String? = null,
    @Serializable(with = NullableJsonStringOrObjectSerializer::class) val arguments: String? = null
)

/** MiniMax sometimes streams `arguments` as an object instead of a JSON string. */
internal object JsonStringOrObjectSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JsonStringOrObject", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.content
            JsonNull -> ""
            else -> element.toString()
        }
    }
}

internal object NullableJsonStringOrObjectSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableJsonStringOrObject", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> null
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }
}

@Serializable
data class ChatChunk(
    val choices: List<ChunkChoice> = emptyList(),
    val usage: Usage? = null,
    val error: ApiError? = null,
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp? = null,
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

@Serializable
data class CalculateArgs(val expression: String)

@Serializable
data class WeatherArgs(val place: String? = null)

@Serializable
data class FetchUrlArgs(val url: String)

@Serializable
data class RememberFactArgs(val fact: String, val category: String? = null)

@Serializable
data class RecallMemoriesArgs(val query: String)

@Serializable
data class ScheduleTaskArgs(
    val title: String,
    val prompt: String,
    val hour: Int,
    val minute: Int = 0,
    val recurrence: String = "daily",
    @SerialName("days_of_week") val daysOfWeek: List<Int> = emptyList()
)

// ── MiniMax request/response shapes ─────────────────────────────────────────

@Serializable
data class MiniMaxImageRequest(
    val model: String,
    val prompt: String,
    // No Kotlin defaults: the app Json uses encodeDefaults=false, so a defaulted
    // response_format="base64" was omitted, MiniMax returned URLs, and the client
    // only read image_base64 — generation looked like it failed with a garbled model tag.
    @SerialName("aspect_ratio") val aspectRatio: String,
    @SerialName("response_format") val responseFormat: String,
    val n: Int
)

@Serializable
data class MiniMaxImageResponse(
    val data: MiniMaxImageData? = null,
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp? = null
)

@Serializable
data class MiniMaxImageData(
    @SerialName("image_base64") val imageBase64: List<String> = emptyList(),
    @SerialName("image_urls") val imageUrls: List<String> = emptyList()
)

@Serializable
data class MiniMaxVideoRequest(
    val model: String,
    val content: List<MiniMaxVideoContentItem>,
    // Required by /v2/video_generation. Must not be defaulted — encodeDefaults=false
    // would drop duration/resolution/ratio when they equal the Kotlin defaults.
    val duration: Int,
    val resolution: String,
    val ratio: String
)

@Serializable
data class MiniMaxVideoContentItem(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: MiniMaxImageUrl? = null,
    val role: String? = null
)

@Serializable
data class MiniMaxImageUrl(val url: String)

@Serializable
data class MiniMaxVideoSubmitResponse(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp? = null
)

@Serializable
data class MiniMaxVideoStatusResponse(
    val task: MiniMaxVideoTask? = null,
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp? = null
)

@Serializable
data class MiniMaxVideoTask(
    @SerialName("task_id") val taskId: String? = null,
    val status: String = "pending",
    val content: MiniMaxVideoContent? = null,
    val error: String? = null
)

@Serializable
data class MiniMaxVideoContent(val url: String = "")

@Serializable
data class MiniMaxMusicRequest(
    val model: String,
    val prompt: String,
    val lyrics: String = "",
    @SerialName("output_format") val outputFormat: String = "url",
    @SerialName("is_instrumental") val isInstrumental: Boolean = true,
    @SerialName("audio_setting") val audioSetting: MiniMaxAudioSetting? = null
)

@Serializable
data class MiniMaxAudioSetting(
    @SerialName("sample_rate") val sampleRate: Int = 44100,
    val bitrate: Int = 256000,
    val format: String = "mp3"
)

@Serializable
data class MiniMaxMusicResponse(
    val data: MiniMaxMusicData? = null,
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp? = null
)

@Serializable
data class MiniMaxMusicData(
    val audio: String = "",
    val status: Int? = null,
    @SerialName("audio_url") val audioUrl: String? = null
)

@Serializable
data class MiniMaxTtsRequest(
    val model: String,
    val text: String,
    @SerialName("voice_id") val voiceId: String = "Wise_Woman",
    @SerialName("response_format") val responseFormat: String = "mp3",
    val speed: Double = 1.0
)

@Serializable
data class MiniMaxTtsResponse(
    val data: MiniMaxTtsData? = null,
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp? = null
)

@Serializable
data class MiniMaxTtsData(
    @SerialName("audio_url") val audioUrl: String? = null,
    @SerialName("audio_base64") val audioBase64: String? = null
)

@Serializable
data class MiniMaxBaseResp(
    @SerialName("status_code") val statusCode: Int? = null,
    @SerialName("status_msg") val statusMsg: String? = null
)

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
