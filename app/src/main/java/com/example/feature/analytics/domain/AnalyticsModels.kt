package com.example.feature.analytics.domain

import com.example.data.local.entity.StudyActivitySource

enum class AnalyticsPeriod(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days"),
    THIS_MONTH("This Month"),
    CUSTOM("Custom")
}

data class DateRange(
    val startMillis: Long,
    val endMillis: Long,
    val label: String
)

data class TodayStudySummary(
    val totalStudyTimeMillis: Long = 0L,
    val formattedStudyTime: String = "0m",
    val sessionCount: Int = 0,
    val completedSessionCount: Int = 0,
    val totalBreakDurationMillis: Long = 0L,
    val formattedBreakTime: String = "0m",
    val breakCount: Int = 0,
    val blockedAttemptsCount: Int = 0,
    val subjectCount: Int = 0,
    val topicCount: Int = 0,
    val dailyGoalMillis: Long = 4 * 3600 * 1000L,
    val goalProgressPercentage: Int = 0,
    val isGoalAchieved: Boolean = false,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val allTimeStudyTimeMillis: Long = 0L,
    val formattedAllTimeStudyTime: String = "0m",
    val allTimeSessionCount: Int = 0,
    val allTimeCompletedCount: Int = 0,
    val totalScreenTimeMillis: Long = 0L,
    val formattedTotalScreenTime: String = "0m",
    val hasUsageAccessPermission: Boolean = false,
    val focusToScreenRatioPercentage: Int = 0
)

data class StudyDay(
    val dayLabel: String,
    val dateFormatted: String,
    val dateKey: String,
    val studyTimeMillis: Long,
    val formattedStudyTime: String,
    val sessionCount: Int,
    val isToday: Boolean,
    val isGoalMet: Boolean
)

data class SubjectBreakdown(
    val subject: String,
    val durationMillis: Long,
    val formattedDuration: String,
    val percentage: Float,
    val sessionCount: Int
)

data class TopicBreakdown(
    val subject: String,
    val topic: String,
    val durationMillis: Long,
    val formattedDuration: String,
    val percentage: Float,
    val sessionCount: Int
)

data class SourceBreakdown(
    val source: StudyActivitySource,
    val sourceLabel: String,
    val durationMillis: Long,
    val formattedDuration: String,
    val percentage: Float,
    val activityCount: Int
)

data class BlockedAppStat(
    val appName: String,
    val packageName: String,
    val attemptCount: Int,
    val percentage: Float
)

data class YouTubeStudyStats(
    val verifiedWatchTimeMillis: Long = 0L,
    val formattedWatchTime: String = "0m",
    val verifiedActivityCount: Int = 0,
    val topChannels: List<Pair<String, Long>> = emptyList(),
    val blockedShortsCount: Int = 0,
    val blockedUnapprovedCount: Int = 0
)

data class PeriodAnalyticsSummary(
    val period: AnalyticsPeriod = AnalyticsPeriod.LAST_7_DAYS,
    val dateRangeLabel: String = "",
    val totalStudyTimeMillis: Long = 0L,
    val formattedTotalStudyTime: String = "0m",
    val totalSessions: Int = 0,
    val completedSessions: Int = 0,
    val totalBreakDurationMillis: Long = 0L,
    val formattedTotalBreakTime: String = "0m",
    val breakCount: Int = 0,
    val averageBreakDurationMillis: Long = 0L,
    val formattedAverageBreakDuration: String = "0m",
    val blockedAttemptsCount: Int = 0,
    val averageStudyTimePerStudyDayMillis: Long = 0L,
    val formattedAverageStudyPerDay: String = "0m",
    val bestStudyDayLabel: String? = null,
    val bestStudyDayTimeMillis: Long = 0L,
    val studyDaysCount: Int = 0,
    val totalDaysInRange: Int = 7,
    val consistencyPercentage: Int = 0,
    val dailyChart: List<StudyDay> = emptyList(),
    val subjectBreakdowns: List<SubjectBreakdown> = emptyList(),
    val topicBreakdowns: List<TopicBreakdown> = emptyList(),
    val sourceBreakdowns: List<SourceBreakdown> = emptyList(),
    val blockedAppStats: List<BlockedAppStat> = emptyList(),
    val youTubeStats: YouTubeStudyStats = YouTubeStudyStats(),
    val streakInfo: StreakResult = StreakResult(0, 0, 0, false, 20)
)
