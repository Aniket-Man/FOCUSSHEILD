package com.example.feature.blocks

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.core.ui.FocusBottomNavigation
import com.example.core.ui.FocusStatusMessage
import com.example.feature.applimits.ui.AppLimitEditorHost
import com.example.feature.applimits.ui.AppLimitFeedback
import com.example.feature.applimits.ui.AppLimitPermissionNotices
import com.example.feature.applimits.ui.AppLimitsViewModel
import com.example.feature.blocks.ui.NotificationBlockerSheet
import com.example.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(
    appLimitsViewModel: AppLimitsViewModel,
    settingsViewModel: SettingsViewModel,
    onTabSelected: (BottomTab) -> Unit,
    onNavigateToWebsiteBlocker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val limits by appLimitsViewModel.uiState.collectAsStateWithLifecycle()
    val limitSaving by appLimitsViewModel.isSaving.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val settingsSaving by settingsViewModel.isSaving.collectAsStateWithLifecycle()
    val settingsError by settingsViewModel.operationError.collectAsStateWithLifecycle()
    val settingsLockError by settingsViewModel.strictLockMessage.collectAsStateWithLifecycle()
    val adminActive by settingsViewModel.isDeviceAdminActive.collectAsStateWithLifecycle()
    val pendingAdmin by settingsViewModel.pendingDeviceAdminRequest.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val dock = rememberFocusDockState()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        settingsViewModel.onDeviceAdminRequestResult()
    }

    fun activateAdmin() {
        try {
            deviceAdminLauncher.launch(settingsViewModel.getDeviceAdminIntent())
        } catch (exception: Exception) {
            Log.w("Blocks", "Unable to open device-admin settings", exception)
            localError = context.getString(R.string.profile_admin_error)
        }
    }

    LaunchedEffect(pendingAdmin) {
        if (pendingAdmin) {
            settingsViewModel.onDeviceAdminRequestLaunched()
            activateAdmin()
        }
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appLimitsViewModel.refresh()
                settingsViewModel.checkAccessibilityStatus()
                settingsViewModel.checkDeviceAdminStatus()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun addLimit() {
        editingPackage = null
        showEditor = true
        appLimitsViewModel.loadInstalledApps()
    }

    val notificationAppsCount = settings.preferences.blockedNotificationPackages.size
    val widthModifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()

    Scaffold(
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("blocks_screen"),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = widthModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.nav_blocks),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = FocusColors.TextPrimary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Surface(
                        color = FocusColors.Surface,
                        contentColor = FocusColors.TextPrimary,
                        shape = FocusShapes.pill,
                        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(FocusShapes.pill)
                            .clickable { showHelp = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HelpOutline,
                                contentDescription = stringResource(R.string.blocks_help),
                                tint = FocusColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.blocks_help_pill),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    start = FocusSpacing.screenHorizontal,
                    end = FocusSpacing.screenHorizontal,
                    top = FocusSpacing.md,
                    bottom = dock.contentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)
            ) {
                item(key = "feedback_banners") {
                    Column(
                        modifier = widthModifier,
                        verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
                    ) {
                        AppLimitFeedback(appLimitsViewModel)
                        FocusStatusMessage(
                            message = settingsLockError ?: settingsError,
                            isError = true,
                            onDismiss = {
                                settingsViewModel.clearStrictLockMessage()
                                settingsViewModel.clearOperationError()
                            }
                        )
                        FocusStatusMessage(
                            message = localError,
                            isError = true,
                            onDismiss = { localError = null }
                        )
                        AppLimitPermissionNotices(appLimitsViewModel)
                    }
                }

                // ── Section 1: App Limits ──
                item(key = "app_limits_header") {
                    BlocksSectionHeader(
                        title = stringResource(R.string.blocks_app_limits_section),
                        modifier = widthModifier.padding(top = FocusSpacing.xs),
                        trailing = {
                            Row(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(FocusShapes.pill)
                                    .clickable(enabled = !limitSaving, onClick = ::addLimit)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("blocks_add_app_btn"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = FocusColors.EmeraldText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.blocks_add_app_short),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = FocusColors.EmeraldText
                                )
                            }
                        }
                    )
                }

                if (limits.isLoading) {
                    item(key = "app_limits_loading") {
                        Row(
                            modifier = widthModifier.padding(vertical = FocusSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.limits_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = FocusColors.TextSecondary
                            )
                        }
                    }
                } else if (limits.loadError != null) {
                    item(key = "app_limits_error") {
                        Column(
                            modifier = widthModifier,
                            verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)
                        ) {
                            Text(limits.loadError.orEmpty(), color = FocusColors.BlockedText)
                            TextButton(onClick = appLimitsViewModel::refresh) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                } else if (limits.items.isEmpty()) {
                    item(key = "app_limits_empty") {
                        DashedEmptyAppLimitCard(
                            onAdd = ::addLimit,
                            enabled = !limitSaving,
                            modifier = widthModifier
                        )
                    }
                } else {
                    items(limits.items, key = { "limit_${it.packageName}" }) { item ->
                        DashedAppLimitCard(
                            item = item,
                            viewModel = appLimitsViewModel,
                            busy = limitSaving,
                            onEdit = {
                                editingPackage = item.packageName
                                showEditor = true
                            },
                            modifier = widthModifier
                        )
                    }
                }

                // ── Section 2: Block Shorts ──
                item(key = "block_shorts_section") {
                    Column(
                        modifier = widthModifier.padding(top = FocusSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BlocksSectionHeader(title = stringResource(R.string.blocks_short_video_section))

                        val ytChecked = settings.preferences.isYouTubeShortsBlockingEnabled
                        BlockTogglePillCard(
                            title = stringResource(R.string.blocks_youtube_shorts),
                            subtitle = stringResource(R.string.blocks_youtube_shorts_subtitle),
                            checked = ytChecked,
                            enabled = !settingsSaving,
                            onToggle = settingsViewModel::updateYouTubeShortsBlocking,
                            leadingBadge = { YouTubeShortsBadge() },
                            modifier = Modifier.testTag("blocks_toggle_yt_shorts")
                        )

                        val igChecked = settings.preferences.isInstagramReelsBlockingEnabled
                        BlockTogglePillCard(
                            title = stringResource(R.string.blocks_instagram_reels),
                            subtitle = stringResource(R.string.blocks_instagram_reels_subtitle),
                            checked = igChecked,
                            enabled = !settingsSaving,
                            onToggle = settingsViewModel::updateInstagramReelsBlocking,
                            leadingBadge = { InstagramReelsBadge() },
                            modifier = Modifier.testTag("blocks_toggle_ig_reels")
                        )

                        val fbChecked = settings.preferences.isFacebookReelsBlockingEnabled
                        BlockTogglePillCard(
                            title = stringResource(R.string.blocks_facebook_reels),
                            subtitle = stringResource(R.string.blocks_facebook_reels_subtitle),
                            checked = fbChecked,
                            enabled = !settingsSaving,
                            onToggle = settingsViewModel::updateFacebookReelsBlocking,
                            leadingBadge = { FacebookReelsBadge() },
                            modifier = Modifier.testTag("blocks_toggle_fb_reels")
                        )
                    }
                }

                // ── Section 3: Other blocks ──
                item(key = "other_blocks_section") {
                    Column(
                        modifier = widthModifier.padding(top = FocusSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BlocksSectionHeader(title = stringResource(R.string.blocks_other_section))

                        BlockNavigationPillCard(
                            title = stringResource(R.string.blocks_websites),
                            subtitle = stringResource(R.string.blocks_websites_body),
                            pillLabel = stringResource(R.string.blocks_pill_manage),
                            isActive = true,
                            onClick = onNavigateToWebsiteBlocker,
                            leadingBadge = { WebsiteGlobeBadge() }
                        )

                        BlockNavigationPillCard(
                            title = stringResource(R.string.blocks_notifications),
                            subtitle = if (notificationAppsCount > 0) {
                                pluralStringResource(
                                    R.plurals.blocks_notification_apps,
                                    notificationAppsCount,
                                    notificationAppsCount
                                )
                            } else {
                                stringResource(R.string.blocks_notifications_body)
                            },
                            pillLabel = stringResource(
                                if (notificationAppsCount > 0) R.string.blocks_pill_manage else R.string.blocks_pill_add
                            ),
                            isActive = notificationAppsCount > 0,
                            onClick = { showNotifications = true },
                            leadingBadge = { SilenceNotificationsBadge(isActive = notificationAppsCount > 0) }
                        )
                    }
                }

                // ── Section 4: Strict mode protections ──
                item(key = "strict_protections_section") {
                    Column(
                        modifier = widthModifier.padding(top = FocusSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BlocksSectionHeader(title = stringResource(R.string.blocks_strict_protections_section))

                        val uninstallEnabled = settings.preferences.isBlockUninstallEnabled
                        val uninstallDescription = when {
                            uninstallEnabled && adminActive -> stringResource(R.string.profile_uninstall_active)
                            uninstallEnabled -> stringResource(R.string.profile_uninstall_pending)
                            else -> stringResource(R.string.blocks_uninstall_off_body)
                        }
                        StrictProtectionCard(
                            title = stringResource(R.string.blocks_uninstall_title),
                            description = uninstallDescription,
                            checked = uninstallEnabled,
                            enabled = !settingsSaving,
                            onCheckedChange = settingsViewModel::updateBlockUninstall,
                            actionLabel = if (uninstallEnabled && !adminActive) {
                                stringResource(R.string.profile_activate_admin)
                            } else {
                                null
                            },
                            onActionClick = if (uninstallEnabled && !adminActive) ::activateAdmin else null,
                            modifier = Modifier.testTag("blocks_uninstall_switch")
                        )

                        StrictProtectionCard(
                            title = stringResource(R.string.blocks_split_screen_title),
                            description = stringResource(R.string.blocks_split_screen_body),
                            checked = settings.preferences.isBlockSplitScreenEnabled,
                            enabled = !settingsSaving,
                            onCheckedChange = settingsViewModel::updateBlockSplitScreen,
                            modifier = Modifier.testTag("blocks_split_screen_switch")
                        )

                        StrictProtectionCard(
                            title = stringResource(R.string.blocks_floating_window_title),
                            description = stringResource(R.string.blocks_floating_window_body),
                            checked = settings.preferences.isBlockFloatingWindowEnabled,
                            enabled = !settingsSaving,
                            onCheckedChange = settingsViewModel::updateBlockFloatingWindow,
                            modifier = Modifier.testTag("blocks_floating_window_switch")
                        )

                        Text(
                            text = stringResource(R.string.strict_android_body),
                            color = FocusColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = FocusSpacing.xs)
                        )
                    }
                }
            }

            FocusBottomNavigation(
                selectedTab = BottomTab.BLOCKS,
                onTabSelected = onTabSelected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged(dock::onSizeChanged)
            )
        }
    }

    if (showEditor) {
        AppLimitEditorHost(
            viewModel = appLimitsViewModel,
            editingPackage = editingPackage,
            onDismiss = { showEditor = false }
        )
    }

    if (showNotifications) {
        NotificationBlockerSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { showNotifications = false },
            onRequestPermission = {
                try {
                    FocusPermissionManager.openNotificationListenerSettings(context)
                } catch (exception: Exception) {
                    Log.w("Blocks", "Unable to open notification access settings", exception)
                    localError = context.getString(R.string.limits_permission_open_error)
                }
            }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.blocks_help)) },
            text = { Text(stringResource(R.string.blocks_help_body)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }
}
