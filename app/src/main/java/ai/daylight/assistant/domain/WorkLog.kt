package ai.daylight.assistant.domain

import ai.daylight.assistant.data.local.MessageEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * v5.11: what the agent did for one answer, rebuilt from the TOOL rows AgentExecutor
 * already stores (no schema change). Shown collapsed under the answer as one summary line
 * ("3 searches · read 4 pages · 1 file · 48s") that expands into the steps.
 */
data class WorkLog(val steps: List<WorkStep>, val durationMs: Long?) {
    val summary: String get() = WorkLogBuilder.summary(steps, durationMs)
}

enum class WorkStepKind { SEARCH, PAGE, CALCULATION, WEATHER, FILE, MEMORY, PAST_CHATS, TIME, SCHEDULE, SKILL, QUESTION, OTHER }

/** One line of the log. [url] opens the page; [failed] steps carry the error in [detail]. */
data class WorkStep(
    val kind: WorkStepKind,
    val title: String,
    val detail: String? = null,
    val url: String? = null,
    val failed: Boolean = false
)

/** A stored tool row reduced to what grouping needs, parsed once. */
data class WorkLogRow(val createdAt: Long, val steps: List<WorkStep>)

object WorkLogBuilder {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Groups tool rows into turns and returns a log per answer id. A turn runs from a USER
     * message up to the next USER message; its answer is the last ASSISTANT message in that
     * span, which also catches rows written after the answer started (a file replaced during
     * the review pass). Answers with no steps get no entry.
     */
    fun build(visible: List<MessageEntity>, rows: List<WorkLogRow>): Map<String, WorkLog> {
        if (rows.isEmpty()) return emptyMap()
        val messages = visible.sortedBy { it.createdAt }
        val sortedRows = rows.sortedBy { it.createdAt }
        val logs = mutableMapOf<String, WorkLog>()
        val userIndexes = messages.indices.filter { messages[it].role == "USER" }
        userIndexes.forEachIndexed { turn, userIndex ->
            val nextUserIndex = userIndexes.getOrNull(turn + 1) ?: messages.size
            val start = messages[userIndex].createdAt
            val end = if (nextUserIndex < messages.size) messages[nextUserIndex].createdAt else Long.MAX_VALUE
            val answer = messages.subList(userIndex + 1, nextUserIndex).lastOrNull { it.role == "ASSISTANT" } ?: return@forEachIndexed
            val steps = sortedRows.filter { it.createdAt in start until end }.flatMap { it.steps }
            if (steps.isNotEmpty()) logs[answer.id] = WorkLog(steps, answer.totalGenerationTimeMs)
        }
        return logs
    }

