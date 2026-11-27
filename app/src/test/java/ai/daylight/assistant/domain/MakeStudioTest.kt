package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ErrorKind
import com.google.common.truth.Truth.assertThat
import java.net.ConnectException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** C7: Make's renders run outside the screen, one at a time, and say how they ended. */
@OptIn(ExperimentalCoroutinesApi::class)
class MakeStudioTest {
    private class Call(val conversationId: String, val prompt: String, val kind: MakeKind, val options: MediaOptions)

    private fun studio(
        scope: TestScope,
        calls: MutableList<Call>,
        gate: CompletableDeferred<Unit>? = null,
        failWith: Throwable? = null,
    ): MakeStudio {
        var created = 0
        return MakeStudio(
            scope = scope.backgroundScope,
            newConversation = { "new-${++created}" },
            render = { id, prompt, kind, options ->
                calls += Call(id, prompt, kind, options)
                gate?.await()
                failWith?.let { throw it }
            },
            clock = { 1_000L }
        )
    }

    @Test
    fun aRenderRunsInANewConversationWithTheChosenRatio() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val gate = CompletableDeferred<Unit>()
        val make = studio(this, calls, gate)

        assertThat(make.start(MakeKind.IMAGE, "  a lantern  ", "3:4", "image-01")).isTrue()
        // Running from the first frame, before the conversation even exists.
        assertThat(make.current.value?.running).isTrue()
        assertThat(make.current.value?.prompt).isEqualTo("a lantern")
        runCurrent()
        assertThat(make.current.value?.conversationId).isEqualTo("new-1")
        // A second render waits for the first.
        assertThat(make.start(MakeKind.VIDEO, "a ferry", null, "MiniMax-H3")).isFalse()

        gate.complete(Unit)
        runCurrent()
        val done = make.current.value!!
        assertThat(done.outcome).isEqualTo(MakeOutcome.DONE)
        assertThat(done.finishedAt).isEqualTo(1_000L)
        assertThat(calls.single().conversationId).isEqualTo("new-1")
        assertThat(calls.single().kind).isEqualTo(MakeKind.IMAGE)
        assertThat(calls.single().options.aspectRatio).isEqualTo("3:4")
    }

    @Test
    fun variantKeepsTheResultsConversation() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val make = studio(this, calls)
        assertThat(make.start(MakeKind.VIDEO, "a ferry", null, "MiniMax-H3", conversationId = "thread-7")).isTrue()
        runCurrent()
        assertThat(calls.single().conversationId).isEqualTo("thread-7")
        assertThat(calls.single().options.aspectRatio).isNull()
        assertThat(make.current.value?.outcome).isEqualTo(MakeOutcome.DONE)
    }

    @Test
    fun failuresKeepTheProvidersMessage() = runTest(StandardTestDispatcher()) {
        val make = studio(
            this, mutableListOf(),
            failWith = AssistantApiException(ErrorKind.INVALID_KEY, "Add a MiniMax API key in Settings before generating an image.")
        )
        make.start(MakeKind.IMAGE, "a lantern", null, "image-01")
        runCurrent()
        assertThat(make.current.value?.outcome).isEqualTo(MakeOutcome.FAILED)
        assertThat(make.current.value?.error).isEqualTo("Add a MiniMax API key in Settings before generating an image.")
        // The next render can start.
        assertThat(make.start(MakeKind.IMAGE, "again", null, "image-01")).isTrue()
    }

    @Test
    fun aDroppedConnectionIsSaidInPlainWords() = runTest(StandardTestDispatcher()) {
        val make = studio(this, mutableListOf(), failWith = ConnectException("Failed to connect to /10.255.255.1:8080"))
        make.start(MakeKind.IMAGE, "a lantern", null, "image-01")
        runCurrent()
        assertThat(make.current.value?.error).isEqualTo("Couldn't reach the provider. Check your connection and try again.")
    }

    @Test
    fun cancelStopsTheRender() = runTest(StandardTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val make = studio(this, mutableListOf(), gate)
        make.start(MakeKind.VIDEO, "a ferry", "16:9", "MiniMax-H3")
        runCurrent()
        make.cancel()
        runCurrent()
        assertThat(make.current.value?.outcome).isEqualTo(MakeOutcome.STOPPED)
        assertThat(make.current.value?.running).isFalse()
    }

    @Test
    fun blankPromptsDoNotStart() = runTest(StandardTestDispatcher()) {
        val make = studio(this, mutableListOf())
        assertThat(make.start(MakeKind.IMAGE, "   ", null, "image-01")).isFalse()
        assertThat(make.current.value).isNull()
    }

    @Test
    fun attachedMediaGoesWithTheRender() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val make = studio(this, calls)
        val photo = MakeSource("a", "cat.jpg", "image/jpeg", "/tmp/cat.jpg")
        assertThat(make.start(MakeKind.VIDEO, "she turns", "16:9", "MiniMax-H3", sources = listOf(photo))).isTrue()
        runCurrent()
        assertThat(calls.single().options.sources).containsExactly(photo)
    }
}
