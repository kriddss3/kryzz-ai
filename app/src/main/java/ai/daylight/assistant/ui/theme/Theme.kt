package ai.daylight.assistant.ui.theme

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import ai.daylight.assistant.domain.AppPalette
import ai.daylight.assistant.domain.FontStyle
import ai.daylight.assistant.domain.TextPalette
import ai.daylight.assistant.domain.ThemeMode

/** Core Obsidian Constellation colour tokens. Keep these opaque so they remain reusable. */
object KryzzColors {
    val Background = Color(0xFF050506)
    val Surface = Color(0xFF0D0E10)
    val SurfaceRaised = Color(0xFF151619)
    val Border = Color(0xFF292A2F)
    val Starlight = Color(0xFFF4F4F1)
    val Moonstone = Color(0xFFD7D7D2)
    val SignalBlue = Starlight
    val SignalViolet = Moonstone
    val SignalMint = Color(0xFFBFC0BC)
    val TextPrimary = Starlight
    val TextSecondary = Color(0xFFA4A5AA)
    val TextTertiary = Color(0xFF6C6E74)

    // Premium light counterpart: cool paper surfaces with restrained signal accents.
    val LightBackground = Color(0xFFF6F5F2)
    val LightSurface = Color(0xFFFCFBF8)
    val LightSurfaceRaised = Color(0xFFECEBE7)
    val LightBorder = Color(0xFFD3D2CE)
    val LightTextPrimary = Color(0xFF111113)
    val LightTextSecondary = Color(0xFF5F6065)
    val LightTextTertiary = Color(0xFF74757A)
}

// Convenient top-level tokens for call sites and previews.
val Midnight = KryzzColors.Background
val MidnightSurface = KryzzColors.Surface
val MidnightSurfaceRaised = KryzzColors.SurfaceRaised
val MidnightBorder = KryzzColors.Border
val SignalBlue = KryzzColors.SignalBlue
val SignalViolet = KryzzColors.SignalViolet
val SignalMint = KryzzColors.SignalMint
val MidnightTextPrimary = KryzzColors.TextPrimary
val MidnightTextSecondary = KryzzColors.TextSecondary
val MidnightTextTertiary = KryzzColors.TextTertiary

/** Shared layout rhythm for new and existing Kryzz surfaces. */
object KryzzSpacing {
    val Micro = 4.dp
    val Compact = 8.dp
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
    val ScreenHorizontal = 24.dp
}

/** Small controls use 10-12dp; primary cards and sheets use 14-18dp. */
object KryzzRadius {
    val Small = 10.dp
    val Control = 12.dp
    val Medium = 14.dp
    val Card = 16.dp
    val Large = 18.dp
}

/** Motion is intentionally short and quiet. Durations are in milliseconds. */
object KryzzMotion {
    const val Fast = 250
    const val ModeChange = 280
    const val Standard = 300
    const val Launch = 320
    const val Emphasized = 350
    const val Ambient = 20_000
}

val LocalGlassOpacity = staticCompositionLocalOf { 0.74f }
val LocalKryzzMotionEnabled = staticCompositionLocalOf { true }

// Legacy public colour names remain source-compatible for existing integrations.
val Forest = Color(0xFF087F5B)
val Emerald = SignalMint
val Mint = Color(0xFFDDF6EA)
val LimeMist = Color(0xFFEDFAE8)
val Ink = KryzzColors.LightTextPrimary
val Night = Midnight

private val Light = lightColorScheme(
    primary = Color(0xFF1A1A1D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E6E2),
    onPrimaryContainer = Color(0xFF1B1B1E),
    secondary = Color(0xFF4F5055),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E1DD),
    onSecondaryContainer = Color(0xFF29292C),
    tertiary = Color(0xFF65666A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9E8E4),
    onTertiaryContainer = Color(0xFF2C2C2F),
    background = KryzzColors.LightBackground,
    onBackground = KryzzColors.LightTextPrimary,
    surface = KryzzColors.LightSurface,
    onSurface = KryzzColors.LightTextPrimary,
    surfaceVariant = KryzzColors.LightSurfaceRaised,
    onSurfaceVariant = KryzzColors.LightTextSecondary,
    outline = KryzzColors.LightTextTertiary,
    outlineVariant = KryzzColors.LightBorder,
    error = Color(0xFFB42331),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADC),
    onErrorContainer = Color(0xFF69000C),
    scrim = Color(0xFF05070B)
)

