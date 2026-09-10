package com.example.feature.youtube.engine

import android.util.Log
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import com.example.data.repository.AnalyticsRepository
import com.example.data.repository.BlockedAttemptRepository
import com.example.feature.session.domain.FocusSession
import com.example.feature.session.engine.FocusSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Turns "the user is watching an approved YouTube video during a focus session" into records.
 *
 * YouTube is the one place a focus session runs *inside* another app, so the study time it earns is
 * not captured by `session_records` — that table only knows the session ran, not how much of it was
 * spent on approved content. Each uninterrupted stay on an approved channel becomes one
 * `study_activities` row (`source = YOUTUBE`), which is what every YouTube figure in the analytics
 * screen reads. Without this, those figures are permanently zero.
 *
 * **Why a segment and not a tick.** The accessibility service re-evaluates the same screen on every
 * window/content event, many times a second. A row per evaluation would be both meaningless and
 * enormous, so the tracker holds one open segment and only closes it when something actually
 * happened: the content changed, the verdict stopped being `ALLOW`, YouTube was left, the session
 * ended, or the screen went off. Repeated evaluations of an unchanged screen are a no-op.
 *
 * **What bounds a segment.** There is no playback-state signal available to the app, so a segment
 * cannot end when playback pauses — only when the *context* changes. That is why the screen-off
 * receiver in `FocusAccessibilityService` matters: without it, a video left on a locked screen would
 * keep accruing wall-clock time until the next accessibility event. Segments shorter than
 * [minimumSegmentMillis] are dropped so a momentary flash of the player is not a study session.
 *
 * **Known limitation.** `YouTubeBlockDecision.approvedChannel()` carries the channel but no title,
 * so switching between videos of the same approved channel is invisible here and reads as one
 * continuous segment. Capping the segment length artificially would under-report genuine long study
 * sessions, so the limitation is left visible rather than papered over. Anything more precise needs
 * a playback signal the app does not currently observe.
 *
 * State is mutated only from the main thread (accessibility callbacks and the screen receiver), but
 * the methods are synchronised anyway so a call from another thread cannot interleave a
 * close-with-start and duplicate or drop a segment.
 */
class YouTubeStudyDwellTracker private constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val blockedAttemptRepository: BlockedAttemptRepository
) {
    private val tag = "YouTubeDwellTracker"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One uninterrupted stay on a piece of approved content. */
    private data class Segment(
        val key: String,
        val sessionId: String,
        val subject: String,
        val topic: String,
        val channelName: String?,
        val videoTitle: String?,
        val startedAt: Long
    )

    private var active: Segment? = null
    private var screenInteractive = true

    /**
     * Called for every `ALLOW` verdict — i.e. approved content is on screen during a running session.
     *
     * A repeat call for the same content is a no-op; a call for different content closes the previous
     * segment first, so the emitted rows always partition the time rather than overlapping.
     */
    @Synchronized
    fun onAllowedContent(
        channelName: String?,
        videoTitle: String?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!screenInteractive) return

        val session = currentSession()
        val key = "${session?.id.orEmpty()}|${channelName.orEmpty()}|${videoTitle.orEmpty()}"

        if (active?.key == key) return

        closeActive(timestamp, "content changed")
        if (session == null) return

        active = Segment(
            key = key,
            sessionId = session.id,
            subject = session.subject,
            topic = session.topic,
            channelName = channelName,
            videoTitle = videoTitle,
            startedAt = timestamp
        )

        // One telemetry event per approved-content episode — this is a genuine rule decision (Study
        // Mode verified the channel), not a playback tick, so it belongs in the protection history.
        // It is emitted once per segment rather than once per evaluation to keep the table readable.
        enqueueAllowedEvent(channelName, videoTitle, session, timestamp)
    }

    /** Closes any open segment. Safe to call on every hook; a call with nothing open does nothing. */
    @Synchronized
    fun flush(reason: String) {
        closeActive(System.currentTimeMillis(), reason)
    }

    /**
     * Screen off. The open segment is closed at this instant rather than allowed to run on, because
     * nothing observed until the screen comes back can distinguish "watched" from "abandoned".
     */
    @Synchronized
    fun onScreenOff(timestamp: Long = System.currentTimeMillis()) {
        screenInteractive = false
        closeActive(timestamp, "screen off")
    }

    @Synchronized
    fun onScreenOn() {
        screenInteractive = true
    }

    private fun closeActive(timestamp: Long, reason: String) {
        val segment = active ?: return
        active = null

        val durationMillis = timestamp - segment.startedAt
        if (durationMillis < minimumSegmentMillis) {
            // Too brief to be deliberate study — a flash of the player, a scroll past, a mis-tap.
            return
        }

        scope.launch {
            try {
                analyticsRepository.recordYouTubeStudyActivity(
                    sessionId = segment.sessionId,
                    subject = segment.subject,
                    topic = segment.topic,
                    channelName = segment.channelName,
                    channelId = null,
                    videoTitle = segment.videoTitle,
                    durationMillis = durationMillis,
                    startedAt = segment.startedAt,
                    endedAt = timestamp
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to record YouTube study segment ($reason): ${e.message}")
            }
        }
    }

    private fun enqueueAllowedEvent(
        channelName: String?,
        videoTitle: String?,
        session: FocusSession,
        timestamp: Long
    ) {
        scope.launch {
            try {
                blockedAttemptRepository.recordAttempt(
                    packageName = YOUTUBE_PACKAGE,
                    appName = channelName ?: "Approved Channel",
                    eventType = BlockedEventType.YOUTUBE_ALLOWED,
                    source = BlockedEventSource.ACCESSIBILITY_SERVICE,
                    sessionId = session.id,
                    channelName = channelName,
                    videoTitle = videoTitle,
                    ruleRef = "STUDY_MODE_APPROVED_CHANNEL",
                    subject = session.subject,
                    topic = session.topic,
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to record YOUTUBE_ALLOWED event: ${e.message}")
            }
        }
    }

    /**
     * The session a segment belongs to, captured when the segment *opens*.
     *
     * Reading it at close time would be wrong: the session is cleared from `activeSession` as it
     * completes, so a segment that ends because the session ended would find nothing to attribute
     * itself to and the watch time would be lost.
     */
    private fun currentSession(): FocusSession? = try {
        FocusSessionManager.instance.activeSession.value
            ?.takeIf { it.isRunning || it.isPaused }
    } catch (_: Exception) {
        null
    }

    companion object {
        /**
         * The phone app is what Study Mode inspects; the TV build has no accessibility service
         * driving it, so events from dwell capture are always attributed to the phone package.
         */
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        /** Segments below this are noise, not study. */
        private const val minimumSegmentMillis = 10_000L

        @Volatile
        private var INSTANCE: YouTubeStudyDwellTracker? = null

        fun initialize(
            analyticsRepository: AnalyticsRepository,
            blockedAttemptRepository: BlockedAttemptRepository
        ): YouTubeStudyDwellTracker {
            return INSTANCE ?: synchronized(this) {
                val instance = YouTubeStudyDwellTracker(analyticsRepository, blockedAttemptRepository)
                INSTANCE = instance
                instance
            }
        }

        val instance: YouTubeStudyDwellTracker
            get() = INSTANCE ?: throw IllegalStateException("YouTubeStudyDwellTracker not initialized")
    }
}
