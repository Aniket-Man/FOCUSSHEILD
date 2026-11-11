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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
    subjectName: String = "",
    subtitleText: String? = null,
    isPaused: Boolean = false,
    isStrictMode: Boolean = false,
    onTogglePause: () -> Unit = {},
    onCardClick: () -> Unit = {},
    onStartSessionClick: () -> Unit = {}
) {
    val heroShape = RoundedCornerShape(26.dp)
    val openTimerLabel = stringResource(R.string.action_open_timer)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = heroShape,
                ambientColor = FocusColors.HeroActionContent.copy(alpha = 0.35f),
                spotColor = FocusColors.HeroGradientEnd.copy(alpha = 0.30f)
            )
            .clip(heroShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        FocusColors.HeroGradientStart,
                        FocusColors.HeroGradientMid,
                        FocusColors.HeroGradientEnd
                    )
                )
            )
            .then(
                if (isActive) Modifier.clickable(role = Role.Button, onClickLabel = openTimerLabel, onClick = onCardClick)
                else Modifier
            )
            .testTag("focus_session_card")
    ) {
        // Decorative ambient background glow circles
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = (-40).dp, y = (-60).dp)
                .clip(CircleShape)
                .background(FocusColors.HeroOnColor.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 60.dp)
                .clip(CircleShape)
                .background(FocusColors.HeroOnColor.copy(alpha = 0.07f))
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 40.dp)
                .clip(CircleShape)
                .background(FocusColors.HeroOnColor.copy(alpha = 0.05f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            // Top Label
            val headerLabel = when {
                isActive && isStrictMode ->
                    stringResource(R.string.focus_session_header_strict, subjectName.uppercase())
                isActive && isPaused ->
                    stringResource(R.string.focus_session_header_paused, subjectName.uppercase())
                isActive ->
                    stringResource(R.string.focus_session_header_active, subjectName.uppercase())
                else -> stringResource(R.string.focus_session_header_ready)
            }
            Text(
                text = headerLabel,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.HeroOnColorMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Main Large Countdown Timer Display
            FocusTimerText(
                time = timeString,
                style = MaterialTheme.typography.displayLarge.copy(
                    color = FocusColors.HeroOnColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_timer_display")
            )

            if (!subtitleText.isNullOrBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = FocusColors.HeroOnColorMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // White Action Pill Button
            Button(
                onClick = {
                    if (isActive) {
                        if (isStrictMode && !isPaused) {
                            onCardClick()
                        } else {
                            onTogglePause()
                        }
                    } else {
                        onStartSessionClick()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.HeroOnColor,
                    contentColor = FocusColors.HeroActionContent
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
                    .heightIn(min = 48.dp)
                    .testTag("session_action_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val showPlayIcon = !isActive || isPaused || isStrictMode
                    val buttonLabel = stringResource(
                        when {
                            !isActive -> R.string.action_start_session
                            isPaused -> R.string.action_resume
                            isStrictMode -> R.string.action_open_timer
                            else -> R.string.action_pause
                        }
                    )
                    Icon(
                        imageVector = if (showPlayIcon) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = null,
                        tint = FocusColors.HeroActionContent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = buttonLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.HeroActionContent
                        )
                    )
                }
            }
        }
    }
}
