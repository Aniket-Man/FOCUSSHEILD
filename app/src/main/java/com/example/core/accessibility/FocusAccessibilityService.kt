package com.example.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.FocusShieldApp
import com.example.core.detector.ShortFormDetectionManager
import com.example.core.util.MediaPauseHelper
import com.example.data.preferences.FocusPreferences
import com.example.feature.applimits.engine.AppLimitDecision
import com.example.feature.applimits.engine.AppLimitManager
import com.example.feature.applimits.engine.AppLimitOverlayMode
import com.example.feature.blocker.FocusBlockerManager
import com.example.feature.youtube.detector.YouTubeDetectionRules
import com.example.feature.youtube.engine.YouTubeBlockDecision
import com.example.feature.youtube.engine.YouTubeBlockVerdict
import com.example.feature.youtube.engine.YouTubeContentBlockEngine
import com.example.feature.youtube.engine.YouTubeNodeExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android Accessibility Service for detecting foreground distraction apps,
 * YouTube Shorts, Instagram Reels, Facebook Reels, and enforcing Study Mode & App Limits.
 *
 * Integrates the Shorts-Blocker detection architecture.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isServiceRunning: Boolean = false
            private set

        var instance: FocusAccessibilityService? = null
            private set
    }

    private val tag = "FocusAccessibility"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val shortFormDetectionManager = ShortFormDetectionManager.instance

    private val tamperPackages = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.settings",
        "com.google.android.settings",
        "com.miui.securitycenter",
        "com.samsung.android.lool",
        "com.coloros.safecenter",
        "com.oppo.launcher",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3"
    )

    private var currentPreferences = FocusPreferences()
    private var lastAppLimitCheckTimestamp: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 80
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        Log.d(tag, "FocusAccessibilityService connected safely and is active.")

        // Track phone unlocks (ACTION_USER_PRESENT must be registered at runtime)
        com.example.core.tracking.PhoneUnlockTracker.register(this)

        // Observe user preferences for shorts/reels blocking
        serviceScope.launch {
            try {
                if (com.example.feature.session.engine.FocusSessionManager.instance.activeSession.value == null) {
                    com.example.feature.session.engine.FocusSessionManager.instance.restoreActiveSessionFromDisk(this@FocusAccessibilityService)
                }
                (application as? FocusShieldApp)?.preferencesRepository?.preferencesFlow?.collectLatest { prefs ->
                    currentPreferences = prefs
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading preferences: ${e.message}")
            }
        }

        // Session state is the source of truth for every YouTube restriction.  Removing the
        // Home warning here (rather than waiting for another YouTube accessibility event) makes
        // a finished session restore normal YouTube behavior immediately.
        serviceScope.launch {
            com.example.feature.session.engine.FocusSessionManager.instance.sessionState.collectLatest { state ->
                if (state != com.example.feature.session.domain.SessionState.RUNNING) {
                    com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                    YouTubeContentBlockEngine.reset()
                    homeFeedPopupDismissed = false
                }
            }
        }
    }

    private var lastForegroundPackage: String? = null
    private var lastShortFormInspectTimestamp: Long = 0L
    private var lastBrowserInspectTimestamp: Long = 0L
    private var lastBrowserBlockTimestamp: Long = 0L
    private var lastYouTubeBlockTimestamp: Long = 0L
    private var lastSplitScreenCheckTimestamp: Long = 0L
    private var lastFloatingWindowCheckTimestamp: Long = 0L
    private var homeFeedPopupDismissed = false
    private var youtubeBackNavigationPending = false
    private val mainThreadHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }

        val rawPackageName = event.packageName?.toString() ?: return
        if (rawPackageName.isBlank()) return

        val isOwnApp = rawPackageName == packageName ||
                rawPackageName == applicationContext.packageName ||
                rawPackageName.startsWith("com.example") ||
                rawPackageName.startsWith("com.aistudio.focusshield")

        if (isOwnApp) {
            if (lastForegroundPackage != null &&
                YouTubeDetectionRules.isYouTubePackage(lastForegroundPackage)
            ) {
                YouTubeContentBlockEngine.reset()
            }
            lastForegroundPackage = rawPackageName
            com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
            homeFeedPopupDismissed = false
            return
        }

        try {
            val blockerManager = try { FocusBlockerManager.instance } catch (e: Exception) { null }
            val appLimitManager = try { AppLimitManager.instance } catch (e: Exception) { null }
            val now = System.currentTimeMillis()

            // Update foreground tracking on window state changes
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                if (lastForegroundPackage != null &&
                    YouTubeDetectionRules.isYouTubePackage(lastForegroundPackage) &&
                    !YouTubeDetectionRules.isYouTubePackage(rawPackageName)
                ) {
                    YouTubeContentBlockEngine.reset()
                }
                lastForegroundPackage = rawPackageName
                
                if (!rawPackageName.startsWith("com.example") &&
                    !rawPackageName.startsWith("com.aistudio.focusshield") &&
                    rawPackageName != applicationContext.packageName
                ) {
                    appLimitManager?.onForegroundPackageChanged(rawPackageName)
                }
            }

            // 0A. REAL-TIME UNINSTALL & ANTI-TAMPER PROTECTION
            // Block Protection: while a focus session is RUNNING/PAUSED this guard is
            // ALWAYS active (preventing mid-session disable attempts like force stop,
            // uninstall, or clear data), independent of the 24/7 preference below.
            if ((currentPreferences.isBlockUninstallEnabled ||
                com.example.feature.blocker.protection.BlockProtectionManager.isSessionProtectionActive) &&
                tamperPackages.contains(rawPackageName)
            ) {
                val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                if (rootNode != null) {
                    val isTamper = isTamperOrUninstallAttempt(rootNode, rawPackageName)
                    try { rootNode.recycle() } catch (_: Exception) {}
                    if (isTamper) {
                        Log.w(tag, "[$rawPackageName] Tamper or FocusShield uninstallation attempt detected! Enforcing instant block.")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        val toastMsg = "FocusShield Uninstall Protection is active"
                        android.widget.Toast.makeText(applicationContext, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
                        blockerManager?.handleBlockedPackage(
                            packageName = rawPackageName,
                            fallbackAppName = "Uninstall Protection",
                            decision = ProtectionDecision.BLOCK_UNINSTALL
                        )
                        return
                    }
                }
            }

            // 0B. REAL-TIME SPLIT-SCREEN & MULTI-WINDOW MULTITASKING BLOCKER
            // Throttle checks to every 500ms to prevent CPU overhead
            if (currentPreferences.isBlockSplitScreenEnabled && (now - lastSplitScreenCheckTimestamp) >= 500L) {
                lastSplitScreenCheckTimestamp = now
                if (checkSplitScreenProtection(blockerManager)) {
                    return
                }
            }

            // 0C. REAL-TIME FLOATING WINDOW & PICTURE-IN-PICTURE (PiP) BLOCKER
            // Throttle checks to every 500ms to prevent CPU overhead
            if (currentPreferences.isBlockFloatingWindowEnabled && (now - lastFloatingWindowCheckTimestamp) >= 500L) {
                lastFloatingWindowCheckTimestamp = now
                if (checkFloatingWindowProtection(blockerManager)) {
                    return
                }
            }

            // 1. FAST SHORT-FORM CONTENT (SHORTS & REELS) BLOCKING FLOW
            // Active if global toggle is ON or if user is in an active Focus Session.
            // YouTube is excluded here while a Study Mode session is active because the
            // full YouTube Content Block Engine below owns YouTube in that case.
            val isShortFormTracked = isShortFormBlockingEnabledFor(rawPackageName)
            if (isShortFormTracked) {
                // Throttle window content inspection to prevent CPU overhead (min 150ms between scans)
                if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    (now - lastShortFormInspectTimestamp) < 150L
                ) {
                    // Skip throttled tick
                } else {
                    lastShortFormInspectTimestamp = now
                    val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                    val isYT = YouTubeDetectionRules.isYouTubePackage(rawPackageName)

                    // YouTube uses the reel-player-container detection which never fires
                    // for Shorts shelves embedded in the Home feed; other apps keep the
                    // generic short-form detector.
                    val shortFormDetected = if (isYT) {
                        YouTubeNodeExtractor.detectShorts(rootNode, event)
                    } else {
                        shortFormDetectionManager.detectShortFormContent(rawPackageName, event, rootNode)
                    }
                    try { rootNode?.recycle() } catch (_: Exception) {}

                    if (shortFormDetected) {
                        val shouldTrigger = if (isYT) {
                            (now - lastYouTubeBlockTimestamp) >= 2000L
                        } else {
                            shortFormDetectionManager.shouldTriggerAction(rawPackageName)
                        }

                        if (shouldTrigger) {
                            Log.i(tag, "[$rawPackageName] Short-form content (Shorts/Reels) detected! Returning to Home.")
                            val isIG = rawPackageName.contains("instagram")
                            val isFB = rawPackageName.contains("facebook") || rawPackageName.contains("katana")

                            if (isYT) {
                                handleYouTubeShortsBlocked()
                                com.example.core.overlay.FocusDisplayOverlayNotificationManager.showShortsBlockedHud(this)
                                com.example.core.notification.FocusShieldBlockNotificationHelper.notifyShortsBlocked(this)
                            } else {
                                val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
                                if (!backSuccess) {
                                    performGlobalAction(GLOBAL_ACTION_HOME)
                                }
                                val appLabel = if (isIG) "Instagram" else if (isFB) "Facebook" else "Shorts"
                                com.example.core.overlay.FocusDisplayOverlayNotificationManager.showReelsBlockedHud(this, appLabel)
                                com.example.core.notification.FocusShieldBlockNotificationHelper.notifyReelsBlocked(this, appLabel)
                            }

                            // Record blocked attempt for analytics
                            val displayName = when {
                                isYT -> "YouTube Shorts"
                                isIG -> "Instagram Reels"
                                isFB -> "Facebook Reels"
                                else -> "Short-form Content"
                            }
                            blockerManager?.handleBlockedPackage(
                                packageName = rawPackageName,
                                fallbackAppName = displayName,
                                decision = ProtectionDecision.BLOCK_SHORTS
                            )
                            return
                        }
                    }
                }
            }

            // 1B. BROWSER WEBSITE BLOCKER FLOW (Auto Adult + Custom Distraction List)
            if (com.example.core.detector.BrowserUrlDetector.isBrowserPackage(rawPackageName)) {
                val protectionState = blockerManager?.getCurrentProtectionState(now)
                val isSessionActive = protectionState?.isSessionActive == true
                val isBrowserStudyMode = (protectionState?.isBrowserStudyModeEnabled != false) || isSessionActive

                // Throttle URL inspection to avoid infinite loops
                if ((eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || (now - lastBrowserInspectTimestamp) >= 200L) &&
                    (now - lastBrowserBlockTimestamp) >= 1200L
                ) {
                    lastBrowserInspectTimestamp = now
                    val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                    val urlInfo = com.example.core.detector.BrowserUrlDetector.extractUrlAndDomain(rawPackageName, rootNode, event)
                    if (urlInfo != null) {
                        val siteEngine = try { com.example.feature.websiteblocker.engine.WebsiteBlockerEngine.instance } catch (e: Exception) { null }
                        val siteDecision = siteEngine?.evaluateDomain(urlInfo.rawUrl, urlInfo.domain)
                        if (siteDecision is com.example.feature.websiteblocker.engine.WebsiteBlockDecision.Blocked) {
                            lastBrowserBlockTimestamp = now
                            Log.w(tag, "[$rawPackageName] Browser site blocked: ${siteDecision.domain} (${siteDecision.reason})")

                            // 1. Perform safe step-down inside the browser
                            navigateBrowserAwayFromBlockedSite(rawPackageName, rootNode)
                            try { rootNode?.recycle() } catch (_: Exception) {}

                            // 2. Display non-intrusive alert HUD & system notification
                            if (siteDecision.engineType == "ADULT_AUTOMATIC") {
                                com.example.core.overlay.FocusDisplayOverlayNotificationManager.showAdultSiteBlockedHud(this, siteDecision.domain)
                                com.example.core.notification.FocusShieldBlockNotificationHelper.notifyAdultSiteBlocked(this, siteDecision.domain)
                            } else {
                                com.example.core.overlay.FocusDisplayOverlayNotificationManager.showCustomWebsiteBlockedHud(this, siteDecision.domain)
                                com.example.core.notification.FocusShieldBlockNotificationHelper.notifyCustomWebsiteBlocked(this, siteDecision.domain)
                            }

                            // 3. Record analytics attempt directly into Room database
                            val blockedAttemptRepo = try { FocusShieldApp.instance.database.blockedAttemptDao() } catch (e: Exception) { null }
                            val activeSessionId = blockerManager?.getCurrentProtectionState(now)?.sessionId
                            serviceScope.launch {
                                try {
                                    blockedAttemptRepo?.insertAttempt(
                                        com.example.data.local.entity.BlockedAttemptEntity(
                                            packageName = rawPackageName,
                                            appName = if (siteDecision.engineType == "ADULT_AUTOMATIC") "18+ Adult Site (${siteDecision.domain})" else "Blocked Website (${siteDecision.domain})",
                                            timestamp = now,
                                            sessionId = activeSessionId
                                        )
                                    )
                                } catch (_: Exception) {}
                            }
                            return
                        }
                    }
                    try { rootNode?.recycle() } catch (_: Exception) {}
                }

                // If Browser Study Mode is active, allow normal browsing and searching
                if (isBrowserStudyMode) {
                    return
                }
            }

            // 2. YouTube Study Mode Inspection Flow (Focus Session & Channels Rules)
            if (YouTubeDetectionRules.isYouTubePackage(rawPackageName) && blockerManager != null) {
                // If recently redirected back from a blocked channel/short, allow a 2.5-second grace period
                // so the YouTube UI can land on Home and dismiss the miniplayer without re-triggering blocks
                if ((now - lastYouTubeBlockTimestamp) < 2500L) {
                    return
                }

                val protectionState = blockerManager.getCurrentProtectionState(now)
                if (protectionState.sessionState.name == "RUNNING" || protectionState.sessionState.name == "PAUSED") {
                    val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                    val decision = YouTubeContentBlockEngine.evaluate(
                        rootNode = rootNode,
                        event = event,
                        approvedChannelIds = protectionState.approvedChannelIds,
                        approvedChannelNames = protectionState.approvedChannelNames,
                        blockUnknownContent = protectionState.blockUnknownContent,
                        currentTime = now
                    )

                    if (decision.verdict == YouTubeBlockVerdict.HOME_FEED) {
                        // Home feed detected during an active session with Study Mode ON:
                        // show the over-the-display Study Mode popup (feed stays visible behind it).
                        // Do not re-show if the user already dismissed it for this Home visit
                        // to avoid Home -> Popup -> Home loops.
                        if (!homeFeedPopupDismissed) {
                            com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.showHomeFeedStudyModePopup(
                                context = this,
                                onSearchClick = {
                                    mainThreadHandler.postDelayed({ navigateToYouTubeSearch() }, 250L)
                                },
                                onDismiss = { homeFeedPopupDismissed = true }
                            )
                        }
                    } else {
                        com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                        homeFeedPopupDismissed = false
                    }

                    if (decision.isBlocking) {
                        Log.i(tag, "YouTube violation detected [${decision.verdict}]: ${decision.reason}. Channel: ${decision.detectedChannel}")

                        val youtubeResult = com.example.feature.youtube.domain.YouTubeDetectionResult(
                            contentType = if (decision.isShorts) {
                                com.example.feature.youtube.domain.YouTubeContentType.SHORT
                            } else {
                                com.example.feature.youtube.domain.YouTubeContentType.VIDEO_PLAYBACK
                            },
                            channelName = decision.detectedChannel,
                            channelId = null,
                            videoTitle = decision.detectedTitle,
                            isShort = decision.isShorts,
                            confidence = com.example.feature.youtube.domain.DetectionConfidence.HIGH,
                            matchedRule = decision.reason
                        )

                        when (decision.verdict) {
                            YouTubeBlockVerdict.BLOCK_SHORTS -> {
                                handleYouTubeShortsBlocked()
                                com.example.core.overlay.FocusDisplayOverlayNotificationManager.showShortsBlockedHud(this)
                                com.example.core.notification.FocusShieldBlockNotificationHelper.notifyShortsBlocked(this)
                            }
                            YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL,
                            YouTubeBlockVerdict.BLOCK_UNKNOWN_CONTENT -> {
                                val chan = decision.detectedChannel ?: decision.detectedTitle ?: "Unapproved Channel"
                                handleYouTubeUnapprovedChannelBlocked(rootNode)
                                com.example.core.overlay.FocusDisplayOverlayNotificationManager.showChannelBlockedHud(this, chan)
                                com.example.core.notification.FocusShieldBlockNotificationHelper.notifyChannelBlocked(this, chan)
                            }
                            else -> performGlobalAction(GLOBAL_ACTION_HOME)
                        }

                        val protectionDecision = when (decision.verdict) {
                            YouTubeBlockVerdict.BLOCK_SHORTS -> ProtectionDecision.BLOCK_SHORTS
                            YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL -> ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL
                            YouTubeBlockVerdict.BLOCK_UNKNOWN_CONTENT -> ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT
                            else -> ProtectionDecision.BLOCK
                        }

                        try { rootNode?.recycle() } catch (_: Exception) {}

                        blockerManager.handleBlockedPackage(
                            packageName = rawPackageName,
                            fallbackAppName = null,
                            decision = protectionDecision,
                            youtubeResult = youtubeResult
                        )
                        return
                    }
                    try { rootNode?.recycle() } catch (_: Exception) {}
                } else {
                    // Session no longer active while YouTube is open: remove any lingering Study Mode popup
                    com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                    homeFeedPopupDismissed = false
                }
            } else {
                com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                homeFeedPopupDismissed = false
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && blockerManager != null) {
                    // 3. Standard Focus Session App Blocking flow (Priority 1)
                    val decision = blockerManager.evaluateProtection(rawPackageName)
                    if (decision == ProtectionDecision.BLOCK) {
                        Log.i(tag, "Distraction app detected in Focus Session: $rawPackageName. Enforcing FocusShield block.")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        blockerManager.handleBlockedPackage(
                            packageName = rawPackageName,
                            fallbackAppName = null,
                            decision = decision
                        )
                        return
                    }
                }
            }

            // 4. Timed App Limit System (Priority 3 - evaluated when no Focus Session is actively blocking the app)
            if (appLimitManager != null) {
                val isYouTubePackage = YouTubeDetectionRules.isYouTubePackage(rawPackageName)
                val isBrowserPackage = com.example.core.detector.BrowserUrlDetector.isBrowserPackage(rawPackageName)
                val protectionState = blockerManager?.getCurrentProtectionState(now)
                val isSessionRunningOrPaused = protectionState?.let { it.sessionState.name == "RUNNING" || it.sessionState.name == "PAUSED" } ?: false
                val sessionActiveWithYouTubeStudyMode = isSessionRunningOrPaused && (protectionState?.isYouTubeStudyModeEnabled == true)
                val sessionActiveWithBrowserStudyMode = isSessionRunningOrPaused && (protectionState?.isBrowserStudyModeEnabled == true)

                if (!ProtectionPolicy.SAFE_SYSTEM_PACKAGES.contains(rawPackageName) &&
                    !rawPackageName.startsWith("com.example") &&
                    !rawPackageName.startsWith("com.aistudio.focusshield") &&
                    rawPackageName != applicationContext.packageName &&
                    !(isYouTubePackage && sessionActiveWithYouTubeStudyMode) &&
                    !(isBrowserPackage && sessionActiveWithBrowserStudyMode)
                ) {
                    val isLimited = appLimitManager.isPackageLimited(rawPackageName)
                    val isSessionActive = appLimitManager.isSessionActiveFor(rawPackageName)

                    val shouldCheck = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) ||
                            (isLimited && !isSessionActive && (now - lastAppLimitCheckTimestamp > 800L))

                    if (shouldCheck) {
                        lastAppLimitCheckTimestamp = now
                        serviceScope.launch {
                            when (val appLimitDecision = appLimitManager.checkAppLimitDecision(rawPackageName)) {
                                is AppLimitDecision.REQUIRE_USAGE_SELECTION -> {
                                    Log.i(tag, "App Limit: Prompting usage selection for ${appLimitDecision.appName}")
                                    performGlobalAction(GLOBAL_ACTION_HOME)
                                    MediaPauseHelper.pauseMedia(applicationContext)
                                    if (isYouTubePackage) {
                                        val currentRoot = try { rootInActiveWindow } catch (e: Exception) { null }
                                        clickPauseButtonInNodeTree(currentRoot)
                                        try { currentRoot?.recycle() } catch (_: Exception) {}
                                    }
                                    val usedMins = kotlin.math.round(appLimitDecision.usedDailyMillis / 60000.0).toInt().coerceAtLeast(0)
                                    val remMins = (appLimitDecision.dailyLimitMinutes - usedMins).coerceAtLeast(0)
                                    appLimitManager.launchOverlay(
                                        packageName = appLimitDecision.packageName,
                                        appName = appLimitDecision.appName,
                                        mode = AppLimitOverlayMode.AWAITING_SELECTION,
                                        usedMinutes = usedMins,
                                        remainingDailyMinutes = remMins,
                                        dailyLimitMinutes = appLimitDecision.dailyLimitMinutes,
                                        emergencyUsesAllowed = appLimitDecision.emergencyUsesAllowed,
                                        isStrict = appLimitDecision.isStrict,
                                        streakDays = appLimitDecision.streakDays,
                                        forceLaunch = true
                                    )
                                }
                                is AppLimitDecision.REQUIRE_DAILY_LIMIT_BLOCK -> {
                                    Log.i(tag, "App Limit: Daily limit exhausted for ${appLimitDecision.appName}. Enforcing blocker.")
                                    performGlobalAction(GLOBAL_ACTION_HOME)
                                    MediaPauseHelper.pauseMedia(applicationContext)
                                    if (isYouTubePackage) {
                                        val currentRoot = try { rootInActiveWindow } catch (e: Exception) { null }
                                        clickPauseButtonInNodeTree(currentRoot)
                                        try { currentRoot?.recycle() } catch (_: Exception) {}
                                    }
                                    appLimitManager.launchOverlay(
                                        packageName = appLimitDecision.packageName,
                                        appName = appLimitDecision.appName,
                                        mode = AppLimitOverlayMode.DAILY_LIMIT_REACHED,
                                        usedMinutes = appLimitDecision.dailyLimitMinutes,
                                        remainingDailyMinutes = 0,
                                        dailyLimitMinutes = appLimitDecision.dailyLimitMinutes,
                                        emergencyUsesCount = appLimitDecision.emergencyUsesCount,
                                        emergencyUsesAllowed = appLimitDecision.emergencyUsesAllowed,
                                        isStrict = appLimitDecision.isStrict,
                                        streakDays = appLimitDecision.streakDays,
                                        forceLaunch = true
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling accessibility event: ${e.message}")
        }
    }

    private fun isShortFormBlockingEnabledFor(packageName: String): Boolean {
        if (!shortFormDetectionManager.hasDetectorFor(packageName)) return false

        val blockerManager = try { FocusBlockerManager.instance } catch (e: Exception) { null }
        val protectionState = blockerManager?.getCurrentProtectionState()
        val isSessionActive = protectionState?.let { it.sessionState.name == "RUNNING" || it.sessionState.name == "PAUSED" } ?: false
        val isYouTubeStudyModeActive = isSessionActive && (protectionState?.isYouTubeStudyModeEnabled == true)

        val isYT = YouTubeDetectionRules.isYouTubePackage(packageName)
        val isIG = packageName.contains("instagram")
        val isFB = packageName.contains("facebook") || packageName.contains("katana")

        return when {
            isYT -> {
                // While a Study Mode session is active the full YouTube Content Block
                // Engine (step 2) owns YouTube entirely, including Shorts. Otherwise the
                // 24/7 Shorts toggle applies without a session.
                if (isYouTubeStudyModeActive) false
                else currentPreferences.isYouTubeShortsBlockingEnabled || currentPreferences.isShortsReelsAlwaysBlocked
            }
            isIG -> currentPreferences.isInstagramReelsBlockingEnabled || currentPreferences.isShortsReelsAlwaysBlocked || isSessionActive
            isFB -> currentPreferences.isFacebookReelsBlockingEnabled || currentPreferences.isShortsReelsAlwaysBlocked || isSessionActive
            else -> false
        }
    }

    private fun isTamperOrUninstallAttempt(rootNode: android.view.accessibility.AccessibilityNodeInfo?, rawPackageName: String): Boolean {
        if (rootNode == null) return false
        // Never block during onboarding or initial setup
        if (!currentPreferences.hasCompletedOnboarding) return false
        if (!tamperPackages.contains(rawPackageName)) return false

        // Exclude legitimate permission setup, battery optimization, and accessibility setup screens
        val safeSetupKeywords = listOf(
            "battery", "optimize", "unrestricted", "background", "notification",
            "accessibility", "special app access", "usage", "appear on top",
            "display over", "draw over", "manage app permissions", "allow"
        )
        for (keyword in safeSetupKeywords) {
            val safeNodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            val hasSafe = !safeNodes.isNullOrEmpty()
            safeNodes?.forEach { try { it.recycle() } catch (_: Exception) {} }
            if (hasSafe) {
                return false
            }
        }

        // Search for references to FocusShield or our package in the settings/uninstaller UI
        val appMatches = rootNode.findAccessibilityNodeInfosByText("FocusShield")
        val packageMatches = rootNode.findAccessibilityNodeInfosByText(packageName)
        val hasAppMatch = (!appMatches.isNullOrEmpty()) || (!packageMatches.isNullOrEmpty())
        appMatches?.forEach { try { it.recycle() } catch (_: Exception) {} }
        packageMatches?.forEach { try { it.recycle() } catch (_: Exception) {} }

        if (hasAppMatch) {
            // Check specifically for uninstallation or clear data/force stop actions
            if (rawPackageName.contains("packageinstaller") || rawPackageName.contains("settings")) {
                val uninstallKeywords = listOf("uninstall", "do you want to uninstall", "delete app", "clear storage", "clear data", "force stop", "deactivate")
                for (kw in uninstallKeywords) {
                    val nodes = rootNode.findAccessibilityNodeInfosByText(kw)
                    val hasMatch = !nodes.isNullOrEmpty()
                    nodes?.forEach { try { it.recycle() } catch (_: Exception) {} }
                    if (hasMatch) {
                        return true
                    }
                }
            }

            // In settings or launcher context menus
            val uninstallConfirmNodes = rootNode.findAccessibilityNodeInfosByText("Do you want to uninstall")
            val hasConfirm = !uninstallConfirmNodes.isNullOrEmpty()
            uninstallConfirmNodes?.forEach { try { it.recycle() } catch (_: Exception) {} }
            if (hasConfirm) {
                return true
            }
        }
        return false
    }

    private fun checkSplitScreenProtection(blockerManager: FocusBlockerManager?): Boolean {
        val result = com.example.feature.blocker.engine.SplitScreenBlockerEngine.instance.evaluateSplitScreen(
            windows = windows,
            displayMetrics = resources.displayMetrics,
            currentPackage = lastForegroundPackage ?: "",
            isBlockSplitScreenEnabled = currentPreferences.isBlockSplitScreenEnabled,
            hasCompletedOnboarding = currentPreferences.hasCompletedOnboarding
        )
        if (result.isBlocked) {
            com.example.feature.blocker.engine.SplitScreenBlockerEngine.instance.enforceSplitScreenBlock(this, result, blockerManager)
            return true
        }
        return false
    }

    private fun checkFloatingWindowProtection(blockerManager: FocusBlockerManager?): Boolean {
        val result = com.example.feature.blocker.engine.FloatingWindowBlockerEngine.instance.evaluateFloatingWindows(
            windows = windows,
            displayMetrics = resources.displayMetrics,
            selfPackageName = packageName,
            isBlockFloatingWindowEnabled = currentPreferences.isBlockFloatingWindowEnabled,
            hasCompletedOnboarding = currentPreferences.hasCompletedOnboarding
        )
        if (result.isBlocked) {
            com.example.feature.blocker.engine.FloatingWindowBlockerEngine.instance.enforceFloatingWindowBlock(this, result, blockerManager)
            return true
        }
        return false
    }

    /**
     * Handles YouTube Shorts blocking: every Short is blocked instantly with NO pause —
     * a single immediate step back returns the user to the previous feed.
     */
    private fun handleYouTubeShortsBlocked() {
        val now = System.currentTimeMillis()
        if (now - lastYouTubeBlockTimestamp < 2000L) {
            return
        }
        lastYouTubeBlockTimestamp = now

        YouTubeContentBlockEngine.reset()
        val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
        if (!backSuccess) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /**
     * Handles YouTube Unapproved Channel blocking: pauses the video first, then steps back once for just one time.
     * When reaching the homepage, clears channel context so no channel name is detected.
     */
    private fun handleYouTubeUnapprovedChannelBlocked(rootNode: android.view.accessibility.AccessibilityNodeInfo?) {
        val now = System.currentTimeMillis()
        if (now - lastYouTubeBlockTimestamp < 2000L) {
            return
        }
        lastYouTubeBlockTimestamp = now

        YouTubeContentBlockEngine.reset()
        pauseThenNavigateYouTubeBackOnce(rootNode)
    }

    /**
     * Enforces the exact blocked-playback lifecycle: PAUSE -> allow the pause to settle -> one
     * BACK.  The pending guard prevents an accessibility-event burst from issuing multiple back
     * actions for one video, and the state check makes a session ending during the short pause
     * interval immediately return YouTube to normal behavior.
     */
    private fun pauseThenNavigateYouTubeBackOnce(rootNode: android.view.accessibility.AccessibilityNodeInfo?) {
        silenceAndPausePlayback()
        clickPauseButtonInNodeTree(rootNode)

        if (youtubeBackNavigationPending) return
        youtubeBackNavigationPending = true
        mainThreadHandler.postDelayed({
            try {
                val protectionState = try {
                    FocusBlockerManager.instance.getCurrentProtectionState()
                } catch (_: Exception) {
                    null
                }
                val shouldStillEnforce = protectionState?.let {
                    (it.sessionState == com.example.feature.session.domain.SessionState.RUNNING ||
                            it.sessionState == com.example.feature.session.domain.SessionState.PAUSED) &&
                            it.isAppBlockingEnabled &&
                            it.isYouTubeStudyModeEnabled
                } == true
                if (shouldStillEnforce) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            } finally {
                youtubeBackNavigationPending = false
            }
        }, 150L)
    }

    /**
     * Finds and clicks any active "Pause" or "Pause video" buttons in the accessibility tree.
     */
    private fun clickPauseButtonInNodeTree(rootNode: android.view.accessibility.AccessibilityNodeInfo?) {
        if (rootNode == null) return
        try {
            // Check view IDs containing pause
            val pauseViewIds = listOf(
                "com.google.android.youtube:id/player_control_play_pause_replay_button",
                "com.google.android.youtube:id/play_pause_button",
                "com.google.android.youtube:id/pause_button",
                "com.google.android.youtube:id/player_pause_button"
            )
            for (id in pauseViewIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes != null) {
                    for (node in nodes) {
                        val desc = node.contentDescription?.toString() ?: ""
                        val clicked = if (desc.contains("Pause", ignoreCase = true)) {
                            performClickOnNodeOrParent(node)
                        } else false
                        try { node.recycle() } catch (_: Exception) {}
                        if (clicked) {
                            return
                        }
                    }
                }
            }

            // Check descriptions containing "Pause video" or "Pause"
            val descKeywords = listOf("Pause video", "Pause")
            for (keyword in descKeywords) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
                if (nodes != null) {
                    for (node in nodes) {
                        val desc = node.contentDescription?.toString() ?: ""
                        val text = node.text?.toString() ?: ""
                        val matches = desc.equals("Pause video", ignoreCase = true) ||
                                desc.equals("Pause", ignoreCase = true) ||
                                text.equals("Pause", ignoreCase = true)
                        val clicked = if (matches) performClickOnNodeOrParent(node) else false
                        try { node.recycle() } catch (_: Exception) {}
                        if (clicked) {
                            return
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Instantly pauses and mutes video audio by requesting transient audio focus and dispatching media pause key event.
     */
    private fun silenceAndPausePlayback() {
        MediaPauseHelper.pauseMedia(this)
    }

    private fun switchYouTubeToHomeTab(rootNode: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        try {
            val homeIds = listOf(
                "com.google.android.youtube:id/pivot_home",
                "com.google.android.youtube:id/tab_home",
                "com.google.android.youtube:id/home_tab",
                "com.google.android.youtube:id/navigation_home"
            )
            for (id in homeIds) {
                val homeNodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!homeNodes.isNullOrEmpty()) {
                    for (node in homeNodes) {
                        val clicked = performClickOnNodeOrParent(node)
                        try { node.recycle() } catch (_: Exception) {}
                        if (clicked) {
                            return true
                        }
                    }
                }
            }

            val textNodes = rootNode.findAccessibilityNodeInfosByText("Home")
            if (!textNodes.isNullOrEmpty()) {
                for (node in textNodes) {
                    val desc = node.contentDescription?.toString() ?: ""
                    val txt = node.text?.toString() ?: ""
                    val matches = desc.equals("Home", ignoreCase = true) || txt.equals("Home", ignoreCase = true)
                    val clicked = if (matches) performClickOnNodeOrParent(node) else false
                    try { node.recycle() } catch (_: Exception) {}
                    if (clicked) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Navigates the user directly to the YouTube Search interface by clicking the in-app
     * search bar / search icon through the accessibility node tree. Used by the Study Mode
     * Home feed popup to move the user from passive feed browsing to intentional search.
     */
    private fun navigateToYouTubeSearch() {
        try {
            val rootNode = try { rootInActiveWindow } catch (e: Exception) { null } ?: return

            // 1. Direct search bar / search icon view IDs on the YouTube Home screen
            val searchViewIds = listOf(
                "com.google.android.youtube:id/search_anchor",
                "com.google.android.youtube:id/search_icon",
                "com.google.android.youtube:id/search_button",
                "com.google.android.youtube:id/menu_item_search",
                "com.google.android.youtube:id/action_search",
                "com.google.android.youtube:id/search_input_text"
            )
            for (id in searchViewIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val clicked = performClickOnNodeOrParent(node)
                        try { node.recycle() } catch (_: Exception) {}
                        if (clicked) {
                            try { rootNode.recycle() } catch (_: Exception) {}
                            return
                        }
                    }
                }
            }

            // 2. Fallback: nodes labeled "Search YouTube" / "Search"
            for (keyword in listOf("Search YouTube", "Search")) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val desc = node.contentDescription?.toString() ?: ""
                        val text = node.text?.toString() ?: ""
                        val matches = desc.equals(keyword, ignoreCase = true) || text.equals(keyword, ignoreCase = true)
                        val clicked = if (matches) performClickOnNodeOrParent(node) else false
                        try { node.recycle() } catch (_: Exception) {}
                        if (clicked) {
                            try { rootNode.recycle() } catch (_: Exception) {}
                            return
                        }
                    }
                }
            }
            try { rootNode.recycle() } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun performClickOnNodeOrParent(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        try {
            if (node.isClickable) {
                return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
            val parent = node.parent
            if (parent != null && parent.isClickable) {
                val res = parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                try { parent.recycle() } catch (_: Exception) {}
                return res
            }
            try { parent?.recycle() } catch (_: Exception) {}
        } catch (_: Exception) {}
        return false
    }

    /**
     * Safely navigates the browser away from a blocked website.
     * Tries in sequence:
     * 1. Clicking Chrome/browser "Close tab" button if opened as a fresh tab.
     * 2. Performing a single BACK action so the browser returns to previous search results.
     * 3. If standard back or tab close didn't redirect or exits browser, opens default search engine intent inside the browser.
     */
    private fun navigateBrowserAwayFromBlockedSite(
        browserPackage: String,
        rootNode: android.view.accessibility.AccessibilityNodeInfo?
    ) {
        lastBrowserBlockTimestamp = System.currentTimeMillis()

        // 1. Try closing tab if a close tab button is found
        if (rootNode != null) {
            val closeTabIds = listOf(
                "com.android.chrome:id/close_button",
                "com.android.chrome:id/tab_close_button",
                "org.mozilla.firefox:id/mozac_browser_toolbar_close",
                "com.sec.android.app.sbrowser:id/close_btn"
            )
            for (viewId in closeTabIds) {
                try {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                    if (nodes != null) {
                        for (node in nodes) {
                            val clicked = performClickOnNodeOrParent(node)
                            try { node.recycle() } catch (_: Exception) {}
                            if (clicked) {
                                return
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Perform a single Back action to step back to the search page or previous tab
        performGlobalAction(GLOBAL_ACTION_BACK)

        // 3. Fallback: If still stuck on the domain after 450ms, open clean Google search directly inside the browser package
        serviceScope.launch {
            kotlinx.coroutines.delay(450L)
            try {
                val currentRoot = try { rootInActiveWindow } catch (e: Exception) { null }
                val currentUrlInfo = com.example.core.detector.BrowserUrlDetector.extractUrlAndDomain(browserPackage, currentRoot, null)
                try { currentRoot?.recycle() } catch (_: Exception) {}
                val siteEngine = try { com.example.feature.websiteblocker.engine.WebsiteBlockerEngine.instance } catch (e: Exception) { null }
                val stillBlocked = currentUrlInfo?.let { siteEngine?.evaluateDomain(it.rawUrl, it.domain) is com.example.feature.websiteblocker.engine.WebsiteBlockDecision.Blocked } ?: false

                if (stillBlocked) {
                    val searchIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com")
                    ).apply {
                        `package` = browserPackage
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(searchIntent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceRunning = false
        instance = null
        com.example.core.tracking.PhoneUnlockTracker.unregister(this)
        com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
        Log.d(tag, "FocusAccessibilityService unbound.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        instance = null
        com.example.core.tracking.PhoneUnlockTracker.unregister(this)
        com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
        super.onDestroy()
        Log.d(tag, "FocusAccessibilityService destroyed.")
    }

    override fun onInterrupt() {
        Log.d(tag, "FocusAccessibilityService interrupted.")
    }
}
