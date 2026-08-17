package ai.daylight.assistant.data.remote

import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException

object HttpErrorMapper {
    fun fromHttp(status: Int, providerMessage: String?, retryAfter: String? = null): AssistantApiException {
        val raw = providerMessage.orEmpty()
        val lower = raw.lowercase()
        val kind = when {
            status == 401 || status == 403 -> ErrorKind.INVALID_KEY
            status == 402 -> ErrorKind.INSUFFICIENT_CREDITS
            status == 429 -> ErrorKind.RATE_LIMIT
            "context" in lower && ("length" in lower || "token" in lower) -> ErrorKind.CONTEXT_LENGTH
            "model" in lower && ("unavailable" in lower || "invalid" in lower || "not found" in lower) -> ErrorKind.MODEL_UNAVAILABLE
            status == 400 || status == 422 -> ErrorKind.VALIDATION
            status >= 500 -> ErrorKind.MODEL_UNAVAILABLE
            else -> ErrorKind.UNKNOWN
        }
        val wait = parseRetryAfter(retryAfter)
        val friendly = when (kind) {
            ErrorKind.INVALID_KEY -> "The API key was rejected. Check it and try again."
            ErrorKind.INSUFFICIENT_CREDITS -> "This account has insufficient credits for the request."
            ErrorKind.RATE_LIMIT -> if (wait != null) "Rate limit reached. Try again in $wait seconds." else "Rate limit reached. Please try again shortly."
            ErrorKind.MODEL_UNAVAILABLE -> "The selected model is unavailable. Choose another model and try again."
            ErrorKind.CONTEXT_LENGTH -> "This conversation is too long for the selected model. Start a new chat or choose a model with a larger context window."
            ErrorKind.VALIDATION -> raw.ifBlank { "The provider could not validate this request." }
            else -> raw.ifBlank { "The provider returned an unexpected error ($status)." }
        }
        return AssistantApiException(kind, friendly, status, wait)
    }

    fun fromThrowable(error: Throwable): AssistantApiException = when (error) {
        is AssistantApiException -> error
        is CancellationException -> AssistantApiException(ErrorKind.CANCELLED, "Generation stopped.")
        is SocketTimeoutException -> AssistantApiException(ErrorKind.TIMEOUT, "The request timed out. Check your connection and try again.")
        else -> AssistantApiException(ErrorKind.NETWORK, "Could not reach the service. Check your connection and try again.")
    }

    private fun parseRetryAfter(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        value.trim().toLongOrNull()?.let { return it.coerceAtLeast(0) }
        return runCatching {
            val time = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().epochSecond
            (time - System.currentTimeMillis() / 1000).coerceAtLeast(0)
        }.getOrNull()
    }
}
