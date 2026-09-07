package com.example.core.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import java.util.Calendar

object DeviceUsageStatsHelper {

    /**
     * Checks if PACKAGE_USAGE_STATS permission is granted by the user in system settings.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Queries total device screen on / foreground usage time for today (from 00:00:00 to now).
     */
    fun getTodayTotalScreenTimeMillis(context: Context): Long {
        if (!hasUsageStatsPermission(context)) return 0L
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        try {
            // 1. Try tracking exact screen on/off events (SCREEN_INTERACTIVE = 15, SCREEN_NON_INTERACTIVE = 16)
            val events = usageStatsManager.queryEvents(startOfDay, now)
            val event = android.app.usage.UsageEvents.Event()
            
            var totalScreenTime = 0L
            var lastInteractiveTime = 0L
            var isInteractive = false
            var hasInteractiveEvents = false
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = event.eventType
                
                if (type == 15) { // SCREEN_INTERACTIVE
                    if (!isInteractive) {
                        lastInteractiveTime = event.timeStamp
                        isInteractive = true
                        hasInteractiveEvents = true
                    }
                } else if (type == 16) { // SCREEN_NON_INTERACTIVE
                    if (isInteractive) {
                        totalScreenTime += (event.timeStamp - maxOf(lastInteractiveTime, startOfDay))
                        isInteractive = false
                        hasInteractiveEvents = true
                    } else if (!hasInteractiveEvents && event.timeStamp > startOfDay) {
                        totalScreenTime += (event.timeStamp - startOfDay)
                        hasInteractiveEvents = true
                    }
                }
            }
            if (isInteractive) {
                totalScreenTime += (now - maxOf(lastInteractiveTime, startOfDay))
            }
            
            if (hasInteractiveEvents && totalScreenTime > 0L) {
                return totalScreenTime
            }

            // 2. Fallback: If device doesn't report screen state, build a timeline of ACTIVITY_RESUMED (1) / ACTIVITY_PAUSED (2)
            val fallbackEvents = usageStatsManager.queryEvents(startOfDay, now)
            val activePackages = mutableMapOf<String, Long>()
            
            class Interval(val start: Long, val end: Long)
            val intervals = mutableListOf<Interval>()
            
            while (fallbackEvents.hasNextEvent()) {
                fallbackEvents.getNextEvent(event)
                val type = event.eventType
                val pkg = event.packageName
                
                if (type == 1) { // ACTIVITY_RESUMED
                    if (!activePackages.containsKey(pkg)) {
                        activePackages[pkg] = event.timeStamp
                    }
                } else if (type == 2 || type == 23 || type == 24) { // ACTIVITY_PAUSED / STOPPED
                    val startTime = activePackages.remove(pkg)
                    if (startTime != null) {
                        intervals.add(Interval(startTime, event.timeStamp))
                    }
                }
            }
            activePackages.forEach { (_, startTime) ->
                intervals.add(Interval(startTime, now))
            }
            
            if (intervals.isNotEmpty()) {
                intervals.sortBy { it.start }
                var mergedTotal = 0L
                var currentStart = maxOf(intervals[0].start, startOfDay)
                var currentEnd = intervals[0].end
                
                for (i in 1 until intervals.size) {
                    val interval = intervals[i]
                    val start = maxOf(interval.start, startOfDay)
                    val end = interval.end
                    
                    if (start <= currentEnd) {
                        currentEnd = maxOf(currentEnd, end)
                    } else {
                        mergedTotal += (currentEnd - currentStart)
                        currentStart = start
                        currentEnd = end
                    }
                }
                mergedTotal += (currentEnd - currentStart)
                if (mergedTotal > 0L) return mergedTotal
            }
        } catch (_: Exception) {}

        return 0L
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        return pkg.startsWith("com.android.systemui") ||
                pkg == "android" ||
                pkg.startsWith("com.google.android.inputmethod")
    }

    /**
     * Queries foreground usage time today (from 00:00:00 to now) for a specific app package.
     */
    fun getTodayAppUsageMillis(context: Context, packageName: String): Long {
        if (!hasUsageStatsPermission(context)) return 0L
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        try {
            // 1. Query aggregated usage stats for today
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
            val stat = aggregated[packageName]
            if (stat != null && stat.totalTimeInForeground > 0L) {
                return stat.totalTimeInForeground
            }

            // 2. Query interval daily usage stats as fallback
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            )
            val item = stats?.find { it.packageName == packageName }
            if (item != null && item.totalTimeInForeground > 0L) {
                return item.totalTimeInForeground
            }
        } catch (_: Exception) {
        }

        return 0L
    }

    /**
     * Queries foreground usage time today (from 00:00:00 to now) for all apps.
     */
    fun getTodayAllAppsUsageMillis(context: Context): Map<String, Long> {
        if (!hasUsageStatsPermission(context)) return emptyMap()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val result = mutableMapOf<String, Long>()
        try {
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
            if (!aggregated.isNullOrEmpty()) {
                aggregated.forEach { (pkg, stat) ->
                    if (stat.totalTimeInForeground > 0L) {
                        result[pkg] = stat.totalTimeInForeground
                    }
                }
                return result
            }

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            )
            if (!stats.isNullOrEmpty()) {
                stats.forEach { stat ->
                    if (stat.totalTimeInForeground > 0L) {
                        result[stat.packageName] = maxOf(result[stat.packageName] ?: 0L, stat.totalTimeInForeground)
                    }
                }
                return result
            }
        } catch (_: Exception) {
        }

        return result
    }

    /**
     * Formats milliseconds into human-readable duration (e.g., "3h 45m" or "42m").
     */
    fun formatDurationHoursMins(millis: Long): String {
        if (millis <= 0) return "0m"
        val totalMinutes = millis / (1000 * 60)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    /**
     * Opens system Usage Access Settings so user can enable device screen time tracking.
     */
    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Unable to open Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Opens the Digital Wellbeing settings or Usage Access page on device.
     */
    fun openDigitalWellbeingSettings(context: Context) {
        val intents = listOf(
            Intent().setClassName("com.google.android.apps.wellbeing", "com.google.android.apps.wellbeing.home.TopLevelSettingsActivity"),
            Intent("com.google.android.apps.wellbeing.action.SETTINGS"),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        openUsageAccessSettings(context)
    }
}
