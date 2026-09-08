package ai.daylight.assistant.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Shared agent-loop helpers: lenient search-argument parsing, the known-tool registry used
 * by inline-XML recovery, and skill auto-activation. Kept out of [AgentExecutor] so they
 * can be unit-tested without a live provider.
 *
 * Turn continuation policy moved to [AgentTurnPolicy] in 5.7 — the old prose-regex
 * decision tree (decideAfterModelText, synthesizeSearch*) was removed because it decided
 * from what the model *said* instead of what it *did*, and its FORCE_FINAL_ANSWER branch
 * stripped every tool mid-turn, which broke multi-step Auto runs.
 */
internal data class SkillSummary(
    val name: String,
    val description: String,
    val instructions: String,
    val examplePrompts: String = ""
)

internal data class ActiveSkillSet(
    val primary: List<SkillSummary>,
    val alsoActive: List<SkillSummary>
) {
    val all: List<SkillSummary> get() = primary + alsoActive
    val isEmpty: Boolean get() = primary.isEmpty() && alsoActive.isEmpty()
}

internal object AgentLoopPolicy {

    val knownToolNames: Set<String> = setOf(
        "parallel_search",
        "search_past_chats",
        "create_artifact",
        "create_skill",
        "create_code_project",
        "generate_image",
        "generate_video",
        "generate_audio",
        "get_current_time",
        "calculate",
        "get_weather",
        "fetch_url",
        "remember_fact",
        "recall_memories",
        "schedule_task"
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "you", "your", "are", "was",
        "were", "will", "have", "has", "had", "not", "but", "use", "using", "please",
        "make", "create", "write", "just", "can", "could", "would", "should", "about",
        "into", "than", "then", "them", "they", "their", "what", "when", "where", "which",
        "who", "whom", "why", "how", "does", "did", "its", "it's", "our", "out", "any"
    )

    /**
     * Accepts the schema name (`search_queries`), the Kotlin property name (`searchQueries`),
     * and common model aliases (`queries` / a lone `query` string).
     */
    fun parseSearchQueries(arguments: String): List<String> {
        if (arguments.isBlank()) return emptyList()
        val element = runCatching { json.parseToJsonElement(arguments) }.getOrNull() as? JsonObject
            ?: return emptyList()
        val raw = element["search_queries"]
            ?: element["searchQueries"]
            ?: element["queries"]
            ?: element["query"]
        return when (raw) {
            null -> emptyList()
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            is JsonPrimitive -> raw.contentOrNull?.trim()?.let { listOf(it) }.orEmpty()
            else -> emptyList()
        }.filter { it.isNotBlank() }
    }

    fun selectActiveSkills(skills: List<SkillSummary>, userText: String): ActiveSkillSet {
        if (skills.isEmpty()) return ActiveSkillSet(emptyList(), emptyList())
        val scored = skills.map { it to skillRelevance(it, userText) }
        val matched = scored.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        return if (matched.isNotEmpty()) {
            ActiveSkillSet(primary = matched.take(6), alsoActive = skills.filter { it !in matched })
        } else {
            ActiveSkillSet(primary = skills, alsoActive = emptyList())
        }
    }

    fun formatActiveSkillsPrompt(set: ActiveSkillSet): String {
        if (set.isEmpty) return ""
        val builder = StringBuilder()
        builder.append(
            "\n\nThe user has already created the following local skills. They are ACTIVE for this request. " +
                "Follow every skill that applies — do not ask whether to use them, and do not wait for the user to pick one.\n"
        )
        if (set.primary.isNotEmpty()) {
            builder.append("\nPRIMARY SKILLS (matched this request — this is the workflow):\n")
            set.primary.forEach { builder.append(renderSkill(it)) }
        }
        if (set.alsoActive.isNotEmpty()) {
            builder.append("\nAlso active (name + purpose only — follow PRIMARY first):\n")
            set.alsoActive.forEach { builder.append("- ${it.name}: ${it.description}\n") }
        }
        return builder.toString().take(20_000)
    }

    fun skillRelevance(skill: SkillSummary, userText: String): Int {
        val queryTokens = tokenize(userText)
        if (queryTokens.isEmpty()) return 0
        val query = userText.lowercase()
        var score = 0
        if (skill.name.isNotBlank() && query.contains(skill.name.lowercase())) score += 8
        score += 4 * tokenize(skill.name).count { it in queryTokens }
        score += 2 * tokenize(skill.description).count { it in queryTokens }
        score += 2 * tokenize(skill.examplePrompts).count { it in queryTokens }
        score += tokenize(skill.instructions).take(40).count { it in queryTokens }
        return score
    }

    private fun renderSkill(skill: SkillSummary): String =
        "\nSkill: ${skill.name}\nPurpose: ${skill.description}\nInstructions:\n${skill.instructions}\n"

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
}
