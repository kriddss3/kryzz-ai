package ai.daylight.assistant.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.daylight.assistant.R
import ai.daylight.assistant.domain.AssistantMode
import ai.daylight.assistant.domain.BackgroundStyle
import ai.daylight.assistant.domain.GradientPalette
import ai.daylight.assistant.ui.theme.KryzzColors
import ai.daylight.assistant.ui.theme.KryzzMotion
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import ai.daylight.assistant.ui.theme.Midnight
import ai.daylight.assistant.ui.theme.SignalBlue
import ai.daylight.assistant.ui.theme.SignalMint
import ai.daylight.assistant.ui.theme.SignalViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
 * Compatibility background host. Every persisted [BackgroundStyle] remains distinct,
 * but each treatment now uses the restrained Obsidian Constellation visual language.
 */
@Composable
fun DaylightBackground(
    modifier: Modifier = Modifier,
    purpleOnly: Boolean = false,
    style: BackgroundStyle = BackgroundStyle.CONSTELLATION,
    blurRadius: Float = 0f,
    colouredGradient: GradientPalette = GradientPalette.OCEAN,
    content: @Composable BoxScope.() -> Unit
) {
    when (style) {
        BackgroundStyle.CONSTELLATION -> ConstellationBackground(
            modifier,
            blurRadius,
            content
        )
        BackgroundStyle.ANIMATED_AURORA -> AnimatedAuroraBackground(
            modifier,
            purpleOnly,
            blurRadius,
            content
        )
        BackgroundStyle.VIDEO_AURORA -> VideoAuroraBackground(
            modifier,
            purpleOnly,
            blurRadius,
            content
        )
        BackgroundStyle.PALETTE_BUBBLES -> PaletteBubbleBackground(
            modifier,
            purpleOnly,
            blurRadius,
            content
        )
        BackgroundStyle.PLAIN_GREY -> PlainGreyBackground(modifier, content)
        BackgroundStyle.COLOURED -> ColouredGradientBackground(modifier, colouredGradient, blurRadius, content)
    }
}

private data class ConstellationPoint(val x: Float, val y: Float, val weight: Float = 1f)

private val kryzzConstellation = listOf(
    ConstellationPoint(0.08f, 0.23f, 0.8f),
    ConstellationPoint(0.18f, 0.17f, 1.2f),
    ConstellationPoint(0.29f, 0.27f, 0.9f),
    ConstellationPoint(0.40f, 0.20f, 1.5f),
    ConstellationPoint(0.51f, 0.31f, 0.9f),
    ConstellationPoint(0.63f, 0.24f, 1.1f),
    ConstellationPoint(0.76f, 0.34f, 1.6f),
    ConstellationPoint(0.87f, 0.25f, 0.8f),
    ConstellationPoint(0.20f, 0.72f, 1.1f),
    ConstellationPoint(0.34f, 0.62f, 0.8f),
    ConstellationPoint(0.48f, 0.75f, 1.5f),
    ConstellationPoint(0.62f, 0.66f, 0.9f),
    ConstellationPoint(0.78f, 0.76f, 1.2f),
    ConstellationPoint(0.91f, 0.65f, 0.8f)
)

private val kryzzConstellationEdges = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7,
    8 to 9, 9 to 10, 10 to 11, 11 to 12, 12 to 13,
    3 to 10, 5 to 11
)

/**
 * Obsidian Constellation: a deterministic, battery-light star field with a few
 * editorial hairline connections. Stars slowly pulse in brightness and drift on a
 * gentle loop; connections stay attached because both endpoints share the drift.
 */
