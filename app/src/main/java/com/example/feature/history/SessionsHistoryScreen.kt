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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.sessionModeTitle
import com.example.core.util.TimeFormatter
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.model.SessionMode
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsHistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToStartSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deletingSessionId by viewModel.deletingSessionId.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            FocusScreenHeader(
                title = stringResource(R.string.history_title),
                onBack = onNavigateBack,
                subtitle = stringResource(R.string.history_subtitle),
                modifier = Modifier.statusBarsPadding(),
                backButtonModifier = Modifier.testTag("sessions_history_back_button")
            )
        },
        containerColor = FocusColors.Background,
        modifier = modifier.navigationBarsPadding().testTag("sessions_history_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (operationError != null) {
                FocusStatusMessage(
                    message = operationError,
                    isError = true,
                    onDismiss = viewModel::clearOperationError,
                    modifier = Modifier.padding(
                        start = FocusSpacing.screenHorizontal,
                        end = FocusSpacing.screenHorizontal,
                        top = FocusSpacing.sm
                    )
                )
            }

            if (uiState.groupedSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                            text = stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 18.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.history_empty_body),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        ElevatedButton(
                            onClick = onNavigateToStartSession,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = FocusColors.Primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
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
                                text = stringResource(R.string.history_start_session),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = FocusSpacing.screenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = FocusSpacing.md,
                        bottom = FocusSpacing.xl
                    )
                ) {
                    item {
                        val totalFormatted = TimeFormatter.formatDetailed(uiState.totalStudyTimeMillis)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.history_total_label),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = FocusColors.TextMuted,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp,
                                            fontSize = 11.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = totalFormatted,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = FocusColors.Primary,
                                            fontSize = 24.sp
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(FocusSpacing.sm))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(FocusColors.PrimaryContainer)
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.history_completed_count,
                                            uiState.totalCompletedCount
                                        ),
                                        color = FocusColors.PrimaryDark,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    uiState.groupedSessions.forEach { group ->
                        item(key = group.dateMillis) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sessionGroupHeader(group),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = FocusColors.TextPrimary,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Text(
                                        text = TimeFormatter.formatDetailed(group.totalFocusTimeMillis),
                                        style = MaterialTheme.typography.bodySmall.copy(
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
                }
            }
        }

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
                    isDeleting = deletingSessionId == session.id,
                    onDelete = { showDeleteConfirm = true },
                    onClose = { viewModel.selectSession(null) }
                )
            }
        }

        if (showDeleteConfirm && uiState.selectedSession != null) {
            val session = uiState.selectedSession!!
            val displayName = if (session.subject.isBlank()) {
                sessionModeTitle(session.mode)
            } else {
                session.subject
            }
            val displayDate = remember(session.startTime) {
                java.text.DateFormat.getDateInstance(
                    java.text.DateFormat.MEDIUM,
                    Locale.getDefault()
                ).format(Date(session.startTime))
            }
            FocusDestructiveDialog(
                title = stringResource(R.string.history_delete_title),
                message = stringResource(R.string.history_delete_body, displayName, displayDate),
                confirmLabel = stringResource(R.string.history_delete_confirm),
                onConfirm = {
                    showDeleteConfirm = false
                    viewModel.deleteSession(session.id)
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

@Composable
private fun sessionGroupHeader(group: DateGroupedSessions): String {
    if (group.group == SessionDateGroup.TODAY) return stringResource(R.string.history_today)
    if (group.group == SessionDateGroup.YESTERDAY) return stringResource(R.string.history_yesterday)
    val configuration = LocalConfiguration.current
    return remember(group.dateMillis, configuration) {
        java.text.DateFormat.getDateInstance(
            java.text.DateFormat.MEDIUM,
            Locale.getDefault()
        ).format(Date(group.dateMillis))
    }
}

@Composable
private fun SessionHistoryCard(
    session: SessionRecordEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val timeFormat = remember(context, configuration) {
        android.text.format.DateFormat.getTimeFormat(context)
    }
    val startTimeStr = remember(session.startTime, timeFormat) { timeFormat.format(Date(session.startTime)) }
    val endTimeStr = remember(session.endTime, timeFormat) { timeFormat.format(Date(session.endTime)) }
    val actualTimeStr = TimeFormatter.formatDetailed(session.actualDurationMillis)
    val modeTitle = sessionModeTitle(session.mode)
    val openLabel = stringResource(R.string.history_card_action)

    val (statusText, statusBg, statusColor) = when {
        session.completed -> Triple(
            stringResource(R.string.history_status_completed),
            FocusColors.EmeraldLight,
            FocusColors.EmeraldText
        )
        session.cancelled -> Triple(
            stringResource(R.string.history_status_cancelled),
            FocusColors.CoralLight,
            FocusColors.CoralText
        )
        else -> Triple(
            stringResource(R.string.history_status_ended),
            FocusColors.AmberLight,
            FocusColors.AmberText
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
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
                        text = session.subject.ifBlank {
                            stringResource(R.string.history_mode_session, modeTitle)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 16.sp
                        )
                    )
                    if (session.topic.isNotBlank()) {
                        Text(
                            text = session.topic,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(FocusSpacing.sm))

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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
                        text = "• $modeTitle",
                        color = FocusColors.TextMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(FocusSpacing.sm))

                Text(
                    text = stringResource(R.string.history_time_range, startTimeStr, endTimeStr),
                    color = FocusColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun SessionDetailSheetContent(
    session: SessionRecordEntity,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val timeFormat = remember(context, configuration) {
        android.text.format.DateFormat.getTimeFormat(context)
    }
    val dateFormat = remember(configuration) {
        java.text.DateFormat.getDateInstance(
            java.text.DateFormat.MEDIUM,
            Locale.getDefault()
        )
    }
    val startTimeFormatted = remember(session.startTime, configuration) {
        "${dateFormat.format(Date(session.startTime))} • ${timeFormat.format(Date(session.startTime))}"
    }
    val endTimeFormatted = remember(session.endTime, configuration) {
        timeFormat.format(Date(session.endTime))
    }
    val modeTitle = sessionModeTitle(session.mode)

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.subject.ifBlank {
                        stringResource(R.string.history_mode_session, modeTitle)
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 22.sp
                    )
                )
                if (session.topic.isNotBlank()) {
                    Text(
                        text = session.topic,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, stringResource(R.string.action_close))
            }
        }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailMetricBox(
                title = stringResource(R.string.history_actual_study),
                value = TimeFormatter.formatDetailed(session.actualDurationMillis),
                modifier = Modifier.weight(1f)
            )
            DetailMetricBox(
                title = stringResource(R.string.history_mode_label),
                value = modeTitle,
                modifier = Modifier.weight(1f)
            )
        }

        if (session.mode == SessionMode.POMODORO) {
            DetailMetricBox(
                title = stringResource(R.string.history_pomodoro_progress),
                value = stringResource(
                    R.string.history_pomodoro_cycles,
                    session.completedPomodoroCycles,
                    session.pomodoroCycles
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        DetailMetricBox(
            title = stringResource(R.string.history_timestamps),
            value = stringResource(R.string.history_timestamps_value, startTimeFormatted, endTimeFormatted),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        ElevatedButton(
            onClick = onDelete,
            enabled = !isDeleting,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = FocusColors.CoralLight,
                contentColor = FocusColors.CoralText
            ),
            shape = FocusShapes.button,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("delete_session_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.history_delete_action),
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
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = FocusColors.TextPrimary,
                    fontSize = 14.sp
                )
            )
        }
    }
}