    /** Parses one stored tool row. update_plan rows and unreadable rows give no steps. */
    fun steps(toolName: String?, content: String): List<WorkStep> {
        val tool = toolName.orEmpty()
        if (tool == AgentTurnPolicy.UPDATE_PLAN) return emptyList()
        val data = runCatching { json.parseToJsonElement(content) }.getOrNull() as? JsonObject
            ?: return listOf(WorkStep(kindOf(tool), baseTitle(tool)))
        if (data.string("status") == "error") {
            return listOf(
                WorkStep(
                    kind = kindOf(tool),
                    title = data.string("detail")?.let { "${baseTitle(tool)}: $it" } ?: baseTitle(tool),
                    detail = data.string("error") ?: "The step failed.",
                    failed = true
                )
            )
        }
        return when (tool) {
            AgentTurnPolicy.PARALLEL_SEARCH -> {
                val sources = (data["sources"] as? JsonArray)?.size ?: 0
                val sourceLabel = plural(sources, "source")
                val queries = (data["queries"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
                    .orEmpty()
                if (queries.isEmpty()) listOf(WorkStep(WorkStepKind.SEARCH, "Web search", sourceLabel))
                else queries.mapIndexed { index, query ->
                    WorkStep(WorkStepKind.SEARCH, query, if (index == queries.lastIndex) sourceLabel else null)
                }
            }
            AgentTurnPolicy.FETCH_URL -> {
                val url = data.string("url")
                val host = url?.let(ToolActivityDetail::hostAndPath)?.substringBefore('/')
                listOf(WorkStep(WorkStepKind.PAGE, data.string("title") ?: host ?: "Web page", host, url))
            }
            AgentTurnPolicy.GET_WEATHER -> listOf(
                WorkStep(WorkStepKind.WEATHER, data.string("place")?.let { "Weather in $it" } ?: "Weather", data.string("current")?.let { ToolActivityDetail.shorten(it, 80) })
            )
            "calculate" -> listOf(
                WorkStep(WorkStepKind.CALCULATION, listOfNotNull(data.string("expression"), data.string("result")).joinToString(" = ").ifBlank { "Calculation" })
            )
            AgentTurnPolicy.CREATE_ARTIFACT, "generate_image", "generate_video", "generate_audio" -> listOf(
                WorkStep(WorkStepKind.FILE, data.string("title") ?: "File", data.string("file_name"))
            )
            "create_code_project" -> listOf(
                WorkStep(
                    WorkStepKind.FILE, data.string("title") ?: "Code project",
                    listOfNotNull((data["file_count"] as? JsonPrimitive)?.intOrNull?.let { plural(it, "file") }, data.string("file_name")).joinToString(" · ")
                )
            )
            "create_skill" -> listOf(WorkStep(WorkStepKind.SKILL, "Saved skill: ${data.string("name") ?: "skill"}"))
            "search_past_chats" -> listOf(
                WorkStep(WorkStepKind.PAST_CHATS, "Past chats: ${data.string("query").orEmpty()}".trimEnd(':', ' '), data.count("match_count", "match"))
            )
            "recall_memories" -> listOf(
                WorkStep(WorkStepKind.MEMORY, "Memories: ${data.string("query").orEmpty()}".trimEnd(':', ' '), data.count("match_count", "match"))
            )
            "remember_fact" -> listOf(
                WorkStep(WorkStepKind.MEMORY, if (data.string("status") == "saved") "Remembered" else "Memory not saved", data.string("fact"))
            )
            "get_current_time" -> listOf(WorkStep(WorkStepKind.TIME, "Checked the time", listOfNotNull(data.string("weekday"), data.string("iso_time")).joinToString(" ").ifBlank { null }))
            "schedule_task" -> listOf(WorkStep(WorkStepKind.SCHEDULE, "Scheduled: ${data.string("title").orEmpty()}".trimEnd(':', ' '), data.string("schedule")))
            "ask_user" -> listOf(
                WorkStep(WorkStepKind.QUESTION, "Asked: ${data.string("question").orEmpty()}".trimEnd(':', ' '), data.string("answer") ?: "No answer")
            )
            else -> listOf(WorkStep(WorkStepKind.OTHER, baseTitle(tool)))
        }
    }

    /** "3 searches · read 4 pages · 1 file · 48s"; failed steps are counted apart. */
    fun summary(steps: List<WorkStep>, durationMs: Long?): String {
        val done = steps.filterNot { it.failed }
        val searches = done.count { it.kind == WorkStepKind.SEARCH }
        val pages = done.count { it.kind == WorkStepKind.PAGE }
        val files = done.count { it.kind == WorkStepKind.FILE }
        val calculations = done.count { it.kind == WorkStepKind.CALCULATION }
        val other = done.size - searches - pages - files - calculations
        val failed = steps.size - done.size
        val parts = listOfNotNull(
            searches.takeIf { it > 0 }?.let { plural(it, "search", "searches") },
            pages.takeIf { it > 0 }?.let { "read ${plural(it, "page")}" },
            files.takeIf { it > 0 }?.let { plural(it, "file") },
            calculations.takeIf { it > 0 }?.let { plural(it, "calculation") },
            other.takeIf { it > 0 }?.let { if (done.size == other) plural(it, "step") else plural(it, "other step") },
            failed.takeIf { it > 0 }?.let { "$it failed" },
            durationMs?.let(::formatDuration)
        )
        return parts.joinToString(" · ")
    }

    fun formatDuration(ms: Long): String {
        val seconds = ms / 1_000
        return when {
            ms < 1_000 -> "<1s"
            seconds < 60 -> "${seconds}s"
            seconds % 60 == 0L -> "${seconds / 60}m"
            else -> "${seconds / 60}m ${seconds % 60}s"
        }
    }

    private fun kindOf(tool: String): WorkStepKind = when (tool) {
        AgentTurnPolicy.PARALLEL_SEARCH -> WorkStepKind.SEARCH
        AgentTurnPolicy.FETCH_URL -> WorkStepKind.PAGE
        AgentTurnPolicy.GET_WEATHER -> WorkStepKind.WEATHER
        "calculate" -> WorkStepKind.CALCULATION
        AgentTurnPolicy.CREATE_ARTIFACT, "create_code_project", "generate_image", "generate_video", "generate_audio" -> WorkStepKind.FILE
        "remember_fact", "recall_memories" -> WorkStepKind.MEMORY
        "search_past_chats" -> WorkStepKind.PAST_CHATS
        "get_current_time" -> WorkStepKind.TIME
        "schedule_task" -> WorkStepKind.SCHEDULE
        "create_skill" -> WorkStepKind.SKILL
        "ask_user" -> WorkStepKind.QUESTION
        else -> WorkStepKind.OTHER
    }

    private fun baseTitle(tool: String): String = when (tool) {
        AgentTurnPolicy.PARALLEL_SEARCH -> "Web search"
        AgentTurnPolicy.FETCH_URL -> "Page read"
        AgentTurnPolicy.GET_WEATHER -> "Weather"
        "calculate" -> "Calculation"
        AgentTurnPolicy.CREATE_ARTIFACT -> "File"
        "create_code_project" -> "Code project"
        "generate_image" -> "Image"
        "generate_video" -> "Video"
        "generate_audio" -> "Audio"
        "remember_fact" -> "Memory"
        "recall_memories" -> "Memory lookup"
        "search_past_chats" -> "Past chats"
        "get_current_time" -> "Time check"
        "schedule_task" -> "Schedule"
        "create_skill" -> "Skill"
        "ask_user" -> "Question"
        else -> tool.replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Step" }
    }

    private fun plural(count: Int, singular: String, pluralForm: String = singular + "s") =
        "$count ${if (count == 1) singular else pluralForm}"

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)

    private fun JsonObject.count(key: String, noun: String): String? = (get(key) as? JsonPrimitive)?.intOrNull?.let { plural(it, noun, noun + "es") }
}
