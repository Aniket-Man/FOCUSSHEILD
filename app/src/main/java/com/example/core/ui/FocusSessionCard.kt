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
    timeString: String = "00:00:00",
    subjectName: String = "Physics",
    subtitleText: String? = null,
    isPaused: Boolean = false,
    onTogglePause: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onStartSessionClick: () -> Unit = {}
) {
    val heroShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = heroShape,
                ambientColor = Color(0xFF6B36F6).copy(alpha = 0.35f),
                spotColor = Color(0xFF9E48FF).copy(alpha = 0.30f)
            )
            .clip(heroShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6832F6),
                        Color(0xFF8641F8),
                        Color(0xFF9D49FF)
                    )
                )
            )
            .clickable(onClick = onCardClick)
            .testTag("focus_session_card")
    ) {
        // Decorative ambient background glow circles
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = (-40).dp, y = (-60).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 60.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            // Top Label
            Text(
                text = if (isActive) "FOCUS SESSION • ${subjectName.uppercase()}" else "READY TO FOCUS",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.8.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Main Large Countdown Timer Display
            Text(
                text = timeString,
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 46.sp,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.testTag("session_timer_display")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // White Action Pill Button
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
                    contentColor = Color(0xFF6B36F6)
                ),
                shape = CircleShape,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 24.dp,
                    vertical = 12.dp
                ),
                modifier = Modifier
                    .height(46.dp)
                    .testTag("session_action_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (!isActive || isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isActive && !isPaused) "Pause" else "Start",
                        tint = Color(0xFF6B36F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (!isActive) "Start Session" else if (isPaused) "Resume" else "Pause",
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B36F6),
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}
