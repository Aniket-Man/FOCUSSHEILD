package com.example.core.sound

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Handles haptic vibration alerts (sound removed per user preference) whenever a distraction,
 * YouTube Short, Facebook/Instagram Reel, unapproved channel, or adult/blocked site is intercepted,
 * as well as for focus session phase transitions.
 */
object FocusAlertSoundManager {

    private const val TAG = "FocusAlertSound"
    private var lastBuzzTime: Long = 0L
    private var lastChimeTime: Long = 0L

    fun init(context: Context) {
        // Sound initialization disabled per user preference (vibration only)
    }

    /**
     * Triggers alert vibration (no sound) when YouTube Shorts, Reels, adult sites, or unapproved channels are blocked.
     */
    fun playWarningBuzzer(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastBuzzTime < 500L) return
        lastBuzzTime = now

        // Assertive haptic vibration for block interception
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 150, 70, 180, 70, 250)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 150, 70, 180, 70, 250), -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering alert vibration", e)
        }
    }

    /**
     * Triggers gentle vibration (no sound) for session start/completion, Pomodoro transitions, and Planner schedule reminders.
     */
    fun playSessionChime(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastChimeTime < 800L) return
        lastChimeTime = now

        // Gentle notification haptics
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 150), intArrayOf(0, 180, 0, 220), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 100, 80, 150), -1)
                }
            }
        } catch (_: Exception) {}
    }
}
