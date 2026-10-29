package ai.daylight.assistant.ui.shell

import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.ui.Routes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArmRoutesTest {
    @Test fun eightArmsInRailOrder() {
        assertThat(Arm.entries.map { it.name })
            .containsExactly("TALK", "MAKE", "FIND", "CREW", "RUN", "VOICE", "LINK", "TUNE")
            .inOrder()
    }

    @Test fun crewLightsUpInC9AndLinkStaysUnlit() {
        assertThat(Arm.entries.filter { it.live })
            .containsExactly(Arm.TALK, Arm.MAKE, Arm.FIND, Arm.CREW, Arm.RUN, Arm.VOICE, Arm.TUNE)
        assertThat(Arm.CREW.live).isTrue()
        assertThat(Arm.LINK.live).isFalse()
        assertThat(Arm.LINK.route).isNull()
    }

    @Test fun liveArmsPointAtRealRoutes() {
        assertThat(Arm.TALK.route).isEqualTo(Routes.CHAT)
        assertThat(Arm.RUN.route).isEqualTo(Routes.CHAT)
        assertThat(Arm.VOICE.route).isEqualTo(Routes.CHAT)
        assertThat(Arm.TUNE.route).isEqualTo(Routes.SETTINGS)
        assertThat(Arm.MAKE.route).isEqualTo(Routes.MAKE)
        assertThat(armForRoute(Routes.MAKE)).isEqualTo(Arm.MAKE)
        assertThat(railVisibleForRoute(Routes.MAKE)).isTrue()
        assertThat(Routes.make()).isEqualTo("make")
        assertThat(Routes.make(ai.daylight.assistant.domain.MakeKind.VIDEO)).isEqualTo("make?kind=VIDEO")
        assertThat(Arm.FIND.route).isEqualTo(Routes.FIND)
        assertThat(armForRoute(Routes.FIND)).isEqualTo(Arm.FIND)
        assertThat(railVisibleForRoute(Routes.FIND)).isTrue()
        assertThat(defaultInkLine(Routes.FIND).text).isEqualTo("FIND READY")
        assertThat(Arm.CREW.route).isEqualTo(Routes.CREW)
        assertThat(armForRoute(Routes.CREW)).isEqualTo(Arm.CREW)
        assertThat(defaultInkLine(Routes.CREW).text).isEqualTo("CREW READY")
    }

    @Test fun aBotsPageKeepsTheRailWithCrewLit() {
        assertThat(Routes.bot("b1")).isEqualTo("crew/b1")
        assertThat(armForRoute(Routes.BOT)).isEqualTo(Arm.CREW)
        assertThat(railVisibleForRoute(Routes.BOT)).isTrue()
        assertThat(railVisibleForRoute(Routes.CREW)).isTrue()
        // The chat library opens on the Crew list (like the other arms), not on a bot's page.
        assertThat(ai.daylight.assistant.ui.panelSwipeEnabledForRoute(Routes.CREW)).isTrue()
        assertThat(ai.daylight.assistant.ui.panelSwipeEnabledForRoute(Routes.BOT)).isFalse()
    }

    @Test fun chatRoutesMapToTalkRunOrVoice() {
        val chat = Routes.chat("abc")
        assertThat(armForRoute(Routes.CHAT)).isEqualTo(Arm.TALK)
        assertThat(armForRoute(chat, AssistantMode.CHAT)).isEqualTo(Arm.TALK)
        assertThat(armForRoute(chat, AssistantMode.AGENT)).isEqualTo(Arm.RUN)
        assertThat(armForRoute(chat, AssistantMode.CHAT, voice = true)).isEqualTo(Arm.VOICE)
        assertThat(armForRoute(chat, AssistantMode.AGENT, voice = true)).isEqualTo(Arm.VOICE)
    }

    @Test fun settingsHomeIsTuneButDepthPagesHaveNoArm() {
        assertThat(armForRoute(Routes.SETTINGS)).isEqualTo(Arm.TUNE)
        listOf(
            Routes.API, Routes.PERSONA, Routes.TOOLS, Routes.PRIVACY, Routes.APPEARANCE,
            Routes.VOICE, Routes.MEMORY, Routes.CRON, Routes.SKILLS, Routes.LLMS,
            Routes.models(), Routes.ONBOARDING, Routes.BOOT, null
        ).forEach { route ->
            assertThat(armForRoute(route)).isNull()
            assertThat(railVisibleForRoute(route)).isFalse()
        }
        assertThat(railVisibleForRoute(Routes.chat("x"))).isTrue()
        assertThat(railVisibleForRoute(Routes.SETTINGS)).isTrue()
    }

    @Test fun chromeHidesOnlyOnOnboarding() {
        assertThat(shellChromeVisibleForRoute(Routes.ONBOARDING)).isFalse()
        assertThat(shellChromeVisibleForRoute(Routes.BOOT)).isTrue()
        assertThat(shellChromeVisibleForRoute(Routes.API)).isTrue()
        assertThat(shellChromeVisibleForRoute(null, onboardingComplete = true)).isTrue()
        assertThat(shellChromeVisibleForRoute(null, onboardingComplete = false)).isFalse()
    }

    @Test fun modeMapsToChatArm() {
        assertThat(armForMode(AssistantMode.CHAT)).isEqualTo(Arm.TALK)
        assertThat(armForMode(AssistantMode.AGENT)).isEqualTo(Arm.RUN)
    }

    @Test fun railTouchMapsToTheNearestDotAndClampsAtTheEnds() {
        val pitch = 26f
        val first = 13f
        Arm.entries.indices.forEach { i ->
            val centre = first + i * pitch
            assertThat(railIndexAt(centre, first, pitch)).isEqualTo(i)
            assertThat(railIndexAt(centre - pitch / 2f + 0.5f, first, pitch)).isEqualTo(i)
            assertThat(railIndexAt(centre + pitch / 2f - 0.5f, first, pitch)).isEqualTo(i)
        }
        assertThat(railIndexAt(-500f, first, pitch)).isEqualTo(0)
        assertThat(railIndexAt(5_000f, first, pitch)).isEqualTo(Arm.entries.lastIndex)
        assertThat(railIndexAt(10f, first, 0f)).isEqualTo(0)
    }

    @Test fun railStaysUnderTheSystemGestureExclusionLimit() {
        // Android honours at most 200dp of back-gesture exclusion per screen edge.
        val dotsSpan = ArmRailSpec.Pitch.value * (Arm.entries.size - 1) + ArmRailSpec.BarHeight.value
        assertThat(dotsSpan).isAtMost(200f)
    }
}
