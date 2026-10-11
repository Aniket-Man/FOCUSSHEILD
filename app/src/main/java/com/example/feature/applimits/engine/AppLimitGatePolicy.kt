package com.example.feature.applimits.engine

/**
 * Whether the timed app-limit gate should evaluate [packageName] for a pending decision
 * right now.
 *
 * Before issue #71 this decision also exempted YouTube and browsers whenever a Focus Session
 * was running with Study Mode on. That exemption is exactly what made the two features
 * clash: during a study session the YouTube daily limit was never evaluated, so the limit was
 * silently dismissed and the watch time was billed to the study session. The gate is now
 * study-mode-independent — Study Mode decides what YouTube shows (Shorts, unapproved
 * channels — the content engine's job), while the app limit still decides *whether* the app
 * may be used.
 */
internal object AppLimitGatePolicy {

    /**
     * @param packageName the foreground package the gate is evaluating.
     * @param isBlockerOnScreen true when this package's blocker is already on screen, in
     * which case there is nothing to decide and nothing to launch.
     * @param isStudyModeSessionActive true while a Focus Session runs with Study Mode on.
     * Accepted only so call sites can hand over the session context; it deliberately does
     * **not** affect the answer (issue #71).
     */
    fun shouldEvaluate(
        packageName: String,
        isBlockerOnScreen: Boolean,
        isStudyModeSessionActive: Boolean = false
    ): Boolean = AppLimitPackageFilter.shouldEvaluateForLimits(packageName) && !isBlockerOnScreen
}
