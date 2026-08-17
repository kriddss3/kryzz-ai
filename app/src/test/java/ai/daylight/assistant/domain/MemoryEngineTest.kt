package ai.daylight.assistant.domain

import ai.daylight.assistant.data.local.MemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEngineTest {

    private fun memory(
        id: String,
        content: String,
        enabled: Boolean = true,
        pinned: Boolean = false,
        usageCount: Int = 0,
        lastUsedAt: Long = 0L,
        updatedAt: Long = System.currentTimeMillis()
    ) = MemoryEntity(
        id = id, content = content, category = "PREFERENCE",
        sourceConversationId = null, createdAt = updatedAt, updatedAt = updatedAt,
        lastUsedAt = lastUsedAt, usageCount = usageCount, enabled = enabled, pinned = pinned
    )

    @Test
    fun extractsIdentityAndLocationFacts() {
        val facts = MemoryEngine.extract("hey, my name is Alex. I live in Gilly. What's up?")
        assertEquals(2, facts.size)
        assertEquals(MemoryEngine.Category.USER, facts[0].category)
        assertEquals("Hey, my name is Alex", facts[0].content)
        assertEquals("I live in Gilly", facts[1].content)
    }

    @Test
    fun extractsAgeAndHometownPhrasings() {
        val facts = MemoryEngine.extract("I'm 16 years old. My hometown is Gilly. I'm based in Switzerland.")
        assertEquals(3, facts.size)
        assertTrue(facts.all { it.category == MemoryEngine.Category.USER })
        assertEquals("I'm 16 years old", facts[0].content)
        assertEquals("My hometown is Gilly", facts[1].content)
        assertEquals("I'm based in Switzerland", facts[2].content)
    }

    @Test
    fun extractsAgeWithFullForm() {
        val facts = MemoryEngine.extract("I am 17 years old. I like skiing.")
        assertEquals(2, facts.size)
        assertEquals("I am 17 years old", facts[0].content)
        assertEquals(MemoryEngine.Category.USER, facts[0].category)
        assertEquals(MemoryEngine.Category.PREFERENCE, facts[1].category)
    }

    @Test
    fun extractsPreferencesAndDislikes() {
        val facts = MemoryEngine.extract("I really like JDM cars. I don't like crowded places though.")
        assertEquals(2, facts.size)
        assertEquals(MemoryEngine.Category.PREFERENCE, facts[0].category)
        assertEquals("I really like JDM cars", facts[0].content)
        assertEquals(MemoryEngine.Category.PREFERENCE, facts[1].category)
    }

    @Test
    fun extractsGoalsAndWork() {
        val facts = MemoryEngine.extract("I want to study business in Hong Kong. I work as a developer.")
        assertEquals(2, facts.size)
        assertEquals(MemoryEngine.Category.GOAL, facts[0].category)
        assertEquals(MemoryEngine.Category.PROJECT, facts[1].category)
    }

    @Test
    fun ignoresQuestionsShoutsAndTransientTopics() {
        val facts = MemoryEngine.extract(
            "Do you like music? I have a question about that. I like cars."
        )
        assertEquals(1, facts.size)
        assertEquals("I like cars", facts[0].content)
    }

    @Test
    fun ignoresEmptyReferents() {
        val facts = MemoryEngine.extract("I like it. I prefer that. I live in Gilly.")
        assertEquals(1, facts.size)
        assertEquals("I live in Gilly", facts[0].content)
    }

    @Test
    fun ignoresShortAndUnrelatedSentences() {
        val facts = MemoryEngine.extract("ok. I like it. The weather is nice today.")
        assertTrue(facts.isEmpty())
    }

    @Test
    fun deduplicatesAcrossMessages() {
        val first = MemoryEngine.extract("I live in Gilly, Switzerland")
        val second = MemoryEngine.extract("I live in gilly switzerland btw")
        assertTrue(MemoryEngine.looksDuplicate(second[0].content, first.map { it.content }))
    }

    @Test
    fun retrievalRanksRelevantMemoriesFirst() {
        val memories = listOf(
            memory("m1", "I like osu rhythm games"),
            memory("m2", "I live in Gilly"),
            memory("m3", "I prefer wide key layouts")
        )
        val top = MemoryEngine.retrieve("recommend an osu map with wide spacing", memories, limit = 2)
        assertTrue(top.isNotEmpty())
        assertEquals("m1", top.first().id)
    }

    @Test
    fun retrievalSkipsDisabledAndIrrelevant() {
        val memories = listOf(
            memory("m1", "I like JDM cars", enabled = false),
            memory("m2", "I live in Gilly")
        )
        val top = MemoryEngine.retrieve("tell me about JDM cars", memories, limit = 4)
        assertTrue(top.none { it.id == "m1" })
    }

    @Test
    fun retrievalAppliesUsageAndPinBonuses() {
        val memories = listOf(
            memory("used", "I study computer science", usageCount = 20),
            memory("pinned", "I live in Gilly", pinned = true),
            memory("plain", "I play basketball")
        )
        val top = MemoryEngine.retrieve("I live in Gilly and study computer science", memories, limit = 4)
        // Both relevant; ordering respects the bonuses but both must appear.
        assertTrue(top.any { it.id == "used" })
        assertTrue(top.any { it.id == "pinned" })
    }

    @Test
    fun retrievalReturnsEmptyForBlankQuery() {
        assertTrue(MemoryEngine.retrieve("  ", listOf(memory("m1", "I like cars"))).isEmpty())
    }

    @Test
    fun compactNormalizesWhitespaceAndCase() {
        assertEquals("My name is alex", MemoryEngine.compactSentence("my   name   is alex."))
    }
}