package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DailyUnlockEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for daily phone unlock records.
 */
@Dao
interface DailyUnlockDao {

    @Query("SELECT * FROM daily_unlocks WHERE dateString = :dateString LIMIT 1")
    fun getUnlockFlow(dateString: String): Flow<DailyUnlockEntity?>

    @Query("SELECT * FROM daily_unlocks WHERE dateString = :dateString LIMIT 1")
    suspend fun getUnlock(dateString: String): DailyUnlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(unlock: DailyUnlockEntity)

    @Query("SELECT * FROM daily_unlocks WHERE dateString BETWEEN :fromDate AND :toDate ORDER BY dateString ASC")
    suspend fun getUnlocksBetween(fromDate: String, toDate: String): List<DailyUnlockEntity>

    @Query("SELECT * FROM daily_unlocks WHERE dateString BETWEEN :fromDate AND :toDate ORDER BY dateString ASC")
    fun getUnlocksBetweenFlow(fromDate: String, toDate: String): Flow<List<DailyUnlockEntity>>

    @Query("SELECT AVG(unlockCount) FROM daily_unlocks WHERE dateString BETWEEN :fromDate AND :toDate")
    suspend fun getAverageUnlocksBetween(fromDate: String, toDate: String): Double?

    @Query("DELETE FROM daily_unlocks WHERE dateString < :olderThanDate")
    suspend fun pruneOldRecords(olderThanDate: String)
}
