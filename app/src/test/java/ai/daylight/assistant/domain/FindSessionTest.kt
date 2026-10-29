package ai.daylight.assistant.domain

import ai.daylight.assistant.data.remote.AssistantApiException
import ai.daylight.assistant.data.remote.ErrorKind
import com.google.common.truth.Truth.assertThat
import java.net.UnknownHostException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** C8: Find's search keeps its results across arms, filters licences and pages through Commons. */
@OptIn(ExperimentalCoroutinesApi::class)
class FindSessionTest {
    private data class Call(val query: String, val filters: FindFilters, val offset: Int)

    private val reusable = ImageLicence.of("CC BY-SA 4.0", "cc-by-sa-4.0")
    private val gfdl = ImageLicence.of("GFDL", "gfdl")

    private fun image(id: Long, licence: ImageLicence = reusable) = FoundImage(
        id = id, fileTitle = "File:$id.jpg", title = "Image $id", author = "A", attribution = null, licence = licence,
        pageUrl = "https://commons.wikimedia.org/wiki/File:$id.jpg", originalUrl = "o", thumbUrl = "t", viewUrl = "v",
        saveUrl = "s", thumbWidth = 500, thumbHeight = 400, width = 1000, height = 800, bytes = 1, mime = "image/jpeg"
    )

    private fun session(
        scope: TestScope,
        calls: MutableList<Call>,
        pages: Map<Int, FindPage> = emptyMap(),
        gate: CompletableDeferred<Unit>? = null,
        failWith: Throwable? = null,
    ) = FindSession(scope.backgroundScope) { query, filters, offset ->
        calls += Call(query, filters, offset)
        gate?.await()
        failWith?.let { throw it }
        pages[offset] ?: FindPage(emptyList(), null)
    }

    @Test fun aSearchShowsReusableResultsAndCountsTheHiddenOnes() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val gate = CompletableDeferred<Unit>()
        val find = session(this, calls, mapOf(0 to FindPage(listOf(image(1), image(2, gfdl), image(3)), 24)), gate)

        assertThat(find.search("  lighthouses   at night ")).isTrue()
        assertThat(find.state.value.status).isEqualTo(FindStatus.SEARCHING)
        assertThat(find.state.value.query).isEqualTo("lighthouses at night")
        gate.complete(Unit)
        runCurrent()

