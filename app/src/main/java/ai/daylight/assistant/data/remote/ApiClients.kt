package ai.daylight.assistant.data.remote

import ai.daylight.assistant.voice.VoiceConfig
import java.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface TextProviderClient {
    suspend fun testKey(key: String): Result<Unit>
    suspend fun models(key: String?): List<OpenRouterModel>
    fun stream(key: String, request: ChatRequest): Flow<StreamEvent>
}

class OpenRouterClient(private val http: OkHttpClient, private val json: Json) : TextProviderClient {
    override suspend fun testKey(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("https://openrouter.ai/api/v1/auth/key")
                .header("Authorization", "Bearer ${key.trim()}").get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw errorFor(response)
            }
        }
    }

    override suspend fun models(key: String?): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("https://openrouter.ai/api/v1/models").get()
        if (!key.isNullOrBlank()) builder.header("Authorization", "Bearer ${key.trim()}")
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            json.decodeFromString<ModelsEnvelope>(response.body?.string().orEmpty()).data.sortedBy { it.name.lowercase() }
        }
    }

    suspend fun benchmarks(key: String?): BenchmarksEnvelope = withContext(Dispatchers.IO) {
        if (key.isNullOrBlank()) return@withContext BenchmarksEnvelope()
        val request = Request.Builder().url("https://openrouter.ai/api/v1/benchmarks?source=artificial-analysis")
            .header("Authorization", "Bearer ${key.trim()}").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    suspend fun imageModels(key: String?): List<MediaModel> = mediaModels(key, "https://openrouter.ai/api/v1/images/models")

    suspend fun videoModels(key: String?): List<MediaModel> = mediaModels(key, "https://openrouter.ai/api/v1/videos/models")

    suspend fun audioModels(key: String?): List<MediaModel> = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("https://openrouter.ai/api/v1/models?output_modalities=speech").get()
        if (!key.isNullOrBlank()) builder.header("Authorization", "Bearer ${key.trim()}")
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            json.decodeFromString<ModelsEnvelope>(response.body?.string().orEmpty()).data
                .map { MediaModel(it.id, it.name, it.description) }
                .sortedBy { it.name.lowercase() }
        }
    }

    private suspend fun mediaModels(key: String?, url: String): List<MediaModel> {
        val builder = Request.Builder().url(url).get()
        if (!key.isNullOrBlank()) builder.header("Authorization", "Bearer ${key.trim()}")
        return http.newCall(builder.build()).awaitResponse().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            json.decodeFromString<MediaModelsEnvelope>(response.body?.string().orEmpty()).data.sortedBy { it.name.lowercase() }
        }
    }

    suspend fun generateImage(key: String, request: ImageGenerationRequest): ImageGenerationResponse {
        val call = http.newCall(
            Request.Builder().url("https://openrouter.ai/api/v1/images")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA)).build()
        )
        return call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    suspend fun submitVideo(key: String, request: VideoGenerationRequest): VideoJobResponse {
        val call = http.newCall(
            Request.Builder().url("https://openrouter.ai/api/v1/videos")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA)).build()
        )
        return call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    suspend fun videoStatus(key: String, jobId: String): VideoJobResponse {
        val call = http.newCall(
            Request.Builder().url("https://openrouter.ai/api/v1/videos/$jobId")
                .header("Authorization", "Bearer ${key.trim()}").get().build()
        )
        return call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    suspend fun downloadVideo(key: String, jobId: String, destination: File): String {
        val call = http.newCall(
            Request.Builder().url("https://openrouter.ai/api/v1/videos/$jobId/content")
                .header("Authorization", "Bearer ${key.trim()}").get().build()
        )
        return call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            val mime = response.header("Content-Type")?.substringBefore(';') ?: "video/mp4"
            withContext(Dispatchers.IO) {
                response.body?.byteStream()?.use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw AssistantApiException(ErrorKind.UNKNOWN, "The generated video was empty.")
            }
            mime
        }
    }

    suspend fun generateSpeech(key: String, request: SpeechGenerationRequest, destination: File): String {
        val call = http.newCall(
            Request.Builder().url("https://openrouter.ai/api/v1/audio/speech")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA)).build()
        )
        return call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw errorFor(response)
            val mime = response.header("Content-Type")?.substringBefore(';') ?: "audio/mpeg"
            withContext(Dispatchers.IO) {
                response.body?.byteStream()?.use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw AssistantApiException(ErrorKind.UNKNOWN, "The speech model returned an empty audio file.")
            }
            mime
        }
    }

    /**
     * Transcribes a recorded voice message with a Whisper-style model.
     * The Groq-hosted large-v3-turbo runs on OpenRouter's /audio/transcriptions endpoint.
     * [language] skips Whisper's language-detect step (Groq: improves accuracy and latency).
     */
    suspend fun transcribe(
        key: String,
        audioFile: File,
        model: String,
        mimeType: String = "audio/mp4",
        language: String = VoiceConfig.DEFAULT_STT_LANGUAGE,
        prompt: String? = null
    ): String =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("temperature", "0")
                .apply {
                    if (language.isNotBlank()) addFormDataPart("language", language)
                    if (!prompt.isNullOrBlank()) addFormDataPart("prompt", prompt)
                }
                .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mimeType.toMediaType()))
                .build()
            val call = http.newCall(
                Request.Builder().url("https://openrouter.ai/api/v1/audio/transcriptions")
                    .header("Authorization", "Bearer ${key.trim()}")
                    .post(body).build()
            )
            call.awaitResponse().use { response ->
                if (!response.isSuccessful) throw errorFor(response)
                json.decodeFromString<SttResponse>(response.body?.string().orEmpty()).text
            }
        }

    /**
     * Streaming-style transcription for models that handle chunked uploads well (Nemotron).
     * Splits the input WAV into 4 s chunks and uploads them in parallel, then concatenates
     * the per-chunk texts in the original order. Falls back to a single request when the
     * recording is too short to split. The caller is responsible for cleaning up the chunk
     * files (use [ai.daylight.assistant.voice.deleteWavChunks]).
     */
    suspend fun transcribeChunked(
        key: String,
        audioFile: File,
        chunks: List<File>,
        model: String,
        mimeType: String = "audio/wav",
        language: String = VoiceConfig.DEFAULT_STT_LANGUAGE,
        prompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (chunks.size <= 1) {
            return@withContext transcribe(key, audioFile, model, mimeType, language, prompt)
        }
        val results = arrayOfNulls<String>(chunks.size)
        coroutineScope {
            chunks.mapIndexed { index, chunk ->
                async(Dispatchers.IO) {
                    try {
                        results[index] = transcribe(key, chunk, model, mimeType, language, prompt)
                    } catch (t: Throwable) {
                        // One bad chunk must not nuke the whole reply — leave the slot empty
                        // so the concatenation just has a small gap rather than crashing.
                        results[index] = ""
                    }
                }
            }.awaitAll()
        }
        results.joinToString(" ") { it?.trim().orEmpty() }
            .split(' ').filter { it.isNotBlank() }.joinToString(" ")
            .trim()
    }

    /** Opens the OpenRouter TLS session so the next transcription does not wait on handshake. */
    fun warmConnection(key: String) {
        val request = Request.Builder().url("https://openrouter.ai/api/v1/auth/key")
            .header("Authorization", "Bearer ${key.trim()}").get().build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    override fun stream(key: String, request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        val body = json.encodeToString(request).toRequestBody(JSON_MEDIA)
        val call = http.newCall(
            Request.Builder().url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .header("X-OpenRouter-Title", "Kryzz AI")
                .post(body).build()
        )
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) trySend(StreamEvent.Failure(HttpErrorMapper.fromThrowable(e)))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        trySend(StreamEvent.Failure(errorFor(it)))
                        close()
                        return
                    }
                    if (!it.header("Content-Type").orEmpty().contains("text/event-stream", ignoreCase = true)) {
                        val payload = it.body?.string().orEmpty()
                        if (payload.isNotBlank()) consumePayload(payload)
                        trySend(StreamEvent.Done)
                        close()
                        return
                    }
                    val parser = SseParser()
                    val source = it.body?.source()
                    try {
                        while (source != null && !source.exhausted() && !call.isCanceled()) {
                            parser.accept(source.readUtf8Line()).forEach(::consumePayload)
                        }
                        parser.finish().forEach(::consumePayload)
                    } catch (t: Throwable) {
                        if (!call.isCanceled()) trySend(StreamEvent.Failure(HttpErrorMapper.fromThrowable(t)))
                    } finally {
                        close()
                    }
                }
            }

            private fun consumePayload(payload: String) {
                if (payload.isBlank()) return
                if (payload == "[DONE]") {
                    trySend(StreamEvent.Done)
                    return
                }
                runCatching { json.decodeFromString<ChatChunk>(payload) }
                    .onSuccess { chunk ->
                        chunk.error?.let { trySend(StreamEvent.Failure(HttpErrorMapper.fromHttp(it.code ?: 500, it.message))) }
                        minimaxBaseRespFailure(chunk.baseResp)?.let { trySend(it) }
                        chunk.choices.firstOrNull()?.let { choice ->
                            choice.error?.let { trySend(StreamEvent.Failure(HttpErrorMapper.fromHttp(it.code ?: 500, it.message))) }
                            val text = extractResponseText(choice.delta.content ?: choice.message?.content ?: choice.text)
                            val calls = choice.delta.toolCalls.orEmpty().ifEmpty {
                                choice.message?.toolCalls.orEmpty().mapIndexed { index, call ->
                                    ToolCallDelta(
                                        index = index,
                                        id = call.id,
                                        function = FunctionCallDelta(call.function.name, call.function.arguments)
                                    )
                                }
                            }
                            if (text.isNotEmpty() || calls.isNotEmpty()) trySend(StreamEvent.Delta(text, calls))
                        }
                        chunk.usage?.let { trySend(StreamEvent.UsageUpdate(it)) }
                    }
                    .onFailure {
                        // Keep-alives, empty `data:` frames, and unknown vendor events are
                        // not fatal. Emitting Failure here used to abort an otherwise good
                        // voice reply and surface "unreadable stream event" in the overlay.
                    }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun errorFor(response: Response): AssistantApiException {
        val text = response.body?.string().orEmpty()
        val message = runCatching { json.decodeFromString<ErrorEnvelope>(text).error?.message }.getOrNull() ?: text.take(500)
        return HttpErrorMapper.fromHttp(response.code, message, response.header("Retry-After"))
    }

    private companion object { val JSON_MEDIA = "application/json; charset=utf-8".toMediaType() }
}

