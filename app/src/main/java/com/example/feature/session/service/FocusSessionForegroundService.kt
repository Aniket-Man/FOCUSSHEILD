package com.example.feature.session.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.feature.session.domain.FocusSession
import com.example.feature.session.domain.PomodoroPhase
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service dedicated to ensuring uninterrupted background execution
 * of active Focus Sessions, timers, and app blockers.
 *
 * Guarantees that when the user clears FocusShield from Recent Apps or turns off the screen:
 * 1. The session timer continues ticking accurately without being killed.
 * 2. All app and website blockers remain 100% active.
 * 3. A persistent notification keeps the process at foreground priority.
 * 4. Automatic resurrection is triggered via onTaskRemoved and AlarmManager if the OS attempts to reclaim memory.
 */
class FocusSessionForegroundService : Service() {

    private val tag = "FocusSessionService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "focus_foreground_service"
        const val CHANNEL_NAME = "Focus Session & Blocker Status"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START = "com.example.focusshield.ACTION_START_SESSION_SERVICE"
        const val ACTION_STOP = "com.example.focusshield.ACTION_STOP_SESSION_SERVICE"
        const val ACTION_PAUSE = "com.example.focusshield.ACTION_PAUSE_SESSION_SERVICE"
        const val ACTION_RESUME = "com.example.focusshield.ACTION_RESUME_SESSION_SERVICE"
        const val ACTION_END = "com.example.focusshield.ACTION_END_SESSION_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, FocusSessionForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("FocusSessionService", "Error starting foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusSessionForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("FocusSessionService", "Error stopping foreground service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        observeActiveSession()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FocusShield:SessionWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // 4 hours maximum safety timeout
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error releasing wake lock: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val sessionManager = FocusSessionManager.instance

        when (action) {
            ACTION_START -> {
                // Ensure session state is restored from disk if empty
                if (sessionManager.activeSession.value == null) {
                    sessionManager.restoreActiveSessionFromDisk(this)
                }
                val current = sessionManager.activeSession.value
                val notification = buildForegroundNotification(current)
                startForegroundWithProperType(notification)
            }
            ACTION_PAUSE -> {
                sessionManager.pauseSession()
                val current = sessionManager.activeSession.value
                updateNotification(current)
            }
            ACTION_RESUME -> {
                sessionManager.resumeSession()
                val current = sessionManager.activeSession.value
                updateNotification(current)
            }
            ACTION_END -> {
                sessionManager.endSession()
                stopForegroundGracefully()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopForegroundGracefully()
                return START_NOT_STICKY
            }
        }

        // Return START_STICKY to guarantee the Android OS will restart this service if killed
        return START_STICKY
    }

    private fun startForegroundWithProperType(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error invoking startForeground: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(tag, "Fallback startForeground failed: ${e2.message}")
            }
        }
    }

    private fun observeActiveSession() {
        collectorJob?.cancel()
        collectorJob = serviceScope.launch {
            FocusSessionManager.instance.activeSession.collectLatest { session ->
                if (session != null && (session.state == SessionState.RUNNING || session.state == SessionState.PAUSED)) {
                    updateNotification(session)
                } else if (session == null || session.state == SessionState.COMPLETED || session.state == SessionState.CANCELLED || session.state == SessionState.IDLE) {
                    // Session ended naturally or was cleared
                    stopForegroundGracefully()
                }
            }
        }
    }

    private fun updateNotification(session: FocusSession?) {
        val notification = buildForegroundNotification(session)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(session: FocusSession?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        if (session == null) {
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_focus_notification)
                .setContentTitle("FocusShield Active")
                .setContentText("Distraction protection is running in the background.")
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        val subject = session.subject.ifBlank { "Study Session" }
        val topic = session.topic.ifBlank { "Focus" }

        val timeFormatted = when (session.mode) {
            SessionMode.TIMER -> {
                TimeFormatter.formatDigital(session.remainingDurationMillis, alwaysShowHours = true)
            }
            SessionMode.POMODORO -> {
                val phaseLabel = when (session.pomodoroPhase) {
                    PomodoroPhase.FOCUS -> "Focus (${session.currentCycle}/${session.totalCycles})"
                    PomodoroPhase.SHORT_BREAK -> "Short Break"
                    PomodoroPhase.LONG_BREAK -> "Long Break"
                    null -> "Focus"
                }
                "$phaseLabel: ${TimeFormatter.formatDigital(session.remainingDurationMillis, alwaysShowHours = false)}"
            }
            SessionMode.STOPWATCH -> {
                "${TimeFormatter.formatDigital(session.elapsedDurationMillis, alwaysShowHours = true)} (Elapsed)"
            }
        }

        val title = when {
            session.isBreakActive -> "☕ Break Active ($subject)"
            session.isPaused -> "⏸️ Session Paused ($subject)"
            else -> "🛡️ Focus Shield Active ($subject)"
        }

        val statusDetail = if (session.isAppBlockingEnabled) "App & Shorts Blocker Active" else "Timer Active"
        val contentText = "$timeFormatted • $statusDetail"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(topic)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Action: Pause / Resume
        if (session.isRunning) {
            val pauseIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, FocusSessionForegroundService::class.java).apply { action = ACTION_PAUSE },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Pause", pauseIntent)
        } else if (session.isPaused) {
            val resumeIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, FocusSessionForegroundService::class.java).apply { action = ACTION_RESUME },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Resume", resumeIntent)
        }

        // Action: Open Session
        builder.addAction(0, "Open", contentIntent)

        return builder.build()
    }

    /**
     * Triggered when the user removes / swipes away FocusShield from Android's Recent Apps list.
     * Prevents session destruction and schedules an immediate resurrection restart alarm.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(tag, "onTaskRemoved triggered: App swiped from recents. Ensuring background execution continues.")

        val sessionManager = FocusSessionManager.instance
        val currentSession = sessionManager.activeSession.value

        if (currentSession != null && (currentSession.state == SessionState.RUNNING || currentSession.state == SessionState.PAUSED)) {
            // Persist current session snapshot immediately to disk
            sessionManager.saveActiveSessionToDisk(this)

            // Schedule immediate restart alarm if Android OS attempts to kill the process
            scheduleRestartAlarm()
        }
    }

    private fun scheduleRestartAlarm() {
        try {
            val restartIntent = Intent(applicationContext, FocusSessionForegroundService::class.java).apply {
                action = ACTION_START
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                999,
                restartIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val triggerAtMillis = System.currentTimeMillis() + 1000L // 1 second resurrection

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    restartPendingIntent
                )
            } else {
                alarmManager?.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    restartPendingIntent
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to schedule restart alarm: ${e.message}")
        }
    }

    private fun stopForegroundGracefully() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping foreground: ${e.message}")
        } finally {
            releaseWakeLock()
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status and timer countdown for active study sessions and app blockers"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
