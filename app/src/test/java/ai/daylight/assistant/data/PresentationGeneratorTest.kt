package ai.daylight.assistant.data

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * v5.13.7: the .pptx package is complete and consistent: every part is well-formed XML,
 * every slide is registered in the content types and the presentation, and every internal
 * relationship points at a part that exists. Set KRYZZ_PPTX_OUT to a folder to also write
 * the sample deck there for a look in PowerPoint.
 */
class PresentationGeneratorTest {

    private val sample = """
        # Kryzz field report
        What we learned in October

        ## Highlights
        - Files now work in **Talk**: PDF, Word, Excel and *PowerPoint*
        - Any text format is saved as written, see [the docs](https://example.com/docs)
          - Sub-point with `inline code`
        - Fewer "I can't" replies

        ## Numbers
        Monthly totals
        | Month | Chats | Files |
        |---|---|---|
        | August | 1,204 | 88 |
        | September | 1,530 | 141 |
        | October | 1,911 | 260 |

        ## Next steps
        1. Ship 5.13.7
        2. Watch the error log
        3. Ask testers about slides

        > Small steps, every week.

        ```
        ./gradlew assembleDebug
        ```
    """.trimIndent()

    /** Crowded slides: long bullets, a long title, a wide table and a long table. */
    private val dense = buildString {
        append("## A very long slide title that goes on and on to test how titles wrap onto a second line\n")
        (1..14).forEach { append("- Bullet $it explains a detail at length so that it wraps across at least two lines of the slide body text\n") }
        append("\n## Wide table\n| Region | Q1 revenue | Q2 revenue | Q3 revenue | Q4 revenue | Notes on the year |\n|---|---|---|---|---|---|\n")
        (1..8).forEach { append("| Region $it | ${it * 1000} | ${it * 1100} | ${it * 1200} | ${it * 1300} | Growth driven by new stores and online orders |\n") }
        append("\n## Long table\n| Item | Count |\n|---|---|\n")
        (1..45).forEach { append("| Item $it | ${it * 7} |\n") }
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> parts[entry.name] = zip.readBytes() }
        }
        return parts
    }

    private fun parse(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    @Test fun packageIsWellFormedAndConsistent() {
        val bytes = PresentationGenerator.presentation(sample, "Field report")
        System.getenv("KRYZZ_PPTX_OUT")?.let { dir ->
            File(dir).apply { mkdirs() }.resolve("sample.pptx").writeBytes(bytes)
            File(dir).resolve("dense.pptx").writeBytes(PresentationGenerator.presentation(dense, "Dense"))
        }
        val parts = unzip(bytes)
        assertThat(parts.keys.first()).isEqualTo("[Content_Types].xml")
        parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (name, content) ->
            runCatching { parse(content) }.onFailure { throw AssertionError("$name is not well-formed XML", it) }
        }

        val slides = parts.keys.filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it) }
        assertThat(slides).hasSize(4)
        val types = String(parts.getValue("[Content_Types].xml"), Charsets.UTF_8)
        slides.forEach { assertThat(types).contains("PartName=\"/$it\"") }
        val presentation = String(parts.getValue("ppt/presentation.xml"), Charsets.UTF_8)
        assertThat(Regex("<p:sldId ").findAll(presentation).count()).isEqualTo(4)

        // Every internal relationship target exists.
        parts.filterKeys { it.endsWith(".rels") }.forEach { (relsName, content) ->
            val base = relsName.substringBefore("_rels/")
            val relationships = parse(content).getElementsByTagName("Relationship")
            for (index in 0 until relationships.length) {
                val rel = relationships.item(index) as Element
                if (rel.getAttribute("TargetMode") == "External") continue
                val target = File(base + rel.getAttribute("Target")).normalize().path.replace('\\', '/')
                assertThat(parts.keys).contains(target)
            }
        }
    }

    @Test fun slidesCarryTheTextFormattingAndLinks() {
        val parts = unzip(PresentationGenerator.presentation(sample))
        val cover = String(parts.getValue("ppt/slides/slide1.xml"), Charsets.UTF_8)
        assertThat(cover).contains("Kryzz field report")
        assertThat(cover).contains("type=\"ctrTitle\"")
        assertThat(String(parts.getValue("ppt/slides/_rels/slide1.xml.rels"), Charsets.UTF_8)).contains("slideLayout1.xml")

        val highlights = String(parts.getValue("ppt/slides/slide2.xml"), Charsets.UTF_8)
        assertThat(highlights).contains("<a:buChar char=\"\u2022\"/>")
        assertThat(highlights).contains("<a:buChar char=\"\u2013\"/>")
        assertThat(highlights).contains("b=\"1\"")
        assertThat(highlights).contains("<a:hlinkClick r:id=\"rId2\"/>")
        assertThat(highlights).contains("typeface=\"Courier New\"")
        assertThat(highlights).contains("&quot;I can&apos;t&quot;")
        val links = String(parts.getValue("ppt/slides/_rels/slide2.xml.rels"), Charsets.UTF_8)
        assertThat(links).contains("Target=\"https://example.com/docs\" TargetMode=\"External\"")

        val numbers = String(parts.getValue("ppt/slides/slide3.xml"), Charsets.UTF_8)
        assertThat(numbers).contains("<a:tbl>")
        assertThat(Regex("<a:gridCol ").findAll(numbers).count()).isEqualTo(3)
        assertThat(Regex("<a:tr ").findAll(numbers).count()).isEqualTo(4)
        assertThat(numbers).contains("algn=\"r\"")

        val next = String(parts.getValue("ppt/slides/slide4.xml"), Charsets.UTF_8)
        assertThat(next).contains("<a:buAutoNum type=\"arabicPeriod\"/>")
        assertThat(next).contains("type=\"slidenum\"")
    }

    @Test fun numberedListsContinuedOnANewSlideKeepTheirNumbers() {
        val items = (1..40).joinToString("\n") { "$it. Step number $it with enough words to need a second line on the slide" }
        val parts = unzip(PresentationGenerator.presentation("# Steps\n$items"))
        val second = String(parts.getValue("ppt/slides/slide2.xml"), Charsets.UTF_8)
        assertThat(second).contains("Steps (cont.)")
        assertThat(second).containsMatch("startAt=\"\\d+\"")
    }
}
