package com.example.feature.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SessionMode
import com.example.feature.session.domain.SessionState
import com.example.feature.session.domain.SessionSummaryData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the "close the app on the summary screen" flow: the un-answered
 * summary must survive process death and come back with enough data to Save or Discard it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingSessionSummaryStoreTest {

    private lateinit var context: Context

    private val summary = SessionSummaryData(
        sessionId = "session-123",
        mode = SessionMode.POMODORO,
        subject = "Mathematics",
        topic = "Integration",
        goal = "Finish 20 problems",
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_001_500_000L,
        plannedDurationMillis = 25 * 60 * 1000L,
        pureStudyDurationMillis = 22 * 60 * 1000L,
        totalBreakDurationMillis = 3 * 60 * 1000L,
        isCompleted = true,
        isCancelled = false,
        isStrictModeEnabled = true,
        isAppBlockingEnabled = true,
        isStudyChannelsEnabled = true,
        isYouTubeStudyModeEnabled = true,
        isBrowserStudyModeEnabled = false,
        blockedAppsCount = 4,
        distractionsBlockedCount = 7,
        completedPomodoroCycles = 2,
        totalPomodoroCycles = 4,
        lapsCount = 0
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PendingSessionSummaryStore.clear(context)
    }

    @After
    fun tearDown() {
        PendingSessionSummaryStore.clear(context)
    }

    @Test
    fun `saved summary is restored with display and record fields intact`() {
        PendingSessionSummaryStore.save(context, summary)

        val restored = PendingSessionSummaryStore.load(context)

        assertNotNull(restored)
        assertEquals(summary.sessionId, restored!!.sessionId)
        assertEquals(summary.mode, restored.mode)
        assertEquals(summary.subject, restored.subject)
        assertEquals(summary.topic, restored.topic)
        assertEquals(summary.goal, restored.goal)
        assertEquals(summary.startedAt, restored.startedAt)
        assertEquals(summary.endedAt, restored.endedAt)
        assertEquals(summary.plannedDurationMillis, restored.plannedDurationMillis)
        assertEquals(summary.totalBreakDurationMillis, restored.totalBreakDurationMillis)
        assertEquals(summary.pureStudyDurationMillis, restored.pureStudyDurationMillis)
        assertEquals(summary.lapsCount, restored.lapsCount)
        assertEquals(summary.blockedAppsCount, restored.blockedAppsCount)
        assertEquals(summary.isYouTubeStudyModeEnabled, restored.isYouTubeStudyModeEnabled)
        assertEquals(summary.distractionsBlockedCount, restored.distractionsBlockedCount)
        assertEquals(summary.completedPomodoroCycles, restored.completedPomodoroCycles)
        assertEquals(summary.totalPomodoroCycles, restored.totalPomodoroCycles)

        val raw = restored.rawSession
        assertNotNull(raw)
        assertEquals(SessionState.COMPLETED, raw!!.state)
        assertEquals(summary.plannedDurationMillis, raw.plannedDurationMillis)
        assertEquals(summary.pureStudyDurationMillis, raw.elapsedDurationMillis)
        assertEquals(summary.totalPomodoroCycles, raw.totalCycles)
        assertEquals(summary.isStrictModeEnabled, raw.isStrictModeEnabled)
        assertTrue(raw.isAppBlockingEnabled)
    }

    @Test
    fun `cancelled summary is restored as a cancelled raw session`() {
        PendingSessionSummaryStore.save(
            context,
            summary.copy(isCompleted = false, isCancelled = true, completedPomodoroCycles = 1)
        )

        val restored = PendingSessionSummaryStore.load(context)

        assertNotNull(restored)
        assertFalse(restored!!.isCompleted)
        assertTrue(restored.isCancelled)
        assertEquals(SessionState.CANCELLED, restored.rawSession?.state)
        assertEquals(2, restored.rawSession?.currentCycle)
    }

    @Test
    fun `clear removes a pending summary so it is not shown again`() {
        PendingSessionSummaryStore.save(context, summary)
        assertNotNull(PendingSessionSummaryStore.load(context))

        PendingSessionSummaryStore.clear(context)

        assertNull(PendingSessionSummaryStore.load(context))
    }

    @Test
    fun `missing summary loads as null instead of throwing`() {
        assertNull(PendingSessionSummaryStore.load(context))
    }
}
