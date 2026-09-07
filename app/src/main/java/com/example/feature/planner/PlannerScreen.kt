package com.example.feature.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusCardStyle
import com.example.core.design.FocusColors
import com.example.core.design.LocalFocusColors
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.model.StudyPlanItem
import com.example.feature.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class CalendarDay(
    val dayOfWeek: String,
    val dayOfMonth: String,
    val fullDate: Date,
    val isToday: Boolean
)

private data class SuggestedPreset(
    val id: String,
    val emoji: String,
    val title: String,
    val timeRange: String,
    val startTime: String,
    val endTime: String,
    val repeatDays: String,
    val subject: String
)

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

    val isDark = LocalFocusColors.current.isDark

    // Adaptive color tokens
    val bgColor = if (isDark) Color(0xFF0D0E11) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF16181D) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF26282E) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)
    val pillBg = if (isDark) Color(0xFF1E2024) else Color(0xFFF1F5F9)
    val pillBorder = if (isDark) Color(0xFF2E3138) else Color(0xFFE2E8F0)
    val selectedDayBg = if (isDark) Color(0xFF1E4620) else Color(0xFFDCFCE7)
    val selectedDayBorder = if (isDark) Color(0xFF2A612D) else Color(0xFF86EFAC)
    val selectedDayText = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D)
    val floatingBtnBg = if (isDark) Color.White else Color(0xFF0F172A)
    val floatingBtnText = if (isDark) Color.Black else Color.White

    var selectedDate by remember { mutableStateOf(Date()) }
    var isSuggestedExpanded by remember { mutableStateOf(true) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAddEditScheduleDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<FocusScheduleEntity?>(null) }
    var scheduleToDelete by remember { mutableStateOf<FocusScheduleEntity?>(null) }

    val dismissedSuggestions = remember { mutableStateListOf<String>() }

    // Presets: Morning Study, Afternoon Study, Night Study
    val suggestedPresets = remember {
        listOf(
            SuggestedPreset(
                id = "morning_study",
                emoji = "🌅",
                title = "Morning Study",
                timeRange = "08:00 AM - 10:00 AM",
                startTime = "08:00",
                endTime = "10:00",
                repeatDays = "Mon - Fri",
                subject = "Morning Focus"
            ),
            SuggestedPreset(
                id = "afternoon_study",
                emoji = "☀️",
                title = "Afternoon Study",
                timeRange = "02:00 PM - 04:00 PM",
                startTime = "14:00",
                endTime = "16:00",
                repeatDays = "Mon - Fri",
                subject = "General Study"
            ),
            SuggestedPreset(
                id = "night_study",
                emoji = "🌙",
                title = "Night Study",
                timeRange = "09:00 PM - 11:00 PM",
                startTime = "21:00",
                endTime = "23:00",
                repeatDays = "Mon - Sun",
                subject = "Deep Focus & Homework"
            )
        )
    }

    val weekDays = remember(selectedDate) {
        val cal = Calendar.getInstance().apply {
            time = selectedDate
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
        val numFormat = SimpleDateFormat("d", Locale.getDefault())

        val list = mutableListOf<CalendarDay>()
        for (i in 0 until 7) {
            val d = cal.time
            val isToday = cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
            list.add(
                CalendarDay(
                    dayOfWeek = dayFormat.format(d),
                    dayOfMonth = numFormat.format(d),
                    fullDate = d,
                    isToday = isToday
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        list
    }

    val monthYearFormatted = remember(selectedDate) {
        val month = SimpleDateFormat("MMMM", Locale.ENGLISH).format(selectedDate)
        val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(selectedDate)
        Pair(month, year)
    }

    // Filter schedules relevant to selected day
    val selectedDayOfWeekAbbr = remember(selectedDate) {
        SimpleDateFormat("EEE", Locale.ENGLISH).format(selectedDate).uppercase(Locale.ENGLISH)
    }

    val activeDaySchedules = remember(focusSchedules, selectedDayOfWeekAbbr) {
        focusSchedules.filter { entity ->
            val days = entity.daysOfWeek.uppercase(Locale.ENGLISH)
            days.contains(selectedDayOfWeekAbbr) || days.contains("DAILY") || days.isEmpty() ||
                    (days.contains("MON - FRI") && (selectedDayOfWeekAbbr in listOf("MON", "TUE", "WED", "THU", "FRI"))) ||
                    (days.contains("WEEKENDS") && (selectedDayOfWeekAbbr in listOf("SAT", "SUN")))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .testTag("planner_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                bottom = FocusCardStyle.BottomNavClearance + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Month & Year Header + Help Button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = monthYearFormatted.first,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = monthYearFormatted.second,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = textSecondary,
                                fontWeight = FontWeight.Normal,
                                fontSize = 24.sp
                            )
                        )
                    }

                    // Help Pill Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillBg)
                            .border(1.dp, pillBorder, RoundedCornerShape(20.dp))
                            .clickable { showHelpDialog = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("help_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = "Help",
                            tint = textPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Help",
                            color = textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 2. Weekly Calendar Strip (Monday - Sunday)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selCal = Calendar.getInstance().apply { time = selectedDate }

                    weekDays.forEach { day ->
                        val dayCal = Calendar.getInstance().apply { time = day.fullDate }
                        val isSelected = selCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                                selCal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)

                        val dayContainerModifier = if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(selectedDayBg)
                                .border(1.dp, selectedDayBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedDate = day.fullDate }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        }

                        Column(
                            modifier = dayContainerModifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = day.dayOfWeek,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) selectedDayText else textSecondary
                            )
                            Text(
                                text = day.dayOfMonth,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) selectedDayText else textPrimary
                            )
                        }
                    }
                }
            }

            // 3. Metrics Cards (Focus and Usage)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Focus Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("focus_metric_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardBg
                        ),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Focus",
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.todayStudyTime.ifEmpty { "0m" },
                                color = textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Usage Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("usage_metric_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardBg
                        ),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Usage",
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.totalScreenTime.ifEmpty { "5h 32m" },
                                color = textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. Day Schedules or Empty State
            if (activeDaySchedules.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(pillBg)
                                .border(1.dp, pillBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = textPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No schedules today",
                            color = textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Add focus blocks to plan your studies and take control",
                            color = textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = "Active Schedules (${activeDaySchedules.size})",
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                items(activeDaySchedules, key = { it.id }) { schedule ->
                    ScheduleCardItem(
                        schedule = schedule,
                        isDark = isDark,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onToggle = { enabled ->
                            viewModel.toggleFocusSchedule(schedule.id, enabled)
                        },
                        onEdit = {
                            editingSchedule = schedule
                            showAddEditScheduleDialog = true
                        },
                        onDelete = {
                            scheduleToDelete = schedule
                        },
                        onStartSession = {
                            onStartPlanSession(
                                StudyPlanItem(
                                    id = schedule.id,
                                    subject = schedule.subjectName.ifEmpty { schedule.title },
                                    topic = schedule.title,
                                    targetTime = schedule.startTime,
                                    accentColor = Color(0xFF4F46E5),
                                    isCompleted = false,
                                    startTime = schedule.startTime,
                                    endTime = schedule.endTime,
                                    targetDate = selectedDate.time
                                )
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // 5. "Suggested schedules" Collapsible Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isSuggestedExpanded = !isSuggestedExpanded }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Suggested schedules",
                            color = textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isSuggestedExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (isSuggestedExpanded) "Collapse" else "Expand",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AnimatedVisibility(
                        visible = isSuggestedExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            suggestedPresets.filter { it.id !in dismissedSuggestions }.forEach { preset ->
                                SuggestedScheduleCard(
                                    preset = preset,
                                    isDark = isDark,
                                    cardBg = cardBg,
                                    cardBorder = cardBorder,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    onDismiss = {
                                        dismissedSuggestions.add(preset.id)
                                    },
                                    onAdd = {
                                        viewModel.addFocusSchedule(
                                            title = preset.title,
                                            daysOfWeek = "MON,TUE,WED,THU,FRI",
                                            startTime = preset.startTime,
                                            endTime = preset.endTime,
                                            isAutoStartSession = true,
                                            mode = "TIMER",
                                            subjectName = preset.subject,
                                            repeatEnabled = true,
                                            scheduledDateMillis = selectedDate.time,
                                            breakMinutes = 5,
                                            description = "Scheduled daily routine",
                                            blockedAppPackages = emptySet(),
                                            blockNotifications = false
                                        )
                                        dismissedSuggestions.add(preset.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Floating "+ Add Schedule" Button
        Button(
            onClick = {
                editingSchedule = null
                showAddEditScheduleDialog = true
            },
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = floatingBtnBg,
                contentColor = floatingBtnText
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .padding(bottom = FocusCardStyle.BottomNavClearance)
                .align(Alignment.BottomCenter)
                .testTag("add_schedule_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = floatingBtnText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Add Schedule",
                color = floatingBtnText,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // 7. Glass Bottom Navigation
        FocusBottomNavigation(
            selectedTab = BottomTab.PLANNER,
            onTabSelected = onTabSelected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("FocusShield Planner", fontWeight = FontWeight.Bold, color = textPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "• Tap any day in the top calendar strip to view or customize focus routines for that date.",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        "• Use 'Add Schedule' to configure automated distraction blocking during your designated study hours.",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        "• Quick suggestions let you add Morning, Afternoon, or Night study blocks with a single tap.",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it", color = FocusColors.Primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = cardBg
        )
    }

    // Add / Edit Schedule Dialog
    if (showAddEditScheduleDialog) {
        FocusScheduleDialog(
            initialSchedule = editingSchedule,
            initialBlockNotifications = editingSchedule?.blockNotifications ?: false,
            availableBlockedApps = blockedApps,
            onOpenBlockedApps = onOpenBlockedApps,
            onDismiss = {
                showAddEditScheduleDialog = false
                editingSchedule = null
            },
            onSave = { title, days, start, end, autoStart, mode, subject, repeat, dateMillis, breakMins, desc, pkgs, blockNotifs ->
                if (editingSchedule != null) {
                    viewModel.updateFocusSchedule(
                        editingSchedule!!.copy(
                            title = title,
                            daysOfWeek = days,
                            startTime = start,
                            endTime = end,
                            isAutoStartSession = autoStart,
                            mode = mode,
                            subjectName = subject,
                            repeatEnabled = repeat,
                            scheduledDateMillis = dateMillis,
                            breakMinutes = breakMins,
                            description = desc,
                            blockedAppPackages = pkgs.joinToString(","),
                            blockNotifications = blockNotifs
                        )
                    )
                } else {
                    viewModel.addFocusSchedule(
                        title = title,
                        daysOfWeek = days,
                        startTime = start,
                        endTime = end,
                        isAutoStartSession = autoStart,
                        mode = mode,
                        subjectName = subject,
                        repeatEnabled = repeat,
                        scheduledDateMillis = dateMillis,
                        breakMinutes = breakMins,
                        description = desc,
                        blockedAppPackages = pkgs,
                        blockNotifications = blockNotifs
                    )
                }
                showAddEditScheduleDialog = false
                editingSchedule = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = {
                Text("Delete Schedule?", fontWeight = FontWeight.Bold, color = textPrimary)
            },
            text = {
                Text(
                    "Are you sure you want to remove \"${scheduleToDelete?.title}\" from your automated planner?",
                    color = textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scheduleToDelete?.let { viewModel.deleteFocusSchedule(it.id) }
                        scheduleToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text("Cancel", color = textSecondary)
                }
            },
            containerColor = cardBg
        )
    }
}

@Composable
private fun SuggestedScheduleCard(
    preset: SuggestedPreset,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val addBtnBg = if (isDark) Color(0xFF2A2D33) else Color(0xFFE2E8F0)
    val addBtnText = if (isDark) Color.White else Color(0xFF0F172A)
    val closeBtnBg = if (isDark) Color(0xFF22242A) else Color(0xFFF1F5F9)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("suggested_schedule_${preset.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dismiss ✕ Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(closeBtnBg)
                    .clickable { onDismiss() }
                    .testTag("dismiss_suggestion_${preset.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Emoji / Icon
            Text(
                text = preset.emoji,
                fontSize = 26.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = preset.title,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = preset.timeRange,
                    color = textSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = preset.repeatDays,
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // "+ Add" Pill Button
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = addBtnBg,
                    contentColor = addBtnText
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("add_suggested_${preset.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = addBtnText,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add",
                    color = addBtnText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ScheduleCardItem(
    schedule: FocusScheduleEntity,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconBg = if (isDark) Color(0xFF1E4620) else Color(0xFFDCFCE7)
    val iconTint = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D)
    val scheduleEmoji = remember(schedule.startTime) {
        getEmojiForTime(schedule.startTime)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("schedule_card_${schedule.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scheduleEmoji,
                            fontSize = 18.sp
                        )
                    }

                    Column {
                        Text(
                            text = schedule.title,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (schedule.subjectName.isNotEmpty()) {
                            Text(
                                text = schedule.subjectName,
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF22C55E),
                        uncheckedThumbColor = textSecondary,
                        uncheckedTrackColor = if (isDark) Color(0xFF2C2F36) else Color(0xFFE2E8F0)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${schedule.startTime} - ${schedule.endTime}",
                        color = textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = schedule.daysOfWeek.ifEmpty { "Daily" },
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onStartSession,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Start Session",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
