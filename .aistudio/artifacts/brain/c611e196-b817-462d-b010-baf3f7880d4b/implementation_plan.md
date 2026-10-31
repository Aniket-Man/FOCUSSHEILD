# Schedule Planner Wall-Clock Start & Stop Overhaul

Transform the Schedule Planner from a duration-based study timer preset into a true wall-clock schedule engine where every schedule defines **exact clock times when the focus session starts and when it stops**, while independently tracking actual focused study time inside that window.

## User Review & Critical Decisions

> [!IMPORTANT]
> All core scheduling behavior decisions have been confirmed and incorporated into this plan:

- **Confirmed Decision 1 — Exact Scheduled End-Time Cutoff**: A scheduled session (e.g., `8:00 PM – 10:00 PM`) always stops at its exact scheduled End Time (`10:00 PM`). Taking a pause or break during the window does not push the end time past `10:00 PM`, while analytics still record only the actual active study minutes.
- **Confirmed Decision 2 — Immediate Mid-Window Auto-Start for Remaining Time**: If a user creates, enables, or opens a schedule while the current clock time is already inside its active `[Start Time, End Time)` window (e.g., at `8:20 PM` during an `8:00 PM – 10:00 PM` schedule), the session automatically starts immediately for the exact remaining wall-clock time (`1 hr 40 min`) until End Time.
- **Confirmed Decision 3 — Pure Start-to-End Clock Window Editor**: Remove the manual `Timer | Stopwatch | Pomodoro` mode tabs from the Add/Edit Schedule screen so schedules are configured purely around a **Start Time → End Time** clock window, repetition rule (`Repeat for Entire Week` vs. `One-Time Schedule`), and focus enforcement settings.

---

## 1. Overview & Core Concept

- **What It Does**: Re-architects the Schedule Planner so schedules govern **when a session starts and when it stops on the real-world clock**, rather than acting as a fixed study-duration counter that drifts whenever a session starts late or is paused.
- **Target Audience / Persona**: Students and self-directed learners who plan structured daily or weekly time blocks (e.g., `8:00 PM – 10:00 PM Evening Revision`) and expect the timer to start on time, run through that exact clock window, and stop cleanly at `10:00 PM` before their next activity.
- **Key Value**:
  - **Predictable Clock Boundaries**: An `8:00 PM – 10:00 PM` schedule is guaranteed to finish at `10:00 PM`—never drifting to `10:15 PM` or `10:30 PM` due to late starts, breaks, or pauses.
  - **Zero Missed Mid-Window Schedules**: Creating or toggling on a schedule whose window has already begun starts immediately for the remaining time instead of waiting until tomorrow or next week.
  - **Accurate Study vs. Window Accounting**: Wall-clock countdown governs when the session stops, while active study elapsed time (excluding breaks and pauses) is recorded to analytics and cloud sync.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Creating or Editing a Schedule (Start-to-End Clock Window)**:
     - The user opens the Add/Edit Schedule modal. The former `Timer | Stopwatch | Pomodoro` mode switcher is removed so the interface focuses immediately on the schedule title, **Start Time**, **End Time**, and a live **Window Summary Banner** (e.g., `Starts at 8:00 PM • Stops at 10:00 PM (2h 0m window)`).
     - Exact minute precision is preserved (`8:45 PM` stays `8:45 PM`), overnight windows (e.g., `11:00 PM – 1:00 AM`) are supported seamlessly, and overlapping windows on the same day are validated before saving.
     - Users choose between **Repeat for Entire Week** or **One-Time Schedule**, select target day(s), and configure focus enforcement (Auto-Start, Focus Mode, Strict Lock, Break policy, Whitelisted apps, and Study tools).
  2. **Saving or Enabling Inside an Active Window**:
     - If the user creates or enables a schedule at `8:30 PM` for an `8:00 PM – 10:00 PM` window with Auto-Start enabled, the planner immediately evaluates the active window and launches the focus session for the remaining `1h 30m` until `10:00 PM`.
  3. **Live Schedule Cards on the Planner Screen**:
     - Each schedule card displays its exact clock window (`08:00 PM – 10:00 PM`) alongside a real-time status badge:
       - **Active Now • Stops at 10:00 PM (Xh Ym left)** when the current clock is inside the window.
       - **Starts at 08:00 PM • Stops at 10:00 PM** when upcoming today.
       - **Completed Today** when today's window has already finished.
     - Tapping the manual start button on a schedule card while inside its window starts the session anchored to the schedule's exact End Time.
  4. **Running a Scheduled Session & Stopping at End Time**:
     - While the scheduled session is running, the countdown displays the remaining wall-clock time until the scheduled End Time (`Stop at 10:00 PM`).
     - If the user pauses the session or takes a break (when allowed), active study time pauses so analytics only count real study effort, while the wall-clock boundary remains anchored to `10:00 PM`.
     - At `10:00 PM` sharp, the session automatically completes (even if currently paused or in a break), logs the actual focused minutes studied during the window, and triggers the completion notification.

