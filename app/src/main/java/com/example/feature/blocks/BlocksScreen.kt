package com.example.feature.blocks

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.design.rememberFocusDockState
import com.example.core.permission.FocusPermissionManager
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusActionRow
import com.example.core.ui.FocusBottomNavigation
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow
import com.example.feature.applimits.ui.AppLimitCard
import com.example.feature.applimits.ui.AppLimitEditorHost
import com.example.feature.applimits.ui.AppLimitEmptyState
import com.example.feature.applimits.ui.AppLimitFeedback
import com.example.feature.applimits.ui.AppLimitPermissionNotices
import com.example.feature.applimits.ui.AppLimitsViewModel
import com.example.feature.blocks.ui.NotificationBlockerSheet
import com.example.feature.settings.ProtectionSettingsSection
import com.example.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(appLimitsViewModel: AppLimitsViewModel, settingsViewModel: SettingsViewModel,
    onTabSelected: (BottomTab) -> Unit, onNavigateToWebsiteBlocker: () -> Unit = {}, modifier: Modifier = Modifier) {
    val limits by appLimitsViewModel.uiState.collectAsStateWithLifecycle()
    val limitSaving by appLimitsViewModel.isSaving.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val settingsSaving by settingsViewModel.isSaving.collectAsStateWithLifecycle()
    val settingsError by settingsViewModel.operationError.collectAsStateWithLifecycle()
    val settingsLockError by settingsViewModel.strictLockMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val dock = rememberFocusDockState()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) appLimitsViewModel.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun addLimit() { editingPackage = null; showEditor = true; appLimitsViewModel.loadInstalledApps() }

    Scaffold(containerColor = FocusColors.Background, modifier = modifier.testTag("blocks_screen"), topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = FocusSpacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
            Text(stringResource(R.string.nav_blocks), style = MaterialTheme.typography.headlineLarge, color = FocusColors.TextPrimary,
                modifier = Modifier.weight(1f).semantics { heading() })
            IconButton({ showHelp = true }) { Icon(Icons.Rounded.HelpOutline, stringResource(R.string.blocks_help), tint = FocusColors.TextSecondary) }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = FocusSpacing.screenHorizontal, end = FocusSpacing.screenHorizontal,
                    top = FocusSpacing.md, bottom = dock.contentPadding),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.xl)) {
                item {
                    AppLimitFeedback(appLimitsViewModel)
                    FocusStatusMessage(settingsLockError ?: settingsError, isError = true,
                        onDismiss = { settingsViewModel.clearStrictLockMessage(); settingsViewModel.clearOperationError() })
                    FocusStatusMessage(localError, isError = true, onDismiss = { localError = null })
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                        BlocksHeading(stringResource(R.string.limits_title))
                        Text(stringResource(R.string.limits_local_body), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        if (limits.items.isNotEmpty()) Button(::addLimit, enabled = !limitSaving,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("blocks_add_app_btn")) { Text(stringResource(R.string.limits_add)) }
                        AppLimitPermissionNotices(appLimitsViewModel)
                        if (limits.isLoading) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.limits_loading), color = FocusColors.TextSecondary)
                        }
                        if (limits.loadError != null) {
                            Text(limits.loadError.orEmpty(), color = FocusColors.BlockedText)
                            TextButton(appLimitsViewModel::refresh) { Text(stringResource(R.string.action_retry)) }
                        } else if (!limits.isLoading && limits.items.isEmpty()) AppLimitEmptyState(::addLimit, !limitSaving)
                    }
                }
                items(limits.items, key = { it.packageName }) { item ->
                    AppLimitCard(item, appLimitsViewModel, onEdit = { editingPackage = item.packageName; showEditor = true })
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                        BlocksHeading(stringResource(R.string.blocks_short_video_section))
                        Surface(color = FocusColors.Surface, shape = FocusShapes.card) {
                            Column {
                                FocusSwitchRow(stringResource(R.string.blocks_youtube_shorts), settings.preferences.isYouTubeShortsBlockingEnabled,
                                    settingsViewModel::updateYouTubeShortsBlocking, enabled = !settingsSaving,
                                    description = stringResource(R.string.blocks_short_video_body), modifier = Modifier.testTag("blocks_toggle_yt_shorts"))
                                FocusSwitchRow(stringResource(R.string.blocks_instagram_reels), settings.preferences.isInstagramReelsBlockingEnabled,
                                    settingsViewModel::updateInstagramReelsBlocking, enabled = !settingsSaving,
                                    description = stringResource(R.string.blocks_short_video_body), modifier = Modifier.testTag("blocks_toggle_ig_reels"))
                                FocusSwitchRow(stringResource(R.string.blocks_facebook_reels), settings.preferences.isFacebookReelsBlockingEnabled,
                                    settingsViewModel::updateFacebookReelsBlocking, enabled = !settingsSaving,
                                    description = stringResource(R.string.blocks_short_video_body), modifier = Modifier.testTag("blocks_toggle_fb_reels"))
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                        BlocksHeading(stringResource(R.string.blocks_other_section))
                        Surface(color = FocusColors.Surface, shape = FocusShapes.card) {
                            Column {
                                FocusActionRow(stringResource(R.string.blocks_websites), onNavigateToWebsiteBlocker,
                                    description = stringResource(R.string.blocks_websites_body))
                                FocusActionRow(stringResource(R.string.blocks_notifications), { showNotifications = true },
                                    value = pluralStringResource(R.plurals.blocks_notification_apps,
                                        settings.preferences.blockedNotificationPackages.size, settings.preferences.blockedNotificationPackages.size),
                                    description = stringResource(R.string.blocks_notifications_body))
                            }
                        }
                    }
                }
                item {
                    BlocksHeading(stringResource(R.string.strict_other_section))
                    ProtectionSettingsSection(settingsViewModel, Modifier.padding(top = FocusSpacing.md))
                    Text(stringResource(R.string.strict_android_body), color = FocusColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = FocusSpacing.md))
                }
            }
            FocusBottomNavigation(BottomTab.BLOCKS, onTabSelected,
                Modifier.align(Alignment.BottomCenter).onSizeChanged(dock::onSizeChanged))
        }
    }
    if (showEditor) AppLimitEditorHost(appLimitsViewModel, editingPackage, onDismiss = { showEditor = false })
    if (showNotifications) NotificationBlockerSheet(rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismiss = { showNotifications = false }, onRequestPermission = {
            try { FocusPermissionManager.openNotificationListenerSettings(context) }
            catch (exception: Exception) { Log.w("Blocks", "Unable to open notification access settings", exception)
                localError = context.getString(R.string.limits_permission_open_error) }
        })
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false },
        title = { Text(stringResource(R.string.blocks_help)) },
        text = { Text(stringResource(R.string.blocks_help_body)) },
        confirmButton = { TextButton({ showHelp = false }) { Text(stringResource(R.string.action_done)) } })
}

@Composable
private fun BlocksHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = FocusColors.TextPrimary, modifier = Modifier.semantics { heading() })
}
