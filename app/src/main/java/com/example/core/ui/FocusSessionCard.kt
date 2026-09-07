package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing

/**
 * Prominent Focus Session banner card matching Clean Minimalism design.
 * Displays the current session state, countdown time, and pause/start action pill with subtle ambient decor.
 */
@Composable
fun FocusSessionCard(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    timeString: String = "01:25:30",
    subjectName: String = "Physics",
    subtitleText: String? = null,
    isPaused: Boolean = false,
    onTogglePause: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onStartSessionClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = FocusShapes.extraLarge,
                ambientColor = FocusColors.Primary.copy(alpha = 0.25f),
                spotColor = FocusColors.PrimaryDark.copy(alpha = 0.35f)
            )
            .clip(FocusShapes.extraLarge)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        FocusColors.GradientStart,
                        FocusColors.PrimaryDark
                    )
                )
            )
            .clickable(onClick = onCardClick)
            .testTag("focus_session_card")
    ) {
        // Decorative ambient background circles (Clean Minimalism detail)
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(x = (-30).dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 50.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocusSpacing.xl, vertical = 24.dp)
        ) {
            // Top Row: Title + Status Pill Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isActive) "Focus Session • $subjectName" else "Ready to Focus",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                        color = Color.White.copy(alpha = 0.95f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.20f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (!isActive) "Ready" else if (isPaused) "Paused" else "Active",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Large Countdown Timer Display
            Text(
                text = timeString,
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 44.sp,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.testTag("session_timer_display")
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle
            Text(
                text = subtitleText ?: if (isActive) "Time remaining" else "Ready for your next $subjectName session",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextOnDarkMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom Action Pill Button (White with Purple Text & Icon)
            Button(
                onClick = {
                    if (isActive) {
                        onTogglePause()
                    } else {
                        onStartSessionClick()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = FocusColors.PrimaryDark
                ),
                shape = FocusShapes.pill,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .width(160.dp)
                    .height(44.dp)
                    .testTag("session_action_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (!isActive || isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isActive && !isPaused) "Pause" else "Start",
                        tint = FocusColors.PrimaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (!isActive) "Start Session" else if (isPaused) "Resume" else "Pause",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.PrimaryDark,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}
