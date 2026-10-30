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
        if (!clickedChannel.isNullOrBlank()) {
            pendingClickedChannel = clickedChannel
            pendingClickedTitle = clickedTitle
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
     *  - The strict watch-header video title changes to a different video, or
     *  - An unapproved channel appears in the active portrait watch header.
     *
     * In Full-Screen mode (or when scrolling down the recommendation feed), the portrait watch header
     * is hidden, so the sandbox remains active as long as the video title has not changed.
     */
    fun checkSandboxStillValid(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>,
        currentTime: Long = System.currentTimeMillis()
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
            val isApproved = isChannelIdentityApproved(identity, approvedChannelIds, approvedChannelNames)
            if (!isApproved) {
                // Positively identified an unapproved channel in the portrait watch header!
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.onVideoNavigationOccurred(currentTime)
                lastApprovedVideoKey = null
                lastApprovedChannel = null
                lastApprovedHandle = null
                currentVideoFirstSeenAt = currentTime
                return false
            }
        }

        return true
    }

    /**
     * Evaluates the current YouTube screen and returns the block decision.
     * Fully synchronous and in-memory so it completes within milliseconds.
     */
    fun evaluate(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        approvedChannelIds: Set<String>,
        approvedChannelNames: Set<String>,
        blockUnknownContent: Boolean = true,
        currentTime: Long = System.currentTimeMillis()
    ): YouTubeBlockDecision {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val (clickedChan, clickedTitle) = YouTubeNodeExtractor.extractClickedVideoCardIdentity(event)
            if (!clickedChan.isNullOrBlank()) {
                pendingClickedChannel = clickedChan
                pendingClickedTitle = clickedTitle
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

        // 3. DUAL CHANNEL IDENTITY EXTRACTION (@handle under title + Channel Name from Subscribe/Avatar/Owner)
        val title = YouTubeNodeExtractor.extractVideoTitle(rootNode)
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
            !title.isNullOrBlank() &&
            title.equals(prevTitleBeforeClick, ignoreCase = true)

        val isHandleApproved = !identity.channelHandle.isNullOrBlank() &&
                isChannelApproved(identity.channelHandle, approvedChannelIds, approvedChannelNames)
        val isNameApproved = !identity.channelName.isNullOrBlank() &&
                isChannelApproved(identity.channelName, approvedChannelIds, approvedChannelNames)
        val isFallbackApproved = !fallbackChannel.isNullOrBlank() &&
                isChannelApproved(fallbackChannel, approvedChannelIds, approvedChannelNames)

        var hasPositiveChannelIdentification = identity.isIdentifiable || !fallbackChannel.isNullOrBlank()
        var isPositivelyApproved = isHandleApproved || isNameApproved || isFallbackApproved
        var approvedChannelFromCardClick: String? = null

        // If the user just clicked a video card whose channel was extracted from the click event:
        //  - If the clicked card is from an UNAPPROVED channel, block it once the transition begins (>= 150ms)
        //    even if the watch header is still rendering or briefly holding the old video's header.
        //  - If the clicked card is from an APPROVED channel and we are in full screen (where the portrait
        //    header will not render), approve the new video directly from the verified card click.
        val clickedChan = pendingClickedChannel
        val clickedTitle = pendingClickedTitle
        val clickedAge = currentTime - pendingClickedAt
        if (!clickedChan.isNullOrBlank() && clickedAge in 0L..6000L) {
            val isClickedChanApproved = isChannelApproved(clickedChan, approvedChannelIds, approvedChannelNames)
            if (!isClickedChanApproved && clickedAge >= 150L && (isStillShowingOldVideoImmediatelyAfterClick || !hasPositiveChannelIdentification)) {
                pendingClickedChannel = null
                pendingClickedTitle = null
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
                isCurrentlyOnApprovedWatchPage = false
                return YouTubeBlockDecision.blockedChannel(clickedChan)
            } else if (isClickedChanApproved && !hasPositiveChannelIdentification) {
                val titleMatchesClicked = clickedTitle.isNullOrBlank() ||
                        title.isNullOrBlank() ||
                        title.equals(clickedTitle, ignoreCase = true) ||
                        title.contains(clickedTitle, ignoreCase = true) ||
                        clickedTitle.contains(title, ignoreCase = true)
                if (titleMatchesClicked && (!isStillShowingOldVideoImmediatelyAfterClick || isFullScreen)) {
                    hasPositiveChannelIdentification = true
                    isPositivelyApproved = true
                    approvedChannelFromCardClick = clickedChan
                    if (!title.isNullOrBlank() && !isStillShowingOldVideoImmediatelyAfterClick) {
                        pendingClickedChannel = null
                        pendingClickedTitle = null
                    }
                }
            }
        }
        if (hasPositiveChannelIdentification && !isStillShowingOldVideoImmediatelyAfterClick && approvedChannelFromCardClick == null) {
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

        val isLatchedApproved = !hasPositiveChannelIdentification && (
            (!isInPostClickCooldown && lastApprovedChannel != null && isSameOrHiddenTitle) ||
            (isFullScreen && lastApprovedChannel != null && isSameOrHiddenTitle) ||
            isSandboxActiveForPlayback
        )

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
            } else if (hasPositiveChannelIdentification && !isPositivelyApproved) {
                // Ad is playing on a positively identified UNAPPROVED channel -> stop ad & step back immediately!
                isCurrentlyOnApprovedWatchPage = false
                return YouTubeBlockDecision.blockedChannel(displayChannel ?: "Unapproved Channel")
            } else {
                // Pre-roll ad is hiding the channel header before the video starts -> allow ad while waiting for channel
                val withinAdWait = (currentTime - currentVideoFirstSeenAt) < AD_MAX_WAIT_FOR_CHANNEL_MILLIS
                if (withinAdWait) {
                    return YouTubeBlockDecision(
                        verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                        reason = "Pre-roll ad playing; waiting for channel identification",
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

            val canEnterSandboxNow = (isPositivelyApproved || (isFullScreen && isLatchedApproved)) &&
                com.example.feature.youtube.detector.YouTubePlaybackSandbox.canEnterSandbox(title, currentTime)

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
        } else if (hasPositiveChannelIdentification) {
            // Unapproved channel positively identified -> exit sandbox and block
            com.example.feature.youtube.detector.YouTubePlaybackSandbox.exitSandbox()
            isCurrentlyOnApprovedWatchPage = false
            return YouTubeBlockDecision.blockedChannel(displayChannel)
        }

        // 3.4 UNVERIFIED VIDEO IN FULL SCREEN:
        // If a brand-new or unverified video is playing in full-screen mode and its channel metadata
        // is hidden because full-screen mode hides the portrait watch header, auto-exit full screen once
        // so FocusShield can read the portrait channel header and verify instead of falsely blocking.
        val elapsedForCurrentVideo = currentTime - currentVideoFirstSeenAt
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

        // 4. Channel metadata not rendered yet: wait briefly, then treat as unknown.
        val withinGrace = elapsedForCurrentVideo < CHANNEL_METADATA_GRACE_MILLIS
        if (withinGrace) {
            return YouTubeBlockDecision(
                verdict = YouTubeBlockVerdict.ALLOW_NAVIGATION,
                reason = "Waiting for channel handle metadata"
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
