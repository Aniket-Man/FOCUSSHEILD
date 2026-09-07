package com.example.feature.blocks

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusCardStyle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation
import com.example.feature.applimits.ui.AppLimitUiItem
import com.example.feature.applimits.ui.AppLimitsViewModel
import com.example.feature.applimits.ui.InstalledAppChoice
import com.example.feature.blocks.ui.NotificationBlockerSheet
import com.example.feature.settings.SettingsViewModel

data class DefaultDistractingApp(
    val name: String,
    val packageName: String,
    val sampleUsage: String,
    val iconTint: Color = Color(0xFFEF4444)
)

val SampleDistractingApps = listOf(
    DefaultDistractingApp("YouTube", "com.google.android.youtube", "8 hours 12 mins", Color(0xFFFF0000)),
    DefaultDistractingApp("Instagram", "com.instagram.android", "2 hours 45 mins", Color(0xFFE1306C)),
    DefaultDistractingApp("Chrome", "com.android.chrome", "1 hour 56 mins", Color(0xFF4285F4)),
    DefaultDistractingApp("Opera", "com.opera.browser", "35 mins", Color(0xFFFF1B2D)),
    DefaultDistractingApp("Free Fire MAX", "com.dts.freefiremax", "17 mins", Color(0xFFFFA500)),
    DefaultDistractingApp("Flipkart", "com.flipkart.android", "12 mins", Color(0xFF2874F0)),
    DefaultDistractingApp("Brave", "com.brave.browser", "9 mins", Color(0xFFFF5722)),
    DefaultDistractingApp("X (Twitter)", "com.twitter.android", "8 mins", Color(0xFF1DA1F2)),
    DefaultDistractingApp("Meesho", "com.meesho.supply", "5 mins", Color(0xFFE91E63)),
    DefaultDistractingApp("Reddit", "com.reddit.frontpage", "25 mins", Color(0xFFFF4500)),
    DefaultDistractingApp("Snapchat", "com.snapchat.android", "40 mins", Color(0xFFFFFC00)),
    DefaultDistractingApp("Facebook", "com.facebook.katana", "50 mins", Color(0xFF1877F2))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(
    appLimitsViewModel: AppLimitsViewModel,
    settingsViewModel: SettingsViewModel,
    onTabSelected: (BottomTab) -> Unit,
    onNavigateToWebsiteBlocker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val limitsState by appLimitsViewModel.uiState.collectAsStateWithLifecycle()
    val installedApps by appLimitsViewModel.installedApps.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val strictLockMessage by appLimitsViewModel.strictLockMessage.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.checkAccessibilityStatus()
                settingsViewModel.checkDeviceAdminStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.runtime.LaunchedEffect(strictLockMessage) {
        strictLockMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            appLimitsViewModel.clearStrictLockMessage()
        }
    }

    var showAppSelectionSheet by remember { mutableStateOf(false) }
    var selectedAppForConfig by remember { mutableStateOf<InstalledAppChoice?>(null) }
    var showConfigSheet by remember { mutableStateOf(false) }
    var openedFromAppSelection by remember { mutableStateOf(false) }
    var showNotificationBlockerSheet by remember { mutableStateOf(false) }

    // Strict Mode / Anti Cheating state toggles bound to real-time preferences
    val blockUninstallEnabled = settingsState.preferences.isBlockUninstallEnabled
    val blockSplitScreenEnabled = settingsState.preferences.isBlockSplitScreenEnabled
    val blockFloatingWindowEnabled = settingsState.preferences.isBlockFloatingWindowEnabled
    val blockNotificationsEnabled = settingsState.preferences.isBlockNotificationsEnabled

    val isDeviceAdminActive by settingsViewModel.isDeviceAdminActive.collectAsStateWithLifecycle()
    val pendingDeviceAdminRequest by settingsViewModel.pendingDeviceAdminRequest.collectAsStateWithLifecycle()
    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        settingsViewModel.onDeviceAdminRequestResult()
    }

    LaunchedEffect(pendingDeviceAdminRequest) {
        if (pendingDeviceAdminRequest) {
            deviceAdminLauncher.launch(settingsViewModel.getDeviceAdminIntent())
        }
    }

    // Help Dialog
    var showHelpDialog by remember { mutableStateOf(false) }

    // Stepwise Back Handling: If Config Sheet was opened from App Selection Sheet,
    // stepping back returns to the App Selection Sheet first, then cancels the popup.
    val handleConfigSheetBack = {
        showConfigSheet = false
        if (openedFromAppSelection) {
            showAppSelectionSheet = true
        }
    }

    androidx.activity.compose.BackHandler(enabled = showHelpDialog) {
        showHelpDialog = false
    }

    androidx.activity.compose.BackHandler(enabled = showConfigSheet) {
        handleConfigSheetBack()
    }

    androidx.activity.compose.BackHandler(enabled = showAppSelectionSheet) {
        showAppSelectionSheet = false
    }

    androidx.activity.compose.BackHandler(enabled = showNotificationBlockerSheet) {
        showNotificationBlockerSheet = false
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Blocks",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    )
                )

                Surface(
                    shape = FocusShapes.pill,
                    color = FocusColors.SurfaceVariant,
                    border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                    modifier = Modifier.clickable { showHelpDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = "Help",
                            tint = FocusColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Help",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("blocks_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = FocusSpacing.screenHorizontal),
                contentPadding = PaddingValues(bottom = FocusCardStyle.BottomNavClearance + 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
            // 1. App Limits Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Limits",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )

                        Text(
                            text = "+ Add App",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier
                                .clickable { showAppSelectionSheet = true }
                                .padding(4.dp)
                                .testTag("blocks_add_app_btn")
                        )
                    }

                    if (limitsState.items.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = FocusColors.Surface,
                            border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAppSelectionSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(FocusColors.PrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = FocusColors.Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Add app limits to stay focused",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = FocusColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = "Limit daily usage for YouTube, Games, Social Media",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = FocusColors.TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            limitsState.items.forEach { item ->
                                AppLimitCard(
                                    item = item,
                                    onToggle = { isEnabled ->
                                        appLimitsViewModel.toggleLimit(item.packageName, isEnabled)
                                    },
                                    onDelete = {
                                        appLimitsViewModel.deleteLimit(item.packageName)
                                    },
                                    onClick = {
                                        if (item.isStrictOverride) {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Strict Mode Active: ${item.appName} limit cannot be edited or modified.",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                            return@AppLimitCard
                                        }
                                        selectedAppForConfig = InstalledAppChoice(
                                            packageName = item.packageName,
                                            appName = item.appName,
                                            icon = item.appIcon,
                                            isAlreadyLimited = true
                                        )
                                        openedFromAppSelection = false
                                        showConfigSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Block Shorts Section (PRO)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Block Shorts",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ProBadge()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(FocusColors.Surface)
                            .border(1.dp, FocusColors.CardBorderSubtle, RoundedCornerShape(16.dp))
                    ) {
                        // YouTube Shorts
                        BlockRowToggleItem(
                            title = "YouTube Shorts",
                            icon = Icons.Rounded.SmartDisplay,
                            iconTint = Color(0xFFFF0000),
                            checked = settingsState.preferences.isYouTubeShortsBlockingEnabled,
                            onCheckedChange = { settingsViewModel.updateYouTubeShortsBlocking(it) },
                            testTag = "blocks_toggle_yt_shorts"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(FocusColors.CardBorderSubtle)
                        )

                        // IG Reels
                        BlockRowToggleItem(
                            title = "IG Reels",
                            icon = Icons.Rounded.VideoLibrary,
                            iconTint = Color(0xFFE1306C),
                            checked = settingsState.preferences.isInstagramReelsBlockingEnabled,
                            onCheckedChange = { settingsViewModel.updateInstagramReelsBlocking(it) },
                            testTag = "blocks_toggle_ig_reels"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(FocusColors.CardBorderSubtle)
                        )

                        // Facebook Reels
                        BlockRowToggleItem(
                            title = "Facebook Reels",
                            icon = Icons.Rounded.SmartDisplay,
                            iconTint = Color(0xFF1877F2),
                            checked = settingsState.preferences.isFacebookReelsBlockingEnabled,
                            onCheckedChange = { settingsViewModel.updateFacebookReelsBlocking(it) },
                            testTag = "blocks_toggle_fb_reels"
                        )
                    }
                }
            }

            // 3. Other blocks Section (PRO)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Other blocks",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ProBadge()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(FocusColors.Surface)
                            .border(1.dp, FocusColors.CardBorderSubtle, RoundedCornerShape(16.dp))
                    ) {
                        // Block Websites
                        BlockRowActionItem(
                            title = "Block Websites & Adult Sites",
                            icon = Icons.Rounded.Language,
                            iconTint = FocusColors.Primary,
                            actionText = "Manage Shield",
                            onClick = onNavigateToWebsiteBlocker
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(FocusColors.CardBorderSubtle)
                        )

                        // Block Notifications
                        BlockRowActionItem(
                            title = "Block Notifications",
                            icon = Icons.Rounded.NotificationsOff,
                            iconTint = FocusColors.AmberOrange,
                            actionText = if (blockNotificationsEnabled && settingsState.preferences.blockedNotificationPackages.isNotEmpty()) {
                                "${settingsState.preferences.blockedNotificationPackages.size} Active"
                            } else if (blockNotificationsEnabled) {
                                "Active"
                            } else {
                                "+ Add"
                            },
                            onClick = {
                                showNotificationBlockerSheet = true
                            }
                        )
                    }
                }
            }

            // 4. Strict mode protections Section (PRO)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Strict mode protections",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ProBadge()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(FocusColors.Surface)
                            .border(1.dp, FocusColors.CardBorderSubtle, RoundedCornerShape(16.dp))
                    ) {
                        // Block uninstall — activates Device Admin
                        ProtectionRowItem(
                            title = "Block FocusShield app uninstall",
                            subtitle = when {
                                blockUninstallEnabled && isDeviceAdminActive -> "Device Admin active — Uninstallation fully blocked"
                                blockUninstallEnabled && !isDeviceAdminActive -> "Pending — Tap to activate Device Admin"
                                else -> "Cannot uninstall or modify protection for FocusShield"
                            },
                            checked = blockUninstallEnabled,
                            onCheckedChange = { settingsViewModel.updateBlockUninstall(it) }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(FocusColors.CardBorderSubtle)
                        )

                        // Block split screen
                        ProtectionRowItem(
                            title = "Block split screen",
                            subtitle = "Cannot use blocked apps in split screen",
                            checked = blockSplitScreenEnabled,
                            onCheckedChange = { settingsViewModel.updateBlockSplitScreen(it) }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(FocusColors.CardBorderSubtle)
                        )

                        // Block floating window
                        ProtectionRowItem(
                            title = "Block floating window",
                            subtitle = "Cannot use blocked apps in floating window",
                            checked = blockFloatingWindowEnabled,
                            onCheckedChange = { settingsViewModel.updateBlockFloatingWindow(it) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(FocusSpacing.base))
            }
        }

        FocusBottomNavigation(
            selectedTab = BottomTab.BLOCKS,
            onTabSelected = onTabSelected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

    // Bottom Sheet: Select an App to Add Limit (Image 3)
    if (showAppSelectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppSelectionSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FocusColors.Surface,
            contentColor = FocusColors.TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(FocusColors.TextMuted.copy(alpha = 0.4f))
                )
            }
        ) {
            SelectAppBottomSheetContent(
                installedApps = installedApps,
                onClose = { showAppSelectionSheet = false },
                onSelectApp = { app ->
                    selectedAppForConfig = app
                    openedFromAppSelection = true
                    showAppSelectionSheet = false
                    showConfigSheet = true
                }
            )
        }
    }

    // Bottom Sheet: App Limit Configuration (Image 4)
    if (showConfigSheet && selectedAppForConfig != null) {
        val currentLimitItem = limitsState.items.find { it.packageName == selectedAppForConfig!!.packageName }
        ModalBottomSheet(
            onDismissRequest = { handleConfigSheetBack() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FocusColors.Surface,
            contentColor = FocusColors.TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(FocusColors.TextMuted.copy(alpha = 0.4f))
                )
            }
        ) {
            AppLimitConfigBottomSheetContent(
                app = selectedAppForConfig!!,
                existingLimit = currentLimitItem,
                isOpenedFromAppSelection = openedFromAppSelection,
                onClose = { handleConfigSheetBack() },
                onSaveLimit = { dailyMinutes, isStrict, showReminders, emergencyAllowed ->
                    if (isStrict && !blockUninstallEnabled) {
                        settingsViewModel.updateBlockUninstall(true)
                    }
                    appLimitsViewModel.saveLimit(
                        packageName = selectedAppForConfig!!.packageName,
                        appName = selectedAppForConfig!!.appName,
                        dailyLimitMinutes = dailyMinutes,
                        isStrictOverride = isStrict,
                        showRemindersBeforeLimit = showReminders,
                        emergencyUsesAllowed = emergencyAllowed
                    )
                    showConfigSheet = false
                }
            )
        }
    }

    // Bottom Sheet: Notification Blocker (Real-time Distraction Alert Shield)
    if (showNotificationBlockerSheet) {
        val context = androidx.compose.ui.platform.LocalContext.current
        NotificationBlockerSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { showNotificationBlockerSheet = false },
            onRequestPermission = {
                com.example.core.permission.FocusPermissionManager.openNotificationListenerSettings(context)
                settingsViewModel.checkAccessibilityStatus()
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "Blocks & App Limits Guide",
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• App Limits allow you to set daily usage caps for distracting apps like YouTube, Instagram, or games.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
                    )
                    Text(
                        text = "• Strict Mode ensures you cannot open the app once the daily cap has expired.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
                    )
                    Text(
                        text = "• Block Shorts automatically closes the Shorts / Reels feed immediately when opened.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
                ) {
                    Text("Got It")
                }
            }
        )
    }
}

