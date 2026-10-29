package ai.daylight.assistant.data

import androidx.room.Room
import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.BotEntity
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.ui.library.LibraryModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** C10: the library list knows each chat's newest mode and capability and its bot. */
@RunWith(RobolectricTestRunner::class)
class LibrarySummaryQueryTest {
    private lateinit var database: AssistantDatabase
    private lateinit var dao: AssistantDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun chat(id: String, at: Long) = dao.upsertConversation(ConversationEntity(id, "Chat $id", at, at))

    private suspend fun ask(id: String, conversation: String, at: Long, mode: String, capability: String? = null) =
        dao.upsertMessage(MessageEntity(id, conversation, "USER", "question $id", at, mode = mode, capability = capability))

    private suspend fun reply(id: String, conversation: String, at: Long) =
        dao.upsertMessage(MessageEntity(id, conversation, "ASSISTANT", "answer $id", at, mode = "CHAT"))

    @Test
    fun eachChatCarriesItsArm() = runBlocking<Unit> {
        chat("talk", 10)
        ask("t1", "talk", 11, "CHAT")
        reply("t2", "talk", 12)

        // A chat that moved to Run: the newest question decides.
        chat("run", 20)
        ask("r1", "run", 21, "CHAT")
        ask("r2", "run", 22, "AGENT", "AUTO")
        reply("r3", "run", 23)

        chat("make", 30)
        ask("m1", "make", 31, "AGENT", "IMAGE")

        chat("crew", 40)
        ask("c1", "crew", 41, "CHAT")
        dao.upsertBot(BotEntity("scout", "Scout", "Reads feeds", 4, "crew", 40, 40))

        val rows = dao.observeConversations(archived = false, query = "").first().associateBy { it.id }
        assertThat(rows.keys).containsExactly("talk", "run", "make", "crew")
        assertThat(rows.getValue("talk").lastMode).isEqualTo("CHAT")
        assertThat(rows.getValue("run").lastMode).isEqualTo("AGENT")
        assertThat(rows.getValue("run").lastCapability).isEqualTo("AUTO")
        assertThat(rows.getValue("make").lastCapability).isEqualTo("IMAGE")
        assertThat(rows.getValue("crew").botName).isEqualTo("Scout")
        assertThat(rows.getValue("talk").botName).isNull()

        assertThat(rows.mapValues { LibraryModel.armLabel(it.value) }).containsExactly(
            "talk", "TALK", "run", "RUN", "make", "MAKE", "crew", "CREW"
        )
    }
}
