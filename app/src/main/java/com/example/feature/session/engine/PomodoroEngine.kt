package com.example.feature.session.engine

import com.example.feature.session.domain.PomodoroConfig
import com.example.feature.session.domain.PomodoroPhase

/**
 * Deterministic Pomodoro State Machine and Timing Engine.
 * Handles cycle progression, phase switches, pause/resume, and final completion.
 */
class PomodoroEngine(
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) {
    var config: PomodoroConfig = PomodoroConfig()
        private set

    var currentPhase: PomodoroPhase = PomodoroPhase.FOCUS
        private set
    var currentCycle: Int = 1
        private set
    var isRunning: Boolean = false
        private set
    var isPaused: Boolean = false
        private set
    var isCompleted: Boolean = false
        private set

    var phaseStartTimestamp: Long = 0L
        private set
    var phasePauseTimestamp: Long = 0L
        private set
    var phaseAccumulatedPausedMillis: Long = 0L
        private set

    /**
     * Total elapsed focus study duration across completed focus phases in the session.
     */
    var completedFocusDurationMillis: Long = 0L
        private set

    fun start(pomodoroConfig: PomodoroConfig = PomodoroConfig(), now: Long = currentTimeProvider()) {
        val validationError = pomodoroConfig.validate()
        require(validationError == null) { validationError ?: "Invalid Pomodoro configuration" }

        config = pomodoroConfig
        currentPhase = PomodoroPhase.FOCUS
        currentCycle = 1
        isRunning = true
        isPaused = false
        isCompleted = false
        phaseStartTimestamp = now
        phasePauseTimestamp = 0L
        phaseAccumulatedPausedMillis = 0L
        completedFocusDurationMillis = 0L
    }

    fun pause(now: Long = currentTimeProvider()) {
        if (!isRunning || isPaused || isCompleted) return
        isPaused = true
        phasePauseTimestamp = now
    }

    fun resume(now: Long = currentTimeProvider()) {
        if (!isRunning || !isPaused || isCompleted) return
        if (phasePauseTimestamp > 0) {
            phaseAccumulatedPausedMillis += (now - phasePauseTimestamp).coerceAtLeast(0L)
        }
        phasePauseTimestamp = 0L
        isPaused = false
    }

    fun getPlannedPhaseDuration(): Long {
        return when (currentPhase) {
            PomodoroPhase.FOCUS -> config.focusDurationMillis
            PomodoroPhase.SHORT_BREAK -> config.shortBreakDurationMillis
            PomodoroPhase.LONG_BREAK -> config.longBreakDurationMillis
        }
    }

    fun calculatePhaseElapsed(now: Long = currentTimeProvider()): Long {
        if (!isRunning && !isCompleted) return 0L
        val effectiveNow = if (isPaused && phasePauseTimestamp > 0) phasePauseTimestamp else now
        val rawElapsed = (effectiveNow - phaseStartTimestamp - phaseAccumulatedPausedMillis).coerceAtLeast(0L)
        return rawElapsed.coerceAtMost(getPlannedPhaseDuration())
    }

    fun calculatePhaseRemaining(now: Long = currentTimeProvider()): Long {
        if (!isRunning && !isCompleted) return getPlannedPhaseDuration()
        val elapsed = calculatePhaseElapsed(now)
        return (getPlannedPhaseDuration() - elapsed).coerceAtLeast(0L)
    }

    /**
     * Checks if current phase reached 0 remaining and advances to next phase.
     * Returns true if phase changed or completed.
     */
    fun updateAndCheckTransitions(now: Long = currentTimeProvider()): Boolean {
        if (!isRunning || isPaused || isCompleted) return isCompleted
        val remaining = calculatePhaseRemaining(now)
        if (remaining <= 0L) {
            advanceToNextPhase(now)
            return true
        }
        return false
    }

    /**
     * Total actual pure focus study time accumulated across all cycles and current focus phase.
     * Excludes short breaks and long breaks.
     */
    fun calculateTotalFocusElapsed(now: Long = currentTimeProvider()): Long {
        if (!isRunning && !isCompleted && completedFocusDurationMillis == 0L) return 0L
        val currentFocusElapsed = if (currentPhase == PomodoroPhase.FOCUS) {
            calculatePhaseElapsed(now)
        } else {
            0L
        }
        val total = (completedFocusDurationMillis + currentFocusElapsed).coerceAtLeast(0L)
        return total.coerceAtMost(calculateTotalFocusPlanned())
    }

    /**
     * Total planned focus study duration for the entire Pomodoro session (focus per cycle * total cycles).
     */
    fun calculateTotalFocusPlanned(): Long {
        return config.focusDurationMillis * config.totalCycles
    }

    /**
     * Manually advances or skips to the next Pomodoro phase.
     */
    fun advanceToNextPhase(now: Long = currentTimeProvider()) {
        if (isCompleted) return

        // If transitioning from a FOCUS phase, accumulate its elapsed time into total study duration
        if (currentPhase == PomodoroPhase.FOCUS) {
            val focusElapsed = calculatePhaseElapsed(now)
            completedFocusDurationMillis = (completedFocusDurationMillis + focusElapsed)
                .coerceAtMost(calculateTotalFocusPlanned())
        }

        when (currentPhase) {
            PomodoroPhase.FOCUS -> {
                if (currentCycle < config.totalCycles) {
                    currentPhase = PomodoroPhase.SHORT_BREAK
                } else {
                    currentPhase = PomodoroPhase.LONG_BREAK
                }
            }
            PomodoroPhase.SHORT_BREAK -> {
                if (currentCycle < config.totalCycles) {
                    currentCycle += 1
                    currentPhase = PomodoroPhase.FOCUS
                } else {
                    currentPhase = PomodoroPhase.LONG_BREAK
                }
            }
            PomodoroPhase.LONG_BREAK -> {
                isCompleted = true
                isRunning = false
                return
            }
        }

        // Reset timestamps for new phase
        phaseStartTimestamp = now
        phasePauseTimestamp = 0L
        phaseAccumulatedPausedMillis = 0L
    }

    fun reset() {
        currentPhase = PomodoroPhase.FOCUS
        currentCycle = 1
        isRunning = false
        isPaused = false
        isCompleted = false
        phaseStartTimestamp = 0L
        phasePauseTimestamp = 0L
        phaseAccumulatedPausedMillis = 0L
        completedFocusDurationMillis = 0L
    }
}
