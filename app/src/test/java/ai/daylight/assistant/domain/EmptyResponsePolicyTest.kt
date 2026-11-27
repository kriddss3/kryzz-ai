package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmptyResponsePolicyTest {
    @Test fun retriesOnlyFirstTrulyEmptyResponse() {
        assertThat(shouldRetryEmptyResponse(0, hasText = false, hasToolCalls = false, allowEmpty = false)).isTrue()
        assertThat(shouldRetryEmptyResponse(1, hasText = false, hasToolCalls = false, allowEmpty = false)).isFalse()
        assertThat(shouldRetryEmptyResponse(0, hasText = true, hasToolCalls = false, allowEmpty = false)).isFalse()
        assertThat(shouldRetryEmptyResponse(0, hasText = false, hasToolCalls = true, allowEmpty = false)).isFalse()
        assertThat(shouldRetryEmptyResponse(0, hasText = false, hasToolCalls = false, allowEmpty = true)).isFalse()
    }

    @Test fun rateLimitWithoutAWaitStillRetriesTwice() {
        assertThat(rateLimitRetryDelayMs(0, null)).isEqualTo(800L)
        assertThat(rateLimitRetryDelayMs(1, null)).isEqualTo(1_600L)
        assertThat(rateLimitRetryDelayMs(2, null)).isNull()
        assertThat(rateLimitRetryDelayMs(0, 5)).isEqualTo(5_000L)
        assertThat(rateLimitRetryDelayMs(0, 0)).isEqualTo(400L)
        assertThat(rateLimitRetryDelayMs(0, 60)).isNull()
    }
}
