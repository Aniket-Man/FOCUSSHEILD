package com.example.feature.notificationblocker.domain

enum class NotificationBlockMode(val title: String, val description: String) {
    SESSION_ONLY(
        title = "During Focus Sessions Only",
        description = "Selected apps stay silent only while a focus session is active. When you are not in a session, notifications arrive normally."
    ),
    ALWAYS_SILENT(
        title = "Always Silent (24/7)",
        description = "Selected apps stay silent at all times, both during focus sessions and regular phone use."
    ),
    SMART_HYBRID(
        title = "Smart Session Sync",
        description = "Automatically silences notifications for any app that is blocked in the current focus session or app limit."
    )
}

data class SilencedNotificationRecord(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val senderName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val wasDuringSession: Boolean = false,
    val sessionId: String? = null
)
