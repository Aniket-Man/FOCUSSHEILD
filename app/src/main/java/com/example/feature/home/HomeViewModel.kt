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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val DEFAULT_QUICK_ACTIONS = listOf(
    QuickActionItem("1", "Start Pomodoro", "Focus timer", QuickActionType.START_POMODORO),
    QuickActionItem("2", "Study Channels", "Educational videos", QuickActionType.STUDY_CHANNELS),
    QuickActionItem("3", "Blocked Apps", "Manage blocking", QuickActionType.BLOCKED_APPS),
    QuickActionItem("4", "Session History", "View analytics", QuickActionType.SESSION_HISTORY)
)

private val DEFAULT_WEEKLY_BARS = listOf(0.04f, 0.04f, 0.04f, 0.04f, 0.04f, 0.04f, 0.04f)
private val DEFAULT_WEEKLY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@Immutable
data class HomeUiState(
    val isSessionActive: Boolean = false,
    val isPaused: Boolean = false,
    val timeRemainingString: String = "01:00:00",
    val activeSubject: String = "Physics",
    val activeTopic: String = "Electrostatics",
    val activeMode: SessionMode = SessionMode.TIMER,
    val pomodoroCycleText: String? = null,
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
        app.preferencesRepository.preferencesFlow
    ) { today, weekly, plans, prefs ->
        HomeDataBundle(
            todaySummary = today,
            weeklySummary = weekly,
            todayPlans = plans,
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
        // Overlap detection
        val overlaps = StudyPlanRepository.detectOverlaps(bundle.todayPlans)
        val overlappingIds = overlaps.flatMap { listOf(it.first.id, it.second.id) }.toSet()

        var totalPlannedMins = 0
        var totalCompletedMins = 0

        val mappedPlans = bundle.todayPlans.map { plan ->
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
    }.distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> = combine(
        sessionManager.activeSession,
        sessionManager.sessionState,
        processedDataFlow
    ) { activeSession: FocusSession?, sessionState: SessionState, processed: HomeProcessedData ->
        val isActive = activeSession != null && (sessionState == SessionState.RUNNING || sessionState == SessionState.PAUSED)

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

        HomeUiState(
            isSessionActive = isActive,
            isPaused = sessionState == SessionState.PAUSED,
            timeRemainingString = displayTime,
            activeSubject = activeSession?.subject ?: "Physics",
            activeTopic = activeSession?.topic ?: "Electrostatics",
            activeMode = activeSession?.mode ?: SessionMode.TIMER,
            pomodoroCycleText = pomodoroText,
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isSessionActive = false)
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
            studyPlanRepository.togglePlanCompletion(planId, isCompleted)
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
        }
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
            app.focusScheduleRepository.addSchedule(schedule)
        }
    }

    fun updateFocusSchedule(schedule: FocusScheduleEntity) {
        viewModelScope.launch {
            app.focusScheduleRepository.updateSchedule(schedule)
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
}

