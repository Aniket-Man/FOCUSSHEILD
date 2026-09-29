package com.example.feature.applimits.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * On-disk snapshot of the temporary usage session the user is currently spending.
 *
 * The session used to exist only in memory. FocusShield runs no foreground service while an App
 * Limit session is live, so the process could be killed (Recents swipe, low-memory kill, OEM
 * battery manager) with the timer inside it — and the deadline that raises the "time's up" blocker
 * went with it. The app then looked unlimited until the limit was re-armed from scratch, which is
 * exactly the "I chose a time and the overlay never came back" report.
 *
 * Persisting the session is what lets [AppLimitManager.restoreSessionFromDisk] put the deadline
 * (and the pause/resume bookkeeping) back after the process restarts. It is deliberately a
 * SharedPreferences snapshot rather than a Room table: it is a single, write-once-per-transition
 * record about *this device right now*, the same shape as `FocusSessionManager`'s active-session
 * snapshot, and nothing else needs to query it.
 *
 * Only structural changes are written (start / pause / resume / finish) — never the 500 ms tick —
 * because the elapsed time is derived from a clock anchor recorded at the last save rather than
 * stored per tick. The anchor is [SystemClock.elapsedRealtime] (monotonic, survives nothing but a
 * reboot): deriving the restore gap from `startedAt` and the wall clock let a manual time change,
 * an NTP step or a timezone shift move the "deadline" arbitrarily far — a session could be
 * declared finished the moment it was restored, or never finish at all. The wall timestamp is kept
 * only as a fallback for snapshots written by an older build and across a reboot, where the
 * monotonic clock has no relationship to the moment the session was saved.
 */
class AppLimitSessionStore(context: Context) {

    private val tag = "AppLimitSessionStore"
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * A restored session plus the moment it was persisted.
     *
     * The date is what keeps yesterday's snapshot from being re-armed today: the daily allowance
     * resets at midnight, so a session started on another day is stale and must be dropped.
     * [savedAtElapsed]/[savedAtWall] anchor the "how much time passed while the process was dead"
     * calculation: elapsedRealtime while it still refers to the same boot, otherwise the wall
     * timestamp, otherwise (legacy snapshot with neither) the session's own `startedAt`.
     */
    data class Snapshot(
        val dateString: String,
        val session: ActiveAppUsageSession,
        val savedAtElapsed: Long = 0L,
        val savedAtWall: Long = 0L
    )

    fun save(session: ActiveAppUsageSession, dateString: String) {
        try {
            prefs.edit().apply {
                putString(KEY_DATE, dateString)
                putString(KEY_ID, session.id)
                putString(KEY_PACKAGE, session.packageName)
                putString(KEY_APP_NAME, session.appName)
                putLong(KEY_STARTED_AT, session.startedAt)
                putLong(KEY_SELECTED_DURATION, session.selectedDurationMillis)
                putLong(KEY_ELAPSED, session.elapsedMillis)
                putBoolean(KEY_IS_EMERGENCY, session.isEmergency)
                putInt(KEY_DAILY_LIMIT_MINUTES, session.dailyLimitMinutes)
                putLong(KEY_INITIAL_DAILY_USED, session.initialDailyUsedMillis)
                putBoolean(KEY_IS_STRICT, session.isStrict)
                putBoolean(KEY_IS_PAUSED, session.isPaused)
                putLong(KEY_TOTAL_PAUSED, session.totalPausedMillis)
                putLong(KEY_LAST_PAUSE_AT, session.lastPauseTimestamp)
                putLong(KEY_SAVED_AT_ELAPSED, SystemClock.elapsedRealtime())
                putLong(KEY_SAVED_AT_WALL, System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
            // Losing the snapshot only costs us the restore path — never fail a user action for it.
            Log.e(tag, "Failed to persist app limit session: ${e.message}")
        }
    }

    fun load(): Snapshot? {
        return try {
            if (!prefs.contains(KEY_PACKAGE)) return null
            val dateString = prefs.getString(KEY_DATE, null) ?: return null
            val packageName = prefs.getString(KEY_PACKAGE, null) ?: return null
            val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
            if (startedAt <= 0L) return null

            Snapshot(
                dateString = dateString,
                savedAtElapsed = prefs.getLong(KEY_SAVED_AT_ELAPSED, 0L),
                savedAtWall = prefs.getLong(KEY_SAVED_AT_WALL, 0L),
                session = ActiveAppUsageSession(
                    id = prefs.getString(KEY_ID, null) ?: java.util.UUID.randomUUID().toString(),
                    packageName = packageName,
                    appName = prefs.getString(KEY_APP_NAME, null) ?: packageName,
                    startedAt = startedAt,
                    selectedDurationMillis = prefs.getLong(KEY_SELECTED_DURATION, 0L),
                    elapsedMillis = prefs.getLong(KEY_ELAPSED, 0L),
                    isEmergency = prefs.getBoolean(KEY_IS_EMERGENCY, false),
                    dailyLimitMinutes = prefs.getInt(KEY_DAILY_LIMIT_MINUTES, 60),
                    initialDailyUsedMillis = prefs.getLong(KEY_INITIAL_DAILY_USED, 0L),
                    isStrict = prefs.getBoolean(KEY_IS_STRICT, false),
                    isPaused = prefs.getBoolean(KEY_IS_PAUSED, false),
                    totalPausedMillis = prefs.getLong(KEY_TOTAL_PAUSED, 0L),
                    lastPauseTimestamp = prefs.getLong(KEY_LAST_PAUSE_AT, 0L)
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to read app limit session snapshot: ${e.message}")
            null
        }
    }

    fun clear() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to clear app limit session snapshot: ${e.message}")
        }
    }

    private companion object {
        const val PREFS_NAME = "focus_app_limit_session_prefs"
        const val KEY_DATE = "dateString"
        const val KEY_ID = "id"
        const val KEY_PACKAGE = "packageName"
        const val KEY_APP_NAME = "appName"
        const val KEY_STARTED_AT = "startedAt"
        const val KEY_SELECTED_DURATION = "selectedDurationMillis"
        const val KEY_ELAPSED = "elapsedMillis"
        const val KEY_IS_EMERGENCY = "isEmergency"
        const val KEY_DAILY_LIMIT_MINUTES = "dailyLimitMinutes"
        const val KEY_INITIAL_DAILY_USED = "initialDailyUsedMillis"
        const val KEY_IS_STRICT = "isStrict"
        const val KEY_IS_PAUSED = "isPaused"
        const val KEY_TOTAL_PAUSED = "totalPausedMillis"
        const val KEY_LAST_PAUSE_AT = "lastPauseTimestamp"
        const val KEY_SAVED_AT_ELAPSED = "savedAtElapsed"
        const val KEY_SAVED_AT_WALL = "savedAtWall"
    }
}
