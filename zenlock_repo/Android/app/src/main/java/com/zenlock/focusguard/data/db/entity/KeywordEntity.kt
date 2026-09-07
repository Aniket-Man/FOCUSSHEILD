package com.zenlock.focusguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a keyword rule for YouTube content filtering.
 * Keywords can be either "allow" or "block" type.
 */
@Entity(tableName = "keywords")
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val type: String, // "allow" or "block"
    val isActive: Boolean = true
)
