package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ErrorKind
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Find looks for. Commons calls these file types "bitmap" and "drawing". */
enum class FindKind(val label: String, val detail: String) {
    IMAGES("Images", "Photos, paintings and scans"),
    DRAWINGS("Drawings", "Icons, maps and diagrams, saved as PNG");

    /** The noun for counts: "12 images", "1 drawing". */
    fun noun(count: Int): String = when (this) {
        IMAGES -> if (count == 1) "image" else "images"
        DRAWINGS -> if (count == 1) "drawing" else "drawings"
    }
}

/** Find's size filter, by overall resolution (Commons measures about the square root of width x height). */
enum class FindSize(val label: String, val detail: String) {
    ANY("Any size", "No limit"),
    MEDIUM("Medium", "About 1 to 4 megapixels"),
    LARGE("Large", "About 4 megapixels and up"),
}

data class FindFilters(
    val kind: FindKind = FindKind.IMAGES,
    val licence: LicenceFilter = LicenceFilter.REUSABLE,
    val size: FindSize = FindSize.ANY,
)

/**
 * One image Find found. Every image carries its source and licence, so nothing is ever
 * shown without them (SCREENS.md section 4). [thumbUrl] is the grid tile (500px),
 * [viewUrl] the large view and [saveUrl] what Save and Share download.
 */
data class FoundImage(
    val id: Long,
    val fileTitle: String,
    val title: String,
    val author: String?,
    val attribution: String?,
    val licence: ImageLicence,
    val pageUrl: String,
    val originalUrl: String,
    val thumbUrl: String,
    val viewUrl: String,
    val saveUrl: String,
    val thumbWidth: Int,
    val thumbHeight: Int,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val mime: String,
    val domain: String = "commons.wikimedia.org",
) {
    /** The tile's shape, kept between very tall and very wide so the grid stays usable. */
    val aspect: Float
        get() {
            val w = if (thumbWidth > 0) thumbWidth else width
            val h = if (thumbHeight > 0) thumbHeight else height
            return if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.45f, 2.2f) else 1f
        }

    val credit: String get() = CreditLine.build(title, author, licence, pageUrl, attribution)

    /** An SVG drawing: shown and saved as a PNG rendition. */
    val isDrawing: Boolean get() = mime == "image/svg+xml"
}

/** One page of results; [nextOffset] is null when there are no more. */
data class FindPage(val images: List<FoundImage>, val nextOffset: Int?)

enum class FindStatus { IDLE, SEARCHING, DONE, FAILED }

/**
 * Find's screen state. [filteredOut] counts results the licence filter hid, so an empty
 * search can say why. [moreError] is a failed "load more" (the shown images stay).
 */
data class FindState(
    val serial: Int = 0,
    val query: String = "",
    val filters: FindFilters = FindFilters(),
    val status: FindStatus = FindStatus.IDLE,
    val images: List<FoundImage> = emptyList(),
    val nextOffset: Int? = null,
    val loadingMore: Boolean = false,
    val filteredOut: Int = 0,
    val error: String? = null,
    val offline: Boolean = false,
    val moreError: String? = null,
) {
    val canLoadMore: Boolean
        get() = status == FindStatus.DONE && nextOffset != null && !loadingMore && moreError == null &&
            images.size < FindSession.MAX_RESULTS
}

/**
 * Find's search (C8), kept in the app while it runs so switching arms keeps the query and
 * the results. [search] fetches one page; the licence filter is applied here, because
 * Commons can't filter by licence itself. A page the filter empties is skipped (up to
 * [PAGES_PER_FETCH] pages per fetch) so a search never stalls on a run of hidden results.
 */
