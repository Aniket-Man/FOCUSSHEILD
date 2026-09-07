package com.example.feature.blocker.protection

import android.content.Context
import android.util.Log
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Block Protection: guards an active focus session against mid-session disable
 * attempts (opening FocusShield's App Info to force stop, uninstall, or clear
 * data) even when the user has NOT enabled the 24/7 uninstall-protection
 * preference.
 *
 * The accessibility service consults [isSessionProtectionActive] before every
 * tamper check, so tamper detection is unconditional while a session is
 * RUNNING or PAUSED.
 */
object BlockProtectionManager {

    private const val TAG = "BlockProtection"

    @Volatile
    var isSessionProtectionActive: Boolean = false
        private set

    private var scope: CoroutineScope? = null

    fun initialize() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch {
                try {
                    FocusSessionManager.instance.activeSession.collectLatest { session ->
                        val active = session != null && (
                            session.state == SessionState.RUNNING ||
                                session.state == SessionState.PAUSED
                            )
                        if (active != isSessionProtectionActive) {
                            isSessionProtectionActive = active
                            Log.d(TAG, "Session protection active: $active")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to observe session state", e)
                }
            }
        }
    }

    fun shutdown() {
        scope?.cancel()
        scope = null
        isSessionProtectionActive = false
    }
}
