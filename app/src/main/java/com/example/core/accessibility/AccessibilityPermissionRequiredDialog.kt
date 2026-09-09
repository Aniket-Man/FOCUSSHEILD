package com.example.core.accessibility

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes

/**
 * Context data for an accessibility-dependent feature prompt.
 */
data class AccessibilityFeaturePromptInfo(
    val title: String,
    val description: String,
    val onGranted: () -> Unit
)

/**
 * Dialog shown when the user toggles on a feature that strictly requires
 * the Android Accessibility Service (such as YouTube Shorts blocker, Instagram Reels,
 * Facebook Reels, YouTube Study Mode, or Anti-Tamper protections).
 */
@Composable
fun AccessibilityPermissionRequiredDialog(
    featureTitle: String,
    featureDescription: String,
    onDismissRequest: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    var showTroubleshootingGuide by remember { mutableStateOf(false) }

    // Check if user enabled the permission upon returning from Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                    onPermissionGranted()
                    onDismissRequest()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showTroubleshootingGuide) {
        AccessibilityTroubleshootingDialog(
            onDismissRequest = {
                showTroubleshootingGuide = false
                if (AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                    onPermissionGranted()
                    onDismissRequest()
                }
            }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("accessibility_permission_required_dialog"),
            shape = FocusShapes.card,
            color = FocusColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(FocusColors.Primary.copy(alpha = 0.15f))
                            .border(1.dp, FocusColors.Primary.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Accessibility,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Required",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = featureTitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(thickness = 1.dp, color = FocusColors.CardBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "To enable $featureTitle, FocusShield needs Accessibility permission to detect and shield $featureDescription in real time.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FocusColors.SurfaceSubtle,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = FocusColors.EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "100% On-Device & Private. FocusShield never records keystrokes, personal info, or sensitive data.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            AccessibilityHelper.openAccessibilitySettings(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_enable_accessibility_settings"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable in Settings", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showTroubleshootingGuide = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_accessibility_troubleshooting"),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = FocusColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Help / Restricted Settings Guide",
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = FocusColors.TextSecondary)
                    }
                }
            }
        }
    }
}
