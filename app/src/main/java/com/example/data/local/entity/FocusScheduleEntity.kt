package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing an automated recurring focus schedule (e.g. Mon-Fri 09:00 - 12:00).
 */
@Entity(tableName = "focus_schedules")
data class FocusScheduleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val daysOfWeek: String = "MON,TUE,WED,THU,FRI",
    val startTime: String = "09:00",
    val endTime: String = "12:00",
    val isEnabled: Boolean = true,
    val isAutoStartSession: Boolean = true,
    val mode: String = "TIMER",
    val subjectName: String = "General Study",
    val colorHex: String = "#4F46E5",
    /** Whether this schedule repeats on the selected days. False means the selected date is one-time. */
    val repeatEnabled: Boolean = true,
    /** Calendar date for one-time schedules; also retained as the anchor date for editing. */
    val scheduledDateMillis: Long = 0L,
    /** Default manual break duration offered by the session started from this schedule. */
    val breakMinutes: Int = 5,
    /** Notes shown/used as the focus goal for this scheduled session. */
    val description: String = "",
    /** Comma-separated package names selected specifically for this schedule. */
    val blockedAppPackages: String = "",
    /** Whether notifications from blocked apps are silenced while this schedule's session is active. */
    val blockNotifications: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
