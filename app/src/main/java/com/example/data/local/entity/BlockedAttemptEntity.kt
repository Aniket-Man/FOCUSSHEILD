package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity recording an attempt to open a blocked distraction app during an active Focus Session.
 */
@Entity(tableName = "blocked_attempts")
data class BlockedAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appName: String,
    val sessionId: String? = null
)
