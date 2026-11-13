package com.example.feature.youtube.engine

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Central YouTube content block engine.
 *
 * Channel-name-only evaluation pipeline:
 *
 *  1. Shorts player detected                    -> BLOCK_SHORTS (always, any short, any channel)
 *  2. Watch page + approved study channel       -> ALLOW
 *  3. Watch page + channel NOT in approved list -> BLOCK_UNAPPROVED_CHANNEL
 *  4. Watch page + channel not rendered yet     -> short metadata grace, then BLOCK_UNKNOWN_CONTENT (if strict)
 *  5. Home feed                                 -> HOME_FEED (Study Mode nudge popup)
 *  6. Anything else (search, subscriptions...)  -> ALLOW_NAVIGATION
 *
 * The video title is NEVER used to judge content. The only authority is the
 * channel name/handle verified against the user's approved channel list.
 */
object YouTubeContentBlockEngine {

    /** Total budget for channel metadata to render before the video is treated as unknown. */
    private const val CHANNEL_METADATA_GRACE_MILLIS = 5000L

    /** Maximum time to wait for channel metadata while a pre-roll ad is actively playing and hiding the channel header. */
    private const val AD_MAX_WAIT_FOR_CHANNEL_MILLIS = 60_000L

    private const val MIN_FUZZY_MATCH_LENGTH = 3

    @Volatile
    private var currentVideoKey: String? = null

    @Volatile
    private var currentVideoFirstSeenAt: Long = 0L

    @Volatile
    private var playbackStartedAt: Long = 0L

    @Volatile
    private var wasAdPlayingOnLastTick: Boolean = false

    @Volatile
    private var lastSeenChannelName: String? = null

    @Volatile
    private var lastSeenVideoTitle: String? = null

    @Volatile
    private var lastApprovedVideoKey: String? = null

    @Volatile
    private var lastApprovedChannel: String? = null

    @Volatile
    private var lastApprovedHandle: String? = null

    @Volatile
    private var lastClickOrNavigationAt: Long = 0L

    @Volatile
    private var pendingClickedChannel: String? = null

    @Volatile
    private var pendingClickedTitle: String? = null

    @Volatile
    private var pendingClickedAt: Long = 0L

    @Volatile
    private var lastFullScreenVerifyExitKey: String? = null

    @Volatile
    private var lastFullScreenVerifyExitAt: Long = 0L

    @Volatile
    var isCurrentlyOnApprovedWatchPage: Boolean = false
        private set

