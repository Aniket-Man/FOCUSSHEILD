package com.example.data.repository

import com.example.data.local.dao.BlockedAppDao
import com.example.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

class BlockedAppRepository(
    private val blockedAppDao: BlockedAppDao
) {
    val allBlockedApps: Flow<List<BlockedAppEntity>> = blockedAppDao.getAllBlockedAppsFlow()
    val enabledBlockedApps: Flow<List<BlockedAppEntity>> = blockedAppDao.getEnabledBlockedAppsFlow()

    suspend fun getEnabledBlockedAppsList(): List<BlockedAppEntity> {
        return blockedAppDao.getEnabledBlockedApps()
    }

    suspend fun setAppBlocked(packageName: String, appName: String, isBlocked: Boolean) {
        val existing = blockedAppDao.getBlockedAppByPackage(packageName)
        if (existing != null) {
            blockedAppDao.updateAppBlockedStatus(packageName, isBlocked)
        } else {
            blockedAppDao.insertBlockedApp(
                BlockedAppEntity(
                    packageName = packageName,
                    appName = appName,
                    isEnabled = isBlocked
                )
            )
        }
    }

    suspend fun isPackageBlocked(packageName: String): Boolean {
        val app = blockedAppDao.getBlockedAppByPackage(packageName)
        return app?.isEnabled == true
    }

    suspend fun removeBlockedApp(packageName: String) {
        blockedAppDao.deleteBlockedApp(packageName)
    }
}
