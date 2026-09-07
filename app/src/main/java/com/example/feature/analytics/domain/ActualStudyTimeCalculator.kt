package com.example.feature.analytics.domain

import com.example.data.model.SessionMode
import com.example.feature.session.domain.FocusSession
import com.example.feature.session.domain.PomodoroPhase

/**
 * Single authoritative calculator for pure study time in FocusShield.
 * Ensures consistent, deterministic calculations across Home, Stats, History, and Session Detail.
 *
 * Rules:
 * - Pure study time includes ONLY time actively spent in focus state.
 * - Pauses are strictly excluded.
 * - Manual breaks and Pomodoro breaks are strictly excluded.
 * - Blocked attempts are recorded as distraction events, NEVER counted as study time.
 */
object ActualStudyTimeCalculator {

    /**
     * Computes pure actual study time in milliseconds for a FocusSession.
     * Always based on the actual verified focus time elapsed, without inflating prematurely ended sessions.
     */
    fun calculatePureStudyDuration(session: FocusSession, isCompleted: Boolean = false): Long {
        val pureTime = when (session.mode) {
            SessionMode.TIMER -> {
                if (session.remainingDurationMillis == 0L && session.plannedDurationMillis > 0L) {
                    session.plannedDurationMillis
                } else {
                    session.elapsedDurationMillis.coerceAtMost(session.plannedDurationMillis)
                }
            }
            SessionMode.STOPWATCH -> {
                session.elapsedDurationMillis
            }
            SessionMode.POMODORO -> {
                // elapsedDurationMillis in Pomodoro stores total verified focus study elapsed time
                session.elapsedDurationMillis
            }
        }
        return pureTime.coerceAtLeast(0L)
    }

    /**
     * Computes pure study time from a raw active session snapshot.
     */
    fun calculateActiveStudyDuration(session: FocusSession): Long {
        return calculatePureStudyDuration(session, isCompleted = false)
    }
}
