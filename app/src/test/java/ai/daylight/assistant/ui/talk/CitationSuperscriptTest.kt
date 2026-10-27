package ai.daylight.assistant.ui.talk

import ai.daylight.assistant.ui.STREAMING_CARET
import ai.daylight.assistant.ui.citedNumbers
import ai.daylight.assistant.ui.richAnnotatedMarkdown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CitationSuperscriptTest {
    private val link = Color.White
    private val accent = Color(0xFFFFC700)
    private val urls = listOf("https://fonts.google.com", "https://github.com/x", "https://fontsource.org")

    @Test fun bracketNumbersBecomeYellowSuperscriptsThatOpenTheirSource() {
        val text = richAnnotatedMarkdown("Full lowercase [1], OFL [3].", link, citationUrls = urls, accent = accent)
        // The space before a converted mark is dropped so the numeral sits on the word.
        assertThat(text.text).isEqualTo("Full lowercase1, OFL3.")
        val sups = text.spanStyles.filter { it.item.baselineShift == BaselineShift.Superscript }
        assertThat(sups.map { text.text.substring(it.start, it.end) }).containsExactly("1", "3").inOrder()
        sups.forEach { assertThat(it.item.color).isEqualTo(accent) }
        val annotations = text.getStringAnnotations("URL", 0, text.length)
        assertThat(annotations.map { it.item }).containsExactly(urls[0], urls[2]).inOrder()
        assertThat(annotations.map { text.text.substring(it.start, it.end) }).containsExactly("1", "3").inOrder()
    }

    @Test fun groupedNumbersEachGetTheirOwnLink() {
        val text = richAnnotatedMarkdown("Both [1, 2] agree.", link, citationUrls = urls, accent = accent)
        assertThat(text.text).isEqualTo("Both1,2 agree.")
        assertThat(text.getStringAnnotations("URL", 0, text.length).map { it.item })
            .containsExactly(urls[0], urls[1]).inOrder()
    }

    @Test fun withoutCitationsOrOutOfRangeTheBracketStaysPlain() {
        assertThat(richAnnotatedMarkdown("See [1].", link).text).isEqualTo("See [1].")
        val outOfRange = richAnnotatedMarkdown("See [7].", link, citationUrls = urls, accent = accent)
        assertThat(outOfRange.text).isEqualTo("See [7].")
        assertThat(outOfRange.getStringAnnotations("URL", 0, outOfRange.length)).isEmpty()
        assertThat(richAnnotatedMarkdown("See [0].", link, citationUrls = urls).text).isEqualTo("See [0].")
    }

    @Test fun markdownLinksAreNotMistakenForCitations() {
        val text = richAnnotatedMarkdown("Read [2](https://example.com) now", link, citationUrls = urls, accent = accent)
        assertThat(text.text).isEqualTo("Read 2 now")
        val url = text.getStringAnnotations("URL", 0, text.length).single()
        assertThat(url.item).isEqualTo("https://example.com")
        assertThat(text.spanStyles.single { it.item.textDecoration == TextDecoration.Underline }.item.color).isEqualTo(link)
        assertThat(text.spanStyles.none { it.item.baselineShift == BaselineShift.Superscript }).isTrue()
    }

    @Test fun otherInlineMarkdownStillWorksNextToCitations() {
        val text = richAnnotatedMarkdown("**Bold** claim [2] and `code`", link, citationUrls = urls, accent = accent)
        assertThat(text.text).isEqualTo("Bold claim2 and code")
        // A plain (unconverted) bracket keeps its space.
        assertThat(richAnnotatedMarkdown("See [1].", link).text).isEqualTo("See [1].")
    }

    @Test fun citedNumbersReadsEveryMarkButNotLinks() {
        assertThat(citedNumbers("a [1] b [2, 4] c [2] d [9](https://x.y)")).containsExactly(1, 2, 4)
        assertThat(citedNumbers("no citations here")).isEmpty()
    }

    @Test fun streamingCaretBecomesAYellowBlock() {
        val text = richAnnotatedMarkdown("Font License$STREAMING_CARET", link, accent = accent)
        assertThat(text.text).doesNotContain(STREAMING_CARET)
        assertThat(text.text).startsWith("Font License")
        val block = text.spanStyles.single { it.item.background == accent }
        assertThat(text.text.substring(block.start, block.end).isNotEmpty()).isTrue()
        assertThat(text.text.substring(block.start, block.end).all { it == ' ' }).isTrue()
    }

    @Test fun textWithoutTheCaretHasNoBlock() {
        val text = richAnnotatedMarkdown("Done.", link, accent = accent)
        assertThat(text.spanStyles.none { it.item.background == accent }).isTrue()
    }
}
