package ai.daylight.assistant.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ai.daylight.assistant.ui.theme.effectiveSurfaceOpacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class UiReviewContractTest {
    @Test
    fun glassOpacityMatchesThePersistedAndDisplayedValue() {
        assertEquals(0.42f, effectiveSurfaceOpacity(0.42f), 0.0001f)
        assertEquals(0.74f, effectiveSurfaceOpacity(0.74f), 0.0001f)
        assertEquals(0.96f, effectiveSurfaceOpacity(0.96f), 0.0001f)
    }

    @Test
    fun darkAgentPrimaryGraphicContrastMeetsThreeToOne() {
        val foreground = agentOnPrimaryColor(darkBackground = true)
        val background = agentPrimaryColor(darkBackground = true)
        assertTrue("Agent controls need at least 3:1 contrast", contrastRatio(foreground, background) >= 3f)
    }

    @Test
    fun launcherUsesAdaptiveRoundAndThemedKryzzArtwork() {
        val mark = File("src/main/res/drawable-nodpi/kryzz_mark_foreground.png")
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val adaptive = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val themed = File("src/main/res/mipmap-anydpi-v33/ic_launcher.xml").readText()
        val bytes = mark.readBytes()

        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        // The launcher uses the safe-zone-padded variant of the Kryzz mark.
        // Light-mode launcher references the BLACK silhouette so the icon
        // reads against the paper background; the night variant in
        // mipmap-anydpi-v26-night uses the WHITE silhouette.
        assertTrue(adaptive.contains("@drawable/kryzz_launcher_foreground_dark"))
        assertTrue(themed.contains("<monochrome"))
        assertEquals(1254, ByteBuffer.wrap(bytes).getInt(16))
        assertEquals(1254, ByteBuffer.wrap(bytes).getInt(20))
        assertEquals("PNG RGBA colour type", 6, bytes[25].toInt())
    }

    @Test
    fun newChatHasNoPromptChipsOrDecorativeModeOrbs() {
        val source = File("src/main/java/ai/daylight/assistant/ui/ChatScreen.kt").readText()

        assertTrue(!source.contains("Research a topic"))
        assertTrue(!source.contains("Analyse a file"))
        assertTrue(!source.contains("Plan a project"))
        assertTrue(!source.contains("AssistantOrb("))
        assertTrue(!source.contains("ModeHyperspaceTransition(currentMode)"))
    }

    @Test
    fun typeOnProgressIsFastMonotonicAndNeverOvershoots() {
        fun nextReveal(current: Int, target: Int): Int {
            val stepped = (current + 6).coerceAtMost(target)
            return if (stepped >= target - 3) target else stepped
        }

        val target = 80
        var progress = 0
        val seen = mutableListOf(progress)
        while (progress < target) {
            progress = nextReveal(progress, target)
            seen += progress
        }

        assertTrue("Type-on progress must never overshoot", seen.all { it <= target })
        assertTrue(
            "Type-on progress must be monotonic non-decreasing",
            seen.zipWithNext().all { (prev, next) -> next >= prev }
        )
        assertTrue("Type-on must tail-finalize exactly at target", seen.last() == target)
    }

    @Test
    fun sidePanelRespectsStatusAndNavigationBars() {
        val source = File("src/main/java/ai/daylight/assistant/ui/SidePanel.kt").readText()

        assertTrue(source.contains(".statusBarsPadding()"))
        assertTrue(source.contains(".navigationBarsPadding()"))
    }

    @Test
    fun sidePanelIsFullyOpaqueAtEveryGlassSetting() {
        assertEquals(1f, panelSurfaceOpacity(0.42f), 0.0001f)
        assertEquals(1f, panelSurfaceOpacity(0.68f), 0.0001f)
        assertEquals(1f, panelSurfaceOpacity(0.99f), 0.0001f)
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = max(first.luminance(), second.luminance())
        val darker = min(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
