package com.example.feature.blocks

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.design.rememberFocusDockState
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusStatusMessage
import com.example.feature.applimits.ui.AppLimitEditorHost
import com.example.feature.applimits.ui.AppLimitEmptyState
import com.example.feature.applimits.ui.AppLimitFeedback
import com.example.feature.applimits.ui.AppLimitPermissionNotices
import com.example.feature.applimits.ui.AppLimitUiItem
import com.example.feature.applimits.ui.AppLimitsViewModel
import com.example.feature.settings.SettingsViewModel

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
    val pendingAdmin by settingsViewModel.pendingDeviceAdminRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val dock = rememberFocusDockState()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        settingsViewModel.onDeviceAdminRequestResult()
    }
    LaunchedEffect(pendingAdmin) {
        if (pendingAdmin) {
            settingsViewModel.onDeviceAdminRequestLaunched()
            runCatching { adminLauncher.launch(settingsViewModel.getDeviceAdminIntent()) }
                .onFailure {
                    Log.w("Blocks", "Unable to open device-admin settings", it)
                    localError = context.getString(R.string.profile_admin_error)
                }
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

    Scaffold(
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("blocks_screen"),
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.nav_blocks),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = FocusColors.TextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Surface(
                    color = FocusColors.SurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable(role = Role.Button) { showHelp = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.HelpOutline, null, tint = FocusColors.TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.blocks_help_action), color = FocusColors.TextSecondary)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 22.dp,
                    bottom = dock.contentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AppLimitFeedback(appLimitsViewModel)
                    FocusStatusMessage(
                        settingsLockError ?: settingsError,
                        isError = true,
                        onDismiss = {
                            settingsViewModel.clearStrictLockMessage()
                            settingsViewModel.clearOperationError()
                        }
                    )
                    FocusStatusMessage(localError, isError = true, onDismiss = { localError = null })
                }

                item {
                    SectionTitleWithAction(
                        title = stringResource(R.string.limits_title),
                        action = stringResource(R.string.blocks_add_app),
                        onAction = ::addLimit,
                        enabled = !limitSaving
                    )
                }
                item { AppLimitPermissionNotices(appLimitsViewModel) }
                if (limits.isLoading) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.limits_loading), color = FocusColors.TextSecondary)
                        }
                    }
                } else if (limits.loadError != null) {
                    item {
                        Column {
                            Text(limits.loadError.orEmpty(), color = FocusColors.BlockedText)
                            TextButton(onClick = appLimitsViewModel::refresh) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                } else if (limits.items.isEmpty()) {
                    item { AppLimitEmptyState(::addLimit, !limitSaving) }
                }
                items(limits.items, key = { it.packageName }) { item ->
                    ReferenceAppLimitRow(
                        item = item,
                        viewModel = appLimitsViewModel,
                        onEdit = {
                            editingPackage = item.packageName
                            showEditor = true
                        }
                    )
                }

                item { SectionHeading(stringResource(R.string.blocks_short_section), pro = true) }
                item {
                    AddBlockRow(
                        icon = Icons.Rounded.SmartDisplay,
                        iconTint = Color(0xFFFF0033),
                        title = stringResource(R.string.blocks_youtube_shorts_short),
                        added = settings.preferences.isYouTubeShortsBlockingEnabled,
                        enabled = !settingsSaving,
                        onToggle = settingsViewModel::updateYouTubeShortsBlocking
                    )
                }
                item {
                    AddBlockRow(
                        icon = Icons.Rounded.Movie,
                        iconTint = Color(0xFFFF4D67),
                        title = stringResource(R.string.blocks_instagram_reels_short),
                        added = settings.preferences.isInstagramReelsBlockingEnabled,
                        enabled = !settingsSaving,
                        onToggle = settingsViewModel::updateInstagramReelsBlocking
                    )
                }

                item { SectionHeading(stringResource(R.string.blocks_other_short), pro = true) }
                item {
                    AddBlockRow(
                        icon = Icons.Rounded.Language,
                        iconTint = FocusColors.TextSecondary,
                        title = stringResource(R.string.blocks_websites_short),
                        added = false,
                        enabled = true,
                        onToggle = { onNavigateToWebsiteBlocker() },
                        actionOnly = true
                    )
                }

                item { SectionHeading(stringResource(R.string.blocks_strict_protections), pro = true) }
                item {
                    Surface(
                        color = FocusColors.SurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            ProtectionRow(
                                title = stringResource(R.string.blocks_uninstall_title),
                                body = stringResource(R.string.blocks_uninstall_body),
                                checked = settings.preferences.isBlockUninstallEnabled,
                                enabled = !settingsSaving,
                                onCheckedChange = settingsViewModel::updateBlockUninstall
                            )
                            ProtectionRow(
                                title = stringResource(R.string.blocks_split_title),
                                body = stringResource(R.string.blocks_split_body),
                                checked = settings.preferences.isBlockSplitScreenEnabled,
                                enabled = !settingsSaving,
                                onCheckedChange = settingsViewModel::updateBlockSplitScreen
                            )
                            ProtectionRow(
                                title = stringResource(R.string.blocks_floating_title),
                                body = stringResource(R.string.blocks_floating_body),
                                checked = settings.preferences.isBlockFloatingWindowEnabled,
                                enabled = !settingsSaving,
                                onCheckedChange = settingsViewModel::updateBlockFloatingWindow
                            )
                        }
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

@Composable
private fun SectionTitleWithAction(
    title: String,
    action: String,
    onAction: () -> Unit,
    enabled: Boolean
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = FocusColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        TextButton(onClick = onAction, enabled = enabled) {
            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
            Text(action)
        }
    }
}

@Composable
private fun SectionHeading(text: String, pro: Boolean) {
    Row(
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = FocusColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() }
        )
        if (pro) {
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.pro_label),
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(FocusColors.ProGold)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ReferenceAppLimitRow(
    item: AppLimitUiItem,
    viewModel: AppLimitsViewModel,
    onEdit: () -> Unit
) {
    val busy by viewModel.isSaving.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmAllowToday by rememberSaveable { mutableStateOf(false) }
    val icon = remember(item.appIcon) {
        runCatching { item.appIcon?.toBitmap(48, 48)?.asImageBitmap() }.getOrNull()
    }
    Surface(
        color = FocusColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onEdit)
            .testTag("app_limit_${item.packageName}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(icon, null, Modifier.size(42.dp).clip(CircleShape))
            } else {
                Icon(Icons.Rounded.Apps, null, tint = FocusColors.Primary, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.appName,
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.blocks_limit_value, formatLimit(item.dailyLimitMinutes)),
                    color = FocusColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
            if (!item.isStrictOverride) {
                Box {
                    androidx.compose.material3.IconButton(
                        onClick = { menuExpanded = true },
                        enabled = !busy,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.limits_more_app, item.appName)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.limits_edit_title)) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        if (item.isEnabled && !item.isBypassedToday) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.limits_allow_action)) },
                                onClick = { menuExpanded = false; confirmAllowToday = true }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.limits_delete_action)) },
                            onClick = { menuExpanded = false; confirmDelete = true }
                        )
                    }
                }
            }
            Button(
                onClick = { viewModel.toggleLimit(item.packageName, !item.isEnabled) },
                enabled = !busy && !item.isStrictOverride,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.SurfaceVariant,
                    contentColor = FocusColors.TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (!item.isEnabled) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(stringResource(if (item.isEnabled) R.string.blocks_added else R.string.channel_add_action))
            }
        }
    }
    if (confirmDelete) {
        FocusDestructiveDialog(
            title = stringResource(R.string.limits_delete_app, item.appName),
            message = stringResource(R.string.limits_delete_body),
            confirmLabel = stringResource(R.string.limits_delete_action),
            onConfirm = { viewModel.deleteLimit(item.packageName) },
            onDismiss = { confirmDelete = false }
        )
    }
    if (confirmAllowToday) {
        AlertDialog(
            onDismissRequest = { confirmAllowToday = false },
            title = { Text(stringResource(R.string.limits_allow_title, item.appName)) },
            text = { Text(stringResource(R.string.limits_allow_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAllowToday = false
                        viewModel.unlockForToday(item.packageName)
                    },
                    enabled = !busy
                ) {
                    Text(stringResource(R.string.limits_allow_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAllowToday = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AddBlockRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    added: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    actionOnly: Boolean = false
) {
    Surface(
        color = FocusColors.SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                color = FocusColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { if (actionOnly) onToggle(true) else onToggle(!added) },
                enabled = enabled,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.SurfaceSubtle,
                    contentColor = FocusColors.TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (!added || actionOnly) Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
                if (!added || actionOnly) Spacer(Modifier.width(4.dp))
                Text(stringResource(if (added && !actionOnly) R.string.blocks_added else R.string.channel_add_action))
            }
        }
    }
}

@Composable
private fun ProtectionRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Text(body, color = FocusColors.TextSecondary, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            colors = FocusColors.SwitchColors,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

private fun formatLimit(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}
