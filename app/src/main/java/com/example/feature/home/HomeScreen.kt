package com.example.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.platform.LocalContext
import com.example.core.util.DeviceUsageStatsHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusCardStyle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.design.FocusType
import com.example.core.design.focusCard
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation
import com.example.core.ui.FocusHeader
import com.example.core.ui.FocusLottieAnimation
import com.example.core.ui.FocusSessionCard
import com.example.core.ui.ProgressCard
import com.example.core.ui.QuickActionCard
import com.example.core.ui.StatisticsOverview
import com.example.core.ui.StudyPlanRow
import com.example.core.ui.TodayPlanSummaryCard
import com.example.data.model.QuickActionType
import com.example.data.model.StudyPlanItem
import com.example.data.repository.StudyPlanRepository

/**
 * Screen 1: FocusShield Home Dashboard.
 * Accurately implements the reference design with comprehensive Today's Plan management.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    sessionViewModel: com.example.feature.session.SessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToStartSession: () -> Unit,
    onNavigateToActiveSession: () -> Unit,
    onNavigateToModeSelection: () -> Unit,
    onNavigateToPlannerTab: () -> Unit,
    onNavigateToStatsTab: () -> Unit,
    onNavigateToBlocksTab: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToStudyChannels: () -> Unit = {},
    onNavigateToBlockedApps: () -> Unit = {},
    onNavigateToAppLimits: () -> Unit = {},
    onStartPlanSession: (StudyPlanItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<StudyPlanItem?>(null) }
    var planToDelete by remember { mutableStateOf<StudyPlanItem?>(null) }
    var showFocusSetupSheet by remember { mutableStateOf(false) }

    if (showFocusSetupSheet) {
        com.example.feature.session.ui.FocusSessionSetupSheet(
            viewModel = sessionViewModel,
            onDismiss = { showFocusSetupSheet = false },
            onStartFocus = {
                showFocusSetupSheet = false
                onNavigateToActiveSession()
            }
        )
    }

    Scaffold(
        bottomBar = {
            FocusBottomNavigation(
                selectedTab = BottomTab.FOCUS,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomTab.FOCUS -> { /* already on home */ }
                        BottomTab.PLANNER -> onNavigateToPlannerTab()
                        BottomTab.STATS -> onNavigateToStatsTab()
                        BottomTab.BLOCKS -> onNavigateToBlocksTab()
                    }
                }
            )
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("home_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            // The floating bottom dock is drawn as an overlay, not a layout sibling, so the list
            // must reserve clearance itself or its final items (the Quick Actions row / stats) sit
            // hidden behind the dock.
            contentPadding = PaddingValues(bottom = FocusCardStyle.BottomNavClearance),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
        ) {
            // 1. Header (Logo, Brand, Slogan, Notifications, Avatar)
            item {
                FocusHeader(
                    photoUri = uiState.userPhotoUri,
                    avatarPresetId = uiState.userAvatarPreset,
                    onNotificationClick = { /* notification click */ },
                    onProfileClick = onNavigateToProfile
                )
            }

            // 2. Focus Session Banner Card (purple gradient card)
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    FocusSessionCard(
                        isActive = uiState.isSessionActive,
                        timeString = uiState.timeRemainingString,
                        subjectName = uiState.activeSubject,
                        subtitleText = uiState.pomodoroCycleText ?: if (uiState.isSessionActive) "${uiState.activeTopic} • Active" else null,
                        isPaused = uiState.isPaused,
                        onTogglePause = { viewModel.toggleSessionPause() },
                        onCardClick = {
                            if (uiState.isSessionActive) {
                                onNavigateToActiveSession()
                            } else {
                                showFocusSetupSheet = true
                            }
                        },
                        onStartSessionClick = { showFocusSetupSheet = true }
                    )
                }
            }

            // 3. Today's Progress Card (Study Time, Bar Chart, Circular Gauge)
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    ProgressCard(
                        studyTime = uiState.todayStudyTime,
                        progressPercentage = uiState.todayProgressPercentage,
                        weeklyBars = uiState.weeklyBars
                    )
                }
            }

            // 3b. Phone Usage vs Focus Time Card (Digital Wellbeing Integration)
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    TodayPhoneUsageVsFocusCard(
                        totalScreenTime = uiState.totalScreenTime,
                        todayFocusTime = uiState.todayStudyTime,
                        completedTimerSessions = uiState.todayCompletedSessionCount,
                        focusRatioPct = uiState.focusToScreenRatioPercentage,
                        hasUsagePermission = uiState.hasUsagePermission
                    )
                }
            }

            // 3c. All-Time Lifetime Study Batch Card
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    AllTimeStudyBatchCard(
                        allTimeStudyTime = uiState.allTimeStudyTime,
                        allTimeSessionsCount = uiState.allTimeSessionCount
                    )
                }
            }

            // 4. Quick Actions Section (2x2 Grid)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.screenHorizontal)
                ) {
                    Text(
                        text = "Quick Actions",
                        style = FocusType.cardTitle
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 1: Start Pomodoro | Study Channels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            item = uiState.quickActions[0],
                            modifier = Modifier.weight(1f),
                            onClick = { showFocusSetupSheet = true }
                        )
                        QuickActionCard(
                            item = uiState.quickActions[1],
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToStudyChannels
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 2: Blocked Apps | Planner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            item = uiState.quickActions[2],
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToBlockedApps
                        )
                        QuickActionCard(
                            item = uiState.quickActions[3],
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToPlannerTab
                        )
                    }
                }
            }

            // 4b. App Limits & Daily Budgets Banner
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FocusShapes.card)
                            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
                            .clickable(onClick = onNavigateToAppLimits)
                            .testTag("home_app_limits_banner"),
                        color = FocusColors.Surface
                    ) {
                        Row(
                            modifier = Modifier.padding(FocusSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(FocusColors.PrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = FocusColors.Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "App Limits & Daily Budgets",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = "Set daily caps & 2m–20m temporary usage sessions",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = FocusColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 5. Today's Study Plan Section with Full CRUD, Overlap Alert & Progress Summary
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.screenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section Header with + Add Study Plan button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Today's Plan",
                                style = FocusType.cardTitle
                            )
                            Text(
                                text = "${uiState.studyPlan.size} Planned Blocks",
                                style = FocusType.secondary
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                editingPlan = null
                                showAddEditDialog = true
                            },
                            shape = FocusShapes.pill,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = FocusColors.Primary.copy(alpha = 0.12f),
                                contentColor = FocusColors.Primary
                            ),
                            modifier = Modifier.testTag("add_study_plan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Study",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Summary Progress Card
                    if (uiState.studyPlan.isNotEmpty()) {
                        TodayPlanSummaryCard(
                            totalPlannedTime = uiState.totalPlannedTime,
                            totalCompletedTime = uiState.totalCompletedTime,
                            remainingTime = uiState.remainingPlanTime,
                            completionPercentage = uiState.planCompletionPercentage
                        )

                        // Scheduled Notifications Info Banner
                        Surface(
                            shape = FocusShapes.medium,
                            color = FocusColors.PrimaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, FocusColors.Primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = FocusColors.Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Plan alerts active • Tap notification to start session directly",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.Primary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    // Overlap Warning Banner
                    if (uiState.hasOverlapWarnings) {
                        Surface(
                            shape = FocusShapes.medium,
                            color = FocusColors.CoralWarning.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, FocusColors.CoralWarning.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = FocusColors.CoralWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Time Overlap Detected: Some scheduled study blocks overlap in time.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.CoralWarning,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }

                    // Plan Items List
                    if (uiState.studyPlan.isEmpty()) {
                        Surface(
                            shape = FocusShapes.medium,
                            color = FocusColors.Surface,
                            border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    tint = FocusColors.TextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No study blocks scheduled for today",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = FocusColors.TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        editingPlan = null
                                        showAddEditDialog = true
                                    },
                                    shape = FocusShapes.pill
                                ) {
                                    Text("Plan First Session")
                                }
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.studyPlan.forEach { planItem ->
                                androidx.compose.runtime.key(planItem.id) {
                                    StudyPlanRow(
                                        item = planItem,
                                        onStartClick = {
                                            onStartPlanSession(planItem)
                                        },
                                        onEditClick = {
                                            editingPlan = planItem
                                            showAddEditDialog = true
                                        },
                                        onDeleteClick = {
                                            planToDelete = planItem
                                        },
                                        onToggleComplete = {
                                            viewModel.togglePlanCompletion(planItem.id, !planItem.isCompleted)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Statistics Metric Overview (Sessions, Blocked attempts, Focus rate)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.screenHorizontal)
                ) {
                    Text(
                        text = "Today's Statistics",
                        style = FocusType.cardTitle
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    StatisticsOverview(metrics = uiState.statistics)
                }
            }

        }
    }

    // Add / Edit Study Plan Dialog
    androidx.activity.compose.BackHandler(enabled = planToDelete != null) {
        planToDelete = null
    }

    androidx.activity.compose.BackHandler(enabled = showAddEditDialog) {
        showAddEditDialog = false
        editingPlan = null
    }

    if (showAddEditDialog) {
        StudyPlanDialog(
            initialPlan = editingPlan,
            existingPlans = uiState.studyPlan,
            onDismiss = {
                showAddEditDialog = false
                editingPlan = null
            },
            onSave = { subject, topic, startTime, endTime, notes ->
                if (editingPlan != null) {
                    viewModel.updateStudyPlan(
                        id = editingPlan!!.id,
                        subjectName = subject,
                        topicName = topic,
                        startTime = startTime,
                        endTime = endTime,
                        targetDate = editingPlan!!.targetDate,
                        notes = notes,
                        isCompleted = editingPlan!!.isCompleted
                    )
                } else {
                    viewModel.addStudyPlan(
                        subjectName = subject,
                        topicName = topic,
                        startTime = startTime,
                        endTime = endTime,
                        notes = notes
                    )
                }
                showAddEditDialog = false
                editingPlan = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (planToDelete != null) {
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = {
                Text(
                    text = "Delete Study Plan?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to remove \"${planToDelete?.subject} - ${planToDelete?.topic}\" from today's plan?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        planToDelete?.let { viewModel.deleteStudyPlan(it.id) }
                        planToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.CoralWarning
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Milestone Reward Celebration Dialog (2 Hours Lifetime Study Badge)
    uiState.pendingRewardBadge?.let { badge ->
        com.example.feature.rewards.ui.RewardCelebrationDialog(
            badge = badge,
            onClaimReward = { viewModel.claimReward(badge.id) },
            onNavigateToProfileRewards = {
                viewModel.claimReward(badge.id)
                onNavigateToProfile()
            }
        )
    }
}

/**
 * Comprehensive Dialog for Adding & Editing Study Plans
 * with Subject selectors, Start/End time inputs, live duration preview, and overlap detection.
 */
@Composable
fun StudyPlanDialog(
    initialPlan: StudyPlanItem?,
    existingPlans: List<StudyPlanItem>,
    onDismiss: () -> Unit,
    onSave: (subject: String, topic: String, startTime: String, endTime: String, notes: String) -> Unit
) {
    val subjects = listOf("Physics", "Chemistry", "Mathematics", "Biology")
    var selectedSubject by remember { mutableStateOf(initialPlan?.subject ?: "Physics") }
    var topicText by remember { mutableStateOf(initialPlan?.topic ?: "") }
    var startTimeText by remember { mutableStateOf(initialPlan?.startTime ?: "08:00") }
    var endTimeText by remember { mutableStateOf(initialPlan?.endTime ?: "10:00") }
    var notesText by remember { mutableStateOf(initialPlan?.notes ?: "") }
    var customSubjectText by remember { mutableStateOf("") }
    var isCustomSubject by remember { mutableStateOf(!subjects.contains(initialPlan?.subject ?: "Physics")) }

    val startMins = StudyPlanRepository.parseTimeToMinutes(startTimeText)
    val endMins = StudyPlanRepository.parseTimeToMinutes(endTimeText)
    val durationMins = StudyPlanRepository.calculateDurationMinutes(startMins, endMins)
    val durationDisplay = StudyPlanRepository.formatDurationHoursMins(durationMins)

    // Overlap check in dialog
    val hasOverlap = existingPlans.any { other ->
        if (initialPlan != null && other.id == initialPlan.id) return@any false
        val otherStart = StudyPlanRepository.parseTimeToMinutes(other.startTime)
        val otherEnd = StudyPlanRepository.parseTimeToMinutes(other.endTime)
        // Checks collision
        (startMins < otherEnd && endMins > otherStart)
    }

    val finalSubject = if (isCustomSubject && customSubjectText.isNotBlank()) customSubjectText else selectedSubject
    val isFormValid = topicText.isNotBlank() && finalSubject.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPlan != null) "Edit Study Plan" else "Add Study Plan",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Subject Selector Chips
                Column {
                    Text(
                        text = "Subject",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FocusColors.TextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subjects.take(3).forEach { subj ->
                            val isSelected = !isCustomSubject && selectedSubject == subj
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(FocusShapes.pill)
                                    .background(if (isSelected) FocusColors.Primary else FocusColors.SurfaceVariant)
                                    .clickable {
                                        isCustomSubject = false
                                        selectedSubject = subj
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = subj,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color.White else FocusColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isBio = !isCustomSubject && selectedSubject == "Biology"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(FocusShapes.pill)
                                .background(if (isBio) FocusColors.Primary else FocusColors.SurfaceVariant)
                                .clickable {
                                    isCustomSubject = false
                                    selectedSubject = "Biology"
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Biology",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isBio) Color.White else FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(FocusShapes.pill)
                                .background(if (isCustomSubject) FocusColors.Primary else FocusColors.SurfaceVariant)
                                .clickable {
                                    isCustomSubject = true
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Other / Custom",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isCustomSubject) Color.White else FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    if (isCustomSubject) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customSubjectText,
                            onValueChange = { customSubjectText = it },
                            placeholder = { Text("Enter subject name") },
                            singleLine = true,
                            shape = FocusShapes.small,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Topic Input
                OutlinedTextField(
                    value = topicText,
                    onValueChange = { topicText = it },
                    label = { Text("Topic / Chapter") },
                    placeholder = { Text("e.g. Electrostatics, Chemical Bonding") },
                    singleLine = true,
                    shape = FocusShapes.small,
                    modifier = Modifier.fillMaxWidth().testTag("plan_topic_input")
                )

                // Time Pickers (Start Time & End Time)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startTimeText,
                        onValueChange = { startTimeText = it },
                        label = { Text("Start Time") },
                        placeholder = { Text("08:00") },
                        singleLine = true,
                        shape = FocusShapes.small,
                        modifier = Modifier.weight(1f).testTag("plan_start_time_input")
                    )

                    OutlinedTextField(
                        value = endTimeText,
                        onValueChange = { endTimeText = it },
                        label = { Text("End Time") },
                        placeholder = { Text("10:00") },
                        singleLine = true,
                        shape = FocusShapes.small,
                        modifier = Modifier.weight(1f).testTag("plan_end_time_input")
                    )
                }

                // Calculated Duration Pill & Overlap Alert
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Duration: $durationDisplay ($durationMins min)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    if (hasOverlap) {
                        Text(
                            text = "⚠️ Overlaps existing",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.CoralWarning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Optional Notes
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes / Goals (Optional)") },
                    placeholder = { Text("e.g. Solve 20 JEE Advanced questions") },
                    maxLines = 2,
                    shape = FocusShapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isFormValid) {
                        onSave(finalSubject, topicText.trim(), startTimeText.trim(), endTimeText.trim(), notesText.trim())
                    }
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary
                ),
                modifier = Modifier.testTag("save_study_plan_button")
            ) {
                Text(if (initialPlan != null) "Update Plan" else "Save Plan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TodayPhoneUsageVsFocusCard(
    totalScreenTime: String,
    todayFocusTime: String,
    completedTimerSessions: Int,
    focusRatioPct: Int,
    hasUsagePermission: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .testTag("today_phone_usage_vs_focus_card"),
        color = FocusColors.Surface
    ) {
        Column(modifier = Modifier.padding(FocusSpacing.lg)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(FocusColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TODAY'S USAGE VS FOCUS",
                        style = FocusType.sectionLabel
                    )
                }

                Surface(
                    shape = FocusShapes.pill,
                    color = FocusColors.EmeraldSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(FocusColors.EmeraldSuccess)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$completedTimerSessions Sessions Online",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.EmeraldSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two main stat columns: Total Phone Usage vs Focus Study Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Stat Column: Phone Usage Time
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(FocusShapes.medium)
                        .clickable { DeviceUsageStatsHelper.openDigitalWellbeingSettings(context) },
                    color = FocusColors.SurfaceSubtle,
                    border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Total Phone Usage",
                                style = FocusType.secondary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "Digital Wellbeing",
                                tint = FocusColors.TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (hasUsagePermission) totalScreenTime else "Tap to sync",
                            style = FocusType.statNumber.copy(
                                color = if (hasUsagePermission) FocusColors.TextPrimary else FocusColors.Primary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasUsagePermission) "Total daily screen-on" else "Grant usage access permission",
                            style = FocusType.caption
                        )
                    }
                }

                // Right Stat Column: Focus Time Today
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(FocusShapes.medium),
                    color = FocusColors.PrimaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, FocusColors.Primary.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Focus Time Today",
                            style = FocusType.secondary.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = todayFocusTime,
                            style = FocusType.statNumber.copy(color = FocusColors.Primary)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Timer online focus",
                            style = FocusType.caption.copy(color = FocusColors.Primary.copy(alpha = 0.8f))
                        )
                    }
                }
            }

            if (hasUsagePermission && focusRatioPct > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Focus Ratio: $focusRatioPct% of daily screen time spent studying",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun AllTimeStudyBatchCard(
    allTimeStudyTime: String,
    allTimeSessionsCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .testTag("all_time_study_batch_card"),
        color = FocusColors.Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FocusSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lottie Streak Celebration Animation
            FocusLottieAnimation(
                rawRes = R.raw.streak_celebration,
                modifier = Modifier.size(52.dp),
                iterations = 1
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ALL-TIME STUDY",
                        style = FocusType.sectionLabel,
                        maxLines = 1
                    )
                    // The badge sits in the row's leftover space and never shrinks, so its single
                    // word can't be squeezed onto two lines the way the old fixed padding allowed.
                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.Primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Lifetime",
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = FocusType.caption.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = allTimeStudyTime,
                    style = FocusType.statNumberLarge
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$allTimeSessionsCount overall study sessions tracked since installation",
                    style = FocusType.caption
                )
            }
        }
    }
}

