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
import com.example.feature.applimits.engine.AppLimitPackageFilter
import com.example.feature.blocker.FocusBlockerManager
import com.example.feature.youtube.detector.YouTubeDetectionRules
import com.example.feature.youtube.engine.YouTubeBlockDecision
import com.example.feature.youtube.engine.YouTubeBlockVerdict
import com.example.feature.youtube.engine.YouTubeContentBlockEngine
import com.example.feature.youtube.engine.YouTubeNodeExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

        @Volatile
        var instance: FocusAccessibilityService? = null
            private set
    }

    private val tag = "FocusAccessibility"
    // var + per-bind recycle: the system can rebind this same service instance after onUnbind,
    // so every bind starts a fresh scope and unbind/destroy cancels the current one — no
    // preference/session collectors or delayed work outlive their bind (issue #16).
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
    private val lastAppLimitCheckTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Approved-content watch-time capture. Every call site treats a missing tracker as "no capture"
     * rather than an error — blocking must never depend on analytics being available.
     */
    private fun dwellTracker(): com.example.feature.youtube.engine.YouTubeStudyDwellTracker? = try {
        com.example.feature.youtube.engine.YouTubeStudyDwellTracker.instance
    } catch (_: Exception) {
        null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        instance = this
        // Start every bind with a fresh scope (defensively dropping anything left from a
        // previous bind of this same instance) before launching the long-lived collectors.
        serviceScope.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        AccessibilityHelper.notifyServiceConnected(this)
        com.example.core.permission.FocusPermissionManager.notifyAccessibilityChanged(this, true)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
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

        // Track screen state so watch-time capture stops when the screen goes dark
        com.example.core.tracking.ScreenStateTracker.register(this)

        // Run the app-limit gate once against whatever is actually on screen (issue #49, O1).
        // Every other path into the gate is an accessibility event, and a static screen behind a
        // freshly killed process emits none — the blocker owed to the limited app the user was
        // still sitting in never came back until they happened to touch something.
        // FLAG_RETRIEVE_INTERACTIVE_WINDOWS (set above) lets rootInActiveWindow answer without
        // waiting for an event; the 1 s delay gives the window stack and the freshly restored
        // sessions a beat to settle. onUnbind's removeCallbacksAndMessages drops the callback if
        // the service is torn down first.
        mainThreadHandler.postDelayed({ evaluateGateFromCurrentWindow() }, 1000L)

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
                    // The session a segment belongs to is over, so close it before `activeSession` is
                    // cleared — the tracker stamps the segment with the session it opened under.
                    dwellTracker()?.flush("session ended")
                }
            }
        }
    }

    private var lastForegroundPackage: String? = null
    private var lastShortFormInspectTimestamp: Long = 0L
    private var lastBrowserInspectTimestamp: Long = 0L
    private var lastBrowserBlockTimestamp: Long = 0L
    private var lastYouTubeBlockTimestamp: Long = 0L
    private var lastYouTubeInspectTimestamp: Long = 0L
    private var lastYouTubeSandboxCheckTimestamp: Long = 0L
    private var lastTamperCheckTimestamp: Long = 0L
    private var lastSplitScreenCheckTimestamp: Long = 0L
    private var lastFloatingWindowCheckTimestamp: Long = 0L
    private var homeFeedPopupDismissed = false
    private var youtubeBackNavigationPending = false
    private var lastBlockedYouTubeVideoKey: String? = null
    private val mainThreadHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val youtubePostClickSweepToken = Any()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        // typeWindowsChanged is declared in accessibility_service_config.xml and both foreground
        // tracking and the app-limit gate below test for it explicitly — but it used to be dropped
        // here, so those branches were unreachable and FocusShield noticed far fewer app switches
        // (and therefore re-checked app limits far less often) than it was written to.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            return
        }

        val rawPackageName = event.packageName?.toString() ?: return
        if (rawPackageName.isBlank()) return

        val isOwnApp = com.example.feature.applimits.engine.AppLimitPackageFilter
            .isOwnApp(rawPackageName)

        if (isOwnApp) {
            if (lastForegroundPackage != null &&
                YouTubeDetectionRules.isYouTubePackage(lastForegroundPackage)
            ) {
                YouTubeContentBlockEngine.reset()
                dwellTracker()?.flush("left YouTube")
            }
            lastForegroundPackage = rawPackageName
            com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
            homeFeedPopupDismissed = false
            // Keep the app-limit engine's foreground model honest, but only when FocusShield is
            // genuinely the active window. Our floating blocker is a non-focusable accessibility
            // overlay: it can emit events carrying our package while the limited app is still the
            // active window behind it. Treating those as "the user opened FocusShield" would hide
            // the blocker the moment it appeared. The active root tells the two apart.
            val activeWindowPackage = try {
                rootInActiveWindow?.packageName?.toString()
            } catch (_: Exception) {
                null
            }
            if (activeWindowPackage != null &&
                com.example.feature.applimits.engine.AppLimitPackageFilter.isOwnApp(activeWindowPackage)
            ) {
                try {
                    AppLimitManager.instance.onForegroundPackageChanged(rawPackageName)
                } catch (_: Exception) {
                    // Manager not initialized yet (early service bind) — nothing to pause.
                }
            }
            return
        }

        try {
            val blockerManager = try { FocusBlockerManager.instance } catch (e: Exception) { null }
            val appLimitManager = try { AppLimitManager.instance } catch (e: Exception) { null }
            val now = System.currentTimeMillis()

            // Update foreground tracking on window state changes — and, until something has been
            // tracked at all, on whatever the first event is (issue #49, W2). After a service
            // (re)bind the first event to arrive can be WINDOW_CONTENT_CHANGED/VIEW_SCROLLED
            // instead of a window transition; gating only on window-state changes then left
            // lastForegroundPackage/currentForegroundPackage null until one happened, so limit
            // evaluation ran blind and isBlockedAppInForeground() could not tell where the user
            // was. Seeding on the first event is idempotent for the common case (the event's own
            // package) and the very next window-state change corrects any exotic mismatch.
            val isWindowStateChange = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            val isTrackedPackageSwitch = rawPackageName != lastForegroundPackage &&
                    com.example.feature.applimits.engine.AppLimitPackageFilter.shouldTrackForeground(rawPackageName)
            if (isWindowStateChange || lastForegroundPackage == null || isTrackedPackageSwitch) {
                if (lastForegroundPackage != null &&
                    YouTubeDetectionRules.isYouTubePackage(lastForegroundPackage) &&
                    !YouTubeDetectionRules.isYouTubePackage(rawPackageName)
                ) {
                    YouTubeContentBlockEngine.reset()
                    dwellTracker()?.flush("left YouTube")
                }
                if (com.example.feature.applimits.engine.AppLimitPackageFilter.shouldTrackForeground(rawPackageName) ||
                    isWindowStateChange
                ) {
                    lastForegroundPackage = rawPackageName
                }

                // Focus-blocker's verified retry loop stops when the user has left the blocked
                // app. This signal is deliberately unfiltered (unlike the app-limit call below):
                // leaving via BlockOverlayActivity's "Return to session" lands in FocusShield
                // itself, and that must count as having left the blocked app too.
                blockerManager?.onForegroundPackageChanged(rawPackageName)
                
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
                // Issue #16: settings/uninstaller screens emit content-change bursts; scan the
                // tree at most every 500ms unless the window itself changed.
                val isTamperWindowChange = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                        eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                if (isTamperWindowChange || (now - lastTamperCheckTimestamp) >= 500L) {
                    lastTamperCheckTimestamp = now
                    val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                    try {
                        if (rootNode != null) {
                            val isTamper = isTamperOrUninstallAttempt(rootNode, rawPackageName)
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
                    } catch (_: Exception) {}
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
            val isYouTubeStudyModeActive = YouTubeDetectionRules.isYouTubePackage(rawPackageName) &&
                    (blockerManager?.getCurrentProtectionState(now)?.let {
                        (it.sessionState == com.example.feature.session.domain.SessionState.RUNNING ||
                                it.sessionState == com.example.feature.session.domain.SessionState.PAUSED) &&
                                it.isYouTubeStudyModeEnabled
                    } == true)

            val isShortFormTracked = !isYouTubeStudyModeActive && isShortFormBlockingEnabledFor(rawPackageName)
            if (isShortFormTracked) {
                // Throttle window content inspection to prevent CPU overhead (min 150ms between scans)
                if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    (now - lastShortFormInspectTimestamp) < 150L
                ) {
                    // Skip throttled tick
                } else {
                    lastShortFormInspectTimestamp = now
                    val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                    try {
                        val isYT = YouTubeDetectionRules.isYouTubePackage(rawPackageName)

                    // YouTube uses the reel-player-container detection which never fires
                    // for Shorts shelves embedded in the Home feed; other apps keep the
                    // generic short-form detector.
                    val shortFormDetected = if (isYT) {
                        YouTubeNodeExtractor.detectShorts(rootNode, event)
                    } else {
                        shortFormDetectionManager.detectShortFormContent(rawPackageName, event, rootNode)
                    }

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
                                val didBlock = handleYouTubeShortsBlocked()
                                if (didBlock) {
                                    com.example.core.overlay.FocusDisplayOverlayNotificationManager.showShortsBlockedHud(this)
                                    com.example.core.notification.FocusShieldBlockNotificationHelper.notifyShortsBlocked(this)
                                }
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
                } catch (_: Exception) {}
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

                            // 2. Display non-intrusive alert HUD & system notification
                            if (siteDecision.engineType == "ADULT_AUTOMATIC") {
                                com.example.core.overlay.FocusDisplayOverlayNotificationManager.showAdultSiteBlockedHud(this, siteDecision.domain)
                                com.example.core.notification.FocusShieldBlockNotificationHelper.notifyAdultSiteBlocked(this, siteDecision.domain)
                            } else {
                                com.example.core.overlay.FocusDisplayOverlayNotificationManager.showCustomWebsiteBlockedHud(this, siteDecision.domain)
                                com.example.core.notification.FocusShieldBlockNotificationHelper.notifyCustomWebsiteBlocked(this, siteDecision.domain)
                            }

                            // 3. Record the event through the repository (single write path → sync)
                            val blockedAttemptRepo =
                                try { FocusShieldApp.instance.blockedAttemptRepository } catch (e: Exception) { null }
                            val activeSessionId = blockerManager?.getCurrentProtectionState(now)?.sessionId
                            val isAdultAuto = siteDecision.engineType == "ADULT_AUTOMATIC"
                            serviceScope.launch {
                                try {
                                    blockedAttemptRepo?.recordAttempt(
                                        packageName = rawPackageName,
                                        appName = if (isAdultAuto) {
                                            "18+ Adult Site (${siteDecision.domain})"
                                        } else {
                                            "Blocked Website (${siteDecision.domain})"
                                        },
                                        eventType = com.example.data.local.entity.BlockedEventType.WEBSITE_BLOCKED,
                                        source = com.example.data.local.entity.BlockedEventSource.ACCESSIBILITY_SERVICE,
                                        sessionId = activeSessionId,
                                        domain = siteDecision.domain,
                                        ruleRef = siteDecision.engineType,
                                        timestamp = now
                                    )
                                } catch (_: Exception) {}
                            }
                            return
                        }
                    }
                }
            }

            // 2. YouTube Study Mode Inspection Flow (Focus Session & Channels Rules)
            if (YouTubeDetectionRules.isYouTubePackage(rawPackageName) && blockerManager != null) {
                // If recently redirected back from a blocked channel/short, allow a 2.5-second grace period
                // so the YouTube UI can land on the previous screen without re-triggering blocks
                if (youtubeBackNavigationPending || (now - lastYouTubeBlockTimestamp) < 2500L) {
                    return
                }

                val protectionState = blockerManager.getCurrentProtectionState(now)
                val isSessionRunningOrPaused =
                    protectionState.sessionState.name == "RUNNING" || protectionState.sessionState.name == "PAUSED"
                val isBreakActiveNow = protectionState.isBreakActive && now < protectionState.breakEndsAt

                if (isSessionRunningOrPaused && isBreakActiveNow) {
                    // During an active Manual or Pomodoro break, do not block YouTube or bill study time
                    com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                    homeFeedPopupDismissed = false
                    lastBlockedYouTubeVideoKey = null
                    dwellTracker()?.flush("break active")
                } else if (isSessionRunningOrPaused && !protectionState.isYouTubeStudyModeEnabled) {
                    // YouTube Study Mode disabled for this session: apply standard whole-app block if YouTube is in blockedPackages
                    com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                    homeFeedPopupDismissed = false
                    lastBlockedYouTubeVideoKey = null
                    dwellTracker()?.flush("study mode disabled")
                    if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                        val decision = blockerManager.evaluateProtection(rawPackageName, now = now)
                        if (decision == ProtectionDecision.BLOCK) {
                            blockerManager.handleBlockedPackage(
                                packageName = rawPackageName,
                                fallbackAppName = null,
                                decision = decision
                            )
                            return
                        }
                    }
                } else if (isSessionRunningOrPaused) {
                    val eventType = event?.eventType
                    val isYouTubeWindowChange = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    val isClickEvent = eventType == AccessibilityEvent.TYPE_VIEW_CLICKED

                    if (isClickEvent || isYouTubeWindowChange) {
                        com.example.feature.youtube.detector.YouTubePlaybackSandbox.onVideoNavigationOccurred(now)
                        YouTubeContentBlockEngine.onUserNavigationOrClick(event, now)
                        if (isClickEvent) {
                            scheduleYouTubePostClickVerifications(rawPackageName)
                        }
                    }

                    // --- SAFE CORNER SANDBOX (PASSIVE MONITORING STATE) ---
                    // Once an approved channel is confirmed during a session, monitoring is moved into a passive state
                    // where expensive node tree walks and continuous text extractions are suspended until a new
                    // video navigation or window transition occurs, preventing UI lag and continuous redundant scans.
                    if (com.example.feature.youtube.detector.YouTubePlaybackSandbox.isSandboxActive) {
                        // Even if YouTube's Litho feed consumes a tap without emitting TYPE_VIEW_CLICKED or
                        // autoplays into a new video, run a lightweight watch-header title/channel check every 500ms.
                        var sandboxStillValid = true
                        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                            (now - lastYouTubeSandboxCheckTimestamp) >= 500L
                        ) {
                            lastYouTubeSandboxCheckTimestamp = now
                            val checkRoot = try { rootInActiveWindow } catch (_: Exception) { null }
                            sandboxStillValid = YouTubeContentBlockEngine.checkSandboxStillValid(
                                rootNode = checkRoot,
                                event = event,
                                approvedChannelIds = protectionState.approvedChannelIds,
                                approvedChannelNames = protectionState.approvedChannelNames,
                                currentTime = now
                            )
                        }

                        if (sandboxStillValid) {
                            // PASSIVE STATE: Monitoring is suspended for routine scroll, selection, and content change events!
                            val chan = com.example.feature.youtube.detector.YouTubePlaybackSandbox.sandboxedChannel?.channelName
                                ?: "Approved Channel"
                            val videoTitle = com.example.feature.youtube.detector.YouTubePlaybackSandbox.sandboxedVideoTitle

                            com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                            homeFeedPopupDismissed = false
                            lastBlockedYouTubeVideoKey = null

                            dwellTracker()?.onAllowedContent(
                                channelName = chan,
                                videoTitle = videoTitle
                            )
                            evaluateAppLimitGate(rawPackageName, eventType ?: 0, now)
                            return
                        }
                    }

                    if (!isYouTubeWindowChange && !isClickEvent && (now - lastYouTubeInspectTimestamp) < 250L) {
                        evaluateAppLimitGate(rawPackageName, eventType ?: 0, now)
                        return
                    }
                    lastYouTubeInspectTimestamp = now
                    val rootNode = try { rootInActiveWindow } catch (e: Exception) { null }
                    try {
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

                        // Approved content is the only verdict that earns watch time. When an ad is
                        // playing on an approved channel (decision.isAdPlaying == true), we allow
                        // the ad to finish so the lecture can start, without billing ad time as study.
                        if (decision.verdict == YouTubeBlockVerdict.ALLOW && !decision.isAdPlaying) {
                            lastBlockedYouTubeVideoKey = null
                            dwellTracker()?.onAllowedContent(
                                channelName = decision.detectedChannel,
                                videoTitle = decision.detectedTitle
                            )
                        } else {
                            if (!decision.isBlocking) {
                                lastBlockedYouTubeVideoKey = null
                            }
                            dwellTracker()?.flush(
                                if (decision.isAdPlaying) "ad playback" else "verdict ${decision.verdict}"
                            )
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
                                    val didBlock = handleYouTubeShortsBlocked()
                                    if (didBlock) {
                                        com.example.core.overlay.FocusDisplayOverlayNotificationManager.showShortsBlockedHud(this)
                                        com.example.core.notification.FocusShieldBlockNotificationHelper.notifyShortsBlocked(this)
                                    }
                                }
                                YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL,
                                YouTubeBlockVerdict.BLOCK_UNKNOWN_CONTENT -> {
                                    val chan = decision.detectedChannel ?: "Unapproved Channel"
                                    val videoKey = "${decision.detectedChannel.orEmpty()}|${decision.detectedTitle.orEmpty()}"
                                    val didBlock = handleYouTubeUnapprovedChannelBlocked(rootNode, videoKey)
                                    if (didBlock) {
                                        com.example.core.overlay.FocusDisplayOverlayNotificationManager.showChannelBlockedHud(this, chan)
                                        com.example.core.notification.FocusShieldBlockNotificationHelper.notifyChannelBlocked(this, chan)
                                    }
                                }
                                else -> {
                                    // Block overlay is launched directly over the blocked app by blockerManager
                                }
                            }

                            val protectionDecision = when (decision.verdict) {
                                YouTubeBlockVerdict.BLOCK_SHORTS -> ProtectionDecision.BLOCK_SHORTS
                                YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL -> ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL
                                YouTubeBlockVerdict.BLOCK_UNKNOWN_CONTENT -> ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT
                                else -> ProtectionDecision.BLOCK
                            }

                            blockerManager.handleBlockedPackage(
                                packageName = rawPackageName,
                                fallbackAppName = null,
                                decision = protectionDecision,
                                youtubeResult = youtubeResult,
                                matchedKeyword = decision.matchedKeyword
                            )
                            return
                        }
                    } catch (_: Exception) {}
                } else {
                    // Session no longer active while YouTube is open: remove any lingering Study Mode popup
                    com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                    homeFeedPopupDismissed = false
                    lastBlockedYouTubeVideoKey = null
                    dwellTracker()?.flush("session not running")
                }
            } else {
                com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
                homeFeedPopupDismissed = false
                if (blockerManager != null &&
                    (isWindowStateChange || isTrackedPackageSwitch || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                ) {
                    // 3. Standard Focus Session App Blocking flow (Priority 1)
                    val decision = blockerManager.evaluateProtection(rawPackageName, now = now)
                    if (decision == ProtectionDecision.BLOCK) {
                        Log.i(tag, "Distraction app detected in Focus Session: $rawPackageName. Enforcing FocusShield block.")
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
            evaluateAppLimitGate(rawPackageName, eventType, now)
        } catch (e: Exception) {
            Log.e(tag, "Error handling accessibility event: ${e.message}")
        }
    }

    /**
     * Section 4 of the event pipeline: the timed App Limit gate for [rawPackageName].
     *
     * An accessibility event is the normal way in here, but it is not the only way this must
     * run: a static screen behind a killed process (a paused video, an idle feed) emits no
     * events at all, so after a process restart the blocker the user was owed never came back
     * until they happened to touch something (issue #49, O1). [evaluateGateFromCurrentWindow]
     * drives this same function from `onServiceConnected`, which fires on every process restart
     * while the service is enabled. Everything the gate needs is resolved here rather than
     * captured from the caller so both entry points behave identically.
     */
    private fun evaluateAppLimitGate(rawPackageName: String, eventType: Int, now: Long) {
        val appLimitManager = try { AppLimitManager.instance } catch (e: Exception) { null }
        if (appLimitManager == null) return

        val isYouTubePackage = YouTubeDetectionRules.isYouTubePackage(rawPackageName)

        // The blocker for this app is already on screen: nothing to decide, and nothing to
        // launch. Without this the gate re-raised the popup on every event the limited app
        // emitted from *behind* the blocker (500 ms throttle) — a new activity instance on
        // top of the one being read, each one finishing the previous one. A launch that
        // lands in that handshake is dropped silently, which is how the blocker could
        // disappear for good; the storm also re-sent the media-pause key and re-scanned the
        // node tree five times a second while the user was trying to read the popup.
        val isBlockerOnScreen = com.example.feature.applimits.ui.AppLimitOverlayActivity
            .isVisibleFor(rawPackageName) ||
            com.example.feature.applimits.ui.AppLimitFloatingOverlayManager
                .isShowingFor(rawPackageName)

        // Study Mode no longer exempts app limits (issue #71). During a study session this
        // gate used to skip YouTube (and browsers) entirely, so the YouTube daily limit was
        // never evaluated and its watch time was freely billed to the study session. Study
        // Mode still owns what YouTube *shows* (Shorts, unapproved channels, home feed) via
        // the content engine — the app limit independently decides whether the app may be
        // used at all, session or not.
        if (com.example.feature.applimits.engine.AppLimitGatePolicy
                .shouldEvaluate(rawPackageName, isBlockerOnScreen)
        ) {
            val isLimited = appLimitManager.isPackageLimited(rawPackageName)
            val isSessionActive = appLimitManager.isSessionActiveFor(rawPackageName)

            val lastCheckTime = lastAppLimitCheckTimestamps[rawPackageName] ?: 0L
            val isWindowStateChange = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

            val shouldCheck = (isWindowStateChange && (now - lastCheckTime > 300L)) ||
                    (isLimited && !isSessionActive && (now - lastCheckTime > 500L))

            if (shouldCheck) {
                lastAppLimitCheckTimestamps[rawPackageName] = now
                serviceScope.launch {
                    when (val appLimitDecision = appLimitManager.checkAppLimitDecision(rawPackageName)) {
                        is AppLimitDecision.REQUIRE_USAGE_SELECTION -> {
                            Log.i(tag, "App Limit: Prompting usage selection for ${appLimitDecision.appName} ($rawPackageName)")
                            // Media is paused by AppLimitManager.launchOverlay itself, once per
                            // launch request rather than once per accessibility event.
                            if (isYouTubePackage) {
                                val currentRoot = try { rootInActiveWindow } catch (e: Exception) { null }
                                clickPauseButtonInNodeTree(currentRoot)
                            }
                            // Floor the remaining millis themselves (issue #49, C2):
                            // subtracting floored used minutes from the limit rounded the
                            // remaining *up* — "1m remaining" while seconds were left.
                            val usedMins = (appLimitDecision.usedDailyMillis / 60000L).toInt().coerceAtLeast(0)
                            val remMins = (appLimitDecision.remainingDailyMillis / 60000L).toInt().coerceAtLeast(0)
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
                            Log.i(tag, "App Limit: Daily limit exhausted for ${appLimitDecision.appName} ($rawPackageName). Enforcing blocker.")
                            // Media is paused by AppLimitManager.launchOverlay itself (see above).
                            if (isYouTubePackage) {
                                val currentRoot = try { rootInActiveWindow } catch (e: Exception) { null }
                                clickPauseButtonInNodeTree(currentRoot)
                            }
                            appLimitManager.launchOverlay(
                                packageName = appLimitDecision.packageName,
                                appName = appLimitDecision.appName,
                                mode = AppLimitOverlayMode.DAILY_LIMIT_REACHED,
                                // Actual time spent, not the configured limit: the blocker's
                                // "Spent today" card was pinned to the limit regardless of how
                                // much over it the user actually was (milliseconds can carry
                                // past the limit before the gate fires).
                                usedMinutes = (appLimitDecision.usedDailyMillis / 60000L)
                                    .toInt().coerceAtLeast(0),
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

    /**
     * One-shot app-limit evaluation from the real foreground window, driven by
     * [onServiceConnected] (issue #49, O1).
     *
     * Reads the window stack directly instead of waiting for an event, seeds the foreground
     * trackers from it — pause/resume bookkeeping and the blocker retry loop's "are we still in
     * the blocked app" answer both start from reality rather than null — then runs exactly the
     * gate an accessibility event would have run. FocusShield itself (the screen that usually
     * just enabled the service) is ignored, mirroring [onAccessibilityEvent]'s own-app early
     * return.
     */
    private fun evaluateGateFromCurrentWindow() {
        val root = try { rootInActiveWindow } catch (e: Exception) { null }
        val foregroundPackage = root?.packageName?.toString()
        if (foregroundPackage.isNullOrBlank()) return

        val isOwnApp = com.example.feature.applimits.engine.AppLimitPackageFilter
            .isOwnApp(foregroundPackage)
        if (isOwnApp) {
            // Seed the engine's foreground model too: if the service reconnects while FocusShield
            // is open during a session, the session must start parked, not keep ticking behind
            // our own UI.
            try {
                AppLimitManager.instance.onForegroundPackageChanged(foregroundPackage)
            } catch (_: Exception) {
                // Manager not initialized yet — nothing to park.
            }
            return
        }

        Log.i(tag, "Service connected with $foregroundPackage in the foreground; running the app-limit gate once")

        val blockerManager = try { FocusBlockerManager.instance } catch (e: Exception) { null }
        val appLimitManager = try { AppLimitManager.instance } catch (e: Exception) { null }

        lastForegroundPackage = foregroundPackage
        blockerManager?.onForegroundPackageChanged(foregroundPackage)
        appLimitManager?.onForegroundPackageChanged(foregroundPackage)

        evaluateAppLimitGate(
            rawPackageName = foregroundPackage,
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            now = System.currentTimeMillis()
        )
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

        if (rootNode == null) return false
        val allTexts = mutableListOf<String>()
        val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        while (queue.isNotEmpty() && visited < 100) {
            val node = queue.removeFirst()
            visited++
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { allTexts.add(it.lowercase()) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { allTexts.add(it.lowercase()) }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        val combinedText = allTexts.joinToString(" ")

        // Exclude legitimate permission setup, battery optimization, and accessibility setup screens
        val safeSetupKeywords = listOf(
            "battery", "optimize", "unrestricted", "background", "notification",
            "accessibility", "special app access", "usage", "appear on top",
            "display over", "draw over", "manage app permissions", "allow"
        )
        if (safeSetupKeywords.any { combinedText.contains(it) }) {
            return false
        }

        // Search for references to FocusShield or our package in the settings/uninstaller UI
        val hasAppMatch = combinedText.contains("focusshield") || combinedText.contains(packageName.lowercase())

        if (hasAppMatch) {
            // Check specifically for uninstallation or clear data/force stop actions
            if (rawPackageName.contains("packageinstaller") || rawPackageName.contains("settings")) {
                val uninstallKeywords = listOf("uninstall", "do you want to uninstall", "delete app", "clear storage", "clear data", "force stop", "deactivate")
                if (uninstallKeywords.any { combinedText.contains(it) }) {
                    return true
                }
            }

            // In settings or launcher context menus
            if (combinedText.contains("do you want to uninstall")) {
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
     * Schedules delayed verification sweeps after a click inside YouTube so that once YouTube
     * finishes loading a newly clicked video from the below-player feed (and swapping its watch
     * header metadata), the new video is guaranteed to be verified even if no further user touches occur.
     */
    private fun scheduleYouTubePostClickVerifications(rawPackageName: String) {
        mainThreadHandler.removeCallbacksAndMessages(youtubePostClickSweepToken)
        val sweepDelays = longArrayOf(400L, 950L, 1700L, 2600L)
        for (delayMs in sweepDelays) {
            androidx.core.os.HandlerCompat.postDelayed(
                mainThreadHandler,
                {
                    try {
                        val fgPkg = lastForegroundPackage ?: rawPackageName
                        if (!YouTubeDetectionRules.isYouTubePackage(fgPkg) || youtubeBackNavigationPending) {
                            return@postDelayed
                        }
                        val blockerManager = try { FocusBlockerManager.instance } catch (_: Exception) { null }
                            ?: return@postDelayed
                        val sweepNow = System.currentTimeMillis()
                        val protectionState = blockerManager.getCurrentProtectionState(sweepNow)
                        val isRunningOrPaused =
                            protectionState.sessionState.name == "RUNNING" ||
                            protectionState.sessionState.name == "PAUSED"
                        val isBreakNow = protectionState.isBreakActive && sweepNow < protectionState.breakEndsAt
                        if (!isRunningOrPaused || isBreakNow || !protectionState.isYouTubeStudyModeEnabled) {
                            return@postDelayed
                        }

                        val rootNode = try { rootInActiveWindow } catch (_: Exception) { null } ?: return@postDelayed
                        if (com.example.feature.youtube.detector.YouTubePlaybackSandbox.isSandboxActive) {
                            val valid = YouTubeContentBlockEngine.checkSandboxStillValid(
                                rootNode = rootNode,
                                event = null,
                                approvedChannelIds = protectionState.approvedChannelIds,
                                approvedChannelNames = protectionState.approvedChannelNames,
                                currentTime = sweepNow
                            )
                            if (valid) return@postDelayed
                        }

                        val decision = YouTubeContentBlockEngine.evaluate(
                            rootNode = rootNode,
                            event = null,
                            approvedChannelIds = protectionState.approvedChannelIds,
                            approvedChannelNames = protectionState.approvedChannelNames,
                            blockUnknownContent = protectionState.blockUnknownContent,
                            currentTime = sweepNow
                        )

                        if (decision.verdict == YouTubeBlockVerdict.ALLOW && !decision.isAdPlaying) {
                            lastBlockedYouTubeVideoKey = null
                            dwellTracker()?.onAllowedContent(
                                channelName = decision.detectedChannel,
                                videoTitle = decision.detectedTitle
                            )
                        } else if (decision.isBlocking) {
                            mainThreadHandler.removeCallbacksAndMessages(youtubePostClickSweepToken)
                            dwellTracker()?.flush("verdict ${decision.verdict}")
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
                                    val didBlock = handleYouTubeShortsBlocked()
                                    if (didBlock) {
                                        com.example.core.overlay.FocusDisplayOverlayNotificationManager.showShortsBlockedHud(this)
                                        com.example.core.notification.FocusShieldBlockNotificationHelper.notifyShortsBlocked(this)
                                    }
                                }
                                YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL,
                                YouTubeBlockVerdict.BLOCK_UNKNOWN_CONTENT -> {
                                    val chan = decision.detectedChannel ?: "Unapproved Channel"
                                    val videoKey = "${decision.detectedChannel.orEmpty()}|${decision.detectedTitle.orEmpty()}"
                                    val didBlock = handleYouTubeUnapprovedChannelBlocked(rootNode, videoKey)
                                    if (didBlock) {
                                        com.example.core.overlay.FocusDisplayOverlayNotificationManager.showChannelBlockedHud(this, chan)
                                        com.example.core.notification.FocusShieldBlockNotificationHelper.notifyChannelBlocked(this, chan)
                                    }
                                }
                                else -> {}
                            }

                            val protectionDecision = when (decision.verdict) {
                                YouTubeBlockVerdict.BLOCK_SHORTS -> ProtectionDecision.BLOCK_SHORTS
                                YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL -> ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL
                                YouTubeBlockVerdict.BLOCK_UNKNOWN_CONTENT -> ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT
                                else -> ProtectionDecision.BLOCK
                            }

                            blockerManager.handleBlockedPackage(
                                packageName = fgPkg,
                                fallbackAppName = null,
                                decision = protectionDecision,
                                youtubeResult = youtubeResult,
                                matchedKeyword = decision.matchedKeyword
                            )
                        }
                    } catch (_: Exception) {}
                },
                youtubePostClickSweepToken,
                delayMs
            )
        }
    }

    /**
     * Handles YouTube Shorts blocking: every Short is blocked instantly with NO pause —
     * a single immediate step back returns the user to the previous feed.
     */
    private fun handleYouTubeShortsBlocked(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastYouTubeBlockTimestamp < 2000L) {
            return false
        }
        lastYouTubeBlockTimestamp = now

        YouTubeContentBlockEngine.reset()
        val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
        if (!backSuccess) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        return true
    }

    /**
     * Handles YouTube Unapproved Channel (and unapproved channel ad) blocking:
     * 1. Stops playback ONCE (media pause key + explicit pause control click).
     * 2. Steps back ONCE from the watch/continue screen to the previous list/search/home screen.
     */
    private fun handleYouTubeUnapprovedChannelBlocked(
        rootNode: android.view.accessibility.AccessibilityNodeInfo?,
        videoKey: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        if (youtubeBackNavigationPending || (now - lastYouTubeBlockTimestamp) < 2500L) {
            return false
        }
        if (!videoKey.isNullOrBlank() &&
            videoKey != "|" &&
            videoKey == lastBlockedYouTubeVideoKey &&
            (now - lastYouTubeBlockTimestamp) < 8000L
        ) {
            return false
        }
        lastYouTubeBlockTimestamp = now
        lastBlockedYouTubeVideoKey = videoKey
        youtubeBackNavigationPending = true

        YouTubeContentBlockEngine.reset()

        // Step 1 (One time): Stop the video/ad playback immediately
        silenceAndPausePlayback()
        clickPauseButtonInNodeTree(rootNode)

        // Step 2 (One time): Step back once from the watch/continue screen
        mainThreadHandler.postDelayed({
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (_: Exception) {}
            mainThreadHandler.postDelayed({
                youtubeBackNavigationPending = false
                try {
                    val afterBackRoot = try { rootInActiveWindow } catch (_: Exception) { null }
                    YouTubeNodeExtractor.dismissMiniplayerIfPresent(afterBackRoot)
                } catch (_: Exception) {}
            }, 350L)
        }, 160L)
        return true
    }

    private fun collapseYouTubePlayerOrBack(rootNode: android.view.accessibility.AccessibilityNodeInfo?) {
        if (rootNode != null) {
            val collapseIds = listOf(
                "com.google.android.youtube:id/watch_collapse_button",
                "com.google.android.youtube:id/collapse_button",
                "com.google.android.youtube:id/action_bar_back",
                "com.google.android.youtube:id/up_button"
            )
            val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visited = 0
            while (queue.isNotEmpty() && visited < 100) {
                val node = queue.removeFirst()
                visited++
                val resId = node.viewIdResourceName ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                val isCollapse = collapseIds.any { resId.contains(it, ignoreCase = true) } ||
                        desc.equals("Collapse", ignoreCase = true) ||
                        desc.equals("Navigate up", ignoreCase = true) ||
                        desc.equals("Close", ignoreCase = true)
                if (isCollapse) {
                    if (performClickOnNodeOrParent(node)) {
                        return
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) queue.add(child)
                }
            }
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Finds and clicks an active "Pause" or "Pause video" button in the accessibility tree using BFS.
     * Crucially checks that the button's label explicitly represents the playing ("Pause") state and
     * never clicks a Play/Pause toggle when it is already paused ("Play video").
     */
    private fun clickPauseButtonInNodeTree(rootNode: android.view.accessibility.AccessibilityNodeInfo?) {
        if (rootNode == null) return
        try {
            val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visited = 0
            while (queue.isNotEmpty() && visited < 80) {
                val node = queue.removeFirst()
                visited++
                val resId = node.viewIdResourceName?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val text = node.text?.toString()?.trim() ?: ""
                val isExplicitPauseState =
                    desc.equals("Pause", ignoreCase = true) ||
                    desc.equals("Pause video", ignoreCase = true) ||
                    text.equals("Pause", ignoreCase = true) ||
                    text.equals("Pause video", ignoreCase = true) ||
                    (resId.contains("pause_button") && !resId.contains("play_pause") && !desc.contains("Play", ignoreCase = true))
                if (isExplicitPauseState) {
                    if (performClickOnNodeOrParent(node)) {
                        return
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) queue.add(child)
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
            val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visited = 0
            while (queue.isNotEmpty() && visited < 80) {
                val node = queue.removeFirst()
                visited++
                val resId = node.viewIdResourceName?.lowercase() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                val text = node.text?.toString() ?: ""
                val isHome = resId.contains("pivot_home") || resId.contains("tab_home") ||
                        resId.contains("home_tab") || resId.contains("navigation_home") ||
                        desc.equals("Home", ignoreCase = true) || text.equals("Home", ignoreCase = true)
                if (isHome) {
                    if (performClickOnNodeOrParent(node)) {
                        return true
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) queue.add(child)
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
            val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visited = 0
            while (queue.isNotEmpty() && visited < 80) {
                val node = queue.removeFirst()
                visited++
                val resId = node.viewIdResourceName?.lowercase() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                val text = node.text?.toString() ?: ""
                val isSearch = resId.contains("search") ||
                        desc.contains("Search", ignoreCase = true) ||
                        text.contains("Search", ignoreCase = true)
                if (isSearch) {
                    if (performClickOnNodeOrParent(node)) {
                        return
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) queue.add(child)
                }
            }
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
                return parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            }
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
            try {
                val queue = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
                queue.add(rootNode)
                var visited = 0
                while (queue.isNotEmpty() && visited < 60) {
                    val node = queue.removeFirst()
                    visited++
                    val resId = node.viewIdResourceName?.lowercase() ?: ""
                    if (resId.contains("close_button") || resId.contains("tab_close_button") ||
                        resId.contains("toolbar_close") || resId.contains("close_btn")
                    ) {
                        if (performClickOnNodeOrParent(node)) {
                            return
                        }
                    }
                    for (i in 0 until node.childCount) {
                        val child = try { node.getChild(i) } catch (_: Exception) { null }
                        if (child != null) queue.add(child)
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Perform a single Back action to step back to the search page or previous tab
        performGlobalAction(GLOBAL_ACTION_BACK)

        // 3. Fallback: If still stuck on the domain after 450ms, open clean Google search directly inside the browser package
        serviceScope.launch {
            kotlinx.coroutines.delay(450L)
            try {
                val currentRoot = try { rootInActiveWindow } catch (e: Exception) { null }
                val currentUrlInfo = com.example.core.detector.BrowserUrlDetector.extractUrlAndDomain(browserPackage, currentRoot, null)
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
        // Tear down bind-scoped work: collectors in serviceScope and any pending delayed
        // runnables (post-back navigation, search click) must not run against an unbound
        // service. onServiceConnected rebuilds the scope on the next bind (issue #16).
        serviceScope.cancel()
        mainThreadHandler.removeCallbacksAndMessages(null)
        AccessibilityHelper.notifyServiceDisconnected()
        com.example.core.permission.FocusPermissionManager.notifyAccessibilityChanged(this, false)
        com.example.core.tracking.PhoneUnlockTracker.unregister(this)
        com.example.core.tracking.ScreenStateTracker.unregister(this)
        // Close any open watch-time segment: the service is what observes YouTube, so nothing will
        // ever report the end of this one once it is gone.
        dwellTracker()?.flush("service unbound")
        com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
        com.example.feature.applimits.ui.AppLimitFloatingOverlayManager.hideOverlay()
        Log.d(tag, "FocusAccessibilityService unbound.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        instance = null
        serviceScope.cancel()
        mainThreadHandler.removeCallbacksAndMessages(null)
        AccessibilityHelper.notifyServiceDisconnected()
        com.example.core.permission.FocusPermissionManager.notifyAccessibilityChanged(this, false)
        com.example.core.tracking.PhoneUnlockTracker.unregister(this)
        com.example.core.tracking.ScreenStateTracker.unregister(this)
        dwellTracker()?.flush("service destroyed")
        com.example.feature.youtube.overlay.YouTubeHomeFeedOverlayManager.hideHomeFeedPopup()
        com.example.feature.applimits.ui.AppLimitFloatingOverlayManager.hideOverlay()
        super.onDestroy()
        Log.d(tag, "FocusAccessibilityService destroyed.")
    }

    override fun onInterrupt() {
        Log.d(tag, "FocusAccessibilityService interrupted.")
    }
}
