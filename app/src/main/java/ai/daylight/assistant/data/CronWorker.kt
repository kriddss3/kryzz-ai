package ai.daylight.assistant.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ai.daylight.assistant.DaylightApplication
import ai.daylight.assistant.MainActivity
import ai.daylight.assistant.R
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ErrorKind

/**
 * Runs one scheduled task: sends its prompt to the AI inside the task's dedicated
 * conversation, then posts a completion notification. Transient failures retry via
 * the WorkManager backoff; anything persistent simply waits for the next period.
 */
class CronWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()
        val container = (applicationContext as DaylightApplication).container
        val dao = container.database.dao()
        val task = dao.scheduledTask(taskId) ?: return Result.success()
        if (!task.enabled) return Result.success()
        val shouldRetry = runAttemptCount < MAX_RETRIES
        var rearmNextOccurrence = true
        return try {
            val botId = task.botId
            if (botId != null) {
                // C9: a bot's routine takes its turn on the Crew desk (never two replies in
                // the bot's chat at once) and keeps the chat's title if it was pruned.
                container.crew.runRoutine(botId, task.id, task.title) {
                    val conversationId = container.bots.ensureConversation(botId) ?: task.conversationId
                    container.agent.send(conversationId, task.prompt, skipAutoMemory = true)
                }
            } else {
                container.agent.send(task.conversationId, task.prompt, skipAutoMemory = true)
            }
            dao.updateScheduledTaskLastRun(task.id, System.currentTimeMillis())
            val botName = task.botId?.let { dao.bot(it)?.name }
            CronNotifications.notifyFinished(applicationContext, task, botName)
            Result.success()
        } catch (error: Exception) {
            if (shouldRetry && retryable(error)) {
                // A pending retry reuses this same request; re-arming now would replace
                // (and cancel) it, so the next occurrence is armed on the final attempt.
                rearmNextOccurrence = false
                Result.retry()
            } else {
                Result.success()
            }
        } finally {
            // Wall-clock tasks re-arm their own next occurrence once the final attempt is
            // done (success or failure), so the chain never dies; legacy interval tasks
            // rely on WorkManager's periodic chain instead.
            if (task.enabled && !CronSchedulePresets.isLegacy(task) && rearmNextOccurrence) {
                container.cron.scheduleNextOccurrence(task)
            }
        }
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
        private const val MAX_RETRIES = 2

        /**
         * A retry sends the prompt again, so it only helps when the failure can pass on its
         * own (network, timeout, rate limit). A rejected key or no credits fails the same way
         * every time and would only stack copies of the prompt in the chat (C9: a bot's
         * routine runs in the bot's own, long-lived chat).
         */
        internal fun retryable(error: Throwable): Boolean = when ((error as? AssistantApiException)?.kind) {
            ErrorKind.INVALID_KEY, ErrorKind.INSUFFICIENT_CREDITS, ErrorKind.VALIDATION, ErrorKind.CONTEXT_LENGTH -> false
            else -> true
        }
    }
}

object CronNotifications {
    const val CHANNEL_ID = "cron_runs"
    const val EXTRA_CONVERSATION_ID = "conversationId"

    /** [botName] is set for a Crew bot's routine: the bot is named and the routine said. */
    fun notifyFinished(context: Context, task: ScheduledTaskEntity, botName: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val openApp = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CONVERSATION_ID, task.conversationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(botName ?: task.title)
            .setContentText(if (botName != null) "Routine done: ${task.title}" else "Cron task finished")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification) }
    }
}
