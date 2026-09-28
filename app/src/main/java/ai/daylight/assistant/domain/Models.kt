package ai.daylight.assistant.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Citation(
    val title: String,
    val url: String,
    @SerialName("publish_date") val publishDate: String? = null,
    val excerpt: String = ""
)

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }
enum class MessageStatus { COMPLETE, STREAMING, ERROR, CANCELLED }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class BackgroundStyle { CONSTELLATION, ANIMATED_AURORA, VIDEO_AURORA, PALETTE_BUBBLES, PLAIN_GREY, COLOURED }
enum class GradientPalette { OCEAN, SUNSET, AURORA, LAVENDER, FOREST, CANDY, EMBER, MONOCHROME }
enum class AppPalette { GREEN, BLUE, PURPLE, RED, ORANGE, PINK, TEAL, MONOCHROME }
enum class FontStyle { SYSTEM, MODERN, SERIF, MONOSPACE, PLAYFUL }
enum class ChatDensity { COMPACT, COMFORTABLE, SPACIOUS }
enum class TextPalette { ADAPTIVE, HIGH_CONTRAST, MINT, LAVENDER, AMBER, ROSE }
enum class AssistantMode { CHAT, AGENT }
/** v5.10: MAX is the optional model Agent mode's Max quality runs on (see AgentQuality). */
enum class ModelPurpose { CHAT, AGENT, RESEARCH, MAX, IMAGE, VIDEO, AUDIO }

enum class ChatProvider(val label: String) {
    OPENROUTER("OpenRouter"),
    MINIMAX("MiniMax");

    companion object {
        fun from(value: String): ChatProvider = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OPENROUTER
    }
}

@Serializable
data class ChatAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String? = null
)

