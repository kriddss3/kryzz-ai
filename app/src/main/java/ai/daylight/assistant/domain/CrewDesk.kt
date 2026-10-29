package ai.daylight.assistant.domain

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What a bot is doing right now: a routine run (from the scheduler) or a chat reply. */
enum class BotWorkKind { ROUTINE, REPLY }

data class BotWork(
    val botId: String,
    val kind: BotWorkKind,
    val startedAt: Long,
    val routineId: String? = null,
    val routineTitle: String? = null
)

/**
 * C9 Crew: the bots' work desk, held by the app (AppContainer) so a reply keeps going when
 * the user leaves the bot's screen, like Make's renders.
 *
 * One job per bot at a time: a routine that fires while the user's message is being
 * answered waits for it, and a message sent during a routine run is refused, so two
 * replies never write into the bot's conversation at once. [work] is the live state the
 * Crew list, Home and the rail read; it is set by the scheduler's worker, which runs in
 * this same process.
 *
 * [prepare] puts the bot's conversation back if it was pruned and returns its id (null
 * when the bot was deleted); [send] is the agent's send.
 */
class CrewDesk(
    private val scope: CoroutineScope,
    private val prepare: suspend (botId: String) -> String?,
    private val send: suspend (conversationId: String, text: String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _work = MutableStateFlow<Map<String, BotWork>>(emptyMap())
    val work: StateFlow<Map<String, BotWork>> = _work.asStateFlow()

    /** The last failed reply per bot, cleared by the next message. */
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    private val locks = ConcurrentHashMap<String, Mutex>()
    private val replies = ConcurrentHashMap<String, Job>()

    private fun lock(botId: String): Mutex = locks.getOrPut(botId) { Mutex() }

    fun isBusy(botId: String): Boolean = botId in _work.value

    /**
     * A routine run for [botId], called by the scheduler's worker around its send. It waits
     * for a reply in progress first, and marks the bot busy while [block] runs.
     */
    suspend fun <T> runRoutine(botId: String, routineId: String, routineTitle: String, block: suspend () -> T): T =
        lock(botId).withLock {
            _work.update { it + (botId to BotWork(botId, BotWorkKind.ROUTINE, clock(), routineId, routineTitle)) }
            try {
                block()
            } finally {
                _work.update { it - botId }
            }
        }

    /**
     * Sends [text] in the bot's chat and lets the agent answer. False when the text is blank
     * or the bot is already working (the screen keeps the draft and says so).
     */
    fun reply(botId: String, text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val lock = lock(botId)
        if (!lock.tryLock()) return false
        _errors.update { it - botId }
        _work.update { it + (botId to BotWork(botId, BotWorkKind.REPLY, clock())) }
        val job = scope.launch {
            try {
                val conversationId = prepare(botId) ?: return@launch
                send(conversationId, clean)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _errors.update { it + (botId to (error.message?.takeIf(String::isNotBlank) ?: "The reply failed.")) }
            }
        }
        replies[botId] = job
        // Cleans up even when the job is stopped before it starts running.
        job.invokeOnCompletion {
            replies.remove(botId, job)
            _work.update { current -> if (current[botId]?.kind == BotWorkKind.REPLY) current - botId else current }
            lock.unlock()
        }
        return true
    }

    /** Stops the bot's chat reply. A routine run is left to finish. */
    fun stop(botId: String) {
        replies[botId]?.cancel()
    }

    fun dismissError(botId: String) = _errors.update { it - botId }
}
