package com.example.feature.youtube.engine

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.feature.youtube.detector.ActiveVideoInfo
import com.example.feature.youtube.detector.ChannelApprovalStatus
import com.example.feature.youtube.detector.ChannelIdentityInfo
import com.example.feature.youtube.detector.ShortsConfidence
import com.example.feature.youtube.detector.ShortsDetectionResult
import com.example.feature.youtube.detector.YouTubeContentInspectionEngine
import com.example.feature.youtube.detector.YouTubeDetectionRules
import com.example.feature.youtube.detector.YouTubeShortsDetectionEngine
import com.example.feature.youtube.domain.DetectionConfidence
import com.example.feature.youtube.domain.YouTubeContentType
import com.example.feature.youtube.domain.YouTubeDetectionResult

/**
 * Screen states of YouTube.
 */
enum class YouTubeScreenState {
    NOT_YOUTUBE,
    HOME,
    SEARCH,
    SEARCH_RESULTS,
    CHANNEL_PAGE,
    VIDEO_WATCH,
    SHORTS_PLAYBACK,
    NAVIGATION,
    UNKNOWN
}

/**
 * Authoritative consolidated state snapshot of YouTube.
 */
data class YouTubeState(
    val screenState: YouTubeScreenState = YouTubeScreenState.UNKNOWN,
    val shortsConfidence: ShortsConfidence = ShortsConfidence.NONE,
    val isConsumingShort: Boolean = false,
    val currentVideo: ActiveVideoInfo? = null,
    val currentChannel: ChannelIdentityInfo? = null,
    val approvalStatus: ChannelApprovalStatus = ChannelApprovalStatus.UNKNOWN,
    val timestamp: Long = System.currentTimeMillis(),
    val diagnosticSignals: List<String> = emptyList()
)

/**
 * CENTRAL YOUTUBE STATE ENGINE
 *
 * Coordinates between:
 * - Engine 1: YouTubeShortsDetectionEngine (Is user consuming a Short?)
 * - Engine 2: YouTubeContentInspectionEngine (What video/channel is active?)
 *
 * Enforces strict separation of concerns, reconciles states, and produces
 * the authoritative YouTubeState and YouTubeDetectionResult for the central ProtectionPolicy.
 */
object YouTubeStateEngine {

    private const val TAG = "YouTubeStateEngine"

    @Volatile
    private var lastAuthoritativeState: YouTubeState = YouTubeState()

