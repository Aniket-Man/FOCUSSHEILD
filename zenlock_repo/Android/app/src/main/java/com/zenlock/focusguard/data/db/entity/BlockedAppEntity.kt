package com.zenlock.focusguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a blocked app in the database.
 * Stores the package name, display name, and whether it's currently active for blocking.
 */
@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis()
)
