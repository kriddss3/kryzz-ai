package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** C8: the credit that goes with every saved or shared Find image (title, author, licence, source). */
class CreditLineTest {
    private val page = "https://commons.wikimedia.org/wiki/File:Tayid_Lighthouse_at_Night.jpg"
    private val bySa = ImageLicence.of("CC BY-SA 4.0", "cc-by-sa-4.0", "https://creativecommons.org/licenses/by-sa/4.0")

    @Test fun aCcImageNamesTitleAuthorLicenceWithItsLinkAndSource() {
        assertThat(CreditLine.build("Tayid Lighthouse at Night", "Bien Orellana Bacarra Jr", bySa, page)).isEqualTo(
            "\"Tayid Lighthouse at Night\" by Bien Orellana Bacarra Jr, CC BY-SA 4.0 " +
                "(https://creativecommons.org/licenses/by-sa/4.0), via Wikimedia Commons: $page"
        )
    }

    @Test fun publicDomainSaysSoAndLeavesOutAMissingAuthor() {
        val pd = ImageLicence.of("Public domain", "pd")
        assertThat(CreditLine.build("Coat of Arms", null, pd, page))
            .isEqualTo("\"Coat of Arms\", public domain, via Wikimedia Commons: $page")
        assertThat(CreditLine.build("Coat of Arms", "  ", pd, page))
            .isEqualTo("\"Coat of Arms\", public domain, via Wikimedia Commons: $page")
    }

    @Test fun theAuthorsOwnAttributionWordingWins() {
        val by = ImageLicence.of("CC BY 3.0", "cc-by-3.0", "https://creativecommons.org/licenses/by/3.0")
        assertThat(CreditLine.build("Harbour", "Letartean", by, page, attribution = "Photo: Letartean / Wikimedia"))
            .isEqualTo(
                "\"Harbour\", Photo: Letartean / Wikimedia, CC BY 3.0 " +
                    "(https://creativecommons.org/licenses/by/3.0), via Wikimedia Commons: $page"
            )
    }

    @Test fun longOrMultiLineFieldsBecomeOneShortLine() {
        val author = "Line one\n\nline two " + "x".repeat(300)
        val credit = CreditLine.build("  A   title\n", author, bySa, page)
        assertThat(credit).startsWith("\"A title\" by Line one line two xxx")
        assertThat(credit).doesNotContain("\n")
        val by = credit.substringAfter(" by ").substringBefore(", CC BY-SA")
        assertThat(by.length).isEqualTo(CreditLine.MAX_PART)
        assertThat(by).endsWith("…")
        assertThat(CreditLine.build("", null, bySa, page)).startsWith("\"Untitled\", CC BY-SA 4.0")
    }

    @Test fun aFoundImageBuildsItsOwnCredit() {
        val image = FoundImage(
            id = 1, fileTitle = "File:T.jpg", title = "T", author = "A", attribution = null, licence = bySa,
            pageUrl = page, originalUrl = "o", thumbUrl = "t", viewUrl = "v", saveUrl = "s",
            thumbWidth = 500, thumbHeight = 250, width = 4000, height = 2000, bytes = 1, mime = "image/jpeg"
        )
        assertThat(image.credit).isEqualTo(CreditLine.build("T", "A", bySa, page))
        assertThat(image.aspect).isEqualTo(2f)
        // Very tall or very wide images are kept usable in the grid.
        assertThat(image.copy(thumbWidth = 100, thumbHeight = 1000).aspect).isEqualTo(0.45f)
        assertThat(image.copy(thumbWidth = 0, thumbHeight = 0, width = 0, height = 0).aspect).isEqualTo(1f)
    }
}
