package ai.daylight.assistant.ui.find

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.daylight.assistant.AppContainer
import ai.daylight.assistant.domain.FindKind
import ai.daylight.assistant.domain.FindMode
import ai.daylight.assistant.domain.FindSize
import ai.daylight.assistant.domain.FindState
import ai.daylight.assistant.domain.FindWebState
import ai.daylight.assistant.domain.FoundImage
import ai.daylight.assistant.domain.LicenceFilter
import ai.daylight.assistant.domain.MakeSource
import ai.daylight.assistant.domain.MakeStudio.StageSourceResult
import coil.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Find's screen state (C8). Image search lives in [ai.daylight.assistant.domain.FindSession]
 * and web browsing lives in [ai.daylight.assistant.domain.FindWebSession], so switching arms
 * keeps both intact.
 */
class FindViewModel(private val container: AppContainer) : ViewModel() {
    private val session = container.find
    val state: StateFlow<FindState> = session.state
    val draft: StateFlow<String> = session.draft

    val mode = MutableStateFlow(FindMode.IMAGES)
    val webState: StateFlow<FindWebState> = container.findWeb.state
    val webDraft: MutableStateFlow<String> = container.findWeb.draft

    /** Find is gated by "Allow web search". Null until the setting has loaded. */
    val searchEnabled: StateFlow<Boolean?> = container.preferences.state
        .map<_, Boolean?> { it.searchEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val imageLoader: ImageLoader get() = container.findImages

    /** Images saved during this visit, so Save can say "Saved". */
    val savedIds = MutableStateFlow<Set<Long>>(emptySet())

    enum class UseInMakeOutcome { ADDED, DUPLICATE, BUSY, FULL, FAILED }

    fun setMode(newMode: FindMode) {
        mode.value = newMode
    }

    fun setDraft(text: String) {
        session.draft.value = text
    }

    fun search(): Boolean = session.search(session.draft.value)

    fun setKind(kind: FindKind) = session.setFilters(state.value.filters.copy(kind = kind))

    fun setLicence(licence: LicenceFilter) = session.setFilters(state.value.filters.copy(licence = licence))

    fun setSize(size: FindSize) = session.setFilters(state.value.filters.copy(size = size))

    fun loadMore() = session.loadMore()

    fun retry() = session.retry()

    fun clear() = session.clear()

    fun stop() = session.stop()

    fun setWebDraft(text: String) {
        container.findWeb.draft.value = text
    }

    fun searchWeb() = container.findWeb.search()

    fun retryWeb() = container.findWeb.retry()

    fun stopWeb() = container.findWeb.stop()

    fun clearWeb() = container.findWeb.clear()

    /** The "Allow web search" button on Find's off state: the same switch as in Tune. It runs what was typed. */
    fun allowSearch() {
        viewModelScope.launch {
            container.preferences.setSearchEnabled(true)
            if (mode.value == FindMode.WEB) {
                container.findWeb.search()
            } else {
                session.search(session.draft.value)
            }
        }
    }

    /** Downloads [image] for Save or Share (cached), with the credit stamped in. Null when it failed. */
    internal suspend fun fileOrNull(context: Context, image: FoundImage): FindFile? = try {
        withContext(Dispatchers.IO) { FindMedia.fetch(context.applicationContext, container.commons, image) }
    } catch (stop: CancellationException) {
        throw stop
    } catch (_: Throwable) {
        null
    }

    /** Stages [image] into durable storage and adds it to Make's draft sources. */
    suspend fun useInMake(context: Context, image: FoundImage): UseInMakeOutcome {
        val file = try {
            FindMedia.stageForMake(context, image, container.commons)
        } catch (stop: CancellationException) {
            throw stop
        } catch (_: Throwable) {
            return UseInMakeOutcome.FAILED
        }
        val mime = if (image.mime.contains("svg", ignoreCase = true)) {
            "image/png"
        } else {
            val resolved = image.mime.ifBlank { "image/jpeg" }
            if (resolved == "image/tiff" && file.extension.isNotBlank()) {
                FindModel.mimeForExtension(file.extension)
            } else {
                resolved
            }
        }
        val ext = file.extension.ifBlank { FindModel.extensionFor(mime) }
        val source = MakeSource(
            id = "find-${image.id}",
            name = FindModel.displayName(image, ext),
            mimeType = mime,
            localPath = file.absolutePath,
        )
        return when (container.make.stageExternalSource(source)) {
            StageSourceResult.ADDED -> UseInMakeOutcome.ADDED
            StageSourceResult.DUPLICATE -> UseInMakeOutcome.DUPLICATE
            StageSourceResult.BUSY -> UseInMakeOutcome.BUSY
            StageSourceResult.FULL -> UseInMakeOutcome.FULL
        }
    }

    fun markSaved(id: Long) {
        savedIds.update { it + id }
    }
}
