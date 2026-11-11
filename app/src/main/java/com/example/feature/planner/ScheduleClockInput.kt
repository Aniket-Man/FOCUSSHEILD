package com.example.feature.planner

import com.example.feature.session.notification.ScheduleTime

internal data class ScheduleClockInputResult(
    val time: String?,
    val hourValid: Boolean,
    val minuteValid: Boolean
)

/** Typed clock values are accepted exactly or rejected, never clamped or replaced by stale state. */
internal object ScheduleClockInput {
    fun validate(hourText: String, minuteText: String, is24Hour: Boolean, isAm: Boolean): ScheduleClockInputResult {
        val hour = parseNumber(hourText)
        val minute = parseNumber(minuteText)
        val hourValid = hour != null && hour in (if (is24Hour) 0..23 else 1..12)
        val minuteValid = minute != null && minute in 0..59
        val time = if (hourValid && minuteValid) {
            val hour24 = if (is24Hour) hour!! else hour!! % 12 + if (isAm) 0 else 12
            ScheduleTime.format(hour24 * 60 + minute!!)
        } else null
        return ScheduleClockInputResult(time, hourValid, minuteValid)
    }

    private fun parseNumber(raw: String): Int? {
        val text = raw.trim()
        if (text.isEmpty() || text.length > 2) return null
        val digits = text.map { Character.digit(it, 10) }
        if (digits.any { it < 0 }) return null
        return digits.fold(0) { value, digit -> value * 10 + digit }
    }
}
