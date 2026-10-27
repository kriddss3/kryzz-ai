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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ai.daylight.assistant.domain.AppPalette
import ai.daylight.assistant.domain.FontStyle
import ai.daylight.assistant.domain.TextPalette
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.ui.dot.DarkDot
import ai.daylight.assistant.ui.dot.DotColors
import ai.daylight.assistant.ui.dot.DotColorsLight
import ai.daylight.assistant.ui.dot.DotGrey
import ai.daylight.assistant.ui.dot.DotShapes
import ai.daylight.assistant.ui.dot.DotTypography
import ai.daylight.assistant.ui.dot.LightDot
import ai.daylight.assistant.ui.dot.LocalDot
import ai.daylight.assistant.ui.dot.dotColorScheme

/*
 * Kryzz Dot theme (Nothing "Eight Arms" redesign). The colours, type and shapes live in
 * ui/dot/. The names below are kept so existing screens compile unchanged; they all
 * point at Dot tokens now.
 */
object KryzzColors {
    val Background = DotColors.BgAlt
    val Surface = DotColors.Surface
    val SurfaceRaised = DotColors.SurfaceRaised
    val Border = DotColors.Border
    val Starlight = DotColors.TextPrimary
    val Moonstone = DotColors.TextSecondary
    val SignalBlue = DotColors.TextPrimary
    val SignalViolet = DotColors.TextSecondary
    val SignalMint = DotGrey.G400
    val TextPrimary = DotColors.TextPrimary
    val TextSecondary = DotColors.TextSecondary
    val TextTertiary = DotColors.TextTertiary

    val LightBackground = DotColorsLight.Bg
    val LightSurface = DotColorsLight.Surface
    val LightSurfaceRaised = DotColorsLight.SurfaceRaised
    val LightBorder = DotColorsLight.Border
    val LightTextPrimary = DotColorsLight.TextPrimary
    val LightTextSecondary = DotColorsLight.TextSecondary
    val LightTextTertiary = DotColorsLight.TextTertiary
}

// Top-level aliases kept for existing call sites.
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

/** Shared layout rhythm (kept for existing call sites; new code uses DotSpace). */
object KryzzSpacing {
    val Micro = 4.dp
    val Compact = 8.dp
    val Small = 12.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
    val ScreenHorizontal = 20.dp
}

/** Radii (kept for existing call sites; new code uses DotRadius). */
object KryzzRadius {
    val Small = 8.dp
    val Control = 16.dp
    val Medium = 16.dp
    val Card = 24.dp
    val Large = 24.dp
}

/** Durations in milliseconds (kept for existing call sites; new code uses DotMotion). */
object KryzzMotion {
    const val Fast = 140
    const val ModeChange = 220
    const val Standard = 220
    const val Launch = 360
    const val Emphasized = 360
    const val Ambient = 20_000
}

/** Kit rule: no glass. Surfaces are opaque, depth comes from surface steps plus a 1dp border. */
val LocalGlassOpacity = staticCompositionLocalOf { 1f }
val LocalKryzzMotionEnabled = staticCompositionLocalOf { true }

// Legacy public colour names, kept source-compatible.
val Forest = DotColors.TextPrimary
val Emerald = SignalMint
val Mint = DotColorsLight.SurfaceRaised
val LimeMist = DotColorsLight.Bg
val Ink = KryzzColors.LightTextPrimary
val Night = Midnight

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
 * The app theme. Palette, text palette, font style and glass opacity are accepted for
 * source compatibility but no longer change the look: the Dot kit defines one palette
 * (yellow accent only), one type system and opaque surfaces. Theme mode, font scale and
 * motion still apply.
 */
@Composable
fun KryzzTheme(
    mode: ThemeMode,
    @Suppress("UNUSED_PARAMETER") palette: AppPalette = AppPalette.MONOCHROME,
    @Suppress("UNUSED_PARAMETER") textPalette: TextPalette = TextPalette.ADAPTIVE,
    @Suppress("UNUSED_PARAMETER") fontStyle: FontStyle = FontStyle.MODERN,
    fontScale: Float = 1f,
    @Suppress("UNUSED_PARAMETER") surfaceOpacity: Float = 0.84f,
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

    val palette = if (dark) DarkDot else LightDot
    val density = LocalDensity.current
    val motionEnabled = animationsEnabled && rememberSystemMotionEnabled()
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * fontScale.coerceIn(0.85f, 1.35f)
        ),
        LocalGlassOpacity provides 1f,
        LocalKryzzMotionEnabled provides motionEnabled,
        LocalDot provides palette
    ) {
        MaterialTheme(
            colorScheme = dotColorScheme(palette),
            typography = DotTypography,
            shapes = DotShapes,
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
