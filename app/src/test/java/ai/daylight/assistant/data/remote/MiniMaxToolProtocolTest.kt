package ai.daylight.assistant.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class MiniMaxToolProtocolTest {
    private val appJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    @Test fun productionJsonKeepsToolTypeAndStreamFlag() {
        val request = ChatRequest(
            model = "MiniMax-M3",
            messages = listOf(ApiMessage("user", "Weather in Geneva?")),
            tools = listOf(
                ToolDefinition(
                    function = FunctionDefinition(
                        name = "parallel_search",
                        description = "Search the web",
                        parameters = buildJsonObject { put("type", "object") }
                    )
                )
            ),
            toolChoice = ToolChoice.named("parallel_search"),
            maxTokens = 8192
        )
        val encoded = appJson.encodeToString(request)
        assertThat(encoded).contains("\"type\":\"function\"")
        assertThat(encoded).contains("\"stream\":true")
        assertThat(encoded).contains("\"max_tokens\":8192")
        assertThat(encoded).contains("\"name\":\"parallel_search\"")
    }

    @Test fun productionJsonKeepsToolCallTypeOnHistoryReplay() {
        val request = ChatRequest(
            model = "MiniMax-M3",
            messages = listOf(
                ApiMessage(
                    role = "assistant",
                    text = "<think>need weather</think>",
                    toolCalls = listOf(
                        ToolCall(
                            id = "call_1",
                            function = FunctionCall("parallel_search", """{"search_queries":["geneva weather"]}""")
                        )
                    )
                )
            )
        )
        val encoded = appJson.encodeToString(request)
        assertThat(encoded).contains("\"type\":\"function\"")
        assertThat(encoded).contains("<think>need weather</think>")
        assertThat(encoded).contains("parallel_search")
    }

    @Test fun parsesMiniMaxThinkBlockPlusObjectArguments() {
        val chunk = appJson.decodeFromString<ChatChunk>(
            """{"choices":[{"finish_reason":"tool_calls","message":{"content":"<think>look up weather</think>\n\n","tool_calls":[{"id":"call_function_1","type":"function","function":{"name":"parallel_search","arguments":{"search_queries":["geneva weather"]}},"index":0}]}}]}"""
        )
        val message = chunk.choices.single().message!!
        assertThat(extractResponseText(message.content)).contains("<think>")
        assertThat(message.toolCalls!!.single().function.name).isEqualTo("parallel_search")
        assertThat(message.toolCalls.single().function.arguments).contains("geneva weather")
    }

    @Test fun accumulateStreamTextDedupesCumulativeDeltas() {
        val first = accumulateStreamText("", "<think>hi")
        val second = accumulateStreamText(first, "<think>hi there")
        val third = accumulateStreamText(second, "!")
        assertThat(second).isEqualTo("<think>hi there")
        assertThat(third).isEqualTo("<think>hi there!")
    }
}
