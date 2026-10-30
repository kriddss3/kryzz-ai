package ai.daylight.assistant.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** What one step of an answer's thinking was. */
@Serializable
enum class ThoughtKind {
    /** The model's own reasoning, as the provider streamed it. */
    THINK,

    /** Something the model said before it went to work ("Let me look that up."). */
    SAY,

    /** A tool it called: [ThoughtStep.text] is the tool's name, [ThoughtStep.detail] what it was about. */
    TOOL
}

@Serializable
data class ThoughtStep(val kind: ThoughtKind, val text: String, val detail: String? = null)

/**
 * v2.1: an answer's thinking, kept with the answer (messages.thinkingJson, Room 14).
 *
 * Before this, every round of a multi-step turn streamed into a message that was deleted as
 * soon as the model called a tool, so whatever it said first ("I'll check the latest
 * figures...") vanished, and the provider's reasoning was asked to be left out entirely.
 * Now one message carries the whole turn, and this trace keeps the reasoning, the lines it
 * said between steps and the tools it called, in order.
 */
object ThinkingTrace {
    /** One reasoning step is cut at this length so a long think cannot bloat the chat database. */
    const val MAX_STEP_CHARS = 16_000

    /** Past this many steps the middle ones are dropped (the start and the newest stay), for the same reason. */
    const val MAX_STEPS = 60

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(steps: List<ThoughtStep>): String = if (steps.isEmpty()) "[]" else json.encodeToString(steps)

    fun decode(raw: String?): List<ThoughtStep> {
        if (raw.isNullOrBlank() || raw == "[]") return emptyList()
        return runCatching { json.decodeFromString<List<ThoughtStep>>(raw) }.getOrDefault(emptyList())
    }

    /** How many steps did work (said something or called a tool); reasoning alone counts as one. */
    fun stepCount(steps: List<ThoughtStep>): Int {
        val actions = steps.count { it.kind != ThoughtKind.THINK }
        return if (actions == 0 && steps.isNotEmpty()) 1 else actions
    }

    /**
     * The newest thing worth showing on one line while the answer is still being worked out:
     * the last sentence of the reasoning, the last thing said, or the tool's [label].
     */
    fun latestLine(steps: List<ThoughtStep>, label: (ThoughtStep) -> String): String? {
        val last = steps.lastOrNull() ?: return null
        return when (last.kind) {
            ThoughtKind.TOOL -> label(last)
            ThoughtKind.SAY -> oneLine(last.text)
            ThoughtKind.THINK -> lastSentence(last.text)
        }?.takeIf { it.isNotBlank() }
    }

    /** "12S", "1M 05S": how long the answer took or has been thinking. */
    fun durationLabel(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) + 500L) / 1_000L
        return if (seconds < 60) "${seconds}S" else "${seconds / 60}M ${(seconds % 60).toString().padStart(2, '0')}S"
    }

    internal fun oneLine(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    /** The last complete or still-growing sentence of [text], at most 140 characters. */
    internal fun lastSentence(text: String): String? {
        val clean = text
            .replace(Regex("[*_#`>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty()) return null
        val sentences = clean.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val last = sentences.lastOrNull() ?: clean
        // A sentence that has only just started says little; show it with the one before.
        val shown = if (last.length < 24 && sentences.size > 1) sentences[sentences.size - 2] + " " + last else last
        return if (shown.length <= 140) shown else "…" + shown.takeLast(139).trimStart()
    }
}

/**
 * Builds one turn's trace as the agent loop runs (pure; AgentExecutor drives it). Reasoning
 * of the round in progress is replaced as it streams; [endRound] closes the round with what
 * the model said and the tools it called.
 */
class TurnTrace {
    private val steps = mutableListOf<ThoughtStep>()
    private var reasoning = ""

    val isEmpty: Boolean get() = steps.isEmpty() && reasoning.isBlank()

    /** The round's reasoning so far (the whole text, not a delta). */
    fun reasoning(text: String) {
        reasoning = text
    }

    /** The round ended in tool calls: keep its reasoning, what it said, and each call. */
    fun endRound(narration: String, tools: List<Pair<String, String?>>) {
        flushReasoning()
        val said = narration.trim()
        if (said.isNotEmpty()) steps += ThoughtStep(ThoughtKind.SAY, said.take(ThinkingTrace.MAX_STEP_CHARS))
        tools.forEach { (name, detail) -> steps += ThoughtStep(ThoughtKind.TOOL, name, detail?.take(200)) }
        trim()
    }

    /** The round wrote the answer: only its reasoning joins the trace. */
    fun finishRound() {
        flushReasoning()
        trim()
    }

    fun steps(): List<ThoughtStep> {
        val live = reasoning.trim()
        return if (live.isEmpty()) steps.toList() else steps + ThoughtStep(ThoughtKind.THINK, live.take(ThinkingTrace.MAX_STEP_CHARS))
    }

    fun json(): String = ThinkingTrace.encode(steps())

    private fun flushReasoning() {
        val text = reasoning.trim()
        reasoning = ""
        if (text.isNotEmpty()) steps += ThoughtStep(ThoughtKind.THINK, text.take(ThinkingTrace.MAX_STEP_CHARS))
    }

    private fun trim() {
        while (steps.size > ThinkingTrace.MAX_STEPS) steps.removeAt(ThinkingTrace.MAX_STEPS / 2)
    }
}
