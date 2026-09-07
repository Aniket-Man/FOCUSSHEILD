package com.example.feature.youtube.engine

/**
 * Playback-first state machine for YouTube Study Mode.
 *
 * A visible watch page is deliberately not enough to produce a content decision.  The
 * accessibility parser feeds this engine only after it has found an actual player, and this
 * engine waits for a positive playing signal before asking the policy to allow or block a
 * channel.  Keeping the state here (rather than in a particular YouTube screen parser) makes
 * dynamic Home, Search, history, playlist, and in-app navigation behave the same way.
 */
enum class YouTubeContentState {
    IDLE,
    VIDEO_AVAILABLE,
    IDENTIFYING_CHANNEL,
    APPROVED,
    UNAPPROVED,
    BLOCKING,
    RETURNING
}

enum class ChannelResolution {
    NOT_PLAYING,
    WAITING_FOR_CHANNEL,
    APPROVED,
    UNAPPROVED,
    UNRESOLVED
}

data class YouTubeContentSnapshot(
    val state: YouTubeContentState = YouTubeContentState.IDLE,
    val videoKey: String? = null,
    val playbackStartedAt: Long = 0L,
    val channelResolution: ChannelResolution = ChannelResolution.NOT_PLAYING
)

/**
 * Centralizes the short metadata grace period and the single blocked-playback lifecycle.
 * This is deliberately Android-free so it can be verified without an accessibility tree.
 */
object YouTubeContentEngine {

    const val CHANNEL_METADATA_GRACE_MILLIS: Long = 1_800L

    @Volatile
    private var snapshot = YouTubeContentSnapshot()

    /**
     * Evaluates one observation of the active player.  `isPlaybackActive` must only be true
     * when the UI exposes a positive playback signal (for example, an enabled Pause control),
     * never just because a thumbnail or watch page is visible.
     */
    @Synchronized
    fun observe(
        videoKey: String?,
        isPlayerVisible: Boolean,
        isPlaybackActive: Boolean,
        isChannelIdentified: Boolean,
        isChannelApproved: Boolean,
        now: Long = System.currentTimeMillis()
    ): YouTubeContentSnapshot {
        if (!isPlayerVisible) {
            snapshot = YouTubeContentSnapshot()
            return snapshot
        }

        val normalizedKey = videoKey?.trim()?.takeIf { it.isNotEmpty() } ?: "unidentified-player"
        if (!isPlaybackActive) {
            snapshot = YouTubeContentSnapshot(
                state = YouTubeContentState.VIDEO_AVAILABLE,
                videoKey = normalizedKey,
                channelResolution = ChannelResolution.NOT_PLAYING
            )
            return snapshot
        }

        // A different title (or a player that was not playing on the prior observation) is a
        // new playback attempt and gets its own metadata window.
        val isNewPlayback = snapshot.videoKey != normalizedKey ||
            snapshot.state == YouTubeContentState.IDLE ||
            snapshot.state == YouTubeContentState.VIDEO_AVAILABLE ||
            snapshot.state == YouTubeContentState.RETURNING
        val playbackStartedAt = if (isNewPlayback) now else snapshot.playbackStartedAt

        val resolution = when {
            isChannelIdentified && isChannelApproved -> ChannelResolution.APPROVED
            isChannelIdentified -> ChannelResolution.UNAPPROVED
            now - playbackStartedAt < CHANNEL_METADATA_GRACE_MILLIS -> ChannelResolution.WAITING_FOR_CHANNEL
            else -> ChannelResolution.UNRESOLVED
        }
        val state = when (resolution) {
            ChannelResolution.APPROVED -> YouTubeContentState.APPROVED
            ChannelResolution.UNAPPROVED -> YouTubeContentState.UNAPPROVED
            ChannelResolution.WAITING_FOR_CHANNEL,
            ChannelResolution.UNRESOLVED -> YouTubeContentState.IDENTIFYING_CHANNEL
            ChannelResolution.NOT_PLAYING -> YouTubeContentState.VIDEO_AVAILABLE
        }

        snapshot = YouTubeContentSnapshot(
            state = state,
            videoKey = normalizedKey,
            playbackStartedAt = playbackStartedAt,
            channelResolution = resolution
        )
        return snapshot
    }

    /** Marks a single detected violation before pause/back enforcement begins. */
    @Synchronized
    fun markBlocking(now: Long = System.currentTimeMillis()) {
        snapshot = snapshot.copy(
            state = YouTubeContentState.BLOCKING,
            playbackStartedAt = if (snapshot.playbackStartedAt == 0L) now else snapshot.playbackStartedAt
        )
    }

    /** Called after the one allowed back-navigation action has been sent. */
    @Synchronized
    fun markReturned() {
        snapshot = YouTubeContentSnapshot(state = YouTubeContentState.RETURNING)
    }

    @Synchronized
    fun reset() {
        snapshot = YouTubeContentSnapshot()
    }

    fun currentSnapshot(): YouTubeContentSnapshot = snapshot
}
