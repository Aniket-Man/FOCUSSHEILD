package com.example.feature.session.domain

/**
 * State machine states for a Focus Session.
 */
enum class SessionState {
    IDLE,
    SETUP,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED
}
