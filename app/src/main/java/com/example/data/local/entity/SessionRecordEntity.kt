package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.SessionMode

/**
 * Persistent Room Entity representing a completed, ended, or cancelled Focus Session.
 */
@Entity(tableName = "session_records")
data class SessionRecordEntity(
    @PrimaryKey val id: String,
    val mode: SessionMode,
    val title: String,
    val subject: String,
    val topic: String,
    val goal: String,
    val plannedDurationMillis: Long,
    val actualDurationMillis: Long, // pure study/focus time in milliseconds (excluding breaks)
    val startTime: Long,
    val endTime: Long,
    val completed: Boolean,
    val cancelled: Boolean,
    val pomodoroCycles: Int = 1,
    val completedPomodoroCycles: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
