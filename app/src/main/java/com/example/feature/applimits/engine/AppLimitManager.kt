package com.example.feature.applimits.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.example.core.util.DeviceUsageStatsHelper
import com.example.core.util.UsageResult
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
import com.example.feature.applimits.ui.AppLimitFloatingOverlayManager
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * Disk snapshot of the live temporary session, so the deadline survives a process kill.
     * See [AppLimitSessionStore] and [restoreSessionFromDisk].
     */
    private val sessionStore = AppLimitSessionStore(appContext)

    /**
     * Fast in-memory pre-filter of enabled limits — never the source of truth for a decision:
     * [checkAppLimitDecision] re-reads the row fresh, because a limit disabled in the UI kept
     * deciding from this cache until the Room flow round-tripped, so protection the user had
     * just turned off kept blocking (issue #49, C4).
     *
     * An immutable map behind a `@Volatile` reference: the refresh builds a new map and swaps
     * the reference, which readers see either fully old or fully new — the previous
     * `clear()` + `putAll()` on a shared ConcurrentHashMap let a concurrent gate read an empty
     * cache mid-refresh and conclude "not limited".
     */
    @Volatile
    private var enabledLimits: Map<String, AppLimitEntity> = emptyMap()

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

    /**
     * Packages whose blocker the user answered by leaving the app, and which must therefore not be
     * prompted again until they genuinely re-enter it (issue #53). See [AppLimitReentryGate] for
     * why this is a re-entry gate and not a timer.
     */
    private val reentryGate = AppLimitReentryGate()

    /**
     * Non-null while the blocker pipeline is failing: set when a retry episode exhausts its
     * horizon without [AppLimitOverlayActivity] ever confirming on screen, cleared when a launch
     * is confirmed or a fresh request comes in. Failed starts were previously log-only even
     * though the code documents that Android drops them silently — the user had no way to know
     * protection was gone.
     */
    private val _overlayLaunchWarning = MutableStateFlow<String?>(null)
    val overlayLaunchWarning: StateFlow<String?> = _overlayLaunchWarning.asStateFlow()

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

    // -----------------------------------------------------------------------------------------
    // Session persistence
    // -----------------------------------------------------------------------------------------

    /**
     * Re-arms a temporary usage session that outlived the process.
     *
     * The session — and with it the deadline that raises the "time's up" blocker — used to live
     * exclusively in memory. Nothing survives a process kill (a Recents swipe, a low-memory kill,
     * or an OEM battery manager; there is no foreground service while an App Limit session runs),
     * so the timer simply stopped existing and the popup never came back. Restoring the snapshot
     * puts the deadline, the pause bookkeeping and the daily-usage baseline back, and the elapsed
     * time grows by the gap between the snapshot's clock anchor and now — the monotonic
     * elapsedRealtime when it still refers to the same boot, the saved wall timestamp otherwise
     * (reboot, or a snapshot written by an older build), `startedAt` as a last resort. Deriving
     * that gap from the wall clock alone (the old behaviour) let a time change made while the
     * process was dead jump the deadline days forward or rewind it, so a session either finished
     * on restore or never finished at all.
     *
     * Nothing is shown from here: at restore time we do not know which app is in the foreground,
     * so raising a blocker could throw a full-screen popup over whatever the user is doing (the
     * launcher, FocusShield itself). The app-limit gate raises it the moment the limited app is
     * opened, which is the same moment it would have been raised anyway.
     *
     * Safe to call more than once: an in-memory session always wins over the snapshot.
     */
    fun restoreSessionFromDisk() {
        if (_activeSession.value != null) return

        val snapshot = sessionStore.load() ?: return
        val today = appLimitRepository.getTodayDateString()

        // A snapshot from another day used to be discarded, which silently killed a session the
        // user had chosen — and which the dashboard still showed as running — the first time the
        // process died across midnight (start 23:30, restore 00:10 → no timer, no blocker, and
        // the rest of the day's usage was free). Roll it onto today instead: keep the session and
        // its remaining time, reset the daily baseline to the new day (yesterday's baseline would
        // double-count yesterday's usage against today's allowance), and re-persist so the new
        // date survives another restart.
        val rolledFromAnotherDay = snapshot.dateString != today
        val session = if (rolledFromAnotherDay) {
            Log.i(tag, "Rolling app limit session snapshot from ${snapshot.dateString} onto $today")
            snapshot.session.copy(initialDailyUsedMillis = 0L)
        } else {
            snapshot.session
        }
        val elapsed = if (session.isPaused) {
            // Still parked outside the app — keep the frozen clock and wait for the user to return.
            session.elapsedMillis
        } else {
            session.elapsedMillis + restoreGapMillis(snapshot)
        }
        val restored = session.copy(elapsedMillis = elapsed)
        _activeSession.value = restored
        if (rolledFromAnotherDay) persistActiveSession()

        if (restored.isSessionExpired) {
            Log.i(tag, "Restored an expired session for ${restored.appName}; finalising it")
            handleSessionExpired(restored, raiseBlocker = false)
        } else {
            Log.i(
                tag,
                "Restored active session for ${restored.appName}: ${restored.remainingSessionMillis / 1000}s left (paused=${restored.isPaused})"
            )
            startTicker()
        }
    }

    /**
     * Unpaused wall time between the snapshot being written and now, in this process's terms.
     *
     * Preference order:
     * 1. **Monotonic** — `elapsedRealtime` delta, valid only while both ends belong to the same
     *    boot (a saved value larger than "now" means the device rebooted). Immune to every
     *    wall-clock change: manual time edits, NTP steps, timezone moves.
     * 2. **Saved wall timestamp** — same-boot check impossible (reboot), but the snapshot still
     *    records when it was written.
     * 3. **`startedAt`** — legacy snapshot from a build without anchors; the original derivation,
     *    paused time subtracted, kept purely so an old snapshot still restores sanely.
     */
    private fun restoreGapMillis(snapshot: AppLimitSessionStore.Snapshot): Long {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (snapshot.savedAtElapsed > 0L && nowElapsed >= snapshot.savedAtElapsed) {
            return nowElapsed - snapshot.savedAtElapsed
        }
        if (snapshot.savedAtWall > 0L) {
            return (System.currentTimeMillis() - snapshot.savedAtWall).coerceAtLeast(0L)
        }
        val session = snapshot.session
        return (System.currentTimeMillis() - session.startedAt - session.totalPausedMillis)
            .coerceAtLeast(0L)
    }

    /** Mirrors the live session to disk. Called on start/pause/resume only, never on the 500 ms tick. */
    private fun persistActiveSession() {
        val session = _activeSession.value
        if (session == null) {
            sessionStore.clear()
            return
        }
        sessionStore.save(session, appLimitRepository.getTodayDateString())
    }

    private fun clearPersistedSession() {
        sessionStore.clear()
    }

    init {
        scope.launch {
            try {
                val active = appLimitRepository.getActiveLimits()
                enabledLimits = active.associateBy { it.packageName }
            } catch (e: Exception) {
                Log.e(tag, "Initial load active limits error: ${e.message}")
            }
            appLimitRepository.getActiveLimitsFlow().collectLatest { limits ->
                // Build the whole map, then swap the reference (atomic, see enabledLimits).
                enabledLimits = limits.associateBy { it.packageName }
            }
        }
    }

    /**
     * Checks if the package has an active configured App Limit.
     */
    fun isPackageLimited(packageName: String): Boolean {
        if (enabledLimits.containsKey(packageName)) return true
        // Fallback: if the in-memory cache hasn't loaded yet, do a synchronous DB read.
        // This path is only hit during early startup before the Flow collector populates the cache.
        return try {
            val fromDb = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                appLimitRepository.getLimitByPackage(packageName)
            }
            if (fromDb != null && fromDb.isEnabled) {
                enabledLimits = enabledLimits + (packageName to fromDb)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun onLimitSaved(limit: AppLimitEntity) {
        enabledLimits = if (limit.isEnabled) {
            enabledLimits + (limit.packageName to limit)
        } else {
            enabledLimits - limit.packageName
        }
    }

    fun onLimitDeleted(packageName: String) {
        enabledLimits = enabledLimits - packageName
    }

    fun getLimit(packageName: String): AppLimitEntity? {
        return enabledLimits[packageName] ?: try {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                appLimitRepository.getLimitByPackage(packageName)?.also {
                    if (it.isEnabled) enabledLimits = enabledLimits + (it.packageName to it)
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

        // The limit row is read fresh, every decision (issue #49, C4). The cache above is only
        // a pre-filter for the hot paths; deciding from it let a just-disabled (or deleted)
        // limit keep blocking until the flow emission landed, and cached rows were never
        // invalidated by edits made elsewhere in the app.
        val limit = try {
            appLimitRepository.getLimitByPackage(packageName)
        } catch (e: Exception) {
            Log.e(tag, "Failed to read limit for $packageName; treating as not limited: ${e.message}")
            null
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

        // The user already answered this app's blocker by closing the app. Nothing else records
        // that choice — `_activeSession` is null by the time the "time's up" blocker is even
        // raised — so without this the events that arrive while the app is being torn down behind
        // the blocker re-prompt the user with a fresh selection popup over the home screen they
        // just reached (issue #53). The gate opens again the moment they really re-enter the app.
        if (reentryGate.isArmedFor(packageName)) {
            return AppLimitDecision.ALLOW_BLOCKER_DISMISSED
        }

        // Check remaining daily allowance from system usage stats and database
        val dailyLimitMinutes = limit.dailyLimitMinutes
        val dailyLimitMillis = dailyLimitMinutes * 60 * 1000L
        val usageResult = DeviceUsageStatsHelper.getTodayAppUsageResult(appContext, packageName)
        val dbUsage = usage?.usedMillis ?: 0L

        // Use system usage when valid. Only fall back to DB when permission is genuinely denied.
        val accumulatedUsed = when (usageResult.status) {
            UsageResult.UsageStatus.VALID -> usageResult.usageMillis
            UsageResult.UsageStatus.PERMISSION_DENIED -> {
                // Permission not granted — use DB as fallback (it's the only data we have)
                dbUsage
            }
            else -> {
                // QUERY_FAILED or NO_DATA — use DB as fallback
                dbUsage
            }
        }

        // One millisecond-precision comparison decides exhaustion (issue #49, C2). The three
        // terms OR-ed here before (minute floor of the allowance, ms remainder, direct ms
        // compare) happened to fire at the same instant — but the minute arithmetic pattern
        // they embodied was copied into every display site, where limit-minus-floored-used
        // rounded the remaining *up* and let the UI promise "1m remaining" while seconds were
        // left and the next evaluation blocked. Display sites now floor the remaining millis
        // directly; this verdict is ms-only.
        val remainingDailyMillis = (dailyLimitMillis - accumulatedUsed).coerceAtLeast(0L)

        val isStrict = limit.isStrictOverride
        val isExhausted = accumulatedUsed >= dailyLimitMillis

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

    /**
     * Called by Accessibility Service whenever the foreground window package changes.
     */
    fun onForegroundPackageChanged(packageName: String) {
        if (!AppLimitPackageFilter.shouldTrackForeground(packageName)) {
            return
        }

        val previous = currentForegroundPackage
        currentForegroundPackage = packageName
        val now = System.currentTimeMillis()

        // Movement is what opens the re-entry gate: leaving confirms the exit, coming back retires
        // the suppression so the next decision is taken normally (issue #53).
        reentryGate.onForegroundPackage(packageName)

        // A block whose launch never landed is re-armed the moment the user steps back into the app
        // it belongs to. This is the one signal that does not depend on the app producing further
        // accessibility events while its blocker is missing, so a static screen (a paused video,
        // an idle feed) can no longer leave the user inside a limited app with no popup at all.
        rearmPendingOverlayIfNeeded(packageName)

        if (previous == packageName) return

        val currentSession = _activeSession.value ?: return

        val sessionPackage = currentSession.packageName

        // 1. If user leaves our active limited app for another app:
        if (previous == sessionPackage && packageName != sessionPackage) {
            if (!currentSession.isPaused) {
                Log.d(tag, "User switched out of limited app: ${currentSession.appName} to $packageName. Pausing timer.")
                // update{} re-reads the live value, so a tick that is already in flight cannot
                // resurrect this session's previous pause/resume flags (see startTicker).
                _activeSession.update { current ->
                    if (current?.id != currentSession.id || current.isPaused) current
                    else current.copy(isPaused = true, lastPauseTimestamp = now)
                }
                persistActiveSession()
            }
        }

        // 2. If user returns to our active limited app:
        if (packageName == sessionPackage) {
            if (currentSession.isPaused && currentSession.lastPauseTimestamp > 0L) {
                val addedPause = (now - currentSession.lastPauseTimestamp).coerceAtLeast(0L)
                Log.d(tag, "User returned to active limited app: ${currentSession.appName}. Resuming timer (paused for ${addedPause}ms).")
                _activeSession.update { current ->
                    if (current?.id != currentSession.id || !current.isPaused) current
                    else current.copy(
                        isPaused = false,
                        totalPausedMillis = current.totalPausedMillis + addedPause,
                        lastPauseTimestamp = 0L
                    )
                }
                persistActiveSession()
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
        val usageResult = DeviceUsageStatsHelper.getTodayAppUsageResult(appContext, packageName)
        val systemUsage = usageResult.usageMillis

        // Log diagnostic comparison for debugging (especially for YouTube)
        if (packageName == "com.google.android.youtube") {
            DeviceUsageStatsHelper.logDiagnosticComparison(appContext, packageName)
        }

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
        // A duration was chosen, so any earlier "dismissed by closing the app" suppression for
        // this package is spent (issue #53).
        reentryGate.clear(packageName)
        persistActiveSession()
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

            _activeSession.update { current ->
                if (current != null && current.packageName == packageName) {
                    current.copy(
                        selectedDurationMillis = actualDuration,
                        initialDailyUsedMillis = usedMillis
                    )
                } else {
                    current
                }
            }
            persistActiveSession()
            Log.i(tag, "Started temporary usage session for $appName: $durationMinutes min ($actualDuration ms, isEmergency=$isEmergency)")
        }
    }

    /**
     * Starts a 5-minute Emergency Session for an app whose daily limit is exhausted.
     *
     * [onResult] reports whether a session actually started. The blocker uses it to decide whether
     * it may step aside: an emergency pass can be refused (the allowance for the day is already
     * spent, and the count the popup was built with can be one launch old), and dismissing the
     * blocker in that case left the app completely unblocked with no session and no popup.
     * Invoked on the main thread.
     */
    fun startEmergencySession(packageName: String, appName: String, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            val started = try {
                val todayDate = appLimitRepository.getTodayDateString()
                val usage = appLimitRepository.getUsage(packageName, todayDate)
                val currentCount = usage?.emergencyUsesCount ?: 0
                // Fresh row: the emergency allowance is enforcement, not display — a stale
                // cache entry could grant (or refuse) a pass after the user edited the limit
                // (issue #49, C4).
                val limit = appLimitRepository.getLimitByPackage(packageName)
                val allowedCount = limit?.emergencyUsesAllowed ?: 1
                if (currentCount >= allowedCount) {
                    Log.w(tag, "Emergency uses exhausted for $packageName (count: $currentCount, allowed: $allowedCount)")
                    false
                } else {
                    // Increment emergency count
                    appLimitRepository.setEmergencyUsesCount(packageName, todayDate, currentCount + 1)

                    // Start 5-minute session
                    startTemporarySession(
                        packageName = packageName,
                        appName = appName,
                        durationMinutes = 5,
                        isEmergency = true
                    )
                    true
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to start emergency session for $packageName: ${e.message}")
                false
            }
            withContext(Dispatchers.Main) { onResult(started) }
        }
    }

    /**
     * Bypasses the limit for the rest of today.
     */
    fun leaveBlockForToday(packageName: String) {
        scope.launch {
            val todayDate = appLimitRepository.getTodayDateString()
            appLimitRepository.setBypassedForToday(packageName, todayDate, true)
            reentryGate.clear(packageName)
            _activeSession.value = null
            stopTicker()
            clearPersistedSession()
            Log.i(tag, "App $packageName bypassed for the rest of today")
        }
    }

    /**
     * Permanently deletes the app limit configuration.
     */
    fun removeLimitPermanently(packageName: String) {
        scope.launch {
            appLimitRepository.deleteLimit(packageName)
            enabledLimits = enabledLimits - packageName
            reentryGate.clear(packageName)
            _activeSession.value = null
            stopTicker()
            clearPersistedSession()
            Log.i(tag, "App limit for $packageName permanently removed")
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            var tickCount = 0
            // UsageStats-derived session elapsed, refreshed every 10th tick (~5 s). Null while
            // the query fails or the permission is denied, which falls back to the monotonic clock.
            var usageElapsed: Long? = null
            // Monotonic baseline for this coroutine: the elapsedRealtime of the previous tick.
            // Each unpaused tick adds (now - lastTick) to the session's elapsed, replacing
            // `wall clock - startedAt - totalPaused` — that formula let a manual time change, an
            // NTP step or a DST/timezone move jump the running timer forward or rewind it
            // mid-session (deadline hours away in seconds, or never reached at all). The baseline
            // lives in this coroutine, so a pause rebase can never race the update{} below.
            var lastTickElapsed = SystemClock.elapsedRealtime()
            while (true) {
                delay(500L)
                val session = _activeSession.value ?: break
                val nowTickElapsed = SystemClock.elapsedRealtime()
                tickCount++

                val currentElapsed = if (session.isPaused) {
                    // Parked outside the app: credit nothing, and rebase so the paused interval is
                    // not added when the user returns (coming back restarts this ticker anyway;
                    // the rebase covers the pause boundary this tick straddles — at most one
                    // 500 ms tick of unpaused time, and only ever in the user's favour).
                    lastTickElapsed = nowTickElapsed
                    session.elapsedMillis
                } else {
                    val monotonicElapsed = session.elapsedMillis +
                        (nowTickElapsed - lastTickElapsed).coerceAtLeast(0L)
                    lastTickElapsed = nowTickElapsed
                    if (tickCount % 10 == 1) {
                        val usage = DeviceUsageStatsHelper.getTodayAppUsageResult(appContext, session.packageName)
                        usageElapsed = if (usage.status == UsageResult.UsageStatus.VALID) {
                            val derived = usage.usageMillis - session.initialDailyUsedMillis
                            // Negative delta = the daily window rolled past the session's
                            // baseline (midnight, or a UsageStats reset). The sample is
                            // meaningless — not "zero seconds used". It used to be coerced to 0,
                            // which fed a non-null 0 into the override below and pinned elapsed
                            // at its current value: the session then never expired and the
                            // blocker for it never came back.
                            if (derived >= 0L) derived else null
                        } else {
                            null
                        }
                    }
                    // UsageStats is the authoritative clock while it agrees with reality: it only
                    // accumulates while the app actually holds the foreground, so screen-off /
                    // service-off periods do not drain the allowance (wall clock did, contradicting
                    // saveSessionRecord's "usage comes from UsageStatsManager" rule).
                    //
                    // A sample that is missing or *behind* the timer is not authoritative — query
                    // failure, stats lag, or a day rollover that threw away the history the
                    // baseline was measured against. The old floor, max(sample, elapsed), pinned
                    // elapsed at its current value in exactly those cases and stopped the session
                    // clock for as long as the disagreement lasted; falling back to the monotonic
                    // elapsed keeps the session expiring on time instead. When the sample is ahead,
                    // min() caps it against the monotonic elapsed so stats can never count more
                    // than real time permits. Either way the result is >= the live value, so
                    // elapsed never rewinds.
                    val derived = usageElapsed
                    if (derived == null || derived < session.elapsedMillis) {
                        monotonicElapsed
                    } else {
                        minOf(derived, monotonicElapsed)
                    }
                }

                // update{} re-reads whatever is live right now. Assigning a copy of the snapshot
                // this tick started from could overwrite a pause/resume the main thread had just
                // recorded, and a resurrected `isPaused = true` freezes the elapsed clock: the
                // session would never expire, so the blocker for it would never be raised again.
                _activeSession.update { current ->
                    if (current?.id != session.id) current
                    else current.copy(
                        elapsedMillis = if (current.isPaused) current.elapsedMillis else currentElapsed
                    )
                }

                val updatedSession = _activeSession.value?.takeIf { it.id == session.id } ?: break

                // Reminder check: notify the user when time is almost up, but only while they are
                // actually in the app (issue #54). This ticker keeps running while the session is
                // parked outside the app, and the parked clock satisfies the "under a minute left"
                // window on every tick — which is how the reminder ended up repeating forever for
                // an app the user was not using. Pause state and the tracked foreground package
                // are both passed in; the once-per-session rule lives in AppLimitReminderPolicy.
                val limit = enabledLimits[updatedSession.packageName]
                if (limit != null && limit.showRemindersBeforeLimit) {
                    AppLimitStrictModeEngine.instance.checkAndSendReminderIfNeeded(
                        packageName = updatedSession.packageName,
                        appName = updatedSession.appName,
                        remainingMillis = updatedSession.remainingSessionMillis,
                        showRemindersSetting = limit.showRemindersBeforeLimit,
                        sessionId = updatedSession.id,
                        isPaused = updatedSession.isPaused,
                        isLimitedAppInForeground = currentForegroundPackage == updatedSession.packageName
                    )
                }

                // Check for session completion or daily limit exhaustion
                if (updatedSession.isSessionExpired || (!updatedSession.isEmergency && updatedSession.isDailyLimitExhausted)) {
                    Log.i(tag, "Temporary session for ${updatedSession.appName} completed! Elapsed: ${updatedSession.elapsedMillis / 1000}s (Target: ${updatedSession.selectedDurationMillis / 1000}s)")
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

    /**
     * Ends an expired usage session.
     *
     * [raiseBlocker] is false only when the expiry is discovered while restoring a session that
     * outlived the process: at that point the foreground app is unknown, so the blocker is left to
     * the app-limit gate, which raises it the moment the limited app is actually opened.
     */
    private fun handleSessionExpired(session: ActiveAppUsageSession, raiseBlocker: Boolean = true) {
        scope.launch {
            try {
                finalizeExpiredSession(session, raiseBlocker)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Recording the session row, syncing usage or looking up the limit must never be
                // able to leave the user unblocked. If any of it throws, drop the session and
                // force the popup anyway — previously an exception here killed the coroutine
                // before launchOverlay() ran, and the allowance silently never expired.
                Log.e(tag, "Failed to finalise expiry for ${session.appName}; forcing overlay anyway", e)
                _activeSession.value = null
                stopTicker()
                clearPersistedSession()
                clearOverlayDebounce(session.packageName)
                if (raiseBlocker) {
                    launchOverlay(
                        packageName = session.packageName,
                        appName = session.appName,
                        mode = AppLimitOverlayMode.SESSION_COMPLETE,
                        forceLaunch = true
                    )
                }
            }
        }
    }

    private suspend fun finalizeExpiredSession(session: ActiveAppUsageSession, raiseBlocker: Boolean = true) {
        val actualUsed = session.elapsedMillis
        val endReason = if (session.isEmergency) "EMERGENCY_EXPIRED" else if (session.isDailyLimitExhausted) "DAILY_LIMIT_REACHED" else "TIMER_EXPIRED"
        saveSessionRecord(session, actualUsed, endReason)

        _activeSession.value = null
        stopTicker()
        clearPersistedSession()

        if (!raiseBlocker) return

        // Clear debounce timestamp so the next foreground change re-checks the limit immediately
        clearOverlayDebounce(session.packageName)

        // 1. Pause media immediately (overlay will be displayed directly over the app without minimizing to home first)
        MediaPauseHelper.pauseMedia(appContext)

        // 2. Query updated total daily usage from system and database
        val limit = appLimitRepository.getLimitByPackage(session.packageName)
        val todayDate = appLimitRepository.getTodayDateString()
        val usage = appLimitRepository.getUsage(session.packageName, todayDate)
        val usageResult = DeviceUsageStatsHelper.getTodayAppUsageResult(appContext, session.packageName)
        val dbUsage = usage?.usedMillis ?: 0L
        // Use system usage when valid. Only fall back to DB when permission is genuinely denied.
        val totalDailyUsedMillis = when (usageResult.status) {
            UsageResult.UsageStatus.VALID -> usageResult.usageMillis
            UsageResult.UsageStatus.PERMISSION_DENIED -> dbUsage
            else -> dbUsage
        }

        val dailyLimitMinutes = limit?.dailyLimitMinutes ?: 60
        val dailyLimitMillis = dailyLimitMinutes * 60 * 1000L

        // Display minutes floor the *remaining millis* (issue #27, item 6; issue #49, C2): the
        // limit minus floored used minutes rounded the remaining up and could promise a minute
        // the user did not have. The verdict is a pure millisecond comparison, so the mode raised
        // (SESSION_COMPLETE vs DAILY_LIMIT_REACHED) is exactly the comparison
        // checkAppLimitDecision uses to block.
        val totalDailyUsedMinutes = (totalDailyUsedMillis / 60000L).toInt().coerceAtLeast(0)
        val remainingDailyMinutes = ((dailyLimitMillis - totalDailyUsedMillis).coerceAtLeast(0L) / 60000L).toInt()
        val isDailyExhausted = totalDailyUsedMillis >= dailyLimitMillis

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
            // Fresh value from the limit row fetched above, not the flag captured when the
            // session started: strict mode toggled mid-session must take effect at expiry, or a
            // blocker built from the stale session flag silently downgrades to non-strict.
            isStrict = limit?.isStrictOverride ?: session.isStrict,
            streakDays = limit?.streakDays ?: 0,
            forceLaunch = true
        )
    }

    private fun finalizeAndStopSession(reason: String) {
        val session = _activeSession.value ?: return
        val actualUsed = session.elapsedMillis
        _activeSession.value = null
        stopTicker()
        clearPersistedSession()

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
        val usageResult = DeviceUsageStatsHelper.getTodayAppUsageResult(appContext, session.packageName)
        if (usageResult.status == UsageResult.UsageStatus.VALID) {
            appLimitRepository.syncSystemUsage(session.packageName, session.appName, usageResult.usageMillis, todayDate)
        }
    }

    /** In-flight overlay relaunch/verify loops, one per package. */
    private val overlayLaunchJobs = ConcurrentHashMap<String, Job>()

    /**
     * The launch request each package's retry loop must attempt next.
     *
     * Holding the intent here (rather than inside the job) is what lets a newer request — fresh
     * usage numbers, a different overlay mode — replace the one a running loop is working with
     * without restarting the loop. Restarting it on every accessibility event is what turned the
     * blocker into a launch storm (see [startOverlayAndVerify]).
     */
    private val pendingOverlayIntents = ConcurrentHashMap<String, Intent>()

    /** How long to wait after a launch attempt before asking whether it actually landed. */
    private val overlayVerifyDelayMs = 1_200L

    /** Attempts in the fast burst that follows a request. */
    private val maxOverlayLaunchAttempts = 4

    /** Slow re-arm interval used after the burst, while the block is still owed and missing. */
    private val overlayHeartbeatIntervalMs = 10_000L

    /** Upper bound on one retry episode, so a failed block can never retry forever. */
    private val overlayRetryHorizonMs = 5 * 60 * 1000L

    /**
     * Raises (or refreshes) the app-limit blocker for [packageName].
     *
     * Two guarantees matter here, both learned from the popup going missing:
     *
     * 1. **One blocker per app.** An overlay is a full-screen activity: launching a second one
     *    while the first is on screen makes the first one `onStop()` → `finish()`, and a launch
     *    that arrives while the previous instance is between "finishing" and "gone" is dropped
     *    silently by Android. The accessibility service re-requests a block on *every* event from
     *    the limited app (throttled to 500 ms) and the app keeps emitting those events from behind
     *    the blocker, so this used to happen continuously — the popup was rebuilt, flickered, and
     *    occasionally vanished for good. If the overlay for this app is already up, there is
     *    nothing to do.
     * 2. **A request is not a launch.** The request is stored and handed to a per-app retry loop
     *    that keeps working until the overlay is confirmed on screen or the block is no longer
     *    required.
     */
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

        // The user is already looking at this app's blocker — a second instance would finish the
        // first one and risk being dropped in the handshake. Keep the one on screen.
        if (AppLimitFloatingOverlayManager.isShowingFor(packageName) || AppLimitOverlayActivity.isVisibleFor(packageName)) {
            Log.d(tag, "Blocker already on screen for $packageName; not launching another instance")
            return
        }

        MediaPauseHelper.pauseMedia(appContext)

        // Try floating window overlay directly over the limited app first
        val accessibilityService = com.example.core.accessibility.FocusAccessibilityService.instance
        if (accessibilityService != null || android.provider.Settings.canDrawOverlays(appContext)) {
            AppLimitFloatingOverlayManager.showOverlay(
                context = accessibilityService ?: appContext,
                packageName = packageName,
                appName = appName,
                mode = mode,
                usedMinutes = usedMinutes,
                remainingDailyMinutes = remainingDailyMinutes,
                dailyLimitMinutes = dailyLimitMinutes,
                emergencyUsesCount = emergencyUsesCount,
                emergencyUsesAllowed = emergencyUsesAllowed,
                isStrict = isStrict,
                streakDays = streakDays,
                onResult = { success ->
                    if (!success) {
                        launchActivityOverlay(
                            packageName, appName, mode, selectedMinutes, usedMinutes,
                            remainingDailyMinutes, dailyLimitMinutes, emergencyUsesCount,
                            emergencyUsesAllowed, isStrict, streakDays
                        )
                    }
                }
            )
            return
        }

        launchActivityOverlay(
            packageName, appName, mode, selectedMinutes, usedMinutes,
            remainingDailyMinutes, dailyLimitMinutes, emergencyUsesCount,
            emergencyUsesAllowed, isStrict, streakDays
        )
    }

    private fun launchActivityOverlay(
        packageName: String,
        appName: String,
        mode: AppLimitOverlayMode,
        selectedMinutes: Int,
        usedMinutes: Int,
        remainingDailyMinutes: Int,
        dailyLimitMinutes: Int,
        emergencyUsesCount: Int,
        emergencyUsesAllowed: Int,
        isStrict: Boolean,
        streakDays: Int
    ) {
        // A fresh request supersedes any previous failure notice; the loop below clears it again
        // once a launch is actually confirmed on screen.
        _overlayLaunchWarning.value = null

        val intent = Intent(appContext, AppLimitOverlayActivity::class.java).apply {
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

        pendingOverlayIntents[packageName] = intent
        startOverlayAndVerify(packageName)
    }

    /**
     * Keeps trying to put the blocker for [packageName] on screen, and verifies that each attempt
     * actually landed.
     *
     * `Context.startActivity()` does not throw when Android's background-activity-start
     * restriction silently discards the launch — it only logs. Treating "no exception" as success
     * is what let the expiry popup vanish with no error and no retry, leaving the user unblocked.
     * So every attempt is confirmed against a real signal ([AppLimitOverlayActivity.isVisibleFor]).
     *
     * A loop is *not* restarted while one is already running for this package: a second loop would
     * cancel the first one mid-attempt, which is precisely the teardown/launch overlap that
     * Android drops. Newer requests only replace the intent the running loop will use next.
     *
     * After the fast burst (~5 s of 1.2 s retries) the loop slows down to a heartbeat instead of
     * giving up, but only while the user is still in the blocked app. That way a launch swallowed
     * on a static screen — where an idle app emits no more accessibility events for the gate to
     * react to — can no longer leave the app unblocked until the user closes and reopens it.
     */
    private fun startOverlayAndVerify(packageName: String) {
        if (overlayLaunchJobs[packageName]?.isActive == true) {
            Log.d(tag, "Overlay retry loop already running for $packageName; updated its pending intent")
            return
        }

        overlayLaunchJobs[packageName] = scope.launch {
            val startedAt = System.currentTimeMillis()
            var attempt = 0

            while (true) {
                if (AppLimitOverlayActivity.isVisibleFor(packageName)) {
                    Log.i(tag, "Blocker confirmed on screen for $packageName (after $attempt attempt(s))")
                    pendingOverlayIntents.remove(packageName)
                    _overlayLaunchWarning.value = null
                    return@launch
                }

                // Re-decide before *every* launch attempt. A request can outlive the reason for it
                // — the user started a session, spent an emergency pass, turned the limit off, or
                // the request is a stale re-arm from an earlier visit — and raising a blocker the
                // engine no longer wants is exactly the kind of popup the user cannot get rid of.
                if (!shouldStillShowOverlay(packageName)) {
                    Log.i(tag, "Overlay no longer required for $packageName; stopping retry loop")
                    pendingOverlayIntents.remove(packageName)
                    return@launch
                }

                val intent = pendingOverlayIntents[packageName]
                if (intent == null) {
                    Log.d(tag, "Overlay request for $packageName cleared; stopping retry loop")
                    return@launch
                }

                attemptOverlayLaunch(intent)
                attempt++

                delay(if (attempt <= maxOverlayLaunchAttempts) overlayVerifyDelayMs else overlayHeartbeatIntervalMs)

                if (System.currentTimeMillis() - startedAt >= overlayRetryHorizonMs) {
                    Log.e(tag, "Could not display AppLimitOverlayActivity for $packageName after $attempt attempts in ${overlayRetryHorizonMs / 1000}s")
                    pendingOverlayIntents.remove(packageName)
                    // Log-only used to hide a real, repeated failure. Surface it so the dashboard
                    // can tell the user their limits are not actually blocking anything right now.
                    val appName = intent.getStringExtra(AppLimitOverlayActivity.EXTRA_APP_NAME) ?: packageName
                    _overlayLaunchWarning.value =
                        "FocusShield could not show the block screen for $appName. " +
                            "Check that the accessibility service is enabled, then reopen $appName."
                    return@launch
                }

                // Only keep retrying while the user is still inside the blocked app (or while we
                // genuinely do not know where they are, e.g. right after a process restart). If
                // they tapped "Close <app>" and left, the request stays pending and is re-armed the
                // moment they open the app again — see rearmPendingOverlayIfNeeded — instead of a
                // popup being thrown over whatever they moved on to.
                if (!isBlockedAppInForeground(packageName)) {
                    Log.d(tag, "Leaving blocker pending for $packageName; user is no longer in the app")
                    return@launch
                }
            }
        }
    }

    /**
     * True while [packageName] is known to be the app on screen, or while the foreground app is
     * simply unknown (the accessibility service has not reported one yet).
     */
    private fun isBlockedAppInForeground(packageName: String): Boolean {
        val foreground = currentForegroundPackage ?: return true
        return foreground == packageName
    }

    /**
     * Re-arms the retry loop for [packageName] when a block is still owed but not on screen.
     * Called from [onForegroundPackageChanged] — see the note there.
     */
    private fun rearmPendingOverlayIfNeeded(packageName: String) {
        if (!pendingOverlayIntents.containsKey(packageName)) return
        if (AppLimitOverlayActivity.isVisibleFor(packageName)) return
        // A blocker the user has already dismissed by leaving must not come back with them.
        if (reentryGate.isArmedFor(packageName)) return
        Log.i(tag, "Re-arming pending blocker for $packageName")
        startOverlayAndVerify(packageName)
    }

    /**
     * The user answered [packageName]'s blocker by closing the app (the "Close <app>" button, or
     * system back, both of which go to the launcher).
     *
     * Everything that could otherwise put a blocker back on screen for this app is retired here:
     * the pending launch request, the retry loop working on it, the launch debounce, and — through
     * [AppLimitReentryGate] — the decision itself, until the app is genuinely opened again. Before
     * this existed the request was deliberately left armed for a later re-entry while the gate
     * simultaneously re-decided from scratch, so the dismissal produced a second popup immediately
     * and a stale one later (issue #53).
     *
     * Safe to call from the main thread; does no I/O.
     */
    fun onBlockerDismissedByUser(packageName: String) {
        if (packageName.isBlank()) return
        reentryGate.onBlockerDismissed(packageName)
        pendingOverlayIntents.remove(packageName)
        overlayLaunchJobs.remove(packageName)?.cancel()
        lastOverlayLaunchPerPackage.remove(packageName)
        Log.i(tag, "Blocker for $packageName dismissed by the user; suppressing until they reopen it")
    }

    /**
     * One launch attempt. Prefers the accessibility service (an enabled service is exempt from the
     * background-activity-start restriction moreso than a plain app context), falling back to the
     * app context. Never assumes the attempt succeeded — see [startOverlayAndVerify].
     */
    private fun attemptOverlayLaunch(intent: Intent) {
        val service = com.example.core.accessibility.FocusAccessibilityService.instance
        if (service != null) {
            try {
                service.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(tag, "FocusAccessibilityService failed to start overlay: ${e.message}")
            }
        }
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "appContext failed to start overlay: ${e.message}")
        }
    }

    /**
     * True while the app-limit engine still wants the block on screen for [packageName].
     * Used to stop the relaunch loop once the user has resolved the prompt.
     *
     * Deliberately cheap. [startOverlayAndVerify] calls this before *every* attempt — every
     * 1.2 s during the burst, then every 10 s — and the old answer came from
     * [checkAppLimitDecision], which reaches its verdict through a UsageStatsManager query:
     * a system service hammered from a background loop, and worse, any transient
     * PERMISSION_DENIED/QUERY_FAILED mid-retry flipped the verdict to "not blocked" and
     * silently cancelled a block that was valid when the loop started (issue #49, O2).
     * So the usage numbers are never re-read here — only the cheap, structural state that can
     * genuinely retire a pending block: Focus Session taking over, a started session, the
     * limit turned off or deleted, today's bypass.
     */
    private suspend fun shouldStillShowOverlay(packageName: String): Boolean {
        // Same answers checkAppLimitDecision gives for these cases, minus the usage query.
        val focusSession = sessionManager.activeSession.value
        val isFocusRunning = sessionManager.sessionState.value == com.example.feature.session.domain.SessionState.RUNNING
        if (isFocusRunning && focusSession != null) return false // Focus Session owns the screen now

        // The user picked a duration: the selection prompt has been answered.
        if (isSessionActiveFor(packageName)) return false

        // The user answered by closing the app instead (issue #53). A loop already in flight when
        // they pressed the button must stop too, not just the next decision.
        if (reentryGate.isArmedFor(packageName)) return false

        val limit = try {
            appLimitRepository.getLimitByPackage(packageName)
        } catch (e: Exception) {
            Log.w(tag, "Could not re-read limit for $packageName; stopping block retry: ${e.message}")
            null
        }
        if (limit == null || !limit.isEnabled) return false

        val usage = try {
            appLimitRepository.getUsage(packageName, appLimitRepository.getTodayDateString())
        } catch (e: Exception) {
            Log.w(tag, "Could not re-read usage for $packageName; keeping block: ${e.message}")
            null
        }
        if (usage?.isBypassedForToday == true) return false

        return true
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

    /**
     * The user answered this app's blocker by closing the app, and has not opened it again since.
     * Enforcement resumes on their next genuine entry (issue #53).
     */
    data object ALLOW_BLOCKER_DISMISSED : AppLimitDecision

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
