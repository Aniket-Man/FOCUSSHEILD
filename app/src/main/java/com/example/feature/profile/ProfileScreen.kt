package com.example.feature.profile

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.cloud.auth.AuthState
import com.example.core.accessibility.AccessibilityFeaturePromptInfo
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.accessibility.AccessibilityPermissionRequiredDialog
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusActionRow
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow
import com.example.core.util.ProfilePhotoStorage
import com.example.feature.account.AccountViewModel
import com.example.feature.profile.ui.EditProfileBottomSheet
import com.example.feature.profile.ui.UserProfileAvatar
import com.example.feature.rewards.domain.RewardBadge
import com.example.feature.rewards.ui.ProfileRewardsSummarySection
import com.example.feature.settings.SettingsViewModel
import com.example.feature.update.domain.UpdateState
import com.example.feature.update.ui.UpdateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStudyChannels: () -> Unit,
    onNavigateToStrictMode: () -> Unit,
    onNavigateToAccount: () -> Unit,
    accountViewModel: AccountViewModel,
    onNavigateToOnboarding: () -> Unit = {},
    updateViewModel: UpdateViewModel,
    onNavigateToUpdates: () -> Unit = {},
    onNavigateToRewards: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val saving by settingsViewModel.isSaving.collectAsStateWithLifecycle()
    val profileSaveState by settingsViewModel.profileSaveState.collectAsStateWithLifecycle()
    val operationError by settingsViewModel.operationError.collectAsStateWithLifecycle()
    val strictError by settingsViewModel.strictLockMessage.collectAsStateWithLifecycle()
    val adminActive by settingsViewModel.isDeviceAdminActive.collectAsStateWithLifecycle()
    val pendingAdmin by settingsViewModel.pendingDeviceAdminRequest.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val showUpdateDot by updateViewModel.showDot.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackIsError by rememberSaveable { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<AccessibilityFeaturePromptInfo?>(null) }
    val prefs = state.preferences
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        settingsViewModel.onDeviceAdminRequestResult()
    }
    fun activateAdmin() {
        try { adminLauncher.launch(settingsViewModel.getDeviceAdminIntent()) }
        catch (exception: Exception) {
            Log.w("Profile", "Unable to open device-admin settings", exception)
            feedback = context.getString(R.string.profile_admin_error)
            feedbackIsError = true
        }
    }
    LaunchedEffect(pendingAdmin) {
        if (pendingAdmin) {
            settingsViewModel.onDeviceAdminRequestLaunched()
            activateAdmin()
        }
    }
    // The save runs in SettingsViewModel, so this effect also closes the recreated editor after a
    // rotation instead of leaving it to submit stale values a second time.
    LaunchedEffect(profileSaveState) {
        when (val result = profileSaveState) {
            is SettingsViewModel.ProfileSaveState.Saved -> {
                showEditor = false
                scope.launch { ProfilePhotoStorage.discardStagedCrop(context) }
                feedbackIsError = result.followUpFailure != null
                feedback = context.getString(when {
                    result.followUpFailure != null -> R.string.profile_cloud_queue_error
                    result.queuedForCloud -> R.string.profile_cloud_queued
                    else -> R.string.profile_saved
                })
                result.followUpFailure?.let {
                    Log.w("Profile", "Local profile saved but follow-up work failed", it)
                }
                settingsViewModel.clearProfileSaveState()
            }
            is SettingsViewModel.ProfileSaveState.Failed -> {
                Log.e("Profile", "Profile save failed; previous profile retained", result.exception)
                // Keep the editor open so the user can retry, and clear the state once the editor
                // has consumed it as a dismissible message.
            }
            else -> Unit
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.checkAccessibilityStatus()
                settingsViewModel.checkDeviceAdminStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { FocusScreenHeader(stringResource(R.string.profile_title), onNavigateBack,
            Modifier.statusBarsPadding(), backButtonModifier = Modifier.testTag("profile_back_button")) },
        containerColor = FocusColors.Background,
        modifier = modifier.navigationBarsPadding().testTag("profile_screen")
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.xl)) {
            item {
                FocusStatusMessage(strictError ?: operationError, isError = true,
                    onDismiss = { settingsViewModel.clearStrictLockMessage(); settingsViewModel.clearOperationError() })
                FocusStatusMessage(feedback, isError = feedbackIsError, onDismiss = { feedback = null })
            }
            item {
                ProfileCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                        // The edit button carries the action; the adjacent photo is decorative.
                        UserProfileAvatar(prefs.userPhotoUri, prefs.userAvatarPreset, size = 64.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                            Text(prefs.userName.ifBlank { stringResource(R.string.profile_default_name) },
                                color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                            Text(prefs.userMotto.ifBlank { stringResource(R.string.profile_default_motto) },
                                color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            if (prefs.userAcademicGoal.isNotBlank()) Text(stringResource(R.string.profile_academic_goal, prefs.userAcademicGoal),
                                color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton({ showEditor = true }, enabled = !saving, modifier = Modifier.testTag("profile_edit_icon_button")) {
                            Icon(Icons.Rounded.Edit, stringResource(R.string.profile_edit), tint = FocusColors.Primary)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                        ProfileMetric(stringResource(R.string.profile_daily_goal), stringResource(R.string.profile_goal_duration,
                            prefs.dailyGoalMinutes / 60, prefs.dailyGoalMinutes % 60))
                        ProfileMetric(stringResource(R.string.profile_lifetime_focus), state.formattedAllTimeStudyTime)
                        ProfileMetric(stringResource(R.string.profile_badges), stringResource(R.string.profile_badge_count,
                            state.badgeUnlockTimes.size, RewardBadge.ALL_BADGES.size))
                    }
                }
            }
            item {
                ProfileNavigationRow(stringResource(R.string.profile_account),
                    stringResource(if (accountState.auth is AuthState.Authenticated) R.string.profile_account_signed_in else R.string.profile_account_local),
                    Icons.Rounded.CloudDone, onNavigateToAccount, "profile_account_nav_row")
            }
            item {
                ProfileRewardsSummarySection(totalLifetimeStudyMillis = state.allTimeStudyTimeMillis,
                    allTimeStudyTimeFormatted = state.formattedAllTimeStudyTime, claimedRewardIds = prefs.claimedRewardIds,
                    badgeUnlockTimes = state.badgeUnlockTimes, onNavigateToAllRewards = onNavigateToRewards,
                    onClaimReward = { if (!saving) settingsViewModel.claimReward(it) })
            }
            item {
                ProfileSectionTitle(stringResource(R.string.profile_theme_section))
                ThemeShifterCard(prefs.themeMode, settingsViewModel::updateThemeMode, enabled = !saving)
            }
            item {
                ProfileSectionTitle(stringResource(R.string.profile_history_section))
                ProfileNavigationRow(stringResource(R.string.profile_history), stringResource(R.string.profile_history_body),
                    Icons.Rounded.History, onNavigateToHistory, "profile_session_history_row")
            }
            item {
                ProfileSectionTitle(stringResource(R.string.profile_protection_section))
                Surface(color = FocusColors.Surface, shape = FocusShapes.card) {
                    Column {
                        FocusSwitchRow(stringResource(R.string.profile_uninstall), prefs.isBlockUninstallEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                    prompt = AccessibilityFeaturePromptInfo(context.getString(R.string.profile_uninstall),
                                        context.getString(R.string.profile_uninstall_permission), onGranted = { settingsViewModel.updateBlockUninstall(true) })
                                } else settingsViewModel.updateBlockUninstall(enabled)
                            }, enabled = !saving, modifier = Modifier.testTag("profile_prevent_uninstall_switch"),
                            description = stringResource(when {
                                prefs.isBlockUninstallEnabled && adminActive -> R.string.profile_uninstall_active
                                prefs.isBlockUninstallEnabled -> R.string.profile_uninstall_pending
                                else -> R.string.profile_uninstall_off
                            }))
                        if (prefs.isBlockUninstallEnabled && !adminActive) FocusActionRow(stringResource(R.string.profile_activate_admin), ::activateAdmin)
                    }
                }
                ProfileNavigationRow(stringResource(R.string.profile_channels), stringResource(R.string.profile_channels_body),
                    Icons.Rounded.SmartDisplay, onNavigateToStudyChannels, "profile_study_channels_row")
                ProfileNavigationRow(stringResource(R.string.profile_strict), stringResource(R.string.profile_strict_body),
                    Icons.Rounded.Security, onNavigateToStrictMode, "profile_strict_mode_row")
                Surface(color = FocusColors.Surface, shape = FocusShapes.card) {
                    FocusActionRow(stringResource(R.string.settings_accessibility), { AccessibilityHelper.openAccessibilitySettings(context) },
                        value = stringResource(if (state.isAccessibilityEnabled) R.string.settings_service_enabled else R.string.settings_service_disabled),
                        description = stringResource(R.string.settings_accessibility_body), modifier = Modifier.testTag("profile_accessibility_row"))
                }
            }
            item {
                ProfileSectionTitle(stringResource(R.string.settings_about_section))
                val updateDescription = when (val update = updateState) {
                    is UpdateState.Downloaded -> stringResource(R.string.profile_update_downloaded, update.info.versionName)
                    is UpdateState.Downloading -> stringResource(R.string.profile_update_downloading)
                    is UpdateState.UpdateAvailable -> stringResource(R.string.profile_update_available, update.info.versionName)
                    else -> stringResource(R.string.profile_update_check)
                }
                ProfileNavigationRow(stringResource(R.string.profile_updates), updateDescription, Icons.Rounded.SystemUpdate,
                    onNavigateToUpdates, "profile_updates_row", showBadge = showUpdateDot)
                ProfileCard {
                    Text(stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME), color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.settings_about_body), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (showEditor) EditProfileBottomSheet(
        currentName = prefs.userName, currentPhotoUri = prefs.userPhotoUri, currentAvatarPreset = prefs.userAvatarPreset,
        currentMotto = prefs.userMotto, currentAcademicGoal = prefs.userAcademicGoal, currentDailyGoalMinutes = prefs.dailyGoalMinutes,
        saving = profileSaveState is SettingsViewModel.ProfileSaveState.Saving,
        saveError = (profileSaveState as? SettingsViewModel.ProfileSaveState.Failed)
            ?.let { context.getString(R.string.profile_save_error) },
        onDismissSaveError = settingsViewModel::clearProfileSaveState,
        onDismiss = {
            if (profileSaveState !is SettingsViewModel.ProfileSaveState.Saving) {
                showEditor = false
                scope.launch { ProfilePhotoStorage.discardStagedCrop(context) }
            }
        },
        onSaveProfile = { name, photo, preset, motto, goal, minutes ->
            settingsViewModel.saveProfileWithPhoto(
                userName = name,
                selectedPhoto = photo,
                avatarPreset = preset,
                motto = motto,
                academicGoal = goal,
                dailyGoalMinutes = minutes
            )
        }
    )
    prompt?.let { info -> AccessibilityPermissionRequiredDialog(info.title, info.description,
        featureKey = info.featureKey, onDismissRequest = { prompt = null }, onPermissionGranted = { info.onGranted(); prompt = null }) }
}

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(title, color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = FocusSpacing.sm).semantics { heading() })
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Surface(color = FocusColors.Surface, shape = FocusShapes.card, border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)) {
        Column(Modifier.fillMaxWidth().padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)) { content() }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
        Text(label, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeShifterCard(currentThemeMode: String, onThemeSelected: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(color = FocusColors.Surface, shape = FocusShapes.card, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
            Text(stringResource(R.string.profile_theme_mode), color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                listOf("LIGHT" to R.string.profile_theme_light, "DARK" to R.string.profile_theme_dark, "SYSTEM" to R.string.profile_theme_system).forEach { (key, label) ->
                    FilterChip(selected = currentThemeMode.equals(key, ignoreCase = true), onClick = { onThemeSelected(key) }, enabled = enabled,
                        label = { Text(stringResource(label)) }, modifier = Modifier.testTag("theme_shifter_${key.lowercase(java.util.Locale.ROOT)}"))
                }
            }
        }
    }
}

@Composable
private fun ProfileNavigationRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, testTag: String, showBadge: Boolean = false) {
    Surface(onClick = onClick, color = FocusColors.Surface, shape = FocusShapes.card, modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Row(Modifier.padding(FocusSpacing.base), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = FocusColors.Primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                Text(title, color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                if (showBadge) Text(stringResource(R.string.update_available_status), color = FocusColors.EmeraldText, style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = FocusColors.TextSecondary, modifier = Modifier.size(24.dp))
        }
    }
}
