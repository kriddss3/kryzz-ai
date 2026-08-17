package ai.daylight.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistorySearchTest {

    private fun message(
        content: String,
        title: String = "Chat",
        conversationId: String = "c1",
        role: String = "USER",
        createdAt: Long = 1L
    ) = ChatHistorySearch.Message(conversationId, title, role, content, createdAt)

    @Test
    fun retrievesOverlappingPastChatAndSkipsUnrelated() {
        val hits = ChatHistorySearch.retrieve(
            "what JDM car should I buy",
            listOf(
                message("I like JDM cars with low upkeep", title = "Cars", conversationId = "cars"),
                message("The weather in Gilly is nice today", title = "Weather", conversationId = "wx")
            )
        )
        assertEquals(1, hits.size)
        assertEquals("cars", hits[0].conversationId)
        assertTrue(hits[0].snippet.contains("JDM"))
    }

    @Test
    fun recallPhrasesStillFindAWeakMatch() {
        val hits = ChatHistorySearch.retrieve(
            "remember what we talked about homework",
            listOf(message("My CS IA is about a homework planner", title = "School", conversationId = "school"))
        )
        assertEquals(1, hits.size)
        assertEquals("school", hits[0].conversationId)
    }

    @Test
    fun formatContextIsCompactAndQuoted() {
        val formatted = ChatHistorySearch.formatContext(
            listOf(
                ChatHistorySearch.Hit(
                    conversationId = "cars",
                    conversationTitle = "Cars",
                    role = "user",
                    snippet = "I like JDM cars",
                    createdAt = 1L,
                    score = 0.5
                )
            )
        )
        assertTrue(formatted.contains("<past chats"))
        assertTrue(formatted.contains("\"Cars\""))
        assertTrue(formatted.contains("user: I like JDM cars"))
        assertTrue(formatted.contains("</past chats>"))
    }

    @Test
    fun emptyQueryOrCorpusReturnsNothing() {
        assertTrue(ChatHistorySearch.retrieve("   ", listOf(message("I like cars"))).isEmpty())
        assertTrue(ChatHistorySearch.retrieve("JDM cars", emptyList()).isEmpty())
    }

    @Test
    fun looksLikeRecallDetectsCommonPhrases() {
        assertTrue(ChatHistorySearch.looksLikeRecall("what did we talk about last time"))
        assertTrue(ChatHistorySearch.looksLikeRecall("remember the other chat"))
        assertTrue(!ChatHistorySearch.looksLikeRecall("what's the weather"))
    }
}
