package com.example.feature.widgets

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.FocusShieldApp
import com.example.MainActivity
import com.example.data.model.SessionMode
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import com.example.feature.session.service.FocusSessionForegroundService
import kotlinx.coroutines.flow.first

/**
 * Glance ActionCallback to toggle Pause / Resume on an active FocusShield study session
 * directly from the home screen widget without needing to open the app.
 */
class TogglePauseWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val manager = FocusSessionManager.instance
        val active = manager.activeSession.value
        val state = manager.sessionState.value
        if (active != null && (!active.isStrictModeEnabled || active.mode == SessionMode.STOPWATCH)) {
            if (state == SessionState.PAUSED) {
                manager.resumeSession()
            } else if (state == SessionState.RUNNING) {
                manager.pauseSession()
            }
            manager.saveActiveSessionToDisk(context)
        }
        FocusShieldWidgetUpdater.updateAll(context, force = true)
    }
}

/**
 * Glance ActionCallback to start a quick 25-minute Focus Session directly from the
 * home screen widget, or bring FocusShield to the foreground if a session is already running.
 */
class QuickStartFocusWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val manager = FocusSessionManager.instance
        val existing = manager.activeSession.value
        val state = manager.sessionState.value
        val isRunningOrPaused = existing != null &&
            !existing.isCompleted &&
            !existing.isCancelled &&
            (state == SessionState.RUNNING || state == SessionState.PAUSED)

        if (!isRunningOrPaused) {
            val app = context.applicationContext as? FocusShieldApp
            val prefs = try {
                app?.preferencesRepository?.preferencesFlow?.first()
            } catch (_: Exception) {
                null
            }
            val blockedPackages = try {
                app?.blockedAppRepository?.enabledBlockedApps?.first()?.map { it.packageName }?.toSet() ?: emptySet()
            } catch (_: Exception) {
                emptySet()
            }

            val subject = prefs?.defaultSubject?.takeIf { it.isNotBlank() } ?: "Quick Focus"
            val result = manager.startSession(
                mode = SessionMode.TIMER,
                subject = subject,
                topic = prefs?.defaultTopic.orEmpty(),
                plannedDurationMillis = 25 * 60 * 1000L,
                isAppBlocking = prefs?.isAppBlockingDefault ?: true,
                isStrictMode = false,
                isStudyChannels = prefs?.isStudyChannelsDefault ?: true,
                blockedAppPackages = blockedPackages,
                blockNotifications = prefs?.isBlockNotificationsEnabled ?: false
            )
            if (result.isSuccess) {
                manager.saveActiveSessionToDisk(context)
                FocusSessionForegroundService.start(context)
            }
        }

        FocusShieldWidgetUpdater.updateAll(context, force = true)

        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(launchIntent)
        } catch (_: Exception) {
        }
    }
}
