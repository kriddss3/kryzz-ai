package ai.daylight.assistant.data

import androidx.room.Room
import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** C9: bots, their chat and their routines (ordinary scheduled tasks with botId). */
@RunWith(RobolectricTestRunner::class)
class BotRepositoryTest {
    private lateinit var database: AssistantDatabase
    private lateinit var dao: AssistantDao
    private lateinit var bots: BotRepository
    private val scheduled = mutableListOf<ScheduledTaskEntity>()
    private val cancelled = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dao()
        bots = BotRepository(dao, schedule = { scheduled += it }, cancel = { cancelled += it }, clock = { 1_000L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private val daily = RoutineDraft("Morning brief", "Read my feeds and write the brief.", CronRecurrence.DAILY, 7, 0)

    @Test
    fun aNewBotGetsItsChatAndItsRoutineRunsInThatChat() = runBlocking<Unit> {
        val bot = bots.create("  Scout ", "Reads my feeds.", avatarSeed = 4, routine = daily)
        assertThat(bot.name).isEqualTo("Scout")
        assertThat(dao.conversation(bot.conversationId)?.title).isEqualTo("Crew · Scout")

        val task = dao.scheduledTasksForBot(bot.id).single()
        assertThat(task.botId).isEqualTo(bot.id)
        assertThat(task.conversationId).isEqualTo(bot.conversationId)
        assertThat(task.hourOfDay).isEqualTo(7)
        assertThat(task.recurrence).isEqualTo("DAILY")
        assertThat(scheduled.map { it.id }).containsExactly(task.id)
    }

    @Test
    fun aChatOnlyBotHasNoRoutineAndAnUnfinishedRoutineIsDropped() = runBlocking<Unit> {
        val chatOnly = bots.create("Tally", "", avatarSeed = 2)
        assertThat(dao.scheduledTasksForBot(chatOnly.id)).isEmpty()
        val noPrompt = bots.create("Pilot", "", avatarSeed = 5, routine = daily.copy(prompt = "  "))
        assertThat(dao.scheduledTasksForBot(noPrompt.id)).isEmpty()
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun weeklyRoutinesKeepTheirDays() = runBlocking<Unit> {
        val bot = bots.create("Pilot", "Plans my week.", avatarSeed = 5)
        val task = bots.addRoutine(bot, RoutineDraft("Week plan", "Plan the week.", CronRecurrence.WEEKLY, 18, 0, setOf(7)))
        assertThat(task.daysOfWeek).isEqualTo("7")
        assertThat(task.dayOfWeek).isEqualTo(7)
        assertThat(CronSchedulePresets.label(task)).isEqualTo("Sunday at 18:00")
    }

    @Test
    fun aBotWithNoNameIsRefused() = runBlocking<Unit> {
        val failure = runCatching { bots.create("   ", "x", avatarSeed = 1) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(dao.allBots()).isEmpty()
    }

    @Test
    fun aPrunedChatComesBackWithTheBotsTitle() = runBlocking<Unit> {
        val bot = bots.create("Scout", "", avatarSeed = 4)
        // Empty chats are pruned across the app; the bot's was empty.
        dao.deleteEmptyConversations()
        assertThat(dao.conversation(bot.conversationId)).isNull()
        assertThat(bots.ensureConversation(bot.id)).isEqualTo(bot.conversationId)
        assertThat(dao.conversation(bot.conversationId)?.title).isEqualTo("Crew · Scout")
        assertThat(bots.ensureConversation("missing")).isNull()
    }

    @Test
    fun editingRenamesTheChatToo() = runBlocking<Unit> {
        val bot = bots.create("Scout", "Reads feeds.", avatarSeed = 4)
        val updated = bots.update(bot.id, "Scout Two", "Reads more feeds.", 9)
        assertThat(updated?.name).isEqualTo("Scout Two")
        assertThat(updated?.avatarSeed).isEqualTo(9)
        assertThat(dao.conversation(bot.conversationId)?.title).isEqualTo("Crew · Scout Two")
    }

    @Test
    fun deletingARoutineKeepsTheBotsChat() = runBlocking<Unit> {
        val bot = bots.create("Scout", "", avatarSeed = 4, routine = daily)
        dao.upsertMessage(MessageEntity("m1", bot.conversationId, "USER", "hello", 2))
        val task = dao.scheduledTasksForBot(bot.id).single()
        bots.deleteRoutine(task.id)
        assertThat(cancelled).containsExactly(task.id)
        assertThat(dao.scheduledTask(task.id)).isNull()
        assertThat(dao.conversation(bot.conversationId)).isNotNull()
    }

    @Test
    fun deletingABotRemovesItsRoutinesAndChatButNoOtherRoutine() = runBlocking<Unit> {
        val bot = bots.create("Scout", "", avatarSeed = 4, routine = daily)
        dao.upsertMessage(MessageEntity("m1", bot.conversationId, "USER", "hello", 2))
        val plain = daily.toTask("plain", "other-chat", botId = null, now = 5)
        dao.upsertScheduledTask(plain)
        val botTask = dao.scheduledTasksForBot(bot.id).single()

        bots.delete(bot.id)
        assertThat(dao.bot(bot.id)).isNull()
        assertThat(dao.scheduledTask(botTask.id)).isNull()
        assertThat(dao.conversation(bot.conversationId)).isNull()
        assertThat(dao.messages(bot.conversationId)).isEmpty()
        assertThat(cancelled).containsExactly(botTask.id)
        assertThat(dao.scheduledTask("plain")).isNotNull()
    }

    @Test
    fun pausingARoutineCancelsItAndResumingReschedules() = runBlocking<Unit> {
        val bot = bots.create("Scout", "", avatarSeed = 4, routine = daily)
        val task = dao.scheduledTasksForBot(bot.id).single()
        scheduled.clear()
        bots.setRoutineEnabled(task.id, false)
        assertThat(cancelled).containsExactly(task.id)
        assertThat(dao.scheduledTask(task.id)?.enabled).isFalse()
        bots.setRoutineEnabled(task.id, true)
        assertThat(scheduled.map { it.id }).containsExactly(task.id)
    }

    @Test
    fun clearingAllDataRemovesBots() = runBlocking<Unit> {
        bots.create("Scout", "", avatarSeed = 4, routine = daily)
        dao.clearAll()
        assertThat(dao.allBots()).isEmpty()
    }
}
