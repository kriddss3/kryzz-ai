package ai.daylight.assistant.domain

import ai.daylight.assistant.domain.DocumentMarkdown.Run
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentMarkdownTest {
    @Test fun parsesHeadingsParagraphsAndRules() {
        val blocks = DocumentMarkdown.parse("# Title\n## Section ##\n#### Deep\nFirst line\nsecond line\n\n---\nAfter")
        assertThat(blocks).containsExactly(
            DocumentMarkdown.Heading(1, listOf(Run("Title"))),
            DocumentMarkdown.Heading(2, listOf(Run("Section"))),
            DocumentMarkdown.Heading(3, listOf(Run("Deep"))),
            DocumentMarkdown.Paragraph(listOf(Run("First line\nsecond line"))),
            DocumentMarkdown.Rule,
            DocumentMarkdown.Paragraph(listOf(Run("After")))
        ).inOrder()
    }

    @Test fun headingKeepsHashInsideText() {
        val heading = DocumentMarkdown.parse("## Learning C#").single() as DocumentMarkdown.Heading
        assertThat(DocumentMarkdown.plain(heading.runs)).isEqualTo("Learning C#")
    }

    @Test fun parsesInlineStyles() {
        assertThat(DocumentMarkdown.inline("Plain **bold** and *italic* with `code` and ~~old~~")).containsExactly(
            Run("Plain "), Run("bold", bold = true), Run(" and "), Run("italic", italic = true),
            Run(" with "), Run("code", code = true), Run(" and "), Run("old", strike = true)
        ).inOrder()
        assertThat(DocumentMarkdown.inline("***both*** __strong__ _em_")).containsExactly(
            Run("both", bold = true, italic = true), Run(" "), Run("strong", bold = true), Run(" "), Run("em", italic = true)
        ).inOrder()
    }

    @Test fun nestedEmphasisAndLinks() {
        assertThat(DocumentMarkdown.inline("**bold *and italic* text**")).containsExactly(
            Run("bold ", bold = true), Run("and italic", bold = true, italic = true), Run(" text", bold = true)
        ).inOrder()
        assertThat(DocumentMarkdown.inline("See [the **docs**](https://example.com/a?b=1) now")).containsExactly(
            Run("See "),
            Run("the ", link = "https://example.com/a?b=1"),
            Run("docs", bold = true, link = "https://example.com/a?b=1"),
            Run(" now")
        ).inOrder()
    }

    @Test fun bareUrlsBecomeLinksWithoutTrailingPunctuation() {
        assertThat(DocumentMarkdown.inline("Visit https://rtings.com/laptop. Then stop")).containsExactly(
            Run("Visit "), Run("https://rtings.com/laptop", link = "https://rtings.com/laptop"), Run(". Then stop")
        ).inOrder()
    }

    @Test fun literalMarkersStayText() {
        assertThat(DocumentMarkdown.plain(DocumentMarkdown.inline("2 * 3 * 4 = 24"))).isEqualTo("2 * 3 * 4 = 24")
        assertThat(DocumentMarkdown.inline("snake_case_name and file_name")).containsExactly(Run("snake_case_name and file_name"))
        assertThat(DocumentMarkdown.inline("unclosed **bold")).containsExactly(Run("unclosed **bold"))
        assertThat(DocumentMarkdown.inline("escaped \\*star\\*")).containsExactly(Run("escaped *star*"))
    }

    @Test fun parsesBulletAndNumberedListsWithOneNestingLevel() {
        val blocks = DocumentMarkdown.parse("- one\n- two\n  - two a\n    - third level\n- three\n\n3. third\n4. fourth")
        val bullets = blocks[0] as DocumentMarkdown.ListBlock
        assertThat(bullets.ordered).isFalse()
        assertThat(bullets.items.map { DocumentMarkdown.plain(it.runs) }).containsExactly("one", "two", "three").inOrder()
        assertThat(bullets.items[1].children!!.items.map { DocumentMarkdown.plain(it.runs) }).containsExactly("two a", "third level").inOrder()
        val numbers = blocks[1] as DocumentMarkdown.ListBlock
        assertThat(numbers.ordered).isTrue()
        assertThat(numbers.start).isEqualTo(3)
        assertThat(numbers.items).hasSize(2)
    }

    @Test fun looseListContinuesAcrossBlankLinesAndListTypeSwitchSplits() {
        val blocks = DocumentMarkdown.parse("1. a\n\n2. b\n- c")
        assertThat(blocks).hasSize(2)
        assertThat((blocks[0] as DocumentMarkdown.ListBlock).items).hasSize(2)
        assertThat((blocks[1] as DocumentMarkdown.ListBlock).ordered).isFalse()
    }

    @Test fun onlyListsStartingAtOneInterruptAParagraph() {
        val blocks = DocumentMarkdown.parse("Budžets tiek pārskatīts\n2026. gada sākumā.\n1. pirmais")
        assertThat(blocks).hasSize(2)
        assertThat(DocumentMarkdown.plain((blocks[0] as DocumentMarkdown.Paragraph).runs)).isEqualTo("Budžets tiek pārskatīts\n2026. gada sākumā.")
        assertThat((blocks[1] as DocumentMarkdown.ListBlock).ordered).isTrue()
    }

    @Test fun listContinuationLinesJoinTheItem() {
        val list = DocumentMarkdown.parse("- first line\n  continues here\n- [x] done").single() as DocumentMarkdown.ListBlock
        assertThat(DocumentMarkdown.plain(list.items[0].runs)).isEqualTo("first line\ncontinues here")
        assertThat(DocumentMarkdown.plain(list.items[1].runs)).isEqualTo("☑ done")
    }

    @Test fun parsesTablesWithInlineCellsAndPadsRows() {
        val table = DocumentMarkdown.parse("| Name | Score |\n|:---|---:|\n| **Ann** | 9 |\n| Bob |\n| a \\| b | 1 | extra |")
            .single() as DocumentMarkdown.Table
        assertThat(table.columnCount).isEqualTo(2)
        assertThat(table.header.map(DocumentMarkdown::plain)).containsExactly("Name", "Score").inOrder()
        assertThat(table.rows[0][0]).containsExactly(Run("Ann", bold = true))
        assertThat(table.rows[1].map(DocumentMarkdown::plain)).containsExactly("Bob", "").inOrder()
        assertThat(table.rows[2].map(DocumentMarkdown::plain)).containsExactly("a | b", "1").inOrder()
    }

    @Test fun fencedCodeIsLiteral() {
        val blocks = DocumentMarkdown.parse("```kotlin\nval x = **1**\n  indented\n```\nafter")
        assertThat(blocks[0]).isEqualTo(DocumentMarkdown.CodeBlock("kotlin", "val x = **1**\n  indented"))
        assertThat(blocks[1]).isEqualTo(DocumentMarkdown.Paragraph(listOf(Run("after"))))
        // An unclosed fence runs to the end.
        assertThat(DocumentMarkdown.parse("~~~\nopen").single()).isEqualTo(DocumentMarkdown.CodeBlock(null, "open"))
    }

    @Test fun quotesSplitIntoParagraphs() {
        val quote = DocumentMarkdown.parse("> first *line*\n> same para\n>\n> second").single() as DocumentMarkdown.Quote
        assertThat(quote.paragraphs).hasSize(2)
        assertThat(quote.paragraphs[0]).containsExactly(Run("first "), Run("line", italic = true), Run("\nsame para")).inOrder()
    }

    @Test fun keepsNonLatinText() {
        val paragraph = DocumentMarkdown.parse("Rīga, Łódź, Αθήνα, Москва, 東京").single() as DocumentMarkdown.Paragraph
        assertThat(DocumentMarkdown.plain(paragraph.runs)).isEqualTo("Rīga, Łódź, Αθήνα, Москва, 東京")
    }
}
