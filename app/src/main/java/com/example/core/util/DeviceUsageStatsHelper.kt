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
     * Formats milliseconds for the Digital Wellbeing ring center (e.g., "9 hr, 34 min" or "42 min").
     */
    fun formatWellbeingRingDuration(millis: Long): String {
        if (millis <= 0L) return "0 min"
        if (millis < 60_000L) return "< 1 min"
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "$hours hr, $mins min"
            hours > 0 -> "$hours hr"
            else -> "$mins min"
        }
    }

    /**
     * Formats milliseconds for the Digital Wellbeing "App activity details" list rows
     * (e.g., "4 minutes", "1 minute", "Less than 1 minute", "1 hr, 20 min").
     */
    fun formatWellbeingAppRowDuration(millis: Long): String {
        if (millis <= 0L) return "0 minutes"
        if (millis < 60_000L) return "Less than 1 minute"
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "$hours hr, $mins min"
            hours > 0 -> "$hours hr"
            mins == 1L -> "1 minute"
            else -> "$mins minutes"
        }
    }

    /**
     * Returns true for core system apps (Settings, Play Store, Phone/Contacts) that show
     * the info (i) icon instead of an hourglass timer in Digital Wellbeing.
     */
    fun isSystemExemptFromTimer(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.android.settings" ||
            pkg == "com.android.vending" ||
            pkg == "com.google.android.dialer" ||
            pkg == "com.android.dialer" ||
            pkg == "com.samsung.android.dialer" ||
            pkg == "com.google.android.contacts" ||
            pkg == "com.android.contacts" ||
            pkg == "com.samsung.android.app.contacts"
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

    /**
     * Opens system Internal Storage settings page.
     */
    fun openStorageSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Intent(Settings.ACTION_MEMORY_CARD_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Builds a complete Digital Wellbeing snapshot for today:
     * - Per-app foreground time, launch count, category label
     * - Hourly screen-time distribution (24 hours)
     * - Device unlocks today
     * - Real device internal storage usage via StatFs
     */
    fun getTodayDigitalWellbeingSnapshot(
        context: Context,
        persistedUnlocks: Int = 0,
        persistedNotifications: Int = 0
    ): DigitalWellbeingSnapshot {
        val hasPerm = hasUsageStatsPermission(context)
        val (startOfDay, now) = getTodayBounds()
        val pm = context.packageManager

        // Query real internal storage stats via StatFs
        var storageTotal = 0L
        var storageFree = 0L
        try {
            val statFs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            storageTotal = statFs.totalBytes
            storageFree = statFs.availableBytes
        } catch (_: Exception) {}
        val storageUsed = (storageTotal - storageFree).coerceAtLeast(0L)

        if (!hasPerm) {
            return DigitalWellbeingSnapshot(
                hasUsagePermission = false,
                totalScreenTimeMillis = 0L,
                formattedTotalScreenTime = "0m",
                unlocksCount = persistedUnlocks,
                notificationsCount = persistedNotifications,
                totalAppLaunches = 0,
                apps = emptyList(),
                hourlyUsageMinutes = List(24) { 0 },
                weeklyDailyUsage = buildEmptyWeeklyUsageHistory(),
                storageUsedBytes = storageUsed,
                storageTotalBytes = storageTotal,
                storageFreeBytes = storageFree
            )
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val packageIntervals = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
        val packageLaunches = mutableMapOf<String, Int>()
        var keyguardHiddenCount = 0
        var notificationInterruptionCount = 0
        var lastForegroundPkg: String? = null

        if (usm != null) {
            try {
                val events = usm.queryEvents(startOfDay, now)
                val event = UsageEvents.Event()
                val foregroundStarts = mutableMapOf<String, Long>()

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val type = event.eventType

                    if (type == UsageEvents.Event.KEYGUARD_HIDDEN) {
                        keyguardHiddenCount++
                    }
                    // UsageEvents.Event.NOTIFICATION_INTERRUPTION == 12
                    if (type == 12) {
                        notificationInterruptionCount++
                    }

                    val pkg = event.packageName
                    if (pkg.isNullOrBlank() || isIgnoredSystemPackage(pkg, context.packageName)) continue

                    val key = "$pkg/${event.className ?: pkg}"
                    when (type) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            if (!foregroundStarts.containsKey(key)) {
                                foregroundStarts[key] = event.timeStamp
                            }
                            if (lastForegroundPkg != pkg) {
                                packageLaunches[pkg] = (packageLaunches[pkg] ?: 0) + 1
                                lastForegroundPkg = pkg
                            }
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED -> {
                            val start = foregroundStarts.remove(key)
                            if (start != null) {
                                val clampedStart = start.coerceAtLeast(startOfDay)
                                val clampedEnd = event.timeStamp.coerceAtMost(now)
                                if (clampedEnd > clampedStart) {
                                    packageIntervals.getOrPut(pkg) { mutableListOf() }.add(clampedStart to clampedEnd)
                                }
                            }
                        }
                    }
                }

                foregroundStarts.forEach { (key, start) ->
                    val pkg = key.substringBefore('/')
                    val clampedStart = start.coerceAtLeast(startOfDay)
                    if (now > clampedStart) {
                        packageIntervals.getOrPut(pkg) { mutableListOf() }.add(clampedStart to now)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getTodayDigitalWellbeingSnapshot events error: ${e.message}")
            }
        }

        val packageUsageMillis = mutableMapOf<String, Long>()
        val hourlyMillis = LongArray(24)
        val oneHourMillis = 3600_000L

        for ((pkg, intervals) in packageIntervals) {
            if (intervals.isEmpty()) continue
            intervals.sortBy { it.first }
            var mergedTotal = 0L
            var currentStart = intervals[0].first
            var currentEnd = intervals[0].second

            fun accumulateInterval(s: Long, e: Long) {
                mergedTotal += (e - s)
                // Distribute across hourly buckets
                var cursor = s
                while (cursor < e) {
                    val hourIndex = (((cursor - startOfDay) / oneHourMillis).toInt()).coerceIn(0, 23)
                    val bucketEnd = (startOfDay + (hourIndex + 1) * oneHourMillis).coerceAtMost(e)
                    val slice = (bucketEnd - cursor).coerceAtLeast(0L)
                    hourlyMillis[hourIndex] += slice
                    cursor = if (bucketEnd > cursor) bucketEnd else cursor + 1L
                }
            }

            for (i in 1 until intervals.size) {
                val (s, e) = intervals[i]
                if (s <= currentEnd) {
                    currentEnd = maxOf(currentEnd, e)
                } else {
                    accumulateInterval(currentStart, currentEnd)
                    currentStart = s
                    currentEnd = e
                }
            }
            accumulateInterval(currentStart, currentEnd)

            if (mergedTotal > 0L) {
                packageUsageMillis[pkg] = mergedTotal
            }
        }

        // Fallback: include aggregate stats for packages missed by raw events
        if (usm != null) {
            try {
                val aggregated = usm.queryAndAggregateUsageStats(startOfDay, now)
                aggregated?.forEach { (pkg, stat) ->
                    if (!isIgnoredSystemPackage(pkg, context.packageName) &&
                        !packageUsageMillis.containsKey(pkg) &&
                        stat.totalTimeInForeground > 0L
                    ) {
                        packageUsageMillis[pkg] = stat.totalTimeInForeground
                        if (!packageLaunches.containsKey(pkg)) {
                            packageLaunches[pkg] = 1
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Also include installed user launcher apps if the list has fewer than 8 apps so the user can see and set timers on their apps
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (!isIgnoredSystemPackage(pkg, context.packageName) && !packageUsageMillis.containsKey(pkg)) {
                    packageUsageMillis[pkg] = 0L
                }
            }
        } catch (_: Exception) {}

        val totalMillis = packageUsageMillis.values.sum().coerceAtMost((now - startOfDay).coerceAtLeast(0L))

        val appItems = packageUsageMillis.entries
            .mapNotNull { (pkg, millis) ->
                val appInfo = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (_: Exception) {
                    null
                }
                // Skip uninstalled packages with 0 usage
                if (appInfo == null && millis <= 0L) return@mapNotNull null

                val appLabel = try {
                    if (appInfo != null) pm.getApplicationLabel(appInfo).toString() else pkg.substringAfterLast('.')
                } catch (_: Exception) {
                    pkg.substringAfterLast('.')
                }

                val category = resolveAppCategoryLabel(pkg, appInfo)
                val launches = packageLaunches[pkg] ?: if (millis > 0L) 1 else 0

                WellbeingAppUsageItem(
                    packageName = pkg,
                    appName = appLabel,
                    usageMillis = millis,
                    formattedUsage = formatDurationHoursMins(millis),
                    launchCount = launches,
                    categoryLabel = category
                )
            }
            .sortedWith(compareByDescending<WellbeingAppUsageItem> { it.usageMillis }.thenBy { it.appName.lowercase() })

        val hourlyMinutes = hourlyMillis.map { (it / 60_000L).toInt().coerceIn(0, 60) }
        val totalLaunches = packageLaunches.values.sum()
        val effectiveUnlocks = maxOf(persistedUnlocks, keyguardHiddenCount)
        val effectiveNotifications = if (notificationInterruptionCount > 0) {
            notificationInterruptionCount + persistedNotifications
        } else {
            maxOf(persistedNotifications, totalLaunches)
        }
        val topAppToday = appItems.firstOrNull { it.usageMillis > 0L }?.appName
        val weeklyHistory = getWeeklyDailyUsageHistory(
            context = context,
            usm = usm,
            pm = pm,
            todayTotalMillis = totalMillis,
            todayTopAppName = topAppToday
        )

        return DigitalWellbeingSnapshot(
            hasUsagePermission = true,
            totalScreenTimeMillis = totalMillis,
            formattedTotalScreenTime = formatDurationHoursMins(totalMillis),
            unlocksCount = effectiveUnlocks,
            notificationsCount = effectiveNotifications,
            totalAppLaunches = totalLaunches,
            apps = appItems,
            hourlyUsageMinutes = hourlyMinutes,
            weeklyDailyUsage = weeklyHistory,
            storageUsedBytes = storageUsed,
            storageTotalBytes = storageTotal,
            storageFreeBytes = storageFree
        )
    }

    /**
     * Queries the past 7 days (6 days ago through today) of daily app usage history
     * so users can track their weekly screen time progress in a Vico bar chart.
     */
    private fun getWeeklyDailyUsageHistory(
        context: Context,
        usm: UsageStatsManager?,
        pm: android.content.pm.PackageManager,
        todayTotalMillis: Long,
        todayTopAppName: String?
    ): List<DailyUsageHistoryItem> {
        val dayShortFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
        val dateFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
        val maxDayMillis = 24L * 3600_000L
        val items = mutableListOf<DailyUsageHistoryItem>()

        for (daysAgo in 6 downTo 0) {
            val dayStartCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = dayStartCal.timeInMillis
            val dayEnd = if (daysAgo == 0) {
                System.currentTimeMillis()
            } else {
                dayStart + maxDayMillis - 1L
            }

            val dayShort = dayShortFormat.format(dayStartCal.time)
            val dateLabel = dateFormat.format(dayStartCal.time)

            if (daysAgo == 0) {
                val clampedToday = todayTotalMillis.coerceIn(0L, maxDayMillis)
                items.add(
                    DailyUsageHistoryItem(
                        dayOfWeekShort = dayShort,
                        dateFormatted = dateLabel,
                        usageMillis = clampedToday,
                        usageHours = (clampedToday.toFloat() / 3_600_000f).coerceAtLeast(0f),
                        formattedUsage = formatDurationHoursMins(clampedToday),
                        topAppName = todayTopAppName,
                        isToday = true
                    )
                )
            } else {
                var dayMillis = 0L
                var topPkg: String? = null
                var topPkgMillis = 0L

                if (usm != null) {
                    try {
                        // First try precise foreground events for that day window
                        val eventsMap = calculateAllAppsForegroundUsageFromEvents(context, dayStart, dayEnd)
                        if (eventsMap.isNotEmpty()) {
                            dayMillis = eventsMap.values.sum()
                            val maxEntry = eventsMap.maxByOrNull { it.value }
                            topPkg = maxEntry?.key
                            topPkgMillis = maxEntry?.value ?: 0L
                        } else {
                            // Fallback to INTERVAL_DAILY bucket stats
                            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
                            val perPkg = mutableMapOf<String, Long>()
                            stats?.forEach { stat ->
                                val pkg = stat.packageName ?: return@forEach
                                if (isIgnoredSystemPackage(pkg, context.packageName)) return@forEach
                                val fg = stat.totalTimeInForeground
                                if (fg > 0L && stat.lastTimeUsed in dayStart..dayEnd) {
                                    perPkg[pkg] = maxOf(perPkg[pkg] ?: 0L, fg)
                                }
                            }
                            dayMillis = perPkg.values.sum()
                            val maxEntry = perPkg.maxByOrNull { it.value }
                            topPkg = maxEntry?.key
                            topPkgMillis = maxEntry?.value ?: 0L
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Weekly usage query failed for daysAgo=$daysAgo: ${e.message}")
                    }
                }

                val clampedDay = dayMillis.coerceIn(0L, maxDayMillis)
                val resolvedTopApp = if (topPkg != null && topPkgMillis > 0L) {
                    try {
                        val info = pm.getApplicationInfo(topPkg, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        topPkg.substringAfterLast('.')
                    }
                } else {
                    null
                }

                items.add(
                    DailyUsageHistoryItem(
                        dayOfWeekShort = dayShort,
                        dateFormatted = dateLabel,
                        usageMillis = clampedDay,
                        usageHours = (clampedDay.toFloat() / 3_600_000f).coerceAtLeast(0f),
                        formattedUsage = formatDurationHoursMins(clampedDay),
                        topAppName = resolvedTopApp,
                        isToday = false
                    )
                )
            }
        }

        return items
    }

    private fun buildEmptyWeeklyUsageHistory(): List<DailyUsageHistoryItem> {
        val dayShortFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
        val dateFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
        return (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            DailyUsageHistoryItem(
                dayOfWeekShort = dayShortFormat.format(cal.time),
                dateFormatted = dateFormat.format(cal.time),
                usageMillis = 0L,
                usageHours = 0f,
                formattedUsage = "0m",
                topAppName = null,
                isToday = daysAgo == 0
            )
        }
    }

    private fun resolveAppCategoryLabel(pkg: String, appInfo: android.content.pm.ApplicationInfo?): String {
        val lower = pkg.lowercase()
        if (lower.contains("youtube") || lower.contains("netflix") || lower.contains("video") || lower.contains("hotstar") || lower.contains("prime")) {
            return "Video & Media"
        }
        if (lower.contains("instagram") || lower.contains("whatsapp") || lower.contains("facebook") || lower.contains("snapchat") || lower.contains("twitter") || lower.contains("telegram") || lower.contains("reddit")) {
            return "Social"
        }
        if (lower.contains("chrome") || lower.contains("browser") || lower.contains("opera") || lower.contains("brave") || lower.contains("firefox")) {
            return "Web Browser"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
            return when (appInfo.category) {
                android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Games"
                android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "Video & Media"
                android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Audio & Music"
                android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "News & Reading"
                android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Maps & Travel"
                else -> "Apps & Tools"
            }
        }
        return "Apps & Tools"
    }
}

data class DailyUsageHistoryItem(
    val dayOfWeekShort: String,
    val dateFormatted: String,
    val usageMillis: Long,
    val usageHours: Float,
    val formattedUsage: String,
    val topAppName: String? = null,
    val isToday: Boolean = false
)

data class WellbeingAppUsageItem(
    val packageName: String,
    val appName: String,
    val usageMillis: Long,
    val formattedUsage: String,
    val launchCount: Int,
    val categoryLabel: String
)

data class DigitalWellbeingSnapshot(
    val hasUsagePermission: Boolean,
    val totalScreenTimeMillis: Long,
    val formattedTotalScreenTime: String,
    val unlocksCount: Int,
    val notificationsCount: Int = 0,
    val totalAppLaunches: Int,
    val apps: List<WellbeingAppUsageItem>,
    val hourlyUsageMinutes: List<Int>,
    val weeklyDailyUsage: List<DailyUsageHistoryItem> = emptyList(),
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long
)

