package com.example.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusPrimaryButton
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.sessionModeSubtitle
import com.example.core.ui.sessionModeTitle
import com.example.data.model.SessionMode

/**
 * Screen 3: Mode Selection Screen.
 * Provides a deeper comparison of Timer, Stopwatch and Pomodoro.
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
            FocusScreenHeader(
                title = stringResource(R.string.session_modes_title),
                onBack = onNavigateBack,
                subtitle = stringResource(R.string.session_modes_select_body),
                modifier = Modifier.statusBarsPadding(),
                backButtonModifier = Modifier.testTag("back_button")
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FocusColors.Surface)
                    .navigationBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.base)
            ) {
                FocusPrimaryButton(
                    text = stringResource(R.string.session_modes_configure),
                    onClick = onNavigateToSetup,
                    testTag = "configure_session_button"
                )
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("mode_selection_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.session_modes_select_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 22.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.session_modes_select_body),
                    style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
                )
            }

            ModeCardDetailed(
                mode = SessionMode.TIMER,
                title = sessionModeTitle(SessionMode.TIMER),
                subtitle = sessionModeSubtitle(SessionMode.TIMER),
                badgeText = stringResource(R.string.session_modes_timer_badge),
                badgeColor = FocusColors.Primary,
                features = listOf(
                    stringResource(R.string.session_modes_timer_feature_time),
                    stringResource(R.string.session_modes_timer_feature_endurance),
                    stringResource(R.string.session_modes_timer_feature_countdown)
                ),
                isSelected = uiState.selectedMode == SessionMode.TIMER,
                onClick = { viewModel.selectMode(SessionMode.TIMER) }
            )

            ModeCardDetailed(
                mode = SessionMode.STOPWATCH,
                title = sessionModeTitle(SessionMode.STOPWATCH),
                subtitle = sessionModeSubtitle(SessionMode.STOPWATCH),
                badgeText = stringResource(R.string.session_modes_stopwatch_badge),
                badgeColor = FocusColors.CyanBlue,
                features = listOf(
                    stringResource(R.string.session_modes_stopwatch_feature_time),
                    stringResource(R.string.session_modes_stopwatch_feature_numericals),
                    stringResource(R.string.session_modes_stopwatch_feature_total)
                ),
                isSelected = uiState.selectedMode == SessionMode.STOPWATCH,
                onClick = { viewModel.selectMode(SessionMode.STOPWATCH) }
            )

            ModeCardDetailed(
                mode = SessionMode.POMODORO,
                title = sessionModeTitle(SessionMode.POMODORO),
                subtitle = sessionModeSubtitle(SessionMode.POMODORO),
                badgeText = stringResource(R.string.session_modes_pomodoro_badge),
                badgeColor = FocusColors.EmeraldSuccess,
                features = listOf(
                    stringResource(R.string.session_modes_pomodoro_feature_cycle),
                    stringResource(R.string.session_modes_pomodoro_feature_energy),
                    stringResource(R.string.session_modes_pomodoro_feature_long)
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
    val backgroundColor =
        if (isSelected) FocusColors.PrimaryContainer.copy(alpha = 0.35f) else FocusColors.Surface
    val selectedLabel =
        if (isSelected) stringResource(R.string.session_mode_selected) else stringResource(R.string.session_mode_not_selected)

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
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(FocusSpacing.lg)
            .testTag("mode_card_${mode.name.lowercase()}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) FocusColors.Primary else FocusColors.SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mode.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else FocusColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(FocusSpacing.md))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = selectedLabel,
                    tint = if (isSelected) FocusColors.Primary else FocusColors.TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .clip(FocusShapes.pill)
                    .background(badgeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            style = MaterialTheme.typography.bodySmall.copy(
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
