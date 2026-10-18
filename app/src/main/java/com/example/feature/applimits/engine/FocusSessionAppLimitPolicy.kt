package com.example.feature.applimits.engine

import com.example.core.detector.BrowserUrlDetector
import com.example.feature.session.domain.SessionState
import com.example.feature.youtube.detector.YouTubeDetectionRules

/**
 * Whether a Focus Session — rather than the timed app-limit engine — is the blocking
 * authority for [packageName] right now (issue #73).
 *
 * A running session owns:
 * 1. Any foreground package while a Focus Session break (Manual or Pomodoro) is active.
 * 2. YouTube when YouTube Study Mode is enabled on the running session.
 * 3. Browser packages when Browser Study Mode is enabled on the running session.
 * 4. Packages explicitly included in [blockedPackages] while the session is RUNNING.
 */
internal object FocusSessionAppLimitPolicy {

    /**
     * @param sessionState the live Focus Session state.
     * @param blockedPackages the packages the running session itself blocks.
     * @param packageName the foreground package being decided.
     * @param isBreakActive true when a manual or Pomodoro break is currently active.
     * @param isYouTubeStudyModeEnabled true when YouTube Study Mode governs YouTube in this session.
     * @param isBrowserStudyModeEnabled true when Browser Study Mode governs browsers in this session.
     */
    fun sessionOwnsPackage(
        sessionState: SessionState,
        blockedPackages: Set<String>,
        packageName: String,
        isBreakActive: Boolean = false,
        isYouTubeStudyModeEnabled: Boolean = false,
        isBrowserStudyModeEnabled: Boolean = false
    ): Boolean {
        if (isBreakActive && (sessionState == SessionState.RUNNING || sessionState == SessionState.PAUSED)) {
            return true
        }
        if (sessionState != SessionState.RUNNING) return false
        if (isYouTubeStudyModeEnabled && YouTubeDetectionRules.isYouTubePackage(packageName)) {
            return true
        }
        if (isBrowserStudyModeEnabled && BrowserUrlDetector.isBrowserPackage(packageName)) {
            return true
        }
        return blockedPackages.contains(packageName)
    }
}
