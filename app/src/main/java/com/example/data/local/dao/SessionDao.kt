package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SessionRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionRecordEntity)

    @Update
    suspend fun updateSession(session: SessionRecordEntity)

    @Query("SELECT * FROM session_records WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): SessionRecordEntity?

    @Query("SELECT * FROM session_records ORDER BY endTime DESC")
    fun getAllSessionsFlow(): Flow<List<SessionRecordEntity>>

    @Query("SELECT * FROM session_records ORDER BY endTime DESC LIMIT :limit")
    fun getRecentSessionsFlow(limit: Int = 10): Flow<List<SessionRecordEntity>>

    @Query("SELECT * FROM session_records WHERE startTime >= :startTime AND endTime <= :endTime ORDER BY endTime DESC")
    fun getSessionsBetweenFlow(startTime: Long, endTime: Long): Flow<List<SessionRecordEntity>>

    @Query("SELECT * FROM session_records WHERE startTime >= :startTime AND endTime <= :endTime")
    suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<SessionRecordEntity>

    @Query("SELECT * FROM session_records WHERE subject = :subject ORDER BY endTime DESC")
    fun getSessionsForSubjectFlow(subject: String): Flow<List<SessionRecordEntity>>

    @Query("SELECT COUNT(*) FROM session_records WHERE completed = 1 AND startTime >= :startOfDay")
    fun getCompletedSessionsCountSinceFlow(startOfDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM session_records WHERE completed = 1 AND startTime >= :startTime AND endTime <= :endTime")
    fun getCompletedSessionsCountBetweenFlow(startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT SUM(actualDurationMillis) FROM session_records WHERE startTime >= :startOfDay")
    fun getTotalStudyTimeMillisSinceFlow(startOfDay: Long): Flow<Long?>

    @Query("SELECT SUM(actualDurationMillis) FROM session_records WHERE startTime >= :startTime AND endTime <= :endTime")
    fun getTotalStudyTimeMillisBetweenFlow(startTime: Long, endTime: Long): Flow<Long?>

    @Query("SELECT SUM(actualDurationMillis) FROM session_records")
    fun getAllTimeStudyTimeMillisFlow(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM session_records")
    fun getAllTimeSessionsCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM session_records WHERE completed = 1")
    fun getAllTimeCompletedSessionsCountFlow(): Flow<Int>

    @Query("SELECT * FROM session_records ORDER BY startTime ASC")
    suspend fun getAllSessionsAscending(): List<SessionRecordEntity>

    @Query("DELETE FROM session_records WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("DELETE FROM session_records")
    suspend fun deleteAllSessions()
}
