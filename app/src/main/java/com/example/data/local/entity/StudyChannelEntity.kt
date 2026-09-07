package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity foundation for approved educational study channels.
 */
@Entity(tableName = "study_channels")
data class StudyChannelEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val channelName: String,
    val channelUrl: String,
    val thumbnailUrl: String = "",
    val isApproved: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
