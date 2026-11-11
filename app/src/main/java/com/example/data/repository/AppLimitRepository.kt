package com.example.data.repository

import com.example.data.local.dao.AppLimitDao
import com.example.data.local.dao.AppLimitSessionDao
import com.example.data.local.dao.DailyAppUsageDao
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.AppLimitSessionEntity
import com.example.data.local.entity.DailyAppUsageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Repository coordinating app limit configs, daily accumulated usage, and temporary usage sessions.
 */
class AppLimitRepository(
    private val appLimitDao: AppLimitDao,
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val appLimitSessionDao: AppLimitSessionDao
) {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val mutationMutex = Mutex()

    private suspend fun mutate(block: suspend () -> AppLimitWriteResult): AppLimitWriteResult =
        mutationMutex.withLock {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                AppLimitWriteResult.Failed(exception)
            }
        }

    // --- App-limit data is device-local in its entirety — the limit configuration, the daily usage
    // counters and the temporary usage sessions are all about what *this phone* should enforce, so
    // nothing here enqueues a sync op. (The study-session block list is a separate feature with its
    // own repository; it is the one that follows the account.)

    fun getTodayDateString(): String = LocalDate.now().format(dateFormat)

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
        emergencyUsesAllowed: Int = 1,
        requireExisting: Boolean = false
    ): AppLimitWriteResult = mutate {
        if (packageName.isBlank() || appName.isBlank() || dailyLimitMinutes !in 1..1440 || emergencyUsesAllowed !in 0..10) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.INVALID)
        }
        val existing = appLimitDao.getLimitByPackage(packageName)
        if (requireExisting && existing == null) return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.MISSING)
        if (existing?.isStrictOverride == true) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.STRICT_LOCKED)
        }
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
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        AppLimitWriteCompletion.run(
            persist = { appLimitDao.insertOrUpdate(entity) },
            { writeBypass(packageName, getTodayDateString(), false) },
            { com.example.feature.applimits.engine.AppLimitManager.instance.onLimitSaved(entity) }
        )
    }

    /**
     * Removes the limit *configuration* only.
     *
     * The per-day usage rows and the individual usage sessions deliberately survive: they record
     * what actually happened on this device, and removing a limit is not a statement that the time
     * already spent did not happen. Clearing usage for an app that is no longer limited is the same
     * thing the user gets by leaving the limit off — nothing reads these rows without a limit config.
     *
     * In Strict Mode (`isStrictOverride == true`), deletion is strictly forbidden from any entry point.
     */
    suspend fun deleteLimitResult(packageName: String): AppLimitWriteResult = mutate {
        val existing = appLimitDao.getLimitByPackage(packageName)
            ?: return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.MISSING)
        if (existing.isStrictOverride) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.STRICT_LOCKED)
        }
        AppLimitWriteCompletion.run(
            { appLimitDao.deleteByPackage(packageName) },
            { com.example.feature.applimits.engine.AppLimitManager.instance.onLimitDeleted(packageName) }
        )
    }

    suspend fun deleteLimit(packageName: String): Boolean = deleteLimitResult(packageName) is AppLimitWriteResult.Applied

    suspend fun setLimitEnabledResult(packageName: String, isEnabled: Boolean): AppLimitWriteResult = mutate {
        val existing = appLimitDao.getLimitByPackage(packageName)
            ?: return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.MISSING)
        if (!isEnabled && existing.isStrictOverride) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.STRICT_LOCKED)
        }
        val updated = existing.copy(isEnabled = isEnabled, updatedAt = System.currentTimeMillis())
        AppLimitWriteCompletion.run({ appLimitDao.update(updated) }, {
            if (isEnabled) writeBypass(packageName, getTodayDateString(), false)
        }, {
            if (isEnabled) {
                com.example.feature.applimits.engine.AppLimitManager.instance.onLimitSaved(updated)
            } else {
                // Disabling is not a delete: keep the configuration and its usage history, but
                // retire the live session/timer/overlay state that belonged to the disabled limit.
                com.example.feature.applimits.engine.AppLimitManager.instance.onLimitDisabled(packageName)
            }
        })
    }

    suspend fun setLimitEnabled(packageName: String, isEnabled: Boolean): Boolean =
        setLimitEnabledResult(packageName, isEnabled) is AppLimitWriteResult.Applied

    suspend fun updateDailyLimitMinutes(packageName: String, minutes: Int): AppLimitWriteResult = mutate {
        if (minutes !in 1..1440) return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.INVALID)
        val existing = appLimitDao.getLimitByPackage(packageName)
            ?: return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.MISSING)
        if (existing.isStrictOverride) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.STRICT_LOCKED)
        }
        val updated = existing.copy(dailyLimitMinutes = minutes, updatedAt = System.currentTimeMillis())
        AppLimitWriteCompletion.run({ appLimitDao.update(updated) },
            { com.example.feature.applimits.engine.AppLimitManager.instance.onLimitSaved(updated) })
    }

    suspend fun updateStrictMode(packageName: String, isStrict: Boolean): AppLimitWriteResult = mutate {
        val existing = appLimitDao.getLimitByPackage(packageName)
            ?: return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.MISSING)
        if (existing.isStrictOverride && !isStrict) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.STRICT_LOCKED)
        }
        val updated = existing.copy(isStrictOverride = isStrict, updatedAt = System.currentTimeMillis())
        AppLimitWriteCompletion.run({ appLimitDao.update(updated) },
            { com.example.feature.applimits.engine.AppLimitManager.instance.onLimitSaved(updated) })
    }

    suspend fun updateRemindersSetting(packageName: String, showReminders: Boolean): AppLimitWriteResult = mutate {
        val existing = appLimitDao.getLimitByPackage(packageName)
            ?: return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.MISSING)
        val updated = existing.copy(showRemindersBeforeLimit = showReminders, updatedAt = System.currentTimeMillis())
        AppLimitWriteCompletion.run({ appLimitDao.update(updated) },
            { com.example.feature.applimits.engine.AppLimitManager.instance.onLimitSaved(updated) })
    }

    suspend fun recordDisciplineStreak(packageName: String) {
        val today = getTodayDateString()
        val limit = appLimitDao.getLimitByPackage(packageName) ?: return
        if (limit.lastStreakDate != today) {
            val newStreak = limit.streakDays + 1
            appLimitDao.updateStreak(packageName, newStreak, today)
        }
    }

    suspend fun resetStreak(packageName: String) {
        appLimitDao.resetStreak(packageName)
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
     * Resets usedMillis to 0 for a specific app and date. Used by the "Reset Today's Usage" button.
     */
    suspend fun resetUsage(packageName: String, dateString: String = getTodayDateString()): AppLimitWriteResult = mutate {
        val limit = appLimitDao.getLimitByPackage(packageName)
        if (limit?.isStrictOverride == true) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.STRICT_LOCKED)
        }
        val existing = dailyAppUsageDao.getUsage(packageName, dateString)
        AppLimitWriteCompletion.run({
            if (existing != null) dailyAppUsageDao.insertOrUpdate(existing.copy(
                usedMillis = 0L,
                emergencyUsedMillis = 0L,
                emergencyUsesCount = 0,
                isBypassedForToday = false,
                lastActiveTimestamp = System.currentTimeMillis()
            ))
        }, {
            com.example.feature.applimits.engine.AppLimitManager.instance.onUsageReset(packageName)
        })
    }

    /**
     * Pushes UsageStats' view of today's usage into the daily row — forward only.
     *
     * UsageStats can transiently report *less* than it did a moment earlier (a partial query
     * after a permission flap, the manager's own lag windows, the value falling back to a
     * narrower interval). The old behaviour deliberately capped inflated rows down to whatever
     * the system just said, so those dips rolled the DB backward: the difference came back as
     * free allowance, and anything already decided on the higher number (dashboard totals, the
     * engine's permission-denied fallback) un-blocked with it (issue #49, C3). A day's usage
     * only grows — a lower sample is stale, not corrected. [resetUsage] is the deliberate way to
     * get back to zero and writes directly.
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

        } else if (systemUsageMillis > existing.usedMillis) {
            // Only ever forward: a sample at or below the stored value changes nothing.
            val updated = existing.copy(
                usedMillis = systemUsageMillis,
                lastActiveTimestamp = System.currentTimeMillis()
            )
            dailyAppUsageDao.insertOrUpdate(updated)

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

    suspend fun bypassLimitForToday(packageName: String): AppLimitWriteResult = mutate {
        val limit = appLimitDao.getLimitByPackage(packageName)
            ?: return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.MISSING)
        if (limit.isStrictOverride) return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.STRICT_LOCKED)
        AppLimitWriteCompletion.run({ writeBypass(packageName, getTodayDateString(), true) },
            { com.example.feature.applimits.engine.AppLimitManager.instance.onUsageReset(packageName) })
    }

    /**
     * Writes (or clears) today's bypass. The result is reported instead of a bare Boolean so a
     * strict lock, a removed limit and a failed write cannot be mistaken for "allowed".
     */
    suspend fun setBypassForTodayResult(
        packageName: String,
        dateString: String = getTodayDateString(),
        bypassed: Boolean
    ): AppLimitWriteResult = mutate {
        val limit = appLimitDao.getLimitByPackage(packageName)
        if (bypassed && limit == null) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.MISSING)
        }
        if (bypassed && limit?.isStrictOverride == true) {
            return@mutate AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.STRICT_LOCKED)
        }
        AppLimitWriteCompletion.run({ writeBypass(packageName, dateString, bypassed) })
    }

    suspend fun setBypassedForToday(
        packageName: String,
        dateString: String = getTodayDateString(),
        bypassed: Boolean
    ): Boolean = setBypassForTodayResult(packageName, dateString, bypassed) is AppLimitWriteResult.Applied

    private suspend fun writeBypass(packageName: String, dateString: String, bypassed: Boolean) {
        val limit = appLimitDao.getLimitByPackage(packageName)
        val existing = dailyAppUsageDao.getUsage(packageName, dateString)
        if (existing == null) {
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
    }

    suspend fun getSessionCountToday(packageName: String, dateString: String = getTodayDateString()): Int =
        appLimitSessionDao.getSessionCountToday(packageName, dateString)
}
