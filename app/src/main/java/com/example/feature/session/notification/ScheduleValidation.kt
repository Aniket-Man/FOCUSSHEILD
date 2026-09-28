package com.example.feature.session.notification

import com.example.data.local.entity.FocusScheduleEntity
import java.util.Calendar
import java.util.TimeZone

/**
 * The single definition of "is this schedule usable?", shared by the editor dialog, the repository
 * and (through the planner) the alarm scheduler.
 *
 * Every rule here replaces a silent substitution that used to exist somewhere in the pipeline:
 *  - an unparsable clock time (previously defaulted to 09:00 while scheduling, and to 09:00 while
 *    auto-starting);
 *  - an unparsable day list (previously defaulted to Mon–Fri);
 *  - a zero-length window (previously "fixed" by adding 24 hours, turning 21:00–21:00 into a full
 *    day);
 *  - an unknown session mode (previously coerced to TIMER);
 *  - a one-time schedule with no date (previously fell back to the weekday list without saying so —
 *    [ScheduleAlarmPlanner] still tolerates that case for already-stored rows, but new writes may not
 *    create it).
 *
 * Pure and Android-free so it is unit-tested directly.
 */
object ScheduleValidation {

    /**
     * Null when [schedule] can be stored and honoured; otherwise a short, user-presentable reason.
     */
    fun validate(schedule: FocusScheduleEntity): String? {
        val start = ScheduleTime.parseToMinutesOrNull(schedule.startTime)
            ?: return "\"${schedule.startTime}\" is not a valid start time (use HH:mm)"
        val end = ScheduleTime.parseToMinutesOrNull(schedule.endTime)
            ?: return "\"${schedule.endTime}\" is not a valid end time (use HH:mm)"
        if (start == end) {
            return "Start and end time cannot be the same; a session needs a non-zero length"
        }
        ScheduleTime.parseDaysOfWeekOrNull(schedule.daysOfWeek)
            ?: return "Choose at least one day of the week (MON…SUN)"
        if (ScheduledSessionFactory.parseModeOrNull(schedule.mode) == null) {
            return "\"${schedule.mode}\" is not a supported session mode"
        }
        if (!schedule.repeatEnabled && schedule.scheduledDateMillis <= 0L) {
            return "A one-time schedule needs a date"
        }
        if (schedule.title.isBlank()) {
            return "Enter a schedule name"
        }
        return null
    }

    /**
     * Returns the schedule as it should be stored: trimmed, with its day list written in the
     * canonical order/spelling so identical day sets always have one stored form. Null when the
     * schedule is invalid (see [validate]).
     */
    fun normalize(schedule: FocusScheduleEntity): FocusScheduleEntity? {
        if (validate(schedule) != null) return null
        val days = ScheduleTime.parseDaysOfWeekOrNull(schedule.daysOfWeek) ?: return null
        val start = ScheduleTime.parseToMinutesOrNull(schedule.startTime) ?: return null
        val end = ScheduleTime.parseToMinutesOrNull(schedule.endTime) ?: return null
        return schedule.copy(
            title = schedule.title.trim(),
            daysOfWeek = ScheduleTime.formatDaysOfWeek(days),
            startTime = ScheduleTime.format(start),
            endTime = ScheduleTime.format(end)
        )
    }

    // ---- overlap -----------------------------------------------------------------------

    /**
     * The first *enabled* schedule in [existing] whose window collides with [candidate], or null.
     *
     * Overlap used to be checked nowhere. Two auto-start schedules could be saved on top of each
     * other, and the collision only showed up at runtime as a single debug line ("A session is
     * already active; not auto-starting …") — the second schedule simply never ran, which reads to
     * the user as the planner failing to protect a window it displays as protected (issue #28).
     * Rejecting the save is the honest place to deal with it: the user can see both windows and fix
     * one.
     *
     * A schedule never collides with itself (compared by id), and disabled schedules are ignored —
     * they arm nothing.
     */
    fun findOverlap(
        candidate: FocusScheduleEntity,
        existing: List<FocusScheduleEntity>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): FocusScheduleEntity? {
        if (!candidate.isEnabled) return null
        return existing.firstOrNull { other ->
            other.id != candidate.id && other.isEnabled && overlaps(candidate, other, timeZone)
        }
    }

