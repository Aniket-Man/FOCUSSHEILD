package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class StudyActivityType {
    FOCUS_SESSION,
    YOUTUBE_STUDY,
    MANUAL_STUDY,
    POMODORO_FOCUS
}

enum class StudyActivitySource(val label: String) {
    TIMER("Focus Timer"),
    STOPWATCH("Stopwatch"),
    POMODORO("Pomodoro Focus"),
    YOUTUBE("YouTube Study"),
    MANUAL("Manual Log")
}

@Entity(
    tableName = "study_activities",
    indices = [
        Index("sessionId"),
        Index("startedAt"),
        Index("subject"),
        Index("source")
    ]
)
data class StudyActivityEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val activityType: StudyActivityType,
    val source: StudyActivitySource,
    val subject: String,
    val topic: String,
    val channelName: String? = null,
    val channelId: String? = null,
    val videoTitle: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val durationMillis: Long,
    val createdAt: Long = System.currentTimeMillis()
)
