package com.example.core.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MediaPauseHelper {

    private const val TAG = "MediaPauseHelper"

    /**
     * Pauses any active media playback (e.g., YouTube, streaming apps, music players)
     * by requesting transient audio focus and dispatching KEYCODE_MEDIA_PAUSE hardware events.
     */
    fun pauseMedia(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            // 1. Request transient audio focus to instantly pause playback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()

                audioManager.requestAudioFocus(focusRequest)

                // Abandon focus after 350ms so future user actions can play media normally
                CoroutineScope(Dispatchers.IO).launch {
                    delay(350L)
                    try {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    } catch (_: Exception) {}
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            // 2. Dispatch KEYCODE_MEDIA_PAUSE hardware key event
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)

            Log.d(TAG, "Sent media pause signal and transient audio focus successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pause media: ${e.message}")
        }
    }
}
