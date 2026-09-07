package com.example.feature.widgets

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll

/**
 * Central refresh orchestrator for all FocusShield home screen widgets.
 *
 * Updates are triggered by session lifecycle changes, today's study time changes,
 * unlock events, daily reset, and a periodic keep-alive. A short throttle prevents
 * rapid-fire data emissions from causing widget update storms.
 */
object FocusShieldWidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val MIN_UPDATE_INTERVAL_MILLIS = 10_000L

    @Volatile
    private var lastUpdateTimestamp = 0L

    suspend fun updateAll(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateTimestamp < MIN_UPDATE_INTERVAL_MILLIS) {
            return
        }
        lastUpdateTimestamp = now

        val appContext = context.applicationContext
        updateWidget(appContext) { FocusShieldFocusWidget().updateAll(appContext) }
        updateWidget(appContext) { FocusShieldUsageWidget().updateAll(appContext) }
        updateWidget(appContext) { FocusShieldUnlockWidget().updateAll(appContext) }
    }

    suspend fun updateUnlocks(context: Context) {
        val appContext = context.applicationContext
        updateWidget(appContext) { FocusShieldUnlockWidget().updateAll(appContext) }
    }

    private inline fun updateWidget(context: Context, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Widget not pinned or Glance not ready — safe to ignore
            Log.d(TAG, "Widget update skipped: ${e.message}")
        }
    }
}
