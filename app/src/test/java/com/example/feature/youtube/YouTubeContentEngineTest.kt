package com.example.feature.youtube

import com.example.feature.youtube.engine.ChannelResolution
import com.example.feature.youtube.engine.YouTubeContentEngine
import com.example.feature.youtube.engine.YouTubeContentState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeContentEngineTest {

    @After
    fun resetEngine() {
        YouTubeContentEngine.reset()
    }

    @Test
    fun visibleVideoThatHasNotStartedPlayingIsOnlyAvailable() {
        val result = YouTubeContentEngine.observe(
            videoKey = "Physics lesson",
            isPlayerVisible = true,
            isPlaybackActive = false,
            isChannelIdentified = true,
            isChannelApproved = false,
            now = 1_000L
        )

        assertEquals(YouTubeContentState.VIDEO_AVAILABLE, result.state)
        assertEquals(ChannelResolution.NOT_PLAYING, result.channelResolution)
    }

    @Test
    fun playingVideoWaitsBrieflyForChannelMetadataThenExpires() {
        val firstObservation = YouTubeContentEngine.observe(
            videoKey = "Physics lesson",
            isPlayerVisible = true,
            isPlaybackActive = true,
            isChannelIdentified = false,
            isChannelApproved = false,
            now = 1_000L
        )
        val expiredObservation = YouTubeContentEngine.observe(
            videoKey = "Physics lesson",
            isPlayerVisible = true,
            isPlaybackActive = true,
            isChannelIdentified = false,
            isChannelApproved = false,
            now = 1_000L + YouTubeContentEngine.CHANNEL_METADATA_GRACE_MILLIS
        )

        assertEquals(ChannelResolution.WAITING_FOR_CHANNEL, firstObservation.channelResolution)
        assertEquals(ChannelResolution.UNRESOLVED, expiredObservation.channelResolution)
    }

    @Test
    fun playingVideoIsClassifiedOnlyFromItsOwnResolvedChannel() {
        val approved = YouTubeContentEngine.observe(
            videoKey = "Algebra",
            isPlayerVisible = true,
            isPlaybackActive = true,
            isChannelIdentified = true,
            isChannelApproved = true,
            now = 1_000L
        )
        val unapproved = YouTubeContentEngine.observe(
            videoKey = "Gaming video",
            isPlayerVisible = true,
            isPlaybackActive = true,
            isChannelIdentified = true,
            isChannelApproved = false,
            now = 2_000L
        )

        assertEquals(ChannelResolution.APPROVED, approved.channelResolution)
        assertEquals(ChannelResolution.UNAPPROVED, unapproved.channelResolution)
    }
}
