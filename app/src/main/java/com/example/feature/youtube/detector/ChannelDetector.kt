package com.example.feature.youtube.detector

import android.view.accessibility.AccessibilityNodeInfo
import com.example.feature.youtube.domain.DetectionConfidence

/**
 * Legacy compatibility wrapper for ChannelDetector.
 * Delegates directly to the dedicated YouTubeContentInspectionEngine.
 */
object ChannelDetector {

    data class ChannelDetection(
        val channelName: String? = null,
        val channelHandle: String? = null,
        val videoTitle: String? = null,
        val confidence: DetectionConfidence = DetectionConfidence.LOW
    )

    /**
     * Inspects nodes to locate channel headers, subscribe buttons, bylines, or video title metadata.
     */
    fun extractChannelInfo(rootNode: AccessibilityNodeInfo?): ChannelDetection {
        val result = YouTubeContentInspectionEngine.inspect(rootNode, null)
        return ChannelDetection(
            channelName = result.channelInfo.channelName,
            channelHandle = result.channelInfo.channelHandle,
            videoTitle = result.activeVideo?.title,
            confidence = result.confidence
        )
    }
}
