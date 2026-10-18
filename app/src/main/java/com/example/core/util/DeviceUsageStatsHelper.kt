package com.example.core.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.util.Calendar

/**
 * Structured result from a usage query.
 * Distinguishes between genuine zero usage, permission denied, and query failure.
 */
data class UsageResult(
    val status: UsageStatus,
    val usageMillis: Long = 0L,
    val method: String = ""
) {
    enum class UsageStatus {
        VALID,
        PERMISSION_DENIED,
        QUERY_FAILED,
        NO_DATA
    }
}

object DeviceUsageStatsHelper {

    private const val TAG = "DeviceUsageStats"

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

    // ================================================================
    // CANONICAL: UsageEvents-based foreground calculation
    // ================================================================

    /**
     * Calculates actual foreground usage for a single package by processing raw UsageEvents.
     * Tracks ACTIVITY_RESUMED / ACTIVITY_PAUSED / ACTIVITY_STOPPED and computes the
     * UNION of foreground intervals (no double-counting overlapping activities).
     */
    fun calculateForegroundUsageFromEvents(
        context: Context,
        packageName: String,
        startOfDay: Long,
        end: Long
    ): Long {
        if (!hasUsageStatsPermission(context)) return 0L
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L

        try {
            val events = usm.queryEvents(startOfDay, end)
            val event = UsageEvents.Event()

            // Track per-package foreground intervals
            val foregroundStarts = mutableMapOf<String, Long>() // key = "$packageName/$activity"
            val intervals = mutableListOf<Pair<Long, Long>>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (pkg != packageName) continue

                val key = "$pkg/${event.className ?: event.className ?: pkg}"
                val type = event.eventType

                when (type) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (!foregroundStarts.containsKey(key)) {
                            foregroundStarts[key] = event.timeStamp
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = foregroundStarts.remove(key)
                        if (start != null) {
                            val clampedStart = start.coerceAtLeast(startOfDay)
                            val clampedEnd = event.timeStamp.coerceAtMost(end)
                            if (clampedEnd > clampedStart) {
                                intervals.add(clampedStart to clampedEnd)
                            }
                        }
                    }
                }
            }

            // Close any still-active intervals at 'end'
            foregroundStarts.values.forEach { start ->
                val clampedStart = start.coerceAtLeast(startOfDay)
                val clampedEnd = end
                if (clampedEnd > clampedStart) {
                    intervals.add(clampedStart to clampedEnd)
                }
            }

            if (intervals.isEmpty()) return 0L

            // Sort by start time and merge overlapping intervals (UNION)
            intervals.sortBy { it.first }
            var mergedTotal = 0L
            var currentStart = intervals[0].first
            var currentEnd = intervals[0].second

            for (i in 1 until intervals.size) {
                val (start, end) = intervals[i]
                if (start <= currentEnd) {
                    // Overlapping — extend the current interval
                    currentEnd = maxOf(currentEnd, end)
                } else {
                    // Non-overlapping — add current interval to total
                    mergedTotal += (currentEnd - currentStart)
                    currentStart = start
                    currentEnd = end
                }
            }
            mergedTotal += (currentEnd - currentStart)

