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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.design.FocusColors
import com.example.feature.notificationblocker.domain.NotificationBlockMode
import com.example.feature.notificationblocker.domain.SilencedNotificationRecord
import com.example.feature.notificationblocker.ui.InstalledAppItem
import com.example.feature.notificationblocker.ui.NotificationBlockerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.checkPermission()
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

    androidx.activity.compose.BackHandler(enabled = true) {
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FocusColors.Surface,
        contentColor = FocusColors.TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(FocusColors.TextMuted.copy(alpha = 0.4f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(FocusColors.AmberOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = FocusColors.AmberOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Notification Blocker Engine",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = if (uiState.isSessionActive) {
                                "⚡ Focus Session ACTIVE — Silencing alerts"
                            } else {
                                "${uiState.blockedPackages.size} apps configured to stay silent"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (uiState.isSessionActive) FocusColors.AmberOrange else FocusColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (uiState.isSessionActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = FocusColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Master Toggle Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FocusColors.Surface)
                    .border(
                        1.dp,
                        if (uiState.isMasterEnabled) FocusColors.AmberOrange.copy(alpha = 0.4f) else FocusColors.CardBorderSubtle,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Real-time Notification Shield",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        if (uiState.isMasterEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = FocusColors.EmeraldSuccess.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.EmeraldSuccess,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (uiState.isMasterEnabled) {
                            "Silences banners, rings, and popups from selected apps"
                        } else {
                            "Shield paused (Turn ON to silence alerts)"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (uiState.isMasterEnabled) FocusColors.TextSecondary else FocusColors.TextMuted,
                            fontSize = 12.sp
                        )
                    )
                }

                Switch(
                    checked = uiState.isMasterEnabled,
                    onCheckedChange = { viewModel.toggleMaster(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = FocusColors.AmberOrange,
                        uncheckedThumbColor = FocusColors.TextSecondary,
                        uncheckedTrackColor = FocusColors.SurfaceVariant
                    ),
                    modifier = Modifier.testTag("notification_blocker_master_switch")
                )
            }

            // Permission Warning Banner (if not granted)
            if (!uiState.isPermissionGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FocusColors.CoralWarning.copy(alpha = 0.12f))
                        .border(1.dp, FocusColors.CoralWarning.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FocusColors.CoralWarning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Access Required",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = "Enable listener access so FocusShield can intercept and cancel distracting alerts.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = FocusColors.CoralWarning),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Navigation: Apps & Rules vs Silenced Vault
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = FocusColors.AmberOrange,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = FocusColors.AmberOrange,
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Apps & Modes (${uiState.blockedPackages.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) FocusColors.TextPrimary else FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Silenced Vault",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) FocusColors.TextPrimary else FocusColors.TextSecondary,
                                fontSize = 13.sp
                            )
                            if (uiState.silencedVault.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = FocusColors.AmberOrange
                                ) {
                                    Text(
                                        text = "${uiState.silencedVault.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                // TAB 0: APPS & BLOCKING MODES
                AppsAndModesTabContent(
                    uiState = uiState,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    filteredApps = filteredApps,
                    onToggleApp = { pkg, blocked -> viewModel.toggleApp(pkg, blocked) },
                    onToggleAlwaysSilent = { pkg, always -> viewModel.toggleAlwaysSilent(pkg, always) },
                    onSelectPreset = { pkgs -> viewModel.setAllAppsBlocked(pkgs) },
                    onSetMode = { mode -> viewModel.setBlockMode(mode) },
                    onTestSimulate = { viewModel.simulateTestNotification() }
                )
            } else {
                // TAB 1: SILENCED NOTIFICATION VAULT
                SilencedVaultTabContent(
                    vaultItems = uiState.silencedVault,
                    onClearVault = { viewModel.clearVault() },
                    onDeleteItem = { id -> viewModel.deleteVaultItem(id) },
                    onTestSimulate = { viewModel.simulateTestNotification() }
                )
            }
        }
    }
}