class FindSession(
    private val scope: CoroutineScope,
    private val search: suspend (query: String, filters: FindFilters, offset: Int) -> FindPage,
) {
    private val _state = MutableStateFlow(FindState())
    val state: StateFlow<FindState> = _state.asStateFlow()

    /** The query field's text, kept apart from the searched [FindState.query]. */
    val draft = MutableStateFlow("")

    private var job: Job? = null
    private var serial = 0

    /** Starts a new search. Returns false for a blank query. */
    fun search(query: String = draft.value, filters: FindFilters = _state.value.filters): Boolean {
        val clean = query.replace(Regex("\\s+"), " ").trim().take(MAX_QUERY)
        if (clean.isEmpty()) return false
        job?.cancel()
        val id = ++serial
        _state.value = FindState(serial = id, query = clean, filters = filters, status = FindStatus.SEARCHING)
        job = scope.launch { fetch(id, clean, filters, offset = 0, append = false) }
        return true
    }

    /** New filters re-run the current search; before any search they are just kept. */
    fun setFilters(filters: FindFilters) {
        val now = _state.value
        if (filters == now.filters) return
        if (now.query.isNotEmpty() && now.status != FindStatus.IDLE) {
            search(now.query, filters)
        } else {
            _state.update { it.copy(filters = filters) }
        }
    }

    /** The next page, when there is one and nothing is loading. */
    fun loadMore() {
        val now = _state.value
        if (!now.canLoadMore) return
        val offset = now.nextOffset ?: return
        _state.update { it.copy(loadingMore = true) }
        job = scope.launch { fetch(now.serial, now.query, now.filters, offset, append = true) }
    }

    /** Tries again after a failure: the whole search, or the page that didn't load. */
    fun retry() {
        val now = _state.value
        when {
            now.status == FindStatus.FAILED -> search(now.query, now.filters)
            now.moreError != null -> {
                _state.update { it.copy(moreError = null) }
                loadMore()
            }
        }
    }

    /** Back to an empty Find, keeping the filters. */
    fun clear() {
        job?.cancel()
        serial++
        draft.value = ""
        _state.update { FindState(serial = serial, filters = it.filters) }
    }

    /** Stops a running search (web search was switched off) and drops the results. */
    fun stop() {
        job?.cancel()
        serial++
        _state.update { FindState(serial = serial, filters = it.filters) }
    }

    private suspend fun fetch(id: Int, query: String, filters: FindFilters, offset: Int, append: Boolean) {
        var next: Int? = offset
        val added = mutableListOf<FoundImage>()
        var hidden = 0
        var pages = 0
        try {
            val seen = if (append) _state.value.images.mapTo(HashSet()) { it.id } else HashSet()
            while (added.isEmpty() && pages < PAGES_PER_FETCH) {
                val at = next ?: break
                val page = search(query, filters, at)
                pages++
                val fresh = page.images.filter { seen.add(it.id) }
                val kept = fresh.filter { filters.licence.accepts(it.licence) }
                hidden += fresh.size - kept.size
                added += kept
                // A page with nothing new means Commons has run out.
                val more = page.nextOffset
                next = if (fresh.isNotEmpty() && more != null && more > at) more else null
            }
            val last = next
            change(id) {
                val images = (if (append) it.images + added else added).take(MAX_RESULTS)
                it.copy(
                    status = FindStatus.DONE,
                    images = images,
                    nextOffset = last.takeIf { images.size < MAX_RESULTS },
                    loadingMore = false,
                    filteredOut = it.filteredOut + hidden,
                    error = null,
                    offline = false,
                    moreError = null
                )
            }
        } catch (stop: CancellationException) {
            throw stop
        } catch (error: Throwable) {
            val (text, offline) = failure(error)
            change(id) {
                if (append) {
                    it.copy(loadingMore = false, moreError = text)
                } else {
                    it.copy(status = FindStatus.FAILED, error = text, offline = offline, loadingMore = false)
                }
            }
        }
    }

    private fun change(id: Int, edit: (FindState) -> FindState) {
        _state.update { if (it.serial == id) edit(it) else it }
    }

    companion object {
        const val MAX_QUERY = 200
        const val MAX_RESULTS = 240
        const val PAGES_PER_FETCH = 3

        /** Plain words for what went wrong, and whether it looks like no connection. */
        fun failure(error: Throwable): Pair<String, Boolean> = when (error) {
            is AssistantApiException -> when (error.kind) {
                ErrorKind.RATE_LIMIT -> "Wikimedia Commons is busy. Try again in a minute."
                else -> error.message
            } to false
            is IOException -> "Couldn't reach Wikimedia Commons. Check your connection and try again." to true
            else -> (error.message?.takeIf(String::isNotBlank) ?: "The search failed. Try again.") to false
        }
    }
}
