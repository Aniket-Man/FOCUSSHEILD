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
 * 2. Fullscreen Video Detection: explicit reel/clip markers inside a fullscreen player,
 *    gated by a negative filter so feed chrome, DM media, stories, and marketplace
 *    surfaces are never ejected (issue #17 — false positives destroy user trust).
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

        // Fast check on class names or event descriptions (precise, keep as-is)
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
        var reelsMarkerSeen = false
        var hasDirectChrome = false
        var hasStoryChrome = false
        var hasCommerceChrome = false

        // Scan first 30 nodes for fast response
        while (queue.isNotEmpty() && nodesScanned < 30) {
            val node = queue.removeFirst()
            nodesScanned++

            val id = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString() ?: ""

            // 1. Explicit Reels / Clips Tab (must be selected — a merely present tab
            //    in the bottom bar is not a Reels surface)
            if ("clips_tab" in id && (node.isSelected || desc.contains("selected", ignoreCase = true))) {
                Log.i(tag, "Instagram Reels detected: clips_tab selected ($id)")
                return true
            }

            // 2. Direct Reels Viewer containers — strong, unambiguous signals.
            //    "reel_preview" is deliberately NOT here: those cards are embedded in
            //    the main feed grid and would eject users while simply scrolling.
            if ("clips_viewer" in id ||
                "reels_viewer_root" in id ||
                "clips_video_container" in id ||
                "reel_viewer_container" in id ||
                "clips_swipe_refresh_container" in id
            ) {
                foundClipsViewer = true
            }

            // Weak reel/clip marker (any screen that talks about reels/clips)
            if (("reel" in id || "clip" in id || "reel" in desc.lowercase() || "clip" in desc.lowercase())) {
                reelsMarkerSeen = true
            }

            // Negative filters: surfaces where fullscreen video is legitimate
            if ("feed_tab" in id || "main_feed" in id) {
                feedTabCount++
            }
            if ("direct" in id || "inbox" in id || "message" in id) {
                hasDirectChrome = true
            }
            if ("story_viewer" in id || "story_camera" in id || "story_" in id) {
                hasStoryChrome = true
            }
            if ("marketplace" in id || "product" in id || "commerce" in id || "shopping" in id) {
                hasCommerceChrome = true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }

        if (foundClipsViewer) {
            Log.i(tag, "Instagram Reels detected via Clips/Reels viewer container")
            return true
        }

        // Negative filter first: feed chrome, DM media viewer, stories viewer, and
        // marketplace/product videos are never treated as Reels, even in fullscreen.
        if (feedTabCount > 0 || hasDirectChrome || hasStoryChrome || hasCommerceChrome) {
            Log.d(
                tag,
                "Fullscreen media NOT treated as Reels (negative chrome): " +
                    "feed=$feedTabCount dm=$hasDirectChrome story=$hasStoryChrome commerce=$hasCommerceChrome"
            )
            return false
        }

        // Fullscreen fallback: no feed chrome AND an explicit reel/clip marker in the
        // tree AND a fullscreen-player class name. Requiring all three removes the
        // false positives from lazy-rendered feeds, stories, and DM media that the old
        // "no feed_tab + Video/Player class" check produced (issue #17).
        if (reelsMarkerSeen &&
            (className.contains("Video", ignoreCase = true) || className.contains("Player", ignoreCase = true))
        ) {
            Log.i(tag, "Instagram Reels detected: fullscreen media with reel marker and no feed chrome")
            return true
        }

        return false
    }
}
