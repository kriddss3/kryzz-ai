package ai.daylight.assistant

import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.data.preferences.coerceSupportedBackgroundStyle
import ai.daylight.assistant.data.preferences.coerceFishModel
import ai.daylight.assistant.data.preferences.coerceOpenRouterTtsModel
import ai.daylight.assistant.data.preferences.coerceVoiceReplyModel
import ai.daylight.assistant.domain.ChatDensity
import ai.daylight.assistant.domain.AppPalette
import ai.daylight.assistant.domain.BackgroundStyle
import ai.daylight.assistant.domain.GradientPalette
import ai.daylight.assistant.domain.FontStyle
import ai.daylight.assistant.domain.TextPalette
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.voice.VoiceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePreferencesContractTest {
    @Test
    fun newInstallDefaultsToObsidianConstellation() {
        val state = SettingsState()

        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(AppPalette.MONOCHROME, state.appPalette)
        assertEquals(TextPalette.ADAPTIVE, state.textPalette)
        assertEquals(FontStyle.MODERN, state.fontStyle)
        assertEquals(BackgroundStyle.CONSTELLATION, state.backgroundStyle)
        assertEquals(0.84f, state.surfaceOpacity, 0.001f)
        assertEquals(0f, state.backgroundBlur, 0.001f)
        assertEquals(ChatDensity.COMFORTABLE, state.chatDensity)
        assertTrue(state.animationsEnabled)
        assertEquals(GradientPalette.MONOCHROME, state.colouredGradient)
        assertTrue(!state.memoryEnabled)
        assertTrue(state.voiceFishStreaming)
    }

    @Test
    fun chatDensityPersistenceNamesRemainStable() {
        assertEquals(listOf("COMPACT", "COMFORTABLE", "SPACIOUS"), ChatDensity.entries.map { it.name })
    }

    @Test
    fun legacyBackgroundValuesAreCoercedToObsidianConstellation() {
        BackgroundStyle.entries.forEach { stored ->
            assertEquals(BackgroundStyle.CONSTELLATION, coerceSupportedBackgroundStyle(stored.name))
        }
        assertEquals(BackgroundStyle.CONSTELLATION, coerceSupportedBackgroundStyle("UNKNOWN"))
    }

    @Test
    fun invalidLegacyVoiceChoicesFallBackToSupportedModels() {
        assertEquals(VoiceConfig.DEFAULT_LLM_MODEL, coerceVoiceReplyModel("removed/model"))
        assertEquals("", coerceVoiceReplyModel(""))
        assertEquals(VoiceConfig.FISH_MODEL_FREE, coerceFishModel("retired-engine"))
        assertEquals("qwen/qwen-audio-3.0-tts-flash", coerceOpenRouterTtsModel("old-tts"))
    }
}
