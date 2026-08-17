package ai.daylight.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantDao {
    @Query("""
        SELECT c.*, (SELECT content FROM messages m WHERE m.conversationId = c.id AND m.role != 'TOOL' ORDER BY createdAt DESC LIMIT 1) AS preview
        FROM conversations c
        WHERE c.archived = :archived
          AND (:query = '' OR c.title LIKE '%' || :query || '%' OR EXISTS(SELECT 1 FROM messages m WHERE m.conversationId = c.id AND m.content LIKE '%' || :query || '%'))
          AND EXISTS(SELECT 1 FROM messages m2 WHERE m2.conversationId = c.id AND m2.role IN ('USER', 'ASSISTANT') AND (TRIM(m2.content) != '' OR m2.outputsJson != '[]' OR m2.attachmentsJson != '[]'))
        ORDER BY c.pinned DESC, c.updatedAt DESC
    """)
    fun observeConversations(archived: Boolean, query: String): Flow<List<ConversationSummary>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeConversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun allConversations(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, now: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id AND title = :expectedTitle")
    suspend fun renameConversationIfTitle(id: String, expectedTitle: String, title: String, now: Long): Int

    @Query("UPDATE conversations SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun archiveConversation(id: String, archived: Boolean, now: Long)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setConversationPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET folderId = :folderId, updatedAt = :now WHERE id = :id")
    suspend fun assignConversationFolder(id: String, folderId: String?, now: Long)

    @Query("SELECT * FROM conversation_folders ORDER BY name COLLATE NOCASE ASC")
    fun observeConversationFolders(): Flow<List<ConversationFolderEntity>>

    @Query("SELECT * FROM conversation_folders ORDER BY name COLLATE NOCASE ASC")
    suspend fun allConversationFolders(): List<ConversationFolderEntity>

    @Query("SELECT * FROM conversation_folders WHERE id = :id")
    suspend fun conversationFolder(id: String): ConversationFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversationFolder(folder: ConversationFolderEntity)

    @Query("UPDATE conversation_folders SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renameConversationFolder(id: String, name: String, now: Long)

    @Query("UPDATE conversations SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearConversationFolderAssignments(folderId: String)

    @Query("DELETE FROM conversation_folders WHERE id = :id")
    suspend fun deleteConversationFolder(id: String)

    @Transaction
    suspend fun deleteConversationFolderAndUnassign(id: String) {
        clearConversationFolderAssignments(id)
        deleteConversationFolder(id)
    }

    @Query("UPDATE conversations SET searchSessionId = :sessionId WHERE id = :id")
    suspend fun updateSearchSession(id: String, sessionId: String?)

    @Query("UPDATE conversations SET searchDepth = :depth, searchWidth = :width, updatedAt = :now WHERE id = :id")
    suspend fun updateResearchTuning(id: String, depth: Int, width: Int, now: Long)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touchConversation(id: String, now: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations WHERE id = :id AND NOT EXISTS (SELECT 1 FROM messages WHERE conversationId = :id AND role IN ('USER', 'ASSISTANT') AND (TRIM(content) != '' OR outputsJson != '[]' OR attachmentsJson != '[]'))")
    suspend fun deleteConversationIfEmpty(id: String)

    @Query("DELETE FROM conversations WHERE NOT EXISTS (SELECT 1 FROM messages WHERE messages.conversationId = conversations.id AND role IN ('USER', 'ASSISTANT') AND (TRIM(content) != '' OR outputsJson != '[]' OR attachmentsJson != '[]'))")
    suspend fun deleteEmptyConversations()

    @Query("DELETE FROM conversations WHERE id != :exceptId AND NOT EXISTS (SELECT 1 FROM messages WHERE messages.conversationId = conversations.id AND role IN ('USER', 'ASSISTANT') AND (TRIM(content) != '' OR outputsJson != '[]' OR attachmentsJson != '[]'))")
    suspend fun deleteEmptyConversationsExcept(exceptId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE conversationId = :id AND role IN ('USER', 'ASSISTANT') AND (TRIM(content) != '' OR outputsJson != '[]' OR attachmentsJson != '[]'))")
    suspend fun conversationHasContent(id: String): Boolean

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND role != 'TOOL' ORDER BY createdAt ASC")
    fun observeVisibleMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun messages(conversationId: String): List<MessageEntity>

    @Query(
        """
        SELECT m.conversationId AS conversationId,
               c.title AS conversationTitle,
               m.role AS role,
               m.content AS content,
               m.createdAt AS createdAt
        FROM messages m
        INNER JOIN conversations c ON c.id = m.conversationId
        WHERE m.role IN ('USER', 'ASSISTANT')
          AND TRIM(m.content) != ''
          AND m.conversationId != :excludeConversationId
        ORDER BY m.createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun recentMessagesOutside(excludeConversationId: String, limit: Int): List<MessageSearchRow>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND role = 'USER' ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestUserMessage(conversationId: String): MessageEntity?

    @Query("SELECT * FROM messages ORDER BY conversationId, createdAt ASC")
    suspend fun allMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :id")
    suspend fun updateMessageContent(id: String, content: String, status: String)

    @Query("UPDATE messages SET content = :content, status = :status, citationsJson = :citationsJson, promptTokens = :promptTokens, completionTokens = :completionTokens, totalTokens = :totalTokens, cost = :cost, outputsJson = :outputsJson, cachedInputTokens = :cachedInputTokens, firstTokenLatencyMs = :firstTokenLatencyMs, totalGenerationTimeMs = :totalGenerationTimeMs WHERE id = :id")
    suspend fun finishMessage(
        id: String,
        content: String,
        status: String,
        citationsJson: String,
        promptTokens: Int?,
        completionTokens: Int?,
        totalTokens: Int?,
        cost: Double?,
        outputsJson: String = "[]",
        cachedInputTokens: Int? = null,
        firstTokenLatencyMs: Long? = null,
        totalGenerationTimeMs: Long? = null
    )

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :fromTimestamp")
    suspend fun deleteMessagesFrom(conversationId: String, fromTimestamp: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM conversation_folders")
    suspend fun deleteAllConversationFolders()

    @Query("SELECT * FROM skills ORDER BY enabled DESC, updatedAt DESC")
    fun observeSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills WHERE enabled = 1 ORDER BY updatedAt DESC")
    suspend fun enabledSkills(): List<SkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(skill: SkillEntity)

    @Query("UPDATE skills SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setSkillEnabled(id: String, enabled: Boolean, now: Long)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteSkill(id: String)

    @Query("DELETE FROM skills")
    suspend fun deleteAllSkills()

    @Query("SELECT * FROM memories WHERE (:query = '' OR content LIKE '%' || :query || '%') ORDER BY pinned DESC, updatedAt DESC")
    fun observeMemories(query: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE 1")
    suspend fun allMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE enabled = 1 ORDER BY updatedAt DESC")
    suspend fun enabledMemories(): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun countMemories(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemories(memories: List<MemoryEntity>)

    @Query("UPDATE memories SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setMemoryEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE memories SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setMemoryPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE memories SET lastUsedAt = :now, usageCount = usageCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun touchMemory(id: String, now: Long)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()

    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt DESC")
    fun observeScheduledTasks(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun scheduledTask(id: String): ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduledTask(task: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setScheduledTaskEnabled(id: String, enabled: Boolean)

    @Query("UPDATE scheduled_tasks SET lastRunAt = :now WHERE id = :id")
    suspend fun updateScheduledTaskLastRun(id: String, now: Long)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteScheduledTask(id: String)

    @Query("DELETE FROM scheduled_tasks")
    suspend fun deleteAllScheduledTasks()

    @Transaction
    suspend fun clearAll() {
        deleteAllMessages()
        deleteAllConversations()
        deleteAllConversationFolders()
        deleteAllSkills()
        deleteAllMemories()
        deleteAllScheduledTasks()
    }
}
