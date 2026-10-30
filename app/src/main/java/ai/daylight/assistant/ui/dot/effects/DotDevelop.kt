package ai.daylight.assistant.ui.dot.effects

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ai.daylight.assistant.ui.dot.DotMotion
import ai.daylight.assistant.ui.dot.DotTheme
import ai.daylight.assistant.ui.theme.LocalKryzzMotionEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Dot develop, ported from kit/screens/src/dotimg.py: an image sampled on a dot grid
 * appears top to bottom behind a ragged grey front, each dot in the image's (brightened)
 * colour and sized by its brightness. Then the dots dissolve into the photo.
 *
 * Kryzz has no render percentage from any provider, so Make runs the develop once, when a
 * finished image lands while you watch, instead of tracking progress.
 */
object DotDevelopModel {
    /** How far behind the front a cell is fully developed (dotimg.py's 0.07). */
    const val FRONT = 0.07f

    /** Progress runs past 1 so the last row's front clears too. */
    const val END = 1f + FRONT

    /** Develop and dissolve run in 28ms steps (about 1.4s and 0.6s). */
    const val STEP_MS = DotMotion.DotStep
    const val DEVELOP_STEPS = 50
    const val DISSOLVE_STEPS = DotMotion.Reveal / DotMotion.DotStep

    /** Grid pitch and radii from dotimg.py, which works in dp at a 6dp pitch. */
    const val PITCH_DP = 6f
    const val FRONT_RADIUS_DP = 1.3f
    const val OFF_RADIUS_DP = 0.9f

    enum class Cell { HIDDEN, FRONT, SHOWN }

    /** Relative luminance 0..1 (Rec. 709 weights, as dotimg.py). */
    fun luma(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }

    /** dotimg.py's brightening: each channel times 1.8 plus 12, capped at 255; opaque. */
    fun brighten(argb: Int): Int {
        fun ch(v: Int) = min(255, (v * 1.8f + 12).toInt())
        return (0xFF shl 24) or (ch((argb shr 16) and 0xFF) shl 16) or (ch((argb shr 8) and 0xFF) shl 8) or ch(argb and 0xFF)
    }

    /** A shown dot's radius in dp at the 6dp pitch: 0.8dp for black up to 3.3dp for bright. */
    fun radiusDp(luma: Float): Float = 0.8f + min(1f, luma * 1.8f).pow(0.6f) * PITCH_DP * 0.42f

    /** When a cell develops (0..1): lower rows later, with noise so the front is ragged. */
    fun threshold(row: Int, rows: Int, noise: Float): Float = row.toFloat() / rows.coerceAtLeast(1) * 0.75f + noise * 0.25f

    fun cell(threshold: Float, progress: Float): Cell = when {
        threshold < progress - FRONT -> Cell.SHOWN
        threshold < progress -> Cell.FRONT
        else -> Cell.HIDDEN
    }

    /** Develop progress at [step] of [DEVELOP_STEPS]. */
    fun progressAt(step: Int): Float = step.coerceIn(0, DEVELOP_STEPS).toFloat() / DEVELOP_STEPS * END

    /**
     * The crop of a [srcW] x [srcH] image that fills a [boxW] x [boxH] frame (centre crop,
     * as ContentScale.Crop and dotimg.py): left, top, width, height.
     */
    fun centreCrop(srcW: Int, srcH: Int, boxW: Float, boxH: Float): IntArray {
        if (srcW <= 0 || srcH <= 0 || boxW <= 0f || boxH <= 0f) return intArrayOf(0, 0, srcW.coerceAtLeast(0), srcH.coerceAtLeast(0))
        val target = boxW / boxH
        return if (srcW.toFloat() / srcH > target) {
            val w = (srcH * target).roundToInt().coerceIn(1, srcW)
            intArrayOf((srcW - w) / 2, 0, w, srcH)
        } else {
            val h = (srcW / target).roundToInt().coerceIn(1, srcH)
            intArrayOf(0, (srcH - h) / 2, srcW, h)
        }
    }

