package com.example.feature.session.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors

@Composable
fun BreakSetupSheet(
    numberOfBreaks: Int,
    breakDurationMinutes: Int,
    onNumberOfBreaksChange: (Int) -> Unit,
    onBreakDurationChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDurationPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FocusColors.Surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Grab Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(FocusColors.TextMuted.copy(alpha = 0.4f))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Header with Back Button and Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }

                Text(
                    text = "Break Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                )
            }

            androidx.compose.material3.TextButton(onClick = onBack) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = FocusColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Break Configuration Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = FocusColors.SurfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Row 1: No. of breaks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "No. of breaks",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    // Counter Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(FocusColors.PrimaryContainer)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { if (numberOfBreaks > 0) onNumberOfBreaksChange(numberOfBreaks - 1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease",
                                tint = if (numberOfBreaks > 0) FocusColors.TextPrimary else FocusColors.TextMuted
                            )
                        }

                        Text(
                            text = "$numberOfBreaks",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        IconButton(
                            onClick = { onNumberOfBreaksChange(numberOfBreaks + 1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase",
                                tint = FocusColors.TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Row 2: Break duration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDurationPicker = !showDurationPicker },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Break duration",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$breakDurationMinutes mins",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Select duration",
                            tint = FocusColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Quick Duration Selector if Expanded
                if (showDurationPicker) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10, 15).forEach { mins ->
                            val isSelected = breakDurationMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) FocusColors.Primary else FocusColors.Surface)
                                    .clickable {
                                        onBreakDurationChange(mins)
                                        showDurationPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mins m",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) Color.White else FocusColors.TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Back / Confirm button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(FocusColors.Primary)
                .clickable { onBack() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Done",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
