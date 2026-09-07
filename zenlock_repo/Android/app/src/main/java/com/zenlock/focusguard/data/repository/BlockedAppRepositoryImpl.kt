package com.zenlock.focusguard.data.repository

import com.zenlock.focusguard.data.db.dao.BlockedAppDao
import com.zenlock.focusguard.data.db.entity.BlockedAppEntity
import com.zenlock.focusguard.domain.repository.BlockedAppRepository
import kotlinx.coroutines.flow.Flow

class BlockedAppRepositoryImpl(
    private val dao: BlockedAppDao
) : BlockedAppRepository {

    override fun getAllBlockedApps(): Flow<List<BlockedAppEntity>> =
        dao.getAllBlockedApps()

    override fun getActiveBlockedApps(): Flow<List<BlockedAppEntity>> =
        dao.getActiveBlockedApps()

    override suspend fun getActiveBlockedAppsList(): List<BlockedAppEntity> =
        dao.getActiveBlockedAppsList()

    override suspend fun isAppBlocked(packageName: String): Boolean =
        dao.isAppBlocked(packageName)

    override suspend fun addBlockedApp(app: BlockedAppEntity) =
        dao.insertBlockedApp(app)

    override suspend fun addBlockedApps(apps: List<BlockedAppEntity>) =
        dao.insertBlockedApps(apps)

    override suspend fun updateBlockedApp(app: BlockedAppEntity) =
        dao.updateBlockedApp(app)

    override suspend fun removeBlockedApp(app: BlockedAppEntity) =
        dao.deleteBlockedApp(app)

    override suspend fun removeByPackageName(packageName: String) =
        dao.deleteByPackageName(packageName)

    override fun getBlockedAppCount(): Flow<Int> =
        dao.getBlockedAppCount()
}
