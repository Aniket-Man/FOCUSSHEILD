package com.example.core.detector

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Detector for Instagram Reels short-form content.
 *
 * Implements the dual detection strategy from Shorts-Blocker:
 * 1. Reels Tab Detection: Checks if "clips_tab" element exists and is selected.
 * 2. Fullscreen Video Detection: Checks for "clips_viewer", "reel_viewer", or absence of "feed_tab" during video viewing.
 */
class InstagramReelsDetector : ShortFormContentDetector {

    private val tag = "InstagramReelsDetector"

    override fun getPackageName(): String = "com.instagram.android"

    override fun getDisplayName(): String = "Instagram Reels"

    override fun isShortFormContent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        val className = event.className?.toString() ?: ""
        val eventText = event.text?.joinToString(" ") ?: ""
        val eventDesc = event.contentDescription?.toString() ?: ""

        // Fast check on class names or event descriptions
        if (className.contains("ClipsViewerActivity", ignoreCase = true) ||
            className.contains("ReelViewerFragment", ignoreCase = true) ||
            eventDesc.contains("Reels, selected", ignoreCase = true) ||
            eventText.contains("Reels, selected", ignoreCase = true)
        ) {
            Log.d(tag, "Instagram Reels detected from event class/desc: $className / $eventDesc")
            return true
        }

        if (rootNode == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var nodesScanned = 0
        var feedTabCount = 0
        var foundClipsViewer = false

        // Scan first 30 nodes for fast response
        while (queue.isNotEmpty() && nodesScanned < 30) {
            val node = queue.removeFirst()
            nodesScanned++

            val id = node.viewIdResourceName?.lowercase()
            val desc = node.contentDescription?.toString() ?: ""

            if (id != null) {
                // 1. Explicit Reels / Clips Tab
                if ("clips_tab" in id && (node.isSelected || desc.contains("selected", ignoreCase = true))) {
                    Log.i(tag, "Instagram Reels detected: clips_tab selected ($id)")
                    return true
                }

                // 2. Direct Reels Viewer identifiers
                if ("clips_viewer" in id ||
                    "reels_viewer_root" in id ||
                    "clips_video_container" in id ||
                    "reel_viewer_container" in id ||
                    "clips_swipe_refresh_container" in id ||
                    "reel_preview" in id
                ) {
                    foundClipsViewer = true
                }

                // Check for home feed indicator
                if ("feed_tab" in id || "main_feed" in id) {
                    feedTabCount++
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }

        if (foundClipsViewer) {
            Log.i(tag, "Instagram Reels detected via Clips/Reels viewer container")
            return true
        }

        // Shorts-Blocker heuristic: if inside Instagram video player with no bottom feed_tab visible, it's fullscreen Reels
        if (feedTabCount == 0 && (className.contains("Video", ignoreCase = true) || className.contains("Player", ignoreCase = true))) {
            Log.i(tag, "Instagram Reels detected: Fullscreen media with no feed tab")
            return true
        }

        return false
    }
}
