package com.example.feature.session.notification

import java.util.Calendar
import java.util.Locale

/**
 * Strict parsing/formatting for the two textual values an automated schedule is built from:
 * a clock time (`startTime`, `endTime`) and a weekday list (`daysOfWeek`).
 *
 * Why this exists as its own object
 * ---------------------------------
 * The previous parsing helpers were lossy: `"garbage".split(":")[0].toIntOrNull() ?: 9` silently
 * became **09:00**, and an unrecognised day list silently became **Mon–Fri**. A malformed schedule
 * therefore did not fail — it fired at a plausible-looking but wrong time, which is the worst
 * possible outcome for a feature whose whole job is to start a protected session at a fixed hour.
 *
 * Every function here is *total and explicit*: a value either parses to a well-defined result or it
 * returns `null`, and callers are required to decide what to do about the `null` (skip, report,
 * surface to the user). Nothing in this file substitutes a default.
 *
 * Parsing rules
 * -------------
 *  - Clock: `H:mm` or `HH:mm`, 24-hour, minutes required and exactly two digits, hour 0–23,
 *    minute 0–59. Surrounding whitespace is tolerated; anything else is invalid.
 *    `"9:00"`, `"09:00"` and `" 21:30 "` are valid. `"9"`, `"9:5"`, `"24:00"`, `"09:60"`,
 *    `"9am"`, `"noon"` and `""` are not.
 *  - Days: comma / space / slash separated tokens, each either a weekday abbreviation
 *    (`MON`, `TUE`/`TUES`, `WED`, `THU`/`THUR`/`THURS`, `FRI`, `SAT`, `SUN`, case-insensitive, any
 *    suffix so `MONDAY` works) or a number in the app's documented 1–7 scheme
 *    (`1 = SUN … 7 = SAT`, i.e. exactly [Calendar]'s own constants). An empty list, an unknown
 *    token, or a mixture containing an unknown token is invalid — a partially understood day list
 *    would schedule *some* days and silently drop others, which is the same class of bug.
 *
 * The number scheme is deliberately `Calendar.SUNDAY … Calendar.SATURDAY` (1…7) rather than the
 * ISO 1 = Monday convention: it is what the persisted data and the previous implementation used,
 * so existing rows keep their meaning.
 */
object ScheduleTime {

    /** Minutes in one day; also the modulus for midnight-crossing durations. */
    const val MINUTES_PER_DAY: Int = 24 * 60

    // ---- clock ------------------------------------------------------------------------

    /**
     * Parses `HH:mm` into minutes-of-day (0…1439), or returns null when [raw] is not a valid clock
     * time. Never falls back to a default.
     */
    fun parseToMinutesOrNull(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        val parts = text.split(":")
        if (parts.size != 2) return null

        val hourText = parts[0].trim()
        val minuteText = parts[1].trim()
        if (hourText.isEmpty() || minuteText.isEmpty()) return null
        // Digits only: rejects signs, whitespace inside, and any locale-specific numerals.
        if (!hourText.all { it in '0'..'9' }) return null
        if (!minuteText.all { it in '0'..'9' }) return null
        // "9:5" is ambiguous (05 or 50 minutes?) so it is rejected rather than guessed.
        if (hourText.length > 2 || minuteText.length != 2) return null

        val hour = hourText.toInt()
        val minute = minuteText.toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** True when [raw] is a well-formed `H:mm` / `HH:mm` clock time. */
    fun isValidClock(raw: String?): Boolean = parseToMinutesOrNull(raw) != null

    /** Formats minutes-of-day as `HH:mm`, wrapping at midnight (1440 → `00:00`). */
    fun format(minutesOfDay: Int): String = String.format(
        Locale.ROOT,
        "%02d:%02d",
        Math.floorMod(minutesOfDay, MINUTES_PER_DAY) / 60,
        Math.floorMod(minutesOfDay, MINUTES_PER_DAY) % 60
    )

    // ---- weekday lists ----------------------------------------------------------------

    /**
     * Parses a stored `daysOfWeek` value into [Calendar] day-of-week constants.
     *
     * Returns null when the string is empty or contains any token that is not a recognised weekday
     * — a schedule with an unparsable day list must be reported, not silently rescheduled onto a
     * default set of days.
     */
    fun parseDaysOfWeekOrNull(raw: String?): Set<Int>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        val tokens = text.split(',', ' ', '/', '|', ';', '-')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val days = mutableSetOf<Int>()
        for (token in tokens) {
            val day = parseDayTokenOrNull(token) ?: return null
            days.add(day)
        }
        return days.ifEmpty { null }
    }

