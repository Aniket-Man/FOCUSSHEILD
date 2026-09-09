package com.example.feature.blocker

import android.content.Context
import android.content.Intent
import com.example.core.accessibility.FocusProtectionState
import com.example.core.accessibility.ProtectionDecision
import com.example.core.accessibility.ProtectionPolicy
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
        val isBreakActive = breakInfo != null && breakInfo.isActive && now < breakInfo.endsAt

        val approvedIds = studyChannelRepository?.getApprovedChannelIds() ?: emptySet()
        val approvedNames = studyChannelRepository?.getApprovedChannelNames() ?: emptySet()

        // Session choices are the source of truth for its one-time shield, so a just-started
        // session can block immediately without waiting for Room/Flow propagation. Permanent
        // blocks and active usage limits remain additive.
        val allBlocked = session?.blockedAppPackages.orEmpty() +
                enabledBlockedPackages.toSet() +
                appLimitPackages.toSet()

        return FocusProtectionState(
            sessionId = session?.id,
            sessionState = state,
            isAppBlockingEnabled = session?.isAppBlockingEnabled ?: false,
            isStrictModeEnabled = session?.isStrictModeEnabled ?: false,
            strictModeConfig = session?.strictModeConfig ?: StrictModeConfig(),
            isBreakActive = isBreakActive,
            breakEndsAt = breakInfo?.endsAt ?: 0L,
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
     */
    fun handleBlockedPackage(
        packageName: String,
        fallbackAppName: String? = null,
        decision: ProtectionDecision = ProtectionDecision.BLOCK,
        youtubeResult: YouTubeDetectionResult? = null
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
            ProtectionDecision.BLOCK_SHORTS -> "YouTube Shorts"
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

        val activeSessionId = sessionManager.activeSession.value?.id

        // Record attempt in database for analytics
        coroutineScope.launch {
            try {
                blockedAttemptRepository.recordAttempt(
                    packageName = packageName,
                    appName = resolvedAppName,
                    sessionId = activeSessionId
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
            val intent = Intent(appContext, BlockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, packageName)
                putExtra(BlockOverlayActivity.EXTRA_BLOCKED_APP_NAME, resolvedAppName)
                putExtra(BlockOverlayActivity.EXTRA_BLOCK_DECISION, decision.name)
                putExtra(BlockOverlayActivity.EXTRA_CHANNEL_NAME, youtubeResult?.channelName ?: "")
                putExtra(BlockOverlayActivity.EXTRA_VIDEO_TITLE, youtubeResult?.videoTitle ?: "")
            }
            appContext.startActivity(intent)
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
