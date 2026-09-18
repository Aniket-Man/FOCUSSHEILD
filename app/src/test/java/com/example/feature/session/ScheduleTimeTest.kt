package com.example.feature.session

import com.example.feature.session.notification.ScheduleTime
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue 4 regression suite: clock/day parsing must never invent a value.
 *
 * The behaviour under test is the *absence* of the old fallbacks — `09:00` for an unparsable time and
 * Mon–Fri for an unparsable day list — so most assertions here are about null/explicit failure rather
 * than about a parsed result.
 */
class ScheduleTimeTest {

    // ---- clock -------------------------------------------------------------------------

    @Test
    fun `accepts the documented HH mm forms`() {
        assertEquals(0, ScheduleTime.parseToMinutesOrNull("00:00"))
        assertEquals(9 * 60, ScheduleTime.parseToMinutesOrNull("09:00"))
        assertEquals(9 * 60, ScheduleTime.parseToMinutesOrNull("9:00"))
        assertEquals(21 * 60 + 30, ScheduleTime.parseToMinutesOrNull("21:30"))
        assertEquals(23 * 60 + 59, ScheduleTime.parseToMinutesOrNull("23:59"))
        assertEquals(9 * 60, ScheduleTime.parseToMinutesOrNull("  09:00  "))
    }

    @Test
    fun `rejects malformed clock values instead of defaulting to 09 00`() {
        val invalid = listOf(
            "", "  ", "9", "9:5", "9:000", "24:00", "23:60", "-1:00", "09:00:00",
            "09", "9am", "noon", "09-00", "aa:bb", "09:0a", "0x9:00"
        )
        for (value in invalid) {
            assertNull("expected \"$value\" to be invalid", ScheduleTime.parseToMinutesOrNull(value))
            assertFalse(ScheduleTime.isValidClock(value))
        }
        assertNull(ScheduleTime.parseToMinutesOrNull(null))
    }

    @Test
    fun `formats minutes of day and wraps at midnight`() {
        assertEquals("09:05", ScheduleTime.format(9 * 60 + 5))
        assertEquals("00:00", ScheduleTime.format(0))
        assertEquals("23:59", ScheduleTime.format(23 * 60 + 59))
        assertEquals("00:00", ScheduleTime.format(ScheduleTime.MINUTES_PER_DAY))
    }

    // ---- days --------------------------------------------------------------------------

    @Test
    fun `parses stored weekday lists`() {
        val weekdays = ScheduleTime.parseDaysOfWeekOrNull("MON,TUE,WED,THU,FRI")
        assertEquals(
            setOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
            ),
            weekdays
        )
        // The one-time-schedule spelling produced by the editor (SimpleDateFormat "EEE").
        assertEquals(setOf(Calendar.SATURDAY), ScheduleTime.parseDaysOfWeekOrNull("Sat"))
        // Alternate separators are tolerated because older rows and imports use them.
        assertEquals(setOf(Calendar.SATURDAY, Calendar.SUNDAY), ScheduleTime.parseDaysOfWeekOrNull("SAT/SUN"))
        assertEquals(setOf(Calendar.MONDAY, Calendar.SUNDAY), ScheduleTime.parseDaysOfWeekOrNull("mon sun"))
        // Full names work (the token is a prefix match).
        assertEquals(setOf(Calendar.THURSDAY), ScheduleTime.parseDaysOfWeekOrNull("THURSDAY"))
    }

    @Test
    fun `parses the numeric day scheme`() {
        // 1 = SUN … 7 = SAT, matching java.util.Calendar.
        assertEquals(setOf(Calendar.SUNDAY), ScheduleTime.parseDaysOfWeekOrNull("1"))
        assertEquals(setOf(Calendar.SATURDAY), ScheduleTime.parseDaysOfWeekOrNull("7"))
        assertEquals(
            setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY),
            ScheduleTime.parseDaysOfWeekOrNull("1,2,3")
        )
        assertNull(ScheduleTime.parseDaysOfWeekOrNull("0"))
        assertNull(ScheduleTime.parseDaysOfWeekOrNull("8"))
    }

    @Test
    fun `rejects empty or partially understood day lists`() {
        assertNull(ScheduleTime.parseDaysOfWeekOrNull(null))
        assertNull(ScheduleTime.parseDaysOfWeekOrNull(""))
        assertNull(ScheduleTime.parseDaysOfWeekOrNull("   "))
        assertNull(ScheduleTime.parseDaysOfWeekOrNull("MON,SMURFDAY"))
        assertNull(ScheduleTime.parseDaysOfWeekOrNull("every day"))
        assertNull(ScheduleTime.parseDayTokenOrNull("Y"))
        assertNull(ScheduleTime.parseDayTokenOrNull("MONDAYISH")) // prefix, but not a weekday name
    }

    @Test
    fun `weekday sets round-trip through the stored spelling`() {
        val days = setOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )
        val stored = ScheduleTime.formatDaysOfWeek(days)
        assertEquals("MON,TUE,WED,THU,FRI,SAT,SUN", stored)
        assertEquals(days, ScheduleTime.parseDaysOfWeekOrNull(stored))
        // Equivalent spellings normalise to one stored form.
        assertEquals(stored, ScheduleTime.formatDaysOfWeek(ScheduleTime.parseDaysOfWeekOrNull("1,2,3,4,5,6,7")!!))
    }

    @Test
    fun `describes common day sets`() {
        assertEquals("Weekdays", ScheduleTime.describeDaysOfWeek(
            setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        ))
        assertEquals("Weekends", ScheduleTime.describeDaysOfWeek(setOf(Calendar.SATURDAY, Calendar.SUNDAY)))
        assertEquals("Every day", ScheduleTime.describeDaysOfWeek((1..7).toSet()))
        assertEquals("", ScheduleTime.describeDaysOfWeek(emptySet()))
        assertEquals("MON,WED", ScheduleTime.describeDaysOfWeek(setOf(Calendar.WEDNESDAY, Calendar.MONDAY)))
    }

    // ---- durations ---------------------------------------------------------------------

    @Test
    fun `computes durations including a window that crosses midnight`() {
        assertEquals(60, ScheduleTime.durationMinutesOrNull("09:00", "10:00"))
        assertEquals(120, ScheduleTime.durationMinutesOrNull("23:00", "01:00"))
        assertEquals(1, ScheduleTime.durationMinutesOrNull("23:59", "00:00"))
        assertEquals(1439, ScheduleTime.durationMinutesOrNull("00:00", "23:59"))
    }

    @Test
    fun `a zero length window is invalid rather than a 24 hour session`() {
        assertNull(ScheduleTime.durationMinutesOrNull("21:00", "21:00"))
        assertNull(ScheduleTime.durationMinutesOrNull("00:00", "00:00"))
        // Either bound being unparsable is also invalid.
        assertNull(ScheduleTime.durationMinutesOrNull("nonsense", "10:00"))
        assertNull(ScheduleTime.durationMinutesOrNull("09:00", "25:00"))
    }
}
