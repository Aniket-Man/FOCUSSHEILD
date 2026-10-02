package com.example.feature.youtube

import com.example.feature.youtube.engine.YouTubeContentBlockEngine
import com.example.feature.youtube.engine.YouTubeNodeExtractor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeContentBlockEngineTest {

    @After
    fun tearDown() {
        YouTubeContentBlockEngine.reset()
    }

    @Test
    fun testApprovedChannelMatches() {
        val approvedIds = setOf("physicswallah", "@khanacademy")
        val approvedNames = setOf("Physics Wallah", "Khan Academy")

        assertTrue(YouTubeContentBlockEngine.isChannelApproved("Physics Wallah", approvedIds, approvedNames))
        assertTrue(YouTubeContentBlockEngine.isChannelApproved("@physicswallah", approvedIds, approvedNames))
        assertTrue(YouTubeContentBlockEngine.isChannelApproved("Khan Academy", approvedIds, approvedNames))
        assertTrue(YouTubeContentBlockEngine.isChannelApproved("@khanacademy", approvedIds, approvedNames))
    }

    @Test
    fun testUnlistedChannelRejected() {
        val approvedIds = setOf("physicswallah", "@khanacademy")
        val approvedNames = setOf("Physics Wallah", "Khan Academy")

        // Unlisted channels not in the approved set must be rejected
        assertFalse(YouTubeContentBlockEngine.isChannelApproved("Unlisted Private Channel", approvedIds, approvedNames))
        assertFalse(YouTubeContentBlockEngine.isChannelApproved("@UnlistedCreator", approvedIds, approvedNames))
        assertFalse(YouTubeContentBlockEngine.isChannelApproved("Gaming Hub 101", approvedIds, approvedNames))
        assertFalse(YouTubeContentBlockEngine.isContentApproved("Important lecture", "Unlisted Private Channel", approvedIds, approvedNames))
    }

    @Test
    fun testChannelCleaning() {
        assertEquals("Physics Wallah", YouTubeNodeExtractor.cleanChannelName("Subscribe to Physics Wallah."))
        assertEquals("Physics Wallah", YouTubeNodeExtractor.cleanChannelName("Subscribed to Physics Wallah"))
        assertEquals("PhysicsWallah", YouTubeNodeExtractor.cleanChannelName("@PhysicsWallah • 1.2M views"))
        assertEquals("Khan Academy", YouTubeNodeExtractor.cleanChannelName("By Khan Academy"))
        assertEquals("MyChannel", YouTubeNodeExtractor.cleanChannelName("MyChannel."))
    }
}
