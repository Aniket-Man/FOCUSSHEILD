package com.zenlock.focusguard.domain.repository

import com.zenlock.focusguard.data.db.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

interface BlockedAppRepository {
    fun getAllBlockedApps(): Flow<List<BlockedAppEntity>>
    fun getActiveBlockedApps(): Flow<List<BlockedAppEntity>>
    suspend fun getActiveBlockedAppsList(): List<BlockedAppEntity>
    suspend fun isAppBlocked(packageName: String): Boolean
    suspend fun addBlockedApp(app: BlockedAppEntity)
    suspend fun addBlockedApps(apps: List<BlockedAppEntity>)
    suspend fun updateBlockedApp(app: BlockedAppEntity)
    suspend fun removeBlockedApp(app: BlockedAppEntity)
    suspend fun removeByPackageName(packageName: String)
    fun getBlockedAppCount(): Flow<Int>
}