    /** Builds the grid from [pixels] (ARGB, [cols] x [rows], row-major). */
    fun grid(pixels: IntArray, cols: Int, rows: Int, seed: Int = 3): DevelopGrid {
        val n = cols * rows
        val rnd = Random(seed)
        val colors = IntArray(n)
        val radii = FloatArray(n)
        val thresholds = FloatArray(n)
        val noise = FloatArray(n)
        for (y in 0 until rows) for (x in 0 until cols) {
            val i = y * cols + x
            val px = pixels.getOrElse(i) { 0 }
            colors[i] = brighten(px)
            radii[i] = radiusDp(luma(px))
            thresholds[i] = threshold(y, rows, rnd.nextFloat())
            noise[i] = dissolveNoise(x, y)
        }
        return DevelopGrid(cols, rows, colors, radii, thresholds, noise)
    }
}

/** A sampled image ready to develop: per cell its colour, radius (dp), develop threshold and dissolve noise. */
class DevelopGrid(
    val cols: Int,
    val rows: Int,
    val colors: IntArray,
    val radii: FloatArray,
    val thresholds: FloatArray,
    val noise: FloatArray,
)

private enum class DevelopPhase { WAIT, DEVELOP, DISSOLVE, DONE }

/**
 * [bitmap] cropped to fill this box. With [develop] (and motion on) it first develops as
 * dots, then dissolves into the photo, once; otherwise it is just the image.
 */
