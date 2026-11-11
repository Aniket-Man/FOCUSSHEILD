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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.core.ui.sessionModeDescription
import com.example.core.ui.sessionModeSubtitle
import com.example.core.ui.sessionModeTitle
import com.example.data.model.SessionMode

/**
 * Screen 2: Start Session screen.
 * Lets the student choose a focus mode, then continue into the detailed setup.
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
    var showSetupSheet by remember { mutableStateOf(true) }

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
            FocusScreenHeader(
                title = stringResource(R.string.session_start_title),
                onBack = onNavigateBack,
                subtitle = stringResource(R.string.session_start_choose_body),
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
                    text = stringResource(R.string.session_start_continue),
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
            Column {
                Text(
                    text = stringResource(R.string.session_start_choose_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 22.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.session_start_choose_body),
                    style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SessionMode.entries.forEach { mode ->
                    val isSelected = uiState.selectedMode == mode
                    val borderColor = if (isSelected) FocusColors.Primary else FocusColors.CardBorderSubtle
                    val backgroundColor =
                        if (isSelected) FocusColors.PrimaryContainer.copy(alpha = 0.4f) else FocusColors.Surface
                    val modeTitle = sessionModeTitle(mode)
                    val modeSubtitle = sessionModeSubtitle(mode)
                    val modeDescription = sessionModeDescription(mode)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (isSelected) 3.dp else 1.dp,
                                shape = FocusShapes.large,
                                ambientColor = if (isSelected) {
                                    FocusColors.Primary.copy(alpha = 0.15f)
                                } else {
                                    Color.Black.copy(alpha = 0.02f)
                                },
                                spotColor = if (isSelected) {
                                    FocusColors.Primary.copy(alpha = 0.2f)
                                } else {
                                    Color.Black.copy(alpha = 0.04f)
                                }
                            )
                            .clip(FocusShapes.large)
                            .background(backgroundColor)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = borderColor,
                                shape = FocusShapes.large
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { viewModel.selectMode(mode) }
                            )
                            .padding(FocusSpacing.base)
                            .testTag("mode_option_${mode.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) FocusColors.Primary else FocusColors.SurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else FocusColors.Primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(FocusSpacing.base))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modeTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.TextPrimary,
                                        fontSize = 16.sp
                                    )
                                )
                                Text(
                                    text = modeSubtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = modeDescription,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextMuted,
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = FocusColors.Primary,
                                    unselectedColor = FocusColors.CardBorder
                                )
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.medium)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium)
                    .clickable(role = Role.Button, onClick = onNavigateToModeSelection)
                    .padding(horizontal = FocusSpacing.base, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.session_start_presets_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.Primary
                            )
                        )
                        Text(
                            text = stringResource(R.string.session_start_presets_body),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = FocusColors.Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(FocusSpacing.base))
        }
    }
}
