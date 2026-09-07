package com.example.feature.session.engine

import com.example.core.util.TimeFormatter
import com.example.feature.session.domain.LapItem

/**
 * Authoritative, drift-free Stopwatch Engine with lap tracking support.
 */
class StopwatchEngine(
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) {
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

    private val _laps = mutableListOf<LapItem>()
    val laps: List<LapItem> get() = _laps.toList()

    fun start(now: Long = currentTimeProvider()) {
        startTimestamp = now
        pauseTimestamp = 0L
        accumulatedPausedMillis = 0L
        isRunning = true
        isPaused = false
        _laps.clear()
    }

    fun pause(now: Long = currentTimeProvider()) {
        if (!isRunning || isPaused) return
        isPaused = true
        pauseTimestamp = now
    }

    fun resume(now: Long = currentTimeProvider()) {
        if (!isRunning || !isPaused) return
        if (pauseTimestamp > 0) {
            accumulatedPausedMillis += (now - pauseTimestamp).coerceAtLeast(0L)
        }
        pauseTimestamp = 0L
        isPaused = false
    }

    fun calculateElapsed(now: Long = currentTimeProvider()): Long {
        if (!isRunning && startTimestamp == 0L) return 0L
        val effectiveNow = if (isPaused && pauseTimestamp > 0) pauseTimestamp else now
        return (effectiveNow - startTimestamp - accumulatedPausedMillis).coerceAtLeast(0L)
    }

    fun recordLap(now: Long = currentTimeProvider()): LapItem? {
        if (!isRunning && startTimestamp == 0L) return null
        val currentTotalElapsed = calculateElapsed(now)
        val previousTotalElapsed = _laps.lastOrNull()?.totalTimeMillis ?: 0L
        val lapDuration = (currentTotalElapsed - previousTotalElapsed).coerceAtLeast(0L)

        val newLap = LapItem(
            lapNumber = _laps.size + 1,
            lapTimeMillis = lapDuration,
            totalTimeMillis = currentTotalElapsed,
            formattedLapTime = TimeFormatter.formatDigital(lapDuration, alwaysShowHours = true),
            formattedTotalTime = TimeFormatter.formatDigital(currentTotalElapsed, alwaysShowHours = true)
        )
        _laps.add(newLap)
        return newLap
    }

    fun reset() {
        startTimestamp = 0L
        pauseTimestamp = 0L
        accumulatedPausedMillis = 0L
        isRunning = false
        isPaused = false
        _laps.clear()
    }
}
