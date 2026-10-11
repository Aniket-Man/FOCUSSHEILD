package com.example.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloud.auth.AuthRepository
import com.example.cloud.auth.AuthState
import com.example.cloud.sync.CloudInstallState
import com.example.cloud.sync.SyncCycleOutcome
import com.example.cloud.sync.SyncGateway
import com.google.firebase.auth.AuthCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Transient operations shown in the UI. */
enum class AccountBusy { SIGN_IN, SIGN_OUT, BACKUP, RESTORE }

data class AccountUiState(
    val configured: Boolean = true,
    val auth: AuthState = AuthState.LocalOnly,
    val syncRunning: Boolean = false,
    val outcome: SyncCycleOutcome? = null,
    val busy: AccountBusy? = null,
    val infoMessage: String? = null
)

/**
 * Drives the Account screen: Google Account authentication, sync status observation,
 * and profile backup/restoration management.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudInstallState: CloudInstallState,
    private val syncGateway: SyncGateway
) : ViewModel() {

    private val _busy = MutableStateFlow<AccountBusy?>(null)
    private val _info = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountUiState> =
        combine(
            authRepository.authState,
            syncGateway.running,
            syncGateway.lastOutcome,
            _busy,
            _info
        ) { auth, syncRunning, outcome, busy, info ->
            AccountUiState(
                configured = true,
                auth = auth,
                syncRunning = syncRunning,
                outcome = outcome,
                busy = busy,
                infoMessage = info
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AccountUiState()
        )

    fun dismissInfo() {
        _info.value = null
    }

    fun signInWithGoogleCredential(credential: AuthCredential, onSuccess: () -> Unit = {}) {
        _busy.value = AccountBusy.SIGN_IN
        viewModelScope.launch {
            try {
                authRepository.signInWithCredential(credential)
                syncGateway.requestSync()
                onSuccess()
            } catch (t: Throwable) {
                _info.value = t.localizedMessage ?: "Sign in failed"
            } finally {
                _busy.value = null
            }
        }
    }

    /** Triggers immediate manual sync. */
    fun retrySync() {
        viewModelScope.launch {
            _busy.value = AccountBusy.BACKUP
            try {
                syncGateway.runCycle()
            } finally {
                _busy.value = null
            }
        }
    }

    fun signOut() {
        _busy.value = AccountBusy.SIGN_OUT
        viewModelScope.launch {
            try {
                authRepository.signOut()
                _info.value = "Signed out successfully"
            } catch (t: Exception) {
                _info.value = t.localizedMessage ?: "Failed to sign out"
            } finally {
                _busy.value = null
            }
        }
    }
}
