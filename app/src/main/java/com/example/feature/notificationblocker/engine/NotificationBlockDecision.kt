package com.example.feature.notificationblocker.engine

import com.example.feature.notificationblocker.domain.NotificationBlockMode

/**
 * The pure "should this notification be silenced?" rule set, extracted from
 * [NotificationBlockerEngine] so the precedence between the safety whitelist, the permanent
 * 24/7 list, the master toggle and the per-mode rules is stated in one place and unit-tested.
 *
 * The precedence fixed here is the bug reported in issue #30: the master/session gate used to be
 * evaluated *before* the always-silent list, so switching "Block notifications" off silently
 * un-silenced every app the user had marked as permanently silent — contradicting the feature's
 * own contract ("silenced 24/7 regardless of focus session") with no indication in the UI.
 */
object NotificationBlockDecision {

    /**
     * Everything the decision depends on, resolved by the caller.
     *
     * @param isProtected the package is on the safety whitelist (dialer, emergency, system,
     *   FocusShield itself). Always wins.
     * @param isAlwaysSilenced the user put this package on the "Always-Silent (24/7)" list.
     * @param masterEnabled the global "Block notifications" switch.
     * @param sessionRequestsBlocking the running session was configured with `blockNotifications`.
     * @param sessionActive a session is RUNNING or PAUSED.
     * @param mode the configured [NotificationBlockMode].
     * @param isExplicitlySelected the package is on the user's per-mode selected list.
     * @param isBlockedInActiveSession the package is blocked by the running session / app limit.
     */
    data class Inputs(
        val isProtected: Boolean,
        val isAlwaysSilenced: Boolean,
        val masterEnabled: Boolean,
        val sessionRequestsBlocking: Boolean,
        val sessionActive: Boolean,
        val mode: NotificationBlockMode,
        val isExplicitlySelected: Boolean,
        val isBlockedInActiveSession: Boolean
    )

    fun shouldBlock(inputs: Inputs): Boolean {
        // 1. Safety first: a protected package is never silenced, whatever any list says.
        if (inputs.isProtected) return false

        // 2. The 24/7 list is its own guarantee. It is deliberately evaluated *before* the master
        //    toggle: "always silent" means always, and a user who turns the session blocker off has
        //    not asked for their permanently-silenced apps to start buzzing again.
        if (inputs.isAlwaysSilenced) return true

        // 3. Session blocking needs either the master switch or a session that asked for it.
        if (!inputs.masterEnabled && !inputs.sessionRequestsBlocking) return false

        // 4. Mode-specific rules.
        return when (inputs.mode) {
            NotificationBlockMode.SESSION_ONLY,
            NotificationBlockMode.SMART_HYBRID ->
                inputs.sessionActive &&
                    (inputs.isExplicitlySelected || inputs.isBlockedInActiveSession)

            // In always-silent mode the selected list is silenced around the clock.
            NotificationBlockMode.ALWAYS_SILENT -> inputs.isExplicitlySelected
        }
    }
}
