package com.example.core.detector

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Interface for detecting short-form content (YouTube Shorts, Instagram Reels, Facebook Reels)
 * in active applications via Android Accessibility Service node hierarchy inspection.
 *
 * Inspired by and integrated from Shorts-Blocker.
 */
interface ShortFormContentDetector {

    /**
     * Inspects the accessibility event and node hierarchy to detect whether
     * the user is actively viewing or scrolling short-form video content.
     *
     * @param event The accessibility event received by the service
     * @param rootNode The root accessibility node of the active window
     * @return true if short-form content is actively being consumed
     */
    fun isShortFormContent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean

    /**
     * Returns the package name this detector handles (e.g. "com.google.android.youtube")
     */
    fun getPackageName(): String

    /**
     * User-friendly name of the platform (e.g. "YouTube Shorts", "Instagram Reels")
     */
    fun getDisplayName(): String
}
