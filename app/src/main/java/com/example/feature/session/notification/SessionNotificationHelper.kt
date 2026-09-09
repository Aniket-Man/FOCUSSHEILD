package com.example.feature.session.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.sound.FocusAlertSoundManager
import com.example.feature.session.domain.FocusSession
import com.example.feature.session.domain.PomodoroPhase

/**
 * Helper object managing system notifications and alerts for FocusShield study sessions,
 * including session starts, timer completion, Pomodoro transitions, breaks, and study planner schedule reminders.
 */
object SessionNotificationHelper {

    const val CHANNEL_ID = "focus_session_alerts_v3"
    private const val CHANNEL_NAME = "Focus Session Alerts"
    private const val CHANNEL_DESCRIPTION = "Vibration alerts for Session start/end, Timer completions, and Pomodoro transitions"

    const val CHANNEL_ID_STUDY_PLAN = "study_plan_reminders_v3"
    private const val CHANNEL_NAME_STUDY_PLAN = "Study Plan Reminders"
    private const val CHANNEL_DESCRIPTION_STUDY_PLAN = "Vibration notifications for scheduled study sessions and subject timing"

    private const val NOTIFICATION_ID_START = 1000
    private const val NOTIFICATION_ID_TIMER = 1001
    private const val NOTIFICATION_ID_POMODORO = 1002
    private const val NOTIFICATION_ID_BREAK = 1003
    private const val NOTIFICATION_ID_PLAN_BASE = 2000

    fun initialize(context: Context) {
        FocusAlertSoundManager.init(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val soundUri = FocusAlertSoundManager.getNotificationSoundUri(context)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // Session alerts channel
            val sessionChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 180)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(sessionChannel)

            // Study plan timing reminder channel
            val planChannel = NotificationChannel(
                CHANNEL_ID_STUDY_PLAN,
                CHANNEL_NAME_STUDY_PLAN,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION_STUDY_PLAN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(planChannel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun createContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /**
     * Alerts the user when a Focus Session starts.
     */
    fun notifySessionStarted(
        context: Context,
        subject: String,
        topic: String,
        durationMinutes: Int
    ) {
        initialize(context)
        FocusAlertSoundManager.playSessionChime(context)
        if (!hasNotificationPermission(context)) return

        val contentIntent = createContentIntent(context)
        val title = "🚀 Focus Session Started!"
        val message = "Good luck! Shield active for $subject ($topic) for $durationMinutes minutes. You got this!"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setSound(null)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setVibrate(longArrayOf(0, 150, 80, 180))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_START, notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Alerts the user when a standard Timer focus session is completed.
     */
    fun notifyTimerCompleted(
        context: Context,
        subject: String,
        topic: String,
        durationMinutes: Int
    ) {
        initialize(context)
        FocusAlertSoundManager.playSessionChime(context)
        if (!hasNotificationPermission(context)) return

        val contentIntent = createContentIntent(context)
        val title = "🎉 Focus Session Complete!"
        val message = "Great job! You finished your $durationMinutes-minute focus session for $subject ($topic)."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(null)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TIMER, notification)
        } catch (e: SecurityException) {
            // Permission revoked concurrently
        }
    }

    /**
     * Alerts the user when a Pomodoro focus phase, break phase, or full cycle set is completed.
     */
    fun notifyPomodoroPhaseTransition(
        context: Context,
        completedPhase: PomodoroPhase,
        newPhase: PomodoroPhase?,
        cycle: Int,
        totalCycles: Int,
        isFullComplete: Boolean,
        subject: String
    ) {
        initialize(context)
        FocusAlertSoundManager.playSessionChime(context)
        if (!hasNotificationPermission(context)) return

        val contentIntent = createContentIntent(context)

        val (title, message) = when {
            isFullComplete -> {
                "🏆 Pomodoro Goal Completed!" to
                        "Outstanding dedication! You conquered all $totalCycles Pomodoro cycles for $subject."
            }
            completedPhase == PomodoroPhase.FOCUS -> {
                val breakType = if (newPhase == PomodoroPhase.LONG_BREAK) "Long Break" else "Short Break"
                "☕ Focus Time Finished! ($breakType)" to
                        "Cycle $cycle of $totalCycles focus completed. Take a well-deserved $breakType."
            }
            else -> {
                "⚡ Break Finished • Ready to Focus!" to
                        "Time to get back in the zone for Cycle $cycle of $totalCycles ($subject)."
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(null)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_POMODORO, notification)
        } catch (e: SecurityException) {
            // Permission revoked concurrently
        }
    }

    /**
     * Alerts the user when a manual study break expires.
     */
    fun notifyBreakExpired(context: Context) {
        initialize(context)
        FocusAlertSoundManager.playSessionChime(context)
        if (!hasNotificationPermission(context)) return

        val contentIntent = createContentIntent(context)
        val title = "⏰ Study Break Expired"
        val message = "Your break is complete. Focus Shield is now actively protecting your study session."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(null)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BREAK, notification)
        } catch (e: SecurityException) {
            // Permission revoked concurrently
        }
    }

