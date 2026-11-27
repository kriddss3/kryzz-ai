package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ApiMessage
import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.ErrorKind
import ai.daylight.assistant.data.remote.FetchUrlArgs
import ai.daylight.assistant.data.remote.FetchedPage
import ai.daylight.assistant.data.remote.FunctionCall
import ai.daylight.assistant.data.remote.FunctionDefinition
import ai.daylight.assistant.data.remote.ParallelClient
import ai.daylight.assistant.data.remote.ParallelResult
import ai.daylight.assistant.data.remote.ParallelSearchArgs
import ai.daylight.assistant.data.remote.ParallelSearchRequest
import ai.daylight.assistant.data.remote.ProviderPreferences
import ai.daylight.assistant.data.remote.PublicWebClient
import ai.daylight.assistant.data.remote.StreamEvent
import ai.daylight.assistant.data.remote.TextProviderClient
import ai.daylight.assistant.data.remote.ToolCall
import ai.daylight.assistant.data.remote.ToolChoice
import ai.daylight.assistant.data.remote.ToolDefinition
import ai.daylight.assistant.data.remote.accumulateStreamText
import java.io.IOException
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Target model and client for Find's Web browsing mode, resolved from the user's
 * configured cheap/default model and provider keys.
 */
data class CheapModelTarget(
    val providerLabel: String,
    val modelId: String,
    val apiKey: String,
    val client: TextProviderClient,
    val useOpenRouterPriceSort: Boolean = false,
)

enum class FindWebStatus { IDLE, SEARCHING, SYNTHESIZING, DONE, FAILED }

data class FindWebSource(
    val title: String,
    val url: String,
    val domain: String,
    val publishDate: String?,
    val snippet: String,
)

data class FindWebResult(
    val query: String,
    val answer: String,
    val sources: List<FindWebSource>,
    val modelLabel: String?,
)

data class FindWebState(
    val query: String = "",
    val status: FindWebStatus = FindWebStatus.IDLE,
    val result: FindWebResult? = null,
    val error: String? = null,
    val serial: Int = 0,
)

/**
 * Find's Web browsing session: runs Parallel Search and an optional cheap model synthesis
 * (with up to 3 tool-calling rounds and seamless Parallel + plain-synthesis fallback),
 * kept in the app scope so switching arms preserves progress and results.
 */
