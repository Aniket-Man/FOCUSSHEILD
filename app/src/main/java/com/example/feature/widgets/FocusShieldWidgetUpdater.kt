package com.example.feature.widgets

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.example.core.util.SingleFlightThrottle

/**
 * Central refresh orchestrator for all FocusShield home screen widgets.
 *
 * Updates are triggered by session lifecycle changes, today's study time changes,
 * unlock events, daily reset, and a periodic keep-alive. A short throttle prevents
 * rapid-fire data emissions from causing widget update storms.
 *
 * The throttle is a [SingleFlightThrottle] rather than a timestamp check, because the update that
 * used to be dropped was systematically the one that mattered: a session ending within ten seconds
 * of the previous refresh left the widget showing a running session for as long as nothing else
 * happened (issue #30). Requests inside the window are now coalesced into one trailing refresh, the
 * work is serialised so two callers cannot double-fire, and the window is measured from the moment
 * the refresh *finished*.
 */
object FocusShieldWidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val MIN_UPDATE_INTERVAL_MILLIS = 10_000L

    private val throttle = SingleFlightThrottle(MIN_UPDATE_INTERVAL_MILLIS)

    suspend fun updateAll(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        throttle.submit(force = force) {
            updateWidget { FocusShieldFocusWidget().updateAll(appContext) }
            updateWidget { FocusShieldUsageWidget().updateAll(appContext) }
            updateWidget { FocusShieldUnlockWidget().updateAll(appContext) }
        }
    }

    suspend fun updateUnlocks(context: Context) {
        val appContext = context.applicationContext
        updateWidget { FocusShieldUnlockWidget().updateAll(appContext) }
    }

    private inline fun updateWidget(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Widget not pinned or Glance not ready — safe to ignore
            Log.d(TAG, "Widget update skipped: ${e.message}")
        }
    }
}
