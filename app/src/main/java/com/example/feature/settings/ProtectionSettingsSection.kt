package com.example.feature.settings

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.ui.FocusActionRow
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow

/** Global protection is separate from a new session's Strict Mode default. */
@Composable
fun ProtectionSettingsSection(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.isSaving.collectAsStateWithLifecycle()
    val adminActive by viewModel.isDeviceAdminActive.collectAsStateWithLifecycle()
    val pendingAdmin by viewModel.pendingDeviceAdminRequest.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onDeviceAdminRequestResult()
    }
    fun activateAdmin() {
        try { launcher.launch(viewModel.getDeviceAdminIntent()) }
        catch (exception: Exception) {
            Log.w("ProtectionSettings", "Unable to open device-admin settings", exception)
            error = context.getString(R.string.profile_admin_error)
        }
    }
    LaunchedEffect(pendingAdmin) {
        if (pendingAdmin) { viewModel.onDeviceAdminRequestLaunched(); activateAdmin() }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { viewModel.checkAccessibilityStatus(); viewModel.checkDeviceAdminStatus() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(modifier) {
        FocusStatusMessage(error, isError = true, onDismiss = { error = null })
        Surface(color = FocusColors.Surface, shape = FocusShapes.card) {
            Column {
                FocusSwitchRow(stringResource(R.string.profile_uninstall), state.preferences.isBlockUninstallEnabled,
                    viewModel::updateBlockUninstall, enabled = !busy, modifier = Modifier.testTag("blocks_uninstall_switch"),
                    description = stringResource(when {
                        state.preferences.isBlockUninstallEnabled && adminActive -> R.string.profile_uninstall_active
                        state.preferences.isBlockUninstallEnabled -> R.string.profile_uninstall_pending
                        else -> R.string.profile_uninstall_off
                    }))
                if (state.preferences.isBlockUninstallEnabled && !adminActive) FocusActionRow(
                    stringResource(R.string.profile_activate_admin), ::activateAdmin, enabled = !busy)
                FocusSwitchRow(stringResource(R.string.protection_split_label), state.preferences.isBlockSplitScreenEnabled,
                    viewModel::updateBlockSplitScreen, enabled = !busy, description = stringResource(R.string.protection_split_body),
                    modifier = Modifier.testTag("blocks_split_screen_switch"))
                FocusSwitchRow(stringResource(R.string.protection_floating_label), state.preferences.isBlockFloatingWindowEnabled,
                    viewModel::updateBlockFloatingWindow, enabled = !busy, description = stringResource(R.string.protection_floating_body),
                    modifier = Modifier.testTag("blocks_floating_window_switch"))
            }
        }
    }
}
