package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity recording individual temporary usage sessions inside a limited app.
 */
@Entity(
    tableName = "app_limit_sessions",
    indices = [
        Index("packageName"),
        Index("dateString"),
        Index("startedAt")
    ]
)
data class AppLimitSessionEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val dateString: String,
    val startedAt: Long,
    val endedAt: Long,
    val selectedDurationMillis: Long,
    val actualUsedMillis: Long,
    val isEmergency: Boolean = false,
    val endReason: String // "USER_LEFT_APP", "TIMER_EXPIRED", "DAILY_LIMIT_REACHED", "SESSION_INTERRUPTED", "EMERGENCY_STARTED", "APP_BLOCKED", "STRICT_LIMIT_REACHED"
)
