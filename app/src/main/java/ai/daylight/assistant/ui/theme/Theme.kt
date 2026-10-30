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
import androidx.core.view.WindowCompat
import ai.daylight.assistant.domain.ThemeMode
import ai.daylight.assistant.ui.dot.DarkDot
import ai.daylight.assistant.ui.dot.DotShapes
import ai.daylight.assistant.ui.dot.DotTypography
import ai.daylight.assistant.ui.dot.LightDot
import ai.daylight.assistant.ui.dot.LocalDot
import ai.daylight.assistant.ui.dot.dotColorScheme

/*
 * Kryzz Dot theme (Nothing "Eight Arms" redesign). The colours, type and shapes live in
 * ui/dot/: one palette with a single yellow accent, one type system, opaque surfaces.
 * Theme mode, font scale and motion are the only settings that change the look.
 */

/** Motion is on: the user's setting and the system animator scale both allow it. */
val LocalKryzzMotionEnabled = staticCompositionLocalOf { true }

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

/** The app theme: Dot palette for [mode], the user's [fontScale], and motion on or off. */
@Composable
fun DaylightTheme(
    mode: ThemeMode,
    fontScale: Float = 1f,
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
