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
 * provider default, so every caller outside Make behaves exactly as before.
 */
data class MediaOptions(val aspectRatio: String? = null)

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
