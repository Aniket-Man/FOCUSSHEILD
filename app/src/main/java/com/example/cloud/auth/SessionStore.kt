package com.example.cloud.auth

import android.content.Context

/**
 * Persists the current GoTrue session in a private SharedPreferences file
 * (`focus_shield_account`). Only tokens/identity are stored — never the password — and callers
 * must never log the tokens read from here.
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("focus_shield_account", Context.MODE_PRIVATE)

    fun save(session: AuthSession) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    fun load(): AuthSession? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
            userId = userId,
            email = email
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
