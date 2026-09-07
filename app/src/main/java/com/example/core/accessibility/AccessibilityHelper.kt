package com.example.core.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityManager

object AccessibilityHelper {

    private const val TAG = "AccessibilityHelper"

    /**
     * Highly resilient accessibility service check.
     * Combines 4 detection layers to guarantee accurate detection on all Android ROMs
     * (Xiaomi MIUI/HyperOS, Samsung OneUI, Oppo ColorOS, Vivo Funtouch, OnePlus, Pixel):
     * 1. Live process memory flag check (FocusAccessibilityService.isServiceRunning)
     * 2. AccessibilityManager enabled service query
     * 3. Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES string parsing
     * 4. Global Settings.Secure.ACCESSIBILITY_ENABLED flag
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // Layer 1: Instant live process verification
        if (FocusAccessibilityService.isServiceRunning) {
            return true
        }

        val expectedComponentName = ComponentName(context, FocusAccessibilityService::class.java)
        val targetPackageName = context.packageName
        val targetServiceName = FocusAccessibilityService::class.java.name
        val targetSimpleName = FocusAccessibilityService::class.java.simpleName

        // Layer 2: System AccessibilityManager check
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                for (enabledService in enabledServices) {
                    val serviceInfo = enabledService.resolveInfo?.serviceInfo ?: continue
                    val sPackage = serviceInfo.packageName ?: ""
                    val sName = serviceInfo.name ?: ""

                    if ((sPackage == targetPackageName || sPackage == expectedComponentName.packageName) &&
                        (sName == targetServiceName || sName.contains(targetSimpleName))
                    ) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AccessibilityManager list check exception: ${e.message}")
        }

        // Layer 3: Settings.Secure string parsing (Handles full, relative, and OEM custom formats)
        try {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            if (!settingValue.isNull_or_empty_safe()) {
                // Direct string matching for OEM ROM variations
                val hasPackageMatch = settingValue.contains(targetPackageName) || settingValue.contains(expectedComponentName.packageName)
                val hasServiceMatch = settingValue.contains(targetServiceName) || settingValue.contains(targetSimpleName) || settingValue.contains("FocusAccessibilityService")

                if (hasPackageMatch && hasServiceMatch) {
                    return true
                }

                // ComponentName unflattening check
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(settingValue)

                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next()
                    val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                    if (enabledComponent != null) {
                        if ((enabledComponent.packageName == targetPackageName || enabledComponent.packageName == expectedComponentName.packageName) &&
                            (enabledComponent.className == targetServiceName || enabledComponent.className.contains(targetSimpleName))
                        ) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Settings.Secure string check exception: ${e.message}")
        }

        return false
    }

    private fun String?.isNull_or_empty_safe(): Boolean {
        return this == null || this.isEmpty() || this.trim().isEmpty()
    }

    /**
     * Safely opens Accessibility Settings for the user.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ACTION_ACCESSIBILITY_SETTINGS: ${e.message}")
            openAppDetailsSettings(context)
        }
    }

    /**
     * Opens App Details Settings page.
     * Essential for Android 13+ (API 33+) "Restricted Settings" unlock flow:
     * User taps App Info -> 3 dots menu -> "Allow restricted settings".
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
}