private val Dark = darkColorScheme(
    primary = KryzzColors.Starlight,
    onPrimary = Midnight,
    primaryContainer = Color(0xFF25262A),
    onPrimaryContainer = KryzzColors.TextPrimary,
    secondary = KryzzColors.Moonstone,
    onSecondary = Midnight,
    secondaryContainer = Color(0xFF1D1E21),
    onSecondaryContainer = KryzzColors.TextPrimary,
    tertiary = Color(0xFFBFC0BC),
    onTertiary = Midnight,
    tertiaryContainer = Color(0xFF222326),
    onTertiaryContainer = KryzzColors.TextPrimary,
    background = Midnight,
    onBackground = KryzzColors.TextPrimary,
    surface = MidnightSurface,
    onSurface = KryzzColors.TextPrimary,
    surfaceVariant = MidnightSurfaceRaised,
    onSurfaceVariant = KryzzColors.TextSecondary,
    outline = KryzzColors.TextTertiary,
    outlineVariant = MidnightBorder,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A22),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black
)

private val KryzzTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.55).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.35).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

private val KryzzShapes = Shapes(
    extraSmall = RoundedCornerShape(KryzzRadius.Small),
    small = RoundedCornerShape(KryzzRadius.Control),
    medium = RoundedCornerShape(KryzzRadius.Medium),
    large = RoundedCornerShape(KryzzRadius.Card),
    extraLarge = RoundedCornerShape(KryzzRadius.Large)
)

private data class PaletteSpec(
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
    val lightSecondary: Color,
    val darkSecondary: Color
)

private fun AppPalette.spec(): PaletteSpec = when (this) {
    AppPalette.GREEN -> PaletteSpec(
        Forest, SignalMint, Color(0xFFD5F3E8), Color(0xFF073C32),
        Color(0xFF123E37), Color(0xFFC7F6E8), Color(0xFF28725F), SignalBlue
    )
    AppPalette.BLUE -> PaletteSpec(
        Color(0xFF315FD6), SignalBlue, Color(0xFFDEE8FF), Color(0xFF17346E),
        Color(0xFF1B2D52), Color(0xFFDCE6FF), Color(0xFF526EAC), SignalViolet
    )
    AppPalette.PURPLE -> PaletteSpec(
        Color(0xFF6D50D5), SignalViolet, Color(0xFFE9E1FF), Color(0xFF35236B),
        Color(0xFF312955), Color(0xFFE9E1FF), Color(0xFF765FAD), SignalBlue
    )
    AppPalette.RED -> PaletteSpec(
        Color(0xFFB4233A), Color(0xFFFF8D9A), Color(0xFFFFDADD), Color(0xFF650018),
        Color(0xFF51242E), Color(0xFFFFDADD), Color(0xFF9B4E5A), SignalViolet
    )
    AppPalette.ORANGE -> PaletteSpec(
        Color(0xFF9A4A00), Color(0xFFFFAC62), Color(0xFFFFE1C7), Color(0xFF572600),
        Color(0xFF4E301C), Color(0xFFFFDFC2), Color(0xFF8B5D35), SignalMint
    )
    AppPalette.PINK -> PaletteSpec(
        Color(0xFFA82461), Color(0xFFFF86B7), Color(0xFFFFD9E6), Color(0xFF600431),
        Color(0xFF502437), Color(0xFFFFD9E6), Color(0xFF94516B), SignalViolet
    )
    AppPalette.TEAL -> PaletteSpec(
        Color(0xFF00796B), SignalMint, Color(0xFFD0F4EF), Color(0xFF003D35),
        Color(0xFF123E37), Color(0xFFC7F6E8), Color(0xFF34766F), SignalBlue
    )
    AppPalette.MONOCHROME -> PaletteSpec(
        Color(0xFF1A1A1D), KryzzColors.Starlight, Color(0xFFE7E6E2), Color(0xFF1B1B1E),
        Color(0xFF25262A), KryzzColors.TextPrimary, Color(0xFF4F5055), KryzzColors.Moonstone
    )
}

private fun paletteScheme(dark: Boolean, palette: AppPalette) = palette.spec().let { spec ->
    if (dark) {
        Dark.copy(
            primary = spec.darkPrimary,
            onPrimary = Midnight,
            primaryContainer = spec.darkContainer,
            onPrimaryContainer = spec.onDarkContainer,
            secondary = spec.darkSecondary,
            onSecondary = Midnight,
            secondaryContainer = spec.darkContainer,
            onSecondaryContainer = spec.onDarkContainer,
            surfaceTint = spec.darkPrimary
        )
    } else {
        Light.copy(
            primary = spec.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = spec.lightContainer,
            onPrimaryContainer = spec.onLightContainer,
            secondary = spec.lightSecondary,
            onSecondary = Color.White,
            secondaryContainer = spec.lightContainer,
            onSecondaryContainer = spec.onLightContainer,
            surfaceTint = spec.lightPrimary
        )
    }
}

private fun FontStyle.family(): FontFamily = when (this) {
    FontStyle.SYSTEM -> FontFamily.Default
    FontStyle.MODERN -> FontFamily.SansSerif
    FontStyle.SERIF -> FontFamily.Serif
    FontStyle.MONOSPACE -> FontFamily.Monospace
    FontStyle.PLAYFUL -> FontFamily.Cursive
}