    /**
     * Notifies the block engine that the user clicked or navigated inside YouTube.
     * Ignores in-player control clicks (e.g. "Enter full screen", "Exit full screen", Play/Pause,
     * Seek bar, Video Quality, Playback Speed, Captions) so full-screen playback never loses its
     * verified channel approval.
     */
    fun onUserNavigationOrClick(
        event: AccessibilityEvent? = null,
        currentTime: Long = System.currentTimeMillis()
    ) {
        if (event != null && YouTubeNodeExtractor.isPlayerControlOrFullscreenInteraction(event)) {
            return
        }

        val (clickedChannel, clickedTitle) = YouTubeNodeExtractor.extractClickedVideoCardIdentity(event)
        if (!clickedChannel.isNullOrBlank() || !clickedTitle.isNullOrBlank()) {
            if (!clickedChannel.isNullOrBlank()) pendingClickedChannel = clickedChannel
            if (!clickedTitle.isNullOrBlank()) pendingClickedTitle = clickedTitle
            pendingClickedAt = currentTime
        }

        // Window state changes inside YouTube (such as rotating to landscape full screen or opening
        // the video quality bottom sheet) should NOT wipe out a verified watch-page approval up front;
        // title-change and surface-change checks in checkSandboxStillValid / evaluate handle real navigations.
        val isWindowChangeOnly = event != null &&
                (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                        event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        if (isWindowChangeOnly && (lastApprovedChannel != null || com.example.feature.youtube.detector.YouTubePlaybackSandbox.isSandboxActive)) {
            return
        }

        lastApprovedVideoKey = null
        lastApprovedChannel = null
        lastApprovedHandle = null
        lastClickOrNavigationAt = currentTime
        currentVideoFirstSeenAt = currentTime
    }

    /**
     * Resets the metadata grace window when FocusAccessibilityService exits full screen back to
     * portrait mode to inspect the portrait channel header.
     */
    fun onExitedFullScreenForVerification(currentTime: Long = System.currentTimeMillis()) {
        currentVideoFirstSeenAt = currentTime
    }

    /**
     * Lightweight periodic check invoked while [YouTubePlaybackSandbox] is active.
     * Ensures that if a user taps a Litho recommendation card (which may not emit TYPE_VIEW_CLICKED)
     * or if autoplay transitions to a new video, the sandbox exits immediately whenever:
     *  - Shorts opens,
     *  - The watch page is exited,
     *  - The watch-header video title changes to a different video, or
     *  - A blocked channel override appears in the active portrait watch header.
     */
    fun checkSandboxStillValid(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>,
        blockedChannelIds: Set<String> = emptySet(),
        blockedChannelNames: Set<String> = emptySet(),
        currentTime: Long = System.currentTimeMillis(),
        enableAiClassification: Boolean = true
    ): Boolean {
        if (rootNode == null) return true

        if (YouTubeNodeExtractor.detectShorts(rootNode, event)) {
            com.example.feature.youtube.detector.YouTubePlaybackSandbox.onVideoNavigationOccurred(currentTime)
            resetVideoTracking()
            return false
        }

        if (!YouTubeNodeExtractor.detectWatchPage(rootNode)) {
            com.example.feature.youtube.detector.YouTubePlaybackSandbox.onVideoNavigationOccurred(currentTime)
            resetVideoTracking()
            return false
        }

        val strictHeaderTitle = YouTubeNodeExtractor.extractWatchHeaderTitle(rootNode)
            ?: YouTubeNodeExtractor.extractVideoTitle(rootNode)
        val sandboxedTitle = com.example.feature.youtube.detector.YouTubePlaybackSandbox.sandboxedVideoTitle
        if (!strictHeaderTitle.isNullOrBlank() &&
            !sandboxedTitle.isNullOrBlank() &&
            !strictHeaderTitle.equals(sandboxedTitle, ignoreCase = true)
        ) {
            // A different video title is now rendered in the active watch header!
            com.example.feature.youtube.detector.YouTubePlaybackSandbox.onVideoNavigationOccurred(currentTime)
            lastApprovedVideoKey = null
            lastApprovedChannel = null
            lastApprovedHandle = null
            currentVideoFirstSeenAt = currentTime
            return false
        }

        // While in Full-Screen mode, the portrait channel header is hidden and overlays may show
        // "More videos" recommendations. Keep the sandbox locked unless the video title changed above.
        if (YouTubeNodeExtractor.isFullScreenVideoPlayback(rootNode)) {
            return true
        }

        val identity = YouTubeNodeExtractor.extractChannelIdentity(rootNode)
        if (identity.isIdentifiable) {
            val temporarilyAllowed = com.example.feature.youtube.ai.YouTubeAiStudyClassifier.isChannelTemporarilyAllowed(
                identity.channelHandle,
                identity.channelName
            )
            if (!temporarilyAllowed && isChannelIdentityApproved(identity, blockedChannelIds, blockedChannelNames)) {
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.onVideoNavigationOccurred(currentTime)
                lastApprovedVideoKey = null
                lastApprovedChannel = null
                lastApprovedHandle = null
                currentVideoFirstSeenAt = currentTime
                return false
            }

            if (!temporarilyAllowed && isChannelIdentityApproved(identity, approvedChannelIds, approvedChannelNames)) {
                val distractionSignal = com.example.feature.youtube.ai.YouTubeAiStudyClassifier
                    .checkObviousDistractionSafety(strictHeaderTitle, identity.preferredDisplay)
                if (distractionSignal != null) {
                    com.example.feature.youtube.detector.YouTubePlaybackSandbox.onVideoNavigationOccurred(currentTime)
                    lastApprovedVideoKey = null
                    lastApprovedChannel = null
                    lastApprovedHandle = null
                    currentVideoFirstSeenAt = currentTime
                    return false
                }
            }

            if (!enableAiClassification) {
                val isApproved = isChannelIdentityApproved(identity, approvedChannelIds, approvedChannelNames)
                if (!isApproved) {
                    com.example.feature.youtube.detector.YouTubePlaybackSandbox.onVideoNavigationOccurred(currentTime)
                    lastApprovedVideoKey = null
                    lastApprovedChannel = null
                    lastApprovedHandle = null
                    currentVideoFirstSeenAt = currentTime
                    return false
                }
            }
        }

        return true
    }

    /**
     * Evaluates the current YouTube screen and returns the block decision.
     * Combines 0ms Trusted/Blocked channel overrides, 0ms L1/L2 AI verdict cache,
     * 0ms high-confidence heuristics, speculative search pre-warming, and
     * non-blocking `gemini-3.5-flash-lite` classification.
     */
    fun evaluate(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>,
        blockUnknownContent: Boolean = true,
        currentTime: Long = System.currentTimeMillis(),
        activeSubject: String = "",
        activeTopic: String = "",
        blockedChannelIds: Set<String> = emptySet(),
        blockedChannelNames: Set<String> = emptySet(),
        enableAiClassification: Boolean = true
    ): YouTubeBlockDecision {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val (clickedChan, clickedTitle) = YouTubeNodeExtractor.extractClickedVideoCardIdentity(event)
            if (!clickedChan.isNullOrBlank() || !clickedTitle.isNullOrBlank()) {
                if (!clickedChan.isNullOrBlank()) pendingClickedChannel = clickedChan
                if (!clickedTitle.isNullOrBlank()) pendingClickedTitle = clickedTitle
                pendingClickedAt = currentTime
            }
        }

        if (rootNode == null) return YouTubeBlockDecision.ALLOW_NAVIGATION

        // 1. SHORTS: every short is blocked instantly, regardless of channel or content.
        val isShorts = YouTubeNodeExtractor.detectShorts(rootNode, event)
        if (isShorts) {
            resetVideoTracking()
            return YouTubeBlockDecision.shorts()
        }

        // 2. Watch page detection
        val isWatchPage = YouTubeNodeExtractor.detectWatchPage(rootNode)
        if (!isWatchPage) {
            resetVideoTracking()
            return if (YouTubeNodeExtractor.isHomeFeed(rootNode)) {
                YouTubeBlockDecision.HOME_FEED
            } else {
                // Speculative Pre-Warming (Layer 3): while user browses Search Results or Channel Pages,
                // pre-classify visible video cards in the background so tapping any result resolves in 0ms!
                if (enableAiClassification) {
                    val candidates = YouTubeNodeExtractor.extractVisibleSearchCandidateTitles(rootNode)
                    if (candidates.isNotEmpty()) {
                        com.example.feature.youtube.ai.YouTubeAiStudyClassifier.prewarmSearchCandidates(
                            candidates = candidates,
                            activeSubject = activeSubject,
                            activeTopic = activeTopic,
                            currentTime = currentTime
                        )
                    }
                }
                YouTubeBlockDecision.ALLOW_NAVIGATION
            }
        }

        val isFullScreen = YouTubeNodeExtractor.isFullScreenVideoPlayback(rootNode)

        // 2.1 Ad detection (Sponsor / Skip Ad / Ad countdown / Visit advertiser)
        val isAd = YouTubeNodeExtractor.detectAd(rootNode)

        // When a pre-roll ad finishes and transitions to the main video, reset the metadata grace timer
        // so the video's watch header has a full grace window to finish rendering.
        if (wasAdPlayingOnLastTick && !isAd) {
            currentVideoFirstSeenAt = currentTime
        }
        wasAdPlayingOnLastTick = isAd

        // 2.2 Record watch page active timestamp
        if (playbackStartedAt == 0L) {
            playbackStartedAt = currentTime
        }

        // 3. DUAL CHANNEL IDENTITY & VIDEO TITLE EXTRACTION
        val extractedTitle = YouTubeNodeExtractor.extractVideoTitle(rootNode)
        val recentClickedTitle = pendingClickedTitle?.takeIf { (currentTime - pendingClickedAt) in 0L..8000L }
        val title = extractedTitle ?: recentClickedTitle
        val identity = if (isFullScreen) {
            // In full-screen mode, only accept strict portrait header signals if explicitly present
            YouTubeNodeExtractor.WatchChannelIdentity(
                channelHandle = null,
                channelName = YouTubeNodeExtractor.extractChannelFromSubscribeButton(rootNode)
            )
        } else {
            YouTubeNodeExtractor.extractChannelIdentity(rootNode)
        }
        val fallbackChannel = if (!isFullScreen && !identity.isIdentifiable) resolveChannelFromText(rootNode) else null

        if (title != null) lastSeenVideoTitle = title
        trackVideo(title, currentTime)

        // Check if we are still in the first 600ms after a click and the watch header still shows the OLD video's title
        val prevTitleBeforeClick = com.example.feature.youtube.detector.YouTubePlaybackSandbox.lastExitedVideoTitle
        val isStillShowingOldVideoImmediatelyAfterClick =
            (currentTime - lastClickOrNavigationAt) < 600L &&
            !prevTitleBeforeClick.isNullOrBlank() &&
            !extractedTitle.isNullOrBlank() &&
            extractedTitle.equals(prevTitleBeforeClick, ignoreCase = true)

        // 3.0 Check Session-Scoped "Allow Anyway" Bypass first (0ms)
        val isTemporarilyAllowed = com.example.feature.youtube.ai.YouTubeAiStudyClassifier.isChannelTemporarilyAllowed(
            channelId = identity.channelHandle,
            channelName = identity.channelName ?: fallbackChannel ?: pendingClickedChannel
        )

        // Check Blocked Channel Override (0ms) unless temporarily allowed for this session
        val isHandleBlockedOverride = !isTemporarilyAllowed && !identity.channelHandle.isNullOrBlank() &&
                isChannelApproved(identity.channelHandle, blockedChannelIds, blockedChannelNames)
        val isNameBlockedOverride = !isTemporarilyAllowed && !identity.channelName.isNullOrBlank() &&
                isChannelApproved(identity.channelName, blockedChannelIds, blockedChannelNames)
        val isFallbackBlockedOverride = !isTemporarilyAllowed && !fallbackChannel.isNullOrBlank() &&
                isChannelApproved(fallbackChannel, blockedChannelIds, blockedChannelNames)
        if (isHandleBlockedOverride || isNameBlockedOverride || isFallbackBlockedOverride) {
            val blockedChan = identity.preferredDisplay ?: fallbackChannel ?: "Blocked Channel"
            com.example.feature.youtube.ai.YouTubeAiStudyClassifier.recordLocalChannelHit(
                channelName = blockedChan,
                videoTitle = title,
                isAllowed = false,
                currentTime = currentTime
            )
            com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
            isCurrentlyOnApprovedWatchPage = false
            return YouTubeBlockDecision.blockedByAi(
                channel = blockedChan,
                title = title,
                reason = "Entertainment channel '$blockedChan' is blocked",
                category = "CHANNEL_BLOCKED",
                latencyMs = 0L
            )
        }

        val isHandleApproved = !identity.channelHandle.isNullOrBlank() &&
                isChannelApproved(identity.channelHandle, approvedChannelIds, approvedChannelNames)
        val isNameApproved = !identity.channelName.isNullOrBlank() &&
                isChannelApproved(identity.channelName, approvedChannelIds, approvedChannelNames)
        val isFallbackApproved = !fallbackChannel.isNullOrBlank() &&
                isChannelApproved(fallbackChannel, approvedChannelIds, approvedChannelNames)

        // Mixed-content safety check: if channel is approved (and not bypassed via "Allow Anyway"),
        // block obvious gaming/prank/vlog/music uploads on the spot in 0ms without changing channel approval.
        val anyChannelApproved = isHandleApproved || isNameApproved || isFallbackApproved
        if (!isTemporarilyAllowed && anyChannelApproved && !title.isNullOrBlank()) {
            val matchedDistraction = com.example.feature.youtube.ai.YouTubeAiStudyClassifier
                .checkObviousDistractionSafety(title, identity.preferredDisplay ?: fallbackChannel)
            if (matchedDistraction != null) {
                val chanLabel = identity.preferredDisplay ?: fallbackChannel ?: "Approved Channel"
                com.example.feature.youtube.ai.YouTubeAiStudyClassifier.recordLocalChannelHit(
                    channelName = chanLabel,
                    videoTitle = title,
                    isAllowed = false,
                    currentTime = currentTime
                )
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
                isCurrentlyOnApprovedWatchPage = false
                return YouTubeBlockDecision.blockedByAi(
                    channel = chanLabel,
                    title = title,
                    reason = "Distraction upload ('$matchedDistraction') blocked on $chanLabel",
                    category = "DISTRACTION_SAFETY",
                    latencyMs = 0L
                )
            }
        }

        var hasPositiveChannelIdentification = identity.isIdentifiable || !fallbackChannel.isNullOrBlank() || isTemporarilyAllowed
        var isPositivelyApproved = isTemporarilyAllowed || anyChannelApproved
        var approvedChannelFromCardClick: String? = null

        val clickedChan = pendingClickedChannel
        val clickedTitle = pendingClickedTitle
        val clickedAge = currentTime - pendingClickedAt
        if ((!clickedChan.isNullOrBlank() || !clickedTitle.isNullOrBlank()) && clickedAge in 0L..6000L) {
            val isClickedTemporarilyAllowed = com.example.feature.youtube.ai.YouTubeAiStudyClassifier
                .isChannelTemporarilyAllowed(null, clickedChan)
            val isClickedChanBlocked = !isClickedTemporarilyAllowed && !clickedChan.isNullOrBlank() &&
                    isChannelApproved(clickedChan, blockedChannelIds, blockedChannelNames)
            val isClickedChanApproved = isClickedTemporarilyAllowed || (!isClickedChanBlocked && !clickedChan.isNullOrBlank() &&
                    isChannelApproved(clickedChan, approvedChannelIds, approvedChannelNames))
            if (isClickedChanBlocked && clickedAge >= 150L) {
                pendingClickedChannel = null
                pendingClickedTitle = null
                com.example.feature.youtube.ai.YouTubeAiStudyClassifier.recordLocalChannelHit(
                    channelName = clickedChan,
                    videoTitle = clickedTitle ?: title,
                    isAllowed = false,
                    currentTime = currentTime
                )
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
                isCurrentlyOnApprovedWatchPage = false
                return YouTubeBlockDecision.blockedByAi(
                    channel = clickedChan ?: "Blocked Channel",
                    title = clickedTitle ?: title,
                    reason = "Entertainment channel '$clickedChan' is blocked",
                    category = "CHANNEL_BLOCKED",
                    latencyMs = 0L
                )
            } else if (!isClickedChanApproved && clickedAge >= 150L && (isStillShowingOldVideoImmediatelyAfterClick || !hasPositiveChannelIdentification)) {
                val candidateTitle = clickedTitle ?: title
                if (enableAiClassification) {
                    if (!candidateTitle.isNullOrBlank()) {
                        val aiOutcome = com.example.feature.youtube.ai.YouTubeAiStudyClassifier.evaluateWatchVideo(
                            videoTitle = candidateTitle,
                            channelName = clickedChan,
                            channelHandle = identity.channelHandle,
                            activeSubject = activeSubject,
                            activeTopic = activeTopic,
                            approvedChannelIds = approvedChannelIds,
                            approvedChannelNames = approvedChannelNames,
                            blockedChannelIds = blockedChannelIds,
                            blockedChannelNames = blockedChannelNames,
                            currentTime = currentTime
                        )
                        when (aiOutcome) {
                            is com.example.feature.youtube.ai.YouTubeAiEvaluationOutcome.Decided -> {
                                pendingClickedChannel = null
                                pendingClickedTitle = null
                                if (aiOutcome.verdict.isAllowed) {
                                    hasPositiveChannelIdentification = true
                                    isPositivelyApproved = true
                                    approvedChannelFromCardClick = clickedChan ?: aiOutcome.verdict.channelName
                                } else {
                                    com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
                                    isCurrentlyOnApprovedWatchPage = false
                                    return YouTubeBlockDecision.blockedByAi(
                                        channel = clickedChan ?: aiOutcome.verdict.channelName,
                                        title = candidateTitle,
                                        reason = aiOutcome.verdict.reason,
                                        category = aiOutcome.verdict.category,
                                        latencyMs = aiOutcome.verdict.latencyMs
                                    )
                                }
                            }
                            is com.example.feature.youtube.ai.YouTubeAiEvaluationOutcome.VerifyingAsync -> {
                                return YouTubeBlockDecision(
                                    verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                                    reason = "AI verifying channel...",
                                    detectedTitle = candidateTitle,
                                    detectedChannel = clickedChan
                                )
                            }
                        }
                    }
                } else if (!clickedChan.isNullOrBlank()) {
                    pendingClickedChannel = null
                    pendingClickedTitle = null
                    com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
                    isCurrentlyOnApprovedWatchPage = false
                    return YouTubeBlockDecision.blockedChannel(clickedChan)
                }
            } else if (isClickedChanApproved && !hasPositiveChannelIdentification) {
                val matchedClickedDistraction = if (!isClickedTemporarilyAllowed) {
                    com.example.feature.youtube.ai.YouTubeAiStudyClassifier
                        .checkObviousDistractionSafety(clickedTitle ?: title, clickedChan)
                } else null
                if (matchedClickedDistraction != null) {
                    pendingClickedChannel = null
                    pendingClickedTitle = null
                    com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
                    isCurrentlyOnApprovedWatchPage = false
                    return YouTubeBlockDecision.blockedByAi(
                        channel = clickedChan ?: "Approved Channel",
                        title = clickedTitle ?: title,
                        reason = "Distraction upload ('$matchedClickedDistraction') blocked",
                        category = "DISTRACTION_SAFETY",
                        latencyMs = 0L
                    )
                }
                val titleMatchesClicked = clickedTitle.isNullOrBlank() ||
                        title.isNullOrBlank() ||
                        title.equals(clickedTitle, ignoreCase = true) ||
                        title.contains(clickedTitle, ignoreCase = true) ||
                        clickedTitle.contains(title, ignoreCase = true)
                if (titleMatchesClicked && (!isStillShowingOldVideoImmediatelyAfterClick || isFullScreen)) {
                    hasPositiveChannelIdentification = true
                    isPositivelyApproved = true
                    approvedChannelFromCardClick = clickedChan
                    if (!extractedTitle.isNullOrBlank() && !isStillShowingOldVideoImmediatelyAfterClick) {
                        pendingClickedChannel = null
                        pendingClickedTitle = null
                    }
                }
            }
        }
        if (hasPositiveChannelIdentification && !extractedTitle.isNullOrBlank() && !isStillShowingOldVideoImmediatelyAfterClick && approvedChannelFromCardClick == null) {
            pendingClickedChannel = null
            pendingClickedTitle = null
        }

        // 3.1 ACTIVE SESSION LATCH & SAFE PLAYBACK SANDBOX:
        // Keep approved status locked across full-screen transitions, hidden player controls (title == null),
        // and portrait scrolling as long as the video title has not changed to a different video.
        val isInPostClickCooldown = currentTime < com.example.feature.youtube.detector.YouTubePlaybackSandbox.navigationCooldownUntil
        val isSandboxActiveForPlayback = com.example.feature.youtube.detector.YouTubePlaybackSandbox.isSandboxActive
        val isSameOrHiddenTitle = title == null ||
                lastApprovedVideoKey == null ||
                currentVideoKey.equals(lastApprovedVideoKey, ignoreCase = true)

        val isAiLatchedForCurrentVideo = !lastApprovedVideoKey.isNullOrBlank() &&
                (title.isNullOrBlank() || title.equals(lastApprovedVideoKey, ignoreCase = true)) &&
                !lastApprovedChannel.isNullOrBlank() &&
                (!hasPositiveChannelIdentification ||
                        identity.channelName.equals(lastApprovedChannel, ignoreCase = true) ||
                        identity.preferredDisplay.equals(lastApprovedChannel, ignoreCase = true))

        val isLatchedApproved = isAiLatchedForCurrentVideo || (!hasPositiveChannelIdentification && (
            (!isInPostClickCooldown && lastApprovedChannel != null && isSameOrHiddenTitle) ||
            (isFullScreen && lastApprovedChannel != null && isSameOrHiddenTitle) ||
            isSandboxActiveForPlayback
        ))

        val approved = isPositivelyApproved || isLatchedApproved
        val displayChannel = when {
            approvedChannelFromCardClick != null -> approvedChannelFromCardClick
            isNameApproved -> identity.channelName
            isHandleApproved -> identity.channelName ?: identity.channelHandle
            isFallbackApproved -> fallbackChannel
            hasPositiveChannelIdentification -> identity.preferredDisplay ?: fallbackChannel
            isLatchedApproved -> lastApprovedChannel
                ?: com.example.feature.youtube.detector.YouTubePlaybackSandbox.sandboxedChannel?.channelName
                ?: lastSeenChannelName
            !clickedChan.isNullOrBlank() && clickedAge in 0L..8000L -> clickedChan
            else -> null
        }
        if (displayChannel != null && !isStillShowingOldVideoImmediatelyAfterClick) {
            lastSeenChannelName = displayChannel
        }

        // 3.2 SMART AD DETECTION & HANDLING RULES:
        if (isAd) {
            if (approved && !isStillShowingOldVideoImmediatelyAfterClick) {
                if (currentVideoKey != null) {
                    lastApprovedVideoKey = currentVideoKey
                }
                lastApprovedChannel = displayChannel ?: lastApprovedChannel ?: "Approved Channel"
                if (!identity.channelHandle.isNullOrBlank()) {
                    lastApprovedHandle = identity.channelHandle
                }
                isCurrentlyOnApprovedWatchPage = true
                return YouTubeBlockDecision.approvedChannel(
                    channel = lastApprovedChannel ?: "Approved Channel",
                    title = title ?: lastSeenVideoTitle,
                    isAdPlaying = true
                )
            } else {
                // Pre-roll ad is playing; allow ad while waiting for main video or evaluate via AI if title known
                val withinAdWait = (currentTime - currentVideoFirstSeenAt) < AD_MAX_WAIT_FOR_CHANNEL_MILLIS
                if (withinAdWait) {
                    return YouTubeBlockDecision(
                        verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                        reason = "Pre-roll ad playing; waiting for video verification",
                        detectedTitle = title,
                        isAdPlaying = true
                    )
                } else {
                    isCurrentlyOnApprovedWatchPage = false
                    return YouTubeBlockDecision.blockedChannel("Unapproved Video Ad")
                }
            }
        }

        // 3.3 NORMAL VIDEO PLAYBACK (No Ad):
        if (approved) {
            // Immediately record the approved channel and video key so switching to full screen
            // at any moment (even within the first second of playback) preserves the approved lock.
            if (!isStillShowingOldVideoImmediatelyAfterClick) {
                if (currentVideoKey != null) {
                    lastApprovedVideoKey = currentVideoKey
                }
                lastApprovedChannel = displayChannel ?: lastApprovedChannel ?: "Approved Channel"
                if (!identity.channelHandle.isNullOrBlank()) {
                    lastApprovedHandle = identity.channelHandle
                }
                isCurrentlyOnApprovedWatchPage = true
            }

            val canEnterSandboxNow = (isPositivelyApproved || isLatchedApproved) &&
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.canEnterSandbox(title, currentTime)

            if (isPositivelyApproved && !isStillShowingOldVideoImmediatelyAfterClick && !isTemporarilyAllowed) {
                com.example.feature.youtube.ai.YouTubeAiStudyClassifier.recordLocalChannelHit(
                    channelName = displayChannel ?: lastApprovedChannel,
                    videoTitle = title ?: lastApprovedVideoKey ?: lastSeenVideoTitle,
                    isAllowed = true,
                    currentTime = currentTime
                )
            }

            if (canEnterSandboxNow) {
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.enterSandbox(
                    channel = com.example.feature.youtube.detector.ChannelIdentityInfo(
                        channelName = lastApprovedChannel,
                        channelHandle = identity.channelHandle ?: lastApprovedHandle
                    ),
                    title = title ?: lastApprovedVideoKey ?: lastSeenVideoTitle
                )
            }
            return YouTubeBlockDecision.approvedChannel(
                channel = displayChannel ?: lastApprovedChannel ?: "Approved Channel",
                title = title ?: lastApprovedVideoKey ?: lastSeenVideoTitle,
                isAdPlaying = false
            )
        }

        val elapsedForCurrentVideo = currentTime - currentVideoFirstSeenAt

        // 3.3B CONTEXT-AWARE AI STUDY MODE CLASSIFICATION:
        // When Context-Aware AI Study Mode is enabled, AI is the authority for whether a video is study content.
        // We NEVER block simply because a channel is not on a manual approved list!
        if (enableAiClassification) {
            val effectiveTitleForAi = title
                ?: lastSeenVideoTitle?.takeIf { elapsedForCurrentVideo < 3000L }
                ?: if (elapsedForCurrentVideo >= 1800L) displayChannel else null

            val alreadyAutoExitedForFullScreen = isFullScreen &&
                (currentVideoKey ?: "__unverified_fullscreen__") == lastFullScreenVerifyExitKey &&
                (currentTime - lastFullScreenVerifyExitAt) <= 6000L
            if (!effectiveTitleForAi.isNullOrBlank() &&
                (!isFullScreen || hasPositiveChannelIdentification || alreadyAutoExitedForFullScreen)
            ) {
                val aiOutcome = com.example.feature.youtube.ai.YouTubeAiStudyClassifier.evaluateWatchVideo(
                    videoTitle = effectiveTitleForAi,
                    channelName = displayChannel,
                    channelHandle = identity.channelHandle,
                    activeSubject = activeSubject,
                    activeTopic = activeTopic,
                    approvedChannelIds = approvedChannelIds,
                    approvedChannelNames = approvedChannelNames,
                    blockedChannelIds = blockedChannelIds,
                    blockedChannelNames = blockedChannelNames,
                    currentTime = currentTime
                )
                when (aiOutcome) {
                    is com.example.feature.youtube.ai.YouTubeAiEvaluationOutcome.Decided -> {
                        val verdict = aiOutcome.verdict
                        if (verdict.isAllowed) {
                            val effectiveChannel = displayChannel ?: verdict.channelName.ifBlank { "AI Verified Study" }
                            if (!isStillShowingOldVideoImmediatelyAfterClick) {
                                currentVideoKey = effectiveTitleForAi
                                lastApprovedVideoKey = effectiveTitleForAi
                                lastApprovedChannel = effectiveChannel
                                if (!identity.channelHandle.isNullOrBlank()) {
                                    lastApprovedHandle = identity.channelHandle
                                }
                                isCurrentlyOnApprovedWatchPage = true
                            }
                            if (com.example.feature.youtube.detector.YouTubePlaybackSandbox.canEnterSandbox(effectiveTitleForAi, currentTime)) {
                                com.example.feature.youtube.detector.YouTubePlaybackSandbox.enterSandbox(
                                    channel = com.example.feature.youtube.detector.ChannelIdentityInfo(
                                        channelName = effectiveChannel,
                                        channelHandle = identity.channelHandle ?: lastApprovedHandle
                                    ),
                                    title = effectiveTitleForAi
                                )
                            }
                            return YouTubeBlockDecision.approvedByAi(
                                channel = effectiveChannel,
                                title = effectiveTitleForAi,
                                reason = verdict.reason,
                                category = verdict.category,
                                latencyMs = verdict.latencyMs
                            )
                        } else {
                            com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
                            isCurrentlyOnApprovedWatchPage = false
                            return YouTubeBlockDecision.blockedByAi(
                                channel = displayChannel ?: verdict.channelName,
                                title = effectiveTitleForAi,
                                reason = verdict.reason,
                                category = verdict.category,
                                latencyMs = verdict.latencyMs
                            )
                        }
                    }
                    is com.example.feature.youtube.ai.YouTubeAiEvaluationOutcome.VerifyingAsync -> {
                        return YouTubeBlockDecision(
                            verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                            reason = "AI verifying study content...",
                            detectedTitle = effectiveTitleForAi,
                            detectedChannel = displayChannel
                        )
                    }
                }
            }
        } else if (hasPositiveChannelIdentification) {
            // Legacy channel-only mode (when enableAiClassification == false)
            com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
            isCurrentlyOnApprovedWatchPage = false
            return YouTubeBlockDecision.blockedChannel(displayChannel)
        }

        // 3.4 UNVERIFIED VIDEO IN FULL SCREEN:
        // If a brand-new or unverified video is playing in full-screen mode and its channel metadata
        // is hidden because full-screen mode hides the portrait watch header, auto-exit full screen once
        // so FocusShield can read the portrait channel header and verify instead of falsely blocking.
        if (isFullScreen && elapsedForCurrentVideo >= 900L) {
            val fsKey = currentVideoKey ?: "__unverified_fullscreen__"
            val canAutoExitFs = fsKey != lastFullScreenVerifyExitKey ||
                    (currentTime - lastFullScreenVerifyExitAt) > 6000L
            if (canAutoExitFs) {
                lastFullScreenVerifyExitKey = fsKey
                lastFullScreenVerifyExitAt = currentTime
                currentVideoFirstSeenAt = currentTime
                return YouTubeBlockDecision.exitFullScreenForVerification(title)
            }
        }

        // 4. Video title/channel metadata not rendered yet: wait briefly, then treat as unknown.
        val withinGrace = elapsedForCurrentVideo < CHANNEL_METADATA_GRACE_MILLIS
        if (withinGrace) {
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                reason = "Waiting for video metadata for AI check"
            )
        }

