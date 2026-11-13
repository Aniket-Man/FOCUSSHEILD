package com.example.feature.youtube

import com.example.feature.youtube.ai.YouTubeAiEvaluationOutcome
import com.example.feature.youtube.ai.YouTubeAiFilterMode
import com.example.feature.youtube.ai.YouTubeAiStudyClassifier
import com.example.feature.youtube.ai.YouTubeAiVerdict
import com.example.feature.youtube.ai.YouTubeAiVerdictSource
import com.example.feature.youtube.engine.YouTubeNodeExtractor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YouTubeAiStudyClassifierTest {

    @Before
    fun setUp() {
        YouTubeAiStudyClassifier.clearVerdictCache()
        YouTubeAiStudyClassifier.clearSessionBypasses()
        YouTubeAiStudyClassifier.setFilterMode(YouTubeAiFilterMode.GENERAL_ACADEMIC)
        YouTubeAiStudyClassifier.testRemoteInferenceOverride = null
        YouTubeAiStudyClassifier.testNetworkAvailableOverride = null
        YouTubeAiStudyClassifier.onChannelLearnedListener = null
    }

    @After
    fun tearDown() {
        YouTubeAiStudyClassifier.clearVerdictCache()
        YouTubeAiStudyClassifier.clearSessionBypasses()
        YouTubeAiStudyClassifier.setFilterMode(YouTubeAiFilterMode.GENERAL_ACADEMIC)
        YouTubeAiStudyClassifier.testRemoteInferenceOverride = null
        YouTubeAiStudyClassifier.testNetworkAvailableOverride = null
        YouTubeAiStudyClassifier.onChannelLearnedListener = null
    }

    @Test
    fun `learned and blocked channel cache resolves in 0ms without AI network call`() {
        val trustedOutcome = YouTubeAiStudyClassifier.evaluateWatchVideo(
            videoTitle = "Special Live Stream Q&A",
            channelName = "Physics Wallah",
            channelHandle = "@PhysicsWallah",
            activeSubject = "Physics",
            activeTopic = "Electrostatics",
            approvedChannelIds = setOf("@physicswallah"),
            approvedChannelNames = setOf("Physics Wallah"),
            blockedChannelIds = emptySet(),
            blockedChannelNames = emptySet()
        )
        assertTrue(trustedOutcome is YouTubeAiEvaluationOutcome.Decided)
        val trustedVerdict = (trustedOutcome as YouTubeAiEvaluationOutcome.Decided).verdict
        assertTrue(trustedVerdict.isAllowed)
        assertEquals(0L, trustedVerdict.latencyMs)
        assertEquals(YouTubeAiVerdictSource.CHANNEL_CACHE, trustedVerdict.source)

        val blockedOutcome = YouTubeAiStudyClassifier.evaluateWatchVideo(
            videoTitle = "Physics Memes & Comedy",
            channelName = "Distraction Hub",
            channelHandle = "@DistractionHub",
            activeSubject = "Physics",
            activeTopic = "",
            approvedChannelIds = emptySet(),
            approvedChannelNames = emptySet(),
            blockedChannelIds = setOf("@distractionhub"),
            blockedChannelNames = setOf("Distraction Hub")
        )
        assertTrue(blockedOutcome is YouTubeAiEvaluationOutcome.Decided)
        val blockedVerdict = (blockedOutcome as YouTubeAiEvaluationOutcome.Decided).verdict
        assertFalse(blockedVerdict.isAllowed)
        assertEquals(0L, blockedVerdict.latencyMs)
        assertEquals(YouTubeAiVerdictSource.CHANNEL_CACHE, blockedVerdict.source)
    }

    @Test
    fun `mixed content obvious distraction safety check blocks gaming or vlog on approved channel in 0ms`() {
        val outcome = YouTubeAiStudyClassifier.evaluateWatchVideo(
            videoTitle = "GTA 5 Funny Moments & Prank Compilation",
            channelName = "Physics Wallah",
            channelHandle = "@PhysicsWallah",
            activeSubject = "Physics",
            activeTopic = "Electrostatics",
            approvedChannelIds = setOf("@physicswallah"),
            approvedChannelNames = setOf("Physics Wallah"),
            blockedChannelIds = emptySet(),
            blockedChannelNames = emptySet()
        )
        assertTrue(outcome is YouTubeAiEvaluationOutcome.Decided)
        val verdict = (outcome as YouTubeAiEvaluationOutcome.Decided).verdict
        assertFalse("Obvious distraction on an approved channel must still be blocked", verdict.isAllowed)
        assertEquals(0L, verdict.latencyMs)
        assertEquals("DISTRACTION_SAFETY", verdict.category)
        assertNull(YouTubeAiStudyClassifier.checkObviousDistractionSafety("Electrostatics Lecture 01 JEE Physics"))
        assertNotNull(YouTubeAiStudyClassifier.checkObviousDistractionSafety("GTA 5 Funny Moments & Pranks"))
    }

    @Test
    fun `unseen channel triggers AI once, learns channel via callback, then resolves locally on repeat`() {
        YouTubeAiStudyClassifier.testNetworkAvailableOverride = true
        var aiCallCount = 0
        YouTubeAiStudyClassifier.testRemoteInferenceOverride = { _, _, _, _ ->
            aiCallCount++
            Triple(true, "STUDY", "Chemistry experiment and lecture channel")
        }

        val learnedLatch = CountDownLatch(1)
        var learnedName: String? = null
        var learnedHandle: String? = null
        var learnedApproved: Boolean? = null
        YouTubeAiStudyClassifier.onChannelLearnedListener = { name, handle, approved, _, _ ->
            learnedName = name
            learnedHandle = handle
            learnedApproved = approved
            learnedLatch.countDown()
        }

        val verdictLatch = CountDownLatch(1)
        var asyncVerdict: YouTubeAiVerdict? = null
        val listener: (YouTubeAiVerdict) -> Unit = { v ->
            asyncVerdict = v
            verdictLatch.countDown()
        }
        YouTubeAiStudyClassifier.registerAsyncVerdictListener(listener)

        try {
            val firstOutcome = YouTubeAiStudyClassifier.evaluateWatchVideo(
                videoTitle = "Titration Lab Experiment Explained",
                channelName = "Science Lab Academy",
                channelHandle = "@ScienceLabAcademy",
                activeSubject = "Physics",
                activeTopic = "Waves",
                approvedChannelIds = emptySet(),
                approvedChannelNames = emptySet()
            )
            assertTrue(firstOutcome is YouTubeAiEvaluationOutcome.VerifyingAsync)
            assertTrue(learnedLatch.await(3, TimeUnit.SECONDS))
            assertTrue(verdictLatch.await(3, TimeUnit.SECONDS))

            assertEquals(1, aiCallCount)
            assertEquals("Science Lab Academy", learnedName)
            assertEquals("@ScienceLabAcademy", learnedHandle)
            assertEquals(true, learnedApproved)
            assertTrue(asyncVerdict?.isAllowed == true)

            // Repeat visit once stored in Room cache resolves in 0ms without another AI call
            val repeatOutcome = YouTubeAiStudyClassifier.evaluateWatchVideo(
                videoTitle = "Another Organic Chemistry Lecture",
                channelName = "Science Lab Academy",
                channelHandle = "@ScienceLabAcademy",
                activeSubject = "Physics",
                activeTopic = "Waves",
                approvedChannelIds = setOf("@sciencelabacademy"),
                approvedChannelNames = setOf("Science Lab Academy")
            )
            assertTrue(repeatOutcome is YouTubeAiEvaluationOutcome.Decided)
            val repeatVerdict = (repeatOutcome as YouTubeAiEvaluationOutcome.Decided).verdict
            assertTrue(repeatVerdict.isAllowed)
            assertEquals(YouTubeAiVerdictSource.CHANNEL_CACHE, repeatVerdict.source)
            assertEquals(1, aiCallCount)
        } finally {
            YouTubeAiStudyClassifier.unregisterAsyncVerdictListener(listener)
        }
    }

    @Test
    fun `allow anyway grants bypass for current focus session only and clears on session end`() {
        YouTubeAiStudyClassifier.syncActiveSessionId("session_alpha")
        YouTubeAiStudyClassifier.allowChannelForCurrentSession(
            channelId = "@distractionhub",
            channelName = "Distraction Hub",
            sessionId = "session_alpha"
        )

        val duringSession = YouTubeAiStudyClassifier.evaluateWatchVideo(
            videoTitle = "Any Video Title",
            channelName = "Distraction Hub",
            channelHandle = "@DistractionHub",
            activeSubject = "",
            activeTopic = "",
            approvedChannelIds = emptySet(),
            approvedChannelNames = emptySet(),
            blockedChannelIds = setOf("@distractionhub"),
            blockedChannelNames = setOf("Distraction Hub")
        )
        assertTrue(duringSession is YouTubeAiEvaluationOutcome.Decided)
        val allowedVerdict = (duringSession as YouTubeAiEvaluationOutcome.Decided).verdict
        assertTrue(allowedVerdict.isAllowed)
        assertEquals(YouTubeAiVerdictSource.SESSION_BYPASS, allowedVerdict.source)

        // Switching to a new session or ending the session clears the temporary bypass
        YouTubeAiStudyClassifier.syncActiveSessionId("session_beta")
        val nextSession = YouTubeAiStudyClassifier.evaluateWatchVideo(
            videoTitle = "Any Video Title",
            channelName = "Distraction Hub",
            channelHandle = "@DistractionHub",
            activeSubject = "",
            activeTopic = "",
            approvedChannelIds = emptySet(),
            approvedChannelNames = emptySet(),
            blockedChannelIds = setOf("@distractionhub"),
            blockedChannelNames = setOf("Distraction Hub")
        )
        assertTrue(nextSession is YouTubeAiEvaluationOutcome.Decided)
        val blockedAgain = (nextSession as YouTubeAiEvaluationOutcome.Decided).verdict
        assertFalse("Session bypass must not leak into the next session", blockedAgain.isAllowed)
        assertEquals(YouTubeAiVerdictSource.CHANNEL_CACHE, blockedAgain.source)
    }

    @Test
    fun `offline educational vs entertainment classification is subject independent`() {
        YouTubeAiStudyClassifier.testNetworkAvailableOverride = false

        val historyOutcome = YouTubeAiStudyClassifier.evaluateWatchVideo(
            videoTitle = "History of the Roman Empire Lecture",
            channelName = "History Archive",
            channelHandle = "@HistoryArchive",
            activeSubject = "Physics",
            activeTopic = "",
            approvedChannelIds = emptySet(),
            approvedChannelNames = emptySet()
        )
        val historyVerdict = (historyOutcome as YouTubeAiEvaluationOutcome.Decided).verdict
        assertTrue("Any educational subject must be allowed without subject matching", historyVerdict.isAllowed)
        assertEquals(YouTubeAiVerdictSource.OFFLINE_FALLBACK, historyVerdict.source)

        val prankOutcome = YouTubeAiStudyClassifier.evaluateWatchVideo(
            videoTitle = "GTA 5 Funny Moments & Pranks Compilation #14",
            channelName = "Pro Gamer",
            channelHandle = "@ProGamer",
            activeSubject = "Physics",
            activeTopic = "",
            approvedChannelIds = emptySet(),
            approvedChannelNames = emptySet()
        )
        val prankVerdict = (prankOutcome as YouTubeAiEvaluationOutcome.Decided).verdict
        assertFalse(prankVerdict.isAllowed)
    }

    @Test
    fun `live AI interactive check caches verdict so subsequent lookup is 0ms CACHE_HIT`() = runBlocking {
        YouTubeAiStudyClassifier.testNetworkAvailableOverride = true
        var aiCallCount = 0
        YouTubeAiStudyClassifier.testRemoteInferenceOverride = { _, _, _, _ ->
            aiCallCount++
            Triple(true, "STUDY", "Covers rotational inertia experiment")
        }

        val firstVerdict = YouTubeAiStudyClassifier.runInteractiveTestCheck(
            videoTitle = "Understanding Moment of Inertia on Inclined Planes",
            channelName = "Visual Science Lab",
            activeSubject = "Physics",
            activeTopic = "Rotation"
        )
        assertTrue(firstVerdict.isAllowed)
        assertEquals(YouTubeAiVerdictSource.AI_LIVE, firstVerdict.source)
        assertEquals(1, aiCallCount)

        val secondVerdict = YouTubeAiStudyClassifier.runInteractiveTestCheck(
            videoTitle = "Understanding Moment of Inertia on Inclined Planes",
            channelName = "Visual Science Lab",
            activeSubject = "Physics",
            activeTopic = "Rotation"
        )
        assertTrue(secondVerdict.isAllowed)
        assertEquals(0L, secondVerdict.latencyMs)
        assertEquals(YouTubeAiVerdictSource.CACHE_HIT, secondVerdict.source)
        assertEquals(1, aiCallCount)
    }

    @Test
    fun `Litho watch header title extractor accepts real video titles and rejects channel names and feed cards`() {
        val extracted = YouTubeNodeExtractor.cleanLithoWatchTitleCandidate(
            raw = "Electrostatics Lecture 01 | Coulomb's Law JEE Physics • 125K views",
            knownChannelName = "Unlisted Physics Educator",
            knownHandle = "@UnlistedPhysics"
        )
        assertEquals("Electrostatics Lecture 01 | Coulomb's Law JEE Physics", extracted)

        assertEquals(
            null,
            YouTubeNodeExtractor.cleanLithoWatchTitleCandidate(
                raw = "Unlisted Physics Educator",
                knownChannelName = "Unlisted Physics Educator",
                knownHandle = "@UnlistedPhysics"
            )
        )
        assertEquals(
            null,
            YouTubeNodeExtractor.cleanLithoWatchTitleCandidate(
                raw = "Subscribe to Unlisted Physics Educator",
                knownChannelName = "Unlisted Physics Educator",
                knownHandle = "@UnlistedPhysics"
            )
        )
        assertEquals(
            null,
            YouTubeNodeExtractor.cleanLithoWatchTitleCandidate(
                raw = "Funny Cat Video - 4 minutes - Go to channel - CatChannel - 1M views - 1 day ago - play video",
                knownChannelName = "Unlisted Physics Educator",
                knownHandle = "@UnlistedPhysics"
            )
        )
    }
}
