package ai.daylight.assistant.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HttpErrorMapperTest {
    @Test fun mapsCommonProviderFailuresToUnderstandableErrors() {
        assertThat(HttpErrorMapper.fromHttp(401, "bad key").kind).isEqualTo(ErrorKind.INVALID_KEY)
        assertThat(HttpErrorMapper.fromHttp(402, "credits").kind).isEqualTo(ErrorKind.INSUFFICIENT_CREDITS)
        assertThat(HttpErrorMapper.fromHttp(400, "context length exceeded").kind).isEqualTo(ErrorKind.CONTEXT_LENGTH)
        val limited = HttpErrorMapper.fromHttp(429, "slow down", "17")
        assertThat(limited.retryAfterSeconds).isEqualTo(17)
        assertThat(limited.message).contains("17 seconds")
    }
}
