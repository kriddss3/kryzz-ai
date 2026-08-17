package ai.daylight.assistant.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Test

class ApiParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun parsesStreamedContentToolFragmentsAndUsage() {
        val chunk = json.decodeFromString<ChatChunk>(
            """{"choices":[{"delta":{"content":"Hi","tool_calls":[{"index":0,"id":"call_1","function":{"name":"parallel_search","arguments":"{\"objective\":\"now\"}"}}]},"finish_reason":null}],"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12,"prompt_tokens_details":{"cached_tokens":6},"cost":0.0001}}"""
        )
        assertThat(extractResponseText(chunk.choices.single().delta.content)).isEqualTo("Hi")
        assertThat(chunk.choices.single().delta.toolCalls!!.single().function!!.name).isEqualTo("parallel_search")
        assertThat(chunk.usage!!.totalTokens).isEqualTo(12)
        assertThat(chunk.usage.promptTokensDetails!!.cachedTokens).isEqualTo(6)
        assertThat(chunk.usage.cost).isWithin(0.000001).of(0.0001)
    }

    @Test fun parsesTextFromStructuredAndNonStreamingCompletionShapes() {
        val structured = json.decodeFromString<ChatChunk>(
            """{"choices":[{"delta":{"content":[{"type":"text","text":"Hello "},{"type":"output_text","text":"there"}]}}]}"""
        )
        val nonStreaming = json.decodeFromString<ChatChunk>(
            """{"choices":[{"message":{"role":"assistant","content":"Fallback answer"},"finish_reason":"stop"}]}"""
        )
        assertThat(extractResponseText(structured.choices.single().delta.content)).isEqualTo("Hello there")
        assertThat(extractResponseText(nonStreaming.choices.single().message!!.content)).isEqualTo("Fallback answer")
    }

    @Test fun parsesParallelSearchResponse() {
        val response = json.decodeFromString<ParallelSearchResponse>(
            """{"search_id":"search_1","session_id":"session_1","results":[{"title":"Example","url":"https://example.com","publish_date":"2025-01-02","excerpts":["Grounded excerpt"]}]}"""
        )
        assertThat(response.sessionId).isEqualTo("session_1")
        assertThat(response.results.single().publishDate).isEqualTo("2025-01-02")
    }

    @Test fun parsesImageAndVideoGenerationResponses() {
        val image = json.decodeFromString<ImageGenerationResponse>(
            """{"data":[{"b64_json":"aW1hZ2U=","media_type":"image/png"}],"usage":{"total_tokens":44,"cost":0.02}}"""
        )
        val video = json.decodeFromString<VideoJobResponse>(
            """{"id":"job_1","status":"completed","unsigned_urls":["https://example.com/video.mp4"],"usage":{"cost":0.4}}"""
        )
        assertThat(image.data.single().mediaType).isEqualTo("image/png")
        assertThat(image.usage!!.totalTokens).isEqualTo(44)
        assertThat(video.status).isEqualTo("completed")
        assertThat(video.unsignedUrls.single()).endsWith("video.mp4")
    }

    @Test fun parsesTranscriptionAndFishTtsShapes() {
        val stt = json.decodeFromString<SttResponse>(
            """{"text":"Hello there","usage":{"seconds":2.5,"total_tokens":113,"cost":0.000508}}"""
        )
        assertThat(stt.text).isEqualTo("Hello there")
        assertThat(stt.usage!!.seconds).isWithin(0.001).of(2.5)
        assertThat(stt.usage!!.cost).isWithin(0.000001).of(0.000508)

        val encoded = json.encodeToString(FishTtsRequest(text = "Hi", referenceId = null, format = "mp3"))
        assertThat(encoded).contains("\"text\":\"Hi\"")
        assertThat(encoded).doesNotContain("reference_id")
        val withVoice = json.encodeToString(FishTtsRequest(text = "Hi", referenceId = "voice-1"))
        assertThat(withVoice).contains("\"reference_id\":\"voice-1\"")

        val error = json.decodeFromString<FishErrorEnvelope>("""{"status":402,"message":"No payment"}""")
        assertThat(error.status).isEqualTo(402)
    }

    @Test fun parsesDedicatedMediaCatalog() {
        val catalog = json.decodeFromString<MediaModelsEnvelope>(
            """{"data":[{"id":"provider/model","name":"Media model","supported_resolutions":["720p"],"supported_aspect_ratios":["16:9"]}]}"""
        )
        assertThat(catalog.data.single().supportedResolutions).containsExactly("720p")
    }

    @Test fun treatsNullMediaResolutionArraysAsEmpty() {
        val catalog = json.decodeFromString<MediaModelsEnvelope>(
            """{"data":[
              {"id":"ok/model","name":"Ok","supported_resolutions":["720p"],"supported_aspect_ratios":["16:9"]},
              {"id":"null/model","name":"Nulls","supported_resolutions":null,"supported_aspect_ratios":null}
            ]}"""
        )
        assertThat(catalog.data).hasSize(2)
        assertThat(catalog.data[1].id).isEqualTo("null/model")
        assertThat(catalog.data[1].supportedResolutions).isEmpty()
        assertThat(catalog.data[1].supportedAspectRatios).isEmpty()
    }

    @Test fun fishVoiceReplyRequestsPcmStream() {
        val encoded = json.encodeToString(
            FishTtsRequest(text = "Hi", format = "pcm", sampleRate = 44_100, latency = "balanced", chunkLength = 100)
        )
        assertThat(encoded).contains("\"format\":\"pcm\"")
        assertThat(encoded).contains("\"sample_rate\":44100")
        assertThat(encoded).contains("\"latency\":\"balanced\"")
    }

    @Test fun fishEmotionTextDisablesTheNormalizer() {
        val production = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
        val tagged = production.encodeToString(
            FishTtsRequest(text = "[happy] Hi", format = "pcm", normalize = false, sampleRate = 44_100)
        )
        assertThat(tagged).contains("\"normalize\":false")
        val plain = production.encodeToString(FishTtsRequest(text = "Hi", format = "mp3", normalize = true))
        assertThat(plain).doesNotContain("normalize")
    }

    @Test fun parsesGrokSttResponse() {
        val parsed = json.decodeFromString<SttResponse>(
            """{"text":"Hello there","language":"en","duration":3.45}"""
        )
        assertThat(parsed.text).isEqualTo("Hello there")
    }

    @Test fun parsesModelModalitiesAndReasoningOptions() {
        val catalog = json.decodeFromString<ModelsEnvelope>(
            """{"data":[{"id":"provider/model","architecture":{"input_modalities":["text","image","file"],"output_modalities":["text","image"]},"supported_parameters":["tools","reasoning"],"reasoning":{"supported_efforts":["high","medium","low"],"default_effort":"medium","mandatory":false}}]}"""
        )
        val model = catalog.data.single()
        assertThat(model.architecture!!.inputModalities).containsExactly("text", "image", "file").inOrder()
        assertThat(model.architecture.outputModalities).containsExactly("text", "image").inOrder()
        assertThat(model.reasoning!!.supportedEfforts).containsExactly("high", "medium", "low").inOrder()
    }

    @Test fun parsesOfficialIntelligenceCodingAndAgenticBenchmarks() {
        val catalog = json.decodeFromString<BenchmarksEnvelope>(
            """{"data":[{"model_permaslug":"openai/gpt-4o","display_name":"GPT-4o","intelligence_index":71.2,"coding_index":65.8,"agentic_index":58.3,"source":"artificial-analysis"}],"meta":{"as_of":"2026-06-03T12:00:00Z","source_url":"https://artificialanalysis.ai"}}"""
        )
        val score = catalog.data.single()
        assertThat(score.modelPermaslug).isEqualTo("openai/gpt-4o")
        assertThat(score.intelligenceIndex).isWithin(0.01).of(71.2)
        assertThat(score.codingIndex).isWithin(0.01).of(65.8)
        assertThat(score.agenticIndex).isWithin(0.01).of(58.3)
        assertThat(catalog.meta!!.asOf).startsWith("2026-06-03")
    }

    @Test fun serializesReasoningWithoutExposingReasoningTrace() {
        val request = ChatRequest(
            model = "provider/model",
            messages = listOf(ApiMessage("user", "Hello")),
            reasoning = ReasoningConfig(effort = "high", exclude = true)
        )
        val encoded = json.encodeToString(request)
        assertThat(encoded).contains("\"reasoning\":{\"effort\":\"high\"")
        assertThat(encoded).contains("\"exclude\":true")
    }

    @Test fun serializesSpeechRequestForMp3Output() {
        val encoded = json.encodeToString(
            SpeechGenerationRequest(
                model = "openai/gpt-4o-mini-tts-2025-12-15",
                input = "Welcome to Kryzz AI",
                voice = "nova",
                responseFormat = "mp3",
                speed = 1.0
            )
        )
        assertThat(encoded).contains("\"response_format\":\"mp3\"")
        assertThat(encoded).contains("\"voice\":\"nova\"")
        assertThat(encoded).doesNotContain("api_key")
    }

    @Test fun serializesMultimodalFileContentWithoutChangingProviderCredentials() {
        val content = buildJsonArray {
            add(buildJsonObject { put("type", "text"); put("text", "Analyse these") })
            add(buildJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") { put("url", "data:image/png;base64,aW1hZ2U=") }
            })
            add(buildJsonObject {
                put("type", "file")
                putJsonObject("file") {
                    put("filename", "notes.pdf")
                    put("file_data", "data:application/pdf;base64,cGRm")
                }
            })
        }
        val encoded = json.encodeToString(ChatRequest("provider/vision", listOf(ApiMessage(role = "user", content = content))))
        assertThat(encoded).contains("\"type\":\"image_url\"")
        assertThat(encoded).contains("\"type\":\"file\"")
        assertThat(encoded).contains("notes.pdf")
        assertThat(encoded).doesNotContain("api_key")
    }
}
