package ai.daylight.assistant.data

import androidx.room.Room
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.ConversationEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ConversationFolderTest {
    private lateinit var database: AssistantDatabase
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AssistantDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ConversationRepository(
            database.dao(),
            Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun foldersAssignExportImportAndDeleteWithoutDeletingChats() = runBlocking {
        database.dao().upsertConversation(ConversationEntity("chat", "Launch plan", 1, 2))
        val folderId = repository.createFolder("  Product   work  ")
        repository.assignFolder("chat", folderId)

        assertThat(repository.folders().first().single().name).isEqualTo("Product work")
        assertThat(repository.conversation("chat")?.folderId).isEqualTo(folderId)

        val exported = repository.exportJson()
        assertThat(exported).contains("\"folders\"")
        assertThat(exported).contains("\"folderId\":\"$folderId\"")

        repository.clear()
        repository.importJson(exported)
        assertThat(repository.folders().first().single().id).isEqualTo(folderId)
        assertThat(repository.conversation("chat")?.folderId).isEqualTo(folderId)

        repository.deleteFolder(folderId)
        assertThat(repository.folders().first()).isEmpty()
        assertThat(repository.conversation("chat")).isNotNull()
        assertThat(repository.conversation("chat")?.folderId).isNull()
    }

    @Test
    fun legacyArchivedImportsAreRestoredToTheVisibleChatList() = runBlocking {
        val legacy = """
            {
              "formatVersion": 1,
              "exportedAt": 1,
              "conversations": [{
                "id": "hidden-before-upgrade",
                "title": "Recovered",
                "createdAt": 1,
                "updatedAt": 2,
                "archived": true,
                "messages": []
              }]
            }
        """.trimIndent()

        repository.importJson(legacy)

        assertThat(repository.conversation("hidden-before-upgrade")?.archived).isFalse()
        // A conversation with no messages is still restored, but stays out of the
        // drawer/library until it actually contains content.
        assertThat(repository.conversations(archived = false, query = "").first())
            .isEmpty()
        database.dao().upsertMessage(
            ai.daylight.assistant.data.local.MessageEntity("m1", "hidden-before-upgrade", "USER", "Hello", 3)
        )
        assertThat(repository.conversations(archived = false, query = "").first().map { it.id })
            .contains("hidden-before-upgrade")
    }

    @Test
    fun rejectedFoldersCannotLeaveOrphanAssignments() = runBlocking {
        val malformed = """
            {
              "formatVersion": 1,
              "exportedAt": 1,
              "folders": [{"id":"bad-folder","name":"   ","createdAt":1,"updatedAt":1}],
              "conversations": [{
                "id": "chat",
                "title": "   ",
                "createdAt": 1,
                "updatedAt": 2,
                "archived": false,
                "folderId": "bad-folder",
                "messages": []
              }]
            }
        """.trimIndent()

        assertThat(repository.importJson(malformed)).isEqualTo(1)
        assertThat(repository.folders().first()).isEmpty()
        assertThat(repository.conversation("chat")?.folderId).isNull()
        assertThat(repository.conversation("chat")?.title).isEqualTo("Imported conversation")
    }
}
