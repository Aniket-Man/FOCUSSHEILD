package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a topic within a study subject.
 */
@Entity(tableName = "study_topics")
data class TopicEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
