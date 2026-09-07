package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a scheduled study plan item for Today's Plan.
 */
@Entity(tableName = "study_plans")
data class StudyPlanEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val subjectName: String,
    val topicName: String,
    val plannedDurationMinutes: Int = 60,
    val targetDate: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val colorHex: String = "#7C3AED",
    val createdAt: Long = System.currentTimeMillis(),
    val dateString: String = "",
    val startTime: String = "08:00",
    val endTime: String = "09:00",
    val startMinutes: Int = 480,
    val endMinutes: Int = 540,
    val status: String = "PLANNED",
    val notes: String = ""
)

