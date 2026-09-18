package com.example.cloud.sync

/**
 * A cloud row could not be converted into a local entity because a field is missing, has the wrong
 * type, or holds a value the app does not understand.
 *
 * Why this is an exception rather than a default
 * ----------------------------------------------
 * Cloud parsing used to substitute a plausible value for anything unusable: an unknown
 * `session_records.mode` became `TIMER`, an unknown `study_activities.activityType` became
 * `FOCUS_SESSION`, and a missing `id` became the empty string. The row then looked valid — a timer
 * session that was really a pomodoro, or two different rows both keyed on `""` — so corrupted or
 * newer-schema cloud data silently changed behaviour instead of being reported.
 *
 * A row that cannot be understood is now **rejected as a whole**: [SyncEngine] does not insert it,
 * records the reason on [SyncTracker] (log + bounded in-memory list) and leaves the next cycle to
 * retry. Nothing is invented, and nothing is lost silently — the row still exists in the cloud and
 * in the outbox, and the diagnostic names the exact column.
 *
 * Not thrown for cosmetic fields (colours, display text, nullable notes): those keep their documented
 * defaults, because a wrong colour is not a correctness problem and refusing the whole row over one
 * would lose real history.
 */
class CloudDataException(
    val table: String,
    val field: String,
    val value: Any?,
    reason: String
) : Exception("$table.$field: $reason (value=$value)")
