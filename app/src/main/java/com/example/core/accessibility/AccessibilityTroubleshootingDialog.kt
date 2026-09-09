package com.example.core.accessibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes

/**
 * Universal troubleshooting and permission guide for phone manufacturers (Xiaomi/MIUI, Samsung, Oppo, Vivo, Realme)
 * and Android 13+ (API 33+) "Restricted Settings" flag fix.
 */
@Composable
fun AccessibilityTroubleshootingDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isServiceActiveState by AccessibilityHelper.isServiceEnabledFlow.collectAsState()
    var isServiceActive by remember {
        mutableStateOf(AccessibilityHelper.isAccessibilityServiceEnabled(context))
    }

    LaunchedEffect(isServiceActiveState) {
        if (isServiceActiveState) {
            isServiceActive = true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                AccessibilityHelper.updateState(context)
                isServiceActive = true
            }
            kotlinx.coroutines.delay(500)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("accessibility_troubleshooting_dialog"),
            shape = FocusShapes.card,
            color = FocusColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isServiceActive) FocusColors.EmeraldLight
                                else FocusColors.CoralLight
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Rounded.CheckCircle else Icons.Rounded.Accessibility,
                            contentDescription = null,
                            tint = if (isServiceActive) FocusColors.EmeraldSuccess else FocusColors.CoralWarning,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Service Guide",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = if (isServiceActive) "Service is active and working properly" else "Fix permission greyed out / restricted settings",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isServiceActive) FocusColors.EmeraldSuccess else FocusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FocusColors.CardBorderSubtle))
                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Android 13+ Restricted Setting Fix
                TroubleshootingStepCard(
                    stepNumber = "1",
                    title = "Android 13 / 14 / 15 Restricted Setting Fix",
                    description = "If phone shows 'Restricted setting: For your security, this setting is currently unavailable':\n" +
                            "• Tap 'Open App Info' below\n" +
                            "• Tap the 3 dots menu (top right)\n" +
                            "• Select 'Allow restricted settings'\n" +
                            "• Return here and enable Accessibility Service!"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Section 2: Xiaomi / MIUI / HyperOS Autostart & Battery
                TroubleshootingStepCard(
                    stepNumber = "2",
                    title = "Xiaomi / MIUI / Samsung / Vivo Fix",
                    description = "If the service turns off automatically when app is closed:\n" +
                            "• Open App Info -> Enable 'Autostart'\n" +
                            "• Battery Saver -> Set to 'No Restrictions'\n" +
                            "• Lock FocusShield in Recent Apps screen"
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            AccessibilityHelper.openAccessibilitySettings(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
                    ) {
                        Icon(imageVector = Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1. Open Accessibility Settings", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            AccessibilityHelper.openAppDetailsSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.Primary)
                    ) {
                        Icon(imageVector = Icons.Rounded.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp), tint = FocusColors.Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. Open App Info (Allow Restricted)", color = FocusColors.Primary, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", color = FocusColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TroubleshootingStepCard(
    stepNumber: String,
    title: String,
    description: String
) {
    Surface(
        shape = FocusShapes.small,
        color = FocusColors.SurfaceSubtle,
        border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(FocusColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 13.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}
