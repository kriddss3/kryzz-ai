package ai.daylight.assistant.ui.dot

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Guards the kit rules in source: colours come only from ui/dot (Color.kt / tokens.json),
 * and there are no gradients, glass blur or named Compose colours anywhere in the app.
 */
class ColourLiteralAuditTest {
    private val root = File("src/main/java/ai/daylight/assistant")
    private val dotPackage = File(root, "ui/dot")

    private fun sources(): List<File> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
        .also { assertWithMessage("source root not found: ${root.absolutePath}").that(it).isNotEmpty() }

    @Test fun hexColourLiteralsOnlyLiveInTheDotPackage() {
        val offenders = sources()
            .filterNot { it.canonicalPath.startsWith(dotPackage.canonicalPath) }
            .filter { it.readText().contains("Color(0x") }
            .map { it.relativeTo(root).path }
        assertWithMessage("Use DotTheme.c / DotColors instead of Color(0x…)").that(offenders).isEmpty()
    }

    @Test fun noNamedComposeHues() {
        val named = Regex("""Color\.(Red|Green|Blue|Cyan|Magenta|Yellow|Gray|LightGray|DarkGray)\b""")
        val offenders = sources().filter { named.containsMatchIn(it.readText()) }.map { it.relativeTo(root).path }
        assertWithMessage("Only kit colours are allowed").that(offenders).isEmpty()
    }

    @Test fun noGradientsOrBlur() {
        val banned = Regex("""Brush\.(linear|horizontal|vertical|radial|sweep)Gradient|\.blur\(""")
        val offenders = sources().filter { banned.containsMatchIn(it.readText()) }.map { it.relativeTo(root).path }
        assertWithMessage("Kit rule: no gradients, no glass blur").that(offenders).isEmpty()
    }

    @Test fun noShadows() {
        val banned = Regex("""\.shadow\(|shadowElevation\s*=\s*[1-9]|defaultElevation\s*=\s*[1-9]""")
        val offenders = sources().filter { banned.containsMatchIn(it.readText()) }.map { it.relativeTo(root).path }
        assertWithMessage("Kit rule: no shadows; depth is a surface step plus a 1dp border").that(offenders).isEmpty()
    }
}
