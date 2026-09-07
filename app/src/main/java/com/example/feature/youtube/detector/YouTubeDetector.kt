package com.example.feature.youtube.detector

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.feature.youtube.domain.YouTubeDetectionResult
import com.example.feature.youtube.engine.YouTubeStateEngine

/**
 * Facade entry point for YouTube detection.
 * Delegates to the central YouTubeStateEngine which orchestrates:
 * - Engine 1: YouTubeShortsDetectionEngine (Is user consuming a Short?)
 * - Engine 2: YouTubeContentInspectionEngine (What video/channel is being consumed?)
 */
object YouTubeDetector {

    /**
     * Inspects the accessibility node tree to identify the current YouTube UI state.
     */
    fun detect(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?,
        packageName: String?,
        approvedIds: Set<String> = emptySet(),
        approvedNames: Set<String> = emptySet()
    ): YouTubeDetectionResult {
        return YouTubeStateEngine.evaluate(
            rootNode = rootNode,
            event = event,
            packageName = packageName,
            approvedIds = approvedIds,
            approvedNames = approvedNames
        )
    }
}
