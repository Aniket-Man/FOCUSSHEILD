package com.example.feature.youtube.detector

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.feature.youtube.domain.DetectionConfidence
import com.example.feature.youtube.domain.YouTubeContentType
import com.example.feature.youtube.engine.ChannelResolution
import com.example.feature.youtube.engine.YouTubeContentEngine
import java.util.Locale

/**
 * Approval status of a detected YouTube channel.
 */
enum class ChannelApprovalStatus {
    APPROVED,
    UNAPPROVED,
    UNKNOWN
}

/**
 * Structured channel identity.
 */
data class ChannelIdentityInfo(
    val channelName: String? = null,
    val channelHandle: String? = null,
    val channelId: String? = null
) {
    val isIdentifiable: Boolean
        get() = !channelHandle.isNullOrBlank() || !channelId.isNullOrBlank() || !channelName.isNullOrBlank()

    fun getPreferredIdentifier(): String {
        return channelHandle ?: channelId ?: channelName ?: "Unknown Channel"
    }
}

/**
 * Structured information about active video playback.
 */
data class ActiveVideoInfo(
    val title: String? = null,
    val isPlayerVisible: Boolean = false,
    val isFullscreen: Boolean = false,
    val isMiniplayer: Boolean = false,
    /** True only when accessibility exposes positive evidence that this player is playing. */
    val isPlaybackActive: Boolean = false,
    val playbackSignals: List<String> = emptyList()
)

/**
 * Result produced by Engine 2 (Content Inspection).
 */
data class ContentInspectionResult(
    val contentType: YouTubeContentType = YouTubeContentType.UNKNOWN,
    val activeVideo: ActiveVideoInfo? = null,
    val channelInfo: ChannelIdentityInfo = ChannelIdentityInfo(),
    val approvalStatus: ChannelApprovalStatus = ChannelApprovalStatus.UNKNOWN,
    val confidence: DetectionConfidence = DetectionConfidence.LOW,
    val matchedSignals: List<String> = emptyList(),
    val isHomeFeed: Boolean = false
)

/**
 * Short-lived diagnostic record of a channel seen in YouTube.
 * It is never used to approve a different video's playback.
 */
data class YouTubeChannelContext(
    val channelInfo: ChannelIdentityInfo,
    val isApproved: Boolean,
    val source: String,
    val establishedAt: Long = System.currentTimeMillis(),
    val ttlMillis: Long = 45_000L // 45-second validity window
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return (now - establishedAt) > ttlMillis
    }
}

/**
 * ENGINE 2: Dedicated YouTube Content & Channel Inspection Engine.
 *
 * Responsibilities:
 * 1. What video/content is currently being consumed?
 * 2. What channel does that content belong to?
 * 3. Is that channel approved?
 *
 * Operates INDEPENDENTLY from Shorts detection.
 * Inspects the ACTIVE content/player subtree to avoid false positives from recommendation shelves,
 * in-app miniplayers, and Home feed video cards.
 */
object YouTubeContentInspectionEngine {

    // Temporal in-memory channel context for the active YouTube session
    @Volatile
    private var activeChannelContext: YouTubeChannelContext? = null

    /**
     * Clears temporal channel context (called on YouTube exit, app switch, or navigation reset).
     */
    fun clearChannelContext() {
        activeChannelContext = null
        YouTubeContentEngine.reset()
    }

    /**
     * Updates channel context when user browses a specific channel page.
     */
    fun setChannelContext(
        channelInfo: ChannelIdentityInfo,
        isApproved: Boolean,
        source: String,
        now: Long = System.currentTimeMillis()
    ) {
        if (channelInfo.isIdentifiable) {
            activeChannelContext = YouTubeChannelContext(
                channelInfo = channelInfo,
                isApproved = isApproved,
                source = source,
                establishedAt = now
            )
        }
    }

    fun getActiveChannelContext(now: Long = System.currentTimeMillis()): YouTubeChannelContext? {
        val ctx = activeChannelContext ?: return null
        if (ctx.isExpired(now)) {
            activeChannelContext = null
            return null
        }
        return ctx
    }