    /** Parses one weekday token: a `MON`-style abbreviation or one of the 1–7 numbers. */
    fun parseDayTokenOrNull(token: String): Int? {
        val text = token.trim().uppercase(Locale.ROOT)
        if (text.isEmpty()) return null

        if (text.all { it in '0'..'9' }) {
            val number = text.toIntOrNull() ?: return null
            return if (number in Calendar.SUNDAY..Calendar.SATURDAY) number else null
        }

        // Exact names/abbreviations (plus the common variants) rather than a prefix match, so
        // "MONDAYISH" or a stray word starting with "SUN" is rejected instead of silently selecting a
        // day the user never chose.
        return ALIASES[text]
    }

    /** Accepted spellings for each weekday, uppercase. */
    private val ALIASES: Map<String, Int> = buildMap {
        fun add(day: Int, vararg names: String) {
            for (name in names) this.put(name, day)
        }
        add(Calendar.SUNDAY, "SUN", "SUNDAY")
        add(Calendar.MONDAY, "MON", "MONDAY")
        add(Calendar.TUESDAY, "TUE", "TUES", "TUESDAY")
        add(Calendar.WEDNESDAY, "WED", "WEDS", "WEDNESDAY")
        add(Calendar.THURSDAY, "THU", "THUR", "THURS", "THURSDAY")
        add(Calendar.FRIDAY, "FRI", "FRIDAY")
        add(Calendar.SATURDAY, "SAT", "SATURDAY")
    }

    /** Renders a day set back into the canonical stored form (`MON,TUE,…`, Sunday last). */
    fun formatDaysOfWeek(days: Set<Int>): String {
        val order = listOf(
            Calendar.MONDAY to "MON",
            Calendar.TUESDAY to "TUE",
            Calendar.WEDNESDAY to "WED",
            Calendar.THURSDAY to "THU",
            Calendar.FRIDAY to "FRI",
            Calendar.SATURDAY to "SAT",
            Calendar.SUNDAY to "SUN"
        )
        return order.filter { days.contains(it.first) }.joinToString(",") { it.second }
    }

    /** Human-readable day list for dialogs/log lines; empty for an empty set. */
    fun describeDaysOfWeek(days: Set<Int>): String {
        if (days.isEmpty()) return ""
        if (days.size == 7) return "Every day"
        val weekdays = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
        )
        if (days == weekdays) return "Weekdays"
        if (days == setOf(Calendar.SATURDAY, Calendar.SUNDAY)) return "Weekends"
        return formatDaysOfWeek(days)
    }

    // ---- durations --------------------------------------------------------------------

    /**
     * Duration of a scheduled window in minutes, handling a window that crosses midnight
     * (e.g. 23:00 → 01:00 = 120 minutes).
     *
     * Returns null when either bound is invalid or the window has zero length. A zero-length window
     * (start == end) is *not* a 24-hour session: the previous code added 24h to any non-positive
     * duration, which silently turned `21:00 → 21:00` into a full-day session.
     */
    fun durationMinutesOrNull(startRaw: String?, endRaw: String?): Int? {
        val start = parseToMinutesOrNull(startRaw) ?: return null
        val end = parseToMinutesOrNull(endRaw) ?: return null
        val duration = Math.floorMod(end - start, MINUTES_PER_DAY)
        return if (duration == 0) null else duration
    }
}
