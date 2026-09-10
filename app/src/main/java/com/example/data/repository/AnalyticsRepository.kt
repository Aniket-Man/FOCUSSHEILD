package com.example.data.repository

import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.core.util.DeviceUsageStatsHelper
import com.example.core.util.TimeFormatter
import com.example.data.local.dao.BlockedAttemptDao
import com.example.data.local.dao.BreakRecordDao
import com.example.data.local.dao.SessionDao
import com.example.data.local.dao.StudyActivityDao
import com.example.data.local.entity.BlockedAttemptEntity
import com.example.data.local.entity.BlockedEventType
import com.example.data.local.entity.BreakRecordEntity
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyActivitySource
import com.example.data.local.entity.StudyActivityType
import com.example.data.preferences.FocusPreferences
import com.example.data.preferences.FocusPreferencesRepository
import com.example.feature.analytics.domain.AnalyticsPeriod
import com.example.feature.analytics.domain.BlockedAppStat
import com.example.feature.analytics.domain.DateRange
import com.example.feature.analytics.domain.PeriodAnalyticsSummary
import com.example.feature.analytics.domain.SourceBreakdown
import com.example.feature.analytics.domain.StreakCalculator
import com.example.feature.analytics.domain.StudyDay
import com.example.feature.analytics.domain.SubjectBreakdown
import com.example.feature.analytics.domain.TodayStudySummary
import com.example.feature.analytics.domain.TopicBreakdown
import com.example.feature.analytics.domain.YouTubeStudyStats
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AnalyticsRepository(
    private val sessionDao: SessionDao,
    private val breakRecordDao: BreakRecordDao,
    private val blockedAttemptDao: BlockedAttemptDao,
    private val studyActivityDao: StudyActivityDao,
    private val preferencesRepository: FocusPreferencesRepository,
    private val context: Context? = null
) {

    private val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    val preferencesFlow: Flow<FocusPreferences> = preferencesRepository.preferencesFlow

    /**
     * Real-time reactive stream of Today's Study Metrics.
     */
    val todaySummaryFlow: Flow<TodayStudySummary> = run {
        val (todayStart, todayEnd) = getTodayBounds()
        combine(
            sessionDao.getSessionsBetweenFlow(todayStart, todayEnd),
            breakRecordDao.getBreaksBetweenFlow(todayStart, todayEnd),
            blockedAttemptDao.getAttemptsBetweenFlow(todayStart, todayEnd),
            sessionDao.getAllSessionsFlow(),
            preferencesRepository.preferencesFlow
        ) { todaySessions, todayBreaks, todayAttempts, allSessions, prefs ->
            val totalStudyMillis = todaySessions.sumOf { it.actualDurationMillis }
            val completedSessions = todaySessions.count { it.completed }
            val totalBreakMillis = todayBreaks.sumOf { it.actualDurationMillis }
            val blockedCount = todayAttempts.size
            val uniqueSubjects = todaySessions.map { it.subject }.distinct().size
            val uniqueTopics = todaySessions.map { it.topic }.distinct().size

            val dailyGoalMillis = prefs.dailyGoalMinutes * 60 * 1000L
            val progressPercent = if (dailyGoalMillis > 0) {
                ((totalStudyMillis.toDouble() / dailyGoalMillis.toDouble()) * 100).toInt()
            } else 0

            val streak = StreakCalculator.calculate(
                sessions = allSessions,
                minDailyThresholdMinutes = prefs.minimumStreakThresholdMinutes
            )

            val allTimeStudyMillis = allSessions.sumOf { it.actualDurationMillis }
            val allTimeCount = allSessions.size
            val allTimeCompleted = allSessions.count { it.completed }

            val hasUsagePermission = context?.let { DeviceUsageStatsHelper.hasUsageStatsPermission(it) } ?: false
            val screenTimeMillis = if (hasUsagePermission && context != null) {
                DeviceUsageStatsHelper.getTodayTotalScreenTimeMillis(context)
            } else 0L

            val effectiveScreenOrStudy = maxOf(screenTimeMillis, totalStudyMillis)
            val focusRatio = if (effectiveScreenOrStudy > 0) {
                ((totalStudyMillis.toDouble() / effectiveScreenOrStudy.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else if (totalStudyMillis > 0) 100 else 0

            TodayStudySummary(
                totalStudyTimeMillis = totalStudyMillis,
                formattedStudyTime = formatDurationToHoursMins(totalStudyMillis),
                sessionCount = todaySessions.size,
                completedSessionCount = completedSessions,
                totalBreakDurationMillis = totalBreakMillis,
                formattedBreakTime = formatDurationToHoursMins(totalBreakMillis),
                breakCount = todayBreaks.size,
                blockedAttemptsCount = blockedCount,
                subjectCount = uniqueSubjects,
                topicCount = uniqueTopics,
                dailyGoalMillis = dailyGoalMillis,
                goalProgressPercentage = progressPercent,
                isGoalAchieved = totalStudyMillis >= dailyGoalMillis,
                currentStreakDays = streak.currentStreak,
                longestStreakDays = streak.longestStreak,
                allTimeStudyTimeMillis = allTimeStudyMillis,
                formattedAllTimeStudyTime = formatDurationToHoursMins(allTimeStudyMillis),
                allTimeSessionCount = allTimeCount,
                allTimeCompletedCount = allTimeCompleted,
                totalScreenTimeMillis = screenTimeMillis,
                formattedTotalScreenTime = formatDurationToHoursMins(screenTimeMillis),
                hasUsageAccessPermission = hasUsagePermission,
                focusToScreenRatioPercentage = focusRatio
            )
        }
    }

    /**
     * Real-time period analytics summary stream based on selected period tab.
     */
    fun getPeriodAnalyticsFlow(
        period: AnalyticsPeriod,
        customStart: Long? = null,
        customEnd: Long? = null
    ): Flow<PeriodAnalyticsSummary> {
        val dateRange = getDateRangeForPeriod(period, customStart, customEnd)

        val firstGroupFlow = combine(
            sessionDao.getSessionsBetweenFlow(dateRange.startMillis, dateRange.endMillis),
            breakRecordDao.getBreaksBetweenFlow(dateRange.startMillis, dateRange.endMillis),
            blockedAttemptDao.getAttemptsBetweenFlow(dateRange.startMillis, dateRange.endMillis)
        ) { sessions, breaks, attempts ->
            Triple(sessions, breaks, attempts)
        }

        val secondGroupFlow = combine(
            studyActivityDao.getActivitiesBetweenFlow(dateRange.startMillis, dateRange.endMillis),
            sessionDao.getAllSessionsFlow(),
            preferencesRepository.preferencesFlow
        ) { activities, allSessions, prefs ->
            Triple(activities, allSessions, prefs)
        }

        return combine(firstGroupFlow, secondGroupFlow) { (sessions, breaks, attempts), (activities, allSessions, prefs) ->
            buildPeriodSummary(
                period = period,
                dateRange = dateRange,
                sessions = sessions,
                breaks = breaks,
                attempts = attempts,
                activities = activities,
                allSessions = allSessions,
                prefs = prefs
            )
        }
    }

    private fun buildPeriodSummary(
        period: AnalyticsPeriod,
        dateRange: DateRange,
        sessions: List<SessionRecordEntity>,
        breaks: List<BreakRecordEntity>,
        attempts: List<BlockedAttemptEntity>,
        activities: List<StudyActivityEntity>,
        allSessions: List<SessionRecordEntity>,
        prefs: FocusPreferences
    ): PeriodAnalyticsSummary {
        val totalStudyMillis = sessions.sumOf { it.actualDurationMillis }
        val completedCount = sessions.count { it.completed }

        val totalBreakMillis = breaks.sumOf { it.actualDurationMillis }
        val breakCount = breaks.size
        val avgBreakMillis = if (breakCount > 0) totalBreakMillis / breakCount else 0L

        val blockedCount = attempts.size

        // Build Daily Chart
        val dailyChart = generateDailyChart(dateRange, sessions, prefs.dailyGoalMinutes)

        val studyDaysCount = dailyChart.count { it.studyTimeMillis > 0 }
        val totalDaysInRange = dailyChart.size.coerceAtLeast(1)
        val avgStudyPerStudyDay = if (studyDaysCount > 0) totalStudyMillis / studyDaysCount else 0L

        val bestDay = dailyChart.maxByOrNull { it.studyTimeMillis }
        val consistencyPercent = ((studyDaysCount.toFloat() / totalDaysInRange.toFloat()) * 100).toInt()

        // Subject Breakdown
        val subjectBreakdowns = computeSubjectBreakdowns(sessions, totalStudyMillis)

        // Topic Breakdown
        val topicBreakdowns = computeTopicBreakdowns(sessions)

        // Source Breakdown
        val sourceBreakdowns = computeSourceBreakdowns(sessions, activities, totalStudyMillis)

        // Blocked Apps Breakdown
        val blockedAppStats = computeBlockedAppStats(attempts)

        // YouTube Study Intelligence
        val youtubeStats = computeYouTubeStats(activities, attempts)

        // Streak Info
        val streakInfo = StreakCalculator.calculate(
            sessions = allSessions,
            minDailyThresholdMinutes = prefs.minimumStreakThresholdMinutes
        )

        return PeriodAnalyticsSummary(
            period = period,
            dateRangeLabel = dateRange.label,
            totalStudyTimeMillis = totalStudyMillis,
            formattedTotalStudyTime = formatDurationToHoursMins(totalStudyMillis),
            totalSessions = sessions.size,
            completedSessions = completedCount,
            totalBreakDurationMillis = totalBreakMillis,
            formattedTotalBreakTime = formatDurationToHoursMins(totalBreakMillis),
            breakCount = breakCount,
            averageBreakDurationMillis = avgBreakMillis,
            formattedAverageBreakDuration = formatDurationToHoursMins(avgBreakMillis),
            blockedAttemptsCount = blockedCount,
            averageStudyTimePerStudyDayMillis = avgStudyPerStudyDay,
            formattedAverageStudyPerDay = formatDurationToHoursMins(avgStudyPerStudyDay),
            bestStudyDayLabel = if ((bestDay?.studyTimeMillis ?: 0L) > 0) bestDay?.dayLabel else null,
            bestStudyDayTimeMillis = bestDay?.studyTimeMillis ?: 0L,
            studyDaysCount = studyDaysCount,
            totalDaysInRange = totalDaysInRange,
            consistencyPercentage = consistencyPercent,
            dailyChart = dailyChart,
            subjectBreakdowns = subjectBreakdowns,
            topicBreakdowns = topicBreakdowns,
            sourceBreakdowns = sourceBreakdowns,
            blockedAppStats = blockedAppStats,
            youTubeStats = youtubeStats,
            streakInfo = streakInfo
        )
    }

    private fun generateDailyChart(
        dateRange: DateRange,
        sessions: List<SessionRecordEntity>,
        goalMinutes: Int
    ): List<StudyDay> {
        val calendar = Calendar.getInstance()
        val todayKey = StreakCalculator.formatDateKey(System.currentTimeMillis())
        val goalMillis = goalMinutes * 60 * 1000L

        // Group sessions by day
        val sessionDayMap = mutableMapOf<String, Pair<Long, Int>>() // dateKey -> (totalMillis, count)
        for (session in sessions) {
            val key = StreakCalculator.formatDateKey(session.startTime)
            val current = sessionDayMap.getOrDefault(key, Pair(0L, 0))
            sessionDayMap[key] = Pair(current.first + session.actualDurationMillis, current.second + 1)
        }

        val result = mutableListOf<StudyDay>()

        calendar.timeInMillis = dateRange.startMillis
        val endCal = Calendar.getInstance().apply { timeInMillis = dateRange.endMillis }

        while (calendar.before(endCal) || calendar.get(Calendar.DAY_OF_YEAR) == endCal.get(Calendar.DAY_OF_YEAR)) {
            val currentDayMillis = calendar.timeInMillis
            val dateKey = StreakCalculator.formatDateKey(currentDayMillis)
            val stats = sessionDayMap.getOrDefault(dateKey, Pair(0L, 0))
            val dayStudyMillis = stats.first
            val sessionCount = stats.second
            val isToday = dateKey == todayKey

            val dayLabel = dayOfWeekFormat.format(Date(currentDayMillis))
            val dateFormatted = monthDayFormat.format(Date(currentDayMillis))

            result.add(
                StudyDay(
                    dayLabel = dayLabel,
                    dateFormatted = dateFormatted,
                    dateKey = dateKey,
                    studyTimeMillis = dayStudyMillis,
                    formattedStudyTime = formatDurationToHoursMins(dayStudyMillis),
                    sessionCount = sessionCount,
                    isToday = isToday,
                    isGoalMet = dayStudyMillis >= goalMillis && goalMillis > 0
                )
            )

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return result
    }

    private fun computeSubjectBreakdowns(
        sessions: List<SessionRecordEntity>,
        totalStudyMillis: Long
    ): List<SubjectBreakdown> {
        if (sessions.isEmpty()) return emptyList()

        val grouped = sessions.groupBy { it.subject }
        return grouped.map { (subject, sessionList) ->
            val duration = sessionList.sumOf { it.actualDurationMillis }
            val percentage = if (totalStudyMillis > 0) duration.toFloat() / totalStudyMillis.toFloat() else 0f
            SubjectBreakdown(
                subject = subject,
                durationMillis = duration,
                formattedDuration = formatDurationToHoursMins(duration),
                percentage = percentage,
                sessionCount = sessionList.size
            )
        }.sortedByDescending { it.durationMillis }
    }

    private fun computeTopicBreakdowns(sessions: List<SessionRecordEntity>): List<TopicBreakdown> {
        if (sessions.isEmpty()) return emptyList()

        val subjectTotals = sessions.groupBy { it.subject }
            .mapValues { (_, list) -> list.sumOf { it.actualDurationMillis } }

        val topicGroups = sessions.groupBy { "${it.subject}:::${it.topic}" }
        return topicGroups.map { (key, list) ->
            val parts = key.split(":::")
            val subject = parts.getOrNull(0) ?: "General"
            val topic = parts.getOrNull(1) ?: "Study"
            val duration = list.sumOf { it.actualDurationMillis }
            val subjectTotal = subjectTotals[subject] ?: duration
            val percentage = if (subjectTotal > 0) duration.toFloat() / subjectTotal.toFloat() else 1f

            TopicBreakdown(
                subject = subject,
                topic = topic,
                durationMillis = duration,
                formattedDuration = formatDurationToHoursMins(duration),
                percentage = percentage,
                sessionCount = list.size
            )
        }.sortedByDescending { it.durationMillis }
    }

    private fun computeSourceBreakdowns(
        sessions: List<SessionRecordEntity>,
        activities: List<StudyActivityEntity>,
        totalStudyMillis: Long
    ): List<SourceBreakdown> {
        val sourceTotals = mutableMapOf<StudyActivitySource, Pair<Long, Int>>()

        // Aggregate from sessions
        for (session in sessions) {
            val source = when (session.mode) {
                com.example.data.model.SessionMode.TIMER -> StudyActivitySource.TIMER
                com.example.data.model.SessionMode.STOPWATCH -> StudyActivitySource.STOPWATCH
                com.example.data.model.SessionMode.POMODORO -> StudyActivitySource.POMODORO
            }
            val cur = sourceTotals.getOrDefault(source, Pair(0L, 0))
            sourceTotals[source] = Pair(cur.first + session.actualDurationMillis, cur.second + 1)
        }

        // Check if there are distinct YouTube activities
        val youtubeActivities = activities.filter { it.source == StudyActivitySource.YOUTUBE }
        if (youtubeActivities.isNotEmpty()) {
            val ytDuration = youtubeActivities.sumOf { it.durationMillis }
            sourceTotals[StudyActivitySource.YOUTUBE] = Pair(ytDuration, youtubeActivities.size)
        }

        val totalCombined = sourceTotals.values.sumOf { it.first }.coerceAtLeast(totalStudyMillis)

        return sourceTotals.map { (source, pair) ->
            val duration = pair.first
            val percentage = if (totalCombined > 0) duration.toFloat() / totalCombined.toFloat() else 0f
            SourceBreakdown(
                source = source,
                sourceLabel = source.label,
                durationMillis = duration,
                formattedDuration = formatDurationToHoursMins(duration),
                percentage = percentage,
                activityCount = pair.second
            )
        }.sortedByDescending { it.durationMillis }
    }

    private fun computeBlockedAppStats(attempts: List<BlockedAttemptEntity>): List<BlockedAppStat> {
        if (attempts.isEmpty()) return emptyList()

        val total = attempts.size
        val grouped = attempts.groupBy { it.packageName }
        return grouped.map { (pkg, list) ->
            val appName = list.firstOrNull()?.appName ?: pkg
            val count = list.size
            val percentage = count.toFloat() / total.toFloat()
            BlockedAppStat(
                appName = appName,
                packageName = pkg,
                attemptCount = count,
                percentage = percentage
            )
        }.sortedByDescending { it.attemptCount }
    }

    private fun computeYouTubeStats(
        activities: List<StudyActivityEntity>,
        attempts: List<BlockedAttemptEntity>
    ): YouTubeStudyStats {
        val ytActivities = activities.filter { it.source == StudyActivitySource.YOUTUBE || it.activityType == StudyActivityType.YOUTUBE_STUDY }
        val totalWatchTime = ytActivities.sumOf { it.durationMillis }

        val channelsMap = mutableMapOf<String, Long>()
        for (act in ytActivities) {
            val name = act.channelName ?: "Approved Channel"
            channelsMap[name] = channelsMap.getOrDefault(name, 0L) + act.durationMillis
        }
        val topChannels = channelsMap.toList().sortedByDescending { it.second }

        // Types come from the recorded event, not from the display name. The old heuristic
        // (`appName.contains("Short")`) broke as soon as short-form blocking covered Instagram and
        // Facebook Reels — those were labelled "Instagram Reels"/"Facebook Reels" and silently
        // counted as unapproved YouTube content instead of Shorts.
        val ytBlocked = attempts.filter { it.packageName in YOUTUBE_PACKAGES }
        val shortsBlocked = ytBlocked.count { it.eventType == BlockedEventType.SHORTS_BLOCKED }
        // Everything YouTube refused to play because it was not an approved study video.
        val unapprovedBlocked = ytBlocked.count {
            it.eventType == BlockedEventType.YOUTUBE_UNAPPROVED_CHANNEL ||
                it.eventType == BlockedEventType.YOUTUBE_UNKNOWN_CONTENT ||
                it.eventType == BlockedEventType.YOUTUBE_HOME_FEED
        }

        return YouTubeStudyStats(
            verifiedWatchTimeMillis = totalWatchTime,
            formattedWatchTime = formatDurationToHoursMins(totalWatchTime),
            verifiedActivityCount = ytActivities.size,
            topChannels = topChannels,
            blockedShortsCount = shortsBlocked.coerceAtLeast(0),
            blockedUnapprovedCount = unapprovedBlocked.coerceAtLeast(0)
        )
    }

    suspend fun recordYouTubeStudyActivity(
        sessionId: String,
        subject: String,
        topic: String,
        channelName: String?,
        channelId: String?,
        videoTitle: String?,
        durationMillis: Long,
        startedAt: Long = System.currentTimeMillis() - durationMillis,
        endedAt: Long = System.currentTimeMillis()
    ) {
        if (durationMillis <= 0) return
        val entity = StudyActivityEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            activityType = StudyActivityType.YOUTUBE_STUDY,
            source = StudyActivitySource.YOUTUBE,
            subject = subject,
            topic = topic,
            channelName = channelName,
            channelId = channelId,
            videoTitle = videoTitle,
            startedAt = startedAt,
            endedAt = endedAt,
            durationMillis = durationMillis,
            createdAt = System.currentTimeMillis()
        )
        studyActivityDao.insertActivity(entity)
        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_ACTIVITIES,
            entity.id,
            CloudJson.studyActivityToJson(entity).toString()
        )
    }

    suspend fun updateDailyGoalMinutes(minutes: Int) {
        preferencesRepository.updateDailyGoalMinutes(minutes)
    }

    suspend fun updateMinimumStreakThresholdMinutes(minutes: Int) {
        preferencesRepository.updateMinimumStreakThresholdMinutes(minutes)
    }

    fun getDateRangeForPeriod(
        period: AnalyticsPeriod,
        customStart: Long? = null,
        customEnd: Long? = null
    ): DateRange {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        return when (period) {
            AnalyticsPeriod.TODAY -> {
                val (start, end) = getTodayBounds()
                DateRange(start, end, "Today")
            }
            AnalyticsPeriod.LAST_7_DAYS -> {
                // 7 days ending today
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.add(Calendar.DAY_OF_YEAR, -6) // 7 days total including today
                val start = calendar.timeInMillis

                val endCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis
                DateRange(start, end, "Last 7 Days")
            }
            AnalyticsPeriod.LAST_30_DAYS -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                val start = calendar.timeInMillis

                val endCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis
                DateRange(start, end, "Last 30 Days")
            }
            AnalyticsPeriod.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis

                val endCal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val end = endCal.timeInMillis
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(now))
                DateRange(start, end, monthName)
            }
            AnalyticsPeriod.CUSTOM -> {
                val start = customStart ?: (now - 7 * 24 * 3600 * 1000L)
                val end = customEnd ?: now
                val startStr = monthDayFormat.format(Date(start))
                val endStr = monthDayFormat.format(Date(end))
                DateRange(start, end, "$startStr - $endStr")
            }
        }
    }

    private fun getTodayBounds(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        return Pair(startOfDay, endOfDay)
    }

    private fun formatDurationToHoursMins(millis: Long): String {
        val totalMinutes = millis / (60 * 1000)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            millis > 0 -> "<1m"
            else -> "0m"
        }
    }

    companion object {
        /**
         * Packages whose blocked events belong on the YouTube study card. These stay a package
         * check rather than an event-type check because `SHORTS_BLOCKED` is shared with Instagram
         * and Facebook Reels — the YouTube card must not absorb Reels figures.
         */
        private val YOUTUBE_PACKAGES = setOf(
            "com.google.android.youtube",
            "com.google.android.youtube.tv"
        )
    }
}
