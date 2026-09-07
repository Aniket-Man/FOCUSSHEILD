package com.example.feature.youtube.domain

/**
 * Immutable configuration for YouTube Study Mode during a Focus Session.
 */
data class YouTubeStudyModeConfig(
    val isStudyModeEnabled: Boolean = true,
    val blockShorts: Boolean = true,
    val blockUnknownContent: Boolean = true,
    val approvedChannelIds: Set<String> = emptySet(),
    val approvedChannelNames: Set<String> = emptySet()
)
