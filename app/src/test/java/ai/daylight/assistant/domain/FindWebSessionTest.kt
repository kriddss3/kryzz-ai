package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.FunctionCallDelta
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.data.remote.ParallelClient
import ai.daylight.assistant.data.remote.ParallelResult
import ai.daylight.assistant.data.remote.ParallelSearchRequest
import ai.daylight.assistant.data.remote.ParallelSearchResponse
import ai.daylight.assistant.data.remote.PublicWebClient
import ai.daylight.assistant.data.remote.StreamEvent
import ai.daylight.assistant.data.remote.TextProviderClient
import ai.daylight.assistant.data.remote.ToolCallDelta
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FindWebSessionTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    private fun fakeParallelHttp(
        recorded: MutableList<ParallelSearchRequest>,
        onSearch: (ParallelSearchRequest) -> ParallelSearchResponse,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            val parsed = json.decodeFromString<ParallelSearchRequest>(buffer.readUtf8())
            recorded += parsed
            val bodyJson = json.encodeToString(onSearch(parsed))
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(bodyJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
        .build()

    private fun fakePublicWebHttp(pages: Map<String, String> = emptyMap()): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val html = pages[url] ?: "<html><title>Page</title><body>Default page text</body></html>"
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }
        .build()

    private class FakeTextClient(
        private val onStream: (ChatRequest) -> List<StreamEvent>,
    ) : TextProviderClient {
        val requests = mutableListOf<ChatRequest>()
        override suspend fun testKey(key: String) = Result.success(Unit)
        override suspend fun models(key: String?) = emptyList<OpenRouterModel>()
        override fun stream(key: String, request: ChatRequest): Flow<StreamEvent> = flow {
            requests += request
            onStream(request).forEach { emit(it) }
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.awaitSettled(session: FindWebSession) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (
            session.state.value.status == FindWebStatus.SEARCHING ||
            session.state.value.status == FindWebStatus.SYNTHESIZING
        ) {
            runCurrent()
            if (session.state.value.status != FindWebStatus.SEARCHING &&
                session.state.value.status != FindWebStatus.SYNTHESIZING
            ) break
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        runCurrent()
    }

    @Test
    fun missingParallelKeyEmitsFailed() = runTest(StandardTestDispatcher()) {
        val parallelCalls = mutableListOf<ParallelSearchRequest>()
        val session = FindWebSession(
            scope = backgroundScope,
            parallel = ParallelClient(fakeParallelHttp(parallelCalls) { error("unused") }, json),
            publicWeb = PublicWebClient(fakePublicWebHttp(), json),
            parallelKey = { null },
            resolveCheapModel = { null },
            json = json,
        )

        session.search("kotlin coroutines")
        runCurrent()

        assertThat(session.state.value.status).isEqualTo(FindWebStatus.FAILED)
        assertThat(session.state.value.error).isEqualTo(FindWebSession.MISSING_PARALLEL_KEY_ERROR)
        assertThat(parallelCalls).isEmpty()
    }

    @Test
    fun toolCallingParallelAndCheapModelProducesSynthesizedAnswerAndSources() = runTest(StandardTestDispatcher()) {
        val parallelCalls = mutableListOf<ParallelSearchRequest>()
        val parallel = ParallelClient(
            fakeParallelHttp(parallelCalls) {
                ParallelSearchResponse(
                    searchId = "s-1",
                    sessionId = "sess-1",
                    results = listOf(
                        ParallelResult(
                            url = "https://www.kotlinlang.org/docs/coroutines-overview.html",
                            title = "Coroutines overview",
                            publishDate = "2026-01-10",
                            excerpts = listOf("Coroutines are lightweight threads."),
                        ),
                        // Duplicate URL with trailing slash should deduplicate cleanly
                        ParallelResult(
                            url = "https://kotlinlang.org/docs/coroutines-overview.html/",
                            title = "Duplicate",
                            publishDate = null,
                            excerpts = listOf("Duplicate excerpt."),
                        ),
                    ),
                )
            },
            json,
        )
        val publicWeb = PublicWebClient(
            fakePublicWebHttp(
                mapOf(
                    "https://www.kotlinlang.org/docs/coroutines-overview.html" to
                        "<html><title>Coroutines</title><body><p>Structured concurrency keeps tasks scoped.</p></body></html>"
                )
            ),
            json,
        )
        var round = 0
        val client = FakeTextClient {
            round++
            when (round) {
                1 -> listOf(
                    StreamEvent.Delta(
                        text = "",
                        toolCalls = listOf(
                            ToolCallDelta(
                                index = 0,
                                id = "call_1",
                                function = FunctionCallDelta(
                                    name = "parallel_search",
                                    arguments = """{"objective":"kotlin coroutines","search_queries":["kotlin coroutines overview"]}""",
                                ),
                            )
                        ),
                    ),
                    StreamEvent.Done,
                )
                2 -> listOf(
                    StreamEvent.Delta(
                        text = "",
                        toolCalls = listOf(
                            ToolCallDelta(
                                index = 0,
                                id = "call_2",
                                function = FunctionCallDelta(
                                    name = "fetch_url",
                                    arguments = """{"url":"https://www.kotlinlang.org/docs/coroutines-overview.html"}""",
                                ),
                            )
                        ),
                    ),
                    StreamEvent.Done,
                )
                else -> listOf(
                    StreamEvent.Delta("Coroutines provide lightweight structured concurrency [1]."),
                    StreamEvent.Done,
                )
            }
        }
        val session = FindWebSession(
            scope = backgroundScope,
            parallel = parallel,
            publicWeb = publicWeb,
            parallelKey = { "parallel-key" },
            resolveCheapModel = {
                CheapModelTarget(
                    providerLabel = "OpenRouter",
                    modelId = "openai/gpt-4o-mini",
                    apiKey = "or-key",
                    client = client,
                    useOpenRouterPriceSort = true,
                )
            },
            json = json,
        )

        session.search("kotlin coroutines")
        awaitSettled(session)

        val done = session.state.value
        assertThat(done.status).isEqualTo(FindWebStatus.DONE)
        assertThat(done.result?.answer).isEqualTo("Coroutines provide lightweight structured concurrency [1].")
        assertThat(done.result?.modelLabel).isEqualTo("OpenRouter · gpt-4o-mini")
        assertThat(done.result?.sources).hasSize(1)
        val source = done.result?.sources?.single()!!
        assertThat(source.title).isEqualTo("Coroutines overview")
        assertThat(source.domain).isEqualTo("kotlinlang.org")
        assertThat(source.publishDate).isEqualTo("2026-01-10")
        assertThat(parallelCalls).hasSize(1)
        assertThat(client.requests).hasSize(3)
        assertThat(client.requests.first().provider?.sort).isEqualTo("price")
    }

    @Test
    fun modelSkippingToolsFallsBackToParallelAndPlainSynthesis() = runTest(StandardTestDispatcher()) {
        val parallelCalls = mutableListOf<ParallelSearchRequest>()
        val parallel = ParallelClient(
            fakeParallelHttp(parallelCalls) {
                ParallelSearchResponse(
                    searchId = "s-2",
                    sessionId = "sess-2",
                    results = listOf(
                        ParallelResult(
                            url = "https://example.org/news",
                            title = "Example News",
                            publishDate = "2026-11-01",
                            excerpts = listOf("Fresh factual snippet."),
                        )
                    ),
                )
            },
            json,
        )
        val client = FakeTextClient { request ->
            if (request.tools != null) {
                // Model ignores tools on round 1 and answers without searching
                listOf(StreamEvent.Delta("Guessing from memory."), StreamEvent.Done)
            } else {
                // Plain synthesis fallback (tools == null)
                listOf(StreamEvent.Delta("Synthesized from Parallel [1]."), StreamEvent.Done)
            }
        }
        val session = FindWebSession(
            scope = backgroundScope,
            parallel = parallel,
            publicWeb = PublicWebClient(fakePublicWebHttp(), json),
            parallelKey = { "parallel-key" },
            resolveCheapModel = {
                CheapModelTarget("MiniMax", "MiniMax-M2", "mm-key", client, useOpenRouterPriceSort = false)
            },
            json = json,
        )

        session.search("latest example news")
        awaitSettled(session)

        val done = session.state.value
        assertThat(done.status).isEqualTo(FindWebStatus.DONE)
        assertThat(done.result?.answer).isEqualTo("Synthesized from Parallel [1].")
        assertThat(done.result?.sources).hasSize(1)
        assertThat(parallelCalls).hasSize(1)
        assertThat(client.requests).hasSize(2)
        assertThat(client.requests[0].tools).isNotNull()
        assertThat(client.requests[1].tools).isNull()
        assertThat(client.requests[1].provider).isNull()
    }

    @Test
    fun noChatKeyUsesParallelOnlyAndEmitsDone() = runTest(StandardTestDispatcher()) {
        val parallelCalls = mutableListOf<ParallelSearchRequest>()
        val parallel = ParallelClient(
            fakeParallelHttp(parallelCalls) {
                ParallelSearchResponse(
                    searchId = "s-3",
                    sessionId = "sess-3",
                    results = listOf(
                        ParallelResult(
                            url = "https://docs.android.com/compose",
                            title = "Compose Docs",
                            publishDate = null,
                            excerpts = listOf("Jetpack Compose builds native UI."),
                        )
                    ),
                )
            },
            json,
        )
        val session = FindWebSession(
            scope = backgroundScope,
            parallel = parallel,
            publicWeb = PublicWebClient(fakePublicWebHttp(), json),
            parallelKey = { "parallel-key" },
            resolveCheapModel = { null },
            json = json,
        )

        session.search("jetpack compose")
        awaitSettled(session)

        val done = session.state.value
        assertThat(done.status).isEqualTo(FindWebStatus.DONE)
        assertThat(done.result?.answer).isEqualTo(FindWebSession.NO_CHAT_KEY_SUMMARY)
        assertThat(done.result?.modelLabel).isNull()
        assertThat(done.result?.sources).hasSize(1)
        assertThat(done.result?.sources?.single()?.domain).isEqualTo("docs.android.com")
        assertThat(parallelCalls).hasSize(1)
    }

    @Test
    fun toolCallingFailureFallsBackToParallelAndPlainSynthesis() = runTest(StandardTestDispatcher()) {
        val parallelCalls = mutableListOf<ParallelSearchRequest>()
        val parallel = ParallelClient(
            fakeParallelHttp(parallelCalls) {
                ParallelSearchResponse(
                    searchId = "s-4",
                    sessionId = "sess-4",
                    results = listOf(
                        ParallelResult(
                            url = "https://example.net/guide",
                            title = "Guide",
                            publishDate = null,
                            excerpts = listOf("Step-by-step guide snippet."),
                        )
                    ),
                )
            },
            json,
        )
        val client = FakeTextClient { request ->
            if (request.tools != null) {
                listOf(
                    StreamEvent.Failure(
                        ai.daylight.assistant.data.remote.AssistantApiException(
                            ai.daylight.assistant.data.remote.ErrorKind.VALIDATION,
                            "Tools not supported on this route",
                        )
                    )
                )
            } else {
                listOf(StreamEvent.Delta("Fallback answer [1]."), StreamEvent.Done)
            }
        }
        val session = FindWebSession(
            scope = backgroundScope,
            parallel = parallel,
            publicWeb = PublicWebClient(fakePublicWebHttp(), json),
            parallelKey = { "parallel-key" },
            resolveCheapModel = {
                CheapModelTarget("OpenRouter", "meta-llama/llama-3.1-8b-instruct", "or-key", client, useOpenRouterPriceSort = true)
            },
            json = json,
        )

        session.search("step by step guide")
        awaitSettled(session)

        val done = session.state.value
        assertThat(done.status).isEqualTo(FindWebStatus.DONE)
        assertThat(done.result?.answer).isEqualTo("Fallback answer [1].")
        assertThat(done.result?.sources).hasSize(1)
        assertThat(parallelCalls).hasSize(1)
        assertThat(client.requests).hasSize(2)
        assertThat(client.requests[1].tools).isNull()
    }
}