@Composable
private fun ConstellationBackground(
    modifier: Modifier,
    blurRadius: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val phase = if (motionEnabled) {
        rememberInfiniteTransition(label = "constellation").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 26000, easing = LinearEasing)),
            label = "phase"
        ).value
    } else {
        0.55f
    }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.45f
    val ink = scheme.onBackground
    val base = if (dark) KryzzColors.Background else KryzzColors.LightBackground

    Box(modifier.fillMaxSize().background(base)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .blur((blurRadius * 0.34f).coerceIn(0f, 8f).dp)
        ) {
            drawRect(
                Brush.verticalGradient(
                    colors = if (dark) {
                        listOf(Color(0xFF050506), Color(0xFF090A0C), Color(0xFF050506))
                    } else {
                        listOf(Color(0xFFF8F7F4), Color(0xFFF1F0EC), Color(0xFFF8F7F4))
                    }
                )
            )

            val haloCenter = Offset(size.width * 0.72f, size.height * 0.18f)
            val haloRadius = size.maxDimension * 0.54f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ink.copy(alpha = if (dark) 0.045f else 0.028f),
                        Color.Transparent
                    ),
                    center = haloCenter,
                    radius = haloRadius
                ),
                center = haloCenter,
                radius = haloRadius
            )

            repeat(72) { index ->
                val seedX = ((index * 73 + 19) % 101) / 101f
                val seedY = ((index * 47 + 11) % 103) / 103f
                val depth = 0.45f + ((index * 29) % 55) / 100f
                val driftX = sin(phase + index * 0.61f) * size.width * 0.0045f * depth
                val driftY = cos(phase * 0.73f + index * 0.37f) * size.height * 0.0032f * depth
                val pulse = 0.55f + 0.45f * sin(phase * 1.3f + index * 0.91f)
                drawCircle(
                    color = ink.copy(alpha = (if (dark) 0.12f else 0.09f) * pulse),
                    radius = (0.55f + (index % 4) * 0.24f).dp.toPx(),
                    center = Offset(size.width * seedX + driftX, size.height * seedY + driftY)
                )
            }

            fun node(point: ConstellationPoint): Offset = Offset(
                x = size.width * point.x + sin(phase + point.y * 4f) * size.width * 0.0035f,
                y = size.height * point.y + cos(phase * 0.8f + point.x * 5f) * size.height * 0.003f
            )
            kryzzConstellationEdges.forEach { (start, end) ->
                drawLine(
                    color = ink.copy(alpha = if (dark) 0.055f else 0.045f),
                    start = node(kryzzConstellation[start]),
                    end = node(kryzzConstellation[end]),
                    strokeWidth = 0.65.dp.toPx()
                )
            }
            kryzzConstellation.forEach { point ->
                val center = node(point)
                val nodePulse = 0.62f + 0.38f * sin(phase * 1.1f + point.x * 7f + point.y * 3f)
                drawCircle(
                    color = ink.copy(alpha = (if (dark) 0.34f else 0.24f) * nodePulse),
                    radius = point.weight.dp.toPx(),
                    center = center
                )
                if (point.weight > 1.4f) {
                    drawCircle(
                        color = ink.copy(alpha = if (dark) 0.075f else 0.05f),
                        radius = 7.dp.toPx(),
                        center = center,
                        style = Stroke(width = 0.6.dp.toPx())
                    )
                }
            }

            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, base.copy(alpha = if (dark) 0.58f else 0.38f)),
                    center = Offset(size.width * 0.5f, size.height * 0.46f),
                    radius = size.maxDimension * 0.76f
                )
            )
        }
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

