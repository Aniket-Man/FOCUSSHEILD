package com.example.cloud.sync

import com.example.data.preferences.FocusPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The background auto-save period is where two independent limits meet: the cadence the user picked
 * in Settings (15s–5min) and the interval WorkManager will accept for periodic work (≥15 minutes).
 *
 * The floor always wins today, because every cadence Settings offers is faster than 15 minutes. That
 * is the point of these assertions: a sub-minute preference must still produce a *legal* request. If
 * it did not, WorkManager would reject the request at runtime and the background job would silently
 * never run.
 *
 * These pin behaviour, not intent — the seconds-level guarantee is served by the foreground loop in
 * [SyncGateway], so collapsing the background period to the floor loses nothing.
 */
class AutoSaveSchedulerTest {

    private val fifteenMinutes = 15 * 60L

    @Test
    fun `every cadence Settings offers collapses to the WorkManager floor`() {
        // The exact presets in SettingsScreen.AUTO_SAVE_INTERVALS_SECONDS.
        listOf(15, 30, 60, 300).forEach { requested ->
            assertEquals(
                "a $requested-second preference must not become an illegal WorkManager request",
                fifteenMinutes,
                AutoSaveScheduler.resolveBackgroundPeriodSeconds(requested)
            )
        }
    }

    @Test
    fun `no input can produce a period below the floor`() {
        // Includes the out-of-range hand-crafted values a restored backup or a downgrade could hold.
        listOf(0, -1, -999, 1, 14, 15, 899, 900, 3600, Int.MAX_VALUE).forEach { requested ->
            val period = AutoSaveScheduler.resolveBackgroundPeriodSeconds(requested)
            assertTrue(
                "requested=$requested produced $period, which WorkManager would reject",
                period >= fifteenMinutes
            )
        }
    }

    @Test
    fun `an absurd interval cannot park the job for days`() {
        // Clamped to the supported preference ceiling (5 minutes) before the floor is applied, so the
        // result stays at the floor rather than inheriting an arbitrary large number.
        assertEquals(
            fifteenMinutes,
            AutoSaveScheduler.resolveBackgroundPeriodSeconds(Int.MAX_VALUE)
        )
        assertEquals(
            fifteenMinutes,
            AutoSaveScheduler.resolveBackgroundPeriodSeconds(
                FocusPreferences.MAX_AUTO_SAVE_INTERVAL_SECONDS * 10
            )
        )
    }
}
