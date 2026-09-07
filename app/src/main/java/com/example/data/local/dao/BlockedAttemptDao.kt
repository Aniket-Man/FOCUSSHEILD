package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BlockedAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: BlockedAttemptEntity): Long

    @Query("SELECT * FROM blocked_attempts ORDER BY timestamp DESC")
    fun getAllAttemptsFlow(): Flow<List<BlockedAttemptEntity>>

    @Query("SELECT * FROM blocked_attempts WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getAttemptsSinceFlow(sinceTimestamp: Long): Flow<List<BlockedAttemptEntity>>

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE timestamp >= :sinceTimestamp")
    fun getAttemptCountSinceFlow(sinceTimestamp: Long): Flow<Int>

    @Query("SELECT * FROM blocked_attempts WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getAttemptsForSessionFlow(sessionId: String): Flow<List<BlockedAttemptEntity>>

    @Query("SELECT * FROM blocked_attempts WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getAttemptsForSession(sessionId: String): List<BlockedAttemptEntity>

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE sessionId = :sessionId")
    fun getAttemptCountForSessionFlow(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE sessionId = :sessionId")
    suspend fun getAttemptCountForSession(sessionId: String): Int

    @Query("SELECT * FROM blocked_attempts WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getAttemptsBetweenFlow(startTime: Long, endTime: Long): Flow<List<BlockedAttemptEntity>>

    @Query("SELECT * FROM blocked_attempts WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getAttemptsBetween(startTime: Long, endTime: Long): List<BlockedAttemptEntity>

    @Query("SELECT COUNT(*) FROM blocked_attempts WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getAttemptCountBetweenFlow(startTime: Long, endTime: Long): Flow<Int>

    @Query("DELETE FROM blocked_attempts")
    suspend fun clearAllAttempts()
}
