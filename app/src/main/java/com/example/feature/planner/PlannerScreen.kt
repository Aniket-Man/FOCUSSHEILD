package com.example.feature.planner

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusCardStyle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.design.FocusType
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation
import com.example.core.ui.StudyPlanRow
import com.example.core.ui.TodayPlanSummaryCard
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.model.StudyPlanItem
import com.example.feature.home.HomeViewModel
import com.example.feature.home.StudyPlanDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlannerScreen(
    viewModel: HomeViewModel,
    onTabSelected: (BottomTab) -> Unit,
    onStartPlanSession: (StudyPlanItem) -> Unit,
    onOpenBlockedApps: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusSchedules by viewModel.focusSchedules.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle(initialValue = com.example.data.preferences.FocusPreferences())

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Today's Plan, 1 = Automated Focus Schedules

    var showAddEditPlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<StudyPlanItem?>(null) }
    var planToDelete by remember { mutableStateOf<StudyPlanItem?>(null) }

    var showAddEditScheduleDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<FocusScheduleEntity?>(null) }
    var scheduleToDelete by remember { mutableStateOf<FocusScheduleEntity?>(null) }

    val todayFormatted = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())

    Scaffold(
        bottomBar = {
            FocusBottomNavigation(
                selectedTab = BottomTab.PLANNER,
                onTabSelected = onTabSelected
            )
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("planner_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = FocusCardStyle.BottomNavClearance
            ),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.base)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Study Planner",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = todayFormatted,
                            style = FocusType.secondary
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (activeSubTab == 0) {
                                editingPlan = null
                                showAddEditPlanDialog = true
                            } else {
                                editingSchedule = null
                                showAddEditScheduleDialog = true
                            }
                        },
                        shape = FocusShapes.pill,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = FocusColors.Primary.copy(alpha = 0.15f),
                            contentColor = FocusColors.Primary
                        ),
                        modifier = Modifier.testTag("planner_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (activeSubTab == 0) "Add Plan" else "New Schedule",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sub-Tab Switcher (Today's Plan vs Automated Schedules)
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.SurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                        ) {
                            PlannerSubTab(
                                label = "Today's Plan",
                                selected = activeSubTab == 0,
                                onClick = { activeSubTab = 0 },
                                modifier = Modifier.weight(1f)
                            )
                            PlannerSubTab(
                                label = "Automated Schedules",
                                selected = activeSubTab == 1,
                                onClick = { activeSubTab = 1 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (activeSubTab == 0) {
                // Today's Plan Content
                if (uiState.studyPlan.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                            TodayPlanSummaryCard(
                                totalPlannedTime = uiState.totalPlannedTime,
                                totalCompletedTime = uiState.totalCompletedTime,
                                remainingTime = uiState.remainingPlanTime,
                                completionPercentage = uiState.planCompletionPercentage
                            )
                        }
                    }

                    // Alerts Info Banner
                    item {
                        Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
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
                    }
                }

                // Overlap Warning Banner
                if (uiState.hasOverlapWarnings) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
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
                    }
                }

                // Schedule List
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FocusSpacing.screenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SCHEDULED BLOCKS",
                            style = FocusType.sectionLabel
                        )

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
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(FocusColors.PrimaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CalendarMonth,
                                            contentDescription = null,
                                            tint = FocusColors.Primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No study blocks scheduled for today",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = FocusColors.TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Plan your revision sessions, mock tests, and subject topics to stay on track.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = FocusColors.TextSecondary,
                                            fontSize = 12.sp
                                        ),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            editingPlan = null
                                            showAddEditPlanDialog = true
                                        },
                                        shape = FocusShapes.pill,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = FocusColors.Primary,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add First Study Plan", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                uiState.studyPlan.forEach { planItem ->
                                    StudyPlanRow(
                                        item = planItem,
                                        onStartClick = { onStartPlanSession(planItem) },
                                        onEditClick = {
                                            editingPlan = planItem
                                            showAddEditPlanDialog = true
                                        },
                                        onDeleteClick = { planToDelete = planItem },
                                        onToggleComplete = {
                                            viewModel.togglePlanCompletion(planItem.id, !planItem.isCompleted)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Automated Focus Schedules Sub-Tab
                item {
                    Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                        AutomatedFocusSchedulesSection(
                            schedules = focusSchedules,
                            onAddClick = {
                                editingSchedule = null
                                showAddEditScheduleDialog = true
                            },
                            onEditClick = { sch ->
                                editingSchedule = sch
                                showAddEditScheduleDialog = true
                            },
                            onDeleteClick = { sch ->
                                scheduleToDelete = sch
                            },
                            onToggleEnabled = { id, enabled ->
                                viewModel.toggleFocusSchedule(id, enabled)
                            }
                        )
                    }
                }
            }

        }
    }

    // Add / Edit Study Plan Dialog
    if (showAddEditPlanDialog) {
        StudyPlanDialog(
            initialPlan = editingPlan,
            existingPlans = uiState.studyPlan,
            onDismiss = {
                showAddEditPlanDialog = false
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
                showAddEditPlanDialog = false
                editingPlan = null
            }
        )
    }

    // Add / Edit Focus Schedule Dialog
    if (showAddEditScheduleDialog) {
        FocusScheduleDialog(
            initialSchedule = editingSchedule,
            onDismiss = {
                showAddEditScheduleDialog = false
                editingSchedule = null
            },
            initialBlockNotifications = editingSchedule?.blockNotifications ?: preferences.isBlockNotificationsEnabled,
            onBlockNotificationsChanged = { /* schedule-specific value is persisted on Save */ },
            onOpenBlockedApps = onOpenBlockedApps,
            availableBlockedApps = blockedApps,
            onSave = { title, daysOfWeek, startTime, endTime, isAutoStart, mode, subjectName, repeatEnabled, scheduledDateMillis, breakMinutes, description, blockedAppPackages, blockNotifications ->
                if (editingSchedule != null) {
                    viewModel.updateFocusSchedule(
                        editingSchedule!!.copy(
                            title = title,
                            daysOfWeek = daysOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            isAutoStartSession = isAutoStart,
                            mode = mode,
                            subjectName = subjectName,
                            repeatEnabled = repeatEnabled,
                            scheduledDateMillis = scheduledDateMillis,
                            breakMinutes = breakMinutes,
                            description = description,
                            blockedAppPackages = blockedAppPackages.joinToString(","),
                            blockNotifications = blockNotifications
                        )
                    )
                } else {
                    viewModel.addFocusSchedule(
                        title = title,
                        daysOfWeek = daysOfWeek,
                        startTime = startTime,
                        endTime = endTime,
                        isAutoStartSession = isAutoStart,
                        mode = mode,
                        subjectName = subjectName,
                        repeatEnabled = repeatEnabled,
                        scheduledDateMillis = scheduledDateMillis,
                        breakMinutes = breakMinutes,
                        description = description,
                        blockedAppPackages = blockedAppPackages,
                        blockNotifications = blockNotifications
                    )
                }
                showAddEditScheduleDialog = false
                editingSchedule = null
            }
        )
    }

    // Delete Plan Confirmation
    if (planToDelete != null) {
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text(text = "Delete Study Plan?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove \"${planToDelete?.subject} - ${planToDelete?.topic}\" from your plan?") },
            confirmButton = {
                Button(
                    onClick = {
                        planToDelete?.let { viewModel.deleteStudyPlan(it.id) }
                        planToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusColors.CoralWarning)
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

    // Delete Schedule Confirmation
    if (scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text(text = "Delete Automated Schedule?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${scheduleToDelete?.title}\"? Automatic app blocking will no longer trigger for this time window.") },
            confirmButton = {
                Button(
                    onClick = {
                        scheduleToDelete?.let { viewModel.deleteFocusSchedule(it.id) }
                        scheduleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusColors.CoralWarning)
                ) {
                    Text("Delete Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * A pill segment in the planner's Today / Automated switcher. Selected fills with the primary
 * accent; the label flips to white on the fill (not black), matching the accent's contrast.
 */
@Composable
private fun PlannerSubTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = FocusShapes.pill,
        color = if (selected) FocusColors.Primary else Color.Transparent,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = FocusType.secondary.copy(
                    color = if (selected) Color.White else FocusColors.TextSecondary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}
