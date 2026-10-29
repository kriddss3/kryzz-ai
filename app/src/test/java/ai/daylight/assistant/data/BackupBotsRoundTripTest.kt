package ai.daylight.assistant.data

import androidx.room.Room
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.BotEntity
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** C9: Crew bots travel in the chat backup, and backups made before Crew still import. */
@RunWith(RobolectricTestRunner::class)
class BackupBotsRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private val databases = mutableListOf<AssistantDatabase>()

    private fun newRepository(): Pair<AssistantDatabase, ConversationRepository> {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases += db
        return db to ConversationRepository(db.dao(), json)
    }

    private lateinit var source: AssistantDatabase
    private lateinit var sourceRepo: ConversationRepository

    @Before
    fun setUp() {
        val (db, repo) = newRepository()
        source = db
        sourceRepo = repo
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    private val scout = BotEntity(
        id = "bot-scout",
        name = "Scout",
        persona = "Reads my three feeds and writes a five-line brief.",
        avatarSeed = 4,
        conversationId = "chat-scout",
        createdAt = 100,
        updatedAt = 200
    )

    @Test
    fun aBotAndItsChatComeBackFromTheBackup() = runBlocking<Unit> {
        val dao = source.dao()
        dao.upsertConversation(ConversationEntity("chat-scout", "Crew · Scout", 100, 300))
        dao.upsertMessage(MessageEntity("m1", "chat-scout", "USER", "Any news?", 250))
        dao.upsertMessage(MessageEntity("m2", "chat-scout", "ASSISTANT", "Two feeds done.", 260))
        dao.upsertBot(scout)

        val backup = sourceRepo.exportJson()
        assertThat(backup).contains("\"bots\"")

        val (fresh, freshRepo) = newRepository()
        assertThat(freshRepo.importJson(backup)).isEqualTo(1)
        assertThat(fresh.dao().allBots()).containsExactly(scout)
        assertThat(fresh.dao().botForConversation("chat-scout")).isEqualTo(scout)
        assertThat(fresh.dao().messages("chat-scout").map { it.content }).containsExactly("Any news?", "Two feeds done.").inOrder()
    }

    @Test
    fun aBackupWithoutCrewStillImports() = runBlocking<Unit> {
        // A 5.12.9 backup: no "bots" field at all.
        val old = """
            {"formatVersion":1,"exportedAt":1,"conversations":[
              {"id":"c1","title":"Old chat","createdAt":1,"updatedAt":2,"archived":false,
               "messages":[{"id":"m1","role":"USER","content":"hello","createdAt":1}]}
            ]}
        """.trimIndent()
        val (fresh, freshRepo) = newRepository()
        assertThat(freshRepo.importJson(old)).isEqualTo(1)
        assertThat(fresh.dao().allBots()).isEmpty()
        assertThat(fresh.dao().conversation("c1")?.title).isEqualTo("Old chat")
    }

    @Test
    fun aBackupWithNoBotsLeavesTheFieldOut() = runBlocking<Unit> {
        source.dao().upsertConversation(ConversationEntity("c1", "Chat", 1, 1))
        assertThat(sourceRepo.exportJson()).doesNotContain("\"bots\"")
    }

    @Test
    fun brokenBotEntriesAreSkippedAndLongTextIsCapped() = runBlocking<Unit> {
        val backup = """
            {"formatVersion":1,"exportedAt":1,"conversations":[],"bots":[
              {"id":"","name":"Nobody","persona":"","conversationId":"x","createdAt":1,"updatedAt":1},
              {"id":"b2","name":"   ","persona":"","conversationId":"y","createdAt":1,"updatedAt":1},
              {"id":"b3","name":"Pilot","persona":"${"p".repeat(900)}","conversationId":"z","createdAt":1,"updatedAt":1}
            ]}
        """.trimIndent()
        val (fresh, freshRepo) = newRepository()
        freshRepo.importJson(backup)
        val bots = fresh.dao().allBots()
        assertThat(bots.map { it.id }).containsExactly("b3")
        assertThat(bots.single().persona).hasLength(ai.daylight.assistant.domain.BotPersona.PERSONA_MAX)
        assertThat(bots.single().avatarSeed).isEqualTo(0)
    }
}
