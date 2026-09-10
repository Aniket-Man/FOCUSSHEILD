package com.example.cloud.auth

/**
 * High-level authentication state for this device, independent of sync progress.
 *
 * - [LocalOnly]  – no account session; the app is fully usable offline from Room.
 * - [ConfirmEmail] – a sign-up was accepted but the project has email confirmation enabled;
 *   the user must click the emailed link before the first sign-in succeeds.
 * - [Authenticated] – a session is active for [email] / [userId].
 */
sealed interface AuthState {
    data object LocalOnly : AuthState
    data object ConfirmEmail : AuthState
    data class Authenticated(val userId: String, val email: String) : AuthState

    /** True when a session exists; convenience for repository/sync gate checks. */
    val isAuthenticated: Boolean
        get() = this is Authenticated
}
