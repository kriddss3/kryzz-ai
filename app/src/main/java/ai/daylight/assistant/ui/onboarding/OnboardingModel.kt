package ai.daylight.assistant.ui.onboarding

import androidx.annotation.DrawableRes
import ai.daylight.assistant.R

/**
 * The four onboarding steps (components.md section 9), in order. Each has a 96dp dot icon,
 * a title of at most two words in the dot face, and a body of at most three lines.
 */
enum class OnboardingStep(@DrawableRes val icon: Int, val title: String, val body: String) {
    LOCAL(
        R.drawable.ic_kz_phone_local_dot,
        "LOCAL FIRST",
        "Your chats stay on this phone. No account and no analytics. Answers come from the AI providers you choose."
    ),
    KEYS(
        R.drawable.ic_kz_key_dot,
        "YOUR KEYS",
        "Kryzz runs on your own keys. OpenRouter is needed to chat. Parallel and Fish Audio are optional."
    ),
    MEMORY(
        R.drawable.ic_kz_memory_dot,
        "MEMORY",
        "Kryzz can remember things you share, on this phone only, so later chats fit you better."
    ),
    PRIVACY(
        R.drawable.ic_kz_shield_dot,
        "PRIVACY",
        "Approximate location helps with weather, news and places near you. It stays off unless you turn it on."
    ),
}

/**
 * How the steps connect. Continue moves on. Skip means "finish setup now": it appears on the
 * steps after the keys (and the keys step itself) once a chat key is in, and leaves every
 * choice not made yet switched off. The keys step can't be passed without an OpenRouter key,
 * which was the rule before the redesign too.
 */
object OnboardingFlow {
    val steps: List<OnboardingStep> = OnboardingStep.entries
    val count: Int get() = steps.size

    fun step(index: Int): OnboardingStep = steps[index.coerceIn(0, steps.lastIndex)]

    /** The top-left counter: "( 1 / 4 )". */
    fun counter(index: Int): String = "( ${index.coerceIn(0, steps.lastIndex) + 1} / ${steps.size} )"

    fun isLast(index: Int): Boolean = index >= steps.lastIndex

    fun next(index: Int): Int = (index + 1).coerceAtMost(steps.lastIndex)

    /** The step Back returns to, or null on the first step (Back then leaves the app). */
    fun back(index: Int): Int? = if (index > 0) index - 1 else null

    /** The app can be entered once there is an OpenRouter key (typed in, or tested and stored). */
    fun hasChatKey(openRouterKey: String, openRouterVerified: Boolean): Boolean =
        openRouterKey.isNotBlank() || openRouterVerified

    /** Continue is unlit only on the keys step while there is no chat key. */
    fun canContinue(index: Int, openRouterKey: String, openRouterVerified: Boolean): Boolean =
        step(index) != OnboardingStep.KEYS || hasChatKey(openRouterKey, openRouterVerified)

    /** Skip is shown on the keys and memory steps; the last step's own button finishes. */
    fun showsSkip(index: Int): Boolean = step(index).let { it == OnboardingStep.KEYS || it == OnboardingStep.MEMORY }

    fun canSkip(index: Int, openRouterKey: String, openRouterVerified: Boolean): Boolean =
        showsSkip(index) && hasChatKey(openRouterKey, openRouterVerified)

    fun primaryLabel(index: Int): String = if (isLast(index)) "Start" else "Continue"

    /** The mono tag next to a provider's name. */
    fun requirement(required: Boolean): String = if (required) "Required" else "Optional"

    /** A key's status line before it has been tested. */
    fun keyIdle(value: String): String = if (value.isBlank()) "" else "Not tested yet. Continue saves it as it is."
}
