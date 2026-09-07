package com.zenlock.focusguard.domain.model

/**
 * Data class representing aggregated daily app usage.
 */
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMs: Long,
    val launchCount: Int = 0,
    val isDistraction: Boolean = false
)

/**
 * Data class representing a daily digital wellbeing report summary.
 */
data class DailyDigitalReport(
    val totalScreenTimeMs: Long,
    val distractionTimeMs: Long,
    val productivityPercentage: Int,
    val topAppUsage: List<AppUsageInfo>,
    val mostUsedAppSummary: String?,
    val launchCountSummary: String?,
    val productivitySummary: String?
)

/**
 * Data class representing a single day's usage for weekly charts.
 */
data class DailyUsageSummary(
    val dayName: String,
    val dateTimestamp: Long,
    val totalScreenTimeMs: Long,
    val distractionTimeMs: Long
)
