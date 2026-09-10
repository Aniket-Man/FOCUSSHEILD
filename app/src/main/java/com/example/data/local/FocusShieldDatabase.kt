package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.converter.Converters
import com.example.data.local.dao.AppLimitDao
import com.example.data.local.dao.AppLimitSessionDao
import com.example.data.local.dao.BlockedAppDao
import com.example.data.local.dao.BlockedAttemptDao
import com.example.data.local.dao.BlockedWebsiteDao
import com.example.data.local.dao.BreakRecordDao
import com.example.data.local.dao.CloudBulkDao
import com.example.data.local.dao.DailyAppUsageDao
import com.example.data.local.dao.DailyUnlockDao
import com.example.data.local.dao.FocusScheduleDao
import com.example.data.local.dao.KeywordDao
import com.example.data.local.dao.SessionDao
import com.example.data.local.dao.ScratchCardDao
import com.example.data.local.dao.StudyActivityDao
import com.example.data.local.dao.StudyChannelDao
import com.example.data.local.dao.StudyPlanDao
import com.example.data.local.dao.SubjectDao
import com.example.data.local.dao.SyncOutboxDao
import com.example.data.local.dao.TopicDao
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.AppLimitSessionEntity
import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.BlockedAttemptEntity
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.local.entity.BreakRecordEntity
import com.example.data.local.entity.DailyAppUsageEntity
import com.example.data.local.entity.DailyUnlockEntity
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.KeywordEntity
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.ScratchCardEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyChannelEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.TopicEntity

