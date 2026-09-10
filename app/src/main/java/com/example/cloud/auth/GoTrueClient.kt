package com.example.cloud.auth

import com.example.cloud.http.SupabaseApiException
import com.example.cloud.http.SupabaseHttp
import org.json.JSONObject

/**
 * Minimal GoTrue (Supabase Auth) REST client over [SupabaseHttp] using only the Android
 * platform JSON parser. Every method is `suspend` and buffered off the main thread.
 *
 * Typed failures surface as [SupabaseApiException] with a machine-readable [SupabaseApiException.code]
 * (`invalid_credentials`, `email_not_confirmed`, `email_exists`, `user_already_exists`, ...) which
 * the UI maps to friendly messages.
 */
class GoTrueClient(private val http: SupabaseHttp) {

    /**
     * Register a new account with email + password.
     *
     * @return a session when the project auto-confirms sign-ups (or already returns one);
     *         `null` when the project requires email confirmation and no session was issued.
     * @throws SupabaseApiException e.g. `user_already_exists` / `email_exists` (400).
     */
    suspend fun signUpWithPassword(email: String, password: String): AuthSession? {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
        val result = http.call("POST", "/auth/v1/signup", jsonBody = body)
        val json = JSONObject(result.body)
        val accessToken = json.optString("access_token")
        return if (accessToken.isBlank()) null else parseSession(json)
    }

    /** Sign in with email + password (returns an auto-refreshing session). */
    suspend fun signInWithPassword(email: String, password: String): AuthSession {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .toString()
        val result = http.call("POST", "/auth/v1/token", query = "grant_type=password", jsonBody = body)
        return parseSession(JSONObject(result.body))
    }

    /** Exchange a refresh token for a fresh session. */
    suspend fun refreshSession(refreshToken: String): AuthSession {
        val body = JSONObject().put("refresh_token", refreshToken).toString()
        val result = http.call("POST", "/auth/v1/token", query = "grant_type=refresh_token", jsonBody = body)
        return parseSession(JSONObject(result.body))
    }

    /** Fetch the current user (used to validate a session). */
    suspend fun getUser(accessToken: String): Pair<String, String> {
        val result = http.call("GET", "/auth/v1/user", accessToken = accessToken)
        val user = JSONObject(result.body)
        return user.optString("id") to user.optString("email")
    }

    /** Revoke the session on the server (best-effort; 204 expected). */
    suspend fun signOut(accessToken: String) {
        http.call("POST", "/auth/v1/logout", accessToken = accessToken)
    }

    /** Ask Supabase to email a password-reset link for [email]. */
    suspend fun recoverPassword(email: String) {
        val body = JSONObject().put("email", email.trim()).toString()
        http.call("POST", "/auth/v1/recover", jsonBody = body)
    }

    private fun parseSession(json: JSONObject): AuthSession {
        val user = json.optJSONObject("user")
        val expiresIn = json.optLong("expires_in", 0L)
        return AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtMillis = System.currentTimeMillis() + (expiresIn * 1000L).coerceAtLeast(0L),
            userId = user?.optString("id").orEmpty(),
            email = user?.optString("email").orEmpty()
        )
    }
}
