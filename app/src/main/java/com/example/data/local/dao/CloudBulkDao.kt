package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.BlockedAttemptEntity
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.local.entity.BreakRecordEntity
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.KeywordEntity
import com.example.data.local.entity.ScratchCardEntity
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyChannelEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.TopicEntity

/**
 * Bulk read/upsert/clear access to the cloud-synced tables, used only by the sync engine.
 *
 * Room validates that every entity used here is declared on [com.example.data.local.FocusShieldDatabase].
 * No table schema is changed by this interface: `@Insert(REPLACE)` upserts on the entity's primary
 * key (which is a stable client-generated id, or the natural package/domain key). Reconcile is
 * implemented by the engine as delete-all-then-reinsert so that no per-row delete-by-key queries are
 * needed and locally-pending rows are preserved by re-inserting them after the remote set.
 *
 * All methods are suspend and intended to run inside the sync engine's serialized transaction.
 */
@Dao
interface CloudBulkDao {

    // ---- study_subjects -------------------------------------------------------------

    @Query("SELECT * FROM study_subjects")
    suspend fun subjects(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubjects(rows: List<SubjectEntity>)

    @Query("DELETE FROM study_subjects")
    suspend fun clearSubjects()

    // ---- study_topics ---------------------------------------------------------------

    @Query("SELECT * FROM study_topics")
    suspend fun topics(): List<TopicEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopics(rows: List<TopicEntity>)

    @Query("DELETE FROM study_topics")
    suspend fun clearTopics()

    // ---- study_plans ----------------------------------------------------------------

    @Query("SELECT * FROM study_plans")
    suspend fun plans(): List<StudyPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlans(rows: List<StudyPlanEntity>)

    @Query("DELETE FROM study_plans")
    suspend fun clearPlans()

    // ---- focus_schedules ------------------------------------------------------------

    @Query("SELECT * FROM focus_schedules")
    suspend fun schedules(): List<FocusScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedules(rows: List<FocusScheduleEntity>)

    @Query("DELETE FROM focus_schedules")
    suspend fun clearSchedules()

    // ---- blocked_apps ---------------------------------------------------------------

    @Query("SELECT * FROM blocked_apps")
    suspend fun blockedApps(): List<BlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedApps(rows: List<BlockedAppEntity>)

    @Query("DELETE FROM blocked_apps")
    suspend fun clearBlockedApps()

    // ---- blocked_websites -----------------------------------------------------------

    @Query("SELECT * FROM blocked_websites")
    suspend fun blockedWebsites(): List<BlockedWebsiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedWebsites(rows: List<BlockedWebsiteEntity>)

    @Query("DELETE FROM blocked_websites")
    suspend fun clearBlockedWebsites()

    // ---- app_limits -----------------------------------------------------------------
    //
    // Deliberately absent. App limits are device-local in their entirety, so this DAO — which exists
    // solely to serve the sync engine — must have no way to read or clear them.

    // ---- study_channels -------------------------------------------------------------

    @Query("SELECT * FROM study_channels")
    suspend fun channels(): List<StudyChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(rows: List<StudyChannelEntity>)

    @Query("DELETE FROM study_channels")
    suspend fun clearChannels()

    // ---- keywords (natural key type+keyword; local autogen id is NOT meaningful) ----
    //
    // Keyword rows have no stable Room PK (auto-increment `id`), so an @Insert(REPLACE) upsert on
    // `id` cannot work for a batch of rows (they would all collide on the default 0). Instead the
    // engine always reconciles keywords as clear-then-insert: clearKeywords() wipes the table and
    // upsertKeywords() inserts the surviving rows (remote minus locally-pending, plus pending local
    // upserts) with fresh auto-generated ids. IGNORE makes the batch tolerant of any residual
    // natural-key duplicates from before sync.

    @Query("SELECT * FROM keywords")
    suspend fun keywords(): List<KeywordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertKeywords(rows: List<KeywordEntity>)

    @Query("DELETE FROM keywords")
    suspend fun clearKeywords()

    // ---- session_records (history) --------------------------------------------------

    @Query("SELECT * FROM session_records")
    suspend fun sessions(): List<SessionRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionsMissing(rows: List<SessionRecordEntity>)

    @Query("DELETE FROM session_records")
    suspend fun clearSessions()

    // ---- study_activities (history) -------------------------------------------------

    @Query("SELECT * FROM study_activities")
    suspend fun activities(): List<StudyActivityEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertActivitiesMissing(rows: List<StudyActivityEntity>)

    @Query("DELETE FROM study_activities")
    suspend fun clearActivities()

    // ---- break_records (history) ----------------------------------------------------

    @Query("SELECT * FROM break_records")
    suspend fun breaks(): List<BreakRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBreaksMissing(rows: List<BreakRecordEntity>)

    @Query("DELETE FROM break_records")
    suspend fun clearBreaks()

    // ---- blocked_attempts (history) -------------------------------------------------
    //
    // `eventId` is the cloud identity and carries a unique index, so IGNORE makes a re-pulled or
    // re-uploaded event idempotent even though the Room PK stays an auto-increment surrogate.

    @Query("SELECT * FROM blocked_attempts")
    suspend fun blockedAttempts(): List<BlockedAttemptEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlockedAttemptsMissing(rows: List<BlockedAttemptEntity>)

    @Query("DELETE FROM blocked_attempts")
    suspend fun clearBlockedAttempts()

    // ---- scratch_cards (history) ----------------------------------------------------

    @Query("SELECT * FROM scratch_cards")
    suspend fun scratchCards(): List<ScratchCardEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScratchCardsMissing(rows: List<ScratchCardEntity>)

    @Query("DELETE FROM scratch_cards")
    suspend fun clearScratchCards()

    // ---- app_limit_sessions, daily_app_usage, daily_unlocks -------------------------
    //
    // Deliberately absent. None of these three tables participates in sync: app_limit_sessions is
    // per-day enforcement history (§13), daily_app_usage is Android usage statistics (§12), and
    // daily_unlocks is general device usage statistics (§12). They stay in Room, read by the
    // enforcement engine, the app-limit UI and the widget — never by the sync engine.
}
