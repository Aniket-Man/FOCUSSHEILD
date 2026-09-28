package com.example.feature.notificationblocker

import com.example.feature.notificationblocker.domain.NotificationBlockMode
import com.example.feature.notificationblocker.engine.NotificationBlockDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #30 regression suite: the precedence of the notification-blocking rules.
 *
 * The headline case is [alwaysSilentSurvivesTheMasterToggleBeingOff]: the master/session gate used
 * to run before the 24/7 list, so turning "Block notifications" off silently voided a guarantee the
 * feature makes in its own description.
 */
class NotificationBlockDecisionTest {

    private fun inputs(
        isProtected: Boolean = false,
        isAlwaysSilenced: Boolean = false,
        masterEnabled: Boolean = true,
        sessionRequestsBlocking: Boolean = false,
        sessionActive: Boolean = false,
        mode: NotificationBlockMode = NotificationBlockMode.SESSION_ONLY,
        isExplicitlySelected: Boolean = false,
        isBlockedInActiveSession: Boolean = false
    ) = NotificationBlockDecision.Inputs(
        isProtected = isProtected,
        isAlwaysSilenced = isAlwaysSilenced,
        masterEnabled = masterEnabled,
        sessionRequestsBlocking = sessionRequestsBlocking,
        sessionActive = sessionActive,
        mode = mode,
        isExplicitlySelected = isExplicitlySelected,
        isBlockedInActiveSession = isBlockedInActiveSession
    )

    private fun shouldBlock(i: NotificationBlockDecision.Inputs) =
        NotificationBlockDecision.shouldBlock(i)

    @Test
    fun `a protected package is never silenced`() {
        assertFalse(
            shouldBlock(
                inputs(
                    isProtected = true,
                    isAlwaysSilenced = true,
                    masterEnabled = true,
                    sessionActive = true,
                    isExplicitlySelected = true
                )
            )
        )
    }

    @Test
    fun alwaysSilentSurvivesTheMasterToggleBeingOff() {
        assertTrue(
            "an app on the 24/7 list must stay silent when the master toggle is off",
            shouldBlock(inputs(isAlwaysSilenced = true, masterEnabled = false))
        )
    }

    @Test
    fun `always silent does not need an active session`() {
        assertTrue(
            shouldBlock(
                inputs(
                    isAlwaysSilenced = true,
                    masterEnabled = true,
                    sessionActive = false,
                    mode = NotificationBlockMode.SESSION_ONLY
                )
            )
        )
    }

    @Test
    fun `nothing else is silenced while both the master toggle and the session gate are off`() {
        assertFalse(
            shouldBlock(
                inputs(
                    masterEnabled = false,
                    sessionRequestsBlocking = false,
                    sessionActive = true,
                    isExplicitlySelected = true
                )
            )
        )
    }

    @Test
    fun `a session that asks for blocking enables it without the master toggle`() {
        assertTrue(
            shouldBlock(
                inputs(
                    masterEnabled = false,
                    sessionRequestsBlocking = true,
                    sessionActive = true,
                    isExplicitlySelected = true
                )
            )
        )
    }

    @Test
    fun `session-only mode allows notifications outside a session`() {
        assertFalse(
            shouldBlock(
                inputs(
                    mode = NotificationBlockMode.SESSION_ONLY,
                    sessionActive = false,
                    isExplicitlySelected = true
                )
            )
        )
        assertTrue(
            shouldBlock(
                inputs(
                    mode = NotificationBlockMode.SESSION_ONLY,
                    sessionActive = true,
                    isExplicitlySelected = true
                )
            )
        )
    }

    @Test
    fun `smart hybrid silences whatever the running session blocks`() {
        assertTrue(
            shouldBlock(
                inputs(
                    mode = NotificationBlockMode.SMART_HYBRID,
                    sessionActive = true,
                    isExplicitlySelected = false,
                    isBlockedInActiveSession = true
                )
            )
        )
        assertFalse(
            shouldBlock(
                inputs(
                    mode = NotificationBlockMode.SMART_HYBRID,
                    sessionActive = false,
                    isBlockedInActiveSession = true
                )
            )
        )
    }

    @Test
    fun `always silent mode silences the selected list around the clock`() {
        assertTrue(
            shouldBlock(
                inputs(
                    mode = NotificationBlockMode.ALWAYS_SILENT,
                    sessionActive = false,
                    isExplicitlySelected = true
                )
            )
        )
        assertFalse(
            shouldBlock(
                inputs(
                    mode = NotificationBlockMode.ALWAYS_SILENT,
                    sessionActive = false,
                    isExplicitlySelected = false
                )
            )
        )
    }
}
