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

    @Test
    fun testHyphenatedAndNewlyAddedChannels() {
        val approvedIds = setOf("pw-jeewallah", "@pw-jeewallah", "apnacollegeofficial", "@apnacollegeofficial")
        val approvedNames = setOf("PW - JEE Wallah", "Apna College", "pwjeewallah", "apnacollege")

        assertTrue(YouTubeContentBlockEngine.isChannelApproved("PW - JEE Wallah", approvedIds, approvedNames))
        assertTrue(YouTubeContentBlockEngine.isChannelApproved("JEE Wallah", approvedIds, approvedNames))
        assertTrue(YouTubeContentBlockEngine.isChannelApproved("@PW-JEEWallah", approvedIds, approvedNames))
        assertTrue(YouTubeContentBlockEngine.isChannelApproved("Apna College", approvedIds, approvedNames))
        assertTrue(YouTubeContentBlockEngine.isChannelApproved("@ApnaCollegeOfficial", approvedIds, approvedNames))

        // Negative check: unrelated entertainment channel
        assertFalse(YouTubeContentBlockEngine.isChannelApproved("Entertainment Daily", approvedIds, approvedNames))
        assertFalse(YouTubeContentBlockEngine.isChannelApproved("@SonypicturesBrasil", approvedIds, approvedNames))
        assertFalse(YouTubeContentBlockEngine.isChannelApproved("Sony Pictures Brasil", approvedIds, approvedNames))
    }

    @Test
    fun testDualHandleAndChannelNameIdentityMatching() {
        val approvedIds = setOf("physicswallah", "@khanacademy")
        val approvedNames = setOf("Physics Wallah - Alakh Pandey", "Khan Academy")

        // Approved when @handle matches even if display name is localized or stylized
        val handleMatchIdentity = YouTubeNodeExtractor.WatchChannelIdentity(
            channelHandle = "@PhysicsWallah",
            channelName = "PW Live Classroom"
        )
        assertTrue(
            YouTubeContentBlockEngine.isChannelIdentityApproved(
                handleMatchIdentity,
                approvedIds,
                approvedNames
            )
        )

        // Approved when channelName matches even if @handle is different
        val nameMatchIdentity = YouTubeNodeExtractor.WatchChannelIdentity(
            channelHandle = "@ka_india",
            channelName = "Khan Academy"
        )
        assertTrue(
            YouTubeContentBlockEngine.isChannelIdentityApproved(
                nameMatchIdentity,
                approvedIds,
                approvedNames
            )
        )

        // Rejected when neither @handle nor channelName matches (e.g., @SonypicturesBrasil from screenshot)
        val unapprovedIdentity = YouTubeNodeExtractor.WatchChannelIdentity(
            channelHandle = "@SonypicturesBrasil",
            channelName = "Sony Pictures Brasil"
        )
        assertFalse(
            YouTubeContentBlockEngine.isChannelIdentityApproved(
                unapprovedIdentity,
                approvedIds,
                approvedNames
            )
        )
    }

    @Test
    fun testAdElementDetectionDistinguishesActiveAdsFromStopAdsChip() {
        // Real active ad indicators must be detected as ads
        assertTrue(
            com.example.feature.youtube.detector.YouTubeDetectionRules.isAdElement(
                viewId = "com.google.android.youtube:id/skip_ad_button",
                text = "Skip",
                desc = "Skip ad"
            )
        )
        assertTrue(
            com.example.feature.youtube.detector.YouTubeDetectionRules.isAdElement(
                viewId = "com.google.android.youtube:id/ad_badge",
                text = "Sponsored · 0:15",
                desc = null
            )
        )
        assertTrue(
            com.example.feature.youtube.detector.YouTubeDetectionRules.isAdElement(
                viewId = null,
                text = "Visit advertiser",
                desc = null
            )
        )

        // YouTube's "Stop ads" Premium upsell chip in the action bar under the video title
        // (visible in the user's screenshot) must NEVER be misidentified as an active ad!
        assertFalse(
            com.example.feature.youtube.detector.YouTubeDetectionRules.isAdElement(
                viewId = "com.google.android.youtube:id/chip_cloud_chip",
                text = "Stop ads",
                desc = "Stop ads"
            )
        )
    }
}
