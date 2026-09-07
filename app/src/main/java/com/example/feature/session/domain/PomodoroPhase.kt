package com.example.feature.session.domain

/**
 * Phase of a Pomodoro interval cycle.
 */
enum class PomodoroPhase(val label: String) {
    FOCUS("Focus"),
    SHORT_BREAK("Short Break"),
    LONG_BREAK("Long Break")
}
