package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A keyword rule used by the YouTube content block engine.
 * type is either "allow" (educational content signal) or "block"
 * (distracting content signal).
 */
@Entity(tableName = "keywords")
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val type: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
