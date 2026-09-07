package com.example.data.model

import androidx.compose.ui.graphics.Color

data class StudyPlanItem(
    val id: String,
    val subject: String,
    val topic: String,
    val targetTime: String,
    val accentColor: Color,
    val isCompleted: Boolean = false,
    val startTime: String = "08:00",
    val endTime: String = "10:00",
    val durationMinutes: Int = 120,
    val timeRangeFormatted: String = "08:00 – 10:00 • 2h",
    val status: String = "PLANNED",
    val notes: String = "",
    val hasOverlap: Boolean = false,
    val targetDate: Long = System.currentTimeMillis()
)

data class FocusMetric(
    val title: String,
    val value: String,
    val unit: String = "",
    val description: String,
    val accentColor: Color
)

data class QuickActionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconType: QuickActionType
)

enum class QuickActionType {
    START_POMODORO,
    STUDY_CHANNELS,
    BLOCKED_APPS,
    SESSION_HISTORY
}

