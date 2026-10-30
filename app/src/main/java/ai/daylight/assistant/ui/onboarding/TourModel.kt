package ai.daylight.assistant.ui.onboarding

import androidx.annotation.DrawableRes
import ai.daylight.assistant.R

/** Where each provider hands out keys (v2.1). Opened from onboarding, the tour and Tune > Keys. */
object KeyLinks {
    const val OPENROUTER = "https://openrouter.ai/settings/keys"
    const val FISH_AUDIO = "https://fish.audio/app/api-keys"
    const val PARALLEL = "https://platform.parallel.ai"

    /** "openrouter.ai/settings/keys": the link as the user reads it. */
    fun display(url: String): String = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

/** One key the tour points to: who, what it unlocks, whether Kryzz needs it, and where to get it. */
data class TourKey(val name: String, val purpose: String, val required: Boolean, val url: String)

/**
 * v2.1: the first-run tour, shown once on Home after onboarding (and from Tune > Tour). Each
 * page is a dot icon, a dot-face title of at most two words and a short body.
 */
enum class TourPage(@DrawableRes val icon: Int, val title: String, val body: String) {
    HOME(
        R.drawable.ic_kz_grid_dot,
        "EIGHT ARMS",
        "Home is a board of eight arms. Tap a row to open it. A row lights up while that arm is busy, and the squid shows how things are going."
    ),
    RAIL(
        R.drawable.ic_kz_model_dot,
        "THE RAIL",
        "Inside an arm, the column of dots on the right edge is the rail. Tap a dot, or slide along it, to jump straight to another arm. Back returns to Home."
    ),
    CHATS(
        R.drawable.ic_kz_history_dot,
        "YOUR CHATS",
        "Swipe in from the left edge for your chat library: folders, pins and backups. Long-press any question or answer to copy, share, edit or hear it."
    ),
    TALK(
        R.drawable.ic_kz_web_dot,
        "TALK, RUN",
        "Talk answers and searches the web. Run takes on bigger jobs: plans, files and several steps. Tap THINKING above an answer to see how Kryzz worked it out."
    ),
    VOICE(
        R.drawable.ic_kz_voice_wave_dot,
        "VOICE",
        "Voice is a hands-free call: Gemini hears you and Fish Audio answers out loud. In any chat you can also hold the reply line to dictate. Let go to drop the words in, slide up to cancel."
    ),
    MAKE(
        R.drawable.ic_kz_image_dot,
        "MAKE MORE",
        "Make draws images and clips; try the Nothing style. Find gets pictures that are free to reuse. Crew runs your own bots on a schedule. Everything made lands in the Inkwell."
    ),
    KEYS(
        R.drawable.ic_kz_key_dot,
        "YOUR KEYS",
        "Kryzz runs on your own keys. Get them here, then paste them in Tune > Keys."
    );

    companion object {
        /** The links on the last page. */
        val keys = listOf(
            TourKey("OpenRouter", "Every model: Talk, Run, Voice, Make", required = true, url = KeyLinks.OPENROUTER),
            TourKey("Fish Audio", "Spoken replies in Voice", required = false, url = KeyLinks.FISH_AUDIO),
            TourKey("Parallel", "Web search and sources", required = false, url = KeyLinks.PARALLEL),
        )
    }
}

/** How the tour moves. Pure, so it is unit tested. */
object TourFlow {
    val pages: List<TourPage> = TourPage.entries
    val count: Int get() = pages.size

    fun page(index: Int): TourPage = pages[index.coerceIn(0, pages.lastIndex)]

    /** "( 3 / 7 )", like onboarding's counter. */
    fun counter(index: Int): String = "( ${index.coerceIn(0, pages.lastIndex) + 1} / ${pages.size} )"

    fun isLast(index: Int): Boolean = index >= pages.lastIndex

    fun next(index: Int): Int = (index + 1).coerceAtMost(pages.lastIndex)

    /** The page Back returns to, or null on the first page (Back then closes the tour). */
    fun back(index: Int): Int? = if (index > 0) index - 1 else null

    fun primaryLabel(index: Int): String = if (isLast(index)) "Start" else "Next"
}
