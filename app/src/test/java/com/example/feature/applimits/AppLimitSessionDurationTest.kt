package com.example.feature.applimits

import com.example.feature.applimits.engine.ActiveAppUsageSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the "the time you picked is the time you get" contract the blocker relies on:
 *
 *  - a temporary session expires only when the selected minutes have actually been
 *    spent inside the limited app (the ticker only credits foreground time), and
 *  - it can still end early when the daily allowance runs out first — which is why
 *    [com.example.feature.applimits.engine.AppLimitManager.startTemporarySession]
 *    clamps the selection to the remaining daily minutes, while an emergency pass
 *    ignores the daily allowance entirely.
 */
class AppLimitSessionDurationTest {

    private fun session(
        selectedMinutes: Int,
        elapsedMillis: Long,
        initialDailyUsedMillis: Long = 0L,
        dailyLimitMinutes: Int = 60,
        isEmergency: Boolean = false
    ) = ActiveAppUsageSession(
        packageName = "com.limited.app",
        appName = "Limited App",
        selectedDurationMillis = selectedMinutes * 60_000L,
        elapsedMillis = elapsedMillis,
        isEmergency = isEmergency,
        dailyLimitMinutes = dailyLimitMinutes,
        initialDailyUsedMillis = initialDailyUsedMillis
    )

    @Test
    fun `a five minute allowance runs out only after five minutes of use`() {
        val almost = session(selectedMinutes = 5, elapsedMillis = 4 * 60_000L + 59_000L)
        assertFalse("4m59s of use must not end a 5 minute choice", almost.isSessionExpired)

        val exactly = session(selectedMinutes = 5, elapsedMillis = 5 * 60_000L)
        assertTrue("5m00s of use ends a 5 minute choice", exactly.isSessionExpired)
    }

    @Test
    fun `remaining session time counts down from the selection`() {
        val session = session(selectedMinutes = 10, elapsedMillis = 2 * 60_000L + 30_000L)
        assertEquals(7 * 60_000L + 30_000L, session.remainingSessionMillis)

        val spent = session(selectedMinutes = 10, elapsedMillis = 12 * 60_000L)
        assertEquals("a spent session never reads negative", 0L, spent.remainingSessionMillis)
    }

    @Test
    fun `daily allowance exhaustion ends a temporary session before the selection`() {
        // 2-minute selection, but only 30 seconds of the daily 10 minutes are left.
        val session = session(
            selectedMinutes = 2,
            elapsedMillis = 5 * 60_000L,
            initialDailyUsedMillis = 9 * 60_000L + 30_000L,
            dailyLimitMinutes = 10
        )
        assertTrue(
            "daily allowance runs out first, so the session counts as exhausted",
            session.isDailyLimitExhausted
        )
    }

    @Test
    fun `an emergency pass is not consumed by daily allowance bookkeeping`() {
        // An emergency session's minutes must stay outside the daily total, which is the
        // other half of why the ticker only lets *temporary* selections end on
        // isDailyLimitExhausted (`!isEmergency && isDailyLimitExhausted`).
        val emergency = session(
            selectedMinutes = 5,
            elapsedMillis = 5 * 60_000L,
            initialDailyUsedMillis = 3 * 60_000L,
            dailyLimitMinutes = 10,
            isEmergency = true
        )
        assertEquals("emergency minutes stay outside the daily total", 3 * 60_000L, emergency.currentTotalDailyUsedMillis)

        val temporary = session(
            selectedMinutes = 5,
            elapsedMillis = 5 * 60_000L,
            initialDailyUsedMillis = 3 * 60_000L,
            dailyLimitMinutes = 10
        )
        assertEquals("a temporary selection counts toward the daily total", 8 * 60_000L, temporary.currentTotalDailyUsedMillis)
    }
}
