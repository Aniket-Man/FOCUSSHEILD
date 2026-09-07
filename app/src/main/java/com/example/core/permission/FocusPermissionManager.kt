package com.example.core.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.core.accessibility.AccessibilityHelper

/**
 * Structured snapshot of FocusShield protection permissions.
 */
data class ProtectionPermissionStatus(
    val isOverlayGranted: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isUsageAccessGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isNotificationListenerGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val areMandatoryGranted: Boolean = false
)

/**
 * Central coordinator and manager for FocusShield system permissions.
 * Checks, requests, and monitors:
 * 1. "Display over other apps" (SYSTEM_ALERT_WINDOW) for overlay blocking shield
 * 2. "Accessibility Service" (FocusAccessibilityService) for foreground app & YouTube state detection
 * 3. "Notifications" (POST_NOTIFICATIONS on Android 13+) for study timers & cycle alerts
 * 4. "Battery Optimization Exemption" (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) to run in background without being killed when swiped from recents
 */
object FocusPermissionManager {

    /**
     * Checks if battery optimization is ignored (unrestricted background execution allowed).
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    /**
     * Directly requests the user to allow ignoring battery optimizations for FocusShield,
     * ensuring that background timers and blockers continue running even when swiped away from recent tasks.
     */
    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to battery optimization settings list
                openBatteryOptimizationSettings(context)
            }
        }
    }

    /**
     * Opens Android System Battery Optimization / App details settings.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppDetailsSettings(context)
            }
        } else {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Checks if Display over other apps (Overlay) permission is granted.
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Checks if FocusAccessibilityService is actively enabled in Android Accessibility Settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return AccessibilityHelper.isAccessibilityServiceEnabled(context)
    }

    /**
     * Checks if notification permission is granted (applicable on Android 13+ / API 33).
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Determines whether mandatory permissions required for active app blocking are granted.
     * When requiresBlocking is false, no permissions are mandatory to run a simple local timer.
     */
    fun areMandatoryPermissionsGranted(context: Context, requiresBlocking: Boolean = true): Boolean {
        if (!requiresBlocking) return true
        return isOverlayPermissionGranted(context) && isAccessibilityServiceEnabled(context)
    }

    /**
     * Checks if Usage Access permission is granted.
     */
    fun isUsageAccessGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if Notification Listener Service permission is granted.
     */
    fun isNotificationListenerGranted(context: Context): Boolean {
        return com.example.core.notification.FocusNotificationListenerService.isNotificationListenerEnabled(context)
    }

    /**
     * Opens Android System Settings for Notification Listener Access.
     */
    fun openNotificationListenerSettings(context: Context) {
        com.example.core.notification.FocusNotificationListenerService.openNotificationListenerSettings(context)
    }

    /**
     * Returns a consolidated real-time status object.
     */
    fun getPermissionStatus(context: Context, requiresBlocking: Boolean = true): ProtectionPermissionStatus {
        val overlay = isOverlayPermissionGranted(context)
        val accessibility = isAccessibilityServiceEnabled(context)
        val usageAccess = isUsageAccessGranted(context)
        val notifications = isNotificationPermissionGranted(context)
        val notificationListener = isNotificationListenerGranted(context)
        val batteryIgnored = isBatteryOptimizationIgnored(context)
        val mandatory = if (requiresBlocking) (overlay && accessibility) else true

        return ProtectionPermissionStatus(
            isOverlayGranted = overlay,
            isAccessibilityEnabled = accessibility,
            isUsageAccessGranted = usageAccess,
            isNotificationGranted = notifications,
            isNotificationListenerGranted = notificationListener,
            isBatteryOptimizationIgnored = batteryIgnored,
            areMandatoryGranted = mandatory
        )
    }

    /**
     * Opens Android System Settings for Usage Access.
     */
    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Opens Android System Settings for "Display over other apps".
     */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general manage overlay settings
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    // Fallback to application details
                    openAppDetailsSettings(context)
                }
            }
        }
    }

    /**
     * Opens Android System Settings for Accessibility Services.
     */
    fun openAccessibilitySettings(context: Context) {
        AccessibilityHelper.openAccessibilitySettings(context)
    }

    /**
     * Opens Android System Settings for Notifications.
     */
    fun openNotificationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppDetailsSettings(context)
            }
        } else {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Opens App Details Settings as a safe fallback.
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }
}
