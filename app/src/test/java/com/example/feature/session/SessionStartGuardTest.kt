package com.example.feature.session

import com.example.data.model.SessionMode
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #28 regression suite: a second `startSession()` must not destroy a live session.
 *
 * The old implementation overwrote `_activeSession` with a brand-new UUID, so a double tap on
 * "Start" abandoned the running session without ever persisting it — every minute of it was gone,
 * and nothing anywhere said so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionStartGuardTest {

    private fun manager(now: () -> Long) = FocusSessionManager(
        coroutineScope = TestScope(UnconfinedTestDispatcher()),
        currentTimeProvider = now
    )

    @Test
    fun `a second start while running is refused and leaves the live session untouched`() {
        var clock = 1_000_000L
        val manager = manager { clock }

        val first = manager.startSession(
            mode = SessionMode.TIMER,
            subject = "Physics",
            topic = "Optics",
            plannedDurationMillis = 3_600_000L
        )
        assertTrue(first.isSuccess)
        val liveId = manager.activeSession.value?.id
        assertNotNull(liveId)

        clock += 5 * 60 * 1000L

        val second = manager.startSession(
            mode = SessionMode.TIMER,
            subject = "Chemistry",
            topic = "Moles",
            plannedDurationMillis = 1_800_000L
        )

        assertTrue("the second start must fail", second.isFailure)
        assertTrue(second.exceptionOrNull() is IllegalStateException)
        assertEquals(
            "the original session must still be the active one",
            liveId,
            manager.activeSession.value?.id
        )
        assertEquals("Physics", manager.activeSession.value?.subject)
        assertEquals(SessionState.RUNNING, manager.sessionState.value)
    }

    @Test
    fun `a second start while paused is refused too`() {
        var clock = 2_000_000L
        val manager = manager { clock }

        assertTrue(
            manager.startSession(
                mode = SessionMode.TIMER,
                plannedDurationMillis = 600_000L
            ).isSuccess
        )
        val liveId = manager.activeSession.value?.id

        clock += 30_000L
        assertTrue(manager.pauseSession())
        assertEquals(SessionState.PAUSED, manager.sessionState.value)

        val second = manager.startSession(mode = SessionMode.STOPWATCH)

        assertTrue(second.isFailure)
        assertEquals(liveId, manager.activeSession.value?.id)
        assertEquals(SessionState.PAUSED, manager.sessionState.value)
    }

    @Test
    fun `a new session can start once the previous one has ended`() {
        var clock = 3_000_000L
        val manager = manager { clock }

        assertTrue(
            manager.startSession(
                mode = SessionMode.TIMER,
                plannedDurationMillis = 600_000L
            ).isSuccess
        )
        val firstId = manager.activeSession.value?.id

        clock += 60_000L
        assertNotNull(manager.endSession())

        val second = manager.startSession(
            mode = SessionMode.TIMER,
            plannedDurationMillis = 600_000L
        )

        assertTrue("ending the session must free the manager again", second.isSuccess)
        assertTrue(manager.activeSession.value?.id != firstId)
        assertEquals(SessionState.RUNNING, manager.sessionState.value)
    }

    @Test
    fun `a refused start does not disturb the running timer`() {
        var clock = 4_000_000L
        val manager = manager { clock }

        manager.startSession(mode = SessionMode.TIMER, plannedDurationMillis = 600_000L)
        clock += 120_000L
        manager.syncStateFromEngines(clock)
        val elapsedBefore = manager.activeSession.value?.elapsedDurationMillis
        assertEquals(120_000L, elapsedBefore)

        manager.startSession(mode = SessionMode.TIMER, plannedDurationMillis = 600_000L)
        manager.syncStateFromEngines(clock)

        assertEquals(
            "a refused start must not reset the engine",
            elapsedBefore,
            manager.activeSession.value?.elapsedDurationMillis
        )
    }
}
