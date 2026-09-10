package com.example.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloud.SupabaseConfig
import com.example.cloud.auth.AuthRepository
import com.example.cloud.auth.AuthState
import com.example.cloud.http.CloudNotConfiguredException
import com.example.cloud.http.SupabaseApiException
import com.example.cloud.sync.CloudInstallState
import com.example.cloud.sync.SyncCycleOutcome
import com.example.cloud.sync.SyncGateway
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sign-in vs. create-account mode of the entry form. */
enum class FormMode { SIGN_IN, SIGN_UP }

/** Transient long-running operation currently shown in the UI (blocks the relevant action). */
enum class AccountBusy { SIGN_IN, SIGN_UP, SEND_RESET, SIGN_OUT, BACKUP, RESTORE }

/**
 * The destructive account-boundary choice the user must confirm before the engine's only
 * destructive operation runs (clear this device's synced tables + restore another account).
 */
enum class RestoreKind { FRESH_RESTORE, OTHER_ACCOUNT_HAS_DATA, OTHER_ACCOUNT_EMPTY }

data class RestoreDecision(val uid: String, val kind: RestoreKind)

private data class AccountForm(
    val mode: FormMode = FormMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val error: String? = null,
    val submitting: Boolean = false
)

data class AccountUiState(
    val configured: Boolean = false,
    val auth: AuthState = AuthState.LocalOnly,
    val syncRunning: Boolean = false,
    val outcome: SyncCycleOutcome? = null,
    val busy: AccountBusy? = null,
    val decision: RestoreDecision? = null,
    val infoMessage: String? = null,
    val mode: FormMode = FormMode.SIGN_IN,
    val emailInput: String = "",
    val passwordInput: String = "",
    val formError: String? = null,
    val submitting: Boolean = false
)

/**
 * Drives the Account screen: email/password auth, first-account migration with visible progress, and
 * the explicit, confirm-first boundary flow when signing into an account that already owns cloud data
 * or is different from the one that owns this device's data.
 *
 * Decisions are never guessed here: after auth we ask [SyncGateway.runCycle], which consults the
 * install bookkeeping ([CloudInstallState]) and returns the engine's authoritative outcome. A normal
 * first account is migrated automatically (busy progress); the two destructive outcomes
 * ([SyncCycleOutcome.RestoreRequired], [SyncCycleOutcome.CrossAccountEmptyAccount]) surface as an
 * explicit [RestoreDecision] for the user to confirm or decline. Declining signs out and leaves the
 * device's Room data untouched — the app is always usable offline, signed out.
 */
