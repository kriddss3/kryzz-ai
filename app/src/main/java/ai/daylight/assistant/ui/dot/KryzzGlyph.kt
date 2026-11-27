package ai.daylight.assistant.ui.dot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

/**
 * A Kryzz mark. Vector icons (`ic_kz_*`) tint as one colour. The generated LED icons
 * (`kz_led_*`) keep three roles: lit dots follow [tint], the live dot stays [accent],
 * and unlit dots stay grey. Near-black tints, such as a selected white chip, fold the
 * live dot into [tint] so it does not disappear.
 */
@Composable
fun KryzzGlyph(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color,
    accent: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val led = remember(icon) { context.resources.getResourceEntryName(icon).startsWith("kz_led_") }
    if (!led) {
        Icon(painterResource(icon), null, modifier, tint = tint)
        return
    }
    val live = when {
        accent != Color.Unspecified -> accent
        tint.luminance() < 0.08f -> tint
        else -> DotTheme.c.accent
    }
    val bitmap = remember(icon, tint, live) {
        LedMarks.image(context, icon, tint.toArgb(), live.toArgb(), UNLIT)
    }
    Image(bitmap, contentDescription = null, modifier = modifier)
}

private const val UNLIT = 0xFF585858.toInt()

private object LedMarks {
    private val cache = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun image(context: android.content.Context, @DrawableRes id: Int, lit: Int, accent: Int, unlit: Int): ImageBitmap {
        val key = "$id:$lit:$accent:$unlit"
        cache.get(key)?.let { return it }
        val rendered = render(context, id, lit, accent, unlit)
        cache.put(key, rendered)
        return rendered
    }

    private fun render(context: android.content.Context, @DrawableRes id: Int, lit: Int, accent: Int, unlit: Int): ImageBitmap {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inSampleSize = 8
        }
        val source = BitmapFactory.decodeResource(context.resources, id, options)
            ?: return ImageBitmap(1, 1)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val red = (pixel shr 16) and 0xff
            val green = (pixel shr 8) and 0xff
            val blue = pixel and 0xff
            val lum = (red + green + blue) / 3
            val yellow = red > 140 && green > 80 && blue < 120 && (red - blue) > 50
            val alpha = when {
                lum < 18 && !yellow -> 0
                yellow -> ((red - blue) * 255 / 180).coerceIn(0, 255)
                lum >= 160 -> lum
                else -> (lum * 255 / 88).coerceIn(0, 255)
            }
            val rgb = when {
                yellow -> accent
                lum >= 160 -> lit
                else -> unlit
            }
            pixels[i] = (alpha shl 24) or (rgb and 0x00ffffff)
        }
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        source.recycle()
        return out.asImageBitmap()
    }
}
