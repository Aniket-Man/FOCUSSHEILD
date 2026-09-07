package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<BlockedAppEntity>)

    @Update
    suspend fun updateBlockedApp(app: BlockedAppEntity)

    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getAllBlockedAppsFlow(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isEnabled = 1")
    fun getEnabledBlockedAppsFlow(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isEnabled = 1")
    suspend fun getEnabledBlockedApps(): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getBlockedAppByPackage(packageName: String): BlockedAppEntity?

    @Query("UPDATE blocked_apps SET isEnabled = :isEnabled WHERE packageName = :packageName")
    suspend fun updateAppBlockedStatus(packageName: String, isEnabled: Boolean)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteBlockedApp(packageName: String)
}