class FindWebSession(
    private val scope: CoroutineScope,
    private val parallel: ParallelClient,
    private val publicWeb: PublicWebClient,
    private val parallelKey: () -> String?,
    private val resolveCheapModel: suspend () -> CheapModelTarget?,
    private val maxSearchChars: suspend () -> Int = { 10_000 },
    private val json: Json,
) {
    private val _state = MutableStateFlow(FindWebState())
    val state: StateFlow<FindWebState> = _state.asStateFlow()

    val draft = MutableStateFlow("")

    private var job: Job? = null
    private var serial = 0

    fun search(raw: String = draft.value) {
        val clean = raw.replace(Regex("\\s+"), " ").trim().take(FindSession.MAX_QUERY)
        if (clean.isEmpty()) return
        job?.cancel()
        val id = ++serial
        val key = parallelKey()?.trim()?.takeIf { it.isNotBlank() }
        if (key == null) {
            _state.value = FindWebState(
                query = clean,
                status = FindWebStatus.FAILED,
                error = MISSING_PARALLEL_KEY_ERROR,
                serial = id,
            )
            return
        }
        _state.value = FindWebState(query = clean, status = FindWebStatus.SEARCHING, serial = id)
        job = scope.launch { execute(id, clean, key) }
    }

    fun stop() {
        job?.cancel()
        serial++
        _state.value = FindWebState(serial = serial)
    }

    fun clear() {
        job?.cancel()
        serial++
        draft.value = ""
        _state.value = FindWebState(serial = serial)
    }

    fun retry() {
        val now = _state.value
        if (now.status == FindWebStatus.FAILED && now.query.isNotBlank()) {
            search(now.query)
        }
    }

    private suspend fun execute(id: Int, query: String, pKey: String) {
        try {
            val target = resolveCheapModel()
            if (target == null) {
                val response = parallel.search(
                    pKey,
                    ParallelSearchRequest(
                        objective = query,
                        searchQueries = listOf(query),
                        mode = "fast",
                        maxCharsTotal = maxSearchChars(),
                    )
                )
                val sources = collectSources(emptyList(), response.results)
                change(id) {
                    it.copy(
                        status = FindWebStatus.DONE,
                        result = FindWebResult(
                            query = query,
                            answer = NO_CHAT_KEY_SUMMARY,
                            sources = sources,
                            modelLabel = null,
                        ),
                        error = null,
                    )
                }
                return
            }

            val modelLabel = formatModelLabel(target)
            val providerPrefs = if (target.useOpenRouterPriceSort) ProviderPreferences(sort = "price") else null
            val sources = mutableListOf<FindWebSource>()
            val fetchedPages = mutableListOf<FetchedPage>()
            var searchSessionId: String? = null
            var didParallelSearch = false

            val messages = mutableListOf(
                ApiMessage("system", TOOL_SYSTEM_PROMPT),
                ApiMessage("user", query),
            )
            val tools = listOf(parallelSearchTool(), fetchUrlTool())

            val toolAnswer = runToolLoop(
                id = id,
                query = query,
                pKey = pKey,
                target = target,
                providerPrefs = providerPrefs,
                tools = tools,
                messages = messages,
                sources = sources,
                fetchedPages = fetchedPages,
                getSessionId = { searchSessionId },
                setSessionId = { searchSessionId = it },
                onParallelSearched = { didParallelSearch = true },
                didParallelSearch = { didParallelSearch },
            )

            val finalAnswer = if (!toolAnswer.isNullOrBlank() && didParallelSearch) {
                toolAnswer
            } else {
                if (!didParallelSearch) {
                    val response = parallel.search(
                        pKey,
                        ParallelSearchRequest(
                            objective = query,
                            searchQueries = listOf(query),
                            mode = "fast",
                            maxCharsTotal = maxSearchChars(),
                            sessionId = searchSessionId,
                            clientModel = target.modelId,
                        )
                    )
                    mergeSources(sources, response.results)
                }
                change(id) { it.copy(status = FindWebStatus.SYNTHESIZING) }
                synthesizeFromSources(target, providerPrefs, query, sources, fetchedPages)
            }

            change(id) {
                it.copy(
                    status = FindWebStatus.DONE,
                    result = FindWebResult(
                        query = query,
                        answer = finalAnswer,
                        sources = sources.toList(),
                        modelLabel = modelLabel,
                    ),
                    error = null,
                )
            }
        } catch (stop: CancellationException) {
            throw stop
        } catch (error: Throwable) {
            change(id) {
                it.copy(status = FindWebStatus.FAILED, error = failureMessage(error))
            }
        }
    }

    private suspend fun runToolLoop(
        id: Int,
        query: String,
        pKey: String,
        target: CheapModelTarget,
        providerPrefs: ProviderPreferences?,
        tools: List<ToolDefinition>,
        messages: MutableList<ApiMessage>,
        sources: MutableList<FindWebSource>,
        fetchedPages: MutableList<FetchedPage>,
        getSessionId: () -> String?,
        setSessionId: (String) -> Unit,
        onParallelSearched: () -> Unit,
        didParallelSearch: () -> Boolean,
    ): String? {
        try {
            repeat(MAX_TOOL_ROUNDS) {
                val request = ChatRequest(
                    model = target.modelId,
                    messages = messages.toList(),
                    stream = true,
                    tools = tools,
                    toolChoice = ToolChoice.AUTO,
                    provider = providerPrefs,
                )
                val streamed = collectStream(target, request, recoverInlineToolCalls = true)
                val calls = streamed.toolCalls
                if (calls.isEmpty()) {
                    return if (didParallelSearch()) streamed.text.takeIf { it.isNotBlank() } else null
                }

                messages += ApiMessage(
                    role = "assistant",
                    text = streamed.rawText.ifBlank { null },
                    toolCalls = calls,
                )

                for (call in calls) {
                    val toolOutput = when (call.function.name) {
                        "parallel_search" -> {
                            val args = parseSearchArgs(call.function.arguments, query)
                            val response = parallel.search(
                                pKey,
                                ParallelSearchRequest(
                                    objective = args.objective ?: query,
                                    searchQueries = args.searchQueries,
                                    mode = args.mode ?: "fast",
                                    maxCharsTotal = maxSearchChars(),
                                    sessionId = getSessionId(),
                                    clientModel = target.modelId,
                                )
                            )
                            setSessionId(response.sessionId)
                            onParallelSearched()
                            mergeSources(sources, response.results)
                            buildJsonObject {
                                put("search_id", response.searchId)
                                putJsonArray("sources") {
                                    response.results.forEach { result ->
                                        val cite = sources.indexOfFirst { sameUrl(it.url, result.url) }
                                            .takeIf { it >= 0 }?.plus(1)
                                        add(buildJsonObject {
                                            cite?.let { put("citation", it) }
                                            put("title", result.title)
                                            put("url", result.url)
                                            result.publishDate?.let { put("publish_date", it) }
                                            putJsonArray("excerpts") {
                                                result.excerpts.forEach { add(JsonPrimitive(it)) }
                                            }
                                        })
                                    }
                                }
                            }.toString()
                        }

                        "fetch_url" -> {
                            val url = runCatching {
                                json.decodeFromString<FetchUrlArgs>(call.function.arguments).url.trim()
                            }.getOrNull().orEmpty()
                            val allowlist = FetchAllowlist(sources.map { it.url }, listOf(query))
                            if (url.isBlank() || !allowlist.permits(url)) {
                                buildJsonObject { put("error", FetchAllowlist.REFUSAL) }.toString()
                            } else {
                                runCatching {
                                    withContext(Dispatchers.IO) { runInterruptible { publicWeb.fetchPage(url) } }
                                }.fold(
                                    onSuccess = { page ->
                                        fetchedPages += page
                                        buildJsonObject {
                                            put("url", page.url)
                                            put("title", page.title)
                                            put("truncated", page.truncated)
                                            put("text", page.text)
                                        }.toString()
                                    },
                                    onFailure = { error ->
                                        if (error is CancellationException) throw error
                                        buildJsonObject {
                                            put("error", error.message ?: "Page fetch failed.")
                                        }.toString()
                                    }
                                )
                            }
                        }

                        else -> buildJsonObject { put("error", "Unknown tool: ${call.function.name}") }.toString()
                    }
                    messages += ApiMessage(
                        role = "tool",
                        text = toolOutput,
                        toolCallId = call.id,
                        name = call.function.name,
                    )
                }

                if (didParallelSearch()) {
                    change(id) { it.copy(status = FindWebStatus.SYNTHESIZING) }
                }
            }
            return null
        } catch (stop: CancellationException) {
            throw stop
        } catch (_: Throwable) {
            return null
        }
    }

    private suspend fun synthesizeFromSources(
        target: CheapModelTarget,
        providerPrefs: ProviderPreferences?,
        query: String,
        sources: List<FindWebSource>,
        fetchedPages: List<FetchedPage>,
    ): String {
        val contextBlock = buildString {
            append("Query: ").append(query).append("\n\n")
            if (sources.isEmpty()) {
                append("No web search sources were returned.")
            } else {
                append("Search sources:\n")
                sources.forEachIndexed { index, source ->
                    append('[').append(index + 1).append("] ").append(source.title)
                    append(" (").append(source.url).append(")")
                    source.publishDate?.let { append(" — ").append(it) }
                    append('\n')
                    if (source.snippet.isNotBlank()) {
                        append(source.snippet).append('\n')
                    }
                    append('\n')
                }
            }
            if (fetchedPages.isNotEmpty()) {
                append("Fetched page excerpts:\n")
                fetchedPages.take(2).forEach { page ->
                    append(page.title).append(" (").append(page.url).append("):\n")
                    append(page.text.take(3_000)).append("\n\n")
                }
            }
        }
        val request = ChatRequest(
            model = target.modelId,
            messages = listOf(
                ApiMessage("system", SYNTHESIS_SYSTEM_PROMPT),
                ApiMessage("user", contextBlock),
            ),
            stream = true,
            tools = null,
            toolChoice = null,
            provider = providerPrefs,
        )
        val streamed = collectStream(target, request, recoverInlineToolCalls = false)
        return streamed.text.takeIf { it.isNotBlank() }
            ?: if (sources.isNotEmpty()) {
                "Found ${sources.size} web sources for \"$query\"."
            } else {
                throw AssistantApiException(ErrorKind.MODEL_UNAVAILABLE, "The model returned no answer.")
            }
    }

    private suspend fun collectStream(
        target: CheapModelTarget,
        request: ChatRequest,
        recoverInlineToolCalls: Boolean,
    ): StreamOutcome {
        val calls = mutableMapOf<Int, MutableToolCall>()
        var text = ""
        var failure: AssistantApiException? = null
        target.client.stream(target.apiKey, request).collect { event ->
            when (event) {
                is StreamEvent.Delta -> {
                    text = accumulateStreamText(text, event.text)
                    event.toolCalls.forEach { delta ->
                        val call = calls.getOrPut(delta.index) { MutableToolCall() }
                        delta.id?.let { call.id = it }
                        delta.function?.name?.let { call.name += it }
                        delta.function?.arguments?.let { call.arguments += it }
                    }
                }
                is StreamEvent.Failure -> failure = event.error
                is StreamEvent.UsageUpdate, StreamEvent.Done -> Unit
            }
        }
        val error = failure
        if (error != null && text.isBlank() && calls.isEmpty()) throw error
        if (recoverInlineToolCalls) {
            val existingNames = calls.values.map { it.name }.toSet()
            var nextIndex = (calls.keys.maxOrNull() ?: -1) + 1
            ToolCallXml.parseToolCallXml(text).forEach { parsed ->
                if (parsed.name.isNotBlank() && parsed.name !in existingNames) {
                    calls[nextIndex++] = MutableToolCall(
                        id = parsed.id,
                        name = parsed.name,
                        arguments = parsed.arguments,
                    )
                }
            }
        }
        return StreamOutcome(
            rawText = text,
            text = ToolCallXml.scrubToolBlocks(text.scrubThinkTags()),
            toolCalls = calls.toSortedMap().values.map {
                ToolCall(
                    id = it.id.ifBlank { "call_${UUID.randomUUID()}" },
                    function = FunctionCall(it.name, it.arguments),
                )
            },
        )
    }

    private fun parseSearchArgs(arguments: String, fallbackQuery: String): ParallelSearchArgs {
        val raw = runCatching { json.decodeFromString<ParallelSearchArgs>(arguments) }.getOrNull()
        val queries = raw?.searchQueries
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(3)
            .orEmpty()
            .ifEmpty { listOf(fallbackQuery) }
        return ParallelSearchArgs(
            objective = raw?.objective?.trim()?.takeIf { it.isNotBlank() } ?: fallbackQuery,
            searchQueries = queries,
            mode = raw?.mode?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun mergeSources(target: MutableList<FindWebSource>, incoming: List<ParallelResult>) {
        collectSources(target, incoming).let { merged ->
            target.clear()
            target.addAll(merged)
        }
    }

    private fun collectSources(
        existing: List<FindWebSource>,
        incoming: List<ParallelResult>,
    ): List<FindWebSource> {
        val out = existing.toMutableList()
        val seen = existing.mapTo(mutableSetOf()) { urlKey(it.url) }
        for (result in incoming) {
            val url = result.url.trim()
            if (url.isBlank()) continue
            val key = urlKey(url)
            if (!seen.add(key)) continue
            val domain = extractDomain(url)
            val title = result.title.trim().ifBlank { domain.ifBlank { url } }
            val snippet = result.excerpts
                .map { it.replace(Regex("\\s+"), " ").trim() }
                .filter { it.isNotBlank() }
                .joinToString(" … ")
                .take(MAX_SNIPPET_CHARS)
            out += FindWebSource(
                title = title,
                url = url,
                domain = domain,
                publishDate = result.publishDate?.trim()?.takeIf { it.isNotBlank() },
                snippet = snippet,
            )
        }
        return out
    }

    private fun change(id: Int, edit: (FindWebState) -> FindWebState) {
        _state.update { if (it.serial == id) edit(it) else it }
    }

    private class MutableToolCall(
        var id: String = "",
        var name: String = "",
        var arguments: String = "",
    )

    private class StreamOutcome(
        val rawText: String,
        val text: String,
        val toolCalls: List<ToolCall>,
    )

    private fun parallelSearchTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "parallel_search",
            description = "Search the web for current, source-backed information. Provide 1 to 3 concise keyword queries.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("objective") {
                        put("type", "string")
                        put("description", "Short natural-language description of what to find.")
                    }
                    putJsonObject("search_queries") {
                        put("type", "array")
                        put("minItems", 1)
                        put("maxItems", 3)
                        putJsonObject("items") {
                            put("type", "string")
                            put("description", "Concise 3-6 word keyword query.")
                        }
                    }
                    putJsonObject("mode") {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("fast"))
                            add(JsonPrimitive("basic"))
                        })
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("search_queries")) })
                put("additionalProperties", false)
            }
        )
    )

    private fun fetchUrlTool() = ToolDefinition(
        function = FunctionDefinition(
            name = "fetch_url",
            description = "Read the text of a public http(s) page returned by parallel_search when excerpts are not enough.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "Exact URL returned by parallel_search.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("url")) })
                put("additionalProperties", false)
            }
        )
    )

    companion object {
        const val MAX_TOOL_ROUNDS = 3
        private const val MAX_SNIPPET_CHARS = 500
        const val MISSING_PARALLEL_KEY_ERROR = "Add a Parallel Search key in Tune → Keys to browse the web."
        const val NO_CHAT_KEY_SUMMARY = "Showing Parallel search results. Add any chat provider key in Tune → Keys for AI summaries."

        private const val TOOL_SYSTEM_PROMPT =
            "You are Kryzz Find Web. Always call parallel_search first to look up current web results for the user's query. " +
                "If excerpts are thin, optionally call fetch_url on 1 or 2 returned URLs. " +
                "Then write a clear, concise, accurate summary grounded in the sources, citing claims with [1], [2], etc."

        private const val SYNTHESIS_SYSTEM_PROMPT =
            "You are Kryzz Find Web. Synthesize a clear, concise, accurate answer to the user's query using only the provided web sources. " +
                "Cite claims with bracketed source numbers such as [1], [2]. Do not invent facts or URLs."

        private fun formatModelLabel(target: CheapModelTarget): String {
            val shortModel = target.modelId.substringAfter('/').ifBlank { target.modelId }
            return "${target.providerLabel} · $shortModel"
        }

        private fun urlKey(url: String): String =
            FetchAllowlist.key(url) ?: url.trim().trimEnd('/').lowercase()

        private fun sameUrl(a: String, b: String): Boolean = urlKey(a) == urlKey(b)

        internal fun extractDomain(url: String): String {
            val host = runCatching { URI(url.trim()).host }.getOrNull()
                ?: url.substringAfter("://").substringBefore('/').substringBefore('?')
            return host.removePrefix("www.").lowercase()
        }

        fun failureMessage(error: Throwable): String = when (error) {
            is AssistantApiException -> error.message
            is IOException -> "Couldn't reach the web search service. Check your connection and try again."
            else -> error.message?.takeIf(String::isNotBlank) ?: "The web search failed. Try again."
        }
    }
}
