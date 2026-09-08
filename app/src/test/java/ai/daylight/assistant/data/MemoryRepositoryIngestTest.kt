package ai.daylight.assistant.data

import androidx.room.Room
import ai.daylight.assistant.data.local.AssistantDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MemoryRepositoryIngestTest {
    private lateinit var database: AssistantDatabase
    private lateinit var repository: MemoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AssistantDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = MemoryRepository(database.dao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ingestPersistsExtractableMemoryAndObserveReturnsIt() = runBlocking {
        val saved = repository.ingest("Hey, my name is Alex and I live in Gilly. I really like JDM cars.", "conv-1")
        assertThat(saved).isTrue()

        // The Memory tab renders exactly this flow, so what it shows == what was saved.
        val observed = repository.observe("").first()
        assertThat(observed).hasSize(3)

        val contents = observed.map { it.content }
        // Compound first-person clauses are split into their own facts so each one
        // surfaces in the Memory tab instead of being swallowed by the first signal.
        assertThat(contents).contains("Hey, my name is Alex")
        assertThat(contents).contains("I live in Gilly")
        assertThat(contents).contains("I really like JDM cars")
    }

    @Test
    fun ingestDeduplicatesAcrossCalls() = runBlocking {
        repository.ingest("my name is Alex", "conv-1")
        repository.ingest("My name is Alex.", "conv-2")
        assertThat(repository.observe("").first()).hasSize(1)
    }

    @Test
    fun ingestWithoutMatchingPatternSavesNothing() = runBlocking {
        val saved = repository.ingest("What's the weather today?", "conv-1")
        assertThat(saved).isFalse()
        assertThat(repository.observe("").first()).isEmpty()
    }
}
