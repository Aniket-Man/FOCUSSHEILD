package com.example.feature.blocks.ui

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.R
import com.example.core.design.FocusColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.design.LocalFocusColors
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusSearchField
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusStatusMessage
import com.example.feature.notificationblocker.domain.NotificationBlockMode
import com.example.feature.notificationblocker.domain.SilencedNotificationRecord
import com.example.feature.notificationblocker.ui.InstalledAppItem
import com.example.feature.notificationblocker.ui.NotificationBlockerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean dashed border around rounded rectangle matching the YouTube Channel popup specification.
 */
private fun Modifier.sheetDashedBorder(
    strokeWidth: Dp = 1.2.dp,
    color: Color,
    cornerRadius: Dp = 16.dp,
    dashLength: Dp = 7.dp,
    gapLength: Dp = 5.dp
): Modifier = this.drawBehind {
    val widthPx = strokeWidth.toPx()
    val radiusPx = cornerRadius.toPx()
    val dashPx = dashLength.toPx()
    val gapPx = gapLength.toPx()

    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx), 0f)
    val stroke = Stroke(width = widthPx, pathEffect = pathEffect)
    drawRoundRect(
        color = color,
        topLeft = Offset(widthPx / 2f, widthPx / 2f),
        size = Size(size.width - widthPx, size.height - widthPx),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = stroke
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBlockerSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationBlockerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strictLockMessage by viewModel.strictLockMessage.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isDark = LocalFocusColors.current.isDark
    val sheetBg = FocusColors.OverlayBackground
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary
    val dragHandleColor = FocusColors.OverlayBorder
    val doneBtnBg = FocusColors.OverlayTextPrimary
    val doneBtnText = FocusColors.OverlayBackground

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredApps = remember(uiState.installedApps, searchQuery, uiState.blockedPackages, uiState.alwaysBlockedPackages) {
        uiState.installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }.sortedWith(
            compareByDescending<InstalledAppItem> {
                uiState.blockedPackages.contains(it.packageName) || uiState.alwaysBlockedPackages.contains(it.packageName)
            }.thenBy { it.appName.lowercase() }
        )
    }

    val handleDismiss: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        handleDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp,
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(sheetBg)
                .testTag("notification_blocker_sheet")
        ) {
            // Drag handle matching YouTube channels popup
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 18.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(dragHandleColor)
                    .align(Alignment.CenterHorizontally)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = FocusSpacing.screenHorizontal, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Header Title matching YouTube channels popup styling
                item {
                    Text(
                        text = stringResource(R.string.notification_sheet_title),
                        color = textPrimary,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 18.dp).semantics { heading() }
                    )
                }

                if (strictLockMessage != null) {
                    item {
                        FocusStatusMessage(
                            message = strictLockMessage,
                            isError = true,
                            onDismiss = viewModel::clearStrictLockMessage,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                // Permission Warning Banner with Stone-colored Grant button
                if (!uiState.isPermissionGranted) {
                    item {
                        StonePermissionBanner(
                            isDark = isDark,
                            onRequestPermission = onRequestPermission,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                // Master Shield Toggle Card
                item {
                    MasterShieldCard(
                        isDark = isDark,
                        isMasterEnabled = uiState.isMasterEnabled,
                        isSessionActive = uiState.isSessionActive,
                        blockedAppsCount = uiState.blockedPackages.size,
                        onToggle = { viewModel.toggleMaster(it) },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Tab Selector: Blocked Apps vs Silenced Vault
                item {
                    NotificationTabSelector(
                        selectedTab = selectedTab,
                        vaultCount = uiState.silencedVault.size,
                        blockedAppsCount = uiState.blockedPackages.size,
                        isDark = isDark,
                        onTabSelect = { selectedTab = it },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (selectedTab == 0) {
                    // Search bar matching YouTube popup
                    item {
                        FocusSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = stringResource(R.string.notification_search_label),
                            placeholder = stringResource(R.string.notification_search_example),
                            modifier = Modifier.testTag("notification_app_search_field")
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Preset Quick Action Chips
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            item {
                                QuickPresetChip(
                                    label = pluralStringResource(R.plurals.notification_silence_all, uiState.installedApps.size, uiState.installedApps.size),
                                    icon = Icons.Rounded.Shield,
                                    isDark = isDark,
                                    onClick = {
                                        val all = uiState.installedApps.map { it.packageName }.toSet()
                                        viewModel.setAllAppsBlocked(all)
                                    }
                                )
                            }
                            item {
                                QuickPresetChip(
                                    label = stringResource(R.string.notification_preset_social),
                                    icon = Icons.Rounded.Apps,
                                    isDark = isDark,
                                    onClick = {
                                        val socialKeywords = listOf("whatsapp", "instagram", "facebook", "snapchat", "telegram", "tiktok", "twitter", "x.corp", "reddit", "threads", "discord")
                                        val social = uiState.installedApps.filter { app ->
                                            socialKeywords.any { kw -> app.packageName.contains(kw, ignoreCase = true) || app.appName.contains(kw, ignoreCase = true) }
                                        }.map { it.packageName }.toSet()
                                        viewModel.setAllAppsBlocked(uiState.blockedPackages + social)
                                    }
                                )
                            }
                            item {
                                QuickPresetChip(
                                    label = stringResource(R.string.notification_preset_entertainment),
                                    icon = Icons.Rounded.NotificationsOff,
                                    isDark = isDark,
                                    onClick = {
                                        val entKeywords = listOf("youtube", "netflix", "prime", "twitch", "game", "spotify", "disney", "hotstar")
                                        val ent = uiState.installedApps.filter { app ->
                                            entKeywords.any { kw -> app.packageName.contains(kw, ignoreCase = true) || app.appName.contains(kw, ignoreCase = true) }
                                        }.map { it.packageName }.toSet()
                                        viewModel.setAllAppsBlocked(uiState.blockedPackages + ent)
                                    }
                                )
                            }
                            if (uiState.blockedPackages.isNotEmpty()) {
                                item {
                                    QuickPresetChip(
                                        label = stringResource(R.string.notification_allow_all),
                                        icon = Icons.Rounded.Close,
                                        isDark = isDark,
                                        onClick = { viewModel.setAllAppsBlocked(emptySet()) }
                                    )
                                }
                            }
                        }
                    }

                    // Section Title
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(if (searchQuery.isNotBlank()) R.string.notification_search_results else R.string.notification_installed_apps),
                                color = textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = pluralStringResource(R.plurals.notification_silenced_count, uiState.blockedPackages.size, uiState.blockedPackages.size),
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (uiState.isLoadingApps) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = FocusColors.Primary,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.notification_scanning),
                                        color = textSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else if (filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) stringResource(R.string.notification_no_search_results, searchQuery) else stringResource(R.string.notification_no_apps),
                                    color = textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        items(filteredApps, key = { it.packageName }) { appItem ->
                            val isChecked = uiState.blockedPackages.contains(appItem.packageName)
                            ThemedAppBlockCard(
                                appItem = appItem,
                                isBlocked = isChecked,
                                isDark = isDark,
                                onToggle = {
                                    viewModel.toggleApp(appItem.packageName, !isChecked)
                                },
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }
                } else {
                    // TAB 1: SILENCED VAULT
                    item {
                        SilencedVaultHeaderRow(
                            vaultCount = uiState.silencedVault.size,
                            isDark = isDark,
                            onClearVault = { viewModel.clearVault() },
                            onTestSimulate = { viewModel.simulateTestNotification() },
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }

                    if (uiState.silencedVault.isEmpty()) {
                        item {
                            EmptySilencedVaultCard(
                                isDark = isDark,
                                onTestSimulate = { viewModel.simulateTestNotification() },
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(uiState.silencedVault, key = { it.id }) { record ->
                            // Find corresponding installed app item for real logo
                            val installed = uiState.installedApps.find { it.packageName == record.packageName }
                            ThemedSilencedVaultItemCard(
                                record = record,
                                installedApp = installed,
                                isDark = isDark,
                                onDelete = { viewModel.deleteVaultItem(record.id) },
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            }

            // Bottom "Done" button matching YouTube popup
            Surface(
                color = sheetBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FocusSpacing.screenHorizontal)
                    .padding(top = 8.dp, bottom = 20.dp)
            ) {
                Button(
                    onClick = handleDismiss,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = doneBtnBg,
                        contentColor = doneBtnText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("done_notification_blocker_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_done),
                        color = doneBtnText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Permission request banner with Stone-like colored button matching user styling request.
 */
@Composable
private fun StonePermissionBanner(
    isDark: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerBg = FocusColors.OverlaySurface
    val bannerBorder = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bannerBg)
            .sheetDashedBorder(
                strokeWidth = 1.2.dp,
                color = bannerBorder,
                cornerRadius = 16.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.notification_permission_title),
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.notification_permission_body),
                    color = textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            OutlinedButton(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, FocusColors.ControlBorder),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_grant_notification_access),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }
        }
    }
}

/**
 * Master real-time notification shield card with dashed border styling.
 */
@Composable
private fun MasterShieldCard(
    isDark: Boolean,
    isMasterEnabled: Boolean,
    isSessionActive: Boolean,
    blockedAppsCount: Int,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = FocusColors.OverlaySurface
    val dashedBorderColor = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .sheetDashedBorder(
                strokeWidth = 1.2.dp,
                color = dashedBorderColor,
                cornerRadius = 16.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .toggleable(value = isMasterEnabled, role = Role.Switch, onValueChange = onToggle)
            .testTag("notification_blocker_master_switch")
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isMasterEnabled) FocusColors.EmeraldLight else FocusColors.SurfaceSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMasterEnabled) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = if (isMasterEnabled) FocusColors.EmeraldText else textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.notification_shield_label),
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isMasterEnabled) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(FocusColors.EmeraldText)
                            )
                        }
                        Text(
                            text = if (isMasterEnabled) {
                                pluralStringResource(
                                    if (isSessionActive) R.plurals.notification_session_active_apps else R.plurals.notification_active_apps,
                                    blockedAppsCount,
                                    blockedAppsCount
                                )
                            } else {
                                stringResource(R.string.notification_shield_paused)
                            },
                            color = if (isMasterEnabled) FocusColors.EmeraldText else textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Switch(
                checked = isMasterEnabled,
                onCheckedChange = null,
                colors = FocusColors.SwitchColors,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

/**
 * Tab switcher between Apps and Silenced Vault.
 */
@Composable
private fun NotificationTabSelector(
    selectedTab: Int,
    vaultCount: Int,
    blockedAppsCount: Int,
    isDark: Boolean,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabBg = FocusColors.OverlaySurface
    val activeBg = FocusColors.OverlayTextPrimary
    val activeText = FocusColors.OverlayBackground
    val inactiveText = FocusColors.OverlayTextSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(50))
            .background(tabBg)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Tab 0: Apps
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selectedTab == 0) activeBg else Color.Transparent)
                .selectable(
                    selected = selectedTab == 0,
                    role = Role.Tab,
                    onClick = { onTabSelect(0) }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pluralStringResource(R.plurals.notification_apps_tab, blockedAppsCount, blockedAppsCount),
                color = if (selectedTab == 0) activeText else inactiveText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 13.sp,
                fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Medium
            )
        }

        // Tab 1: Silenced Vault
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selectedTab == 1) activeBg else Color.Transparent)
                .selectable(
                    selected = selectedTab == 1,
                    role = Role.Tab,
                    onClick = { onTabSelect(1) }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pluralStringResource(R.plurals.notification_vault_tab, vaultCount, vaultCount),
                color = if (selectedTab == 1) activeText else inactiveText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 13.sp,
                fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

/**
 * Individual App card in the "Blocked Apps" tab styled like YouTube channel cards with dashed borders and Add/Blocked buttons.
 */
@Composable
private fun ThemedAppBlockCard(
    appItem: InstalledAppItem,
    isBlocked: Boolean,
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = FocusColors.OverlaySurface
    val dashedBorderColor = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .sheetDashedBorder(
                strokeWidth = 1.2.dp,
                color = dashedBorderColor,
                cornerRadius = 16.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .toggleable(value = isBlocked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Logo
            if (appItem.icon != null) {
                val bitmap = remember(appItem.packageName) {
                    try { appItem.icon.toBitmap(80, 80).asImageBitmap() } catch (e: Exception) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    DefaultAppLogoFallback(appName = appItem.appName, isDark = isDark)
                }
            } else {
                DefaultAppLogoFallback(appName = appItem.appName, isDark = isDark)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.appName,
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(if (isBlocked) R.string.notification_silenced_status else R.string.notification_allowed_status),
                    color = if (isBlocked) FocusColors.EmeraldText else textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Checkbox(checked = isBlocked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/**
 * Silenced Notification item in Vault with the blocked app logo, sender name visibility (e.g. Alex Rivera for WhatsApp), message text, and dismiss action.
 */
@Composable
private fun ThemedSilencedVaultItemCard(
    record: SilencedNotificationRecord,
    installedApp: InstalledAppItem?,
    isDark: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = FocusColors.OverlaySurface
    val dashedBorderColor = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary

    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val timeFormat = remember(context, configuration) { android.text.format.DateFormat.getTimeFormat(context) }
    val formattedTime = remember(record.timestamp) { timeFormat.format(Date(record.timestamp)) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Sender name display logic: prioritize senderName, fallback to title if non-empty
    val displaySender = record.senderName ?: record.title

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .sheetDashedBorder(
                strokeWidth = 1.2.dp,
                color = dashedBorderColor,
                cornerRadius = 16.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with App Logo, App Name, Sender/Channel badge, and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Blocked App Logo
                    if (installedApp?.icon != null) {
                        val bitmap = remember(installedApp.packageName) {
                            try { installedApp.icon.toBitmap(72, 72).asImageBitmap() } catch (e: Exception) { null }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            DefaultAppLogoFallback(appName = record.appName, isDark = isDark, size = 36.dp)
                        }
                    } else {
                        DefaultAppLogoFallback(appName = record.appName, isDark = isDark, size = 36.dp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Column {
                            Text(
                                text = record.appName,
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = FocusColors.SurfaceSubtle
                            ) {
                                Text(
                                    text = stringResource(R.string.notification_silenced_badge),
                                    color = FocusColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = formattedTime,
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.notification_delete_title, record.appName),
                        tint = FocusColors.BlockedText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sender Name Row with Person icon (e.g. WhatsApp sender name)
            if (!displaySender.isNullOrBlank() && displaySender != record.appName) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(FocusColors.SurfaceSubtle)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.notification_sender, displaySender),
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Message text content
            if (record.text.isNotBlank()) {
                Text(
                    text = record.text,
                    color = textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        FocusDestructiveDialog(
            title = stringResource(R.string.notification_delete_title, record.appName),
            message = stringResource(R.string.notification_delete_body),
            confirmLabel = stringResource(R.string.action_delete_notification),
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

/**
 * Vault header with Delete All / Test Simulate actions.
 */
@Composable
private fun SilencedVaultHeaderRow(
    vaultCount: Int,
    isDark: Boolean,
    onClearVault: () -> Unit,
    onTestSimulate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textPrimary = FocusColors.OverlayTextPrimary
    var showClearConfirmation by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.notification_vault_title),
                color = textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.notification_vault_subtitle),
                color = FocusColors.OverlayTextSecondary,
                fontSize = 12.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vaultCount > 0) {
                TextButton(onClick = { showClearConfirmation = true }) {
                    Text(stringResource(R.string.action_clear_all), color = FocusColors.BlockedText)
                }
            } else {
                TextButton(onClick = onTestSimulate) {
                    Text(stringResource(R.string.action_test_notification))
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.clear_vault_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.notification_clear_vault_body, vaultCount, vaultCount)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearVault()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_clear_all),
                        color = FocusColors.BlockedText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Empty state for Silenced Vault.
 */
@Composable
private fun EmptySilencedVaultCard(
    isDark: Boolean,
    onTestSimulate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = FocusColors.OverlaySurface
    val dashedBorderColor = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .sheetDashedBorder(
                strokeWidth = 1.2.dp,
                color = dashedBorderColor,
                cornerRadius = 16.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = FocusColors.OverlayTextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.notification_vault_empty_title),
                color = textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.notification_vault_empty_body),
                color = textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onTestSimulate,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.OverlayTextPrimary,
                    contentColor = FocusColors.OverlayBackground
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FlashOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_test_notification),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Fallback app logo container when drawable isn't available.
 */
@Composable
private fun DefaultAppLogoFallback(
    appName: String,
    isDark: Boolean,
    size: Dp = 46.dp
) {
    val initial = appName.firstOrNull()?.uppercase() ?: "A"
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(FocusColors.SurfaceSubtle)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = FocusColors.OverlayTextPrimary,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Quick action preset chip.
 */
@Composable
private fun QuickPresetChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val textColor = FocusColors.OverlayTextPrimary
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, FocusColors.ControlBorder),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
