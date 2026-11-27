package ai.daylight.assistant.ui.dot.effects

import android.graphics.Paint
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/** Pure math for the water-splash dot-grid animation. */
internal object DotSplashMath {
    const val SPLASH_MS = 780
    const val TALK_DOT_ALPHA_DARK = 0.22f
    const val TALK_DOT_ALPHA_LIGHT = 0.24f

    private const val SIGMA_PITCHES = 2.8f
    private const val HALF_BAND_PITCHES = 8.5f

    data class DotSample(
        val offsetX: Float,
        val offsetY: Float,
        val radiusScale: Float,
        val tintWeight: Float,
        val alphaBoost: Float,
    )

    fun frontRadius(progress: Float, maxRadius: Float, pitchPx: Float): Float {
        val halfBand = pitchPx * HALF_BAND_PITCHES
        return progress * (maxRadius + halfBand * 1.4f) - halfBand * 0.2f
    }

    fun sample(
        dotX: Float,
        dotY: Float,
        originX: Float,
        originY: Float,
        progress: Float,
        maxRadius: Float,
        pitchPx: Float,
    ): DotSample {
        if (pitchPx <= 0f) return DotSample(0f, 0f, 1f, 0f, 0f)
        val sigma = pitchPx * SIGMA_PITCHES
        val halfBand = pitchPx * HALF_BAND_PITCHES
        val frontR = frontRadius(progress, maxRadius, pitchPx)
        val dx = dotX - originX
        val dy = dotY - originY
        val dist = hypot(dx, dy)
        val delta = dist - frontR
        if (abs(delta) > halfBand) return DotSample(0f, 0f, 1f, 0f, 0f)
        val u = delta / sigma
        val attenuation = (1f - progress * 0.35f).coerceIn(0f, 1f)
        val crest = exp(-(u * u)) * attenuation
        val tintWeight = crest.coerceIn(0f, 1f)
        val radiusScale = 1f + crest * 1.35f
        val alphaBoost = crest * 0.62f
        val offsetX: Float
        val offsetY: Float
        if (dist > 0.5f) {
            val push = crest * pitchPx * 0.32f
            offsetX = (dx / dist) * push
            offsetY = (dy / dist) * push
        } else {
            offsetX = 0f
            offsetY = 0f
        }
        return DotSample(offsetX, offsetY, radiusScale, tintWeight, alphaBoost)
    }
}

/** Pre-allocated coordinates and Paint for zero-allocation splash frame rendering. */
internal class SplashDotGridCache {
    private var cachedWidth = -1f
    private var cachedHeight = -1f
    private var cachedPitch = -1f
    var allCoords = FloatArray(0)
        private set
    var staticPoints = FloatArray(0)
        private set
    var coordCount = 0
        private set
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    fun ensureGrid(width: Float, height: Float, pitchPx: Float): Int {
        if (width <= 0f || height <= 0f || pitchPx <= 0f) {
            coordCount = 0
            return 0
        }
        if (width == cachedWidth && height == cachedHeight && pitchPx == cachedPitch) {
            return coordCount
        }
        cachedWidth = width
        cachedHeight = height
        cachedPitch = pitchPx
        val cols = ((width - pitchPx / 2f) / pitchPx).toInt().coerceAtLeast(0) + 1
        val rows = ((height - pitchPx / 2f) / pitchPx).toInt().coerceAtLeast(0) + 1
        val needed = cols * rows * 2
        if (allCoords.size < needed) {
            allCoords = FloatArray(needed)
            staticPoints = FloatArray(needed)
        }
        var idx = 0
        var y = pitchPx / 2f
        while (y < height) {
            var x = pitchPx / 2f
            while (x < width) {
                if (idx + 1 < allCoords.size) {
                    allCoords[idx++] = x
                    allCoords[idx++] = y
                }
                x += pitchPx
            }
            y += pitchPx
        }
        coordCount = idx
        return idx
    }
}

/** Shared trigger state for the background water-splash dot ripple. */
@Stable
class DotSplashState {
    var serial by mutableIntStateOf(0)
        private set
    var origin by mutableStateOf<Offset?>(null)
        private set
    var fromColor by mutableStateOf<Color?>(null)
        private set
    var toColor by mutableStateOf<Color?>(null)
        private set
    var persistTint by mutableStateOf(false)
        private set

    fun trigger(
        origin: Offset?,
        targetColor: Color,
        fromColor: Color? = null,
        persistTint: Boolean = false,
    ) {
        this.origin = origin
        this.toColor = targetColor
        this.fromColor = fromColor
        this.persistTint = persistTint
        serial += 1
    }
}

