package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ApiMessage
import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.FunctionDefinition
import ai.daylight.assistant.data.remote.ToolChoice
import ai.daylight.assistant.data.remote.ToolDefinition
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class MiniMaxToolSafetyTest {

    @Test fun datetimeSchemaHasARequiredTimezoneProperty() {
        val params = MiniMaxToolSafety.datetimeParameters()
        assertThat(MiniMaxToolSafety.propertiesAreNonEmpty(params)).isTrue()
        assertThat(params.toString()).contains("timezone")
        assertThat(params.toString()).contains("required")
        assertThat(params.toString()).doesNotContain("\"properties\":{}")
    }

    @Test fun emptyPropertiesAreRejected() {
        val empty = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
        }
        assertThat(MiniMaxToolSafety.propertiesAreNonEmpty(empty)).isFalse()
    }

    @Test fun namedToolChoiceRetryKeepsTheTools() {
        val request = requestWith(
            tools = listOf(searchTool(), datetimeToolDef()),
            choice = ToolChoice.named("parallel_search")
        )
        val next = MiniMaxToolSafety.nextValidationRetry(request)
        assertThat(next.toolChoice).isEqualTo(ToolChoice.AUTO)
        assertThat(next.tools!!.map { it.function.name }).containsExactly("parallel_search", "get_current_time").inOrder()
    }

    @Test fun secondRetryDropsUtilityToolsInsteadOfStrippingSearch() {
        val request = requestWith(
            tools = listOf(searchTool(), datetimeToolDef(), calculateToolDef()),
            choice = ToolChoice.AUTO
        )
        val next = MiniMaxToolSafety.nextValidationRetry(request)
        assertThat(next.tools!!.map { it.function.name }).containsExactly("parallel_search")
        assertThat(next.toolChoice).isEqualTo(ToolChoice.AUTO)
    }

    @Test fun lastRetryStripsToolsWhenOnlyUtilityRemain() {
        val request = requestWith(tools = listOf(datetimeToolDef()), choice = ToolChoice.AUTO)
        val next = MiniMaxToolSafety.nextValidationRetry(request)
        assertThat(next.tools).isNull()
        assertThat(next.toolChoice).isNull()
    }

    @Test fun terminalRoundRetryDropsToolsInsteadOfAllowingCalls() {
        // v5.10: the terminal round sends tools with tool_choice "none"; a rejected request
        // must lose the tools, never switch to "auto" and let calls run past the budget.
        val request = requestWith(tools = listOf(searchTool(), datetimeToolDef()), choice = ToolChoice.NONE)
        val next = MiniMaxToolSafety.nextValidationRetry(request)
        assertThat(next.tools).isNull()
        assertThat(next.toolChoice).isNull()
    }

    @Test fun timezoneArgDefaultsToLocal() {
        assertThat(MiniMaxToolSafety.timezoneArg("")).isEqualTo("local")
        assertThat(MiniMaxToolSafety.timezoneArg("{}")).isEqualTo("local")
        assertThat(MiniMaxToolSafety.timezoneArg("""{"timezone":"Europe/Zurich"}""")).isEqualTo("Europe/Zurich")
    }

    private fun requestWith(tools: List<ToolDefinition>, choice: kotlinx.serialization.json.JsonElement) = ChatRequest(
        model = "MiniMax-M3",
        messages = listOf(ApiMessage("user", "hi")),
        tools = tools,
        toolChoice = choice
    )

    private fun searchTool() = ToolDefinition(
        function = FunctionDefinition("parallel_search", "Search", buildJsonObject { put("type", "object") })
    )

    private fun datetimeToolDef() = ToolDefinition(
        function = FunctionDefinition("get_current_time", "Time", MiniMaxToolSafety.datetimeParameters())
    )

    private fun calculateToolDef() = ToolDefinition(
        function = FunctionDefinition("calculate", "Math", buildJsonObject { put("type", "object") })
    )
}
