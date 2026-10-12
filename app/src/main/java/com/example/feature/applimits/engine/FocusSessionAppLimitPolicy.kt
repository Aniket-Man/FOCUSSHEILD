package com.example.feature.applimits.engine

import com.example.feature.session.domain.SessionState

/**
 * Whether a Focus Session — rather than the timed app-limit engine — is the blocking
 * authority for [packageName] right now (issue #73).
 *
 * A running session only owns the packages it actually blocks: its blocker for those is
 * raised earlier in the accessibility pipeline (`evaluateProtection` → BLOCK →
 * `handleBlockedPackage` + return), so the app-limit gate must then stay out of the way.
 * Every other package keeps its app limit during the session.
 *
 * Before issue #73 the app-limit decision treated *any* running session as owning *every*
 * package, so while a session ran no app limit fired at all and the blocker never appeared
 * for the limited app.
 */
internal object FocusSessionAppLimitPolicy {

    /**
     * @param sessionState the live Focus Session state.
     * @param blockedPackages the packages the running session itself blocks.
     * @param packageName the foreground package being decided.
     */
    fun sessionOwnsPackage(
        sessionState: SessionState,
        blockedPackages: Set<String>,
        packageName: String
    ): Boolean = sessionState == SessionState.RUNNING && blockedPackages.contains(packageName)
}