    /**
     * Inspects the accessibility node tree for content, active player, channels, and navigation states.
     */
    fun inspect(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        approvedIds: Set<String> = emptySet(),
        approvedNames: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis()
    ): ContentInspectionResult {
        val signals = mutableListOf<String>()

        if (rootNode == null) {
            YouTubeContentEngine.reset()
            return ContentInspectionResult(
                contentType = YouTubeContentType.UNKNOWN,
                confidence = DetectionConfidence.LOW,
                matchedSignals = listOf("no_root_node")
            )
        }

        // Playback has priority over every navigation surface.  YouTube keeps Home/Search
        // containers in its dynamic hierarchy after opening a video, so navigation must never
        // be allowed to hide a confirmed playing video.
        val activePlayer = detectActiveWatchPlayer(rootNode)
        if (activePlayer != null && !activePlayer.isMiniplayer) {
            val extractedChannel = extractChannelFromActivePlayer(rootNode)
            val isApproved = extractedChannel.isIdentifiable &&
                isChannelApproved(extractedChannel, approvedIds, approvedNames)
            val playback = YouTubeContentEngine.observe(
                videoKey = activePlayer.title,
                isPlayerVisible = activePlayer.isPlayerVisible,
                isPlaybackActive = activePlayer.isPlaybackActive,
                isChannelIdentified = extractedChannel.isIdentifiable,
                isChannelApproved = isApproved,
                now = now
            )

            signals.add("active_watch_player")
            signals.addAll(activePlayer.playbackSignals)
            when (playback.channelResolution) {
                ChannelResolution.APPROVED,
                ChannelResolution.UNAPPROVED -> {
                    val approvalStatus = if (playback.channelResolution == ChannelResolution.APPROVED) {
                        ChannelApprovalStatus.APPROVED
                    } else {
                        ChannelApprovalStatus.UNAPPROVED
                    }
                    // Only a verified active video's own channel is remembered.  A channel page
                    // or an earlier recommendation is never used as approval for this video.
                    setChannelContext(extractedChannel, isApproved, source = "ACTIVE_PLAYING_VIDEO", now = now)
                    signals.add("playback_detected")
                    signals.add("channel_${approvalStatus.name.lowercase(Locale.ROOT)}")
                    return ContentInspectionResult(
                        contentType = YouTubeContentType.VIDEO_PLAYBACK,
                        activeVideo = activePlayer,
                        channelInfo = extractedChannel,
                        approvalStatus = approvalStatus,
                        confidence = DetectionConfidence.HIGH,
                        matchedSignals = signals
                    )
                }
                ChannelResolution.WAITING_FOR_CHANNEL -> {
                    signals.add("playback_detected")
                    signals.add("channel_metadata_loading_grace_period")
                    return ContentInspectionResult(
                        contentType = YouTubeContentType.VIDEO_PLAYBACK,
                        activeVideo = activePlayer,
                        channelInfo = extractedChannel,
                        approvalStatus = ChannelApprovalStatus.UNKNOWN,
                        confidence = DetectionConfidence.LOW,
                        matchedSignals = signals
                    )
                }
                ChannelResolution.UNRESOLVED -> {
                    signals.add("playback_detected")
                    signals.add("channel_metadata_loading_expired")
                    return ContentInspectionResult(
                        contentType = YouTubeContentType.VIDEO_PLAYBACK,
                        activeVideo = activePlayer,
                        channelInfo = extractedChannel,
                        approvalStatus = ChannelApprovalStatus.UNKNOWN,
                        confidence = DetectionConfidence.MEDIUM,
                        matchedSignals = signals
                    )
                }
                ChannelResolution.NOT_PLAYING -> {
                    // A watch page, thumbnail, paused player, or remembered player is not a
                    // blockable event.  Continue to classify navigation below.
                    signals.add("video_available_not_playing")
                }
            }
        } else {
            YouTubeContentEngine.observe(
                videoKey = null,
                isPlayerVisible = false,
                isPlaybackActive = false,
                isChannelIdentified = false,
                isChannelApproved = false,
                now = now
            )
        }

        // Navigation is evaluated only after confirmed playback.  These are discovery surfaces,
        // not implicit approval grants for any video card they happen to display.
        if (isSearchInputActive(rootNode) || eventIndicatesSearch(event)) {
            signals.add("search_input_active")
            return ContentInspectionResult(
                contentType = YouTubeContentType.SEARCH_QUERY,
                confidence = DetectionConfidence.HIGH,
                matchedSignals = signals
            )
        }

        if (isSearchResultsActive(rootNode)) {
            signals.add("search_results_active")
            return ContentInspectionResult(
                contentType = YouTubeContentType.SEARCH_RESULTS,
                confidence = DetectionConfidence.HIGH,
                matchedSignals = signals
            )
        }

        if (isHomeFeedOrBottomNavActive(rootNode)) {
            clearChannelContext()
            signals.add("youtube_home_feed_browsing")
            return ContentInspectionResult(
                contentType = YouTubeContentType.YOUTUBE_HOME,
                confidence = DetectionConfidence.HIGH,
                matchedSignals = signals,
                isHomeFeed = true
            )
        }

        val channelPageInfo = detectChannelPage(rootNode)
        if (channelPageInfo != null) {
            val isApproved = isChannelApproved(channelPageInfo, approvedIds, approvedNames)
            // This context is diagnostic only; it is intentionally never used to approve a
            // different video's playback.
            setChannelContext(channelPageInfo, isApproved, source = "CHANNEL_PAGE_BROWSE", now = now)
            signals.add("channel_page:${channelPageInfo.getPreferredIdentifier()}")
            return ContentInspectionResult(
                contentType = YouTubeContentType.CHANNEL_PAGE,
                channelInfo = channelPageInfo,
                approvalStatus = if (isApproved) ChannelApprovalStatus.APPROVED else ChannelApprovalStatus.UNAPPROVED,
                confidence = DetectionConfidence.HIGH,
                matchedSignals = signals
            )
        }

        signals.add("youtube_general_navigation")
        return ContentInspectionResult(
            contentType = YouTubeContentType.YOUTUBE_NAVIGATION,
            confidence = DetectionConfidence.MEDIUM,
            matchedSignals = signals
        )
    }

