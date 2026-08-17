package ai.daylight.assistant.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SseParserTest {
    @Test fun ignoresKeepAliveCommentsAndEmitsData() {
        val parser = SseParser()
        assertThat(parser.accept(": OPENROUTER PROCESSING")).isEmpty()
        assertThat(parser.accept("data: {\"choices\":[]}")).isEmpty()
        assertThat(parser.accept("")).containsExactly("{\"choices\":[]}")
    }

    @Test fun joinsMultilineDataAndFlushesUnterminatedEvent() {
        val parser = SseParser()
        parser.accept("data: first")
        parser.accept("data: second")
        assertThat(parser.finish()).containsExactly("first\nsecond")
    }
}
