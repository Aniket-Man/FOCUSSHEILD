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
    private val remindedSessions = ConcurrentHashMap<String, Long>()

    private val channelId = "app_limit_reminders_channel"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Limit Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you shortly before app limits expire to help you wrap up."
                enableVibration(true)
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
     * Evaluates whether to show a pre-limit reminder notification (e.g., 1 minute remaining).
     */
    fun checkAndSendReminderIfNeeded(
        packageName: String,
        appName: String,
        remainingMillis: Long,
        showRemindersSetting: Boolean
    ) {
        if (!showRemindersSetting) return
        val remainingSeconds = remainingMillis / 1000L

        // Trigger reminder if between 30s and 60s remaining and hasn't been notified yet for this session window
        if (remainingSeconds in 1..60) {
            val lastReminded = remindedSessions[packageName] ?: 0L
            val now = System.currentTimeMillis()
            if (now - lastReminded > 120_000L) { // Only once every 2 mins
                remindedSessions[packageName] = now
                sendLimitReminderNotification(packageName, appName, remainingSeconds.toInt())
            }
        }
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
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
