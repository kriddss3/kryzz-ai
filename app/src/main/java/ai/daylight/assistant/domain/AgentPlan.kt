package ai.daylight.assistant.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class PlanStepStatus { PENDING, IN_PROGRESS, DONE }

data class PlanStep(val title: String, val status: PlanStepStatus)

/**
 * The agent's live checklist for one conversation, written through the `update_plan` tool.
 * The chat renders it as a card while the turn runs so the user can see what the agent
 * intends to do and how far it has got.
 */
data class AgentPlan(val conversationId: String, val steps: List<PlanStep>) {
    val doneCount: Int get() = steps.count { it.status == PlanStepStatus.DONE }
}

/**
 * Lenient `update_plan` argument parser. Every call carries the whole plan, so parsing
 * is stateless: `{"steps":[{"title":"…","status":"in_progress"}]}`. Plain strings are
 * accepted as pending steps and common status synonyms are mapped, because a plan that
 * bounces on a spelling difference wastes a tool round and shows the user nothing.
 */
internal object AgentPlanParser {
    const val MAX_STEPS = 8
    const val MAX_TITLE_CHARS = 80

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** The parsed steps, or null when the arguments hold no usable step. */
    fun parse(arguments: String): List<PlanStep>? {
        val root = runCatching { json.parseToJsonElement(arguments) }.getOrNull() as? JsonObject ?: return null
        val raw = (root["steps"] ?: root["plan"] ?: root["items"]) as? JsonArray ?: return null
        val steps = raw.mapNotNull(::step)
            .distinctBy { it.title.lowercase() }
            .take(MAX_STEPS)
        return steps.ifEmpty { null }
    }

    private fun step(element: JsonElement): PlanStep? {
        val (title, status) = when (element) {
            is JsonPrimitive -> element.contentOrNull to null
            is JsonObject -> {
                val text = (element["title"] ?: element["step"] ?: element["text"] ?: element["content"]) as? JsonPrimitive
                text?.contentOrNull to (element["status"] as? JsonPrimitive)?.contentOrNull
            }
            else -> null to null
        }
        val clean = title?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_TITLE_CHARS)
        if (clean.isNullOrBlank()) return null
        return PlanStep(clean, status(status))
    }

    private fun status(value: String?): PlanStepStatus =
        when (value?.trim()?.lowercase()?.replace('-', '_')?.replace(' ', '_')) {
            "in_progress", "active", "doing", "current", "running", "started" -> PlanStepStatus.IN_PROGRESS
            "done", "complete", "completed", "finished", "skipped" -> PlanStepStatus.DONE
            else -> PlanStepStatus.PENDING
        }
}
