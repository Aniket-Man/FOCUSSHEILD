package com.example.data.repository

import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncKeycode
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.data.local.dao.AppLimitDao
import com.example.data.local.dao.AppLimitSessionDao
import com.example.data.local.dao.DailyAppUsageDao
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.AppLimitSessionEntity
import com.example.data.local.entity.DailyAppUsageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository coordinating app limit configs, daily accumulated usage, and temporary usage sessions.
 */
class AppLimitRepository(
    private val appLimitDao: AppLimitDao,
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val appLimitSessionDao: AppLimitSessionDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // --- Sync helpers: enqueue a row so the pull that follows never reverts a local write.
    //
    // Column split for `daily_app_usage`: only the usage columns are account-level (CloudJson's
    // converter drops the rest), so the emergency/bypass setters below deliberately enqueue
    // nothing — there is no remote column for them to disagree with.

    private suspend fun enqueueLimitUpsert(e: AppLimitEntity) {
        SyncTracker.enqueueUpsert(
            SyncTables.APP_LIMITS,
            e.packageName,
            CloudJson.appLimitToJson(e).toString()
        )
    }

    private suspend fun enqueueLimitDelete(packageName: String) {
        SyncTracker.enqueueDelete(SyncTables.APP_LIMITS, packageName)
    }

    private suspend fun enqueueLimitState(packageName: String) {
        appLimitDao.getLimitByPackage(packageName)?.let { enqueueLimitUpsert(it) }
    }

    /**
     * Enqueues the row's post-write state. The usage DAO increments with SQL (`addUsedTime`), so the
     * row has to be read back rather than recomputed here — a stale total would be pushed as the
     * account's usage figure.
     */
    private suspend fun enqueueAppUsageUpsert(packageName: String, dateString: String) {
        val row = dailyAppUsageDao.getUsage(packageName, dateString) ?: return
        SyncTracker.enqueueUpsert(
            SyncTables.DAILY_APP_USAGE,
            SyncKeycode.appUsageKey(packageName, dateString),
            CloudJson.dailyAppUsageToJson(row).toString()
        )
    }

    private suspend fun enqueueSessionUpsert(e: AppLimitSessionEntity) {
        SyncTracker.enqueueUpsert(
            SyncTables.APP_LIMIT_SESSIONS,
            e.id,
            CloudJson.appLimitSessionToJson(e).toString()
        )
    }

    fun getTodayDateString(): String = dateFormat.format(Date())

    // --- APP LIMIT CONFIGURATIONS ---

    fun getAllLimitsFlow(): Flow<List<AppLimitEntity>> = appLimitDao.getAllLimitsFlow()

    fun getActiveLimitsFlow(): Flow<List<AppLimitEntity>> = appLimitDao.getActiveLimitsFlow()

    suspend fun getAllLimits(): List<AppLimitEntity> = appLimitDao.getAllLimits()

    suspend fun getActiveLimits(): List<AppLimitEntity> = appLimitDao.getActiveLimits()

    suspend fun getLimitByPackage(packageName: String): AppLimitEntity? =
        appLimitDao.getLimitByPackage(packageName)

    fun getLimitByPackageFlow(packageName: String): Flow<AppLimitEntity?> =
        appLimitDao.getLimitByPackageFlow(packageName)

    suspend fun saveLimit(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        isEnabled: Boolean = true,
        isStrictOverride: Boolean = false,
        showRemindersBeforeLimit: Boolean = true,
        emergencyUsesAllowed: Int = 1
    ) {
        val existing = appLimitDao.getLimitByPackage(packageName)
        val entity = AppLimitEntity(
            packageName = packageName,
            appName = appName,
            dailyLimitMinutes = dailyLimitMinutes,
            isEnabled = isEnabled,
            isStrictOverride = isStrictOverride,
            showRemindersBeforeLimit = showRemindersBeforeLimit,
            emergencyUsesAllowed = emergencyUsesAllowed,
            streakDays = existing?.streakDays ?: 0,
            lastStreakDate = existing?.lastStreakDate ?: "",
            updatedAt = System.currentTimeMillis()
        )
        appLimitDao.insertOrUpdate(entity)
        enqueueLimitUpsert(entity)
        try {
            com.example.feature.applimits.engine.AppLimitManager.instance.onLimitSaved(entity)
        } catch (_: Exception) {}
    }

    /**
     * Removes the limit *configuration* only.
     *
     * The per-day usage rows and the individual usage sessions deliberately survive: they record
     * what actually happened on this device and are the source for the usage/session analytics, so
     * cascading them away here would both destroy history and leave the cloud copy orphaned (a
     * `daily_app_usage` pull merges, and a `HISTORY` pull never deletes, so the rows would simply
     * reappear). Clearing usage for an app that is no longer limited is the same thing the user
     * gets by leaving the limit off — nothing reads these rows without a limit config.
     */
    suspend fun deleteLimit(packageName: String) {
        appLimitDao.deleteByPackage(packageName)
        enqueueLimitDelete(packageName)
        try {
            com.example.feature.applimits.engine.AppLimitManager.instance.onLimitDeleted(packageName)
        } catch (_: Exception) {}
    }

    suspend fun setLimitEnabled(packageName: String, isEnabled: Boolean) {
        appLimitDao.setEnabled(packageName, isEnabled)
        enqueueLimitState(packageName)
        try {
            if (isEnabled) {
                appLimitDao.getLimitByPackage(packageName)?.let {
                    com.example.feature.applimits.engine.AppLimitManager.instance.onLimitSaved(it)
                }
            } else {
                com.example.feature.applimits.engine.AppLimitManager.instance.onLimitDeleted(packageName)
            }
        } catch (_: Exception) {}
    }

    suspend fun updateDailyLimitMinutes(packageName: String, minutes: Int) {
        appLimitDao.updateDailyLimitMinutes(packageName, minutes)
        enqueueLimitState(packageName)
    }

    suspend fun updateStrictMode(packageName: String, isStrict: Boolean) {
        appLimitDao.updateStrictMode(packageName, isStrict)
        enqueueLimitState(packageName)
    }

    suspend fun updateRemindersSetting(packageName: String, showReminders: Boolean) {
        appLimitDao.updateRemindersSetting(packageName, showReminders)
        enqueueLimitState(packageName)
    }

    suspend fun recordDisciplineStreak(packageName: String) {
        val today = getTodayDateString()
        val limit = appLimitDao.getLimitByPackage(packageName) ?: return
        if (limit.lastStreakDate != today) {
            val newStreak = limit.streakDays + 1
            appLimitDao.updateStreak(packageName, newStreak, today)
            enqueueLimitState(packageName)
        }
    }

    suspend fun resetStreak(packageName: String) {
        appLimitDao.resetStreak(packageName)
        enqueueLimitState(packageName)
    }

    // --- DAILY USAGE TRACKING ---

    fun getUsageForDateFlow(dateString: String = getTodayDateString()): Flow<List<DailyAppUsageEntity>> =
        dailyAppUsageDao.getUsageForDateFlow(dateString)

    suspend fun getUsageForDate(dateString: String = getTodayDateString()): List<DailyAppUsageEntity> =
        dailyAppUsageDao.getUsageForDate(dateString)

    suspend fun getUsage(packageName: String, dateString: String = getTodayDateString()): DailyAppUsageEntity? =
        dailyAppUsageDao.getUsage(packageName, dateString)

    fun getUsageFlow(packageName: String, dateString: String = getTodayDateString()): Flow<DailyAppUsageEntity?> =
        dailyAppUsageDao.getUsageFlow(packageName, dateString)

    fun getTotalLimitedAppUsageForDateFlow(dateString: String = getTodayDateString()): Flow<Long> =
        dailyAppUsageDao.getTotalLimitedAppUsageForDateFlow(dateString).map { it ?: 0L }

    /**
     * Incrementally records actual elapsed foreground usage for an app limit.
     */
    suspend fun recordUsage(
        packageName: String,
        appName: String,
        dateString: String = getTodayDateString(),
        deltaMillis: Long,
        isEmergency: Boolean = false
    ) {
        if (deltaMillis <= 0L) return
        val existing = dailyAppUsageDao.getUsage(packageName, dateString)
        if (existing == null) {
            val newRecord = DailyAppUsageEntity(
                packageName = packageName,
                dateString = dateString,
                appName = appName,
                usedMillis = if (!isEmergency) deltaMillis else 0L,
                emergencyUsedMillis = if (isEmergency) deltaMillis else 0L,
                emergencyUsesCount = 0,
                isBypassedForToday = false,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            dailyAppUsageDao.insertOrUpdate(newRecord)
        } else {
            if (isEmergency) {
                dailyAppUsageDao.addEmergencyTime(packageName, dateString, deltaMillis)
            } else {
                dailyAppUsageDao.addUsedTime(packageName, dateString, deltaMillis)
            }
        }
        enqueueAppUsageUpsert(packageName, dateString)
    }

    /**
     * Synchronizes usage with actual system usage if the system usage is higher than DB.
     */
    suspend fun syncSystemUsage(
        packageName: String,
        appName: String,
        systemUsageMillis: Long,
        dateString: String = getTodayDateString()
    ) {
        if (systemUsageMillis <= 0L) return
        val existing = dailyAppUsageDao.getUsage(packageName, dateString)
        if (existing == null) {
            val newRecord = DailyAppUsageEntity(
                packageName = packageName,
                dateString = dateString,
                appName = appName,
                usedMillis = systemUsageMillis,
                emergencyUsedMillis = 0L,
                emergencyUsesCount = 0,
                isBypassedForToday = false,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            dailyAppUsageDao.insertOrUpdate(newRecord)
            enqueueAppUsageUpsert(packageName, dateString)
        } else if (existing.usedMillis < systemUsageMillis) {
            val updated = existing.copy(
                usedMillis = systemUsageMillis,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            dailyAppUsageDao.insertOrUpdate(updated)
            enqueueAppUsageUpsert(packageName, dateString)
        }
    }

    suspend fun setEmergencyUsesCount(packageName: String, dateString: String = getTodayDateString(), count: Int) {
        val existing = dailyAppUsageDao.getUsage(packageName, dateString)
        if (existing == null) {
            val limit = appLimitDao.getLimitByPackage(packageName)
            val newRecord = DailyAppUsageEntity(
                packageName = packageName,
                dateString = dateString,
                appName = limit?.appName ?: packageName,
                emergencyUsesCount = count,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            dailyAppUsageDao.insertOrUpdate(newRecord)
        } else {
            dailyAppUsageDao.setEmergencyUsesCount(packageName, dateString, count)
        }
    }

    suspend fun setBypassedForToday(packageName: String, dateString: String = getTodayDateString(), bypassed: Boolean) {
        val existing = dailyAppUsageDao.getUsage(packageName, dateString)
        if (existing == null) {
            val limit = appLimitDao.getLimitByPackage(packageName)
            val newRecord = DailyAppUsageEntity(
                packageName = packageName,
                dateString = dateString,
                appName = limit?.appName ?: packageName,
                isBypassedForToday = bypassed,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            dailyAppUsageDao.insertOrUpdate(newRecord)
        } else {
            dailyAppUsageDao.setBypassedForToday(packageName, dateString, bypassed)
        }
    }

    // --- INDIVIDUAL USAGE SESSIONS ---

    fun getSessionsForDateFlow(dateString: String = getTodayDateString()): Flow<List<AppLimitSessionEntity>> =
        appLimitSessionDao.getSessionsForDateFlow(dateString)

    suspend fun recordSession(session: AppLimitSessionEntity) {
        appLimitSessionDao.insertSession(session)
        enqueueSessionUpsert(session)
    }

    suspend fun getSessionCountToday(packageName: String, dateString: String = getTodayDateString()): Int =
        appLimitSessionDao.getSessionCountToday(packageName, dateString)
}
