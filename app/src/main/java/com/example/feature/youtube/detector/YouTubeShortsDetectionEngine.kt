package com.example.feature.youtube.detector

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Multi-level confidence for YouTube Shorts detection.
 */
enum class ShortsConfidence {
    NONE,
    LOW_CONFIDENCE,
    PROBABLE_SHORT,
    CONFIRMED_SHORT
}

/**
 * Result of YouTube Shorts detection.
 */
data class ShortsDetectionResult(
    val isConsumingShort: Boolean = false,
    val confidence: ShortsConfidence = ShortsConfidence.NONE,
    val matchedSignals: List<String> = emptyList(),
    val isShortsShelfOnly: Boolean = false
) {
    companion object {
        val NO_SHORTS = ShortsDetectionResult(
            isConsumingShort = false,
            confidence = ShortsConfidence.NONE,
            matchedSignals = emptyList()
        )

        val SHELF_ONLY = ShortsDetectionResult(
            isConsumingShort = false,
            confidence = ShortsConfidence.NONE,
            matchedSignals = listOf("shorts_shelf_present_not_active"),
            isShortsShelfOnly = true
        )
    }
}

/**
 * ENGINE 1: Dedicated YouTube Shorts Detection Engine.
 *
 * Sole Responsibility:
 * "Is the user currently consuming a YouTube Short?"
 *
 * It does NOT determine:
 * - Whether a channel is approved or educational
 * - Whether YouTube Home should be blocked
 * - Whether normal videos are permitted
 *
 * Prevents False-Positives:
 * - Explicitly differentiates between a Shorts shelf (on Home, Search, or Channel) and actual Shorts playback.
 * - Requires multi-signal evidence before confirming Shorts consumption.
 */
object YouTubeShortsDetectionEngine {

    /**
     * Evaluates accessibility node hierarchy and event to determine whether active Shorts playback is occurring.
     */
    fun inspect(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?
    ): ShortsDetectionResult {
        val matchedSignals = mutableListOf<String>()
        var score = 0
        var foundShelfOnly = false

        // 1. FAST EVENT INSPECTION
        if (event != null) {
            val eventText = event.text?.joinToString(" ") ?: ""
            val eventDesc = event.contentDescription?.toString() ?: ""
            val fullEvent = "$eventText $eventDesc"

            // Look for distinct action descriptions unique to Shorts player
            for (action in YouTubeDetectionRules.ACTIVE_SHORTS_ACTION_DESCRIPTIONS) {
                if (fullEvent.contains(action, ignoreCase = true)) {
                    matchedSignals.add("event_action:$action")
                    score += 3
                }
            }

            // Check if bottom tab changed to Shorts tab
            for (tabId in YouTubeDetectionRules.SHORTS_TAB_PIVOT_IDS) {
                if (event.className?.toString()?.contains("Pivot", ignoreCase = true) == true ||
                    fullEvent.contains("Shorts, selected", ignoreCase = true) ||
                    fullEvent.contains("Shorts tab", ignoreCase = true)
                ) {
                    matchedSignals.add("event_shorts_tab_selected")
                    score += 2
                }
            }
        }

        if (rootNode == null) {
            return resolveResult(score, matchedSignals, foundShelfOnly)
        }

        // 2. ACCESSIBILITY TREE INSPECTION
        var hasActiveReelPlayer = false
        var hasShortsActionButtons = false
        var hasSelectedShortsTab = false
        var hasShortsShelf = false
        var hasNormalWatchPlayer = false

        // Fast direct view ID check (Zenlock pattern)
        val reelIds = listOf(
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_player_view",
            "com.google.android.youtube:id/reel_player_page_view",
            "com.google.android.youtube:id/reel_player_fragment"
        )
        for (id in reelIds) {
            try {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    hasActiveReelPlayer = true
                    matchedSignals.add("fast_view_id:$id")
                    score += 4
                    break
                }
            } catch (_: Exception) {}
        }

        scanNodes(rootNode, maxDepth = 6, maxNodes = 45) { node ->
            val viewId = node.viewIdResourceName ?: ""
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val className = node.className?.toString() ?: ""

            // Signal A: Active reel/shorts player containers
            for (keyword in YouTubeDetectionRules.ACTIVE_SHORTS_PLAYER_VIEW_IDS) {
                if (viewId.contains(keyword, ignoreCase = true)) {
                    hasActiveReelPlayer = true
                    matchedSignals.add("view_id:$keyword")
                    score += 3
                    break
                }
            }

            // Signal B: Shorts-specific action descriptions (Like/Dislike this Short, Sound, Remix)
            for (action in YouTubeDetectionRules.ACTIVE_SHORTS_ACTION_DESCRIPTIONS) {
                if (desc.contains(action, ignoreCase = true) || text.contains(action, ignoreCase = true)) {
                    hasShortsActionButtons = true
                    matchedSignals.add("action_desc:$action")
                    score += 3
                    break
                }
            }

            // Signal C: Selected Shorts Tab in bottom navigation bar
            for (tabId in YouTubeDetectionRules.SHORTS_TAB_PIVOT_IDS) {
                if (viewId.contains(tabId, ignoreCase = true) && (node.isSelected || desc.contains("selected", ignoreCase = true))) {
                    hasSelectedShortsTab = true
                    matchedSignals.add("tab_selected:$tabId")
                    score += 2
                    break
                }
            }

            // Signal D: Shorts Shelf indicator on Home / Search / Channel
            for (shelfId in YouTubeDetectionRules.SHORTS_SHELF_VIEW_IDS) {
                if (viewId.contains(shelfId, ignoreCase = true)) {
                    hasShortsShelf = true
                    break
                }
            }

            // Check for normal watch player (counter-signal to active Shorts)
            for (watchId in YouTubeDetectionRules.ACTIVE_WATCH_PLAYER_VIEW_IDS) {
                if (viewId.contains(watchId, ignoreCase = true)) {
                    hasNormalWatchPlayer = true
                    break
                }
            }

            false
        }

        // 3. FALSE-POSITIVE FILTERING:
        // If we found a Shorts Shelf, but NO active reel player, and NO active shorts action buttons:
        // This is a shelf on Home/Search/Channel. User is browsing, NOT consuming Shorts.
        if (hasShortsShelf && !hasActiveReelPlayer && !hasShortsActionButtons) {
            foundShelfOnly = true
            // Suppress low-confidence signals caused by shelf headers
            score = 0
            matchedSignals.clear()
            matchedSignals.add("shorts_shelf_detected_browsing_allowed")
        }

        // If normal watch player is active, reduce shorts confidence unless strong active reel player exists
        if (hasNormalWatchPlayer && !hasActiveReelPlayer && !hasShortsActionButtons) {
            score = 0
            matchedSignals.clear()
        }

        return resolveResult(score, matchedSignals, foundShelfOnly)
    }

    private fun resolveResult(
        score: Int,
        matchedSignals: List<String>,
        isShelfOnly: Boolean
    ): ShortsDetectionResult {
        if (isShelfOnly) {
            return ShortsDetectionResult.SHELF_ONLY
        }

        return when {
            score >= 3 -> ShortsDetectionResult(
                isConsumingShort = true,
                confidence = ShortsConfidence.CONFIRMED_SHORT,
                matchedSignals = matchedSignals
            )
            score in 1..2 -> ShortsDetectionResult(
                isConsumingShort = true,
                confidence = ShortsConfidence.PROBABLE_SHORT,
                matchedSignals = matchedSignals
            )
            else -> ShortsDetectionResult.NO_SHORTS
        }
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
            }
        }
    }
}
