package ai.daylight.assistant

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.daylight.assistant.data.local.AssistantDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val databaseName = "migration-v2-v3"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AssistantDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun migratesExistingConversationsToSkillsSchema() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL("INSERT INTO conversations (id, title, createdAt, updatedAt, archived, searchSessionId) VALUES ('one', 'Existing', 1, 1, 0, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 3, true, AssistantDatabase.MIGRATION_2_3).apply {
            query("SELECT title FROM conversations WHERE id = 'one'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "Existing")
            }
            execSQL("INSERT INTO skills (id, name, description, instructions, examplePrompts, enabled, createdAt, updatedAt) VALUES ('skill', 'Planner', 'Plans work', 'Make a clear plan with steps', '', 1, 2, 2)")
            query("SELECT enabled FROM skills WHERE id = 'skill'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 1)
            }
            close()
        }
    }

    @Test fun migratesResearchTuningAndAttachmentMetadata() {
        val name = "migration-v3-v4"
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO conversations (id, title, createdAt, updatedAt, archived, searchSessionId) VALUES ('one', 'Existing', 1, 1, 0, NULL)")
            execSQL("INSERT INTO messages (id, conversationId, role, content, createdAt, status, citationsJson, mode, outputsJson) VALUES ('m1', 'one', 'USER', 'hello', 2, 'COMPLETE', '[]', 'CHAT', '[]')")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, AssistantDatabase.MIGRATION_3_4).apply {
            query("SELECT searchDepth, searchWidth FROM conversations WHERE id = 'one'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 2 && cursor.getInt(1) == 3)
            }
            query("SELECT attachmentsJson FROM messages WHERE id = 'm1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "[]")
            }
            close()
        }
    }

    @Test fun migratesCachedInputTokenMetadata() {
        val name = "migration-v4-v5"
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO conversations (id, title, createdAt, updatedAt, archived, searchSessionId, searchDepth, searchWidth) VALUES ('one', 'Existing', 1, 1, 0, NULL, 2, 3)")
            execSQL("INSERT INTO messages (id, conversationId, role, content, createdAt, status, citationsJson, mode, outputsJson, attachmentsJson) VALUES ('m1', 'one', 'ASSISTANT', 'hello', 2, 'COMPLETE', '[]', 'CHAT', '[]', '[]')")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, AssistantDatabase.MIGRATION_4_5).apply {
            query("SELECT cachedInputTokens FROM messages WHERE id = 'm1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.isNull(0))
            }
            execSQL("UPDATE messages SET cachedInputTokens = 7 WHERE id = 'm1'")
            query("SELECT cachedInputTokens FROM messages WHERE id = 'm1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 7)
            }
            close()
        }
    }

    @Test fun migratesResponseTimingMetadata() {
        val name = "migration-v5-v6"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO conversations (id, title, createdAt, updatedAt, archived, searchSessionId, searchDepth, searchWidth) VALUES ('one', 'Existing', 1, 1, 0, NULL, 2, 3)")
            execSQL("INSERT INTO messages (id, conversationId, role, content, createdAt, status, citationsJson, mode, outputsJson, attachmentsJson) VALUES ('m1', 'one', 'ASSISTANT', 'hello', 2, 'COMPLETE', '[]', 'CHAT', '[]', '[]')")
            close()
        }
        helper.runMigrationsAndValidate(name, 6, true, AssistantDatabase.MIGRATION_5_6).apply {
            query("SELECT firstTokenLatencyMs, totalGenerationTimeMs FROM messages WHERE id = 'm1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.isNull(0) && cursor.isNull(1))
            }
            execSQL("UPDATE messages SET firstTokenLatencyMs = 1250, totalGenerationTimeMs = 4800 WHERE id = 'm1'")
            query("SELECT firstTokenLatencyMs, totalGenerationTimeMs FROM messages WHERE id = 'm1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == 1250L && cursor.getLong(1) == 4800L)
            }
            close()
        }
    }

    @Test fun migratesToMemorySchema() {
        val name = "migration-v6-v7"
        helper.createDatabase(name, 6).apply {
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, AssistantDatabase.MIGRATION_6_7).apply {
            execSQL("INSERT INTO memories (id, content, category, createdAt, updatedAt) VALUES ('m1', 'I like JDM cars', 'PREFERENCE', 1, 1)")
            query("SELECT content, enabled FROM memories WHERE id = 'm1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "I like JDM cars" && cursor.getInt(1) == 1)
            }
            execSQL("UPDATE memories SET usageCount = 3, lastUsedAt = 5 WHERE id = 'm1'")
            query("SELECT usageCount, lastUsedAt FROM memories WHERE id = 'm1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 3 && cursor.getLong(1) == 5L)
            }
            close()
        }
    }

    @Test fun migratesConversationPinnedState() {
        val name = "migration-v7-v8"
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO conversations (id, title, createdAt, updatedAt, archived, searchSessionId, searchDepth, searchWidth) VALUES ('one', 'Existing', 1, 1, 0, NULL, 2, 3)")
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, AssistantDatabase.MIGRATION_7_8).apply {
            query("SELECT pinned FROM conversations WHERE id = 'one'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0)
            }
            execSQL("UPDATE conversations SET pinned = 1 WHERE id = 'one'")
            query("SELECT pinned FROM conversations WHERE id = 'one'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 1)
            }
            close()
        }
    }

    @Test fun migratesFoldersAndRestoresArchivedConversations() {
        val name = "migration-v8-v9"
        helper.createDatabase(name, 8).apply {
            execSQL("INSERT INTO conversations (id, title, createdAt, updatedAt, archived, searchSessionId, searchDepth, searchWidth, pinned) VALUES ('one', 'Existing', 1, 1, 1, NULL, 2, 3, 0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 9, true, AssistantDatabase.MIGRATION_8_9).apply {
            query("SELECT archived, folderId FROM conversations WHERE id = 'one'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0 && cursor.isNull(1))
            }
            execSQL("INSERT INTO conversation_folders (id, name, createdAt, updatedAt) VALUES ('folder', 'Work', 2, 2)")
            execSQL("UPDATE conversations SET folderId = 'folder' WHERE id = 'one'")
            query("SELECT folderId FROM conversations WHERE id = 'one'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "folder")
            }
            close()
        }
    }

    @Test fun migratesToScheduledTasksSchema() {
        val name = "migration-v9-v10"
        helper.createDatabase(name, 9).apply {
            close()
        }
        helper.runMigrationsAndValidate(name, 10, true, AssistantDatabase.MIGRATION_9_10).apply {
            execSQL("INSERT INTO scheduled_tasks (id, title, prompt, intervalMinutes, conversationId, enabled, createdAt) VALUES ('task1', 'Morning briefing', 'Summarise the news', 1440, 'conv1', 1, 1)")
            query("SELECT title, intervalMinutes, enabled, lastRunAt FROM scheduled_tasks WHERE id = 'task1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "Morning briefing" && cursor.getLong(1) == 1440L && cursor.getInt(2) == 1 && cursor.isNull(3))
            }
            execSQL("UPDATE scheduled_tasks SET enabled = 0, lastRunAt = 7 WHERE id = 'task1'")
            query("SELECT enabled, lastRunAt FROM scheduled_tasks WHERE id = 'task1'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0 && cursor.getLong(1) == 7L)
            }
            close()
        }
    }

    @Test fun migratesToWallClockScheduledTaskSchema() {
        val name = "migration-v10-v11"
        helper.createDatabase(name, 10).apply {
            execSQL("INSERT INTO scheduled_tasks (id, title, prompt, intervalMinutes, conversationId, enabled, createdAt) VALUES ('legacy', 'Old task', 'Prompt', 60, 'conv1', 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 11, true, AssistantDatabase.MIGRATION_10_11).apply {
            // Legacy rows get the new defaults and keep their interval behaviour.
            query("SELECT recurrence, hourOfDay, minuteOfHour, dayOfWeek, dayOfMonth FROM scheduled_tasks WHERE id = 'legacy'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "DAILY" && cursor.isNull(1) && cursor.isNull(2) && cursor.isNull(3) && cursor.isNull(4))
            }
            // A fresh wall-clock task round-trips its schedule fields.
            execSQL(
                "INSERT INTO scheduled_tasks (id, title, prompt, intervalMinutes, conversationId, enabled, createdAt, recurrence, hourOfDay, minuteOfHour, dayOfWeek, dayOfMonth) " +
                    "VALUES ('new', 'Weekly', 'Prompt', 1440, 'conv2', 1, 1, 'WEEKLY', 7, 30, 1, NULL)"
            )
            query("SELECT recurrence, hourOfDay, minuteOfHour, dayOfWeek FROM scheduled_tasks WHERE id = 'new'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "WEEKLY" && cursor.getInt(1) == 7 && cursor.getInt(2) == 30 && cursor.getInt(3) == 1)
            }
            close()
        }
    }

    @Test fun migratesWeeklyDaysOfWeekColumn() {
        val name = "migration-v11-v12"
        helper.createDatabase(name, 11).apply {
            execSQL(
                "INSERT INTO scheduled_tasks (id, title, prompt, intervalMinutes, conversationId, enabled, createdAt, recurrence, hourOfDay, minuteOfHour, dayOfWeek, dayOfMonth) " +
                    "VALUES ('weekly', 'Weekly', 'Prompt', 1440, 'conv2', 1, 1, 'WEEKLY', 7, 30, 1, NULL)"
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 12, true, AssistantDatabase.MIGRATION_11_12).apply {
            query("SELECT daysOfWeek, dayOfWeek FROM scheduled_tasks WHERE id = 'weekly'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "1" && cursor.getInt(1) == 1)
            }
            execSQL("UPDATE scheduled_tasks SET daysOfWeek = '1,3,5' WHERE id = 'weekly'")
            query("SELECT daysOfWeek FROM scheduled_tasks WHERE id = 'weekly'").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "1,3,5")
            }
            close()
        }
    }
}