@Composable
fun DotDevelopImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    develop: Boolean,
    modifier: Modifier = Modifier,
    pitch: Dp = DotDevelopModel.PITCH_DP.dp,
) {
    val c = DotTheme.c
    val motion = LocalKryzzMotionEnabled.current
    val density = LocalDensity.current
    var phase by remember(bitmap) { mutableStateOf(if (develop && motion) DevelopPhase.WAIT else DevelopPhase.DONE) }
    val step = remember(bitmap) { mutableIntStateOf(0) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var grid by remember(bitmap) { mutableStateOf<DevelopGrid?>(null) }
    val pitchPx = with(density) { pitch.toPx() }

    LaunchedEffect(bitmap, boxSize) {
        when {
            phase == DevelopPhase.DONE -> return@LaunchedEffect
            // Restarted mid-way (the frame changed size): just show the photo.
            phase != DevelopPhase.WAIT -> {
                phase = DevelopPhase.DONE
                return@LaunchedEffect
            }
            boxSize.width <= 0 || boxSize.height <= 0 -> return@LaunchedEffect
        }
        val cols = (boxSize.width / pitchPx).toInt().coerceAtLeast(1)
        val rows = (boxSize.height / pitchPx).toInt().coerceAtLeast(1)
        grid = withContext(Dispatchers.Default) { sample(bitmap, boxSize, cols, rows) }
        if (grid == null) {
            phase = DevelopPhase.DONE
            return@LaunchedEffect
        }
        phase = DevelopPhase.DEVELOP
        for (s in 0..DotDevelopModel.DEVELOP_STEPS) {
            step.intValue = s
            delay(DotDevelopModel.STEP_MS.toLong())
        }
        phase = DevelopPhase.DISSOLVE
        for (s in 0..DotDevelopModel.DISSOLVE_STEPS) {
            step.intValue = s
            delay(DotDevelopModel.STEP_MS.toLong())
        }
        phase = DevelopPhase.DONE
    }

    Box(modifier.onSizeChanged { boxSize = it }) {
        if (phase == DevelopPhase.DISSOLVE || phase == DevelopPhase.DONE) {
            Image(bitmap, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        val g = grid
        if (g != null && (phase == DevelopPhase.DEVELOP || phase == DevelopPhase.DISSOLVE)) {
            val dissolving = phase == DevelopPhase.DISSOLVE
            Canvas(Modifier.fillMaxSize()) {
                val s = step.intValue
                if (dissolving) {
                    drawDissolveCover(g, s.toFloat() / DotDevelopModel.DISSOLVE_STEPS, pitchPx, c.surface)
                } else {
                    drawDevelop(g, DotDevelopModel.progressAt(s), pitchPx, c.textTertiary, c.dotOff)
                }
            }
        }
    }
}

/** Crops and scales [bitmap] to [cols] x [rows] samples for a box of [box] pixels. */
private fun sample(bitmap: ImageBitmap, box: IntSize, cols: Int, rows: Int): DevelopGrid? = runCatching {
    val src = bitmap.asAndroidBitmap()
    val crop = DotDevelopModel.centreCrop(src.width, src.height, box.width.toFloat(), box.height.toFloat())
    val cropped = Bitmap.createBitmap(src, crop[0], crop[1], crop[2], crop[3])
    val small = Bitmap.createScaledBitmap(cropped, cols, rows, true)
    val pixels = IntArray(cols * rows)
    small.getPixels(pixels, 0, cols, 0, 0, cols, rows)
    if (small !== cropped) small.recycle()
    if (cropped !== src) cropped.recycle()
    DotDevelopModel.grid(pixels, cols, rows)
}.getOrNull()

/** Cells stretch a little so the grid covers the whole box (no bare margin at the edges). */
private fun DrawScope.cellSize(g: DevelopGrid): Size = Size(size.width / g.cols, size.height / g.rows)

private fun cellCentre(cell: Size, x: Int, y: Int): Offset = Offset((x + .5f) * cell.width, (y + .5f) * cell.height)

/** The develop at [progress]: shown dots in colour, the front grey, the rest unlit. */
private fun DrawScope.drawDevelop(g: DevelopGrid, progress: Float, pitchPx: Float, front: Color, off: Color) {
    val dpScale = pitchPx / DotDevelopModel.PITCH_DP
    val cell = cellSize(g)
    val hidden = ArrayList<Offset>(g.cols * g.rows)
    val fronts = ArrayList<Offset>(g.cols * 2)
    for (y in 0 until g.rows) for (x in 0 until g.cols) {
        val i = y * g.cols + x
        val o = cellCentre(cell, x, y)
        when (DotDevelopModel.cell(g.thresholds[i], progress)) {
            DotDevelopModel.Cell.HIDDEN -> hidden += o
            DotDevelopModel.Cell.FRONT -> fronts += o
            DotDevelopModel.Cell.SHOWN -> drawCircle(Color(g.colors[i]), g.radii[i] * dpScale, o)
        }
    }
    if (hidden.isNotEmpty()) drawPoints(hidden, PointMode.Points, off, strokeWidth = DotDevelopModel.OFF_RADIUS_DP * 2 * dpScale, cap = StrokeCap.Round)
    if (fronts.isNotEmpty()) drawPoints(fronts, PointMode.Points, front, strokeWidth = DotDevelopModel.FRONT_RADIUS_DP * 2 * dpScale, cap = StrokeCap.Round)
}

/** Over the photo: the cells not yet dissolved still show their developed dot on the frame colour. */
private fun DrawScope.drawDissolveCover(g: DevelopGrid, progress: Float, pitchPx: Float, cover: Color) {
    if (progress >= 1f) return
    val dpScale = pitchPx / DotDevelopModel.PITCH_DP
    val cell = cellSize(g)
    val tile = Size(cell.width + .5f, cell.height + .5f)
    for (y in 0 until g.rows) for (x in 0 until g.cols) {
        val i = y * g.cols + x
        if (g.noise[i] <= progress) continue
        drawRect(cover, Offset(x * cell.width, y * cell.height), tile)
        drawCircle(Color(g.colors[i]), g.radii[i] * dpScale, cellCentre(cell, x, y))
    }
}
