package ai.daylight.assistant.data

import androidx.room.Room
import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
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
class ConversationPinTest {
    private lateinit var database: AssistantDatabase
    private lateinit var dao: AssistantDao
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AssistantDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.dao()
        repository = ConversationRepository(
            dao,
            Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pinnedConversationsSortFirstAndCanBeToggled() = runBlocking {
        dao.upsertConversation(ConversationEntity("older", "Older", 1, 10))
        dao.upsertMessage(MessageEntity("m1", "older", "USER", "First", 1))
        dao.upsertConversation(ConversationEntity("newer", "Newer", 2, 30))
        dao.upsertMessage(MessageEntity("m2", "newer", "USER", "Second", 2))
        dao.upsertConversation(ConversationEntity("pinned", "Pinned", 3, 20, pinned = true))
        dao.upsertMessage(MessageEntity("m3", "pinned", "USER", "Third", 3))

        assertThat(repository.conversations(archived = false, query = "").first().map { it.id })
            .containsExactly("pinned", "newer", "older")
            .inOrder()

        repository.pin("newer", true)
        assertThat(repository.conversation("newer")?.pinned).isTrue()
        assertThat(repository.conversations(archived = false, query = "").first().map { it.id })
            .containsExactly("newer", "pinned", "older")
            .inOrder()

        repository.pin("newer", false)
        assertThat(repository.conversation("newer")?.pinned).isFalse()
    }

    @Test
    fun emptyConversationsStayHiddenFromTheConversationList() = runBlocking {
        dao.upsertConversation(ConversationEntity("empty", "Blank", 1, 10))
        assertThat(repository.conversations(archived = false, query = "").first()).isEmpty()
        assertThat(repository.conversation("empty")).isNotNull()
    }

    @Test
    fun exportImportPreservesPinsAndLegacyExportsDefaultToUnpinned() = runBlocking {
        dao.upsertConversation(ConversationEntity("pinned", "Pinned", 1, 2, pinned = true))

        val exported = repository.exportJson()
        assertThat(exported).contains("\"pinned\":true")

        repository.clear()
        repository.importJson(exported)
        assertThat(repository.conversation("pinned")?.pinned).isTrue()

        val legacyExport = """
            {
              "formatVersion": 1,
              "exportedAt": 1,
              "conversations": [{
                "id": "legacy",
                "title": "Legacy",
                "createdAt": 1,
                "updatedAt": 2,
                "archived": false,
                "messages": []
              }]
            }
        """.trimIndent()
        repository.clear()
        repository.importJson(legacyExport)
        assertThat(repository.conversation("legacy")?.pinned).isFalse()
    }
}
