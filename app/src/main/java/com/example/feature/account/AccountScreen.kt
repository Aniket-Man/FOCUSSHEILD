package com.example.feature.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.cloud.auth.AuthState
import com.example.cloud.auth.GoogleWebClientId
import com.example.cloud.sync.SyncCycleOutcome
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.FocusStatusMessage
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun Context.accountActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun AccountScreen(accountViewModel: AccountViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by accountViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    fun launchGoogleSignIn() {
        if (!accountViewModel.beginGoogleSignIn()) return
        scope.launch {
            var submitted = false
            try {
                val clientId = GoogleWebClientId.resolve(context)
                val activity = context.accountActivity()
                if (clientId == null || activity == null) {
                    accountViewModel.finishCredentialRequest(if (clientId == null) AccountMessage.CONFIGURATION_MISSING else AccountMessage.ACTIVITY_UNAVAILABLE)
                    return@launch
                }
                val option = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = credentialManager.getCredential(activity, request).credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    submitted = accountViewModel.signInWithGoogleCredential(GoogleAuthProvider.getCredential(token, null))
                } else {
                    accountViewModel.finishCredentialRequest(AccountMessage.UNSUPPORTED_CREDENTIAL)
                }
            } catch (cancelled: GetCredentialCancellationException) {
                accountViewModel.finishCredentialRequest(AccountMessage.SIGN_IN_CANCELLED)
            } catch (missing: NoCredentialException) {
                accountViewModel.finishCredentialRequest(AccountMessage.NO_GOOGLE_ACCOUNT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.e("AccountScreen", "Unable to get Google credential", exception)
                accountViewModel.finishCredentialRequest(AccountMessage.SIGN_IN_FAILED)
            } finally {
                if (!submitted) accountViewModel.finishCredentialRequest()
            }
        }
    }

    AccountContent(state, onNavigateBack, ::launchGoogleSignIn, accountViewModel::retrySync,
        accountViewModel::signOut, accountViewModel::dismissInfo, modifier)
}

@Composable
internal fun AccountContent(
    state: AccountUiState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val unavailable = state.busy != null || state.syncRunning
    val signingIn = state.busy == AccountBusy.CREDENTIAL_REQUEST || state.busy == AccountBusy.SIGN_IN
    val syncing = state.syncRunning || state.busy == AccountBusy.SYNC

    Column(modifier.fillMaxSize().background(FocusColors.Background).statusBarsPadding().navigationBarsPadding()) {
        // The header is content-height; it does not inherit TopAppBar's single-line title limit.
        FocusScreenHeader(stringResource(R.string.account_title), onBack, Modifier.testTag("account_back_button"))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.xl)
        ) {
            FocusStatusMessage(state.feedback?.let { stringResource(it.text) }, onDismissMessage, isError = state.feedback?.isError == true)
            when (val auth = state.auth) {
                is AuthState.Authenticated -> {
                    AccountCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.base)) {
                            if (!auth.photoUrl.isNullOrBlank()) {
                                AsyncImage(auth.photoUrl, contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape))
                            } else {
                                Box(Modifier.size(64.dp).background(FocusColors.PrimaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Person, null, tint = FocusColors.OnPrimaryContainer, modifier = Modifier.size(32.dp))
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                                Text(auth.displayName.ifBlank { stringResource(R.string.account_user_fallback) }, color = FocusColors.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                Text(auth.email, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = FocusColors.EmeraldText)
                                    Text(stringResource(R.string.account_google_active), color = FocusColors.EmeraldText, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    AccountCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                            Icon(Icons.Rounded.CloudDone, null, tint = FocusColors.Primary)
                            Text(stringResource(R.string.account_backup_title), color = FocusColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                        }
                        val outcomeText = stringResource(when {
                            syncing -> R.string.account_syncing
                            state.outcome == SyncCycleOutcome.Synced -> R.string.account_backup_synced
                            state.outcome == SyncCycleOutcome.Offline -> R.string.account_sync_offline
                            state.outcome is SyncCycleOutcome.Failed -> R.string.account_sync_error
                            else -> R.string.account_backup_ready
                        })
                        Text(outcomeText, color = if (state.outcome is SyncCycleOutcome.Failed) FocusColors.BlockedText else FocusColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Button(onSync, enabled = !unavailable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sync_now_button")) {
                            Icon(Icons.Rounded.Refresh, null)
                            Spacer(Modifier.width(FocusSpacing.sm))
                            Text(stringResource(if (syncing) R.string.account_syncing else R.string.account_sync_now))
                        }
                    }
                    Text(stringResource(R.string.account_sign_out_hint), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton({ confirmSignOut = true }, enabled = !unavailable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account_sign_out_button")) {
                        Icon(Icons.Rounded.Logout, null)
                        Spacer(Modifier.width(FocusSpacing.sm))
                        Text(stringResource(if (state.busy == AccountBusy.SIGN_OUT) R.string.account_signing_out else R.string.account_sign_out))
                    }
                }
                AuthState.LocalOnly, AuthState.ConfirmEmail -> {
                    AccountCard {
                        Icon(Icons.Rounded.Security, null, tint = FocusColors.Primary, modifier = Modifier.size(40.dp))
                        Text(stringResource(R.string.account_guest_title), color = FocusColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.account_guest_body), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        if (!state.configured) Text(stringResource(R.string.account_configuration_error), color = FocusColors.BlockedText, style = MaterialTheme.typography.bodyMedium)
                        Button(onSignIn, enabled = !unavailable && state.configured,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("account_google_sign_in_button")) {
                            if (signingIn) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else Image(painterResource(R.drawable.ic_google_logo), contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(FocusSpacing.sm))
                            Text(stringResource(if (signingIn) R.string.account_signing_in else R.string.account_sign_in))
                        }
                    }
                }
            }
            Spacer(Modifier.size(FocusSpacing.base))
        }
    }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text(stringResource(R.string.account_sign_out_title)) },
        text = { Text(stringResource(R.string.account_sign_out_hint)) },
        confirmButton = { TextButton({ confirmSignOut = false; onSignOut() }, enabled = !unavailable) { Text(stringResource(R.string.account_sign_out)) } },
        dismissButton = { TextButton({ confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun AccountCard(content: @Composable () -> Unit) {
    Surface(color = FocusColors.Surface, shape = FocusShapes.large, border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)) {
        Column(Modifier.fillMaxWidth().padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.base)) { content() }
    }
}
