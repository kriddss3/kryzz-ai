package ai.daylight.assistant.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** C9: one job per bot, so a routine and a chat reply never write into the bot's chat at once. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrewDeskTest {
    private val sent = mutableListOf<Pair<String, String>>()
    private var gate = CompletableDeferred<Unit>()

    private fun TestScope.desk(failWith: Exception? = null) = CrewDesk(
        scope = backgroundScope,
        prepare = { botId -> if (botId == "gone") null else "chat-$botId" },
        send = { conversationId, text ->
            sent += conversationId to text
            gate.await()
            if (failWith != null) throw failWith
        },
        clock = { 42L }
    )

    @Test fun aReplyMarksTheBotBusyAndRefusesASecondMessage() = runTest(StandardTestDispatcher()) {
        val desk = desk()
        assertThat(desk.reply("scout", "  hello ")).isTrue()
        runCurrent()
        assertThat(sent).containsExactly("chat-scout" to "hello")
        assertThat(desk.work.value["scout"]?.kind).isEqualTo(BotWorkKind.REPLY)
        assertThat(desk.reply("scout", "again")).isFalse()
        // Another bot is free to work.
        assertThat(desk.reply("pilot", "hi")).isTrue()
        gate.complete(Unit)
        runCurrent()
        assertThat(desk.work.value).isEmpty()
        assertThat(desk.reply("scout", "again")).isTrue()
        runCurrent()
    }

    @Test fun blankMessagesAreNotSent() = runTest(StandardTestDispatcher()) {
        val desk = desk()
        assertThat(desk.reply("scout", "   ")).isFalse()
        assertThat(desk.work.value).isEmpty()
    }

    @Test fun aRoutineWaitsForTheReplyToFinish() = runTest(StandardTestDispatcher()) {
        val desk = desk()
        desk.reply("scout", "hello")
        runCurrent()
        var routineRan = false
        val routine = launch {
            desk.runRoutine("scout", "r1", "Morning brief") { routineRan = true }
        }
        runCurrent()
        assertThat(routineRan).isFalse()
        gate.complete(Unit)
        runCurrent()
        assertThat(routineRan).isTrue()
        assertThat(routine.isCompleted).isTrue()
        assertThat(desk.work.value).isEmpty()
    }

    @Test fun aRoutineIsShownWhileItRunsAndBlocksMessages() = runTest(StandardTestDispatcher()) {
        val desk = desk()
        val release = CompletableDeferred<Unit>()
        launch { desk.runRoutine("scout", "r1", "Morning brief") { release.await() } }
        runCurrent()
        val work = desk.work.value["scout"]
        assertThat(work?.kind).isEqualTo(BotWorkKind.ROUTINE)
        assertThat(work?.routineTitle).isEqualTo("Morning brief")
        assertThat(desk.reply("scout", "hi")).isFalse()
        release.complete(Unit)
        runCurrent()
        assertThat(desk.work.value).isEmpty()
    }

    @Test fun stopEndsTheReplyAndFreesTheBot() = runTest(StandardTestDispatcher()) {
        val desk = desk()
        desk.reply("scout", "hello")
        runCurrent()
        desk.stop("scout")
        runCurrent()
        assertThat(desk.work.value).isEmpty()
        assertThat(desk.errors.value).isEmpty()
        assertThat(desk.reply("scout", "again")).isTrue()
        gate.complete(Unit)
        runCurrent()
    }

    @Test fun aFailedReplyIsKeptUntilTheNextMessage() = runTest(StandardTestDispatcher()) {
        val desk = desk(failWith = IllegalStateException("The API key was rejected"))
        gate.complete(Unit)
        desk.reply("scout", "hello")
        runCurrent()
        assertThat(desk.errors.value["scout"]).isEqualTo("The API key was rejected")
        desk.reply("scout", "again")
        assertThat(desk.errors.value).doesNotContainKey("scout")
        runCurrent()
    }

    @Test fun aDeletedBotSendsNothing() = runTest(StandardTestDispatcher()) {
        val desk = desk()
        assertThat(desk.reply("gone", "hello")).isTrue()
        runCurrent()
        assertThat(sent).isEmpty()
        assertThat(desk.work.value).isEmpty()
    }
}
