package com.example.cloud.auth

/**
 * High-level authentication state for FocusShield.
 *
 * - [LocalOnly] – no cloud account; the app runs seamlessly offline using Room.
 * - [ConfirmEmail] – legacy stub retained for backward compatibility.
 * - [Authenticated] – active Google account session with Firebase Auth.
 */
sealed interface AuthState {
    data object LocalOnly : AuthState
    data object ConfirmEmail : AuthState
    data class Authenticated(
        val userId: String,
        val email: String,
        val displayName: String = "",
        val photoUrl: String? = null
    ) : AuthState

    /** True when an authenticated session exists. */
    val isAuthenticated: Boolean
        get() = this is Authenticated
}
