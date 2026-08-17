package ai.daylight.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, ConversationFolderEntity::class, MessageEntity::class, SkillEntity::class, MemoryEntity::class, ScheduledTaskEntity::class],
    version = 12,
    exportSchema = true
)
abstract class AssistantDatabase : RoomDatabase() {
    abstract fun dao(): AssistantDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN mode TEXT NOT NULL DEFAULT 'CHAT'")
                db.execSQL("ALTER TABLE messages ADD COLUMN capability TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN outputsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `skills` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        `examplePrompts` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_skills_name` ON `skills` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_skills_enabled` ON `skills` (`enabled`)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN searchDepth INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE conversations ADD COLUMN searchWidth INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN cachedInputTokens INTEGER")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN firstTokenLatencyMs INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN totalGenerationTimeMs INTEGER")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memories` (
                        `id` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `sourceConversationId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL DEFAULT 0,
                        `usageCount` INTEGER NOT NULL DEFAULT 0,
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `pinned` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_enabled` ON `memories` (`enabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_updatedAt` ON `memories` (`updatedAt`)")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN folderId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_folderId` ON `conversations` (`folderId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversation_folders` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_folders_updatedAt` ON `conversation_folders` (`updatedAt`)")
                // Archive no longer exists in the user experience. Restore every legacy
                // conversation so upgrading users cannot lose access to archived work.
                db.execSQL("UPDATE conversations SET archived = 0 WHERE archived = 1")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scheduled_tasks` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `prompt` TEXT NOT NULL,
                        `intervalMinutes` INTEGER NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `lastRunAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_tasks_enabled` ON `scheduled_tasks` (`enabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_tasks_conversationId` ON `scheduled_tasks` (`conversationId`)")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 4.7.0 cron schedules: exact time of day plus daily / weekly / monthly
                // recurrence. Legacy rows keep intervalMinutes and null times, so their
                // existing interval behaviour is preserved unchanged.
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'DAILY'")
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN hourOfDay INTEGER")
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN minuteOfHour INTEGER")
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN dayOfWeek INTEGER")
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN dayOfMonth INTEGER")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Weekly jobs can now fire on several weekdays. Existing single-day
                // rows keep dayOfWeek and get a matching daysOfWeek string.
                db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN daysOfWeek TEXT")
                db.execSQL(
                    "UPDATE scheduled_tasks SET daysOfWeek = CAST(dayOfWeek AS TEXT) " +
                        "WHERE dayOfWeek IS NOT NULL AND recurrence = 'WEEKLY'"
                )
            }
        }
    }
}
