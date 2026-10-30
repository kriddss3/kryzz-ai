package ai.daylight.assistant.ui.make

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.daylight.assistant.AppContainer
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.domain.MakeKind
import ai.daylight.assistant.domain.MakeRender
import ai.daylight.assistant.domain.MakeStyle
import ai.daylight.assistant.domain.MediaJobStatus
import ai.daylight.assistant.domain.RecentOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The model a kind renders with. */
internal fun SettingsState.makeModel(kind: MakeKind): String = if (kind == MakeKind.VIDEO) videoModel else imageModel

/**
 * Make's screen state (C7). The render itself, the prompt draft, the kind and the chosen
 * ratios live in the app-wide [ai.daylight.assistant.domain.MakeStudio], so they survive
 * leaving the screen; this ViewModel only holds what is on show.
 */
class MakeViewModel(private val container: AppContainer) : ViewModel() {
    private val studio = container.make
    val settings: StateFlow<SettingsState> = container.preferences.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())
    val render: StateFlow<MakeRender?> = studio.current
    val jobStatus: StateFlow<MediaJobStatus?> = container.agent.mediaStatus
    /** Null until the first read, so a finished render isn't briefly drawn as an empty Make. */
    val recent: StateFlow<List<RecentOutput>?> = container.conversations.recentOutputs(MakeModel.RECENT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val kind: StateFlow<MakeKind> = studio.kind
    val prompt: StateFlow<String> = studio.draft
    val ratios: StateFlow<Map<MakeKind, String>> = studio.ratios
    val openRouterVideoModels: StateFlow<List<MediaModel>?> = studio.openRouterVideoModels
    val miniMaxImageModels: List<MediaModel> = container.minimax.imageModels()
    val miniMaxVideoModels: List<MediaModel> = container.minimax.videoModels()

    /** A file picked from recent ink. It wins over the render that just finished. */
    val selected = MutableStateFlow<RecentOutput?>(null)

    /** True while the prompt is being written: the result steps aside for the frame and dials. */
    val composing = MutableStateFlow(false)

    /** The last failed or stopped render whose notice the user has moved past. */
    val dismissedSerial = MutableStateFlow(0)

    /** Outputs saved to the gallery during this visit, so Save can say "Saved". */
    val savedIds = MutableStateFlow<Set<String>>(emptySet())

    private var loadingVideoModels = false

    fun setKind(kind: MakeKind) {
        if (render.value?.running == true) return
        studio.kind.value = kind
        dismissNotice()
    }

    fun setPrompt(text: String) {
        studio.draft.value = text
        dismissNotice()
    }

    fun setRatio(kind: MakeKind, ratio: String) {
        studio.ratios.update { it + (kind to ratio) }
    }

    /** v2.1: Plain or the Nothing OS style; kept in settings, so it stays on. */
    fun setStyle(style: MakeStyle) = viewModelScope.launch { container.preferences.setMakeStyle(style) }

    /** Starts a render of the draft prompt. */
    fun run(ratio: String?) {
        val kind = kind.value
        val text = prompt.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val settings = container.preferences.state.first()
            if (studio.start(kind, text, ratio, settings.makeModel(kind), style = settings.makeStyle)) {
                selected.value = null
                composing.value = false
            }
        }
    }

    /** Variant: the same prompt again, in the same conversation as [result]. */
    fun variant(result: RecentOutput, ratio: String?) {
        val kind = MakeModel.kindOf(result.output) ?: return
        val text = result.prompt ?: return
        viewModelScope.launch {
            val settings = container.preferences.state.first()
            if (studio.start(kind, text, ratio, settings.makeModel(kind), result.conversationId, settings.makeStyle)) {
                studio.kind.value = kind
                studio.draft.value = text
                selected.value = null
                composing.value = false
            }
        }
    }

    fun cancel() = studio.cancel()

    /** Shows a file from recent ink in the frame. */
    fun show(result: RecentOutput) {
        selected.value = result
        composing.value = false
        MakeModel.kindOf(result.output)?.let { if (render.value?.running != true) studio.kind.value = it }
        dismissNotice()
    }

    /** Back to writing: the prompt of [from] (when given) goes into the draft. */
    fun edit(from: RecentOutput? = null, kind: MakeKind? = null) {
        from?.prompt?.let { studio.draft.value = it }
        if (kind != null && render.value?.running != true) studio.kind.value = kind
        selected.value = null
        composing.value = true
        dismissNotice()
    }

    fun markSaved(outputId: String) {
        savedIds.update { it + outputId }
    }

    /** OpenRouter lists each video model's ratios; fetched once per app run (no key needed). */
    fun loadVideoModels() {
        if (studio.openRouterVideoModels.value != null || loadingVideoModels) return
        loadingVideoModels = true
        viewModelScope.launch {
            val models = withContext(Dispatchers.IO) {
                runCatching { container.openRouter.videoModels(container.credentials.openRouterKey()) }.getOrNull()
            }
            if (models != null) studio.openRouterVideoModels.value = models
            loadingVideoModels = false
        }
    }

    private fun dismissNotice() {
        val last = render.value ?: return
        if (!last.running) dismissedSerial.value = last.serial
    }
}