            return mergedTotal.coerceAtLeast(0L)
        } catch (e: Exception) {
            Log.e(TAG, "calculateForegroundUsageFromEvents failed for $packageName: ${e.message}")
            return 0L
        }
    }

    /**
     * Calculates actual foreground usage for ALL packages by processing raw UsageEvents.
     * Returns a map of packageName → foregroundMillis.
     */
    fun calculateAllAppsForegroundUsageFromEvents(
        context: Context,
        startOfDay: Long,
        end: Long
    ): Map<String, Long> {
        if (!hasUsageStatsPermission(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()

        val result = mutableMapOf<String, Long>()

        try {
            val events = usm.queryEvents(startOfDay, end)
            val event = UsageEvents.Event()

            // Track per-package-per-activity foreground starts
            val foregroundStarts = mutableMapOf<String, Long>() // key = "pkg/activity"
            // Track per-package intervals
            val packageIntervals = mutableMapOf<String, MutableList<Pair<Long, Long>>>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (pkg.isBlank() || isIgnoredSystemPackage(pkg, context.packageName)) continue

                val key = "$pkg/${event.className ?: event.className ?: pkg}"
                val type = event.eventType

                when (type) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (!foregroundStarts.containsKey(key)) {
                            foregroundStarts[key] = event.timeStamp
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = foregroundStarts.remove(key)
                        if (start != null) {
                            val clampedStart = start.coerceAtLeast(startOfDay)
                            val clampedEnd = event.timeStamp.coerceAtMost(end)
                            if (clampedEnd > clampedStart) {
                                packageIntervals.getOrPut(pkg) { mutableListOf() }.add(clampedStart to clampedEnd)
                            }
                        }
                    }
                }
            }

            // Close any still-active intervals at 'end'
            foregroundStarts.forEach { (key, start) ->
                val pkg = key.substringBefore('/')
                val clampedStart = start.coerceAtLeast(startOfDay)
                if (end > clampedStart) {
                    packageIntervals.getOrPut(pkg) { mutableListOf() }.add(clampedStart to end)
                }
            }

            // For each package, merge overlapping intervals and sum
            for ((pkg, intervals) in packageIntervals) {
                if (intervals.isEmpty()) continue
                intervals.sortBy { it.first }
                var mergedTotal = 0L
                var currentStart = intervals[0].first
                var currentEnd = intervals[0].second

                for (i in 1 until intervals.size) {
                    val (start, end) = intervals[i]
                    if (start <= currentEnd) {
                        currentEnd = maxOf(currentEnd, end)
                    } else {
                        mergedTotal += (currentEnd - currentStart)
                        currentStart = start
                        currentEnd = end
                    }
                }
                mergedTotal += (currentEnd - currentStart)

                if (mergedTotal > 0L) {
                    result[pkg] = mergedTotal
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "calculateAllAppsForegroundUsageFromEvents failed: ${e.message}")
        }

        return result
    }

    // ================================================================
    // Structured usage query methods (with UsageResult)
    // ================================================================

    /**
     * Returns structured usage result for a single package.
     */
    fun getTodayAppUsageResult(context: Context, packageName: String): UsageResult {
        if (!hasUsageStatsPermission(context)) {
            return UsageResult(UsageResult.UsageStatus.PERMISSION_DENIED)
        }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return UsageResult(UsageResult.UsageStatus.QUERY_FAILED)

        val (startOfDay, now) = getTodayBounds()

        // Primary: UsageEvents-based calculation (most accurate, matches Digital Wellbeing approach)
        val eventsUsage = calculateForegroundUsageFromEvents(context, packageName, startOfDay, now)
        if (eventsUsage > 0L) {
            return UsageResult(UsageResult.UsageStatus.VALID, eventsUsage, "UsageEvents")
        }

        // Fallback 1: queryAndAggregateUsageStats
        try {
            val aggregated = usm.queryAndAggregateUsageStats(startOfDay, now)
            val stat = aggregated[packageName]
            if (stat != null && stat.totalTimeInForeground > 0L) {
                Log.d(TAG, "Fallback to aggregate for $packageName: ${stat.totalTimeInForeground}ms")
                return UsageResult(UsageResult.UsageStatus.VALID, stat.totalTimeInForeground, "AggregateUsageStats")
            }
        } catch (_: Exception) {}

        // Fallback 2: queryUsageStats INTERVAL_DAILY
        try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            val item = stats?.find { it.packageName == packageName }
            if (item != null && item.totalTimeInForeground > 0L) {
                Log.d(TAG, "Fallback to daily for $packageName: ${item.totalTimeInForeground}ms")
                return UsageResult(UsageResult.UsageStatus.VALID, item.totalTimeInForeground, "DailyUsageStats")
            }
        } catch (_: Exception) {}

        return UsageResult(UsageResult.UsageStatus.NO_DATA, 0L, "None")
    }

    /**
     * Returns structured usage result for all apps.
     */
    fun getTodayAllAppsUsageResult(context: Context): Map<String, UsageResult> {
        if (!hasUsageStatsPermission(context)) return emptyMap()

        val (startOfDay, now) = getTodayBounds()

        // Primary: UsageEvents-based calculation
        val eventsUsage = calculateAllAppsForegroundUsageFromEvents(context, startOfDay, now)
        val result = mutableMapOf<String, UsageResult>()

        // All packages from events
        eventsUsage.forEach { (pkg, millis) ->
            result[pkg] = UsageResult(UsageResult.UsageStatus.VALID, millis, "UsageEvents")
        }

        // Add any packages from aggregate that aren't in events (events may miss some)
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return result
            val aggregated = usm.queryAndAggregateUsageStats(startOfDay, now)
            aggregated?.forEach { (pkg, stat) ->
                if (!isIgnoredSystemPackage(pkg, context.packageName) && !result.containsKey(pkg) && stat.totalTimeInForeground > 0L) {
                    result[pkg] = UsageResult(UsageResult.UsageStatus.VALID, stat.totalTimeInForeground, "AggregateUsageStats")
                }
            }
        } catch (_: Exception) {}

        return result
    }

    // ================================================================
    // Legacy API (kept for backward compatibility, now uses events-based calculation)
    // ================================================================

    /**
     * Queries foreground usage time today for a specific app package.
     * NOW uses UsageEvents-based calculation as primary source.
     */
    fun getTodayAppUsageMillis(context: Context, packageName: String): Long {
        return getTodayAppUsageResult(context, packageName).usageMillis
    }

    /**
     * Queries foreground usage time today for all apps.
     * NOW uses UsageEvents-based calculation as primary source.
     */
    fun getTodayAllAppsUsageMillis(context: Context): Map<String, Long> {
        return getTodayAllAppsUsageResult(context).mapValues { it.value.usageMillis }
    }

    // ================================================================
    // Diagnostics
    // ================================================================

    /**
     * Logs a diagnostic comparison of all three measurement methods for a specific package.
     * Call this from development/testing to understand discrepancies.
     */
    fun logDiagnosticComparison(context: Context, packageName: String) {
        if (!hasUsageStatsPermission(context)) {
            Log.w(TAG, "DIAGNOSTIC: Permission denied")
            return
        }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val (startOfDay, now) = getTodayBounds()

        // 1. queryAndAggregateUsageStats
        var aggregateUsage = 0L
        try {
            val aggregated = usm.queryAndAggregateUsageStats(startOfDay, now)
            aggregateUsage = aggregated[packageName]?.totalTimeInForeground ?: 0L
        } catch (_: Exception) {}

        // 2. queryUsageStats INTERVAL_DAILY
        var dailyUsage = 0L
        try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
            dailyUsage = stats?.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
        } catch (_: Exception) {}

        // 3. UsageEvents-based calculation
        val eventsUsage = calculateForegroundUsageFromEvents(context, packageName, startOfDay, now)

        // 4. Count event types
        var resumedCount = 0
        var pausedCount = 0
        var stoppedCount = 0
        var destroyedCount = 0
        try {
            val events = usm.queryEvents(startOfDay, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> resumedCount++
                    UsageEvents.Event.ACTIVITY_PAUSED -> pausedCount++
                    UsageEvents.Event.ACTIVITY_STOPPED -> stoppedCount++
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == 24) {
                    destroyedCount++
                }
            }
        } catch (_: Exception) {}

        Log.i(TAG, """
            ===== DIAGNOSTIC: $packageName =====
            Time range: $startOfDay → $now (${(now - startOfDay) / 60000}min window)
            Timezone: ${java.util.TimeZone.getDefault().id}
            AggregateUsageStats = ${aggregateUsage / 60000}min (${aggregateUsage}ms)
            DailyUsageStats     = ${dailyUsage / 60000}min (${dailyUsage}ms)
            UsageEventsUsage    = ${eventsUsage / 60000}min (${eventsUsage}ms)
            ---- Events Count ----
            ACTIVITY_RESUMED   = $resumedCount
            ACTIVITY_PAUSED    = $pausedCount
            ACTIVITY_STOPPED   = $stoppedCount
            ACTIVITY_DESTROYED = $destroyedCount
            ====================================
        """.trimIndent())
    }

    // ================================================================
    // Screen time (total across all apps)
    // ================================================================

    /**
     * Queries total device foreground app usage time for today, excluding system launchers,
     * lock-screen/SystemUI wakeups, input methods, and FocusShield's own overlays.
     */
    fun getTodayTotalScreenTimeMillis(context: Context): Long {
        if (!hasUsageStatsPermission(context)) return 0L
        val (startOfDay, now) = getTodayBounds()

        try {
            // Primary: Sum of merged foreground intervals across all non-ignored user apps.
            // This avoids counting lock-screen wakeups, launcher home screen idle time, or stuck
            // SCREEN_INTERACTIVE flags from before midnight.
            val foregroundAppsTotal = calculateAllAppsForegroundUsageFromEvents(context, startOfDay, now)
                .values
                .sum()
            if (foregroundAppsTotal > 0L) {
                return foregroundAppsTotal.coerceAtMost(now - startOfDay)
            }

            // Fallback: Aggregate usage stats across non-ignored packages
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0L
            val aggregated = usm.queryAndAggregateUsageStats(startOfDay, now)
            val fallbackSum = aggregated?.entries
                ?.filter { (pkg, _) -> !isIgnoredSystemPackage(pkg, context.packageName) }
                ?.sumOf { (_, stat) -> stat.totalTimeInForeground.coerceAtLeast(0L) }
                ?: 0L
            return fallbackSum.coerceAtMost(now - startOfDay)
        } catch (_: Exception) {}

        return 0L
    }

    // ================================================================
    // Utility
    // ================================================================

    private val IGNORED_LAUNCHER_AND_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.android.launcher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    private fun isIgnoredSystemPackage(pkg: String, selfPackageName: String = "com.example"): Boolean {
        if (pkg in IGNORED_LAUNCHER_AND_SYSTEM_PACKAGES) return true
        if (pkg == selfPackageName || pkg.startsWith("com.example") || pkg.startsWith("com.aistudio.focusshield")) return true
        return pkg.startsWith("com.android.systemui") ||
                pkg.startsWith("com.google.android.inputmethod") ||
                pkg.startsWith("com.samsung.android.honeyboard") ||
                pkg.contains(".launcher")
    }

    /**
     * Returns (startOfDay, now) as millisecond timestamps.
     */
    private fun getTodayBounds(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis to System.currentTimeMillis()
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
