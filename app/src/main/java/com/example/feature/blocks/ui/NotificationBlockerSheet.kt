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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.core.design.FocusColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isDark = LocalFocusColors.current.isDark
    val sheetBg = if (isDark) Color(0xFF121214) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)
    val dragHandleColor = if (isDark) Color(0xFF4E4E52) else Color(0xFFCBD5E1)
    val doneBtnBg = if (isDark) Color.White else Color(0xFF0F172A)
    val doneBtnText = if (isDark) Color.Black else Color(0xFFFFFFFF)

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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Header Title matching YouTube channels popup styling
                item {
                    Text(
                        text = "Silence distracting app notifications\nduring your study",
                        color = textPrimary,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color(0xFF1E1E20) else Color(0xFFF1F5F9))
                                .border(1.dp, if (isDark) Color(0xFF2E2E32) else Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = if (isDark) Color(0xFF7E7E82) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search apps to silence (e.g. WhatsApp, Insta)",
                                            color = if (isDark) Color(0xFF7E7E82) else Color(0xFF94A3B8),
                                            fontSize = 14.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(textPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("notification_app_search_field")
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }
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
                                    label = "Silence All (${uiState.installedApps.size})",
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
                                    label = "Social & Messaging",
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
                                    label = "Entertainment & Games",
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
                                        label = "Clear All",
                                        icon = Icons.Rounded.Close,
                                        isDark = isDark,
                                        isDestructive = true,
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
                                text = if (searchQuery.isNotBlank()) "Search Results" else "Installed Apps",
                                color = textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${uiState.blockedPackages.size} silenced",
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
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Scanning installed device apps...",
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
                                    text = if (searchQuery.isNotEmpty()) "No apps matching \"$searchQuery\"" else "No installed apps found",
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
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
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
                        .height(54.dp)
                        .testTag("done_notification_blocker_button")
                ) {
                    Text(
                        text = "Done",
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
    val bannerBg = if (isDark) Color(0xFF1E2124) else Color(0xFFF1F5F9)
    val bannerBorder = if (isDark) Color(0xFF33383F) else Color(0xFFCBD5E1)
    val stoneBtnBg = if (isDark) Color(0xFF475569) else Color(0xFF64748B) // Stone slate tone
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(stoneBtnBg.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = "Permission Required",
                    tint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notification Access Required",
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Allow FocusShield to intercept distracting alerts during sessions.",
                    color = textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Stone-like styled button
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = stoneBtnBg,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    text = "Grant",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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
    val cardBg = if (isDark) Color(0xFF16181D).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    val dashedBorderColor = if (isDark) Color(0xFF333336) else Color(0xFFCBD5E1)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isMasterEnabled) (if (isDark) Color(0xFF1E3A2F) else Color(0xFFDCFCE7)) else (if (isDark) Color(0xFF242426) else Color(0xFFE2E8F0))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMasterEnabled) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = if (isMasterEnabled) (if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)) else textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Notification Shield Engine",
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isMasterEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isDark) Color(0xFF1E3A2F) else Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = if (isSessionActive) "SESSION ACTIVE" else "ACTIVE",
                                    color = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isMasterEnabled) {
                            "$blockedAppsCount apps selected to stay silent"
                        } else {
                            "Shield paused (Turn ON to silence alerts)"
                        },
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Switch(
                checked = isMasterEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B),
                    uncheckedTrackColor = if (isDark) Color(0xFF2E2E32) else Color(0xFFE2E8F0)
                ),
                modifier = Modifier.testTag("notification_blocker_master_switch")
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
    val tabBg = if (isDark) Color(0xFF1E1E20) else Color(0xFFF1F5F9)
    val activeBg = if (isDark) Color.White else Color(0xFF0F172A)
    val activeText = if (isDark) Color.Black else Color.White
    val inactiveText = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(tabBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Tab 0: Apps
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(if (selectedTab == 0) activeBg else Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTabSelect(0) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Blocked Apps ($blockedAppsCount)",
                color = if (selectedTab == 0) activeText else inactiveText,
                fontSize = 13.sp,
                fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Medium
            )
        }

        // Tab 1: Silenced Vault
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(if (selectedTab == 1) activeBg else Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTabSelect(1) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Silenced Vault",
                    color = if (selectedTab == 1) activeText else inactiveText,
                    fontSize = 13.sp,
                    fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Medium
                )
                if (vaultCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (selectedTab == 1) (if (isDark) Color.Black else Color.White) else (if (isDark) Color.White else Color(0xFF0F172A))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$vaultCount",
                            color = if (selectedTab == 1) (if (isDark) Color.White else Color(0xFF0F172A)) else (if (isDark) Color.Black else Color.White),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
    val cardBg = if (isDark) Color(0xFF16181D).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    val dashedBorderColor = if (isDark) Color(0xFF333336) else Color(0xFFCBD5E1)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)

    val addBtnBg = if (isDark) Color.White else Color(0xFF0F172A)
    val addBtnText = if (isDark) Color.Black else Color.White
    val addedBtnBg = if (isDark) Color(0xFF242426) else Color(0xFFE2E8F0)
    val addedBtnText = if (isDark) Color(0xFF9E9EA3) else Color(0xFF64748B)

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
                        contentDescription = appItem.appName,
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isBlocked) "Notifications will be silenced" else "Notifications allowed",
                    color = if (isBlocked) (if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)) else textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!isBlocked) {
                // "Silence" / "Add" button matching YouTube sheet Add button
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(addBtnBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggle
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Silence",
                            color = addBtnText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Silence app",
                            tint = addBtnText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                // "Silenced" check button matching YouTube sheet Added button
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(addedBtnBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggle
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Silenced",
                            color = addedBtnText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Silenced",
                            tint = addedBtnText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
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
    val cardBg = if (isDark) Color(0xFF16181D).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    val dashedBorderColor = if (isDark) Color(0xFF333336) else Color(0xFFCBD5E1)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(record.timestamp) { timeFormat.format(Date(record.timestamp)) }

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
                                contentDescription = record.appName,
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

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = record.appName,
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isDark) Color(0xFF242426) else Color(0xFFE2E8F0)
                            ) {
                                Text(
                                    text = "SILENCED",
                                    color = if (isDark) Color(0xFF9E9EA3) else Color(0xFF64748B),
                                    fontSize = 8.sp,
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
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss from vault",
                        tint = textSecondary,
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
                        .background(if (isDark) Color(0xFF1E1E20) else Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sender: $displaySender",
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Message text content
            if (record.text.isNotBlank()) {
                Text(
                    text = record.text,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Silenced Notification Vault",
                color = textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Interceptions preserved quietly while studying",
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B),
                fontSize = 12.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vaultCount > 0) {
                Text(
                    text = "Clear all",
                    color = Color(0xFFFF6D2C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onClearVault)
                )
            } else {
                Text(
                    text = "Test sample",
                    color = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onTestSimulate)
                )
            }
        }
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
    val cardBg = if (isDark) Color(0xFF16181D).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    val dashedBorderColor = if (isDark) Color(0xFF333336) else Color(0xFFCBD5E1)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)

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
                    .background(if (isDark) Color(0xFF1E1E20) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = if (isDark) Color.White else Color(0xFF0F172A),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No Silenced Notifications Yet",
                color = textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "When you start a Focus Session, incoming notifications from selected apps will be caught and displayed with their logo and sender here.",
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
                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                    contentColor = if (isDark) Color.Black else Color.White
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
                    text = "Test WhatsApp Interception",
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
            .background(if (isDark) Color(0xFF242426) else Color(0xFFE2E8F0)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = if (isDark) Color.White else Color(0xFF0F172A),
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
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val bg = if (isDestructive) {
        Color(0xFFEF4444).copy(alpha = 0.12f)
    } else {
        if (isDark) Color(0xFF1E1E20) else Color(0xFFF1F5F9)
    }
    val border = if (isDestructive) {
        Color(0xFFEF4444).copy(alpha = 0.3f)
    } else {
        if (isDark) Color(0xFF2E2E32) else Color(0xFFE2E8F0)
    }
    val textColor = if (isDestructive) {
        Color(0xFFEF4444)
    } else {
        if (isDark) Color.White else Color(0xFF0F172A)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
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
