package ai.daylight.assistant.data.remote

import ai.daylight.assistant.domain.ChatProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

class OpenAiCompatibleClientTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun fromResolvesEveryProviderByNameAndIdCaseInsensitively() {
        ChatProvider.entries.forEach { provider ->
            assertThat(ChatProvider.from(provider.name)).isEqualTo(provider)
            assertThat(ChatProvider.from(provider.name.lowercase())).isEqualTo(provider)
            assertThat(ChatProvider.from(provider.id)).isEqualTo(provider)
            assertThat(ChatProvider.from(provider.id.uppercase())).isEqualTo(provider)
            assertThat(ChatProvider.from("  ${provider.id}  ")).isEqualTo(provider)
        }
        assertThat(ChatProvider.from(null)).isEqualTo(ChatProvider.OPENROUTER)
        assertThat(ChatProvider.from("")).isEqualTo(ChatProvider.OPENROUTER)
        assertThat(ChatProvider.from("unknown-vendor")).isEqualTo(ChatProvider.OPENROUTER)
    }

    @Test fun minimaxLabelMarksTokenPlanOnly() {
        assertThat(ChatProvider.MINIMAX.label).isEqualTo("MiniMax (token plan only)")
    }

    @Test fun fallbackModelsExposeToolsCapabilityAndMatchProviderFallbackIds() {
        listOf(ChatProvider.DEEPSEEK, ChatProvider.OPENCODE, ChatProvider.PERPLEXITY).forEach { provider ->
            val client = OpenAiCompatibleClient(OkHttpClient(), json, provider)
            val fallback = client.fallbackModels()
            assertThat(fallback.map { it.id }).containsExactlyElementsIn(provider.fallbackModelIds).inOrder()
            assertThat(fallback.map { it.name }).containsExactlyElementsIn(provider.fallbackModelIds).inOrder()
            fallback.forEach { model ->
                assertThat(model.supportedParameters).contains("tools")
            }
        }
    }

    @Test fun sanitizeRequestStripsProviderAndReasoningOnly() {
        val client = OpenAiCompatibleClient(OkHttpClient(), json, ChatProvider.DEEPSEEK)
        val original = ChatRequest(
            model = "deepseek-chat",
            messages = listOf(ApiMessage(role = "user", text = "Hello")),
            stream = true,
            tools = listOf(
                ToolDefinition(
                    function = FunctionDefinition(
                        name = "get_weather",
                        description = "Weather",
                        parameters = ToolChoice.AUTO,
                    )
                )
            ),
            toolChoice = ToolChoice.AUTO,
            reasoning = ReasoningConfig(effort = "high", exclude = true),
            provider = ProviderPreferences(sort = "latency", ignore = listOf("together")),
            maxTokens = 512,
        )

        val sanitized = client.sanitizeRequest(original)

        assertThat(sanitized.provider).isNull()
        assertThat(sanitized.reasoning).isNull()
        assertThat(sanitized.model).isEqualTo("deepseek-chat")
        assertThat(sanitized.messages).isEqualTo(original.messages)
        assertThat(sanitized.stream).isTrue()
        assertThat(sanitized.tools).isEqualTo(original.tools)
        assertThat(sanitized.toolChoice).isEqualTo(ToolChoice.AUTO)
        assertThat(sanitized.maxTokens).isEqualTo(512)
    }

    @Test fun testKeyRejectsBlankKeyWithoutNetworkCall() = runTest {
        var called = false
        val http = clientWithInterceptor {
            called = true
            jsonResponse(it.request(), 200, "{}")
        }
        val client = OpenAiCompatibleClient(http, json, ChatProvider.DEEPSEEK)

        val result = client.testKey("   ")

        assertThat(result.isFailure).isTrue()
        assertThat(called).isFalse()
    }

    @Test fun testKeyFallsBackToChatPingOn404AndSkipsModelsWhenUnsupported() = runTest {
        val requests = mutableListOf<Pair<String, String>>()
        val fallbackHttp = clientWithInterceptor { chain ->
            val req = chain.request()
            requests += req.method to req.url.toString()
            if (req.method == "GET") jsonResponse(req, 404, """{"error":{"message":"Not found"}}""")
            else jsonResponse(req, 200, """{"choices":[{"message":{"content":"ok"}}]}""")
        }
        val opencode = OpenAiCompatibleClient(fallbackHttp, json, ChatProvider.OPENCODE)
        assertThat(opencode.testKey("sk-test-1234567890").isSuccess).isTrue()
        assertThat(requests).containsExactly(
            "GET" to "https://opencode.ai/zen/v1/models",
            "POST" to "https://opencode.ai/zen/v1/chat/completions",
        ).inOrder()

        requests.clear()
        val perplexity = OpenAiCompatibleClient(fallbackHttp, json, ChatProvider.PERPLEXITY)
        assertThat(perplexity.testKey("pplx-1234567890").isSuccess).isTrue()
        assertThat(requests).containsExactly(
            "POST" to "https://api.perplexity.ai/chat/completions",
        )
    }

    @Test fun testKeyReturnsInvalidKeyFailureOn401() = runTest {
        val http = clientWithInterceptor { chain ->
            jsonResponse(chain.request(), 401, """{"error":{"message":"Invalid key"}}""")
        }
        val client = OpenAiCompatibleClient(http, json, ChatProvider.DEEPSEEK)

        val result = client.testKey("sk-bad-1234567890")

        assertThat(result.isFailure).isTrue()
        val error = result.exceptionOrNull() as? AssistantApiException
        assertThat(error?.kind).isEqualTo(ErrorKind.INVALID_KEY)
    }

    @Test fun modelsMergesMissingFallbacksAndFallsBackWhenKeyMissingOrFailing() = runTest {
        val successHttp = clientWithInterceptor { chain ->
            jsonResponse(chain.request(), 200, """{"data":[{"id":"deepseek-chat"},{"id":"custom-model"}]}""")
        }
        val deepseek = OpenAiCompatibleClient(successHttp, json, ChatProvider.DEEPSEEK)

        val fetched = deepseek.models("sk-ok-1234567890")
        assertThat(fetched.map { it.id }).containsExactly("custom-model", "deepseek-chat", "deepseek-reasoner").inOrder()

        assertThat(deepseek.models(null).map { it.id }).containsExactlyElementsIn(ChatProvider.DEEPSEEK.fallbackModelIds).inOrder()

        val failingHttp = clientWithInterceptor { chain ->
            jsonResponse(chain.request(), 500, """{"error":{"message":"Server error"}}""")
        }
        val failingClient = OpenAiCompatibleClient(failingHttp, json, ChatProvider.DEEPSEEK)
        assertThat(failingClient.models("sk-ok-1234567890").map { it.id })
            .containsExactlyElementsIn(ChatProvider.DEEPSEEK.fallbackModelIds).inOrder()
    }

    @Test fun streamSanitizesRequestAndParsesSseChunks() = runTest {
        var sentBody = ""
        var sentUrl = ""
        val http = clientWithInterceptor { chain ->
            val req = chain.request()
            sentUrl = req.url.toString()
            val buffer = Buffer()
            req.body?.writeTo(buffer)
            sentBody = buffer.readUtf8()
            val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\ndata: [DONE]\n\n"
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(sse.toResponseBody("text/event-stream".toMediaType()))
                .build()
        }
        val client = OpenAiCompatibleClient(http, json, ChatProvider.DEEPSEEK)
        val events = client.stream(
            "sk-ds-1234567890",
            ChatRequest(
                model = "deepseek-chat",
                messages = listOf(ApiMessage("user", "Hi")),
                reasoning = ReasoningConfig(effort = "high"),
                provider = ProviderPreferences(sort = "latency"),
            ),
        ).toList()

        assertThat(sentUrl).isEqualTo("https://api.deepseek.com/v1/chat/completions")
        assertThat(sentBody).doesNotContain("\"provider\"")
        assertThat(sentBody).doesNotContain("\"reasoning\"")
        assertThat(events).containsExactly(
            StreamEvent.Delta(text = "Hi"),
            StreamEvent.Done,
        ).inOrder()
    }

    private fun clientWithInterceptor(handler: (Interceptor.Chain) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor(handler)).build()

    private fun jsonResponse(request: okhttp3.Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
}
