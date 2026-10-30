package com.example.feature.youtube.engine

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.feature.youtube.detector.YouTubeDetectionRules

/**
 * Extracts YouTube UI state from the accessibility node tree using direct,
 * fully-qualified view ID lookups with bounded tree scans as fallback.
 *
 * Only signals that prove ACTIVE content consumption are treated as evidence:
 *  - reel_player_page_container / reel_player_view => Shorts is actively playing
 *  - watch_panel / watch_player                   => full watch page (never the Home feed miniplayer)
 *
 * Shorts shelves embedded in the Home feed intentionally expose none of those
 * container IDs, so browsing the feed never triggers a block.
 */
object YouTubeNodeExtractor {

    private const val YT = "com.google.android.youtube"

    private val SHORTS_PLAYER_VIEW_IDS = listOf(
        "$YT:id/reel_player_page_container",
        "$YT:id/reel_player_view",
        "$YT:id/reel_player_overlay"
    )

    private val SHORTS_ACTION_DESCRIPTIONS = listOf(
        "Like this Short",
        "Dislike this Short",
        "Remix this Short",
        "Sound used in this Short",
        "Share this Short"
    )

    private val WATCH_PAGE_VIEW_IDS = listOf(
        "$YT:id/watch_panel",
        "$YT:id/watch_player",
        "$YT:id/watch_header_layout",
        "$YT:id/watch_swipey_item",
        "$YT:id/watch_metadata_title",
        "$YT:id/watch_title_text",
        "$YT:id/player_view",
        "$YT:id/player_overlays",
        "$YT:id/player_control_screen",
        "$YT:id/youtube_controls_overlay",
        "$YT:id/video_surface",
        "$YT:id/fullscreen_button",
        "$YT:id/youtube_controls_fullscreen_button",
        "$YT:id/fullscreen_engagement_panel",
        "$YT:id/quick_actions_container"
    )

    private val WATCH_CONTAINER_VIEW_IDS = listOf(
        "$YT:id/watch_header_layout",
        "$YT:id/watch_panel",
        "$YT:id/watch_layout"
    )

    private val PORTRAIT_WATCH_HEADER_VIEW_IDS = listOf(
        "$YT:id/watch_header_layout",
        "$YT:id/subscribe_button",
        "$YT:id/subscribe_button_text",
        "$YT:id/channel_handle",
        "$YT:id/watch_metadata_byline"
    )

    private val AD_VIEW_IDS = listOf(
        "$YT:id/skip_ad_button",
        "$YT:id/skip_ad_button_container",
        "$YT:id/skip_ad_button_text",
        "$YT:id/ad_progress_text",
        "$YT:id/ad_countdown_text",
        "$YT:id/ad_countdown",
        "$YT:id/ad_skip_button",
        "$YT:id/ad_badge",
        "$YT:id/ad_duration",
        "$YT:id/ad_time_remaining",
        "$YT:id/ad_view",
        "$YT:id/ad_break",
        "$YT:id/modern_ad_ui",
        "$YT:id/ad_headline",
        "$YT:id/ad_cta_button",
        "$YT:id/ad_layout",
        "$YT:id/visit_advertiser_button"
    )

    private val SUBSCRIBE_BUTTON_IDS = listOf(
        "$YT:id/subscribe_button",
        "$YT:id/subscribe_button_text",
        "$YT:id/subscription_notification_toggle_button"
    )

    private val WATCH_HEADER_TITLE_VIEW_IDS = listOf(
        "$YT:id/watch_title_text",
        "$YT:id/watch_metadata_title",
        "$YT:id/title_and_badge",
        "$YT:id/headline_title"
    )

    private val TITLE_VIEW_IDS = listOf(
        "$YT:id/watch_title_text",
        "$YT:id/watch_metadata_title",
        "$YT:id/title_and_badge",
        "$YT:id/headline_title",
        "$YT:id/title"
    )

    private val CHANNEL_VIEW_IDS = listOf(
        "$YT:id/channel_name",
        "$YT:id/owner_text",
        "$YT:id/owner_name",
        "$YT:id/channel_title",
        "$YT:id/channel_header",
        "$YT:id/owner_layout",
        "$YT:id/channel_handle",
        "$YT:id/byline",
        "$YT:id/byline_text",
        "$YT:id/byline_view",
        "$YT:id/watch_metadata_byline",
        "$YT:id/watch_metadata_subtitle",
        "$YT:id/metadata_line",
        "$YT:id/channel_sub_text",
        "$YT:id/upload_info",
        "$YT:id/subtitle"
    )

    private val HANDLE_VIEW_IDS = listOf(
        "$YT:id/channel_handle",
        "$YT:id/byline",
        "$YT:id/byline_text",
        "$YT:id/byline_view",
        "$YT:id/watch_metadata_subtitle",
        "$YT:id/watch_metadata_byline",
        "$YT:id/metadata_line",
        "$YT:id/owner_text",
        "$YT:id/owner_name",
        "$YT:id/channel_name",
        "$YT:id/channel_title"
    )

    private val EXCLUDED_TITLE_VIEW_KEYWORDS = listOf(
        "watch_title_text",
        "watch_metadata_title",
        "video_title",
        "compact_media_item_headline",
        "title_text",
        "header_title",
        "description_sheet",
        "expandable_metadata",
        "video_description",
        "description",
        "comment",
        "reply"
    )

    val HANDLE_REGEX = Regex("@[A-Za-z0-9_.\\-]{2,50}")

    private val HOME_FEED_VIEW_IDS = listOf(
        "$YT:id/home_feed"
    )

