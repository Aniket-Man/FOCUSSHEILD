package com.example.feature.blocker.engine

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.example.core.accessibility.ProtectionDecision
import com.example.feature.blocker.FocusBlockerManager

data class FloatingWindowBlockResult(
    val isBlocked: Boolean,
    val reason: String = "",
    val culpritPackage: String = "com.android.systemui.pip"
)

/**
 * High-performance engine for detecting and intercepting Picture-in-Picture (PiP),
 * freeform pop-up windows, chat heads, overlays, Samsung Smart Pop-Up, Xiaomi Floating Windows,
 * and floating overlays. Enhanced with OEM-specific detection and aggressive enforcement.
 */
class FloatingWindowBlockerEngine private constructor() {

    private val tag = "FloatingWindowBlockerEngine"

    private var lastBlockTimestamp: Long = 0L
    private var blockCount: Int = 0

    /**
     * Known chat head / bubble overlay packages.
     */
    private val chatHeadPackages = setOf(
        "com.facebook.orca",          // Facebook Messenger
        "com.facebook.katana",        // Facebook
        "com.instagram.android",      // Instagram
        "com.whatsapp",               // WhatsApp
        "com.whatsapp.w4b",           // WhatsApp Business
        "org.telegram.messenger",     // Telegram
        "org.telegram.messenger.web",
        "com.viber.voip",             // Viber
        "com.snapchat.android",       // Snapchat
        "com.discord",                // Discord
        "com.slack.android",          // Slack
        "com.microsoft.teams",        // Microsoft Teams
        "com.google.android.apps.tachyon" // Google Meet
    )

    /**
     * OEM floating window / freeform packages.
     */
    private val oemFloatingPackages = setOf(
        // Samsung
        "com.samsung.android.app.tips",
        "com.samsung.android.spay",
        "com.samsung.android.bixby.agent",
        "com.samsung.android.bixby.service",
        "com.samsung.android.app.routines",
        "com.samsung.android.visionintelligence",
        "com.samsung.android.game.gamehome",
        "com.samsung.android.app.spage",
        // Xiaomi / MIUI
        "com.miui.freeform",
        "com.miui.securitycenter",
        "com.miui.guardprovider",
        // Huawei
        "com.huawei.android.launcher",
        "com.huawei.systemui",
        // OnePlus
        "com.oneplus.brickmode",
        // Oppo
        "com.coloros.assistantscreen",
        // Vivo
        "com.vivo.aiassistant"
    )