@Database(
    entities = [
        SessionRecordEntity::class,
        SubjectEntity::class,
        TopicEntity::class,
        StudyPlanEntity::class,
        BlockedAppEntity::class,
        BlockedAttemptEntity::class,
        BreakRecordEntity::class,
        StudyChannelEntity::class,
        StudyActivityEntity::class,
        AppLimitEntity::class,
        DailyAppUsageEntity::class,
        AppLimitSessionEntity::class,
        BlockedWebsiteEntity::class,
        FocusScheduleEntity::class,
        KeywordEntity::class,
        DailyUnlockEntity::class,
        ScratchCardEntity::class,
        SyncOutboxEntity::class
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FocusShieldDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun subjectDao(): SubjectDao
    abstract fun topicDao(): TopicDao
    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun blockedAttemptDao(): BlockedAttemptDao
    abstract fun breakRecordDao(): BreakRecordDao
    abstract fun studyChannelDao(): StudyChannelDao
    abstract fun studyActivityDao(): StudyActivityDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun appLimitSessionDao(): AppLimitSessionDao
    abstract fun blockedWebsiteDao(): BlockedWebsiteDao
    abstract fun focusScheduleDao(): FocusScheduleDao
    abstract fun keywordDao(): KeywordDao
    abstract fun dailyUnlockDao(): DailyUnlockDao
    abstract fun scratchCardDao(): ScratchCardDao
    abstract fun syncOutboxDao(): SyncOutboxDao

    /** Bulk read/upsert/clear access to the cloud-synced tables (sync engine only). */
    abstract fun cloudBulkDao(): CloudBulkDao

    companion object {
        @Volatile
        private var INSTANCE: FocusShieldDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_activities` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `sessionId` TEXT NOT NULL,
                        `activityType` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `subject` TEXT NOT NULL,
                        `topic` TEXT NOT NULL,
                        `channelName` TEXT,
                        `channelId` TEXT,
                        `videoTitle` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `durationMillis` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_activities_sessionId` ON `study_activities` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_activities_startedAt` ON `study_activities` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_activities_subject` ON `study_activities` (`subject`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_activities_source` ON `study_activities` (`source`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `dateString` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `startTime` TEXT NOT NULL DEFAULT '08:00'")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `endTime` TEXT NOT NULL DEFAULT '09:00'")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `startMinutes` INTEGER NOT NULL DEFAULT 480")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `endMinutes` INTEGER NOT NULL DEFAULT 540")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'PLANNED'")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_limits` (
                        `packageName` TEXT NOT NULL PRIMARY KEY,
                        `appName` TEXT NOT NULL,
                        `dailyLimitMinutes` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `isStrictOverride` INTEGER NOT NULL DEFAULT 0,
                        `showRemindersBeforeLimit` INTEGER NOT NULL DEFAULT 1,
                        `emergencyUsesAllowed` INTEGER NOT NULL DEFAULT 1,
                        `streakDays` INTEGER NOT NULL DEFAULT 0,
                        `lastStreakDate` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_app_usage` (
                        `packageName` TEXT NOT NULL,
                        `dateString` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `usedMillis` INTEGER NOT NULL DEFAULT 0,
                        `emergencyUsedMillis` INTEGER NOT NULL DEFAULT 0,
                        `emergencyUsesCount` INTEGER NOT NULL DEFAULT 0,
                        `isBypassedForToday` INTEGER NOT NULL DEFAULT 0,
                        `lastActiveTimestamp` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`packageName`, `dateString`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_app_usage_dateString` ON `daily_app_usage` (`dateString`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_app_usage_packageName` ON `daily_app_usage` (`packageName`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_limit_sessions` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `dateString` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `selectedDurationMillis` INTEGER NOT NULL,
                        `actualUsedMillis` INTEGER NOT NULL,
                        `isEmergency` INTEGER NOT NULL DEFAULT 0,
                        `endReason` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_limit_sessions_packageName` ON `app_limit_sessions` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_limit_sessions_dateString` ON `app_limit_sessions` (`dateString`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_limit_sessions_startedAt` ON `app_limit_sessions` (`startedAt`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_websites` (
                        `domain` TEXT NOT NULL PRIMARY KEY,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `category` TEXT NOT NULL DEFAULT 'MANUAL',
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `showRemindersBeforeLimit` INTEGER NOT NULL DEFAULT 1")
                } catch (e: Exception) {
                    // Column already exists
                }
                try {
                    db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `emergencyUsesAllowed` INTEGER NOT NULL DEFAULT 1")
                } catch (e: Exception) {
                    // Column already exists
                }
                try {
                    db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `streakDays` INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Column already exists
                }
                try {
                    db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `lastStreakDate` TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // Column already exists
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_schedules` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `daysOfWeek` TEXT NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
                        `startTime` TEXT NOT NULL DEFAULT '09:00',
                        `endTime` TEXT NOT NULL DEFAULT '12:00',
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `isAutoStartSession` INTEGER NOT NULL DEFAULT 1,
                        `mode` TEXT NOT NULL DEFAULT 'TIMER',
                        `subjectName` TEXT NOT NULL DEFAULT 'General Study',
                        `colorHex` TEXT NOT NULL DEFAULT '#4F46E5',
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `keywords` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `keyword` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_keywords_type` ON `keywords` (`type`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_unlocks` (
                        `dateString` TEXT NOT NULL PRIMARY KEY,
                        `unlockCount` INTEGER NOT NULL DEFAULT 0,
                        `firstUnlockAt` INTEGER NOT NULL DEFAULT 0,
                        `lastUnlockAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_unlocks_dateString` ON `daily_unlocks` (`dateString`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `repeatEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `scheduledDateMillis` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `breakMinutes` INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `blockedAppPackages` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `focus_schedules` ADD COLUMN `blockNotifications` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scratch_cards` (
                        `sessionId` TEXT NOT NULL PRIMARY KEY,
                        `createdAt` INTEGER NOT NULL,
                        `rewardType` TEXT NOT NULL,
                        `rewardEmoji` TEXT NOT NULL,
                        `rewardTitle` TEXT NOT NULL,
                        `rewardMessage` TEXT NOT NULL,
                        `studyMinutes` INTEGER NOT NULL,
                        `isRevealed` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scratch_cards_createdAt` ON `scratch_cards` (`createdAt`)")
            }
        }

        /**
         * v11 -> v12 (additive): introduces the cloud sync outbox. No existing table is altered;
         * this is purely a new table so every prior release migrates losslessly.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_outbox` (
                        `seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tableName` TEXT NOT NULL,
                        `rowId` TEXT NOT NULL,
                        `op` TEXT NOT NULL,
                        `payload` TEXT,
                        `attemptCount` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_tableName_rowId` ON `sync_outbox` (`tableName`, `rowId`)")
            }
        }

        /**
         * v12 -> v13: gives `blocked_attempts` a real event model so protection history can be
         * synced and reconstructed (stable event identity + typed event/source + the payload
         * fields the app already has at block time).
         *
         * Purely additive — every column is added with `ALTER TABLE`, so no table is recreated and
         * no existing row is lost. Legacy rows are then backfilled in place:
         *
         *  - `eventId` becomes `'legacy-' || id`. Deterministic on purpose: re-pushing a legacy row
         *    yields the same cloud primary key, so migration cannot duplicate history. (`deviceId`
         *    stays NULL for these rows — they predate install identity.)
         *  - `eventType`/`source` are derived from the string conventions the old writers used, so
         *    pre-existing history gains real types instead of being dropped.
         *  - the domain / channel that used to be packed into `appName` is split into its own
         *    column, and the silenced-notification suffix is trimmed, so `appName` is an app name
         *    again.
         *
         * The unique index on `eventId` is created only after the backfill, so the UPDATE that
         * populates it cannot trip a uniqueness violation mid-migration.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `eventId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `eventType` TEXT NOT NULL DEFAULT 'LEGACY'")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'LEGACY'")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `domain` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `channelId` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `channelName` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `videoTitle` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `matchedKeyword` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `ruleRef` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `scheduleId` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `subject` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `topic` TEXT")
                db.execSQL("ALTER TABLE `blocked_attempts` ADD COLUMN `deviceId` TEXT")

                db.execSQL("UPDATE `blocked_attempts` SET `eventId` = 'legacy-' || `id` WHERE `eventId` = ''")

                // Classify against the ORIGINAL appName strings (must run before the cleanup below).
                db.execSQL(
                    """
                    UPDATE `blocked_attempts` SET `eventType` = CASE
                        WHEN `appName` LIKE '% (Silenced Notification)' THEN 'NOTIFICATION_SILENCED'
                        WHEN `appName` LIKE 'Blocked Website (%' OR `appName` LIKE '18+ Adult Site (%' THEN 'WEBSITE_BLOCKED'
                        WHEN `appName` LIKE '% Reels' OR `appName` IN ('YouTube Shorts', 'Short-form Content') THEN 'SHORTS_BLOCKED'
                        WHEN `appName` LIKE 'YouTube (%' THEN 'YOUTUBE_UNAPPROVED_CHANNEL'
                        WHEN `appName` = 'YouTube' THEN 'YOUTUBE_UNKNOWN_CONTENT'
                        WHEN `appName` = 'Split-Screen Multitasking' THEN 'SPLIT_SCREEN_BLOCKED'
                        WHEN `appName` = 'Floating Window / PiP' THEN 'FLOATING_WINDOW_BLOCKED'
                        ELSE 'APP_BLOCKED'
                    END
                    """.trimIndent()
                )

                // Unpack `Blocked Website (x.com)` / `18+ Adult Site (x.com)` -> domain.
                db.execSQL(
                    """
                    UPDATE `blocked_attempts`
                    SET `domain` = substr(`appName`, length('Blocked Website (') + 1,
                                          length(`appName`) - length('Blocked Website (') - 1)
                    WHERE (`appName` LIKE 'Blocked Website (%' OR `appName` LIKE '18+ Adult Site (%')
                      AND `domain` IS NULL
                    """.trimIndent()
                )

                // Unpack `YouTube (Channel Name)` -> channelName.
                db.execSQL(
                    """
                    UPDATE `blocked_attempts`
                    SET `channelName` = substr(`appName`, length('YouTube (') + 1,
                                               length(`appName`) - length('YouTube (') - 1)
                    WHERE `appName` LIKE 'YouTube (%' AND `channelName` IS NULL
                    """.trimIndent()
                )

                // Trim the ` (Silenced Notification)` suffix so appName is the bare app label again.
                db.execSQL(
                    """
                    UPDATE `blocked_attempts`
                    SET `appName` = substr(`appName`, 1, length(`appName`) - length(' (Silenced Notification)'))
                    WHERE `appName` LIKE '% (Silenced Notification)'
                    """.trimIndent()
                )

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_blocked_attempts_eventId` ON `blocked_attempts` (`eventId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocked_attempts_timestamp` ON `blocked_attempts` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocked_attempts_sessionId` ON `blocked_attempts` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocked_attempts_eventType` ON `blocked_attempts` (`eventType`)")
            }
        }

        fun getInstance(context: Context): FocusShieldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusShieldDatabase::class.java,
                    "focus_shield_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
