package ai.daylight.assistant.ui.dot

import androidx.compose.ui.graphics.Color

/** Secondary accent colours for Talk's dot-grid splash and Tune's appearance selector. */
enum class DotSecondaryColor(
    val id: String,
    val label: String,
    val dark: Color,
    val light: Color,
) {
    AMBER("amber", "Amber", DotColors.Accent, DotColorsLight.AccentText),
    CORAL("coral", "Coral", Color(0xFFFF5A36), Color(0xFFC93616)),
    CRIMSON("crimson", "Crimson", Color(0xFFFF2E54), Color(0xFFC41638)),
    MAGENTA("magenta", "Magenta", Color(0xFFE040FB), Color(0xFF9C1AB3)),
    VIOLET("violet", "Violet", Color(0xFF8B5CF6), Color(0xFF5B2FD6)),
    COBALT("cobalt", "Cobalt", Color(0xFF3B82F6), Color(0xFF1D5CD6)),
    CYAN("cyan", "Cyan", Color(0xFF06B6D4), Color(0xFF087F94)),
    EMERALD("emerald", "Emerald", Color(0xFF10B981), Color(0xFF0A7A55)),
    LIME("lime", "Lime", Color(0xFFA3E635), Color(0xFF4D7C0F)),
    MONO("mono", "Mono", DotColors.TextPrimary, DotColorsLight.TextPrimary);

    fun colorFor(isDark: Boolean): Color = if (isDark) dark else light

    companion object {
        fun from(id: String?): DotSecondaryColor =
            entries.firstOrNull { it.id == id } ?: AMBER

        fun indexOf(id: String?): Int = entries.indexOf(from(id))
    }
}