@Composable
private fun PlainGreyBackground(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.45f
    val background = if (dark) KryzzColors.Background else Color(0xFFF2F4F8)
    Box(modifier.fillMaxSize().background(background)) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

@Composable
private fun ColouredGradientBackground(
    modifier: Modifier,
    gradient: GradientPalette,
    blurRadius: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.45f
    val stops = gradient.colors(dark)
    Box(modifier.fillMaxSize().background(scheme.background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .blur(blurRadius.coerceIn(0f, 28f).dp)
        ) {
            drawRect(
                Brush.linearGradient(
                    colors = stops,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        stops.first().copy(alpha = if (dark) 0.34f else 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.78f, size.height * 0.18f),
                    radius = size.minDimension * 0.55f
                )
            )
            drawRect(scheme.background.copy(alpha = if (dark) 0.34f else 0.18f))
        }
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

private fun GradientPalette.colors(dark: Boolean): List<Color> = when (this) {
    GradientPalette.OCEAN -> if (dark) {
        listOf(Color(0xFF071525), Color(0xFF0F3B63), Color(0xFF1A6FA8), Color(0xFF2FD1C0))
    } else {
        listOf(Color(0xFFE8F4FF), Color(0xFFB9DCFF), Color(0xFF7FC0F5), Color(0xFF4FD0C4))
    }
    GradientPalette.SUNSET -> if (dark) {
        listOf(Color(0xFF1A0C18), Color(0xFF5A1D3A), Color(0xFFC14A2D), Color(0xFFF0A33A))
    } else {
        listOf(Color(0xFFFFF1E8), Color(0xFFFFC4A8), Color(0xFFFF8B6A), Color(0xFFF4B35B))
    }
    GradientPalette.AURORA -> if (dark) {
        listOf(Color(0xFF08141F), Color(0xFF163B4D), Color(0xFF2F7F7A), Color(0xFF8B6CFF))
    } else {
        listOf(Color(0xFFEAF8F6), Color(0xFFB9E9E2), Color(0xFF8BC9FF), Color(0xFFC8B6FF))
    }
    GradientPalette.LAVENDER -> if (dark) {
        listOf(Color(0xFF120F22), Color(0xFF35245F), Color(0xFF6B4CC4), Color(0xFFB58CFF))
    } else {
        listOf(Color(0xFFF5F0FF), Color(0xFFDCCBFF), Color(0xFFB89BFF), Color(0xFF8F74E8))
    }
    GradientPalette.FOREST -> if (dark) {
        listOf(Color(0xFF07140F), Color(0xFF124033), Color(0xFF1F7A56), Color(0xFF7FD1A0))
    } else {
        listOf(Color(0xFFECF8F1), Color(0xFFBFE8CF), Color(0xFF74C69D), Color(0xFF40916C))
    }
    GradientPalette.CANDY -> if (dark) {
        listOf(Color(0xFF1A0E1C), Color(0xFF6A2558), Color(0xFFD45C9C), Color(0xFFFFA8D4))
    } else {
        listOf(Color(0xFFFFF0F7), Color(0xFFFFC2E0), Color(0xFFFF8FC7), Color(0xFFC77DFF))
    }
    GradientPalette.EMBER -> if (dark) {
        listOf(Color(0xFF140A08), Color(0xFF4A1A12), Color(0xFFB03A1C), Color(0xFFFF8A3D))
    } else {
        listOf(Color(0xFFFFF2EA), Color(0xFFFFD0B5), Color(0xFFFF9A62), Color(0xFFE36414))
    }
    GradientPalette.MONOCHROME -> if (dark) {
        listOf(Color(0xFF0B0E14), Color(0xFF1A2130), Color(0xFF3A4558), Color(0xFF9AA6B8))
    } else {
        listOf(Color(0xFFF5F7FB), Color(0xFFE4E9F1), Color(0xFFC2CAD6), Color(0xFF7B8798))
    }
}

@Composable
private fun VideoAuroraBackground(
    modifier: Modifier,
    purpleOnly: Boolean,
    blurRadius: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val motionEnabled = LocalKryzzMotionEnabled.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.45f
    val resource = if (purpleOnly) R.raw.aurora_purple_loop else R.raw.aurora_green_loop
    val player = remember(resource, motionEnabled) {
        if (!motionEnabled) {
            null
        } else {
            MediaPlayer.create(context, resource)?.apply {
                isLooping = true
                setVolume(0f, 0f)
                setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            }
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> runCatching {
                    if (player?.isPlaying == false) player.start()
                }
                Lifecycle.Event.ON_STOP -> runCatching {
                    if (player?.isPlaying == true) player.pause()
                }
                else -> Unit
            }
        }
        if (player != null) lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (player != null) lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { player?.release() }
        }
    }

    Box(modifier.fillMaxSize().background(scheme.background)) {
        if (player != null) {
            key(resource) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = if (dark) 0.16f else 0.08f)
                        .blur(blurRadius.coerceIn(0f, 28f).dp),
                    factory = { viewContext ->
                        TextureView(viewContext).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    texture: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    val surface = Surface(texture)
                                    player.setSurface(surface)
                                    surface.release()
                                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                        runCatching { player.start() }
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    texture: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) = Unit

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                    runCatching { player.setSurface(null) }
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                            }
                        }
                    }
                )
            }
        }
        // Video is intentionally atmospheric rather than a competing content layer.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (dark) 0.32f else 0.12f))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.background.copy(alpha = if (dark) 0.64f else 0.82f))
        )
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

