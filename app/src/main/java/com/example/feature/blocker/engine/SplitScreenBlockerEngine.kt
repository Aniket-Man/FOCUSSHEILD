package com.example.feature.blocker.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.example.core.accessibility.ProtectionDecision
import com.example.feature.blocker.FocusBlockerManager

data class SplitScreenBlockResult(
    val isBlocked: Boolean,
    val reason: String = "",
    val culpritPackage: String = "com.android.systemui.splitscreen"
)

/**
 * High-performance engine for detecting and intercepting Split-Screen / Multi-Window multitasking.
 * Analyzes display partitioning, window topologies, OEM split-screen systems, and multi-window flags.
 */
class SplitScreenBlockerEngine private constructor() {

    private val tag = "SplitScreenBlockerEngine"

    private var lastBlockTimestamp: Long = 0L
    private var blockCount: Int = 0

    /**
     * OEM multi-window package identifiers for split-screen docks and dividers.
     */
    private val oemSplitScreenPackages = setOf(
        // Samsung Multi-Window
        "com.sec.android.app.splitscreen",
        "com.samsung.android.app.splitscreen",
        "com.samsung.android.multiwindow",
        "com.samsung.android.splitwindow",
        "com.samsung.android.wallpaperservice",
        // Xiaomi / MIUI
        "com.miui.freeform",
        "com.miui.split",
        "com.miui.splitscreen",
        "com.xiaomi.split",
        // Huawei / EMUI
        "com.huawei.android.launcher",
        "com.huawei.systemui",
        // OnePlus / OxygenOS
        "com.oneplus.split",
        "com.oneplus.freeform",
        // Oppo / ColorOS
        "com.coloros.split",
        "com.oppo.split",
        // Vivo / FuntouchOS
        "com.vivo.split",
        "com.vivo.freeform",
        // Android Generic
        "com.android.systemui",
        "com.android.systemui.splitscreen",
        "com.android.systemui.divider"
    )

    /**
     * OEM floating/split-screen overlay window packages to ignore when evaluating.
     */
    private val oemOverlayPackages = setOf(
        "com.samsung.android.app.tips",
        "com.samsung.android.spay",
        "com.samsung.android.bixby.agent",
        "com.samsung.android.bixby.service",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.nexuslauncher"
    )

    /**
     * Evaluates all open system windows to determine if split-screen multi-window mode is active.
     * Enhanced with OEM-specific detection and multi-window activity flags.
     */
    fun evaluateSplitScreen(
        windows: List<AccessibilityWindowInfo>?,
        displayMetrics: DisplayMetrics,
        currentPackage: String,
        isBlockSplitScreenEnabled: Boolean,
        hasCompletedOnboarding: Boolean
    ): SplitScreenBlockResult {
        if (!hasCompletedOnboarding || !isBlockSplitScreenEnabled) {
            return SplitScreenBlockResult(isBlocked = false)
        }

        if (windows == null || windows.isEmpty()) {
            return SplitScreenBlockResult(isBlocked = false)
        }

        // Throttle repeated blocks to avoid spam
        val now = System.currentTimeMillis()
        if (now - lastBlockTimestamp < 3000L) {
            return SplitScreenBlockResult(isBlocked = false)
        }

        try {
            val appWindows = windows.filter {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.layer >= 0
            }

            // 1. STANDARD SPLIT-SCREEN DETECTION: 2+ app windows that partition the display
            val standardResult = detectStandardSplitScreen(appWindows, displayMetrics)
            if (standardResult.isBlocked) {
                return enforceBlock(standardResult, now)
            }

            // 2. OEM SPLIT-SCREEN DOCK DETECTION: Check for OEM split-screen system packages
            val oemResult = detectOemSplitScreen(appWindows)
            if (oemResult.isBlocked) {
                return enforceBlock(oemResult, now)
            }

            // 3. STACKED WINDOWS DETECTION: 2+ windows stacked vertically or horizontally
            //    with significant overlap (common in some split-screen implementations)
            val stackedResult = detectStackedWindows(appWindows, displayMetrics)
            if (stackedResult.isBlocked) {
                return enforceBlock(stackedResult, now)
            }

            // 4. MULTI-WINDOW SYSTEMUI DETECTION: SystemUI split-screen divider visible
            val systemuiResult = detectSystemUiSplitScreen(windows)
            if (systemuiResult.isBlocked) {
                return enforceBlock(systemuiResult, now)
            }

        } catch (e: Exception) {
            Log.e(tag, "Error during split screen window evaluation", e)
        }

        return SplitScreenBlockResult(isBlocked = false)
    }

