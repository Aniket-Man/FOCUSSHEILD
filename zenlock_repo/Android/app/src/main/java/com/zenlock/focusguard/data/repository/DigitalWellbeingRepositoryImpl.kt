package com.zenlock.focusguard.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.zenlock.focusguard.domain.model.AppUsageInfo
import com.zenlock.focusguard.domain.model.DailyDigitalReport
import com.zenlock.focusguard.domain.model.DailyUsageSummary
import com.zenlock.focusguard.domain.repository.BlockedAppRepository
import com.zenlock.focusguard.domain.repository.DigitalWellbeingRepository
import com.zenlock.focusguard.util.TimeUtils
import java.util.Calendar
import java.util.Locale

class DigitalWellbeingRepositoryImpl(
    private val context: Context,
    private val blockedAppRepository: BlockedAppRepository
) : DigitalWellbeingRepository {

    companion object {
        private const val TAG = "Zenlock[Wellbeing]"
    }

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager = context.packageManager

    override fun getForegroundPackage(): String? {
        val manager = usageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 // Look back 10 seconds

        val events = manager.queryEvents(startTime, endTime) ?: return null
        var lastForegroundApp: String? = null
        var lastEventTime = 0L

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= lastEventTime) {
                    lastEventTime = event.timeStamp
                    lastForegroundApp = event.packageName
                }
            }
        }
        return lastForegroundApp
    }

    override suspend fun getDailyReport(dayStartMs: Long): DailyDigitalReport {
        val manager = usageStatsManager ?: return DailyDigitalReport(0, 0, 0, emptyList(), null, null, null)
        
        val cal = Calendar.getInstance().apply {
            timeInMillis = dayStartMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        val blockedPackages = blockedAppRepository.getActiveBlockedAppsList()
            .map { it.packageName }.toSet()

        val usageStatsList = manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()

        // Also query events to get precise launch counts (MOVE_TO_FOREGROUND events)
        val launchCounts = mutableMapOf<String, Int>()
        val events = manager.queryEvents(startTime, endTime)
        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    val pkg = event.packageName
                    launchCounts[pkg] = (launchCounts[pkg] ?: 0) + 1
                }
            }
        }

        val appUsageList = mutableListOf<AppUsageInfo>()
        var totalScreenTimeMs = 0L
        var totalDistractionTimeMs = 0L

        // Consolidate usage stats per package
        val packageTimeMap = mutableMapOf<String, Long>()
        for (stats in usageStatsList) {
            if (stats.totalTimeInForeground > 0) {
                val current = packageTimeMap[stats.packageName] ?: 0L
                packageTimeMap[stats.packageName] = maxOf(current, stats.totalTimeInForeground)
            }
        }

        for ((pkg, timeMs) in packageTimeMap) {
            if (pkg == "com.zenlock.focusguard" || isSystemAppOrLauncher(pkg)) continue

            val isDistraction = blockedPackages.contains(pkg)
            val appName = getAppName(pkg)
            val launches = launchCounts[pkg] ?: 0

            totalScreenTimeMs += timeMs
            if (isDistraction) {
                totalDistractionTimeMs += timeMs
            }

            appUsageList.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    totalTimeInForegroundMs = timeMs,
                    launchCount = launches,
                    isDistraction = isDistraction
                )
            )
        }

        // Sort by longest usage time
        val sortedAppList = appUsageList.sortedByDescending { it.totalTimeInForegroundMs }

        val productivityScore = if (totalScreenTimeMs > 0) {
            val productiveTime = (totalScreenTimeMs - totalDistractionTimeMs).coerceAtLeast(0)
            ((productiveTime.toDouble() / totalScreenTimeMs.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else 100

        // Generate factual summary texts
        val mostUsed = sortedAppList.firstOrNull()
        val mostUsedSummary = if (mostUsed != null && mostUsed.totalTimeInForegroundMs > 0) {
            "Your most-used app today was ${mostUsed.appName} at ${TimeUtils.formatDuration(mostUsed.totalTimeInForegroundMs / 1000)}."
        } else null

        val topLaunched = sortedAppList.maxByOrNull { it.launchCount }
        val launchSummary = if (topLaunched != null && topLaunched.launchCount > 0) {
            "You launched ${topLaunched.appName} ${topLaunched.launchCount} times today."
        } else null

        val prodSummary = if (totalScreenTimeMs > 0) {
            "You spent $productivityScore% of your tracked screen time on productive apps."
        } else null

        return DailyDigitalReport(
            totalScreenTimeMs = totalScreenTimeMs,
            distractionTimeMs = totalDistractionTimeMs,
            productivityPercentage = productivityScore,
            topAppUsage = sortedAppList,
            mostUsedAppSummary = mostUsedSummary,
            launchCountSummary = launchSummary,
            productivitySummary = prodSummary
        )
    }

    override suspend fun getWeeklyReport(): List<DailyUsageSummary> {
        val manager = usageStatsManager ?: return emptyList()

        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val blockedPackages = blockedAppRepository.getActiveBlockedAppsList()
            .map { it.packageName }.toSet()

        val weeklyList = mutableListOf<DailyUsageSummary>()
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        for (i in 0..6) {
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + (24 * 3600 * 1000L)

            val statsList = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                dayStart,
                dayEnd
            ) ?: emptyList()

            var dayTotalMs = 0L
            var dayDistractionMs = 0L

            for (stats in statsList) {
                val pkg = stats.packageName
                if (pkg == "com.zenlock.focusguard" || isSystemAppOrLauncher(pkg)) continue
                if (stats.totalTimeInForeground > 0) {
                    dayTotalMs += stats.totalTimeInForeground
                    if (blockedPackages.contains(pkg)) {
                        dayDistractionMs += stats.totalTimeInForeground
                    }
                }
            }

            weeklyList.add(
                DailyUsageSummary(
                    dayName = dayNames[i],
                    dateTimestamp = dayStart,
                    totalScreenTimeMs = dayTotalMs,
                    distractionTimeMs = dayDistractionMs
                )
            )

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return weeklyList
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    private fun isSystemAppOrLauncher(packageName: String): Boolean {
        if (packageName == "com.android.systemui" || packageName == "com.google.android.apps.nexuslauncher") {
            return true
        }
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent == null
        } catch (e: Exception) {
            true
        }
    }
}
