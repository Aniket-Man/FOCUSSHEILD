package com.example.feature.applimits.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the app-limit gate's decision scope. The regression these lock down is issue #71:
 * a Focus Session running with Study Mode on used to exempt YouTube (and browsers) from the
 * gate entirely, so the daily limit was never evaluated during the session.
 */
class AppLimitGatePolicyTest {

    @Test
    fun `a limited user app is evaluated while no blocker is on screen`() {
        assertTrue(
            AppLimitGatePolicy.shouldEvaluate("com.google.android.youtube", isBlockerOnScreen = false)
        )
    }

    @Test
    fun `a study-mode session no longer suppresses the gate for youtube`() {
        assertTrue(
            "issue #71: the app limit must still apply during a YouTube study-mode session",
            AppLimitGatePolicy.shouldEvaluate(
                packageName = "com.google.android.youtube",
                isBlockerOnScreen = false,
                isStudyModeSessionActive = true
            )
        )
    }

    @Test
    fun `a study-mode session no longer suppresses the gate for browsers`() {
        assertTrue(
            "same clash as YouTube, through the browser study-mode exemption",
            AppLimitGatePolicy.shouldEvaluate(
                packageName = "com.android.chrome",
                isBlockerOnScreen = false,
                isStudyModeSessionActive = true
            )
        )
    }

    @Test
    fun `an app whose blocker is already on screen is not re-evaluated`() {
        assertFalse(
            AppLimitGatePolicy.shouldEvaluate(
                "com.google.android.youtube",
                isBlockerOnScreen = true,
                isStudyModeSessionActive = true
            )
        )
    }

    @Test
    fun `shells launchers and our own app are never evaluated`() {
        assertFalse(AppLimitGatePolicy.shouldEvaluate("com.android.launcher3", isBlockerOnScreen = false))
        assertFalse(AppLimitGatePolicy.shouldEvaluate("com.android.systemui", isBlockerOnScreen = false))
        assertFalse(AppLimitGatePolicy.shouldEvaluate("com.example", isBlockerOnScreen = false))
        assertFalse(AppLimitGatePolicy.shouldEvaluate("com.example.core.keyboard", isBlockerOnScreen = false))
    }
}
