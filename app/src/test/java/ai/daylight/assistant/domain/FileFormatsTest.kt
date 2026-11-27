package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** v5.13.7: create_artifact picks the format from the file name, like a command line. */
class FileFormatsTest {

    @Test fun officePdfAndDataFormatsAreConverted() {
        assertThat(FileFormats.formatFor("pdf")).isEqualTo(FileFormats.PDF)
        assertThat(FileFormats.formatFor("docx")).isEqualTo(FileFormats.DOCX)
        assertThat(FileFormats.formatFor("xlsx")).isEqualTo(FileFormats.XLSX)
        assertThat(FileFormats.formatFor("pptx")).isEqualTo(FileFormats.PPTX)
        assertThat(FileFormats.formatFor("sqlite")).isEqualTo(FileFormats.SQLITE)
        assertThat(FileFormats.formatFor("ics")).isEqualTo(FileFormats.ICS)
        assertThat(FileFormats.formatFor("zip")).isEqualTo(FileFormats.Zip)
    }

    @Test fun anyOtherExtensionIsWrittenAsText() {
        assertThat(FileFormats.formatFor("html")).isEqualTo(FileFormats.Text("html", "text/html"))
        assertThat(FileFormats.formatFor("json")).isEqualTo(FileFormats.Text("json", "application/json"))
        assertThat(FileFormats.formatFor("svg")).isEqualTo(FileFormats.Text("svg", "image/svg+xml"))
        assertThat(FileFormats.formatFor("vcf")).isEqualTo(FileFormats.Text("vcf", "text/x-vcard"))
        // Unknown text formats still work; they open as plain text.
        assertThat(FileFormats.formatFor("rs")).isEqualTo(FileFormats.Text("rs", "text/plain"))
        assertThat(FileFormats.formatFor("")).isEqualTo(FileFormats.Text("", "text/plain"))
    }

    @Test fun binaryFormatsAreRefusedWithAnAlternative() {
        assertThat(FileFormats.formatFor("png")).isNull()
        assertThat(FileFormats.refusal("png")).contains("generate_image")
        assertThat(FileFormats.refusal("png")).contains(".svg")
        assertThat(FileFormats.refusal("mp3")).contains("generate_audio")
        assertThat(FileFormats.refusal("mp4")).contains("generate_video")
        assertThat(FileFormats.refusal("rar")).contains(".zip")
        assertThat(FileFormats.refusal("odp")).contains(".pptx")
        assertThat(FileFormats.refusal("exe")).contains("source")
        assertThat(FileFormats.refusal("html")).isNull()
    }

    @Test fun fileNamesAreMadeSafe() {
        assertThat(FileFormats.cleanFileName("../../etc/Trip Plan.PDF", null)).isEqualTo("Trip Plan.pdf")
        assertThat(FileFormats.cleanFileName("report.doc", null)).isEqualTo("report.docx")
        assertThat(FileFormats.cleanFileName("a:b*c?.txt", null)).isEqualTo("a-b-c.txt")
        assertThat(FileFormats.cleanFileName(null, "Q3 Budget", "xlsx")).isEqualTo("Q3 Budget.xlsx")
        assertThat(FileFormats.cleanFileName("   ", null, "md")).isEqualTo("kryzz-file.md")
        assertThat(FileFormats.cleanFileName(".env", null)).isEqualTo(".env")
        assertThat(FileFormats.cleanFileName("budžets.xlsx", null)).isEqualTo("budžets.xlsx")
    }

    @Test fun requestReadsTheFileNameForm() {
        val request = FileFormats.request("""{"file_name":"trip.pdf","title":"Trip","content":"# Day 1\n- Fly"}""")
        assertThat(request.fileName).isEqualTo("trip.pdf")
        assertThat(request.title).isEqualTo("Trip")
        assertThat(request.format).isEqualTo(FileFormats.PDF)
        assertThat(request.kind).isEqualTo(OutputKind.PDF)
        assertThat(request.content).isEqualTo("# Day 1\n- Fly")
    }

    @Test fun textFilesLoseAWrappingFenceAndKeepTheirName() {
        val request = FileFormats.request("""{"file_name":"index.html","content":"```html\n<h1>Hi</h1>\n```"}""")
        assertThat(request.kind).isEqualTo(OutputKind.TEXT)
        assertThat(request.content).isEqualTo("<h1>Hi</h1>")
        assertThat(request.title).isEqualTo("index")
    }

    @Test fun csvFromAMarkdownTableBecomesRealCsv() {
        val request = FileFormats.request("""{"file_name":"a.csv","content":"| A | B |\n|---|---|\n| 1 | 2 |"}""")
        assertThat(request.content).isEqualTo("A,B\n1,2")
    }

