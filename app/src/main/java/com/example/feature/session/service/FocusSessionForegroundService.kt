package com.example.feature.session.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps an active focus session alive in the background.
 *
 * What it actually guarantees — and what it does not
 * -------------------------------------------------
 * While a session is running this service:
 *  1. keeps the process at foreground priority with an ongoing notification, so the session timer
 *     and the accessibility-based blockers keep running when FocusShield is not on screen;
 *  2. holds a **renewed** partial wake lock for as long as the session is *running* (see
 *     [WAKE_LOCK_RENEW_INTERVAL_MILLIS]), so a multi-hour session cannot outlive its lock. The old
 *     implementation acquired one lock with a fixed 4-hour timeout, which silently stopped
 *     protecting the CPU partway through any longer session;
 *  3. releases the lock and the foreground state as soon as the session is paused, completed or
 *     cancelled — a paused session needs no CPU and holding a wake lock for it drains the battery;
 *  4. asks the platform for a restart after the user swipes the app away from Recents
 *     ([onTaskRemoved]).
 *
 * What it does **not** guarantee: [START_STICKY] is not a resurrection contract. It asks the system
 * to recreate the service after a low-memory kill, and the system is free to refuse — Android 12+
 * forbids background foreground-service starts, and there are known Android 14/15 platform bugs
 * where a sticky restart of a foreground service throws
 * `ForegroundServiceStartNotAllowedException` (which the platform then follows with a
 * foreground-service timeout ANR). Aggressive OEM battery managers (Xiaomi/Huawei/Oppo/Samsung)
 * additionally freeze or kill even sticky foreground services.
 *
 * So the design is *layered* rather than trusting one flag:
 *  - START_STICKY, best-effort recreation by the OS;
 *  - [onTaskRemoved] → an exact alarm 1 s later that starts this service through a
 *    foreground-service `PendingIntent` (exact alarms are documented as exempt from the Android 12+
 *    background-start restriction);
 *  - `FocusShieldApp.onCreate` restores a persisted session on the next app launch and re-starts
 *    this service;
 *  - the session itself is persisted to disk ([FocusSessionManager.saveActiveSessionToDisk]) so the
 *    elapsed time survives a process death;
 *  - a reboot re-arms everything through [com.example.feature.session.notification.BootAndDailyResetReceiver].
 * Each failure path is logged with the reason, so "the session stopped in the background" is
 * diagnosable from `adb logcat -s FocusSessionService` instead of being a mystery.
 *
 * Foreground-service type: see [ForegroundServiceTypes] and the manifest comment on the `<service>`
 * element. Getting this wrong is fatal (`IllegalArgumentException` on API 29–33 for an undeclared
 * type, `MissingForegroundServiceTypeException` on API 34+), so the type is resolved from the
 * declared manifest mask and validated before use.
 */
class FocusSessionForegroundService : Service() {

    private val tag = "FocusSessionService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectorJob: Job? = null
    private var wakeLockJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Set once [startForeground] succeeded, so the observer never stops a service that never ran. */
    @Volatile
    private var isForeground = false

