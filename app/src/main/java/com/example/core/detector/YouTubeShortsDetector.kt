package com.example.core.detector

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Detector for YouTube Shorts short-form content.
 *
 * Implements high-speed BFS traversal (max 120 nodes) matching the Shorts-Blocker detection algorithm.
 * Searches for distinctive view IDs ("reel_progress_bar", "reel_player_overlay", etc.) and action descriptions.
 */
class YouTubeShortsDetector : ShortFormContentDetector {

    private val tag = "YouTubeShortsDetector"

    override fun getPackageName(): String = "com.google.android.youtube"

    override fun getDisplayName(): String = "YouTube Shorts"

    override fun isShortFormContent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        // Fast Event Check
        val eventText = event.text?.joinToString(" ") ?: ""
        val eventDesc = event.contentDescription?.toString() ?: ""
        val fullEvent = "$eventText $eventDesc"

        if (fullEvent.contains("Shorts, selected", ignoreCase = true) ||
            fullEvent.contains("Dislike this Short", ignoreCase = true) ||
            fullEvent.contains("Like this Short", ignoreCase = true) ||
            fullEvent.contains("Remix this Short", ignoreCase = true) ||
            fullEvent.contains("Sound used in this Short", ignoreCase = true)
        ) {
            Log.d(tag, "Shorts detected from event descriptions: $fullEvent")
            return true
        }

        if (rootNode == null) return false

        // BFS Traversal matching Shorts-Blocker
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var nodeCount = 0

        while (queue.isNotEmpty() && nodeCount < 120) {
            val node = queue.removeFirst()
            nodeCount++

            val id = node.viewIdResourceName?.lowercase()
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""

            // Primary Shorts-Blocker signal: reel_progress_bar or reel_player components
            if (id != null) {
                if ("reel_progress_bar" in id ||
                    "reel_player_overlay" in id ||
                    "reel_watch_fragment" in id ||
                    "reel_player_page_view" in id ||
                    "reel_recycler_layout" in id ||
                    "reel_video_player" in id ||
                    "shorts_player_view" in id ||
                    "reel_comment_button" in id ||
                    "reel_pivot_button" in id
                ) {
                    Log.d(tag, "YouTube Shorts detected via node viewId: $id")
                    return true
                }

                // Check for Shorts bottom tab selection
                if (("pivot_shorts" in id || "tab_shorts" in id) && (node.isSelected || desc.contains("selected", ignoreCase = true))) {
                    Log.d(tag, "YouTube Shorts detected via selected tab: $id")
                    return true
                }
            }

            // Shorts action descriptions
            if (desc.contains("Dislike this Short", ignoreCase = true) ||
                desc.contains("Remix this Short", ignoreCase = true) ||
                desc.contains("Sound used in this Short", ignoreCase = true) ||
                desc.contains("Create a Short with this sound", ignoreCase = true)
            ) {
                Log.d(tag, "YouTube Shorts detected via action description: $desc")
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }

        return false
    }
}