private fun typography(style: FontStyle): Typography {
    val family = style.family()
    return KryzzTypography.copy(
        headlineLarge = KryzzTypography.headlineLarge.copy(fontFamily = family),
        headlineMedium = KryzzTypography.headlineMedium.copy(fontFamily = family),
        headlineSmall = KryzzTypography.headlineSmall.copy(fontFamily = family),
        titleLarge = KryzzTypography.titleLarge.copy(fontFamily = family),
        titleMedium = KryzzTypography.titleMedium.copy(fontFamily = family),
        titleSmall = KryzzTypography.titleSmall.copy(fontFamily = family),
        bodyLarge = KryzzTypography.bodyLarge.copy(fontFamily = family),
        bodyMedium = KryzzTypography.bodyMedium.copy(fontFamily = family),
        bodySmall = KryzzTypography.bodySmall.copy(fontFamily = family),
        labelLarge = KryzzTypography.labelLarge.copy(fontFamily = family),
        labelMedium = KryzzTypography.labelMedium.copy(fontFamily = family),
        labelSmall = KryzzTypography.labelSmall.copy(fontFamily = family)
    )
}

private fun androidx.compose.material3.ColorScheme.withTextPalette(
    dark: Boolean,
    palette: TextPalette
): androidx.compose.material3.ColorScheme {
    if (palette == TextPalette.ADAPTIVE) return this
    val (main, muted) = when (palette) {
        TextPalette.ADAPTIVE -> onSurface to onSurfaceVariant
        TextPalette.HIGH_CONTRAST -> if (dark) {
            Color.White to Color(0xFFDCE4EF)
        } else {
            Color(0xFF080B11) to Color(0xFF343C49)
        }
        TextPalette.MINT -> if (dark) {
            Color(0xFFDDFFF5) to Color(0xFFA9D8CB)
        } else {
            Color(0xFF0A3B32) to Color(0xFF38665B)
        }
        TextPalette.LAVENDER -> if (dark) {
            Color(0xFFF2EDFF) to Color(0xFFC9BDE4)
        } else {
            Color(0xFF352650) to Color(0xFF675879)
        }
        TextPalette.AMBER -> if (dark) {
            Color(0xFFFFF1D6) to Color(0xFFD8C29B)
        } else {
            Color(0xFF3E2B08) to Color(0xFF6F5A31)
        }
        TextPalette.ROSE -> if (dark) {
            Color(0xFFFFEAF0) to Color(0xFFDDB9C4)
        } else {
            Color(0xFF461827) to Color(0xFF73525C)
        }
    }
    return copy(onBackground = main, onSurface = main, onSurfaceVariant = muted)
}

/** Keeps the rendered opacity identical to the value stored and shown in Appearance. */
internal fun effectiveSurfaceOpacity(value: Float): Float = value.coerceIn(0.42f, 0.96f)

@Composable
private fun rememberSystemMotionEnabled(): Boolean {
    val context = LocalContext.current
    fun readSetting(): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        return scale > 0f && ValueAnimator.areAnimatorsEnabled()
    }

    var enabled by remember(context) { mutableStateOf(readSetting()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = readSetting()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled
}

/**
 * Obsidian Constellation theme API. Palette, text, font, scale and glass opacity remain
 * user-controlled while contrast-sensitive values are constrained to safe bounds.
 */
@Composable
fun KryzzTheme(
    mode: ThemeMode,
    palette: AppPalette = AppPalette.MONOCHROME,
    textPalette: TextPalette = TextPalette.ADAPTIVE,
    fontStyle: FontStyle = FontStyle.MODERN,
    fontScale: Float = 1f,
    surfaceOpacity: Float = 0.84f,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    val density = LocalDensity.current
    val motionEnabled = animationsEnabled && rememberSystemMotionEnabled()
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * fontScale.coerceIn(0.85f, 1.35f)
        ),
        LocalGlassOpacity provides effectiveSurfaceOpacity(surfaceOpacity),
        LocalKryzzMotionEnabled provides motionEnabled
    ) {
        MaterialTheme(
            colorScheme = paletteScheme(dark, palette).withTextPalette(dark, textPalette),
            typography = typography(fontStyle),
            shapes = KryzzShapes,
            content = content
        )
    }
}

/** Compatibility wrapper retained for all existing call sites. */
@Composable
fun DaylightTheme(
    mode: ThemeMode,
    palette: AppPalette = AppPalette.MONOCHROME,
    textPalette: TextPalette = TextPalette.ADAPTIVE,
    fontStyle: FontStyle = FontStyle.MODERN,
    fontScale: Float = 1f,
    surfaceOpacity: Float = 0.84f,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    KryzzTheme(
        mode = mode,
        palette = palette,
        textPalette = textPalette,
        fontStyle = fontStyle,
        fontScale = fontScale,
        surfaceOpacity = surfaceOpacity,
        animationsEnabled = animationsEnabled,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
