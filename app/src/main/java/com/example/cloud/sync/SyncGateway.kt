package com.example.cloud.sync

import com.example.cloud.auth.AuthRepository
import com.example.cloud.auth.AuthState
import com.example.cloud.net.ConnectivityMonitor
import com.example.data.preferences.FocusPreferences
import com.example.data.preferences.FocusPreferencesRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central sync trigger hub.
 *
 * FocusShield has no background job library (no new dependencies), so sync is driven reactively from
 * process-local signals: (a) the auth state becomes [AuthState.Authenticated], (b) connectivity
 * returns after being offline, and (c) any `SyncTracker.enqueue*` (via [SyncTracker.onPendingChanged])
 * records a local edit. Each trigger is debounced/coalesced into at most one cycle that drains the
 * outbox and reconciles — draining reads the whole outbox each time, so coalescing can never lose an
 * edit. All cycles are additionally serialized on [runMutex] so a debounced cycle, a forced UI retry
 * and a migration/restore never overlap.
 *
 * Every entry point is a no-op (or a benign outcome) while the app is not configured / signed out /
 * offline, keeping the optional cloud layer fully inert for the default local-only user.
 */
class SyncGateway(
    private val scope: CoroutineScope,
    private val authRepository: AuthRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val engine: SyncEngine,
    private val installState: CloudInstallState,
    private val preferencesRepository: FocusPreferencesRepository
) {

    private val _running = MutableStateFlow(false)

    /** True while a sync cycle, migration, or restore is executing. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _lastOutcome = MutableStateFlow<SyncCycleOutcome?>(null)

    /** Outcome of the most recent attempt; null before the first attempt. */
    val lastOutcome: StateFlow<SyncCycleOutcome?> = _lastOutcome.asStateFlow()

    private val _lastSyncAtMillis = MutableStateFlow(0L)

    /** Wall-clock time of the last cycle that actually reached the cloud; 0 before the first one. */
    val lastSyncAtMillis: StateFlow<Long> = _lastSyncAtMillis.asStateFlow()

    private val runMutex = Mutex()

    /** True while a debounced sync is already scheduled (coalescing guard). */
    private val scheduled = AtomicBoolean(false)

    /** Subscribe to the reactive triggers. Called once from app wiring. */
    fun start() {
        scope.launch {
            authRepository.authState.collect { state ->
                // StateFlow replays the current value, so a session restored before start() also fires.
                if (state is AuthState.Authenticated) requestSync()
            }
        }
        scope.launch {
            connectivityMonitor.isOnline.collect { online ->
                if (online) requestSync()
            }
        }
        startPeriodicAutoSave()
    }

    /**
     * The periodic auto-save loop: on every tick, push pending changes if there are any.
     *
     * Two properties matter here. First, the tick is gated on [SyncTracker.pendingCount] — an idle
     * install returns immediately and never touches Firestore, which is what makes a seconds-level
     * cadence affordable. Second, the loop is rebuilt whenever the user changes the interval in
     * Settings (`collectLatest` cancels the in-flight delay), so the new cadence takes effect
     * immediately instead of after the previous (possibly much longer) wait elapses.
     *
     * Cycles are serialized on [runMutex] inside [runCycle], so a tick can never overlap a debounced
     * sync, a manual "Sync now", or a migration.
     */
    private fun startPeriodicAutoSave() {
        scope.launch {
            preferencesRepository.preferencesFlow
                .map { prefs ->
                    AutoSaveCadence(
                        enabled = prefs.isAutoSaveToCloudEnabled,
                        seconds = prefs.autoSaveIntervalSeconds.coerceIn(
                            FocusPreferences.MIN_AUTO_SAVE_INTERVAL_SECONDS,
                            FocusPreferences.MAX_AUTO_SAVE_INTERVAL_SECONDS
                        )
                    )
                }
                .distinctUntilChanged()
                .collectLatest { cadence ->
                    while (true) {
                        delay(cadence.seconds * 1000L)
                        if (!cadence.enabled) continue
                        if (!hasPendingWork()) continue
                        runCycle()
                    }
                }
        }
    }

    /** The auto-save setting the periodic loop is currently honouring. */
    private data class AutoSaveCadence(val enabled: Boolean, val seconds: Int)

    /**
     * Is there anything at all that has not reached the cloud yet?
     *
     * Two independent kinds of pending work, and both have to be counted. Room-backed rows queue ops
     * in the outbox ([SyncTracker.pendingCount]); the `profiles` and `user_preferences` documents are
     * pushed as whole rows and instead carry dirty flags ([CloudInstallState]). Gating on the outbox
     * alone would silently drop every preference or profile edit — the user changes their daily goal,
     * the tick sees an empty outbox, and nothing is ever uploaded.
     */
    private suspend fun hasPendingWork(): Boolean =
        SyncTracker.pendingCount.value > 0 || installState.profileDirty() || installState.prefsDirty()

    /**
     * Force a push of anything currently pending (Settings "Sync now", app-backgrounded, or the
     * WorkManager background job). Returns the cycle outcome; never throws.
     */
    suspend fun flushPending(): SyncCycleOutcome {
        if (!eligible()) return SyncCycleOutcome.NoSession
        if (!hasPendingWork()) return SyncCycleOutcome.Synced
        return runCycle()
    }

    /** Ask for a sync soon. Coalesces concurrent requests; safe from any thread/coroutine. */
    fun requestSync() {
        if (!eligible()) return
        if (!scheduled.compareAndSet(false, true)) return
        scope.launch {
            delay(SYNC_DEBOUNCE_MILLIS)
            scheduled.set(false)
            runCycle()
        }
    }

    /** Run one sync cycle now (subject to online/auth gating). Never throws. */
    suspend fun runCycle(): SyncCycleOutcome {
        if (!eligible()) return SyncCycleOutcome.NoSession
        return runMutex.withLock {
            if (!connectivityMonitor.isOnline.value) {
                SyncCycleOutcome.Offline
            } else {
                execute { engine.runCycle() }
            }
        }
    }

    /** First-account migration / resume of an interrupted migration. Never throws, never destructive. */
    suspend fun migrate(): SyncCycleOutcome = runExclusive { engine.migrate() }

    /** Destructive cross-account restore. Call only after the UI has confirmed. Never throws. */
    suspend fun restore(uid: String): SyncCycleOutcome = runExclusive { engine.restore(uid) }

    private suspend fun runExclusive(block: suspend () -> SyncCycleOutcome): SyncCycleOutcome =
        runMutex.withLock { execute(block) }

    private suspend fun execute(block: suspend () -> SyncCycleOutcome): SyncCycleOutcome {
        _running.value = true
        try {
            val outcome = block()
            _lastOutcome.value = outcome
            // Only a cycle that actually reached Firestore counts as "synced"; an offline or
            // no-session attempt must not make the UI claim the data is backed up.
            if (outcome == SyncCycleOutcome.Synced || outcome == SyncCycleOutcome.Migrated) {
                _lastSyncAtMillis.value = System.currentTimeMillis()
            }
            return outcome
        } finally {
            _running.value = false
        }
    }

    private fun eligible(): Boolean =
        authRepository.isConfigured && authRepository.currentUserId() != null

    private companion object {
        const val SYNC_DEBOUNCE_MILLIS = 1500L
    }
}
