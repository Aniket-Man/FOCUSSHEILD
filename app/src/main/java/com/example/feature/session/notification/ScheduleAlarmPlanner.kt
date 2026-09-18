package com.example.feature.session.notification

import com.example.data.local.entity.FocusScheduleEntity
import java.util.Calendar
import java.util.TimeZone

/**
 * The decision "when should this schedule next fire, if at all?" — separated from the Android
 * alarm plumbing so it can be reasoned about (and tested) without an [android.app.AlarmManager].
 *
 * The previous implementation searched a fixed **14-day** window and, when nothing matched, fell
 * back to `now + 24 hours`. That fallback is wrong in an obvious way: `now + 24h` is the current
 * wall-clock time tomorrow, which is only the configured start time by coincidence. It also could
 * never fire for a day list it failed to parse. On top of that, a 14-day window is silently
 * insufficient for *one-off* schedules whose anchor date is further out (a schedule created for a
 * date three weeks ahead would be rescheduled onto a wrong day, or dropped).
 *
 * This planner is exact instead of windowed: for a recurring schedule and a non-empty validated day
 * set it computes the next occurrence directly (at most one candidate per selected weekday, so at
 * most 8 candidates), and returns `null` only when the configuration itself is unusable. `null` is
 * never turned into a guess — the caller ([FocusScheduleAlarmScheduler]) logs the reason, cancels any
 * stale alarm for that schedule, and (for user-visible surfaces) reports the invalid configuration.
 */
object ScheduleAlarmPlanner {

    /**
     * What to do with one schedule right now.
     */
    sealed interface Plan {

        /** Arm (or re-arm) an alarm at [triggerAtMillis] (epoch millis, UTC). */
        data class Arm(
            val triggerAtMillis: Long,
            /**
             * Non-null when the schedule fired under a documented, still-deterministic fallback
             * (e.g. a one-time schedule with no anchor date falling back to its weekday list).
             * Callers log it; it is never silent.
             */
            val degradedReason: String? = null
        ) : Plan

        /** Do not arm anything; [reason] explains why, and any existing alarm must be cancelled. */
        data class Skip(val reason: Reason, val detail: String) : Plan
    }

    /** Why a schedule produced no trigger. */
    enum class Reason {
        DISABLED,
        INVALID_START_TIME,
        INVALID_DAYS_OF_WEEK,
        PAST_ONE_TIME_DATE,
        NO_FUTURE_OCCURRENCE
    }

    /**
     * Plans the next alarm for [schedule].
     *
     * @param nowMillis "now" in epoch millis (injected so this stays testable and DST-exact).
     * @param timeZone zone the schedule's wall-clock times are interpreted in; the device zone in
     *   production, so a time-zone change is picked up by the reschedule path on the next boot/
     *   time-change broadcast.
     * @param minLeadMillis the trigger must be at least this far in the future; otherwise it is
     *   treated as "already past" (a trigger equal to `now` is useless and would fire immediately).
     */
    fun plan(
        schedule: FocusScheduleEntity,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        minLeadMillis: Long = DEFAULT_MIN_LEAD_MILLIS
    ): Plan {
        if (!schedule.isEnabled) {
            return Plan.Skip(Reason.DISABLED, "schedule '${schedule.title}' is disabled")
        }

        val startMinutes = ScheduleTime.parseToMinutesOrNull(schedule.startTime)
            ?: return Plan.Skip(
                Reason.INVALID_START_TIME,
                "startTime='${schedule.startTime}' is not a valid HH:mm time"
            )

        val isOneTime = !schedule.repeatEnabled && schedule.scheduledDateMillis > 0L
        if (isOneTime) {
            val trigger = oneTimeTrigger(
                anchorDateMillis = schedule.scheduledDateMillis,
                minutesOfDay = startMinutes,
                timeZone = timeZone
            )
            return if (trigger <= nowMillis + minLeadMillis) {
                Plan.Skip(
                    Reason.PAST_ONE_TIME_DATE,
                    "one-time date ${ScheduleTime.format(startMinutes)} on " +
                        "${java.util.Date(schedule.scheduledDateMillis)} is in the past"
                )
            } else {
                Plan.Arm(trigger)
            }
        }

        val days = ScheduleTime.parseDaysOfWeekOrNull(schedule.daysOfWeek)
            ?: return Plan.Skip(
                Reason.INVALID_DAYS_OF_WEEK,
                "daysOfWeek='${schedule.daysOfWeek}' contains no recognisable weekday"
            )

        val next = nextRecurringTriggerOrNull(
            selectedDays = days,
            minutesOfDay = startMinutes,
            nowMillis = nowMillis,
            timeZone = timeZone,
            minLeadMillis = minLeadMillis
        ) ?: return Plan.Skip(
            Reason.NO_FUTURE_OCCURRENCE,
            "no occurrence of ${ScheduleTime.describeDaysOfWeek(days)} at " +
                "${ScheduleTime.format(startMinutes)} found in the next 8 days"
        )

        // A one-time schedule with no anchor date is ambiguous persisted state. Falling back to the
        // weekday list keeps long-standing rows working, but it is reported rather than assumed.
        val degraded = if (!schedule.repeatEnabled) {
            "repeatEnabled=false but scheduledDateMillis=${schedule.scheduledDateMillis}; " +
                "fell back to the weekday list"
        } else {
            null
        }
        return Plan.Arm(next, degraded)
    }