class AccountViewModel(
    private val authRepository: AuthRepository,
    private val cloudInstallState: CloudInstallState,
    private val syncGateway: SyncGateway
) : ViewModel() {

    private val _busy = MutableStateFlow<AccountBusy?>(null)
    private val _decision = MutableStateFlow<RestoreDecision?>(null)
    private val _info = MutableStateFlow<String?>(null)
    private val _form = MutableStateFlow(AccountForm())

    /** Uid whose entry boundary flow already ran this process, so it never double-fires. */
    private var handledSessionUid: String? = null

    val uiState: StateFlow<AccountUiState> =
        // kotlinx.coroutines combine() only has typed overloads up to 5 flows, so the 7 upstreams
        // are combined through the vararg form and each slot cast back to its known type below.
        combine(
            authRepository.authState,
            syncGateway.running,
            syncGateway.lastOutcome,
            _busy,
            _decision,
            _info,
            _form
        ) { values ->
            val auth = values[0] as AuthState
            val running = values[1] as Boolean
            val outcome = values[2] as SyncCycleOutcome?
            val busy = values[3] as AccountBusy?
            val decision = values[4] as RestoreDecision?
            val info = values[5] as String?
            val form = values[6] as AccountForm
            AccountUiState(
                configured = SupabaseConfig.isConfigured,
                auth = auth,
                syncRunning = running,
                outcome = outcome,
                busy = busy,
                decision = decision,
                infoMessage = info,
                mode = form.mode,
                emailInput = form.email,
                passwordInput = form.password,
                formError = form.error,
                submitting = form.submitting
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AccountUiState(configured = SupabaseConfig.isConfigured)
        )

    // ---- form editing --------------------------------------------------------------------

    fun updateEmail(value: String) = _form.update { it.copy(email = value, error = null) }

    fun updatePassword(value: String) = _form.update { it.copy(password = value, error = null) }

    fun selectMode(mode: FormMode) = _form.update { it.copy(mode = mode, password = "", error = null) }

    fun dismissInfo() {
        _info.value = null
    }

    // ---- submit actions -------------------------------------------------------------------

    fun submitSignIn() {
        val form = _form.value
        val email = form.email.trim()
        val validation = validate(email, form.password)
        if (validation != null) {
            _form.update { it.copy(error = validation) }
            return
        }
        launchAuth(AccountBusy.SIGN_IN) { authRepository.signIn(email, form.password) }
    }

    fun submitSignUp() {
        val form = _form.value
        val email = form.email.trim()
        val validation = validate(email, form.password)
        if (validation != null) {
            _form.update { it.copy(error = validation) }
            return
        }
        launchAuth(AccountBusy.SIGN_UP) { authRepository.signUp(email, form.password) }
    }

    fun submitForgotPassword() {
        val email = _form.value.email.trim()
        if (!isPlausibleEmail(email)) {
            _form.update { it.copy(error = "Enter the email address you signed up with.") }
            return
        }
        _busy.value = AccountBusy.SEND_RESET
        viewModelScope.launch {
            try {
                authRepository.forgotPassword(email)
                _info.value = "If an account exists for $email, a password-reset link is on its way."
            } catch (t: Throwable) {
                _form.update { it.copy(error = friendly(t)) }
            } finally {
                _busy.value = null
            }
        }
    }

    /** Common wrapper for sign-in / sign-up: busy + submitting while the network call runs. */
    private fun launchAuth(busy: AccountBusy, call: suspend () -> AuthState) {
        _form.update { it.copy(error = null, submitting = true) }
        _busy.value = busy
        viewModelScope.launch {
            var next: AuthState? = null
            try {
                next = call()
            } catch (t: Throwable) {
                _form.update { it.copy(error = friendly(t)) }
            } finally {
                _busy.value = null
                _form.update { it.copy(submitting = false) }
            }
            next?.let { handleAuthResult(it) }
        }
    }

    private fun handleAuthResult(state: AuthState) {
        when (state) {
            is AuthState.Authenticated -> maybeHandleSession(state.userId)
            AuthState.ConfirmEmail -> _info.value =
                "Account created! Confirm your email using the link we sent, then sign in."
            else -> Unit
        }
    }

    // ---- post-auth boundary flow ----------------------------------------------------------

    /**
     * Entry point called whenever the UI observes an [AuthState.Authenticated] session (on-screen
     * restore or a fresh submit). Runs once per uid. If the account was already migrated from this
     * install it just asks the gateway to sync; otherwise it runs a cycle, which the engine resolves
     * to an automatic safe migration (empty cloud) or a [RestoreDecision] that needs confirmation.
     */
    fun maybeHandleSession(uid: String) {
        if (handledSessionUid == uid) return
        handledSessionUid = uid
        viewModelScope.launch { boundaryFlow(uid) }
    }

    private suspend fun boundaryFlow(uid: String) {
        if (cloudInstallState.migratedAccounts().contains(uid)) {
            // Already synced from this install; a normal cycle uploads any offline edits.
            syncGateway.requestSync()
            return
        }
        _busy.value = AccountBusy.BACKUP
        val outcome = try {
            syncGateway.runCycle()
        } finally {
            _busy.value = null
        }
        decisionFor(outcome, uid)?.let { _decision.value = it }
    }

    private suspend fun decisionFor(outcome: SyncCycleOutcome, uid: String): RestoreDecision? =
        when (outcome) {
            SyncCycleOutcome.RestoreRequired -> {
                val owner = cloudInstallState.dataOwnerAccountId()
                val kind = if (owner == null || owner == uid) {
                    RestoreKind.FRESH_RESTORE
                } else {
                    RestoreKind.OTHER_ACCOUNT_HAS_DATA
                }
                RestoreDecision(uid, kind)
            }
            SyncCycleOutcome.CrossAccountEmptyAccount ->
                RestoreDecision(uid, RestoreKind.OTHER_ACCOUNT_EMPTY)
            else -> null
        }

    /** "Sync now" / retry after a transient [SyncCycleOutcome.Failed]/[SyncCycleOutcome.Offline]. */
    fun retrySync() {
        val uid = (authRepository.authState.value as? AuthState.Authenticated)?.userId ?: return
        viewModelScope.launch { boundaryFlow(uid) }
    }

    // ---- destructive boundary confirmation ------------------------------------------------

    /** User confirmed the destructive restore (only destructive path in the app). */
    fun confirmRestore() {
        val decision = _decision.value ?: return
        _decision.value = null
        _busy.value = AccountBusy.RESTORE
        viewModelScope.launch {
            val outcome = try {
                syncGateway.restore(decision.uid)
            } finally {
                _busy.value = null
            }
            when (outcome) {
                is SyncCycleOutcome.Failed,
                SyncCycleOutcome.Offline,
                SyncCycleOutcome.NoSession -> _decision.value = decision
                else -> _info.value = "Restored this account's cloud backup on this device."
            }
        }
    }

    /**
     * User declined the destructive restore: sign out and keep this device's Room data untouched.
     * The app remains fully usable offline.
     */
    fun declineRestore() {
        _decision.value = null
        signOut()
    }

    fun signOut() {
        _decision.value = null
        _info.value = null
        handledSessionUid = null
        _busy.value = AccountBusy.SIGN_OUT
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } finally {
                _busy.value = null
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun isPlausibleEmail(email: String): Boolean =
        email.isNotBlank() && email.contains("@") && email.substringAfter("@", "").contains(".")

    private fun validate(email: String, password: String): String? {
        if (!isPlausibleEmail(email)) return "Enter a valid email address."
        if (password.length < 6) return "Password must be at least 6 characters."
        return null
    }

    /** Map Supabase / network errors to typed, human messages (never raw server strings with state). */
    private fun friendly(t: Throwable): String = when (t) {
        is CloudNotConfiguredException -> "Cloud backup isn't configured on this build."
        is SupabaseApiException -> when (t.code) {
            "weak_password" -> "Password is too weak — use at least 6 characters."
            "email_not_confirmed" -> "Please confirm your email first — check your inbox for the link."
            "invalid_credentials", "invalid_grant" -> "Incorrect email or password."
            "user_already_exists", "email_exists", "email_taken" ->
                "An account with this email already exists. Try signing in instead."
            "over_email_send_rate_limit", "over_request_rate_limit" ->
                "Too many attempts. Please wait a moment and try again."
            else -> t.message?.takeIf { it.isNotBlank() } ?: "Couldn't reach the server (${t.status})."
        }
        is IOException -> "Can't reach the server. Check your internet connection and try again."
        else -> t.message ?: "Something went wrong. Please try again."
    }
}