    private fun isSearchInputActive(rootNode: AccessibilityNodeInfo): Boolean {
        var found = false
        scanNodes(rootNode, maxDepth = 4, maxNodes = 30) { node ->
            val viewId = node.viewIdResourceName ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""

            for (keyword in YouTubeDetectionRules.SEARCH_INPUT_VIEW_IDS) {
                if (viewId.contains(keyword, ignoreCase = true)) {
                    found = true
                    return@scanNodes true
                }
            }
            if (desc.contains("Search YouTube", ignoreCase = true) || text.contains("Search YouTube", ignoreCase = true)) {
                found = true
                return@scanNodes true
            }
            false
        }
        return found
    }

    private fun eventIndicatesSearch(event: AccessibilityEvent?): Boolean {
        if (event == null) return false
        val eventText = event.text?.joinToString(" ") ?: ""
        val eventDesc = event.contentDescription?.toString() ?: ""
        val combined = "$eventText $eventDesc"
        return combined.contains("Search YouTube", ignoreCase = true) ||
            combined.contains("Clear search query", ignoreCase = true)
    }

    private fun isSearchResultsActive(rootNode: AccessibilityNodeInfo): Boolean {
        var found = false
        scanNodes(rootNode, maxDepth = 4, maxNodes = 30) { node ->
            val viewId = node.viewIdResourceName ?: ""
            for (keyword in YouTubeDetectionRules.SEARCH_RESULTS_VIEW_IDS) {
                if (viewId.contains(keyword, ignoreCase = true)) {
                    found = true
                    return@scanNodes true
                }
            }
            false
        }
        return found
    }

    private fun detectChannelPage(rootNode: AccessibilityNodeInfo): ChannelIdentityInfo? {
        var isChannelPage = false
        var channelName: String? = null
        var channelHandle: String? = null

        scanNodes(rootNode, maxDepth = 12, maxNodes = 100) { node ->
            val viewId = node.viewIdResourceName ?: ""
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""

            for (keyword in YouTubeDetectionRules.CHANNEL_PAGE_VIEW_IDS) {
                if (viewId.contains(keyword, ignoreCase = true)) {
                    isChannelPage = true
                    break
                }
            }

            if (text.startsWith("@") && text.length in 3..40) {
                channelHandle = text
            } else if (desc.startsWith("@") && desc.length in 3..40) {
                channelHandle = desc
            }

            if (viewId.contains("channel_title", ignoreCase = true) ||
                viewId.contains("channel_name", ignoreCase = true) ||
                viewId.contains("channel_header", ignoreCase = true)
            ) {
                val valid = YouTubeDetectionRules.extractAndValidateChannelName(text)
                if (valid != null && channelName == null) {
                    channelName = valid
                }
            }

            false
        }

        return if (isChannelPage) {
            ChannelIdentityInfo(channelName = channelName, channelHandle = channelHandle)
        } else {
            null
        }
    }

