package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.isMiniMaxImageModel
import ai.daylight.assistant.data.remote.isMiniMaxVideoModel

/** The ratio an image or video render uses when nobody picks one (the behaviour before Make). */
const val DEFAULT_MEDIA_RATIO = "16:9"

/** What every MiniMax video request asks for. Make shows these as facts, so they live in one place. */
const val MINIMAX_VIDEO_SECONDS = 5
const val MINIMAX_VIDEO_RESOLUTION = "2K"

/**
 * Make's choices for one image or video render (C7). A null [aspectRatio] keeps the
 * provider default, so every caller outside Make behaves exactly as before. v2.1: [style]
 * dresses the prompt the provider gets; the prompt saved in the chat stays the user's own.
 */
data class MediaOptions(val aspectRatio: String? = null, val style: MakeStyle = MakeStyle.PLAIN)

/** v2.1: Make's look. [NOTHING] asks for the Nothing OS feel: black, white, one red, dots and Glyph light. */
enum class MakeStyle(val label: String, val caption: String) {
    PLAIN("Plain", "Just your prompt"),
    NOTHING("Nothing", "Black, white and red, dot matrix, Glyph light");

    /** The prompt the provider gets for [prompt] in this style. */
    fun styled(prompt: String): String = when (this) {
        PLAIN -> prompt
        NOTHING -> buildString {
            append(prompt.trim())
            appendLine()
            appendLine()
            append(NOTHING_STYLE_PROMPT)
        }
    }

    companion object {
        fun from(value: String?): MakeStyle = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PLAIN

        /**
         * The Nothing look in words an image model follows: the monochrome palette with its one
         * red, dot-matrix type and patterns, Glyph light strips, see-through industrial parts,
         * and a lot of empty space. "No logos" keeps the brand itself out of the picture.
         */
        const val NOTHING_STYLE_PROMPT =
            "Style: Nothing OS design language. Monochrome palette of deep black, pure white and soft greys with a single small " +
                "signal-red accent (#D71921). Dot-matrix details: dotted lettering like the Ndot typeface, dot grids and pixel-dot " +
                "patterns. Glyph Interface lighting: thin, glowing white LED strips, rings and arcs on dark surfaces. Transparent, " +
                "industrial hardware with visible inner parts and small screws where it suits the subject. Clean minimal composition " +
                "with generous negative space, precise soft studio light, subtle grain, crisp edges. No other colours, no clutter, " +
                "no logos, no brand names, no readable text unless the prompt asks for it."
    }
}

/**
 * Where a video job stands at its provider, for Make's status line. [status] is the
 * provider's own word in lower case ("queued", "processing"…), or one of the app's own
 * steps: [SENDING] before the job is accepted and [DOWNLOADING] once it has finished.
 */
data class MediaJobStatus(val conversationId: String, val status: String, val at: Long) {
    companion object {
        const val SENDING = "sending"
        const val DOWNLOADING = "downloading"
    }
}

/**
 * True when a render goes to MiniMax rather than OpenRouter: the MiniMax chat provider is
 * on, or the chosen model is a MiniMax one. The agent's generation and Make's dials both
 * use this, so the screen only offers what the request will really carry.
 */
fun mediaUsesMiniMax(video: Boolean, provider: ChatProvider, model: String): Boolean =
    provider == ChatProvider.MINIMAX || if (video) isMiniMaxVideoModel(model) else isMiniMaxImageModel(model)