enum class ReasoningEffort(val apiValue: String?, val label: String) {
    AUTO(null, "Model default"),
    NONE("none", "Off"),
    MINIMAL("minimal", "Minimal"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    XHIGH("xhigh", "Extra high"),
    MAX("max", "Maximum");

    companion object {
        fun fromApi(value: String?): ReasoningEffort = entries.firstOrNull { it.apiValue == value } ?: AUTO
    }
}

enum class AgentCapability(val title: String, val shortLabel: String, val instruction: String) {
    AUTO("Agent workspace", "Auto", "Choose the best available tools and produce a practical, finished result. Work in multiple steps when needed: call a tool, read the result, then call the next tool until the job is done. For a task with three or more distinct steps, first call update_plan with a short checklist, then keep it current as you work (mark a step in_progress when you start it and done when it is finished); skip the plan for simple questions. Call parallel_search whenever the answer depends on fresh, niche, uncertain, or source-backed facts — never answer such questions from memory alone. When search excerpts are too thin to answer precisely, call fetch_url on the one to three most relevant result URLs and read them before answering; independent lookups can be called together in one round. Call get_current_time before answering anything that depends on today or now. Call calculate for arithmetic instead of guessing. Call get_weather for forecasts, fetch_url when the user pasted a link, remember_fact / recall_memories for durable personal facts, and schedule_task for recurring reminders. When the user asks for a picture, video, or piece of music, call the matching generate_image / generate_video / generate_audio tool instead of describing how to make it. When they want a document, spreadsheet, database, skill, or code zip, call create_artifact / create_skill / create_code_project. When a choice genuinely matters and you can offer concrete options, call ask_user to ask the user with tappable options instead of guessing."),
    DOCUMENT("Document maker", "Document", "Create a polished Markdown document and return it through the create_artifact tool."),
    DEEP_RESEARCH("Deep search", "Deep search", "Run at least two focused, related web-search passes when the configured tool bound permits it. Cover different query angles, reconcile disagreements, distinguish sourced facts from inference, and cite claims."),
    DATABASE("Database maker", "Database", "Design a production-minded SQLite database schema with constraints, indexes, comments, safe seed rows, and useful starter queries, then return it through the create_artifact tool."),
    SPREADSHEET("Spreadsheet maker", "Spreadsheet", "Create clean CSV data that opens in Excel or another spreadsheet app, including useful headings and formulas where portable, then return it through the create_artifact tool."),
    WIDE_SEARCH("Wide search", "Wide search", "Search broadly with several concise query angles, synthesize coverage, and cite every time-sensitive claim."),
    SKILL_MAKER("Skill maker", "Skill maker", "Turn the user's requested workflow into a reusable local assistant skill. You must call create_skill exactly once with concise instructions and useful example prompts."),
    CODE("Full-stack code", "Code", "Create a complete multi-file full-stack application that can be saved and developed on this phone. Include a README, setup/build commands, frontend and backend files where the request calls for both, safe configuration examples without secrets, and call create_code_project exactly once."),
    IMAGE("Image generation", "Image", "Generate an image from the user's request with the configured provider image model."),
    VIDEO("Video generation", "Video", "Generate a video from the user's request with the configured provider video model."),
    AUDIO("Music production", "Music", "Produce original music tracks using the configured provider audio models. Specify genre, mood, and instrumentation, then return the generated audio through the create_artifact tool.");
}

@Serializable
enum class OutputKind { DOCUMENT, SPREADSHEET, DATABASE, CODE, IMAGE, VIDEO, AUDIO, PDF }

@Serializable
data class GeneratedOutput(
    val id: String,
    val kind: OutputKind,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val content: String? = null,
    val localPath: String? = null,
    val remoteUrl: String? = null
)

data class AssistantPreset(val id: String, val title: String, val prompt: String) {
    companion object {
        val balanced = AssistantPreset(
            "balanced", "Balanced Assistant",
            "You are Kryzz AI, a warm, direct, practical personal AI assistant. Give useful answers without unnecessary ceremony. Decide when current information requires web search. When search is used, distinguish sourced facts from inference and cite sources with bracketed numbers such as [1]. Include working links. Never claim an external action was performed unless a tool result proves it. Require explicit user confirmation before any destructive or external action; web search itself does not require confirmation."
        )
        val concise = AssistantPreset(
            "concise", "Concise",
            balanced.prompt + " Prefer short answers and only the detail needed to act."
        )
        val thoughtful = AssistantPreset(
            "thoughtful", "Thoughtful",
            balanced.prompt + " Explore ambiguity, alternatives, and tradeoffs carefully before recommending a path."
        )
        val proactive = AssistantPreset(
            "proactive", "Proactive",
            balanced.prompt + " Anticipate useful next steps and surface likely pitfalls, while staying within the user's requested scope."
        )
        val uwu = AssistantPreset(
            "uwu", "UWU Anime Companion",
            balanced.prompt + " Use a playful, stereotypically cute anime-girl voice with cheerful warmth, light kaomoji, and an occasional 'uwu' for casual fun. Keep it readable and never let the persona reduce factual accuracy. For medical, legal, financial, safety-critical, or serious professional work, drop the playful act and answer clearly and soberly. Never imply that you are human."
        )
        val custom = AssistantPreset("custom", "Custom", "")
        val all = listOf(balanced, concise, thoughtful, proactive, uwu, custom)
        fun byId(id: String) = all.firstOrNull { it.id == id } ?: balanced
    }
}

@Serializable
data class ExportBundle(
    val formatVersion: Int = 1,
    val exportedAt: Long,
    val conversations: List<ExportConversation>,
    val folders: List<ExportConversationFolder> = emptyList()
)

@Serializable
data class ExportConversationFolder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ExportConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
    val messages: List<ExportMessage>,
    val searchDepth: Int = 2,
    val searchWidth: Int = 3,
    val pinned: Boolean = false,
    val folderId: String? = null
)

@Serializable
data class ExportMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val citations: List<Citation> = emptyList(),
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null,
    val mode: String = AssistantMode.CHAT.name,
    val capability: String? = null,
    val outputs: List<GeneratedOutput> = emptyList(),
    val attachments: List<ChatAttachment> = emptyList(),
    val cachedInputTokens: Int? = null
)

data class ConversationUsage(
    val totalTokens: Long = 0,
    val inputTokens: Long = 0,
    val cacheHitInputTokens: Long = 0,
    val cacheMissInputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cost: Double = 0.0
)
