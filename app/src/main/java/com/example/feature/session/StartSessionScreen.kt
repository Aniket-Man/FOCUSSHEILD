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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
 * Screen 2: Start Session screen.
 * Allows student to choose their focus mode (Timer, Stopwatch, Pomodoro) with clear explanations.
 */
@Composable
fun StartSessionScreen(
    viewModel: SessionViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModeSelection: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.setupState.collectAsStateWithLifecycle()
    var showSetupSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

    if (showSetupSheet) {
        com.example.feature.session.ui.FocusSessionSetupSheet(
            viewModel = viewModel,
            onDismiss = {
                showSetupSheet = false
                onNavigateBack()
            },
            onStartFocus = {
                showSetupSheet = false
                onNavigateToSetup()
            }
        )
    }

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
                    text = "Start Session",
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
                    text = "Continue to Setup",
                    onClick = onNavigateToSetup,
                    testTag = "continue_to_setup_button"
                )
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("start_session_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
        ) {
            // Intro headline
            Column {
                Text(
                    text = "Choose how you want to focus",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 22.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select a study session style tailored to your goal.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary
                    )
                )
            }

            // Mode Selection Options
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SessionMode.values().forEach { mode ->
                    val isSelected = uiState.selectedMode == mode
                    val borderColor = if (isSelected) FocusColors.Primary else FocusColors.CardBorderSubtle
                    val backgroundColor = if (isSelected) FocusColors.PrimaryContainer.copy(alpha = 0.4f) else FocusColors.Surface

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (isSelected) 3.dp else 1.dp,
                                shape = FocusShapes.large,
                                ambientColor = if (isSelected) FocusColors.Primary.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.02f),
                                spotColor = if (isSelected) FocusColors.Primary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.04f)
                            )
                            .clip(FocusShapes.large)
                            .background(backgroundColor)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = borderColor,
                                shape = FocusShapes.large
                            )
                            .clickable { viewModel.selectMode(mode) }
                            .padding(FocusSpacing.base)
                            .testTag("mode_option_${mode.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Mode Icon Container
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) FocusColors.Primary else FocusColors.SurfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = mode.title,
                                    tint = if (isSelected) Color.White else FocusColors.Primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(FocusSpacing.base))

                            // Text details
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = mode.title,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.TextPrimary,
                                        fontSize = 16.sp
                                    )
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mode.description,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextMuted,
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Radio Indicator
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectMode(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = FocusColors.Primary,
                                    unselectedColor = FocusColors.CardBorder
                                )
                            )
                        }
                    }
                }
            }

            // Quick link to detailed mode customization
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.medium)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium)
                    .clickable(onClick = onNavigateToModeSelection)
                    .padding(horizontal = FocusSpacing.base, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Explore Mode Presets",
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.Primary
                            )
                        )
                        Text(
                            text = "JEE Sprint, Deep Study & Interval options",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Details",
                        tint = FocusColors.Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(FocusSpacing.base))
        }
    }
}
