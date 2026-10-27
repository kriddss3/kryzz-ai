package ai.daylight.assistant.ui.dot

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class DotThemeContractTest {
    private val kitColours: Set<Color> = setOf(
        DotColors.Bg, DotColors.BgAlt, DotColors.Surface, DotColors.SurfaceRaised, DotColors.SurfaceHigh,
        DotColors.Border, DotColors.BorderStrong, DotColors.DotOff, DotColors.TextPrimary,
        DotColors.TextSecondary, DotColors.TextTertiary, DotColors.TextDisabled, DotColors.Accent,
        DotColors.AccentText, DotColors.AccentPressed, DotColors.AccentDim, DotColors.OnAccent,
        DotColors.Recording, DotColors.Success, DotColors.Warning, DotColors.Error,
        DotColorsLight.Bg, DotColorsLight.Surface, DotColorsLight.SurfaceRaised, DotColorsLight.SurfaceHigh,
        DotColorsLight.Border, DotColorsLight.BorderStrong, DotColorsLight.DotOff, DotColorsLight.TextPrimary,
        DotColorsLight.TextSecondary, DotColorsLight.TextTertiary, DotColorsLight.TextDisabled,
        DotColorsLight.Accent, DotColorsLight.AccentText, DotColorsLight.Error, DotColorsLight.OnAccent,
        DotGrey.G100, DotGrey.G200, DotGrey.G300, DotGrey.G400, DotGrey.G500, DotGrey.G600,
        DotGrey.G700, DotGrey.G800, DotGrey.G900, DotGrey.G1000,
    )

    private fun ColorScheme.roles(): Map<String, Color> = mapOf(
        "primary" to primary, "onPrimary" to onPrimary, "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer, "inversePrimary" to inversePrimary,
        "secondary" to secondary, "onSecondary" to onSecondary, "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer, "tertiary" to tertiary, "onTertiary" to onTertiary,
        "tertiaryContainer" to tertiaryContainer, "onTertiaryContainer" to onTertiaryContainer,
        "background" to background, "onBackground" to onBackground, "surface" to surface,
        "onSurface" to onSurface, "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint, "inverseSurface" to inverseSurface, "inverseOnSurface" to inverseOnSurface,
        "error" to error, "onError" to onError, "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer, "outline" to outline, "outlineVariant" to outlineVariant,
        "scrim" to scrim, "surfaceBright" to surfaceBright, "surfaceDim" to surfaceDim,
        "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest, "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainerLowest" to surfaceContainerLowest,
    )

    @Test fun everyDarkSchemeRoleIsAKitColour() {
        dotColorScheme(DarkDot).roles().forEach { (role, colour) ->
            assertWithMessage(role).that(kitColours).contains(colour)
        }
    }

    @Test fun everyLightSchemeRoleIsAKitColour() {
        dotColorScheme(LightDot).roles().forEach { (role, colour) ->
            assertWithMessage(role).that(kitColours).contains(colour)
        }
    }

    @Test fun primaryIsMonochromeAndYellowStaysExplicit() {
        val dark = dotColorScheme(DarkDot)
        assertThat(dark.primary).isEqualTo(DotColors.TextPrimary)
        assertThat(dark.onPrimary).isEqualTo(DotColors.Bg)
        assertThat(dark.roles().values).doesNotContain(DotColors.Accent)
        assertThat(DarkDot.accent).isEqualTo(Color(0xFFFFC700))
        assertThat(DarkDot.onAccent).isEqualTo(Color(0xFF000000))
    }

    @Test fun errorIsNothingRedAndSurfacesNeverTint() {
        val dark = dotColorScheme(DarkDot)
        assertThat(dark.error).isEqualTo(Color(0xFFD71920))
        assertThat(dark.surfaceTint).isEqualTo(dark.surface)
        assertThat(dotColorScheme(LightDot).surfaceTint).isEqualTo(dotColorScheme(LightDot).surface)
    }
}
