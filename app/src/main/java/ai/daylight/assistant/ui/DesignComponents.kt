package ai.daylight.assistant.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.BackgroundStyle
import ai.daylight.assistant.domain.GradientPalette
import ai.daylight.assistant.ui.dot.DotMotion
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.dot.DotType
import ai.daylight.assistant.ui.theme.KryzzColors
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled

@Composable
fun KryzzWordmark(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    contentDescription: String? = "Kryzz AI"
) {
    val darkBackground = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Image(
        painter = painterResource(
            if (darkBackground) R.drawable.kryzz_wordmark_white else R.drawable.kryzz_wordmark_black
        ),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let(ColorFilter::tint)
    )
}

/** The supplied Kryzz monogram, prepared as a transparent adaptive brand asset. */
@Composable
fun KryzzMark(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    contentDescription: String? = "Kryzz AI"
) {
    val darkBackground = MaterialTheme.colorScheme.background.luminance() < 0.45f
    // Pick the silhouette whose colour already matches the surface, then tint
    // on top — that way the tint layer can only recolour, never reveal the
    // background through a same-coloured silhouette.
    val silhouette = if (darkBackground) R.drawable.kryzz_mark_foreground else R.drawable.kryzz_mark_foreground_dark
    Image(
        painter = painterResource(silhouette),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(
            tint ?: if (darkBackground) KryzzColors.TextPrimary else KryzzColors.LightTextPrimary
        )
    )
}

/**
 * App background. Every persisted [BackgroundStyle] now renders the same Dot kit
 * background: a flat near-black field (kit rule: no gradients, no glass, no blur).
 * The parameters stay so existing call sites and saved settings keep working.
 */
@Composable
fun DaylightBackground(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") purpleOnly: Boolean = false,
    @Suppress("UNUSED_PARAMETER") style: BackgroundStyle = BackgroundStyle.CONSTELLATION,
    @Suppress("UNUSED_PARAMETER") blurRadius: Float = 0f,
    @Suppress("UNUSED_PARAMETER") colouredGradient: GradientPalette = GradientPalette.OCEAN,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(scheme.background)) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

/** Launch intro: the Kryzz mark and name on black. The dot-matrix reveal arrives in phase D. */
@Composable
fun KryzzLaunchIntro(onFinished: () -> Unit) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (motionEnabled) {
            progress.animateTo(1f, tween(520, easing = DotMotion.Standard))
        } else {
            progress.snapTo(1f)
        }
        currentOnFinished()
    }

    val c = DotTheme.c
    Box(
        Modifier.fillMaxSize().background(c.bgAlt),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .graphicsLayer(alpha = progress.value.coerceIn(0f, 1f))
        ) {
            KryzzMark(
                Modifier.size(96.dp),
                tint = c.textPrimary,
                contentDescription = null
            )
            Text(
                text = "KRYZZ AI",
                modifier = Modifier.padding(top = 20.dp),
                color = c.textPrimary,
                style = DotType.DotTitle
            )
            Text(
                text = "PRIVATE  /  LOCAL-FIRST",
                modifier = Modifier.padding(top = 8.dp),
                color = c.textTertiary,
                style = DotType.Label
            )
        }
    }
}
