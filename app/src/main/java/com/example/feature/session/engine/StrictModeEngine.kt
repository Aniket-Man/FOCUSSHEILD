package com.example.feature.session.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.data.model.SessionMode

/**
 * Level-3 Strict Mode Engine:
 * Enforces un-cancelable, tamper-proof session lockouts.
 * Anchored to SystemClock.elapsedRealtime() to prevent device clock manipulation bypasses.
 */
class StrictModeEngine private constructor() {

    private val tag = "StrictModeEngine"
    private var isStrictActive: Boolean = false
    private var targetElapsedRealtime: Long = 0L
    private var activeSessionMode: SessionMode? = null
    private var activeSessionId: String? = null

    /**
     * Initializes strict mode lock for an active Timer or Pomodoro session.
     * Stopwatch sessions are not eligible for Strict Mode.
     */
    fun startStrictMode(
        context: Context,
        sessionId: String,
        mode: SessionMode,
        durationMillis: Long
    ) {
        if (mode == SessionMode.STOPWATCH || durationMillis <= 0) {
            isStrictActive = false
            return
        }

        isStrictActive = true
        activeSessionMode = mode
        activeSessionId = sessionId
        targetElapsedRealtime = SystemClock.elapsedRealtime() + durationMillis

        // Persist to disk for reboot & process-kill recovery
        try {
            val prefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_strict_active", true)
                .putString("session_id", sessionId)
                .putString("session_mode", mode.name)
                .putLong("target_elapsed_realtime", targetElapsedRealtime)
                .putLong("duration_millis", durationMillis)
                .putLong("started_boot_count_time", SystemClock.elapsedRealtime())
                .putLong("wall_clock_target", System.currentTimeMillis() + durationMillis)
                .apply()
            Log.d(tag, "Strict Mode locked until elapsedRealtime=$targetElapsedRealtime (duration=${durationMillis}ms)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to persist strict mode state", e)
        }
    }

    /**
     * Checks if a strict session is currently active and within its locked time window.
     */
    fun isStrictSessionActive(): Boolean {
        if (!isStrictActive) return false
        val remaining = targetElapsedRealtime - SystemClock.elapsedRealtime()
        if (remaining <= 0) {
            isStrictActive = false
            return false
        }
        return true
    }

    /**
     * Checks if the session can be ended or cancelled.
     */
    fun canEndSession(): Boolean {
        return !isStrictSessionActive()
    }

    /**
     * Returns the remaining time in millis based on hardware elapsedRealtime.
     */
    fun getRemainingStrictMillis(): Long {
        if (!isStrictActive) return 0L
        return (targetElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    /**
     * Releases strict mode when the session naturally and fully completes.
     */
    fun onSessionCompleted(context: Context) {
        isStrictActive = false
        targetElapsedRealtime = 0L
        activeSessionMode = null
        activeSessionId = null
        try {
            val prefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.d(tag, "Strict Mode unlocked: session completed.")
        } catch (e: Exception) {
            Log.e(tag, "Failed to clear strict mode prefs", e)
        }
    }

    /**
     * Recovers strict mode state after app reboot, cold start, or process recovery.
     */
    fun recoverStateOnLaunch(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences("strict_mode_prefs", Context.MODE_PRIVATE)
            val wasStrict = prefs.getBoolean("is_strict_active", false)
            if (!wasStrict) {
                isStrictActive = false
                return false
            }

            val savedTargetElapsed = prefs.getLong("target_elapsed_realtime", 0L)
            val wallClockTarget = prefs.getLong("wall_clock_target", 0L)
            val nowRealtime = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()

            // Verify both elapsedRealtime and wallClockTarget
            val remainingRealtime = savedTargetElapsed - nowRealtime
            val remainingWall = wallClockTarget - nowWall

            // If time has expired according to wall clock or realtime, release
            if (remainingRealtime <= 0 && remainingWall <= 0) {
                isStrictActive = false
                prefs.edit().clear().apply()
                return false
            }

            isStrictActive = true
            targetElapsedRealtime = if (remainingRealtime > 0) savedTargetElapsed else (nowRealtime + remainingWall.coerceAtLeast(0L))
            activeSessionMode = try {
                SessionMode.valueOf(prefs.getString("session_mode", SessionMode.TIMER.name) ?: SessionMode.TIMER.name)
            } catch (_: Exception) {
                SessionMode.TIMER
            }
            activeSessionId = prefs.getString("session_id", null)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    companion object {
        val instance: StrictModeEngine by lazy { StrictModeEngine() }
    }
}
