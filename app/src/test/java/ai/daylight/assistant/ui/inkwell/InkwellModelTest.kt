package ai.daylight.assistant.ui.inkwell

import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.RecentOutput
import ai.daylight.assistant.ui.Routes
import ai.daylight.assistant.ui.panelSwipeEnabledForRoute
import ai.daylight.assistant.ui.shell.InkLine
import ai.daylight.assistant.ui.shell.InkLines
import ai.daylight.assistant.ui.shell.armForRoute
import ai.daylight.assistant.ui.shell.defaultInkLine
import ai.daylight.assistant.ui.shell.railVisibleForRoute
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** C10: the Inkwell feed, its captions, and Home's badge. */
class InkwellModelTest {
    private val zone = ZoneId.of("Europe/Riga")
    private fun at(d: Int, h: Int, mi: Int = 0): Long =
        LocalDateTime.of(2026, 10, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private val now = at(29, 18)

    private fun ink(
        id: String,
        kind: OutputKind,
        createdAt: Long,
        message: String = "m-$id",
        prompt: String? = "a prompt for $id",
        title: String = "Generated $id",
        file: String = "$id.bin",
        localPath: String? = "/files/$id",
    ) = RecentOutput(
        GeneratedOutput(id, kind, title, file, "application/octet-stream", localPath = localPath),
        message, "c-$id", createdAt, prompt
    )

    @Test fun mediaPairsUpFilesTakeARowAndDaysGetHeaders() {
        val items = listOf(
            ink("i1", OutputKind.IMAGE, at(29, 17)),
            ink("i2", OutputKind.IMAGE, at(29, 16)),
            ink("v1", OutputKind.VIDEO, at(29, 15)),
            ink("doc", OutputKind.DOCUMENT, at(29, 14)),
            ink("i3", OutputKind.IMAGE, at(29, 13)),
            ink("old", OutputKind.IMAGE, at(23, 9)),
        )
        val rows = InkwellModel.rows(items, InkFilter.ALL, now, zone)
        val shape = rows.map { row ->
            when (row) {
                is InkRow.Header -> row.label
                is InkRow.Media -> row.items.joinToString("+") { it.output.id }
                is InkRow.File -> "file:${row.item.output.id}"
            }
        }
        assertThat(shape).containsExactly("TODAY", "i1+i2", "v1", "file:doc", "i3", "( 23 ) OCT", "old").inOrder()
        assertThat(rows.map { it.key }).containsNoDuplicates()
    }

    @Test fun filtersPickTheirKinds() {
        val items = listOf(
            ink("i1", OutputKind.IMAGE, at(29, 17)),
            ink("v1", OutputKind.VIDEO, at(29, 16)),
            ink("pdf", OutputKind.PDF, at(29, 15)),
            ink("xlsx", OutputKind.SPREADSHEET, at(29, 14)),
        )
        fun ids(filter: InkFilter) = InkwellModel.rows(items, filter, now, zone)
            .flatMap { row ->
                when (row) {
                    is InkRow.Header -> emptyList()
                    is InkRow.Media -> row.items.map { it.output.id }
                    is InkRow.File -> listOf(row.item.output.id)
                }
            }
        assertThat(ids(InkFilter.ALL)).containsExactly("i1", "v1", "pdf", "xlsx").inOrder()
        assertThat(ids(InkFilter.IMAGES)).containsExactly("i1")
        assertThat(ids(InkFilter.CLIPS)).containsExactly("v1")
        assertThat(ids(InkFilter.FILES)).containsExactly("pdf", "xlsx").inOrder()
        assertThat(InkwellModel.rows(emptyList(), InkFilter.ALL, now, zone)).isEmpty()
    }

    @Test fun anImageWithNoFileIsAFileRowNotABlankTile() {
        val lost = ink("lost", OutputKind.IMAGE, at(29, 10), localPath = null)
        assertThat(InkwellModel.isTile(lost.output)).isFalse()
        assertThat(InkwellModel.rows(listOf(lost), InkFilter.ALL, now, zone).last()).isInstanceOf(InkRow.File::class.java)
    }

    @Test fun oneAnswerWithTwoFilesKeepsTwoKeys() {
        val a = ink("a", OutputKind.DOCUMENT, at(29, 10), message = "same")
        val b = ink("b", OutputKind.SPREADSHEET, at(29, 10), message = "same")
        assertThat(InkwellModel.key(a)).isNotEqualTo(InkwellModel.key(b))
    }

    @Test fun captionsTitlesAndSummary() {
        val image = ink("i1", OutputKind.IMAGE, at(29, 9, 12), prompt = "Yellow lantern over a harbour")
        val doc = ink("doc", OutputKind.DOCUMENT, at(26, 21, 5), title = "Quarterly report", file = "report.docx")
        val clip = ink("v1", OutputKind.VIDEO, at(29, 8), prompt = null, title = "Generated clip")
        assertThat(InkwellModel.caption(image, zone)).isEqualTo("09:12 · IMAGE")
        assertThat(InkwellModel.caption(doc, zone)).isEqualTo("21:05 · DOCX")
        assertThat(InkwellModel.sheetLabel(doc, zone)).isEqualTo("DOCX · 26 OCT 21:05")
        // Media are named by what was asked; files by their own title.
        assertThat(InkwellModel.title(image)).isEqualTo("Yellow lantern over a harbour")
        assertThat(InkwellModel.title(doc)).isEqualTo("Quarterly report")
        assertThat(InkwellModel.title(clip)).isEqualTo("Generated clip")
        assertThat(InkwellModel.kindLabel(clip.output)).isEqualTo("CLIP")
        assertThat(InkwellModel.summary(listOf(image, doc, clip))).isEqualTo("( 3 ) · 1 IMAGE · 1 CLIP · 1 FILE")
        assertThat(InkwellModel.summary(listOf(image, image.copy(messageId = "x")))).isEqualTo("( 2 ) · 2 IMAGES")
    }

    @Test fun badgeCountsWhatIsNewerThanTheSeenMark() {
        val items = listOf(ink("a", OutputKind.IMAGE, 300), ink("b", OutputKind.IMAGE, 200), ink("c", OutputKind.PDF, 100))
        assertThat(InkwellModel.newest(items)).isEqualTo(300L)
        assertThat(InkwellModel.newest(emptyList())).isNull()
        assertThat(InkwellModel.newCount(items, seenAt = 0)).isEqualTo(3)
        assertThat(InkwellModel.newCount(items, seenAt = 200)).isEqualTo(1)
        assertThat(InkwellModel.newCount(items, seenAt = 300)).isEqualTo(0)
        assertThat(InkwellModel.badge(0)).isEmpty()
        assertThat(InkwellModel.badge(6)).isEqualTo("6")
        assertThat(InkwellModel.badge(9)).isEqualTo("9")
        assertThat(InkwellModel.badge(10)).isEqualTo("9+")
    }

    @Test fun newTagsOnlyAfterAFirstVisit() {
        val item = ink("a", OutputKind.IMAGE, 300)
        assertThat(InkwellModel.isNew(item, seenAtOnOpen = null)).isFalse()
        assertThat(InkwellModel.isNew(item, seenAtOnOpen = 0)).isFalse()
        assertThat(InkwellModel.isNew(item, seenAtOnOpen = 200)).isTrue()
        assertThat(InkwellModel.isNew(item, seenAtOnOpen = 300)).isFalse()
    }

    @Test fun inkwellRouteIsADepthPageWithItsOwnLine() {
        assertThat(Routes.INKWELL).isEqualTo("inkwell")
        assertThat(armForRoute(Routes.INKWELL)).isNull()
        assertThat(railVisibleForRoute(Routes.INKWELL)).isFalse()
        assertThat(panelSwipeEnabledForRoute(Routes.INKWELL)).isFalse()
        assertThat(defaultInkLine(Routes.INKWELL)).isEqualTo(InkLine("INKWELL"))
        assertThat(InkLines.inkwell(0)).isEqualTo(InkLine("INKWELL EMPTY"))
        assertThat(InkLines.inkwell(1, "CLIP")).isEqualTo(InkLine("INKWELL 1 CLIP"))
        assertThat(InkLines.inkwell(18)).isEqualTo(InkLine("INKWELL 18 PIECES"))
        assertThat(InkLines.inkwell(7, InkFilter.FILES.noun)).isEqualTo(InkLine("INKWELL 7 FILES"))
        assertThat(InkLines.inkwell(9, InkFilter.IMAGES.noun)).isEqualTo(InkLine("INKWELL 9 IMAGES"))
        // The words fit the strip's 5x7 font unchanged.
        listOf(InkLines.inkwell(18), InkLines.inkwell(1, "CLIP"), InkLine("INKWELL SAVED"), InkLine("INKWELL SAVE FAILED"))
            .forEach { assertThat(InkLine.sanitize(it.text)).isEqualTo(it.text) }
    }
}
