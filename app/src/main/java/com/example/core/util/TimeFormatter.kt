package com.example.core.util

import com.example.data.model.SessionMode
import java.util.Locale

/**
 * Standardized, drift-free time formatting utility for FocusShield.
 */
object TimeFormatter {

    /**
     * Formats milliseconds into standard digital display "HH:MM:SS" or "MM:SS".
     * If [alwaysShowHours] is true, always returns "HH:MM:SS" (e.g. "00:25:00").
     */
    fun formatDigital(millis: Long, alwaysShowHours: Boolean = true): String {
        val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (alwaysShowHours || hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats display time for active session depending on session mode.
     * Timer: remaining time (HH:MM:SS)
     * Stopwatch: elapsed time (HH:MM:SS)
     * Pomodoro: remaining phase time (MM:SS or HH:MM:SS)
     */
    fun formatDisplayTime(remainingMillis: Long, elapsedMillis: Long, mode: SessionMode): String {
        return when (mode) {
            SessionMode.TIMER -> formatDigital(remainingMillis, alwaysShowHours = true)
            SessionMode.STOPWATCH -> formatDigital(elapsedMillis, alwaysShowHours = true)
            SessionMode.POMODORO -> {
                val totalSeconds = remainingMillis.coerceAtLeast(0L) / 1000L
                val hours = totalSeconds / 3600
                if (hours > 0) {
                    formatDigital(remainingMillis, alwaysShowHours = true)
                } else {
                    formatDigital(remainingMillis, alwaysShowHours = false)
                }
            }
        }
    }

    /**
     * Formats duration into detailed/compact human-readable string like "1h 30m" or "45m".
     */
     fun formatDetailed(millis: Long): String = formatHumanReadable(millis)

    /**
     * Formats duration into compact human-readable string like "1h 30m" or "45m".
     */
    fun formatHumanReadable(millis: Long): String {
        val totalMinutes = (millis.coerceAtLeast(0L) / (1000L * 60L))
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}
