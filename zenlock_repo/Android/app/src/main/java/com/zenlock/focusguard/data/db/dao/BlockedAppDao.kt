package com.zenlock.focusguard.data.db.dao

import androidx.room.*
import com.zenlock.focusguard.data.db.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for blocked apps CRUD operations.
 */
@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps ORDER BY appName ASC")
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isBlocked = 1")
    fun getActiveBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps WHERE isBlocked = 1")
    suspend fun getActiveBlockedAppsList(): List<BlockedAppEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_apps WHERE packageName = :packageName AND isBlocked = 1)")
    suspend fun isAppBlocked(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApp(app: BlockedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApps(apps: List<BlockedAppEntity>)

    @Update
    suspend fun updateBlockedApp(app: BlockedAppEntity)

    @Delete
    suspend fun deleteBlockedApp(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("SELECT COUNT(*) FROM blocked_apps WHERE isBlocked = 1")
    fun getBlockedAppCount(): Flow<Int>
}
