package com.example.feature.session.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusLottieAnimation
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.feature.session.domain.SessionSummaryData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Beautiful, immersive Post-Session Summary Screen.
 * Displays all telemetry and session metrics gathered during the focus session:
 * - Subject & Topic
 * - Pure Focused Study Duration vs Planned Target
 * - Break Time taken
 * - Session Mode & Discipline achievements
 * - Protection metrics (Strict Mode, Blocked Apps Count, YouTube Study Mode, etc.)
 * - Start & End Timestamps
 *
 * Provides two distinct, beautifully styled options:
 * 1. "Save Session" (Stores data into user database & cloud sync)
 * 2. "Don't Save" (Discards the session data without writing to database)
 */
@Composable
fun FocusSessionSummaryScreen(
    summaryData: SessionSummaryData,
    onSaveSession: () -> Unit,
    onDiscardSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSaving by remember { mutableStateOf(false) }

    // Intercept back button to prompt discard/leave cleanly
    BackHandler {
        if (!isSaving) {
            onDiscardSession()
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val startFormatted = remember(summaryData.startedAt) { timeFormat.format(Date(summaryData.startedAt)) }
    val endFormatted = remember(summaryData.endedAt) { timeFormat.format(Date(summaryData.endedAt)) }

    val formattedStudyDuration = remember(summaryData.pureStudyDurationMillis) {
        TimeFormatter.formatDetailed(summaryData.pureStudyDurationMillis)
    }

    val isCompletedGoal = summaryData.isCompleted && !summaryData.isCancelled

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (!isSaving) onDiscardSession()
                    },
                    modifier = Modifier.testTag("summary_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }

                // Header title
                Text(
                    text = "Session Summary",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 17.sp
                    )
                )

                // Top right discard icon button for quick navigation
                IconButton(
                    onClick = {
                        if (!isSaving) onDiscardSession()
                    },
                    modifier = Modifier.testTag("summary_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Discard and Exit",
                        tint = FocusColors.TextSecondary
                    )
                }
            }
        },
        bottomBar = {
            // Prominent Bottom Action Bar with "Save Session" and "Don't Save"
            Surface(
                color = FocusColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 16.dp, ambientColor = Color.Black.copy(alpha = 0.2f))
                    .border(1.dp, FocusColors.CardBorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Save Session (Primary Emerald Green / Purple Action)
                    Button(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                onSaveSession()
                            }
                        },
                        enabled = !isSaving,
                        shape = FocusShapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FocusColors.Primary,
                            contentColor = Color.White,
                            disabledContainerColor = FocusColors.Primary.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_session_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Saving Session...",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save Session",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }

                    // Option 2: Don't Save (Subtle outline / coral discard)
                    OutlinedButton(
                        onClick = {
                            if (!isSaving) {
                                onDiscardSession()
                            }
                        },
                        enabled = !isSaving,
                        shape = FocusShapes.large,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            FocusColors.CoralWarning.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FocusColors.CoralWarning
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("dont_save_session_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = FocusColors.CoralWarning
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Don't Save",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = FocusColors.CoralWarning
                                )
                            )
                        }
                    }
                }
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.fillMaxSize().testTag("focus_session_summary_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Animated Celebration or Completion Header
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                FocusColors.Primary.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                FocusLottieAnimation(
                    rawRes = if (isCompletedGoal) R.raw.focus_success else R.raw.timer_animation,
                    modifier = Modifier.size(92.dp),
                    iterations = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isCompletedGoal) "Great Job! Session Finished" else "Focus Session Ended",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = FocusColors.TextPrimary,
                    fontSize = 22.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isCompletedGoal)
                    "Review all data taken during this session below. Choose whether to save it to your history and database."
                else
                    "Your session progress was stopped early. You can review the gathered metrics below before saving or discarding.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Highlight Card: Pure Focus Study Duration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.card)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                FocusColors.Primary.copy(alpha = 0.16f),
                                FocusColors.Surface
                            )
                        )
                    )
                    .border(1.5.dp, FocusColors.Primary.copy(alpha = 0.35f), FocusShapes.card)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "TOTAL STUDY TIME RECORDED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.Primary,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = formattedStudyDuration,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = FocusColors.TextPrimary,
                            fontSize = 32.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "$startFormatted  ➔  $endFormatted",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subject, Topic & Session Goal Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.large)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subject",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = summaryData.subject,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Topic",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = summaryData.topic,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = FocusColors.TextPrimary
                            )
                        )
                    }

                    if (summaryData.goal.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FocusShapes.medium)
                                .background(FocusColors.SurfaceSubtle)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = FocusColors.Primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = summaryData.goal,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2x2 Grid: Key Telemetry Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Metric 1: Mode
                MetricSummaryTile(
                    title = "SESSION MODE",
                    value = summaryData.mode.label,
                    icon = when (summaryData.mode) {
                        SessionMode.TIMER -> Icons.Rounded.HourglassBottom
                        SessionMode.POMODORO -> Icons.Rounded.Timer
                        SessionMode.STOPWATCH -> Icons.Rounded.Schedule
                    },
                    accentColor = FocusColors.Primary,
                    modifier = Modifier.weight(1f)
                )

                // Metric 2: Distractions Blocked
                MetricSummaryTile(
                    title = "DISTRACTIONS BLOCKED",
                    value = "${summaryData.distractionsBlockedCount} blocked",
                    icon = Icons.Rounded.Block,
                    accentColor = if (summaryData.distractionsBlockedCount > 0) FocusColors.CoralWarning else FocusColors.EmeraldSuccess,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Metric 3: YouTube Study Mode Status
                val ytStatusText = if (summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled) "Study Mode ON" else "Standard"
                MetricSummaryTile(
                    title = "YOUTUBE STUDY MODE",
                    value = ytStatusText,
                    icon = Icons.Rounded.SmartDisplay,
                    accentColor = if (summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled) FocusColors.ChannelsAmber else FocusColors.TextMuted,
                    modifier = Modifier.weight(1f)
                )

                // Metric 4: Shielded Apps / Pomodoro Cycles / Laps / Break
                val secondaryMetricTitle: String
                val secondaryMetricValue: String
                val secondaryMetricIcon: ImageVector

                when (summaryData.mode) {
                    SessionMode.POMODORO -> {
                        secondaryMetricTitle = "POMODORO CYCLES"
                        secondaryMetricValue = "${summaryData.completedPomodoroCycles} of ${summaryData.totalPomodoroCycles}"
                        secondaryMetricIcon = Icons.Rounded.CheckCircle
                    }
                    SessionMode.STOPWATCH -> {
                        secondaryMetricTitle = "LAPS RECORDED"
                        secondaryMetricValue = "${summaryData.lapsCount} recorded"
                        secondaryMetricIcon = Icons.Rounded.Flag
                    }
                    SessionMode.TIMER -> {
                        secondaryMetricTitle = "BREAK TIME"
                        secondaryMetricValue = if (summaryData.totalBreakDurationMillis > 0) {
                            TimeFormatter.formatDigital(summaryData.totalBreakDurationMillis, true)
                        } else {
                            "None (0m)"
                        }
                        secondaryMetricIcon = Icons.Rounded.Schedule
                    }
                }

                MetricSummaryTile(
                    title = secondaryMetricTitle,
                    value = secondaryMetricValue,
                    icon = secondaryMetricIcon,
                    accentColor = FocusColors.CyanBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Protection & Feature Breakdown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.large)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Protection & Telemetry Breakdown",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextSecondary
                        )
                    )

                    FeatureCheckRow(
                        label = "Distractions Intercepted & Blocked",
                        isActive = summaryData.distractionsBlockedCount > 0,
                        detail = "${summaryData.distractionsBlockedCount} attempts blocked"
                    )

                    FeatureCheckRow(
                        label = "YouTube Educational Study Mode",
                        isActive = summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled,
                        detail = if (summaryData.isYouTubeStudyModeEnabled || summaryData.isStudyChannelsEnabled)
                            "Distracting videos blocked, study channels allowed"
                        else
                            "Not active"
                    )

                    FeatureCheckRow(
                        label = "App Shield & Distraction Blocker",
                        isActive = summaryData.isAppBlockingEnabled,
                        detail = if (summaryData.isAppBlockingEnabled) "${summaryData.blockedAppsCount} apps shielded" else "Not active"
                    )

                    FeatureCheckRow(
                        label = "Safe Browser Study Mode",
                        isActive = summaryData.isBrowserStudyModeEnabled,
                        detail = if (summaryData.isBrowserStudyModeEnabled) "Adult & distracting sites blocked" else "Not active"
                    )

                    FeatureCheckRow(
                        label = "Strict Mode Tamper Guard",
                        isActive = summaryData.isStrictModeEnabled,
                        detail = if (summaryData.isStrictModeEnabled) "Enforced throughout session" else "Standard focus"
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MetricSummaryTile(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(FocusShapes.large)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary,
                    fontSize = 15.sp
                )
            )
        }
    }
}

@Composable
private fun FeatureCheckRow(
    label: String,
    isActive: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) FocusColors.EmeraldSuccess.copy(alpha = 0.15f)
                        else FocusColors.TextMuted.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = if (isActive) FocusColors.EmeraldSuccess else FocusColors.TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = FocusColors.TextPrimary,
                    fontSize = 13.sp
                )
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isActive) FocusColors.TextSecondary else FocusColors.TextMuted,
                fontSize = 11.5.sp
            )
        )
    }
}
