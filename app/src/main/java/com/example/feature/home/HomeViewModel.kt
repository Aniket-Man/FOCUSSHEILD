package com.example.feature.home

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.core.design.FocusColors
import com.example.core.util.TimeFormatter
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.model.FocusMetric
import com.example.data.model.QuickActionItem
import com.example.data.model.QuickActionType
import com.example.data.model.SessionMode
import com.example.data.model.StudyPlanItem
import com.example.data.repository.AnalyticsRepository
import com.example.data.repository.StudyPlanRepository
import com.example.feature.analytics.domain.AnalyticsPeriod
import com.example.feature.analytics.domain.PeriodAnalyticsSummary
import com.example.feature.analytics.domain.TodayStudySummary
import com.example.feature.rewards.domain.RewardBadge
import com.example.feature.session.domain.FocusSession
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val DEFAULT_QUICK_ACTIONS = listOf(
    QuickActionItem("1", "Start", "Focus timer", QuickActionType.START_POMODORO),
    QuickActionItem("2", "Study", "Educational videos", QuickActionType.STUDY_CHANNELS),
    QuickActionItem("3", "Blocked Apps", "Manage blocking", QuickActionType.BLOCKED_APPS),
    QuickActionItem("4", "App Limits", "Daily budgets", QuickActionType.APP_LIMITS),
    QuickActionItem("5", "Strict Mode", "Anti-cheating", QuickActionType.STRICT_MODE),
    QuickActionItem("6", "Sessions", "View analytics", QuickActionType.SESSION_HISTORY)
)

private val DEFAULT_WEEKLY_BARS = listOf(0.04f, 0.04f, 0.04f, 0.04f, 0.04f, 0.04f, 0.04f)
private val DEFAULT_WEEKLY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * State of the live session banner only.
 *
 * Kept apart from [HomeUiState] on purpose: the session ticker updates twice a second, and when the
 * countdown lived in the dashboard state every tick invalidated the whole Home screen — header,
 * quick actions, stats and plan list included. Only the banner composable observes this flow now,
 * so a running second repaints the clock and nothing else.
 */
@Immutable
data class HomeSessionUiState(
    val isSessionActive: Boolean = false,
    val isPaused: Boolean = false,
    val timeRemainingString: String = "00:00:00",
    val subject: String = "",
    val topic: String = "",
    val pomodoroCycleText: String? = null
)

@Immutable
data class HomeUiState(
    val todayStudyTime: String = "0m",
    val todayProgressPercentage: Int = 0,
    val streakDays: Int = 0,
    val weeklyBars: List<Float> = DEFAULT_WEEKLY_BARS,
    val weeklyBarLabels: List<String> = DEFAULT_WEEKLY_LABELS,
    val quickActions: List<QuickActionItem> = DEFAULT_QUICK_ACTIONS,
    val studyPlan: List<StudyPlanItem> = emptyList(),
    val statistics: List<FocusMetric> = emptyList(),
    val totalPlannedTime: String = "0m",
    val totalCompletedTime: String = "0m",
    val remainingPlanTime: String = "0m",
    val planCompletionPercentage: Int = 0,
    val hasOverlapWarnings: Boolean = false,
    val overlapCount: Int = 0,
    val totalScreenTime: String = "0m",
    val hasUsagePermission: Boolean = false,
    val allTimeStudyTime: String = "0m",
    val allTimeStudyTimeMillis: Long = 0L,
    val allTimeSessionCount: Int = 0,
    val todayCompletedSessionCount: Int = 0,
    val focusToScreenRatioPercentage: Int = 0,
    val pendingRewardBadge: RewardBadge? = null,
    val userPhotoUri: String? = null,
    val userAvatarPreset: String = "SHIELD",
    val userName: String = "Focus Scholar"
)

