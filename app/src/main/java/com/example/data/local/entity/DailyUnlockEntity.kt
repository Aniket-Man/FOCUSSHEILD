package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Room Entity tracking daily phone unlock counts (e.g. "2026-09-04").
 * Each row accumulates how many times the user unlocked the phone that day,
 * powering the unlock analytics and the home screen unlock widget.
 */
@Entity(
    tableName = "daily_unlocks",
    primaryKeys = ["dateString"],
    indices = [Index("dateString")]
)
data class DailyUnlockEntity(
    val dateString: String,           // Format: "yyyy-MM-dd"
    val unlockCount: Int = 0,         // Total unlocks this day
    val firstUnlockAt: Long = 0L,     // Epoch millis of the first unlock of the day
    val lastUnlockAt: Long = 0L       // Epoch millis of the most recent unlock
)
