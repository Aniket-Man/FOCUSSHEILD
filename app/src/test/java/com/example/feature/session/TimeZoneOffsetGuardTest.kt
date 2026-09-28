package com.example.feature.session

import com.example.feature.session.notification.TimeZoneOffsetGuard
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #28 regression suite: detecting a UTC-offset change that no broadcast announces.
 *
 * A DST transition leaves the zone id untouched and sends nothing an app can subscribe to, so
 * alarms armed before it fire an hour off. These tests pin the comparison the receivers use to
 * notice, against transitions that are fixed calendar facts.
 */
class TimeZoneOffsetGuardTest {

    private val berlin: TimeZone = TimeZone.getTimeZone("Europe/Berlin") // +1 / +2
    private val kolkata: TimeZone = TimeZone.getTimeZone("Asia/Kolkata") // +5:30, no DST

    private fun instant(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    @Test
    fun `no record yet is not a shift`() {
        assertFalse(TimeZoneOffsetGuard.hasShifted(null, Long.MIN_VALUE, "Europe/Berlin", 3_600_000L))
    }

    @Test
    fun `an unchanged offset is not a shift`() {
        assertFalse(
            TimeZoneOffsetGuard.hasShifted("Europe/Berlin", 3_600_000L, "Europe/Berlin", 3_600_000L)
        )
    }

    @Test
    fun `the same zone with a different offset is a shift`() {
        assertTrue(
            "this is exactly the DST case: same zone id, new offset",
            TimeZoneOffsetGuard.hasShifted("Europe/Berlin", 3_600_000L, "Europe/Berlin", 7_200_000L)
        )
    }

    @Test
    fun `a different zone is a shift`() {
        assertTrue(
            TimeZoneOffsetGuard.hasShifted("Europe/Berlin", 3_600_000L, "Asia/Kolkata", 19_800_000L)
        )
    }

    @Test
    fun `berlin really does change offset across the spring transition`() {
        // Europe/Berlin springs forward on the last Sunday of March 2026 (29 March).
        val before = TimeZoneOffsetGuard.currentOffsetMillis(berlin, instant(berlin, 2026, 3, 28, 12))
        val after = TimeZoneOffsetGuard.currentOffsetMillis(berlin, instant(berlin, 2026, 3, 30, 12))

        assertEquals(3_600_000L, before)
        assertEquals(7_200_000L, after)
        assertTrue(TimeZoneOffsetGuard.hasShifted(berlin.id, before, berlin.id, after))
    }

    @Test
    fun `a zone without DST never reports a shift across the same dates`() {
        val before = TimeZoneOffsetGuard.currentOffsetMillis(kolkata, instant(kolkata, 2026, 3, 28, 12))
        val after = TimeZoneOffsetGuard.currentOffsetMillis(kolkata, instant(kolkata, 2026, 3, 30, 12))

        assertEquals(19_800_000L, before)
        assertEquals(19_800_000L, after)
        assertFalse(TimeZoneOffsetGuard.hasShifted(kolkata.id, before, kolkata.id, after))
    }
}
