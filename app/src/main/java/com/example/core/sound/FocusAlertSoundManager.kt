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
    private var lastBuzzTime: Long = 0L
    private var lastChimeTime: Long = 0L

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
        val now = System.currentTimeMillis()
        if (now - lastChimeTime < 800L) return
        lastChimeTime = now

        playSound(context, "chime")
    }

    /**
     * Triggers notification sound when YouTube Shorts, Reels, adult sites, or unapproved channels are blocked.
     * Audio only — vibration removed.
     */
    fun playWarningBuzzer(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastBuzzTime < 500L) return
        lastBuzzTime = now

        playSound(context, "buzzer")
    }

    private fun playSound(context: Context, label: String) {
        var mediaPlayer: MediaPlayer? = null
        try {
            mediaPlayer = MediaPlayer.create(context.applicationContext, R.raw.mixkit_correct_answer_tone)
            if (mediaPlayer == null) {
                Log.w(TAG, "MediaPlayer.create returned null for $label")
                return
            }
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
            mediaPlayer.setOnCompletionListener { mp ->
                try { mp.release() } catch (_: Exception) {}
            }
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                try { mp.release() } catch (_: Exception) {}
                true
            }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing notification $label sound", e)
            try { mediaPlayer?.release() } catch (_: Exception) {}
        }
    }
}
