package com.example.feature.session.notification

import com.example.data.local.entity.FocusScheduleEntity

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
}
