package com.example.feature.applimits.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.util.DeviceUsageStatsHelper
import com.example.core.util.MediaPauseHelper
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.AppLimitSessionEntity
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import com.example.data.local.entity.DailyAppUsageEntity
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.AppLimitRepository
import com.example.data.repository.BlockedAttemptRepository
import com.example.feature.applimits.ui.AppLimitOverlayActivity
import com.example.feature.session.engine.FocusSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Live snapshot of an active temporary usage session inside a limited application.
 */
data class ActiveAppUsageSession(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val startedAt: Long = System.currentTimeMillis(),
    val selectedDurationMillis: Long,
    val elapsedMillis: Long = 0L,
    val isEmergency: Boolean = false,
    val dailyLimitMinutes: Int,
    val initialDailyUsedMillis: Long,
    val isStrict: Boolean = false,
    val isPaused: Boolean = false,
    val totalPausedMillis: Long = 0L,
    val lastPauseTimestamp: Long = 0L
) {
    val remainingSessionMillis: Long
        get() = (selectedDurationMillis - elapsedMillis).coerceAtLeast(0L)

    val currentTotalDailyUsedMillis: Long
        get() = initialDailyUsedMillis + (if (!isEmergency) elapsedMillis else 0L)

    val remainingDailyAllowanceMillis: Long
        get() {
            val totalAllowed = dailyLimitMinutes * 60 * 1000L
            return (totalAllowed - currentTotalDailyUsedMillis).coerceAtLeast(0L)
        }

    val isSessionExpired: Boolean
        get() = elapsedMillis >= selectedDurationMillis

    val isDailyLimitExhausted: Boolean
        get() = remainingDailyAllowanceMillis <= 0L || currentTotalDailyUsedMillis >= (dailyLimitMinutes * 60 * 1000L)
}

/**
 * Central engine managing App Limits, temporary usage sub-sessions, emergency usage,
 * and foreground usage tracking.
 */