- **Visual Identity & Theme**:
  - **Aesthetic Direction**: Clean, high-contrast Material 3 surface hierarchy matching the app's existing focus aesthetic and Digital Wellbeing clarity.
  - **Color Palette & Mood**: Uses semantic Material 3 tokens (`colorScheme.primary` for active window indicators and time pills, `colorScheme.primaryContainer` for the live Start-to-Stop summary banner, `colorScheme.surfaceContainerLow` for schedule cards, and `colorScheme.errorContainer` for overlap validation alerts).
  - **Typography & Hierarchy**: Large tabular numerals for Start Time and End Time pickers (`headlineMedium` / `titleLarge` bold), crisp metadata subtitles (`bodyMedium`), and compact uppercase status pills (`labelSmall`).
  - **Interactive Feedback & Motion**: Live updating window duration summary in the schedule editor, immediate visual status transition when a schedule enters its active window, and smooth progress indicators on active schedule cards.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Decoupling Wall-Clock Cutoff (`scheduledEndTimeMillis`) from Active Study Elapsed Time (`elapsedDurationMillis`)**
  - *Chosen Approach*: Attach an explicit wall-clock `scheduledEndTimeMillis` timestamp to scheduled focus sessions. When `scheduledEndTimeMillis > 0`, the session countdown tracks remaining wall-clock time (`scheduledEndTimeMillis - now`) and strictly completes when `now >= scheduledEndTimeMillis`, while `elapsedDurationMillis` only increments during active `RUNNING` focus ticks (pausing during breaks or pauses).
  - *Why*: Previously, the timer treated schedules as a fixed duration (`plannedDurationMillis = end - start`) and counted down only while actively studying. Starting 15 minutes late or pausing for 10 minutes caused the session to run past its scheduled stop time, breaking the user's daily timetable. Decoupling wall-clock stop time from active study time guarantees the timer stops on schedule while keeping study statistics 100% accurate.
  - *Alternatives Considered*: Letting pauses push back the end time (rejected by user preference, as schedules must stop at the exact scheduled end time).

- **Decision 2 — Full Active-Window Detection (`[windowStartMillis, windowEndMillis)`) with Per-Window Deduplication**
  - *Chosen Approach*: Replace the narrow 1-minute alarm check (`minuteDelta in 0..1`) with a full active-window bounds check (`nowMillis in windowStartMillis until windowEndMillis`), paired with a deterministic per-window deduplication key (`scheduleId + windowStartMillis`) and immediate evaluation whenever a schedule is created, edited, enabled, or when the app opens.
  - *Why*: Previously, if an alarm fired 2 minutes late due to OS battery optimization, or if a user created/enabled an `8:00 PM – 10:00 PM` schedule at `8:05 PM`, `minuteDelta in 0..1` failed and the schedule was skipped until tomorrow. Checking the full active window and starting for `windowEndMillis - nowMillis` ensures 100% reliability while the deduplication key prevents duplicate restarts within the same window if the user intentionally stops early.
  - *Alternatives Considered*: Only triggering at the exact start minute (rejected because late alarms or mid-window schedule edits fail to start).

- **Decision 3 — Streamlined Start-to-End Clock Window Editor**
  - *Chosen Approach*: Remove the `Timer | Stopwatch | Pomodoro` mode selector from the schedule editor and standardize all planner schedules on wall-clock Start-to-End windows, with a dedicated Start → Stop summary banner.
  - *Why*: Stopwatch (open-ended count-up) and Pomodoro cycle presets conflicted with the mental model of a time-table schedule that starts at `Start Time` and stops at `End Time`.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        Schedule Planner UI Layer                           │
│  ┌──────────────────────────────────┐  ┌────────────────────────────────┐  │
│  │        PlannerScreen             │  │     FocusScheduleDialog        │  │
│  │ • Today / Weekly Timeline        │  │ • Pure Start → End Time Picker │  │
│  │ • Live Window Status Badges      │  │ • Live Start/Stop Window Pill  │  │
│  │ • Manual Start (Remaining Time)  │  │ • Repeat Week vs One-Time      │  │
│  └───────────────┬──────────────────┘  └───────────────┬────────────────┘  │
└──────────────────┼─────────────────────────────────────┼───────────────────┘
                   │                                     │
                   ▼                                     ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   Schedule Persistence & Alarm Layer                       │
