package ai.daylight.assistant.ui.find

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.daylight.assistant.AppContainer
import ai.daylight.assistant.domain.FindKind
import ai.daylight.assistant.domain.FindSize
import ai.daylight.assistant.domain.FindState
import ai.daylight.assistant.domain.FoundImage
import ai.daylight.assistant.domain.LicenceFilter
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
 * Find's screen state (C8). The search itself, its query and its results live in the
 * app-wide [ai.daylight.assistant.domain.FindSession], so switching arms keeps them.
 */
class FindViewModel(private val container: AppContainer) : ViewModel() {
    private val session = container.find
    val state: StateFlow<FindState> = session.state
    val draft: StateFlow<String> = session.draft

    /** Find is gated by "Allow web search". Null until the setting has loaded. */
    val searchEnabled: StateFlow<Boolean?> = container.preferences.state
        .map<_, Boolean?> { it.searchEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val imageLoader: ImageLoader get() = container.findImages

    /** Images saved during this visit, so Save can say "Saved". */
    val savedIds = MutableStateFlow<Set<Long>>(emptySet())

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

    /** The "Allow web search" button on Find's off state: the same switch as in Tune. It runs what was typed. */
    fun allowSearch() {
        viewModelScope.launch {
            container.preferences.setSearchEnabled(true)
            session.search(session.draft.value)
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

    fun markSaved(id: Long) {
        savedIds.update { it + id }
    }
}
