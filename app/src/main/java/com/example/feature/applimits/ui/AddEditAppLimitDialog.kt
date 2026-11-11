package com.example.feature.applimits.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusActionRow
import com.example.core.ui.FocusSearchField
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow
import com.example.data.repository.AppLimitWriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Shared by Blocks and App Limits. The selected app and unsaved fields survive recreation. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditAppLimitDialog(
    installedApps: List<InstalledAppChoice>,
    editingItem: AppLimitUiItem?,
    onDismiss: () -> Unit,
    onSave: suspend (packageName: String, appName: String, dailyLimitMinutes: Int,
        isStrictOverride: Boolean, showRemindersBeforeLimit: Boolean, emergencyUsesAllowed: Int,
        requireExisting: Boolean) -> AppLimitWriteResult,
    existingLimits: List<AppLimitUiItem> = emptyList(),
    loadingApps: Boolean = false,
    appsLoadError: String? = null,
    onRetryApps: () -> Unit = {},
    saving: Boolean = false,
    initialEditingPackage: String? = null,
    initialAppName: String? = null,
    forceRequireExisting: Boolean? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedPackage by rememberSaveable { mutableStateOf(editingItem?.packageName ?: initialEditingPackage) }
    var selectedName by rememberSaveable { mutableStateOf(editingItem?.appName ?: initialAppName) }
    var query by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var localSaving by remember { mutableStateOf(false) }
    val busy = localSaving || saving
    val selectedApp = installedApps.firstOrNull { it.packageName == selectedPackage }
    val existing = editingItem ?: existingLimits.firstOrNull { it.packageName == selectedPackage }
    val locked = existing?.isStrictOverride == true || selectedApp?.isStrictLocked == true
    val fixedApp = editingItem != null || initialEditingPackage != null
    // Keep the edit intent even if the row disappears while this editor is open. Wellbeing can
    // open a fixed app that has no limit yet, so it passes the intent explicitly.
    val requireExisting = rememberSaveable(selectedPackage) {
        forceRequireExisting ?: (existing != null || fixedApp)
    }
    var minutesText by rememberSaveable(selectedPackage) { mutableStateOf((existing?.dailyLimitMinutes ?: 45).toString()) }
    var strict by rememberSaveable(selectedPackage) { mutableStateOf(existing?.isStrictOverride ?: false) }
    var reminders by rememberSaveable(selectedPackage) { mutableStateOf(existing?.showRemindersBeforeLimit ?: true) }
    var emergencyText by rememberSaveable(selectedPackage) { mutableStateOf((existing?.emergencyUsesAllowed ?: 1).toString()) }
    var minutesError by rememberSaveable(selectedPackage) { mutableStateOf<String?>(null) }
    var emergencyError by rememberSaveable(selectedPackage) { mutableStateOf<String?>(null) }
    var confirmStrict by rememberSaveable { mutableStateOf(false) }
    val minutesFocus = remember { FocusRequester() }
    val emergencyFocus = remember { FocusRequester() }
    val maxPickerHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    val filteredApps = remember(installedApps, query) {
        installedApps.filter { it.appName.contains(query, true) || it.packageName.contains(query, true) }
    }

    fun dismiss() { if (!busy) onDismiss() }
    fun save() {
        if (localSaving || saving || locked) return
        val minutes = minutesText.toIntOrNull()
        val emergency = if (strict) 0 else emergencyText.toIntOrNull()
        if (minutes == null || minutes !in 1..1440) {
            minutesError = context.getString(R.string.limits_minutes_error)
            minutesFocus.requestFocus()
            return
        }
        if (emergency == null || emergency !in 0..10) {
            emergencyError = context.getString(R.string.limits_emergency_error)
            emergencyFocus.requestFocus()
            return
        }
        if (strict && !confirmStrict) { confirmStrict = true; return }
        val packageName = selectedPackage ?: return
        val appName = selectedName ?: selectedApp?.appName ?: existing?.appName ?: initialAppName ?: packageName
        localSaving = true
        scope.launch {
            try {
                when (val result = onSave(packageName, appName, minutes, strict, reminders, emergency, requireExisting)) {
                    is AppLimitWriteResult.Applied -> onDismiss()
                    else -> feedback = result.feedbackMessage(context)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.e("AppLimitEditor", "Unable to save app limit", exception)
                feedback = context.getString(R.string.limits_save_error)
            } finally { localSaving = false; confirmStrict = false }
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { value -> value != SheetValue.Hidden || !busy }),
        containerColor = FocusColors.Surface,
        contentColor = FocusColors.TextPrimary,
        modifier = Modifier.testTag("app_limit_editor")
    ) {
        if (selectedPackage == null) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxPickerHeight).navigationBarsPadding().imePadding(),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                item {
                    EditorHeading(stringResource(R.string.limits_choose_app), ::dismiss, !busy)
                    FocusSearchField(value = query, onValueChange = { query = it }, label = stringResource(R.string.limits_search_label),
                        placeholder = stringResource(R.string.limits_search_example), modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal))
                }
                item {
                    Column(Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                        FocusStatusMessage(feedback, isError = true, onDismiss = { feedback = null })
                        if (appsLoadError != null) {
                            Text(appsLoadError, color = FocusColors.BlockedText)
                            TextButton(onRetryApps, enabled = !loadingApps) { Text(stringResource(R.string.action_retry)) }
                        }
                        if (loadingApps) CircularProgressIndicator()
                        else Text(pluralStringResource(R.plurals.limits_apps_found, filteredApps.size, filteredApps.size),
                            color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(filteredApps, key = { it.packageName }) { app ->
                    FocusActionRow(title = app.appName,
                        description = app.packageName,
                        value = if (app.isStrictLocked) stringResource(R.string.limits_strict_status)
                            else if (app.isAlreadyLimited) stringResource(R.string.limits_edit_existing) else null,
                        onClick = {
                            if (app.isStrictLocked) feedback = context.getString(R.string.limits_strict_locked)
                            else { selectedPackage = app.packageName; selectedName = app.appName; feedback = null }
                        }, modifier = Modifier.testTag("limit_app_${app.packageName}"), enabled = !busy)
                }
                if (!loadingApps && filteredApps.isEmpty() && appsLoadError == null) item {
                    Column(Modifier.padding(FocusSpacing.screenHorizontal)) {
                        Text(stringResource(if (query.isBlank()) R.string.limits_no_apps else R.string.limits_no_results), color = FocusColors.TextSecondary)
                        TextButton(if (query.isBlank()) onRetryApps else ({ query = "" })) {
                            Text(stringResource(if (query.isBlank()) R.string.action_retry else R.string.action_clear_search))
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = FocusSpacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)) {
                EditorHeading(stringResource(if (requireExisting) R.string.limits_edit_title else R.string.limits_add_title), ::dismiss, !busy)
                FocusStatusMessage(feedback ?: if (locked) stringResource(R.string.limits_strict_locked) else null,
                    isError = true, onDismiss = { feedback = null })
                Text(selectedName ?: selectedApp?.appName ?: existing?.appName ?: initialAppName
                    ?: selectedPackage.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(selectedPackage.orEmpty(), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (!fixedApp) TextButton(onClick = { if (!busy) selectedPackage = null }, enabled = !busy) {
                    Text(stringResource(R.string.limits_change_app))
                }
                OutlinedTextField(minutesText, { minutesText = it; minutesError = null },
                    label = { Text(stringResource(R.string.limits_daily_minutes)) }, enabled = !busy && !locked,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    isError = minutesError != null,
                    supportingText = { Text(minutesError ?: stringResource(R.string.limits_minutes_hint)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(minutesFocus).testTag("limit_minutes_input"))
                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                    listOf(10, 15, 30, 45, 60, 90, 120).forEach { minutes ->
                        FilterChip(selected = minutesText.toIntOrNull() == minutes,
                            onClick = { minutesText = minutes.toString(); minutesError = null }, enabled = !busy && !locked,
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(pluralStringResource(R.plurals.limits_minutes, minutes, minutes)) })
                    }
                }
                FocusSwitchRow(stringResource(R.string.limits_strict_label), strict, { strict = it }, enabled = !busy && !locked,
                    description = stringResource(R.string.limits_strict_body), modifier = Modifier.testTag("limit_strict_switch"))
                FocusSwitchRow(stringResource(R.string.limits_reminders), reminders, { reminders = it }, enabled = !busy && !locked,
                    description = stringResource(R.string.limits_reminders_body))
                if (!strict) OutlinedTextField(emergencyText, { emergencyText = it; emergencyError = null },
                    label = { Text(stringResource(R.string.limits_emergency_label)) }, enabled = !busy && !locked,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    isError = emergencyError != null,
                    supportingText = { Text(emergencyError ?: stringResource(R.string.limits_emergency_hint)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(emergencyFocus).testTag("limit_emergency_input"))
                Button(onClick = ::save, enabled = !busy && !locked,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(bottom = FocusSpacing.lg).testTag("save_app_limit")) {
                    Text(stringResource(if (busy) R.string.action_saving else R.string.limits_save))
                }
            }
        }
    }
    BackHandler {
        if (!busy) {
            if (confirmStrict) confirmStrict = false
            else if (selectedPackage != null && !fixedApp) selectedPackage = null
            else onDismiss()
        }
    }
    if (confirmStrict) AlertDialog(
        onDismissRequest = { if (!busy) confirmStrict = false },
        title = { Text(stringResource(R.string.limits_strict_confirm_title,
            selectedName ?: selectedApp?.appName ?: existing?.appName ?: initialAppName
                ?: selectedPackage.orEmpty())) },
        text = { Text(stringResource(R.string.limits_strict_confirm_body)) },
        confirmButton = { Button(onClick = ::save, enabled = !busy) { Text(stringResource(R.string.limits_save_strict)) } },
        dismissButton = { TextButton({ confirmStrict = false }, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun EditorHeading(title: String, onClose: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = FocusSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        IconButton(onClose, enabled = enabled) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
    }
}
