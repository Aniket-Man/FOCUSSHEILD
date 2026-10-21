package com.example.feature.applimits.engine

/**
 * The minute choices an app-limit blocker may offer, derived from the time actually left today.
 *
 * The selection screen and the floating overlay used to compute this independently: the screen had
 * dynamic options, while the floating sheet offered a fixed 2/5/10/20 grid filtered down to the
 * remaining minutes. That produced choices the engine had to clamp afterwards (a "2 mins" button
 * with one minute left, or a "1 min" fallback with no allowance at all), so the duration the user
 * tapped was not the duration the session got. Both surfaces now share this one function.
 *
 * The standard grid is kept whenever it fits. When the remaining allowance is smaller, the
 * remaining minutes are appended as an explicit "max" choice instead of silently shortening one
 * of the standard choices.
 *
 * A remaining value of 0 is not treated as exhausted here: the engine decides exhaustion in
 * milliseconds and can legitimately offer a session with 59 seconds left that floors to "0m"
 * (issue #49, C2). Offering the one-minute choice is still correct; the session is clamped to the
 * exact remaining millis when it starts.
 */
object AppLimitDurationOptions {

    private val STANDARD_OPTIONS = listOf(2, 5, 10, 20)

    fun forRemainingMinutes(remainingMinutes: Int): List<Int> {
        val remaining = remainingMinutes.coerceAtLeast(0)
        val options = when {
            remaining <= 1 -> listOf(1)
            remaining < 5 -> listOf(minOf(2, remaining), remaining)
            remaining < 10 -> listOf(2, 5, remaining)
            remaining < 20 -> listOf(2, 5, 10, remaining)
            else -> STANDARD_OPTIONS
        }
        return options.filter { it > 0 }.distinct()
    }
}
