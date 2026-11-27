package ai.daylight.assistant.data

import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.MemoryEntity
import ai.daylight.assistant.domain.MemoryEngine
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val dao: AssistantDao) {

    companion object { const val MAX_MEMORIES = 300 }

    fun observe(query: String): Flow<List<MemoryEntity>> = dao.observeMemories(query)

    suspend fun all(): List<MemoryEntity> = dao.allMemories()

    suspend fun count(): Int = dao.countMemories()

    /**
     * Scans a user message for durable facts and stores any NEW ones locally.
     * Cheap regex work — safe to call before dispatching the API request.
     * Returns true when at least one new memory was saved.
     */
    suspend fun ingest(text: String, conversationId: String?): Boolean {
        val existing = dao.allMemories()
        if (existing.size >= MAX_MEMORIES) return false
        val existingContents = existing.map { it.content }
        val fresh = MemoryEngine.extract(text)
            .filter { !MemoryEngine.looksDuplicate(it.content, existingContents) }
            .take((MAX_MEMORIES - existing.size).coerceAtLeast(0))
        if (fresh.isEmpty()) return false
        val now = System.currentTimeMillis()
        dao.upsertMemories(
            fresh.map { fact ->
                MemoryEntity(
                    id = UUID.randomUUID().toString(),
                    content = fact.content,
                    category = fact.category.name,
                    sourceConversationId = conversationId,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
        return true
    }

    /**
     * Retrieves the most relevant memories for [userText] and formats them as a
     * system-prompt block. Touches usage tracking for the memories it returns.
     * Returns "" when nothing is relevant, so callers can gate on [settings.memoryEnabled].
     */
    suspend fun buildMemoryContext(userText: String, limit: Int = 6): String {
        val enabled = dao.enabledMemories()
        if (enabled.isEmpty()) return ""
        val relevant = MemoryEngine.retrieveWithCore(userText, enabled, limit = limit)
        if (relevant.isEmpty()) return ""
        val now = System.currentTimeMillis()
        relevant.forEach { dao.touchMemory(it.id, now) }
        return buildString {
            appendLine("<memory context — facts the user has shared before; use them only when relevant, never invent new ones>")
            relevant.forEach { appendLine("- ${it.content}") }
            append("</memory context>")
        }
    }

    /** Stores a durable memory produced by automatic turn extraction after safety and dedup checks. */
    suspend fun addFromAuto(content: String, category: MemoryEngine.Category, conversationId: String?): Boolean {
        val clean = content.replace(Regex("""\s+"""), " ").trim()
        if (clean.length !in MemoryEngine.MIN_MEMORY_CHARS..MemoryEngine.MAX_MEMORY_CHARS) return false
        if (MemoryEngine.containsSecretPattern(clean)) return false
        val existing = dao.allMemories()
        if (existing.size >= MAX_MEMORIES) return false
        if (MemoryEngine.looksDuplicate(clean, existing.map { it.content })) return false
        val now = System.currentTimeMillis()
        dao.upsertMemory(
            MemoryEntity(
                id = UUID.randomUUID().toString(),
                content = clean,
                category = category.name,
                sourceConversationId = conversationId,
                createdAt = now,
                updatedAt = now
            )
        )
        return true
    }

    /** Updates an existing memory's text and category while preserving usage and pin metadata. */
    suspend fun updateContent(
        id: String,
        content: String,
        category: MemoryEngine.Category,
        conversationId: String? = null
    ): Boolean {
        val all = dao.allMemories()
        val existing = all.firstOrNull { it.id == id } ?: return false
        val clean = content.replace(Regex("""\s+"""), " ").trim()
        if (clean.length !in MemoryEngine.MIN_MEMORY_CHARS..MemoryEngine.MAX_MEMORY_CHARS) return false
        if (MemoryEngine.containsSecretPattern(clean)) return false
        val otherContents = all.filter { it.id != id }.map { it.content }
        if (MemoryEngine.looksDuplicate(clean, otherContents)) return false
        dao.upsertMemory(
            existing.copy(
                content = clean,
                category = category.name,
                updatedAt = System.currentTimeMillis(),
                sourceConversationId = conversationId ?: existing.sourceConversationId
            )
        )
        return true
    }

    suspend fun add(content: String, category: MemoryEngine.Category): Boolean {
        val clean = content.trim()
        if (clean.length < MemoryEngine.MIN_MEMORY_CHARS || clean.length > MemoryEngine.MAX_MEMORY_CHARS) return false
        if (MemoryEngine.looksDuplicate(clean, dao.allMemories().map { it.content })) return false
        val now = System.currentTimeMillis()
        dao.upsertMemory(
            MemoryEntity(
                id = UUID.randomUUID().toString(),
                content = MemoryEngine.compactSentence(clean),
                category = category.name,
                createdAt = now,
                updatedAt = now
            )
        )
        return true
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setMemoryEnabled(id, enabled, System.currentTimeMillis())

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setMemoryPinned(id, pinned, System.currentTimeMillis())

    suspend fun delete(id: String) = dao.deleteMemory(id)

    suspend fun clearAll() = dao.deleteAllMemories()
}