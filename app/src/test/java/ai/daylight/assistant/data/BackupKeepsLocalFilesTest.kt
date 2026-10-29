package ai.daylight.assistant.data

import androidx.room.Room
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.GeneratedOutput
import ai.daylight.assistant.domain.OutputKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * C10 fix: restoring a backup on the same phone replaced each chat that was still there,
 * which deleted its messages and put back the backup's copies: newer messages, Run's work
 * log and every file link were lost. A chat on the phone is now merged instead.
 */
@RunWith(RobolectricTestRunner::class)
class BackupKeepsLocalFilesTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
    private val databases = mutableListOf<AssistantDatabase>()

    private fun newRepository(): Pair<AssistantDatabase, ConversationRepository> {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases += db
        return db to ConversationRepository(db.dao(), json)
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    private val image = GeneratedOutput(
        "out-1", OutputKind.IMAGE, "Generated image", "kryzz-image.jpg", "image/jpeg",
        localPath = "/data/user/0/app/files/generated_outputs/lantern.jpg"
    )

    private suspend fun seed(db: AssistantDatabase) {
        val dao = db.dao()
        dao.upsertConversation(ConversationEntity("make", "Squid lantern", 1, 2))
        dao.upsertMessage(MessageEntity("u1", "make", "USER", "A squid lantern", 1, mode = "AGENT", capability = "IMAGE"))
        dao.upsertMessage(
            MessageEntity(
                "a1", "make", "ASSISTANT", "Created your image.", 2, mode = "AGENT", capability = "IMAGE",
                outputsJson = json.encodeToString(listOf(image))
            )
        )
    }

    private suspend fun outputsOf(db: AssistantDatabase): List<GeneratedOutput> =
        json.decodeFromString(db.dao().messages("make").single { it.id == "a1" }.outputsJson)

    @Test
    fun restoringOnTheSamePhoneKeepsTheFiles() = runBlocking<Unit> {
        val (db, repo) = newRepository()
        seed(db)
        val backup = repo.exportJson()

        assertThat(repo.importJson(backup)).isEqualTo(1)
        assertThat(outputsOf(db).single().localPath).isEqualTo(image.localPath)
        assertThat(db.dao().messages("make").map { it.content }).containsExactly("A squid lantern", "Created your image.").inOrder()
    }

    @Test
    fun restoringAnOlderBackupKeepsWhatCameAfterIt() = runBlocking<Unit> {
        val (db, repo) = newRepository()
        seed(db)
        val backup = repo.exportJson()
        // After the backup: a work-log row (never in backups) and a newer exchange.
        val dao = db.dao()
        dao.upsertMessage(MessageEntity("t1", "make", "TOOL", "{\"ok\":true}", 3, toolCallId = "call-1"))
        dao.upsertMessage(MessageEntity("u2", "make", "USER", "Now a wider one", 4))
        dao.upsertMessage(MessageEntity("a2", "make", "ASSISTANT", "Here it is.", 5))
        repo.rename("make", "Lanterns")

        repo.importJson(backup)
        assertThat(dao.messages("make").map { it.id }).containsExactly("u1", "a1", "t1", "u2", "a2").inOrder()
        assertThat(repo.conversation("make")?.title).isEqualTo("Lanterns")
    }

    @Test
    fun aDeletedChatComesBackInFull() = runBlocking<Unit> {
        val (db, repo) = newRepository()
        seed(db)
        val backup = repo.exportJson()
        repo.delete("make")
        assertThat(repo.conversation("make")).isNull()

        repo.importJson(backup)
        assertThat(repo.conversation("make")?.title).isEqualTo("Squid lantern")
        assertThat(db.dao().messages("make").map { it.id }).containsExactly("u1", "a1").inOrder()
        // The file itself was not in the backup, so the restored chat names it without a path.
        assertThat(outputsOf(db).single().localPath).isNull()
    }

    @Test
    fun anotherPhoneGetsTheChatWithoutFilePaths() = runBlocking<Unit> {
        val (db, repo) = newRepository()
        seed(db)
        val backup = repo.exportJson()

        val (fresh, freshRepo) = newRepository()
        assertThat(freshRepo.importJson(backup)).isEqualTo(1)
        assertThat(outputsOf(fresh).single().localPath).isNull()
    }
}
