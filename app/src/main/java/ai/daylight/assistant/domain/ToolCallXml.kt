package ai.daylight.assistant.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Some chat models (notably several OpenRouter / Qwen / Hermes variants) ignore the
 * native function-calling field and instead emit their tool calls inline in the content
 * stream wrapped in tags such as (tools)...(/tools), (tool_call)...(/tool_call), or
 * (tool)...(/tool). When that happens two things go wrong for the chat UI:
 *
 *  1. the raw XML leaks into the assistant bubble as visible text, and
 *  2. the agent never actually runs the requested tool, so the user gets no result
 *     (the classic "it said it would check, then never gave me the weather").
 *
 * This object fixes both: [scrubToolBlocks] strips the tag blocks (including a still-open
 * partial block) from the live bubble exactly like String.scrubThinkTags, and
 * [parseToolCallXml] turns the completed blocks it finds into [ParsedToolCall]s that
 * AgentExecutor can hand to the same tool-execution loop it uses for native tool calls.
 *
 * (Angle brackets are avoided in this comment so the doc tooling does not trip on them.)
 */
internal object ToolCallXml {

    /** A tool call recovered from inline XML, mirroring the native ToolCall shape. */
    data class ParsedToolCall(
        val id: String,
        val name: String,
        val arguments: String
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Recognised wrapper tag names. We only treat content inside these tags as tool
    // calls; bare JSON objects in prose are ignored unless they name a known tool.
    private val OPEN_TAGS = listOf("tools", "tool_call", "tool", "function")

    private val FENCE = Regex(
        """```(?:json|tool|tool_call|function)?\s*(\{[\s\S]*?\})\s*```""",
        RegexOption.IGNORE_CASE
    )
    private val FUNCTION_EQ = Regex(
        """<function\s*=\s*([a-zA-Z0-9_]+)>([\s\S]*?)</function>""",
        RegexOption.IGNORE_CASE
    )
    private val QWEN_FUNCTION = Regex(
        """✿FUNCTION✿\s*([a-zA-Z0-9_]+)\s*✿ARGS✿\s*(\{[\s\S]*?\})(?:\s*✿|$)""",
        setOf(RegexOption.IGNORE_CASE)
    )

    /**
     * Removes every tool-call tag block from [text] (complete or still-streaming partial).
     * Designed to run on the accumulated text every delta, so a half-arriving block is
     * hidden until its closing tag lands.
     */
    fun scrubToolBlocks(text: String): String {
        if (text.isEmpty()) return text
        var working = text
        if (OPEN_TAGS.any { working.contains("<" + it) }) {
            working = scrubTagged(working)
        }
        working = FENCE.replace(working) { match ->
            if (fromJsonObject(parseObject(match.groupValues[1])) != null) "" else match.value
        }
        working = FUNCTION_EQ.replace(working, "")
        working = QWEN_FUNCTION.replace(working, "")
        working = working.replace("✿FUNCTION✿", "").replace("✿ARGS✿", "")
        return working
            .replace(Regex("\\r?\\n\\s*\\r?\\n\\s*\\r?\\n+"), "\n\n")
            .trim()
    }

    /**
     * Extracts completed tool calls from inline XML/JSON blocks. Handles the common
     * shapes emitted by Hermes / Qwen / Granite-style tool formats:
     *
     *  - JSON object: {"name":"x","arguments":{...}} / {"name":"x","parameters":{...}}
     *  - XML body: <name>x</name><arguments>{...}</arguments>
     *  - <tool name="x">{...}</tool> / <tool>{"name":"x",...}</tool>
     *  - any of the above wrapped in <tools>...</tools>
     *  - <function=name>{...}</function>
     *  - Qwen ✿FUNCTION✿ / ✿ARGS✿
     *  - fenced ```json / ```tool blocks
     *  - a bare JSON object whose name is a known Kryzz tool
     */
    fun parseToolCallXml(text: String): List<ParsedToolCall> {
        if (text.isEmpty()) return emptyList()
        val calls = mutableListOf<ParsedToolCall>()
        for (tag in OPEN_TAGS) {
            var index = 0
            while (true) {
                val open = text.indexOf("<" + tag, index)
                if (open < 0) break
                val closeTag = "</" + tag + ">"
                val close = text.indexOf(closeTag, open)
                if (close < 0) break
                val openEnd = text.indexOf('>', open)
                if (openEnd < 0 || openEnd >= close) { index = open + tag.length + 1; continue }
                val openTagText = text.substring(open, openEnd + 1)
                val body = text.substring(openEnd + 1, close).trim()
                parseBody(openTagText, body)?.let { calls += it }
                index = close + closeTag.length
            }
        }
        FUNCTION_EQ.findAll(text).forEach { match ->
            val name = match.groupValues[1].trim()
            val args = match.groupValues[2].trim().ifBlank { "{}" }
            if (name.isNotBlank()) {
                calls += ParsedToolCall(
                    id = "xml_${name.hashCode()}_${args.hashCode()}",
                    name = name,
                    arguments = normalizeArgs(args)
                )
            }
        }
        QWEN_FUNCTION.findAll(text).forEach { match ->
            val name = match.groupValues[1].trim()
            val args = match.groupValues[2].trim().ifBlank { "{}" }
            if (name.isNotBlank()) {
                calls += ParsedToolCall(
                    id = "xml_${name.hashCode()}_${args.hashCode()}",
                    name = name,
                    arguments = normalizeArgs(args)
                )
            }
        }
        FENCE.findAll(text).forEach { match ->
            fromJsonObject(parseObject(match.groupValues[1]))?.let { calls += it }
        }
        recoverBareKnownJson(text).forEach { calls += it }
        return calls.distinctBy { it.name to it.arguments }
    }

    private fun scrubTagged(text: String): String {
        val builder = StringBuilder(text.length)
        var cursor = 0
        while (cursor < text.length) {
            val nextOpen = OPEN_TAGS
                .mapNotNull { tag ->
                    val idx = text.indexOf("<" + tag, cursor)
                    if (idx < 0) null else idx to tag
                }
                .minByOrNull { it.first }
            if (nextOpen == null) {
                builder.append(text, cursor, text.length)
                break
            }
            val (openStart, tag) = nextOpen
            builder.append(text, cursor, openStart)
            val closeTag = "</" + tag + ">"
            val closeStart = text.indexOf(closeTag, openStart)
            if (closeStart < 0) {
                // Block is still streaming: drop the rest and wait for the closing tag.
                break
            }
            cursor = closeStart + closeTag.length
        }
        return builder.toString()
    }

    private fun parseBody(openTag: String, body: String): ParsedToolCall? {
        if (body.isEmpty()) {
            // A name="x" attribute with an empty body is still a (argument-less) call.
            val attrName = extractNameAttribute(openTag) ?: return null
            return ParsedToolCall(id = "xml_${attrName.hashCode()}_0", name = attrName, arguments = "{}")
        }
        // JSON object form first.
        fromJsonObject(parseObject(body))?.let { return it }
        // XML-ish form: <name>x</name><arguments>{...}</arguments>
        parseXmlArgs(body)?.let { return it }
        // Tag-attribute form: <tool name="x">{...}</tool>
        val attrName = extractNameAttribute(openTag)
        if (attrName != null) {
            return ParsedToolCall(
                id = "xml_${attrName.hashCode()}_${body.hashCode()}",
                name = attrName,
                arguments = normalizeArgs(body)
            )
        }
        return null
    }

    private fun fromJsonObject(obj: JsonObject?): ParsedToolCall? {
        if (obj == null) return null
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val argsElement = obj["arguments"] ?: obj["parameters"] ?: obj["args"]
        val arguments = when (argsElement) {
            null -> "{}"
            is JsonPrimitive -> argsElement.contentOrNull ?: "{}"
            else -> argsElement.toString()
        }
        return ParsedToolCall(
            id = (obj["id"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "xml_${name.hashCode()}_${arguments.hashCode()}" },
            name = name,
            arguments = arguments.ifBlank { "{}" }
        )
    }

    private val nameRegex = Regex("""<name>\s*([^<]+?)\s*</name>""", RegexOption.IGNORE_CASE)
    private val argsRegex = Regex("""<arguments>\s*(.*?)\s*</arguments>""", RegexOption.IGNORE_CASE)

    private fun parseXmlArgs(body: String): ParsedToolCall? {
        val nameMatch = nameRegex.find(body) ?: return null
        val name = nameMatch.groupValues[1].trim().ifBlank { return null }
        val argsRaw = argsRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        return ParsedToolCall(
            id = "xml_${name.hashCode()}_${argsRaw.hashCode()}",
            name = name,
            arguments = normalizeArgs(argsRaw)
        )
    }

    /** Pull a name="x" attribute out of an opening tag string such as '<tool name="x">'. */
    fun extractNameAttribute(openTag: String): String? {
        val match = Regex("""name\s*=\s*"([^"]+)\"""", RegexOption.IGNORE_CASE).find(openTag) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    private fun recoverBareKnownJson(text: String): List<ParsedToolCall> {
        val calls = mutableListOf<ParsedToolCall>()
        var index = 0
        while (index < text.length) {
            val start = text.indexOf("{\"name\"", index).takeIf { it >= 0 }
                ?: text.indexOf("{\"name\":", index).takeIf { it >= 0 }
                ?: break
            val obj = parseObjectFrom(text, start) ?: break
            val parsed = fromJsonObject(obj)
            if (parsed != null && parsed.name in AgentLoopPolicy.knownToolNames) {
                calls += parsed
            }
            index = start + 1
        }
        return calls
    }

    private fun parseObject(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    private fun parseObjectFrom(text: String, start: Int): JsonObject? {
        if (start < 0 || start >= text.length || text[start] != '{') return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val ch = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    ch == '\\' -> escape = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return parseObject(text.substring(start, i + 1))
                }
            }
        }
        return null
    }

    private fun normalizeArgs(raw: String): String {
        val trimmed = raw.trim().ifBlank { return "{}" }
        return runCatching { json.parseToJsonElement(trimmed).toString() }.getOrDefault(trimmed)
    }
}