class AppLimitManager private constructor(
    private val appContext: Context,
    private val appLimitRepository: AppLimitRepository,
    private val preferencesRepository: FocusPreferencesRepository,
    private val blockedAttemptRepository: BlockedAttemptRepository,
    private val sessionManager: FocusSessionManager = FocusSessionManager.instance
) {
    private val tag = "AppLimitManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Fast in-memory cache of enabled limits
    private val enabledLimits = ConcurrentHashMap<String, AppLimitEntity>()

    /**
     * When each package's limit-block episode was last recorded, keyed by package.
     *
     * The accessibility service re-checks the foreground package every 500 ms while its limit is
     * exhausted, so [checkAppLimitDecision] returns `REQUIRE_DAILY_LIMIT_BLOCK` over and over for a
     * single stay in the app. That stay is one episode, not one event per check, so the event is
     * recorded on entry into the state and suppressed for [limitEventReemitMs] — long enough that
     * sitting on the blocked screen does not flood the history, short enough that genuinely coming
     * back later is recorded as a fresh attempt.
     */
    private val limitBlockRecordedAt = ConcurrentHashMap<String, Long>()
    private val limitEventReemitMs = 5 * 60 * 1000L

    private val _activeSession = MutableStateFlow<ActiveAppUsageSession?>(null)
    val activeSession: StateFlow<ActiveAppUsageSession?> = _activeSession.asStateFlow()

    private var currentForegroundPackage: String? = null
    private var tickerJob: Job? = null
    private var lastForegroundTimestamp: Long = 0L

    /**
     * Clears the overlay debounce for a package so the next foreground change re-triggers immediately.
     * Called when a session expires to ensure the blocker re-appears without waiting.
     */
    fun clearOverlayDebounce(packageName: String) {
        lastOverlayLaunchPerPackage.remove(packageName)
    }

    init {
        scope.launch {
            try {
                val active = appLimitRepository.getActiveLimits()
                active.forEach { enabledLimits[it.packageName] = it }
            } catch (e: Exception) {
                Log.e(tag, "Initial load active limits error: ${e.message}")
            }
            appLimitRepository.getActiveLimitsFlow().collectLatest { limits ->
                enabledLimits.clear()
                limits.forEach { enabledLimits[it.packageName] = it }
            }
        }
    }

    /**
     * Checks if the package has an active configured App Limit.
     */
    fun isPackageLimited(packageName: String): Boolean {
        if (enabledLimits.containsKey(packageName)) return true
        return try {
            val fromDb = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                appLimitRepository.getLimitByPackage(packageName)
            }
            if (fromDb != null && fromDb.isEnabled) {
                enabledLimits[packageName] = fromDb
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun onLimitSaved(limit: AppLimitEntity) {
        if (limit.isEnabled) {
            enabledLimits[limit.packageName] = limit
        } else {
            enabledLimits.remove(limit.packageName)
        }
    }

    fun onLimitDeleted(packageName: String) {
        enabledLimits.remove(packageName)
    }

    fun getLimit(packageName: String): AppLimitEntity? {
        return enabledLimits[packageName] ?: try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                appLimitRepository.getLimitByPackage(packageName)?.also {
                    if (it.isEnabled) enabledLimits[it.packageName] = it
                }
            }
        } catch (_: Exception) { null }
    }

    /**
     * Returns true if there is an active temporary session currently running for this package.
     */
    fun isSessionActiveFor(packageName: String): Boolean {
        val current = _activeSession.value ?: return false
        return current.packageName == packageName && !current.isSessionExpired
    }

    /**
     * Checks whether the given package is allowed or requires App Limit intervention.
     * Evaluates:
     * 1. If Focus Session is RUNNING and app is blocked by session -> Focus Session has absolute priority (return false).
     * 2. If app is not limited -> Allowed.
     * 3. If app is bypassed for today -> Allowed.
     * 4. If active temporary session is running -> Allowed.
     * 5. Otherwise -> Requires App Limit intervention (Prompt selection or Show blocker).
     */
    suspend fun checkAppLimitDecision(packageName: String): AppLimitDecision {
        // Priority 1: Focus Session has absolute priority
        val focusSession = sessionManager.activeSession.value
        val isFocusRunning = sessionManager.sessionState.value == com.example.feature.session.domain.SessionState.RUNNING
        if (isFocusRunning && focusSession != null) {
            // Focus session takes full priority; App Limit engine yields to Focus Block Engine
            return AppLimitDecision.ALLOW_FOCUS_SESSION_RULES
        }

        val limit = enabledLimits[packageName] ?: run {
            val fromDb = appLimitRepository.getLimitByPackage(packageName)
            if (fromDb != null && fromDb.isEnabled) {
                enabledLimits[packageName] = fromDb
                fromDb
            } else {
                null
            }
        } ?: return AppLimitDecision.ALLOW_NOT_LIMITED

        if (!limit.isEnabled) return AppLimitDecision.ALLOW_NOT_LIMITED

        val todayDate = appLimitRepository.getTodayDateString()
        val usage = appLimitRepository.getUsage(packageName, todayDate)

        if (usage?.isBypassedForToday == true) {
            return AppLimitDecision.ALLOW_BYPASSED_TODAY
        }

        val active = _activeSession.value
        if (active != null && active.packageName == packageName && !active.isSessionExpired) {
            return AppLimitDecision.ALLOW_ACTIVE_SESSION
        }

        // Check remaining daily allowance from system usage stats and database
        val dailyLimitMinutes = limit.dailyLimitMinutes
        val dailyLimitMillis = dailyLimitMinutes * 60 * 1000L
        val systemUsage = DeviceUsageStatsHelper.getTodayAppUsageMillis(appContext, packageName)
        val dbUsage = usage?.usedMillis ?: 0L

        // Use the higher of system vs DB as the source of truth.
        // When systemUsage is 0 (missing permission or lagging), fall back to DB.
        val accumulatedUsed = when {
            systemUsage > 0 && systemUsage >= dbUsage -> systemUsage
            dbUsage > 0 -> dbUsage  // System is 0 or lagging — trust DB
            else -> systemUsage  // Both zero
        }

        val usedMinutes = kotlin.math.round(accumulatedUsed / 60000.0).toInt().coerceAtLeast(0)
        val remainingDailyMinutes = (dailyLimitMinutes - usedMinutes).coerceAtLeast(0)
        val remainingDailyMillis = (dailyLimitMillis - accumulatedUsed).coerceAtLeast(0L)

        val isStrict = limit.isStrictOverride
        val isExhausted = remainingDailyMinutes <= 0 || remainingDailyMillis <= 0L || accumulatedUsed >= dailyLimitMillis

        return if (!isExhausted) {
            AppLimitDecision.REQUIRE_USAGE_SELECTION(
                packageName = packageName,
                appName = limit.appName,
                dailyLimitMinutes = dailyLimitMinutes,
                remainingDailyMillis = remainingDailyMillis,
                usedDailyMillis = accumulatedUsed,
                streakDays = limit.streakDays,
                isStrict = isStrict,
                emergencyUsesAllowed = limit.emergencyUsesAllowed
            )
        } else {
            recordLimitReached(packageName, limit.appName, isStrict)
            AppLimitDecision.REQUIRE_DAILY_LIMIT_BLOCK(
                packageName = packageName,
                appName = limit.appName,
                dailyLimitMinutes = dailyLimitMinutes,
                emergencyUsesCount = usage?.emergencyUsesCount ?: 0,
                emergencyUsesAllowed = limit.emergencyUsesAllowed,
                isStrict = isStrict,
                streakDays = limit.streakDays
            )
        }
    }

    private val lastOverlayLaunchPerPackage = ConcurrentHashMap<String, Long>()

    /**
     * Records the moment a limited app's daily allowance ran out and the blocker took over.
     *
     * A strict limit records `STRICT_MODE_ENFORCED` instead of `APP_LIMIT_REACHED` rather than both:
     * they are the same episode, and emitting two rows would double-count "times I hit my limit".
     * Strict mode is the *form* of that hit, so an analytics query wanting every limit hit filters
     * on both event types. Deduplication is described on [limitBlockRecordedAt].
     */
    private suspend fun recordLimitReached(packageName: String, appName: String, isStrict: Boolean) {
        val now = System.currentTimeMillis()
        val lastRecorded = limitBlockRecordedAt[packageName] ?: 0L
        if (now - lastRecorded < limitEventReemitMs) return
        limitBlockRecordedAt[packageName] = now
        try {
            blockedAttemptRepository.recordAttempt(
                packageName = packageName,
                appName = appName,
                eventType = if (isStrict) BlockedEventType.STRICT_MODE_ENFORCED
                            else BlockedEventType.APP_LIMIT_REACHED,
                source = BlockedEventSource.APP_LIMIT_ENGINE,
                ruleRef = "DAILY_LIMIT",
                timestamp = now
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to record app limit event for $packageName: ${e.message}")
        }
    }

    private fun isIgnoredOverlayOrImePackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        val lower = pkg.lowercase()
        return lower.startsWith("com.example") ||
                lower.startsWith("com.aistudio.focusshield") ||
                lower == "android" ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("honeyboard") ||
                lower.contains("swiftkey") ||
                lower.contains("systemui") ||
                lower.contains("permissioncontroller")
    }

    /**
     * Called by Accessibility Service whenever the foreground window package changes.
     */
    fun onForegroundPackageChanged(packageName: String) {
        if (isIgnoredOverlayOrImePackage(packageName)) {
            return
        }

        val previous = currentForegroundPackage
        currentForegroundPackage = packageName
        val now = System.currentTimeMillis()

        if (previous == packageName) return

        val currentSession = _activeSession.value ?: return

        // 1. If user leaves our active limited app for another app:
        if (previous == currentSession.packageName && packageName != currentSession.packageName) {
            if (!currentSession.isPaused) {
                Log.d(tag, "User switched out of limited app: ${currentSession.appName} to $packageName. Pausing timer.")
                _activeSession.value = currentSession.copy(
                    isPaused = true,
                    lastPauseTimestamp = now
                )
            }
        }

        // 2. If user returns to our active limited app:
        if (packageName == currentSession.packageName) {
            if (currentSession.isPaused && currentSession.lastPauseTimestamp > 0L) {
                val addedPause = (now - currentSession.lastPauseTimestamp).coerceAtLeast(0L)
                Log.d(tag, "User returned to active limited app: ${currentSession.appName}. Resuming timer (paused for ${addedPause}ms).")
                _activeSession.value = currentSession.copy(
                    isPaused = false,
                    totalPausedMillis = currentSession.totalPausedMillis + addedPause,
                    lastPauseTimestamp = 0L
                )
            }
            startTicker()
        }
    }

    /**
     * Starts a new temporary usage session chosen by the user.
     */
    fun startTemporarySession(
        packageName: String,
        appName: String,
        durationMinutes: Int,
        isEmergency: Boolean = false
    ) {
        val durationMillis = durationMinutes * 60 * 1000L
        val limit = enabledLimits[packageName]
        val dailyLimitMinutes = limit?.dailyLimitMinutes ?: 60

        // 1. Synchronously set active session in memory immediately so no window-change race condition occurs
        val systemUsage = DeviceUsageStatsHelper.getTodayAppUsageMillis(appContext, packageName)
        val initialSession = ActiveAppUsageSession(
            packageName = packageName,
            appName = appName,
            startedAt = System.currentTimeMillis(),
            selectedDurationMillis = durationMillis,
            elapsedMillis = 0L,
            isEmergency = isEmergency,
            dailyLimitMinutes = dailyLimitMinutes,
            initialDailyUsedMillis = systemUsage,
            isStrict = limit?.isStrictOverride ?: false,
            isPaused = false,
            totalPausedMillis = 0L,
            lastPauseTimestamp = 0L
        )
        _activeSession.value = initialSession
        currentForegroundPackage = packageName
        lastForegroundTimestamp = System.currentTimeMillis()
        startTicker()

        // 2. Asynchronously sync precise daily usage remaining from database and system stats
        scope.launch {
            val todayDate = appLimitRepository.getTodayDateString()
            val usage = appLimitRepository.getUsage(packageName, todayDate)
            val dbUsedMillis = usage?.usedMillis ?: 0L
            // Trust the OS-level usage as source of truth
            val usedMillis = systemUsage

            if (systemUsage != dbUsedMillis) {
                appLimitRepository.syncSystemUsage(packageName, appName, systemUsage, todayDate)
            }

            val dailyRemaining = (dailyLimitMinutes * 60 * 1000L - usedMillis).coerceAtLeast(0L)
            val actualDuration = if (isEmergency) durationMillis else minOf(durationMillis, dailyRemaining)

            val current = _activeSession.value
            if (current != null && current.packageName == packageName) {
                _activeSession.value = current.copy(
                    selectedDurationMillis = actualDuration,
                    initialDailyUsedMillis = usedMillis
                )
            }
            Log.i(tag, "Started temporary usage session for $appName: $durationMinutes min ($actualDuration ms, isEmergency=$isEmergency)")
        }
    }

    /**
     * Starts a 5-minute Emergency Session for an app whose daily limit is exhausted.
     */
    fun startEmergencySession(packageName: String, appName: String) {
        scope.launch {
            val todayDate = appLimitRepository.getTodayDateString()
            val usage = appLimitRepository.getUsage(packageName, todayDate)
            val currentCount = usage?.emergencyUsesCount ?: 0
            val limit = enabledLimits[packageName] ?: appLimitRepository.getLimitByPackage(packageName)
            val allowedCount = limit?.emergencyUsesAllowed ?: 1
            if (currentCount >= allowedCount) {
                Log.w(tag, "Emergency uses exhausted for $packageName (count: $currentCount, allowed: $allowedCount)")
                return@launch
            }

            // Increment emergency count
            appLimitRepository.setEmergencyUsesCount(packageName, todayDate, currentCount + 1)

            // Start 5-minute session
            startTemporarySession(
                packageName = packageName,
                appName = appName,
                durationMinutes = 5,
                isEmergency = true
            )
        }
    }

    /**
     * Bypasses the limit for the rest of today.
     */
    fun leaveBlockForToday(packageName: String) {
        scope.launch {
            val todayDate = appLimitRepository.getTodayDateString()
            appLimitRepository.setBypassedForToday(packageName, todayDate, true)
            _activeSession.value = null
            stopTicker()
            Log.i(tag, "App $packageName bypassed for the rest of today")
        }
    }

    /**
     * Permanently deletes the app limit configuration.
     */
    fun removeLimitPermanently(packageName: String) {
        scope.launch {
            appLimitRepository.deleteLimit(packageName)
            enabledLimits.remove(packageName)
            _activeSession.value = null
            stopTicker()
            Log.i(tag, "App limit for $packageName permanently removed")
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                delay(500L)
                val session = _activeSession.value ?: break
                val now = System.currentTimeMillis()

                val currentElapsed = if (session.isPaused) {
                    session.elapsedMillis
                } else {
                    (now - session.startedAt - session.totalPausedMillis).coerceAtLeast(0L)
                }

                val updatedSession = session.copy(elapsedMillis = currentElapsed)
                _activeSession.value = updatedSession

                // Reminder check: notify user when time is almost up if enabled
                val limit = enabledLimits[session.packageName]
                if (limit != null && limit.showRemindersBeforeLimit) {
                    AppLimitStrictModeEngine.instance.checkAndSendReminderIfNeeded(
                        packageName = session.packageName,
                        appName = session.appName,
                        remainingMillis = updatedSession.remainingSessionMillis,
                        showRemindersSetting = limit.showRemindersBeforeLimit
                    )
                }

                // Check for session completion or daily limit exhaustion
                if (updatedSession.isSessionExpired || (!updatedSession.isEmergency && updatedSession.isDailyLimitExhausted)) {
                    Log.i(tag, "Temporary session for ${session.appName} completed! Elapsed: ${currentElapsed / 1000}s (Target: ${session.selectedDurationMillis / 1000}s)")
                    handleSessionExpired(updatedSession)
                    break
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun handleSessionExpired(session: ActiveAppUsageSession) {
        scope.launch {
            val actualUsed = session.elapsedMillis
            val endReason = if (session.isEmergency) "EMERGENCY_EXPIRED" else if (session.isDailyLimitExhausted) "DAILY_LIMIT_REACHED" else "TIMER_EXPIRED"
            saveSessionRecord(session, actualUsed, endReason)

            _activeSession.value = null
            stopTicker()

            // Clear debounce timestamp so the next foreground change re-checks the limit immediately
            clearOverlayDebounce(session.packageName)

            // 1. Pause media immediately (overlay will be displayed directly over the app without minimizing to home first)
            MediaPauseHelper.pauseMedia(appContext)

            // 2. Query updated total daily usage from system and database
            val limit = appLimitRepository.getLimitByPackage(session.packageName)
            val todayDate = appLimitRepository.getTodayDateString()
            val usage = appLimitRepository.getUsage(session.packageName, todayDate)
            val systemUsage = DeviceUsageStatsHelper.getTodayAppUsageMillis(appContext, session.packageName)
            val dbUsage = usage?.usedMillis ?: 0L
            // Use the higher of system vs DB as source of truth
            val totalDailyUsedMillis = when {
                systemUsage > 0 && systemUsage >= dbUsage -> systemUsage
                dbUsage > 0 -> dbUsage
                else -> systemUsage
            }

            val dailyLimitMinutes = limit?.dailyLimitMinutes ?: 60
            val dailyLimitMillis = dailyLimitMinutes * 60 * 1000L

            val totalDailyUsedMinutes = kotlin.math.round(totalDailyUsedMillis / 60000.0).toInt().coerceAtLeast(0)
            val remainingDailyMinutes = (dailyLimitMinutes - totalDailyUsedMinutes).coerceAtLeast(0)
            val isDailyExhausted = remainingDailyMinutes <= 0 || totalDailyUsedMillis >= dailyLimitMillis

            // 3. Trigger overlay popup with forceLaunch
            launchOverlay(
                packageName = session.packageName,
                appName = session.appName,
                mode = if (!isDailyExhausted) AppLimitOverlayMode.SESSION_COMPLETE else AppLimitOverlayMode.DAILY_LIMIT_REACHED,
                selectedMinutes = (session.selectedDurationMillis / 60000L).toInt(),
                usedMinutes = totalDailyUsedMinutes,
                remainingDailyMinutes = remainingDailyMinutes,
                dailyLimitMinutes = dailyLimitMinutes,
                emergencyUsesCount = usage?.emergencyUsesCount ?: 0,
                emergencyUsesAllowed = limit?.emergencyUsesAllowed ?: 1,
                isStrict = session.isStrict,
                streakDays = limit?.streakDays ?: 0,
                forceLaunch = true
            )
        }
    }

    private fun finalizeAndStopSession(reason: String) {
        val session = _activeSession.value ?: return
        val actualUsed = session.elapsedMillis
        _activeSession.value = null
        stopTicker()

        if (actualUsed > 0L) {
            scope.launch {
                saveSessionRecord(session, actualUsed, reason)
            }
        }
    }

    private suspend fun saveSessionRecord(session: ActiveAppUsageSession, actualUsedMillis: Long, reason: String) {
        val todayDate = appLimitRepository.getTodayDateString()

        // Record session metadata for analytics (NOT actual usage).
        // Actual usage comes from UsageStatsManager — never from the FocusShield timer.
        val sessionEntity = AppLimitSessionEntity(
            id = session.id,
            packageName = session.packageName,
            appName = session.appName,
            dateString = todayDate,
            startedAt = session.startedAt,
            endedAt = System.currentTimeMillis(),
            selectedDurationMillis = session.selectedDurationMillis,
            actualUsedMillis = actualUsedMillis,
            isEmergency = session.isEmergency,
            endReason = reason
        )
        appLimitRepository.recordSession(sessionEntity)

        // Sync the database with UsageStatsManager so historical records are accurate.
        // This ensures usedMillis reflects actual foreground time, not FocusShield timer increments.
        val systemUsage = DeviceUsageStatsHelper.getTodayAppUsageMillis(appContext, session.packageName)
        appLimitRepository.syncSystemUsage(session.packageName, session.appName, systemUsage, todayDate)
    }

    fun launchOverlay(
        packageName: String,
        appName: String,
        mode: AppLimitOverlayMode,
        selectedMinutes: Int = 0,
        usedMinutes: Int = 0,
        remainingDailyMinutes: Int = 0,
        dailyLimitMinutes: Int = 60,
        emergencyUsesCount: Int = 0,
        emergencyUsesAllowed: Int = 1,
        isStrict: Boolean = false,
        streakDays: Int = 0,
        forceLaunch: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val lastLaunch = lastOverlayLaunchPerPackage[packageName] ?: 0L
        if (!forceLaunch && now - lastLaunch < 1000L) {
            Log.d(tag, "Suppressing duplicate overlay launch within debounce window for $packageName")
            return
        }
        lastOverlayLaunchPerPackage[packageName] = now

        MediaPauseHelper.pauseMedia(appContext)
        val intent = Intent(appContext, AppLimitOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppLimitOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AppLimitOverlayActivity.EXTRA_APP_NAME, appName)
            putExtra(AppLimitOverlayActivity.EXTRA_OVERLAY_MODE, mode.name)
            putExtra(AppLimitOverlayActivity.EXTRA_SELECTED_MINUTES, selectedMinutes)
            putExtra(AppLimitOverlayActivity.EXTRA_USED_MINUTES, usedMinutes)
            putExtra(AppLimitOverlayActivity.EXTRA_REMAINING_DAILY_MINUTES, remainingDailyMinutes)
            putExtra(AppLimitOverlayActivity.EXTRA_DAILY_LIMIT_MINUTES, dailyLimitMinutes)
            putExtra(AppLimitOverlayActivity.EXTRA_EMERGENCY_COUNT, emergencyUsesCount)
            putExtra(AppLimitOverlayActivity.EXTRA_EMERGENCY_ALLOWED, emergencyUsesAllowed)
            putExtra(AppLimitOverlayActivity.EXTRA_IS_STRICT, isStrict)
            putExtra(AppLimitOverlayActivity.EXTRA_STREAK_DAYS, streakDays)
        }

        // Try multiple strategies to launch the overlay, ensuring it appears even after session expiry
        var launched = false
        val service = com.example.core.accessibility.FocusAccessibilityService.instance
        try {
            if (service != null) {
                service.startActivity(intent)
                launched = true
                Log.i(tag, "Launched AppLimitOverlayActivity via FocusAccessibilityService for $packageName (mode=$mode)")
            }
        } catch (e: Exception) {
            Log.w(tag, "FocusAccessibilityService failed to start overlay: ${e.message}")
        }

        if (!launched) {
            try {
                appContext.startActivity(intent)
                launched = true
                Log.i(tag, "Launched AppLimitOverlayActivity via appContext for $packageName (mode=$mode)")
            } catch (e: Exception) {
                Log.e(tag, "appContext failed to start overlay: ${e.message}")
            }
        }

        if (!launched) {
            // Last resort: try with NEW_TASK flag only
            try {
                val fallbackIntent = Intent(appContext, AppLimitOverlayActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(AppLimitOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AppLimitOverlayActivity.EXTRA_APP_NAME, appName)
                    putExtra(AppLimitOverlayActivity.EXTRA_OVERLAY_MODE, mode.name)
                    putExtra(AppLimitOverlayActivity.EXTRA_SELECTED_MINUTES, selectedMinutes)
                    putExtra(AppLimitOverlayActivity.EXTRA_USED_MINUTES, usedMinutes)
                    putExtra(AppLimitOverlayActivity.EXTRA_REMAINING_DAILY_MINUTES, remainingDailyMinutes)
                    putExtra(AppLimitOverlayActivity.EXTRA_DAILY_LIMIT_MINUTES, dailyLimitMinutes)
                    putExtra(AppLimitOverlayActivity.EXTRA_EMERGENCY_COUNT, emergencyUsesCount)
                    putExtra(AppLimitOverlayActivity.EXTRA_EMERGENCY_ALLOWED, emergencyUsesAllowed)
                    putExtra(AppLimitOverlayActivity.EXTRA_IS_STRICT, isStrict)
                    putExtra(AppLimitOverlayActivity.EXTRA_STREAK_DAYS, streakDays)
                }
                appContext.startActivity(fallbackIntent)
                Log.i(tag, "Launched AppLimitOverlayActivity via fallback intent for $packageName (mode=$mode)")
            } catch (e: Exception) {
                Log.e(tag, "All overlay launch strategies failed for $packageName: ${e.message}", e)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppLimitManager? = null

        fun initialize(
            appContext: Context,
            appLimitRepository: AppLimitRepository,
            preferencesRepository: FocusPreferencesRepository,
            blockedAttemptRepository: BlockedAttemptRepository
        ): AppLimitManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AppLimitManager(
                    appContext = appContext,
                    appLimitRepository = appLimitRepository,
                    preferencesRepository = preferencesRepository,
                    blockedAttemptRepository = blockedAttemptRepository
                )
                INSTANCE = instance
                instance
            }
        }

        val instance: AppLimitManager
            get() = INSTANCE ?: throw IllegalStateException("AppLimitManager must be initialized in FocusShieldApp.onCreate")
    }
}

/**
 * Outcome of evaluating an app against App Limit policy.
 */
sealed interface AppLimitDecision {
    data object ALLOW_FOCUS_SESSION_RULES : AppLimitDecision
    data object ALLOW_NOT_LIMITED : AppLimitDecision
    data object ALLOW_BYPASSED_TODAY : AppLimitDecision
    data object ALLOW_ACTIVE_SESSION : AppLimitDecision

    data class REQUIRE_USAGE_SELECTION(
        val packageName: String,
        val appName: String,
        val dailyLimitMinutes: Int,
        val remainingDailyMillis: Long,
        val usedDailyMillis: Long,
        val streakDays: Int = 0,
        val isStrict: Boolean = false,
        val emergencyUsesAllowed: Int = 1
    ) : AppLimitDecision

    data class REQUIRE_DAILY_LIMIT_BLOCK(
        val packageName: String,
        val appName: String,
        val dailyLimitMinutes: Int,
        val emergencyUsesCount: Int,
        val emergencyUsesAllowed: Int = 1,
        val isStrict: Boolean,
        val streakDays: Int = 0
    ) : AppLimitDecision
}

enum class AppLimitOverlayMode {
    AWAITING_SELECTION,
    SESSION_COMPLETE,
    DAILY_LIMIT_REACHED
}
