package com.example.feature.blocker

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.accessibility.FocusProtectionState
import com.example.core.accessibility.ProtectionDecision
import com.example.core.accessibility.ProtectionPolicy
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import com.example.data.repository.BlockedAppRepository
import com.example.data.repository.BlockedAttemptRepository
import com.example.data.repository.StudyChannelRepository
import com.example.feature.session.domain.SessionState
import com.example.feature.session.domain.StrictModeConfig
import com.example.feature.session.engine.FocusSessionManager
import com.example.feature.youtube.detector.YouTubeDetectionRules
import com.example.feature.youtube.domain.YouTubeDetectionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Central Blocking Decision and Enforcement Manager for FocusShield.
 * Coordinates real-time blocking checks between the active focus session,
 * active breaks, strict mode rules, YouTube study mode, and the Android AccessibilityService.
 */
class FocusBlockerManager private constructor(
    private val appContext: Context,
    private val blockedAppRepository: BlockedAppRepository,
    private val blockedAttemptRepository: BlockedAttemptRepository,
    private val studyChannelRepository: StudyChannelRepository? = null,
    private val sessionManager: FocusSessionManager = FocusSessionManager.instance,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    // In-memory fast cache of currently enabled blocked package names
    private val enabledBlockedPackages = ConcurrentHashMap.newKeySet<String>()
    private val appLimitPackages = ConcurrentHashMap.newKeySet<String>()

    private var lastBlockedKey: String? = null
    private var lastBlockTimestamp: Long = 0L

    private val tag = "FocusBlockerManager"

    /** Last foreground package reported by the accessibility service; null until the first event. */
    @Volatile
    private var currentForegroundPackage: String? = null

    /** The block-screen launch each package's retry loop must attempt next. */
    private val pendingBlockIntents = ConcurrentHashMap<String, Intent>()

    /** In-flight block-screen verify/retry loops, one per package. */
    private val blockLaunchJobs = ConcurrentHashMap<String, Job>()

    private val blockVerifyDelayMs = 1_200L
    private val maxBlockLaunchAttempts = 4
    private val blockHeartbeatIntervalMs = 10_000L
    private val blockRetryHorizonMs = 5 * 60 * 1000L

    init {
        // Observe enabled blocked apps from Room database and maintain fast in-memory cache
        coroutineScope.launch {
            blockedAppRepository.enabledBlockedApps.collectLatest { apps ->
                enabledBlockedPackages.clear()
                apps.filter { it.isEnabled }.forEach {
                    enabledBlockedPackages.add(it.packageName)
                }
            }
        }

        // Observe active app limits from AppLimitRepository
        coroutineScope.launch {
            try {
                val appLimitRepo = com.example.FocusShieldApp.instance.appLimitRepository
                appLimitRepo.getActiveLimitsFlow().collectLatest { limits ->
                    appLimitPackages.clear()
                    limits.filter { it.isEnabled }.forEach {
                        appLimitPackages.add(it.packageName)
                    }
                }
            } catch (e: Exception) {
                // Safe catch if database not initialized
            }
        }
    }

    /**
     * Builds the current snapshot of FocusProtectionState for evaluation.
     */
    fun getCurrentProtectionState(now: Long = System.currentTimeMillis()): FocusProtectionState {
        val session = sessionManager.activeSession.value
        val state = sessionManager.sessionState.value
        val breakInfo = session?.manualBreak
        val isManualBreakActive = breakInfo != null && breakInfo.isActive && now < breakInfo.endsAt
        val isPomodoroBreakActive = session != null &&
                session.mode == com.example.data.model.SessionMode.POMODORO &&
                (session.pomodoroPhase == com.example.feature.session.domain.PomodoroPhase.SHORT_BREAK ||
                        session.pomodoroPhase == com.example.feature.session.domain.PomodoroPhase.LONG_BREAK) &&
                session.remainingDurationMillis > 0L
        val isBreakActive = isManualBreakActive || isPomodoroBreakActive
        val breakEndsAt = when {
            isManualBreakActive -> breakInfo!!.endsAt
            isPomodoroBreakActive -> now + (session?.remainingDurationMillis ?: 0L)
            else -> 0L
        }

        val approvedIds = studyChannelRepository?.getApprovedChannelIds() ?: emptySet()
        val approvedNames = studyChannelRepository?.getApprovedChannelNames() ?: emptySet()

        // Session choices are the source of truth for its one-time shield, so a just-started
        // session can block immediately without waiting for Room/Flow propagation. Permanent
        // blocks and active usage limits remain additive.
        val allBlocked = session?.blockedAppPackages.orEmpty() +
                enabledBlockedPackages.toSet()

        return FocusProtectionState(
            sessionId = session?.id,
            sessionState = state,
            isAppBlockingEnabled = session?.isAppBlockingEnabled ?: false,
            isStrictModeEnabled = session?.isStrictModeEnabled ?: false,
            strictModeConfig = session?.strictModeConfig ?: StrictModeConfig(),
            isBreakActive = isBreakActive,
            breakEndsAt = breakEndsAt,
            blockedPackages = allBlocked,
            isYouTubeStudyModeEnabled = session?.isStudyChannelsEnabled ?: true,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockShorts = true,
            blockUnknownContent = true,
            isBrowserStudyModeEnabled = session?.isBrowserStudyModeEnabled ?: true
        )
    }

    /**
     * Evaluates whether [packageName] and [youtubeResult] should be blocked or allowed according to ProtectionPolicy.
     */
    fun evaluateProtection(
        packageName: String,
        youtubeResult: YouTubeDetectionResult? = null,
        now: Long = System.currentTimeMillis()
    ): ProtectionDecision {
        val state = getCurrentProtectionState(now)
        return ProtectionPolicy.evaluate(
            packageName = packageName,
            state = state,
            youtubeResult = youtubeResult,
            currentTime = now,
            myPackageName = appContext.packageName
        )
    }

    /**
     * Determines whether the given decision requires blocking.
     */
    fun isBlockingDecision(decision: ProtectionDecision): Boolean {
        return decision == ProtectionDecision.BLOCK ||
                decision == ProtectionDecision.BLOCK_SHORTS ||
                decision == ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL ||
                decision == ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT ||
                decision == ProtectionDecision.BLOCK_UNINSTALL ||
                decision == ProtectionDecision.BLOCK_SPLIT_SCREEN ||
                decision == ProtectionDecision.BLOCK_FLOATING_WINDOW
    }

    /**
     * Determines whether the given package name should be blocked right now.
     */
    fun shouldBlock(packageName: String, youtubeResult: YouTubeDetectionResult? = null): Boolean {
        val decision = evaluateProtection(packageName, youtubeResult)
        return isBlockingDecision(decision)
    }

    /**
     * Called when a blocked application or blocked content is detected.
     * Records the attempt into Room and launches the blocking shield UI.
     *
     * [matchedKeyword] is only supplied when a keyword rule is what actually fired; the YouTube
     * branch has it on its block decision and passes it down, every other caller leaves it null.
     */
    fun handleBlockedPackage(
        packageName: String,
        fallbackAppName: String? = null,
        decision: ProtectionDecision = ProtectionDecision.BLOCK,
        youtubeResult: YouTubeDetectionResult? = null,
        matchedKeyword: String? = null
    ) {
        val now = System.currentTimeMillis()
        if (!isBlockingDecision(decision)) {
            return
        }

        // Prevent duplicate trigger bursts within 1.2 seconds for the same package and reason
        val triggerKey = "$packageName:${decision.name}:${youtubeResult?.contentType?.name ?: ""}"
        if (triggerKey == lastBlockedKey && (now - lastBlockTimestamp) < 1200) {
            return
        }
        lastBlockedKey = triggerKey
        lastBlockTimestamp = now

        val resolvedAppName = when (decision) {
            // Short-form covers YouTube Shorts, Instagram Reels and Facebook Reels; the caller knows
            // which one it actually was, so its label wins over the YouTube-only default.
            ProtectionDecision.BLOCK_SHORTS -> fallbackAppName ?: "YouTube Shorts"
            ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL -> {
                val chan = youtubeResult?.channelName ?: youtubeResult?.channelId ?: "Unapproved Channel"
                "YouTube ($chan)"
            }
            ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT -> "YouTube"
            ProtectionDecision.BLOCK_UNINSTALL -> "Settings / Uninstaller"
            ProtectionDecision.BLOCK_SPLIT_SCREEN -> "Split-Screen Multitasking"
            ProtectionDecision.BLOCK_FLOATING_WINDOW -> "Floating Window / PiP"
            else -> fallbackAppName ?: getAppNameFromPackage(packageName)
        }

        // The protection decision *is* the blocking rule that fired, so it is recorded as-is
        // rather than being re-derived later from the display name.
        val eventType = when (decision) {
            ProtectionDecision.BLOCK_SHORTS -> BlockedEventType.SHORTS_BLOCKED
            ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL -> BlockedEventType.YOUTUBE_UNAPPROVED_CHANNEL
            ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT -> BlockedEventType.YOUTUBE_UNKNOWN_CONTENT
            ProtectionDecision.BLOCK_SPLIT_SCREEN -> BlockedEventType.SPLIT_SCREEN_BLOCKED
            ProtectionDecision.BLOCK_FLOATING_WINDOW -> BlockedEventType.FLOATING_WINDOW_BLOCKED
            else -> BlockedEventType.APP_BLOCKED
        }

        val activeSessionId = sessionManager.activeSession.value?.id

        // Record attempt in database for analytics
        coroutineScope.launch {
            try {
                blockedAttemptRepository.recordAttempt(
                    packageName = packageName,
                    appName = resolvedAppName,
                    eventType = eventType,
                    source = BlockedEventSource.ACCESSIBILITY_SERVICE,
                    sessionId = activeSessionId,
                    channelId = youtubeResult?.channelId,
                    channelName = youtubeResult?.channelName,
                    videoTitle = youtubeResult?.videoTitle,
                    matchedKeyword = matchedKeyword,
                    ruleRef = decision.name
                )
            } catch (e: Exception) {
                // Room record logged safely
            }
        }

        // Launch blocking UI for whole app blocks, anti-tamper uninstall, split screen, and floating windows
        if (decision == ProtectionDecision.BLOCK ||
            decision == ProtectionDecision.BLOCK_UNINSTALL ||
            decision == ProtectionDecision.BLOCK_SPLIT_SCREEN ||
            decision == ProtectionDecision.BLOCK_FLOATING_WINDOW
        ) {
            // The block is already on screen — a second instance would finish the first one in
            // the launch handshake, exactly the race AppLimitManager guards against.
            if (BlockOverlayActivity.isVisibleFor(packageName)) {
                Log.d(tag, "Block screen already on screen for $packageName; not launching another instance")
                return
            }

            val intent = Intent(appContext, BlockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, packageName)
                putExtra(BlockOverlayActivity.EXTRA_BLOCKED_APP_NAME, resolvedAppName)
                putExtra(BlockOverlayActivity.EXTRA_BLOCK_DECISION, decision.name)
                putExtra(BlockOverlayActivity.EXTRA_CHANNEL_NAME, youtubeResult?.channelName ?: "")
                putExtra(BlockOverlayActivity.EXTRA_VIDEO_TITLE, youtubeResult?.videoTitle ?: "")
            }
            // A request, not a launch: handed to the verified retry loop. Android can silently
            // drop a startActivity (background-activity-start rules) without throwing, so the old
            // one-shot call could leave the app fully unblocked with nothing but a log line.
            pendingBlockIntents[packageName] = intent
            startBlockOverlayAndVerify(packageName)
        }
    }

    /** Called by the accessibility service on foreground window changes (see that call site). */
    fun onForegroundPackageChanged(packageName: String) {
        currentForegroundPackage = packageName
    }

    /**
     * Keeps trying to put the block screen for [packageName] on screen and verifies every attempt
     * against a real signal ([BlockOverlayActivity.isVisibleFor]), mirroring the app-limit
     * pipeline in AppLimitManager: `startActivity()` does not throw when Android drops the launch,
     * so "no exception" proves nothing (issue #27, item 5).
     *
     * The fast burst slows to a heartbeat, bounded by [blockRetryHorizonMs], and the loop stops
     * as soon as the user is known to be somewhere else — the service re-requests the block on
     * every event from the blocked app, which restarts it if the block is still owed.
     */
    private fun startBlockOverlayAndVerify(packageName: String) {
        if (blockLaunchJobs[packageName]?.isActive == true) {
            Log.d(tag, "Block retry loop already running for $packageName; updated its pending intent")
            return
        }

        blockLaunchJobs[packageName] = coroutineScope.launch {
            val startedAt = System.currentTimeMillis()
            var attempt = 0

            while (true) {
                if (BlockOverlayActivity.isVisibleFor(packageName)) {
                    Log.i(tag, "Block screen confirmed on screen for $packageName (after $attempt attempt(s))")
                    pendingBlockIntents.remove(packageName)
                    return@launch
                }

                val intent = pendingBlockIntents[packageName]
                if (intent == null) {
                    Log.d(tag, "Block request for $packageName cleared; stopping retry loop")
                    return@launch
                }

                attemptOverlayLaunch(intent)
                attempt++

                delay(if (attempt <= maxBlockLaunchAttempts) blockVerifyDelayMs else blockHeartbeatIntervalMs)

                if (System.currentTimeMillis() - startedAt >= blockRetryHorizonMs) {
                    Log.e(tag, "Could not display BlockOverlayActivity for $packageName after $attempt attempts in ${blockRetryHorizonMs / 1000}s")
                    pendingBlockIntents.remove(packageName)
                    return@launch
                }

                // Stop once the user is somewhere else: retrying would throw the block screen
                // over whatever they moved on to (e.g. FocusShield itself after "Return to
                // session"). Leaving the blocked app's package pending is safe — the service
                // re-requests on the next event from that app.
                if (!isBlockedAppInForeground(packageName)) {
                    Log.d(tag, "Leaving block pending for $packageName; user is no longer in the app")
                    pendingBlockIntents.remove(packageName)
                    return@launch
                }
            }
        }
    }

    /**
     * True while [packageName] is known to be on screen, or while the foreground app is simply
     * unknown (the service has not reported one yet) — same contract as the app-limit loop.
     */
    private fun isBlockedAppInForeground(packageName: String): Boolean {
        val foreground = currentForegroundPackage ?: return true
        return foreground == packageName
    }

    /**
     * One launch attempt. Prefers the accessibility service (an enabled service is exempt from
     * the background-activity-start restriction moreso than a plain app context), falling back to
     * the app context. Never assumes the attempt succeeded — see [startBlockOverlayAndVerify].
     */
    private fun attemptOverlayLaunch(intent: Intent) {
        val service = com.example.core.accessibility.FocusAccessibilityService.instance
        if (service != null) {
            try {
                service.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(tag, "FocusAccessibilityService failed to start block screen: ${e.message}")
            }
        }
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "appContext failed to start block screen: ${e.message}")
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = appContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: FocusBlockerManager? = null

        fun initialize(
            appContext: Context,
            blockedAppRepository: BlockedAppRepository,
            blockedAttemptRepository: BlockedAttemptRepository,
            studyChannelRepository: StudyChannelRepository? = null
        ): FocusBlockerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FocusBlockerManager(
                    appContext = appContext,
                    blockedAppRepository = blockedAppRepository,
                    blockedAttemptRepository = blockedAttemptRepository,
                    studyChannelRepository = studyChannelRepository
                )
                INSTANCE = instance
                instance
            }
        }

        val instance: FocusBlockerManager
            get() = INSTANCE ?: throw IllegalStateException("FocusBlockerManager must be initialized in Application.onCreate")
    }
}
