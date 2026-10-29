package ai.daylight.assistant.ui.find

import ai.daylight.assistant.domain.FindFilters
import ai.daylight.assistant.domain.FindKind
import ai.daylight.assistant.domain.FindSize
import ai.daylight.assistant.domain.FindState
import ai.daylight.assistant.domain.FindStatus
import ai.daylight.assistant.domain.FoundImage
import ai.daylight.assistant.domain.ImageLicence
import ai.daylight.assistant.domain.LicenceFilter
import ai.daylight.assistant.ui.shell.InkLine
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** C8: Find's strip lines, captions and file names. */
class FindModelTest {
    private fun image(id: Long, title: String = "Tayid Lighthouse at Night", mime: String = "image/jpeg") = FoundImage(
        id = id, fileTitle = "File:x.jpg", title = title, author = "Bien Orellana Bacarra Jr", attribution = null,
        licence = ImageLicence.of("CC BY-SA 4.0", "cc-by-sa-4.0"), pageUrl = "https://commons.wikimedia.org/wiki/File:x.jpg",
        originalUrl = "o", thumbUrl = "t", viewUrl = "v", saveUrl = "s", thumbWidth = 500, thumbHeight = 400,
        width = 1000, height = 800, bytes = 1, mime = mime
    )

    @Test fun theStripReadsLikeTheMockup() {
        val twelve = FindState(status = FindStatus.DONE, images = (1L..12L).map(::image))
        assertThat(FindModel.inkLine(twelve, off = false)).isEqualTo(InkLine("FIND 12 IMAGES"))
        assertThat(FindModel.inkLine(twelve.copy(images = listOf(image(1))), off = false).text).isEqualTo("FIND 1 IMAGE")
        assertThat(FindModel.inkLine(twelve.copy(filters = FindFilters(kind = FindKind.DRAWINGS)), off = false).text)
            .isEqualTo("FIND 12 DRAWINGS")
        // Loading the next page lights the live mark.
        assertThat(FindModel.inkLine(twelve.copy(loadingMore = true), off = false).live).isTrue()
        assertThat(FindModel.inkLine(FindState(), off = false)).isEqualTo(InkLine("FIND READY"))
        assertThat(FindModel.inkLine(FindState(status = FindStatus.SEARCHING), off = false))
            .isEqualTo(InkLine("FIND SEARCHING", live = true))
        assertThat(FindModel.inkLine(FindState(status = FindStatus.DONE), off = false).text).isEqualTo("FIND NO MATCHES")
        assertThat(FindModel.inkLine(FindState(status = FindStatus.FAILED, offline = true), off = false).text).isEqualTo("FIND OFFLINE")
        assertThat(FindModel.inkLine(FindState(status = FindStatus.FAILED), off = false).text).isEqualTo("FIND FAILED")
        // Web search off wins over everything.
        assertThat(FindModel.inkLine(twelve, off = true).text).isEqualTo("FIND OFF")
        // Every line fits the 5x7 strip font.
        listOf("FIND 240 DRAWINGS", "FIND NO MATCHES", "FIND SAVED CREDIT COPIED", "USE IN MAKE NOT YET").forEach {
            assertThat(InkLine.sanitize(it)).isEqualTo(it)
        }
    }

    @Test fun everyTileNamesItsSourceAndLicence() {
        assertThat(FindModel.tileCaption(image(1))).isEqualTo("Commons, CC BY-SA 4.0")
        assertThat(FindModel.countCaption(12)).isEqualTo("12 shown")
        assertThat(FindModel.tileDescription(image(1)))
            .isEqualTo("Tayid Lighthouse at Night, by Bien Orellana Bacarra Jr, CC BY-SA 4.0, from Wikimedia Commons")
        assertThat(FindModel.tileDescription(image(1).copy(author = null)))
            .isEqualTo("Tayid Lighthouse at Night, CC BY-SA 4.0, from Wikimedia Commons")
    }

    @Test fun anEmptySearchSaysWhatToChange() {
        val empty = FindState(status = FindStatus.DONE, query = "owl")
        assertThat(FindModel.emptyBody(empty)).contains("Try fewer or different words")
        assertThat(FindModel.emptyBody(empty.copy(filteredOut = 3))).contains("Choose Any licence")
        // With Any licence on, hidden counts don't apply.
        assertThat(FindModel.emptyBody(empty.copy(filteredOut = 3, filters = FindFilters(licence = LicenceFilter.ANY))))
            .contains("Try fewer or different words")
        assertThat(FindModel.emptyBody(empty.copy(filters = FindFilters(size = FindSize.LARGE)))).contains("Try Any size")
    }

    @Test fun savedFilesGetTheRightTypeAndAReadableName() {
        assertThat(FindModel.savedMime("image/jpeg; charset=binary", image(1))).isEqualTo("image/jpeg")
        assertThat(FindModel.savedMime("image/png", image(1, mime = "image/svg+xml"))).isEqualTo("image/png")
        // A drawing never lands as SVG; a missing type follows the file.
        assertThat(FindModel.savedMime("image/svg+xml", image(1, mime = "image/svg+xml"))).isEqualTo("image/png")
        assertThat(FindModel.savedMime(null, image(1, mime = "image/webp"))).isEqualTo("image/webp")
        assertThat(FindModel.savedMime("text/html", image(1, mime = "application/pdf"))).isEqualTo("image/jpeg")
        assertThat(FindModel.extensionFor("image/png")).isEqualTo("png")
        assertThat(FindModel.extensionFor("image/jpeg")).isEqualTo("jpg")
        assertThat(FindModel.mimeForExtension("JPG")).isEqualTo("image/jpeg")
        assertThat(FindModel.mimeForExtension("png")).isEqualTo("image/png")

        assertThat(FindModel.displayName(image(1), "jpg")).isEqualTo("Tayid-Lighthouse-at-Night.jpg")
        assertThat(FindModel.displayName(image(1, title = "Ērgļi: café / 1935?"), "png")).isEqualTo("Ergli-cafe-1935.png")
        assertThat(FindModel.displayName(image(7, title = "Мост"), "jpg")).isEqualTo("commons-7.jpg")
        assertThat(FindModel.displayName(image(1, title = "a".repeat(100)), "jpg")).isEqualTo("a".repeat(60) + ".jpg")
    }

    @Test fun exifTextIsFoldedToAscii() {
        assertThat(FindModel.asciiFold("“Étang” by Zoë – © 2020…")).isEqualTo("\"Etang\" by Zoe - (c) 2020...")
        val cyrillic = FindModel.asciiFold("Анна Гарсия")
        assertThat(cyrillic).isEqualTo("???? ??????")
        assertThat(FindModel.readableAfterFold(cyrillic)).isFalse()
        assertThat(FindModel.readableAfterFold(FindModel.asciiFold("Bien Orellana Bacarra Jr"))).isTrue()
        assertThat(FindModel.readableAfterFold("")).isFalse()
    }
}
