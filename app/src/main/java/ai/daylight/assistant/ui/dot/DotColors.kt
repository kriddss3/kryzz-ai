package ai.daylight.assistant.ui.dot

import androidx.compose.ui.graphics.Color

/*
 * Kryzz Dot palette, ported from kit/android/theme/Color.kt and kit/tokens.json.
 * These are the only colours the app may use. Yellow (#FFC700) means "live" or
 * "act here"; anything placed on yellow is black. Errors are #D71920.
 *
 * Snap table: greys that appear in the kit mockups but are not tokens are mapped
 * to the nearest token instead of being added:
 *   #151515 (ink strip unlit dot)      -> Surface        #111111
 *   #161616, #1C1C1C (row rules)       -> SurfaceRaised  #181818
 *   #1A1A1A, #1E1E1E, #1F1F1F (unlit)  -> SurfaceRaised  #181818
 *   #222222, #2E2E2E (queued / rail)   -> Border         #2A2A2A
 *   #3A3A3A (unlit remainder)          -> BorderStrong   #3C3C3C
 *   #4A4A4A, #6B6D6D, #6F7070          -> TextDisabled   #595A5A
 *   #9A9C9C (dim board rows)           -> Grey600        #9B9D9D
 *   #C9C9C9, #E6E6E6, #E8E8E8 (answer) -> TextPrimary    #FFFFFF
 *   #0A0A0A, #0B0B0B, #0C0C0C, #0E0E0E -> Surface        #111111
 */
object DotColors {
    val Bg = Color(0xFF000000)
    val BgAlt = Color(0xFF040404)
    val Surface = Color(0xFF111111)
    val SurfaceRaised = Color(0xFF181818)
    val SurfaceHigh = Color(0xFF232323)
    val Border = Color(0xFF2A2A2A)
    val BorderStrong = Color(0xFF3C3C3C)
    val DotOff = Color(0xFF262626)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB1B3B3)
    val TextTertiary = Color(0xFF858787)
    val TextDisabled = Color(0xFF595A5A)
    val Accent = Color(0xFFFFC700)
    val AccentText = Color(0xFFFFC700)
    val AccentPressed = Color(0xFFE0AF00)
    val AccentDim = Color(0x33FFC700)
    val OnAccent = Color(0xFF000000)
    val Recording = Color(0xFFFF2A1A)
    val Success = Color(0xFFFFFFFF)
    val Warning = Color(0xFFFFFFFF)
    val Error = Color(0xFFD71920)
}

object DotColorsLight {
    val Bg = Color(0xFFF4F4F4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFECECEC)
    val SurfaceHigh = Color(0xFFD8D9D9) // kit grey-200; light has no surfaceHigh token
    val Border = Color(0xFFD8D9D9)
    val BorderStrong = Color(0xFFCBCCCC) // kit grey-300
    val DotOff = Color(0xFFDCDCDC)
    val TextPrimary = Color(0xFF000000)
    val TextSecondary = Color(0xFF595A5A)
    // Kit grey-800, one step darker than the kit's light tertiary (#858787, 3.3:1 on Bg):
    // mono captions are small, so they need 4.5:1 to stay readable.
    val TextTertiary = Color(0xFF6F7070)
    val TextDisabled = Color(0xFF9B9D9D) // kit grey-600
    val Accent = Color(0xFFFFC700) // fills only on light
    val AccentText = Color(0xFF7A5900)
    val Error = Color(0xFFD71920)
    val OnAccent = Color(0xFF000000)
}

/** Kit reference grey scale (tokens.json color.reference.grey). */
object DotGrey {
    val G100 = Color(0xFFF4F4F4)
    val G200 = Color(0xFFD8D9D9)
    val G300 = Color(0xFFCBCCCC)
    val G400 = Color(0xFFBEC0C0)
    val G500 = Color(0xFFB1B3B3)
    val G600 = Color(0xFF9B9D9D)
    val G700 = Color(0xFF858787)
    val G800 = Color(0xFF6F7070)
    val G900 = Color(0xFF595A5A)
    val G1000 = Color(0xFF3C3C3C)
}
