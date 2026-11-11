package com.example.feature.account

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.cloud.auth.AuthRepository
import com.example.cloud.auth.AuthState
import com.example.cloud.sync.SyncCycleOutcome
import com.example.cloud.sync.SyncGateway
import com.example.data.preferences.FocusPreferencesRepository
import com.google.firebase.auth.AuthCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AccountBusy { CREDENTIAL_REQUEST, SIGN_IN, SIGN_OUT, SYNC }

enum class AccountMessage(@StringRes val text: Int, val isError: Boolean = true) {
    CONFIGURATION_MISSING(R.string.account_configuration_error),
    ACTIVITY_UNAVAILABLE(R.string.account_activity_error),
    UNSUPPORTED_CREDENTIAL(R.string.account_credential_error),
    NO_GOOGLE_ACCOUNT(R.string.account_no_google_account),
    SIGN_IN_CANCELLED(R.string.account_sign_in_cancelled, false),
    SIGN_IN_FAILED(R.string.account_sign_in_error),
    SIGNED_IN(R.string.account_signed_in, false),
    PROFILE_IMPORT_FAILED(R.string.account_profile_import_error),
    SYNC_FAILED(R.string.account_sync_error),
    SYNC_OFFLINE(R.string.account_sync_offline),
    SYNC_COMPLETE(R.string.account_sync_complete, false),
    SIGN_OUT_FAILED(R.string.account_sign_out_error),
    SIGNED_OUT(R.string.account_signed_out, false)
}

data class AccountUiState(
    val configured: Boolean = false,
    val auth: AuthState = AuthState.LocalOnly,
    val syncRunning: Boolean = false,
    val outcome: SyncCycleOutcome? = null,
    val busy: AccountBusy? = null,
    val feedback: AccountMessage? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncGateway: SyncGateway,
    private val preferencesRepository: FocusPreferencesRepository
) : ViewModel() {
    private val busy = MutableStateFlow<AccountBusy?>(null)
    private val feedback = MutableStateFlow<AccountMessage?>(null)

    val uiState: StateFlow<AccountUiState> = combine(
        authRepository.authState, syncGateway.running, syncGateway.lastOutcome, busy, feedback
    ) { auth, running, outcome, operation, message ->
        AccountUiState(authRepository.isConfigured, auth, running, outcome, operation, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountUiState(configured = authRepository.isConfigured))

    fun dismissInfo() { feedback.value = null }

    /** Lock the entire credential + Firebase flow, not just the account picker. */
    fun beginGoogleSignIn(): Boolean {
        if (!busy.compareAndSet(null, AccountBusy.CREDENTIAL_REQUEST)) return false
        feedback.value = null
        return true
    }

    fun finishCredentialRequest(message: AccountMessage? = null) {
        if (busy.compareAndSet(AccountBusy.CREDENTIAL_REQUEST, null) && message != null) feedback.value = message
    }

    fun signInWithGoogleCredential(credential: AuthCredential): Boolean {
        if (!busy.compareAndSet(AccountBusy.CREDENTIAL_REQUEST, AccountBusy.SIGN_IN)) return false
        viewModelScope.launch {
            try {
                val account = authRepository.signInWithCredential(credential)
                if (account !is AuthState.Authenticated) {
                    feedback.value = AccountMessage.SIGN_IN_FAILED
                    return@launch
                }
                // Authentication already succeeded. A sync/profile error must not claim otherwise.
                try {
                    val outcome = syncGateway.runCycle()
                    feedback.value = when (outcome) {
                        SyncCycleOutcome.Offline -> AccountMessage.SYNC_OFFLINE
                        is SyncCycleOutcome.Failed -> AccountMessage.SYNC_FAILED
                        SyncCycleOutcome.NoSession -> AccountMessage.SYNC_FAILED
                        else -> AccountMessage.SIGNED_IN
                    }
                    val current = preferencesRepository.snapshot()
                    if (!account.photoUrl.isNullOrBlank() && current.userPhotoUri.isNullOrBlank()) {
                        preferencesRepository.updateUserPhotoUri(account.photoUrl)
                    }
                    if (account.displayName.isNotBlank() && (current.userName.isBlank() || current.userName == "Focus Scholar")) {
                        preferencesRepository.updateUserName(account.displayName)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    Log.e(TAG, "Signed in but profile import failed", exception)
                    feedback.value = AccountMessage.PROFILE_IMPORT_FAILED
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.e(TAG, "Google sign-in failed", exception)
                feedback.value = AccountMessage.SIGN_IN_FAILED
            } finally {
                busy.value = null
            }
        }
        return true
    }

    fun retrySync() {
        if (syncGateway.running.value || authRepository.authState.value !is AuthState.Authenticated ||
            !busy.compareAndSet(null, AccountBusy.SYNC)) return
        feedback.value = null
        viewModelScope.launch {
            try {
                feedback.value = when (syncGateway.runCycle()) {
                    SyncCycleOutcome.Synced -> AccountMessage.SYNC_COMPLETE
                    SyncCycleOutcome.Offline -> AccountMessage.SYNC_OFFLINE
                    else -> AccountMessage.SYNC_FAILED
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.e(TAG, "Manual sync failed", exception)
                feedback.value = AccountMessage.SYNC_FAILED
            } finally {
                busy.value = null
            }
        }
    }

    fun signOut() {
        if (syncGateway.running.value || !busy.compareAndSet(null, AccountBusy.SIGN_OUT)) return
        feedback.value = null
        viewModelScope.launch {
            try {
                authRepository.signOut()
                feedback.value = AccountMessage.SIGNED_OUT
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.e(TAG, "Sign-out failed", exception)
                feedback.value = AccountMessage.SIGN_OUT_FAILED
            } finally {
                busy.value = null
            }
        }
    }

    private companion object { const val TAG = "AccountViewModel" }
}
