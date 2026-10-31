package com.example.feature.session.domain

import com.example.data.model.SessionMode

/**
 * Domain model representing an active Focus Session across Timer, Stopwatch, and Pomodoro modes.
 */
data class FocusSession(
    val id: String,
    val mode: SessionMode,
    val subject: String,
    val topic: String,
    val goal: String,
    val state: SessionState,
    val startedAt: Long,
    val plannedDurationMillis: Long,
    val elapsedDurationMillis: Long,
    val remainingDurationMillis: Long,
    val pomodoroPhase: PomodoroPhase? = null,
    val currentCycle: Int = 1,
    val totalCycles: Int = 1,
    val laps: List<LapItem> = emptyList(),
    val isAppBlockingEnabled: Boolean = true,
    val isStrictModeEnabled: Boolean = false,
    val strictModeConfig: StrictModeConfig = StrictModeConfig(enabled = isStrictModeEnabled),
    val isStudyChannelsEnabled: Boolean = true,
    val isBrowserStudyModeEnabled: Boolean = false,
    /** Packages selected for this session's app shield. They are not global block rules. */
    val blockedAppPackages: Set<String> = emptySet(),
    /** Schedule-specific notification blocking flag. */
    val blockNotifications: Boolean = false,
    /** Default manual break duration for this session. */
    val defaultBreakMinutes: Int = 5,
    val pomodoroConfig: PomodoroConfig = PomodoroConfig(),
    val manualBreak: ManualBreakInfo? = null,
    val totalBreakDurationMillis: Long = 0L,
    /**
     * When > 0L, this session is anchored to a scheduled wall-clock end time (epoch millis).
     * The session strictly stops at [scheduledEndTimeMillis], while [elapsedDurationMillis]
     * tracks only active focused study time (excluding pauses and breaks).
     */
    val scheduledEndTimeMillis: Long = 0L
) {
    val isRunning: Boolean get() = state == SessionState.RUNNING
    val isPaused: Boolean get() = state == SessionState.PAUSED
    val isCompleted: Boolean get() = state == SessionState.COMPLETED
    val isCancelled: Boolean get() = state == SessionState.CANCELLED
    val isBreakActive: Boolean
        get() = (manualBreak != null && manualBreak.isActive) ||
                (mode == SessionMode.POMODORO &&
                        (pomodoroPhase == PomodoroPhase.SHORT_BREAK || pomodoroPhase == PomodoroPhase.LONG_BREAK) &&
                        remainingDurationMillis > 0L)

    /**
     * Progress ratio from 0.0 to 1.0 based on session mode.
     */
    val progress: Float
        get() = when (mode) {
            SessionMode.TIMER -> {
                if (plannedDurationMillis <= 0) 0f
                else if (scheduledEndTimeMillis > 0L) {
                    val wallElapsed = (plannedDurationMillis - remainingDurationMillis).coerceIn(0L, plannedDurationMillis)
                    (wallElapsed.toFloat() / plannedDurationMillis.toFloat()).coerceIn(0f, 1f)
                } else {
                    (elapsedDurationMillis.toFloat() / plannedDurationMillis.toFloat()).coerceIn(0f, 1f)
                }
            }
            SessionMode.POMODORO -> {
                val phaseDuration = when (pomodoroPhase) {
                    PomodoroPhase.FOCUS -> pomodoroConfig.focusDurationMillis
                    PomodoroPhase.SHORT_BREAK -> pomodoroConfig.shortBreakDurationMillis
                    PomodoroPhase.LONG_BREAK -> pomodoroConfig.longBreakDurationMillis
                    null -> pomodoroConfig.focusDurationMillis
                }
                if (phaseDuration <= 0) 0f
                else {
                    val phaseRemaining = remainingDurationMillis.coerceIn(0L, phaseDuration)
                    val phaseElapsed = phaseDuration - phaseRemaining
                    (phaseElapsed.toFloat() / phaseDuration.toFloat()).coerceIn(0f, 1f)
                }
            }
            SessionMode.STOPWATCH -> {
                // Circular 60-second loop progress for stopwatch visual appeal
                val secondsInMinute = (elapsedDurationMillis / 1000) % 60
                secondsInMinute / 60f
            }
        }
}
