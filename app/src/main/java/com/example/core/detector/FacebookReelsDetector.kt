package com.example.core.detector

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Detector for Facebook Reels short-form content (com.facebook.katana / com.facebook.lite).
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

        while (queue.isNotEmpty() && nodesScanned < 30) {
            val node = queue.removeFirst()
            nodesScanned++

            val id = node.viewIdResourceName?.lowercase()
            val desc = node.contentDescription?.toString() ?: ""

            if (id != null) {
                if ("fb_reels_tab" in id ||
                    "reels_viewer" in id ||
                    "reels_tray" in id ||
                    "reel_video_player" in id ||
                    "short_form_video" in id
                ) {
                    Log.i(tag, "Facebook Reels detected via node: $id")
                    return true
                }
            }

            if (desc.contains("Remix Reel", ignoreCase = true) ||
                desc.contains("Audio from this Reel", ignoreCase = true)
            ) {
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }

        return false
    }
}
