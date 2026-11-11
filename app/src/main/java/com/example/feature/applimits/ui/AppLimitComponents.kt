package com.example.feature.applimits.ui

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusActionRow
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow
import com.example.core.util.DeviceUsageStatsHelper
import java.text.NumberFormat
import java.util.UUID

@Composable
fun AppLimitFeedback(viewModel: AppLimitsViewModel) {
    val error by viewModel.operationError.collectAsStateWithLifecycle()
    val strictError by viewModel.strictLockMessage.collectAsStateWithLifecycle()
    val info by viewModel.infoMessage.collectAsStateWithLifecycle()
    FocusStatusMessage(strictError ?: error ?: info, isError = strictError != null || error != null,
        onDismiss = viewModel::clearFeedback)
}

@Composable
fun AppLimitPermissionNotices(viewModel: AppLimitsViewModel) {
    val accessibility by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val usageAccess by viewModel.hasUsageAccess.collectAsStateWithLifecycle()
    val warning by viewModel.overlayLaunchWarning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun openSettings(block: () -> Unit) {
        try { block() } catch (exception: Exception) {
            Log.w("AppLimits", "Unable to open permission settings", exception)
            error = context.getString(R.string.limits_permission_open_error)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
        FocusStatusMessage(error, isError = true, onDismiss = { error = null })
        if (!accessibility || !usageAccess || warning != null) Surface(color = FocusColors.SurfaceVariant, shape = FocusShapes.card) {
            Column {
                if (!accessibility) FocusActionRow(stringResource(R.string.limits_open_accessibility),
                    onClick = { openSettings { AccessibilityHelper.openAccessibilitySettings(context) } },
                    description = stringResource(R.string.limits_accessibility_body))
                if (!usageAccess) FocusActionRow(stringResource(R.string.limits_open_usage),
                    onClick = { openSettings { DeviceUsageStatsHelper.openUsageAccessSettings(context) } },
                    description = stringResource(R.string.limits_usage_access_body))
                if (warning != null) Column(Modifier.padding(FocusSpacing.base)) {
                    Text(stringResource(R.string.limits_enforcement_warning), style = MaterialTheme.typography.titleSmall)
                    Text(warning.orEmpty(), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AppLimitEmptyState(onAdd: () -> Unit, enabled: Boolean = true) {
    Surface(color = FocusColors.Surface, shape = FocusShapes.card, border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)) {
        Column(Modifier.fillMaxWidth().padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
            Text(stringResource(R.string.limits_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.limits_empty_body), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Button(onAdd, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.limits_add)) }
        }
    }
}

@Composable
fun AppLimitSummary(state: AppLimitsUiState) {
    Surface(color = FocusColors.PrimaryContainer, contentColor = FocusColors.OnPrimaryContainer, shape = FocusShapes.card) {
        Column(Modifier.fillMaxWidth().padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)) {
            SummaryMetric(stringResource(R.string.limits_summary_used), pluralStringResource(R.plurals.limits_minutes,
                state.totalUsedMinutesToday, state.totalUsedMinutesToday))
            SummaryMetric(stringResource(R.string.limits_summary_active), NumberFormat.getIntegerInstance().format(state.activeLimitsCount))
            SummaryMetric(stringResource(R.string.limits_summary_reached), NumberFormat.getIntegerInstance().format(state.reachedLimitsCount))
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLimitCard(item: AppLimitUiItem, viewModel: AppLimitsViewModel, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val busy by viewModel.isSaving.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmAllow by rememberSaveable { mutableStateOf(false) }
    val icon = remember(item.appIcon) { try { item.appIcon?.toBitmap(48, 48)?.asImageBitmap() } catch (_: Exception) { null } }
    val allowance = pluralStringResource(R.plurals.limits_minutes, item.dailyLimitMinutes, item.dailyLimitMinutes)
    val used = pluralStringResource(R.plurals.limits_minutes, item.usedMinutes, item.usedMinutes)
    val status = when {
        !item.isEnabled -> stringResource(R.string.limits_off)
        item.isBypassedToday -> stringResource(R.string.limits_allowed_today)
        item.isLimitReached -> stringResource(R.string.limits_reached)
        item.remainingMinutes == 0 -> stringResource(R.string.limits_less_than_minute)
        else -> pluralStringResource(R.plurals.limits_remaining, item.remainingMinutes, item.remainingMinutes)
    }
    Surface(color = FocusColors.Surface, shape = FocusShapes.card, border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = modifier.fillMaxWidth().testTag("app_limit_${item.packageName}")) {
        Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
            Row(Modifier.fillMaxWidth().padding(start = FocusSpacing.base, top = FocusSpacing.base, end = FocusSpacing.sm),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                if (icon != null) Image(icon, null, Modifier.size(40.dp))
                else Icon(Icons.Rounded.Apps, null, tint = FocusColors.Primary, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                    Text(item.appName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(item.packageName, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (!item.isStrictOverride) Box {
                    IconButton({ menu = true }, enabled = !busy) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.limits_more_app, item.appName))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.limits_delete_action)) },
                            onClick = { menu = false; confirmDelete = true })
                        if (item.isEnabled && !item.isBypassedToday) DropdownMenuItem(text = { Text(stringResource(R.string.limits_allow_action)) },
                            onClick = { menu = false; confirmAllow = true })
                    }
                }
            }
            Column(Modifier.padding(horizontal = FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                Text(stringResource(R.string.limits_daily_allowance, allowance), color = FocusColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                Text(stringResource(R.string.limits_used_today, used), color = FocusColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                LinearProgressIndicator(progress = { item.usagePercentage }, modifier = Modifier.fillMaxWidth(),
                    color = if (item.isLimitReached) FocusColors.BlockedRed else FocusColors.Primary,
                    trackColor = FocusColors.SurfaceVariant)
                Text(status, color = if (item.isLimitReached) FocusColors.BlockedText else FocusColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium)
                if (item.isStrictOverride) Text(stringResource(R.string.limits_strict_locked),
                    color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            FocusSwitchRow(stringResource(R.string.limits_enforce_app, item.appName), item.isEnabled,
                { viewModel.toggleLimit(item.packageName, it) }, enabled = !busy && !item.isStrictOverride,
                modifier = Modifier.testTag("toggle_limit_${item.packageName}"))
            FlowRow(Modifier.padding(horizontal = FocusSpacing.base, vertical = FocusSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                if (!item.isStrictOverride) OutlinedButton(onEdit, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.limits_edit_app, item.appName))
                }
            }
        }
    }
    if (confirmDelete) FocusDestructiveDialog(stringResource(R.string.limits_delete_app, item.appName),
        stringResource(R.string.limits_delete_body), onConfirm = { viewModel.deleteLimit(item.packageName) },
        onDismiss = { confirmDelete = false }, confirmLabel = stringResource(R.string.limits_delete_action))
    if (confirmAllow) AlertDialog(onDismissRequest = { confirmAllow = false },
        title = { Text(stringResource(R.string.limits_allow_title, item.appName)) },
        text = { Text(stringResource(R.string.limits_allow_body)) },
        confirmButton = { TextButton({ confirmAllow = false; viewModel.unlockForToday(item.packageName) }, enabled = !busy) {
            Text(stringResource(R.string.limits_allow_action))
        } }, dismissButton = { TextButton({ confirmAllow = false }) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
fun AppLimitEditorHost(
    viewModel: AppLimitsViewModel,
    editingPackage: String?,
    onDismiss: () -> Unit,
    initialAppName: String? = null,
    requireExisting: Boolean = editingPackage != null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val apps by viewModel.installedApps.collectAsStateWithLifecycle()
    val loading by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val loadError by viewModel.appsLoadError.collectAsStateWithLifecycle()
    val saving by viewModel.isSaving.collectAsStateWithLifecycle()
    val editorId = rememberSaveable { UUID.randomUUID().toString() }
    val completed by viewModel.completedEditors.collectAsStateWithLifecycle()
    LaunchedEffect(editorId, completed) {
        if (editorId in completed) {
            onDismiss()
            viewModel.acknowledgeEditor(editorId)
        }
    }
    if (state.items.isEmpty() && editingPackage != null && (state.isLoading || state.loadError != null)) {
        AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
            title = { Text(stringResource(if (state.loadError != null) R.string.limits_edit_title else R.string.limits_loading)) },
            text = state.loadError?.let { message -> ({ Text(message, color = FocusColors.BlockedText) }) },
            confirmButton = { TextButton(if (state.loadError != null) viewModel::refresh else onDismiss, enabled = !saving) {
                Text(stringResource(if (state.loadError != null) R.string.action_retry else R.string.action_cancel))
            } },
            dismissButton = { if (state.loadError != null) TextButton(onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) } })
        return
    }
    AddEditAppLimitDialog(installedApps = apps, editingItem = state.items.firstOrNull { it.packageName == editingPackage },
        onDismiss = onDismiss, onSave = { pkg, name, minutes, strict, reminders, emergency, requireExisting ->
            viewModel.saveLimit(pkg, name, minutes, strict, reminders, emergency, requireExisting, editorId)
        }, existingLimits = state.items,
        loadingApps = loading, appsLoadError = loadError, onRetryApps = viewModel::loadInstalledApps, saving = saving,
        initialEditingPackage = editingPackage, initialAppName = initialAppName,
        forceRequireExisting = requireExisting)
}
