package com.example.core.notification

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.feature.notificationblocker.engine.NotificationBlockerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Real-time notification interception service.
 * Intercepts, cancels, and silences intrusive notifications from user-selected distracting
 * apps (e.g., social media, gaming, shopping) and during active focus study sessions.
 */
class FocusNotificationListenerService : NotificationListenerService() {

    private val tag = "FocusNotifListener"

    // One supervisor-owned scope for the whole service lifetime: children failures don't cancel
    // siblings, the scope is bounded to a handful of concurrent handlers so a notification storm
    // cannot fan out into hundreds of simultaneous coroutines, and onDestroy() cancels it so no
    // work (or service-context reference) outlives the service (issue #19).
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO.limitedParallelism(4))

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.i(tag, "FocusNotificationListenerService successfully connected and listening.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.i(tag, "FocusNotificationListenerService disconnected.")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        serviceScope.launch {
            try {
                val engine = try {
                    NotificationBlockerEngine.instance
                } catch (e: Exception) {
                    null
                }

                if (engine != null) {
                    engine.processIncomingNotification(sbn) {
                        try {
                            cancelNotification(sbn.key)
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to cancel notification with key ${sbn.key}: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error processing incoming notification in listener: ${e.message}", e)
            }
        }
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        /**
         * Checks whether notification listener access is enabled for this app.
         */
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!flat.isNullOrEmpty()) {
                val names = flat.split(":")
                for (name in names) {
                    val componentName = android.content.ComponentName.unflattenFromString(name)
                    if (componentName != null && componentName.packageName == packageName) {
                        return true
                    }
                }
            }
            return false
        }

        /**
         * Opens the system Notification Listener Settings screen so user can grant permission.
         */
        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            }
        }
    }
}