internal fun extractResponseText(content: JsonElement?): String = when (content) {
    null -> ""
    is JsonPrimitive -> content.contentOrNull.orEmpty()
    is JsonArray -> content.joinToString(separator = "") { extractResponseText(it) }
    is JsonObject -> {
        val type = (content["type"] as? JsonPrimitive)?.contentOrNull
        when (type) {
            "text", "output_text" -> extractResponseText(content["text"] ?: content["content"])
            else -> extractResponseText(content["text"] ?: content["output_text"] ?: content["content"])
        }
    }
}

/**
 * MiniMax (and a few other vendors) sometimes send the *accumulated* completion in each
 * delta instead of the next fragment. Concatenating those chunks duplicates the reply and
 * breaks tool-call recovery. If the new piece is a prefix/extension of what we already
 * have, keep the longer copy; otherwise append.
 */
internal fun accumulateStreamText(previous: String, incoming: String): String {
    if (incoming.isEmpty()) return previous
    if (previous.isEmpty()) return incoming
    if (incoming.startsWith(previous)) return incoming
    if (previous.startsWith(incoming)) return previous
    return previous + incoming
}

class ParallelClient(private val http: OkHttpClient, private val json: Json) {
    suspend fun search(key: String, request: ParallelSearchRequest): ParallelSearchResponse {
        val call = http.newCall(
            Request.Builder().url("https://api.parallel.ai/v1/search")
                .header("x-api-key", key.trim())
                .header("Content-Type", "application/json")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA)).build()
        )
        return call.awaitResponse().use { response ->
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                val message = runCatching { json.decodeFromString<ParallelErrorEnvelope>(text).error?.message }.getOrNull() ?: text.take(500)
                throw HttpErrorMapper.fromHttp(response.code, message, response.header("Retry-After"))
            }
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    suspend fun testKey(key: String): Result<Unit> = runCatching {
        search(
            key,
            ParallelSearchRequest(
                objective = "Verify API access using the official Parallel documentation site.",
                searchQueries = listOf("Parallel API documentation"),
                maxCharsTotal = 2_000
            )
        )
        Unit
    }

    private companion object { val JSON_MEDIA = "application/json; charset=utf-8".toMediaType() }
}

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.MINUTES)
    .callTimeout(6, TimeUnit.MINUTES)
    // Deliberately no HTTP logging interceptor: keys and prompts never enter logs.
    .build()