    /**
     * Sends a rich notification when the scheduled start time for a study plan subject arrives (e.g. Physics, Chemistry, etc.).
     * Tapping the notification brings the user directly into the Focus Session setup / active session.
     */
    fun notifyStudyPlanTiming(
        context: Context,
        planId: String?,
        subject: String,
        topic: String,
        startTime: String,
        endTime: String,
        durationMinutes: Int,
        notes: String
    ) {
        initialize(context)
        FocusAlertSoundManager.playSessionChime(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            action = StudyPlanAlarmScheduler.ACTION_OPEN_PLAN_SESSION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(StudyPlanAlarmScheduler.EXTRA_START_PLAN_SESSION, true)
            putExtra(StudyPlanAlarmScheduler.EXTRA_PLAN_ID, planId)
            putExtra(StudyPlanAlarmScheduler.EXTRA_SUBJECT, subject)
            putExtra(StudyPlanAlarmScheduler.EXTRA_TOPIC, topic)
            putExtra(StudyPlanAlarmScheduler.EXTRA_DURATION_MINUTES, durationMinutes)
            putExtra(StudyPlanAlarmScheduler.EXTRA_START_TIME, startTime)
            putExtra(StudyPlanAlarmScheduler.EXTRA_END_TIME, endTime)
            putExtra(StudyPlanAlarmScheduler.EXTRA_NOTES, notes)
        }

        val requestCode = (planId ?: subject).hashCode()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, flags)

        val title = "📚 Hey, now is the time for $subject!"
        val message = "$topic • $startTime – $endTime ($durationMinutes min). Tap to start your focus session now!"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STUDY_PLAN)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(null)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .addAction(
                R.drawable.ic_focus_notification,
                "Start Session",
                pendingIntent
            )
            .build()

        val notificationId = NOTIFICATION_ID_PLAN_BASE + Math.abs((planId ?: subject).hashCode() % 5000)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission revoked concurrently
        }
    }

    /**
     * Sends notification when an automated recurring focus schedule starts.
     */
    fun showScheduleStartNotification(
        context: Context,
        schedule: com.example.data.local.entity.FocusScheduleEntity
    ) {
        initialize(context)
        FocusAlertSoundManager.playSessionChime(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, schedule.id.hashCode(), intent, flags)

        val autoStartText = if (schedule.isAutoStartSession) "Focus Shield active & session started." else "Tap to begin."
        val title = "⚡ Focus Window Active: ${schedule.title}"
        val message = "${schedule.subjectName} (${schedule.startTime} - ${schedule.endTime}). $autoStartText"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STUDY_PLAN)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setSound(null)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(3000 + Math.abs(schedule.id.hashCode() % 5000), notification)
        } catch (_: SecurityException) {}
    }
}
