package com.example.feature.applimits.engine

import com.example.feature.session.domain.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the coexistence rule between Focus Sessions and timed app limits (issue #73).
 *
 * A running session owns the decision only for the packages it itself blocks; everything
 * else keeps its app limit during the session. The bug these lock down: both app-limit
 * suppression points used to treat any running session as owning every package, so while a
 * session ran no app limit fired at all — the blocker never came for the limited app.
 */
class FocusSessionAppLimitPolicyTest {

    @Test
    fun `a running session owns the packages it blocks`() {
        assertTrue(
            FocusSessionAppLimitPolicy.sessionOwnsPackage(
                sessionState = SessionState.RUNNING,
                blockedPackages = setOf("com.google.android.youtube", "com.instagram.android"),
                packageName = "com.google.android.youtube"
            )
        )
    }

    @Test
    fun `a running session does not own packages it leaves unblocked`() {
        // The issue-#73 regression: a session that doesn't block this app must not suppress
        // its app limit — the blocker has to appear for the limited app mid-session.
        assertFalse(
            FocusSessionAppLimitPolicy.sessionOwnsPackage(
                sessionState = SessionState.RUNNING,
                blockedPackages = setOf("com.instagram.android"),
                packageName = "com.google.android.youtube"
            )
        )
    }

    @Test
    fun `a session with an empty block list owns nothing`() {
        assertFalse(
            FocusSessionAppLimitPolicy.sessionOwnsPackage(
                sessionState = SessionState.RUNNING,
                blockedPackages = emptySet(),
                packageName = "com.google.android.youtube"
            )
        )
    }

    @Test
    fun `a paused session never owns the package`() {
        assertFalse(
            FocusSessionAppLimitPolicy.sessionOwnsPackage(
                sessionState = SessionState.PAUSED,
                blockedPackages = setOf("com.google.android.youtube"),
                packageName = "com.google.android.youtube"
            )
        )
    }

    @Test
    fun `an idle or completed session never owns the package`() {
        assertFalse(
            FocusSessionAppLimitPolicy.sessionOwnsPackage(
                sessionState = SessionState.IDLE,
                blockedPackages = setOf("com.google.android.youtube"),
                packageName = "com.google.android.youtube"
            )
        )
        assertFalse(
            FocusSessionAppLimitPolicy.sessionOwnsPackage(
                sessionState = SessionState.COMPLETED,
                blockedPackages = setOf("com.google.android.youtube"),
                packageName = "com.google.android.youtube"
            )
        )
    }

    @Test
    fun `an active break owns the foreground package so app limits do not interrupt breaks`() {
        assertTrue(
            FocusSessionAppLimitPolicy.sessionOwnsPackage(
                sessionState = SessionState.RUNNING,
                blockedPackages = emptySet(),
                packageName = "com.instagram.android",
                isBreakActive = true
            )
        )
    }

    @Test
    fun `a running session with YouTube Study Mode enabled owns YouTube`() {
        assertTrue(
            FocusSessionAppLimitPolicy.sessionOwnsPackage(
                sessionState = SessionState.RUNNING,
                blockedPackages = emptySet(),
                packageName = "com.google.android.youtube",
                isYouTubeStudyModeEnabled = true
            )
        )
    }
}
