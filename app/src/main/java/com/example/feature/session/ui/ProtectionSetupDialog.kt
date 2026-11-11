package com.example.feature.session.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.permission.FocusPermissionManager
import com.example.core.permission.ProtectionPermissionStatus

/**
 * Educational setup and permission verification dialog shown before starting a protected session.
 * It guides the student through the Android permissions protection actually uses.
 */
@Composable
fun ProtectionSetupDialog(
    permissionStatus: ProtectionPermissionStatus,
    onRefreshPermissions: () -> Unit,
    onConfirmStart: () -> Unit,
    onStartWithoutBlocking: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    dismissActionLabel: String? = null
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

    // 3. Active ticker to catch an immediate grant without waiting for ON_RESUME.
    //
    // `rememberUpdatedState` is what makes this correct. The loop used to close over the
    // `permissionStatus` of the *first* composition: once the user granted something, the freshly
    // read values never matched that frozen snapshot again, so the comparison stayed true and the
    // dialog called `onRefreshPermissions()` every 400 ms for as long as it was open (issue #28).
    // Reading through an updated state means each tick compares against the status the dialog is
    // currently showing, so the poll goes quiet again as soon as the UI has caught up.
    val currentStatus by rememberUpdatedState(permissionStatus)
    val currentRefresh by rememberUpdatedState(onRefreshPermissions)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            val currentAcc = com.example.core.accessibility.AccessibilityHelper.isAccessibilityServiceEnabled(context)
            val currentOverlay = FocusPermissionManager.isOverlayPermissionGranted(context)
            // The exact-alarm grant can be changed from this dialog's own row (and revoked by the
            // system at any time), so it is polled with the rest.
            val currentExactAlarm = FocusPermissionManager.isExactAlarmGranted(context)
            if (currentAcc != currentStatus.isAccessibilityEnabled ||
                currentOverlay != currentStatus.isOverlayGranted ||
                currentExactAlarm != currentStatus.isExactAlarmGranted
            ) {
                com.example.core.accessibility.AccessibilityHelper.updateState(context)
                currentRefresh()
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
                text = stringResource(R.string.session_protection_title),
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
                    text = stringResource(R.string.session_protection_body),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.5.sp
                    )
                )

                PermissionItemCard(
                    title = stringResource(R.string.session_protection_overlay),
                    description = stringResource(R.string.session_protection_overlay_body),
                    icon = Icons.Rounded.Layers,
                    isGranted = permissionStatus.isOverlayGranted,
                    isRequired = true,
                    onClick = {
                        FocusPermissionManager.openOverlaySettings(context)
                        onRefreshPermissions()
                    },
                    testTag = "overlay_permission_item"
                )

                PermissionItemCard(
                    title = stringResource(R.string.session_protection_accessibility),
                    description = stringResource(
                        if (permissionStatus.isAccessibilityEnabled) {
                            R.string.session_protection_accessibility_on
                        } else {
                            R.string.session_protection_accessibility_off
                        }
                    ),
                    icon = Icons.Rounded.Shield,
                    isGranted = permissionStatus.isAccessibilityEnabled,
                    isRequired = true,
                    onClick = {
                        FocusPermissionManager.openAccessibilitySettings(context)
                        onRefreshPermissions()
                    },
                    testTag = "accessibility_permission_item"
                )

                PermissionItemCard(
                    title = stringResource(R.string.session_protection_background),
                    description = stringResource(R.string.session_protection_background_body),
                    icon = Icons.Rounded.Security,
                    isGranted = permissionStatus.isBatteryOptimizationIgnored,
                    isRequired = false,
                    onClick = {
                        FocusPermissionManager.requestIgnoreBatteryOptimization(context)
                        onRefreshPermissions()
                    },
                    testTag = "battery_optimization_permission_item"
                )

                PermissionItemCard(
                    title = stringResource(R.string.session_protection_exact_alarms),
                    description = stringResource(
                        if (permissionStatus.isExactAlarmGranted) {
                            R.string.session_protection_exact_on
                        } else {
                            R.string.session_protection_exact_off
                        }
                    ),
                    icon = Icons.Rounded.Alarm,
                    isGranted = permissionStatus.isExactAlarmGranted,
                    isRequired = false,
                    onClick = {
                        if (!FocusPermissionManager.openExactAlarmSettings(context)) {
                            // Pre-Android 12, or an OEM build without the per-app screen.
                            FocusPermissionManager.openAppDetailsSettings(context)
                        }
                        onRefreshPermissions()
                    },
                    testTag = "exact_alarm_permission_item"
                )

                PermissionItemCard(
                    title = stringResource(R.string.session_protection_notifications),
                    description = stringResource(R.string.session_protection_notifications_body),
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
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = FocusShapes.medium,
                modifier = Modifier.testTag("confirm_start_protected_session_button")
            ) {
                Text(
                    text = when {
                        !allMandatoryGranted -> stringResource(R.string.session_protection_enable_required)
                        confirmLabel != null -> confirmLabel
                        else -> stringResource(R.string.session_protection_start)
                    },
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
                        text = dismissActionLabel
                            ?: stringResource(R.string.session_protection_without_blockers),
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel_setup_dialog_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = FocusColors.TextSecondary
                    )
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
            .clickable(
                enabled = !isGranted,
                role = Role.Button,
                onClick = onClick
            )
            .testTag(testTag),
        shape = FocusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                FocusColors.EmeraldSuccess.copy(alpha = 0.08f)
            } else {
                FocusColors.SurfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isGranted) {
                FocusColors.EmeraldSuccess.copy(alpha = 0.3f)
            } else {
                FocusColors.CardBorderSubtle
            }
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
                        .background(
                            if (isGranted) {
                                FocusColors.EmeraldSuccess.copy(alpha = 0.15f)
                            } else {
                                FocusColors.Primary.copy(alpha = 0.12f)
                            }
                        ),
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
                                text = stringResource(R.string.session_protection_required),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.CoralText,
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
                            contentDescription = null,
                            tint = FocusColors.EmeraldSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.session_permission_granted),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.EmeraldText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(FocusShapes.pill)
                        .border(1.dp, FocusColors.Primary.copy(alpha = 0.5f), FocusShapes.pill)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.session_permission_enable),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
