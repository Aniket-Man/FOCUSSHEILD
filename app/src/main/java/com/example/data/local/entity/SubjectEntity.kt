package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a study subject (e.g. Physics, Chemistry, Mathematics, Biology).
 */
@Entity(tableName = "study_subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String = "#7C3AED",
    val iconIdentifier: String = "default",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