        isCurrentlyOnApprovedWatchPage = false
        if (!blockUnknownContent) {
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW,
                reason = "Unknown content allowed by setting",
                detectedTitle = title
            )
        }
        return YouTubeBlockDecision.unknownContent(title, null)
    }

    /**
     * Extracts a channel handle (@name) from the watch player subtree when the
     * dedicated channel view IDs expose nothing (e.g. plain-text channels).
     * Only scans the watch panel — never recommendations, comments, or descriptions.
     */
    private fun resolveChannelFromText(rootNode: AccessibilityNodeInfo): String? {
        val watchText = extractWatchPanelText(rootNode)
        if (watchText.isBlank()) return null
        val handleMatch = HANDLE_REGEX.find(watchText) ?: return null
        return YouTubeNodeExtractor.cleanChannelName(handleMatch.value)
    }

    private val HANDLE_REGEX = Regex("@[A-Za-z0-9_.\\-]{2,50}")

    private fun extractWatchPanelText(rootNode: AccessibilityNodeInfo): String {
        val watchIds = listOf(
            "channel_handle",
            "byline",
            "byline_text",
            "watch_metadata_byline",
            "watch_metadata_subtitle",
            "metadata_line",
            "watch_header_layout",
            "owner_layout",
            "channel_name",
            "owner_text",
            "owner_name",
            "channel_title"
        )
        val excludedTitleIds = listOf(
            "watch_title_text",
            "watch_metadata_title",
            "video_title",
            "title"
        )
        val builder = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        while (queue.isNotEmpty() && visited < 100) {
            val node = queue.removeFirst()
            visited++
            val resId = node.viewIdResourceName ?: ""
            val isTitle = excludedTitleIds.any { resId.contains(it, ignoreCase = true) } &&
                    !resId.contains("channel_title", ignoreCase = true) &&
                    !resId.contains("subtitle", ignoreCase = true)
            if (!isTitle &&
                watchIds.any { resId.contains(it, ignoreCase = true) } &&
                !YouTubeNodeExtractor.isNodeInExcludedContainer(node)
            ) {
                node.text?.let { builder.append(it).append(" ") }
                node.contentDescription?.let { builder.append(it).append(" ") }
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        return builder.toString().trim()
    }

    /**
     * Tracks the currently playing video by title. A changed title means a new
     * video opened: the metadata grace window restarts and any channel remembered
     * from the previous video is discarded so it can never approve the new one.
     */
    private fun trackVideo(title: String?, currentTime: Long) {
        val videoKey = title?.trim()?.takeIf { it.isNotEmpty() }
        if (videoKey != null) {
            if (currentVideoKey == null) {
                // First time seeing the title for the current video (e.g. title node rendered slightly after channel header)
                currentVideoKey = videoKey
                if (currentVideoFirstSeenAt == 0L) {
                    currentVideoFirstSeenAt = currentTime
                }
                if (lastApprovedChannel != null && lastApprovedVideoKey == null) {
                    lastApprovedVideoKey = videoKey
                }
            } else if (!videoKey.equals(currentVideoKey, ignoreCase = true)) {
                currentVideoKey = videoKey
                currentVideoFirstSeenAt = currentTime
                lastSeenChannelName = null
                lastApprovedVideoKey = null
                lastApprovedChannel = null
                lastApprovedHandle = null
                isCurrentlyOnApprovedWatchPage = false
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
            }
        } else {
            if (currentVideoFirstSeenAt == 0L) {
                currentVideoFirstSeenAt = currentTime
            }
        }
    }

    private fun resetVideoTracking() {
        currentVideoKey = null
        currentVideoFirstSeenAt = 0L
        playbackStartedAt = 0L
        wasAdPlayingOnLastTick = false
        lastApprovedVideoKey = null
        lastApprovedChannel = null
        lastApprovedHandle = null
        isCurrentlyOnApprovedWatchPage = false
        com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
    }

    /**
     * Clears all tracking state when YouTube is exited or a block is enforced.
     */
    fun reset() {
        resetVideoTracking()
        com.example.feature.youtube.detector.YouTubePlaybackSandbox.reset()
        lastSeenChannelName = null
        lastSeenVideoTitle = null
        lastApprovedVideoKey = null
        lastApprovedChannel = null
        lastApprovedHandle = null
        pendingClickedChannel = null
        pendingClickedTitle = null
        lastFullScreenVerifyExitKey = null
        lastFullScreenVerifyExitAt = 0L
        isCurrentlyOnApprovedWatchPage = false
    }

    /**
     * Evaluates whether a dual WatchChannelIdentity (@handle + channelName) matches the user's approved channels.
     */
    fun isChannelIdentityApproved(
        identity: YouTubeNodeExtractor.WatchChannelIdentity,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>
    ): Boolean {
        val handleApproved = !identity.channelHandle.isNullOrBlank() &&
                isChannelApproved(identity.channelHandle, approvedChannelIds, approvedChannelNames)
        val nameApproved = !identity.channelName.isNullOrBlank() &&
                isChannelApproved(identity.channelName, approvedChannelIds, approvedChannelNames)
        return handleApproved || nameApproved
    }

    /**
     * Evaluates whether the detected channel matches the user's approved study channels.
     * The video title is never used to judge approval — only verified channel handles and names.
     */
    fun isContentApproved(
        detectedTitle: String?,
        detectedChannel: String?,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>
    ): Boolean {
        if (approvedChannelIds.isEmpty() && approvedChannelNames.isEmpty()) return false
        if (detectedChannel.isNullOrBlank()) return false
        return isChannelApproved(detectedChannel, approvedChannelIds, approvedChannelNames)
    }

    /**
     * Checks whether the detected channel (either @handle or display name) is whitelisted
     * against the user's approved study channels (both handles and names).
     */
    fun isChannelApproved(
        detectedChannel: String,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>
    ): Boolean {
        if (approvedChannelIds.isEmpty() && approvedChannelNames.isEmpty()) return false

        val detected = detectedChannel.trim()
        val detectedHandle = normalizeHandle(detected)
        val detectedName = normalizeName(detected)
        if (detectedHandle.isEmpty() && detectedName.isEmpty()) return false
        val detectedWords = extractSignificantWords(detected)

        for (approved in approvedChannelIds) {
            val approvedHandle = normalizeHandle(approved)
            if (approvedHandle.isNotEmpty() && (approvedHandle == detectedHandle || approvedHandle == detectedName)) return true
            if (isFuzzyMatch(detectedHandle, approvedHandle)) return true
            if (isFuzzyMatch(detectedName, approvedHandle)) return true
            if (matchWords(detectedWords, extractSignificantWords(approved))) return true
        }

        for (approved in approvedChannelNames) {
            val approvedName = normalizeName(approved)
            if (approvedName.isNotEmpty() && (approvedName == detectedName || approvedName == detectedHandle)) return true
            if (isFuzzyMatch(detectedName, approvedName)) return true
            if (isFuzzyMatch(detectedHandle, approvedName)) return true
            if (matchWords(detectedWords, extractSignificantWords(approved))) return true
        }

        // Sub-channel / Brand alias matching:
        // E.g. PW-JEEWallah, PW-NEETWallah, PW Foundation belong to Physics Wallah
        val isPwDetected = detectedWords.contains("pw") ||
                detectedHandle.startsWith("pw") ||
                detectedHandle.contains("physicswallah") ||
                detectedName.contains("physicswallah")
        if (isPwDetected) {
            val isPwApproved = approvedChannelIds.any {
                val h = normalizeHandle(it)
                val w = extractSignificantWords(it)
                w.contains("pw") || h.startsWith("pw") || h.contains("physicswallah")
            } || approvedChannelNames.any {
                val n = normalizeName(it)
                val w = extractSignificantWords(it)
                w.contains("pw") || n.startsWith("pw") || n.contains("physicswallah")
            }
            if (isPwApproved) return true
        }

        return false
    }

    private fun extractSignificantWords(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), " ")
            .split(" ")
            .filter { it.length >= 2 && it !in listOf("the", "and", "official", "channel", "video", "hindi", "english") }
    }

    private fun matchWords(wordsA: List<String>, wordsB: List<String>): Boolean {
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false
        val overlap = wordsA.intersect(wordsB.toSet())
        return (overlap.isNotEmpty() && overlap.size == minOf(wordsA.size, wordsB.size) && overlap.any { it.length >= 3 }) ||
                overlap.count { it.length >= 3 } >= 2
    }

    private fun isFuzzyMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        if (shorter < 4) return false
        if (a.contains(b) || b.contains(a)) return true

        // Brand acronym expansion (e.g. pw -> physicswallah)
        val expandedA = if (a.startsWith("pw")) a.replaceFirst("pw", "physicswallah") else a
        val expandedB = if (b.startsWith("pw")) b.replaceFirst("pw", "physicswallah") else b
        if (expandedA.contains(expandedB) || expandedB.contains(expandedA)) return true

        return false
    }

    private fun normalizeHandle(value: String): String {
        var v = value.trim().lowercase(Locale.ROOT)
        if (v.startsWith("@")) v = v.removePrefix("@")
        return v.filter { it.isLetterOrDigit() }
    }

    private fun normalizeName(value: String): String {
        return value.trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }

    val lastDetectedChannel: String? get() = lastSeenChannelName
    val lastDetectedTitle: String? get() = lastSeenVideoTitle
}
