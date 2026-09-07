package com.example.feature.session.domain

/**
 * Configuration model for Strict Mode protection across FocusShield.
 * Covers Focus Sessions, App Limits, Anti-Cheating, and Content Blocking.
 */
data class StrictModeConfig(
    val enabled: Boolean = false,
    val blockSelectedApps: Boolean = true,
    val requireConfirmOnPause: Boolean = true,
    val requireConfirmOnEnd: Boolean = true,
    val requireConfirmOnBreak: Boolean = true,
    val strongerExitProtection: Boolean = true,
    val lockAppLimits: Boolean = true,
    val disableEmergencyBypass: Boolean = false,
    val blockUninstallTamper: Boolean = true,
    val blockSplitScreen: Boolean = true,
    val blockFloatingWindow: Boolean = true,
    val blockAdultAndDistractingWebsites: Boolean = true,
    val blockShortsAndReels: Boolean = true
)

