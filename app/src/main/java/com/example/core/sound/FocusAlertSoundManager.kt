package com.example.core.sound

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.R

/**
 * Handles custom notification sound playback and haptic vibrations whenever a distraction,
 * YouTube Short, Facebook/Instagram Reel, unapproved channel, or adult/blocked site is intercepted,
 * as well as for focus session phase transitions.
 */
object FocusAlertSoundManager {

    private const val TAG = "FocusAlertSound"
    private var lastBuzzTime: Long = 0L
    private var lastChimeTime: Long = 0L

    fun getNotificationSoundUri(context: Context): Uri {
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/focus_notification_chime")
    }

    fun init(context: Context) {
        // Sound initialization ready
    }

    /**
     * Plays the custom notification chime tone along with gentle vibration.
     */
    fun playSessionChime(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastChimeTime < 800L) return
        lastChimeTime = now

        // Play custom notification sound
        try {
            val mediaPlayer = MediaPlayer.create(context.applicationContext, R.raw.focus_notification_chime)
            mediaPlayer?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing notification chime sound", e)
        }

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

    /**
     * Triggers alert vibration when YouTube Shorts, Reels, adult sites, or unapproved channels are blocked.
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
}
