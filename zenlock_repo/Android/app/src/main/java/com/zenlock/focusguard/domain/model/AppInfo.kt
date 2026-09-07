package com.zenlock.focusguard.domain.model

/**
 * Represents an installed app on the device for the app picker UI.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = false
)