    companion object {
        const val CHANNEL_ID = "focus_foreground_service"
        const val CHANNEL_NAME = "Focus Session & Blocker Status"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START = "com.example.focusshield.ACTION_START_SESSION_SERVICE"
        const val ACTION_STOP = "com.example.focusshield.ACTION_STOP_SESSION_SERVICE"
        const val ACTION_PAUSE = "com.example.focusshield.ACTION_PAUSE_SESSION_SERVICE"
        const val ACTION_RESUME = "com.example.focusshield.ACTION_RESUME_SESSION_SERVICE"
        const val ACTION_END = "com.example.focusshield.ACTION_END_SESSION_SERVICE"

        private const val RESTART_REQUEST_CODE = 999

        /**
         * The wake lock is held in chunks and renewed before each chunk expires, so an arbitrarily
         * long session stays covered without ever holding a lock beyond its stated timeout.
         */
        private const val WAKE_LOCK_RENEW_INTERVAL_MILLIS = 15 * 60 * 1000L
        private const val WAKE_LOCK_CHUNK_MILLIS = 20 * 60 * 1000L

        /**
         * Starts (or re-foregrounds) the service. Returns false when the platform refused the
         * background start — callers should not assume the session is protected in that case.
         */
        fun start(context: Context): Boolean {
            val intent = Intent(context, FocusSessionForegroundService::class.java).apply {
                action = ACTION_START
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: IllegalStateException) {
                // Android 8+ background-execution limit or Android 12+ background FGS restriction.
                // Not fatal for the session (the timer keeps running while the process lives), but the
                // user's protection is weaker, so it is logged loudly and reported to the caller.
                Log.e(
                    LOG_TAG,
                    "Foreground service start was refused by the platform " +
                        "(background start restriction). The session continues, but the OS may " +
                        "throttle it: ${e.message}"
                )
                false
            } catch (e: SecurityException) {
                Log.e(LOG_TAG, "Foreground service start denied (missing permission?)", e)
                false
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusSessionForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                // startService is refused when the process is in the background. The service may
                // still be running, so ask the platform to stop it directly — that is always allowed.
                Log.d(LOG_TAG, "Graceful stop was refused (${e.message}); stopping the service directly")
                context.stopService(Intent(context, FocusSessionForegroundService::class.java))
            }
        }

        private const val LOG_TAG = "FocusSessionService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeActiveSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent is the normal case when the system recreates a START_STICKY service after a
        // kill. It means "resume what the persisted state says", which is exactly ACTION_START.
        val action = intent?.action ?: ACTION_START
        val sessionManager = FocusSessionManager.instance

        when (action) {
            ACTION_START -> {
                if (sessionManager.activeSession.value == null) {
                    sessionManager.restoreActiveSessionFromDisk(this)
                }
                val current = sessionManager.activeSession.value
                if (current == null) {
                    // Nothing to protect. Holding a foreground service with no session would keep the
                    // process pinned (and, on Android 14+, consume a foreground-service slot) for no
                    // benefit, so the service steps down. This is the recovery path after a stale
                    // restart alarm fires once the session has already ended.
                    Log.i(tag, "No active session to protect; not entering the foreground")
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                startForegroundWithProperType(buildForegroundNotification(current))
            }
            ACTION_PAUSE -> {
                sessionManager.pauseSession()
                updateNotification(sessionManager.activeSession.value)
            }
            ACTION_RESUME -> {
                sessionManager.resumeSession()
                updateNotification(sessionManager.activeSession.value)
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

        // Best-effort only. START_STICKY asks the OS to recreate the service (with a null intent)
        // after a low-memory kill; it is not a guarantee — Android may refuse the restart (Android
        // 12+ background-start rules, Android 14/15 sticky-restart bugs) and OEM battery managers may
        // block it. The alarm in onTaskRemoved(), the app-launch restore, and the persisted session
        // snapshot are the layers that actually recover the session; see the class doc.
        return START_STICKY
    }

    // ---- foreground + service type ------------------------------------------------------

    /**
     * Enters the foreground with a type that the manifest actually declares.
     *
     * Failure here must never crash the user's session: each failure mode is caught, logged, and
     * followed by stepping the service down (rather than leaving a started service that the platform
     * will kill with a foreground-service timeout ANR).
     */
    private fun startForegroundWithProperType(notification: Notification) {
        val declaredTypes = declaredForegroundServiceTypes()
        val resolution = ForegroundServiceTypes.resolve(Build.VERSION.SDK_INT, declaredTypes)
        if (resolution.warning != null) {
            Log.w(tag, resolution.warning)
        }

        try {
            when (val choice = resolution.choice) {
                is ForegroundServiceTypes.Choice.Typed ->
                    startForeground(NOTIFICATION_ID, notification, choice.type)
                ForegroundServiceTypes.Choice.Untyped ->
                    @Suppress("DEPRECATION")
                    startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            Log.i(
                tag,
                "Service entered the foreground (sdk=${Build.VERSION.SDK_INT}, " +
                    "declared=0x${declaredTypes.toString(16)}, choice=${resolution.choice})"
            )
        } catch (e: IllegalArgumentException) {
            // "type is not a subset of foregroundServiceType attribute" — a manifest/runtime mismatch.
            Log.e(tag, "startForeground rejected the service type; stepping the service down", e)
            stepDownAfterForegroundFailure()
        } catch (e: IllegalStateException) {
            // Includes ForegroundServiceStartNotAllowedException (Android 12+ background start) and
            // MissingForegroundServiceTypeException (Android 14+ without a declared type) — both are
            // IllegalStateException subclasses, checked this way so the API-31 class need not be
            // referenced on older devices.
            Log.e(tag, "startForeground not allowed by the platform; stepping the service down", e)
            stepDownAfterForegroundFailure()
        } catch (e: SecurityException) {
            Log.e(tag, "Missing FOREGROUND_SERVICE permission for this type", e)
            stepDownAfterForegroundFailure()
        }
    }

    private fun stepDownAfterForegroundFailure() {
        isForeground = false
        releaseWakeLock()
        stopSelf()
    }

    /** The `foregroundServiceType` mask the manifest declares for this service, or 0 if unreadable. */
    private fun declaredForegroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return try {
            val component = ComponentName(this, javaClass)
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getServiceInfo(component, 0)
            }
            info.foregroundServiceType
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(tag, "Could not read this service's manifest entry: ${e.message}")
            0
        }
    }

    // ---- wake lock ----------------------------------------------------------------------

    /**
     * Keeps the CPU awake while — and only while — a session is actually running.
     *
     * The lock is re-acquired on a timer ([WAKE_LOCK_RENEW_INTERVAL_MILLIS]) with a chunk timeout
     * ([WAKE_LOCK_CHUNK_MILLIS]) that is longer than the interval, so the lock is continuously held
     * across renewals yet is guaranteed to be released by the OS if this process dies. Renewing an
     * already-held, non-reference-counted lock resets its timeout; it does not stack.
     */
    private fun syncWakeLock(running: Boolean) {
        if (running) startWakeLockRenewal() else stopWakeLockRenewal()
    }

    private fun startWakeLockRenewal() {
        if (wakeLockJob?.isActive == true) return
        wakeLockJob = serviceScope.launch {
            Log.d(tag, "Acquiring session wake lock (renewed every ${WAKE_LOCK_RENEW_INTERVAL_MILLIS / 60_000} min)")
            while (isActive) {
                renewWakeLock()
                delay(WAKE_LOCK_RENEW_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopWakeLockRenewal() {
        wakeLockJob?.cancel()
        wakeLockJob = null
        releaseWakeLock()
    }

    private fun renewWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager == null) {
            Log.w(tag, "PowerManager unavailable; session runs without a wake lock")
            return
        }
        if (wakeLock == null) {
            wakeLock = try {
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusShield:SessionWakeLock")
                    .apply { setReferenceCounted(false) }
            } catch (e: SecurityException) {
                // WAKE_LOCK permission missing (should not happen: it is declared in the manifest).
                Log.e(tag, "Cannot create a wake lock: ${e.message}")
                null
            } ?: return
        }

        try {
            wakeLock?.acquire(WAKE_LOCK_CHUNK_MILLIS)
        } catch (e: SecurityException) {
            Log.e(tag, "Wake lock acquisition denied: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(tag, "Wake lock could not be acquired: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (e: IllegalStateException) {
            // release() throws IllegalStateException when the lock was already released by the OS
            // (e.g. the timeout elapsed during a long doze). Nothing to do beyond noting it.
            Log.w(tag, "Wake lock release reported: ${e.message}")
        }
    }

    // ---- session observation ------------------------------------------------------------

    private fun observeActiveSession() {
        collectorJob?.cancel()
        collectorJob = serviceScope.launch {
            FocusSessionManager.instance.activeSession.collectLatest { session ->
                when {
                    session == null -> {
                        // Nothing is running. Stop only if we are actually in the foreground, so a
                        // collector tick that lands before onStartCommand cannot tear down a service
                        // that is in the middle of starting.
                        if (isForeground) stopForegroundGracefully() else syncWakeLock(running = false)
                    }
                    session.state == SessionState.RUNNING -> {
                        syncWakeLock(running = true)
                        if (isForeground) updateNotification(session)
                    }
                    session.state == SessionState.PAUSED -> {
                        syncWakeLock(running = false)
                        if (isForeground) updateNotification(session)
                    }
                    else -> {
                        // COMPLETED / CANCELLED / IDLE → the session is over.
                        syncWakeLock(running = false)
                        if (isForeground) stopForegroundGracefully()
                    }
                }
            }
        }
    }

    // ---- notification -------------------------------------------------------------------

    private fun updateNotification(session: FocusSession?) {
        if (!isForeground) return
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
            pendingIntentFlags()
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
                pendingIntentFlags()
            )
            builder.addAction(0, "Pause", pauseIntent)
        } else if (session.isPaused) {
            val resumeIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, FocusSessionForegroundService::class.java).apply { action = ACTION_RESUME },
                pendingIntentFlags()
            )
            builder.addAction(0, "Resume", resumeIntent)
        }

        // Action: Open Session
        builder.addAction(0, "Open", contentIntent)

        return builder.build()
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    // ---- swipe-from-recents recovery ------------------------------------------------------

    /**
     * Triggered when the user removes FocusShield from Recents.
     *
     * The session snapshot is persisted immediately, then a restart is requested through an exact
     * alarm: exact alarms are documented as exempt from the Android 12+ restriction on starting
     * foreground services from the background, which `startService()` straight from here is not.
     * If exact alarms are unavailable the alarm is still armed inexactly (best effort) and the
     * limitation is logged — the persisted session means the next app launch recovers regardless.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(tag, "onTaskRemoved: app swiped from Recents; requesting a restart")

        val sessionManager = FocusSessionManager.instance
        val currentSession = sessionManager.activeSession.value

        if (currentSession != null && (currentSession.state == SessionState.RUNNING || currentSession.state == SessionState.PAUSED)) {
            sessionManager.saveActiveSessionToDisk(this)
            scheduleRestartAlarm()
        }
    }

    private fun scheduleRestartAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.w(tag, "AlarmManager unavailable; cannot schedule a restart request")
            return
        }

        val restartIntent = Intent(applicationContext, FocusSessionForegroundService::class.java).apply {
            action = ACTION_START
        }
        // A foreground-service PendingIntent makes the restart a "start a foreground service" request,
        // which is the only form the OS accepts for a service that will call startForeground().
        val operation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                applicationContext,
                RESTART_REQUEST_CODE,
                restartIntent,
                pendingIntentFlags()
            )
        } else {
            PendingIntent.getService(
                applicationContext,
                RESTART_REQUEST_CODE,
                restartIntent,
                pendingIntentFlags()
            )
        }

        val triggerAtMillis = System.currentTimeMillis() + 1_000L
        try {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)

                com.example.core.permission.FocusPermissionManager.isExactAlarmGranted(this) ->
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operation
                    )

                else -> {
                    Log.w(
                        tag,
                        "Exact alarms are not available; restarting the session service with an " +
                            "inexact alarm. Recovery may be delayed by the system."
                    )
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operation
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(tag, "Restart alarm refused by the platform: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.w(tag, "Restart alarm could not be armed: ${e.message}")
        }
    }

    // ---- lifecycle ---------------------------------------------------------------------

    private fun stopForegroundGracefully() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: IllegalStateException) {
            // The service was never in the foreground or was already stepped down.
            Log.w(tag, "stopForeground reported: ${e.message}")
        } finally {
            isForeground = false
            stopWakeLockRenewal()
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
        stopWakeLockRenewal()
        collectorJob?.cancel()
        serviceScope.cancel()
        Log.d(tag, "Service destroyed (session state=${FocusSessionManager.instance.activeSession.value?.state})")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
