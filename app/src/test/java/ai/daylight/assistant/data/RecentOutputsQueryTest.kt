package ai.daylight.assistant.data

import androidx.room.Room
import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** C7: Make's recent ink reads every generated file across conversations, newest first. */
@RunWith(RobolectricTestRunner::class)
class RecentOutputsQueryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private lateinit var database: AssistantDatabase
    private lateinit var dao: AssistantDao
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dao()
        repository = ConversationRepository(dao, json)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun out(id: String, kind: OutputKind) =
        GeneratedOutput(id, kind, "Generated $id", "$id.bin", "application/octet-stream", localPath = "/files/$id")

    private suspend fun answer(id: String, conversation: String, at: Long, vararg outputs: GeneratedOutput, role: String = "ASSISTANT") {
        dao.upsertMessage(
            MessageEntity(id, conversation, role, "Done", at, mode = "AGENT", outputsJson = json.encodeToString(outputs.toList()))
        )
    }

    private suspend fun ask(id: String, conversation: String, at: Long, text: String) {
        dao.upsertMessage(MessageEntity(id, conversation, "USER", text, at, mode = "AGENT", capability = "IMAGE"))
    }

    @Test
    fun newestFilesFirstEachWithThePromptThatMadeIt() = runBlocking<Unit> {
        dao.upsertConversation(ConversationEntity("make", "Make", 1, 1))
        dao.upsertConversation(ConversationEntity("run", "Run", 1, 1))
        ask("u1", "make", 10, "Yellow lantern over a harbour")
        answer("a1", "make", 20, out("lantern", OutputKind.IMAGE))
        ask("u2", "run", 30, "Write the quarterly report")
        answer("a2", "run", 40, out("report", OutputKind.DOCUMENT), out("sheet", OutputKind.SPREADSHEET))
        ask("u3", "make", 50, "Night ferry, slow push-in")
        answer("a3", "make", 60, out("ferry", OutputKind.VIDEO))

        val recent = repository.recentOutputs().first()

        assertThat(recent.map { it.output.id }).containsExactly("ferry", "report", "sheet", "lantern").inOrder()
        assertThat(recent.map { it.prompt }).containsExactly(
            "Night ferry, slow push-in",
            "Write the quarterly report",
            "Write the quarterly report",
            "Yellow lantern over a harbour"
        ).inOrder()
        assertThat(recent.first().conversationId).isEqualTo("make")
        assertThat(recent.first().messageId).isEqualTo("a3")
        assertThat(recent.first().createdAt).isEqualTo(60)
        assertThat(recent.first().output.kind).isEqualTo(OutputKind.VIDEO)
    }

    @Test
    fun answersWithoutFilesAndOtherRolesAreLeftOut() = runBlocking<Unit> {
        dao.upsertConversation(ConversationEntity("c", "C", 1, 1))
        ask("u1", "c", 10, "Hello")
        dao.upsertMessage(MessageEntity("plain", "c", "ASSISTANT", "Hi", 20))
        dao.upsertMessage(MessageEntity("blank", "c", "ASSISTANT", "Hi", 21, outputsJson = ""))
        answer("tool", "c", 22, out("tool-file", OutputKind.IMAGE), role = "TOOL")
        dao.upsertMessage(MessageEntity("broken", "c", "ASSISTANT", "Hi", 23, outputsJson = "{not json"))
        answer("real", "c", 24, out("real-file", OutputKind.IMAGE))

        val recent = repository.recentOutputs().first()

        // The unreadable row is skipped instead of failing the list.
        assertThat(recent.map { it.output.id }).containsExactly("real-file")
    }

    @Test
    fun aFileWithNoEarlierRequestHasNoPromptAndTheLimitCountsAnswers() = runBlocking<Unit> {
        dao.upsertConversation(ConversationEntity("c", "C", 1, 1))
        answer("a0", "c", 5, out("orphan", OutputKind.IMAGE))
        (1..5).forEach { i -> answer("a$i", "c", 10L * i, out("f$i", OutputKind.IMAGE)) }

        assertThat(repository.recentOutputs(limit = 3).first().map { it.output.id })
            .containsExactly("f5", "f4", "f3").inOrder()
        assertThat(repository.recentOutputs().first().last().prompt).isNull()
    }

    @Test
    fun theListUpdatesWhenANewFileLands() = runBlocking<Unit> {
        dao.upsertConversation(ConversationEntity("c", "C", 1, 1))
        assertThat(repository.recentOutputs().first()).isEmpty()
        ask("u", "c", 10, "A squid")
        answer("a", "c", 20, out("squid", OutputKind.IMAGE))
        assertThat(repository.recentOutputs().first().map { it.output.id }).containsExactly("squid")
    }
}
