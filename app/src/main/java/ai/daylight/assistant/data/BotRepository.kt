package ai.daylight.assistant.data

import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.BotEntity
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.domain.BotPersona
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * What a new routine needs: a name, the prompt it sends, and when. [days] are ISO weekdays
 * (1 = Monday) and only count for a weekly routine.
 */
data class RoutineDraft(
    val title: String,
    val prompt: String,
    val recurrence: CronRecurrence = CronRecurrence.DAILY,
    val hour: Int = 9,
    val minute: Int = 0,
    val days: Set<Int> = emptySet()
) {
    val valid: Boolean
        get() = title.isNotBlank() && prompt.isNotBlank() &&
            (recurrence != CronRecurrence.WEEKLY || days.any { it in 1..7 })

    /** The scheduled task, built the same way the Routines screen builds one. */
    fun toTask(id: String, conversationId: String, botId: String?, now: Long): ScheduledTaskEntity {
        val encodedDays = if (recurrence == CronRecurrence.WEEKLY) CronSchedulePresets.encodeDays(days) else null
        return ScheduledTaskEntity(
            id = id,
            title = title.trim().take(80),
            prompt = prompt.trim(),
            // Kept for legacy compatibility; wall-clock tasks ignore it.
            intervalMinutes = 1_440L,
            conversationId = conversationId,
            createdAt = now,
            recurrence = recurrence.name,
            hourOfDay = hour.coerceIn(0, 23),
            minuteOfHour = minute.coerceIn(0, 59),
            dayOfWeek = encodedDays?.split(',')?.firstOrNull()?.toIntOrNull(),
            daysOfWeek = encodedDays,
            botId = botId
        )
    }
}

/**
 * C9 Crew: personal bots. Each bot talks in one conversation, and its routines are ordinary
 * scheduled tasks (with botId) that send their prompt into that same conversation.
 *
 * Empty conversations are pruned all over the app, so a new bot's chat can vanish before
 * anyone writes in it; [ensureConversation] puts it back (with the bot's title) before each
 * send. [schedule] and [cancel] are the CronScheduler's, passed in so tests need no WorkManager.
 */
class BotRepository(
    private val dao: AssistantDao,
    private val schedule: (ScheduledTaskEntity) -> Unit,
    private val cancel: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun bots(): Flow<List<BotEntity>> = dao.observeBots()
    fun bot(id: String): Flow<BotEntity?> = dao.observeBot(id)
    fun routines(): Flow<List<ScheduledTaskEntity>> = dao.observeScheduledTasks()
    suspend fun find(id: String): BotEntity? = dao.bot(id)

    /** Creates the bot, its conversation and (when [routine] is valid) its first routine. */
    suspend fun create(name: String, persona: String, avatarSeed: Int, routine: RoutineDraft? = null): BotEntity {
        val cleanName = BotPersona.cleanName(name)
        require(cleanName.isNotEmpty()) { "Give the bot a name." }
        val now = clock()
        val bot = BotEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            persona = BotPersona.cleanPersona(persona),
            avatarSeed = avatarSeed,
            conversationId = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now
        )
        dao.upsertConversation(ConversationEntity(bot.conversationId, conversationTitle(cleanName), now, now))
        dao.upsertBot(bot)
        if (routine != null && routine.valid) addRoutine(bot, routine)
        return bot
    }

    suspend fun update(id: String, name: String, persona: String, avatarSeed: Int): BotEntity? {
        val bot = dao.bot(id) ?: return null
        val cleanName = BotPersona.cleanName(name)
        require(cleanName.isNotEmpty()) { "Give the bot a name." }
        val updated = bot.copy(
            name = cleanName,
            persona = BotPersona.cleanPersona(persona),
            avatarSeed = avatarSeed,
            updatedAt = clock()
        )
        dao.upsertBot(updated)
        if (dao.conversation(bot.conversationId) != null) {
            dao.renameConversation(bot.conversationId, conversationTitle(cleanName), updated.updatedAt)
        }
        return updated
    }

    /** A routine for [bot]: it runs in the bot's conversation and is scheduled at once. */
    suspend fun addRoutine(bot: BotEntity, draft: RoutineDraft): ScheduledTaskEntity {
        require(draft.valid) { "Enter a name and a prompt." }
        val task = draft.toTask(UUID.randomUUID().toString(), bot.conversationId, bot.id, clock())
        dao.upsertScheduledTask(task)
        schedule(task)
        return task
    }

    suspend fun setRoutineEnabled(taskId: String, enabled: Boolean) {
        dao.setScheduledTaskEnabled(taskId, enabled)
        val task = dao.scheduledTask(taskId)
        if (enabled && task != null) schedule(task) else cancel(taskId)
    }

    /** Deletes one routine. The bot's conversation stays: it is the bot's, not the routine's. */
    suspend fun deleteRoutine(taskId: String) {
        cancel(taskId)
        dao.deleteScheduledTask(taskId)
    }

    /** Deletes the bot, its routines and its conversation. */
    suspend fun delete(id: String) {
        val bot = dao.bot(id) ?: return
        dao.scheduledTasksForBot(id).forEach { task ->
            cancel(task.id)
            dao.deleteScheduledTask(task.id)
        }
        dao.deleteBot(id)
        dao.deleteConversation(bot.conversationId)
    }

    /** Puts the bot's conversation back if it was pruned. Returns its id, or null when the bot is gone. */
    suspend fun ensureConversation(botId: String): String? {
        val bot = dao.bot(botId) ?: return null
        if (dao.conversation(bot.conversationId) == null) {
            val now = clock()
            dao.upsertConversation(ConversationEntity(bot.conversationId, conversationTitle(bot.name), now, now))
        }
        return bot.conversationId
    }

    companion object {
        /** How a bot's chat is titled in the chat library. */
        fun conversationTitle(name: String): String = "Crew · ${name.trim()}"
    }
}