        val done = find.state.value
        assertThat(done.status).isEqualTo(FindStatus.DONE)
        assertThat(done.images.map { it.id }).containsExactly(1L, 3L).inOrder()
        assertThat(done.filteredOut).isEqualTo(1)
        assertThat(done.nextOffset).isEqualTo(24)
        assertThat(calls.single()).isEqualTo(Call("lighthouses at night", FindFilters(), 0))
    }

    @Test fun aBlankQueryDoesNothing() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val find = session(this, calls)
        assertThat(find.search("   ")).isFalse()
        runCurrent()
        assertThat(calls).isEmpty()
        assertThat(find.state.value.status).isEqualTo(FindStatus.IDLE)
    }

    @Test fun aPageTheFilterEmptiesIsSkippedUpToThreePages() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val hidden = FindPage(listOf(image(1, gfdl)), 24)
        val find = session(
            this, calls,
            mapOf(0 to hidden, 24 to FindPage(listOf(image(2, gfdl)), 48), 48 to FindPage(listOf(image(3)), 72))
        )
        find.search("maps")
        runCurrent()
        assertThat(calls.map { it.offset }).containsExactly(0, 24, 48).inOrder()
        assertThat(find.state.value.images.map { it.id }).containsExactly(3L)
        assertThat(find.state.value.filteredOut).isEqualTo(2)

        // Only hidden results for three pages: the search ends empty but can go on.
        val calls2 = mutableListOf<Call>()
        val stuck = session(
            this, calls2,
            mapOf(0 to hidden, 24 to FindPage(listOf(image(2, gfdl)), 48), 48 to FindPage(listOf(image(4, gfdl)), 72))
        )
        stuck.search("maps")
        runCurrent()
        assertThat(calls2).hasSize(3)
        assertThat(stuck.state.value.images).isEmpty()
        assertThat(stuck.state.value.nextOffset).isEqualTo(72)
    }

    @Test fun loadMoreAppendsNewImagesOnlyAndStopsAtTheEnd() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val find = session(
            this, calls,
            mapOf(
                0 to FindPage(listOf(image(1), image(2)), 2),
                // Commons can repeat an image across pages; it is shown once.
                2 to FindPage(listOf(image(2), image(3)), null)
            )
        )
        find.search("ferry")
        runCurrent()
        assertThat(find.state.value.canLoadMore).isTrue()
        find.loadMore()
        assertThat(find.state.value.loadingMore).isTrue()
        // A second ask while one is loading is ignored.
        find.loadMore()
        runCurrent()
        assertThat(calls.map { it.offset }).containsExactly(0, 2).inOrder()
        assertThat(find.state.value.images.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(find.state.value.nextOffset).isNull()
        assertThat(find.state.value.canLoadMore).isFalse()
        find.loadMore()
        runCurrent()
        assertThat(calls).hasSize(2)
    }

    @Test fun aNewFilterReRunsTheSearchAndANewSearchReplacesTheOld() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val find = session(this, calls, mapOf(0 to FindPage(listOf(image(1)), null)))
        // Before any search a filter is just kept.
        find.setFilters(FindFilters(size = FindSize.LARGE))
        runCurrent()
        assertThat(calls).isEmpty()
        assertThat(find.state.value.filters.size).isEqualTo(FindSize.LARGE)

        find.search("owl")
        runCurrent()
        find.setFilters(find.state.value.filters.copy(kind = FindKind.DRAWINGS))
        runCurrent()
        assertThat(calls.map { it.filters.kind }).containsExactly(FindKind.IMAGES, FindKind.DRAWINGS).inOrder()
        assertThat(calls.last().filters.size).isEqualTo(FindSize.LARGE)
        assertThat(calls.last().query).isEqualTo("owl")
        // The same filters again change nothing.
        find.setFilters(find.state.value.filters)
        runCurrent()
        assertThat(calls).hasSize(2)
    }

    @Test fun aSlowOldSearchNeverOverwritesANewerOne() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val gate = CompletableDeferred<Unit>()
        val find = FindSession(backgroundScope) { query, filters, offset ->
            calls += Call(query, filters, offset)
            if (query == "slow") gate.await()
            FindPage(listOf(image(if (query == "slow") 1 else 2)), null)
        }
        find.search("slow")
        runCurrent()
        find.search("fast")
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertThat(find.state.value.query).isEqualTo("fast")
        assertThat(find.state.value.images.map { it.id }).containsExactly(2L)
    }

    @Test fun noConnectionSaysSoAndRetryRunsTheSearchAgain() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val find = session(this, calls, failWith = UnknownHostException("commons.wikimedia.org"))
        find.search("ferry")
        runCurrent()
        val failed = find.state.value
        assertThat(failed.status).isEqualTo(FindStatus.FAILED)
        assertThat(failed.offline).isTrue()
        assertThat(failed.error).contains("Couldn't reach Wikimedia Commons")
        find.retry()
        runCurrent()
        assertThat(calls).hasSize(2)
    }

    @Test fun aBusyCommonsAndOtherErrorsReadPlainly() {
        assertThat(FindSession.failure(AssistantApiException(ErrorKind.RATE_LIMIT, "429", 429)).first)
            .isEqualTo("Wikimedia Commons is busy. Try again in a minute.")
        assertThat(FindSession.failure(AssistantApiException(ErrorKind.NETWORK, "Commons search failed (HTTP 404).", 404)))
            .isEqualTo("Commons search failed (HTTP 404)." to false)
        assertThat(FindSession.failure(IllegalStateException("")).first).isEqualTo("The search failed. Try again.")
    }

    @Test fun aFailedNextPageKeepsTheShownImages() = runTest(StandardTestDispatcher()) {
        var fail = false
        val find = FindSession(backgroundScope) { _, _, offset ->
            if (fail) throw UnknownHostException("x")
            FindPage(listOf(image(offset + 1L)), offset + 1)
        }
        find.search("owl")
        runCurrent()
        fail = true
        find.loadMore()
        runCurrent()
        val state = find.state.value
        assertThat(state.status).isEqualTo(FindStatus.DONE)
        assertThat(state.images.map { it.id }).containsExactly(1L)
        assertThat(state.moreError).isNotNull()
        // It won't ask again by itself; Retry does.
        assertThat(state.canLoadMore).isFalse()
        fail = false
        find.retry()
        runCurrent()
        assertThat(find.state.value.images.map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(find.state.value.moreError).isNull()
    }

    @Test fun clearAndStopEmptyFindButKeepTheFilters() = runTest(StandardTestDispatcher()) {
        val calls = mutableListOf<Call>()
        val gate = CompletableDeferred<Unit>()
        val find = session(this, calls, mapOf(0 to FindPage(listOf(image(1)), null)), gate)
        find.draft.value = "owl"
        find.setFilters(FindFilters(licence = LicenceFilter.ANY))
        find.search()
        runCurrent()
        // Web search switched off mid-search: nothing lands afterwards.
        find.stop()
        gate.complete(Unit)
        runCurrent()
        assertThat(find.state.value.status).isEqualTo(FindStatus.IDLE)
        assertThat(find.state.value.images).isEmpty()
        assertThat(find.state.value.filters.licence).isEqualTo(LicenceFilter.ANY)
        assertThat(find.draft.value).isEqualTo("owl")

        find.clear()
        assertThat(find.draft.value).isEmpty()
        assertThat(find.state.value.filters.licence).isEqualTo(LicenceFilter.ANY)
    }

    @Test fun resultsStopAtTheCap() = runTest(StandardTestDispatcher()) {
        val find = FindSession(backgroundScope) { _, _, offset ->
            FindPage((0 until 50).map { image(offset + it.toLong()) }, offset + 50)
        }
        find.search("many")
        runCurrent()
        repeat(10) {
            find.loadMore()
            runCurrent()
        }
        assertThat(find.state.value.images).hasSize(FindSession.MAX_RESULTS)
        assertThat(find.state.value.nextOffset).isNull()
    }
}
