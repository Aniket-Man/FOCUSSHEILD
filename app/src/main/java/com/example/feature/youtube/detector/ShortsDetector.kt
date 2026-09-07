package com.example.feature.youtube.detector

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.feature.youtube.domain.DetectionConfidence

/**
 * Legacy compatibility wrapper for ShortsDetector.
 * Delegates directly to the dedicated YouTubeShortsDetectionEngine.
 */
object ShortsDetector {

    /**
     * Inspects the accessibility node hierarchy to detect whether the user is watching a Short.
     */
    fun isShortsPresent(
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent?
    ): Pair<Boolean, DetectionConfidence> {
        val result = YouTubeShortsDetectionEngine.inspect(rootNode, event)
        val confidence = when (result.confidence) {
            ShortsConfidence.CONFIRMED_SHORT -> DetectionConfidence.HIGH
            ShortsConfidence.PROBABLE_SHORT -> DetectionConfidence.MEDIUM
            ShortsConfidence.LOW_CONFIDENCE -> DetectionConfidence.LOW
            ShortsConfidence.NONE -> DetectionConfidence.LOW
        }
        return Pair(result.isConsumingShort, confidence)
    }
}
