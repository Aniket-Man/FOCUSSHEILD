package com.example.feature.applimits.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the duration choices both blockers show for a given remaining allowance.
 *
 * The floating sheet and the activity blocker used to compute these separately; the floating one
 * filtered a fixed 2/5/10/20 grid with `coerceAtLeast(2)`, so "1 minute left" still offered
 * "2 mins" and no allowance fell back to a bogus "1 min" that the engine clamped to zero. These
 * tests make the shared policy explicit.
 */
class AppLimitDurationOptionsTest {

    @Test
    fun `a full allowance keeps the standard grid`() {
        assertEquals(listOf(2, 5, 10, 20), AppLimitDurationOptions.forRemainingMinutes(60))
        assertEquals(listOf(2, 5, 10, 20), AppLimitDurationOptions.forRemainingMinutes(20))
    }

    @Test
    fun `a partial allowance appends the exact remaining minutes as the max choice`() {
        assertEquals(listOf(2, 5, 10, 12), AppLimitDurationOptions.forRemainingMinutes(12))
        assertEquals(listOf(2, 5, 7), AppLimitDurationOptions.forRemainingMinutes(7))
        assertEquals(listOf(2, 3), AppLimitDurationOptions.forRemainingMinutes(3))
    }

    @Test
    fun `one minute left does not offer a longer choice`() {
        val options = AppLimitDurationOptions.forRemainingMinutes(1)

        assertEquals(listOf(1), options)
        assertFalse("2 minutes must not be offered with 1 minute left", options.contains(2))
    }

    @Test
    fun `sub-minute remainder still offers the one minute choice the engine can clamp`() {
        // The engine decides exhaustion in milliseconds; a selection prompt may legitimately
        // arrive with 59 seconds left, which floors to 0 display minutes (issue #49, C2).
        assertEquals(listOf(1), AppLimitDurationOptions.forRemainingMinutes(0))
    }

    @Test
    fun `two minutes left offers exactly two minutes`() {
        assertEquals(listOf(2), AppLimitDurationOptions.forRemainingMinutes(2))
    }

    @Test
    fun `choices are unique and never zero or negative`() {
        for (remaining in -5..120) {
            val options = AppLimitDurationOptions.forRemainingMinutes(remaining)
            assertTrue("remaining=$remaining produced no choices", options.isNotEmpty())
            assertEquals("remaining=$remaining produced duplicates", options.size, options.distinct().size)
            assertTrue("remaining=$remaining produced a non-positive choice", options.all { it > 0 })
        }
    }
}
