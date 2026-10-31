package com.example.feature.session.engine

import android.content.Context
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.feature.session.domain.FocusSession
import com.example.feature.session.domain.LapItem
import com.example.feature.session.domain.ManualBreakInfo
import com.example.feature.session.domain.PomodoroConfig
import com.example.feature.session.domain.PomodoroPhase
import com.example.feature.session.domain.SessionState
import com.example.feature.session.domain.StrictModeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed class SessionNotificationEvent {
    data class TimerCompleted(val session: FocusSession) : SessionNotificationEvent()
    data class PomodoroPhaseTransition(
        val previousPhase: PomodoroPhase,
        val newPhase: PomodoroPhase?,
        val currentCycle: Int,
        val totalCycles: Int,
        val isFullCompleted: Boolean,
        val subject: String
    ) : SessionNotificationEvent()
    data object BreakExpired : SessionNotificationEvent()
}

/**
 * Central FocusSessionManager coordinating Timer, Stopwatch, and Pomodoro engines.
 * Serves as the single authoritative source of truth for all active study sessions in FocusShield.
 */
class FocusSessionManager(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) {
    val timerEngine = TimerEngine(currentTimeProvider)
    val stopwatchEngine = StopwatchEngine(currentTimeProvider)
    val pomodoroEngine = PomodoroEngine(currentTimeProvider)
    val breakManager = BreakManager(coroutineScope, currentTimeProvider)

    var onNotificationEvent: ((SessionNotificationEvent) -> Unit)? = null

    private val _activeSession = MutableStateFlow<FocusSession?>(null)
    val activeSession: StateFlow<FocusSession?> = _activeSession.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var tickerJob: Job? = null
    @Volatile
    private var cachedAppContext: Context? = null
    @Volatile
    private var lastDiskSaveTimestamp: Long = 0L

    companion object {
        val instance: FocusSessionManager by lazy { FocusSessionManager() }
    }