    /**
     * Standard split-screen: 2+ application windows that partition the display area.
     */
    private fun detectStandardSplitScreen(
        appWindows: List<AccessibilityWindowInfo>,
        displayMetrics: DisplayMetrics
    ): SplitScreenBlockResult {
        if (appWindows.size < 2) return SplitScreenBlockResult(isBlocked = false)

        for (i in appWindows.indices) {
            for (j in i + 1 until appWindows.size) {
                val rect1 = Rect()
                val rect2 = Rect()
                try {
                    appWindows[i].getBoundsInScreen(rect1)
                    appWindows[j].getBoundsInScreen(rect2)
                } catch (_: Exception) { continue }

                // Both windows must have valid dimensions
                if (rect1.width() <= 0 || rect1.height() <= 0 || rect2.width() <= 0 || rect2.height() <= 0) {
                    continue
                }

                // Check if windows are adjacent (split-screen layout)
                val isHorizontallySplit = rect1.bottom <= rect2.top + 10 || rect2.bottom <= rect1.top + 10
                val isVerticallySplit = rect1.right <= rect2.left + 10 || rect2.right <= rect1.left + 10
                val isSplitLayout = isHorizontallySplit || isVerticallySplit

                // Check if windows cover most of the screen
                val displayArea = displayMetrics.widthPixels * displayMetrics.heightPixels
                val combinedArea = (rect1.width() * rect1.height()) + (rect2.width() * rect2.height())
                val coversMostScreen = combinedArea >= (displayArea * 0.55f)

                if (isSplitLayout && coversMostScreen) {
                    val pkg1 = try { appWindows[i].root?.packageName?.toString() ?: "" } catch (_: Exception) { "" }
                    val pkg2 = try { appWindows[j].root?.packageName?.toString() ?: "" } catch (_: Exception) { "" }

                    // Skip if both are OEM overlay packages
                    if (isOemOverlayPackage(pkg1) && isOemOverlayPackage(pkg2)) continue

                    val culprit = if (pkg1.isNotBlank() && !isOemOverlayPackage(pkg1)) pkg1
                    else if (pkg2.isNotBlank() && !isOemOverlayPackage(pkg2)) pkg2
                    else "com.android.systemui.splitscreen"

                    Log.w(tag, "Split-screen multi-window detected between '$pkg1' and '$pkg2'.")
                    return SplitScreenBlockResult(
                        isBlocked = true,
                        reason = "Multi-window split screen multitasking is prohibited during focus protection.",
                        culpritPackage = culprit
                    )
                }
            }
        }

        return SplitScreenBlockResult(isBlocked = false)
    }

    /**
     * OEM split-screen: Check for OEM-specific multi-window packages in the window list.
     */
    private fun detectOemSplitScreen(
        appWindows: List<AccessibilityWindowInfo>
    ): SplitScreenBlockResult {
        for (window in appWindows) {
            val root = try { window.root } catch (_: Exception) { null }
            val pkg = root?.packageName?.toString() ?: continue

            if (oemSplitScreenPackages.contains(pkg)) {
                // Check if this is a split-screen dock/divider (not just a regular app)
                val rect = Rect()
                try {
                    window.getBoundsInScreen(rect)
                } catch (_: Exception) { continue }

                // OEM split-screen docks are typically narrow and tall/wide
                val isDockShape = (rect.width() < 100 && rect.height() > 200) ||
                        (rect.height() < 100 && rect.width() > 200)

                if (isDockShape) {
                    Log.w(tag, "OEM split-screen dock/divider detected: $pkg")
                    return SplitScreenBlockResult(
                        isBlocked = true,
                        reason = "OEM split-screen multi-window is prohibited during focus protection.",
                        culpritPackage = pkg
                    )
                }
            }
        }

        return SplitScreenBlockResult(isBlocked = false)
    }

