package com.example.feature.strictmode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoAdultContent
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.feature.session.SessionViewModel
import com.example.feature.session.domain.StrictModeConfig
import com.example.feature.settings.SettingsViewModel

/**
 * Screen: Strict Mode Configuration.
 * Fully compliant with supported Android and FocusShield mechanisms.
 */
@Composable
fun StrictModeScreen(
    sessionViewModel: SessionViewModel,
    settingsViewModel: SettingsViewModel? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val setupState by sessionViewModel.setupState.collectAsStateWithLifecycle()
    val activeSessionState by sessionViewModel.activeSessionState.collectAsStateWithLifecycle()

    var config by remember(setupState.strictModeConfig) {
        mutableStateOf(setupState.strictModeConfig)
    }

    var showEnableStrictDialog by remember { mutableStateOf(false) }

    val isDeviceAdminActive = if (settingsViewModel != null) {
        settingsViewModel.isDeviceAdminActive.collectAsStateWithLifecycle().value
    } else false
    val pendingDeviceAdminRequest = if (settingsViewModel != null) {
        settingsViewModel.pendingDeviceAdminRequest.collectAsStateWithLifecycle().value
    } else false
    val deviceAdminLauncher = if (settingsViewModel != null) {
        rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) {
            settingsViewModel.onDeviceAdminRequestResult()
        }
    } else null

    LaunchedEffect(pendingDeviceAdminRequest) {
        if (pendingDeviceAdminRequest && deviceAdminLauncher != null && settingsViewModel != null) {
            deviceAdminLauncher.launch(settingsViewModel.getDeviceAdminIntent())
        }
    }

    // Confirmation dialog when enabling Strict Mode
    if (showEnableStrictDialog) {
        AlertDialog(
            onDismissRequest = { showEnableStrictDialog = false },
            containerColor = FocusColors.Surface,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = FocusColors.CoralWarning,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Enable App-Wide Strict Mode?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
            },
            text = {
                Text(
                    text = "Strict Mode locks all anti-cheating, app limits, session controls, and uninstall protections across FocusShield. Early exit, pausing, and bypassing will require rigorous discipline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newConfig = config.copy(enabled = true)
                        config = newConfig
                        sessionViewModel.updateStrictModeConfig(newConfig)
                        showEnableStrictDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.Primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_enable_strict_button")
                ) {
                    Text("Enable Strict Mode", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEnableStrictDialog = false },
                    modifier = Modifier.testTag("dismiss_enable_strict_button")
                ) {
                    Text("Cancel", color = FocusColors.TextSecondary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.base, vertical = FocusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("strict_mode_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Strict Mode & Anti-Cheating",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("strict_mode_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.base)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Hero Banner Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.large)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                FocusColors.PrimaryDark,
                                FocusColors.Primary
                            )
                        )
                    )
                    .padding(FocusSpacing.lg)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "App-Wide Strict Protection",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (config.enabled) "Active • Maximum Anti-Cheating Discipline" else "Disabled • Standard Mode",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                    Text(
                        text = "Eliminate bypass impulses during study, cap daily app usage strictly, and prevent bypassing via system settings, split screen, or uninstall attempts.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            // Master Switch Card
            Card(
                shape = FocusShapes.large,
                colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FocusSpacing.base),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Strict Mode All Over App",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Enforce rigorous study locks & daily app limits",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                showEnableStrictDialog = true
                            } else {
                                val newConfig = config.copy(enabled = false)
                                config = newConfig
                                sessionViewModel.updateStrictModeConfig(newConfig)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FocusColors.Primary,
                            uncheckedThumbColor = FocusColors.TextSecondary,
                            uncheckedTrackColor = FocusColors.SurfaceVariant
                        ),
                        modifier = Modifier.testTag("strict_mode_master_switch")
                    )
                }
            }

            // SECTION 1: FOCUS SESSIONS
            Text(
                text = "FOCUS SESSION ENFORCEMENT",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextSecondary,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )

            Card(
                shape = FocusShapes.large,
                colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option 1: App Blocking Enforced
                    StrictModeOptionRow(
                        icon = Icons.Outlined.Apps,
                        title = "App Blocking Enforced",
                        subtitle = "Immediately intercepts configured distracting apps during study",
                        checked = config.blockSelectedApps,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(blockSelectedApps = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_block_apps_switch"
                    )

                    HorizontalDivider(color = FocusColors.CardBorderSubtle, modifier = Modifier.padding(horizontal = FocusSpacing.base))

                    // Option 2: Confirm Before Pause
                    StrictModeOptionRow(
                        icon = Icons.Outlined.PauseCircleOutline,
                        title = "Confirm Before Pausing",
                        subtitle = "Requires an extra confirmation to pause the timer to prevent procrastination",
                        checked = config.requireConfirmOnPause,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(requireConfirmOnPause = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_confirm_pause_switch"
                    )

                    HorizontalDivider(color = FocusColors.CardBorderSubtle, modifier = Modifier.padding(horizontal = FocusSpacing.base))

                    // Option 3: Locked Session (Zero Early Exit)
                    StrictModeOptionRow(
                        icon = Icons.Outlined.StopCircle,
                        title = "Lock Session (Zero Early Exit)",
                        subtitle = "Session cannot be ended early even with discipline confirmation until the timer completes",
                        checked = config.requireConfirmOnEnd,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(requireConfirmOnEnd = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_confirm_end_switch"
                    )

                    HorizontalDivider(color = FocusColors.CardBorderSubtle, modifier = Modifier.padding(horizontal = FocusSpacing.base))

                    // Option 4: Confirm Before Break
                    StrictModeOptionRow(
                        icon = Icons.Outlined.Lock,
                        title = "Strict Break Policy",
                        subtitle = "Confirm break duration before temporarily lifting app shields",
                        checked = config.requireConfirmOnBreak,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(requireConfirmOnBreak = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_confirm_break_switch"
                    )
                }
            }

            // SECTION 2: APP LIMITS & ANTI-BYPASS
            Text(
                text = "APP LIMITS & ANTI-BYPASS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextSecondary,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )

            Card(
                shape = FocusShapes.large,
                colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option 5: App Limit Strict Lock
                    StrictModeOptionRow(
                        icon = Icons.Rounded.Lock,
                        title = "Lock App Limits (Strict Cap)",
                        subtitle = "Prevents modifying, deleting, or disabling daily app limits once set for today",
                        checked = config.lockAppLimits,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(lockAppLimits = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_lock_app_limits_switch"
                    )

                    HorizontalDivider(color = FocusColors.CardBorderSubtle, modifier = Modifier.padding(horizontal = FocusSpacing.base))

                    // Option 6: Disable Emergency Bypass
                    StrictModeOptionRow(
                        icon = Icons.Outlined.WarningAmber,
                        title = "Restrict Emergency Bypasses",
                        subtitle = "Disallow using extra emergency time once daily allowance is exhausted",
                        checked = config.disableEmergencyBypass,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(disableEmergencyBypass = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_disable_emergency_switch"
                    )

                    HorizontalDivider(color = FocusColors.CardBorderSubtle, modifier = Modifier.padding(horizontal = FocusSpacing.base))

                    // Option 7: Anti-Uninstall & Tamper Protection — activates Device Admin
                    StrictModeOptionRow(
                        icon = Icons.Rounded.AdminPanelSettings,
                        title = "Block App Uninstall & Tamper",
                        subtitle = when {
                            config.blockUninstallTamper && isDeviceAdminActive -> "Device Admin active — Uninstallation fully blocked"
                            config.blockUninstallTamper && !isDeviceAdminActive -> "Pending — Tap to activate Device Admin"
                            else -> "Prevents opening Settings > Apps to force-stop or uninstall FocusShield"
                        },
                        checked = config.blockUninstallTamper,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(blockUninstallTamper = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                            if (settingsViewModel != null) {
                                settingsViewModel.updateBlockUninstall(isChecked)
                            }
                        },
                        testTag = "strict_anti_uninstall_switch"
                    )

                    HorizontalDivider(color = FocusColors.CardBorderSubtle, modifier = Modifier.padding(horizontal = FocusSpacing.base))

                    // Option 8: Split-Screen & Floating Window Block
                    StrictModeOptionRow(
                        icon = Icons.Outlined.PhoneAndroid,
                        title = "Block Split-Screen & PIP",
                        subtitle = "Intercepts split-screen or floating window attempts to bypass study mode",
                        checked = config.blockSplitScreen,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(
                                blockSplitScreen = isChecked,
                                blockFloatingWindow = isChecked
                            )
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_split_screen_switch"
                    )
                }
            }

            // SECTION 3: DISTRACTION & CONTENT SHIELD
            Text(
                text = "DISTRACTION & CONTENT FILTER",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextSecondary,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )

            Card(
                shape = FocusShapes.large,
                colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Option 9: Block Adult & Distracting Websites
                    StrictModeOptionRow(
                        icon = Icons.Outlined.Web,
                        title = "Block Adult & 18+ Websites",
                        subtitle = "Instant URL detection and blocking in Chrome, Brave, Samsung Internet, and Firefox",
                        checked = config.blockAdultAndDistractingWebsites,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(blockAdultAndDistractingWebsites = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_block_adult_sites_switch"
                    )

                    HorizontalDivider(color = FocusColors.CardBorderSubtle, modifier = Modifier.padding(horizontal = FocusSpacing.base))

                    // Option 10: Shorts, Reels & Infinite Feeds
                    StrictModeOptionRow(
                        icon = Icons.Outlined.SmartDisplay,
                        title = "Block YouTube Shorts & Reels",
                        subtitle = "Enforce immediate blocking of YouTube Shorts, Instagram Reels, and Facebook Reels",
                        checked = config.blockShortsAndReels,
                        enabled = config.enabled,
                        onCheckedChange = { isChecked ->
                            val updated = config.copy(blockShortsAndReels = isChecked)
                            config = updated
                            sessionViewModel.updateStrictModeConfig(updated)
                        },
                        testTag = "strict_block_shorts_reels_switch"
                    )
                }
            }

            // Real Accessibility Service Status Check
            val isServiceEnabled = AccessibilityHelper.isAccessibilityServiceEnabled(context)
            if (!isServiceEnabled) {
                Card(
                    shape = FocusShapes.large,
                    colors = CardDefaults.cardColors(containerColor = FocusColors.AmberLight.copy(alpha = 0.35f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.AmberOrange.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FocusSpacing.base),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = FocusColors.AmberOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Permission Required",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = "Enable FocusShield in Settings to enforce app blocking.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary
                                )
                            )
                        }
                        TextButton(
                            onClick = { AccessibilityHelper.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.textButtonColors(contentColor = FocusColors.Primary),
                            modifier = Modifier.testTag("enable_accessibility_button")
                        ) {
                            Text("Enable", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StrictModeOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(FocusSpacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (enabled && checked) FocusColors.PrimaryLight.copy(alpha = 0.15f)
                    else FocusColors.SurfaceVariant.copy(alpha = 0.4f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled && checked) FocusColors.Primary else FocusColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) FocusColors.TextPrimary else FocusColors.TextSecondary
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
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FocusColors.Primary,
                uncheckedThumbColor = FocusColors.TextSecondary,
                uncheckedTrackColor = FocusColors.SurfaceVariant
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