│  ┌──────────────────────────────────┐  ┌────────────────────────────────┐  │
│  │     FocusScheduleRepository      │  │  ScheduleValidation / Time     │  │
│  │ • Normalize HH:mm (exact mins)   │─▶│ • activeWindowBoundsMillis()   │  │
│  │ • Save & Sync Alarm Triggers     │  │ • nextUpcomingWindowBounds()   │  │
│  │ • Immediate In-Window Evaluation │  │ • Overnight & One-Time Pruning │  │
│  └───────────────┬──────────────────┘  └───────────────┬────────────────┘  │
│                  │                                     │                   │
│                  ▼                                     ▼                   │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │        FocusScheduleAlarmScheduler & FocusScheduleReceiver           │  │
│  │ • Exact Start Alarm + End-of-Window Cutoff Check                     │  │
│  │ • Full Active-Window Detection (now in [windowStart, windowEnd))     │  │
│  │ • Per-Window Deduplication Key (scheduleId + windowStartMillis)      │  │
│  └───────────────────────────────┬──────────────────────────────────────┘  │
└──────────────────────────────────┼─────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                     Wall-Clock Focus Session Engine                        │
│  ┌───────────────────────────────┐     ┌────────────────────────────────┐  │
│  │    ScheduledSessionFactory    │────▶│      FocusSessionManager       │  │
│  │ • Computes windowEndMillis    │     │ • Wall-Clock Countdown to End  │  │
│  │ • Remaining Duration =        │     │ • Separate Active Study Timer  │  │
│  │   (windowEndMillis - now)     │     │ • Exact End-Time Auto-Stop     │  │
│  └───────────────────────────────┘     └────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **Active Window Bounds (`WindowBounds`)**:
  - Represents a concrete calendar occurrence of a schedule with `windowStartMillis: Long` and `windowEndMillis: Long`.
  - Handles both same-day windows (e.g., `20:00 – 22:00` today) and overnight windows that started yesterday before midnight and end today after midnight (e.g., `23:00 – 01:00`).
- **Scheduled Session Draft & Active Session State**:
  - `scheduledEndTimeMillis: Long`: Exact epoch millisecond timestamp when the scheduled window ends (`0L` for unscheduled manual timers).
  - `plannedDurationMillis: Long`: Set to `(windowEndMillis - startWallClockMillis)` so even if a schedule starts mid-window, its planned duration matches the remaining window length.
  - `remainingDurationMillis: Long`: When `scheduledEndTimeMillis > 0L`, continuously synced to `(scheduledEndTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)` on every 1-second tick.
  - `elapsedDurationMillis: Long`: Increments strictly during `SessionStatus.RUNNING` ticks so pauses and breaks are excluded from recorded study duration.

### Interactive Component & State Mapping

- **Adding / Updating / Enabling a Schedule**:
  1. User selects Start Time and End Time in the schedule dialog and taps Save, or toggles the enable switch on a schedule card.
  2. Repository normalizes times (preserving exact minutes), persists the schedule, arms the next exact alarm, and immediately invokes in-process active-window evaluation.
  3. If `isEnabled && isAutoStartSession` and `nowMillis` is inside `[windowStartMillis, windowEndMillis)`, the receiver verifies that `windowStartMillis` has not already been auto-started for this schedule, constructs a session draft with `scheduledEndTimeMillis = windowEndMillis` and `plannedDurationMillis = windowEndMillis - nowMillis`, and starts the foreground focus session immediately.
- **Manual Start from a Schedule Card**:
  1. When the user taps Start on a schedule card while `nowMillis` is inside `[windowStartMillis, windowEndMillis)`, the session starts with `scheduledEndTimeMillis = windowEndMillis` and `plannedDurationMillis = windowEndMillis - nowMillis`.
  2. If tapped outside the scheduled window (e.g., starting a schedule early), it starts with `scheduledEndTimeMillis = todayUpcomingEndMillis` (if starting early on the same day before `endTime`) or the window's span from `now`.
- **Wall-Clock Tick, Pause/Break Handling, and End-Time Stop**:
  1. Every second during an active scheduled session (`scheduledEndTimeMillis > 0L`), the engine checks `val wallRemaining = (scheduledEndTimeMillis - now).coerceAtLeast(0L)`.
  2. While `status == RUNNING`, `elapsedDurationMillis` advances by 1 second per tick and `remainingDurationMillis` updates to `wallRemaining`.
  3. While `status == PAUSED` or on a manual break, `elapsedDurationMillis` stays frozen, while a background wall-clock watcher continues checking `now >= scheduledEndTimeMillis`.
  4. As soon as `now >= scheduledEndTimeMillis` (or upon disk state restoration if `now >= scheduledEndTimeMillis`), the session immediately transitions to `COMPLETED`, records the actual `elapsedDurationMillis` studied, prunes the schedule if `One-Time`, and notifies the user.
