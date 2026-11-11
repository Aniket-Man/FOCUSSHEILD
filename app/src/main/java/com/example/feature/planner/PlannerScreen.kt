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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
import com.example.core.design.FocusSpacing
import com.example.core.design.LocalFocusColors
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation
import com.example.core.ui.FocusStatusMessage
import com.example.data.repository.FocusScheduleRepository
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.core.design.LocalFocusMotion
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusSwitchRow
import com.example.core.ui.localizedSessionTag
import com.example.feature.session.notification.ScheduleTime
import com.example.feature.session.notification.ScheduleValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
    val startTime: String,
    val endTime: String,
    val repeatDays: String,
    val subject: String
)

@OptIn(ExperimentalLayoutApi::class)
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

    // Schedule errors stay until dismissed. The editor awaits its own write before closing.
    val scheduleSaveError by viewModel.scheduleSaveError.collectAsStateWithLifecycle()
    val plannerContext = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var presetError by rememberSaveable { mutableStateOf<String?>(null) }
    var addingPresetId by remember { mutableStateOf<String?>(null) }

    val palette = LocalFocusColors.current
    val motion = LocalFocusMotion.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val isDark = palette.isDark

    // Adaptive color tokens unified with FocusShield Daylight & Late-Night Study themes
    val bgColor = palette.background
    val cardBg = palette.surface
    val cardBorder = palette.cardBorderSubtle
    val textPrimary = palette.textPrimary
    val textSecondary = palette.textSecondary
    val pillBg = palette.surfaceVariant
    val pillBorder = palette.cardBorder
    val selectedDayBg = palette.primaryContainer
    val selectedDayBorder = palette.primary
    val selectedDayText = palette.primary
    val floatingBtnBg = palette.primary
    val floatingBtnText = MaterialTheme.colorScheme.onPrimary

    var selectedDateMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    val selectedDate = remember(selectedDateMillis) { Date(selectedDateMillis) }
    var isSuggestedExpanded by rememberSaveable { mutableStateOf(true) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showAddEditScheduleDialog by rememberSaveable { mutableStateOf(false) }
    var editingSchedule by rememberSaveable(stateSaver = FocusScheduleSnapshotSaver) { mutableStateOf<FocusScheduleEntity?>(null) }
    var scheduleToDelete by rememberSaveable(stateSaver = FocusScheduleSnapshotSaver) { mutableStateOf<FocusScheduleEntity?>(null) }
    var newScheduleId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }

    val plannerPrefs = remember(plannerContext) {
        plannerContext.getSharedPreferences("planner_dismissed_presets", android.content.Context.MODE_PRIVATE)
    }
    val dismissedSuggestions = remember {
        mutableStateListOf<String>().apply {
            addAll(plannerPrefs.getStringSet("dismissed_ids", emptySet()) ?: emptySet())
        }
    }

    // Presets: Morning Study, Afternoon Study, Night Study
    val suggestedPresets = remember {
        listOf(
            SuggestedPreset(
                id = "morning_study",
                emoji = "🌅",
                title = "Morning Study",
                startTime = "08:00",
                endTime = "10:00",
                repeatDays = "MON,TUE,WED,THU,FRI",
                subject = "Morning Focus"
            ),
            SuggestedPreset(
                id = "afternoon_study",
                emoji = "☀️",
                title = "Afternoon Study",
                startTime = "14:00",
                endTime = "16:00",
                repeatDays = "MON,TUE,WED,THU,FRI",
                subject = "General Study"
            ),
            SuggestedPreset(
                id = "night_study",
                emoji = "🌙",
                title = "Night Study",
                startTime = "21:00",
                endTime = "23:00",
                repeatDays = "MON,TUE,WED,THU,FRI,SAT,SUN",
                subject = "Deep Focus & Homework"
            )
        )
    }

    val weekDays = remember(selectedDate, locale) {
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

        val dayFormat = SimpleDateFormat("EEE", locale)
        val numFormat = SimpleDateFormat("d", locale)

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

    val monthYearFormatted = remember(selectedDate, locale) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale).format(selectedDate)
    }

    val dockState = com.example.core.design.rememberFocusDockState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var addScheduleHeightPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Filter schedules relevant to selected day
    // The day abbreviation is a stable data key stored in English (MON, TUE, ...),
    // so it must not follow the display locale.
    val selectedDayOfWeekAbbr = remember(selectedDate) {
        SimpleDateFormat("EEE", Locale.ENGLISH).format(selectedDate).uppercase(Locale.ENGLISH)
    }
    val selectedDateKey = remember(selectedDate) {
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(selectedDate)
    }

    val activeDaySchedules = remember(focusSchedules, selectedDayOfWeekAbbr, selectedDateKey) {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        focusSchedules.filter { entity ->
            if (!entity.repeatEnabled && entity.scheduledDateMillis > 0L) {
                dateFmt.format(Date(entity.scheduledDateMillis)) == selectedDateKey
            } else {
                val days = ScheduleTime.parseDaysOfWeekOrNull(entity.daysOfWeek)
                // Invalid rows remain reachable for editing, never described as a guessed daily routine.
                days == null || Calendar.getInstance().apply { time = selectedDate }.get(Calendar.DAY_OF_WEEK) in days
            }
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
                bottom = dockState.contentPadding + with(density) { addScheduleHeightPx.toDp() } + FocusSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                FocusStatusMessage(
                    presetError ?: scheduleSaveError,
                    isError = true,
                    onDismiss = { presetError = null; viewModel.consumeScheduleSaveError() },
                    modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)
                )
            }
            // 1. Month & Year Header + Help Button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(monthYearFormatted, color = textPrimary, style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = { showHelpDialog = true }, modifier = Modifier.testTag("help_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = stringResource(R.string.planner_help),
                            tint = textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. Weekly Calendar Strip (Monday - Sunday)
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.screenHorizontal).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
                ) {
                    val selCal = Calendar.getInstance().apply { time = selectedDate }

                    weekDays.forEach { day ->
                        val dayCal = Calendar.getInstance().apply { time = day.fullDate }
                        val isSelected = selCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                                selCal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)

                        val fullDate = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, locale).format(day.fullDate)
                        val todayState = stringResource(R.string.planner_today_state)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDateMillis = day.fullDate.time },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = fullDate
                                if (day.isToday) stateDescription = todayState
                            },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                                    Text(day.dayOfWeek, style = MaterialTheme.typography.labelMedium)
                                    Text(day.dayOfMonth, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                                }
                            }
                        )
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
                                text = stringResource(R.string.planner_focus_metric),
                                color = textSecondary,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.todayStudyTime.ifEmpty { "0m" },
                                color = textPrimary,
                                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum")
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
                                text = stringResource(R.string.planner_usage_metric),
                                color = textSecondary,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (uiState.hasUsagePermission) uiState.totalScreenTime else stringResource(R.string.planner_usage_unavailable),
                                color = textPrimary,
                                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum")
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
                             .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.xl),
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
                            text = stringResource(R.string.planner_no_schedules),
                            color = textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.planner_no_schedules_body),
                            color = textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                item {
                    Text(
                        text = pluralStringResource(R.plurals.planner_schedule_count, activeDaySchedules.size, activeDaySchedules.size),
                        color = textSecondary,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.xs).semantics { heading() }
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
                        onStartSession = startSchedule@{
                            if (ScheduleValidation.validate(schedule) != null) {
                                presetError = plannerContext.getString(R.string.planner_invalid_schedule)
                                return@startSchedule
                            }
                            val nowMillis = System.currentTimeMillis()
                            val activeBounds = com.example.feature.session.notification.ScheduleValidation
                                .activeWindowBoundsMillis(schedule, nowMillis)
                            val dateBounds = com.example.feature.session.notification.ScheduleValidation
                                .resolveWindowBoundsForDate(schedule, selectedDate.time)
                            val effectiveBounds = activeBounds ?: dateBounds?.takeIf { nowMillis < it.endMillis }
                            val scheduledEndMillis = effectiveBounds?.endMillis ?: 0L
                            val durationMins = if (effectiveBounds != null && nowMillis < effectiveBounds.endMillis) {
                                val effectiveStart = maxOf(nowMillis, effectiveBounds.startMillis)
                                (((effectiveBounds.endMillis - effectiveStart) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                            } else {
                                com.example.feature.session.notification.ScheduleTime
                                     .durationMinutesOrNull(schedule.startTime, schedule.endTime) ?: return@startSchedule
                            }
                            val blockedPkgs = schedule.blockedAppPackages
                                .split(',')
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .toSet()
                            onStartPlanSession(
                                StudyPlanItem(
                                    id = schedule.id,
                                    subject = schedule.subjectName.ifEmpty { schedule.title },
                                    topic = schedule.title,
                                    targetTime = schedule.startTime,
                                     accentColor = palette.primary,
                                    isCompleted = false,
                                    startTime = schedule.startTime,
                                    endTime = schedule.endTime,
                                    durationMinutes = durationMins,
                                    notes = schedule.description,
                                    targetDate = selectedDate.time,
                                    scheduledEndTimeMillis = scheduledEndMillis,
                                    blockedAppPackages = blockedPkgs,
                                    breakMinutes = schedule.breakMinutes,
                                    blockNotifications = schedule.blockNotifications
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
                             .clickable(role = Role.Button) { isSuggestedExpanded = !isSuggestedExpanded }
                             .heightIn(min = 48.dp)
                             .semantics { stateDescription = plannerContext.getString(if (isSuggestedExpanded) R.string.schedule_expanded_state else R.string.schedule_collapsed_state) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.planner_suggestions),
                            color = textSecondary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isSuggestedExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AnimatedVisibility(
                        visible = isSuggestedExpanded,
                        enter = if (motion.reduceMotion) fadeIn() else fadeIn() + expandVertically(),
                        exit = if (motion.reduceMotion) fadeOut() else fadeOut() + shrinkVertically()
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
                                    enabled = addingPresetId == null,
                                    adding = addingPresetId == preset.id,
                                    onDismiss = {
                                        dismissedSuggestions.add(preset.id)
                                        plannerPrefs.edit().putStringSet("dismissed_ids", dismissedSuggestions.toSet()).apply()
                                    },
                                    onAdd = {
                                        if (addingPresetId == null) {
                                        addingPresetId = preset.id
                                        val defaultPkgs = setOf(
                                            "com.google.android.youtube",
                                            "com.android.chrome",
                                            "com.opera.browser",
                                            "com.brave.browser",
                                            "com.instagram.android"
                                        ) + blockedApps.map { it.packageName }
                                        scope.launch {
                                            try {
                                        val result = viewModel.addFocusSchedule(
                                            title = preset.title,
                                            daysOfWeek = preset.repeatDays,
                                            startTime = preset.startTime,
                                            endTime = preset.endTime,
                                            isAutoStartSession = true,
                                            mode = "TIMER",
                                            subjectName = preset.subject,
                                            repeatEnabled = true,
                                            scheduledDateMillis = selectedDate.time,
                                            breakMinutes = 5,
                                            description = "Scheduled daily routine",
                                            blockedAppPackages = defaultPkgs,
                                            blockNotifications = true
                                        )
                                        when (result) {
                                            is FocusScheduleRepository.SaveResult.Saved -> {
                                                dismissedSuggestions.add(preset.id)
                                                plannerPrefs.edit().putStringSet("dismissed_ids", dismissedSuggestions.toSet()).apply()
                                            }
                                            is FocusScheduleRepository.SaveResult.Rejected -> presetError = result.reason
                                        }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (exception: Exception) {
                                                android.util.Log.e("Planner", "Unable to add suggested schedule", exception)
                                                presetError = plannerContext.getString(R.string.planner_preset_error)
                                            } finally {
                                                addingPresetId = null
                                            }
                                        }
                                        }
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
                newScheduleId = java.util.UUID.randomUUID().toString()
                showAddEditScheduleDialog = true
            },
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = floatingBtnBg,
                contentColor = floatingBtnText
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = FocusSpacing.screenHorizontal)
                .padding(bottom = dockState.clearance + FocusSpacing.md)
                .onSizeChanged { addScheduleHeightPx = it.height }
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
                text = stringResource(R.string.planner_add_schedule),
                color = floatingBtnText,
                style = MaterialTheme.typography.labelLarge
            )
        }

        // 7. Glass Bottom Navigation
        FocusBottomNavigation(
            selectedTab = BottomTab.PLANNER,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged(dockState::onSizeChanged)
        )

        // 8. Full-Screen Add / Edit Schedule Editor Overlay
        if (showAddEditScheduleDialog) {
            FocusScheduleDialog(
                initialSchedule = editingSchedule,
                initialBlockNotifications = editingSchedule?.blockNotifications ?: false,
                availableBlockedApps = blockedApps,
                selectedDateMillis = selectedDate.time,
                onOpenBlockedApps = onOpenBlockedApps,
                onDefaultsSaveFailed = { presetError = plannerContext.getString(R.string.schedule_defaults_save_error) },
                onFollowUpSaveFailed = { presetError = plannerContext.getString(R.string.schedule_follow_up_save_error) },
                onDismiss = {
                    showAddEditScheduleDialog = false
                    editingSchedule = null
                },
                onSave = { title, days, start, end, autoStart, mode, subject, repeat, dateMillis, breakMins, desc, pkgs, blockNotifs, saveDefaults ->
                    val result = if (editingSchedule != null) {
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
                            ),
                            saveDefaults = saveDefaults
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
                            blockNotifications = blockNotifs,
                            id = newScheduleId,
                            saveDefaults = saveDefaults
                        )
                    }
                    result
                }
            )
        }
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
                    Text(stringResource(R.string.planner_help_title), color = textPrimary, modifier = Modifier.weight(1f))
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                    Text(
                        stringResource(R.string.planner_help_calendar),
                        color = textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.planner_help_add),
                        color = textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.planner_help_suggestions),
                        color = textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            containerColor = cardBg
        )
    }

    // Delete Confirmation Dialog
    scheduleToDelete?.let { schedule ->
        FocusDestructiveDialog(
            title = stringResource(R.string.planner_delete_title, schedule.title),
            message = stringResource(R.string.planner_delete_body),
            confirmLabel = stringResource(R.string.planner_delete_confirm),
            onDismiss = { scheduleToDelete = null },
            onConfirm = { viewModel.deleteFocusSchedule(schedule.id) }
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
    enabled: Boolean = true,
    adding: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalFocusColors.current
    val title = stringResource(when (preset.id) {
        "morning_study" -> R.string.planner_morning_title
        "afternoon_study" -> R.string.planner_afternoon_title
        else -> R.string.planner_night_title
    })
    val addLabel = stringResource(R.string.planner_add_suggestion, title)

    Card(
        modifier = modifier
            .fillMaxWidth()
            // The whole suggestion is the action, not just the small "+ Add" pill: tapping the
            // suggested time range now applies that timing instead of doing nothing.
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = addLabel, onClick = onAdd)
            .testTag("suggested_schedule_${preset.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                Text(preset.emoji, style = MaterialTheme.typography.headlineLarge)
                Text(title, color = textPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.testTag("dismiss_suggestion_${preset.id}")) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.planner_dismiss_suggestion, title), tint = textSecondary)
                }
            }
            Text(stringResource(R.string.planner_time_range, scheduleTimeDisplay(preset.startTime), scheduleTimeDisplay(preset.endTime)),
                color = textSecondary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
            Text(stringResource(if (preset.repeatDays.endsWith("SAT,SUN")) R.string.planner_daily else R.string.planner_weekdays),
                color = textSecondary, style = MaterialTheme.typography.bodySmall)
            // The card owns the single Add action; this fill is its visible action cue, not a duplicate focus stop.
            Row(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(palette.primaryContainer)
                .heightIn(min = 48.dp).padding(FocusSpacing.md).testTag("add_suggested_${preset.id}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = palette.onPrimaryContainer)
                Text(if (adding) stringResource(R.string.planner_adding_suggestion) else addLabel,
                    color = palette.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    val palette = LocalFocusColors.current
    val iconBg = palette.primaryContainer
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
                .padding(FocusSpacing.base),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(iconBg).padding(FocusSpacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scheduleEmoji,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                        Text(
                            text = schedule.title,
                            color = textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        val blockedCount = schedule.blockedAppPackages.split(',').count { it.isNotBlank() }
                        if (schedule.subjectName.isNotBlank()) Text(localizedSessionTag(schedule.subjectName), color = textSecondary, style = MaterialTheme.typography.bodySmall)
                        val windowMins = ScheduleTime.durationMinutesOrNull(schedule.startTime, schedule.endTime)
                        if (windowMins != null) Text(pluralStringResource(R.plurals.schedule_window_minutes, windowMins, windowMins), color = textSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
                        if (blockedCount > 0) Text(pluralStringResource(R.plurals.planner_blocked_apps, blockedCount, blockedCount), color = textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }

            }
            FocusSwitchRow(title = stringResource(R.string.planner_enable_schedule, schedule.title), checked = schedule.isEnabled, onCheckedChange = onToggle)

            val nowMillis = System.currentTimeMillis()
            val activeBounds = remember(schedule, nowMillis / 30_000L) {
                com.example.feature.session.notification.ScheduleValidation
                    .activeWindowBoundsMillis(schedule, nowMillis)
            }

            Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                    Text(
                        text = stringResource(R.string.schedule_window_summary, scheduleTimeDisplay(schedule.startTime), scheduleTimeDisplay(schedule.endTime)),
                        color = textPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                    )
                    if (activeBounds != null && schedule.isEnabled) {
                        val remMins = ((activeBounds.remainingMillis(nowMillis) + 59_999L) / 60_000L).coerceAtLeast(1L)
                        Text(
                            text = stringResource(R.string.planner_active_remaining, remMins, scheduleTimeDisplay(schedule.endTime)),
                            color = palette.emeraldText,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                        )
                    } else {
                        val repeatLabel = if (schedule.repeatEnabled) {
                            scheduleDaysDisplay(schedule.daysOfWeek)
                        } else {
                            stringResource(R.string.planner_one_time)
                        }
                        Text(
                            text = repeatLabel,
                            color = textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
            }
            if (schedule.breakMinutes > 0) Text(pluralStringResource(R.plurals.schedule_break_minutes, schedule.breakMinutes, schedule.breakMinutes),
                color = textSecondary, style = MaterialTheme.typography.bodySmall)
            val invalidSchedule = ScheduleValidation.validate(schedule) != null
            if (invalidSchedule) Text(stringResource(R.string.planner_invalid_schedule), color = palette.blockedText, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                    IconButton(
                        onClick = onStartSession,
                        enabled = !invalidSchedule
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.planner_start_schedule, schedule.title),
                            tint = palette.emeraldText,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.planner_edit_schedule, schedule.title),
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.planner_delete_schedule, schedule.title),
                            tint = palette.blockedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
            }
        }
    }
}
