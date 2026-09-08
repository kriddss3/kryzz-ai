package ai.daylight.assistant.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Interactive question cards ("ask the user") — v5.7.2.
 *
 * Two delivery paths share one model:
 *
 *  - **Chat mode** has no tool calling, so the model asks by emitting a fenced
 *    ```` ```kryzz-question ```` block with a JSON body in its reply. The chat UI parses
 *    completed blocks out of the assistant message and renders a tappable card
 *    (lettered options + a free-text box) instead of the raw JSON. The user's choice
 *    is sent back as the next user message via [InlineQuestionProtocol.formatAnswerEcho].
 *  - **Agent mode** gets the real `ask_user` tool (see AgentExecutor): the executor
 *    suspends on the tool call until the UI delivers the answer, which then becomes
 *    the tool result.
 *
 * Everything here is pure and unit-tested; the UI only maps segments to composables.
 */
@Serializable
data class InlineQuestion(
    val question: String,
    val options: List<String> = emptyList()
) {
    /** Sanitised copy with trimmed/blank entries removed and caps enforced, or null when unusable. */
    fun sanitised(): InlineQuestion? {
        val q = question.trim()
        val opts = options.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(MAX_OPTIONS)
        if (q.length !in 2..MAX_QUESTION_CHARS) return null
        if (opts.any { it.length > MAX_OPTION_CHARS }) return null
        return InlineQuestion(q, opts)
    }

    companion object {
        const val MAX_QUESTION_CHARS = 240
        const val MAX_OPTIONS = 8
        const val MAX_OPTION_CHARS = 120
    }
}

/** One piece of an assistant message after question blocks are lifted out. */
sealed interface MessageSegment {
    data class Markdown(val text: String) : MessageSegment
    data class Question(val question: InlineQuestion) : MessageSegment
}

object InlineQuestionProtocol {
    const val FENCE_LANGUAGE = "kryzz-question"

    /**
     * Appended to the chat-mode system prompt so models know the card exists. Deliberately
     * short: over-specifying the format makes small models echo the example verbatim.
     */
    const val CHAT_PROTOCOL_PROMPT: String =
        "Interactive question cards: when a choice genuinely matters and you can offer concrete " +
            "options, you may ask the user with ONE fenced block per reply like " +
            "```kryzz-question {\"question\": \"Which subject first?\", \"options\": [\"Option A\", \"Option B\"]} ``` " +
            "(options may be omitted for a pure free-text question). The app renders it as tappable " +
            "options plus a free-text box, and the answer arrives as the next user message. Use it " +
            "sparingly — never for rhetorical questions, never more than one per reply."

    private val blockRegex = Regex(
        "```$FENCE_LANGUAGE[\\t ]*\\n?([\\s\\S]*?)```"
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Splits assistant message [content] into Markdown / Question segments in order. A block
     * whose JSON is missing, malformed, or fails validation stays inline as ordinary markdown
     * (it renders as a code block), so a partially streamed block never breaks the reply.
     */
    fun parse(content: String): List<MessageSegment> {
        val matches = blockRegex.findAll(content).toList()
        if (matches.isEmpty()) return listOf(MessageSegment.Markdown(content))
        val segments = mutableListOf<MessageSegment>()
        var cursor = 0
        for (match in matches) {
            val decoded = runCatching {
                json.decodeFromString(InlineQuestion.serializer(), match.groupValues[1].trim())
            }.getOrNull()?.sanitised()
            if (decoded == null) {
                // Not a usable card: leave the raw block in the text stream.
                continue
            }
            val before = content.substring(cursor, match.range.first)
            if (before.isNotBlank()) segments += MessageSegment.Markdown(before.trim())
            segments += MessageSegment.Question(decoded)
            cursor = match.range.last + 1
        }
        if (segments.isEmpty()) return listOf(MessageSegment.Markdown(content))
        val rest = content.substring(cursor)
        if (rest.isNotBlank()) segments += MessageSegment.Markdown(rest.trim())
        return segments
    }

    /** True when the content contains at least one renderable question card. */
    fun containsCard(content: String): Boolean = parse(content).any { it is MessageSegment.Question }

    /**
     * The user message sent when a card is answered. Worded so the model unambiguously
     * connects the answer to its own earlier question.
     */
    fun formatAnswerEcho(question: String, answer: String): String {
        val q = question.trim().take(InlineQuestion.MAX_QUESTION_CHARS)
        val a = answer.trim().take(500)
        return "You asked: \"$q\" — my answer: $a"
    }
}
