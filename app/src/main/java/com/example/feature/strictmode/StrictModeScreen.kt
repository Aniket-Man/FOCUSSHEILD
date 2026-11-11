package com.example.feature.strictmode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow
import com.example.feature.session.SessionViewModel
import com.example.feature.settings.ProtectionSettingsSection
import com.example.feature.settings.SettingsViewModel

@Composable
fun StrictModeScreen(sessionViewModel: SessionViewModel, settingsViewModel: SettingsViewModel? = null,
    onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val setup by sessionViewModel.setupState.collectAsStateWithLifecycle()
    val active by sessionViewModel.activeSessionState.collectAsStateWithLifecycle()
    val saving by sessionViewModel.isSavingProtection.collectAsStateWithLifecycle()
    val error by sessionViewModel.protectionError.collectAsStateWithLifecycle()
    var confirmEnable by rememberSaveable { mutableStateOf(false) }
    val locked = active.hasActiveSession && active.isStrictModeEnabled && !active.isSessionCompleted
    val busy = saving || setup.isLoadingPreferences
    Scaffold(containerColor = FocusColors.Background, modifier = modifier.navigationBarsPadding().testTag("strict_mode_screen"),
        topBar = { FocusScreenHeader(stringResource(R.string.strict_title), onNavigateBack, Modifier.statusBarsPadding(),
            backButtonModifier = Modifier.testTag("strict_mode_back_button")) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.xl)) {
            item { FocusStatusMessage(error, isError = true, onDismiss = sessionViewModel::clearProtectionError) }
            item {
                Surface(color = FocusColors.Surface, shape = FocusShapes.card) {
                    FocusSwitchRow(stringResource(R.string.strict_default_label), setup.isStrictModeEnabled,
                        onCheckedChange = { if (it) confirmEnable = true else sessionViewModel.toggleStrictMode(false) },
                        enabled = !busy && !locked, description = stringResource(R.string.strict_default_body),
                        modifier = Modifier.testTag("strict_mode_master_switch"))
                }
            }
            if (locked) item { StrictInfoCard(stringResource(R.string.strict_active_title), stringResource(R.string.strict_active_body)) }
            item {
                StrictInfoCard(stringResource(R.string.strict_actual_section), stringResource(R.string.strict_breaks_body),
                    stringResource(R.string.strict_scope_body))
            }
            if (settingsViewModel != null) item {
                val settingsError by settingsViewModel.operationError.collectAsStateWithLifecycle()
                val lockError by settingsViewModel.strictLockMessage.collectAsStateWithLifecycle()
                FocusStatusMessage(lockError ?: settingsError, isError = true, onDismiss = {
                    settingsViewModel.clearOperationError(); settingsViewModel.clearStrictLockMessage()
                })
                Text(stringResource(R.string.strict_other_section), style = MaterialTheme.typography.titleMedium,
                    color = FocusColors.TextPrimary, modifier = Modifier.padding(bottom = FocusSpacing.md).semantics { heading() })
                ProtectionSettingsSection(settingsViewModel)
            }
            item { StrictInfoCard(stringResource(R.string.limits_title), stringResource(R.string.strict_limits_body), stringResource(R.string.strict_android_body)) }
        }
    }
    if (confirmEnable) AlertDialog(onDismissRequest = { confirmEnable = false },
        title = { Text(stringResource(R.string.strict_enable_title)) },
        text = { Text(stringResource(R.string.strict_enable_body)) },
        confirmButton = { Button({ confirmEnable = false; sessionViewModel.toggleStrictMode(true) }, enabled = !busy,
            modifier = Modifier.testTag("confirm_enable_strict_button")) { Text(stringResource(R.string.strict_enable_action)) } },
        dismissButton = { TextButton({ confirmEnable = false }) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun StrictInfoCard(title: String, vararg paragraphs: String) {
    Surface(color = FocusColors.SurfaceVariant, shape = FocusShapes.card) {
        Column(Modifier.fillMaxWidth().padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
            Text(title, color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            paragraphs.forEach { Text(it, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
