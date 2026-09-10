package com.example.core.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log

/**
 * Reports screen on/off transitions to [com.example.feature.youtube.engine.YouTubeStudyDwellTracker].
 *
 * Watch-time capture measures how long approved YouTube content was on screen, and nothing else in
 * the app observes the screen state — accessibility events simply stop arriving once it goes dark.
 * Without this, a video left playing on a locked phone would keep accruing time until the user came
 * back and produced the next event, inflating the figure by however long the phone sat there.
 *
 * ACTION_SCREEN_ON/OFF cannot be declared in the manifest, so the receiver is registered at runtime.
 * Its lifecycle is owned by [com.example.core.accessibility.FocusAccessibilityService] — the app's
 * always-alive component whenever blocking is active — alongside `PhoneUnlockTracker`.
 */
object ScreenStateTracker {

    private const val TAG = "ScreenStateTracker"

    private var receiverRegistered = false

    @Volatile
    private var interactive = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    interactive = false
                    dwellTracker()?.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    fun register(context: Context) {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            context.registerReceiver(screenReceiver, filter)
            receiverRegistered = true

            // Seed from the authoritative source: the service can be (re)connected while the screen
            // is already off, in which case neither broadcast will arrive until the user wakes it.
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            interactive = powerManager?.isInteractive ?: true
            Log.d(TAG, "Screen state tracking registered (interactive=$interactive)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screen state receiver", e)
        }
    }

    fun unregister(context: Context) {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        receiverRegistered = false
    }

    /** The screen may already be on when the user unlocks from a dark screen. */
    fun onScreenOn() {
        interactive = true
        dwellTracker()?.onScreenOn()
    }

    private fun dwellTracker(): com.example.feature.youtube.engine.YouTubeStudyDwellTracker? = try {
        com.example.feature.youtube.engine.YouTubeStudyDwellTracker.instance
    } catch (_: Exception) {
        null
    }
}
