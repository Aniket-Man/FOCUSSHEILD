package com.example.core.ui

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors

/**
 * Two-card stats row matching the reference design pixel-accurately:
 * - Left card: "Today's Study" with light purple icon, big duration, and linear progress bar with percentage.
 * - Right card: "Phone Usage" with light blue phone icon, chevron arrow, and big duration.
 */
@Composable
fun StudyAndUsageStatsRow(
    studyTime: String = "1m",
    progressPercentage: Int = 0,
    phoneUsageTime: String = "8h 42m",
    onPhoneUsageClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = com.example.core.design.LocalFocusColors.current.isDark
    val cardShape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Card: Today's Study
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(
                    elevation = if (isDark) 0.dp else 2.dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = 0.03f),
                    spotColor = Color.Black.copy(alpha = 0.04f)
                )
                .clip(cardShape)
                .background(FocusColors.Surface)
                .border(1.dp, FocusColors.CardBorderSubtle, cardShape)
                .padding(16.dp)
                .testTag("today_study_card")
        ) {
            Column {
                // Top: Light purple circle with purple clock icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF26183C) else Color(0xFFEDE8FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = "Today's Study",
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Today's Study",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = studyTime,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Linear Progress bar + Percentage text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { (progressPercentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = FocusColors.Primary,
                        trackColor = if (isDark) Color(0xFF26183C) else Color(0xFFEDE8FF),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$progressPercentage%",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        // Right Card: Phone Usage
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(
                    elevation = if (isDark) 0.dp else 2.dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = 0.03f),
                    spotColor = Color.Black.copy(alpha = 0.04f)
                )
                .clip(cardShape)
                .background(FocusColors.Surface)
                .border(1.dp, FocusColors.CardBorderSubtle, cardShape)
                .clickable(onClick = onPhoneUsageClick)
                .padding(16.dp)
                .testTag("phone_usage_card")
        ) {
            Column {
                // Top row: Light blue circle with blue phone icon + Chevron arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF13253D) else Color(0xFFE5F1FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = "Phone Usage",
                            tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "View Phone Usage Stats",
                        tint = FocusColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Phone Usage",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = phoneUsageTime,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                // Extra bottom spacer for visual balance matching the left card
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}
