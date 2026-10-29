package ai.daylight.assistant.ui.shell

import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.ui.Routes

/**
 * The eight arms of the squid, in rail order (top to bottom). Each arm is one capability.
 *
 * [route] is the working destination the arm opens today. Null means the arm is unlit: it
 * is drawn as a hollow ring and only says "not yet". Make lit up in C7 and Find in C8; Crew
 * lights up in its own phase. Link stays unlit for good (a future project): it opens its COMING LATER page
 * (Routes.LINK), which is not a working screen.
 */
enum class Arm(val label: String, val route: String?) {
    TALK("Talk", Routes.CHAT),
    MAKE("Make", Routes.MAKE),
    FIND("Find", Routes.FIND),
    CREW("Crew", null),
    RUN("Run", Routes.CHAT),
    VOICE("Voice", Routes.CHAT),
    LINK("Link", null),
    TUNE("Tune", Routes.SETTINGS);

    /** True when the arm opens a working screen. */
    val live: Boolean get() = route != null
}

private fun isChatRoute(route: String?): Boolean =
    route == Routes.CHAT || route?.startsWith("chat/") == true

/**
 * The arm a route belongs to, or null for Home, a depth page (settings subpages, models,
 * skills, cron, memory) and onboarding. A chat route is Run in agent mode, Voice when
 * it was opened for voice, and Talk otherwise. The chat screen refines this live (its
 * mode switch and voice session) through [InkStripEffect].
 */
fun armForRoute(route: String?, mode: AssistantMode = AssistantMode.CHAT, voice: Boolean = false): Arm? = when {
    isChatRoute(route) -> when {
        voice -> Arm.VOICE
        mode == AssistantMode.AGENT -> Arm.RUN
        else -> Arm.TALK
    }
    route == Routes.SETTINGS -> Arm.TUNE
    route == Routes.MAKE -> Arm.MAKE
    route == Routes.FIND -> Arm.FIND
    route == Routes.LINK -> Arm.LINK
    else -> null
}

/** The rail is drawn only on arm screens, not on Home, depth pages or onboarding. */
fun railVisibleForRoute(route: String?): Boolean = armForRoute(route) != null

/**
 * The strip and the rail's gutter are part of every screen except onboarding. Before the
 * first destination is known (route == null) the shell follows where the app will land.
 */
fun shellChromeVisibleForRoute(route: String?, onboardingComplete: Boolean = true): Boolean =
    if (route == null) onboardingComplete else route != Routes.ONBOARDING

/** The arm a chat conversation shows as, from its mode. */
fun armForMode(mode: AssistantMode): Arm = if (mode == AssistantMode.AGENT) Arm.RUN else Arm.TALK

/**
 * Which rail slot a touch at [y] is over. Each slot is a band [pitch] tall centred on its
 * dot; touches above the first or below the last dot clamp to the ends, so a drag that
 * overshoots the rail keeps the end arm.
 */
fun railIndexAt(y: Float, firstCenter: Float, pitch: Float, count: Int = Arm.entries.size): Int {
    if (count <= 0 || pitch <= 0f) return 0
    val raw = kotlin.math.floor((y - firstCenter + pitch / 2f) / pitch).toInt()
    return raw.coerceIn(0, count - 1)
}
