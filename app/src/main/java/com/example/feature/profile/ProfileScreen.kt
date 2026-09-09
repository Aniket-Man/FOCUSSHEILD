package com.example.feature.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.feature.settings.SettingsViewModel
import com.example.feature.rewards.ui.ProfileRewardsSection
import com.example.feature.profile.ui.UserProfileAvatar
import com.example.feature.profile.ui.EditProfileBottomSheet

@Composable
fun ProfileScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStudyChannels: () -> Unit,
    onNavigateToStrictMode: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var pendingAccessibilityPrompt by remember { mutableStateOf<AccessibilityFeaturePromptInfo?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.checkAccessibilityStatus()
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
                    modifier = Modifier.testTag("profile_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scholar Profile",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 20.sp
                    )
                )
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("profile_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = FocusSpacing.screenHorizontal)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
            ) {
                // Profile Card Hero
                item {
                    val prefs = uiState.preferences

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FocusShapes.card)
                            .background(FocusColors.Surface)
                            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar with upload badge
                                UserProfileAvatar(
                                    photoUri = prefs.userPhotoUri,
                                    avatarPresetId = prefs.userAvatarPreset,
                                    size = 64.dp,
                                    showEditBadge = true,
                                    onEditClick = { showEditProfileSheet = true }
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prefs.userName.ifBlank { "Focus Scholar" },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = FocusColors.TextPrimary,
                                            fontSize = 18.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = prefs.userMotto.ifBlank { "Deep Work & Daily Mastery" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = FocusColors.TextSecondary,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (prefs.userAcademicGoal.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "🎯 ${prefs.userAcademicGoal}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = FocusColors.Primary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showEditProfileSheet = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(FocusColors.SurfaceVariant)
                                        .testTag("profile_edit_icon_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = FocusColors.Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Quick Stats Pill Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = FocusColors.SurfaceSubtle,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${prefs.dailyGoalMinutes / 60}h Target",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = FocusColors.TextPrimary,
                                                fontSize = 12.sp
                                            )
                                        )
                                        Text(
                                            text = "Daily Goal",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = FocusColors.TextMuted,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = FocusColors.SurfaceSubtle,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = uiState.formattedAllTimeStudyTime,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = FocusColors.AmberOrange,
                                                fontSize = 12.sp
                                            )
                                        )
                                        Text(
                                            text = "Lifetime Focus",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = FocusColors.TextMuted,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = FocusColors.SurfaceSubtle,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${prefs.claimedRewardIds.size} / 11",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = FocusColors.EmeraldSuccess,
                                                fontSize = 12.sp
                                            )
                                        )
                                        Text(
                                            text = "Badges Won",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = FocusColors.TextMuted,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Rewards & Milestones Section (Horizontal Carousel Cards)
                item {
                    ProfileRewardsSection(
                        totalLifetimeStudyMillis = uiState.allTimeStudyTimeMillis,
                        allTimeStudyTimeFormatted = uiState.formattedAllTimeStudyTime,
                        claimedRewardIds = uiState.preferences.claimedRewardIds,
                        onClaimReward = { rewardId -> settingsViewModel.claimReward(rewardId) }
                    )
                }

                // Theme Switcher Section (Dark / Light / System)
                item {
                    Text(
                        text = "APP THEME & APPEARANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeShifterCard(
                        currentThemeMode = uiState.preferences.themeMode,
                        onThemeSelected = { mode ->
                            settingsViewModel.updateThemeMode(mode)
                        }
                    )
                }

                // Session History Section
                item {
                    Text(
                        text = "STUDY LOGS & SESSIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ProfileNavigationRow(
                        title = "Session History",
                        subtitle = "View detailed logs, subject distribution, and duration records",
                        icon = Icons.Rounded.History,
                        iconTint = FocusColors.Primary,
                        onClick = onNavigateToHistory,
                        testTag = "profile_session_history_row"
                    )
                }

                // Shield Protections & Controls
                item {
                    Text(
                        text = "SHIELD & CONTROLS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Prevent Uninstall Protection Switch — activates Device Admin
                        ProfileSwitchRow(
                            title = "Prevent App Uninstall",
                            subtitle = when {
                                uiState.preferences.isBlockUninstallEnabled && isDeviceAdminActive ->
                                    "Active — Device Admin enabled, uninstallation blocked"
                                uiState.preferences.isBlockUninstallEnabled && !isDeviceAdminActive ->
                                    "Pending — Tap to activate Device Admin protection"
                                else ->
                                    "Disabled — Prevents deleting FocusShield during study"
                            },
                            icon = Icons.Rounded.Shield,
                            iconTint = when {
                                uiState.preferences.isBlockUninstallEnabled && isDeviceAdminActive -> FocusColors.EmeraldSuccess
                                uiState.preferences.isBlockUninstallEnabled && !isDeviceAdminActive -> FocusColors.AmberOrange
                                else -> FocusColors.Primary
                            },
                            checked = uiState.preferences.isBlockUninstallEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                    pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                        title = "App Uninstall Protection",
                                        description = "system settings to prevent FocusShield from being uninstalled",
                                        onGranted = { settingsViewModel.updateBlockUninstall(true) }
                                    )
                                } else {
                                    settingsViewModel.updateBlockUninstall(checked)
                                }
                            },
                            testTag = "profile_prevent_uninstall_switch"
                        )

                        ProfileNavigationRow(
                            title = "Approved Study Channels",
                            subtitle = "YouTube Study Mode allowlist & Shorts blocker",
                            icon = Icons.Rounded.SmartDisplay,
                            iconTint = Color(0xFFEF4444),
                            onClick = onNavigateToStudyChannels,
                            testTag = "profile_study_channels_row"
                        )

                        ProfileNavigationRow(
                            title = "Strict Mode Rules & Anti-Cheating",
                            subtitle = "Configure emergency pauses, break rules, and PIN protection",
                            icon = Icons.Rounded.Security,
                            iconTint = FocusColors.Primary,
                            onClick = onNavigateToStrictMode,
                            testTag = "profile_strict_mode_row"
                        )

                        ProfileNavigationRow(
                            title = "Accessibility Blocker Service",
                            subtitle = if (uiState.isAccessibilityEnabled) "Service active and ready" else "Permission required — Tap to enable",
                            icon = if (uiState.isAccessibilityEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            iconTint = if (uiState.isAccessibilityEnabled) FocusColors.EmeraldSuccess else FocusColors.CoralWarning,
                            onClick = { AccessibilityHelper.openAccessibilitySettings(context) },
                            testTag = "profile_accessibility_row"
                        )

                        ProfileNavigationRow(
                            title = "App Tour & System Setup Wizard",
                            subtitle = "Revisit the 3-step onboarding and permissions walkthrough",
                            icon = Icons.Rounded.Security,
                            iconTint = FocusColors.Primary,
                            onClick = onNavigateToOnboarding,
                            testTag = "profile_onboarding_row"
                        )
                    }
                }

                // Study Defaults
                item {
                    Text(
                        text = "DEFAULT STUDY PREFERENCES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Default Timer Duration",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = "${uiState.preferences.defaultTimerMinutes} minutes per standard study block",
                                    style = MaterialTheme.typography.bodySmall.copy(
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pomodoro Default Intervals",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = "${uiState.preferences.pomodoroFocusMinutes}m Focus • ${uiState.preferences.pomodoroShortBreakMinutes}m Break • ${uiState.preferences.pomodoroCycles} Cycles",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }

                // About FocusShield
                item {
                    Text(
                        text = "ABOUT FOCUSSHIELD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FocusShield v1.0",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = "Local-First Study & Anti-Distraction Engine for JEE & NEET",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(FocusSpacing.xl))
                }
            }
        }

        // Edit Profile Sheet
        if (showEditProfileSheet) {
            val prefs = uiState.preferences
            EditProfileBottomSheet(
                currentName = prefs.userName,
                currentPhotoUri = prefs.userPhotoUri,
                currentAvatarPreset = prefs.userAvatarPreset,
                currentMotto = prefs.userMotto,
                currentAcademicGoal = prefs.userAcademicGoal,
                currentDailyGoalMinutes = prefs.dailyGoalMinutes,
                onDismiss = { showEditProfileSheet = false },
                onSaveProfile = { name, photo, preset, motto, goal, minutes ->
                    settingsViewModel.updateUserProfile(
                        userName = name,
                        photoUri = photo,
                        avatarPreset = preset,
                        motto = motto,
                        academicGoal = goal,
                        dailyGoalMinutes = minutes
                    )
                }
            )
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
}

/**
 * 3-way Theme Shifter Card (Light / Dark / System Default) with smooth selection transitions
 */
@Composable
fun ThemeShifterCard(
    currentThemeMode: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        Triple("LIGHT", "Light", Icons.Rounded.LightMode),
        Triple("DARK", "Dark", Icons.Rounded.DarkMode),
        Triple("SYSTEM", "System", Icons.Rounded.BrightnessAuto)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 14.sp
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when (currentThemeMode) {
                        "LIGHT" -> "Light"
                        "DARK" -> "Dark"
                        else -> "System Default"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }

            // 3 Segmented Pill Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FocusColors.SurfaceSubtle)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                modes.forEach { (modeKey, title, icon) ->
                    val isSelected = currentThemeMode.equals(modeKey, ignoreCase = true)

                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) FocusColors.Primary else Color.Transparent,
                        animationSpec = tween(durationMillis = 200),
                        label = "theme_btn_container_$modeKey"
                    )

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) FocusColors.TextOnDark else FocusColors.TextSecondary,
                        animationSpec = tween(durationMillis = 200),
                        label = "theme_btn_content_$modeKey"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(containerColor)
                            .clickable { onThemeSelected(modeKey) }
                            .testTag("theme_shifter_${modeKey.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
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
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FocusColors.TextOnDark,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = FocusColors.TextMuted,
                    uncheckedTrackColor = FocusColors.SurfaceSubtle
                ),
                modifier = Modifier.testTag("${testTag}_switch")
            )
        }
    }
}

@Composable
private fun ProfileNavigationRow(
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
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
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
