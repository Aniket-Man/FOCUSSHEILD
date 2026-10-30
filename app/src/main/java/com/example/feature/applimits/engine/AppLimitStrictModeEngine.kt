package com.example.feature.applimits.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.sound.FocusAlertSoundManager
import com.example.data.local.entity.AppLimitEntity
import com.example.data.repository.AppLimitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated engine enforcing App Limit Strict Mode, reminder triggers before limits expire,
 * and discipline streaks tracking per app.
 */
class AppLimitStrictModeEngine private constructor(
    private val context: Context,
    private val appLimitRepository: AppLimitRepository
) {
    private val tag = "AppLimitStrictEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Package → id of the temporary session a reminder was already posted for (issue #54). */
    private val remindedSessions = ConcurrentHashMap<String, String>()

    private val channelId = "app_limit_reminders_channel"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = FocusAlertSoundManager.getNotificationSoundUri(context)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                "App Limit Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you shortly before app limits expire to help you wrap up."
                enableVibration(false)
                vibrationPattern = null
                setSound(soundUri, audioAttributes)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Checks if strict mode is active and locked for the given package.
     */
    suspend fun isAppInStrictMode(packageName: String): Boolean {
        val limit = appLimitRepository.getLimitByPackage(packageName) ?: return false
        return limit.isEnabled && limit.isStrictOverride
    }

    /**
     * Checks if "Turn off" is permitted for this package.
     * In Strict Mode, Turn Off is strictly FORBIDDEN.
     */
    suspend fun canTurnOffLimit(packageName: String): Boolean {
        val limit = appLimitRepository.getLimitByPackage(packageName) ?: return true
        return !limit.isStrictOverride
    }

    /**
     * True when at least one enabled app limit has Strict Mode locked (`isStrictOverride == true`).
     */
    suspend fun hasAnyStrictLimit(): Boolean {
        return try {
            appLimitRepository.getActiveLimits().any { it.isStrictOverride }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when a Focus Session is currently running or paused with Strict Mode enabled.
     */
    fun isStrictFocusSessionActive(): Boolean {
        return try {
            val active = com.example.feature.session.engine.FocusSessionManager.instance.activeSession.value
            active != null && (active.isRunning || active.isPaused) && active.isStrictModeEnabled
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when either a Strict Mode Focus Session is running OR any Strict Mode App Limit is active.
     */
    suspend fun isStrictProtectionLocked(): Boolean {
        return isStrictFocusSessionActive() || hasAnyStrictLimit()
    }

    /**
     * Evaluates whether to show a pre-limit reminder notification (e.g., 1 minute remaining).
     *
     * The verdict itself lives in [AppLimitReminderPolicy]: the reminder belongs to *actual use*,
     * so it is posted only while the session is running and the limited app is the app on screen,
     * and only once per session. The old rule — "whenever under a minute is left, at most once
     * every two minutes" — kept notifying the user about an app they had walked away from,
     * because the session ticker (and therefore this check) keeps running while the session is
     * paused and the parked clock never leaves the window (issue #54).
     */
    fun checkAndSendReminderIfNeeded(
        packageName: String,
        appName: String,
        remainingMillis: Long,
        showRemindersSetting: Boolean,
        sessionId: String,
        isPaused: Boolean,
        isLimitedAppInForeground: Boolean
    ) {
        val shouldRemind = AppLimitReminderPolicy.shouldRemind(
            remainingMillis = remainingMillis,
            isPaused = isPaused,
            isLimitedAppInForeground = isLimitedAppInForeground,
            showRemindersSetting = showRemindersSetting,
            sessionId = sessionId,
            lastRemindedSessionId = remindedSessions[packageName]
        )
        if (!shouldRemind) return

        remindedSessions[packageName] = sessionId
        sendLimitReminderNotification(packageName, appName, (remainingMillis / 1000L).toInt())
    }

    private fun sendLimitReminderNotification(packageName: String, appName: String, remainingSec: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = android.graphics.BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_focus_shield_app_icon
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setLargeIcon(largeIcon)
            .setColor(0xFF6E18E6.toInt())
            .setContentTitle("⏰ $appName Time Almost Up")
            .setContentText("You have less than 1 minute left before your $appName limit is reached. Wrap up your task!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager?.notify(packageName.hashCode() + 1000, notification)
            Log.d(tag, "Sent pre-limit reminder notification for $appName ($remainingSec s left)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send limit reminder", e)
        }
    }

    /**
     * Called when a user gives up/turns off the limit in normal mode after completing
     * the motivational reflection process. Resets discipline streak to 0.
     */
    fun onUserQuitLimit(packageName: String) {
        scope.launch {
            appLimitRepository.resetStreak(packageName)
            Log.i(tag, "Discipline streak reset to 0 for $packageName because user turned off limit.")
        }
    }

    /**
     * Called when a daily cycle completes or a session finishes safely within limit.
     */
    fun onSessionCompletedSafely(packageName: String) {
        scope.launch {
            appLimitRepository.recordDisciplineStreak(packageName)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppLimitStrictModeEngine? = null

        fun initialize(context: Context, repository: AppLimitRepository): AppLimitStrictModeEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = AppLimitStrictModeEngine(context, repository)
                INSTANCE = instance
                instance
            }
        }

        val instance: AppLimitStrictModeEngine
            get() = INSTANCE ?: throw IllegalStateException("AppLimitStrictModeEngine not initialized")
    }
}
