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
        "$YT:id/watch_player"
    )

    private val TITLE_VIEW_IDS = listOf(
        "$YT:id/watch_title_text",
        "$YT:id/watch_metadata_title",
        "$YT:id/video_title",
        "$YT:id/compact_media_item_headline",
        "$YT:id/title"
    )

    private val CHANNEL_VIEW_IDS = listOf(
        "$YT:id/channel_name",
        "$YT:id/owner_text",
        "$YT:id/owner_name",
        "$YT:id/channel_title",
        "$YT:id/subtitle"
    )

    private val HOME_FEED_VIEW_IDS = listOf(
        "$YT:id/home_feed"
    )

    private val MAX_SCAN_DEPTH = 12
    private val MAX_SCAN_NODES = 200

    /**
     * Detects whether a Shorts player is actively on screen.
     * A Shorts shelf on the Home feed does not expose the reel player
     * container IDs, so it is never mistaken for active playback.
     */
    fun detectShorts(rootNode: AccessibilityNodeInfo?, event: AccessibilityEvent?): Boolean {
        if (rootNode == null) return false
        return try {
            for (viewId in SHORTS_PLAYER_VIEW_IDS) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes != null && nodes.isNotEmpty()) {
                    nodes.forEach { try { it.recycle() } catch (_: Exception) {} }
                    return true
                }
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
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes != null && nodes.isNotEmpty()) {
                    nodes.forEach { try { it.recycle() } catch (_: Exception) {} }
                    return true
                }
            }
            val titleNodes = rootNode.findAccessibilityNodeInfosByViewId("$YT:id/watch_title_text")
            val hasTitle = titleNodes != null && titleNodes.isNotEmpty()
            titleNodes?.forEach { try { it.recycle() } catch (_: Exception) {} }
            hasTitle
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detects the YouTube Home feed so Study Mode can show its nudge popup.
     */
    fun isHomeFeed(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        return try {
            for (viewId in HOME_FEED_VIEW_IDS) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes != null && nodes.isNotEmpty()) {
                    nodes.forEach { try { it.recycle() } catch (_: Exception) {} }
                    return true
                }
            }
            isTabSelected(rootNode, "pivot_home") || isTabSelected(rootNode, "tab_home")
        } catch (_: Exception) {
            false
        }
    }

    private fun isTabSelected(rootNode: AccessibilityNodeInfo, viewId: String): Boolean {
        return try {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("$YT:id/$viewId")
            if (nodes != null) {
                for (node in nodes) {
                    val selected = node.isSelected
                    try { node.recycle() } catch (_: Exception) {}
                    if (selected) return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts the title of the video currently on the watch page.
     */
    fun extractVideoTitle(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        for (viewId in TITLE_VIEW_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes != null) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        try { node.recycle() } catch (_: Exception) {}
                        if (!text.isNullOrBlank() && text.length >= 3) return text
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Extracts the channel name of the video currently on the watch page.
     * First tries dedicated channel view IDs, then falls back to scanning
     * only the watch player subtree for subscribe buttons.
     */
    fun extractChannelName(rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null
        for (viewId in CHANNEL_VIEW_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes != null) {
                    for (node in nodes) {
                        val text = node.text?.toString()?.trim()
                        try { node.recycle() } catch (_: Exception) {}
                        val cleaned = cleanChannelName(text)
                        if (cleaned != null) return cleaned
                    }
                }
            } catch (_: Exception) {}
        }
        return extractChannelFromWatchPlayer(rootNode)
    }

    /**
     * Extracts channel identity ONLY from the watch player subtree by locating
     * "Subscribe to [Channel]" or "@handle" nodes that sit near the video
     * controls — never from recommendations, comments, or description sheets.
     */
    private fun extractChannelFromWatchPlayer(rootNode: AccessibilityNodeInfo): String? {
        // Find the watch panel container so we only scan the active video area.
        var watchContainer: AccessibilityNodeInfo? = null
        for (id in WATCH_PAGE_VIEW_IDS) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    watchContainer = nodes.first()
                    for (i in 1 until nodes.size) {
                        try { nodes[i].recycle() } catch (_: Exception) {}
                    }
                    break
                }
            } catch (_: Exception) {}
        }

        val searchRoot = watchContainer ?: rootNode
        val candidates = mutableListOf<String>()
        val allocatedNodes = mutableListOf<AccessibilityNodeInfo>()
        if (watchContainer != null) allocatedNodes.add(watchContainer)
        var found = false

        // Manual BFS limited to the watch player subtree
        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(searchRoot to 0)
        try {
            while (queue.isNotEmpty() && !found && visited < MAX_SCAN_NODES) {
                val (node, depth) = queue.removeFirst()
                visited++

                val viewId = node.viewIdResourceName ?: ""

                // Skip nodes from recommendations, comments, description sheet, live chat
                val isExcluded = YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS.any {
                    viewId.contains(it, ignoreCase = true)
                }
                if (!isExcluded) {
                    val desc = node.contentDescription?.toString() ?: ""

                    // Subscribe button near the video — strongest signal for the video's own channel
                    val subscribePrefix = "Subscribe to "
                    if (desc.startsWith(subscribePrefix, ignoreCase = true) && desc.length > subscribePrefix.length) {
                        candidates.add(desc.substring(subscribePrefix.length).trim())
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
                }

                if (depth >= MAX_SCAN_DEPTH) continue
                for (i in 0 until node.childCount) {
                    if (visited >= MAX_SCAN_NODES) break
                    val child = try { node.getChild(i) } catch (_: Exception) { null }
                    if (child != null) {
                        allocatedNodes.add(child)
                        queue.add(child to depth + 1)
                    }
                }
            }
        } finally {
            for (allocated in allocatedNodes) {
                try { allocated.recycle() } catch (_: Exception) {}
            }
        }

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
        if (name.startsWith("@")) name = name.removePrefix("@").trim()
        name = name.substringBefore(" • ").substringBefore(" · ").substringBefore(" | ").trim()
        if (name.startsWith("By ", ignoreCase = true)) name = name.removePrefix("By ").trim()

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
}
