package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity foundation for break intervals during study sessions.
 */
@Entity(tableName = "break_records")
data class BreakRecordEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val requestedDurationMillis: Long,
    val actualDurationMillis: Long,
    val startTime: Long,
    val endTime: Long,
    val completed: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
