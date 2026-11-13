package com.example.feature.youtube.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.core.accessibility.FocusAccessibilityService
import com.example.core.notification.FocusShieldBlockNotificationHelper
import com.example.core.overlay.FocusDisplayOverlayNotificationManager
import com.example.feature.youtube.ai.YouTubeAiStudyClassifier

/**
 * Handles the notification / HUD "Allow Anyway" action for a blocked YouTube channel,
 * granting a temporary bypass scoped strictly to the active Focus Session.
 */
class YouTubeAllowAnywayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)?.trim().orEmpty()
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)?.trim()?.takeIf { it.isNotBlank() }
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)?.trim()?.takeIf { it.isNotBlank() }

        if (channelName.isBlank() && channelId.isNullOrBlank()) return

        YouTubeAiStudyClassifier.allowChannelForCurrentSession(
            channelId = channelId,
            channelName = channelName.takeIf { it.isNotBlank() },
            sessionId = sessionId
        )

        FocusAccessibilityService.instance?.clearLastBlockedYouTubeVideoKey()
        FocusDisplayOverlayNotificationManager.dismissHud()
        FocusShieldBlockNotificationHelper.cancelAiBlockedNotification(context)

        val displayLabel = channelName.ifBlank { channelId ?: "Channel" }
        Toast.makeText(
            context.applicationContext,
            "Allowed $displayLabel for this focus session",
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        const val ACTION_ALLOW_ANYWAY = "com.example.focusshield.ACTION_YOUTUBE_ALLOW_ANYWAY"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
