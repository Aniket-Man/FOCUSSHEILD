package com.example.feature.applimits.engine

/**
 * Decides whether the "your time is almost up" reminder may be posted (issue #54).
 *
 * The reminder used to be a pure function of the session clock, evaluated on every 500 ms tick of
 * `AppLimitManager.startTicker()`. That ticker keeps running while the session is *paused* (the
 * user left the app; only `elapsedMillis` freezes), the reminder check sat outside the paused
 * branch, and its only de-duplication was a repeating two-minute window — so a session parked with
 * under a minute left kept notifying the user, forever, about an app they were not using.
 *
 * The policy is therefore: the reminder belongs to *actual use*. It may fire only while the
 * session is running and the limited app is the app on screen, and only once for a given session.
 * Nothing here depends on FocusShield itself being in the foreground — the reminder is raised from
 * the engine, so it keeps working while FocusShield is in the background.
 */
object AppLimitReminderPolicy {

    /** Inclusive lower bound (seconds). Zero means the session is over; that is the blocker's job. */
    const val WINDOW_START_SECONDS: Long = 1L

    /** Inclusive upper bound (seconds): "less than a minute left". */
    const val WINDOW_END_SECONDS: Long = 60L

    /**
     * @param remainingMillis remaining time in the temporary session.
     * @param isPaused true while the session clock is parked because the user left the app.
     * @param isLimitedAppInForeground true while the limited app is the tracked foreground app.
     * @param showRemindersSetting the per-app "Show reminders before limit" preference.
     * @param sessionId the session the reminder would belong to.
     * @param lastRemindedSessionId the session a reminder was already posted for, if any.
     */
    fun shouldRemind(
        remainingMillis: Long,
        isPaused: Boolean,
        isLimitedAppInForeground: Boolean,
        showRemindersSetting: Boolean,
        sessionId: String,
        lastRemindedSessionId: String?
    ): Boolean {
        if (!showRemindersSetting) return false
        // Not using the app: a reminder about wrapping up is meaningless, and the paused clock
        // would satisfy the window below on every tick for as long as the session stays parked.
        if (isPaused || !isLimitedAppInForeground) return false
        if (sessionId.isBlank()) return false
        // Per session, not per time window: one session can only ever produce one reminder.
        if (lastRemindedSessionId == sessionId) return false

        val remainingSeconds = remainingMillis / 1000L
        return remainingSeconds in WINDOW_START_SECONDS..WINDOW_END_SECONDS
    }
}
