package com.example.feature.youtube

import com.example.core.accessibility.FocusProtectionState
import com.example.core.accessibility.ProtectionDecision
import com.example.core.accessibility.ProtectionPolicy
import com.example.feature.session.domain.SessionState
import com.example.feature.youtube.detector.YouTubeDetectionRules
import com.example.feature.youtube.domain.YouTubeContentType
import com.example.feature.youtube.domain.YouTubeDetectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeProtectionPolicyTest {

    @Test
    fun testYouTubePackageDetection() {
        assertTrue(YouTubeDetectionRules.isYouTubePackage("com.google.android.youtube"))
        assertTrue(YouTubeDetectionRules.isYouTubePackage("com.google.android.youtube.tv"))
        assertTrue(YouTubeDetectionRules.isYouTubePackage("com.google.android.apps.youtube.kids"))
        assertFalse(YouTubeDetectionRules.isYouTubePackage("com.instagram.android"))
        assertFalse(YouTubeDetectionRules.isYouTubePackage(null))
        assertFalse(YouTubeDetectionRules.isYouTubePackage(""))
    }

    @Test
    fun testShortsBlockedDuringActiveStudySession() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isYouTubeStudyModeEnabled = true,
            approvedChannelIds = setOf("physicswallah", "khanacademy"),
            approvedChannelNames = setOf("Physics Wallah", "Khan Academy"),
            blockedPackages = setOf("com.instagram.android")
        )

        val shortsResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.SHORT,
            isShort = true
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = protectionState,
            youtubeResult = shortsResult
        )

        assertEquals(ProtectionDecision.BLOCK_SHORTS, decision)
    }

    @Test
    fun testApprovedChannelAllowedDuringActiveSession() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isYouTubeStudyModeEnabled = true,
            approvedChannelIds = setOf("physicswallah", "khanacademy"),
            approvedChannelNames = setOf("Physics Wallah", "Khan Academy"),
            blockedPackages = setOf("com.instagram.android")
        )

        val approvedResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO,
            channelName = "Physics Wallah - Alakh Pandey",
            channelId = "physicswallah",
            isShort = false
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = protectionState,
            youtubeResult = approvedResult
        )

        assertEquals(ProtectionDecision.ALLOW_APPROVED_CHANNEL, decision)
    }

    @Test
    fun testUnapprovedChannelBlockedDuringActiveSession() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isYouTubeStudyModeEnabled = true,
            approvedChannelIds = setOf("physicswallah", "khanacademy"),
            approvedChannelNames = setOf("Physics Wallah", "Khan Academy"),
            blockedPackages = setOf("com.instagram.android")
        )

        val unapprovedResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO,
            channelName = "Gaming Highlights & Memes",
            channelId = "gamingmemes",
            isShort = false
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = protectionState,
            youtubeResult = unapprovedResult
        )

        assertEquals(ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL, decision)
    }

    @Test
    fun testYouTubeHomeFeedTriggersStudyModePopup() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isYouTubeStudyModeEnabled = true,
            approvedChannelIds = setOf("physicswallah")
        )

        val homeResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.YOUTUBE_HOME,
            isShort = false
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = protectionState,
            youtubeResult = homeResult
        )

        // Home feed is a navigation surface (never approved content): during an active
        // Study Mode session it triggers the Study Mode nudge popup, not a block.
        assertEquals(ProtectionDecision.BLACKOUT_YOUTUBE_FEED, decision)
    }

    @Test
    fun testUnverifiedVideoBlockedWhenUnknownContentBlocked() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isYouTubeStudyModeEnabled = true,
            blockUnknownContent = true,
            approvedChannelIds = setOf("physicswallah")
        )

        val unverifiedVideoResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO_PLAYBACK,
            channelName = null,
            channelId = null,
            isShort = false
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = protectionState,
            youtubeResult = unverifiedVideoResult
        )

        assertEquals(ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT, decision)
    }

    @Test
    fun testRegularBlockedAppEvaluation() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            blockedPackages = setOf("com.instagram.android", "com.snapchat.android")
        )

        val blockedDecision = ProtectionPolicy.evaluate(
            packageName = "com.instagram.android",
            state = protectionState
        )

        assertEquals(ProtectionDecision.BLOCK, blockedDecision)

        val unblockedDecision = ProtectionPolicy.evaluate(
            packageName = "com.calculator.android",
            state = protectionState
        )

        assertEquals(ProtectionDecision.ALLOW_UNBLOCKED_APP, unblockedDecision)
    }

    @Test
    fun testSystemAppsAndInactiveSessionAllowed() {
        val inactiveState = FocusProtectionState(
            sessionState = SessionState.IDLE,
            isAppBlockingEnabled = true,
            blockedPackages = setOf("com.instagram.android")
        )

        val inactiveDecision = ProtectionPolicy.evaluate(
            packageName = "com.instagram.android",
            state = inactiveState
        )
        assertEquals(ProtectionDecision.ALLOW_INACTIVE_SESSION, inactiveDecision)

        val activeState = FocusProtectionState(
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            blockedPackages = setOf("com.android.settings", "com.instagram.android")
        )

        val systemDecision = ProtectionPolicy.evaluate(
            packageName = "com.android.settings",
            state = activeState
        )
        assertEquals(ProtectionDecision.ALLOW_SYSTEM, systemDecision)
    }

    @Test
    fun testTemporaryBreakAllowsApps() {
        val now = System.currentTimeMillis()
        val breakActiveState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isBreakActive = true,
            breakEndsAt = now + 60000L,
            blockedPackages = setOf("com.instagram.android")
        )

        val breakDecision = ProtectionPolicy.evaluate(
            packageName = "com.instagram.android",
            state = breakActiveState,
            currentTime = now
        )
        assertEquals(ProtectionDecision.ALLOW_DURING_BREAK, breakDecision)
    }

    @Test
    fun testYouTubeSearchAndNavigationAllowed() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isYouTubeStudyModeEnabled = true,
            approvedChannelIds = setOf("physicswallah"),
            blockedPackages = setOf("com.google.android.youtube") // Even if YouTube is in blocked packages
        )

        // 1. Search Query
        val searchQueryResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.SEARCH_QUERY,
            isShort = false
        )
        val searchDecision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = protectionState,
            youtubeResult = searchQueryResult
        )
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, searchDecision)

        // 2. Search Results
        val searchResultsResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.SEARCH_RESULTS,
            isShort = false
        )
        val searchResultsDecision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = protectionState,
            youtubeResult = searchResultsResult
        )
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, searchResultsDecision)

        // 3. Channel Page Navigation
        val channelPageResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.CHANNEL_PAGE,
            channelName = "Physics Wallah",
            isShort = false
        )
        val channelPageDecision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = protectionState,
            youtubeResult = channelPageResult
        )
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, channelPageDecision)
    }

    @Test
    fun testStudyModeOverridesGeneralBlockList() {
        // When YouTube Study Mode is ON and YouTube package is also in blocked list,
        // Study mode takes precedence over coarse blocking:
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isYouTubeStudyModeEnabled = true,
            approvedChannelIds = setOf("physicswallah"),
            blockedPackages = setOf("com.google.android.youtube", "com.instagram.android")
        )

        // Approved video -> allowed
        val approvedResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO,
            channelId = "physicswallah"
        )
        assertEquals(
            ProtectionDecision.ALLOW_APPROVED_CHANNEL,
            ProtectionPolicy.evaluate("com.google.android.youtube", protectionState, approvedResult)
        )

        // Unapproved video -> blocked with unapproved channel reason
        val unapprovedResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO,
            channelId = "unapproved_creator"
        )
        assertEquals(
            ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL,
            ProtectionPolicy.evaluate("com.google.android.youtube", protectionState, unapprovedResult)
        )
    }

    @Test
    fun testGeneralBlockWhenStudyModeDisabled() {
        // When YouTube Study Mode is OFF and YouTube is in blockedPackages, YouTube is fully blocked
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isYouTubeStudyModeEnabled = false,
            blockedPackages = setOf("com.google.android.youtube")
        )

        val searchResult = YouTubeDetectionResult(contentType = YouTubeContentType.SEARCH_RESULTS)
        val decision = ProtectionPolicy.evaluate("com.google.android.youtube", protectionState, searchResult)
        assertEquals(ProtectionDecision.BLOCK, decision)
    }

    @Test
    fun testBrowserStudyModeAllowsNavigationAndSearching() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isBrowserStudyModeEnabled = true,
            blockedPackages = setOf("com.android.chrome", "com.brave.browser", "com.instagram.android")
        )

        // Browsers with study mode enabled should allow opening and searching
        val chromeDecision = ProtectionPolicy.evaluate("com.android.chrome", protectionState)
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, chromeDecision)

        val braveDecision = ProtectionPolicy.evaluate("com.brave.browser", protectionState)
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, braveDecision)

        // Non-browser blocked app is still fully blocked
        val instagramDecision = ProtectionPolicy.evaluate("com.instagram.android", protectionState)
        assertEquals(ProtectionDecision.BLOCK, instagramDecision)
    }

    @Test
    fun testBrowserBlockedWhenStudyModeDisabled() {
        val protectionState = FocusProtectionState(
            sessionId = "session_123",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isBrowserStudyModeEnabled = false,
            blockedPackages = setOf("com.android.chrome")
        )

        val decision = ProtectionPolicy.evaluate("com.android.chrome", protectionState)
        assertEquals(ProtectionDecision.BLOCK, decision)
    }

    @Test
    fun testChannelMatchingHelper() {
        val approvedIds = setOf("physicswallah", "khanacademy")
        val approvedNames = setOf("physics wallah", "khan academy")

        assertTrue(ProtectionPolicy.isChannelApproved("physicswallah", null, approvedIds, approvedNames))
        assertTrue(ProtectionPolicy.isChannelApproved("@physicswallah", null, approvedIds, approvedNames))
        assertTrue(ProtectionPolicy.isChannelApproved(null, "Physics Wallah - Alakh Pandey", approvedIds, approvedNames))
        assertFalse(ProtectionPolicy.isChannelApproved("randomchannel", "Random Gaming", approvedIds, approvedNames))
    }
}