    /**
     * Safely executes [block] with the list of nodes found by [viewId] on [rootNode] using in-memory BFS.
     * In modern Android, using in-memory traversal avoids Binder IPC prefetch cache corruption
     * and completely prevents AccessibilityInteractionClient disconnected node errors.
     */
    inline fun <T> findNodesByViewId(
        rootNode: AccessibilityNodeInfo?,
        viewId: String,
        block: (List<AccessibilityNodeInfo>) -> T
    ): T? {
        if (rootNode == null) return null
        val matches = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        val rawTargetId = viewId.substringAfterLast(":id/").substringAfterLast("/")
        while (queue.isNotEmpty() && visited < 150) {
            val node = queue.removeFirst()
            visited++
            val resId = node.viewIdResourceName ?: ""
            if (resId.isNotEmpty()) {
                val rawResId = resId.substringAfterLast(":id/").substringAfterLast("/")
                if (resId == viewId || rawResId.equals(rawTargetId, ignoreCase = true)) {
                    matches.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        return block(matches)
    }

    /**
     * Safely executes [block] with the list of nodes found by [text] on [rootNode] using in-memory BFS.
     */
    inline fun <T> findNodesByText(
        rootNode: AccessibilityNodeInfo?,
        text: String,
        block: (List<AccessibilityNodeInfo>) -> T
    ): T? {
        if (rootNode == null) return null
        val matches = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        while (queue.isNotEmpty() && visited < 150) {
            val node = queue.removeFirst()
            visited++
            val nodeText = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            if (nodeText.contains(text, ignoreCase = true) || desc.contains(text, ignoreCase = true)) {
                matches.add(node)
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        return block(matches)
    }

    /**
     * Finds the first node matching any of [viewIds] in a single BFS pass.
     */
    inline fun <T> findFirstMatching(
        rootNode: AccessibilityNodeInfo?,
        viewIds: Collection<String>,
        maxNodes: Int = 400,
        predicate: (AccessibilityNodeInfo) -> T?
    ): T? {
        if (rootNode == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        val targetNames = viewIds.map { it.substringAfterLast(":id/").substringAfterLast("/") }
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            val resId = node.viewIdResourceName ?: ""
            if (resId.isNotEmpty()) {
                val rawResId = resId.substringAfterLast(":id/").substringAfterLast("/")
                val matches = viewIds.any { resId == it } || targetNames.any { it.equals(rawResId, ignoreCase = true) }
                if (matches) {
                    val result = predicate(node)
                    if (result != null) return result
                }
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        return null
    }

    private val MAX_SCAN_DEPTH = 15
    private val MAX_SCAN_NODES = 400

    /**
     * Detects whether a Shorts player is actively on screen.
     * A Shorts shelf on the Home feed does not expose the reel player
     * container IDs, so it is never mistaken for active playback.
     */
    fun detectShorts(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?): Boolean {
        if (rootNode == null) return false
        return try {
            for (viewId in SHORTS_PLAYER_VIEW_IDS) {
                val found = findNodesByViewId(rootNode, viewId) { nodes ->
                    nodes.isNotEmpty()
                } ?: false
                if (found) return true
            }

            event?.let { hasShortsEventSignal(it) } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fast event-level Shorts signal: content descriptions of Short-specific
     * action controls or the selected Shorts tab.
     */
    private fun hasShortsEventSignal(event: AccessibilityEvent): Boolean {
        val eventText = event.text?.joinToString(" ") { it?.toString() ?: "" } ?: ""
        val eventDesc = event.contentDescription?.toString() ?: ""
        val combined = "$eventText $eventDesc"

        for (desc in SHORTS_ACTION_DESCRIPTIONS) {
            if (combined.contains(desc, ignoreCase = true)) return true
        }
        if (combined.contains("Shorts, selected", ignoreCase = true)) return true
        if (combined.contains("Shorts tab", ignoreCase = true) && combined.contains("selected", ignoreCase = true)) return true
        return false
    }

    /**
     * Detects whether the full video watch page is active.
     * watch_panel / watch_player only exist on the actual watch screen,
     * never for inline Home feed playback or the docked miniplayer.
     */
    fun detectWatchPage(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        return try {
            for (viewId in WATCH_PAGE_VIEW_IDS) {
                val found = findNodesByViewId(rootNode, viewId) { it.isNotEmpty() } ?: false
                if (found) return true
            }
            if (findNodesByViewId(rootNode, "$YT:id/watch_title_text") { it.isNotEmpty() } == true) {
                return true
            }
            // In full-screen mode when player controls auto-hide, check if "Exit full screen" or
            // landscape watch_while_layout without Home/Shorts/Search navigation is active.
            isFullScreenVideoPlayback(rootNode)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detects whether the YouTube video player is currently in Full-Screen mode.
     * In full-screen mode, the portrait watch metadata header below the player is hidden:
     *  - The full-screen toggle button exposes "Exit full screen" (instead of "Enter full screen"), OR
     *  - Full-screen engagement / quick action containers are present, OR
     *  - The active window bounds are in landscape orientation while not on Home/Shorts/Search, OR
     *  - An active player surface is present while both the portrait watch header (subscribe/byline)
     *    and bottom navigation bar (pivot_bar / home_feed) are absent.
     */
    fun isFullScreenVideoPlayback(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        try {
            if (detectShorts(rootNode, null) || isHomeFeed(rootNode)) return false

            var hasExitFullScreenControl = false
            var hasEnterFullScreenControl = false
            var hasFullScreenContainer = false
            var hasPlayerSurface = false
            var hasPortraitHeaderOrSubscribe = false
            var hasBottomNavOrSearch = false

            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visited = 0
            while (queue.isNotEmpty() && visited < 220) {
                val node = queue.removeFirst()
                visited++

                val resId = node.viewIdResourceName ?: ""
                val rawResId = resId.substringAfterLast(":id/").substringAfterLast("/")
                val desc = node.contentDescription?.toString()?.trim() ?: ""

                if (desc.equals("Exit full screen", ignoreCase = true) ||
                    desc.equals("Exit fullscreen", ignoreCase = true) ||
                    rawResId.equals("exit_fullscreen", ignoreCase = true) ||
                    rawResId.equals("exit_fullscreen_button", ignoreCase = true)
                ) {
                    hasExitFullScreenControl = true
                }
                if (desc.equals("Enter full screen", ignoreCase = true) ||
                    desc.equals("Enter fullscreen", ignoreCase = true)
                ) {
                    hasEnterFullScreenControl = true
                }
                if (rawResId.contains("fullscreen_engagement", ignoreCase = true) ||
                    rawResId.contains("quick_actions_container", ignoreCase = true)
                ) {
                    hasFullScreenContainer = true
                }
                if (rawResId.equals("player_view", ignoreCase = true) ||
                    rawResId.equals("watch_player", ignoreCase = true) ||
                    rawResId.equals("watch_panel", ignoreCase = true) ||
                    rawResId.equals("player_overlays", ignoreCase = true) ||
                    rawResId.equals("player_control_screen", ignoreCase = true) ||
                    rawResId.equals("youtube_controls_overlay", ignoreCase = true) ||
                    rawResId.equals("video_surface", ignoreCase = true) ||
                    rawResId.equals("time_bar", ignoreCase = true) ||
                    rawResId.equals("fullscreen_button", ignoreCase = true) ||
                    rawResId.equals("youtube_controls_fullscreen_button", ignoreCase = true)
                ) {
                    hasPlayerSurface = true
                }
                if (rawResId.equals("watch_header_layout", ignoreCase = true) ||
                    rawResId.equals("subscribe_button", ignoreCase = true) ||
                    rawResId.equals("subscribe_button_text", ignoreCase = true) ||
                    desc.startsWith("Subscribe to ", ignoreCase = true) ||
                    desc.startsWith("Subscribed to ", ignoreCase = true)
                ) {
                    hasPortraitHeaderOrSubscribe = true
                }
                if (rawResId.contains("pivot_bar", ignoreCase = true) ||
                    rawResId.contains("bottom_navigation", ignoreCase = true) ||
                    rawResId.contains("search_results", ignoreCase = true) ||
                    rawResId.contains("home_feed", ignoreCase = true)
                ) {
                    hasBottomNavOrSearch = true
                }

                for (i in 0 until node.childCount) {
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) queue.add(child)
                }
            }

            if (hasExitFullScreenControl || hasFullScreenContainer) return true
            if (hasEnterFullScreenControl || hasPortraitHeaderOrSubscribe || hasBottomNavOrSearch) return false

            val bounds = android.graphics.Rect()
            rootNode.getBoundsInScreen(bounds)
            val isLandscapeBounds = bounds.width() > bounds.height() && bounds.width() > 0
            if (isLandscapeBounds && (hasPlayerSurface || visited > 1)) {
                return true
            }

            if (hasPlayerSurface && !hasPortraitHeaderOrSubscribe && !hasBottomNavOrSearch) {
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Attempts to click the "Exit full screen" button in the YouTube player overlay so the
     * player transitions from full-screen mode back to the portrait watch page (without closing the video).
     */
    fun clickExitFullScreenButton(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visited = 0
            while (queue.isNotEmpty() && visited < 150) {
                val node = queue.removeFirst()
                visited++
                val resId = node.viewIdResourceName ?: ""
                val rawResId = resId.substringAfterLast(":id/").substringAfterLast("/")
                val desc = node.contentDescription?.toString()?.trim() ?: ""

                val isExitFs = desc.equals("Exit full screen", ignoreCase = true) ||
                        desc.equals("Exit fullscreen", ignoreCase = true) ||
                        rawResId.equals("exit_fullscreen_button", ignoreCase = true) ||
                        (rawResId.contains("fullscreen_button", ignoreCase = true) &&
                                !desc.equals("Enter full screen", ignoreCase = true))

                if (isExitFs) {
                    var target: AccessibilityNodeInfo? = node
                    var depth = 0
                    while (target != null && depth < 4) {
                        if (target.isClickable) {
                            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                return true
                            }
                        }
                        target = try { target.parent } catch (_: Exception) { null }
                        depth++
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
     * Detects the YouTube Home feed so Study Mode can show its nudge popup.
     */
    fun isHomeFeed(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        return try {
            for (viewId in HOME_FEED_VIEW_IDS) {
                val found = findNodesByViewId(rootNode, viewId) { it.isNotEmpty() } ?: false
                if (found) return true
            }
            isTabSelected(rootNode, "pivot_home") || isTabSelected(rootNode, "tab_home")
        } catch (_: Exception) {
            false
        }
    }

    private fun isTabSelected(rootNode: AccessibilityNodeInfo, viewId: String): Boolean {
        return findNodesByViewId(rootNode, "$YT:id/$viewId") { nodes ->
            nodes.any { it.isSelected || (it.contentDescription?.contains("selected", ignoreCase = true) == true) }
        } ?: false
    }

    /**
     * Checks if the video is currently paused by searching for a Play button
     * in the active player. If "Play video" is visible, the video is paused.
     */
    fun isVideoPaused(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visited = 0
            while (queue.isNotEmpty() && visited < 400) { // Limit search depth
                val node = queue.removeFirst()
                visited++
                
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (desc.equals("Play video", ignoreCase = true) || desc.equals("Play", ignoreCase = true)) {
                    return true
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
     * Checks if the video is actively playing in the watch player (e.g. Pause control is present).
     */
    fun isVideoPlaying(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            var visited = 0
            var foundPause = false
            var foundPlay = false
            while (queue.isNotEmpty() && visited < 400) {
                val node = queue.removeFirst()
                visited++

                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (desc.equals("Pause video", ignoreCase = true) || desc.equals("Pause", ignoreCase = true)) {
                    foundPause = true
                } else if (desc.equals("Play video", ignoreCase = true) || desc.equals("Play", ignoreCase = true)) {
                    foundPlay = true
                }

                for (i in 0 until node.childCount) {
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) queue.add(child)
                }
            }
            if (foundPlay) return false
            if (foundPause) return true
        } catch (_: Exception) {}
        return false
    }

    /**
     * Extracts the title of the video currently on the watch page.
     * Strictly prioritizes dedicated watch header title views and ignores recommendation cards.
     */
    fun extractVideoTitle(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        val strictHeaderTitle = findFirstMatching(rootNode, WATCH_HEADER_TITLE_VIEW_IDS) { node ->
            if (isInsideExcludedContainer(node)) return@findFirstMatching null
            val text = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length >= 3) text else null
        }
        if (strictHeaderTitle != null) return strictHeaderTitle

        return findFirstMatching(rootNode, TITLE_VIEW_IDS) { node ->
            if (isInsideExcludedContainer(node)) return@findFirstMatching null
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length >= 3) text else null
        }
    }

    /**
     * Extracts the strict watch-header video title (never falling back to generic title IDs).
     */
    fun extractWatchHeaderTitle(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return findFirstMatching(rootNode, WATCH_HEADER_TITLE_VIEW_IDS) { node ->
            if (isInsideExcludedContainer(node)) return@findFirstMatching null
            val text = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length >= 3) text else null
        }
    }

    /**
     * Detects whether an in-stream video advertisement is actively playing in the watch player.
     * When an in-stream ad is playing, blocking decisions and channel tracking are suspended.
     */
    fun detectAd(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        // 1. Direct view ID lookup for in-stream player ad controls ONLY
        val hasAdControl = findFirstMatching(rootNode, AD_VIEW_IDS) { true } ?: false
        if (hasAdControl) return true

        // 2. Scan watch player area ONLY for explicit in-stream ad countdown/skip text
        try {
            var visited = 0
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            while (queue.isNotEmpty() && visited < 80) {
                val node = queue.removeFirst()
                visited++

                val resId = node.viewIdResourceName ?: ""
                // Skip recommendations, comments, and companion promo cards
                if (YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS.any { resId.contains(it, ignoreCase = true) }) {
                    continue
                }

                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""

                if (YouTubeDetectionRules.isAdElement(resId, text, desc)) {
                    return true
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
     * Structured dual channel identity extracted from the active YouTube watch header.
     * Holds both the @handle under the title (e.g., "@SonypicturesBrasil") and the
     * full channel display name from the Subscribe button / avatar / owner bar (e.g., "Sony Pictures Brasil").
     */
    data class WatchChannelIdentity(
        val channelHandle: String? = null,
        val channelName: String? = null
    ) {
        val isIdentifiable: Boolean
            get() = !channelHandle.isNullOrBlank() || !channelName.isNullOrBlank()

        val preferredDisplay: String?
            get() = channelName ?: channelHandle
    }

    /**
     * Checks if [node] or any of its ancestors (up to 5 levels) belongs to a recommendation card,
     * comment thread, or ad companion overlay so below-the-fold channels (e.g. BnfTV, Food Pharmer)
     * are never mistaken for the active video's channel.
     */
    fun isNodeInExcludedContainer(node: AccessibilityNodeInfo?): Boolean = isInsideExcludedContainer(node)

    private fun isInsideExcludedContainer(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var depth = 0
        while (current != null && depth < 6) {
            val resId = current.viewIdResourceName ?: ""
            if (resId.isNotEmpty()) {
                if (YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS.any {
                        resId.contains(it, ignoreCase = true)
                    }
                ) {
                    return true
                }
            }
            val desc = current.contentDescription?.toString() ?: ""
            if (desc.contains(" - Go to channel - ", ignoreCase = true) ||
                desc.endsWith("- play video", ignoreCase = true) ||
                desc.equals("More videos", ignoreCase = true)
            ) {
                return true
            }
            if (depth in 1..2) {
                // Check if this container has an "Action menu" or "- play video" sibling indicating a feed video card
                val childCount = current.childCount
                if (childCount in 2..12) {
                    var hasActionMenu = false
                    var hasPlayVideo = false
                    var hasSubscribeButton = false
                    for (i in 0 until childCount) {
                        val sibling = try { current.getChild(i) } catch (_: Exception) { null } ?: continue
                        val sDesc = sibling.contentDescription?.toString()?.trim() ?: ""
                        val sRes = sibling.viewIdResourceName ?: ""
                        if (sDesc.equals("Action menu", ignoreCase = true)) {
                            hasActionMenu = true
                        }
                        if (sDesc.contains(" - Go to channel - ", ignoreCase = true) ||
                            sDesc.endsWith("- play video", ignoreCase = true)
                        ) {
                            hasPlayVideo = true
                        }
                        if (sDesc.startsWith("Subscribe to ", ignoreCase = true) ||
                            sDesc.startsWith("Subscribed to ", ignoreCase = true) ||
                            sRes.contains("subscribe_button", ignoreCase = true)
                        ) {
                            hasSubscribeButton = true
                        }
                    }
                    if ((hasActionMenu || hasPlayVideo) && !hasSubscribeButton) {
                        return true
                    }
                }
            }
            current = try { current.parent } catch (_: Exception) { null }
            depth++
        }
        return false
    }

    /**
     * Determines whether a TYPE_VIEW_CLICKED event is an interaction with the video player's own controls
     * (e.g. "Enter full screen", "Exit full screen", Play/Pause, Seek bar, 10s skip, Video quality,
     * Playback speed, Captions, Lock screen, or tapping the player overlay to show/hide controls)
     * rather than clicking a new video card.
     */
    fun isPlayerControlOrFullscreenInteraction(
        event: AccessibilityEvent?,
        rootNode: AccessibilityNodeInfo? = null
    ): Boolean {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return false
        }
        try {
            // If the click event carries a video card identity ("- Go to channel -"), it is a video navigation click!
            val (clickedCardChannel, _) = extractClickedVideoCardIdentity(event)
            if (!clickedCardChannel.isNullOrBlank()) {
                return false
            }

            val eventDesc = event.contentDescription?.toString()?.trim() ?: ""
            val eventText = event.text?.joinToString(" ") { it?.toString()?.trim() ?: "" }?.trim() ?: ""
            val source = try { event.source } catch (_: Exception) { null }
            val sourceResId = source?.viewIdResourceName ?: ""
            val sourceDesc = source?.contentDescription?.toString()?.trim() ?: ""
            val sourceText = source?.text?.toString()?.trim() ?: ""
            val parent = try { source?.parent } catch (_: Exception) { null }
            val parentResId = parent?.viewIdResourceName ?: ""
            val parentDesc = parent?.contentDescription?.toString()?.trim() ?: ""

            val allDescs = listOf(eventDesc, eventText, sourceDesc, sourceText, parentDesc)
                .filter { it.isNotBlank() }
            val allResIds = listOf(sourceResId, parentResId)
                .filter { it.isNotBlank() }

            // 1. Check explicit player control descriptions (Enter/Exit full screen, Play/Pause, Quality, Speed, Seek...)
            for (d in allDescs) {
                if (YouTubeDetectionRules.PLAYER_CONTROL_DESCRIPTIONS.any { d.equals(it, ignoreCase = true) }) {
                    return true
                }
                if (d.startsWith("Enter full screen", ignoreCase = true) ||
                    d.startsWith("Exit full screen", ignoreCase = true) ||
                    d.startsWith("Full screen", ignoreCase = true) ||
                    d.startsWith("Seek", ignoreCase = true) ||
                    d.startsWith("Fast-forward", ignoreCase = true) ||
                    d.startsWith("Fast forward", ignoreCase = true) ||
                    d.startsWith("Rewind", ignoreCase = true) ||
                    d.startsWith("Double-tap to seek", ignoreCase = true) ||
                    d.startsWith("Playback speed", ignoreCase = true) ||
                    d.startsWith("Video quality", ignoreCase = true) ||
                    d.startsWith("Quality", ignoreCase = true) ||
                    d.startsWith("Captions", ignoreCase = true) ||
                    d.startsWith("Subtitles", ignoreCase = true)
                ) {
                    return true
                }
            }

            // 2. Check explicit player control view IDs
            for (res in allResIds) {
                val rawRes = res.substringAfterLast(":id/").substringAfterLast("/")
                if (YouTubeDetectionRules.PLAYER_CONTROL_VIEW_IDS.any { rawRes.equals(it, ignoreCase = true) || rawRes.contains(it, ignoreCase = true) }) {
                    return true
                }
            }

            // 3. Tapping the active video player surface itself (to show/hide full-screen or portrait overlay controls)
            if (source != null && !isInsideExcludedContainer(source)) {
                val rawSourceRes = sourceResId.substringAfterLast(":id/").substringAfterLast("/")
                val rawParentRes = parentResId.substringAfterLast(":id/").substringAfterLast("/")
                val playerSurfaceIds = listOf(
                    "player_view", "watch_player", "player_overlays",
                    "player_control_screen", "controls_layout", "youtube_controls_overlay",
                    "scrim_overlay", "video_surface", "video_zoom_overlay"
                )
                if (playerSurfaceIds.any { rawSourceRes.equals(it, ignoreCase = true) || rawParentRes.equals(it, ignoreCase = true) }) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * When the user clicks a video card in the below-player recommendation feed (or search/home feed),
     * extracts the clicked video's (channelName, videoTitle) directly from the clicked AccessibilityEvent
     * if YouTube's accessibility card label ("<Title> - <Duration> - Go to channel - <Channel> - <Views> - <Time> - play video")
     * is present on the clicked node or its immediate parent/children.
     */
    fun extractClickedVideoCardIdentity(event: AccessibilityEvent?): Pair<String?, String?> {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return null to null
        }
        try {
            val eventDesc = event.contentDescription?.toString()?.trim() ?: ""
            val eventText = event.text?.joinToString(" ") { it?.toString()?.trim() ?: "" }?.trim() ?: ""

            // Ignore clicks on overflow menu, pause/play controls, full screen toggle, collapse, etc.
            val ignoredControlLabels = YouTubeDetectionRules.PLAYER_CONTROL_DESCRIPTIONS + listOf(
                "Action menu", "Navigate up", "Share", "Like", "Dislike", "Save"
            )
            if (ignoredControlLabels.any { eventDesc.equals(it, ignoreCase = true) || eventText.equals(it, ignoreCase = true) }) {
                return null to null
            }

            val candidates = mutableListOf<String>()
            if (eventDesc.isNotBlank()) candidates.add(eventDesc)
            if (eventText.isNotBlank()) candidates.add(eventText)

            val source = try { event.source } catch (_: Exception) { null }
            if (source != null) {
                source.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                source.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                for (i in 0 until minOf(source.childCount, 8)) {
                    val child = try { source.getChild(i) } catch (_: Exception) { null } ?: continue
                    child.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                    child.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                }
                val parent = try { source.parent } catch (_: Exception) { null }
                if (parent != null) {
                    parent.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                    for (i in 0 until minOf(parent.childCount, 8)) {
                        val sibling = try { parent.getChild(i) } catch (_: Exception) { null } ?: continue
                        sibling.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
                    }
                }
            }

            for (raw in candidates) {
                val marker = " - Go to channel - "
                val idx = raw.indexOf(marker, ignoreCase = true)
                if (idx >= 0) {
                    val beforeMarker = raw.substring(0, idx).trim()
                    val afterMarker = raw.substring(idx + marker.length).trim()
                    val channelPart = afterMarker.substringBefore(" - ").trim()
                    val cleanedChannel = cleanChannelName(channelPart)
                    val titlePart = beforeMarker.substringBeforeLast(" - ").trim().ifEmpty { beforeMarker }
                    if (!cleanedChannel.isNullOrBlank()) {
                        return cleanedChannel to titlePart.takeIf { it.isNotBlank() }
                    }
                }
            }
        } catch (_: Exception) {}
        return null to null
    }

    /**
     * Extracts BOTH the @handle and the full channel display name from the active watch header.
     * Cross-checks the watch subtitle/byline (@handle), Subscribe button description,
     * channel avatar description, and watch owner views while strictly ignoring recommendations.
     */
    fun extractChannelIdentity(rootNode: AccessibilityNodeInfo?): WatchChannelIdentity {
        if (rootNode == null) return WatchChannelIdentity()

        val handle = extractChannelHandle(rootNode)
        val fromSubscribe = extractChannelFromSubscribeButton(rootNode)
        val fromAvatar = extractChannelFromAvatar(rootNode)
        val fromOwnerView = extractChannelDisplayNameFromOwnerViews(rootNode)
        val fromWatchPlayer = if (fromSubscribe == null && fromAvatar == null && fromOwnerView == null) {
            extractChannelFromWatchPlayer(rootNode)
        } else null

        // Pick the best non-handle display name if available
        val displayCandidates = listOfNotNull(fromSubscribe, fromAvatar, fromOwnerView, fromWatchPlayer)
        val bestDisplayName = displayCandidates.firstOrNull { candidate ->
            !candidate.startsWith("@") && !candidate.equals(handle, ignoreCase = true)
        } ?: displayCandidates.firstOrNull()

        val resolvedHandle = when {
            !handle.isNullOrBlank() -> if (handle.startsWith("@")) handle else "@$handle"
            bestDisplayName != null && bestDisplayName.startsWith("@") -> bestDisplayName
            else -> null
        }

        val resolvedName = bestDisplayName?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }

        return WatchChannelIdentity(
            channelHandle = resolvedHandle,
            channelName = resolvedName
        )
    }

    /**
     * Extracts channel name directly from the Subscribe button accessibility content description
     * (e.g. "Subscribe to PW - JEE Wallah." or "Subscribed to Sony Pictures Brasil").
     */
    fun extractChannelFromSubscribeButton(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return findFirstMatching(rootNode, SUBSCRIBE_BUTTON_IDS) { node ->
            if (isInsideExcludedContainer(node)) return@findFirstMatching null
            val desc = node.contentDescription?.toString()?.trim()
            val candidate = YouTubeDetectionRules.extractChannelFromSubscribeButton(desc)
            if (candidate != null) {
                cleanChannelName(candidate)
            } else null
        }
    }

    /**
     * Extracts the channel handle (@name) specifically from the watch metadata area
     * below the video title (as shown in modern YouTube watch layouts, e.g. @SonypicturesBrasil).
     */
    fun extractChannelHandle(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null

        // 1. Direct view ID query on dedicated handle and byline containers in the watch header
        val handleFromView = findFirstMatching(rootNode, HANDLE_VIEW_IDS) { node ->
            val resId = node.viewIdResourceName ?: ""
            if (!EXCLUDED_TITLE_VIEW_KEYWORDS.any { resId.contains(it, ignoreCase = true) } &&
                !isInsideExcludedContainer(node)
            ) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""

                val handleMatch = YouTubeDetectionRules.extractHandle(text)
                    ?: YouTubeDetectionRules.extractHandle(desc)
                if (handleMatch != null) {
                    cleanChannelName(handleMatch)
                } else null
            } else null
        }
        if (handleFromView != null) return handleFromView

        // 2. Scan watch player / metadata subtree for @handle text nodes
        return extractHandleFromWatchSubtree(rootNode)
    }

    private fun extractChannelDisplayNameFromOwnerViews(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        return findFirstMatching(rootNode, CHANNEL_VIEW_IDS) { node ->
            val resId = node.viewIdResourceName ?: ""
            if (!EXCLUDED_TITLE_VIEW_KEYWORDS.any { resId.contains(it, ignoreCase = true) } &&
                !isInsideExcludedContainer(node)
            ) {
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val candidate = when {
                    !text.isNullOrBlank() -> text
                    !desc.isNullOrBlank() -> desc
                    else -> {
                        var childCandidate: String? = null
                        for (i in 0 until node.childCount) {
                            val child = try { node.getChild(i) } catch (_: Exception) { null }
                            val ct = child?.text?.toString()?.trim()
                            val cd = child?.contentDescription?.toString()?.trim()
                            if (!ct.isNullOrBlank()) {
                                childCandidate = ct
                                break
                            } else if (!cd.isNullOrBlank()) {
                                childCandidate = cd
                                break
                            }
                        }
                        childCandidate
                    }
                }
                if (!candidate.isNullOrBlank()) {
                    val valid = YouTubeDetectionRules.extractAndValidateChannelName(candidate)
                    if (valid != null) {
                        cleanChannelName(valid)
                    } else null
                } else null
            } else null
        }
    }

    /**
     * Extracts the verified channel name or @handle of the video currently on the watch page.
     */
    fun extractChannelName(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        val identity = extractChannelIdentity(rootNode)
        return identity.channelName ?: identity.channelHandle?.removePrefix("@")
    }

    /**
     * Extracts channel name from Channel avatar / header button
     * (e.g. "Go to channel Khan Academy" or "Sony Pictures Brasil avatar").
     */
    fun extractChannelFromAvatar(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        val avatarIds = listOf(
            "$YT:id/channel_avatar",
            "$YT:id/channel_header",
            "$YT:id/owner_avatar",
            "$YT:id/avatar",
            "$YT:id/channel_thumbnail"
        )
        return findFirstMatching(rootNode, avatarIds) { node ->
            if (isInsideExcludedContainer(node)) return@findFirstMatching null
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            if (desc.isBlank()) return@findFirstMatching null
            val handleMatch = HANDLE_REGEX.find(desc)
            if (handleMatch != null) {
                return@findFirstMatching cleanChannelName(handleMatch.value)
            }
            val prefixes = listOf("Go to channel ", "Channel avatar for ", "Channel: ", "Visit ", "Open channel page for ")
            for (p in prefixes) {
                if (desc.startsWith(p, ignoreCase = true) && desc.length > p.length) {
                    val name = desc.substring(p.length).trim()
                    val cleaned = cleanChannelName(name)
                    if (cleaned != null) return@findFirstMatching cleaned
                }
            }
            val suffixes = listOf(", Go to channel", " avatar", " channel avatar", ", open channel")
            for (s in suffixes) {
                if (desc.endsWith(s, ignoreCase = true) && desc.length > s.length) {
                    val name = desc.substring(0, desc.length - s.length).trim()
                    val cleaned = cleanChannelName(name)
                    if (cleaned != null) return@findFirstMatching cleaned
                }
            }
            if (desc.contains(" • ") || desc.contains(" · ")) {
                val candidate = desc.substringBefore(" • ").substringBefore(" · ").trim()
                val cleaned = cleanChannelName(candidate)
                if (cleaned != null) return@findFirstMatching cleaned
            }
            val direct = cleanChannelName(desc)
            if (direct != null && !direct.contains("avatar", ignoreCase = true) && !direct.contains("thumbnail", ignoreCase = true)) {
                return@findFirstMatching direct
            }
            null
        }
    }

    /**
     * Direct scan of accessibility node tree looking specifically for @handle text tokens
     * in the upper watch header (e.g. "@SonypicturesBrasil 1.7K").
     */
    fun extractHandleFromAnyNode(rootNode: AccessibilityNodeInfo?, maxNodes: Int = 200): String? {
        if (rootNode == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            val resId = node.viewIdResourceName ?: ""
            val isTitleNode = EXCLUDED_TITLE_VIEW_KEYWORDS.any { resId.contains(it, ignoreCase = true) }
            val isExcluded = isTitleNode || isInsideExcludedContainer(node)
            if (!isExcluded) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val match = HANDLE_REGEX.find(text) ?: HANDLE_REGEX.find(desc)
                if (match != null) {
                    val candidate = match.value.trim()
                    if (candidate.length in 3..60) {
                        val cleaned = cleanChannelName(candidate)
                        if (cleaned != null) return cleaned
                    }
                }
            } else if (YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS.any { resId.contains(it, ignoreCase = true) }) {
                // Do not traverse into recommendation / comment containers
                continue
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        return null
    }

    /**
     * Scans specifically the watch player metadata subtree for @handle nodes.
     */
    private fun extractHandleFromWatchSubtree(rootNode: AccessibilityNodeInfo): String? {
        if (isFullScreenVideoPlayback(rootNode)) {
            val strictHeader = findFirstMatching(rootNode, listOf("$YT:id/watch_header_layout")) { it }
            return if (strictHeader != null) scanSubtreeForHandle(strictHeader) else null
        }
        val watchContainer = findFirstMatching(rootNode, WATCH_CONTAINER_VIEW_IDS) { it }
        if (watchContainer != null) {
            val fromContainer = scanSubtreeForHandle(watchContainer)
            if (fromContainer != null) return fromContainer
        }
        return scanSubtreeForHandle(rootNode)
    }

    private fun scanSubtreeForHandle(searchRoot: AccessibilityNodeInfo): String? {
        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(searchRoot to 0)
        try {
            while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
                val (node, depth) = queue.removeFirst()
                visited++

                val viewId = node.viewIdResourceName ?: ""
                val isRecommendationContainer = YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS.any {
                    viewId.contains(it, ignoreCase = true)
                }
                if (isRecommendationContainer) {
                    // Never descend into related videos or comments subtrees
                    continue
                }

                val isTitleNode = EXCLUDED_TITLE_VIEW_KEYWORDS.any { viewId.contains(it, ignoreCase = true) }
                if (!isTitleNode && !isInsideExcludedContainer(node)) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""

                    val handleMatch = HANDLE_REGEX.find(text) ?: HANDLE_REGEX.find(desc)
                    if (handleMatch != null) {
                        val cleaned = cleanChannelName(handleMatch.value)
                        if (cleaned != null) return cleaned
                    }
                }

                if (depth >= MAX_SCAN_DEPTH) continue
                for (i in 0 until node.childCount) {
                    if (visited >= MAX_SCAN_NODES) break
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) {
                        queue.add(child to depth + 1)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Extracts channel identity ONLY from the watch player subtree by locating
     * "Subscribe to [Channel]", "@handle", or "Go to channel [Name]" nodes.
     */
    private fun extractChannelFromWatchPlayer(rootNode: AccessibilityNodeInfo): String? {
        if (isFullScreenVideoPlayback(rootNode)) {
            val strictHeader = findFirstMatching(rootNode, listOf("$YT:id/watch_header_layout")) { it }
            return if (strictHeader != null) scanSubtreeForWatchChannel(strictHeader) else null
        }
        val watchContainer = findFirstMatching(rootNode, WATCH_CONTAINER_VIEW_IDS) { it }
        if (watchContainer != null) {
            val fromContainer = scanSubtreeForWatchChannel(watchContainer)
            if (fromContainer != null) return fromContainer
        }
        return scanSubtreeForWatchChannel(rootNode)
    }

    private fun scanSubtreeForWatchChannel(searchRoot: AccessibilityNodeInfo): String? {
        val candidates = mutableListOf<String>()
        var found = false

        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(searchRoot to 0)
        try {
            while (queue.isNotEmpty() && !found && visited < MAX_SCAN_NODES) {
                val (node, depth) = queue.removeFirst()
                visited++

                val viewId = node.viewIdResourceName ?: ""
                val isExcluded = YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS.any {
                    viewId.contains(it, ignoreCase = true)
                }
                if (isExcluded) {
                    // Do not descend into recommendation lists, comments, or ad overlays
                    continue
                }

                if (!isInsideExcludedContainer(node)) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""

                    // Subscribe button near the video — strongest signal for the video's own channel name
                    val subscribePrefix = "Subscribe to "
                    if (desc.startsWith(subscribePrefix, ignoreCase = true) && desc.length > subscribePrefix.length) {
                        candidates.add(desc.substring(subscribePrefix.length).trim())
                        found = true
                        break
                    }
                    val subscribedPrefix = "Subscribed to "
                    if (desc.startsWith(subscribedPrefix, ignoreCase = true) && desc.length > subscribedPrefix.length) {
                        candidates.add(desc.substring(subscribedPrefix.length).trim())
                        found = true
                        break
                    }

                    // Channel header / byline "Go to channel [Name]"
                    val goPrefix = "Go to channel "
                    if (desc.startsWith(goPrefix, ignoreCase = true) && desc.length > goPrefix.length) {
                        candidates.add(desc.substring(goPrefix.length).trim())
                        found = true
                        break
                    }

                    // Check for @handle in watch header subtree
                    val isTitleNode = EXCLUDED_TITLE_VIEW_KEYWORDS.any { viewId.contains(it, ignoreCase = true) }
                    if (!isTitleNode) {
                        val handleMatch = HANDLE_REGEX.find(text) ?: HANDLE_REGEX.find(desc)
                        if (handleMatch != null) {
                            candidates.add(handleMatch.value)
                            found = true
                            break
                        }
                    }
                }

                if (depth >= MAX_SCAN_DEPTH) continue
                for (i in 0 until node.childCount) {
                    if (visited >= MAX_SCAN_NODES) break
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) {
                        queue.add(child to depth + 1)
                    }
                }
            }
        } catch (_: Exception) {}

        for (candidate in candidates) {
            val cleaned = cleanChannelName(candidate)
            if (cleaned != null) return cleaned
        }
        return null
    }

    /**
     * Cleans and validates a raw channel string. Rejects view counts, video
     * counts, durations, timestamps and UI labels without rejecting legitimate
     * channel names that merely contain words like "view" or "reviews".
     */
    fun cleanChannelName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var name = raw.trim()

        // If raw contains an @handle, extract ONLY the handle token
        val handleMatch = HANDLE_REGEX.find(name)
        if (handleMatch != null) {
            name = handleMatch.value.removePrefix("@").trim()
        } else {
            if (name.startsWith("@")) name = name.removePrefix("@").trim()
            name = name.substringBefore(" • ").substringBefore(" · ").substringBefore(" | ").trim()
            val prefixes = listOf("Subscribe to ", "Subscribed to ", "Unsubscribe from ", "By ", "by ")
            for (p in prefixes) {
                if (name.startsWith(p, ignoreCase = true)) {
                    name = name.substring(p.length).trim()
                    break
                }
            }
        }
        if (name.endsWith(".")) name = name.dropLast(1).trim()

        if (name.length < 2 || name.length > 70) return null
        if (!name.any { it.isLetter() }) return null
        if (isViewCount(name)) return null
        if (isDuration(name)) return null
        if (name.equals("Subscriptions", ignoreCase = true)) return null
        if (name.equals("Subscribe", ignoreCase = true) || name.equals("Subscribed", ignoreCase = true)) return null
        if (name.equals("Home", ignoreCase = true) || name.equals("Shorts", ignoreCase = true)) return null
        if (name.equals("You", ignoreCase = true) || name.equals("Notifications", ignoreCase = true)) return null
        return name
    }

    /**
     * Matches full view-count patterns such as "1.2M views", "15K views" or
     * "1,234,567 views" but never a plain channel name containing "view".
     */
    private fun isViewCount(value: String): Boolean {
        return VIEW_COUNT_REGEX.matches(value)
    }

    private val VIEW_COUNT_REGEX = Regex("^[0-9][0-9.,]*\\s*([KMkm])?\\s*(views?|subscribers?|videos?)$", setOf(RegexOption.IGNORE_CASE))

    private fun isDuration(value: String): Boolean {
        return DURATION_REGEX.matches(value)
    }

    private val DURATION_REGEX = Regex("^[0-9]+(:[0-5]?[0-9])+$")

    /**
     * Extracts all visible text from the node tree as a classification fallback.
     */
    fun extractAllText(rootNode: AccessibilityNodeInfo?): String {
        if (rootNode == null) return ""
        val builder = StringBuilder()
        scanTree(rootNode) { node ->
            node.text?.let { builder.append(it).append(" ") }
            node.contentDescription?.let { builder.append(it).append(" ") }
        }
        return builder.toString().trim()
    }

    /**
     * Bounded breadth-first traversal of the node tree with explicit depth tracking.
     */
    private fun scanTree(root: AccessibilityNodeInfo, visitor: (AccessibilityNodeInfo) -> Unit) {
        try {
            var visited = 0
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            queue.add(root to 0)
            while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
                val (node, depth) = queue.removeFirst()
                visited++
                visitor(node)
                if (depth >= MAX_SCAN_DEPTH) continue
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) queue.add(child to depth + 1)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * If YouTube minimized the paused unapproved video into the bottom miniplayer bar after
     * stepping back once, clicks the miniplayer's Close button so it does not linger.
     */
    fun dismissMiniplayerIfPresent(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        try {
            var visited = 0
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            while (queue.isNotEmpty() && visited < 100) {
                val node = queue.removeFirst()
                visited++
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val isMiniplayerClose =
                    viewId.contains("miniplayer_close") ||
                    viewId.contains("floaty_close") ||
                    viewId.contains("modern_miniplayer_close") ||
                    desc.equals("Close miniplayer", ignoreCase = true) ||
                    desc.equals("Close player", ignoreCase = true)
                if (isMiniplayerClose) {
                    var target: AccessibilityNodeInfo? = node
                    var depth = 0
                    while (target != null && depth < 4) {
                        if (target.isClickable) {
                            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                return true
                            }
                        }
                        target = target.parent
                        depth++
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
}
