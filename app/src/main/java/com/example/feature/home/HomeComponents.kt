package com.example.feature.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.*
import com.example.core.ui.*
import com.example.data.model.StudyPlanItem
import com.example.data.repository.StudyPlanRepository
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager

@Composable
fun HomeSessionSection(
    viewModel: HomeViewModel,
    onNavigateToActiveSession: () -> Unit,
    onStartSession: () -> Unit
) {
    val session by viewModel.sessionUiState.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .padding(horizontal = FocusSpacing.screenHorizontal)
            .padding(bottom = 10.dp)
    ) {
        FocusSessionCard(
            isActive = session.isSessionActive,
            timeString = session.timeRemainingString,
            subjectName = session.subject.ifBlank { "Focus Session" },
            subtitleText = session.pomodoroCycleText
                ?: if (session.isSessionActive) {
                    if (session.topic.isNotBlank()) "${session.topic} • Active" else "Active Session"
                } else null,
            isPaused = session.isPaused,
            onTogglePause = { viewModel.toggleSessionPause() },
            onCardClick = {
                if (session.isSessionActive) {
                    onNavigateToActiveSession()
                } else {
                    onStartSession()
                }
            },
            onStartSessionClick = onStartSession
        )
    }
}

@Composable
fun HomeUpdateBanner(
    updateState: com.example.feature.update.domain.UpdateState,
    onPrimaryAction: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ... (code for HomeUpdateBanner)
}
// ... and so on
