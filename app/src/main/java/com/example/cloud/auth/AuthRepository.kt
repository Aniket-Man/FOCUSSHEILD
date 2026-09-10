package com.example.cloud.auth

import android.content.Context
import com.example.cloud.SupabaseConfig
import com.example.cloud.http.SupabaseHttp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central auth coordinator. Exposes [authState] as the single source of truth the UI and the
 * sync engine observe, and owns the persisted [SessionStore].
 *
 * Account *boundary* decisions (which account's data currently owns this install, whether the
 * first-ever migration already ran) are tracked separately by the cloud install state in Phase 3;
 * this repository only manages the session itself.
 */
class AuthRepository(context: Context) {

    private val store = SessionStore(context)
    private val client = GoTrueClient(SupabaseHttp(SupabaseConfig))
    private val opMutex = Mutex()

    private val _authState = MutableStateFlow<AuthState>(AuthState.LocalOnly)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isConfigured: Boolean get() = SupabaseConfig.isConfigured

    /** Current persisted session without touching the network, or null. */
    fun currentSession(): AuthSession? = store.load()

    /** Convenience for gate checks: the signed-in user's id, or null. */
    fun currentUserId(): String? = currentSession()?.userId

    /**
     * Called once at app start. Restores a persisted session by refreshing it against the server;
     * if the refresh fails the session is discarded and the app returns to local-only mode.
     * Never throws.
     */
    suspend fun restoreOnStart() {
        opMutex.withLock {
            if (!SupabaseConfig.isConfigured) {
                store.clear()
                _authState.value = AuthState.LocalOnly
                return
            }
            val stored = store.load()
            if (stored == null) {
                _authState.value = AuthState.LocalOnly
                return
            }
            try {
                val fresh = client.refreshSession(stored.refreshToken)
                store.save(fresh)
                _authState.value = AuthState.Authenticated(fresh.userId, fresh.email)
            } catch (_: Exception) {
                store.clear()
                _authState.value = AuthState.LocalOnly
            }
        }
    }

    /** Sign in with email + password. Throws typed errors for the UI to map. */
    suspend fun signIn(email: String, password: String): AuthState = opMutex.withLock {
        val session = client.signInWithPassword(email, password)
        applySession(session)
        _authState.value
    }

    /**
     * Create an account. Returns the resulting state: [AuthState.Authenticated] when a session was
     * issued, or [AuthState.ConfirmEmail] when the project requires email confirmation.
     */
    suspend fun signUp(email: String, password: String): AuthState = opMutex.withLock {
        val session = client.signUpWithPassword(email, password)
        if (session == null) {
            _authState.value = AuthState.ConfirmEmail
        } else {
            applySession(session)
        }
        _authState.value
    }

    /** Clear the local + server session; Room and local profile data are untouched. */
    suspend fun signOut() {
        opMutex.withLock {
            val session = store.load()
            if (session != null) {
                try {
                    client.signOut(session.accessToken)
                } catch (_: Exception) {
                    // Best-effort server revoke; always clear locally.
                }
            }
            store.clear()
            _authState.value = AuthState.LocalOnly
        }
    }

    /** Ask Supabase to email a password-reset link. Throws on failure. */
    suspend fun forgotPassword(email: String) {
        client.recoverPassword(email)
    }

    /**
     * A usable, unexpired session — refreshing when necessary. Used by the sync engine before a
     * cycle and by profile-image upload/download. Returns null when there is no session.
     */
    suspend fun requireFreshSession(): AuthSession? = opMutex.withLock {
        val stored = store.load() ?: return null
        if (!stored.isExpired()) return stored
        try {
            val fresh = client.refreshSession(stored.refreshToken)
            store.save(fresh)
            _authState.value = AuthState.Authenticated(fresh.userId, fresh.email)
            fresh
        } catch (_: Exception) {
            store.clear()
            _authState.value = AuthState.LocalOnly
            null
        }
    }

    private fun applySession(session: AuthSession) {
        store.save(session)
        _authState.value = AuthState.Authenticated(session.userId, session.email)
    }
}
