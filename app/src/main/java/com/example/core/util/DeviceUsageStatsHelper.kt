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
            // 1. Query aggregated usage stats
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfDay, now)
            if (!aggregated.isNullOrEmpty()) {
                val totalTime = aggregated.values
                    .filter { it.totalTimeInForeground > 0 && !isIgnoredSystemPackage(it.packageName) }
                    .sumOf { it.totalTimeInForeground }
                if (totalTime > 0) return totalTime
            }

            // 2. Query interval daily usage stats
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            )
            if (!stats.isNullOrEmpty()) {
                val totalTime = stats
                    .filter { it.totalTimeInForeground > 0 && !isIgnoredSystemPackage(it.packageName) }
                    .sumOf { it.totalTimeInForeground }
                if (totalTime > 0) return totalTime
            }
        } catch (_: Exception) {
        }

        return 0L
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        return pkg.startsWith("com.android.systemui") ||
                pkg == "android" ||
                pkg.startsWith("com.google.android.inputmethod")
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