    /** Detects an expanded YouTube watch player without treating feed cards as players. */
    private fun detectActiveWatchPlayer(rootNode: AccessibilityNodeInfo): ActiveVideoInfo? {
        var isPlayerFound = false
        var isMiniplayer = false
        var videoTitle: String? = null

        // Check if in-app miniplayer / floaty bar is active
        for (miniId in YouTubeDetectionRules.MINIPLAYER_VIEW_IDS) {
            val miniNodes = rootNode.findAccessibilityNodeInfosByViewId(miniId)
            val isNotEmpty = !miniNodes.isNullOrEmpty()
            miniNodes?.forEach { try { it.recycle() } catch (_: Exception) {} }
            if (isNotEmpty) {
                isMiniplayer = true
                break
            }
        }

        // Fast direct view ID search for true watch player containers
        val watchPlayerIds = listOf(
            "com.google.android.youtube:id/watch_panel",
            "com.google.android.youtube:id/watch_player",
            "com.google.android.youtube:id/watch_title_text",
            "com.google.android.youtube:id/watch_metadata_title",
            "com.google.android.youtube:id/watch_header_layout"
        )

        for (id in watchPlayerIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    isPlayerFound = true
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        if (!text.isNullOrBlank() && text.length > 3) {
                            videoTitle = text
                            break
                        }
                    }
                }
                nodes?.forEach { try { it.recycle() } catch (_: Exception) {} }
            } catch (_: Exception) {}
            if (isPlayerFound && videoTitle != null) break
        }

        if (!isPlayerFound) {
            scanNodes(rootNode, maxDepth = 8, maxNodes = 60) { node ->
                val viewId = node.viewIdResourceName ?: ""
                val text = node.text?.toString()?.trim() ?: ""

                // Ignore feed containers
                for (exId in YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS) {
                    if (viewId.contains(exId, ignoreCase = true)) {
                        return@scanNodes false
                    }
                }

                for (keyword in YouTubeDetectionRules.ACTIVE_WATCH_PLAYER_VIEW_IDS) {
                    if (viewId.contains(keyword, ignoreCase = true)) {
                        isPlayerFound = true
                        if (text.length > 3 && videoTitle == null) {
                            videoTitle = text
                        }
                        break
                    }
                }
                false
            }
        }

        // If watch player is confirmed, look for title
        if (isPlayerFound && videoTitle == null) {
            val titleIds = listOf(
                "com.google.android.youtube:id/watch_title_text",
                "com.google.android.youtube:id/watch_metadata_title"
            )
            for (id in titleIds) {
                try {
                    val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                    if (!nodes.isNullOrEmpty()) {
                        for (node in nodes) {
                            val text = node.text?.toString()?.trim()
                            if (!text.isNullOrBlank() && text.length > 3) {
                                videoTitle = text
                                break
                            }
                        }
                    }
                    nodes?.forEach { try { it.recycle() } catch (_: Exception) {} }
                } catch (_: Exception) {}
                if (videoTitle != null) break
            }
        }

        return if (isPlayerFound) {
            val playbackSignals = findActualPlaybackSignals(rootNode)
            ActiveVideoInfo(
                title = videoTitle,
                isPlayerVisible = true,
                isMiniplayer = isMiniplayer,
                isPlaybackActive = playbackSignals.isNotEmpty(),
                playbackSignals = playbackSignals
            )
        } else {
            null
        }
    }

    /**
     * A visible player is not sufficient evidence of consumption.  In YouTube the play/pause
     * control switches to a Pause label only after playback has started, which is the strongest
     * accessible signal available across watch, search, recommendation, and history launches.
     */
    private fun findActualPlaybackSignals(rootNode: AccessibilityNodeInfo): List<String> {
        val signals = linkedSetOf<String>()
        scanNodes(rootNode, maxDepth = 12, maxNodes = 140) { node ->
            val viewId = node.viewIdResourceName ?: ""
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val label = if (desc.isNotBlank()) desc else text
            val isPlayerControl = viewId.contains("play_pause", ignoreCase = true) ||
                viewId.contains("player_control", ignoreCase = true) ||
                viewId.contains("pause_button", ignoreCase = true) ||
                viewId.contains("player_button", ignoreCase = true)
            val isPauseLabel = label.equals("Pause", ignoreCase = true) ||
                label.equals("Pause video", ignoreCase = true) ||
                label.startsWith("Pause ", ignoreCase = true)

            if (isPauseLabel && (isPlayerControl || viewId.contains("player", ignoreCase = true))) {
                signals.add("playback_pause_control")
            }
            false
        }
        return signals.toList()
    }

    /**
     * Inspects active watch subtree for channel information, excluding feed containers & recommendations.
     */
    private fun extractChannelFromActivePlayer(rootNode: AccessibilityNodeInfo): ChannelIdentityInfo {
        var channelName: String? = null
        var channelHandle: String? = null
        val channelId: String? = null

        // Fast direct view ID search for verified channel name view IDs
        val fastChannelIds = listOf(
            "com.google.android.youtube:id/channel_name",
            "com.google.android.youtube:id/owner_name",
            "com.google.android.youtube:id/owner_text",
            "com.google.android.youtube:id/channel_title"
        )
        for (id in fastChannelIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        if (!text.isNullOrBlank()) {
                            val validName = YouTubeDetectionRules.extractAndValidateChannelName(text)
                            if (validName != null) {
                                channelName = validName
                                break
                            }
                        }
                    }
                }
                nodes?.forEach { try { it.recycle() } catch (_: Exception) {} }
            } catch (_: Exception) {}
            if (!channelName.isNullOrBlank()) break
        }

        scanNodes(rootNode, maxDepth = 15, maxNodes = 120) { node ->
            val viewId = node.viewIdResourceName ?: ""
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""

            // Strict exclusion of recommendation lists, home feed grids, video cards, and comments
            for (exId in YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS) {
                if (viewId.contains(exId, ignoreCase = true)) {
                    return@scanNodes false
                }
            }

            // Direct Handle detection: only from channel header or byline views, never inside comments
            if (text.startsWith("@") && text.length in 3..40 && channelHandle == null) {
                // Ensure viewId doesn't belong to a comment or chat
                val isCommentRelated = YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS.any { viewId.contains(it, ignoreCase = true) }
                if (!isCommentRelated) {
                    channelHandle = text
                }
            } else if (desc.startsWith("@") && desc.length in 3..40 && channelHandle == null) {
                val isCommentRelated = YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS.any { viewId.contains(it, ignoreCase = true) }
                if (!isCommentRelated) {
                    channelHandle = desc
                }
            }

            // Channel byline / title near active player
            for (chanId in YouTubeDetectionRules.CHANNEL_IDENTITY_VIEW_IDS) {
                if (viewId.contains(chanId, ignoreCase = true)) {
                    if (text.isNotBlank() && channelName == null) {
                        val validName = YouTubeDetectionRules.extractAndValidateChannelName(text)
                        if (validName != null) {
                            channelName = validName
                        }
                    }
                }
            }

            // Content Descriptions: "Subscribe to [Channel]", "Go to channel [Channel]"
            if (desc.contains("Subscribe to", ignoreCase = true)) {
                val rawCandidate = desc.substringAfter("Subscribe to", "").substringBefore(".").trim()
                val validName = YouTubeDetectionRules.extractAndValidateChannelName(rawCandidate)
                if (validName != null && channelName == null) {
                    channelName = validName
                }
            } else if (desc.contains("Go to channel", ignoreCase = true) || desc.contains("View channel", ignoreCase = true)) {
                val rawCandidate = desc.replace("Go to channel", "", ignoreCase = true)
                    .replace("View channel", "", ignoreCase = true).trim()
                val validName = YouTubeDetectionRules.extractAndValidateChannelName(rawCandidate)
                if (validName != null && channelName == null) {
                    channelName = validName
                }
            }

            false
        }

        return ChannelIdentityInfo(
            channelName = channelName,
            channelHandle = channelHandle,
            channelId = channelId
        )
    }

    private fun isHomeFeedOrBottomNavActive(rootNode: AccessibilityNodeInfo): Boolean {
        var foundHome = false
        scanNodes(rootNode, maxDepth = 6, maxNodes = 50) { node ->
            val viewId = node.viewIdResourceName ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val isExplicitHomeFeed = viewId.contains("home_feed", ignoreCase = true)
            val isHomePivot = viewId.contains("pivot_home", ignoreCase = true) ||
                viewId.contains("tab_home", ignoreCase = true) ||
                viewId.contains("navigation_home", ignoreCase = true)
            val isSelectedHome = desc.contains("home, selected", ignoreCase = true) ||
                desc.contains("home tab, selected", ignoreCase = true) ||
                ((desc.equals("Home", ignoreCase = true) || text.equals("Home", ignoreCase = true)) && node.isSelected)

            // Bottom navigation is present on most YouTube screens.  It marks Home only when
            // the Home pivot is selected; merely finding a Home button must not allow a video.
            if (isExplicitHomeFeed || (isHomePivot && isSelectedHome)) {
                foundHome = true
                return@scanNodes true
            }
            false
        }
        return foundHome
    }

    /**
     * Checks whether candidate channel is in the approved sets with rich fuzzy matching.
     */
    fun isChannelApproved(
        channelInfo: ChannelIdentityInfo,
        approvedIds: Set<String>,
        approvedNames: Set<String>
    ): Boolean {
        val handle = channelInfo.channelHandle
        val id = channelInfo.channelId
        val name = channelInfo.channelName

        if (!handle.isNullOrBlank()) {
            val normHandle = handle.trim().lowercase(Locale.ROOT).replace("@", "")
            if (approvedIds.contains(normHandle) || approvedIds.contains("@$normHandle")) return true
            for (appId in approvedIds) {
                val normAppId = appId.lowercase(Locale.ROOT).replace("@", "")
                if (normAppId.isNotBlank() && (normHandle.contains(normAppId) || normAppId.contains(normHandle))) {
                    return true
                }
            }
        }

        if (!id.isNullOrBlank()) {
            val normId = id.trim().lowercase(Locale.ROOT).replace("@", "")
            if (approvedIds.contains(normId) || approvedIds.contains("@$normId")) return true
        }

        if (!name.isNullOrBlank()) {
            val normCandidate = YouTubeDetectionRules.normalizeString(name)
            if (normCandidate.isNotBlank()) {
                if (approvedNames.contains(normCandidate)) return true
                for (approved in approvedNames) {
                    val normApproved = YouTubeDetectionRules.normalizeString(approved)
                    if (normApproved.isNotBlank()) {
                        if (normCandidate.contains(normApproved) || normApproved.contains(normCandidate)) {
                            return true
                        }
                    }
                }
                for (appId in approvedIds) {
                    val normAppId = YouTubeDetectionRules.normalizeString(appId)
                    if (normAppId.isNotBlank() && normAppId.length >= 4) {
                        if (normCandidate.contains(normAppId) || normAppId.contains(normCandidate)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun scanNodes(
        node: AccessibilityNodeInfo?,
        currentDepth: Int = 0,
        maxDepth: Int,
        nodeCount: IntArray = intArrayOf(0),
        maxNodes: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ) {
        if (node == null || currentDepth > maxDepth || nodeCount[0] >= maxNodes) return
        nodeCount[0]++

        val viewId = node.viewIdResourceName ?: ""
        // Prune excluded subtrees completely (comments, live chat, engagement panels, feed recommendations)
        for (exId in YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS) {
            if (viewId.contains(exId, ignoreCase = true)) {
                return // DO NOT RECURSE into comments / suggestions!
            }
        }

        if (predicate(node)) return

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (nodeCount[0] >= maxNodes) break
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            if (child != null) {
                scanNodes(child, currentDepth + 1, maxDepth, nodeCount, maxNodes, predicate)
                try { child.recycle() } catch (_: Exception) {}
            }
        }
    }
}
