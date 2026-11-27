package ai.daylight.assistant.ui.run

import ai.daylight.assistant.R
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FileCardModelTest {
    private fun output(fileName: String, kind: OutputKind, localPath: String? = "/x", content: String? = null, remote: String? = null) =
        GeneratedOutput("id", kind, "Title", fileName, "application/octet-stream", content = content, localPath = localPath, remoteUrl = remote)

    @Test fun theCaptionIsTheTypeAndTheSize() {
        val docx = output("summary.docx", OutputKind.DOCUMENT)
        assertThat(FileCardModel.extension(docx)).isEqualTo("DOCX")
        assertThat(FileCardModel.caption(docx, 24_576)).isEqualTo("DOCX · 24 KB")
        assertThat(FileCardModel.caption(docx, null)).isEqualTo("DOCX")
    }

    @Test fun sizes() {
        assertThat(FileCardModel.sizeLabel(512)).isEqualTo("512 B")
        assertThat(FileCardModel.sizeLabel(1_258_291)).isEqualTo("1.2 MB")
    }

    @Test fun aNameWithoutAnExtensionFallsBackToTheKind() {
        assertThat(FileCardModel.extension(output("kryzz-image", OutputKind.IMAGE))).isEqualTo("IMAGE")
    }

    @Test fun textOnlyResultsCannotBeOpenedOnlySaved() {
        assertThat(FileCardModel.canOpen(output("app.zip", OutputKind.CODE, localPath = null, content = "x"))).isFalse()
        assertThat(FileCardModel.canOpen(output("clip.mp4", OutputKind.VIDEO, localPath = null, remote = "https://v"))).isTrue()
        assertThat(FileCardModel.canOpen(output("a.pdf", OutputKind.PDF))).isTrue()
    }

    @Test fun newFileTypesGetTheirIcons() {
        // v5.13.7: slides, web pages, code, SVG and zips made from Talk.
        assertThat(FileCardModel.icon(output("pitch.pptx", OutputKind.PRESENTATION))).isEqualTo(R.drawable.ic_kz_file_pptx_dot)
        assertThat(FileCardModel.icon(output("index.html", OutputKind.TEXT))).isEqualTo(R.drawable.ic_kz_file_code_dot)
        assertThat(FileCardModel.icon(output("tool.py", OutputKind.TEXT))).isEqualTo(R.drawable.ic_kz_file_code_dot)
        assertThat(FileCardModel.icon(output("logo.svg", OutputKind.TEXT))).isEqualTo(R.drawable.ic_kz_image_dot)
        assertThat(FileCardModel.icon(output("site.zip", OutputKind.ARCHIVE))).isEqualTo(R.drawable.ic_kz_file_code_dot)
        assertThat(FileCardModel.icon(output("contacts.vcf", OutputKind.TEXT))).isEqualTo(R.drawable.ic_kz_file_dot)
        assertThat(FileCardModel.icon(output("Makefile", OutputKind.TEXT))).isEqualTo(R.drawable.ic_kz_file_dot)
    }

    @Test fun textFilesCanFallBackToAPlainTextViewer() {
        val python = GeneratedOutput("p", OutputKind.TEXT, "Tool", "tool.py", "text/x-python", localPath = "/x")
        val yaml = GeneratedOutput("y", OutputKind.TEXT, "Config", "c.yaml", "application/yaml", localPath = "/x")
        val plain = GeneratedOutput("t", OutputKind.TEXT, "Notes", "n.txt", "text/plain", localPath = "/x")
        val pdf = GeneratedOutput("f", OutputKind.PDF, "Doc", "d.pdf", "application/pdf", localPath = "/x")
        assertThat(FileCardModel.opensAsText(python)).isTrue()
        assertThat(FileCardModel.opensAsText(yaml)).isTrue()
        assertThat(FileCardModel.opensAsText(plain)).isFalse() // already plain text
        assertThat(FileCardModel.opensAsText(pdf)).isFalse()
        assertThat(FileCardModel.noViewerMessage(pdf)).contains("PDF files")
    }

    @Test fun iconsFollowTheRealFileType() {
        assertThat(FileCardModel.icon(output("summary.docx", OutputKind.DOCUMENT))).isEqualTo(R.drawable.ic_kz_file_docx_dot)
        assertThat(FileCardModel.icon(output("costs.xlsx", OutputKind.SPREADSHEET))).isEqualTo(R.drawable.ic_kz_file_xlsx_dot)
        // A document that fell back to Markdown is a plain file, not a Word file.
        assertThat(FileCardModel.icon(output("summary.md", OutputKind.DOCUMENT))).isEqualTo(R.drawable.ic_kz_file_dot)
        assertThat(FileCardModel.icon(output("kryzz-image.png", OutputKind.IMAGE))).isEqualTo(R.drawable.ic_kz_image_dot)
        assertThat(FileCardModel.icon(output("data.sqlite", OutputKind.DATABASE))).isEqualTo(R.drawable.ic_kz_grid_dot)
        assertThat(FileCardModel.icon(output("dentist.ics", OutputKind.CALENDAR))).isEqualTo(R.drawable.ic_kz_file_dot)
        assertThat(FileCardModel.savesToGallery(output("dentist.ics", OutputKind.CALENDAR))).isFalse()
    }
}