    private fun resolveContext(explicitContext: Context? = null): Context? {
        if (explicitContext != null) {
            cachedAppContext = explicitContext.applicationContext ?: explicitContext
            return cachedAppContext
        }
        cachedAppContext?.let { return it }
        return try {
            if (com.example.FocusShieldApp::class.java.declaredFields.any { it.name == "instance" }) {
                com.example.FocusShieldApp.instance.applicationContext.also { cachedAppContext = it }
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun persistSnapshotSafely(context: Context? = null) {
        val ctx = resolveContext(context) ?: return
        saveActiveSessionToDisk(ctx)
    }

    /**
     * Starts a new session with the specified parameters.
     *
     * Refuses when a session is already RUNNING or PAUSED. Without that guard a second call simply
     * overwrote `_activeSession` with a fresh UUID, and the live session — never persisted, never
     * completed — was gone along with every minute the user had put into it. A double tap on
     * "Start" was enough (issue #28). The alarm path already guarded against this; the UI path did
     * not, so the guard now lives in the one place both paths go through.
     *
     * End, cancel or reset the current session first; the failure is a plain [IllegalStateException]
     * whose message is shown by [com.example.feature.session.SessionViewModel] as a validation error.
     */
    fun startSession(
        mode: SessionMode,
        subject: String = "",
        topic: String = "",
        goal: String = "",
        plannedDurationMillis: Long = 60 * 60 * 1000L,
        pomodoroConfig: PomodoroConfig = PomodoroConfig(),
        isAppBlocking: Boolean = true,
        isStrictMode: Boolean = false,
        strictModeConfig: StrictModeConfig = StrictModeConfig(enabled = isStrictMode),
        isStudyChannels: Boolean = true,
        isBrowserStudyMode: Boolean = false,
        blockedAppPackages: Set<String> = emptySet(),
        blockNotifications: Boolean = false,
        defaultBreakMinutes: Int = 5,
        scheduledEndTimeMillis: Long = 0L
    ): Result<FocusSession> {
        // Guard *before* any mutation: breakManager.reset() and the engine starts below are
        // side effects that must not happen when the call is going to be refused.
        val existing = _activeSession.value
        val currentState = _sessionState.value
        if (existing != null &&
            !existing.isCompleted &&
            !existing.isCancelled &&
            (currentState == SessionState.RUNNING || currentState == SessionState.PAUSED)
        ) {
            return Result.failure(
                IllegalStateException(
                    "A ${existing.mode.name.lowercase()} session is already " +
                        "${currentState.name.lowercase()}; end or cancel it before starting another."
                )
            )
        }

        val now = currentTimeProvider()
        breakManager.reset()

        val effectivePlannedDurationMillis = if (scheduledEndTimeMillis > now && mode == SessionMode.TIMER) {
            (scheduledEndTimeMillis - now).coerceAtLeast(1_000L)
        } else {
            plannedDurationMillis
        }

        // Validation based on mode
        when (mode) {
            SessionMode.TIMER -> {
                if (effectivePlannedDurationMillis <= 0) {
                    return Result.failure(IllegalArgumentException("Timer duration must be greater than 0"))
                }
                timerEngine.start(effectivePlannedDurationMillis, now)
            }
            SessionMode.STOPWATCH -> {
                stopwatchEngine.start(now)
            }
            SessionMode.POMODORO -> {
                val validationError = pomodoroConfig.validate()
                if (validationError != null) {
                    return Result.failure(IllegalArgumentException(validationError))
                }
                pomodoroEngine.start(pomodoroConfig, now)
            }
        }

        val session = FocusSession(
            id = UUID.randomUUID().toString(),
            mode = mode,
            subject = subject.trim(),
            topic = topic.trim(),
            goal = goal,
            state = SessionState.RUNNING,
            startedAt = now,
            plannedDurationMillis = when (mode) {
                SessionMode.TIMER -> effectivePlannedDurationMillis
                SessionMode.POMODORO -> pomodoroConfig.focusDurationMillis * pomodoroConfig.totalCycles
                SessionMode.STOPWATCH -> 0L
            },
            elapsedDurationMillis = 0L,
            remainingDurationMillis = when (mode) {
                SessionMode.TIMER -> effectivePlannedDurationMillis
                SessionMode.POMODORO -> pomodoroConfig.focusDurationMillis
                SessionMode.STOPWATCH -> 0L
            },
            pomodoroPhase = if (mode == SessionMode.POMODORO) PomodoroPhase.FOCUS else null,
            currentCycle = 1,
            totalCycles = if (mode == SessionMode.POMODORO) pomodoroConfig.totalCycles else 1,
            isAppBlockingEnabled = isAppBlocking,
            isStrictModeEnabled = isStrictMode,
            strictModeConfig = strictModeConfig,
            isStudyChannelsEnabled = isStudyChannels,
            isBrowserStudyModeEnabled = isBrowserStudyMode,
            blockedAppPackages = blockedAppPackages,
            blockNotifications = blockNotifications,
            defaultBreakMinutes = defaultBreakMinutes.coerceAtLeast(0),
            pomodoroConfig = pomodoroConfig,
            manualBreak = null,
            totalBreakDurationMillis = 0L,
            scheduledEndTimeMillis = if (scheduledEndTimeMillis > now) scheduledEndTimeMillis else 0L
        )

        _activeSession.value = session
        _sessionState.value = SessionState.RUNNING
        lastDiskSaveTimestamp = now
        persistSnapshotSafely()

        startTicker()
        return Result.success(session)
    }

    /**
     * Persists the active session snapshot to SharedPreferences so it survives process kills,
     * task clears, and low memory reclaims.
     */
    fun saveActiveSessionToDisk(context: Context) {
        cachedAppContext = context.applicationContext ?: context
        val current = _activeSession.value ?: return
        val now = currentTimeProvider()
        lastDiskSaveTimestamp = now
        try {
            val prefs = context.getSharedPreferences("focus_active_session_prefs", Context.MODE_PRIVATE)
            val activeBreak = current.manualBreak
            prefs.edit().apply {
                putString("id", current.id)
                putString("mode", current.mode.name)
                putString("subject", current.subject)
                putString("topic", current.topic)
                putString("goal", current.goal)
                putString("state", current.state.name)
                putLong("startedAt", current.startedAt)
                putLong("plannedDurationMillis", current.plannedDurationMillis)
                putLong("elapsedDurationMillis", current.elapsedDurationMillis)
                putLong("remainingDurationMillis", current.remainingDurationMillis)
                putString("pomodoroPhase", current.pomodoroPhase?.name)
                putInt("currentCycle", current.currentCycle)
                putInt("totalCycles", current.totalCycles)
                putBoolean("isAppBlockingEnabled", current.isAppBlockingEnabled)
                putBoolean("isStrictModeEnabled", current.isStrictModeEnabled)
                putBoolean("isStudyChannelsEnabled", current.isStudyChannelsEnabled)
                putBoolean("isBrowserStudyModeEnabled", current.isBrowserStudyModeEnabled)
                putStringSet("blockedAppPackages", current.blockedAppPackages)
                putBoolean("blockNotifications", current.blockNotifications)
                putInt("defaultBreakMinutes", current.defaultBreakMinutes)
                putLong("pomodoroFocusDuration", current.pomodoroConfig.focusDurationMillis)
                putLong("pomodoroShortBreakDuration", current.pomodoroConfig.shortBreakDurationMillis)
                putLong("pomodoroLongBreakDuration", current.pomodoroConfig.longBreakDurationMillis)
                putLong("totalBreakDurationMillis", current.totalBreakDurationMillis)
                putLong("scheduledEndTimeMillis", current.scheduledEndTimeMillis)
                if (activeBreak != null && activeBreak.isActive) {
                    putString("manualBreakId", activeBreak.id)
                    putLong("manualBreakRequestedMillis", activeBreak.requestedDurationMillis)
                    putLong("manualBreakStartedAt", activeBreak.startedAt)
                    putLong("manualBreakEndsAt", activeBreak.endsAt)
                } else {
                    remove("manualBreakId")
                    remove("manualBreakRequestedMillis")
                    remove("manualBreakStartedAt")
                    remove("manualBreakEndsAt")
                }
                putLong("savedAt", now)
                apply()
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    /**
     * Clears persisted active session state from disk.
     */
    fun clearActiveSessionFromDisk(context: Context) {
        cachedAppContext = context.applicationContext ?: context
        try {
            val prefs = context.getSharedPreferences("focus_active_session_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            // Ignored
        }
    }

    /**
     * Restores an active session from disk if one was running/paused prior to process restart.
     */
    fun restoreActiveSessionFromDisk(context: Context): FocusSession? {
        cachedAppContext = context.applicationContext ?: context
        try {
            val prefs = context.getSharedPreferences("focus_active_session_prefs", Context.MODE_PRIVATE)
            val id = prefs.getString("id", null) ?: return null
            val modeStr = prefs.getString("mode", null) ?: return null
            val stateStr = prefs.getString("state", null) ?: return null
            var state = try { SessionState.valueOf(stateStr) } catch (e: Exception) { return null }

            if (state != SessionState.RUNNING && state != SessionState.PAUSED) {
                return null
            }

            val mode = try { SessionMode.valueOf(modeStr) } catch (e: Exception) { SessionMode.TIMER }
            val subject = prefs.getString("subject", "") ?: ""
            val topic = prefs.getString("topic", "") ?: ""
            val goal = prefs.getString("goal", "") ?: ""
            val startedAt = prefs.getLong("startedAt", currentTimeProvider())
            val plannedDuration = prefs.getLong("plannedDurationMillis", 60 * 60 * 1000L)
            val savedElapsed = prefs.getLong("elapsedDurationMillis", 0L)
            val savedRemaining = prefs.getLong("remainingDurationMillis", plannedDuration)
            val savedAt = prefs.getLong("savedAt", startedAt)
            val isAppBlocking = prefs.getBoolean("isAppBlockingEnabled", true)
            val isStrictMode = prefs.getBoolean("isStrictModeEnabled", false)
            val isStudyChannels = prefs.getBoolean("isStudyChannelsEnabled", true)
            val isBrowserStudyMode = prefs.getBoolean("isBrowserStudyModeEnabled", false)
            val blockedAppPackages = prefs.getStringSet("blockedAppPackages", emptySet()) ?: emptySet()
            val blockNotifications = prefs.getBoolean("blockNotifications", false)
            val defaultBreakMinutes = prefs.getInt("defaultBreakMinutes", 5)
            var totalBreakDuration = prefs.getLong("totalBreakDurationMillis", 0L)
            val scheduledEndTimeMillis = prefs.getLong("scheduledEndTimeMillis", 0L)
            val currentCycle = prefs.getInt("currentCycle", 1)
            val totalCycles = prefs.getInt("totalCycles", 4)
            val savedPhaseStr = prefs.getString("pomodoroPhase", null)
            val savedPhase = try {
                if (savedPhaseStr != null) PomodoroPhase.valueOf(savedPhaseStr) else PomodoroPhase.FOCUS
            } catch (_: Exception) {
                PomodoroPhase.FOCUS
            }
            val pomodoroConfig = PomodoroConfig(
                focusDurationMillis = prefs.getLong("pomodoroFocusDuration", 25 * 60 * 1000L),
                shortBreakDurationMillis = prefs.getLong("pomodoroShortBreakDuration", 5 * 60 * 1000L),
                longBreakDurationMillis = prefs.getLong("pomodoroLongBreakDuration", 15 * 60 * 1000L),
                totalCycles = totalCycles
            )

            val now = currentTimeProvider()

            // If the session is ancient (e.g. started > 24 hours ago), clean it up and do not restore
            if (now - startedAt > 24 * 60 * 60 * 1000L || startedAt <= 0L) {
                clearActiveSessionFromDisk(context)
                return null
            }

            if (scheduledEndTimeMillis > 0L && now > scheduledEndTimeMillis + 300_000L) {
                // Scheduled window ended more than 5 minutes ago while app was closed
                clearActiveSessionFromDisk(context)
                return null
            }

            // In Strict Mode (non-Stopwatch), a session cannot remain paused indefinitely across app close
            if (isStrictMode && mode != SessionMode.STOPWATCH && state == SessionState.PAUSED) {
                state = SessionState.RUNNING
            }

            // Restore manual break if one was active when saved
            val manualBreakId = prefs.getString("manualBreakId", null)
            val manualBreakRequested = prefs.getLong("manualBreakRequestedMillis", 0L)
            val manualBreakStartedAt = prefs.getLong("manualBreakStartedAt", 0L)
            val manualBreakEndsAt = prefs.getLong("manualBreakEndsAt", 0L)
            val restoredBreak = if (manualBreakId != null && manualBreakEndsAt > 0L) {
                if (now < manualBreakEndsAt && (scheduledEndTimeMillis <= 0L || now < scheduledEndTimeMillis)) {
                    breakManager.restoreBreak(
                        id = manualBreakId,
                        sessionId = id,
                        requestedDurationMillis = manualBreakRequested,
                        startedAt = manualBreakStartedAt,
                        endsAt = manualBreakEndsAt,
                        now = now
                    )
                } else {
                    // Break finished while app was closed; credit break duration and resume study time from break end
                    totalBreakDuration += manualBreakRequested.coerceAtLeast(0L)
                    breakManager.reset()
                    null
                }
            } else {
                breakManager.reset()
                null
            }

            // Effective timestamp from which running study time progressed since last save
            val effectiveSavedAt = if (manualBreakId != null && manualBreakEndsAt > 0L && now >= manualBreakEndsAt) {
                maxOf(savedAt, manualBreakEndsAt)
            } else {
                savedAt
            }
            val isEnginePaused = state == SessionState.PAUSED || restoredBreak != null

            // Re-initialize engine using exact saved elapsed/remaining state
            when (mode) {
                SessionMode.TIMER -> {
                    val deltaSinceSave = if (!isEnginePaused && effectiveSavedAt > 0L) {
                        val capNow = if (scheduledEndTimeMillis > 0L) minOf(now, scheduledEndTimeMillis) else now
                        (capNow - effectiveSavedAt).coerceAtLeast(0L)
                    } else 0L
                    if (scheduledEndTimeMillis <= 0L && !isEnginePaused && savedElapsed + deltaSinceSave > plannedDuration + 300_000L) {
                        // Timer expired more than 5 minutes ago while app was closed
                        clearActiveSessionFromDisk(context)
                        return null
                    }
                    timerEngine.restore(
                        durationMillis = plannedDuration,
                        elapsedAtSaveMillis = savedElapsed,
                        savedAtTimestamp = effectiveSavedAt,
                        wasPaused = isEnginePaused,
                        now = if (scheduledEndTimeMillis > 0L) minOf(now, scheduledEndTimeMillis) else now
                    )
                }
                SessionMode.STOPWATCH -> {
                    stopwatchEngine.restore(
                        elapsedAtSaveMillis = savedElapsed,
                        savedAtTimestamp = effectiveSavedAt,
                        wasPaused = isEnginePaused,
                        now = now
                    )
                }
                SessionMode.POMODORO -> {
                    pomodoroEngine.restore(
                        pomodoroConfig = pomodoroConfig,
                        phase = savedPhase,
                        cycle = currentCycle,
                        phaseRemainingAtSaveMillis = savedRemaining,
                        totalFocusElapsedAtSaveMillis = savedElapsed,
                        savedAtTimestamp = effectiveSavedAt,
                        wasPaused = isEnginePaused,
                        now = now
                    )
                }
            }

            val restoredElapsed = when (mode) {
                SessionMode.TIMER -> timerEngine.calculateElapsed(
                    if (scheduledEndTimeMillis > 0L) minOf(now, scheduledEndTimeMillis) else now
                )
                SessionMode.STOPWATCH -> stopwatchEngine.calculateElapsed(now)
                SessionMode.POMODORO -> pomodoroEngine.calculateTotalFocusElapsed(now)
            }
            val restoredRemaining = when (mode) {
                SessionMode.TIMER -> {
                    if (scheduledEndTimeMillis > 0L) {
                        (scheduledEndTimeMillis - now).coerceAtLeast(0L)
                    } else {
                        timerEngine.calculateRemaining(now)
                    }
                }
                SessionMode.STOPWATCH -> 0L
                SessionMode.POMODORO -> pomodoroEngine.calculatePhaseRemaining(now)
            }

            val session = FocusSession(
                id = id,
                mode = mode,
                subject = subject,
                topic = topic,
                goal = goal,
                state = state,
                startedAt = startedAt,
                plannedDurationMillis = plannedDuration,
                elapsedDurationMillis = restoredElapsed,
                remainingDurationMillis = restoredRemaining,
                pomodoroPhase = if (mode == SessionMode.POMODORO) pomodoroEngine.currentPhase else null,
                currentCycle = if (mode == SessionMode.POMODORO) pomodoroEngine.currentCycle else currentCycle,
                totalCycles = totalCycles,
                isAppBlockingEnabled = isAppBlocking,
                isStrictModeEnabled = isStrictMode,
                strictModeConfig = StrictModeConfig(enabled = isStrictMode),
                isStudyChannelsEnabled = isStudyChannels,
                isBrowserStudyModeEnabled = isBrowserStudyMode,
                blockedAppPackages = blockedAppPackages,
                blockNotifications = blockNotifications,
                defaultBreakMinutes = defaultBreakMinutes,
                pomodoroConfig = pomodoroConfig,
                manualBreak = restoredBreak,
                totalBreakDurationMillis = totalBreakDuration,
                scheduledEndTimeMillis = scheduledEndTimeMillis
            )

            _activeSession.value = session
            _sessionState.value = state

            // Recover or re-anchor StrictModeEngine lock if strict mode is enabled on this session
            if (isStrictMode && mode != SessionMode.STOPWATCH) {
                val recovered = StrictModeEngine.instance.recoverStateOnLaunch(context)
                if (!recovered && restoredRemaining > 0L) {
                    StrictModeEngine.instance.startStrictMode(
                        context = context,
                        sessionId = id,
                        mode = mode,
                        durationMillis = restoredRemaining
                    )
                }
            }

            // Always start ticker so active countdowns, breaks, and subsequent resumes tick reliably
            startTicker()
            if (state == SessionState.RUNNING || scheduledEndTimeMillis > 0L) {
                syncStateFromEngines(now)
            }
            persistSnapshotSafely(context)

            return _activeSession.value
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Initiates a manual temporary break during an active session.
     * Pauses the active study engine so study time does not progress.
     */
    fun startBreak(durationMinutes: Int): ManualBreakInfo? {
        val current = _activeSession.value ?: return null
        if (current.state != SessionState.RUNNING) return null
        val now = currentTimeProvider()

        // Pause engine study timing
        when (current.mode) {
            SessionMode.TIMER -> timerEngine.pause(now)
            SessionMode.STOPWATCH -> stopwatchEngine.pause(now)
            SessionMode.POMODORO -> pomodoroEngine.pause(now)
        }

        val breakInfo = breakManager.startBreak(current.id, durationMinutes, now)
        _activeSession.update {
            it?.copy(manualBreak = breakInfo)
        }
        startTicker()
        persistSnapshotSafely()
        return breakInfo
    }

    /**
     * Ends an active manual break early and resumes study session timing.
     */
    fun endBreakEarly(): ManualBreakInfo? {
        val current = _activeSession.value ?: return null
        val now = currentTimeProvider()

        val endedBreak = breakManager.endBreakEarly(now)
        val addedBreakDuration = endedBreak?.actualDurationMillis ?: 0L

        // Resume engine study timing
        when (current.mode) {
            SessionMode.TIMER -> timerEngine.resume(now)
            SessionMode.STOPWATCH -> stopwatchEngine.resume(now)
            SessionMode.POMODORO -> pomodoroEngine.resume(now)
        }

        _sessionState.value = SessionState.RUNNING
        _activeSession.update {
            it?.copy(
                state = SessionState.RUNNING,
                manualBreak = null,
                totalBreakDurationMillis = (it.totalBreakDurationMillis + addedBreakDuration)
            )
        }
        startTicker()
        syncStateFromEngines(now)
        persistSnapshotSafely()
        return endedBreak
    }

    /**
     * Pauses the active session.
     * Guarded in Strict Mode (non-Stopwatch) so strict sessions cannot be arbitrarily paused.
     */
    fun pauseSession(): Boolean {
        val current = _activeSession.value ?: return false
        if (current.state != SessionState.RUNNING) return false
        if (current.isStrictModeEnabled && current.mode != SessionMode.STOPWATCH) {
            return false
        }

        val now = currentTimeProvider()
        syncStateFromEngines(now)
        when (current.mode) {
            SessionMode.TIMER -> timerEngine.pause(now)
            SessionMode.STOPWATCH -> stopwatchEngine.pause(now)
            SessionMode.POMODORO -> pomodoroEngine.pause(now)
        }

        _sessionState.value = SessionState.PAUSED
        _activeSession.update {
            it?.copy(state = SessionState.PAUSED)
        }
        persistSnapshotSafely()
        return true
    }

    /**
     * Resumes the paused session.
     */
    fun resumeSession(): Boolean {
        val current = _activeSession.value ?: return false
        if (current.state != SessionState.PAUSED) return false

        val now = currentTimeProvider()
        when (current.mode) {
            SessionMode.TIMER -> timerEngine.resume(now)
            SessionMode.STOPWATCH -> stopwatchEngine.resume(now)
            SessionMode.POMODORO -> pomodoroEngine.resume(now)
        }

        _sessionState.value = SessionState.RUNNING
        _activeSession.update {
            it?.copy(state = SessionState.RUNNING)
        }
        startTicker()
        syncStateFromEngines(now)
        persistSnapshotSafely()
        return true
    }

    /**
     * Toggles pause/resume.
     */
    fun togglePause(): Boolean {
        val current = _activeSession.value ?: return false
        return if (current.state == SessionState.PAUSED) {
            resumeSession()
        } else if (current.state == SessionState.RUNNING) {
            pauseSession()
        } else {
            false
        }
    }

    /**
     * Records a lap in Stopwatch mode.
     */
    fun recordLap(): LapItem? {
        val current = _activeSession.value ?: return null
        if (current.mode != SessionMode.STOPWATCH) return null

        val now = currentTimeProvider()
        val lap = stopwatchEngine.recordLap(now)
        if (lap != null) {
            _activeSession.update {
                it?.copy(laps = stopwatchEngine.laps)
            }
            persistSnapshotSafely()
        }
        return lap
    }

    /**
     * Advances to the next Pomodoro phase.
     */
    fun skipPomodoroPhase() {
        val current = _activeSession.value ?: return
        if (current.mode != SessionMode.POMODORO) return

        val now = currentTimeProvider()
        val prevPhase = pomodoroEngine.currentPhase
        val prevCycle = pomodoroEngine.currentCycle
        pomodoroEngine.advanceToNextPhase(now)
        onNotificationEvent?.invoke(
            SessionNotificationEvent.PomodoroPhaseTransition(
                previousPhase = prevPhase,
                newPhase = if (pomodoroEngine.isCompleted) null else pomodoroEngine.currentPhase,
                currentCycle = prevCycle,
                totalCycles = current.totalCycles,
                isFullCompleted = pomodoroEngine.isCompleted,
                subject = current.subject
            )
        )
        syncStateFromEngines(now)
        persistSnapshotSafely()
    }

    /**
     * Completes or ends the session manually.
     */
    fun endSession(): FocusSession? {
        val now = currentTimeProvider()
        syncStateFromEngines(now)
        val current = _activeSession.value ?: return null
        if (current.isStrictModeEnabled &&
            current.mode != SessionMode.STOPWATCH &&
            current.remainingDurationMillis > 0L &&
            current.state != SessionState.COMPLETED
        ) {
            return null
        }
        stopTicker()
        breakManager.reset()

        val completedSession = current.copy(
            state = SessionState.COMPLETED,
            manualBreak = null
        )
        _sessionState.value = SessionState.COMPLETED
        _activeSession.value = completedSession

        timerEngine.reset()
        stopwatchEngine.reset()
        pomodoroEngine.reset()

        return completedSession
    }

    /**
     * Cancels the session without recording completion.
     */
    fun cancelSession(): FocusSession? {
        val now = currentTimeProvider()
        syncStateFromEngines(now)
        val current = _activeSession.value ?: return null
        if (current.isStrictModeEnabled &&
            current.mode != SessionMode.STOPWATCH &&
            current.remainingDurationMillis > 0L &&
            current.state != SessionState.COMPLETED
        ) {
            return null
        }
        stopTicker()
        breakManager.reset()

        val cancelledSession = current.copy(
            state = SessionState.CANCELLED,
            manualBreak = null
        )
        _sessionState.value = SessionState.CANCELLED
        _activeSession.value = cancelledSession

        timerEngine.reset()
        stopwatchEngine.reset()
        pomodoroEngine.reset()

        return cancelledSession
    }

    /**
     * Resets the manager to IDLE.
     */
    fun resetToIdle() {
        stopTicker()
        breakManager.reset()
        timerEngine.reset()
        stopwatchEngine.reset()
        pomodoroEngine.reset()
        _activeSession.value = null
        _sessionState.value = SessionState.IDLE
    }

    /**
     * Periodic ticker for updating UI snapshot.
     * Note: Timestamps in the engines remain the absolute source of truth.
     */
    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = coroutineScope.launch {
            while (isActive) {
                delay(500)
                val now = currentTimeProvider()
                syncStateFromEngines(now)
            }
        }
    }

    fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Computes the authoritative values from engines and updates the state flow.
     */
    fun syncStateFromEngines(now: Long = currentTimeProvider()) {
        var current = _activeSession.value ?: return

        // 0. If this session is anchored to a scheduled wall-clock end time (scheduledEndTimeMillis > 0L),
        // enforce the exact scheduled end-time cutoff regardless of whether the session is running, paused,
        // or currently on a manual break.
        if (current.scheduledEndTimeMillis > 0L && now >= current.scheduledEndTimeMillis) {
            var updatedBreakTotal = current.totalBreakDurationMillis
            if (breakManager.isBreakActive) {
                val endedBreak = breakManager.endBreakEarly(now)
                if (endedBreak != null) {
                    updatedBreakTotal += endedBreak.actualDurationMillis
                }
            }
            val rawFocusElapsed = when (current.mode) {
                SessionMode.TIMER -> timerEngine.calculateElapsed(current.scheduledEndTimeMillis)
                SessionMode.STOPWATCH -> stopwatchEngine.calculateElapsed(current.scheduledEndTimeMillis)
                SessionMode.POMODORO -> pomodoroEngine.calculateTotalFocusElapsed(current.scheduledEndTimeMillis)
            }.coerceIn(0L, current.plannedDurationMillis)

            // If no break or pause occurred and active elapsed is within 1.5s of planned duration, snap to planned duration
            val finalFocusElapsed = if (
                updatedBreakTotal == 0L &&
                current.state == SessionState.RUNNING &&
                (current.plannedDurationMillis - rawFocusElapsed) in 0L..1_500L
            ) {
                current.plannedDurationMillis
            } else {
                rawFocusElapsed
            }

            stopTicker()
            breakManager.reset()
            val completedSession = current.copy(
                state = SessionState.COMPLETED,
                elapsedDurationMillis = finalFocusElapsed,
                remainingDurationMillis = 0L,
                manualBreak = null,
                totalBreakDurationMillis = updatedBreakTotal
            )
            _sessionState.value = SessionState.COMPLETED
            _activeSession.value = completedSession
            onNotificationEvent?.invoke(SessionNotificationEvent.TimerCompleted(completedSession))
            return
        }

        // Check if an active break expired naturally
        if (breakManager.isBreakActive) {
            val expiredBreak = breakManager.checkExpiration(now)
            if (expiredBreak != null) {
                // Break completed/expired naturally, resume engine
                when (current.mode) {
                    SessionMode.TIMER -> timerEngine.resume(now)
                    SessionMode.STOPWATCH -> stopwatchEngine.resume(now)
                    SessionMode.POMODORO -> pomodoroEngine.resume(now)
                }

                _sessionState.value = SessionState.RUNNING
                val resumed = current.copy(
                    state = SessionState.RUNNING,
                    manualBreak = null,
                    totalBreakDurationMillis = (current.totalBreakDurationMillis + expiredBreak.actualDurationMillis)
                )
                _activeSession.value = resumed
                current = resumed
                persistSnapshotSafely()
                onNotificationEvent?.invoke(SessionNotificationEvent.BreakExpired)
            } else {
                // Break still actively counting down — update elapsed progress so StateFlow emits!
                val activeBreak = breakManager.updateProgress(now)
                val wallRemaining = if (current.scheduledEndTimeMillis > 0L) {
                    (current.scheduledEndTimeMillis - now).coerceAtLeast(0L)
                } else {
                    current.remainingDurationMillis
                }
                _activeSession.update {
                    it?.copy(
                        manualBreak = activeBreak,
                        remainingDurationMillis = wallRemaining
                    )
                }
                if (now - lastDiskSaveTimestamp >= 5_000L) {
                    persistSnapshotSafely()
                }
                return
            }
        }

        if (current.state == SessionState.PAUSED) {
            if (current.scheduledEndTimeMillis > 0L) {
                val wallRemaining = (current.scheduledEndTimeMillis - now).coerceAtLeast(0L)
                if (wallRemaining != current.remainingDurationMillis) {
                    _activeSession.update {
                        it?.copy(remainingDurationMillis = wallRemaining)
                    }
                }
            }
            return
        }

        when (current.mode) {
            SessionMode.TIMER -> {
                if (current.scheduledEndTimeMillis > 0L) {
                    val elapsed = timerEngine.calculateElapsed(now).coerceIn(0L, current.plannedDurationMillis)
                    val remaining = (current.scheduledEndTimeMillis - now).coerceAtLeast(0L)
                    _activeSession.value = current.copy(
                        elapsedDurationMillis = elapsed,
                        remainingDurationMillis = remaining
                    )
                    if (now - lastDiskSaveTimestamp >= 5_000L) {
                        persistSnapshotSafely()
                    }
                } else {
                    val isDone = timerEngine.updateAndCheckCompletion(now)
                    val elapsed = timerEngine.calculateElapsed(now)
                    val remaining = timerEngine.calculateRemaining(now)

                    if (isDone) {
                        stopTicker()
                        val completedSession = current.copy(
                            state = SessionState.COMPLETED,
                            elapsedDurationMillis = current.plannedDurationMillis,
                            remainingDurationMillis = 0L,
                            manualBreak = null
                        )
                        _sessionState.value = SessionState.COMPLETED
                        _activeSession.value = completedSession
                        onNotificationEvent?.invoke(SessionNotificationEvent.TimerCompleted(completedSession))
                    } else {
                        _activeSession.value = current.copy(
                            elapsedDurationMillis = elapsed,
                            remainingDurationMillis = remaining
                        )
                        if (now - lastDiskSaveTimestamp >= 5_000L) {
                            persistSnapshotSafely()
                        }
                    }
                }
            }
            SessionMode.STOPWATCH -> {
                val elapsed = stopwatchEngine.calculateElapsed(now)
                _activeSession.value = current.copy(
                    elapsedDurationMillis = elapsed,
                    remainingDurationMillis = 0L,
                    laps = stopwatchEngine.laps
                )
                if (now - lastDiskSaveTimestamp >= 5_000L) {
                    persistSnapshotSafely()
                }
            }
            SessionMode.POMODORO -> {
                val prevPhase = pomodoroEngine.currentPhase
                val prevCycle = pomodoroEngine.currentCycle
                val transitioned = pomodoroEngine.updateAndCheckTransitions(now)
                val totalFocusElapsed = pomodoroEngine.calculateTotalFocusElapsed(now)
                val phaseRemaining = pomodoroEngine.calculatePhaseRemaining(now)

                if (transitioned) {
                    onNotificationEvent?.invoke(
                        SessionNotificationEvent.PomodoroPhaseTransition(
                            previousPhase = prevPhase,
                            newPhase = if (pomodoroEngine.isCompleted) null else pomodoroEngine.currentPhase,
                            currentCycle = prevCycle,
                            totalCycles = current.totalCycles,
                            isFullCompleted = pomodoroEngine.isCompleted,
                            subject = current.subject
                        )
                    )
                }

                if (pomodoroEngine.isCompleted) {
                    stopTicker()
                    _sessionState.value = SessionState.COMPLETED
                    _activeSession.value = current.copy(
                        state = SessionState.COMPLETED,
                        pomodoroPhase = pomodoroEngine.currentPhase,
                        currentCycle = pomodoroEngine.currentCycle,
                        elapsedDurationMillis = totalFocusElapsed,
                        remainingDurationMillis = 0L,
                        manualBreak = null
                    )
                } else {
                    _activeSession.value = current.copy(
                        pomodoroPhase = pomodoroEngine.currentPhase,
                        currentCycle = pomodoroEngine.currentCycle,
                        elapsedDurationMillis = totalFocusElapsed,
                        remainingDurationMillis = phaseRemaining
                    )
                    if (transitioned || now - lastDiskSaveTimestamp >= 5_000L) {
                        persistSnapshotSafely()
                    }
                }
            }
        }
    }
}
