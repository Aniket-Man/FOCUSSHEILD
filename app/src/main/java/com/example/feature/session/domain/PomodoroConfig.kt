package com.example.feature.session.domain

/**
 * Configuration options for a Pomodoro session.
 * All durations in milliseconds.
 */
data class PomodoroConfig(
    val focusDurationMillis: Long = 25 * 60 * 1000L,
    val shortBreakDurationMillis: Long = 5 * 60 * 1000L,
    val longBreakDurationMillis: Long = 15 * 60 * 1000L,
    val totalCycles: Int = 4
) {
    val focusMinutes: Int get() = (focusDurationMillis / (60 * 1000L)).toInt()
    val shortBreakMinutes: Int get() = (shortBreakDurationMillis / (60 * 1000L)).toInt()
    val longBreakMinutes: Int get() = (longBreakDurationMillis / (60 * 1000L)).toInt()

    fun validate(): String? {
        if (focusDurationMillis <= 0) return "Focus duration must be greater than 0"
        if (shortBreakDurationMillis <= 0) return "Short break duration must be greater than 0"
        if (longBreakDurationMillis <= 0) return "Long break duration must be greater than 0"
        if (totalCycles <= 0) return "Total cycles must be at least 1"
        return null
    }

    companion object {
        fun fromMinutes(
            focusMinutes: Int = 25,
            shortBreakMinutes: Int = 5,
            longBreakMinutes: Int = 15,
            cycles: Int = 4
        ): PomodoroConfig {
            return PomodoroConfig(
                focusDurationMillis = focusMinutes.toLong() * 60 * 1000L,
                shortBreakDurationMillis = shortBreakMinutes.toLong() * 60 * 1000L,
                longBreakDurationMillis = longBreakMinutes.toLong() * 60 * 1000L,
                totalCycles = cycles
            )
        }
    }
}
