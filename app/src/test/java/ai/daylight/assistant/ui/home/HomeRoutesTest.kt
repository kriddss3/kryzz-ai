package ai.daylight.assistant.ui.home

import ai.daylight.assistant.ui.Routes
import ai.daylight.assistant.ui.consumeChatAutoSend
import ai.daylight.assistant.ui.hasChatDraft
import ai.daylight.assistant.ui.queueChatSend
import ai.daylight.assistant.ui.shell.Arm
import ai.daylight.assistant.ui.shell.InkLine
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.armForRoute
import ai.daylight.assistant.ui.shell.defaultInkLine
import ai.daylight.assistant.ui.shell.railVisibleForRoute
import ai.daylight.assistant.ui.shell.shellChromeVisibleForRoute
import ai.daylight.assistant.ui.sidePanelOpenAllowedForRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeRoutesTest {
    @Test fun homeHasTheStripButNoRailAndIsNotAnArm() {
        assertThat(armForRoute(Routes.HOME)).isNull()
        assertThat(railVisibleForRoute(Routes.HOME)).isFalse()
        assertThat(shellChromeVisibleForRoute(Routes.HOME)).isTrue()
    }

    @Test fun linkOpensItsPageButStaysUnlit() {
        assertThat(armForRoute(Routes.LINK)).isEqualTo(Arm.LINK)
        assertThat(railVisibleForRoute(Routes.LINK)).isTrue()
        assertThat(Arm.LINK.live).isFalse()
    }

    @Test fun drawerOpensFromHome() {
        assertThat(sidePanelOpenAllowedForRoute(Routes.HOME)).isTrue()
    }

    @Test fun stripLines() {
        assertThat(defaultInkLine(Routes.HOME)).isEqualTo(InkLine("KRYZZ READY"))
        assertThat(defaultInkLine(Routes.LINK)).isEqualTo(InkLine("LINK NOT CONNECTED"))
        assertThat(InkLines.home(0)).isEqualTo(InkLine("KRYZZ READY"))
        assertThat(InkLines.home(1)).isEqualTo(InkLine("1 ARM WORKING", live = true))
        assertThat(InkLines.home(2)).isEqualTo(InkLine("2 ARMS WORKING", live = true))
        // Every line fits the 5x7 font unchanged.
        listOf(InkLines.home(3), InkLines.LINK, InkLines.inkwell(12), InkLines.inkwell(1, "CLIP")).forEach {
            assertThat(InkLine.sanitize(it.text)).isEqualTo(it.text)
        }
    }

    @Test fun homeHandOffSendsExactlyOnce() {
        val id = "home-handoff-test"
        queueChatSend(id, "What is a squid's ink made of?")
        // The draft keeps the new, still empty conversation from being pruned.
        assertThat(hasChatDraft(id)).isTrue()
        assertThat(consumeChatAutoSend(id)).isTrue()
        assertThat(consumeChatAutoSend(id)).isFalse()
        assertThat(consumeChatAutoSend("never-queued")).isFalse()
    }
}
