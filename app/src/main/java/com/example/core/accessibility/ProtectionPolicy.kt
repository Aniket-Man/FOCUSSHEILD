package com.example.core.accessibility

import com.example.feature.session.domain.SessionState
import com.example.feature.session.domain.StrictModeConfig
import com.example.feature.youtube.detector.YouTubeDetectionRules
import com.example.feature.youtube.domain.YouTubeContentType
import com.example.feature.youtube.domain.YouTubeDetectionResult
import java.util.Locale

/**
 * Encapsulates the complete real-time Focus Protection state for decision making.
 */
data class FocusProtectionState(
    val sessionId: String? = null,
    val sessionState: SessionState = SessionState.IDLE,
    val isAppBlockingEnabled: Boolean = true,
    val isStrictModeEnabled: Boolean = false,
    val strictModeConfig: StrictModeConfig = StrictModeConfig(),
    val isBreakActive: Boolean = false,
    val breakEndsAt: Long = 0L,
    val blockedPackages: Set<String> = emptySet(),
    val isYouTubeStudyModeEnabled: Boolean = true,
    val approvedChannelIds: Set<String> = emptySet(),
    val approvedChannelNames: Set<String> = emptySet(),
    val blockShorts: Boolean = true,
    val blockUnknownContent: Boolean = true,
    val isBrowserStudyModeEnabled: Boolean = false
) {
    val isSessionActive: Boolean
        get() = sessionState == SessionState.RUNNING || sessionState == SessionState.PAUSED
}

/**
 * The deterministic outcome of a protection policy evaluation.
 */
enum class ProtectionDecision {
    ALLOW_SYSTEM,
    ALLOW_FOCUSSHIELD,
    ALLOW_INACTIVE_SESSION,
    ALLOW_BLOCKING_DISABLED,
    ALLOW_DURING_BREAK,
    ALLOW_UNBLOCKED_APP,
    ALLOW_APPROVED_CHANNEL,
    ALLOW_NAVIGATION,
    BLACKOUT_YOUTUBE_FEED,
    BLOCK,
    BLOCK_SHORTS,
    BLOCK_UNAPPROVED_CHANNEL,
    BLOCK_UNKNOWN_YOUTUBE_CONTENT,
    BLOCK_UNINSTALL,
    BLOCK_SPLIT_SCREEN,
    BLOCK_FLOATING_WINDOW
}

/**
 * Central deterministic policy determining whether a foreground application or content is permitted or blocked.
 * Used by FocusBlockerManager and FocusAccessibilityService.
 */
object ProtectionPolicy {

