package ai.daylight.assistant.ui.inkwell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.daylight.assistant.AppContainer
import ai.daylight.assistant.domain.RecentOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Inkwell's data: every generated file across all conversations (the C7 query), the
 * seen mark as it stood when the Inkwell opened (for NEW tags), and which pieces were saved
 * here. Built per visit, so each visit reads the mark afresh.
 */
class InkwellViewModel(private val container: AppContainer) : ViewModel() {
    /** Null until the first read, so the screen doesn't flash its empty state. */
    val items: StateFlow<List<RecentOutput>?> = container.conversations.recentOutputs(InkwellModel.ANSWER_LIMIT)
        .map<List<RecentOutput>, List<RecentOutput>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _seenAtOnOpen = MutableStateFlow<Long?>(null)

    /** The seen mark before this visit moved it; pieces newer than it get a NEW tag. */
    val seenAtOnOpen: StateFlow<Long?> = _seenAtOnOpen.asStateFlow()

    private val _savedKeys = MutableStateFlow<Set<String>>(emptySet())
    val savedKeys: StateFlow<Set<String>> = _savedKeys.asStateFlow()

    init {
        viewModelScope.launch { _seenAtOnOpen.value = container.preferences.inkwellSeenAt.first() }
    }

    /** Everything up to [at] has been shown: Home's badge stops counting it. */
    fun markSeen(at: Long) {
        viewModelScope.launch {
            // Read the old mark first, so this visit's NEW tags still show.
            _seenAtOnOpen.first { it != null }
            container.preferences.markInkwellSeen(at)
        }
    }

    fun markSaved(key: String) {
        _savedKeys.value = _savedKeys.value + key
    }
}
