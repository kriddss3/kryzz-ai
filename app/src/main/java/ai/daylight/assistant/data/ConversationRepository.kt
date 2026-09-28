package ai.daylight.assistant.data

import ai.daylight.assistant.data.local.AssistantDao
import ai.daylight.assistant.data.local.ConversationEntity
import ai.daylight.assistant.data.local.ConversationFolderEntity
import ai.daylight.assistant.data.local.ConversationSummary
import ai.daylight.assistant.data.local.MessageEntity
import ai.daylight.assistant.domain.Citation
import ai.daylight.assistant.domain.ChatAttachment
import ai.daylight.assistant.domain.ExportBundle
import ai.daylight.assistant.domain.ExportConversation
import ai.daylight.assistant.domain.ExportConversationFolder
import ai.daylight.assistant.domain.ExportMessage
import ai.daylight.assistant.domain.GeneratedOutput
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConversationRepository(private val dao: AssistantDao, private val json: Json) {
    fun conversations(archived: Boolean, query: String): Flow<List<ConversationSummary>> = dao.observeConversations(archived, query)
    fun messages(conversationId: String): Flow<List<MessageEntity>> = dao.observeVisibleMessages(conversationId)
    fun toolMessages(conversationId: String): Flow<List<MessageEntity>> = dao.observeToolMessages(conversationId)
    suspend fun conversation(id: String) = dao.conversation(id)
    fun observeConversation(id: String): Flow<ConversationEntity?> = dao.observeConversation(id)
    fun folders(): Flow<List<ConversationFolderEntity>> = dao.observeConversationFolders()

    suspend fun createConversation(): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertConversation(ConversationEntity(id, "New conversation", now, now))
        return id
    }

    /** Recreates the row if a stale screen sends into a conversation that was pruned. */
    suspend fun ensureConversation(id: String) {
        if (dao.conversation(id) == null) {
            val now = System.currentTimeMillis()
            dao.upsertConversation(ConversationEntity(id, "New conversation", now, now))
        }
    }

    suspend fun rename(id: String, title: String) {
        val clean = title.trim().take(100)
        if (clean.isNotEmpty()) dao.renameConversation(id, clean, System.currentTimeMillis())
    }

    suspend fun renameIfCurrent(id: String, expectedTitle: String, title: String): Boolean {
        val clean = title.trim().take(100)
        if (clean.isEmpty()) return false
        return dao.renameConversationIfTitle(id, expectedTitle, clean, System.currentTimeMillis()) > 0
    }

    suspend fun createFolder(name: String): String {
        val clean = cleanFolderName(name)
        require(clean.isNotEmpty()) { "Enter a folder name." }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertConversationFolder(ConversationFolderEntity(id, clean, now, now))
        return id
    }

    suspend fun renameFolder(id: String, name: String) {
        val clean = cleanFolderName(name)
        require(clean.isNotEmpty()) { "Enter a folder name." }
        dao.renameConversationFolder(id, clean, System.currentTimeMillis())
    }

    suspend fun deleteFolder(id: String) = dao.deleteConversationFolderAndUnassign(id)

    suspend fun assignFolder(conversationId: String, folderId: String?) {
        val validFolderId = folderId?.takeIf { dao.conversationFolder(it) != null }
        dao.assignConversationFolder(conversationId, validFolderId, System.currentTimeMillis())
    }

    suspend fun archive(id: String, archived: Boolean) = dao.archiveConversation(id, archived, System.currentTimeMillis())
    suspend fun pin(id: String, pinned: Boolean) = dao.setConversationPinned(id, pinned)
    suspend fun delete(id: String) = dao.deleteConversation(id)
    suspend fun deleteIfEmpty(id: String) = dao.deleteConversationIfEmpty(id)
    suspend fun deleteEmpty() = dao.deleteEmptyConversations()
    suspend fun deleteEmptyExcept(id: String) = dao.deleteEmptyConversationsExcept(id)
    suspend fun deleteEmptyExcept(ids: Set<String>) {
        if (ids.isEmpty()) {
            dao.deleteEmptyConversations()
        } else {
            dao.allConversations().forEach { conversation ->
                if (conversation.id !in ids) dao.deleteConversationIfEmpty(conversation.id)
            }
        }
    }
    suspend fun hasContent(id: String) = dao.conversationHasContent(id)
    suspend fun latestUserMessage(id: String) = dao.latestUserMessage(id)
    suspend fun deleteMessage(id: String) = dao.deleteMessage(id)
    suspend fun deleteFrom(conversationId: String, timestamp: Long) = dao.deleteMessagesFrom(conversationId, timestamp)
    suspend fun deleteToolRowsFrom(conversationId: String, timestamp: Long) = dao.deleteToolMessagesFrom(conversationId, timestamp)

    fun citations(message: MessageEntity): List<Citation> =
        runCatching { json.decodeFromString<List<Citation>>(message.citationsJson) }.getOrDefault(emptyList())

    fun outputs(message: MessageEntity): List<GeneratedOutput> =
        runCatching { json.decodeFromString<List<GeneratedOutput>>(message.outputsJson) }.getOrDefault(emptyList())

    fun attachments(message: MessageEntity): List<ChatAttachment> =
        runCatching { json.decodeFromString<List<ChatAttachment>>(message.attachmentsJson) }.getOrDefault(emptyList())

    suspend fun setResearchTuning(id: String, depth: Int, width: Int) =
        dao.updateResearchTuning(id, depth.coerceIn(1, 3), width.coerceIn(1, 5), System.currentTimeMillis())

    suspend fun exportJson(): String {
        val conversations = dao.allConversations()
        val folders = dao.allConversationFolders()
        val messages = dao.allMessages().groupBy { it.conversationId }
        val bundle = ExportBundle(
            exportedAt = System.currentTimeMillis(),
            conversations = conversations.map { c ->
                ExportConversation(
                    c.id, c.title, c.createdAt, c.updatedAt, c.archived,
                    messages[c.id].orEmpty().filter { it.role != "TOOL" }.map { m ->
                        ExportMessage(
                            m.id, m.role, m.content, m.createdAt,
                            runCatching { json.decodeFromString<List<Citation>>(m.citationsJson) }.getOrDefault(emptyList()),
                            m.promptTokens, m.completionTokens, m.totalTokens, m.cost,
                            m.mode, m.capability,
                            outputs(m).map { it.portableForExport() },
                            attachments(m).map { it.copy(localPath = null) },
                            cachedInputTokens = m.cachedInputTokens
                        )
                    },
                    searchDepth = c.searchDepth,
                    searchWidth = c.searchWidth,
                    pinned = c.pinned,
                    folderId = c.folderId
                )
            },
            folders = folders.map { folder ->
                ExportConversationFolder(folder.id, folder.name, folder.createdAt, folder.updatedAt)
            }
        )
        return json.encodeToString(bundle)
    }

    suspend fun importJson(content: String): Int {
        val bundle = json.decodeFromString<ExportBundle>(content)
        require(bundle.formatVersion == 1) { "Unsupported export format" }
        val importedFolderIds = mutableSetOf<String>()
        bundle.folders.forEach { folder ->
            val cleanName = cleanFolderName(folder.name)
            if (folder.id.isNotBlank() && cleanName.isNotEmpty()) {
                dao.upsertConversationFolder(
                    ConversationFolderEntity(folder.id, cleanName, folder.createdAt, folder.updatedAt)
                )
                importedFolderIds += folder.id
            }
        }
        val importableConversations = bundle.conversations.filter { it.id.isNotBlank() }
        importableConversations.forEach { c ->
            val importedTitle = c.title.trim().take(100).ifBlank { "Imported conversation" }
            dao.upsertConversation(
                ConversationEntity(
                    c.id, importedTitle, c.createdAt, c.updatedAt, archived = false,
                    searchDepth = c.searchDepth.coerceIn(1, 3),
                    searchWidth = c.searchWidth.coerceIn(1, 5),
                    pinned = c.pinned,
                    folderId = c.folderId?.takeIf { it in importedFolderIds }
                )
            )
            dao.upsertMessages(c.messages.map { m ->
                MessageEntity(
                    id = m.id,
                    conversationId = c.id,
                    role = m.role,
                    content = m.content,
                    createdAt = m.createdAt,
                    citationsJson = json.encodeToString(m.citations),
                    promptTokens = m.promptTokens,
                    completionTokens = m.completionTokens,
                    totalTokens = m.totalTokens,
                    cost = m.cost,
                    mode = m.mode,
                    capability = m.capability,
                    outputsJson = json.encodeToString(m.outputs.map { it.copy(localPath = null) }),
                    attachmentsJson = json.encodeToString(m.attachments.map { it.copy(localPath = null) }),
                    cachedInputTokens = m.cachedInputTokens
                )
            })
        }
        return importableConversations.size
    }

    suspend fun clear() = dao.clearAll()

    private fun cleanFolderName(value: String): String = value.trim().replace(Regex("\\s+"), " ").take(48)

    private fun GeneratedOutput.portableForExport(): GeneratedOutput = when {
        localPath == null -> this
        kind == ai.daylight.assistant.domain.OutputKind.DOCUMENT -> copy(localPath = null, fileName = fileName.substringBeforeLast('.') + ".md", mimeType = "text/markdown")
        kind == ai.daylight.assistant.domain.OutputKind.SPREADSHEET -> copy(localPath = null, fileName = fileName.substringBeforeLast('.') + ".csv", mimeType = "text/csv")
        kind == ai.daylight.assistant.domain.OutputKind.DATABASE -> copy(localPath = null, fileName = fileName.substringBeforeLast('.') + ".sql", mimeType = "application/sql")
        kind == ai.daylight.assistant.domain.OutputKind.PDF -> copy(localPath = null, fileName = fileName.substringBeforeLast('.') + ".txt", mimeType = "text/plain")
        else -> copy(localPath = null)
    }
}