    @Test fun oldTypeFormStillLands() {
        val request = FileFormats.request("""{"type":"presentation","title":"Mars","content":"# Mars"}""")
        assertThat(request.fileName).isEqualTo("Mars.pptx")
        assertThat(request.kind).isEqualTo(OutputKind.PRESENTATION)
        val calendar = FileFormats.request("""{"type":"calendar","title":"Dentist","content":"BEGIN:VEVENT\nSUMMARY:Dentist\nDTSTART:20261103T090000Z\nEND:VEVENT"}""")
        assertThat(calendar.fileName).isEqualTo("Dentist.ics")
        assertThat(calendar.content).startsWith("BEGIN:VCALENDAR")
    }

    @Test fun extensionlessNamesStayExtensionless() {
        assertThat(FileFormats.request("""{"file_name":"Makefile","content":"all:\n\techo hi"}""").fileName).isEqualTo("Makefile")
        assertThat(FileFormats.request("""{"file_name":"notes","content":"hello"}""").fileName).isEqualTo("notes.txt")
    }

    @Test fun badRequestsExplainWhatToFix() {
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"logo.png","content":"x"}""")
        }).hasMessageThat().contains("generate_image")
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"empty.txt","content":"  "}""")
        }).hasMessageThat().contains("no content")
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"chart.svg","content":"a bar chart"}""")
        }).hasMessageThat().contains("<svg>")
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"party.ics","content":"Party on Friday"}""")
        }).hasMessageThat().contains("BEGIN:VCALENDAR")
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"shop.sqlite","content":"SELECT 1;"}""")
        }).hasMessageThat().contains("CREATE TABLE")
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("not json")
        }).hasMessageThat().contains("file_name")
    }

    @Test fun zipEntriesFollowTheSameRules() {
        val request = FileFormats.request(
            """{"file_name":"site.zip","content":"A small site","files":[
                {"path":"index.html","content":"<p>Hi</p>"},
                {"path":"docs/guide.pdf","content":"# Guide"},
                {"path":"data\\prices.xlsx","content":"item,price\nTea,2"}
            ]}"""
        )
        assertThat(request.format).isEqualTo(FileFormats.Zip)
        assertThat(request.kind).isEqualTo(OutputKind.ARCHIVE)
        assertThat(request.entries.map { it.path }).containsExactly("index.html", "docs/guide.pdf", "data/prices.xlsx").inOrder()
        assertThat(request.entries.map { it.format }).containsExactly(
            FileFormats.Text("html", "text/html"), FileFormats.PDF, FileFormats.XLSX
        ).inOrder()
        // Files alone imply a zip.
        assertThat(FileFormats.request("""{"title":"Bundle","files":[{"path":"a.txt","content":"x"}]}""").fileName).isEqualTo("Bundle.zip")
    }

    @Test fun zipEntriesCannotEscapeOrRepeat() {
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"x.zip","content":"","files":[{"path":"../evil.sh","content":"x"}]}""")
        }).hasMessageThat().contains("cannot leave")
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"x.zip","content":"","files":[{"path":"a.txt","content":"1"},{"path":"A.txt","content":"2"}]}""")
        }).hasMessageThat().contains("unique")
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"x.zip","content":"","files":[{"path":"pic.jpg","content":"x"}]}""")
        }).hasMessageThat().contains("pic.jpg")
        assertThat(assertThrows(IllegalArgumentException::class.java) {
            FileFormats.request("""{"file_name":"x.zip","content":"no files"}""")
        }).hasMessageThat().contains("files list")
    }

    @Test fun jsonThatDoesNotParseGetsAWarning() {
        val format = FileFormats.Text("json", "application/json")
        assertThat(FileFormats.warning(format, """{"a": 1}""")).isNull()
        assertThat(FileFormats.warning(format, """{"a": 1,}""")).contains("not valid JSON")
        assertThat(FileFormats.warning(FileFormats.Text("txt", "text/plain"), "{")).isNull()
    }

    @Test fun bytesUseTheLineEndingsAndMarksTheFormatNeeds() {
        val vcf = FileFormats.bytesOf(FileFormats.Text("vcf", "text/x-vcard"), "BEGIN:VCARD\nEND:VCARD")
        assertThat(String(vcf, Charsets.UTF_8)).isEqualTo("BEGIN:VCARD\r\nEND:VCARD")
        val csv = FileFormats.bytesOf(FileFormats.Text("csv", "text/csv"), "name\nJānis")
        assertThat(csv.take(3).map { it.toInt() and 0xFF }).containsExactly(0xEF, 0xBB, 0xBF).inOrder()
        val ascii = FileFormats.bytesOf(FileFormats.Text("csv", "text/csv"), "name\nAnna")
        assertThat(String(ascii, Charsets.UTF_8)).isEqualTo("name\nAnna")
    }
}
