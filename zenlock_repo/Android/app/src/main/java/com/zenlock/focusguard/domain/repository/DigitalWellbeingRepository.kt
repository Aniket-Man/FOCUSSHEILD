package com.zenlock.focusguard.domain.repository

import com.zenlock.focusguard.domain.model.AppUsageInfo
import com.zenlock.focusguard.domain.model.DailyDigitalReport
import com.zenlock.focusguard.domain.model.DailyUsageSummary

interface DigitalWellbeingRepository {
    /**
     * Fetch daily digital report for the current day or specified start timestamp.
     */
    suspend fun getDailyReport(dayStartMs: Long = System.currentTimeMillis()): DailyDigitalReport

    /**
     * Fetch weekly usage breakdown for the past 7 days.
     */
    suspend fun getWeeklyReport(): List<DailyUsageSummary>

    /**
     * Detect current foreground package using UsageStatsManager.
     */
    fun getForegroundPackage(): String?
}