@Composable
private fun PaletteBubbleBackground(
    modifier: Modifier,
    purpleOnly: Boolean,
    blurRadius: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val phase = 0.35f
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.45f
    val colors = if (purpleOnly) {
        listOf(SignalViolet, Color(0xFF745BDB), SignalBlue)
    } else {
        listOf(scheme.primary, scheme.secondary, scheme.tertiary, SignalBlue)
    }

    Box(modifier.fillMaxSize().background(scheme.background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .blur(blurRadius.coerceIn(0f, 28f).dp)
        ) {
            drawRect(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.surface.copy(alpha = if (dark) 0.34f else 0.50f),
                        scheme.background
                    )
                )
            )
            repeat(4) { index ->
                val seedX = listOf(0.14f, 0.78f, 0.42f, 0.88f)[index]
                val seedY = listOf(0.20f, 0.34f, 0.72f, 0.86f)[index]
                val drift = size.minDimension * (0.012f + index * 0.003f)
                val center = Offset(
                    x = size.width * seedX + sin(phase + index * 1.7f) * drift,
                    y = size.height * seedY + cos(phase * 0.72f + index) * drift
                )
                val radius = size.minDimension * (0.16f + index * 0.025f)
                val color = colors[index % colors.size]
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = if (dark) 0.085f else 0.055f),
                            color.copy(alpha = if (dark) 0.028f else 0.018f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = color.copy(alpha = if (dark) 0.11f else 0.07f),
                    radius = radius * 0.38f,
                    center = center,
                    style = Stroke(width = 0.75.dp.toPx())
                )
                drawCircle(
                    color = color.copy(alpha = if (dark) 0.18f else 0.10f),
                    radius = 1.5.dp.toPx(),
                    center = center
                )
            }
        }
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

