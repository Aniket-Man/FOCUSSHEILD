package com.example.feature.session

import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.model.SessionMode
import com.example.feature.session.notification.ScheduleValidation
import com.example.feature.session.notification.ScheduledSessionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue 4 (second site) regression suite: the session a scheduled alarm starts.
 *
 * The receiver used to build these values inline, with a `catch { 9 * 60 }` time default, an
 * `else -> TIMER` mode default and a `durationMins += 24 * 60` fix-up that turned a zero-length
 * window into a 24-hour session. Every one of those substitutions is now an explicit rejection here.
 */
class ScheduledSessionFactoryTest {

    private fun schedule(
        start: String = "09:00",
        end: String = "11:00",
        mode: String = "TIMER",
        subject: String = "Physics",
        description: String = "",
        packages: String = "com.a, com.b ,",
        breakMinutes: Int = 5,
        blockNotifications: Boolean = false
    ) = FocusScheduleEntity(
        id = "s1",
        title = "Deep Work",
        subjectName = subject,
        description = description,
        startTime = start,
        endTime = end,
        mode = mode,
        blockedAppPackages = packages,
        breakMinutes = breakMinutes,
        blockNotifications = blockNotifications
    )

    private fun ready(schedule: FocusScheduleEntity): ScheduledSessionFactory.Draft {
        val result = ScheduledSessionFactory.draftFor(schedule)
        assertTrue("expected a draft, got $result", result is ScheduledSessionFactory.Result.Ready)
        return (result as ScheduledSessionFactory.Result.Ready).draft
    }

    private fun rejected(schedule: FocusScheduleEntity): String {
        val result = ScheduledSessionFactory.draftFor(schedule)
        assertTrue("expected a rejection, got $result", result is ScheduledSessionFactory.Result.Rejected)
        return (result as ScheduledSessionFactory.Result.Rejected).reason
    }

    @Test
    fun `builds the session from the schedule`() {
        val draft = ready(schedule(description = "Revise optics"))
        assertEquals(SessionMode.TIMER, draft.mode)
        assertEquals("Physics", draft.subject)
        assertEquals("Revise optics", draft.topic)
        assertEquals(2 * 60 * 60 * 1000L, draft.plannedDurationMillis)
        assertEquals(setOf("com.a", "com.b"), draft.blockedAppPackages)
        assertEquals(5, draft.breakMinutes)
    }

    @Test
    fun `handles a midnight crossing window as a real duration`() {
        val draft = ready(schedule(start = "23:30", end = "01:00"))
        assertEquals(90 * 60 * 1000L, draft.plannedDurationMillis)
    }

    @Test
    fun `a zero length window is rejected instead of becoming a 24 hour session`() {
        val reason = rejected(schedule(start = "21:00", end = "21:00"))
        assertTrue(reason, reason.contains("zero-length"))
    }

    @Test
    fun `an unparsable time is rejected instead of defaulting to 09 00`() {
        assertTrue(rejected(schedule(start = "9")).contains("invalid"))
        assertTrue(rejected(schedule(end = "25:00")).contains("invalid"))
    }

    @Test
    fun `an unknown mode is rejected instead of being coerced to TIMER`() {
        val reason = rejected(schedule(mode = "DEEP_WORK"))
        assertTrue(reason, reason.contains("DEEP_WORK"))
    }

    @Test
    fun `all three real modes are accepted`() {
        assertEquals(SessionMode.TIMER, ready(schedule(mode = "TIMER", end = "10:00")).mode)
        assertEquals(SessionMode.POMODORO, ready(schedule(mode = "POMODORO", end = "10:00")).mode)
        assertEquals(SessionMode.STOPWATCH, ready(schedule(mode = "STOPWATCH", end = "10:00")).mode)
        // Mode strings are stored uppercase but tolerate surrounding whitespace/lowercase.
        assertEquals(SessionMode.POMODORO, ready(schedule(mode = " pomodoro ", end = "10:00")).mode)
    }

    @Test
    fun `a pomodoro window shorter than one cycle is rejected`() {
        val reason = rejected(schedule(mode = "POMODORO", start = "09:00", end = "09:10"))
        assertTrue(reason, reason.contains("pomodoro"))
    }

    @Test
    fun `goal and topic fall back to documented text, never to a placeholder time`() {
        val draft = ready(schedule(description = ""))
        assertEquals("Automated Focus Window (09:00 - 11:00)", draft.goal)
        assertEquals("Automated Schedule: Deep Work", draft.topic)
    }

    @Test
    fun `break minutes are clamped to a sane range`() {
        assertEquals(0, ready(schedule(breakMinutes = -5)).breakMinutes)
        assertEquals(180, ready(schedule(breakMinutes = 10_000)).breakMinutes)
    }

    @Test
    fun `mode parsing is strict`() {
        assertNull(ScheduledSessionFactory.parseModeOrNull(null))
        assertNull(ScheduledSessionFactory.parseModeOrNull(""))
        assertNull(ScheduledSessionFactory.parseModeOrNull("POMODORO_25"))
        assertNotNull(ScheduledSessionFactory.parseModeOrNull("pomodoro"))
    }

    // ---- validation rules (item 4 at the write boundary) --------------------------------

    @Test
    fun `validation accepts a well formed schedule and normalises its stored form`() {
        val messy = FocusScheduleEntity(
            id = "s2",
            title = "  Evening Focus  ",
            daysOfWeek = "fri,mon,wed",
            startTime = " 9:00 ",
            endTime = "11:00",
            mode = "timer"
        )
        assertNull(ScheduleValidation.validate(messy))
        val normalized = ScheduleValidation.normalize(messy)!!
        assertEquals("Evening Focus", normalized.title)
        assertEquals("MON,WED,FRI", normalized.daysOfWeek)
        assertEquals("09:00", normalized.startTime)
        assertEquals("11:00", normalized.endTime)
    }

    @Test
    fun `validation rejects each unusable shape with a specific reason`() {
        assertTrue(ScheduleValidation.validate(schedule(start = "nope"))!!.contains("start time"))
        assertTrue(ScheduleValidation.validate(schedule(end = "nope"))!!.contains("end time"))
        assertTrue(ScheduleValidation.validate(schedule(start = "10:00", end = "10:00"))!!.contains("non-zero"))
        assertTrue(
            ScheduleValidation.validate(schedule().copy(daysOfWeek = "MON,SMURF"))!!.contains("day")
        )
        assertTrue(ScheduleValidation.validate(schedule(mode = "NOPE"))!!.contains("session mode"))
        assertTrue(
            ScheduleValidation.validate(schedule().copy(repeatEnabled = false, scheduledDateMillis = 0L))!!
                .contains("one-time")
        )
        assertTrue(ScheduleValidation.validate(schedule().copy(title = "   "))!!.contains("name"))
        assertNull(ScheduleValidation.normalize(schedule(start = "nope")))
    }
}
