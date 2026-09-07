package com.zenlock.focusguard.data.db.dao

import androidx.room.*
import com.zenlock.focusguard.data.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for focus session CRUD and statistics queries.
 */
@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE isCompleted = 1 ORDER BY startTime DESC")
    fun getCompletedSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :startTime ORDER BY startTime DESC")
    fun getSessionsSince(startTime: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :startTime AND actualDurationSeconds > 0 ORDER BY startTime DESC")
    suspend fun getAllSessionsSince(startTime: Long): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE startTime >= :startTime AND isCompleted = 1")
    suspend fun getCompletedSessionsSince(startTime: Long): List<FocusSessionEntity>

    @Query("SELECT SUM(actualDurationSeconds) FROM focus_sessions WHERE startTime >= :startTime AND actualDurationSeconds > 0")
    suspend fun getTotalFocusTimeSince(startTime: Long): Long?

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE startTime >= :startTime AND actualDurationSeconds > 0")
    suspend fun getSessionCountSince(startTime: Long): Int

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE startTime >= :startTime AND isCompleted = 1")
    suspend fun getCompletedSessionCountSince(startTime: Long): Int

    @Query("SELECT SUM(blockedAttempts) FROM focus_sessions WHERE startTime >= :startTime")
    suspend fun getTotalBlockedAttemptsSince(startTime: Long): Int?

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAllSessions()
}
