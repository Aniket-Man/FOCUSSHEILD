package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a website domain or URL pattern configured for browser site blocking.
 */
@Entity(tableName = "blocked_websites")
data class BlockedWebsiteEntity(
    @PrimaryKey val domain: String,
    val isEnabled: Boolean = true,
    val category: String = "MANUAL", // "MANUAL" or "ADULT"
    val createdAt: Long = System.currentTimeMillis()
)