    val SAFE_SYSTEM_PACKAGES: Set<String> = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.android.launcher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.server.telecom"
    )

    /**
     * Evaluates whether [packageName] and [youtubeResult] is allowed or blocked based on current [state].
     */
    fun evaluate(
        packageName: String,
        state: FocusProtectionState,
        youtubeResult: YouTubeDetectionResult? = null,
        currentTime: Long = System.currentTimeMillis(),
        myPackageName: String = "com.example"
    ): ProtectionDecision {
        if (packageName.isBlank()) return ProtectionDecision.ALLOW_SYSTEM

        // 1. FocusShield App & subcomponents
        if (isFocusShield(packageName, myPackageName)) {
            return ProtectionDecision.ALLOW_FOCUSSHIELD
        }

        // 2. Android critical system components & accessibility safety
        if (SAFE_SYSTEM_PACKAGES.contains(packageName)) {
            return ProtectionDecision.ALLOW_SYSTEM
        }

        // 3. Only active RUNNING sessions enforce blocking
        if (state.sessionState != SessionState.RUNNING) {
            return ProtectionDecision.ALLOW_INACTIVE_SESSION
        }

        // 4. Check if session has app blocking enabled
        if (!state.isAppBlockingEnabled) {
            return ProtectionDecision.ALLOW_BLOCKING_DISABLED
        }

        // 5. Check if a temporary manual break is active and not expired
        if (state.isBreakActive && currentTime < state.breakEndsAt) {
            return ProtectionDecision.ALLOW_DURING_BREAK
        }

        // 6. Handle YouTube with Study Mode exception rules
        val isYouTube = YouTubeDetectionRules.isYouTubePackage(packageName)
        if (isYouTube) {
            return evaluateYouTubeContent(state, youtubeResult, packageName)
        }

        // 6B. Handle Browsers with Study Mode exception rules
        val isBrowser = com.example.core.detector.BrowserUrlDetector.isBrowserPackage(packageName)
        if (isBrowser) {
            return if (state.isBrowserStudyModeEnabled) {
                // Browser Study Mode is enabled: allow opening browser, searching, and reading allowed sites.
                // Malicious/adult/custom blocked domains are filtered dynamically in real time.
                ProtectionDecision.ALLOW_NAVIGATION
            } else {
                val isPackageBlocked = state.blockedPackages.contains(packageName) ||
                        state.blockedPackages.any { com.example.core.detector.BrowserUrlDetector.isBrowserPackage(it) }
                if (isPackageBlocked) ProtectionDecision.BLOCK else ProtectionDecision.ALLOW_UNBLOCKED_APP
            }
        }

        // 7. General non-YouTube apps: Block if in user's configured blocked packages
        if (state.blockedPackages.contains(packageName)) {
            return ProtectionDecision.BLOCK
        }

        return ProtectionDecision.ALLOW_UNBLOCKED_APP
    }

    /**
     * Dedicated deterministic policy for YouTube content when YouTube is encountered.
     * Implements the 4-case Matrix:
     * - Case 1: Normal Block OFF, Study Mode OFF -> ALLOW_UNBLOCKED_APP
     * - Case 2: Normal Block ON, Study Mode OFF -> BLOCK
     * - Case 3: Normal Block OFF, Study Mode ON -> YouTube Study Mode Policy (Navigation allowed, Shorts blocked, unapproved videos blocked)
     * - Case 4: Normal Block ON, Study Mode ON -> YouTube Study Mode Policy (Study Mode takes priority over whole-app block!)
     */
    private fun evaluateYouTubeContent(
        state: FocusProtectionState,
        youtubeResult: YouTubeDetectionResult?,
        packageName: String
    ): ProtectionDecision {
        // If YouTube Study Mode is disabled, follow normal App-level blocking rules
        if (!state.isYouTubeStudyModeEnabled) {
            val isPackageBlocked = state.blockedPackages.contains(packageName) ||
                    state.blockedPackages.any { YouTubeDetectionRules.isYouTubePackage(it) }
            return if (isPackageBlocked) ProtectionDecision.BLOCK else ProtectionDecision.ALLOW_UNBLOCKED_APP
        }

        // YouTube Study Mode IS ENABLED (Takes priority over generic block list):
        // Rule A: Shorts are ALWAYS blocked unconditionally, even from approved channels
        if (youtubeResult?.isShort == true || youtubeResult?.contentType == YouTubeContentType.SHORT) {
            return ProtectionDecision.BLOCK_SHORTS
        }

        // Rule B: Navigation surfaces MUST BE ALLOWED:
        // YouTube App Launch, Search query input, Search results list, Channel page browsing
        if (youtubeResult != null) {
            when (youtubeResult.contentType) {
                YouTubeContentType.YOUTUBE_HOME -> {
                    // The Home feed is a NAVIGATION SURFACE ONLY — never approved/whitelisted content.
                    // Show the Study Mode distraction popup over the feed (feed remains visible),
                    // nudging the user from passive browsing toward intentional Search.
                    return ProtectionDecision.BLACKOUT_YOUTUBE_FEED
                }
                YouTubeContentType.SEARCH_QUERY,
                YouTubeContentType.SEARCH_RESULTS,
                YouTubeContentType.CHANNEL_PAGE,
                YouTubeContentType.SUBSCRIPTIONS,
                YouTubeContentType.YOUTUBE_NAVIGATION -> {
                    return ProtectionDecision.ALLOW_NAVIGATION
                }
                YouTubeContentType.VIDEO_PLAYBACK,
                YouTubeContentType.VIDEO -> {
                    // Active Video Playback: Check if channel is approved
                    val isApproved = isChannelApproved(
                        candidateId = youtubeResult.channelId,
                        candidateName = youtubeResult.channelName,
                        approvedIds = state.approvedChannelIds,
                        approvedNames = state.approvedChannelNames
                    )

                    return if (isApproved) {
                        ProtectionDecision.ALLOW_APPROVED_CHANNEL
                    } else if (!youtubeResult.channelName.isNullOrBlank() || !youtubeResult.channelId.isNullOrBlank()) {
                        // Channel IS identified but NOT in the approved list -> block
                        ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL
                    } else if (youtubeResult.matchedRule?.contains("loading_grace_period") == true) {
                        // Channel metadata is still loading in UI: allow grace window for channel name to finish loading
                        ProtectionDecision.ALLOW_NAVIGATION
                    } else if (state.blockUnknownContent) {
                        // Playback was positively detected but its metadata did not appear within
                        // the inspection window.  Do not let a permanently unidentifiable video
                        // become an approval bypass; the inspection engine only reaches this
                        // branch after its short loading grace period has elapsed.
                        ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT
                    } else {
                        // Channel identity could not be extracted from the accessibility tree
                        // and this installation has explicitly opted out of unknown-content
                        // blocking.  Navigation remains available in that case.
                        ProtectionDecision.ALLOW_NAVIGATION
                    }
                }
                YouTubeContentType.UNKNOWN -> {
                    // Unknown UI node state within YouTube should default to ALLOW_NAVIGATION so user can browse/search
                    return ProtectionDecision.ALLOW_NAVIGATION
                }
                else -> {}
            }
        }

        // Default fallback for YouTube when Study Mode is enabled: allow navigation
        return ProtectionDecision.ALLOW_NAVIGATION
    }

    /**
     * Checks whether candidate channel ID or Name matches the approved set.
     */
    fun isChannelApproved(
        candidateId: String?,
        candidateName: String?,
        approvedIds: Set<String>,
        approvedNames: Set<String>
    ): Boolean {
        if (!candidateId.isNullOrBlank()) {
            val normId = candidateId.trim().lowercase(Locale.ROOT)
            if (approvedIds.contains(normId)) return true
            if (normId.startsWith("@") && approvedIds.contains(normId.removePrefix("@"))) return true
            if (approvedIds.contains("@$normId")) return true
        }

        if (!candidateName.isNullOrBlank()) {
            val normCandidate = normalize(candidateName)
            if (approvedNames.contains(normCandidate)) return true
            for (approved in approvedNames) {
                val normApproved = normalize(approved)
                if (normApproved.isNotBlank() && (normCandidate.contains(normApproved) || normApproved.contains(normCandidate))) {
                    return true
                }
            }
        }

        return false
    }

    private fun normalize(str: String): String {
        return str.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "").trim()
    }

    private fun isFocusShield(packageName: String, myPackageName: String): Boolean {
        return packageName == myPackageName ||
                packageName.startsWith("com.example") ||
                packageName.startsWith("com.aistudio.focusshield")
    }
}
