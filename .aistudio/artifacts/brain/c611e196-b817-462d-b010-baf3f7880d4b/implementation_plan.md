# FocusShield Performance Optimization, Break Timer Fix, Overlay Reliability & Planner Sync

This plan resolves UI lag and frame jitter across the application, fixes the frozen break countdown timer during active focus sessions, ensures the App Limit and Focus Session blocking overlays appear reliably every time a restricted app is opened, and unifies the Home screen's Today's Plan section directly with the Planner tab.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and technical decisions incorporate your confirmed preferences and root-cause diagnostics from the codebase:

- **Confirmed Decision 1 (Home Screen Today's Plan & Planner Sync)**: The "+ Add Study" button on the Home screen will be removed. The Home screen's "Today's Plan" section will automatically sync from the schedules configured in the Planner tab for the current day. Tapping a plan item on the Home screen will let you start its focus session immediately or jump to the Planner tab to edit it.
- **Confirmed Decision 2 (App Limit & Session Block Overlay Reliability)**: Both the floating window overlay and fallback activity overlay pipelines will be hardened so transient system window events (such as SystemUI, status/navigation bars, or keyboard popups) cannot prematurely dismiss the App Limit overlay, and re-opening a limited app always triggers the prompt immediately.
- **Confirmed Decision 3 (Break Timer Auto-Resume & Alert)**: The break countdown will tick smoothly every second on screen. When the break reaches `00:00`, the focus session will automatically resume counting down and fire a break-ended alert notification.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms FocusShield into a responsive, 60/120fps experience with accurate real-time session and break timers, rock-solid app limit and distraction shield overlays, and a single source of truth for daily schedules managed through the Planner tab.
- **Target Audience / Persona**: Students and deep-work practitioners who rely on uninterrupted focus timers, strict daily app usage quotas, and structured daily timetables without UI stutter or missed blocks.
- **Key Value**: Eliminates main-thread freezes during navigation and scrolling, restores live visual feedback during session breaks, closes window-transition loopholes that prevented the App Limit popup from appearing, and removes duplicate schedule management between Home and Planner.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Smooth Navigation & Scrolling**: Switching between Home, Planner, Stats, Blocks, and Profile tabs responds instantaneously without frame drops.
  2. **Taking a Break During an Active Session**: Tapping "Take Break" pauses the main study timer and displays the active break countdown banner ticking down second-by-second (e.g., `04:59`, `04:58` ... `00:00`). When the break finishes, the study timer automatically resumes and an alert notification is sent.
  3. **Opening a Limited or Blocked App**: Opening an app with an active App Limit (or during a Focus Session) immediately displays the App Limit usage-selection / time's-up overlay on top of the restricted app—whether launched from the home launcher, Recents, gesture navigation, or notification shade.
  4. **Viewing & Starting Today's Plan from Home**: The Home screen's "Today's Plan" header is clean (no "+ Add Study" button) and displays today's scheduled blocks from the Planner. Users can tap Play/Start on any item to launch that focus session or tap the item/summary card to open the Planner tab for edits.
- **Visual Identity & Theme**:
  - Preserves FocusShield's unified **Daylight Study** (`#F3ECFF` background, `#FFFFFF` surface, `#6366F1` primary indigo) and **Late-Night Study** (`#090A0F` obsidian background, `#13141C` elevated charcoal surface, `#8B5CF6` neon violet) palettes across all cards, dialogs, and overlays.
- **Interactive Feedback & Motion**:
  - Sub-200ms navigation transitions with zero main-thread I/O contention.
  - Whole-second timer state emissions so circular progress rings and digital clocks animate cleanly without redundant half-second recompositions.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Offloading UsageStats IPC & Analytics Aggregation off the Main Thread**
  - *Chosen Approach*: Move all `UsageStatsManager` system queries, streak calculations, and 7-day chart aggregations onto background worker threads (`Dispatchers.Default` / `Dispatchers.IO`), and scope screen ViewModels lazily to their navigation destinations rather than instantiating all 11 ViewModels simultaneously on startup.
  - *Why*: Android's `UsageStatsManager.queryEvents()` parses thousands of daily system events over Binder IPC. Running this inside flows collected on the main UI thread caused 150–400ms frame stalls whenever the Home or Stats screen recomposed.
  - *Alternatives Considered*: Reducing analytics detail was rejected because full accuracy can be preserved simply by executing the queries on background dispatchers and caching results appropriately.

- **Decision 2: Fixing StateFlow Structural Equality Deduplication on Active Breaks**
  - *Chosen Approach*: Update `ManualBreakInfo` on each timer tick with the live elapsed/remaining break duration (and emit once per whole second) so `MutableStateFlow` recognizes each second of the break as a new state snapshot.
  - *Why*: Previously, `ManualBreakInfo` held static `startedAt` and `endsAt` timestamps with `actualDurationMillis = 0L` throughout the break. Because no field inside `FocusSession` or `ManualBreakInfo` changed during the 5-minute countdown, Kotlin's `MutableStateFlow` treated every tick copy as structurally equal (`old == new`) and dropped all emissions for 5 minutes until the break expired.

- **Decision 3: Hardening App Limit & Focus Block Overlays Against SystemUI Window Noise**
  - *Chosen Approach*: Filter out non-app system packages (`com.android.systemui`, `android`, keyboards/IMEs, permission controllers, and FocusShield's own overlay window events) before `AppLimitFloatingOverlayManager` checks for foreground package exit, and add a time-bounded auto-reset to the re-entry gate when the user closes an app from the blocker.
  - *Why*: Presenting the floating overlay or opening an app triggers `TYPE_WINDOWS_CHANGED` events from `com.android.systemui` (status/navigation bars). Previously, `AppLimitFloatingOverlayManager` treated `com.android.systemui` as the user switching to a different app and immediately hid the overlay within milliseconds of showing it. Additionally, if the home launcher event was missed after tapping "Close App", the re-entry gate stayed armed and suppressed future popups on re-entry.

- **Decision 4: Single-Source Planner Synchronization for Today's Plan**
  - *Chosen Approach*: Remove the "+ Add Study" pill button from the Home screen and drive the Home screen's "Today's Plan" list and summary metrics directly from the Planner's active schedules for the current day of the week (merged with any existing today plan items).
  - *Why*: Having two separate creation flows (Home "Add Study" vs. Planner schedules) caused confusion where schedules created in the Planner did not reflect as Today's Plan items on the Home screen.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        BACKGROUND COMPUTATION LAYER                         │
│                                                                             │
│  ┌──────────────────────────┐        ┌───────────────────────────────────┐  │
│  │  Analytics & UsageStats  │        │  FocusSessionManager & BreakTimer │  │
│  │  (Dispatchers.Default)   │        │     (1s Whole-Second Ticker)      │  │
│  │  • Off-main IPC queries  │        │  • Mutates ManualBreakInfo.elapsed│  │
│  │  • Cached app icons/meta │        │  • Auto-resumes & fires alert at 0│  │
│  └────────────┬─────────────┘        └─────────────────┬─────────────────┘  │
└───────────────┼────────────────────────────────────────┼────────────────────┘
                │                                        │
                ▼                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         UI & NAVIGATION LAYER (60/120 FPS)                  │
│                                                                             │
│  ┌──────────────────────────────────┐    ┌───────────────────────────────┐  │
│  │          Planner Tab             │───►│     Home Screen Dashboard     │  │
│  │  • Single source of schedules    │    │  • No "+ Add Study" button    │  │
│  │  • Day-of-week & time blocks     │◄───│  • Syncs Today's Plan live    │  │
│  └──────────────────────────────────┘    │  • Tap to Start or Edit       │  │
│                                          └───────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ACCESSIBILITY & OVERLAY ENFORCEMENT                     │
│                                                                             │
│  [Window / Content Event] ──► [Package Filter: Ignore SystemUI / IME]       │
│                                      │                                      │
│         ┌────────────────────────────┴────────────────────────────┐         │
│         ▼                                                         ▼         │
│  ┌──────────────────────────────────┐    ┌───────────────────────────────┐  │
│  │       App Limit Manager          │    │     Focus Blocker Manager     │  │
│  │  • Re-entry gate auto-disarms    │    │  • Evaluates Window + Windows │  │
│  │  • Floating overlay stays pinned │    │    Changed + Content events   │  │
│  │  • Verified Activity fallback    │    │  • Handles onNewIntent cleanly│  │
│  └──────────────────────────────────┘    └───────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

1. **Performance & Frame Pacing Optimization**:
   - Apply `.flowOn(Dispatchers.Default)` to `todaySummaryFlow`, `getPeriodAnalyticsFlow`, and `HomeViewModel.processedDataFlow` so all `UsageStatsManager` IPC queries, date clamping, and chart calculations never touch the UI thread.
   - Scope secondary ViewModels (`BlockedAppsViewModel`, `AnalyticsViewModel`, `HistoryViewModel`, `WebsiteBlockerViewModel`, `StudyChannelsViewModel`, `AccountViewModel`) inside their respective navigation destinations and cache installed application labels/icons in memory.
   - Change `FocusSessionManager` and `FocusSessionForegroundService` notification updates to emit only when the displayed whole second or session state actually changes, cutting recomposition and notification Binder calls by 50%.

2. **Break Timer Live Countdown & Auto-Resume**:
   - Add a `remainingMillis` / `actualDurationMillis` update on every tick inside `BreakManager` and `FocusSessionManager.syncStateFromEngines(now)`.
   - Because `ManualBreakInfo` updates its elapsed/remaining field on every tick, `FocusSessionManager.activeSession` emits a fresh state every second, driving `SessionViewModel.activeSessionState.breakRemainingFormatted` smoothly from `05:00` down to `00:00`.
   - Upon reaching `00:00`, `BreakManager.checkExpiration(now)` transitions the session back to `RUNNING`, resumes the underlying `TimerEngine` / `StopwatchEngine` / `PomodoroEngine`, and triggers `SessionNotificationHelper.notifyBreakExpired()`.

3. **App Limit & Session Block Overlay Reliability**:
   - In `AppLimitManager.onForegroundPackageChanged`, validate `AppLimitPackageFilter.shouldTrackForeground(packageName)` *before* notifying `AppLimitFloatingOverlayManager.onForegroundPackageChanged(packageName)`, preventing `com.android.systemui`, `"android"`, keyboards, and permission controllers from dismissing an active App Limit overlay.
   - Update `AppLimitReentryGate` so that dismissing a blocker suppresses trailing teardown events from the closing app for a bounded window (1.5 seconds) and automatically marks the exit complete, ensuring that re-opening the same limited app from any launcher or recents switcher always triggers the App Limit overlay.
   - In `FocusAccessibilityService`, evaluate active Focus Session app blocking on `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOWS_CHANGED`, and throttled `TYPE_WINDOW_CONTENT_CHANGED` events, and implement `onNewIntent` state updates in `BlockOverlayActivity`.

4. **Home Screen Today's Plan Synced with Planner**:
   - Remove the `+ Add Study` pill button (`add_study_plan_button`) from `HomeScreen` and update the empty-state prompt to guide users to the Planner tab.
   - In `HomeViewModel`, combine `focusScheduleRepository.allSchedules` (filtered for schedules enabled and scheduled for today's day of the week) into Today's Plan (`mappedPlans`), calculating `totalPlannedTime`, `totalCompletedTime`, `remainingPlanTime`, and overlap warnings directly from the Planner's today schedule.
   - Tapping "Start" on a Today's Plan item launches the focus session configured with that schedule's subject, duration, and blocking settings; tapping Edit/Card navigates directly to the Planner tab.
