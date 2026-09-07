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

    companion object {
        val instance: FocusSessionManager by lazy { FocusSessionManager() }
    }

    /**
     * Starts a new session with the specified parameters.
     */
    fun startSession(
        mode: SessionMode,
        subject: String = "Physics",
        topic: String = "General Study",
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
        defaultBreakMinutes: Int = 5
    ): Result<FocusSession> {
        val now = currentTimeProvider()
        breakManager.reset()

        // Validation based on mode
        when (mode) {
            SessionMode.TIMER -> {
                if (plannedDurationMillis <= 0) {
                    return Result.failure(IllegalArgumentException("Timer duration must be greater than 0"))
                }
                timerEngine.start(plannedDurationMillis, now)
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
            subject = subject.ifBlank { "General Study" },
            topic = topic.ifBlank { "Practice & Review" },
            goal = goal,
            state = SessionState.RUNNING,
            startedAt = now,
            plannedDurationMillis = when (mode) {
                SessionMode.TIMER -> plannedDurationMillis
                SessionMode.POMODORO -> pomodoroConfig.focusDurationMillis * pomodoroConfig.totalCycles
                SessionMode.STOPWATCH -> 0L
            },
            elapsedDurationMillis = 0L,
            remainingDurationMillis = when (mode) {
                SessionMode.TIMER -> plannedDurationMillis
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
            totalBreakDurationMillis = 0L
        )

        _activeSession.value = session
        _sessionState.value = SessionState.RUNNING

        startTicker()
        return Result.success(session)
    }

    /**
     * Persists the active session snapshot to SharedPreferences so it survives process kills,
     * task clears, and low memory reclaims.
     */
    fun saveActiveSessionToDisk(context: Context) {
        val current = _activeSession.value ?: return
        try {
            val prefs = context.getSharedPreferences("focus_active_session_prefs", Context.MODE_PRIVATE)
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
                putLong("savedAt", currentTimeProvider())
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
        try {
            val prefs = context.getSharedPreferences("focus_active_session_prefs", Context.MODE_PRIVATE)
            val id = prefs.getString("id", null) ?: return null
            val modeStr = prefs.getString("mode", null) ?: return null
            val stateStr = prefs.getString("state", null) ?: return null
            val state = try { SessionState.valueOf(stateStr) } catch (e: Exception) { return null }

            if (state != SessionState.RUNNING && state != SessionState.PAUSED) {
                return null
            }

            val mode = try { SessionMode.valueOf(modeStr) } catch (e: Exception) { SessionMode.TIMER }
            val subject = prefs.getString("subject", "General Study") ?: "General Study"
            val topic = prefs.getString("topic", "Practice") ?: "Practice"
            val goal = prefs.getString("goal", "") ?: ""
            val startedAt = prefs.getLong("startedAt", currentTimeProvider())
            val plannedDuration = prefs.getLong("plannedDurationMillis", 60 * 60 * 1000L)
            val isAppBlocking = prefs.getBoolean("isAppBlockingEnabled", true)
            val isStrictMode = prefs.getBoolean("isStrictModeEnabled", false)
            val isStudyChannels = prefs.getBoolean("isStudyChannelsEnabled", true)
            val isBrowserStudyMode = prefs.getBoolean("isBrowserStudyModeEnabled", false)
            val blockedAppPackages = prefs.getStringSet("blockedAppPackages", emptySet()) ?: emptySet()
            val blockNotifications = prefs.getBoolean("blockNotifications", false)
            val defaultBreakMinutes = prefs.getInt("defaultBreakMinutes", 5)
            val totalBreakDuration = prefs.getLong("totalBreakDurationMillis", 0L)
            val currentCycle = prefs.getInt("currentCycle", 1)
            val totalCycles = prefs.getInt("totalCycles", 4)
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

            // Re-initialize engine
            when (mode) {
                SessionMode.TIMER -> {
                    if (now - startedAt > plannedDuration + 300_000L && state == SessionState.RUNNING) {
                        // Timer expired while app was closed, clean up stale session
                        clearActiveSessionFromDisk(context)
                        return null
                    }
                    timerEngine.start(plannedDuration, startedAt)
                    if (state == SessionState.PAUSED) {
                        timerEngine.pause(startedAt + prefs.getLong("elapsedDurationMillis", 0L))
                    }
                }
                SessionMode.STOPWATCH -> {
                    stopwatchEngine.start(startedAt)
                    if (state == SessionState.PAUSED) {
                        stopwatchEngine.pause(startedAt + prefs.getLong("elapsedDurationMillis", 0L))
                    }
                }
                SessionMode.POMODORO -> {
                    pomodoroEngine.start(pomodoroConfig, startedAt)
                    if (state == SessionState.PAUSED) {
                        pomodoroEngine.pause(now)
                    }
                }
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
                elapsedDurationMillis = prefs.getLong("elapsedDurationMillis", 0L),
                remainingDurationMillis = prefs.getLong("remainingDurationMillis", plannedDuration),
                pomodoroPhase = if (mode == SessionMode.POMODORO) PomodoroPhase.FOCUS else null,
                currentCycle = currentCycle,
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
                manualBreak = null,
                totalBreakDurationMillis = totalBreakDuration
            )

            _activeSession.value = session
            _sessionState.value = state

            if (state == SessionState.RUNNING) {
                startTicker()
                syncStateFromEngines(now)
            }

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

        _activeSession.update {
            it?.copy(
                manualBreak = null,
                totalBreakDurationMillis = (it.totalBreakDurationMillis + addedBreakDuration)
            )
        }
        return endedBreak
    }

    /**
     * Pauses the active session.
     */
    fun pauseSession(): Boolean {
        val current = _activeSession.value ?: return false
        if (current.state != SessionState.RUNNING) return false

        val now = currentTimeProvider()
        when (current.mode) {
            SessionMode.TIMER -> timerEngine.pause(now)
            SessionMode.STOPWATCH -> stopwatchEngine.pause(now)
            SessionMode.POMODORO -> pomodoroEngine.pause(now)
        }

        _sessionState.value = SessionState.PAUSED
        _activeSession.update {
            it?.copy(state = SessionState.PAUSED)
        }
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
    }

    /**
     * Completes or ends the session manually.
     */
    fun endSession(): FocusSession? {
        val now = currentTimeProvider()
        syncStateFromEngines(now)
        val current = _activeSession.value ?: return null
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
        val current = _activeSession.value ?: return

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

                _activeSession.update {
                    it?.copy(
                        manualBreak = null,
                        totalBreakDurationMillis = (it.totalBreakDurationMillis + expiredBreak.actualDurationMillis)
                    )
                }
                onNotificationEvent?.invoke(SessionNotificationEvent.BreakExpired)
            } else {
                // Break still actively counting down
                val activeBreak = breakManager.activeBreak.value
                _activeSession.update {
                    it?.copy(manualBreak = activeBreak)
                }
                return
            }
        }

        when (current.mode) {
            SessionMode.TIMER -> {
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
                }
            }
            SessionMode.STOPWATCH -> {
                val elapsed = stopwatchEngine.calculateElapsed(now)
                _activeSession.value = current.copy(
                    elapsedDurationMillis = elapsed,
                    remainingDurationMillis = 0L,
                    laps = stopwatchEngine.laps
                )
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
                }
            }
        }
    }
}
