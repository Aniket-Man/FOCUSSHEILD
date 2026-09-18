package com.example.feature.session

import com.example.data.local.entity.FocusScheduleEntity
import com.example.feature.session.notification.ScheduleAlarmPlanner
import com.example.feature.session.notification.ScheduleTime
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue 6 regression suite (scheduled-alarm lifecycle, item 14): the next trigger for a schedule
 * must be exact, bounded, and never a guess.
 *
 * All cases inject `now` and a `TimeZone`, so they are deterministic on any machine — including the
 * DST cases, which are run against zones whose transitions are fixed calendar facts.
 */
class ScheduleAlarmPlannerTest {

    private val kolkata = TimeZone.getTimeZone("Asia/Kolkata") // UTC+5:30, no DST
    private val berlin = TimeZone.getTimeZone("Europe/Berlin") // DST transitions

    // ---- helpers -----------------------------------------------------------------------

    private fun at(
        zone: TimeZone,
        year: Int,
        month: Int, // 1-based, human style
        day: Int,
        hour: Int,
        minute: Int
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    private fun localFields(millis: Long, zone: TimeZone): List<Int> =
        Calendar.getInstance(zone).apply { timeInMillis = millis }
            .let { listOf(it.get(Calendar.DAY_OF_WEEK), it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }

    private fun schedule(
        days: String = "MON,TUE,WED,THU,FRI",
        start: String = "09:00",
        end: String = "11:00",
        enabled: Boolean = true,
        repeat: Boolean = true,
        dateMillis: Long = 0L
    ) = FocusScheduleEntity(
        id = "schedule-under-test",
        title = "Deep Work",
        daysOfWeek = days,
        startTime = start,
        endTime = end,
        isEnabled = enabled,
        repeatEnabled = repeat,
        scheduledDateMillis = dateMillis
    )

    private fun arm(plan: ScheduleAlarmPlanner.Plan): ScheduleAlarmPlanner.Plan.Arm {
        assertTrue("expected a scheduled alarm, got $plan", plan is ScheduleAlarmPlanner.Plan.Arm)
        return plan as ScheduleAlarmPlanner.Plan.Arm
    }

    private fun skip(plan: ScheduleAlarmPlanner.Plan): ScheduleAlarmPlanner.Plan.Skip {
        assertTrue("expected a skip, got $plan", plan is ScheduleAlarmPlanner.Plan.Skip)
        return plan as ScheduleAlarmPlanner.Plan.Skip
    }

    // ---- recurring schedules ------------------------------------------------------------

    @Test
    fun `arms today when the time is still ahead`() {
        // Wednesday 2026-09-16, 08:00 local → the 09:00 weekday slot is one hour away.
        val now = at(kolkata, 2026, 9, 16, 8, 0)
        val plan = arm(ScheduleAlarmPlanner.plan(schedule(), now, kolkata))

        assertEquals(at(kolkata, 2026, 9, 16, 9, 0), plan.triggerAtMillis)
        assertEquals(listOf(Calendar.WEDNESDAY, 9, 0), localFields(plan.triggerAtMillis, kolkata))
        assertNull(plan.degradedReason)
    }

    @Test
    fun `rolls to the next selected day when today's slot has passed`() {
        // Wednesday 10:00 → the next weekday slot is Thursday 09:00, not "now + 24h".
        val now = at(kolkata, 2026, 9, 16, 10, 0)
        val plan = arm(ScheduleAlarmPlanner.plan(schedule(), now, kolkata))

        assertEquals(at(kolkata, 2026, 9, 17, 9, 0), plan.triggerAtMillis)
        assertEquals(listOf(Calendar.THURSDAY, 9, 0), localFields(plan.triggerAtMillis, kolkata))
    }

    @Test
    fun `weekend schedules roll across the week`() {
        // Friday after the slot → Saturday (the only selected day).
        val fridayEvening = at(kolkata, 2026, 9, 18, 22, 0)
        val plan = arm(ScheduleAlarmPlanner.plan(schedule(days = "SAT", start = "07:30"), fridayEvening, kolkata))
        assertEquals(at(kolkata, 2026, 9, 19, 7, 30), plan.triggerAtMillis)

        // Sunday after the slot → the following Saturday (a full week later, never "now + 24h").
        val sundayEvening = at(kolkata, 2026, 9, 20, 22, 0)
        val weekLater = arm(ScheduleAlarmPlanner.plan(schedule(days = "SAT", start = "07:30"), sundayEvening, kolkata))
        assertEquals(at(kolkata, 2026, 9, 26, 7, 30), weekLater.triggerAtMillis)
    }

    @Test
    fun `the 14 day window is gone - a once-a-month style day list still resolves`() {
        // A schedule on one day a week is the worst case for a fixed search window; it must resolve
        // to exactly 7 days ahead, deterministically.
        val mondayMorning = at(kolkata, 2026, 9, 14, 0, 1)
        val plan = arm(
            ScheduleAlarmPlanner.plan(schedule(days = "SUN", start = "23:00"), mondayMorning, kolkata)
        )
        // Next Sunday 23:00 (6 days later, not 14 days, and never in the past).
        assertEquals(at(kolkata, 2026, 9, 20, 23, 0), plan.triggerAtMillis)
    }

    @Test
    fun `month and year boundaries are handled`() {
        val newYearEve = at(kolkata, 2026, 12, 31, 23, 30)
        val plan = arm(ScheduleAlarmPlanner.plan(schedule(days = "FRI", start = "00:15"), newYearEve, kolkata))
        assertEquals(at(kolkata, 2027, 1, 1, 0, 15), plan.triggerAtMillis)
    }

    // ---- DST --------------------------------------------------------------------------

    @Test
    fun `spring forward resolves to the shifted instant but keeps the wall clock intent`() {
        // Europe/Berlin DST starts 2026-03-29 at 02:00 → 03:00. A 03:30 alarm that day exists.
        val now = at(berlin, 2026, 3, 28, 12, 0)
        val plan = arm(ScheduleAlarmPlanner.plan(schedule(days = "SUN", start = "03:30"), now, berlin))
        assertEquals(listOf(Calendar.SUNDAY, 3, 30), localFields(plan.triggerAtMillis, berlin))
        assertEquals(at(berlin, 2026, 3, 29, 3, 30), plan.triggerAtMillis)
    }

    @Test
    fun `a nonexistent local time on the spring forward day is normalised forward, not skipped`() {
        // 02:30 does not exist on 2026-03-29 in Berlin; java.util.Calendar shifts it to 03:30 local.
        val now = at(berlin, 2026, 3, 28, 12, 0)
        val plan = arm(ScheduleAlarmPlanner.plan(schedule(days = "SUN", start = "02:30"), now, berlin))
        val fields = localFields(plan.triggerAtMillis, berlin)
        assertEquals(Calendar.SUNDAY, fields[0])
        // Either 02:30 (if a platform resolves differently) or the shifted 03:30 — never another day.
        assertTrue("unexpected local time $fields", fields[1] == 2 || fields[1] == 3)
    }

    @Test
    fun `fall back keeps a stable wall clock time across the transition`() {
        // Europe/Berlin DST ends 2026-10-25 at 03:00 → 02:00 (02:30 happens twice).
        val now = at(berlin, 2026, 10, 24, 12, 0)
        val plan = arm(ScheduleAlarmPlanner.plan(schedule(days = "SUN", start = "02:30"), now, berlin))
        val fields = localFields(plan.triggerAtMillis, berlin)
        assertEquals(Calendar.SUNDAY, fields[0])
        assertEquals(2, fields[1])
        assertEquals(30, fields[2])
    }

    // ---- one-time schedules -------------------------------------------------------------

    @Test
    fun `one-time schedules fire on their own date`() {
        val anchor = at(kolkata, 2026, 10, 5, 12, 0) // date only; the clock comes from startTime
        val now = at(kolkata, 2026, 9, 16, 8, 0)
        val plan = arm(
            ScheduleAlarmPlanner.plan(
                schedule(repeat = false, dateMillis = anchor, start = "16:45"),
                now,
                kolkata
            )
        )
        assertEquals(at(kolkata, 2026, 10, 5, 16, 45), plan.triggerAtMillis)
    }

    @Test
    fun `one-time schedules further than 14 days out are still armed`() {
        val now = at(kolkata, 2026, 9, 1, 12, 0)
        val anchor = at(kolkata, 2026, 10, 1, 12, 0) // 30 days ahead
        val plan = arm(
            ScheduleAlarmPlanner.plan(schedule(repeat = false, dateMillis = anchor), now, kolkata)
        )
        assertEquals(at(kolkata, 2026, 10, 1, 9, 0), plan.triggerAtMillis)
    }

    @Test
    fun `a past one-time date is skipped with a reason, never rescheduled to now plus a day`() {
        val now = at(kolkata, 2026, 9, 16, 8, 0)
        val anchor = at(kolkata, 2026, 9, 10, 12, 0)
        val plan = skip(
            ScheduleAlarmPlanner.plan(schedule(repeat = false, dateMillis = anchor), now, kolkata)
        )
        assertEquals(ScheduleAlarmPlanner.Reason.PAST_ONE_TIME_DATE, plan.reason)
    }

    @Test
    fun `legacy one-time rows without a date fall back to the weekday list but say so`() {
        val now = at(kolkata, 2026, 9, 16, 8, 0)
        val plan = arm(
            ScheduleAlarmPlanner.plan(schedule(repeat = false, dateMillis = 0L), now, kolkata)
        )
        assertEquals(at(kolkata, 2026, 9, 16, 9, 0), plan.triggerAtMillis)
        assertNotNull("the fallback must be reported, not silent", plan.degradedReason)
    }

    // ---- invalid configuration ---------------------------------------------------------

    @Test
    fun `a disabled schedule is never armed`() {
        val now = at(kolkata, 2026, 9, 16, 8, 0)
        val plan = skip(ScheduleAlarmPlanner.plan(schedule(enabled = false), now, kolkata))
        assertEquals(ScheduleAlarmPlanner.Reason.DISABLED, plan.reason)
    }

    @Test
    fun `an unparsable start time is skipped rather than defaulted to 09 00`() {
        val now = at(kolkata, 2026, 9, 16, 8, 0)
        for (bad in listOf("", "9", "25:00", "9:5", "morning")) {
            val plan = skip(ScheduleAlarmPlanner.plan(schedule(start = bad), now, kolkata))
            assertEquals("startTime=\"$bad\"", ScheduleAlarmPlanner.Reason.INVALID_START_TIME, plan.reason)
        }
    }

    @Test
    fun `an unparsable day list is skipped rather than defaulted to weekdays`() {
        val now = at(kolkata, 2026, 9, 16, 8, 0)
        for (bad in listOf("", "   ", "FUNDAY", "MON,FUNDAY")) {
            val plan = skip(ScheduleAlarmPlanner.plan(schedule(days = bad), now, kolkata))
            assertEquals("daysOfWeek=\"$bad\"", ScheduleAlarmPlanner.Reason.INVALID_DAYS_OF_WEEK, plan.reason)
        }
    }

    @Test
    fun `an occurrence at or before the minimum lead is never armed`() {
        val now = at(kolkata, 2026, 9, 16, 9, 0) // Wednesday 09:00 exactly

        // One-time, dated today at the current minute: the moment has passed → skipped, not moved.
        val oneTime = skip(
            ScheduleAlarmPlanner.plan(
                schedule(repeat = false, dateMillis = now, start = "09:00"),
                now,
                kolkata
            )
        )
        assertEquals(ScheduleAlarmPlanner.Reason.PAST_ONE_TIME_DATE, oneTime.reason)

        // Recurring on Wednesday only, with a one-minute lead: today's slot is too close, so the
        // planner rolls a full week instead of arming an alarm that fires immediately.
        val recurring = arm(
            ScheduleAlarmPlanner.plan(
                schedule(days = "WED", start = "09:00"),
                now,
                kolkata,
                minLeadMillis = 60_000L
            )
        )
        assertEquals(at(kolkata, 2026, 9, 23, 9, 0), recurring.triggerAtMillis)
    }

    @Test
    fun `next recurring trigger returns null only for an empty day set`() {
        val now = at(kolkata, 2026, 9, 16, 8, 0)
        assertNull(ScheduleAlarmPlanner.nextRecurringTriggerOrNull(emptySet(), 540, now, kolkata))
        assertNotNull(
            ScheduleAlarmPlanner.nextRecurringTriggerOrNull(setOf(Calendar.WEDNESDAY), 540, now, kolkata)
        )
    }

    @Test
    fun `midnight crossing schedules arm at their start time`() {
        val now = at(kolkata, 2026, 9, 16, 22, 0)
        val plan = arm(
            ScheduleAlarmPlanner.plan(schedule(days = "WED", start = "23:30", end = "01:30"), now, kolkata)
        )
        assertEquals(at(kolkata, 2026, 9, 16, 23, 30), plan.triggerAtMillis)
        assertEquals(120, ScheduleTime.durationMinutesOrNull("23:30", "01:30"))
    }
}
