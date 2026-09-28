package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.ToolChoice
import ai.daylight.assistant.data.remote.ToolDefinition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * MiniMax 2013 safety for Auto tool lists. Empty `properties: {}` plus
 * `additionalProperties: false` is invalid JSON Schema; MiniMax rejects the
 * whole turn, then the old retry stripped every tool.
 */
internal object MiniMaxToolSafety {

    val utilityToolNames: Set<String> = setOf(
        "get_current_time",
        "calculate",
        "get_weather",
        "fetch_url",
        "remember_fact",
        "recall_memories",
        "schedule_task",
        "update_plan"
    )

    fun datetimeParameters() = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("timezone") {
                put("type", "string")
                put("description", "Pass \"local\" to use this phone's timezone.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("timezone")) })
    }

    fun propertiesAreNonEmpty(parameters: JsonObject): Boolean {
        val properties = parameters["properties"]?.jsonObject ?: return false
        return properties.isNotEmpty()
    }

    fun withoutUtilityTools(tools: List<ToolDefinition>): List<ToolDefinition> =
        tools.filterNot { it.function.name in utilityToolNames }

    /**
     * After a MiniMax 2013 / validation reject:
     * 0. v5.10: a terminal round (tool_choice "none") drops its tools, as it did before it
     *    kept them for caching; switching it to "auto" would let tools run past the budget
     * 1. drop a named/required tool_choice (keep the tools)
     * 2. drop the extra utility tools, keep search / artifact / media
     * 3. strip tools entirely
     */
    fun nextValidationRetry(request: ChatRequest): ChatRequest {
        val tools = request.tools
        if (tools.isNullOrEmpty() || request.toolChoice == ToolChoice.NONE) return request.copy(tools = null, toolChoice = null)
        if (request.toolChoice != null && request.toolChoice != ToolChoice.AUTO) {
            return request.copy(toolChoice = ToolChoice.AUTO)
        }
        val reduced = withoutUtilityTools(tools)
        if (reduced.size < tools.size && reduced.isNotEmpty()) {
            return request.copy(tools = reduced, toolChoice = ToolChoice.AUTO)
        }
        return request.copy(tools = null, toolChoice = null)
    }

    fun timezoneArg(arguments: String): String {
        if (arguments.isBlank() || arguments == "{}") return "local"
        val root = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(arguments).jsonObject
        }.getOrNull() ?: return "local"
        return root["timezone"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim().orEmpty()
            .ifBlank { "local" }
    }
}
