package ai.daylight.assistant.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichMarkdownTest {
    @Test
    fun blockParserRecognizesHeadingsAndMixedLists() {
        val blocks = parseMarkdownBlocks(
            """
            # Mission control
            ### Flight notes

            - Calibrate sensors
            * Confirm telemetry
            1. Start countdown
            2) Launch
            """.trimIndent()
        )

        assertThat(blocks).containsExactly(
            MarkdownBlock.Heading(1, "Mission control"),
            MarkdownBlock.Heading(3, "Flight notes"),
            MarkdownBlock.ListItems(
                listOf(
                    "•" to "Calibrate sensors",
                    "•" to "Confirm telemetry",
                    "1." to "Start countdown",
                    "2." to "Launch"
                )
            )
        ).inOrder()
    }

    @Test
    fun blockParserRecognizesMultilineQuoteAndTable() {
        val blocks = parseMarkdownBlocks(
            """
            > Stay curious.
            > Verify the signal.

            | Star | Status |
            | :--- | ---: |
            | Vega | Ready |
            | Sol | Visible |
            """.trimIndent()
        )

        assertThat(blocks).containsExactly(
            MarkdownBlock.Quote("Stay curious.\nVerify the signal."),
            MarkdownBlock.Table(
                header = listOf("Star", "Status"),
                rows = listOf(
                    listOf("Vega", "Ready"),
                    listOf("Sol", "Visible")
                )
            )
        ).inOrder()
    }

    @Test
    fun inlineParserAppliesStylesAndUrlAnnotation() {
        val linkColor = Color(0xFFE6E6E6)
        val annotated = richAnnotatedMarkdown(
            "**Bold** __Strong__ *italic* ~~retired~~ `code` [Kryzz](https://kryzz.example)",
            linkColor
        )

        assertThat(annotated.text).isEqualTo("Bold Strong italic retired code Kryzz")

        val boldText = annotated.spanStyles
            .filter { it.item.fontWeight == FontWeight.Bold }
            .map { annotated.text.substring(it.start, it.end) }
        assertThat(boldText).containsExactly("Bold", "Strong").inOrder()

        val italic = annotated.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertThat(annotated.text.substring(italic.start, italic.end)).isEqualTo("italic")

        val struck = annotated.spanStyles.single { it.item.textDecoration == TextDecoration.LineThrough }
        assertThat(annotated.text.substring(struck.start, struck.end)).isEqualTo("retired")

        val code = annotated.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertThat(annotated.text.substring(code.start, code.end)).isEqualTo("code")

        val url = annotated.getStringAnnotations("URL", 0, annotated.length).single()
        assertThat(url.item).isEqualTo("https://kryzz.example")
        assertThat(annotated.text.substring(url.start, url.end)).isEqualTo("Kryzz")
        val linkStyle = annotated.spanStyles.single {
            it.start == url.start && it.end == url.end && it.item.textDecoration == TextDecoration.Underline
        }
        assertThat(linkStyle.item.color).isEqualTo(linkColor)
    }
}