    /**
     * Inspects YouTube accessibility event and node tree, returning the authoritative YouTubeDetectionResult.
     */
    fun evaluate(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        packageName: String?,
        approvedIds: Set<String> = emptySet(),
        approvedNames: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis()
    ): YouTubeDetectionResult {
        // 1. Verify YouTube package
        if (!YouTubeDetectionRules.isYouTubePackage(packageName)) {
            lastAuthoritativeState = YouTubeState(screenState = YouTubeScreenState.NOT_YOUTUBE, timestamp = now)
            return YouTubeDetectionResult.NOT_YOUTUBE
        }

        val allDiagnostics = mutableListOf<String>()

        // 2. RUN ENGINE 1: Dedicated Shorts Detection Engine
        val shortsResult: ShortsDetectionResult = YouTubeShortsDetectionEngine.inspect(rootNode, event)
        allDiagnostics.addAll(shortsResult.matchedSignals)

        // 3. RUN ENGINE 2: Dedicated Content & Channel Inspection Engine
        val contentResult = YouTubeContentInspectionEngine.inspect(
            rootNode = rootNode,
            event = event,
            approvedIds = approvedIds,
            approvedNames = approvedNames,
            now = now
        )
        allDiagnostics.addAll(contentResult.matchedSignals)

        // 4. STATE RECONCILIATION & CONFLICT RESOLUTION
        val authoritativeScreenState: YouTubeScreenState
        val finalContentType: YouTubeContentType
        val isShortPlayback: Boolean

        if (shortsResult.isConsumingShort) {
            // CONFIRMED SHORTS CONSUMPTION
            authoritativeScreenState = YouTubeScreenState.SHORTS_PLAYBACK
            finalContentType = YouTubeContentType.SHORT
            isShortPlayback = true
            allDiagnostics.add("resolved:SHORTS_PLAYBACK")
        } else {
            isShortPlayback = false
            when (contentResult.contentType) {
                YouTubeContentType.SEARCH_QUERY -> {
                    authoritativeScreenState = YouTubeScreenState.SEARCH
                    finalContentType = YouTubeContentType.SEARCH_QUERY
                    allDiagnostics.add("resolved:SEARCH")
                }
                YouTubeContentType.SEARCH_RESULTS -> {
                    authoritativeScreenState = YouTubeScreenState.SEARCH_RESULTS
                    finalContentType = YouTubeContentType.SEARCH_RESULTS
                    allDiagnostics.add("resolved:SEARCH_RESULTS")
                }
                YouTubeContentType.CHANNEL_PAGE -> {
                    authoritativeScreenState = YouTubeScreenState.CHANNEL_PAGE
                    finalContentType = YouTubeContentType.CHANNEL_PAGE
                    allDiagnostics.add("resolved:CHANNEL_PAGE")
                }
                YouTubeContentType.VIDEO_PLAYBACK -> {
                    authoritativeScreenState = YouTubeScreenState.VIDEO_WATCH
                    finalContentType = YouTubeContentType.VIDEO_PLAYBACK
                    allDiagnostics.add("resolved:VIDEO_WATCH")
                }
                YouTubeContentType.YOUTUBE_HOME -> {
                    authoritativeScreenState = YouTubeScreenState.HOME
                    finalContentType = YouTubeContentType.YOUTUBE_HOME
                    allDiagnostics.add("resolved:HOME")
                }
                else -> {
                    authoritativeScreenState = YouTubeScreenState.NAVIGATION
                    finalContentType = YouTubeContentType.YOUTUBE_NAVIGATION
                    allDiagnostics.add("resolved:NAVIGATION")
                }
            }
        }

        // 5. STORE AUTHORITATIVE STATE SNAPSHOT
        val stateSnapshot = YouTubeState(
            screenState = authoritativeScreenState,
            shortsConfidence = shortsResult.confidence,
            isConsumingShort = isShortPlayback,
            currentVideo = contentResult.activeVideo,
            currentChannel = contentResult.channelInfo,
            approvalStatus = contentResult.approvalStatus,
            timestamp = now,
            diagnosticSignals = allDiagnostics
        )
        lastAuthoritativeState = stateSnapshot

        // 6. BUILD FINAL RESULT FOR PROTECTION POLICY
        val mappedConfidence = when (shortsResult.confidence) {
            ShortsConfidence.CONFIRMED_SHORT -> DetectionConfidence.HIGH
            ShortsConfidence.PROBABLE_SHORT -> DetectionConfidence.MEDIUM
            ShortsConfidence.LOW_CONFIDENCE, ShortsConfidence.NONE -> contentResult.confidence
        }

        return YouTubeDetectionResult(
            contentType = finalContentType,
            channelId = contentResult.channelInfo.channelHandle ?: contentResult.channelInfo.channelId,
            channelName = contentResult.channelInfo.channelName,
            videoTitle = contentResult.activeVideo?.title,
            isShort = isShortPlayback,
            confidence = mappedConfidence,
            matchedRule = allDiagnostics.joinToString(";")
        )
    }

    /**
     * Called when the user exits YouTube or switches to a non-YouTube application.
     * Clears all short-lived channel context and resets state.
     */
    fun onYouTubeExited() {
        YouTubeContentInspectionEngine.clearChannelContext()
        lastAuthoritativeState = YouTubeState(screenState = YouTubeScreenState.NOT_YOUTUBE)
        try {
            Log.d(TAG, "YouTube exited: cleared channel context and reset state.")
        } catch (e: Throwable) {
            // Android Log not mocked in non-Robolectric JVM tests
        }
    }

    /**
     * Returns the current internal diagnostic state for developer troubleshooting and tests.
     */
    fun getDiagnosticSnapshot(): YouTubeState {
        return lastAuthoritativeState
    }
}