/**
 * Image 3: Select an app to add limit Bottom Sheet Content
 */
@Composable
private fun SelectAppBottomSheetContent(
    installedApps: List<InstalledAppChoice>,
    onClose: () -> Unit,
    onSelectApp: (InstalledAppChoice) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isAppsListExpanded by remember { mutableStateOf(true) }

    // Filter strictly by installed apps on the device
    val filteredInstalledApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.80f)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select an app to add limit",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary,
                    fontSize = 20.sp
                )
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = FocusColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search installed apps",
                    color = FocusColors.TextMuted,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = FocusColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FocusColors.Surface,
                unfocusedContainerColor = FocusColors.Surface,
                focusedBorderColor = FocusColors.Primary,
                unfocusedBorderColor = FocusColors.CardBorderSubtle,
                focusedTextColor = FocusColors.TextPrimary,
                unfocusedTextColor = FocusColors.TextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("select_app_search_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Expandable Category: "Installed Apps"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isAppsListExpanded = !isAppsListExpanded }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Installed Apps (${filteredInstalledApps.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary,
                    fontSize = 16.sp
                )
            )

            Icon(
                imageVector = if (isAppsListExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = FocusColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Apps List
        if (filteredInstalledApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "No installed apps found on device" else "No matching apps for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isAppsListExpanded) {
                    items(filteredInstalledApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectApp(app) }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // App Icon
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.toBitmap(96, 96).asImageBitmap(),
                                    contentDescription = app.appName,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(FocusColors.PrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Apps,
                                        contentDescription = null,
                                        tint = FocusColors.Primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // App Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary,
                                        fontSize = 15.sp
                                    )
                                )
                                Text(
                                    text = if (app.isAlreadyLimited) "Daily limit active" else "Installed application",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (app.isAlreadyLimited) FocusColors.EmeraldSuccess else FocusColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            // + Add Button
                            Surface(
                                shape = FocusShapes.pill,
                                color = if (app.isAlreadyLimited) FocusColors.PrimaryContainer else FocusColors.SurfaceVariant,
                                border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                                modifier = Modifier
                                    .clickable { onSelectApp(app) }
                                    .testTag("btn_add_${app.appName.lowercase().replace(" ", "_")}")
                            ) {
                                Text(
                                    text = if (app.isAlreadyLimited) "Edit" else "+ Add",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Image 4: App limit configuration Bottom Sheet Content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLimitConfigBottomSheetContent(
    app: InstalledAppChoice,
    existingLimit: AppLimitUiItem? = null,
    isOpenedFromAppSelection: Boolean = false,
    onClose: () -> Unit,
    onSaveLimit: (dailyMinutes: Int, isStrict: Boolean, showReminders: Boolean, emergencyUsesAllowed: Int) -> Unit
) {
    val initialHours = existingLimit?.let { it.dailyLimitMinutes / 60 } ?: 1
    val initialMins = existingLimit?.let { it.dailyLimitMinutes % 60 } ?: 30
    var hours by remember { mutableIntStateOf(initialHours) }
    var minutes by remember { mutableIntStateOf(initialMins) }
    var emergencyUsesAllowed by remember { mutableIntStateOf(existingLimit?.emergencyUsesAllowed ?: 1) }
    var showReminders by remember { mutableStateOf(existingLimit?.showRemindersBeforeLimit ?: true) }
    var isStrictModeEnabled by remember { mutableStateOf(existingLimit?.isStrictOverride ?: true) }
    var showTimeAdjustDialog by remember { mutableStateOf(false) }
    var showStrictWarningDialog by remember { mutableStateOf(false) }

    val formattedLimit = "${hours}h ${minutes}m"

    // Nested BackHandler: dismisses Time Adjust Picker first before parent config
    androidx.activity.compose.BackHandler(enabled = showTimeAdjustDialog || showStrictWarningDialog) {
        if (showStrictWarningDialog) {
            showStrictWarningDialog = false
        } else {
            showTimeAdjustDialog = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        // App Title & Close/Back
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon.toBitmap(80, 80).asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FocusColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${app.appName} limit",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 19.sp
                        )
                    )
                    if (existingLimit != null && existingLimit.streakDays > 0) {
                        Text(
                            text = "🔥 ${existingLimit.streakDays} days discipline streak",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.AmberOrange,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceVariant)
            ) {
                Icon(
                    imageVector = if (isOpenedFromAppSelection) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                    contentDescription = if (isOpenedFromAppSelection) "Back to app selection" else "Close",
                    tint = FocusColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Settings Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FocusColors.Surface)
                .border(1.dp, FocusColors.CardBorderSubtle, RoundedCornerShape(16.dp))
        ) {
            // Row 1: Limit (Opens scroll wheel picker)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimeAdjustDialog = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Limit",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedLimit,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.Primary,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = FocusColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FocusColors.CardBorderSubtle)
            )

            // Row 2: No. of emergency use
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "No. of emergency use",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { if (emergencyUsesAllowed > 0) emergencyUsesAllowed-- },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FocusColors.SurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease",
                            tint = FocusColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "$emergencyUsesAllowed",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 15.sp
                        )
                    )

                    IconButton(
                        onClick = { if (emergencyUsesAllowed < 10) emergencyUsesAllowed++ },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(FocusColors.SurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase",
                            tint = FocusColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FocusColors.CardBorderSubtle)
            )

            // Row 3: Show reminders before limit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showReminders = !showReminders }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show reminders before limit",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (showReminders) "Yes" else "No",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FocusColors.TextSecondary,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = FocusColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FocusColors.CardBorderSubtle)
            )

            // Row 4: Strict mode (PRO)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Strict mode",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ProBadge()
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "You cannot delete, edit, or toggle off the limit once set",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isStrictModeEnabled) FocusColors.BlockedRed else FocusColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                Switch(
                    checked = isStrictModeEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            showStrictWarningDialog = true
                        } else {
                            isStrictModeEnabled = false
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FocusColors.TextOnDark,
                        checkedTrackColor = FocusColors.Primary,
                        uncheckedThumbColor = FocusColors.TextMuted,
                        uncheckedTrackColor = FocusColors.SurfaceSubtle
                    ),
                    modifier = Modifier.testTag("config_strict_mode_switch")
                )
            }
        }

        // Strict Mode Warning Banner
        if (isStrictModeEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = FocusColors.BlockedRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, FocusColors.BlockedRed.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FocusColors.BlockedRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Strict Mode active: This limit cannot be deleted or edited once saved. Choose your limit wisely!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Large Bottom Add / Update Button
        Button(
            onClick = {
                val totalMinutes = (hours * 60) + minutes
                onSaveLimit(totalMinutes.coerceAtLeast(1), isStrictModeEnabled, showReminders, emergencyUsesAllowed)
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FocusColors.Primary,
                contentColor = FocusColors.TextOnDark
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("config_save_limit_button")
        ) {
            Text(
                text = if (existingLimit != null) "Update Limit" else "Add Limit",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Scroll Wheel Time Adjustment Bottom Sheet
    if (showTimeAdjustDialog) {
        ModalBottomSheet(
            onDismissRequest = { showTimeAdjustDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FocusColors.Surface,
            contentColor = FocusColors.TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(FocusColors.TextMuted.copy(alpha = 0.4f))
                )
            }
        ) {
            UsageLimitPickerSheet(
                appName = app.appName,
                appIcon = app.icon,
                initialHours = hours,
                initialMinutes = minutes,
                onClose = { showTimeAdjustDialog = false },
                onConfirm = { selectedH, selectedM ->
                    hours = selectedH
                    minutes = selectedM
                    showTimeAdjustDialog = false
                }
            )
        }
    }

    // Strict Mode Warning Modal Dialog
    if (showStrictWarningDialog) {
        AlertDialog(
            onDismissRequest = { showStrictWarningDialog = false },
            containerColor = FocusColors.Surface,
            titleContentColor = FocusColors.TextPrimary,
            textContentColor = FocusColors.TextSecondary,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FocusColors.BlockedRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FocusColors.BlockedRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Strict Mode Warning",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "Once you enable Strict Mode and save, this app limit CANNOT be deleted, edited, or toggled off during the day. Choose your limit wisely!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isStrictModeEnabled = true
                        showStrictWarningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.BlockedRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I Understand", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isStrictModeEnabled = false
                        showStrictWarningDialog = false
                    }
                ) {
                    Text("Cancel", color = FocusColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AppLimitCard(
    item: AppLimitUiItem,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val hrs = item.dailyLimitMinutes / 60
    val mins = item.dailyLimitMinutes % 60
    val limitStr = if (hrs > 0 && mins > 0) "${hrs}h ${mins}m" else if (hrs > 0) "${hrs}h" else "${mins}m"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = FocusColors.Surface,
        border = BorderStroke(
            1.dp,
            if (item.isLimitReached) FocusColors.BlockedRed.copy(alpha = 0.5f) else FocusColors.CardBorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("app_limit_card_${item.appName.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (item.appIcon != null) {
                Image(
                    bitmap = item.appIcon.toBitmap(96, 96).asImageBitmap(),
                    contentDescription = item.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FocusColors.PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 15.sp
                        )
                    )
                    if (item.isStrictOverride) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = FocusColors.BlockedRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "STRICT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.BlockedRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (item.streakDays > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🔥 ${item.streakDays}d",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.AmberOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$limitStr Limit • ${item.usedMinutes}m used today",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (item.isLimitReached) FocusColors.BlockedRed else FocusColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (item.isLimitReached) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }

            // Switch (disabled or warned if strictly locked)
            Switch(
                checked = item.isEnabled,
                onCheckedChange = { isChecked ->
                    onToggle(isChecked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = FocusColors.TextMuted,
                    uncheckedTrackColor = FocusColors.SurfaceSubtle
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete limit",
                    tint = FocusColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockRowToggleItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = FocusColors.TextPrimary,
                fontSize = 15.sp
            ),
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FocusColors.Primary,
                uncheckedThumbColor = FocusColors.TextMuted,
                uncheckedTrackColor = FocusColors.SurfaceSubtle
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun BlockRowActionItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = FocusColors.TextPrimary,
                fontSize = 15.sp
            ),
            modifier = Modifier.weight(1f)
        )

        Surface(
            shape = FocusShapes.pill,
            color = FocusColors.SurfaceVariant,
            border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ProtectionRowItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = FocusColors.TextPrimary,
                    fontSize = 15.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 12.sp
                )
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FocusColors.Primary,
                uncheckedThumbColor = FocusColors.TextMuted,
                uncheckedTrackColor = FocusColors.SurfaceSubtle
            )
        )
    }
}

@Composable
private fun ProBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(FocusColors.ProGold)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "PRO",
            style = MaterialTheme.typography.labelSmall.copy(
                color = FocusColors.ProGoldText,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}
