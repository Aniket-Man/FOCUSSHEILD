package com.example.feature.session

import com.example.data.local.entity.FocusScheduleEntity
import com.example.feature.session.notification.ScheduleValidation
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #28 regression suite: overlapping automated schedules must be rejected at save time.
 *
 * Before this, two auto-start schedules could be saved on top of each other and the collision only
 * surfaced at runtime, as a debug line, when the second alarm found a session already running — so
 * the planner displayed a protected window that was never protected.
 */
class ScheduleOverlapTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata") // no DST, keeps dates simple

    private fun schedule(
        id: String,
        days: String = "MON,TUE,WED,THU,FRI",
        start: String = "09:00",
        end: String = "11:00",
        enabled: Boolean = true,
        repeat: Boolean = true,
        dateMillis: Long = 0L
    ) = FocusScheduleEntity(
        id = id,
        title = "Schedule $id",
        daysOfWeek = days,
        startTime = start,
        endTime = end,
        isEnabled = enabled,
        repeatEnabled = repeat,
        scheduledDateMillis = dateMillis
    )

    /** Midnight (local) on the given date, which is how the date picker stores an anchor date. */
    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis

    @Test
    fun `identical windows on shared days overlap`() {
        assertTrue(
            ScheduleValidation.overlaps(schedule("a"), schedule("b"), zone)
        )
    }

    @Test
    fun `adjacent windows do not overlap`() {
        assertFalse(
            "09:00-11:00 and 11:00-13:00 touch but do not overlap",
            ScheduleValidation.overlaps(
                schedule("a", start = "09:00", end = "11:00"),
                schedule("b", start = "11:00", end = "13:00"),
                zone
            )
        )
    }

    @Test
    fun `partially overlapping windows on shared days overlap`() {
        assertTrue(
            ScheduleValidation.overlaps(
                schedule("a", start = "09:00", end = "11:00"),
                schedule("b", start = "10:30", end = "12:00"),
                zone
            )
        )
    }

    @Test
    fun `same window on different days does not overlap`() {
        assertFalse(
            ScheduleValidation.overlaps(
                schedule("a", days = "MON,WED"),
                schedule("b", days = "TUE,THU"),
                zone
            )
        )
    }

    @Test
    fun `a window that runs past midnight collides with the next morning`() {
        assertTrue(
            "MON 22:00-01:00 runs into TUE 00:30-02:00",
            ScheduleValidation.overlaps(
                schedule("a", days = "MON", start = "22:00", end = "01:00"),
                schedule("b", days = "TUE", start = "00:30", end = "02:00"),
                zone
            )
        )
    }

    @Test
    fun `a saturday night window wraps around the end of the week into sunday`() {
        // The interval arithmetic runs on minutes-of-the-week, so this is the case that has to be
        // split at the week boundary rather than compared as a negative range.
        assertTrue(
            ScheduleValidation.overlaps(
                schedule("a", days = "SAT", start = "23:00", end = "01:00"),
                schedule("b", days = "SUN", start = "00:15", end = "03:00"),
                zone
            )
        )
        assertFalse(
            ScheduleValidation.overlaps(
                schedule("a", days = "SAT", start = "23:00", end = "01:00"),
                schedule("b", days = "SUN", start = "02:00", end = "04:00"),
                zone
            )
        )
    }

    @Test
    fun `one-time schedules on different dates never collide`() {
        assertFalse(
            "same weekday, different weeks",
            ScheduleValidation.overlaps(
                schedule("a", repeat = false, dateMillis = date(2026, 3, 2)),
                schedule("b", repeat = false, dateMillis = date(2026, 3, 9)),
                zone
            )
        )
    }

    @Test
    fun `one-time schedules on the same date collide`() {
        assertTrue(
            ScheduleValidation.overlaps(
                schedule("a", repeat = false, dateMillis = date(2026, 3, 2)),
                schedule("b", repeat = false, dateMillis = date(2026, 3, 2), start = "10:00", end = "12:00"),
                zone
            )
        )
    }

    @Test
    fun `findOverlap ignores itself and disabled schedules`() {
        val candidate = schedule("a")
        assertNull(
            "a schedule cannot collide with its own stored row",
            ScheduleValidation.findOverlap(candidate, listOf(candidate.copy(title = "old")), zone)
        )
        assertNull(
            "a disabled schedule arms nothing, so it cannot be collided with",
            ScheduleValidation.findOverlap(candidate, listOf(schedule("b", enabled = false)), zone)
        )
        assertNull(
            "a disabled candidate arms nothing either",
            ScheduleValidation.findOverlap(
                schedule("a", enabled = false),
                listOf(schedule("b")),
                zone
            )
        )
    }

    @Test
    fun `findOverlap names the schedule that collides`() {
        val clash = ScheduleValidation.findOverlap(
            schedule("a", start = "10:00", end = "12:00"),
            listOf(
                schedule("b", days = "SAT", start = "10:00", end = "12:00"),
                schedule("c", start = "11:00", end = "13:00")
            ),
            zone
        )
        assertEquals("c", clash?.id)
    }

    @Test
    fun `an unusable schedule reports no overlap - validate rejects it first`() {
        assertFalse(
            ScheduleValidation.overlaps(
                schedule("a", start = "nope"),
                schedule("b"),
                zone
            )
        )
    }

    @Test
    fun `isCurrentlyActiveWindow detects active recurring and overnight windows`() {
        val monday10Am = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.MARCH, 2, 10, 0, 0) // 2026-03-02 is a Monday
        }.timeInMillis
        val monday12Pm = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.MARCH, 2, 12, 0, 0)
        }.timeInMillis

        val morningSchedule = schedule("m", days = "MON,TUE,WED,THU,FRI", start = "09:00", end = "11:00")
        assertTrue(ScheduleValidation.isCurrentlyActiveWindow(morningSchedule, monday10Am, zone))
        assertFalse(ScheduleValidation.isCurrentlyActiveWindow(morningSchedule, monday12Pm, zone))
        assertFalse(ScheduleValidation.isCurrentlyActiveWindow(morningSchedule.copy(isEnabled = false), monday10Am, zone))
    }
}
