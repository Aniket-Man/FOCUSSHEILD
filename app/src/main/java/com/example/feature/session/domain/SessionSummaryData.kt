package com.example.feature.session.domain

import com.example.data.model.SessionMode
import java.io.Serializable

/**
 * Encapsulates the complete metrics, configurations, and achievements of an ended focus session
 * to be displayed on the post-session summary screen before saving or discarding.
 */
data class SessionSummaryData(
    val sessionId: String,
    val mode: SessionMode,
    val subject: String,
    val topic: String,
    val goal: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedDurationMillis: Long,
    val pureStudyDurationMillis: Long,
    val totalBreakDurationMillis: Long,
    val isCompleted: Boolean,
    val isCancelled: Boolean,
    val isStrictModeEnabled: Boolean,
    val isAppBlockingEnabled: Boolean,
    val isStudyChannelsEnabled: Boolean,
    val isYouTubeStudyModeEnabled: Boolean = false,
    val isBrowserStudyModeEnabled: Boolean,
    val blockedAppsCount: Int,
    val distractionsBlockedCount: Int = 0,
    val completedPomodoroCycles: Int = 0,
    val totalPomodoroCycles: Int = 1,
    val lapsCount: Int = 0,
    val rawSession: FocusSession? = null
) : Serializable