    /**
     * True when the two schedules cover any instant in common.
     *
     * Windows are expanded into minutes-of-the-week so a window that runs past midnight
     * (`22:00–01:00`) is compared correctly against the following day, and a week wrap
     * (Sunday 23:00 → Monday 01:00) is compared correctly against Monday.
     *
     * Two one-time schedules are compared by their absolute dates instead: same weekday on
     * different dates is not a collision.
     */
    fun overlaps(
        first: FocusScheduleEntity,
        second: FocusScheduleEntity,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val firstWindow = windowOf(first) ?: return false
        val secondWindow = windowOf(second) ?: return false

        val firstIsOneTime = isOneTime(first)
        val secondIsOneTime = isOneTime(second)

        if (firstIsOneTime && secondIsOneTime) {
            val firstStart = ScheduleAlarmPlanner.oneTimeTrigger(
                first.scheduledDateMillis, firstWindow.startMinutes, timeZone
            )
            val secondStart = ScheduleAlarmPlanner.oneTimeTrigger(
                second.scheduledDateMillis, secondWindow.startMinutes, timeZone
            )
            return firstStart < secondStart + secondWindow.lengthMinutes * MILLIS_PER_MINUTE &&
                secondStart < firstStart + firstWindow.lengthMinutes * MILLIS_PER_MINUTE
        }

        val firstDays = daysOf(first, timeZone) ?: return false
        val secondDays = daysOf(second, timeZone) ?: return false

        val firstIntervals = weekIntervals(firstDays, firstWindow)
        val secondIntervals = weekIntervals(secondDays, secondWindow)
        return firstIntervals.any { a -> secondIntervals.any { b -> a.first < b.second && b.first < a.second } }
    }

    // ---- internals ---------------------------------------------------------------------

    private const val MINUTES_PER_DAY = 24 * 60
    private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY
    private const val MILLIS_PER_MINUTE = 60_000L

    private data class Window(val startMinutes: Int, val lengthMinutes: Int)

    private fun isOneTime(schedule: FocusScheduleEntity): Boolean =
        !schedule.repeatEnabled && schedule.scheduledDateMillis > 0L

    /** Start-of-day offset and length in minutes, or null when the schedule is unusable. */
    private fun windowOf(schedule: FocusScheduleEntity): Window? {
        val start = ScheduleTime.parseToMinutesOrNull(schedule.startTime) ?: return null
        val end = ScheduleTime.parseToMinutesOrNull(schedule.endTime) ?: return null
        if (start == end) return null
        val length = if (end > start) end - start else MINUTES_PER_DAY - start + end
        return Window(start, length)
    }

    /**
     * The `Calendar.DAY_OF_WEEK` values a schedule can start on: its weekday list when recurring,
     * and the single weekday of its date when one-time.
     */
    private fun daysOf(schedule: FocusScheduleEntity, timeZone: TimeZone): Set<Int>? =
        if (isOneTime(schedule)) {
            setOf(
                Calendar.getInstance(timeZone)
                    .apply { timeInMillis = schedule.scheduledDateMillis }
                    .get(Calendar.DAY_OF_WEEK)
            )
        } else {
            ScheduleTime.parseDaysOfWeekOrNull(schedule.daysOfWeek)
        }

    /**
     * Half-open `[from, to)` intervals in minutes-of-the-week, split where they cross the week
     * boundary so a plain numeric comparison is enough.
     */
    private fun weekIntervals(days: Set<Int>, window: Window): List<Pair<Int, Int>> {
        val intervals = mutableListOf<Pair<Int, Int>>()
        for (day in days) {
            // Calendar.SUNDAY == 1, so day - 1 is the 0-based index of the day in the week.
            val from = (day - 1) * MINUTES_PER_DAY + window.startMinutes
            val to = from + window.lengthMinutes
            if (to <= MINUTES_PER_WEEK) {
                intervals += from to to
            } else {
                intervals += from to MINUTES_PER_WEEK
                intervals += 0 to (to - MINUTES_PER_WEEK)
            }
        }
        return intervals
    }
}