    /**
     * Stacked windows: 2+ windows stacked with significant vertical/horizontal overlap.
     * This catches split-screen modes that don't use adjacent layout.
     */
    private fun detectStackedWindows(
        appWindows: List<AccessibilityWindowInfo>,
        displayMetrics: DisplayMetrics
    ): SplitScreenBlockResult {
        if (appWindows.size < 2) return SplitScreenBlockResult(isBlocked = false)

        for (i in appWindows.indices) {
            for (j in i + 1 until appWindows.size) {
                val rect1 = Rect()
                val rect2 = Rect()
                try {
                    appWindows[i].getBoundsInScreen(rect1)
                    appWindows[j].getBoundsInScreen(rect2)
                } catch (_: Exception) { continue }

                if (rect1.width() <= 0 || rect1.height() <= 0 || rect2.width() <= 0 || rect2.height() <= 0) {
                    continue
                }

                // Calculate overlap area
                val overlapLeft = maxOf(rect1.left, rect2.left)
                val overlapTop = maxOf(rect1.top, rect2.top)
                val overlapRight = minOf(rect1.right, rect2.right)
                val overlapBottom = minOf(rect1.bottom, rect2.bottom)

                if (overlapLeft < overlapRight && overlapTop < overlapBottom) {
                    val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
                    val minArea = minOf(
                        rect1.width() * rect1.height(),
                        rect2.width() * rect2.height()
                    )

                    // If overlap is less than 30% of the smaller window, windows are side-by-side
                    val isSideBySide = minArea > 0 && overlapArea < minArea * 0.3f

                    if (isSideBySide) {
                        val pkg1 = try { appWindows[i].root?.packageName?.toString() ?: "" } catch (_: Exception) { "" }
                        val pkg2 = try { appWindows[j].root?.packageName?.toString() ?: "" } catch (_: Exception) { "" }

                        if (isOemOverlayPackage(pkg1) && isOemOverlayPackage(pkg2)) continue

                        val displayArea = displayMetrics.widthPixels * displayMetrics.heightPixels
                        val combinedArea = (rect1.width() * rect1.height()) + (rect2.width() * rect2.height())

                        if (combinedArea >= displayArea * 0.60f) {
                            val culprit = if (pkg1.isNotBlank() && !isOemOverlayPackage(pkg1)) pkg1
                            else if (pkg2.isNotBlank() && !isOemOverlayPackage(pkg2)) pkg2
                            else "com.android.systemui.splitscreen"

                            Log.w(tag, "Stacked split-screen windows detected: '$pkg1' and '$pkg2'")
                            return SplitScreenBlockResult(
                                isBlocked = true,
                                reason = "Multi-window split screen multitasking is prohibited during focus protection.",
                                culpritPackage = culprit
                            )
                        }
                    }
                }
            }
        }

        return SplitScreenBlockResult(isBlocked = false)
    }

    /**
     * SystemUI split-screen: Check for split-screen divider bar in SystemUI windows.
     */
    private fun detectSystemUiSplitScreen(
        windows: List<AccessibilityWindowInfo>
    ): SplitScreenBlockResult {
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

            val root = try { window.root } catch (_: Exception) { null }
            val pkg = root?.packageName?.toString() ?: continue

            // Check for split-screen divider in SystemUI
            if (pkg == "com.android.systemui") {
                val rect = Rect()
                try {
                    window.getBoundsInScreen(rect)
                } catch (_: Exception) { continue }

                // Split-screen divider is typically a narrow bar
                val isDividerBar = (rect.width() in 1..40 && rect.height() > 200) ||
                        (rect.height() in 1..40 && rect.width() > 200)

                if (isDividerBar) {
                    Log.w(tag, "SystemUI split-screen divider detected")
                    return SplitScreenBlockResult(
                        isBlocked = true,
                        reason = "Split-screen divider detected during focus protection.",
                        culpritPackage = "com.android.systemui.splitscreen"
                    )
                }
            }
        }

        return SplitScreenBlockResult(isBlocked = false)
    }

    private fun isOemOverlayPackage(pkg: String): Boolean {
        return oemOverlayPackages.any { pkg.startsWith(it) }
    }

    private fun enforceBlock(result: SplitScreenBlockResult, now: Long): SplitScreenBlockResult {
        lastBlockTimestamp = now
        blockCount++
        return result
    }

    /**
     * Enforces the blocking action by returning to Home and launching the blocker overlay.
     * Also triggers vibration alert.
     */
    fun enforceSplitScreenBlock(
        service: AccessibilityService,
        result: SplitScreenBlockResult,
        blockerManager: FocusBlockerManager?
    ) {
        if (!result.isBlocked) return
        Log.w(tag, "Enforcing Split-Screen block for ${result.culpritPackage} (block #$blockCount)")

        // Trigger notification sound alert
        try {
            com.example.core.sound.FocusAlertSoundManager.playWarningBuzzer(service)
        } catch (_: Exception) {}

        // Show overlay notification
        try {
            com.example.core.overlay.FocusDisplayOverlayNotificationManager.showSplitScreenBlockedHud(service)
        } catch (_: Exception) {}

        // Send notification
        try {
            com.example.core.notification.FocusShieldBlockNotificationHelper.notifySplitScreenBlocked(service)
        } catch (_: Exception) {}

        // Force home action
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        blockerManager?.handleBlockedPackage(
            packageName = result.culpritPackage,
            fallbackAppName = "Split Screen Multi-window",
            decision = ProtectionDecision.BLOCK_SPLIT_SCREEN
        )
    }

    companion object {
        val instance: SplitScreenBlockerEngine by lazy { SplitScreenBlockerEngine() }
    }
}
