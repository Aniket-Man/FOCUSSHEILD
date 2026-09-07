package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a daily time limit configuration for an installed application.
 */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int, // Total daily allowance in minutes (e.g. 5, 15, 60)
    val isEnabled: Boolean = true,
    val isStrictOverride: Boolean = false, // Level-3 Strict Mode for app limit (no turn-off, no bypass, no emergency after limit)
    val showRemindersBeforeLimit: Boolean = true, // Show reminders before limit is over
    val emergencyUsesAllowed: Int = 1, // Number of emergency uses granted in normal mode
    val streakDays: Int = 0, // Discipline streak tracking how many consecutive days the user stayed disciplined
    val lastStreakDate: String = "", // e.g. "2026-08-24"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

