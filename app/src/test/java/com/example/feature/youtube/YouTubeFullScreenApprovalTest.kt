package com.example.feature.youtube

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.feature.youtube.detector.YouTubePlaybackSandbox
import com.example.feature.youtube.engine.YouTubeBlockVerdict
import com.example.feature.youtube.engine.YouTubeContentBlockEngine
import com.example.feature.youtube.engine.YouTubeNodeExtractor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeFullScreenApprovalTest {

    private val approvedIds = setOf("physicswallah", "@pw-jeewallah", "@khanacademy")
    private val approvedNames = setOf("Physics Wallah", "PW - JEE Wallah", "Khan Academy")

    @Before
    fun setUp() {
        YouTubeContentBlockEngine.reset()
        YouTubePlaybackSandbox.exitSandbox()
    }

    @After
    fun tearDown() {
        YouTubeContentBlockEngine.reset()
        YouTubePlaybackSandbox.exitSandbox()
    }

    private fun createNode(
        viewId: String? = null,
        text: String? = null,
        contentDesc: String? = null,
        bounds: Rect = Rect(0, 0, 1080, 2400)
    ): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        if (viewId != null) node.viewIdResourceName = viewId
        if (text != null) node.text = text
        if (contentDesc != null) node.contentDescription = contentDesc
        node.setBoundsInScreen(bounds)
        return node
    }

    private fun addChild(parent: AccessibilityNodeInfo, child: AccessibilityNodeInfo) {
        shadowOf(parent).addChild(child)
    }

    private fun buildPortraitApprovedWatchTree(
        title: String = "Rotational Motion Class 11 One Shot",
        handle: String = "@PW-JEEWallah",
        channelName: String = "PW - JEE Wallah"
    ): AccessibilityNodeInfo {
        val root = createNode(
            viewId = "com.google.android.youtube:id/watch_while_layout",
            bounds = Rect(0, 0, 1080, 2400)
        )
        val player = createNode(
            viewId = "com.google.android.youtube:id/player_view",
            bounds = Rect(0, 0, 1080, 608)
        )
        val enterFsBtn = createNode(
            viewId = "com.google.android.youtube:id/fullscreen_button",
            contentDesc = "Enter full screen",
            bounds = Rect(980, 500, 1060, 580)
        )
        addChild(player, enterFsBtn)

        val watchHeader = createNode(
            viewId = "com.google.android.youtube:id/watch_header_layout",
            bounds = Rect(0, 608, 1080, 950)
        )
        val titleNode = createNode(
            viewId = "com.google.android.youtube:id/watch_title_text",
            text = title
        )
        val handleNode = createNode(
            viewId = "com.google.android.youtube:id/channel_handle",
            text = "$handle 120K views 2 days ago"
        )
        val subscribeNode = createNode(
            viewId = "com.google.android.youtube:id/subscribe_button",
            contentDesc = "Subscribe to $channelName."
        )
        addChild(watchHeader, titleNode)
        addChild(watchHeader, handleNode)
        addChild(watchHeader, subscribeNode)

        addChild(root, player)
        addChild(root, watchHeader)
        return root
    }

    private fun buildFullScreenWatchTree(
        title: String? = "Rotational Motion Class 11 One Shot",
        controlsVisible: Boolean = true,
        includeUnapprovedMoreVideosShelf: Boolean = false
    ): AccessibilityNodeInfo {
        val landscapeBounds = Rect(0, 0, 2400, 1080)
        val root = createNode(
            viewId = "com.google.android.youtube:id/watch_while_layout",
            bounds = landscapeBounds
        )
        val player = createNode(
            viewId = "com.google.android.youtube:id/player_view",
            bounds = landscapeBounds
        )
        if (controlsVisible) {
            if (title != null) {
                val titleNode = createNode(
                    viewId = "com.google.android.youtube:id/player_video_title_view",
                    text = title,
                    bounds = Rect(40, 40, 1800, 120)
                )
                addChild(player, titleNode)
            }
            val exitFsBtn = createNode(
                viewId = "com.google.android.youtube:id/fullscreen_button",
                contentDesc = "Exit full screen",
                bounds = Rect(2250, 940, 2360, 1040)
            )
            addChild(player, exitFsBtn)
        }
        addChild(root, player)

        if (includeUnapprovedMoreVideosShelf) {
            val fsEngagement = createNode(
                viewId = "com.google.android.youtube:id/fullscreen_engagement_panel",
                bounds = Rect(0, 700, 2400, 1080)
            )
            val relatedCard1 = createNode(
                viewId = "com.google.android.youtube:id/video_with_context",
                contentDesc = "Funny Vlog - 12 minutes - Go to channel - Unapproved Vlogger - 500K views - 1 day ago - play video"
            )
            val relatedByline = createNode(
                viewId = "com.google.android.youtube:id/byline",
                text = "@UnapprovedVlogger • 500K views"
            )
            val relatedAvatar = createNode(
                viewId = "com.google.android.youtube:id/channel_avatar",
                contentDesc = "Go to channel Unapproved Vlogger"
            )
            addChild(relatedCard1, relatedByline)
            addChild(relatedCard1, relatedAvatar)
            addChild(fsEngagement, relatedCard1)
            addChild(root, fsEngagement)
        }

        return root
    }

    @Test
    fun testApprovedVideoInPortraitThenFullScreenRemainsApprovedEvenWhenControlsHide() {
        var now = 10_000L

        // Step 1: Student opens an approved study channel video in portrait mode
        val portraitRoot = buildPortraitApprovedWatchTree()
        assertFalse(YouTubeNodeExtractor.isFullScreenVideoPlayback(portraitRoot))

        val portraitDecision = YouTubeContentBlockEngine.evaluate(
            rootNode = portraitRoot,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockUnknownContent = true,
            currentTime = now
        )
        assertEquals(YouTubeBlockVerdict.ALLOW, portraitDecision.verdict)
        assertTrue(YouTubePlaybackSandbox.isSandboxActive)

        // Step 2: Student clicks "Enter full screen"
        now += 1500L
        val enterFsClickEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            contentDescription = "Enter full screen"
            packageName = "com.google.android.youtube"
        }
        assertTrue(YouTubeNodeExtractor.isPlayerControlOrFullscreenInteraction(enterFsClickEvent, portraitRoot))
        YouTubeContentBlockEngine.onUserNavigationOrClick(enterFsClickEvent, now)

        // Sandbox and approved latch must NOT be wiped by clicking "Enter full screen"
        assertTrue(YouTubePlaybackSandbox.isSandboxActive)
        assertTrue(YouTubeContentBlockEngine.isCurrentlyOnApprovedWatchPage)

        // Step 3: Window rotates to Full Screen (with controls visible and "More videos" shelf showing unapproved channels)
        now += 400L
        val fullScreenWithControls = buildFullScreenWatchTree(
            controlsVisible = true,
            includeUnapprovedMoreVideosShelf = true
        )
        assertTrue(YouTubeNodeExtractor.isFullScreenVideoPlayback(fullScreenWithControls))

        val sandboxValidInFs = YouTubeContentBlockEngine.checkSandboxStillValid(
            rootNode = fullScreenWithControls,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            currentTime = now
        )
        assertTrue("Sandbox must stay valid in full-screen even with More Videos shelf", sandboxValidInFs)

        val fsDecisionWithControls = YouTubeContentBlockEngine.evaluate(
            rootNode = fullScreenWithControls,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockUnknownContent = true,
            currentTime = now
        )
        assertEquals(YouTubeBlockVerdict.ALLOW, fsDecisionWithControls.verdict)

        // Step 4: Full-screen player controls fade out completely (no title or channel nodes visible)
        // Student watches for 60 seconds (well past the 5s CHANNEL_METADATA_GRACE_MILLIS)
        now += 60_000L
        val fullScreenHiddenControls = buildFullScreenWatchTree(
            title = null,
            controlsVisible = false,
            includeUnapprovedMoreVideosShelf = false
        )
        assertTrue(YouTubeNodeExtractor.isFullScreenVideoPlayback(fullScreenHiddenControls))
        assertTrue(YouTubeNodeExtractor.detectWatchPage(fullScreenHiddenControls))

        val fsDecisionHiddenControls = YouTubeContentBlockEngine.evaluate(
            rootNode = fullScreenHiddenControls,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockUnknownContent = true,
            currentTime = now
        )
        assertEquals(
            "Approved video in full screen with hidden controls must stay ALLOW after 60s",
            YouTubeBlockVerdict.ALLOW,
            fsDecisionHiddenControls.verdict
        )
        assertFalse(fsDecisionHiddenControls.needsExitFullScreenForVerification)
    }

    @Test
    fun testPlayerControlClicksInFullScreenNeverBreakApprovedState() {
        val now = 20_000L
        val portraitRoot = buildPortraitApprovedWatchTree()
        YouTubeContentBlockEngine.evaluate(
            rootNode = portraitRoot,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockUnknownContent = true,
            currentTime = now
        )

        val controlLabels = listOf(
            "Enter full screen",
            "Exit full screen",
            "Pause video",
            "Play video",
            "Fast-forward 10 seconds",
            "Rewind 10 seconds",
            "Seek to 15:20",
            "Video quality",
            "Playback speed",
            "Captions",
            "More actions",
            "Lock screen"
        )

        for (label in controlLabels) {
            val clickEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
                contentDescription = label
                packageName = "com.google.android.youtube"
            }
            assertTrue(
                "Expected '$label' to be recognized as player control interaction",
                YouTubeNodeExtractor.isPlayerControlOrFullscreenInteraction(clickEvent)
            )
            YouTubeContentBlockEngine.onUserNavigationOrClick(clickEvent, now + 500L)
            assertTrue(
                "Expected approved watch page state to remain active after clicking '$label'",
                YouTubeContentBlockEngine.isCurrentlyOnApprovedWatchPage
            )
        }
    }

    @Test
    fun testClickingUnapprovedVideoCardInFullScreenImmediatelyBlocks() {
        var now = 30_000L
        val portraitRoot = buildPortraitApprovedWatchTree()
        YouTubeContentBlockEngine.evaluate(
            rootNode = portraitRoot,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockUnknownContent = true,
            currentTime = now
        )

        // Student in full screen clicks an unapproved video card in the "More videos" shelf
        now += 5000L
        val cardClickEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED).apply {
            contentDescription = "GTA 6 Gameplay Trailer - 4 minutes - Go to channel - Gaming Hub 101 - 2M views - 3 hours ago - play video"
            packageName = "com.google.android.youtube"
        }
        assertFalse(YouTubeNodeExtractor.isPlayerControlOrFullscreenInteraction(cardClickEvent))
        YouTubePlaybackSandbox.onVideoNavigationOccurred(now)
        YouTubeContentBlockEngine.onUserNavigationOrClick(cardClickEvent, now)

        now += 300L
        val fullScreenRoot = buildFullScreenWatchTree(
            title = "GTA 6 Gameplay Trailer",
            controlsVisible = true
        )
        val decision = YouTubeContentBlockEngine.evaluate(
            rootNode = fullScreenRoot,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockUnknownContent = true,
            currentTime = now
        )
        assertEquals(YouTubeBlockVerdict.BLOCK_UNAPPROVED_CHANNEL, decision.verdict)
        assertEquals("Gaming Hub 101", decision.detectedChannel)
    }

    @Test
    fun testUnverifiedVideoStartingDirectlyInFullScreenRequestsAutoExitForVerificationOnce() {
        var now = 50_000L
        val fullScreenUnverifiedRoot = buildFullScreenWatchTree(
            title = "Unknown Lecture Opened Directly In Fullscreen",
            controlsVisible = true
        )

        // Initial tick at t=0ms: within initial settle window, allows navigation
        val initialDecision = YouTubeContentBlockEngine.evaluate(
            rootNode = fullScreenUnverifiedRoot,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockUnknownContent = true,
            currentTime = now
        )
        assertEquals(YouTubeBlockVerdict.ALLOW_NAVIGATION, initialDecision.verdict)
        assertFalse(initialDecision.needsExitFullScreenForVerification)

        // At t=1000ms in full screen with hidden portrait metadata: requests one-time full-screen exit
        now += 1000L
        val autoExitDecision = YouTubeContentBlockEngine.evaluate(
            rootNode = fullScreenUnverifiedRoot,
            event = null,
            approvedChannelIds = approvedIds,
            approvedChannelNames = approvedNames,
            blockUnknownContent = true,
            currentTime = now
        )
        assertEquals(YouTubeBlockVerdict.ALLOW_NAVIGATION, autoExitDecision.verdict)
        assertTrue(
            "Unverified full-screen video must request auto-exit to read portrait header instead of blocking immediately",
            autoExitDecision.needsExitFullScreenForVerification
        )
    }
}
