package ai.daylight.assistant.ui.dot

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Spacing (tokens.json space_dp). 4dp base, 8dp dot pitch. */
object DotSpace {
    val S1 = 2.dp; val S2 = 4.dp; val S3 = 8.dp; val S4 = 12.dp; val S5 = 16.dp
    val S6 = 20.dp; val S7 = 24.dp; val S8 = 32.dp; val S9 = 40.dp; val S10 = 48.dp; val S11 = 64.dp
    val ScreenH = 20.dp; val CardPad = 16.dp; val ListGap = 8.dp; val SectionGap = 32.dp
}

/** Radii (tokens.json radius_dp). Cards are big soft squircles, controls are pills. */
object DotRadius {
    val Xs = 4.dp; val Sm = 8.dp; val Md = 16.dp; val Lg = 24.dp; val Xl = 32.dp
    val Card = RoundedCornerShape(Lg)
    val Tile = RoundedCornerShape(Md)
    val Sheet = RoundedCornerShape(topStart = Xl, topEnd = Xl)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Motion (tokens.json motion). Dot effects step discretely; layout motion eases. */
object DotMotion {
    const val Instant = 80; const val Fast = 140; const val Base = 220; const val Slow = 360
    const val Reveal = 600; const val DotStep = 28; const val Pulse = 1600; const val Blink = 1000
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decel = CubicBezierEasing(0f, 0f, 0f, 1f)
    val Accel = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

@Immutable
data class DotPalette(
    val dark: Boolean,
    val bg: Color,
    val bgAlt: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderStrong: Color,
    val dotOff: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    /** Yellow fills: buttons, badges, the voice core, the squid's eyes. Black goes on top. */
    val accent: Color,
    /**
     * Yellow for text and small marks (dots, hairlines, bars, cursors). The same yellow on
     * dark; dark gold on light, where yellow text and dots would vanish (1.4:1 on the page).
     */
    val accentText: Color,
    val accentPressed: Color,
    val accentDim: Color,
    val onAccent: Color,
    val recording: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

val DarkDot = DotPalette(
    dark = true,
    bg = DotColors.Bg, bgAlt = DotColors.BgAlt,
    surface = DotColors.Surface, surfaceRaised = DotColors.SurfaceRaised, surfaceHigh = DotColors.SurfaceHigh,
    border = DotColors.Border, borderStrong = DotColors.BorderStrong, dotOff = DotColors.DotOff,
    textPrimary = DotColors.TextPrimary, textSecondary = DotColors.TextSecondary,
    textTertiary = DotColors.TextTertiary, textDisabled = DotColors.TextDisabled,
    accent = DotColors.Accent, accentText = DotColors.AccentText, accentPressed = DotColors.AccentPressed,
    accentDim = DotColors.AccentDim, onAccent = DotColors.OnAccent,
    recording = DotColors.Recording, success = DotColors.Success, warning = DotColors.Warning, error = DotColors.Error,
)

val LightDot = DotPalette(
    dark = false,
    bg = DotColorsLight.Bg, bgAlt = DotColorsLight.Bg,
    surface = DotColorsLight.Surface, surfaceRaised = DotColorsLight.SurfaceRaised, surfaceHigh = DotColorsLight.SurfaceHigh,
    border = DotColorsLight.Border, borderStrong = DotColorsLight.BorderStrong, dotOff = DotColorsLight.DotOff,
    textPrimary = DotColorsLight.TextPrimary, textSecondary = DotColorsLight.TextSecondary,
    textTertiary = DotColorsLight.TextTertiary, textDisabled = DotColorsLight.TextDisabled,
    accent = DotColorsLight.Accent, accentText = DotColorsLight.AccentText, accentPressed = DotColors.AccentPressed,
    accentDim = DotColors.AccentDim, onAccent = DotColorsLight.OnAccent,
    recording = DotColors.Recording, success = DotColorsLight.TextPrimary, warning = DotColorsLight.TextPrimary,
    error = DotColorsLight.Error,
)

val LocalDot = staticCompositionLocalOf { DarkDot }

/** Read the palette anywhere inside the theme: `DotTheme.c.accent`. */
object DotTheme {
    val c: DotPalette
        @Composable @ReadOnlyComposable get() = LocalDot.current
}

/**
 * Full Material 3 colour scheme from the Dot palette. Every role is set so no stock
 * component falls back to the baseline purple, and surfaceTint equals surface so
 * tonal elevation never tints anything.
 *
 * primary is white (black on light), not yellow: stock buttons, switches and sliders
 * stay monochrome like the mockups. Yellow is applied on purpose via DotTheme.c.accent.
 */
fun dotColorScheme(p: DotPalette): ColorScheme = if (p.dark) {
    darkColorScheme(
        primary = p.textPrimary, onPrimary = p.bg,
        primaryContainer = p.surfaceHigh, onPrimaryContainer = p.textPrimary,
        inversePrimary = p.bg,
        secondary = p.textSecondary, onSecondary = p.bg,
        secondaryContainer = p.surfaceHigh, onSecondaryContainer = p.textPrimary,
        tertiary = p.textTertiary, onTertiary = p.bg,
        tertiaryContainer = p.surfaceRaised, onTertiaryContainer = p.textPrimary,
        background = p.bgAlt, onBackground = p.textPrimary,
        surface = p.surface, onSurface = p.textPrimary,
        surfaceVariant = p.surfaceRaised, onSurfaceVariant = p.textSecondary,
        surfaceTint = p.surface,
        inverseSurface = p.textPrimary, inverseOnSurface = p.bg,
        error = p.error, onError = p.textPrimary,
        errorContainer = p.surfaceRaised, onErrorContainer = p.textPrimary,
        outline = p.borderStrong, outlineVariant = p.border,
        scrim = p.bg,
        surfaceBright = p.surfaceHigh, surfaceDim = p.bgAlt,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.surface,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceRaised,
        surfaceContainerHighest = p.surfaceHigh,
    )
} else {
    lightColorScheme(
        primary = p.textPrimary, onPrimary = p.surface,
        primaryContainer = p.surfaceRaised, onPrimaryContainer = p.textPrimary,
        inversePrimary = p.surface,
        secondary = p.textSecondary, onSecondary = p.surface,
        secondaryContainer = p.surfaceRaised, onSecondaryContainer = p.textPrimary,
        tertiary = p.textTertiary, onTertiary = p.surface,
        tertiaryContainer = p.surfaceRaised, onTertiaryContainer = p.textPrimary,
        background = p.bg, onBackground = p.textPrimary,
        surface = p.surface, onSurface = p.textPrimary,
        surfaceVariant = p.surfaceRaised, onSurfaceVariant = p.textSecondary,
        surfaceTint = p.surface,
        inverseSurface = p.textPrimary, inverseOnSurface = p.surface,
        error = p.error, onError = p.surface,
        errorContainer = p.surfaceRaised, onErrorContainer = p.textPrimary,
        outline = p.borderStrong, outlineVariant = p.border,
        scrim = p.textPrimary,
        surfaceBright = p.surface, surfaceDim = p.surfaceRaised,
        surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceRaised,
        surfaceContainerHighest = p.surfaceHigh,
    )
}

val DotShapes = Shapes(
    extraSmall = RoundedCornerShape(DotRadius.Xs),
    small = RoundedCornerShape(DotRadius.Sm),
    medium = RoundedCornerShape(DotRadius.Md),
    large = RoundedCornerShape(DotRadius.Lg),
    extraLarge = RoundedCornerShape(DotRadius.Xl),
)
