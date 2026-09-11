package com.example.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation
import com.example.core.util.TimeFormatter
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.model.SessionMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.automirrored.rounded.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsHistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToStartSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("sessions_history_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Study History",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "Complete log of your focused study sessions",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.navigationBarsPadding().testTag("sessions_history_screen")
    ) { innerPadding ->
        if (uiState.groupedSessions.isEmpty()) {
            // Polished Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = FocusSpacing.screenHorizontal),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(FocusColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No Study Sessions Yet",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 18.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Start and complete a focus session to record your daily study logs and track progress.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    ElevatedButton(
                        onClick = onNavigateToStartSession,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = FocusColors.Primary,
                            contentColor = Color.White
                        ),
                        shape = FocusShapes.button,
                        modifier = Modifier.testTag("empty_state_start_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Focus Session",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = FocusSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
            ) {
                // Top Summary Header Card
                item {
                    val totalFormatted = TimeFormatter.formatDetailed(uiState.totalStudyTimeMillis)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 2.dp,
                                shape = FocusShapes.card,
                                ambientColor = Color.Black.copy(alpha = 0.02f),
                                spotColor = Color.Black.copy(alpha = 0.04f)
                            )
                            .clip(FocusShapes.card)
                            .background(FocusColors.Surface)
                            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
                            .padding(FocusSpacing.lg)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL RECORDED STUDY",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                                        color = FocusColors.TextMuted,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        fontSize = 11.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = totalFormatted,
                                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.Primary,
                                        fontSize = 24.sp
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(FocusColors.PrimaryContainer)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${uiState.totalCompletedCount} Completed",
                                    color = FocusColors.PrimaryDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Grouped Session Lists
                uiState.groupedSessions.forEach { group ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = group.dateHeader,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.TextPrimary,
                                        fontSize = 14.sp
                                    )
                                )
                                Text(
                                    text = TimeFormatter.formatDetailed(group.totalFocusTimeMillis),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                group.sessions.forEach { session ->
                                    SessionHistoryCard(
                                        session = session,
                                        onClick = { viewModel.selectSession(session) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(FocusSpacing.base))
                }
            }
        }

        // Session Detail Modal Bottom Sheet
        androidx.activity.compose.BackHandler(enabled = uiState.selectedSession != null) {
            viewModel.selectSession(null)
        }

        if (uiState.selectedSession != null) {
            val session = uiState.selectedSession!!
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectSession(null) },
                sheetState = sheetState,
                containerColor = FocusColors.Surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                SessionDetailSheetContent(
                    session = session,
                    onDelete = {
                        viewModel.deleteSession(session.id)
                    },
                    onClose = { viewModel.selectSession(null) }
                )
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: SessionRecordEntity,
    onClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val startTimeStr = timeFormat.format(Date(session.startTime))
    val endTimeStr = timeFormat.format(Date(session.endTime))

    val actualTimeStr = TimeFormatter.formatDetailed(session.actualDurationMillis)

    val (statusText, statusBg, statusColor) = when {
        session.completed -> Triple("Completed", FocusColors.EmeraldLight, Color(0xFF15803D))
        session.cancelled -> Triple("Cancelled", FocusColors.CoralLight, Color(0xFFC2410C))
        else -> Triple("Ended", FocusColors.AmberLight, Color(0xFFB45309))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .clickable(onClick = onClick)
            .padding(FocusSpacing.base)
            .testTag("session_history_card_${session.id}")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.subject,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = session.topic,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom row: Time metrics & Mode tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (session.mode) {
                            SessionMode.TIMER -> Icons.Rounded.HourglassBottom
                            SessionMode.STOPWATCH -> Icons.Rounded.Timer
                            SessionMode.POMODORO -> Icons.Rounded.Schedule
                        },
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = actualTimeStr,
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${session.mode.label}",
                        color = FocusColors.TextMuted,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "$startTimeStr - $endTimeStr",
                    color = FocusColors.TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SessionDetailSheetContent(
    session: SessionRecordEntity,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val fullDateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm a", Locale.getDefault())
    val startTimeFormatted = fullDateFormat.format(Date(session.startTime))
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val endTimeFormatted = timeFormat.format(Date(session.endTime))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.80f)
            .padding(horizontal = FocusSpacing.xl)
            .padding(bottom = FocusSpacing.xl)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.subject,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 22.sp
                    )
                )
                Text(
                    text = session.topic,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 15.sp
                    )
                )
            }

            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
            }
        }

        // Goal snippet if set
        if (session.goal.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.medium)
                    .background(FocusColors.SurfaceSubtle)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = session.goal,
                        color = FocusColors.TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailMetricBox(
                title = "ACTUAL STUDY",
                value = TimeFormatter.formatDetailed(session.actualDurationMillis),
                modifier = Modifier.weight(1f)
            )
            DetailMetricBox(
                title = "MODE",
                value = session.mode.label,
                modifier = Modifier.weight(1f)
            )
        }

        if (session.mode == SessionMode.POMODORO) {
            DetailMetricBox(
                title = "POMODORO PROGRESS",
                value = "${session.completedPomodoroCycles} of ${session.pomodoroCycles} Cycles Completed",
                modifier = Modifier.fillMaxWidth()
            )
        }

        DetailMetricBox(
            title = "TIMESTAMPS",
            value = "$startTimeFormatted  ➔  $endTimeFormatted",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Delete button
        ElevatedButton(
            onClick = onDelete,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = FocusColors.CoralLight,
                contentColor = Color(0xFFC2410C)
            ),
            shape = FocusShapes.button,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Delete Session Record",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DetailMetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(FocusShapes.medium)
            .background(FocusColors.SurfaceVariant)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = FocusColors.TextPrimary,
                    fontSize = 14.sp
                )
            )
        }
    }
}
