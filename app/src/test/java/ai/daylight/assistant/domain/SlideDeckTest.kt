package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** v5.13.7: Markdown to slides for .pptx files. */
class SlideDeckTest {

    private fun titles(slides: List<SlideDeck.Slide>) = slides.map { DocumentMarkdown.plain(it.title) }

    @Test fun deckTitleThenSlideHeadings() {
        val slides = SlideDeck.plan(
            """
            # Trip to Riga
            Three days, one budget

            ## Day 1
            - Old town walk
            - Central market

            ## Day 2
            - Jurmala beach
            """.trimIndent()
        )
        assertThat(titles(slides)).containsExactly("Trip to Riga", "Day 1", "Day 2").inOrder()
        assertThat(slides[0].cover).isTrue()
        assertThat(slides[0].subtitle.map(DocumentMarkdown::plain)).containsExactly("Three days, one budget")
        assertThat(slides[1].cover).isFalse()
        assertThat(slides[1].lines.map { it.marker }).containsExactly(SlideDeck.Marker.BULLET, SlideDeck.Marker.BULLET)
        // Short slides get the largest body size.
        assertThat(slides[1].bodySize).isEqualTo(28)
    }

    @Test fun rulesSplitSlidesAndDeeperHeadingsBecomeSubheads() {
        val slides = SlideDeck.plan(
            """
            # Plan
            ### Goals
            Ship it
            ---
            # Risks
            1. Time
            2. Money
            """.trimIndent()
        )
        assertThat(titles(slides)).containsExactly("Plan", "Risks").inOrder()
        // Two slides: the first is not a cover because it has a sub-head.
        assertThat(slides[0].cover).isFalse()
        assertThat(slides[0].lines.first().style).isEqualTo(SlideDeck.Style.SUBHEAD)
        assertThat(slides[1].lines.map { it.number }).containsExactly(1, 2).inOrder()
        assertThat(slides[1].lines.map { it.marker }).containsExactly(SlideDeck.Marker.NUMBER, SlideDeck.Marker.NUMBER)
    }

    @Test fun aSingleSlideIsNeverACover() {
        val slides = SlideDeck.plan("# Hello\nJust one slide")
        assertThat(slides).hasSize(1)
        assertThat(slides[0].cover).isFalse()
    }

    @Test fun nestedBulletsKeepTheirLevel() {
        val slides = SlideDeck.plan("# A\n- one\n  - one point five\n- two")
        assertThat(slides.single().lines.map { it.level }).containsExactly(0, 1, 0).inOrder()
    }

    @Test fun longSlidesShrinkThenContinue() {
        val bullets = (1..10).joinToString("\n") { "- Point $it with a sentence that runs long enough to wrap onto a second line on the slide" }
        val shrunk = SlideDeck.plan("# Many\n$bullets").single()
        assertThat(shrunk.bodySize).isLessThan(28)
        assertThat(shrunk.bodyHeight).isAtMost(SlideDeck.BODY_H)

        val many = (1..40).joinToString("\n") { "- Point $it with a sentence that runs long enough to wrap onto a second line on the slide" }
        val split = SlideDeck.plan("# Many\n$many")
        assertThat(split.size).isAtLeast(2)
        assertThat(DocumentMarkdown.plain(split[1].title)).isEqualTo("Many (cont.)")
        assertThat(split.sumOf { it.lines.size }).isEqualTo(40)
        split.forEach { assertThat(it.bodyHeight).isAtMost(SlideDeck.BODY_H) }
        // Continuation slides share one text size.
        assertThat(split.map { it.bodySize }.distinct()).hasSize(1)
    }

    @Test fun tablesSitUnderTheirTextAndNumbersAlignRight() {
        val slide = SlideDeck.plan(
            """
            # Budget
            Spending by month
            | Month | Cost |
            |---|---|
            | Jan | 1,200 |
            | Feb | 980 |
            """.trimIndent()
        ).single()
        assertThat(slide.lines).hasSize(1)
        val table = slide.table!!
        assertThat(table.rows).hasSize(2)
        assertThat(table.numeric).containsExactly(false, true).inOrder()
        assertThat(table.columnWidths.sum()).isWithin(0.01).of(SlideDeck.CONTENT_W)
        assertThat(slide.bodyHeight + SlideDeck.GAP + table.height).isAtMost(SlideDeck.BODY_H)
    }

    @Test fun bigTablesRepeatTheirHeaderOnContinuationSlides() {
        val rows = (1..60).joinToString("\n") { "| Item $it | ${it * 3} |" }
        val slides = SlideDeck.plan("# Stock\n| Item | Count |\n|---|---|\n$rows")
        assertThat(slides.size).isAtLeast(2)
        assertThat(slides.sumOf { it.table!!.rows.size }).isEqualTo(60)
        assertThat(slides.map { it.table!!.size }.distinct()).hasSize(1)
        slides.forEach {
            assertThat(it.table!!.header.map(DocumentMarkdown::plain)).containsExactly("Item", "Count").inOrder()
            assertThat(it.table!!.height).isAtMost(SlideDeck.BODY_H)
        }
    }

    @Test fun textAfterATableMovesToTheNextSlide() {
        val slides = SlideDeck.plan("# T\n| A | B |\n|---|---|\n| 1 | 2 |\n\nAfter the table")
        assertThat(slides).hasSize(2)
        assertThat(slides[0].table).isNotNull()
        assertThat(slides[1].lines.map { DocumentMarkdown.plain(it.runs) }).containsExactly("After the table")
        assertThat(DocumentMarkdown.plain(slides[1].title)).isEqualTo("T (cont.)")
    }

    @Test fun emptyInputStillMakesADeck() {
        assertThat(SlideDeck.plan("---")).hasSize(1)
    }

    @Test fun longTitlesGetSmaller() {
        assertThat(SlideDeck.titleSize(listOf(DocumentMarkdown.Run("Short")), cover = false)).isEqualTo(32)
        val long = DocumentMarkdown.Run("A much longer slide title that keeps going and going past one line")
        assertThat(SlideDeck.titleSize(listOf(long), cover = false)).isLessThan(32)
    }
}
