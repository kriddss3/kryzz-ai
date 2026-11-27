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

enum class ChatProvider(
    val id: String,
    val label: String,
    val description: String,
    val baseUrl: String,
    val keyUrl: String?,
    val defaultModel: String,
    val cheapModel: String,
    val supportsModelsEndpoint: Boolean = true,
    val fallbackModelIds: List<String> = emptyList(),
) {
    OPENROUTER(
        id = "openrouter",
        label = "OpenRouter",
        description = "Chat, image, video, audio and speech models.",
        baseUrl = "https://openrouter.ai/api/v1",
        keyUrl = "https://openrouter.ai/keys",
        defaultModel = "openai/gpt-4o-mini",
        cheapModel = "google/gemini-2.5-flash-lite",
        fallbackModelIds = listOf("openai/gpt-4o-mini", "google/gemini-2.5-flash-lite"),
    ),
    MINIMAX(
        id = "minimax",
        label = "MiniMax (token plan only)",
        description = "Token plan only — chat, image, video and audio from MiniMax.",
        baseUrl = "https://api.minimax.io/v1",
        keyUrl = "https://platform.minimax.io/",
        defaultModel = "MiniMax-M3",
        cheapModel = "MiniMax-M3",
        fallbackModelIds = listOf("MiniMax-M3"),
    ),
    DEEPSEEK(
        id = "deepseek",
        label = "DeepSeek",
        description = "Chat and reasoning models from DeepSeek.",
        baseUrl = "https://api.deepseek.com/v1",
        keyUrl = "https://platform.deepseek.com/api_keys",
        defaultModel = "deepseek-chat",
        cheapModel = "deepseek-chat",
        fallbackModelIds = listOf("deepseek-chat", "deepseek-reasoner"),
    ),
    OPENCODE(
        id = "opencode",
        label = "OpenCode",
        description = "Curated coding and chat models via OpenCode.",
        baseUrl = "https://opencode.ai/zen/v1",
        keyUrl = "https://opencode.ai/auth",
        defaultModel = "gpt-5-nano",
        cheapModel = "gpt-5-nano",
        fallbackModelIds = listOf("gpt-5-nano", "claude-haiku-4-5", "gemini-2.5-flash", "deepseek-v3", "qwen3-coder", "kimi-k2"),
    ),
    OPENAI(
        id = "openai",
        label = "OpenAI",
        description = "Chat and reasoning models from OpenAI.",
        baseUrl = "https://api.openai.com/v1",
        keyUrl = "https://platform.openai.com/api-keys",
        defaultModel = "gpt-4o-mini",
        cheapModel = "gpt-4o-mini",
        fallbackModelIds = listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4.1", "gpt-4o", "o4-mini"),
    ),
    GEMINI(
        id = "gemini",
        label = "Google Gemini",
        description = "Google AI models via OpenAI-compatible API.",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        keyUrl = "https://aistudio.google.com/apikey",
        defaultModel = "gemini-2.5-flash",
        cheapModel = "gemini-2.5-flash-lite",
        fallbackModelIds = listOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro"),
    ),
    GROQ(
        id = "groq",
        label = "Groq",
        description = "Ultra-fast open-weight model inference.",
        baseUrl = "https://api.groq.com/openai/v1",
        keyUrl = "https://console.groq.com/keys",
        defaultModel = "llama-3.3-70b-versatile",
        cheapModel = "llama-3.1-8b-instant",
        fallbackModelIds = listOf("llama-3.1-8b-instant", "llama-3.3-70b-versatile"),
    ),
    XAI(
        id = "xai",
        label = "xAI (Grok)",
        description = "Chat and reasoning models from xAI.",
        baseUrl = "https://api.x.ai/v1",
        keyUrl = "https://console.x.ai/",
        defaultModel = "grok-3-mini",
        cheapModel = "grok-3-mini",
        fallbackModelIds = listOf("grok-3-mini", "grok-3", "grok-4"),
    ),
    MISTRAL(
        id = "mistral",
        label = "Mistral",
        description = "Chat and reasoning models from Mistral.",
        baseUrl = "https://api.mistral.ai/v1",
        keyUrl = "https://console.mistral.ai/api-keys",
        defaultModel = "mistral-small-latest",
        cheapModel = "ministral-8b-latest",
        fallbackModelIds = listOf("ministral-8b-latest", "mistral-small-latest", "mistral-large-latest"),
    ),
    TOGETHER(
        id = "together",
        label = "Together AI",
        description = "Open-source models hosted by Together AI.",
        baseUrl = "https://api.together.xyz/v1",
        keyUrl = "https://api.together.xyz/settings/api-keys",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        cheapModel = "meta-llama/Llama-3.2-3B-Instruct-Turbo",
        fallbackModelIds = listOf("meta-llama/Llama-3.2-3B-Instruct-Turbo", "meta-llama/Llama-3.3-70B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3"),
    ),
    FIREWORKS(
        id = "fireworks",
        label = "Fireworks AI",
        description = "Fast serverless open-weight models.",
        baseUrl = "https://api.fireworks.ai/inference/v1",
        keyUrl = "https://fireworks.ai/account/api-keys",
        defaultModel = "accounts/fireworks/models/llama-v3p3-70b-instruct",
        cheapModel = "accounts/fireworks/models/llama-v3p1-8b-instruct",
        fallbackModelIds = listOf("accounts/fireworks/models/llama-v3p1-8b-instruct", "accounts/fireworks/models/llama-v3p3-70b-instruct"),
    ),
    PERPLEXITY(
        id = "perplexity",
        label = "Perplexity",
        description = "Search-grounded chat models from Perplexity.",
        baseUrl = "https://api.perplexity.ai",
        keyUrl = "https://www.perplexity.ai/settings/api",
        defaultModel = "sonar",
        cheapModel = "sonar",
        supportsModelsEndpoint = false,
        fallbackModelIds = listOf("sonar", "sonar-pro", "sonar-reasoning"),
    ),
    MOONSHOT(
        id = "moonshot",
        label = "Moonshot (Kimi)",
        description = "Chat and reasoning models from Moonshot.",
        baseUrl = "https://api.moonshot.ai/v1",
        keyUrl = "https://platform.moonshot.ai/console/api-keys",
        defaultModel = "kimi-k2-0711-preview",
        cheapModel = "moonshot-v1-8k",
        fallbackModelIds = listOf("moonshot-v1-8k", "kimi-k2-0711-preview"),
    ),
    CEREBRAS(
        id = "cerebras",
        label = "Cerebras",
        description = "Wafer-scale ultra-fast model inference.",
        baseUrl = "https://api.cerebras.ai/v1",
        keyUrl = "https://cloud.cerebras.ai/",
        defaultModel = "llama-3.3-70b",
        cheapModel = "llama3.1-8b",
        fallbackModelIds = listOf("llama3.1-8b", "llama-3.3-70b"),
    ),
    QWEN(
        id = "qwen",
        label = "Qwen (DashScope)",
        description = "Alibaba Cloud models via DashScope.",
        baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        keyUrl = "https://dashscope.console.aliyun.com/apiKey",
        defaultModel = "qwen-plus",
        cheapModel = "qwen-turbo",
        fallbackModelIds = listOf("qwen-turbo", "qwen-plus", "qwen-max"),
    ),
    ZHIPU(
        id = "zhipu",
        label = "Z.ai (GLM)",
        description = "Chat and reasoning models from Z.ai.",
        baseUrl = "https://api.z.ai/api/paas/v4",
        keyUrl = "https://z.ai/manage-apikey/apikey-list",
        defaultModel = "glm-4.5-air",
        cheapModel = "glm-4.5-flash",
        fallbackModelIds = listOf("glm-4.5-flash", "glm-4.5-air", "glm-4.5"),
    );

    companion object {
        fun from(value: String?): ChatProvider {
            val clean = value?.trim().orEmpty()
            if (clean.isEmpty()) return OPENROUTER
            return entries.firstOrNull {
                it.name.equals(clean, ignoreCase = true) || it.id.equals(clean, ignoreCase = true)
            } ?: OPENROUTER
        }
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
    AUTO("Agent workspace", "Auto", "Choose the best available tools and produce a practical, finished result. Work in multiple steps when needed: call a tool, read the result, then call the next tool until the job is done. For a task with three or more distinct steps, first call update_plan with a short checklist, then keep it current as you work (mark a step in_progress when you start it and done when it is finished); skip the plan for simple questions. Call parallel_search whenever the answer depends on fresh, niche, uncertain, or source-backed facts — never answer such questions from memory alone. When search excerpts are too thin to answer precisely, call fetch_url on the one to three most relevant result URLs and read them before answering; independent lookups can be called together in one round. Call get_current_time before answering anything that depends on today or now. Call calculate for arithmetic instead of guessing. Call get_weather for forecasts, fetch_url when the user pasted a link, remember_fact / recall_memories for durable personal facts, and schedule_task for recurring reminders. When the user asks for a picture, video, or piece of music, call the matching generate_image / generate_video / generate_audio tool instead of describing how to make it. When they want any file (PDF, Word, Excel, PowerPoint, a web page, code, data, an SVG, a calendar event, a ZIP of several files), call create_artifact with a file_name whose extension sets the format; for a reusable skill or a multi-file code project call create_skill / create_code_project. When a choice genuinely matters and you can offer concrete options, call ask_user to ask the user with tappable options instead of guessing."),
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

/**
 * What a generated file is. v5.13.7: PRESENTATION (.pptx), TEXT (any text or code file written as
 * given) and ARCHIVE (a .zip of several files) joined the list; they are stored by name.
 */
@Serializable
enum class OutputKind { DOCUMENT, SPREADSHEET, DATABASE, CODE, IMAGE, VIDEO, AUDIO, PDF, CALENDAR, PRESENTATION, TEXT, ARCHIVE }

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

/**
 * C7: a generated file as it appears in Make's recent ink (and the Inkwell later): the file,
 * where it lives in the history, when it was made, and the request that made it.
 */
data class RecentOutput(
    val output: GeneratedOutput,
    val messageId: String,
    val conversationId: String,
    val createdAt: Long,
    val prompt: String?
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
    val folders: List<ExportConversationFolder> = emptyList(),
    /** C9 Crew bots. Optional: backups made before 5.12.10 have none and still import. */
    val bots: List<ExportBot> = emptyList()
)

/** A Crew bot in a backup. Its routines are not in backups (no routine ever was). */
@Serializable
data class ExportBot(
    val id: String,
    val name: String,
    val persona: String,
    val avatarSeed: Int = 0,
    val conversationId: String,
    val createdAt: Long,
    val updatedAt: Long
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
    val cachedInputTokens: Int? = null,
    /** v2.1: the answer's thinking trace; older backups have none. */
    val thinking: List<ThoughtStep> = emptyList()
)

data class ConversationUsage(
    val totalTokens: Long = 0,
    val inputTokens: Long = 0,
    val cacheHitInputTokens: Long = 0,
    val cacheMissInputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cost: Double = 0.0
)