    /**
     * Next epoch-millis occurrence of [minutesOfDay] on any day in [selectedDays], strictly after
     * `nowMillis + minLeadMillis`.
     *
     * Implemented by walking days 0…7 from today and taking the earliest candidate, which is exact
     * for every possible day set (each selected weekday occurs exactly once in that range) and
     * bounded at 8 iterations. `Calendar` does the calendar arithmetic, so:
     *  - a month/year boundary is handled;
     *  - DST transitions are handled (`Calendar` normalises a nonexistent local time forward by the
     *    size of the gap, and a repeated local time resolves to its first occurrence);
     *  - a leap day is handled.
     *
     * Returns null only when [selectedDays] is empty.
     */
    fun nextRecurringTriggerOrNull(
        selectedDays: Set<Int>,
        minutesOfDay: Int,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        minLeadMillis: Long = DEFAULT_MIN_LEAD_MILLIS
    ): Long? {
        if (selectedDays.isEmpty()) return null

        val earliest = nowMillis + minLeadMillis
        var best: Long? = null
        for (offset in 0..7) {
            val candidate = atLocalTime(
                baseMillis = nowMillis,
                timeZone = timeZone,
                dayOffset = offset,
                minutesOfDay = minutesOfDay
            )
            if (!selectedDays.contains(dayOfWeekOf(candidate, timeZone))) continue
            if (candidate.timeInMillis <= earliest) continue
            if (best == null || candidate.timeInMillis < best) best = candidate.timeInMillis
        }
        return best
    }

    /**
     * Epoch millis of `HH:mm` on the calendar date of [anchorDateMillis], interpreted in
     * [timeZone]. Used for one-time schedules, where the user picked both a date and a clock time.
     */
    fun oneTimeTrigger(
        anchorDateMillis: Long,
        minutesOfDay: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = atLocalTime(
        baseMillis = anchorDateMillis,
        timeZone = timeZone,
        dayOffset = 0,
        minutesOfDay = minutesOfDay
    ).timeInMillis

    // ---- internals --------------------------------------------------------------------

    /** Local date of [baseMillis] shifted by [dayOffset] days, at [minutesOfDay]. */
    private fun atLocalTime(
        baseMillis: Long,
        timeZone: TimeZone,
        dayOffset: Int,
        minutesOfDay: Int
    ): Calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = baseMillis
        // Zero the clock fields before adding days: adding a day first and only then setting the
        // time would land on the wrong date when the source time is late in the day and the target
        // is early in the day (DST shifts UTC offsets by an hour either way).
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        set(Calendar.MINUTE, minutesOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun dayOfWeekOf(millis: Calendar, timeZone: TimeZone): Int =
        Calendar.getInstance(timeZone).apply { timeInMillis = millis.timeInMillis }
            .get(Calendar.DAY_OF_WEEK)

    /** A trigger must be at least one second away to be worth arming. */
    const val DEFAULT_MIN_LEAD_MILLIS: Long = 1_000L
}
