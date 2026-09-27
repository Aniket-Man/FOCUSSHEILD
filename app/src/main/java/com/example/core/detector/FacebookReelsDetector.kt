package com.example.core.detector

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Detector for Facebook Reels short-form content (com.facebook.katana / com.facebook.lite).
 *
 * Presence alone is never enough: the Reels tab lives in the bottom bar of every
 * feed screen, and reels trays are embedded in feed and Marketplace surfaces. A node
 * only counts when it is selected/strong, and a negative commerce filter keeps
 * Marketplace/product videos from ever being classified as Reels (issue #17).
 */
class FacebookReelsDetector : ShortFormContentDetector {

    private val tag = "FacebookReelsDetector"

    override fun getPackageName(): String = "com.facebook.katana"

    override fun getDisplayName(): String = "Facebook Reels"

    override fun isShortFormContent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        val eventText = event.text?.joinToString(" ") ?: ""
        val eventDesc = event.contentDescription?.toString() ?: ""
        val className = event.className?.toString() ?: ""

        if (className.contains("Reel", ignoreCase = true) ||
            eventDesc.contains("Reels, selected", ignoreCase = true) ||
            eventText.contains("Reels, selected", ignoreCase = true)
        ) {
            Log.d(tag, "Facebook Reels detected from event: $className / $eventDesc")
            return true
        }

        if (rootNode == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var nodesScanned = 0
        var strongViewerSeen = false
        var reelsMarkerSeen = false
        var hasCommerceChrome = false

        while (queue.isNotEmpty() && nodesScanned < 30) {
            val node = queue.removeFirst()
            nodesScanned++

            val id = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val descLower = desc.lowercase()

            // Strong viewer containers: only these exist on a real fullscreen Reels surface
            if ("reels_viewer" in id || "reel_video_player" in id) {
                strongViewerSeen = true
            }

            // The Reels tab in the bottom navigation is present on non-Reels screens too;
            // it only counts when it is the selected tab.
            if ("fb_reels_tab" in id && (node.isSelected || desc.contains("selected", ignoreCase = true))) {
                strongViewerSeen = true
            }

            // Weak markers (tray/short-form modules embedded in feed) — evidence, not verdict
            if ("reels_tray" in id || "short_form_video" in id || "reel" in id) {
                reelsMarkerSeen = true
            }

            if (desc.contains("Remix Reel", ignoreCase = true) ||
                desc.contains("Audio from this Reel", ignoreCase = true)
            ) {
                strongViewerSeen = true
            }

            // Negative filter: Marketplace/product videos are never Reels
            if ("marketplace" in id || "product" in id || "commerce" in id || "catalog" in id ||
                "marketplace" in descLower
            ) {
                hasCommerceChrome = true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }

        if (strongViewerSeen) {
            Log.i(tag, "Facebook Reels detected via strong viewer signal")
            return true
        }

        if (hasCommerceChrome) {
            Log.d(tag, "Fullscreen video NOT treated as Reels: commerce chrome present")
            return false
        }

        // Weak markers alone no longer trigger the block (that was the false-positive
        // path for feed videos and embedded reels trays).
        if (reelsMarkerSeen) {
            Log.d(tag, "Reel marker seen but weak (no viewer); not blocking")
        }

        return false
    }
}
