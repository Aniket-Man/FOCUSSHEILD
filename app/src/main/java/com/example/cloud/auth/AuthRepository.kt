package com.example.cloud.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Coordinates Firebase Authentication for FocusShield.
 * Provides real-time observing of Google Sign-In state via Jetpack CredentialManager.
 */
open class AuthRepository(
    private val context: Context,
    authProvider: () -> FirebaseAuth? = { runCatching { FirebaseAuth.getInstance() }.getOrNull() }
) {

    private val auth: FirebaseAuth? = authProvider()

    private val _authState = MutableStateFlow<AuthState>(mapUserToState(auth?.currentUser))
    open val authState: StateFlow<AuthState> = _authState.asStateFlow()

    open val isConfigured: Boolean get() = auth != null

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            _authState.value = mapUserToState(firebaseAuth.currentUser)
        }
    }

    private fun mapUserToState(user: FirebaseUser?): AuthState {
        return if (user != null) {
            AuthState.Authenticated(
                userId = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                photoUrl = user.photoUrl?.toString()
            )
        } else {
            AuthState.LocalOnly
        }
    }

    open fun currentUserId(): String? = auth?.currentUser?.uid
    open fun currentUserEmail(): String? = auth?.currentUser?.email
    open fun currentDisplayName(): String? = auth?.currentUser?.displayName
    open fun currentPhotoUrl(): String? = auth?.currentUser?.photoUrl?.toString()

    open suspend fun restoreOnStart() {
        _authState.value = mapUserToState(auth?.currentUser)
    }

    open suspend fun signInWithCredential(credential: AuthCredential): AuthState {
        val firebaseAuth = auth ?: throw IllegalStateException("FirebaseAuth is not available")
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val state = mapUserToState(authResult.user)
        _authState.value = state
        return state
    }

    open suspend fun signOut() {
        auth?.signOut()
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
        _authState.value = AuthState.LocalOnly
    }
}
