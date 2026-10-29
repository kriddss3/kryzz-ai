package ai.daylight.assistant.ui.about

import androidx.annotation.DrawableRes
import ai.daylight.assistant.R

/** One ruled fact on the About page. */
data class AboutFact(@DrawableRes val icon: Int, val title: String, val caption: String? = null)

/** What the About page says (C11, replacing the old galaxy card). */
object AboutModel {
    /** The mono line under the name: "VERSION 5.12.12 (97)". */
    fun versionLine(name: String, code: Int): String = "Version $name ($code)"

    const val MADE_BY = "Created and coded by Kryzz"

    val madeWith = listOf(
        AboutFact(R.drawable.ic_kz_user, MADE_BY),
        AboutFact(R.drawable.ic_kz_code, "Kotlin and Jetpack Compose", "Built with"),
    )

    val poweredBy = listOf(
        AboutFact(R.drawable.ic_kz_model, "OpenRouter", "Chat models and voice chat"),
        AboutFact(R.drawable.ic_kz_video, "MiniMax", "Optional chat, images and video"),
        AboutFact(R.drawable.ic_kz_web, "Parallel", "Web search and citations"),
        AboutFact(R.drawable.ic_kz_voice_wave, "Fish Audio", "Spoken replies"),
        AboutFact(R.drawable.ic_kz_image, "Wikimedia Commons", "Free images in Find"),
    )

    val privacy = listOf(
        AboutFact(R.drawable.ic_kz_phone_local, "Chats stay on this phone", "No cloud copy"),
        AboutFact(R.drawable.ic_kz_shield, "No analytics or telemetry", "Nothing is tracked"),
        AboutFact(R.drawable.ic_kz_lock, "Keys in Android Keystore", "Never in a backup"),
    )

    const val FONTS = "Doto, Geist, Geist Mono, Space Mono"
    const val FONT_LICENCE = "SIL Open Font License 1.1"

    /** The folder in assets that holds the OFL texts shipped with the fonts. */
    const val LICENCE_FOLDER = "licenses"

    /**
     * The OFL files break lines at about 70 characters, which wraps into ragged pairs on a
     * phone. This joins the lines of each paragraph, keeps a short caps heading ("PREAMBLE")
     * and list items on their own lines, and drops the rows of dashes.
     */
    fun reflow(text: String): String = text.replace("\r\n", "\n")
        .split(Regex("\n[ \t]*\n"))
        .mapNotNull { paragraph ->
            val lines = paragraph.lines().map { it.trim() }.filter { line -> line.isNotEmpty() && !line.all { it == '-' } }
            if (lines.isEmpty()) return@mapNotNull null
            buildString {
                lines.forEachIndexed { i, line ->
                    if (i > 0) append(if (isHeading(lines[i - 1]) || LIST_ITEM.containsMatchIn(line)) "\n" else " ")
                    append(line)
                }
            }
        }
        .joinToString("\n\n")

    private val LIST_ITEM = Regex("""^(\d+\)|[-*•])\s""")

    private fun isHeading(line: String): Boolean =
        line.length <= 40 && line.any { it.isLetter() } && line == line.uppercase()

    /** "Geist-OFL.txt" -> "Geist and Geist Mono". Unknown files keep their stem. */
    fun licenceTitle(file: String): String = when (val stem = file.substringBefore('-').substringBefore('.')) {
        "Doto" -> "Doto"
        "Geist" -> "Geist and Geist Mono"
        "SpaceMono" -> "Space Mono"
        else -> stem
    }
}