@Immutable
private data class HomeProcessedData(
    val mappedPlans: List<StudyPlanItem>,
    val totalPlannedTime: String,
    val totalCompletedTime: String,
    val remainingPlanTime: String,
    val planCompletionPercentage: Int,
    val hasOverlapWarnings: Boolean,
    val overlapCount: Int,
    val todayStudyTime: String,
    val todayProgressPercentage: Int,
    val streakDays: Int,
    val weeklyBars: List<Float>,
    val weeklyBarLabels: List<String>,
    val statistics: List<FocusMetric>,
    val totalScreenTime: String,
    val hasUsagePermission: Boolean,
    val allTimeStudyTime: String,
    val allTimeStudyTimeMillis: Long,
    val allTimeSessionCount: Int,
    val todayCompletedSessionCount: Int,
    val focusToScreenRatioPercentage: Int,
    val pendingRewardBadge: RewardBadge?,
    val userPhotoUri: String?,
    val userAvatarPreset: String,
    val userName: String
)

private data class HomeDataBundle(
    val todaySummary: TodayStudySummary,
    val weeklySummary: PeriodAnalyticsSummary,
    val todayPlans: List<StudyPlanEntity>,
    val focusSchedules: List<FocusScheduleEntity>,
    val claimedRewardIds: Set<String>,
    val userPhotoUri: String?,
    val userAvatarPreset: String,
    val userName: String
)

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app = application as FocusShieldApp
    private val sessionManager: FocusSessionManager = FocusSessionManager.instance
    private val studyPlanRepository: StudyPlanRepository = app.studyPlanRepository
    private val analyticsRepository: AnalyticsRepository = app.analyticsRepository

    val preferencesFlow = app.preferencesRepository.preferencesFlow

    private val processedDataFlow: Flow<HomeProcessedData> = combine(
        analyticsRepository.todaySummaryFlow,
        analyticsRepository.getPeriodAnalyticsFlow(AnalyticsPeriod.LAST_7_DAYS),
        studyPlanRepository.getTodayPlansFlow(),
        app.focusScheduleRepository.allSchedules,
        app.preferencesRepository.preferencesFlow
    ) { today, weekly, plans, schedules, prefs ->
        HomeDataBundle(
            todaySummary = today,
            weeklySummary = weekly,
            todayPlans = plans,
            focusSchedules = schedules,
            claimedRewardIds = prefs.claimedRewardIds,
            userPhotoUri = prefs.userPhotoUri,
            userAvatarPreset = prefs.userAvatarPreset,
            userName = prefs.userName
        )
    }.distinctUntilChanged().map { bundle ->
        val allTimeMillis = bundle.todaySummary.allTimeStudyTimeMillis
        // Check if any study milestone reward (30m, 2h, 5h, 10h, 20h, 50h, 100h, etc.) is reached and unclaimed
        val pendingBadge = RewardBadge.ALL_BADGES.firstOrNull { badge ->
            badge.isUnlocked(allTimeMillis) && !bundle.claimedRewardIds.contains(badge.id)
        }

        // Sync Today's Plan directly from the Planner's active schedules for today (merged with any existing today plan rows)
        val nowMillis = System.currentTimeMillis()
        val todayDayAbbr = java.text.SimpleDateFormat("EEE", java.util.Locale.ENGLISH)
            .format(java.util.Date(nowMillis)).uppercase(java.util.Locale.ENGLISH)
        val todayDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(nowMillis))
        val existingById = bundle.todayPlans.associateBy { it.id }

        val schedulePlansForToday = bundle.focusSchedules.filter { entity ->
            if (!entity.isEnabled) return@filter false
            if (!entity.repeatEnabled && entity.scheduledDateMillis > 0L) {
                val schedDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date(entity.scheduledDateMillis))
                schedDateStr == todayDateStr
            } else {
                val days = entity.daysOfWeek.uppercase(java.util.Locale.ENGLISH)
                days.contains(todayDayAbbr) || days.contains("DAILY") || days.isEmpty() ||
                    (days.contains("MON - FRI") && (todayDayAbbr in listOf("MON", "TUE", "WED", "THU", "FRI"))) ||
                    (days.contains("WEEKENDS") && (todayDayAbbr in listOf("SAT", "SUN")))
            }
        }.map { schedule ->
            val existing = existingById[schedule.id]
            val startMins = StudyPlanRepository.parseTimeToMinutes(schedule.startTime)
            val endMins = StudyPlanRepository.parseTimeToMinutes(schedule.endTime)
            val durationMins = StudyPlanRepository.calculateDurationMinutes(startMins, endMins)
            val isComp = existing?.isCompleted == true
            StudyPlanEntity(
                id = schedule.id,
                subjectId = schedule.subjectName.lowercase().replace(" ", "_"),
                subjectName = schedule.subjectName.ifBlank { schedule.title },
                topicName = schedule.title,
                plannedDurationMinutes = durationMins,
                targetDate = nowMillis,
                dateString = todayDateStr,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                startMinutes = startMins,
                endMinutes = endMins,
                isCompleted = isComp,
                status = if (isComp) "COMPLETED" else "PLANNED",
                colorHex = schedule.colorHex,
                notes = schedule.description,
                createdAt = schedule.createdAt
            )
        }

        val scheduleIds = schedulePlansForToday.map { it.id }.toSet()
        val unifiedTodayPlans = (schedulePlansForToday + bundle.todayPlans.filter { it.id !in scheduleIds })
            .sortedBy { it.startMinutes }

        // Overlap detection
        val overlaps = StudyPlanRepository.detectOverlaps(unifiedTodayPlans)
        val overlappingIds = overlaps.flatMap { listOf(it.first.id, it.second.id) }.toSet()

        var totalPlannedMins = 0
        var totalCompletedMins = 0

        val mappedPlans = unifiedTodayPlans.map { plan ->
            totalPlannedMins += plan.plannedDurationMinutes
            if (plan.isCompleted) {
                totalCompletedMins += plan.plannedDurationMinutes
            }

            val color = try {
                Color(android.graphics.Color.parseColor(plan.colorHex))
            } catch (e: Exception) {
                when (plan.subjectName.lowercase()) {
                    "physics" -> Color(0xFF8B5CF6)
                    "chemistry" -> Color(0xFFF97316)
                    "mathematics" -> Color(0xFF10B981)
                    else -> Color(0xFF8B5CF6)
                }
            }

            StudyPlanItem(
                id = plan.id,
                subject = plan.subjectName,
                topic = plan.topicName,
                targetTime = StudyPlanRepository.formatDurationHoursMins(plan.plannedDurationMinutes),
                accentColor = color,
                isCompleted = plan.isCompleted,
                startTime = plan.startTime,
                endTime = plan.endTime,
                durationMinutes = plan.plannedDurationMinutes,
                timeRangeFormatted = "${plan.startTime} – ${plan.endTime}",
                status = plan.status,
                notes = plan.notes,
                hasOverlap = overlappingIds.contains(plan.id),
                targetDate = plan.targetDate
            )
        }

        val remainingMins = (totalPlannedMins - totalCompletedMins).coerceAtLeast(0)
        val planCompletionPct = if (totalPlannedMins > 0) {
            ((totalCompletedMins.toFloat() / totalPlannedMins) * 100).toInt().coerceIn(0, 100)
        } else 0

        // Calculate dynamic weekly bars from actual 7-day study time
        val maxDayStudy = bundle.weeklySummary.dailyChart.maxOfOrNull { it.studyTimeMillis } ?: 1L
        val maxThreshold = maxOf(maxDayStudy, 3600 * 1000L).toFloat() // at least 1 hr baseline

        val bars = bundle.weeklySummary.dailyChart.map { day ->
            (day.studyTimeMillis.toFloat() / maxThreshold).coerceIn(0.04f, 1f)
        }.ifEmpty { DEFAULT_WEEKLY_BARS }

        val barLabels = bundle.weeklySummary.dailyChart.map { day ->
            day.dayLabel.take(1)
        }.ifEmpty { DEFAULT_WEEKLY_LABELS }

        val stats = listOf(
            FocusMetric("Sessions", bundle.todaySummary.completedSessionCount.toString(), "", "Completed today", FocusColors.DarkPalette.primary),
            FocusMetric("Shielded", bundle.todaySummary.blockedAttemptsCount.toString(), "", "Distractions blocked", FocusColors.DarkPalette.coralWarning),
            FocusMetric("Focus Time", bundle.todaySummary.formattedStudyTime, "", "Today's pure study", FocusColors.DarkPalette.emeraldSuccess)
        )

        HomeProcessedData(
            mappedPlans = mappedPlans,
            totalPlannedTime = StudyPlanRepository.formatDurationHoursMins(totalPlannedMins),
            totalCompletedTime = StudyPlanRepository.formatDurationHoursMins(totalCompletedMins),
            remainingPlanTime = StudyPlanRepository.formatDurationHoursMins(remainingMins),
            planCompletionPercentage = planCompletionPct,
            hasOverlapWarnings = overlaps.isNotEmpty(),
            overlapCount = overlaps.size,
            todayStudyTime = bundle.todaySummary.formattedStudyTime,
            todayProgressPercentage = bundle.todaySummary.goalProgressPercentage.coerceIn(0, 100),
            streakDays = bundle.todaySummary.currentStreakDays,
            weeklyBars = bars,
            weeklyBarLabels = barLabels,
            statistics = stats,
            totalScreenTime = bundle.todaySummary.formattedTotalScreenTime,
            hasUsagePermission = bundle.todaySummary.hasUsageAccessPermission,
            allTimeStudyTime = bundle.todaySummary.formattedAllTimeStudyTime,
            allTimeStudyTimeMillis = allTimeMillis,
            allTimeSessionCount = bundle.todaySummary.allTimeSessionCount,
            todayCompletedSessionCount = bundle.todaySummary.completedSessionCount,
            focusToScreenRatioPercentage = bundle.todaySummary.focusToScreenRatioPercentage,
            pendingRewardBadge = pendingBadge,
            userPhotoUri = bundle.userPhotoUri,
            userAvatarPreset = bundle.userAvatarPreset,
            userName = bundle.userName
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    /**
     * Live banner state. Collected only by the session card, so the 500 ms ticker no longer
     * recomposes the rest of the dashboard.
     */
    val sessionUiState: StateFlow<HomeSessionUiState> = combine(
        sessionManager.activeSession,
        sessionManager.sessionState
    ) { activeSession: FocusSession?, sessionState: SessionState ->
        val isActive = activeSession != null &&
            (sessionState == SessionState.RUNNING || sessionState == SessionState.PAUSED)

        val displayTime = if (activeSession != null) {
            TimeFormatter.formatDisplayTime(
                remainingMillis = activeSession.remainingDurationMillis,
                elapsedMillis = activeSession.elapsedDurationMillis,
                mode = activeSession.mode
            )
        } else "00:00:00"

        val pomodoroText = if (activeSession != null && activeSession.mode == SessionMode.POMODORO) {
            "${activeSession.pomodoroPhase?.label ?: "Focus"} • Cycle ${activeSession.currentCycle} of ${activeSession.totalCycles}"
        } else null

        HomeSessionUiState(
            isSessionActive = isActive,
            isPaused = sessionState == SessionState.PAUSED,
            timeRemainingString = displayTime,
            subject = activeSession?.subject ?: "",
            topic = activeSession?.topic ?: "",
            pomodoroCycleText = pomodoroText
        )
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeSessionUiState()
    )

    val uiState: StateFlow<HomeUiState> = processedDataFlow
        .map { processed ->
            HomeUiState(
                todayStudyTime = processed.todayStudyTime,
                todayProgressPercentage = processed.todayProgressPercentage,
                streakDays = processed.streakDays,
                weeklyBars = processed.weeklyBars,
                weeklyBarLabels = processed.weeklyBarLabels,
                quickActions = DEFAULT_QUICK_ACTIONS,
                studyPlan = processed.mappedPlans,
                statistics = processed.statistics,
                totalPlannedTime = processed.totalPlannedTime,
                totalCompletedTime = processed.totalCompletedTime,
                remainingPlanTime = processed.remainingPlanTime,
                planCompletionPercentage = processed.planCompletionPercentage,
                hasOverlapWarnings = processed.hasOverlapWarnings,
                overlapCount = processed.overlapCount,
                totalScreenTime = processed.totalScreenTime,
                hasUsagePermission = processed.hasUsagePermission,
                allTimeStudyTime = processed.allTimeStudyTime,
                allTimeStudyTimeMillis = processed.allTimeStudyTimeMillis,
                allTimeSessionCount = processed.allTimeSessionCount,
                todayCompletedSessionCount = processed.todayCompletedSessionCount,
                focusToScreenRatioPercentage = processed.focusToScreenRatioPercentage,
                pendingRewardBadge = processed.pendingRewardBadge,
                userPhotoUri = processed.userPhotoUri,
                userAvatarPreset = processed.userAvatarPreset,
                userName = processed.userName
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )

    val blockedApps: StateFlow<List<BlockedAppEntity>> = app.blockedAppRepository.allBlockedApps
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val focusSchedules: StateFlow<List<FocusScheduleEntity>> = app.focusScheduleRepository.allSchedules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun claimReward(rewardId: String) {
        viewModelScope.launch {
            app.preferencesRepository.claimReward(rewardId)
        }
    }

    fun toggleSessionPause() {
        sessionManager.togglePause()
    }

    fun togglePlanCompletion(planId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            val existing = studyPlanRepository.getPlanById(planId)
            if (existing != null) {
                studyPlanRepository.togglePlanCompletion(planId, isCompleted)
            } else {
                val schedule = focusSchedules.value.firstOrNull { it.id == planId }
                if (schedule != null) {
                    val subjectId = schedule.subjectName.lowercase().replace(" ", "_")
                    studyPlanRepository.updatePlan(
                        id = schedule.id,
                        subjectId = subjectId,
                        subjectName = schedule.subjectName.ifBlank { schedule.title },
                        topicName = schedule.title,
                        startTime = schedule.startTime,
                        endTime = schedule.endTime,
                        targetDate = System.currentTimeMillis(),
                        colorHex = schedule.colorHex,
                        notes = schedule.description,
                        isCompleted = isCompleted
                    )
                }
            }
        }
    }

    fun addStudyPlan(
        subjectName: String,
        topicName: String,
        startTime: String,
        endTime: String,
        targetDate: Long = System.currentTimeMillis(),
        notes: String = ""
    ) {
        viewModelScope.launch {
            val subjectId = subjectName.lowercase().replace(" ", "_")
            studyPlanRepository.addPlan(
                subjectId = subjectId,
                subjectName = subjectName,
                topicName = topicName,
                startTime = startTime,
                endTime = endTime,
                targetDate = targetDate,
                notes = notes
            )
        }
    }

    fun updateStudyPlan(
        id: String,
        subjectName: String,
        topicName: String,
        startTime: String,
        endTime: String,
        targetDate: Long = System.currentTimeMillis(),
        notes: String = "",
        isCompleted: Boolean = false
    ) {
        viewModelScope.launch {
            val subjectId = subjectName.lowercase().replace(" ", "_")
            studyPlanRepository.updatePlan(
                id = id,
                subjectId = subjectId,
                subjectName = subjectName,
                topicName = topicName,
                startTime = startTime,
                endTime = endTime,
                targetDate = targetDate,
                notes = notes,
                isCompleted = isCompleted
            )
        }
    }

    fun deleteStudyPlan(planId: String) {
        viewModelScope.launch {
            studyPlanRepository.deletePlan(planId)
            app.focusScheduleRepository.deleteSchedule(planId)
        }
    }

    private val _scheduleSaveError = MutableStateFlow<String?>(null)

    /**
     * Why the last schedule write was refused, or null.
     *
     * The repository can reject a save for a reason the editor dialog cannot check on its own —
     * most importantly an overlap with an existing schedule (issue #28). The planner observes this
     * and tells the user, instead of the dialog closing as if the schedule had been created.
     */
    val scheduleSaveError: StateFlow<String?> = _scheduleSaveError.asStateFlow()

    /** Clears [scheduleSaveError] once it has been shown. */
    fun consumeScheduleSaveError() {
        _scheduleSaveError.value = null
    }

    fun addFocusSchedule(
        title: String,
        daysOfWeek: String,
        startTime: String,
        endTime: String,
        isAutoStartSession: Boolean,
        mode: String,
        subjectName: String,
        repeatEnabled: Boolean = true,
        scheduledDateMillis: Long = 0L,
        breakMinutes: Int = 5,
        description: String = "",
        blockedAppPackages: Set<String> = emptySet(),
        blockNotifications: Boolean = false
    ) {
        viewModelScope.launch {
            val schedule = FocusScheduleEntity(
                title = title,
                daysOfWeek = daysOfWeek,
                startTime = startTime,
                endTime = endTime,
                isEnabled = true,
                isAutoStartSession = isAutoStartSession,
                mode = mode,
                subjectName = subjectName,
                repeatEnabled = repeatEnabled,
                scheduledDateMillis = scheduledDateMillis,
                breakMinutes = breakMinutes,
                description = description,
                blockedAppPackages = blockedAppPackages.joinToString(","),
                blockNotifications = blockNotifications
            )
            when (val result = app.focusScheduleRepository.addSchedule(schedule)) {
                // A rejection the dialog cannot pre-check (an overlap with another schedule) has to
                // reach the user: a Save button that quietly does nothing is worse than the bug it
                // is preventing (issue #28).
                is com.example.data.repository.FocusScheduleRepository.SaveResult.Rejected -> {
                    android.util.Log.w("HomeViewModel", "Schedule not saved: ${result.reason}")
                    _scheduleSaveError.value = result.reason
                }
                is com.example.data.repository.FocusScheduleRepository.SaveResult.Saved ->
                    _scheduleSaveError.value = null
            }
        }
    }

    fun updateFocusSchedule(schedule: FocusScheduleEntity) {
        viewModelScope.launch {
            when (val result = app.focusScheduleRepository.updateSchedule(schedule)) {
                is com.example.data.repository.FocusScheduleRepository.SaveResult.Rejected -> {
                    android.util.Log.w("HomeViewModel", "Schedule not updated: ${result.reason}")
                    _scheduleSaveError.value = result.reason
                }
                is com.example.data.repository.FocusScheduleRepository.SaveResult.Saved ->
                    _scheduleSaveError.value = null
            }
        }
    }

    fun toggleFocusSchedule(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            app.focusScheduleRepository.toggleScheduleEnabled(id, isEnabled)
        }
    }

    fun setBlockNotifications(enabled: Boolean) {
        viewModelScope.launch {
            app.preferencesRepository.updateBlockNotifications(enabled)
        }
    }

    fun deleteFocusSchedule(id: String) {
        viewModelScope.launch {
            app.focusScheduleRepository.deleteSchedule(id)
        }
    }

    val themeModeFlow: StateFlow<String> = app.preferencesRepository.preferencesFlow
        .map { it.themeMode }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "SYSTEM"
        )

    fun toggleThemeMode() {
        viewModelScope.launch {
            val current = themeModeFlow.value
            val next = if (current == "DARK") "LIGHT" else "DARK"
            app.preferencesRepository.updateThemeMode(next)
        }
    }
}

