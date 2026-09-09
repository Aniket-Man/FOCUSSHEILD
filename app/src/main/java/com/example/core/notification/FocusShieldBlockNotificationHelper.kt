package com.example.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.sound.FocusAlertSoundManager

/**
 * Manages rich Android system notifications posted when real-time protection features trigger:
 * - YouTube Shorts blocked
 * - Facebook / Instagram Reels blocked
 * - Unapproved YouTube Channel blocked
 * - Adult 18+ website blocked
 * - Custom distracting website blocked
 */
object FocusShieldBlockNotificationHelper {

    const val CHANNEL_ID_BLOCKS = "focus_shield_blocks_v3"
    private const val CHANNEL_NAME_BLOCKS = "FocusShield Warning Alerts"
    private const val CHANNEL_DESCRIPTION_BLOCKS = "Haptic alerts and notifications when distraction content, Shorts, Reels, or adult sites are blocked"

    private const val NOTIF_ID_SHORTS = 3001
    private const val NOTIF_ID_CHANNEL = 3002
    private const val NOTIF_ID_ADULT = 3003
    private const val NOTIF_ID_CUSTOM_SITE = 3004
    private const val NOTIF_ID_REELS = 3005
    private const val NOTIF_ID_SPLIT_SCREEN = 3006
    private const val NOTIF_ID_FLOATING_WINDOW = 3007

    fun initialize(context: Context) {
        FocusAlertSoundManager.init(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val soundUri = FocusAlertSoundManager.getNotificationSoundUri(context)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID_BLOCKS,
                CHANNEL_NAME_BLOCKS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION_BLOCKS
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 70, 180, 70, 250)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(channel)
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
     * System notification for YouTube Shorts block.
     */
    fun notifyShortsBlocked(context: Context) {
        initialize(context)
        FocusAlertSoundManager.playWarningBuzzer(context)
        if (!hasNotificationPermission(context)) return

        val title = "⚡ YouTube Shorts Blocked!"
        val message = "Shorts are blocked because they're designed to be highly addictive. Redirected safely to YouTube Home!"

        postNotification(
            context = context,
            notificationId = NOTIF_ID_SHORTS,
            title = title,
            message = message
        )
    }

    /**
     * System notification for Facebook / Instagram Reels block.
     */
    fun notifyReelsBlocked(context: Context, appName: String) {
        initialize(context)
        FocusAlertSoundManager.playWarningBuzzer(context)
        if (!hasNotificationPermission(context)) return

        val title = "⚡ $appName Reels Blocked!"
        val message = "Short-form video feeds are blocked to protect your attention span. Returned to Home!"

        postNotification(
            context = context,
            notificationId = NOTIF_ID_REELS,
            title = title,
            message = message
        )
    }

    /**
     * System notification for unapproved YouTube channel.
     */
    fun notifyChannelBlocked(context: Context, channelName: String) {
        initialize(context)
        FocusAlertSoundManager.playWarningBuzzer(context)
        if (!hasNotificationPermission(context)) return

        val cleanName = if (channelName.isNotBlank()) channelName else "This channel"
        val title = "🎯 Channel Not in Study List"
        val message = "'$cleanName' is not in your allowed YouTube study channels. Returned to Home to keep your streak!"

        postNotification(
            context = context,
            notificationId = NOTIF_ID_CHANNEL,
            title = title,
            message = message
        )
    }

    /**
     * System notification for adult website block in Chrome/browser.
     */
    fun notifyAdultSiteBlocked(context: Context, domain: String) {
        initialize(context)
        FocusAlertSoundManager.playWarningBuzzer(context)
        if (!hasNotificationPermission(context)) return

        val title = "🔞 18+ Sites are Blocked!"
        val message = "Adult content on '$domain' was blocked automatically. Stepped down to search."

        postNotification(
            context = context,
            notificationId = NOTIF_ID_ADULT,
            title = title,
            message = message
        )
    }

    /**
     * System notification for custom distracting website block.
     */
    fun notifyCustomWebsiteBlocked(context: Context, domain: String) {
        initialize(context)
        FocusAlertSoundManager.playWarningBuzzer(context)
        if (!hasNotificationPermission(context)) return

        val title = "🌐 Website '$domain' Blocked!"
        val message = "Site domain '$domain' is in your blocked list. Stepped down to search."

        postNotification(
            context = context,
            notificationId = NOTIF_ID_CUSTOM_SITE,
            title = title,
            message = message
        )
    }

    /**
     * System notification for split-screen block.
     */
    fun notifySplitScreenBlocked(context: Context) {
        initialize(context)
        FocusAlertSoundManager.playWarningBuzzer(context)
        if (!hasNotificationPermission(context)) return

        val title = "🚫 Split-Screen Blocked!"
        val message = "Multi-window split-screen is disabled during focus sessions."

        postNotification(
            context = context,
            notificationId = NOTIF_ID_SPLIT_SCREEN,
            title = title,
            message = message
        )
    }

    /**
     * System notification for floating window / PiP block.
     */
    fun notifyFloatingWindowBlocked(context: Context) {
        initialize(context)
        FocusAlertSoundManager.playWarningBuzzer(context)
        if (!hasNotificationPermission(context)) return

        val title = "🚫 Floating Window Blocked!"
        val message = "Picture-in-Picture and floating windows are disabled during focus sessions."

        postNotification(
            context = context,
            notificationId = NOTIF_ID_FLOATING_WINDOW,
            title = title,
            message = message
        )
    }

    private fun postNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val contentIntent = createContentIntent(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BLOCKS)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(FocusAlertSoundManager.getNotificationSoundUri(context))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setVibrate(longArrayOf(0, 150, 70, 180, 70, 250))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }
}
