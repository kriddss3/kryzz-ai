package ai.daylight.assistant.data

import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ErrorKind
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test

/** C9: a routine retries only failures that can pass, so a bad key doesn't stack prompts in a chat. */
class CronRetryTest {
    @Test fun passingFailuresAreRetried() {
        listOf(ErrorKind.NETWORK, ErrorKind.TIMEOUT, ErrorKind.RATE_LIMIT, ErrorKind.MODEL_UNAVAILABLE, ErrorKind.UNKNOWN).forEach {
            assertThat(CronWorker.retryable(AssistantApiException(it, "x"))).isTrue()
        }
        assertThat(CronWorker.retryable(IOException("reset"))).isTrue()
    }

    @Test fun failuresThatRepeatAreNot() {
        listOf(ErrorKind.INVALID_KEY, ErrorKind.INSUFFICIENT_CREDITS, ErrorKind.VALIDATION, ErrorKind.CONTEXT_LENGTH).forEach {
            assertThat(CronWorker.retryable(AssistantApiException(it, "x"))).isFalse()
        }
    }
}
