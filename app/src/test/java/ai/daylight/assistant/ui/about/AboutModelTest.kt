package ai.daylight.assistant.ui.about

import ai.daylight.assistant.ui.Routes
import ai.daylight.assistant.ui.panelSwipeEnabledForRoute
import ai.daylight.assistant.ui.shell.armForRoute
import ai.daylight.assistant.ui.shell.defaultInkLine
import ai.daylight.assistant.ui.shell.railVisibleForRoute
import ai.daylight.assistant.ui.shell.shellChromeVisibleForRoute
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class AboutModelTest {
    @Test fun versionLineCarriesNameAndBuild() {
        assertThat(AboutModel.versionLine("5.12.12", 97)).isEqualTo("Version 5.12.12 (97)")
    }

    @Test fun creditsKeepTheMakerAndEveryProvider() {
        assertThat(AboutModel.madeWith.map { it.title }).contains("Created and coded by Kryzz")
        assertThat(AboutModel.poweredBy.map { it.title })
            .containsAtLeast("OpenRouter", "Parallel", "Fish Audio", "Wikimedia Commons")
        assertThat(AboutModel.privacy.map { it.title }).contains("No analytics or telemetry")
    }

    @Test fun licenceTitlesNameTheFonts() {
        assertThat(AboutModel.licenceTitle("Doto-OFL.txt")).isEqualTo("Doto")
        assertThat(AboutModel.licenceTitle("Geist-OFL.txt")).isEqualTo("Geist and Geist Mono")
        assertThat(AboutModel.licenceTitle("SpaceMono-OFL.txt")).isEqualTo("Space Mono")
        assertThat(AboutModel.licenceTitle("Other.txt")).isEqualTo("Other")
    }

    @Test fun everyShippedLicenceIsListed() {
        val files = File("src/main/assets/${AboutModel.LICENCE_FOLDER}").listFiles().orEmpty().map { it.name }
        assertThat(files).isNotEmpty()
        // Each OFL text belongs to a font the page names ("Geist and Geist Mono" -> "Geist").
        files.forEach { assertThat(AboutModel.FONTS).contains(AboutModel.licenceTitle(it).substringBefore(" and ")) }
    }

    @Test fun licenceTextReflowsIntoParagraphs() {
        val raw = "Copyright 2024 The Doto\r\nProject Authors\n\n" +
            "-----------\nSIL OPEN FONT LICENSE Version 1.1\n-----------\n\n" +
            "PREAMBLE\nThe goals of the\nOpen Font License.\n\n" +
            "1) Neither the Font\nSoftware\n2) Original versions\n"
        assertThat(AboutModel.reflow(raw)).isEqualTo(
            "Copyright 2024 The Doto Project Authors\n\n" +
                "SIL OPEN FONT LICENSE Version 1.1\n\n" +
                "PREAMBLE\nThe goals of the Open Font License.\n\n" +
                "1) Neither the Font Software\n2) Original versions"
        )
    }

    @Test fun shippedLicencesReflowWithoutLosingWords() {
        File("src/main/assets/${AboutModel.LICENCE_FOLDER}").listFiles().orEmpty().forEach { file ->
            val raw = file.readText()
            val words = { s: String -> s.split(Regex("\\s+")).filter { w -> w.isNotEmpty() && !w.all { it == '-' } } }
            assertThat(words(AboutModel.reflow(raw))).isEqualTo(words(raw))
        }
    }

    @Test fun aboutIsATuneDepthPage() {
        assertThat(defaultInkLine(Routes.ABOUT).text).isEqualTo("TUNE ABOUT")
        assertThat(armForRoute(Routes.ABOUT)).isNull()
        assertThat(railVisibleForRoute(Routes.ABOUT)).isFalse()
        assertThat(shellChromeVisibleForRoute(Routes.ABOUT)).isTrue()
        assertThat(panelSwipeEnabledForRoute(Routes.ABOUT)).isFalse()
    }
}
