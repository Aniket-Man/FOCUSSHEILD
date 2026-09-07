package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for scratch card rewards. One card is created per eligible completed
 * focus session; the user scratches it on the completion screen to reveal the reward.
 */
@Entity(
    tableName = "scratch_cards",
    indices = [Index("createdAt")]
)
data class ScratchCardEntity(
    @PrimaryKey val sessionId: String,     // One card per session
    val createdAt: Long,
    val rewardType: String,         // STREAK_FIRE | SESSION_MILESTONE | BADGE_PROGRESS | QUOTE
    val rewardEmoji: String,
    val rewardTitle: String,
    val rewardMessage: String,
    val studyMinutes: Int,
    val isRevealed: Boolean = false
)
