package ai.daylight.assistant.domain

import ai.daylight.assistant.domain.DocumentLayout.Measure
import ai.daylight.assistant.domain.DocumentLayout.Slice
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentLayoutTest {
    @Test fun listMarkers() {
        assertThat(DocumentLayout.listMarker(ordered = false, start = 1, index = 0, depth = 0)).isEqualTo("•")
        assertThat(DocumentLayout.listMarker(ordered = false, start = 1, index = 3, depth = 1)).isEqualTo("◦")
        assertThat(DocumentLayout.listMarker(ordered = true, start = 3, index = 1, depth = 0)).isEqualTo("4.")
        assertThat(DocumentLayout.listMarker(ordered = true, start = 1, index = 1, depth = 1)).isEqualTo("b.")
        assertThat(DocumentLayout.alphabetic(27)).isEqualTo("aa")
    }

    @Test fun columnWidthsFillTheWidthAndFavourLongerColumns() {
        val table = DocumentMarkdown.parse("| # | Description |\n|---|---|\n| 1 | A much longer explanation of the item |").single() as DocumentMarkdown.Table
        val widths = DocumentLayout.columnWidths(table, totalWidth = 480f, minWidth = 48f)
        assertThat(widths.sum()).isWithin(0.01f).of(480f)
        assertThat(widths[1]).isGreaterThan(widths[0])
        assertThat(widths[0]).isAtLeast(48f)
    }

    @Test fun pdfItemsCarryIndentsMarkersAndTableRows() {
        val items = DocumentLayout.pdfItems(
            DocumentMarkdown.parse("# Plan\n1. One\n   - sub\n2. Two\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n```\ncode\n```"),
            contentWidth = 480f
        )
        val heading = items[0] as DocumentLayout.TextItem
        assertThat(heading.role).isEqualTo(DocumentLayout.Role.H1)
        assertThat(heading.keepWithNext).isTrue()
        val first = items[1] as DocumentLayout.TextItem
        assertThat(first.marker).isEqualTo("1.")
        assertThat(first.indent).isEqualTo(DocumentLayout.LIST_INDENT)
        val nested = items[2] as DocumentLayout.TextItem
        assertThat(nested.marker).isEqualTo("◦")
        assertThat(nested.indent).isEqualTo(DocumentLayout.LIST_INDENT * 2)
        assertThat((items[3] as DocumentLayout.TextItem).marker).isEqualTo("2.")
        val header = items[4] as DocumentLayout.TableRowItem
        assertThat(header.header).isTrue()
        assertThat(header.keepWithNext).isTrue()
        assertThat(header.columnWidths.sum()).isWithin(0.01f).of(480f)
        assertThat((items[5] as DocumentLayout.TableRowItem).header).isFalse()
        assertThat((items[6] as DocumentLayout.TextItem).role).isEqualTo(DocumentLayout.Role.CODE)
    }

    @Test fun paginateFitsItemsAndDropsSpaceAtPageTop() {
        val slices = DocumentLayout.paginate(
            listOf(
                Measure(listOf(10f, 10f), spaceBefore = 5f, spaceAfter = 5f, splittable = true),
                Measure(listOf(10f), spaceBefore = 5f, spaceAfter = 0f, splittable = true)
            ),
            pageHeight = 100f
        )
        assertThat(slices).containsExactly(Slice(0, 0, 0f, 0, 2), Slice(1, 0, 30f, 0, 1)).inOrder()
    }

    @Test fun paginateSplitsLongParagraphsAcrossPages() {
        val slices = DocumentLayout.paginate(
            listOf(Measure(List(25) { 10f }, spaceBefore = 0f, spaceAfter = 0f, splittable = true)),
            pageHeight = 100f
        )
        assertThat(slices.map { it.page to (it.fromLine until it.toLine).count() })
            .containsExactly(0 to 10, 1 to 10, 2 to 5).inOrder()
    }

    @Test fun paginateMovesUnsplittableRowsAndAvoidsOrphans() {
        val rows = DocumentLayout.paginate(
            listOf(
                Measure(listOf(85f), 0f, 0f, splittable = false),
                Measure(listOf(20f), 0f, 0f, splittable = false)
            ),
            pageHeight = 100f
        )
        assertThat(rows[1]).isEqualTo(Slice(1, 1, 0f, 0, 1))
        // Only one line of a three-line paragraph would fit: the whole paragraph moves.
        val orphan = DocumentLayout.paginate(
            listOf(
                Measure(listOf(85f), 0f, 0f, splittable = true),
                Measure(listOf(10f, 10f, 10f), 0f, 0f, splittable = true)
            ),
            pageHeight = 100f
        )
        assertThat(orphan[1]).isEqualTo(Slice(1, 1, 0f, 0, 3))
    }

    @Test fun paginateKeepsHeadingWithFollowingText() {
        val slices = DocumentLayout.paginate(
            listOf(
                Measure(listOf(80f), 0f, 0f, splittable = true),
                Measure(listOf(12f), spaceBefore = 0f, spaceAfter = 0f, splittable = true, keepWithNext = true),
                Measure(listOf(10f, 10f), 0f, 0f, splittable = true)
            ),
            pageHeight = 100f
        )
        assertThat(slices[1].page).isEqualTo(1)
        assertThat(slices[2].page).isEqualTo(1)
    }

    @Test fun paginateDrawsOversizedItemsInsteadOfLooping() {
        val slices = DocumentLayout.paginate(
            listOf(Measure(listOf(10f), 0f, 0f, splittable = true), Measure(listOf(150f), 0f, 0f, splittable = false)),
            pageHeight = 100f
        )
        assertThat(slices[1]).isEqualTo(Slice(1, 1, 0f, 0, 1))
    }
}
