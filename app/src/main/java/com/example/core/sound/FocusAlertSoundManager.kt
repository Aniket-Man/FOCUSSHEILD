package com.example.core.sound

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.R

/**
 * Handles custom notification sound playback whenever a distraction,
 * YouTube Short, Facebook/Instagram Reel, unapproved channel, or adult/blocked site is intercepted,
 * as well as for focus session phase transitions.
 * Pure audio notifications with vibration completely removed per design.
 */
object FocusAlertSoundManager {

    private const val TAG = "FocusAlertSound"
    private const val MIN_SOUND_INTERVAL_MS = 2200L
    @Volatile private var lastSoundTime: Long = 0L
    @Volatile private var activeMediaPlayer: MediaPlayer? = null
    private val lock = Any()

    fun getNotificationSoundUri(context: Context): Uri {
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/mixkit_correct_answer_tone")
    }

    fun init(context: Context) {
        // Sound initialization ready
    }

    /**
     * Plays the custom notification chime tone (audio only, no vibration).
     */
    fun playSessionChime(context: Context) {
        playSound(context, "chime")
    }

    /**
     * Triggers notification sound when YouTube Shorts, Reels, adult sites, or unapproved channels are blocked.
     * Audio only — vibration removed.
     */
    fun playWarningBuzzer(context: Context) {
        playSound(context, "buzzer")
    }

    private fun playSound(context: Context, label: String) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (now - lastSoundTime < MIN_SOUND_INTERVAL_MS) {
                Log.d(TAG, "Skipping $label sound: throttled (${now - lastSoundTime}ms since last sound)")
                return
            }

            try {
                if (activeMediaPlayer?.isPlaying == true) {
                    Log.d(TAG, "Skipping $label sound: previous sound is still playing")
                    return
                }
            } catch (_: Exception) {}

            try {
                activeMediaPlayer?.release()
            } catch (_: Exception) {}
            activeMediaPlayer = null

            lastSoundTime = now

            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                val mp = MediaPlayer.create(
                    context.applicationContext,
                    R.raw.mixkit_correct_answer_tone,
                    audioAttributes,
                    0
                )
                if (mp == null) {
                    Log.w(TAG, "MediaPlayer.create returned null for $label")
                    return
                }
                activeMediaPlayer = mp
                mp.setOnCompletionListener { player ->
                    synchronized(lock) {
                        try { player.release() } catch (_: Exception) {}
                        if (activeMediaPlayer === player) {
                            activeMediaPlayer = null
                        }
                    }
                }
                mp.setOnErrorListener { player, _, _ ->
                    synchronized(lock) {
                        try { player.release() } catch (_: Exception) {}
                        if (activeMediaPlayer === player) {
                            activeMediaPlayer = null
                        }
                    }
                    true
                }
                mp.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing notification $label sound", e)
                try { activeMediaPlayer?.release() } catch (_: Exception) {}
                activeMediaPlayer = null
            }
        }
    }
}
