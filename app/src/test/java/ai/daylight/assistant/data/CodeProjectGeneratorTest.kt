package ai.daylight.assistant.data

import ai.daylight.assistant.data.remote.CodeProjectFile
import com.google.common.truth.Truth.assertThat
import java.util.zip.ZipFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CodeProjectGeneratorTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun writesVerifiedPortableProjectZip() {
        val destination = temporary.newFile("project.zip")
        CodeProjectGenerator.write(
            destination,
            listOf(
                CodeProjectFile("README.md", "# App\nRun it locally."),
                CodeProjectFile("frontend/src/App.tsx", "export const App = () => <main>Hello</main>"),
                CodeProjectFile("backend/server.ts", "export const port = 3000")
            )
        )
        ZipFile(destination).use { zip ->
            assertThat(zip.getEntry("README.md")).isNotNull()
            assertThat(zip.getEntry("frontend/src/App.tsx")).isNotNull()
            assertThat(zip.size()).isEqualTo(3)
        }
    }

    @Test fun rejectsTraversalAndProjectsWithoutReadme() {
        val traversal = runCatching {
            CodeProjectGenerator.validate(listOf(CodeProjectFile("../secret", "x"), CodeProjectFile("README.md", "read")))
        }
        val noReadme = runCatching {
            CodeProjectGenerator.validate(listOf(CodeProjectFile("src/a.kt", "a"), CodeProjectFile("src/b.kt", "b")))
        }
        assertThat(traversal.isFailure).isTrue()
        assertThat(noReadme.isFailure).isTrue()
    }
}
