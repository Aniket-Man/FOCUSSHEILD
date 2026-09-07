package com.zenlock.focusguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a completed or ongoing focus session.
 * Tracks duration, type, and completion status for statistics.
 */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val plannedDurationMinutes: Int,
    val actualDurationSeconds: Long = 0,
    val sessionType: String = "focus", // "focus" or "break"
    val isCompleted: Boolean = false,
    val blockedAttempts: Int = 0 // Number of times user tried to open blocked apps
)