    /**
     * System packages to ignore when evaluating floating windows.
     */
    private val ignoredPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.settings",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher"
    )

    /**
     * Evaluates all open system windows to identify Picture-in-Picture, floating pop-up,
     * chat heads, overlays, and OEM floating windows.
     */
    fun evaluateFloatingWindows(
        windows: List<AccessibilityWindowInfo>?,
        displayMetrics: DisplayMetrics,
        selfPackageName: String,
        isBlockFloatingWindowEnabled: Boolean,
        hasCompletedOnboarding: Boolean
    ): FloatingWindowBlockResult {
        if (!hasCompletedOnboarding || !isBlockFloatingWindowEnabled) {
            return FloatingWindowBlockResult(isBlocked = false)
        }

        if (windows == null || windows.isEmpty()) {
            return FloatingWindowBlockResult(isBlocked = false)
        }

        // Throttle repeated blocks
        val now = System.currentTimeMillis()
        if (now - lastBlockTimestamp < 3000L) {
            return FloatingWindowBlockResult(isBlocked = false)
        }

        try {
            for (w in windows) {
                // 1. PICTURE-IN-PICTURE MODE (Android 8.0+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && w.isInPictureInPictureMode) {
                    val root = try { w.root } catch (_: Exception) { null }
                    val windowPkg = root?.packageName?.toString() ?: "com.android.systemui.pip"
                    Log.w(tag, "Picture-in-Picture (PiP) window detected: $windowPkg")
                    return enforceBlock(FloatingWindowBlockResult(
                        isBlocked = true,
                        reason = "Picture-in-Picture (PiP) multitasking is prohibited during focus protection.",
                        culpritPackage = windowPkg
                    ), now)
                }

                // Only check application windows
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

                val root = try { w.root } catch (_: Exception) { null }
                val windowPkg = root?.packageName?.toString() ?: ""

                // Skip self, system, and ignored packages
                if (shouldIgnorePackage(windowPkg, selfPackageName)) continue

                val rect = Rect()
                try {
                    w.getBoundsInScreen(rect)
                } catch (_: Exception) { continue }

                if (rect.width() <= 0 || rect.height() <= 0) continue

                // 2. CHAT HEAD / BUBBLE OVERLAY DETECTION
                val chatHeadResult = detectChatHead(windowPkg, rect, displayMetrics)
                if (chatHeadResult.isBlocked) {
                    return enforceBlock(chatHeadResult, now)
                }

                // 3. OEM FLOATING WINDOW DETECTION
                val oemResult = detectOemFloatingWindow(windowPkg, rect, displayMetrics)
                if (oemResult.isBlocked) {
                    return enforceBlock(oemResult, now)
                }

                // 4. FREEFORM / POP-UP WINDOW DETECTION
                val freeformResult = detectFreeformWindow(w, windowPkg, rect, displayMetrics)
                if (freeformResult.isBlocked) {
                    return enforceBlock(freeformResult, now)
                }

                // 5. OVERLAY WINDOW DETECTION (from non-system apps)
                val overlayResult = detectOverlayWindow(w, windowPkg, rect, displayMetrics, selfPackageName)
                if (overlayResult.isBlocked) {
                    return enforceBlock(overlayResult, now)
                }

                // 6. SMALL FLOATING WINDOW DETECTION (catch-all for unknown floating apps)
                val floatingResult = detectSmallFloatingWindow(windowPkg, rect, displayMetrics, w)
                if (floatingResult.isBlocked) {
                    return enforceBlock(floatingResult, now)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during floating window evaluation", e)
        }

        return FloatingWindowBlockResult(isBlocked = false)
    }

    /**
     * Chat Head / Bubble detection: Small circular floating windows from messaging apps.
     */
    private fun detectChatHead(
        pkg: String,
        rect: Rect,
        displayMetrics: DisplayMetrics
    ): FloatingWindowBlockResult {
        if (chatHeadPackages.none { pkg.startsWith(it) }) {
            return FloatingWindowBlockResult(isBlocked = false)
        }

        // Chat heads are typically small and circular (width ≈ height, both < 200px)
        val isSmallAndRoughlySquare = rect.width() in 50..250 && rect.height() in 50..250
        val aspectRatio = rect.width().toFloat() / rect.height().toFloat()
        val isCircularish = aspectRatio in 0.7f..1.4f

        if (isSmallAndRoughlySquare && isCircularish) {
            Log.w(tag, "Chat head / bubble overlay detected: $pkg")
            return FloatingWindowBlockResult(
                isBlocked = true,
                reason = "Chat head / bubble overlay is prohibited during focus protection.",
                culpritPackage = pkg
            )
        }

        return FloatingWindowBlockResult(isBlocked = false)
    }

    /**
     * OEM floating window detection: Samsung Smart Pop-Up, Xiaomi Freeform, etc.
     */
    private fun detectOemFloatingWindow(
        pkg: String,
        rect: Rect,
        displayMetrics: DisplayMetrics
    ): FloatingWindowBlockResult {
        if (oemFloatingPackages.none { pkg.startsWith(it) }) {
            return FloatingWindowBlockResult(isBlocked = false)
        }

        // OEM floating windows are typically smaller than full screen
        val isSmallWindow = rect.width() < displayMetrics.widthPixels * 0.85f &&
                rect.height() < displayMetrics.heightPixels * 0.85f

        if (isSmallWindow) {
            Log.w(tag, "OEM floating window detected: $pkg")
            return FloatingWindowBlockResult(
                isBlocked = true,
                reason = "OEM floating window is prohibited during focus protection.",
                culpritPackage = pkg
            )
        }

        return FloatingWindowBlockResult(isBlocked = false)
    }

    /**
     * Freeform window detection: Windows that are not focused, not full-screen, and positioned
     * in a way that suggests freeform/floating mode.
     */
    private fun detectFreeformWindow(
        window: AccessibilityWindowInfo,
        pkg: String,
        rect: Rect,
        displayMetrics: DisplayMetrics
    ): FloatingWindowBlockResult {
        // Freeform windows are typically:
        // - Not focused (user is interacting with another app)
        // - Smaller than full screen
        // - Positioned in the middle or bottom of screen (not at edges)
        val isNotFocused = !window.isFocused
        val isSmallWindow = rect.width() < displayMetrics.widthPixels * 0.85f &&
                rect.height() < displayMetrics.heightPixels * 0.85f
        val isPositionedFloating = rect.top > 50 && rect.left > 50

        if (isNotFocused && isSmallWindow && isPositionedFloating) {
            // Additional check: window must have significant size (not just a status bar widget)
            val hasSignificantSize = rect.width() > 150 && rect.height() > 150
            if (hasSignificantSize) {
                Log.w(tag, "Freeform / floating window detected: $pkg (${rect.width()}x${rect.height()})")
                return FloatingWindowBlockResult(
                    isBlocked = true,
                    reason = "Freeform floating window is prohibited during focus protection.",
                    culpritPackage = pkg
                )
            }
        }

        return FloatingWindowBlockResult(isBlocked = false)
    }

    /**
     * Overlay window detection: Windows drawn on top of other apps with special flags.
     */
    private fun detectOverlayWindow(
        window: AccessibilityWindowInfo,
        pkg: String,
        rect: Rect,
        displayMetrics: DisplayMetrics,
        selfPackageName: String
    ): FloatingWindowBlockResult {
        // Overlay windows are typically:
        // - Type APPLICATION but drawn on top
        // - Not focused
        // - Have specific layer ordering
        // - Are smaller than full screen
        val isNotFocused = !window.isFocused
        val isSmallWindow = rect.width() < displayMetrics.widthPixels * 0.80f &&
                rect.height() < displayMetrics.heightPixels * 0.80f
        val isLayered = window.layer > 0

        if (isNotFocused && isSmallWindow && isLayered) {
            // Check if this is a notification shade or similar system overlay
            val isNotificationShade = pkg.contains("systemui") || pkg.contains("notification")
            if (!isNotificationShade) {
                val hasSignificantSize = rect.width() > 100 && rect.height() > 100
                if (hasSignificantSize) {
                    Log.w(tag, "Overlay window detected: $pkg (${rect.width()}x${rect.height()})")
                    return FloatingWindowBlockResult(
                        isBlocked = true,
                        reason = "Overlay window is prohibited during focus protection.",
                        culpritPackage = pkg
                    )
                }
            }
        }

        return FloatingWindowBlockResult(isBlocked = false)
    }

    /**
     * Small floating window catch-all: Any application window that is significantly smaller
     * than the display and positioned in a floating manner.
     */
    private fun detectSmallFloatingWindow(
        pkg: String,
        rect: Rect,
        displayMetrics: DisplayMetrics,
        window: AccessibilityWindowInfo
    ): FloatingWindowBlockResult {
        // Only trigger for clearly floating windows (much smaller than screen)
        val widthRatio = rect.width().toFloat() / displayMetrics.widthPixels
        val heightRatio = rect.height().toFloat() / displayMetrics.heightPixels

        // Window must be less than 60% of screen width AND less than 60% of screen height
        if (widthRatio < 0.60f && heightRatio < 0.60f) {
            // Must have reasonable size (not a tiny widget)
            if (rect.width() > 200 && rect.height() > 200) {
                // Must not be focused (floating windows are typically background)
                if (!window.isFocused) {
                    Log.w(tag, "Small floating window detected: $pkg (${rect.width()}x${rect.height()}, ${widthRatio}x${heightRatio})")
                    return FloatingWindowBlockResult(
                        isBlocked = true,
                        reason = "Floating window is prohibited during focus protection.",
                        culpritPackage = pkg
                    )
                }
            }
        }

        return FloatingWindowBlockResult(isBlocked = false)
    }

    private fun shouldIgnorePackage(pkg: String, selfPackageName: String): Boolean {
        if (pkg.isEmpty() || pkg == selfPackageName) return true
        return ignoredPackages.any { pkg.startsWith(it) }
    }

    private fun enforceBlock(result: FloatingWindowBlockResult, now: Long): FloatingWindowBlockResult {
        lastBlockTimestamp = now
        blockCount++
        return result
    }

    /**
     * Enforces the blocking action by returning to Home and launching the blocker overlay.
     * Also triggers vibration alert.
     */
    fun enforceFloatingWindowBlock(
        service: AccessibilityService,
        result: FloatingWindowBlockResult,
        blockerManager: FocusBlockerManager?
    ) {
        if (!result.isBlocked) return
        Log.w(tag, "Enforcing Floating Window block for ${result.culpritPackage} (block #$blockCount)")

        // Trigger vibration alert
        try {
            com.example.core.sound.FocusAlertSoundManager.playWarningBuzzer(service)
        } catch (_: Exception) {}

        // Show overlay notification
        try {
            com.example.core.overlay.FocusDisplayOverlayNotificationManager.showFloatingWindowBlockedHud(service)
        } catch (_: Exception) {}

        // Send notification
        try {
            com.example.core.notification.FocusShieldBlockNotificationHelper.notifyFloatingWindowBlocked(service)
        } catch (_: Exception) {}

        // Force home action
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

        blockerManager?.handleBlockedPackage(
            packageName = result.culpritPackage,
            fallbackAppName = "Picture-in-Picture / Floating Window",
            decision = ProtectionDecision.BLOCK_FLOATING_WINDOW
        )
    }

    companion object {
        val instance: FloatingWindowBlockerEngine by lazy { FloatingWindowBlockerEngine() }
    }
}
