package com.example.core.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.FocusShieldApp

/**
 * Counts phone unlocks by listening for ACTION_USER_PRESENT (keyguard dismissed).
 *
 * ACTION_USER_PRESENT cannot be declared in the manifest on modern Android, so the
 * receiver must be registered at runtime. The [register]/[unregister] lifecycle is owned
 * by [com.example.core.accessibility.FocusAccessibilityService] — the app's always-alive
 * component whenever blocking is active.
 *
 * A debounce window prevents a single physical unlock (which can fire several rapid
 * screen/keyguard transitions) from being counted multiple times.
 */
object PhoneUnlockTracker {

    private const val TAG = "PhoneUnlockTracker"

    /** Minimum gap between two distinct unlock events. */
    private const val UNLOCK_DEBOUNCE_MILLIS = 3_000L

    @Volatile
    private var lastUnlockTimestamp = 0L

    private var receiverRegistered = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                onPhoneUnlocked()
            }
        }
    }

    fun register(context: Context) {
        if (receiverRegistered) return
        try {
            context.registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
            receiverRegistered = true
            Log.d(TAG, "Unlock tracking registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register unlock receiver", e)
        }
    }

    fun unregister(context: Context) {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(unlockReceiver)
        } catch (_: Exception) {
        }
        receiverRegistered = false
    }

    /**
     * Called on every keyguard dismissal. Debounced, then persisted via the repository.
     */
    fun onPhoneUnlocked(timestamp: Long = System.currentTimeMillis()) {
        if (timestamp - lastUnlockTimestamp < UNLOCK_DEBOUNCE_MILLIS) {
            return
        }
        lastUnlockTimestamp = timestamp

        try {
            val app = FocusShieldApp.instance
            app.dailyUnlockRepository.recordUnlockAsync(timestamp)
            app.refreshWidgets()
        } catch (_: UninitializedPropertyAccessException) {
        }
    }
}
