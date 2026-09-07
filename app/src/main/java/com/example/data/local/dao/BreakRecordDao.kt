package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BreakRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreak(breakRecord: BreakRecordEntity)

    @Update
    suspend fun updateBreak(breakRecord: BreakRecordEntity)

    @Query("SELECT * FROM break_records WHERE sessionId = :sessionId ORDER BY startTime ASC")
    fun getBreaksForSessionFlow(sessionId: String): Flow<List<BreakRecordEntity>>

    @Query("SELECT * FROM break_records WHERE sessionId = :sessionId ORDER BY startTime ASC")
    suspend fun getBreaksForSession(sessionId: String): List<BreakRecordEntity>

    @Query("SELECT * FROM break_records WHERE startTime >= :startTime AND endTime <= :endTime ORDER BY startTime DESC")
    fun getBreaksBetweenFlow(startTime: Long, endTime: Long): Flow<List<BreakRecordEntity>>

    @Query("SELECT * FROM break_records WHERE startTime >= :startTime AND endTime <= :endTime")
    suspend fun getBreaksBetween(startTime: Long, endTime: Long): List<BreakRecordEntity>

    @Query("SELECT SUM(actualDurationMillis) FROM break_records WHERE startTime >= :startTime AND endTime <= :endTime")
    fun getTotalBreakDurationBetweenFlow(startTime: Long, endTime: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM break_records WHERE startTime >= :startTime AND endTime <= :endTime")
    fun getBreakCountBetweenFlow(startTime: Long, endTime: Long): Flow<Int>
}
