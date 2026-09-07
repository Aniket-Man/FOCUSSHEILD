package com.example.feature.session.notification

/**
 * Data payload passed when launching the app directly from a study plan notification
 * to automatically navigate to the Session setup / active session page.
 */
data class PlanLaunchPayload(
    val planId: String? = null,
    val subject: String,
    val topic: String,
    val durationMinutes: Int,
    val startTime: String? = null,
    val endTime: String? = null,
    val notes: String = "",
    val autoStart: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