/**
 * Fish Audio S2.1 Pro TTS (https://api.fish.audio/v1/tts).
 * The response body of a successful call is the raw MP3 audio (chunked transfer).
 */
class FishAudioClient(private val http: OkHttpClient, private val json: Json) {
    /** Synthesises [text] into [destination] and returns the response MIME type. */
    suspend fun synthesize(
        key: String,
        text: String,
        destination: File,
        voiceId: String? = null,
        speed: Double = 1.0,
        model: String = VoiceConfig.FISH_MODEL_FREE
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer ${key.trim()}")
            .header("model", model)
            .post(
                json.encodeToString(
                    fishTtsRequest(
                        text = text,
                        voiceId = voiceId,
                        format = "mp3",
                        speed = speed
                    )
                ).toRequestBody(JSON_MEDIA)
            )
            .build()
        http.newCall(request).awaitResponse().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string().orEmpty()
                val message = runCatching { json.decodeFromString<FishErrorEnvelope>(raw).message }
                    .getOrNull() ?: raw.take(300)
                throw when (response.code) {
                    401 -> AssistantApiException(ErrorKind.INVALID_KEY, "Fish Audio rejected the API key. Check it in Settings → Voice chat.")
                    402 -> AssistantApiException(ErrorKind.INSUFFICIENT_CREDITS, "Fish Audio has no credits left for voice replies (HTTP 402).")
                    else -> HttpErrorMapper.fromHttp(response.code, message, response.header("Retry-After"))
                }
            }
            val mime = response.header("Content-Type")?.substringBefore(';') ?: "audio/mpeg"
            response.body?.byteStream()?.use { input ->
                destination.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            } ?: throw AssistantApiException(ErrorKind.UNKNOWN, "Fish Audio returned an empty voice file.")
            mime
        }
    }

    /**
     * Streams PCM16 mono audio as Fish generates it so playback can start on the first chunk.
     *
     * The HTTP call is cancelled when the collector cancels. If Fish ignores `format=pcm`
     * and returns MP3, [FishPcmUnavailableException] is thrown so the caller can fall
     * back to the file + MediaPlayer path. Emit happens in the flow's own context
     * (`flowOn(IO)`), never from a nested `withContext`, which used to trip the Flow
     * invariant and abort the first spoken sentence.
     */
    fun synthesizeStream(
        key: String,
        text: String,
        voiceId: String? = null,
        speed: Double = 1.0,
        model: String = VoiceConfig.FISH_MODEL_FREE,
        sampleRate: Int = VoiceConfig.FISH_PCM_SAMPLE_RATE
    ): Flow<ByteArray> = flow {
        val request = Request.Builder().url("https://api.fish.audio/v1/tts")
            .header("Authorization", "Bearer ${key.trim()}")
            .header("model", model)
            .post(
                json.encodeToString(
                    fishTtsRequest(
                        text = text,
                        voiceId = voiceId,
                        format = "pcm",
                        speed = speed,
                        sampleRate = sampleRate
                    )
                ).toRequestBody(JSON_MEDIA)
            )
            .build()
        val call = http.newCall(request)
        try {
            val response = call.execute()
            response.use {
                if (!it.isSuccessful) {
                    val raw = it.body?.string().orEmpty()
                    val message = runCatching { json.decodeFromString<FishErrorEnvelope>(raw).message }
                        .getOrNull() ?: raw.take(300)
                    throw when (it.code) {
                        401 -> AssistantApiException(ErrorKind.INVALID_KEY, "Fish Audio rejected the API key. Check it in Settings → Voice chat.")
                        402 -> AssistantApiException(ErrorKind.INSUFFICIENT_CREDITS, "Fish Audio has no credits left for voice replies (HTTP 402).")
                        else -> HttpErrorMapper.fromHttp(it.code, message, it.header("Retry-After"))
                    }
                }
                val contentType = it.header("Content-Type").orEmpty()
                if (contentType.contains("mpeg", ignoreCase = true) || contentType.contains("mp3", ignoreCase = true)) {
                    throw FishPcmUnavailableException("Fish Audio returned MP3 instead of a PCM stream.")
                }
                val source = it.body?.source()
                    ?: throw AssistantApiException(ErrorKind.UNKNOWN, "Fish Audio returned an empty voice stream.")
                val buffer = ByteArray(4_096)
                while (!source.exhausted() && !call.isCanceled()) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read <= 0) break
                    emit(buffer.copyOf(read))
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /** Renders one short phrase to verify the key (and voice when provided) without touching chat history. */
    suspend fun testKey(key: String, voiceId: String?, model: String = VoiceConfig.FISH_MODEL_FREE): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File.createTempFile("kryzz-fish-test", ".mp3")
        try {
            synthesize(key, "Hello, this is a voice test.", file, voiceId, speed = 1.0, model = model)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val MAX_INPUT_CHARS = 4_000
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val EMOTION_MARK = Regex("""[\[(][A-Za-z][^)\]]*[)\]]""")
    }

    private fun fishTtsRequest(
        text: String,
        voiceId: String?,
        format: String,
        speed: Double,
        sampleRate: Int? = null
    ): FishTtsRequest {
        val clipped = text.take(MAX_INPUT_CHARS)
        // Fish's English/Chinese normalizer treats [happy] as markup and can swallow
        // the rest of the sentence. Disable it whenever a tag is present.
        val hasEmotion = EMOTION_MARK.containsMatchIn(clipped)
        return FishTtsRequest(
            text = clipped,
            referenceId = voiceId?.takeIf { it.isNotBlank() },
            format = format,
            latency = VoiceConfig.FISH_LATENCY,
            chunkLength = VoiceConfig.FISH_CHUNK_LENGTH,
            prosody = FishProsody(speed = speed.coerceIn(0.5, 2.0)),
            normalize = !hasEmotion,
            sampleRate = sampleRate
        )
    }
}

/**
 * MiniMax streams vendor-internal errors inline as a top-level `base_resp` object
 * (e.g. status_code 2013 "invalid tool type") rather than an OpenAI-style `error`.
 * Those codes are not HTTP statuses, so map a non-zero one to a validation fault
 * (auth-style 1004 stays an auth fault) — this lets the agent's tool-payload
 * retry recover by dropping tools when the provider rejects them mid-stream.
 */
internal fun minimaxBaseRespFailure(baseResp: MiniMaxBaseResp?): StreamEvent.Failure? {
    val base = baseResp ?: return null
    val code = base.statusCode ?: return null
    if (code == 0) return null
    val msg = base.statusMsg.orEmpty().ifBlank { "MiniMax error $code" }
    val kind = if (code == 1004) ErrorKind.INVALID_KEY else ErrorKind.VALIDATION
    return StreamEvent.Failure(AssistantApiException(kind, "$msg ($code)", code))
}

/** Global MiniMax API client (Token Plan / pay-as-you-go). */
class MiniMaxClient(private val http: OkHttpClient, private val json: Json) : TextProviderClient {

    override suspend fun testKey(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$BASE_URL/v1/models")
                .header("Authorization", "Bearer ${key.trim()}").get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw minimaxErrorFor(response)
            }
        }
    }

    override suspend fun models(key: String?): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("$BASE_URL/v1/models").get()
        if (!key.isNullOrBlank()) builder.header("Authorization", "Bearer ${key.trim()}")
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw minimaxErrorFor(response)
            json.decodeFromString<ModelsEnvelope>(response.body?.string().orEmpty()).data
                .sortedBy { it.name.lowercase() }
        }
    }

    override fun stream(key: String, request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        val body = json.encodeToString(request).toRequestBody(JSON_MEDIA)
        val call = http.newCall(
            Request.Builder().url("$BASE_URL/v1/chat/completions")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .post(body).build()
        )
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) trySend(StreamEvent.Failure(HttpErrorMapper.fromThrowable(e)))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        trySend(StreamEvent.Failure(minimaxErrorFor(it)))
                        close()
                        return
                    }
                    if (!it.header("Content-Type").orEmpty().contains("text/event-stream", ignoreCase = true)) {
                        val payload = it.body?.string().orEmpty()
                        if (payload.isNotBlank()) consumePayload(payload)
                        trySend(StreamEvent.Done)
                        close()
                        return
                    }
                    val parser = SseParser()
                    val source = it.body?.source()
                    try {
                        while (source != null && !source.exhausted() && !call.isCanceled()) {
                            parser.accept(source.readUtf8Line()).forEach(::consumePayload)
                        }
                        parser.finish().forEach(::consumePayload)
                    } catch (t: Throwable) {
                        if (!call.isCanceled()) trySend(StreamEvent.Failure(HttpErrorMapper.fromThrowable(t)))
                    } finally {
                        close()
                    }
                }
            }

            private fun consumePayload(payload: String) {
                if (payload.isBlank()) return
                if (payload == "[DONE]") {
                    trySend(StreamEvent.Done)
                    return
                }
                runCatching { json.decodeFromString<ChatChunk>(payload) }
                    .onSuccess { chunk ->
                        chunk.error?.let { trySend(StreamEvent.Failure(HttpErrorMapper.fromHttp(it.code ?: 500, it.message))) }
                        minimaxBaseRespFailure(chunk.baseResp)?.let { trySend(it) }
                        chunk.choices.firstOrNull()?.let { choice ->
                            choice.error?.let { trySend(StreamEvent.Failure(HttpErrorMapper.fromHttp(it.code ?: 500, it.message))) }
                            val text = extractResponseText(choice.delta.content ?: choice.message?.content ?: choice.text)
                            val calls = choice.delta.toolCalls.orEmpty().ifEmpty {
                                choice.message?.toolCalls.orEmpty().mapIndexed { index, call ->
                                    ToolCallDelta(
                                        index = index,
                                        id = call.id,
                                        function = FunctionCallDelta(call.function.name, call.function.arguments)
                                    )
                                }
                            }
                            if (text.isNotEmpty() || calls.isNotEmpty()) trySend(StreamEvent.Delta(text, calls))
                        }
                        chunk.usage?.let { trySend(StreamEvent.UsageUpdate(it)) }
                    }
                    .onFailure { /* keep-alive / unknown events are non-fatal */ }
            }
        })
        awaitClose { call.cancel() }
    }

    fun imageModels(): List<MediaModel> = listOf(
        MediaModel("image-01", "MiniMax Image 01", "Text-to-image generation", false, emptyList(), listOf("1:1", "16:9", "9:16", "4:3", "3:4")),
        MediaModel("image-01-live", "MiniMax Image 01 Live", "Faster text-to-image generation", false, emptyList(), listOf("1:1", "16:9", "9:16", "4:3", "3:4"))
    )

    fun videoModels(): List<MediaModel> = listOf(
        MediaModel("MiniMax-H3", "MiniMax H3", "Text/image/video/audio-to-video, 768P / 2K, 4–15s", false, emptyList(), listOf("16:9", "9:16", "1:1", "4:3", "adaptive"))
    )

    fun audioModels(): List<MediaModel> = listOf(
        // MiniMax music generation is async (submit + poll). The documented music model
        // ids are music-01 (standard) and music-02 (higher quality); the free tier is
        // exposed as music-01-free. Listing the real ids means the request no longer 400s
        // on an unknown model when the user picks Music.
        MediaModel("music-01-free", "MiniMax Music 01 Free", "Text-to-music generation (free tier)", false, emptyList(), emptyList()),
        MediaModel("music-01", "MiniMax Music 01", "Text-to-music generation", false, emptyList(), emptyList()),
        MediaModel("music-02", "MiniMax Music 02", "Higher-quality text-to-music generation", false, emptyList(), emptyList()),
        MediaModel("speech-2.6-turbo", "MiniMax Speech 2.6 Turbo", "Fast multilingual TTS", false, emptyList(), emptyList()),
        MediaModel("speech-2.6-hd", "MiniMax Speech 2.6 HD", "High-quality TTS", false, emptyList(), emptyList())
    )

    suspend fun generateImage(key: String, request: MiniMaxImageRequest): MiniMaxImageResponse = withContext(Dispatchers.IO) {
        val call = http.newCall(
            Request.Builder().url("$BASE_URL/v1/image_generation")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA)).build()
        )
        call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw minimaxErrorFor(response)
            val parsed = json.decodeFromString<MiniMaxImageResponse>(response.body?.string().orEmpty())
            parsed.baseResp.throwIfFailed("The MiniMax image model rejected this request.")
            parsed
        }
    }

    suspend fun submitVideo(key: String, request: MiniMaxVideoRequest): MiniMaxVideoSubmitResponse = withContext(Dispatchers.IO) {
        val call = http.newCall(
            Request.Builder().url("$BASE_URL/v2/video_generation")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA)).build()
        )
        call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw minimaxErrorFor(response)
            val parsed = json.decodeFromString<MiniMaxVideoSubmitResponse>(response.body?.string().orEmpty())
            parsed.baseResp.throwIfFailed("MiniMax rejected the video request.")
            parsed
        }
    }

    suspend fun queryVideo(key: String, taskId: String): MiniMaxVideoStatusResponse = withContext(Dispatchers.IO) {
        val call = http.newCall(
            Request.Builder().url("$BASE_URL/v2/query/video_generation/$taskId")
                .header("Authorization", "Bearer ${key.trim()}")
                .get().build()
        )
        call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw minimaxErrorFor(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    suspend fun downloadVideo(url: String, destination: File): String = withContext(Dispatchers.IO) {
        val call = http.newCall(Request.Builder().url(url).get().build())
        call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw HttpErrorMapper.fromHttp(response.code, "Could not download the generated video.")
            val mime = response.header("Content-Type")?.substringBefore(';') ?: "video/mp4"
            response.body?.byteStream()?.use { input ->
                destination.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            } ?: throw AssistantApiException(ErrorKind.UNKNOWN, "The generated video was empty.")
            mime
        }
    }

    suspend fun generateMusic(key: String, request: MiniMaxMusicRequest): MiniMaxMusicResponse = withContext(Dispatchers.IO) {
        val call = http.newCall(
            Request.Builder().url("$BASE_URL/v1/music_generation")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA)).build()
        )
        call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw minimaxErrorFor(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    /**
     * Polls the async music task. MiniMax's /v1/music_generation is asynchronous: the submit
     * response carries a task id in `data.audio`, and the finished URL is fetched from
     * /v1/query/music_generation?task_id=… until `data.audio_url` is populated. Returns the
     * same shape as the submit call so the caller can read `data.audio_url` and `base_resp`.
     */
    suspend fun queryMusic(key: String, taskId: String): MiniMaxMusicResponse = withContext(Dispatchers.IO) {
        val call = http.newCall(
            Request.Builder().url("$BASE_URL/v1/query/music_generation?task_id=${taskId.trim()}")
                .header("Authorization", "Bearer ${key.trim()}")
                .get().build()
        )
        call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw minimaxErrorFor(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    suspend fun generateSpeech(key: String, request: MiniMaxTtsRequest): MiniMaxTtsResponse = withContext(Dispatchers.IO) {
        val call = http.newCall(
            Request.Builder().url("$BASE_URL/v1/t2a_v2")
                .header("Authorization", "Bearer ${key.trim()}")
                .header("Content-Type", "application/json")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA)).build()
        )
        call.awaitResponse().use { response ->
            if (!response.isSuccessful) throw minimaxErrorFor(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    private fun minimaxErrorFor(response: Response): AssistantApiException {
        val text = response.body?.string().orEmpty()
        val message = parseMiniMaxError(json, text) ?: text.take(500)
        return HttpErrorMapper.fromHttp(response.code, message, response.header("Retry-After"))
    }

    private companion object {
        const val BASE_URL = "https://api.minimax.io"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** Fish answered a PCM request with a non-PCM body; play the MP3 file path instead. */
internal class FishPcmUnavailableException(message: String) : IOException(message)

internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