val LocalDotSplash = compositionLocalOf<DotSplashState?> { null }

internal fun DrawScope.drawSplashDotGrid(
    pitchPx: Float,
    baseRadiusPx: Float,
    baseColor: Color,
    @Suppress("UNUSED_PARAMETER") fromColor: Color?,
    toColor: Color,
    origin: Offset,
    progress: Float,
    @Suppress("UNUSED_PARAMETER") persistTint: Boolean,
    @Suppress("UNUSED_PARAMETER") settledAlpha: Float,
    showBaseGrid: Boolean,
    cache: SplashDotGridCache? = null,
) {
    if (pitchPx <= 0f || baseRadiusPx <= 0f) return
    val activeCache = cache ?: SplashDotGridCache()
    val coordCount = activeCache.ensureGrid(size.width, size.height, pitchPx)
    if (coordCount == 0) return

    val paint = activeCache.paint
    val native = drawContext.canvas.nativeCanvas
    if (progress >= 1f) {
        if (!showBaseGrid) return
        paint.color = baseColor.toArgb()
        paint.strokeWidth = baseRadiusPx * 2f
        paint.strokeCap = Paint.Cap.ROUND
        native.drawPoints(activeCache.allCoords, 0, coordCount, paint)
        return
    }

    val maxRadius = maxOf(
        hypot(origin.x, origin.y),
        hypot(size.width - origin.x, origin.y),
        hypot(origin.x, size.height - origin.y),
        hypot(size.width - origin.x, size.height - origin.y),
    )
    val sigma = pitchPx * 2.8f
    val halfBand = pitchPx * 8.5f
    val frontR = DotSplashMath.frontRadius(progress, maxRadius, pitchPx)
    val innerR = (frontR - halfBand).coerceAtLeast(0f)
    val outerR = frontR + halfBand
    val innerRSq = innerR * innerR
    val outerRSq = outerR * outerR
    val attenuation = (1f - progress * 0.35f).coerceIn(0f, 1f)

    val baseA = if (showBaseGrid) baseColor.alpha else 0f
    val baseR = baseColor.red
    val baseG = baseColor.green
    val baseB = baseColor.blue
    val toR = toColor.red
    val toG = toColor.green
    val toB = toColor.blue

    val allCoords = activeCache.allCoords
    val staticPoints = activeCache.staticPoints
    var staticCount = 0
    paint.style = Paint.Style.FILL

    var i = 0
    while (i < coordCount) {
        val x = allCoords[i]
        val y = allCoords[i + 1]
        i += 2
        val dx = x - origin.x
        val dy = y - origin.y
        val distSq = dx * dx + dy * dy
        if (distSq < innerRSq || distSq > outerRSq) {
            if (showBaseGrid) {
                staticPoints[staticCount++] = x
                staticPoints[staticCount++] = y
            }
            continue
        }
        val dist = sqrt(distSq)
        val delta = dist - frontR
        val u = delta / sigma
        val crest = exp(-(u * u)) * attenuation
        val tw = crest.coerceIn(0f, 1f)
        val a = (baseA + crest * 0.62f).coerceIn(0f, 1f)
        if (a > 0.004f) {
            val r = (baseR + (toR - baseR) * tw).coerceIn(0f, 1f)
            val g = (baseG + (toG - baseG) * tw).coerceIn(0f, 1f)
            val b = (baseB + (toB - baseB) * tw).coerceIn(0f, 1f)
            paint.color = ((a * 255f + 0.5f).toInt() shl 24) or
                ((r * 255f + 0.5f).toInt() shl 16) or
                ((g * 255f + 0.5f).toInt() shl 8) or
                (b * 255f + 0.5f).toInt()
            val cx: Float
            val cy: Float
            if (dist > 0.5f) {
                val push = (crest * pitchPx * 0.32f) / dist
                cx = x + dx * push
                cy = y + dy * push
            } else {
                cx = x
                cy = y
            }
            native.drawCircle(cx, cy, baseRadiusPx * (1f + crest * 1.35f), paint)
        }
    }

    if (showBaseGrid && staticCount > 0) {
        paint.color = baseColor.toArgb()
        paint.strokeWidth = baseRadiusPx * 2f
        paint.strokeCap = Paint.Cap.ROUND
        native.drawPoints(staticPoints, 0, staticCount, paint)
    }
}
