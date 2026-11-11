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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusPrimaryButton

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
    val canConfigureDuration = numberOfBreaks > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FocusColors.Surface)
            .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 16.dp)
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
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("break_setup_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = FocusColors.TextPrimary
                    )
                }

                Text(
                    text = stringResource(R.string.session_break_settings),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                )
            }

            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.action_done),
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
                // Row 1: Number of breaks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.session_break_count),
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
                            onClick = {
                                if (numberOfBreaks > 0) {
                                    onNumberOfBreaksChange(numberOfBreaks - 1)
                                }
                            },
                            enabled = numberOfBreaks > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = stringResource(R.string.session_break_decrease),
                                tint = if (numberOfBreaks > 0) {
                                    FocusColors.TextPrimary
                                } else {
                                    FocusColors.TextMuted
                                }
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
                                contentDescription = stringResource(R.string.session_break_increase),
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
                        .clickable(enabled = canConfigureDuration, role = Role.Button) {
                            showDurationPicker = !showDurationPicker
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.session_break_duration),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (canConfigureDuration) {
                                FocusColors.TextPrimary
                            } else {
                                FocusColors.TextMuted
                            },
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.session_break_minutes,
                                breakDurationMinutes,
                                breakDurationMinutes
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = if (canConfigureDuration) {
                                    FocusColors.Primary
                                } else {
                                    FocusColors.TextMuted
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = stringResource(R.string.session_break_select_duration),
                            tint = if (canConfigureDuration) {
                                FocusColors.TextMuted
                            } else {
                                FocusColors.CardBorder
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Quick Duration Selector if Expanded
                if (showDurationPicker && canConfigureDuration) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10, 15).forEach { mins ->
                            val isSelected = breakDurationMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onBreakDurationChange(mins)
                                    showDurationPicker = false
                                },
                                label = {
                                    Text(
                                        text = stringResource(
                                            R.string.session_break_minutes_short,
                                            mins
                                        ),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        FocusPrimaryButton(
            text = stringResource(R.string.action_done),
            onClick = onBack,
            testTag = "break_setup_done_button"
        )
    }
}
