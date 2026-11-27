package ai.daylight.assistant.domain

import androidx.room.Room
import ai.daylight.assistant.data.MemoryRepository
import ai.daylight.assistant.data.local.AssistantDatabase
import ai.daylight.assistant.data.local.MemoryEntity
import ai.daylight.assistant.data.remote.ChatRequest
import ai.daylight.assistant.data.remote.OpenRouterModel
import ai.daylight.assistant.data.remote.StreamEvent
import ai.daylight.assistant.data.remote.TextProviderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AutoMemoryExtractorTest {

    private lateinit var database: AssistantDatabase
    private lateinit var repository: MemoryRepository
    private val json = Json { ignoreUnknownKeys = true }

    private class FakeTextProviderClient(
        private val responseText: String
    ) : TextProviderClient {
        var calls = 0
        var lastRequest: ChatRequest? = null

        override suspend fun testKey(key: String): Result<Unit> = Result.success(Unit)
        override suspend fun models(key: String?): List<OpenRouterModel> = emptyList()
        override fun stream(key: String, request: ChatRequest): Flow<StreamEvent> {
            calls += 1
            lastRequest = request
            return flowOf(StreamEvent.Delta(responseText), StreamEvent.Done)
        }
    }

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
    fun shouldSkipTurnFiltersBlankShortAndAcks() {
        assertTrue(AutoMemoryParser.shouldSkipTurn(""))
        assertTrue(AutoMemoryParser.shouldSkipTurn("   "))
        assertTrue(AutoMemoryParser.shouldSkipTurn("hi"))
        assertTrue(AutoMemoryParser.shouldSkipTurn("ok"))
        assertTrue(AutoMemoryParser.shouldSkipTurn("Thank you!"))
        assertTrue(AutoMemoryParser.shouldSkipTurn("Sounds good."))
        assertTrue(AutoMemoryParser.shouldSkipTurn("got it"))
        assertFalse(AutoMemoryParser.shouldSkipTurn("I live in Zurich and prefer Kotlin."))
    }

    @Test
    fun parserHandlesValidAddAndUpdateActionsAndCodeFences() {
        val raw = """
            Here are the extracted facts:
            ```json
            {
              "actions": [
                { "op": "add", "category": "USER", "content": "Lives in Zurich" },
                { "op": "update", "id": "mem-1", "category": "PREFERENCE", "content": "Prefers Kotlin over Java" },
                { "op": "add", "category": "UNKNOWN_CAT", "content": "Has a dog named Pixel" }
              ]
            }
            ```
        """.trimIndent()

        val parsed = AutoMemoryParser.parse(raw, json)
        assertEquals(3, parsed.size)
        assertEquals(AutoMemoryParser.Op.ADD, parsed[0].op)
        assertEquals(MemoryEngine.Category.USER, parsed[0].category)
        assertEquals("Lives in Zurich", parsed[0].content)

        assertEquals(AutoMemoryParser.Op.UPDATE, parsed[1].op)
        assertEquals("mem-1", parsed[1].targetId)
        assertEquals(MemoryEngine.Category.PREFERENCE, parsed[1].category)
        assertEquals("Prefers Kotlin over Java", parsed[1].content)

        assertEquals(MemoryEngine.Category.OTHER, parsed[2].category)
    }

    @Test
    fun parserRejectsSecretsInvalidLengthsMissingUpdateIdsAndCapsAtThree() {
        val tooLong = "a".repeat(MemoryEngine.MAX_MEMORY_CHARS + 1)
        val raw = """
            {
              "actions": [
                { "op": "add", "category": "USER", "content": "short" },
                { "op": "add", "category": "USER", "content": "$tooLong" },
                { "op": "add", "category": "OTHER", "content": "API key is sk-1234567890abcdefghij" },
                { "op": "update", "category": "USER", "content": "Missing target id here" },
                { "op": "add", "category": "USER", "content": "Valid fact number one" },
                { "op": "add", "category": "PREFERENCE", "content": "Valid fact number two" },
                { "op": "add", "category": "GOAL", "content": "Valid fact number three" },
                { "op": "add", "category": "PROJECT", "content": "Valid fact number four" }
              ]
            }
        """.trimIndent()

        val parsed = AutoMemoryParser.parse(raw, json)
        assertEquals(AutoMemoryParser.MAX_ACTIONS_PER_TURN, parsed.size)
        assertEquals(
            listOf("Valid fact number one", "Valid fact number two", "Valid fact number three"),
            parsed.map { it.content }
        )
    }

    @Test
    fun parserReturnsEmptyOnMalformedJson() {
        assertTrue(AutoMemoryParser.parse("not json at all", json).isEmpty())
        assertTrue(AutoMemoryParser.parse("{ broken: [", json).isEmpty())
        assertTrue(AutoMemoryParser.parse("{\"actions\": \"wrong\"}", json).isEmpty())
    }

    @Test
    fun onTurnCompletedInsertsNewFactAndUpdatesExistingFact() = runBlocking {
        val dao = database.dao()
        dao.upsertMemory(
            MemoryEntity(
                id = "mem-city",
                content = "Lives in Geneva",
                category = "USER",
                sourceConversationId = "conv-0",
                createdAt = 100L,
                updatedAt = 100L,
                usageCount = 4,
                pinned = true
            )
        )

        val client = FakeTextProviderClient(
            """
            {
              "actions": [
                { "op": "update", "id": "mem-city", "category": "USER", "content": "Lives in Zurich" },
                { "op": "add", "category": "PREFERENCE", "content": "Prefers concise technical answers" }
              ]
            }
            """.trimIndent()
        )
        val savedConversations = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob())
        val extractor = AutoMemoryExtractor(
            scope = scope,
            memories = repository,
            isEnabled = { true },
            resolveCheapModel = {
                AutoMemoryModelTarget(
                    modelId = "meta-llama/llama-3.2-1b-instruct",
                    apiKey = "test-key",
                    client = client,
                    useOpenRouterPriceSort = true
                )
            },
            json = json,
            onMemorySaved = { savedConversations += it }
        )

        extractor.onTurnCompleted(
            conversationId = "conv-42",
            userText = "I actually moved to Zurich recently. Please keep answers concise.",
            assistantText = "Got it, I will keep things concise."
        )
        scope.coroutineContext.job.children.toList().forEach { it.join() }
        scope.cancel()

        assertEquals(1, client.calls)
        assertEquals("price", client.lastRequest?.provider?.sort)
        assertEquals(listOf("conv-42"), savedConversations)

        val all = repository.all().associateBy { it.content }
        val updated = all["Lives in Zurich"]!!
        assertEquals("mem-city", updated.id)
        assertTrue(updated.pinned)
        assertEquals(4, updated.usageCount)
        assertEquals("conv-42", updated.sourceConversationId)

        val added = all["Prefers concise technical answers"]!!
        assertEquals("PREFERENCE", added.category)
        assertEquals("conv-42", added.sourceConversationId)
    }

    @Test
    fun onTurnCompletedSkipsWhenDisabledAndFallsBackWhenNoTarget() = runBlocking {
        val client = FakeTextProviderClient("""{"actions":[{"op":"add","category":"USER","content":"Should not run"}]}""")
        val savedConversations = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob())

        val disabledExtractor = AutoMemoryExtractor(
            scope = scope,
            memories = repository,
            isEnabled = { false },
            resolveCheapModel = { AutoMemoryModelTarget("m", "k", client) },
            json = json,
            onMemorySaved = { savedConversations += it }
        )
        disabledExtractor.onTurnCompleted("conv-1", "My name is Alex and I live in Gilly.", "Nice to meet you!")
        scope.coroutineContext.job.children.toList().forEach { it.join() }

        assertEquals(0, client.calls)
        assertTrue(repository.all().isEmpty())

        val fallbackExtractor = AutoMemoryExtractor(
            scope = scope,
            memories = repository,
            isEnabled = { true },
            resolveCheapModel = { null },
            json = json,
            onMemorySaved = { savedConversations += it }
        )
        fallbackExtractor.onTurnCompleted("conv-2", "My name is Alex.", "Hello Alex!")
        scope.coroutineContext.job.children.toList().forEach { it.join() }
        scope.cancel()

        assertEquals(listOf("conv-2"), savedConversations)
        assertEquals(1, repository.all().size)
        assertEquals("My name is Alex", repository.all().first().content)
    }
}
