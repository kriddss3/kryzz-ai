package ai.daylight.assistant.ui.make

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.daylight.assistant.AppContainer
import ai.daylight.assistant.data.preferences.SettingsState
import ai.daylight.assistant.data.remote.MediaModel
import ai.daylight.assistant.domain.MakeKind
import ai.daylight.assistant.domain.MakeRender
import ai.daylight.assistant.domain.MakeSource
import ai.daylight.assistant.domain.MakeSources
import ai.daylight.assistant.domain.MakeStyle
import ai.daylight.assistant.domain.MediaJobStatus
import ai.daylight.assistant.domain.OutputKind
import ai.daylight.assistant.domain.RecentOutput
import java.io.File
import java.util.UUID
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
    val sources: StateFlow<List<MakeSource>> = studio.sources
    val composeRequests: StateFlow<Int> = studio.composeRequests
    val openRouterVideoModels: StateFlow<List<MediaModel>?> = studio.openRouterVideoModels
    val miniMaxImageModels: List<MediaModel> = container.minimax.imageModels()
    val miniMaxVideoModels: List<MediaModel> = container.minimax.videoModels()

    /** A file picked from recent ink. It wins over the render that just finished. */
    val selected = MutableStateFlow<RecentOutput?>(null)

    /** True while the prompt is being written: the result steps aside for the frame and dials. */
    val composing = MutableStateFlow(false)

    /** The last failed or stopped render whose notice the user has moved past. */
    val dismissedSerial = MutableStateFlow(0)

    /** Why the last photo or clip could not be added, until the next change. */
    val sourceNote = MutableStateFlow<String?>(null)

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
            if (studio.start(kind, text, ratio, settings.makeModel(kind), style = settings.makeStyle, sources = studio.sources.value)) {
                sourceNote.value = null
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

    /** Shows the composer with externally staged sources instead of a previously finished render. */
    fun enterComposeFromExternal() {
        selected.value = null
        composing.value = true
        sourceNote.value = null
    }

    fun markSaved(outputId: String) {
        savedIds.update { it + outputId }
    }

    /** Copies photos and clips from the gallery onto the draft, up to [MakeSources.MAX]. */
    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty() || render.value?.running == true) return
        viewModelScope.launch {
            val room = MakeSources.MAX - studio.sources.value.size
            if (room <= 0) {
                sourceNote.value = "You can add ${MakeSources.MAX} photos or clips."
                return@launch
            }
            val imported = runCatching { container.attachments.importUris(uris.distinct().take(room)) }
            val files = imported.getOrElse { error ->
                val raw = error.message.orEmpty()
                sourceNote.value = if (raw.contains("larger", ignoreCase = true) || raw.contains("limited", ignoreCase = true)) {
                    "Each photo or clip has to be under 20 MB."
                } else {
                    raw.take(160).ifBlank { "That file couldn't be added." }
                }
                return@launch
            }
            val accepted = mutableListOf<MakeSource>()
            var skipped = false
            files.forEach { file ->
                val path = file.localPath
                val source = MakeSource(file.id, file.name, file.mimeType, path.orEmpty())
                val length = path?.let { File(it).length() } ?: 0L
                if (source.usable && length in 1..MakeSources.MAX_BYTES) {
                    accepted += source
                } else {
                    container.attachments.discardCopy(path)
                    skipped = true
                }
            }
            if (accepted.isEmpty()) {
                sourceNote.value = "Photos and clips only, each under 20 MB."
                return@launch
            }
            studio.sources.update { current ->
                (current + accepted).distinctBy { it.localPath }.takeLast(MakeSources.MAX)
            }
            sourceNote.value = if (skipped || uris.distinct().size > room) {
                "Some files were left out. Photos and clips only, up to ${MakeSources.MAX}, each under 20 MB."
            } else {
                null
            }
        }
    }

    fun removeSource(id: String) {
        if (studio.sources.value.none { it.id == id }) return
        studio.sources.update { it.filterNot { source -> source.id == id } }
        sourceNote.value = null
    }

    /**
     * Puts a finished image or clip on the draft and opens the prompt, so the next render
     * edits it or uses it as the start of a new one.
     */
    fun useMade(result: RecentOutput) {
        if (render.value?.running == true) return
        val output = result.output
        val file = output.localPath?.let(::File)?.takeIf { it.isFile }
        if (file == null) {
            sourceNote.value = "That file isn't on this phone."
            edit(result)
            return
        }
        if (file.length() > MakeSources.MAX_BYTES) {
            sourceNote.value = "That file is too large to send. Use one under 20 MB."
            edit(result)
            return
        }
        val mime = output.mimeType.substringBefore(';').ifBlank {
            if (output.kind == OutputKind.VIDEO) "video/mp4" else "image/jpeg"
        }
        val source = MakeSource(UUID.randomUUID().toString(), output.fileName, mime, file.absolutePath)
        if (source.usable) {
            studio.sources.update { current ->
                if (current.any { it.localPath == source.localPath }) current
                else (current + source).takeLast(MakeSources.MAX)
            }
            sourceNote.value = null
        }
        edit(result)
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
