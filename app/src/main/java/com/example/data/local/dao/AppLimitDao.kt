package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AppLimitEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for AppLimit configurations.
 */
@Dao
interface AppLimitDao {

    @Query("SELECT * FROM app_limits ORDER BY appName ASC")
    fun getAllLimitsFlow(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits ORDER BY appName ASC")
    suspend fun getAllLimits(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE isEnabled = 1")
    suspend fun getActiveLimits(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE isEnabled = 1")
    fun getActiveLimitsFlow(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getLimitByPackage(packageName: String): AppLimitEntity?

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    fun getLimitByPackageFlow(packageName: String): Flow<AppLimitEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(limit: AppLimitEntity)

    @Update
    suspend fun update(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("UPDATE app_limits SET isEnabled = :isEnabled, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, isEnabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE app_limits SET dailyLimitMinutes = :minutes, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateDailyLimitMinutes(packageName: String, minutes: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE app_limits SET isStrictOverride = :isStrict, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateStrictMode(packageName: String, isStrict: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE app_limits SET streakDays = :streak, lastStreakDate = :dateStr, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateStreak(packageName: String, streak: Int, dateStr: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE app_limits SET streakDays = 0, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun resetStreak(packageName: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE app_limits SET showRemindersBeforeLimit = :show, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateRemindersSetting(packageName: String, show: Boolean, timestamp: Long = System.currentTimeMillis())
}
