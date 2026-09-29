package com.example.core.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #30 regression suite for the widget refresh throttle.
 *
 * Each test corresponds to one of the three defects in the timestamp-check it replaced: the
 * dropped trailing update, the double-fire from a non-atomic check-then-set, and the window being
 * measured from before the work instead of after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightThrottleTest {

    @Test
    fun `the first call runs immediately`() = runTest {
        var runs = 0
        val throttle = SingleFlightThrottle(WINDOW) { testScheduler.currentTime }

        throttle.submit { runs++ }

        assertEquals(1, runs)
    }

    @Test
    fun `a call inside the window is deferred, never dropped`() = runTest {
        var runs = 0
        val throttle = SingleFlightThrottle(WINDOW) { testScheduler.currentTime }

        throttle.submit { runs++ }
        assertEquals(1, runs)

        // This is the "session just ended" refresh the old throttle silently discarded.
        val trailing = launch { throttle.submit { runs++ } }
        assertEquals("the trailing run must not jump the window", 1, runs)

        advanceUntilIdle()
        trailing.join()

        assertEquals("the trailing run must still happen", 2, runs)
        assertEquals(WINDOW, testScheduler.currentTime)
    }

    @Test
    fun `many calls inside the window collapse into exactly one trailing run`() = runTest {
        var runs = 0
        val throttle = SingleFlightThrottle(WINDOW) { testScheduler.currentTime }

        throttle.submit { runs++ }
        repeat(5) { launch { throttle.submit { runs++ } } }
        advanceUntilIdle()

        assertEquals("a burst must coalesce, not queue up", 2, runs)
    }

    @Test
    fun `simultaneous callers produce one immediate run and one trailing run, never four`() = runTest {
        var runs = 0
        val throttle = SingleFlightThrottle(WINDOW) { testScheduler.currentTime }

        val jobs = List(4) { launch { throttle.submit { runs++ } } }
        runCurrent()
        assertEquals("only one of four simultaneous callers may run immediately", 1, runs)

        advanceUntilIdle()
        jobs.forEach { it.join() }
        assertEquals("the other three collapse into a single trailing run", 2, runs)
    }

    @Test
    fun `force bypasses the window`() = runTest {
        var runs = 0
        val throttle = SingleFlightThrottle(WINDOW) { testScheduler.currentTime }

        throttle.submit { runs++ }
        throttle.submit(force = true) { runs++ }

        assertEquals(2, runs)
    }

    @Test
    fun `the window is measured from when the work finished, not when it started`() = runTest {
        var runs = 0
        val throttle = SingleFlightThrottle(WINDOW) { testScheduler.currentTime }

        throttle.submit {
            delay(4_000L) // a slow Glance update
            runs++
        }
        assertEquals(1, runs)
        assertEquals(4_000L, testScheduler.currentTime)

        val trailing = launch { throttle.submit { runs++ } }
        advanceUntilIdle()
        trailing.join()

        assertEquals(2, runs)
        // 4_000 (work finished) + WINDOW, not 0 + WINDOW.
        assertEquals(4_000L + WINDOW, testScheduler.currentTime)
    }

    @Test
    fun `a failing run still closes the window instead of hanging the throttle`() = runTest {
        var runs = 0
        val throttle = SingleFlightThrottle(WINDOW) { testScheduler.currentTime }

        runCatching { throttle.submit { throw IllegalStateException("glance not ready") } }

        val trailing = launch { throttle.submit { runs++ } }
        advanceUntilIdle()
        trailing.join()

        assertEquals(1, runs)
    }

    private companion object {
        const val WINDOW = 10_000L
    }
}
