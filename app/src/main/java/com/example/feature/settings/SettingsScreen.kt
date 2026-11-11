package com.example.feature.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.cloud.sync.SyncCycleOutcome
import com.example.core.accessibility.AccessibilityFeaturePromptInfo
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.accessibility.AccessibilityPermissionRequiredDialog
import com.example.core.accessibility.AccessibilityTroubleshootingDialog
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.permission.FocusPermissionManager
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusActionRow
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow
import com.example.feature.timer.FloatingOverlayPermissionDialog
import com.example.feature.widgets.FocusShieldCompactWidgetReceiver
import com.example.feature.widgets.FocusShieldDetailedWidgetReceiver
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToBlockedApps: () -> Unit,
    onNavigateToAppLimits: () -> Unit = {},
    onNavigateToStrictMode: () -> Unit = {},
    onNavigateToStudyChannels: () -> Unit = {},
    onNavigateToWebsiteBlocker: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToDigitalWellbeing: () -> Unit = {},
    onTabSelected: (BottomTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strictMessage by viewModel.strictLockMessage.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val saving by viewModel.isSaving.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var prompt by remember { mutableStateOf<AccessibilityFeaturePromptInfo?>(null) }
    var showOverlayPermission by rememberSaveable { mutableStateOf(false) }
    var showTroubleshooting by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var widgetFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    val prefs = state.preferences

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.checkAccessibilityStatus() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun changeProtected(enabled: Boolean, title: Int, description: Int, featureKey: String?, update: (Boolean) -> Unit) {
        if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
            prompt = AccessibilityFeaturePromptInfo(context.getString(title), context.getString(description),
                featureKey = featureKey, onGranted = { update(true) })
        } else update(enabled)
    }

    Scaffold(
        topBar = { FocusScreenHeader(stringResource(R.string.settings_title), onNavigateBack,
            Modifier.statusBarsPadding().testTag("settings_back_button")) },
        containerColor = FocusColors.Background,
        modifier = modifier.navigationBarsPadding().testTag("settings_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.xl)
        ) {
            item {
                FocusStatusMessage(strictMessage ?: operationError, isError = true,
                    onDismiss = { viewModel.clearStrictLockMessage(); viewModel.clearOperationError() })
                FocusStatusMessage(widgetFeedback, onDismiss = { widgetFeedback = null })
            }
            item {
                SettingsGroup(stringResource(R.string.settings_protection_section)) {
                    FocusActionRow(stringResource(R.string.settings_wellbeing), onNavigateToDigitalWellbeing,
                        description = stringResource(R.string.settings_wellbeing_body), modifier = Modifier.testTag("settings_digital_wellbeing_row"))
                    FocusActionRow(stringResource(R.string.settings_app_limits), onNavigateToAppLimits,
                        description = stringResource(R.string.settings_app_limits_body), modifier = Modifier.testTag("settings_app_limits_row"))
                    FocusActionRow(stringResource(R.string.settings_blocked_apps), onNavigateToBlockedApps,
                        description = pluralStringResource(R.plurals.settings_blocked_apps_count, state.blockedAppsCount, state.blockedAppsCount),
                        modifier = Modifier.testTag("settings_blocked_apps_row"))
                    FocusActionRow(stringResource(R.string.settings_strict_mode), onNavigateToStrictMode,
                        description = stringResource(R.string.settings_strict_mode_body), modifier = Modifier.testTag("settings_strict_mode_row"))
                    FocusActionRow(stringResource(R.string.settings_websites), onNavigateToWebsiteBlocker,
                        description = stringResource(R.string.settings_websites_body), modifier = Modifier.testTag("settings_website_blocker_row"))
                    FocusActionRow(stringResource(R.string.settings_channels), onNavigateToStudyChannels,
                        description = stringResource(R.string.settings_channels_body), modifier = Modifier.testTag("settings_study_channels_row"))
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_permissions_section)) {
                    // Android owns this setting. A button is honest; an app-owned switch was not.
                    FocusActionRow(stringResource(R.string.settings_accessibility), { AccessibilityHelper.openAccessibilitySettings(context) },
                        value = stringResource(if (state.isAccessibilityEnabled) R.string.settings_service_enabled else R.string.settings_service_disabled),
                        description = stringResource(R.string.settings_accessibility_body), modifier = Modifier.testTag("toggle_accessibility_service"))
                    TextButton({ showTroubleshooting = true }, modifier = Modifier.padding(horizontal = FocusSpacing.md).testTag("btn_accessibility_troubleshoot")) {
                        Text(stringResource(R.string.settings_accessibility_help))
                    }
                    FocusActionRow(stringResource(R.string.settings_background), { FocusPermissionManager.requestIgnoreBatteryOptimization(context) },
                        value = stringResource(if (state.isBatteryOptimizationIgnored) R.string.settings_background_allowed else R.string.settings_background_restricted),
                        description = stringResource(R.string.settings_background_body), modifier = Modifier.testTag("settings_battery_optimization_row"))
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_short_form_section)) {
                    FocusSwitchRow(stringResource(R.string.settings_short_form_always), prefs.isShortsReelsAlwaysBlocked,
                        { changeProtected(it, R.string.settings_short_form_always, R.string.settings_short_form_permission, AccessibilityHelper.FEATURE_SHORTS_ALWAYS, viewModel::updateShortsReelsAlwaysBlocked) },
                        description = stringResource(R.string.settings_short_form_always_body), enabled = !saving, modifier = Modifier.testTag("toggle_always_block_shorts"))
                    FocusSwitchRow(stringResource(R.string.settings_youtube_shorts), prefs.isYouTubeShortsBlockingEnabled,
                        { changeProtected(it, R.string.settings_youtube_shorts, R.string.settings_youtube_shorts_permission, AccessibilityHelper.FEATURE_YT_SHORTS, viewModel::updateYouTubeShortsBlocking) },
                        description = stringResource(R.string.settings_youtube_shorts_body), enabled = !saving, modifier = Modifier.testTag("toggle_block_yt_shorts"))
                    FocusSwitchRow(stringResource(R.string.settings_instagram_reels), prefs.isInstagramReelsBlockingEnabled,
                        { changeProtected(it, R.string.settings_instagram_reels, R.string.settings_instagram_permission, AccessibilityHelper.FEATURE_IG_REELS, viewModel::updateInstagramReelsBlocking) },
                        description = stringResource(R.string.settings_instagram_body), enabled = !saving, modifier = Modifier.testTag("toggle_block_ig_reels"))
                    FocusSwitchRow(stringResource(R.string.settings_facebook_reels), prefs.isFacebookReelsBlockingEnabled,
                        { changeProtected(it, R.string.settings_facebook_reels, R.string.settings_facebook_permission, AccessibilityHelper.FEATURE_FB_REELS, viewModel::updateFacebookReelsBlocking) },
                        description = stringResource(R.string.settings_facebook_body), enabled = !saving, modifier = Modifier.testTag("toggle_block_fb_reels"))
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_study_section)) {
                    Column(Modifier.padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                        Text(stringResource(R.string.settings_timer_default), color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(pluralStringResource(R.plurals.settings_timer_minutes, prefs.defaultTimerMinutes, prefs.defaultTimerMinutes),
                            color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                        Text(stringResource(R.string.settings_pomodoro_defaults), color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_pomodoro_summary, prefs.pomodoroFocusMinutes, prefs.pomodoroShortBreakMinutes, prefs.pomodoroCycles),
                            color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_widgets_section)) {
                    FocusSwitchRow(stringResource(R.string.settings_floating_timer), prefs.isFloatingTimerOverlayEnabled && state.isOverlayPermissionGranted,
                        { if (it && !state.isOverlayPermissionGranted) showOverlayPermission = true else viewModel.updateFloatingTimerOverlay(it) },
                        description = stringResource(if (state.isOverlayPermissionGranted) R.string.settings_floating_timer_body else R.string.settings_floating_timer_permission),
                        enabled = !saving, modifier = Modifier.testTag("toggle_floating_timer_overlay"))
                    if (!state.isOverlayPermissionGranted) TextButton({ showOverlayPermission = true }, Modifier.padding(horizontal = FocusSpacing.md)) {
                        Text(stringResource(R.string.settings_grant_overlay))
                    }
                    Column(Modifier.padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                        Text(stringResource(R.string.settings_pin_widgets), color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_widgets_body), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton({ widgetFeedback = requestPinFocusWidget(context, FocusShieldCompactWidgetReceiver::class.java) },
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pin_compact_widget_button")) { Text(stringResource(R.string.settings_pin_compact)) }
                        OutlinedButton({ widgetFeedback = requestPinFocusWidget(context, FocusShieldDetailedWidgetReceiver::class.java) },
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pin_detailed_widget_button")) { Text(stringResource(R.string.settings_pin_detailed)) }
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_account_section)) {
                    FocusActionRow(stringResource(R.string.account_title), onNavigateToAccount,
                        value = state.signedInEmail ?: stringResource(R.string.settings_signed_out),
                        description = stringResource(R.string.settings_account_body), modifier = Modifier.testTag("settings_sign_in_row"))
                    if (state.signedInEmail != null) {
                        FocusSwitchRow(stringResource(R.string.settings_auto_save), prefs.isAutoSaveToCloudEnabled, viewModel::updateAutoSaveToCloud,
                            description = stringResource(R.string.settings_auto_save_body), enabled = !saving, modifier = Modifier.testTag("toggle_auto_save_cloud"))
                        Column(Modifier.padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                            if (prefs.isAutoSaveToCloudEnabled) {
                                Text(stringResource(R.string.settings_backup_every), color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                                FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                                    listOf(15, 30, 60, 300).forEach { seconds ->
                                        FilterChip(selected = prefs.autoSaveIntervalSeconds == seconds, onClick = { viewModel.updateAutoSaveIntervalSeconds(seconds) },
                                            enabled = !saving && !state.isSyncRunning, modifier = Modifier.heightIn(min = 48.dp).testTag("settings_autosave_interval_$seconds"),
                                            label = { Text(if (seconds < 60) pluralStringResource(R.plurals.settings_interval_seconds, seconds, seconds)
                                                else pluralStringResource(R.plurals.settings_interval_minutes, seconds / 60, seconds / 60)) })
                                    }
                                }
                            }
                            Text(stringResource(R.string.settings_auto_save_hint), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            SyncStatusLine(state, !saving, viewModel::syncNow)
                            OutlinedButton({ confirmSignOut = true }, enabled = !saving && !state.isSyncRunning,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_sign_out_button")) { Text(stringResource(R.string.account_sign_out)) }
                        }
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.settings_about_section)) {
                    Column(Modifier.padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                        Text(stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME), color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.settings_about_body), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    prompt?.let { info -> AccessibilityPermissionRequiredDialog(info.title, info.description,
        featureKey = info.featureKey, onDismissRequest = { prompt = null }, onPermissionGranted = { info.onGranted(); prompt = null }) }
    if (showOverlayPermission) FloatingOverlayPermissionDialog(onDismiss = { showOverlayPermission = false }, onPermissionGranted = {
        showOverlayPermission = false; viewModel.checkAccessibilityStatus(); viewModel.updateFloatingTimerOverlay(true)
    })
    if (showTroubleshooting) AccessibilityTroubleshootingDialog(onDismissRequest = { showTroubleshooting = false })
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text(stringResource(R.string.account_sign_out_title)) },
        text = { Text(stringResource(R.string.account_sign_out_hint)) },
        confirmButton = { TextButton({ confirmSignOut = false; viewModel.signOut() }, enabled = !saving && !state.isSyncRunning) { Text(stringResource(R.string.account_sign_out)) } },
        dismissButton = { TextButton({ confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
        Text(title, color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Surface(color = FocusColors.Surface, shape = FocusShapes.card, border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun SyncStatusLine(state: SettingsUiState, enabled: Boolean, onSync: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lastSync = remember(state.lastSyncAtMillis, configuration) {
        if (state.lastSyncAtMillis > 0) {
            val date = Date(state.lastSyncAtMillis)
            android.text.format.DateFormat.getDateFormat(context).format(date) + " · " + android.text.format.DateFormat.getTimeFormat(context).format(date)
        } else null
    }
    val status = when {
        state.isSyncRunning -> stringResource(R.string.account_syncing)
        state.lastSyncOutcome is SyncCycleOutcome.Failed -> stringResource(R.string.account_sync_error)
        state.lastSyncOutcome == SyncCycleOutcome.Offline -> stringResource(R.string.account_sync_offline)
        state.pendingSyncCount > 0 -> pluralStringResource(R.plurals.settings_pending_changes, state.pendingSyncCount, state.pendingSyncCount)
        lastSync == null -> stringResource(R.string.settings_not_backed_up)
        else -> stringResource(R.string.settings_last_sync, lastSync)
    }
    Text(status, color = if (state.lastSyncOutcome is SyncCycleOutcome.Failed) FocusColors.BlockedText else FocusColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    TextButton(onSync, enabled = enabled && !state.isSyncRunning, modifier = Modifier.testTag("settings_sync_now_button")) {
        Text(stringResource(R.string.account_sync_now))
    }
}

private fun requestPinFocusWidget(context: Context, receiverClass: Class<*>): String {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, receiverClass), null, null)) {
                return context.getString(R.string.settings_widget_request_sent)
            }
        }
    } catch (exception: Exception) { Log.w("SettingsScreen", "Unable to request widget pin", exception) }
    return context.getString(R.string.settings_widget_manual_hint)
}
