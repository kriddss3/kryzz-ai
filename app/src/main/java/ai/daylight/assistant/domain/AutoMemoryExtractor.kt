package ai.daylight.assistant.domain

import ai.daylight.assistant.data.MemoryRepository
import ai.daylight.assistant.data.remote.ApiMessage
import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.ProviderPreferences
import ai.daylight.assistant.data.remote.StreamEvent
import ai.daylight.assistant.data.remote.TextProviderClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object AutoMemoryParser {

    const val MAX_ACTIONS_PER_TURN = 3

    enum class Op { ADD, UPDATE }

    data class MemoryAction(
        val op: Op,
        val content: String,
        val category: MemoryEngine.Category,
        val targetId: String? = null
    )

    private val SHORT_ACKS = setOf(
        "ok", "okay", "thanks", "thank you", "thx", "got it",
        "cool", "yes", "no", "sure", "nice", "great", "sounds good"
    )

    /** True when [userText] is too brief or conversational to carry durable user facts. */
    fun shouldSkipTurn(userText: String): Boolean {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || trimmed.length < 8) return true
        val normalized = trimmed
            .lowercase()
            .trimEnd('.', '!', '?', ',', ' ')
            .replace(Regex("""\s+"""), " ")
        return normalized in SHORT_ACKS
    }

    /** Extracts validated memory add/update actions from raw model JSON output; never throws. */
    fun parse(raw: String, json: Json): List<MemoryAction> {
        val stripped = stripMarkdownFences(raw)
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val candidate = stripped.substring(start, end + 1)
        val root = runCatching { json.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
            ?: return emptyList()
        val actionsArray = root["actions"] as? JsonArray ?: return emptyList()

        val result = ArrayList<MemoryAction>(MAX_ACTIONS_PER_TURN)
        for (element in actionsArray) {
            if (result.size >= MAX_ACTIONS_PER_TURN) break
            val obj = element as? JsonObject ?: continue
            val opRaw = obj.stringValue("op")?.lowercase() ?: continue
            val op = when (opRaw) {
                "add" -> Op.ADD
                "update" -> Op.UPDATE
                else -> continue
            }
            val targetId = obj.stringValue("id")?.takeIf { it.isNotEmpty() }
            if (op == Op.UPDATE && targetId == null) continue

            val rawContent = obj.stringValue("content") ?: continue
            val content = rawContent.replace(Regex("""\s+"""), " ").trim()
            if (content.length !in MemoryEngine.MIN_MEMORY_CHARS..MemoryEngine.MAX_MEMORY_CHARS) continue
            if (MemoryEngine.containsSecretPattern(content)) continue

            val categoryRaw = obj.stringValue("category")?.uppercase()
            val category = MemoryEngine.Category.entries.firstOrNull { it.name == categoryRaw }
                ?: MemoryEngine.Category.OTHER
            result.add(
                MemoryAction(
                    op = op,
                    content = content,
                    category = category,
                    targetId = if (op == Op.UPDATE) targetId else null
                )
            )
        }
        return result
    }

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.contains("```")) return trimmed
        return trimmed.lines()
            .filterNot { it.trimStart().startsWith("```") }
            .joinToString("\n")
            .trim()
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
}

data class AutoMemoryModelTarget(
    val modelId: String,
    val apiKey: String,
    val client: TextProviderClient,
    val useOpenRouterPriceSort: Boolean = false
)

/**
 * Background learner that extracts or updates durable user memories after each completed turn
 * using a tiny cheap model, falling back to local regex extraction when no remote target exists.
 */
class AutoMemoryExtractor(
    private val scope: CoroutineScope,
    private val memories: MemoryRepository,
    private val isEnabled: suspend () -> Boolean,
    private val resolveCheapModel: suspend () -> AutoMemoryModelTarget?,
    private val json: Json,
    private val onMemorySaved: (conversationId: String) -> Unit = {}
) {
    fun onTurnCompleted(conversationId: String, userText: String, assistantText: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                extractAndApply(conversationId, userText, assistantText)
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }
    }

    private suspend fun extractAndApply(conversationId: String, userText: String, assistantText: String) {
        if (!isEnabled()) return
        if (AutoMemoryParser.shouldSkipTurn(userText)) return

        val target = resolveCheapModel()
        if (target == null) {
            if (memories.ingest(userText, conversationId)) onMemorySaved(conversationId)
            return
        }

        val existing = memories.all().take(MAX_EXISTING_CONTEXT)
        val existingBlock = if (existing.isEmpty()) {
            "(none)"
        } else {
            existing.joinToString("\n") { "- [id=${it.id}] (${it.category}) ${it.content}" }
        }

        val request = ChatRequest(
            model = target.modelId,
            messages = listOf(
                ApiMessage("system", EXTRACTION_SYSTEM_PROMPT),
                ApiMessage(
                    "user",
                    buildExtractionPrompt(
                        existingBlock = existingBlock,
                        userText = userText.trim().take(MAX_TURN_CHARS),
                        assistantText = assistantText.trim().take(MAX_TURN_CHARS)
                    )
                )
            ),
            stream = false,
            provider = if (target.useOpenRouterPriceSort) ProviderPreferences(sort = "price") else null,
            maxTokens = 320
        )

        val response = StringBuilder()
        target.client.stream(target.apiKey, request).collect { event ->
            when (event) {
                is StreamEvent.Delta -> response.append(event.text)
                is StreamEvent.Failure -> throw event.error
                is StreamEvent.Done, is StreamEvent.UsageUpdate -> Unit
            }
        }

        val actions = AutoMemoryParser.parse(response.toString(), json)
        if (actions.isEmpty()) return

        var anySaved = false
        for (action in actions) {
            val saved = when (action.op) {
                AutoMemoryParser.Op.ADD -> memories.addFromAuto(action.content, action.category, conversationId)
                AutoMemoryParser.Op.UPDATE -> {
                    val targetId = action.targetId ?: continue
                    memories.updateContent(targetId, action.content, action.category, conversationId)
                }
            }
            if (saved) anySaved = true
        }
        if (anySaved) onMemorySaved(conversationId)
    }

    private fun buildExtractionPrompt(existingBlock: String, userText: String, assistantText: String): String =
        """
        Existing memories:
        $existingBlock

        Latest turn:
        User: $userText
        Assistant: $assistantText
        """.trimIndent()

    private companion object {
        const val MAX_EXISTING_CONTEXT = 40
        const val MAX_TURN_CHARS = 1500
        const val EXTRACTION_SYSTEM_PROMPT =
            "Extract durable facts about the user from the latest conversation turn. " +
                "Output ONLY a JSON object of the form " +
                "{\"actions\":[{\"op\":\"add\"|\"update\",\"id\":\"...\",\"category\":\"USER\"|\"PREFERENCE\"|\"GOAL\"|\"PROJECT\"|\"OTHER\",\"content\":\"...\"}]}. " +
                "Include only durable personal facts: identity, background, preferences, long-term goals, ongoing projects, or tools/constraints. " +
                "Use \"op\":\"update\" with the existing \"id\" when superseding or refining an existing memory, or \"op\":\"add\" for a new fact. " +
                "Never save one-off requests, greetings, assistant-only trivia, or secrets, passwords, tokens, or API keys. " +
                "Return {\"actions\":[]} if nothing durable was learned."
    }
}
