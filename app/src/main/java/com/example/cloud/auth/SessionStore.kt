package com.example.cloud.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the current GoTrue session in an EncryptedSharedPreferences file
 * (`focus_shield_account_secure`) backed by an Android Keystore master key (AES-256-GCM).
 * Only tokens/identity are stored — never the password — and callers must never log the
 * tokens read from here.
 *
 * Legacy plaintext tokens in `focus_shield_account` are migrated into the encrypted file
 * (commit-before-wipe) and the plaintext file is cleared. On devices where the keystore is
 * broken/unsupported the store falls back to the app-private plaintext file so accounts
 * keep working (issue #20).
 */
class SessionStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences = openPrefs()

    private fun openPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val secure = EncryptedSharedPreferences.create(
                appContext,
                SECURE_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            migrateFromLegacy(secure)
            secure
        } catch (t: Throwable) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable; falling back to private prefs: ${t.message}")
            appContext.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        }
    }

    /** Move any plaintext tokens into the encrypted file, then wipe the plaintext file. */
    private fun migrateFromLegacy(target: SharedPreferences) {
        try {
            val legacy = appContext.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
            val legacyAccess = legacy.getString(KEY_ACCESS, null)
            if (!target.contains(KEY_ACCESS) && legacyAccess != null) {
                val copied = target.edit()
                    .putString(KEY_ACCESS, legacyAccess)
                    .putString(KEY_REFRESH, legacy.getString(KEY_REFRESH, null))
                    .putLong(KEY_EXPIRES_AT, legacy.getLong(KEY_EXPIRES_AT, 0L))
                    .putString(KEY_USER_ID, legacy.getString(KEY_USER_ID, null))
                    .putString(KEY_EMAIL, legacy.getString(KEY_EMAIL, null))
                    .commit()
                if (!copied) {
                    Log.w(TAG, "Session migration to encrypted store failed; plaintext copy kept.")
                    return
                }
            }
            if (legacyAccess != null || legacy.all.isNotEmpty()) {
                legacy.edit().clear().commit()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Session legacy migration failed: ${t.message}")
        }
    }

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
        const val TAG = "SessionStore"
        const val SECURE_FILE = "focus_shield_account_secure"
        const val LEGACY_FILE = "focus_shield_account"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
