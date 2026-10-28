package com.example.feature.widgets

import android.content.Context
import com.example.FocusShieldApp
import com.example.feature.analytics.domain.StreakCalculator
import com.example.feature.session.domain.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Immutable snapshot of everything the home screen widgets display, loaded in one
 * suspending call so all three widgets render from identical data.
 */
data class FocusWidgetSnapshot(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isStrictMode: Boolean = false,
    val subject: String = "",
    val remainingMillis: Long = 0L,
    val sessionProgress: Float = 0f,
    val todayStudyMillis: Long = 0L,
    val streakDays: Int = 0,
    val dailyGoalMinutes: Int = 240,
    val unlockCount: Int = 0,
    val avgUnlocks7d: Int = 0,
    val nextPlanSubject: String? = null,
    val nextPlanTopic: String? = null,
    val nextPlanTime: String? = null,
    val totalPlansToday: Int = 0,
    val completedPlansToday: Int = 0
) {
    val goalProgress: Float
        get() = if (dailyGoalMinutes <= 0) 0f
        else (todayStudyMillis / (dailyGoalMinutes * 60_000f)).coerceIn(0f, 1f)

    companion object {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        private data class StudyPlanTuple(
            val total: Int,
            val completed: Int,
            val nextSubject: String?,
            val nextTopic: String?,
            val nextTime: String?
        )

        /**
         * Aggregates session, streak, goal, study plan, and unlock data for widget rendering.
         * Every repository access is guarded so a widget refresh can never crash
         * the process when a subsystem is not yet initialized.
         */
        suspend fun load(context: Context): FocusWidgetSnapshot = withContext(Dispatchers.IO) {
            val app = context.applicationContext as? FocusShieldApp
                ?: return@withContext FocusWidgetSnapshot()

            val session = try {
                com.example.feature.session.engine.FocusSessionManager.instance.activeSession.value
            } catch (_: Exception) {
                null
            }

            val preferences = try {
                app.preferencesRepository.preferencesFlow.first()
            } catch (_: Exception) {
                null
            }

            val todayStudyMillis = try {
                app.sessionRepository.getTodayTotalStudyTimeMillisFlow().first()
            } catch (_: Exception) {
                0L
            }

            val streakDays = try {
                val sessions = app.sessionRepository.getAllSessionsAscending()
                StreakCalculator.calculate(
                    sessions = sessions,
                    minDailyThresholdMinutes = preferences?.minimumStreakThresholdMinutes ?: 20
                ).currentStreak
            } catch (_: Exception) {
                0
            }

            val todayUnlocks = try {
                app.dailyUnlockRepository.getTodayUnlock()?.unlockCount ?: 0
            } catch (_: Exception) {
                0
            }

            val avgUnlocks = try {
                val cal = Calendar.getInstance()
                val today = synchronized(dateFormat) { dateFormat.format(Date()) }
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = synchronized(dateFormat) { dateFormat.format(cal.time) }
                app.dailyUnlockRepository.getAverageUnlocks(
                    fromDate = weekAgo,
                    toDate = today
                ).toInt()
            } catch (_: Exception) {
                0
            }

            val planInfo = try {
                val plans = app.studyPlanRepository.getTodayPlans()
                val completed = plans.count { it.isCompleted }
                val next = plans.firstOrNull { !it.isCompleted } ?: plans.firstOrNull()
                StudyPlanTuple(
                    total = plans.size,
                    completed = completed,
                    nextSubject = next?.subjectName,
                    nextTopic = next?.topicName,
                    nextTime = next?.startTime
                )
            } catch (_: Exception) {
                StudyPlanTuple(0, 0, null, null, null)
            }

            val isStopwatch = session?.mode == com.example.data.model.SessionMode.STOPWATCH
            val displayTimerMillis = if (isStopwatch) {
                session?.elapsedDurationMillis ?: 0L
            } else {
                session?.remainingDurationMillis ?: 0L
            }

            FocusWidgetSnapshot(
                isActive = session != null && (session.isRunning || session.isPaused),
                isPaused = session?.state == SessionState.PAUSED,
                isStrictMode = session?.isStrictModeEnabled == true && !isStopwatch,
                subject = session?.subject.orEmpty(),
                remainingMillis = displayTimerMillis,
                sessionProgress = session?.progress ?: 0f,
                todayStudyMillis = todayStudyMillis,
                streakDays = streakDays,
                dailyGoalMinutes = preferences?.dailyGoalMinutes ?: 240,
                unlockCount = todayUnlocks,
                avgUnlocks7d = avgUnlocks,
                nextPlanSubject = planInfo.nextSubject,
                nextPlanTopic = planInfo.nextTopic,
                nextPlanTime = planInfo.nextTime,
                totalPlansToday = planInfo.total,
                completedPlansToday = planInfo.completed
            )
        }

        fun formatDuration(millis: Long): String {
            val totalMinutes = millis / 60_000L
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }

        fun formatCountdown(millis: Long): String {
            val totalSeconds = millis / 1000L
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }
}
