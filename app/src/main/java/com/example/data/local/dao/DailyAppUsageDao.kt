package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DailyAppUsageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for daily accumulated app usage records.
 */
@Dao
interface DailyAppUsageDao {

    @Query("SELECT * FROM daily_app_usage WHERE dateString = :dateString")
    fun getUsageForDateFlow(dateString: String): Flow<List<DailyAppUsageEntity>>

    @Query("SELECT * FROM daily_app_usage WHERE dateString = :dateString")
    suspend fun getUsageForDate(dateString: String): List<DailyAppUsageEntity>

    @Query("SELECT * FROM daily_app_usage WHERE packageName = :packageName AND dateString = :dateString LIMIT 1")
    suspend fun getUsage(packageName: String, dateString: String): DailyAppUsageEntity?

    @Query("SELECT * FROM daily_app_usage WHERE packageName = :packageName AND dateString = :dateString LIMIT 1")
    fun getUsageFlow(packageName: String, dateString: String): Flow<DailyAppUsageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(usage: DailyAppUsageEntity)

    @Query("UPDATE daily_app_usage SET usedMillis = usedMillis + :deltaMillis, lastActiveTimestamp = :timestamp WHERE packageName = :packageName AND dateString = :dateString")
    suspend fun addUsedTime(packageName: String, dateString: String, deltaMillis: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE daily_app_usage SET emergencyUsedMillis = emergencyUsedMillis + :deltaMillis, lastActiveTimestamp = :timestamp WHERE packageName = :packageName AND dateString = :dateString")
    suspend fun addEmergencyTime(packageName: String, dateString: String, deltaMillis: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE daily_app_usage SET emergencyUsesCount = :count WHERE packageName = :packageName AND dateString = :dateString")
    suspend fun setEmergencyUsesCount(packageName: String, dateString: String, count: Int)

    @Query("UPDATE daily_app_usage SET isBypassedForToday = :bypassed WHERE packageName = :packageName AND dateString = :dateString")
    suspend fun setBypassedForToday(packageName: String, dateString: String, bypassed: Boolean)

    @Query("SELECT SUM(usedMillis + emergencyUsedMillis) FROM daily_app_usage WHERE dateString = :dateString")
    fun getTotalLimitedAppUsageForDateFlow(dateString: String): Flow<Long?>

    @Query("DELETE FROM daily_app_usage WHERE packageName = :packageName")
    suspend fun deleteUsageForPackage(packageName: String)

    @Query("DELETE FROM daily_app_usage WHERE dateString < :olderThanDate")
    suspend fun pruneOldRecords(olderThanDate: String)
}
