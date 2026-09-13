package com.example.feature.update.notification

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

/**
 * System notification announcing a new FocusShield release (prompt.txt §5).
 *
 * Uses its own channel rather than the block-alert channel: a release announcement is informational
 * and must not inherit the warning buzzer or `IMPORTANCE_HIGH` those alerts rely on. Tapping it opens
 * the update screen directly.
 *
 * The POST_NOTIFICATIONS check mirrors [com.example.core.notification.FocusShieldBlockNotificationHelper]
 * — posting is skipped, never forced, when the user has not granted it.
 */
object UpdateNotificationHelper {

    const val CHANNEL_ID_UPDATES = "focus_shield_updates"
    private const val CHANNEL_NAME_UPDATES = "FocusShield App Updates"
    private const val CHANNEL_DESCRIPTION_UPDATES = "Notifies you when a new version of FocusShield is available"

    private const val NOTIF_ID_UPDATE = 3010

    /** Set on the launch intent so MainActivity can route straight to the update screen. */
    const val EXTRA_OPEN_UPDATES = "com.example.extra.OPEN_UPDATES"

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID_UPDATES,
                CHANNEL_NAME_UPDATES,
                // Default importance: worth a glance in the shade, not worth interrupting study.
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION_UPDATES
                setShowBadge(true)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * Posts the "update available" notification for [versionName].
     *
     * Callers are responsible for only invoking this once per version (see
     * [com.example.feature.update.data.UpdateChecker.shouldNotify]); a stable notification id means a
     * repeat would replace rather than stack anyway.
     */
    fun notifyUpdateAvailable(context: Context, versionName: String) {
        initialize(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATES, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(context, NOTIF_ID_UPDATE, intent, flags)

        val title = "FocusShield update available"
        val message = "FocusShield v$versionName is now available."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_UPDATE, notification)
        } catch (_: SecurityException) {
        }
    }

    /** Removes the announcement once the update has been installed or marked read (prompt.txt §7). */
    fun clear(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIF_ID_UPDATE)
        } catch (_: SecurityException) {
        }
    }
}
