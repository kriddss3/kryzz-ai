package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.MediaModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Make renders. Each kind runs the existing agent capability of the same name. */
enum class MakeKind(val capability: AgentCapability, val purpose: ModelPurpose) {
    IMAGE(AgentCapability.IMAGE, ModelPurpose.IMAGE),
    VIDEO(AgentCapability.VIDEO, ModelPurpose.VIDEO);

    companion object {
        fun from(value: String?): MakeKind? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

enum class MakeOutcome { RUNNING, DONE, FAILED, STOPPED }

/**
 * One render started from Make. [conversationId] is null for the moment between the start
 * and the new conversation being created. [model] is the model the render was sent to.
 */
data class MakeRender(
    val serial: Int,
    val kind: MakeKind,
    val prompt: String,
    val ratio: String?,
    val model: String,
    val startedAt: Long,
    val conversationId: String? = null,
    val outcome: MakeOutcome = MakeOutcome.RUNNING,
    val finishedAt: Long? = null,
    val error: String? = null,
) {
    val running: Boolean get() = outcome == MakeOutcome.RUNNING
}

/**
 * Runs Make's renders outside any screen (C7). Generation used to live in a chat screen's
 * ViewModel, so leaving the screen cancelled it; here it runs in the app's scope, so "Run in
 * background" is true: the render keeps going while you use other arms and lands in the
 * history as usual. One render at a time.
 *
 * [render] is the existing agent generation (AgentExecutor.send with mode AGENT and the
 * kind's capability), so Make adds no new generation path.
 */
class MakeStudio(
    private val scope: CoroutineScope,
    private val newConversation: suspend () -> String,
    private val render: suspend (conversationId: String, prompt: String, kind: MakeKind, options: MediaOptions) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _current = MutableStateFlow<MakeRender?>(null)

    /** The render running now, or the last one and how it ended. */
    val current: StateFlow<MakeRender?> = _current.asStateFlow()

    /** Make's prompt, kind and chosen ratios, kept while the app runs so leaving Make loses nothing. */
    val draft = MutableStateFlow("")
    val kind = MutableStateFlow(MakeKind.IMAGE)
    val ratios = MutableStateFlow<Map<MakeKind, String>>(emptyMap())

    /** OpenRouter's video models with the ratios each takes, fetched once per app run. */
    val openRouterVideoModels = MutableStateFlow<List<MediaModel>?>(null)

    private var job: Job? = null
    private var serial = 0

    /**
     * Starts a render, in [conversationId] when given (Variant keeps the result's thread),
     * otherwise in a new conversation. Returns false when the prompt is blank or a render is
     * already running.
     */
    fun start(kind: MakeKind, prompt: String, ratio: String?, model: String, conversationId: String? = null): Boolean {
        val clean = prompt.trim()
        if (clean.isEmpty() || _current.value?.running == true) return false
        val id = ++serial
        _current.value = MakeRender(id, kind, clean, ratio, model, clock(), conversationId)
        job = scope.launch {
            try {
                val conversation = conversationId ?: newConversation()
                change(id) { it.copy(conversationId = conversation) }
                render(conversation, clean, kind, MediaOptions(ratio))
                change(id) { it.copy(outcome = MakeOutcome.DONE, finishedAt = clock()) }
            } catch (stop: CancellationException) {
                change(id) { it.copy(outcome = MakeOutcome.STOPPED, finishedAt = clock()) }
                throw stop
            } catch (error: Throwable) {
                change(id) { it.copy(outcome = MakeOutcome.FAILED, finishedAt = clock(), error = failureText(error)) }
            }
        }
        return true
    }

    /** Stops the running render. The history keeps a "stopped" note, as it does in Run. */
    fun cancel() {
        job?.cancel()
    }

    /** The provider's own words when the app wrapped them, plain words for a dropped connection. */
    private fun failureText(error: Throwable): String = when (error) {
        is AssistantApiException -> error.message
        is IOException -> "Couldn't reach the provider. Check your connection and try again."
        else -> error.message
    }?.takeIf(String::isNotBlank) ?: "The render failed. Check the model and your provider balance."

    private fun change(serial: Int, edit: (MakeRender) -> MakeRender) {
        _current.update { if (it?.serial == serial) edit(it) else it }
    }
}
