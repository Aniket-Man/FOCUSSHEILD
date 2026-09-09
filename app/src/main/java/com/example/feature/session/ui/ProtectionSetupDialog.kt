package com.example.feature.session.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.permission.FocusPermissionManager
import com.example.core.permission.ProtectionPermissionStatus

/**
 * Educational setup & permission verification dialog shown before starting a protected session.
 * Clearly guides the student through granting the necessary Android permissions:
 * 1. "Display over other apps" (SYSTEM_ALERT_WINDOW)
 * 2. "Accessibility Service" (FocusAccessibilityService)
 * 3. "Notifications" (POST_NOTIFICATIONS)
 */
@Composable
fun ProtectionSetupDialog(
    permissionStatus: ProtectionPermissionStatus,
    onRefreshPermissions: () -> Unit,
    onConfirmStart: () -> Unit,
    onStartWithoutBlocking: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAccessibilityActive by com.example.core.accessibility.AccessibilityHelper.isServiceEnabledFlow.collectAsState()

    // 1. Immediately refresh whenever service connects or flow emits
    LaunchedEffect(isAccessibilityActive) {
        onRefreshPermissions()
    }

    // 2. Refresh immediately upon returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                com.example.core.accessibility.AccessibilityHelper.updateState(context)
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 3. Active ticker to catch immediate grant without delay
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            val currentAcc = com.example.core.accessibility.AccessibilityHelper.isAccessibilityServiceEnabled(context)
            val currentOverlay = FocusPermissionManager.isOverlayPermissionGranted(context)
            if (currentAcc != permissionStatus.isAccessibilityEnabled || currentOverlay != permissionStatus.isOverlayGranted) {
                com.example.core.accessibility.AccessibilityHelper.updateState(context)
                onRefreshPermissions()
            }
        }
    }

    val allMandatoryGranted = permissionStatus.isOverlayGranted && permissionStatus.isAccessibilityEnabled

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("protection_setup_dialog"),
        shape = FocusShapes.large,
        containerColor = FocusColors.Surface,
        titleContentColor = FocusColors.TextPrimary,
        textContentColor = FocusColors.TextSecondary,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(FocusColors.PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Focus Shield Setup",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "To protect your deep focus and filter YouTube distractions during your session, Android requires two quick permissions:",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.5.sp
                    )
                )

                // 1. Overlay Permission Card
                PermissionItemCard(
                    title = "Display over other apps",
                    description = "Shows the focus lock screen when a blocked app is opened.",
                    icon = Icons.Rounded.Layers,
                    isGranted = permissionStatus.isOverlayGranted,
                    isRequired = true,
                    onClick = {
                        FocusPermissionManager.openOverlaySettings(context)
                        onRefreshPermissions()
                    },
                    testTag = "overlay_permission_item"
                )

                var showAccessibilityTroubleshooting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                // 2. Accessibility Service Card
                PermissionItemCard(
                    title = "Accessibility Service",
                    description = if (permissionStatus.isAccessibilityEnabled) "Enables YouTube Study Mode and blocks distracting apps & Shorts." else "Enables YouTube Study Mode & blocks Shorts. Tap to enable in Settings.",
                    icon = Icons.Rounded.Shield,
                    isGranted = permissionStatus.isAccessibilityEnabled,
                    isRequired = true,
                    onClick = {
                        FocusPermissionManager.openAccessibilitySettings(context)
                        onRefreshPermissions()
                    },
                    testTag = "accessibility_permission_item"
                )

                if (showAccessibilityTroubleshooting) {
                    com.example.core.accessibility.AccessibilityTroubleshootingDialog(
                        onDismissRequest = {
                            showAccessibilityTroubleshooting = false
                            onRefreshPermissions()
                        }
                    )
                }

                // 3. Background Persistence Card
                PermissionItemCard(
                    title = "Background Protection",
                    description = "Keeps timers & blockers running if FocusShield is swiped from Recent Apps.",
                    icon = Icons.Rounded.Security,
                    isGranted = permissionStatus.isBatteryOptimizationIgnored,
                    isRequired = false,
                    onClick = {
                        FocusPermissionManager.requestIgnoreBatteryOptimization(context)
                        onRefreshPermissions()
                    },
                    testTag = "battery_optimization_permission_item"
                )

                // 4. Notification Permission Card (Optional / Recommended)
                PermissionItemCard(
                    title = "Notifications",
                    description = "Sends cycle timers and session complete alerts.",
                    icon = Icons.Rounded.Notifications,
                    isGranted = permissionStatus.isNotificationGranted,
                    isRequired = false,
                    onClick = {
                        FocusPermissionManager.openNotificationSettings(context)
                        onRefreshPermissions()
                    },
                    testTag = "notification_permission_item"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (allMandatoryGranted) {
                        onConfirmStart()
                    } else {
                        onRefreshPermissions()
                    }
                },
                enabled = allMandatoryGranted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = Color.White
                ),
                shape = FocusShapes.medium,
                modifier = Modifier.testTag("confirm_start_protected_session_button")
            ) {
                Text(
                    text = if (allMandatoryGranted) "Start Protected Session" else "Enable Required Above",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onStartWithoutBlocking,
                    modifier = Modifier.testTag("start_without_blocking_button")
                ) {
                    Text(
                        text = "Timer Only",
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel_setup_dialog_button")
                ) {
                    Text("Cancel", color = FocusColors.TextSecondary)
                }
            }
        }
    )
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    isRequired: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(FocusShapes.medium)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = FocusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) FocusColors.EmeraldSuccess.copy(alpha = 0.08f) else FocusColors.SurfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (isGranted) FocusColors.EmeraldSuccess.copy(alpha = 0.3f) else FocusColors.CardBorderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FocusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) FocusColors.EmeraldSuccess.copy(alpha = 0.15f) else FocusColors.Primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) FocusColors.EmeraldSuccess else FocusColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 13.5.sp
                            )
                        )
                        if (isRequired && !isGranted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "• Required",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.CoralWarning,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .clip(FocusShapes.pill)
                        .background(FocusColors.EmeraldSuccess.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Granted",
                            tint = FocusColors.EmeraldSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Granted",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.EmeraldSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    shape = FocusShapes.pill,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FocusColors.Primary
                    ),
                    border = BorderStroke(1.dp, FocusColors.Primary.copy(alpha = 0.5f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Enable",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
