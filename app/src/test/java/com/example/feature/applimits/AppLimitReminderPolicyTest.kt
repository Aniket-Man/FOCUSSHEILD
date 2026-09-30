package com.example.feature.applimits

import com.example.feature.applimits.engine.AppLimitReminderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the "1 minute left" reminder firing while the app is not in use
 * (issue #54).
 *
 * The session ticker keeps running while the user is away — only the elapsed clock freezes — so
 * a session parked with under a minute left satisfied the old "remaining in 1..60" rule on every
 * 500 ms tick, and the two-minute throttle turned that into a notification every two minutes,
 * forever, about an app nobody was using.
 */
class AppLimitReminderPolicyTest {

    private val sessionId = "session-1"

    private fun shouldRemind(
        remainingMillis: Long = 45_000L,
        isPaused: Boolean = false,
        isLimitedAppInForeground: Boolean = true,
        showRemindersSetting: Boolean = true,
        sessionId: String = this.sessionId,
        lastRemindedSessionId: String? = null
    ) = AppLimitReminderPolicy.shouldRemind(
        remainingMillis = remainingMillis,
        isPaused = isPaused,
        isLimitedAppInForeground = isLimitedAppInForeground,
        showRemindersSetting = showRemindersSetting,
        sessionId = sessionId,
        lastRemindedSessionId = lastRemindedSessionId
    )

    @Test
    fun `reminds while the user is actually in the app with under a minute left`() {
        assertTrue(shouldRemind(remainingMillis = 45_000L))
        assertTrue("The whole last minute counts", shouldRemind(remainingMillis = 60_000L))
        assertTrue("One second left still counts", shouldRemind(remainingMillis = 1_000L))
    }

    @Test
    fun `never reminds while the session is parked outside the app`() {
        assertFalse(shouldRemind(isPaused = true))
        assertFalse(shouldRemind(isPaused = true, isLimitedAppInForeground = false))
    }

    @Test
    fun `never reminds while another app is in the foreground`() {
        assertFalse(shouldRemind(isLimitedAppInForeground = false))
    }

    @Test
    fun `a parked session cannot re-notify on every tick`() {
        // 500 ms ticks over a parked session: the frozen clock stays inside the window forever.
        repeat(300) {
            assertFalse(shouldRemind(remainingMillis = 30_000L, isPaused = true))
        }
    }

    @Test
    fun `reminds at most once per session`() {
        assertTrue(shouldRemind(lastRemindedSessionId = null))
        assertFalse(shouldRemind(lastRemindedSessionId = sessionId))
    }

    @Test
    fun `a new session gets its own reminder`() {
        assertTrue(shouldRemind(sessionId = "session-2", lastRemindedSessionId = "session-1"))
    }

    @Test
    fun `respects the per-app show reminders setting`() {
        assertFalse(shouldRemind(showRemindersSetting = false))
    }

    @Test
    fun `stays quiet outside the last minute`() {
        assertFalse("More than a minute left", shouldRemind(remainingMillis = 61_000L))
        assertFalse("Expired sessions are the blocker's job", shouldRemind(remainingMillis = 0L))
        assertFalse(shouldRemind(remainingMillis = 999L))
    }

    @Test
    fun `a session without an id is never reminded for`() {
        assertFalse(shouldRemind(sessionId = ""))
    }
}