@Composable
private fun AnimatedAuroraBackground(
    modifier: Modifier,
    purpleOnly: Boolean,
    blurRadius: Float,
    content: @Composable BoxScope.() -> Unit
) {
    val phase = 0.4f
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.45f
    val primarySignal = if (purpleOnly) SignalViolet else scheme.primary
    val secondarySignal = if (purpleOnly) Color(0xFF755FDB) else SignalBlue
    val tertiarySignal = if (purpleOnly) SignalBlue else SignalMint

    Box(modifier.fillMaxSize().background(scheme.background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .blur(blurRadius.coerceIn(0f, 28f).dp)
        ) {
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.background,
                        scheme.surface.copy(alpha = if (dark) 0.30f else 0.48f),
                        scheme.background
                    )
                )
            )

            val glowCenter = Offset(
                x = size.width * (0.72f + sin(phase) * 0.025f),
                y = size.height * (0.18f + cos(phase * 0.7f) * 0.018f)
            )
            val glowRadius = size.minDimension * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        primarySignal.copy(alpha = if (dark) 0.085f else 0.045f),
                        primarySignal.copy(alpha = if (dark) 0.025f else 0.012f),
                        Color.Transparent
                    ),
                    center = glowCenter,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = glowCenter
            )

            val signals = listOf(primarySignal, secondarySignal, tertiarySignal)
            repeat(9) { index ->
                val x = size.width * (((index * 37 + 11) % 97) / 97f)
                val y = size.height * (((index * 61 + 19) % 101) / 101f)
                val pulse = 0.72f + 0.28f * sin(phase * 0.8f + index * 1.31f)
                val color = signals[index % signals.size]
                drawCircle(
                    color = color.copy(alpha = (if (dark) 0.15f else 0.09f) * pulse),
                    radius = if (index % 3 == 0) 1.8.dp.toPx() else 1.1.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

/** Legacy name retained; mode changes use a low-opacity 280ms signal sweep. */
@Composable
fun ModeHyperspaceTransition(mode: AssistantMode) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val previousMode = remember { mutableStateOf(mode) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(mode) {
        if (previousMode.value != mode) {
            previousMode.value = mode
            progress.snapTo(0f)
            if (motionEnabled) {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(KryzzMotion.ModeChange, easing = FastOutSlowInEasing)
                )
            } else {
                progress.snapTo(1f)
            }
        }
    }

    if (progress.value < 0.999f) {
        Canvas(Modifier.fillMaxSize()) {
            val pulse = sin(progress.value * PI.toFloat()).coerceAtLeast(0f)
            val direction = if (mode == AssistantMode.AGENT) -1f else 1f
            val signal = if (mode == AssistantMode.AGENT) SignalViolet else SignalBlue
            val sweep = size.width * (
                if (direction < 0f) 1.15f - progress.value * 1.3f
                else -0.15f + progress.value * 1.3f
            )
            drawRect(Color.Black.copy(alpha = pulse * 0.14f))
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        signal.copy(alpha = pulse * 0.16f),
                        Color.Transparent
                    ),
                    start = Offset(sweep - size.width * 0.24f, 0f),
                    end = Offset(sweep + size.width * 0.24f, size.height)
                )
            )
            drawLine(
                color = signal.copy(alpha = pulse * 0.30f),
                start = Offset(sweep, 0f),
                end = Offset(sweep + direction * size.width * 0.10f, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

/** Legacy name retained; the launch intro is the Obsidian Constellation brand reveal. */
@Composable
fun KryzzLaunchIntro(onFinished: () -> Unit) {
    val motionEnabled = LocalKryzzMotionEnabled.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (motionEnabled) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(520, easing = FastOutSlowInEasing)
            )
        } else {
            progress.snapTo(1f)
        }
        currentOnFinished()
    }

    Box(
        Modifier.fillMaxSize().background(Midnight),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val eased = FastOutSlowInEasing.transform(progress.value)
            repeat(38) { index ->
                val x = size.width * (((index * 67 + 13) % 101) / 101f)
                val y = size.height * (((index * 43 + 17) % 103) / 103f)
                val reveal = ((eased * 1.35f) - index / 72f).coerceIn(0f, 1f)
                drawCircle(
                    color = Color.White.copy(alpha = reveal * (0.08f + (index % 4) * 0.025f)),
                    radius = (0.6f + (index % 3) * 0.35f).dp.toPx(),
                    center = Offset(x, y)
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.055f * eased),
                        Color.White.copy(alpha = 0.012f * eased),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.46f),
                    radius = size.minDimension * (0.14f + eased * 0.22f)
                ),
                center = Offset(size.width * 0.5f, size.height * 0.46f),
                radius = size.minDimension * (0.14f + eased * 0.22f)
            )
            val orbitRadius = size.minDimension * (0.16f + eased * 0.025f)
            val orbitCenter = Offset(size.width * 0.5f, size.height * 0.46f)
            drawCircle(
                color = Color.White.copy(alpha = 0.10f * eased),
                radius = orbitRadius,
                center = orbitCenter,
                style = Stroke(width = 0.75.dp.toPx())
            )
            val satellite = Offset(
                x = orbitCenter.x + cos(eased * PI.toFloat() * 1.55f) * orbitRadius,
                y = orbitCenter.y + sin(eased * PI.toFloat() * 1.55f) * orbitRadius
            )
            drawCircle(Color.White.copy(alpha = 0.86f * eased), 2.dp.toPx(), satellite)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .graphicsLayer(
                    alpha = progress.value.coerceIn(0f, 1f),
                    translationY = (1f - progress.value) * 12f
                )
        ) {
            KryzzMark(
                Modifier.size(116.dp),
                tint = Color.White,
                contentDescription = null
            )
            Text(
                text = "KRYZZ AI",
                modifier = Modifier.padding(top = 18.dp),
                color = KryzzColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "PRIVATE  /  LOCAL-FIRST",
                modifier = Modifier.padding(top = 6.dp),
                color = KryzzColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
