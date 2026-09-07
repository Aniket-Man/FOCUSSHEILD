package com.example.feature.session.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing

/**
 * Clean Material 3 Break Request Dialog.
 * Allows users to choose a 5, 10, or 15-minute temporary break during active study sessions.
 */
@Composable
fun BreakRequestDialog(
    isStrictModeEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirmBreak: (durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMinutes by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusColors.Surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(FocusColors.CyanBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Coffee,
                        contentDescription = null,
                        tint = FocusColors.CyanBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Take a Study Break",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "App shields will be temporarily lowered and your study countdown will pause. Choose your break duration:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.TextSecondary
                )

                // Break Duration Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BreakOptionCard(
                        minutes = 5,
                        label = "5 min",
                        sublabel = "Quick Rest",
                        icon = Icons.Outlined.Timer,
                        isSelected = selectedMinutes == 5,
                        onClick = { selectedMinutes = 5 },
                        modifier = Modifier.weight(1f),
                        testTag = "break_option_5"
                    )

                    BreakOptionCard(
                        minutes = 10,
                        label = "10 min",
                        sublabel = "Tea / Coffee",
                        icon = Icons.Outlined.Coffee,
                        isSelected = selectedMinutes == 10,
                        onClick = { selectedMinutes = 10 },
                        modifier = Modifier.weight(1f),
                        testTag = "break_option_10"
                    )

                    BreakOptionCard(
                        minutes = 15,
                        label = "15 min",
                        sublabel = "Stretch / Walk",
                        icon = Icons.Outlined.DirectionsWalk,
                        isSelected = selectedMinutes == 15,
                        onClick = { selectedMinutes = 15 },
                        modifier = Modifier.weight(1f),
                        testTag = "break_option_15"
                    )
                }

                if (isStrictModeEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FocusShapes.medium)
                            .background(FocusColors.AmberLight.copy(alpha = 0.4f))
                            .border(1.dp, FocusColors.AmberOrange.copy(alpha = 0.3f), FocusShapes.medium)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = FocusColors.AmberOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Strict Mode: The shield automatically re-engages immediately when the timer expires.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextPrimary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmBreak(selectedMinutes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.CyanBlue,
                    contentColor = Color.White
                ),
                shape = FocusShapes.medium,
                modifier = Modifier.testTag("confirm_start_break_button")
            ) {
                Text("Start Break ($selectedMinutes min)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_break_dialog_button")
            ) {
                Text("Continue Studying")
            }
        },
        modifier = modifier.testTag("break_request_dialog")
    )
}

@Composable
private fun BreakOptionCard(
    minutes: Int,
    label: String,
    sublabel: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val borderColor = if (isSelected) FocusColors.CyanBlue else FocusColors.CardBorderSubtle
    val bgColor = if (isSelected) FocusColors.CyanBlue.copy(alpha = 0.1f) else FocusColors.Surface

    Box(
        modifier = modifier
            .clip(FocusShapes.medium)
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, FocusShapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) FocusColors.CyanBlue else FocusColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) FocusColors.CyanBlue else FocusColors.TextPrimary
                )
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    color = FocusColors.TextSecondary
                )
            )
        }
    }
}
