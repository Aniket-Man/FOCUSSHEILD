package com.example.data.repository

import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.data.local.dao.BlockedAppDao
import com.example.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Distinguishes a committed local toggle from a write that never happened. */
sealed interface BlockedAppWriteResult {
    data class Applied(val followUpFailure: Exception? = null) : BlockedAppWriteResult
    data class Failed(val exception: Exception) : BlockedAppWriteResult
}

class BlockedAppRepository(
    private val blockedAppDao: BlockedAppDao
) {
    private val mutationMutex = Mutex()

    val allBlockedApps: Flow<List<BlockedAppEntity>> = blockedAppDao.getAllBlockedAppsFlow()
    val enabledBlockedApps: Flow<List<BlockedAppEntity>> = blockedAppDao.getEnabledBlockedAppsFlow()

    suspend fun getEnabledBlockedAppsList(): List<BlockedAppEntity> {
        return blockedAppDao.getEnabledBlockedApps()
    }

    suspend fun setAppBlocked(packageName: String, appName: String, isBlocked: Boolean) {
        setAppBlockedResult(packageName, appName, isBlocked)
    }

    /**
     * Serialises toggles and finishes the short Room write even when the screen is recreated.
     * Backup tracking is follow-up work unless the write itself failed.
     */
    suspend fun setAppBlockedResult(
        packageName: String,
        appName: String,
        isBlocked: Boolean
    ): BlockedAppWriteResult = mutationMutex.withLock {
        try {
            val saved = withContext(NonCancellable) {
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
                // Read back the full row (insert or update) so the reconcile pull never reverts it.
                blockedAppDao.getBlockedAppByPackage(packageName)
            } ?: return@withLock BlockedAppWriteResult.Applied()

            val followUpFailure = try {
                SyncTracker.enqueueUpsert(
                    SyncTables.BLOCKED_APPS,
                    saved.packageName,
                    CloudJson.blockedAppToJson(saved).toString()
                )
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                exception
            }
            BlockedAppWriteResult.Applied(followUpFailure)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            BlockedAppWriteResult.Failed(exception)
        }
    }

    suspend fun isPackageBlocked(packageName: String): Boolean {
        val app = blockedAppDao.getBlockedAppByPackage(packageName)
        return app?.isEnabled == true
    }

    suspend fun removeBlockedApp(packageName: String) {
        blockedAppDao.deleteBlockedApp(packageName)
        SyncTracker.enqueueDelete(SyncTables.BLOCKED_APPS, packageName)
    }
}
