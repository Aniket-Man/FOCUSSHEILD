package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.AppLimitSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for recorded individual App Limit temporary sessions.
 */
@Dao
interface AppLimitSessionDao {

    @Query("SELECT * FROM app_limit_sessions WHERE dateString = :dateString ORDER BY startedAt DESC")
    fun getSessionsForDateFlow(dateString: String): Flow<List<AppLimitSessionEntity>>

    @Query("SELECT * FROM app_limit_sessions WHERE dateString = :dateString ORDER BY startedAt DESC")
    suspend fun getSessionsForDate(dateString: String): List<AppLimitSessionEntity>

    @Query("SELECT * FROM app_limit_sessions WHERE packageName = :packageName AND dateString = :dateString ORDER BY startedAt DESC")
    fun getSessionsForPackageFlow(packageName: String, dateString: String): Flow<List<AppLimitSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AppLimitSessionEntity)

    @Query("SELECT COUNT(*) FROM app_limit_sessions WHERE packageName = :packageName AND dateString = :dateString")
    suspend fun getSessionCountToday(packageName: String, dateString: String): Int

    @Query("DELETE FROM app_limit_sessions WHERE packageName = :packageName")
    suspend fun deleteSessionsForPackage(packageName: String)

    @Query("DELETE FROM app_limit_sessions WHERE dateString < :olderThanDate")
    suspend fun pruneOldSessions(olderThanDate: String)
}
