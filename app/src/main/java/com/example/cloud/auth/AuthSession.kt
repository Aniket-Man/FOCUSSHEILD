package com.example.cloud.auth

/**
 * A GoTrue session. Tokens are held in memory and persisted by [SessionStore]; passwords are
 * never stored, and tokens are never logged.
 */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch millis at which [accessToken] expires and must be refreshed. */
    val expiresAtMillis: Long,
    val userId: String,
    val email: String
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= expiresAtMillis
}
