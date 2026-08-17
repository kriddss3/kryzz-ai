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
}
