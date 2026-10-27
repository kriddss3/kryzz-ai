@file:OptIn(ExperimentalTextApi::class)

package ai.daylight.assistant.ui.dot

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ai.daylight.assistant.R

/*
 * Fonts (all SIL OFL 1.1, licences in assets/licenses/):
 *  - doto.ttf        Doto variable: dot-matrix titles. UPPERCASE, short strings only.
 *  - geist.ttf       Geist variable: body and headings.
 *  - geist_mono.ttf  Geist Mono variable: times, counts, code.
 *  - space_mono*.ttf Space Mono: UPPERCASE labels and captions.
 * Variable weights use FontVariation (API 26+, minSdk is 28).
 */
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** The mockups render Doto at its default instance (900); tokens.json says 700. */
const val DOTO_WEIGHT = 900

val DotFamily = FontFamily(variable(R.font.doto, 400), variable(R.font.doto, 700), variable(R.font.doto, 900))
val SansFamily = FontFamily(
    variable(R.font.geist, 400),
    variable(R.font.geist, 500),
    variable(R.font.geist, 600),
    variable(R.font.geist, 700)
)
val MonoFamily = FontFamily(
    Font(R.font.space_mono, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold)
)
val DataMonoFamily = FontFamily(variable(R.font.geist_mono, 400), variable(R.font.geist_mono, 500))

/** Kit type scale (tokens.json font.scale_sp plus the sizes the screen mockups use). */
object DotType {
    // Dot face: UPPERCASE / numerals, short strings, >= 24sp. Uppercase the string yourself.
    val DotHero = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight(DOTO_WEIGHT), fontSize = 64.sp, lineHeight = 58.sp)
    val DotDisplay = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight(DOTO_WEIGHT), fontSize = 40.sp, lineHeight = 38.sp)
    val DotTitle = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight(DOTO_WEIGHT), fontSize = 28.sp, lineHeight = 28.sp)

    // Geist
    val Question = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.022).em)
    val Headline = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em)
    val Title = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp)
    val Answer = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 28.sp)
    val Body = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.006.em)
    val BodySmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.006.em)
    val Button = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)

    // Space Mono: always uppercase the string yourself, e.g. Text(text.uppercase(), style = DotType.Label)
    val Label = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em)
    val Caption = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.06.em)

    // Geist Mono: times, counts, code
    val Data = TextStyle(fontFamily = DataMonoFamily, fontSize = 12.sp, lineHeight = 16.sp)
    val Code = TextStyle(fontFamily = DataMonoFamily, fontSize = 13.sp, lineHeight = 19.sp)
}

/**
 * Every Material 3 slot is mapped so no stock component falls back to Roboto.
 * Display slots are the dot face (uppercase only); nothing in the app used them before.
 */
val DotTypography = Typography(
    displayLarge = DotType.DotHero,
    displayMedium = DotType.DotDisplay,
    displaySmall = DotType.DotTitle,
    headlineLarge = DotType.Question,
    headlineMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.015).em),
    headlineSmall = DotType.Headline,
    titleLarge = DotType.Headline,
    titleMedium = DotType.Title,
    titleSmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = DotType.Body,
    bodySmall = DotType.BodySmall,
    labelLarge = DotType.Button,
    labelMedium = DotType.Label,
    labelSmall = DotType.Caption,
)
