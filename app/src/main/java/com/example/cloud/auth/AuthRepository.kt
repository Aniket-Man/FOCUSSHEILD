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
class AuthRepository(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(mapUserToState(auth.currentUser))
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isConfigured: Boolean get() = true

    init {
        auth.addAuthStateListener { firebaseAuth ->
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

    fun currentUserId(): String? = auth.currentUser?.uid
    fun currentUserEmail(): String? = auth.currentUser?.email
    fun currentDisplayName(): String? = auth.currentUser?.displayName
    fun currentPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()

    suspend fun restoreOnStart() {
        _authState.value = mapUserToState(auth.currentUser)
    }

    suspend fun signInWithCredential(credential: AuthCredential): AuthState {
        val authResult = auth.signInWithCredential(credential).await()
        val state = mapUserToState(authResult.user)
        _authState.value = state
        return state
    }

    suspend fun signOut() {
        auth.signOut()
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
        _authState.value = AuthState.LocalOnly
    }
}
