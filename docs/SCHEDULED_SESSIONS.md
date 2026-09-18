# FocusShield — Automated (scheduled) focus sessions

**Status:** implemented (2026-09-18). Referenced from `AndroidManifest.xml` and the
`feature/session/notification` KDoc.

An automated schedule says *"start a protected focus session on these weekdays at this time"*. The
feature has one non-negotiable rule:

> **Nothing is ever armed or started at a time, on a day, or in a mode the user did not configure.**

Every silent substitution the old implementation had is now an explicit, logged, user-visible
rejection:

| Old behaviour | Now |
| --- | --- |
| Unparsable `startTime` → **09:00** | rejected (`ScheduleTime.parseToMinutesOrNull` returns null) |
| Unparsable `daysOfWeek` → **Mon–Fri** | rejected |
| `startTime == endTime` → **24-hour** session | rejected as a zero-length window |
| Unknown `mode` → **TIMER** | rejected |
| Fixed 14-day search window → `now + 24 h` fallback | exact day-walk, no fallback |
| One-time date further out than 14 days → wrong day/dropped | anchored on its own date, any distance |

## 1. Pipeline

```
FocusScheduleDialog      validates title / days / HH:mm / non-zero window before onSave
        │
FocusScheduleRepository   ScheduleValidation.validate + normalize, then Room + outbox, then alarm
        │
FocusScheduleAlarmScheduler  ScheduleAlarmPlanner.plan(...) → Arm | Skip(reason)
        │                              │
        │                              └── Skip → cancel any existing alarm + log the reason
        ▼
ExactAlarmGate.arm(...)  exact when SCHEDULE_EXACT_ALARM allows it, otherwise a logged inexact
        │                fallback (setAndAllowWhileIdle); never a silent success
        ▼
FocusScheduleReceiver    re-reads the row, notifies, and auto-starts only if
        │                ScheduledSessionFactory accepted the configuration
        ▼
FocusSessionManager.startSession(...) → FocusSessionForegroundService
```

Support pieces:

* `ScheduleTime` — the single strict parser/formatter for `HH:mm` clock times, weekday lists and
  windows that cross midnight. Total functions only: a value parses or it is null.
* `ScheduleAlarmPlanner` — pure "when is the next occurrence?" logic (no `AlarmManager`), so it can
  be reasoned about and tested directly, including DST and month/year boundaries.
* `ScheduledSessionFactory` — pure schedule → session arguments, rejecting rather than defaulting.
* `ScheduleValidation` — the one rule set shared by the dialog, the repository and the tests.
* `ExactAlarmGate` — the one place an alarm is handed to `AlarmManager`, shared by schedules and
  study-plan reminders.

## 2. Alarm planning rules

* **Recurring**: walk the next 8 days, take the earliest candidate whose weekday is selected and
  which is more than the minimum lead (~1 s) away. Each selected weekday occurs exactly once in that
  range, so a single pass is exact for every possible day set — a schedule on one weekday resolves in
  at most 7 days, never "about a day from now".
* **One-time** (`repeatEnabled = false` with a date): fires at that date's `startTime`. If the moment
  has already passed it is skipped with `PAST_ONE_TIME_DATE` — never moved to `now + 24 h`. A
  *legacy* one-time row with no date still falls back to its weekday list, but the planner reports
  that in `Plan.Arm.degradedReason`, which the scheduler logs.
* **DST**: the candidate is built with `Calendar` in the schedule's zone after zeroing the clock
  fields and then adding days, so the wall-clock intent is preserved across offsets. A nonexistent
  local time (spring forward) is normalised forward by the platform; a repeated local time (fall
  back) resolves to its first occurrence.
* **Disabled** schedules are cancelled, not armed.
* **Invalid** schedules (`startTime`, `daysOfWeek`) are skipped with the offending raw value in the
  log line, and any previously armed alarm for that id is cancelled first.

`ScheduleAlarmPlannerTest` covers: today/next-day/weekly roll-over, a once-a-week day list, month and
year boundaries, both DST transitions, one-time dates in the past/future/beyond 14 days, the legacy
no-date fallback, disabled schedules, and every invalid-input rejection.

## 3. Exactness is a permission, not an assumption

`SCHEDULE_EXACT_ALARM` is declared in the manifest; `USE_EXACT_ALARM` is **not** (it is reserved for
alarm-clock/calendar apps and is stripped at install time for anyone else). On Android 12+ the user
can therefore deny "Alarms & reminders", and on Android 14+ it is denied by default.

* Exact request only when `FocusPermissionManager.isExactAlarmGranted(context)` says it can succeed;
  `SecurityException` is still caught because the grant can be revoked between the check and the call.
* Otherwise the alarm is armed with `setAndAllowWhileIdle` and the log line says so. Android 12+ may
  deliver an inexact alarm **up to an hour late** (longer under battery saver/Doze), so a session can
  start late in that configuration — the UI says so rather than pretending:
  * `ProtectionSetupDialog` has an **Exact alarms** row (`exact_alarm_permission_item`) that opens the
    system screen;
  * the planner's schedule section shows an amber `ExactAlarmNotice` (`exact_alarm_notice`) that
    re-checks on resume.
* `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` is handled by
  `BootAndDailyResetReceiver`, which re-arms every enabled schedule so a grant takes effect (and a
  revocation is noticed) without waiting for a reboot.

## 4. Lifecycle events that re-arm everything

`BootAndDailyResetReceiver` handles, and re-plans every enabled schedule for:

* `BOOT_COMPLETED` — alarms do not survive a reboot;
* `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED` — wall-clock schedules move with the zone/DST;
* `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` — see above;
* `ACTION_DAILY_RESET` — FocusShield's own midnight alarm (also re-arms the next midnight).

Re-planning is a full reconcile: enabled schedules are (re-)armed through the planner, and disabled
or invalid ones are cancelled, so no stale trigger survives a change.

## 5. Known gaps

* `FocusScheduleReceiver` itself (notification + auto-start path) has no Robolectric test yet; its
  pure parts (`ScheduledSessionFactory`) and the planner/scheduler decision logic are covered.
* Inexact delivery on Android 12+ is a platform behaviour, not something the app can work around
  without the exact-alarm grant.
* A schedule whose stored configuration is invalid stays invalid until the user edits it — the app
  reports it (log + `ExactAlarmNotice`-style surface) instead of guessing a fix.
