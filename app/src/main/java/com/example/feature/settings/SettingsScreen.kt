package com.example.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.accessibility.AccessibilityFeaturePromptInfo
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.accessibility.AccessibilityPermissionRequiredDialog
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation

import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.IconButton

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToBlockedApps: () -> Unit,
    onNavigateToAppLimits: () -> Unit = {},
    onNavigateToStrictMode: () -> Unit = {},
    onNavigateToStudyChannels: () -> Unit = {},
    onNavigateToWebsiteBlocker: () -> Unit = {},
    onTabSelected: (BottomTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingAccessibilityPrompt by remember { mutableStateOf<AccessibilityFeaturePromptInfo?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAccessibilityStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Shield Settings",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "App blocking, accessibility, and study preferences",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
        ) {
            // Section 1: Distraction Protection
            item {
                SettingsSectionHeader(title = "DISTRACTION PROTECTION")
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsNavigationRow(
                        title = "App Limits & Daily Budgets",
                        subtitle = "Daily usage allowances & timed sessions outside study",
                        icon = Icons.Rounded.Timer,
                        iconTint = FocusColors.Primary,
                        onClick = onNavigateToAppLimits,
                        testTag = "settings_app_limits_row"
                    )

                    SettingsNavigationRow(
                        title = "Blocked Applications",
                        subtitle = "${uiState.blockedAppsCount} applications selected to block",
                        icon = Icons.Rounded.Block,
                        iconTint = FocusColors.CoralWarning,
                        onClick = onNavigateToBlockedApps,
                        testTag = "settings_blocked_apps_row"
                    )

                    SettingsNavigationRow(
                        title = "Strict Mode Rules & Anti-Cheating",
                        subtitle = "Configure emergency pauses, break rules, and PIN protection",
                        icon = Icons.Rounded.Security,
                        iconTint = FocusColors.Primary,
                        onClick = onNavigateToStrictMode,
                        testTag = "settings_strict_mode_row"
                    )

                    SettingsNavigationRow(
                        title = "Browser & Website Shield",
                        subtitle = "Two-engine adult content & custom URL filter across browsers",
                        icon = Icons.Rounded.Language,
                        iconTint = FocusColors.Primary,
                        onClick = onNavigateToWebsiteBlocker,
                        testTag = "settings_website_blocker_row"
                    )

                    SettingsNavigationRow(
                        title = "Approved Study Channels",
                        subtitle = "YouTube Study Mode allowlist & Shorts blocker",
                        icon = Icons.Rounded.SmartDisplay,
                        iconTint = Color(0xFFEF4444),
                        onClick = onNavigateToStudyChannels,
                        testTag = "settings_study_channels_row"
                    )

                    var showAccessibilityTroubleshooting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                    SettingsNavigationRow(
                        title = "Accessibility Blocker Service",
                        subtitle = if (uiState.isAccessibilityEnabled) "Service active and ready" else "Permission required — Tap to enable or fix malfunction",
                        icon = if (uiState.isAccessibilityEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        iconTint = if (uiState.isAccessibilityEnabled) FocusColors.EmeraldSuccess else FocusColors.CoralWarning,
                        onClick = {
                            if (!uiState.isAccessibilityEnabled) {
                                showAccessibilityTroubleshooting = true
                            } else {
                                AccessibilityHelper.openAccessibilitySettings(context)
                            }
                        },
                        testTag = "settings_accessibility_row"
                    )

                    if (showAccessibilityTroubleshooting) {
                        com.example.core.accessibility.AccessibilityTroubleshootingDialog(
                            onDismissRequest = { showAccessibilityTroubleshooting = false }
                        )
                    }

                    SettingsNavigationRow(
                        title = "Background Running & Persistence",
                        subtitle = if (uiState.isBatteryOptimizationIgnored) "Unrestricted background running enabled" else "Battery restriction active — Tap to allow uninterrupted background",
                        icon = if (uiState.isBatteryOptimizationIgnored) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                        iconTint = if (uiState.isBatteryOptimizationIgnored) FocusColors.EmeraldSuccess else FocusColors.Primary,
                        onClick = {
                            com.example.core.permission.FocusPermissionManager.requestIgnoreBatteryOptimization(context)
                        },
                        testTag = "settings_battery_optimization_row"
                    )
                }
            }

            // Section 1.5: Short-Form Content Blocker (Shorts & Reels - Integrated from Shorts-Blocker)
            item {
                SettingsSectionHeader(title = "SHORT-FORM CONTENT BLOCKER (SHORTS & REELS)")
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FocusShapes.card)
                        .background(FocusColors.Surface)
                        .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
                        .padding(FocusSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Always Blocked vs Session Only Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "24/7 Always-On Blocker",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = if (uiState.preferences.isShortsReelsAlwaysBlocked)
                                    "Block Shorts & Reels at all times (even outside study sessions)"
                                else
                                    "Only block Shorts & Reels during active Focus Sessions",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary
                                )
                            )
                        }
                        Switch(
                            checked = uiState.preferences.isShortsReelsAlwaysBlocked,
                            onCheckedChange = { checked ->
                                if (checked && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                    pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                        title = "24/7 Shorts & Reels Blocker",
                                        description = "YouTube Shorts, Instagram Reels, and Facebook Reels at all times",
                                        onGranted = { viewModel.updateShortsReelsAlwaysBlocked(true) }
                                    )
                                } else {
                                    viewModel.updateShortsReelsAlwaysBlocked(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FocusColors.TextOnDark,
                                checkedTrackColor = FocusColors.Primary,
                                uncheckedThumbColor = FocusColors.TextMuted,
                                uncheckedTrackColor = FocusColors.SurfaceSubtle
                            ),
                            modifier = Modifier.testTag("toggle_always_block_shorts")
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(FocusColors.CardBorderSubtle)
                    )

                    // YouTube Shorts Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "YouTube Shorts",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = "Instant back navigation when Shorts feed or video player opens",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary
                                )
                            )
                        }
                        Switch(
                            checked = uiState.preferences.isYouTubeShortsBlockingEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                    pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                        title = "YouTube Shorts Blocker",
                                        description = "YouTube Shorts feeds and video players",
                                        onGranted = { viewModel.updateYouTubeShortsBlocking(true) }
                                    )
                                } else {
                                    viewModel.updateYouTubeShortsBlocking(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FocusColors.TextOnDark,
                                checkedTrackColor = FocusColors.Primary,
                                uncheckedThumbColor = FocusColors.TextMuted,
                                uncheckedTrackColor = FocusColors.SurfaceSubtle
                            ),
                            modifier = Modifier.testTag("toggle_block_yt_shorts")
                        )
                    }

                    // Instagram Reels Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Instagram Reels",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = "Blocks Reels tab and full-screen clips viewer automatically",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary
                                )
                            )
                        }
                        Switch(
                            checked = uiState.preferences.isInstagramReelsBlockingEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                    pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                        title = "Instagram Reels Blocker",
                                        description = "Instagram Reels tab and clips viewer",
                                        onGranted = { viewModel.updateInstagramReelsBlocking(true) }
                                    )
                                } else {
                                    viewModel.updateInstagramReelsBlocking(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FocusColors.TextOnDark,
                                checkedTrackColor = FocusColors.Primary,
                                uncheckedThumbColor = FocusColors.TextMuted,
                                uncheckedTrackColor = FocusColors.SurfaceSubtle
                            ),
                            modifier = Modifier.testTag("toggle_block_ig_reels")
                        )
                    }

                    // Facebook Reels Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Facebook Reels",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = "Blocks Facebook Reels tray and vertical video players",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary
                                )
                            )
                        }
                        Switch(
                            checked = uiState.preferences.isFacebookReelsBlockingEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                    pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                        title = "Facebook Reels Blocker",
                                        description = "Facebook Reels tray and video players",
                                        onGranted = { viewModel.updateFacebookReelsBlocking(true) }
                                    )
                                } else {
                                    viewModel.updateFacebookReelsBlocking(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FocusColors.TextOnDark,
                                checkedTrackColor = FocusColors.Primary,
                                uncheckedThumbColor = FocusColors.TextMuted,
                                uncheckedTrackColor = FocusColors.SurfaceSubtle
                            ),
                            modifier = Modifier.testTag("toggle_block_fb_reels")
                        )
                    }
                }
            }

            // Section 2: Study Defaults
            item {
                SettingsSectionHeader(title = "DEFAULT STUDY PREFERENCES")
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FocusShapes.card)
                        .background(FocusColors.Surface)
                        .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
                        .padding(FocusSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.HourglassBottom,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Default Timer Duration",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = "${uiState.preferences.defaultTimerMinutes} minutes per standard study block",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = FocusColors.AmberOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pomodoro Default Intervals",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = "${uiState.preferences.pomodoroFocusMinutes}m Focus • ${uiState.preferences.pomodoroShortBreakMinutes}m Break • ${uiState.preferences.pomodoroCycles} Cycles",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Section 3: App Information
            item {
                SettingsSectionHeader(title = "ABOUT FOCUSSHIELD")
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FocusShapes.card)
                        .background(FocusColors.Surface)
                        .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
                        .padding(FocusSpacing.lg)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(FocusColors.PrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = FocusColors.Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "FocusShield v1.0",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = "Local-First Study & Anti-Distraction Engine for JEE & NEET",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(FocusSpacing.base))
            }
        }
    }

    // Dynamic Accessibility Permission Prompt Dialog
    pendingAccessibilityPrompt?.let { promptInfo ->
        AccessibilityPermissionRequiredDialog(
            featureTitle = promptInfo.title,
            featureDescription = promptInfo.description,
            onDismissRequest = { pendingAccessibilityPrompt = null },
            onPermissionGranted = {
                promptInfo.onGranted()
                pendingAccessibilityPrompt = null
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
            color = FocusColors.TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontSize = 11.sp
        )
    )
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = FocusSpacing.base, vertical = 14.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = FocusColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
