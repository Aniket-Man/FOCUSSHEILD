package com.example.feature.session.notification

import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.model.SessionMode

/**
 * Turns an automated schedule into the arguments for a focus session, or explains why it cannot.
 *
 * This is the pure half of [FocusScheduleReceiver]: the parts that used to be inline in the
 * broadcast handler and that were the source of two silent-substitution bugs —
 *
 *  1. an unparsable `startTime`/`endTime` became **09:00** (`parseTimeToMinutes`'s catch-all),
 *     so a corrupt schedule could auto-start a session at a time nobody configured;
 *  2. a non-positive duration was "fixed" by adding 24 hours, so a zero-length window
 *     (`startTime == endTime`, which the picker allows) became a **24-hour** session.
 *
 * Both are now explicit rejections. The receiver logs them and simply does not auto-start, leaving
 * the schedule's configuration untouched for the user to correct — no session is ever invented from
 * data the app could not understand.
 */
object ScheduledSessionFactory {

    /** Everything `FocusSessionManager.startSession` needs for a scheduled session. */
    data class Draft(
        val mode: SessionMode,
        val subject: String,
        val topic: String,
        val goal: String,
        val plannedDurationMillis: Long,
        val blockedAppPackages: Set<String>,
        val blockNotifications: Boolean,
        val breakMinutes: Int
    )

    sealed interface Result {
        data class Ready(val draft: Draft) : Result

        /** The schedule cannot produce a session; [reason] is a log/user-facing explanation. */
        data class Rejected(val reason: String) : Result
    }

    fun draftFor(schedule: FocusScheduleEntity): Result {
        val mode = parseModeOrNull(schedule.mode)
            ?: return Result.Rejected(
                "unrecognised session mode '${schedule.mode}' (expected TIMER, POMODORO or STOPWATCH)"
            )

        val durationMinutes = ScheduleTime.durationMinutesOrNull(schedule.startTime, schedule.endTime)
            ?: return Result.Rejected(
                "invalid or zero-length window '${schedule.startTime}' - '${schedule.endTime}'"
            )

        if (mode == SessionMode.POMODORO && durationMinutes < MIN_POMODORO_MINUTES) {
            return Result.Rejected(
                "pomodoro window of $durationMinutes min is shorter than one $MIN_POMODORO_MINUTES min cycle"
            )
        }

        val subject = schedule.subjectName.ifBlank { schedule.title }.ifBlank { "Study Session" }
        val goal = schedule.description.ifBlank {
            "Automated Focus Window (${schedule.startTime} - ${schedule.endTime})"
        }

        return Result.Ready(
            Draft(
                mode = mode,
                subject = subject,
                topic = schedule.description.ifBlank { "Automated Schedule: ${schedule.title}" },
                goal = goal,
                plannedDurationMillis = durationMinutes * 60_000L,
                blockedAppPackages = parsePackageList(schedule.blockedAppPackages),
                blockNotifications = schedule.blockNotifications,
                breakMinutes = schedule.breakMinutes.coerceIn(0, MAX_BREAK_MINUTES)
            )
        )
    }

    /**
     * Strict mode-string parsing. Only the three real modes are accepted; an unknown value is
     * rejected rather than coerced to TIMER (see the class doc).
     */
    fun parseModeOrNull(raw: String?): SessionMode? {
        val text = raw?.trim()?.uppercase() ?: return null
        return SessionMode.entries.firstOrNull { it.name == text }
    }

    /** Comma-separated package names → trimmed, non-empty, de-duplicated set. */
    fun parsePackageList(raw: String?): Set<String> = raw
        .orEmpty()
        .split(',')
        .map { it.trim() }
        .filterTo(linkedSetOf()) { it.isNotEmpty() }

    /** One 25/5 pomodoro cycle needs at least this much wall-clock time to be meaningful. */
    private const val MIN_POMODORO_MINUTES = 25

    /** Manual break length offered during the session; keeps a corrupt value out of the UI. */
    private const val MAX_BREAK_MINUTES = 180
}
