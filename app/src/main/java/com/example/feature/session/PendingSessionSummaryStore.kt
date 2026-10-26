package com.example.feature.session

import android.content.Context
import com.example.data.model.SessionMode
import com.example.feature.session.domain.FocusSession
import com.example.feature.session.domain.SessionState
import com.example.feature.session.domain.SessionSummaryData
import com.example.feature.session.domain.StrictModeConfig

/**
 * Durable home for a finished session that the user has not saved or discarded yet.
 *
 * The summary screen is the only place where a session becomes a database record. Until the user
 * taps "Save Session" or "Don't Save", a process death must not throw that decision away, so the
 * summary is mirrored to SharedPreferences as soon as it is produced and cleared only when the
 * user answers. [SessionViewModel] restores it on construction and the splash redirect opens the
 * summary route again, which is what makes "close the app on the summary screen" safe.
 *
 * Only the fields the summary screen renders and the fields `SessionRepository.recordSession`
 * needs are stored. The heavy live-session extras (laps, app packages) are not needed to persist
 * a finished record, so they are deliberately rebuilt as empty/minimal values.
 */
object PendingSessionSummaryStore {

    private const val PREFS_NAME = "pending_session_summary_prefs"
    private const val KEY_ID = "session_id"

    fun save(context: Context, summary: SessionSummaryData) {
        try {
            // commit(), not apply(): the whole point of this snapshot is to survive the process
            // being killed on the summary screen, so the write must reach disk before we return.
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putString(KEY_ID, summary.sessionId)
                .putString("mode", summary.mode.name)
                .putString("subject", summary.subject)
                .putString("topic", summary.topic)
                .putString("goal", summary.goal)
                .putLong("started_at", summary.startedAt)
                .putLong("ended_at", summary.endedAt)
                .putLong("planned_duration", summary.plannedDurationMillis)
                .putLong("pure_study_duration", summary.pureStudyDurationMillis)
                .putLong("total_break_duration", summary.totalBreakDurationMillis)
                .putBoolean("is_completed", summary.isCompleted)
                .putBoolean("is_cancelled", summary.isCancelled)
                .putBoolean("is_strict_mode", summary.isStrictModeEnabled)
                .putBoolean("is_app_blocking", summary.isAppBlockingEnabled)
                .putBoolean("is_study_channels", summary.isStudyChannelsEnabled)
                .putBoolean("is_youtube_study_mode", summary.isYouTubeStudyModeEnabled)
                .putBoolean("is_browser_study_mode", summary.isBrowserStudyModeEnabled)
                .putInt("blocked_apps_count", summary.blockedAppsCount)
                .putInt("distractions_blocked_count", summary.distractionsBlockedCount)
                .putInt("completed_pomodoro_cycles", summary.completedPomodoroCycles)
                .putInt("total_pomodoro_cycles", summary.totalPomodoroCycles)
                .putInt("laps_count", summary.lapsCount)
                .commit()
        } catch (_: Exception) {
            // Persistence is a safety net for the user's decision, never a reason to crash the
            // session flow in an environment without storage.
        }
    }

    fun load(context: Context): SessionSummaryData? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sessionId = prefs.getString(KEY_ID, null)?.takeIf { it.isNotBlank() } ?: return null
            val mode = prefs.getString("mode", null)
                ?.let { runCatching { SessionMode.valueOf(it) }.getOrNull() }
                ?: return null

            val isCompleted = prefs.getBoolean("is_completed", true)
            val isCancelled = prefs.getBoolean("is_cancelled", false)
            val totalPomodoroCycles = prefs.getInt("total_pomodoro_cycles", 1).coerceAtLeast(1)
            val completedPomodoroCycles = prefs.getInt("completed_pomodoro_cycles", 0)

            // Rebuild only what recording needs. The summary values are authoritative for study
            // time and result flags, so this cannot turn a saved session into a different record.
            val rawSession = FocusSession(
                id = sessionId,
                mode = mode,
                subject = prefs.getString("subject", "").orEmpty(),
                topic = prefs.getString("topic", "").orEmpty(),
                goal = prefs.getString("goal", "").orEmpty(),
                state = if (isCancelled) SessionState.CANCELLED else SessionState.COMPLETED,
                startedAt = prefs.getLong("started_at", 0L),
                plannedDurationMillis = prefs.getLong("planned_duration", 0L),
                elapsedDurationMillis = prefs.getLong("pure_study_duration", 0L),
                remainingDurationMillis = 0L,
                currentCycle = if (isCancelled) {
                    (completedPomodoroCycles + 1).coerceAtLeast(1)
                } else {
                    totalPomodoroCycles
                },
                totalCycles = totalPomodoroCycles,
                isAppBlockingEnabled = prefs.getBoolean("is_app_blocking", true),
                isStrictModeEnabled = prefs.getBoolean("is_strict_mode", false),
                strictModeConfig = StrictModeConfig(
                    enabled = prefs.getBoolean("is_strict_mode", false)
                ),
                isStudyChannelsEnabled = prefs.getBoolean("is_study_channels", false),
                isBrowserStudyModeEnabled = prefs.getBoolean("is_browser_study_mode", false)
            )

            SessionSummaryData(
                sessionId = sessionId,
                mode = mode,
                subject = rawSession.subject,
                topic = rawSession.topic,
                goal = rawSession.goal,
                startedAt = rawSession.startedAt,
                endedAt = prefs.getLong("ended_at", rawSession.startedAt),
                plannedDurationMillis = rawSession.plannedDurationMillis,
                pureStudyDurationMillis = prefs.getLong("pure_study_duration", 0L),
                totalBreakDurationMillis = prefs.getLong("total_break_duration", 0L),
                isCompleted = isCompleted,
                isCancelled = isCancelled,
                isStrictModeEnabled = rawSession.isStrictModeEnabled,
                isAppBlockingEnabled = rawSession.isAppBlockingEnabled,
                isStudyChannelsEnabled = rawSession.isStudyChannelsEnabled,
                isYouTubeStudyModeEnabled = prefs.getBoolean("is_youtube_study_mode", false),
                isBrowserStudyModeEnabled = rawSession.isBrowserStudyModeEnabled,
                blockedAppsCount = prefs.getInt("blocked_apps_count", 0),
                distractionsBlockedCount = prefs.getInt("distractions_blocked_count", 0),
                completedPomodoroCycles = completedPomodoroCycles,
                totalPomodoroCycles = totalPomodoroCycles,
                lapsCount = prefs.getInt("laps_count", 0),
                rawSession = rawSession
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        } catch (_: Exception) {
            // Nothing to clear (or no storage); the in-memory summary is still discarded by the VM.
        }
    }
}
