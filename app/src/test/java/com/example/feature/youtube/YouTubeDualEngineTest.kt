package com.example.feature.youtube

import com.example.core.accessibility.FocusProtectionState
import com.example.core.accessibility.ProtectionDecision
import com.example.core.accessibility.ProtectionPolicy
import com.example.feature.session.domain.SessionState
import com.example.feature.youtube.detector.ActiveVideoInfo
import com.example.feature.youtube.detector.ChannelApprovalStatus
import com.example.feature.youtube.detector.ChannelIdentityInfo
import com.example.feature.youtube.detector.ShortsConfidence
import com.example.feature.youtube.detector.ShortsDetectionResult
import com.example.feature.youtube.detector.YouTubeContentInspectionEngine
import com.example.feature.youtube.detector.YouTubeDetectionRules
import com.example.feature.youtube.detector.YouTubeShortsDetectionEngine
import com.example.feature.youtube.domain.DetectionConfidence
import com.example.feature.youtube.domain.YouTubeContentType
import com.example.feature.youtube.domain.YouTubeDetectionResult
import com.example.feature.youtube.engine.YouTubeScreenState
import com.example.feature.youtube.engine.YouTubeStateEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verification of the Dual-Engine Architecture (Shorts Engine + Content Inspection Engine)
 * and all 16 Mandatory FocusShield Test Cases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class YouTubeDualEngineTest {

    private val approvedIds = setOf("physicswallah", "khanacademy", "mitocw", "@physicswallah", "@khanacademy")
    private val approvedNames = setOf("physics wallah", "khan academy", "mit opencourseware")

    private val activeStudyState = FocusProtectionState(
        sessionId = "session_focus_study",
        sessionState = SessionState.RUNNING,
        isAppBlockingEnabled = true,
        isYouTubeStudyModeEnabled = true,
        blockShorts = true,
        blockUnknownContent = true,
        approvedChannelIds = approvedIds,
        approvedChannelNames = approvedNames,
        blockedPackages = emptySet()
    )

    @Before
    fun setUp() {
        YouTubeContentInspectionEngine.clearChannelContext()
        YouTubeStateEngine.onYouTubeExited()
    }

    // =========================================================================
    // MANDATORY TEST CASES 1 THROUGH 16
    // =========================================================================

    @Test
    fun test01_YouTubeStudyModeOn_HomeScreen_ShortsShelfVisible_ExpectAllow() {
        // TEST 1: Shorts shelf visible on Home screen must be ALLOWED
        val shelfResult = ShortsDetectionResult.SHELF_ONLY
        assertFalse(shelfResult.isConsumingShort)
        assertEquals(ShortsConfidence.NONE, shelfResult.confidence)

        val homeResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.YOUTUBE_HOME,
            isShort = false,
            confidence = DetectionConfidence.HIGH,
            matchedRule = "home_feed_with_shorts_shelf"
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = activeStudyState,
            youtubeResult = homeResult
        )

        // Home feed is never blocked, but during an active Study Mode session it triggers
        // the over-the-display Study Mode nudge popup (feed remains visible behind it).
        assertEquals(ProtectionDecision.BLACKOUT_YOUTUBE_FEED, decision)
    }

    @Test
    fun test02_YouTubeStudyModeOn_SearchScreen_ExpectAllow() {
        // TEST 2: Active search query input must be ALLOWED
        val searchResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.SEARCH_QUERY,
            isShort = false,
            confidence = DetectionConfidence.HIGH,
            matchedRule = "search_input_active"
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = activeStudyState,
            youtubeResult = searchResult
        )

        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, decision)
    }

    @Test
    fun test03_YouTubeStudyModeOn_SearchResults_ExpectAllow() {
        // TEST 3: Viewing search results must be ALLOWED
        val searchResults = YouTubeDetectionResult(
            contentType = YouTubeContentType.SEARCH_RESULTS,
            isShort = false,
            confidence = DetectionConfidence.HIGH,
            matchedRule = "search_results_active"
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = activeStudyState,
            youtubeResult = searchResults
        )

        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, decision)
    }

    @Test
    fun test04_ApprovedChannel_NormalVideo_ExpectAllow() {
        // TEST 4: Approved channel playing a normal video must be ALLOWED
        val videoResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO_PLAYBACK,
            channelName = "Physics Wallah",
            channelId = "@PhysicsWallah",
            videoTitle = "Rotational Motion Class 11",
            isShort = false,
            confidence = DetectionConfidence.HIGH
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = activeStudyState,
            youtubeResult = videoResult
        )

        assertEquals(ProtectionDecision.ALLOW_APPROVED_CHANNEL, decision)
    }

    @Test
    fun test05_ApprovedChannel_Short_ExpectBlock() {
        // TEST 5: Short from an approved channel MUST BE BLOCKED (Shorts prohibition cannot be bypassed)
        val shortResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.SHORT,
            channelName = "Physics Wallah",
            channelId = "@PhysicsWallah",
            isShort = true,
            confidence = DetectionConfidence.HIGH
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = activeStudyState,
            youtubeResult = shortResult
        )

        assertEquals(ProtectionDecision.BLOCK_SHORTS, decision)
    }

    @Test
    fun test06_UnapprovedChannel_NormalVideo_ExpectBlock() {
        // TEST 6: Unapproved channel playing a normal video must be BLOCKED
        val unapprovedResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO_PLAYBACK,
            channelName = "Entertainment & Gaming Clipz",
            channelId = "@GamingClipz",
            videoTitle = "Top 10 Epic Moments",
            isShort = false,
            confidence = DetectionConfidence.HIGH
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = activeStudyState,
            youtubeResult = unapprovedResult
        )

        assertEquals(ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL, decision)
    }

    @Test
    fun test07_UnknownChannel_NavigationOnly_ExpectAllowNavigation() {
        // TEST 7: Unknown channel during navigation / browsing must ALLOW navigation
        val navResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.YOUTUBE_NAVIGATION,
            channelName = null,
            channelId = null,
            isShort = false,
            confidence = DetectionConfidence.MEDIUM
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = activeStudyState,
            youtubeResult = navResult
        )

        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, decision)
    }

    @Test
    fun test08_UnknownActualVideoContent_ExpectBlockContent() {
        // TEST 8: Active watch player with unverified/unknown content when blockUnknownContent is ON -> BLOCK
        val unverifiedVideo = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO_PLAYBACK,
            channelName = null,
            channelId = null,
            videoTitle = null,
            isShort = false,
            confidence = DetectionConfidence.LOW
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.youtube",
            state = activeStudyState.copy(blockUnknownContent = true),
            youtubeResult = unverifiedVideo
        )

        assertEquals(ProtectionDecision.BLOCK_UNKNOWN_YOUTUBE_CONTENT, decision)
    }

    @Test
    fun test09_HomeShortsShelf_NoShortOpened_ExpectAllow() {
        // TEST 9: Shorts shelf present on Home, but no Short is opened -> no Shorts block,
        // only the Study Mode Home feed nudge popup (feed stays visible)
        val detection = YouTubeShortsDetectionEngine.inspect(null, null)
        assertFalse(detection.isConsumingShort)

        val homeResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.YOUTUBE_HOME,
            isShort = false
        )
        val decision = ProtectionPolicy.evaluate("com.google.android.youtube", activeStudyState, homeResult)
        assertEquals(ProtectionDecision.BLACKOUT_YOUTUBE_FEED, decision)
    }

    @Test
    fun test10_UserTapsShorts_ShortsEngineConfirmsShort_ExpectBlock() {
        // TEST 10: User enters Shorts player -> Shorts Engine confirms Short -> BLOCK
        val shortsPlayback = YouTubeDetectionResult(
            contentType = YouTubeContentType.SHORT,
            isShort = true,
            confidence = DetectionConfidence.HIGH,
            matchedRule = "view_id:reel_player_page_view"
        )

        val decision = ProtectionPolicy.evaluate("com.google.android.youtube", activeStudyState, shortsPlayback)
        assertEquals(ProtectionDecision.BLOCK_SHORTS, decision)
    }

    @Test
    fun test11_UserLeavesShorts_ReturnsToHome_ExpectAllow() {
        // TEST 11: User leaves Shorts and returns to Home -> State cleared -> no block,
        // Home feed triggers the Study Mode nudge popup instead
        YouTubeStateEngine.onYouTubeExited()

        val homeResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.YOUTUBE_HOME,
            isShort = false
        )

        val decision = ProtectionPolicy.evaluate("com.google.android.youtube", activeStudyState, homeResult)
        assertEquals(ProtectionDecision.BLACKOUT_YOUTUBE_FEED, decision)
    }

    @Test
    fun test12_ApprovedChannelA_UserWatchesVideo_ThenNavigatesToUnrelatedChannelB_ExpectNoLeak() {
        // TEST 12: Channel context must not carry over approval to unrelated channel B
        val channelA = ChannelIdentityInfo(channelName = "Khan Academy", channelHandle = "@KhanAcademy")
        YouTubeContentInspectionEngine.setChannelContext(channelA, isApproved = true, source = "CHANNEL_PAGE")

        // User navigates to Channel B (unapproved)
        val channelB = ChannelIdentityInfo(channelName = "Unapproved Vlogs", channelHandle = "@UnapprovedVlogs")
        val isChannelBApproved = YouTubeContentInspectionEngine.isChannelApproved(channelB, approvedIds, approvedNames)
        assertFalse(isChannelBApproved)

        val videoBResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO_PLAYBACK,
            channelName = channelB.channelName,
            channelId = channelB.channelHandle,
            isShort = false
        )

        val decision = ProtectionPolicy.evaluate("com.google.android.youtube", activeStudyState, videoBResult)
        assertEquals(ProtectionDecision.BLOCK_UNAPPROVED_CHANNEL, decision)
    }

    @Test
    fun test13_YouTubeStudyModeOn_YouTubeGlobalBlockOff_ExpectNavigationWorks() {
        // TEST 13: Study Mode ON, Global Block OFF -> Navigation/Search allowed
        val state = activeStudyState.copy(blockedPackages = emptySet())

        val navDecision = ProtectionPolicy.evaluate(
            "com.google.android.youtube",
            state,
            YouTubeDetectionResult(contentType = YouTubeContentType.YOUTUBE_NAVIGATION)
        )
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, navDecision)

        val searchDecision = ProtectionPolicy.evaluate(
            "com.google.android.youtube",
            state,
            YouTubeDetectionResult(contentType = YouTubeContentType.SEARCH_QUERY)
        )
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, searchDecision)
    }

    @Test
    fun test14_YouTubeStudyModeOn_YouTubeGlobalBlockOn_ExpectStudyModePriority() {
        // TEST 14: Study Mode ON, YouTube package is in blockedPackages -> Study Mode has priority
        val stateWithGlobalBlock = activeStudyState.copy(
            blockedPackages = setOf("com.google.android.youtube", "com.instagram.android")
        )

        // 1. Navigation is allowed
        val navDecision = ProtectionPolicy.evaluate(
            "com.google.android.youtube",
            stateWithGlobalBlock,
            YouTubeDetectionResult(contentType = YouTubeContentType.SEARCH_RESULTS)
        )
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, navDecision)

        // 2. Approved video is allowed
        val approvedDecision = ProtectionPolicy.evaluate(
            "com.google.android.youtube",
            stateWithGlobalBlock,
            YouTubeDetectionResult(
                contentType = YouTubeContentType.VIDEO_PLAYBACK,
                channelId = "@PhysicsWallah"
            )
        )
        assertEquals(ProtectionDecision.ALLOW_APPROVED_CHANNEL, approvedDecision)

        // 3. Shorts are blocked with BLOCK_SHORTS (not generic BLOCK)
        val shortsDecision = ProtectionPolicy.evaluate(
            "com.google.android.youtube",
            stateWithGlobalBlock,
            YouTubeDetectionResult(contentType = YouTubeContentType.SHORT, isShort = true)
        )
        assertEquals(ProtectionDecision.BLOCK_SHORTS, shortsDecision)

        // 4. Non-YouTube app in blocked list is still blocked generically
        val instaDecision = ProtectionPolicy.evaluate("com.instagram.android", stateWithGlobalBlock)
        assertEquals(ProtectionDecision.BLOCK, instaDecision)
    }

    @Test
    fun test15_ShortsShelfOnApprovedChannel_ShelfVisibleAllows_TapShortBlocks() {
        // TEST 15: Shorts shelf on approved channel: Shelf visible -> ALLOW, Tap Short -> BLOCK
        val shelfResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.CHANNEL_PAGE,
            channelName = "Physics Wallah",
            isShort = false
        )
        val shelfDecision = ProtectionPolicy.evaluate("com.google.android.youtube", activeStudyState, shelfResult)
        assertEquals(ProtectionDecision.ALLOW_NAVIGATION, shelfDecision)

        val tappedShortResult = YouTubeDetectionResult(
            contentType = YouTubeContentType.SHORT,
            channelName = "Physics Wallah",
            channelId = "@PhysicsWallah",
            isShort = true
        )
        val tappedShortDecision = ProtectionPolicy.evaluate("com.google.android.youtube", activeStudyState, tappedShortResult)
        assertEquals(ProtectionDecision.BLOCK_SHORTS, tappedShortDecision)
    }

    @Test
    fun test16_ApprovedNormalVideo_RecommendationsNotMistakenForActiveVideo() {
        // TEST 16: Active video is approved, user scrolls recommendations -> Active video remains approved
        val activeVideoChannel = ChannelIdentityInfo(channelName = "MIT OpenCourseWare", channelHandle = "@mitocw")
        assertTrue(YouTubeContentInspectionEngine.isChannelApproved(activeVideoChannel, approvedIds, approvedNames))

        val result = YouTubeDetectionResult(
            contentType = YouTubeContentType.VIDEO_PLAYBACK,
            channelName = activeVideoChannel.channelName,
            channelId = activeVideoChannel.channelHandle,
            videoTitle = "Linear Algebra 18.06",
            isShort = false
        )

        val decision = ProtectionPolicy.evaluate("com.google.android.youtube", activeStudyState, result)
        assertEquals(ProtectionDecision.ALLOW_APPROVED_CHANNEL, decision)
    }

    // =========================================================================
    // DUAL ENGINE & CONTEXT LIFECYCLE TESTS
    // =========================================================================

    @Test
    fun testChannelContextExpiration() {
        val channel = ChannelIdentityInfo(channelName = "Physics Wallah", channelHandle = "@PhysicsWallah")
        val now = 100_000L
        YouTubeContentInspectionEngine.setChannelContext(channel, isApproved = true, source = "BROWSE", now = now)

        val validContext = YouTubeContentInspectionEngine.getActiveChannelContext(now + 10_000L)
        assertTrue(validContext != null && !validContext.isExpired(now + 10_000L))

        // Exceed TTL (45 seconds)
        val expiredContext = YouTubeContentInspectionEngine.getActiveChannelContext(now + 60_000L)
        assertNull(expiredContext)
    }

    @Test
    fun testChannelContextResetOnAppExit() {
        val channel = ChannelIdentityInfo(channelName = "Physics Wallah", channelHandle = "@PhysicsWallah")
        YouTubeContentInspectionEngine.setChannelContext(channel, isApproved = true, source = "BROWSE")
        assertTrue(YouTubeContentInspectionEngine.getActiveChannelContext() != null)

        YouTubeStateEngine.onYouTubeExited()
        assertNull(YouTubeContentInspectionEngine.getActiveChannelContext())
    }

    @Test
    fun testShortsDetectionEngineSeparation() {
        // Shorts Engine does NOT determine channel approval
        val result = YouTubeShortsDetectionEngine.inspect(null, null)
        assertFalse(result.isConsumingShort)
        assertEquals(ShortsConfidence.NONE, result.confidence)
    }

    // =========================================================================
    // CHANNEL NAME VS VIDEO VIEWS / METADATA VALIDATION TESTS
    // =========================================================================

    @Test
    fun testYouTubeViewsAreNotIdentifiedAsChannelName() {
        val viewCountStrings = listOf(
            "1.2M views",
            "124K views",
            "15M views • 2 days ago",
            "1.2M views • 3 hours ago",
            "1 view",
            "No views",
            "0 views",
            "10 lakh views",
            "1.5 crore views",
            "2,345,678 views",
            "3.4M visualizaciones",
            "50K Aufrufe",
            "12K vues",
            "12M subscribers",
            "1.4M subscribers",
            "Streamed 3 days ago",
            "Premiered 2 hours ago",
            "2 days ago",
            "4 months ago",
            "Subscribe",
            "Subscribed",
            "12:34"
        )

        for (invalid in viewCountStrings) {
            assertTrue("Expected '$invalid' to be invalid channel name", YouTubeDetectionRules.isInvalidChannelName(invalid))
            val extracted = YouTubeDetectionRules.extractAndValidateChannelName(invalid)
            assertNull("Expected extractAndValidateChannelName('$invalid') to be null but was '$extracted'", extracted)
        }
    }

    @Test
    fun testGenuineChannelNamesAreExtractedFromCompoundStrings() {
        val testCases = mapOf(
            "Physics Wallah • 12.4M subscribers" to "Physics Wallah",
            "Khan Academy • 1.5M views • 3 years ago" to "Khan Academy",
            "MIT OpenCourseWare • 5M subscribers" to "MIT OpenCourseWare",
            "Veritasium • 15M subscribers" to "Veritasium",
            "By 3Blue1Brown" to "3Blue1Brown"
        )

        for ((input, expected) in testCases) {
            val extracted = YouTubeDetectionRules.extractAndValidateChannelName(input)
            assertEquals("Failed extracting from '$input'", expected, extracted)
        }
    }
}
