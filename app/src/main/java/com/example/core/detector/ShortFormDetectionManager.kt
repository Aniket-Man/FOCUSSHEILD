package com.example.core.detector

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages detection and cooldown enforcement for short-form content (YouTube Shorts, Instagram Reels, Facebook Reels).
 *
 * Implements the architecture from Shorts-Blocker with FocusShield integration.
 */
class ShortFormDetectionManager {

    private val tag = "ShortFormDetector"
    private val actionCooldownMillis = 1200L
    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()

    private val detectors: Map<String, ShortFormContentDetector> = mapOf(
        "com.google.android.youtube" to YouTubeShortsDetector(),
        "com.google.android.youtube.tv" to YouTubeShortsDetector(),
        "com.instagram.android" to InstagramReelsDetector(),
        "com.instagram.lite" to InstagramReelsDetector(),
        "com.facebook.katana" to FacebookReelsDetector(),
        "com.facebook.lite" to FacebookReelsDetector()
    )

    /**
     * Checks if the given package has a registered short-form content detector.
     */
    fun hasDetectorFor(packageName: String): Boolean {
        return detectors.containsKey(packageName)
    }

    /**
     * Inspects active window and event to determine if short-form content is currently active.
     */
    fun detectShortFormContent(
        packageName: String,
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        val detector = detectors[packageName] ?: return false
        return detector.isShortFormContent(event, rootNode)
    }

    /**
     * Checks if an action (e.g. BACK press or Overlay launch) is allowed under cooldown rules.
     * Prevents rapid duplicate triggers when scrolling.
     */
    fun shouldTriggerAction(packageName: String): Boolean {
        val now = SystemClock.uptimeMillis()
        val lastTime = lastActionTimestamps[packageName] ?: 0L
        val elapsed = now - lastTime

        if (elapsed < actionCooldownMillis) {
            Log.v(tag, "[$packageName] Action skipped due to cooldown (${elapsed}ms < ${actionCooldownMillis}ms)")
            return false
        }

        lastActionTimestamps[packageName] = now
        Log.d(tag, "[$packageName] Action triggered (Cooldown reset)")
        return true
    }

    /**
     * Resets action timestamps.
     */
    fun resetCooldown(packageName: String) {
        lastActionTimestamps.remove(packageName)
    }

    companion object {
        val instance: ShortFormDetectionManager by lazy { ShortFormDetectionManager() }
    }
}
