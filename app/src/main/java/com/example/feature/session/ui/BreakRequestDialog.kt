package com.example.feature.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes

/**
 * Material 3 break request dialog.
 * Allows 5, 10 or 15 minute temporary breaks during active study sessions.
 */
@Composable
fun BreakRequestDialog(
    isStrictModeEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirmBreak: (durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    isBlockingEnabled: Boolean = true
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
                    text = stringResource(R.string.session_break_request_title),
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
                    text = stringResource(
                        if (isBlockingEnabled) {
                            R.string.session_break_request_body
                        } else {
                            R.string.session_break_request_body_no_blocking
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.TextSecondary
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BreakOptionRow(
                        label = stringResource(
                            R.string.session_break_minutes_short,
                            5
                        ),
                        sublabel = stringResource(R.string.session_break_option_5),
                        icon = Icons.Outlined.Timer,
                        isSelected = selectedMinutes == 5,
                        onClick = { selectedMinutes = 5 },
                        testTag = "break_option_5"
                    )

                    BreakOptionRow(
                        label = stringResource(
                            R.string.session_break_minutes_short,
                            10
                        ),
                        sublabel = stringResource(R.string.session_break_option_10),
                        icon = Icons.Outlined.Coffee,
                        isSelected = selectedMinutes == 10,
                        onClick = { selectedMinutes = 10 },
                        testTag = "break_option_10"
                    )

                    BreakOptionRow(
                        label = stringResource(
                            R.string.session_break_minutes_short,
                            15
                        ),
                        sublabel = stringResource(R.string.session_break_option_15),
                        icon = Icons.Outlined.DirectionsWalk,
                        isSelected = selectedMinutes == 15,
                        onClick = { selectedMinutes = 15 },
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
                                text = stringResource(
                                    if (isBlockingEnabled) {
                                        R.string.session_break_strict_note
                                    } else {
                                        R.string.session_break_strict_note_no_blocking
                                    }
                                ),
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
                    containerColor = FocusColors.Primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = FocusShapes.medium,
                modifier = Modifier.testTag("confirm_start_break_button")
            ) {
                Text(
                    text = stringResource(R.string.session_break_start, selectedMinutes),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_break_dialog_button")
            ) {
                Text(stringResource(R.string.session_break_continue))
            }
        },
        modifier = modifier.testTag("break_request_dialog")
    )
}

@Composable
private fun BreakOptionRow(
    label: String,
    sublabel: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (isSelected) FocusColors.CyanBlue else FocusColors.CardBorderSubtle
    val bgColor = if (isSelected) FocusColors.CyanBlue.copy(alpha = 0.1f) else FocusColors.Surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.medium)
            .background(bgColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, FocusShapes.medium)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) FocusColors.CyanBlue else FocusColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) FocusColors.CyanText else FocusColors.TextPrimary
                )
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = FocusColors.TextSecondary
                )
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = FocusColors.CyanBlue,
                unselectedColor = FocusColors.CardBorder
            ),
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}
