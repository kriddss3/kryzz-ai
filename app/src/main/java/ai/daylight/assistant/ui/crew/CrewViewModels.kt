package ai.daylight.assistant.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.daylight.assistant.AppContainer
import ai.daylight.assistant.data.RoutineDraft
import ai.daylight.assistant.data.local.BotEntity
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.data.local.ScheduledTaskEntity
import ai.daylight.assistant.domain.BotWork
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Crew (C9): the bot list, the routines without a bot, and what each bot is doing. */
class CrewViewModel(private val container: AppContainer) : ViewModel() {
    /** Null until the first read, so the screen never flashes "no bots" on open. */
    val bots: StateFlow<List<BotEntity>?> = container.bots.bots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val routines: StateFlow<List<ScheduledTaskEntity>> = container.bots.routines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val work: StateFlow<Map<String, BotWork>> = container.crew.work
    val notice = MutableStateFlow<String?>(null)

    fun create(name: String, persona: String, seed: Int, routine: RoutineDraft?, onDone: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { container.bots.create(name, persona, seed, routine) }
                .onSuccess { onDone(it.id) }
                .onFailure { notice.value = it.message ?: "The bot could not be saved." }
        }
    }
}

/** One bot's page: its chat, routines and runs. Replies run on the app's Crew desk. */
@OptIn(ExperimentalCoroutinesApi::class)
class BotViewModel(private val container: AppContainer, val botId: String) : ViewModel() {
    val bot: StateFlow<BotEntity?> = container.bots.bot(botId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True when the bot no longer exists (a stale page after a delete), so the page can close. */
    val gone = MutableStateFlow(false)

    val routines: StateFlow<List<ScheduledTaskEntity>> = container.bots.routines()
        .map { all -> CrewModel.routinesOf(botId, all) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messages: StateFlow<List<MessageEntity>> = container.bots.bot(botId)
        .map { it?.conversationId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.conversations.messages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val work: StateFlow<BotWork?> = container.crew.work
        .map { it[botId] }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.crew.work.value[botId])

    val error: StateFlow<String?> = container.crew.errors
        .map { it[botId] }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val draft = MutableStateFlow("")
    val notice = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            gone.value = container.bots.find(botId) == null
        }
    }

    /** Sends the draft. False (draft kept) when the bot is busy with a routine or a reply. */
    fun send(): Boolean {
        val text = draft.value.trim()
        if (text.isEmpty()) return false
        if (!container.crew.reply(botId, text)) {
            notice.value = "Busy with a run. Send again when it is done."
            return false
        }
        draft.value = ""
        notice.value = null
        return true
    }

    /** The sources an answer cites, so its [n] marks open them. */
    fun citationUrls(message: MessageEntity): List<String> = container.conversations.citations(message).map { it.url }

    fun stop() = container.crew.stop(botId)
    fun dismissError() = container.crew.dismissError(botId)

    fun update(name: String, persona: String, seed: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { container.bots.update(botId, name, persona, seed) }
                .onSuccess { onDone() }
                .onFailure { notice.value = it.message ?: "The bot could not be saved." }
        }
    }

    fun delete(onDone: () -> Unit) {
        container.crew.stop(botId)
        viewModelScope.launch {
            container.bots.delete(botId)
            onDone()
        }
    }

    fun addRoutine(draft: RoutineDraft, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val bot = container.bots.find(botId)
            if (bot == null || !draft.valid) {
                notice.value = "Enter a name and a prompt."
                onDone(false)
                return@launch
            }
            container.bots.addRoutine(bot, draft)
            notice.value = "Routine scheduled."
            onDone(true)
        }
    }

    fun setRoutineEnabled(taskId: String, enabled: Boolean) = viewModelScope.launch {
        container.bots.setRoutineEnabled(taskId, enabled)
    }

    fun runNow(taskId: String) {
        container.cron.runNow(taskId)
        notice.value = "Run queued. It starts in a moment."
    }

    fun deleteRoutine(taskId: String) = viewModelScope.launch {
        container.bots.deleteRoutine(taskId)
    }
}
