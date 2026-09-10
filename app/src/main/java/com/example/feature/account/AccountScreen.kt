package com.example.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cloud.auth.AuthState
import com.example.cloud.sync.SyncCycleOutcome
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing

/**
 * Account & Cloud Backup hub.
 *
 * Signed out: an email/password Sign In / Create Account form with inline validation and typed error
 * messages. Signed in: an account summary, live sync status with Retry, and Sign out. Creating the
 * first account on a device runs the automatic local→cloud migration with a visible progress dialog
 * (never destructive). Signing into an account that already owns cloud data — or a different account
 * than the one that owns this device — surfaces an explicit confirm dialog before the app's only
 * destructive operation runs. Declining simply signs out and leaves this device's Room data intact;
 * the app is fully usable offline, signed out.
 */
@Composable
fun AccountScreen(
    accountViewModel: AccountViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by accountViewModel.uiState.collectAsStateWithLifecycle()

    // Whenever a session is (or becomes) present, run the post-auth boundary flow exactly once per
    // uid: migrate a brand-new account automatically, or ask for explicit confirmation before a
    // destructive restore.
    val auth = state.auth
    LaunchedEffect(auth) {
        if (auth is AuthState.Authenticated) accountViewModel.maybeHandleSession(auth.userId)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Account & Cloud",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 20.sp
                    )
                )
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("account_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = FocusSpacing.screenHorizontal)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
            ) {
                if (!state.configured) {
                    item { NotConfiguredCard() }
                } else if (auth is AuthState.Authenticated) {
                    item { SignedInPane(state, auth, accountViewModel) }
                } else {
                    item { SignedOutForm(state, accountViewModel) }
                }
            }
        }
    }

    // Transient info message (email-confirmation notice, restore result, …) as a small dialog.
    state.infoMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { accountViewModel.dismissInfo() },
            containerColor = FocusColors.Surface,
            shape = FocusShapes.large,
            title = {
                Text(
                    text = "Account & Cloud",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
            },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
                )
            },
            confirmButton = {
                TextButton(onClick = { accountViewModel.dismissInfo() }) {
                    Text("Got it", color = FocusColors.Primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Explicit, confirm-first boundary before the only destructive operation in the app.
    state.decision?.let { decision ->
        when (decision.kind) {
            RestoreKind.FRESH_RESTORE -> DecisionDialog(
                title = "Restore this backup to the device?",
                message = "This account already has a cloud backup. Continue replaces this device's " +
                    "data with the account's backup so you can pick up where you left off on another device.",
                confirmLabel = "Restore & Replace",
                onConfirm = { accountViewModel.confirmRestore() },
                onDecline = { accountViewModel.declineRestore() }
            )
            RestoreKind.OTHER_ACCOUNT_HAS_DATA -> DecisionDialog(
                title = "Switch to this account's backup?",
                message = "This device's data belongs to a different account. Continuing will clear this " +
                    "device's synced data and load the new account's cloud backup in its place. This only " +
                    "affects this device — nothing is deleted from any cloud account.",
                confirmLabel = "Replace Device Data",
                onConfirm = { accountViewModel.confirmRestore() },
                onDecline = { accountViewModel.declineRestore() }
            )
            RestoreKind.OTHER_ACCOUNT_EMPTY -> DecisionDialog(
                title = "Start fresh with this account?",
                message = "This device's data belongs to a different account, and the account you just " +
                    "signed into has no cloud backup yet. Continuing clears this device's synced data and " +
                    "starts fresh under the new account (your old account's data is never mixed in).",
                confirmLabel = "Switch Account",
                onConfirm = { accountViewModel.confirmRestore() },
                onDecline = { accountViewModel.declineRestore() }
            )
        }
    }

    // Non-dismissible progress for the automatic first-account migration and the confirmed restore.
    when (state.busy) {
        AccountBusy.BACKUP -> BusyDialog(
            title = "Creating your cloud backup",
            body = "Uploading this device's data to your account. This first backup is automatic and " +
                "never deletes anything from this device — you can keep studying while it runs."
        )
        AccountBusy.RESTORE -> BusyDialog(
            title = "Restoring your backup",
            body = "Replacing this device's data with the account's cloud backup…"
        )
        else -> Unit
    }
}

// ---- signed-out form -----------------------------------------------------------------------

@Composable
private fun SignedOutForm(state: AccountUiState, viewModel: AccountViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
        Surface(
            shape = FocusShapes.card,
            color = FocusColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
        ) {
            Column(
                modifier = Modifier.padding(FocusSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(FocusColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloud backup, when you want it",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                        Text(
                            text = "Sign in to back up your data and restore it on another device.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                Text(
                    text = "Everything already works without an account. Your data stays on this device " +
                        "until you choose to turn on backup — and signing out keeps it all here, fully usable.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextMuted,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // Sign In / Create Account segmented toggle
        ModeToggle(
            mode = state.mode,
            enabled = !state.submitting && state.busy == null,
            onSelect = { viewModel.selectMode(it) }
        )

        val isSignUp = state.mode == FormMode.SIGN_UP

        OutlinedTextField(
            value = state.emailInput,
            onValueChange = viewModel::updateEmail,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account_email_field"),
            label = { Text("Email address") },
            leadingIcon = {
                Icon(Icons.Rounded.Email, contentDescription = null, tint = FocusColors.TextMuted)
            },
            singleLine = true,
            enabled = !state.submitting,
            isError = state.formError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = FocusShapes.medium,
            colors = accountFieldColors()
        )

        var passwordVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = state.passwordInput,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account_password_field"),
            label = { Text("Password") },
            leadingIcon = {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = FocusColors.TextMuted)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = FocusColors.TextMuted
                    )
                }
            },
            singleLine = true,
            enabled = !state.submitting,
            isError = state.formError != null,
            supportingText = state.formError?.let { error ->
                {
                    Text(
                        text = error,
                        color = FocusColors.BlockedRed,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("account_form_error")
                    )
                }
            },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            shape = FocusShapes.medium,
            colors = accountFieldColors()
        )

        Button(
            onClick = { if (isSignUp) viewModel.submitSignUp() else viewModel.submitSignIn() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(if (isSignUp) "account_sign_up_button" else "account_sign_in_button"),
            enabled = !state.submitting && state.busy == null,
            shape = FocusShapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = FocusColors.Primary,
                contentColor = FocusColors.TextOnDark
            )
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = FocusColors.TextOnDark,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isSignUp) "Create Account & Turn On Backup" else "Sign In",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextOnDark
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (state.busy == AccountBusy.SEND_RESET) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            TextButton(
                onClick = { viewModel.submitForgotPassword() },
                enabled = !state.submitting && state.busy == null
            ) {
                Text(
                    text = "Forgot password?",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        if (isSignUp) {
            Text(
                text = "Creating an account immediately backs up this device's data to it. If you'd " +
                    "rather keep an account for a different device, sign in on that device instead.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextMuted,
                    fontSize = 11.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---- signed-in pane -------------------------------------------------------------------------

@Composable
private fun SignedInPane(
    state: AccountUiState,
    auth: AuthState.Authenticated,
    viewModel: AccountViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)) {
        // Account summary card
        Surface(
            shape = FocusShapes.card,
            color = FocusColors.Surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
        ) {
            Row(
                modifier = Modifier.padding(FocusSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(FocusColors.PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = auth.email.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.Primary
                        )
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = auth.email,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Cloud backup is on for this account",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.EmeraldSuccess,
                            fontSize = 12.sp
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = FocusColors.EmeraldSuccess,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Live sync status banner
        SyncStatusBanner(
            running = state.syncRunning,
            outcome = state.outcome,
            onRetry = { viewModel.retrySync() }
        )

        Text(
            text = "Your profile, blocklists, schedules, plans and study history are backed up and " +
                "restorable on another device. Syncing is automatic whenever you're online.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextSecondary,
                fontSize = 12.sp
            )
        )

        OutlinedButton(
            onClick = { viewModel.signOut() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("account_sign_out_button"),
            enabled = state.busy == null,
            shape = FocusShapes.button,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = FocusColors.BlockedRed),
            border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.BlockedRed.copy(alpha = 0.5f))
        ) {
            if (state.busy == AccountBusy.SIGN_OUT) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = FocusColors.BlockedRed,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Sign Out (data stays on this device)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.BlockedRed
                )
            )
        }
        Text(
            text = "Signing out keeps all of your data on this device and the app works fully offline. " +
                "Nothing is deleted — you can sign back in anytime.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextMuted,
                fontSize = 11.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---- status banner ---------------------------------------------------------------------------

private data class BannerStyle(val color: Color, val icon: ImageVector, val title: String)

@Composable
private fun SyncStatusBanner(
    running: Boolean,
    outcome: SyncCycleOutcome?,
    onRetry: () -> Unit
) {
    val style: BannerStyle = when {
        running -> BannerStyle(
            FocusColors.Primary,
            Icons.Rounded.Sync,
            "Syncing your changes…"
        )
        outcome is SyncCycleOutcome.Synced || outcome is SyncCycleOutcome.Migrated -> BannerStyle(
            FocusColors.EmeraldSuccess,
            Icons.Rounded.CheckCircle,
            "Backup is up to date"
        )
        outcome is SyncCycleOutcome.Offline -> BannerStyle(
            FocusColors.AmberOrange,
            Icons.Rounded.CloudOff,
            "Offline — changes will sync automatically when you're back online"
        )
        outcome is SyncCycleOutcome.Failed -> BannerStyle(
            FocusColors.BlockedRed,
            Icons.Rounded.Warning,
            "Couldn't sync this time"
        )
        else -> BannerStyle(
            FocusColors.TextSecondary,
            Icons.Rounded.Sync,
            "Not synced yet"
        )
    }
    val isError = outcome is SyncCycleOutcome.Failed || outcome is SyncCycleOutcome.Offline

    Surface(
        shape = FocusShapes.medium,
        color = style.color.copy(alpha = 0.10f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, style.color.copy(alpha = 0.25f), FocusShapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = FocusSpacing.base, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = style.color,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = style.color,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                )
                if (outcome is SyncCycleOutcome.Failed && outcome.message.isNotBlank()) {
                    Text(
                        text = outcome.message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 2
                    )
                }
            }
            if (!running && isError) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRetry) {
                    Text("Retry", color = FocusColors.Primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---- shared chrome ---------------------------------------------------------------------------

@Composable
private fun ModeToggle(
    mode: FormMode,
    enabled: Boolean,
    onSelect: (FormMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.medium)
            .background(FocusColors.SurfaceSubtle)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeTab(label = "Sign In", selected = mode == FormMode.SIGN_IN, enabled = enabled) {
            onSelect(FormMode.SIGN_IN)
        }
        ModeTab(label = "Create Account", selected = mode == FormMode.SIGN_UP, enabled = enabled) {
            onSelect(FormMode.SIGN_UP)
        }
    }
}

@Composable
private fun RowScope.ModeTab(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(46.dp)
            .clip(FocusShapes.medium)
            .background(if (selected) FocusColors.Primary else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) FocusColors.TextOnDark else FocusColors.TextSecondary,
                fontSize = 13.sp
            )
        )
    }
}

@Composable
private fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FocusColors.Primary,
    unfocusedBorderColor = FocusColors.CardBorder,
    focusedLabelColor = FocusColors.Primary,
    unfocusedLabelColor = FocusColors.TextMuted,
    cursorColor = FocusColors.Primary,
    focusedContainerColor = FocusColors.Surface,
    unfocusedContainerColor = FocusColors.Surface,
    errorBorderColor = FocusColors.BlockedRed,
    errorLabelColor = FocusColors.BlockedRed,
    errorSupportingTextColor = FocusColors.BlockedRed
)

@Composable
private fun NotConfiguredCard() {
    Surface(
        shape = FocusShapes.card,
        color = FocusColors.Surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
    ) {
        Column(
            modifier = Modifier.padding(FocusSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = FocusColors.TextMuted,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Cloud backup isn't available on this build",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "FocusShield works fully offline with everything stored on this device.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DecisionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        // No outside-tap / back dismissal: the user must explicitly choose Restore or Not Now, so
        // system-back can never silently trigger the sign-out that decline implies.
        onDismissRequest = {},
        containerColor = FocusColors.Surface,
        shape = FocusShapes.large,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                )
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = FocusColors.BlockedRed,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(
                    text = "Not Now",
                    color = FocusColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun BusyDialog(title: String, body: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = FocusShapes.large,
            color = FocusColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(FocusSpacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = FocusColors.Primary,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(FocusSpacing.base))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}
