package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Room Entity tracking actual accumulated foreground usage, emergency usage, and bypass state
 * for a limited application on a specific calendar date (e.g. "2026-08-21").
 */
@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["packageName", "dateString"],
    indices = [
        Index("dateString"),
        Index("packageName")
    ]
)
data class DailyAppUsageEntity(
    val packageName: String,
    val dateString: String, // Format: "yyyy-MM-dd"
    val appName: String,
    val usedMillis: Long = 0L,              // Accumulated normal foreground time today
    val emergencyUsedMillis: Long = 0L,     // Accumulated emergency usage time today
    val emergencyUsesCount: Int = 0,        // Number of 5-min emergency sessions consumed (0..3)
    val isBypassedForToday: Boolean = false,// If true, App Limit block is lifted until midnight reset
    val lastActiveTimestamp: Long = 0L
)
