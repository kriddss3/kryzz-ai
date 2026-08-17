package ai.daylight.assistant.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ai.daylight.assistant.domain.MessageStatus

@Entity(tableName = "conversations", indices = [Index("updatedAt"), Index("archived"), Index("folderId")])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    val searchSessionId: String? = null,
    val searchDepth: Int = 2,
    val searchWidth: Int = 3,
    val pinned: Boolean = false,
    val folderId: String? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId"), Index(value = ["conversationId", "createdAt"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String = MessageStatus.COMPLETE.name,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val citationsJson: String = "[]",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val cost: Double? = null,
    val mode: String = "CHAT",
    val capability: String? = null,
    val outputsJson: String = "[]",
    val attachmentsJson: String = "[]",
    val cachedInputTokens: Int? = null,
    val firstTokenLatencyMs: Long? = null,
    val totalGenerationTimeMs: Long? = null
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
    val searchSessionId: String?,
    val searchDepth: Int,
    val searchWidth: Int,
    val preview: String?,
    val pinned: Boolean = false,
    val folderId: String? = null
)

/** One earlier chat message used for local past-chat search. */
data class MessageSearchRow(
    val conversationId: String,
    val conversationTitle: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

@Entity(tableName = "conversation_folders", indices = [Index("updatedAt")])
data class ConversationFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "skills", indices = [Index(value = ["name"], unique = true), Index("enabled")])
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val examplePrompts: String = "",
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "scheduled_tasks",
    indices = [Index("enabled"), Index("conversationId")]
)
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val intervalMinutes: Long,
    val conversationId: String,
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val createdAt: Long,
    val recurrence: String = "DAILY",
    val hourOfDay: Int? = null,
    val minuteOfHour: Int? = null,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val daysOfWeek: String? = null
)

@Entity(
    tableName = "memories",
    indices = [Index("enabled"), Index("updatedAt")]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val category: String,
    val sourceConversationId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long = 0L,
    val usageCount: Int = 0,
    val enabled: Boolean = true,
    val pinned: Boolean = false
)
