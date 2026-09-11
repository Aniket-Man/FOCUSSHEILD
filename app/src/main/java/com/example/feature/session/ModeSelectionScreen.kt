package com.example.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusPrimaryButton
import com.example.data.model.SessionMode

/**
 * Screen 3: Mode Selection Screen.
 * Provides deep-dive comparison and selection across Timer, Stopwatch, and Pomodoro.
 */
@Composable
fun ModeSelectionScreen(
    viewModel: SessionViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.setupState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.sm, vertical = FocusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(FocusSpacing.xs))
                Text(
                    text = "Session Modes",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FocusColors.Surface)
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.base)
            ) {
                FocusPrimaryButton(
                    text = "Configure Session",
                    onClick = onNavigateToSetup,
                    testTag = "configure_session_button"
                )
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.navigationBarsPadding().testTag("mode_selection_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
        ) {
            // Header explanation
            Column {
                Text(
                    text = "Select Focus Mode",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 22.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Three distinct methods to master JEE/NEET preparation.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary
                    )
                )
            }

            // Mode 1: Timer Card
            ModeCardDetailed(
                mode = SessionMode.TIMER,
                title = "TIMER",
                subtitle = "Fixed-duration countdown",
                badgeText = "Most Popular for JEE",
                badgeColor = FocusColors.Primary,
                features = listOf(
                    "Set exact focus time (25m - 120m)",
                    "Builds real exam time endurance",
                    "Enforces strict countdown discipline"
                ),
                isSelected = uiState.selectedMode == SessionMode.TIMER,
                onClick = { viewModel.selectMode(SessionMode.TIMER) }
            )

            // Mode 2: Stopwatch Card
            ModeCardDetailed(
                mode = SessionMode.STOPWATCH,
                title = "STOPWATCH",
                subtitle = "Track open-ended study time",
                badgeText = "Deep Flow State",
                badgeColor = FocusColors.CyanBlue,
                features = listOf(
                    "Uncapped, pressure-free session",
                    "Ideal for long numerical solving",
                    "Track total continuous study hours"
                ),
                isSelected = uiState.selectedMode == SessionMode.STOPWATCH,
                onClick = { viewModel.selectMode(SessionMode.STOPWATCH) }
            )

            // Mode 3: Pomodoro Card
            ModeCardDetailed(
                mode = SessionMode.POMODORO,
                title = "POMODORO",
                subtitle = "Focus and break cycles",
                badgeText = "Anti-Burnout",
                badgeColor = FocusColors.EmeraldSuccess,
                features = listOf(
                    "25m Study + 5m Refreshing Break",
                    "Maintains peak mental energy",
                    "Scheduled long breaks every 4 cycles"
                ),
                isSelected = uiState.selectedMode == SessionMode.POMODORO,
                onClick = { viewModel.selectMode(SessionMode.POMODORO) }
            )

            Spacer(modifier = Modifier.height(FocusSpacing.base))
        }
    }
}

@Composable
private fun ModeCardDetailed(
    mode: SessionMode,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    features: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) FocusColors.Primary else FocusColors.CardBorderSubtle
    val backgroundColor = if (isSelected) FocusColors.PrimaryContainer.copy(alpha = 0.35f) else FocusColors.Surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 4.dp else 1.dp,
                shape = FocusShapes.card,
                ambientColor = if (isSelected) FocusColors.Primary.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.02f),
                spotColor = if (isSelected) FocusColors.Primary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.04f)
            )
            .clip(FocusShapes.card)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = FocusShapes.card
            )
            .clickable(onClick = onClick)
            .padding(FocusSpacing.lg)
            .testTag("mode_card_${mode.name.lowercase()}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Title & Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) FocusColors.Primary else FocusColors.SurfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mode.icon,
                            contentDescription = title,
                            tint = if (isSelected) Color.White else FocusColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(FocusSpacing.md))

                    Column {
                        Text(
                            text = title,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = subtitle,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Radio Check indicator
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Unselected",
                    tint = if (isSelected) FocusColors.Primary else FocusColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badge Pill
            Box(
                modifier = Modifier
                    .clip(FocusShapes.pill)
                    .background(badgeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Key features checklist
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(FocusColors.Primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feature,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 12.5.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
