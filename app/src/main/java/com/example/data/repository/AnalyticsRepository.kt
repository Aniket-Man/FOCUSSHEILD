package com.example.data.repository

import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.core.util.DayBounds
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
import com.example.feature.rewards.domain.RewardBadge
import com.example.feature.rewards.domain.StudyStamp
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.UUID

private data class TodayInputsBundle(
    val todaySessions: List<SessionRecordEntity>,
    val todayBreaks: List<BreakRecordEntity>,
    val todayAttempts: List<BlockedAttemptEntity>,
    val allSessions: List<SessionRecordEntity>,
    val prefs: FocusPreferences
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsRepository(
    private val sessionDao: SessionDao,
    private val breakRecordDao: BreakRecordDao,
    private val blockedAttemptDao: BlockedAttemptDao,
    private val studyActivityDao: StudyActivityDao,
    private val preferencesRepository: FocusPreferencesRepository,
    private val context: Context? = null
) {

    // Immutable formatters: java.time's DateTimeFormatter is thread-safe, unlike the shared
    // SimpleDateFormat instances these replace (concurrent formatting from parallel flows could
    // corrupt the output or throw).
    private val dayOfWeekFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val monthDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    @Volatile
    private var cachedScreenTimeMillis: Long = 0L
    @Volatile
    private var lastScreenTimeQueryTime: Long = 0L
    @Volatile
    private var cachedHasUsagePermission: Boolean? = null
    @Volatile
    private var lastPermissionCheckTime: Long = 0L
    @Volatile
    private var isRefreshingScreenTime: Boolean = false

    private val backgroundScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val screenTimeRefreshVersion = kotlinx.coroutines.flow.MutableStateFlow(0L)

    init {
        if (context != null) {
            refreshScreenTimeAsync()
        }
    }

    fun refreshScreenTimeAsync() {
        val ctx = context ?: return
        if (isRefreshingScreenTime) return
        isRefreshingScreenTime = true
        backgroundScope.launch {
            try {
                val now = System.currentTimeMillis()
                val perm = DeviceUsageStatsHelper.hasUsageStatsPermission(ctx)
                cachedHasUsagePermission = perm
                lastPermissionCheckTime = now
                val queried = if (perm) {
                    DeviceUsageStatsHelper.getTodayTotalScreenTimeMillis(ctx)
                } else 0L
                val changed = queried != cachedScreenTimeMillis
                cachedScreenTimeMillis = queried
                lastScreenTimeQueryTime = now
                if (changed) {
                    screenTimeRefreshVersion.value = now
                }
            } catch (_: Exception) {
            } finally {
                isRefreshingScreenTime = false
            }
        }
    }

    val preferencesFlow: Flow<FocusPreferences> = preferencesRepository.preferencesFlow

    /**
     * Real-time reactive stream of Today's Study Metrics.
     *
     * The day bounds come from [DayBounds.todayFlow], which re-emits at every local midnight and
     * rebuilds the inner query flows via flatMapLatest. Capturing the bounds once when this flow
     * was constructed (as before) froze "today" at app start — after 00:00 the summary kept
     * showing yesterday's numbers until the process restarted.
     */
    val todaySummaryFlow: Flow<TodayStudySummary> = DayBounds.todayFlow().flatMapLatest { (todayStart, todayEnd) ->
        val baseInputsFlow = combine(
            sessionDao.getSessionsBetweenFlow(todayStart, todayEnd),
            breakRecordDao.getBreaksBetweenFlow(todayStart, todayEnd),
            blockedAttemptDao.getAttemptsBetweenFlow(todayStart, todayEnd),
            sessionDao.getAllSessionsFlow(),
            preferencesRepository.preferencesFlow
        ) { todaySessions, todayBreaks, todayAttempts, allSessions, prefs ->
            TodayInputsBundle(todaySessions, todayBreaks, todayAttempts, allSessions, prefs)
        }
        combine(baseInputsFlow, screenTimeRefreshVersion) { bundle, _ ->
            val todaySessions = bundle.todaySessions
            val todayBreaks = bundle.todayBreaks
            val todayAttempts = bundle.todayAttempts
            val allSessions = bundle.allSessions
            val prefs = bundle.prefs
            val totalStudyMillis = todaySessions.sumOf { session ->
                DayBounds.clampedDurationInWindow(
                    startTime = session.startTime,
                    endTime = session.endTime,
                    actualDurationMillis = session.actualDurationMillis,
                    windowStart = todayStart,
                    windowEnd = todayEnd
                )
            }
            val completedSessions = todaySessions.count { it.completed }
            val totalBreakMillis = todayBreaks.sumOf { brk ->
                DayBounds.clampedDurationInWindow(
                    startTime = brk.startTime,
                    endTime = brk.endTime,
                    actualDurationMillis = brk.actualDurationMillis,
                    windowStart = todayStart,
                    windowEnd = todayEnd
                )
            }
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

            // Achievement unlock times come from the same synced session records that produce the
            // lifetime total above, so both agree by construction — no stored counter to drift (§4).
            val badgeUnlockTimes = RewardBadge.deriveUnlockTimes(
                allSessions.map { StudyStamp(it.startTime, it.actualDurationMillis) }
            )

            val now = System.currentTimeMillis()
            val hasUsagePermission = if (context != null) {
                if (now - lastPermissionCheckTime > 15_000L || cachedHasUsagePermission == null) {
                    lastPermissionCheckTime = now
                    val perm = DeviceUsageStatsHelper.hasUsageStatsPermission(context)
                    cachedHasUsagePermission = perm
                    perm
                } else {
                    cachedHasUsagePermission ?: false
                }
            } else false

            val screenTimeMillis = if (hasUsagePermission && context != null) {
                if (now - lastScreenTimeQueryTime > 15_000L) {
                    refreshScreenTimeAsync()
                }
                cachedScreenTimeMillis
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
                focusToScreenRatioPercentage = focusRatio,
                badgeUnlockTimes = badgeUnlockTimes
            )
        }
    }.flowOn(Dispatchers.Default)

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
        }.flowOn(Dispatchers.Default)
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
        // Clamp cross-boundary sessions and breaks to the active period window so multi-day
        // or cross-midnight records contribute only the portion inside [startMillis, endMillis].
        val clampedSessions = sessions.map { session ->
            val clampedDuration = DayBounds.clampedDurationInWindow(
                startTime = session.startTime,
                endTime = session.endTime,
                actualDurationMillis = session.actualDurationMillis,
                windowStart = dateRange.startMillis,
                windowEnd = dateRange.endMillis
            )
            session.copy(actualDurationMillis = clampedDuration)
        }.filter { it.actualDurationMillis > 0L }

        val clampedActivities = activities.map { act ->
            val clampedDuration = DayBounds.clampedDurationInWindow(
                startTime = act.startedAt,
                endTime = act.endedAt,
                actualDurationMillis = act.durationMillis,
                windowStart = dateRange.startMillis,
                windowEnd = dateRange.endMillis
            )
            act.copy(durationMillis = clampedDuration)
        }.filter { it.durationMillis > 0L }

        val sessionIdsInPeriod = clampedSessions.map { it.id }.toSet()
        val standaloneYouTubeMillis = clampedActivities
            .filter { it.source == StudyActivitySource.YOUTUBE && it.sessionId !in sessionIdsInPeriod }
            .sumOf { it.durationMillis }

        val totalStudyMillis = clampedSessions.sumOf { it.actualDurationMillis } + standaloneYouTubeMillis
        val completedCount = clampedSessions.count { it.completed }

        val totalBreakMillis = breaks.sumOf { brk ->
            DayBounds.clampedDurationInWindow(
                startTime = brk.startTime,
                endTime = brk.endTime,
                actualDurationMillis = brk.actualDurationMillis,
                windowStart = dateRange.startMillis,
                windowEnd = dateRange.endMillis
            )
        }
        val breakCount = breaks.size
        val avgBreakMillis = if (breakCount > 0) totalBreakMillis / breakCount else 0L

        val blockedCount = attempts.size

        // Build Daily Chart using original sessions sliced by local calendar day
        val dailyChart = generateDailyChart(dateRange, sessions, prefs.dailyGoalMinutes)

        val studyDaysCount = dailyChart.count { it.studyTimeMillis > 0 }
        val totalDaysInRange = dailyChart.size.coerceAtLeast(1)
        val avgStudyPerStudyDay = if (studyDaysCount > 0) totalStudyMillis / studyDaysCount else 0L

        val bestDay = dailyChart.maxByOrNull { it.studyTimeMillis }
        val consistencyPercent = ((studyDaysCount.toFloat() / totalDaysInRange.toFloat()) * 100).toInt()

        // Subject Breakdown
        val subjectBreakdowns = computeSubjectBreakdowns(clampedSessions, totalStudyMillis)

        // Topic Breakdown
        val topicBreakdowns = computeTopicBreakdowns(clampedSessions)

        // Source Breakdown (partitioning YouTube Study time inside sessions so there is zero double-counting)
        val sourceBreakdowns = computeSourceBreakdowns(clampedSessions, clampedActivities, totalStudyMillis)

        // Blocked Apps Breakdown
        val blockedAppStats = computeBlockedAppStats(attempts)

        // YouTube Study Intelligence
        val youtubeStats = computeYouTubeStats(clampedActivities, attempts)

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
            totalSessions = clampedSessions.size,
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

        // Slice sessions across local calendar days identically to StreakCalculator so
        // cross-midnight sessions attribute each day's exact study minutes without discrepancies.
        val sessionDayMap = mutableMapOf<String, Pair<Long, Int>>() // dateKey -> (totalMillis, count)
        for (session in sessions) {
            val endMillis = if (session.endTime > 0) session.endTime else (session.startTime + session.actualDurationMillis)
            val slices = DayBounds.sliceSessionDurationByDay(
                startTime = session.startTime,
                endTime = endMillis,
                actualDurationMillis = session.actualDurationMillis
            )
            for ((dateKey, sliceMillis) in slices) {
                val current = sessionDayMap.getOrDefault(dateKey, Pair(0L, 0))
                sessionDayMap[dateKey] = Pair(current.first + sliceMillis, current.second + 1)
            }
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

            val dayDate = DayBounds.localDate(currentDayMillis)
            val dayLabel = dayDate.format(dayOfWeekFormat)
            val dateFormatted = dayDate.format(monthDayFormat)

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

        // Partition YouTube study activities that occurred inside a Focus Session so they are
        // never double-counted on top of the session's primary mode (TIMER / STOPWATCH / POMODORO).
        val youtubeActivities = activities.filter { it.source == StudyActivitySource.YOUTUBE }
        val youtubeBySessionId = youtubeActivities.groupBy { it.sessionId }

        for (session in sessions) {
            val source = when (session.mode) {
                com.example.data.model.SessionMode.TIMER -> StudyActivitySource.TIMER
                com.example.data.model.SessionMode.STOPWATCH -> StudyActivitySource.STOPWATCH
                com.example.data.model.SessionMode.POMODORO -> StudyActivitySource.POMODORO
            }
            val linkedYouTubeMillis = youtubeBySessionId[session.id]
                ?.sumOf { it.durationMillis }
                ?.coerceIn(0L, session.actualDurationMillis)
                ?: 0L
            val nonYouTubeSessionMillis = (session.actualDurationMillis - linkedYouTubeMillis).coerceAtLeast(0L)
            if (nonYouTubeSessionMillis > 0L || linkedYouTubeMillis == 0L) {
                val cur = sourceTotals.getOrDefault(source, Pair(0L, 0))
                sourceTotals[source] = Pair(cur.first + nonYouTubeSessionMillis, cur.second + 1)
            }
        }

        if (youtubeActivities.isNotEmpty()) {
            // Cap each session's YouTube duration to that session's actual duration when linked
            var totalYtDuration = 0L
            val sessionDurationById = sessions.associate { it.id to it.actualDurationMillis }
            for ((sessId, actList) in youtubeBySessionId) {
                val rawSum = actList.sumOf { it.durationMillis }
                val cap = sessionDurationById[sessId]
                totalYtDuration += if (cap != null) rawSum.coerceAtMost(cap) else rawSum
            }
            if (totalYtDuration > 0L) {
                sourceTotals[StudyActivitySource.YOUTUBE] = Pair(totalYtDuration, youtubeActivities.size)
            }
        }

        val totalCombined = sourceTotals.values.sumOf { it.first }.coerceAtLeast(totalStudyMillis)

        return sourceTotals.map { (source, pair) ->
            val duration = pair.first
            val percentage = if (totalCombined > 0) (duration.toFloat() / totalCombined.toFloat()).coerceIn(0f, 1f) else 0f
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
                val (start, end) = DayBounds.today()
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
                val monthName = DayBounds.localDate(now)
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                DateRange(start, end, monthName)
            }
            AnalyticsPeriod.CUSTOM -> {
                val start = customStart ?: (now - 7 * 24 * 3600 * 1000L)
                val end = customEnd ?: now
                val startStr = DayBounds.localDate(start).format(monthDayFormat)
                val endStr = DayBounds.localDate(end).format(monthDayFormat)
                DateRange(start, end, "$startStr - $endStr")
            }
        }
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