@Composable
private fun AppsAndModesTabContent(
    uiState: com.example.feature.notificationblocker.ui.NotificationBlockerUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredApps: List<InstalledAppItem>,
    onToggleApp: (String, Boolean) -> Unit,
    onToggleAlwaysSilent: (String, Boolean) -> Unit,
    onSelectPreset: (Set<String>) -> Unit,
    onSetMode: (NotificationBlockMode) -> Unit,
    onTestSimulate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Mode Selector: Session Only vs Always Silent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(FocusColors.SurfaceSubtle)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeButton(
                title = "⚡ In Focus Sessions",
                subtitle = "Silent during study; normal outside",
                isSelected = uiState.blockMode == NotificationBlockMode.SESSION_ONLY,
                onClick = { onSetMode(NotificationBlockMode.SESSION_ONLY) },
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                title = "🔒 Always Silent",
                subtitle = "Silenced 24/7 continuously",
                isSelected = uiState.blockMode == NotificationBlockMode.ALWAYS_SILENT,
                onClick = { onSetMode(NotificationBlockMode.ALWAYS_SILENT) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                PresetChip(
                    label = "Silence All (${uiState.installedApps.size})",
                    icon = Icons.Rounded.Shield,
                    onClick = {
                        val all = uiState.installedApps.map { it.packageName }.toSet()
                        onSelectPreset(all)
                    }
                )
            }
            item {
                PresetChip(
                    label = "Social Media",
                    icon = Icons.Rounded.Apps,
                    onClick = {
                        val socialKeywords = listOf("instagram", "facebook", "snapchat", "tiktok", "twitter", "x.corp", "reddit", "threads")
                        val social = uiState.installedApps.filter { app ->
                            socialKeywords.any { kw -> app.packageName.contains(kw, ignoreCase = true) || app.appName.contains(kw, ignoreCase = true) }
                        }.map { it.packageName }.toSet()
                        onSelectPreset(uiState.blockedPackages + social)
                    }
                )
            }
            item {
                PresetChip(
                    label = "Entertainment & Games",
                    icon = Icons.Rounded.NotificationsOff,
                    onClick = {
                        val entKeywords = listOf("youtube", "netflix", "prime", "twitch", "game", "spotify", "disney")
                        val ent = uiState.installedApps.filter { app ->
                            entKeywords.any { kw -> app.packageName.contains(kw, ignoreCase = true) || app.appName.contains(kw, ignoreCase = true) }
                        }.map { it.packageName }.toSet()
                        onSelectPreset(uiState.blockedPackages + ent)
                    }
                )
            }
            if (uiState.blockedPackages.isNotEmpty()) {
                item {
                    PresetChip(
                        label = "Clear All",
                        icon = Icons.Rounded.Close,
                        isDestructive = true,
                        onClick = { onSelectPreset(emptySet()) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text("Search installed phone apps...", color = FocusColors.TextMuted, fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = FocusColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = FocusColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FocusColors.AmberOrange,
                unfocusedBorderColor = FocusColors.CardBorderSubtle,
                focusedContainerColor = FocusColors.Surface,
                unfocusedContainerColor = FocusColors.Surface,
                focusedTextColor = FocusColors.TextPrimary,
                unfocusedTextColor = FocusColors.TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        )

        // App List
        if (uiState.isLoadingApps) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = FocusColors.AmberOrange,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scanning installed apps on your device...",
                        style = MaterialTheme.typography.bodySmall.copy(color = FocusColors.TextSecondary)
                    )
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No apps matching \"$searchQuery\"" else "No installed apps found",
                    style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextMuted)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appItem ->
                    val isChecked = uiState.blockedPackages.contains(appItem.packageName)
                    val isAlwaysSilent = uiState.alwaysBlockedPackages.contains(appItem.packageName)

                    AppNotificationRowItem(
                        appItem = appItem,
                        isChecked = isChecked,
                        isAlwaysSilent = isAlwaysSilent,
                        blockMode = uiState.blockMode,
                        onToggle = { onToggleApp(appItem.packageName, !isChecked) },
                        onToggleAlwaysSilent = { onToggleAlwaysSilent(appItem.packageName, !isAlwaysSilent) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun AppNotificationRowItem(
    appItem: InstalledAppItem,
    isChecked: Boolean,
    isAlwaysSilent: Boolean,
    blockMode: NotificationBlockMode,
    onToggle: () -> Unit,
    onToggleAlwaysSilent: () -> Unit
) {
    val borderColor by animateColorAsState(
        if (isChecked || isAlwaysSilent) FocusColors.AmberOrange.copy(alpha = 0.4f) else FocusColors.CardBorderSubtle,
        label = "border"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isChecked || isAlwaysSilent) FocusColors.SurfaceVariant else FocusColors.Surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // App Icon
                if (appItem.icon != null) {
                    val bitmap = remember(appItem.packageName) {
                        try { appItem.icon.toBitmap(80, 80).asImageBitmap() } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = appItem.appName,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        DefaultAppIcon(isChecked = isChecked)
                    }
                } else {
                    DefaultAppIcon(isChecked = isChecked)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appItem.appName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isAlwaysSilent) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = FocusColors.Primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "24/7 SILENT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.Primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isAlwaysSilent) {
                            "Silenced always (24/7)"
                        } else if (isChecked) {
                            if (blockMode == NotificationBlockMode.SESSION_ONLY) "Silenced during focus sessions" else "Silenced always"
                        } else {
                            "Allowed (ring & notify normally)"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isChecked || isAlwaysSilent) FocusColors.AmberOrange else FocusColors.TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Switch(
                checked = isChecked || isAlwaysSilent,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.AmberOrange,
                    uncheckedThumbColor = FocusColors.TextSecondary,
                    uncheckedTrackColor = FocusColors.SurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun DefaultAppIcon(isChecked: Boolean) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = null,
            tint = if (isChecked) FocusColors.AmberOrange else FocusColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) FocusColors.AmberOrange.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (isSelected) FocusColors.AmberOrange else Color.Transparent
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) FocusColors.AmberOrange else FocusColors.TextPrimary,
                    fontSize = 12.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) FocusColors.TextPrimary else FocusColors.TextMuted,
                    fontSize = 9.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDestructive) FocusColors.BlockedRed.copy(alpha = 0.12f) else FocusColors.Surface,
        border = BorderStroke(
            1.dp,
            if (isDestructive) FocusColors.BlockedRed.copy(alpha = 0.3f) else FocusColors.CardBorderSubtle
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) FocusColors.BlockedRed else FocusColors.AmberOrange,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isDestructive) FocusColors.BlockedRed else FocusColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun SilencedVaultTabContent(
    vaultItems: List<SilencedNotificationRecord>,
    onClearVault: () -> Unit,
    onDeleteItem: (String) -> Unit,
    onTestSimulate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Silenced Notification Vault",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = "Review all notifications intercepted during focus sessions",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            if (vaultItems.isNotEmpty()) {
                TextButton(onClick = onClearVault) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear Vault",
                        tint = FocusColors.BlockedRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clear Vault",
                        color = FocusColors.BlockedRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (vaultItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(FocusColors.AmberOrange.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = FocusColors.AmberOrange,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Silenced Notifications Yet",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "When you start a Focus Session, incoming notifications from selected distracting apps will be safely caught and logged here without interrupting your study flow.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            lineHeight = 16.sp
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onTestSimulate,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, FocusColors.AmberOrange.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FlashOn,
                            contentDescription = null,
                            tint = FocusColors.AmberOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Test Simulate Interception",
                            color = FocusColors.AmberOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vaultItems, key = { it.id }) { item ->
                    SilencedItemCard(item = item, onDelete = { onDeleteItem(item.id) })
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun SilencedItemCard(
    item: SilencedNotificationRecord,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(item.timestamp) { timeFormat.format(Date(item.timestamp)) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = FocusColors.Surface,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(FocusColors.AmberOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = FocusColors.AmberOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 13.sp
                        )
                    )
                    if (item.wasDuringSession) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = FocusColors.AmberOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "STUDY SESSION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.AmberOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = FocusColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
