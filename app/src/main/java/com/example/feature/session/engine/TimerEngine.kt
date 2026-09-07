package com.example.feature.session.engine

import com.example.feature.session.domain.SessionState

/**
 * Authoritative, drift-free Countdown Timer Engine.
 * Calculations are strictly derived from real timestamps to survive backgrounding and process stalls.
 */
class TimerEngine(
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) {
    var plannedDurationMillis: Long = 0L
        private set
    var startTimestamp: Long = 0L
        private set
    var pauseTimestamp: Long = 0L
        private set
    var accumulatedPausedMillis: Long = 0L
        private set
    var isRunning: Boolean = false
        private set
    var isPaused: Boolean = false
        private set
    var isCompleted: Boolean = false
        private set

    fun start(durationMillis: Long, now: Long = currentTimeProvider()) {
        require(durationMillis > 0) { "Timer duration must be greater than 0" }
        plannedDurationMillis = durationMillis
        startTimestamp = now
        pauseTimestamp = 0L
        accumulatedPausedMillis = 0L
        isRunning = true
        isPaused = false
        isCompleted = false
    }

    fun pause(now: Long = currentTimeProvider()) {
        if (!isRunning || isPaused || isCompleted) return
        isPaused = true
        pauseTimestamp = now
    }

    fun resume(now: Long = currentTimeProvider()) {
        if (!isRunning || !isPaused || isCompleted) return
        if (pauseTimestamp > 0) {
            accumulatedPausedMillis += (now - pauseTimestamp).coerceAtLeast(0L)
        }
        pauseTimestamp = 0L
        isPaused = false
    }

    fun calculateElapsed(now: Long = currentTimeProvider()): Long {
        if (!isRunning && !isCompleted) return 0L
        val effectiveNow = if (isPaused && pauseTimestamp > 0) pauseTimestamp else now
        val rawElapsed = (effectiveNow - startTimestamp - accumulatedPausedMillis).coerceAtLeast(0L)
        return rawElapsed.coerceAtMost(plannedDurationMillis)
    }

    fun calculateRemaining(now: Long = currentTimeProvider()): Long {
        if (!isRunning && !isCompleted) return plannedDurationMillis
        val elapsed = calculateElapsed(now)
        return (plannedDurationMillis - elapsed).coerceAtLeast(0L)
    }

    fun updateAndCheckCompletion(now: Long = currentTimeProvider()): Boolean {
        if (!isRunning || isPaused || isCompleted) return isCompleted
        val remaining = calculateRemaining(now)
        if (remaining <= 0L) {
            isCompleted = true
            isRunning = false
            return true
        }
        return false
    }

    fun reset() {
        plannedDurationMillis = 0L
        startTimestamp = 0L
        pauseTimestamp = 0L
        accumulatedPausedMillis = 0L
        isRunning = false
        isPaused = false
        isCompleted = false
    }
}
