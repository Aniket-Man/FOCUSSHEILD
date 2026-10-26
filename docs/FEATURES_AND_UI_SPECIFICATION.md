# FocusShield: Technical Architecture, Features, and UI Specification

## 1. Executive Summary & Architecture Overview

**FocusShield** is a production-grade, privacy-first Android application designed to eliminate digital distractions, cultivate deep study habits, and enforce disciplined device usage. Built natively with modern Android engineering paradigms (Kotlin, Jetpack Compose, Coroutines, StateFlow, Room Database, and Firebase Firestore), FocusShield combines low-level OS capabilities with a clean Material Design 3 interface.

### 1.1 Core Technology Stack

- **UI Framework:** Jetpack Compose (Material Design 3 with custom dynamic theming)
- **Programming Language:** Kotlin 2.0+
- **Architecture Pattern:** MVVM (Model-View-ViewModel) with unidirectional data flow (UDF) and immutable UI states
- **Local Persistence:** Room Database v2.6+ with SQLite, 18 Entity models, 15 DAOs, TypeConverters, and Transaction management
- **Cloud Synchronization:** Firebase Cloud Firestore with zero-trust security rules and Jetpack Credential Manager (Google Sign-In)
- **Concurrency & Asynchrony:** Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Default`, `Dispatchers.Main.immediate`) and reactive `StateFlow` / `SharedFlow`
- **Dependency Injection:** Dagger Hilt & standard constructor-based view-model providers
- **Image Loading & Disk Caching:** Coil v2 with custom OkHttpClient disk caching for remote channel avatars
- **Audio Synthesis & Playback:** Android `MediaPlayer` / `SoundPool` for generative ambient focus soundscapes (Rain, Waves, White Noise, Forest, Cafe)

### 1.2 Android System Integrations & OS Services

| Service / Component | Class Name | Permissions Required | Technical Role |
| :--- | :--- | :--- | :--- |
| **Accessibility Engine** | `FocusAccessibilityService` | `BIND_ACCESSIBILITY_SERVICE` | Real-time window inspection, package routing, node tree evaluation, YouTube recommendation interception, and synthetic gesture injection (`GLOBAL_ACTION_BACK`). |
| **Session Foreground Service** | `FocusSessionForegroundService` | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` | Non-killable background study timer, ambient sound controller, and persistent lock enforcement across screen-off transitions. |
| **Notification Interceptor** | `FocusNotificationListenerService` | `BIND_NOTIFICATION_LISTENER_SERVICE` | Intercepts, cancels, and caches non-whitelisted notifications during active focus sessions into a post-session vault. |
| **Anti-Uninstall Device Admin** | `FocusDeviceAdminReceiver` | `BIND_DEVICE_ADMIN` | Prevents unauthorized uninstallation or force-closing of FocusShield while an active Strict Mode session is locked. |
| **Alarm & Schedule Dispatcher** | `FocusScheduleReceiver`, `StudyPlanReminderReceiver`, `BootAndDailyResetReceiver` | `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK` | Exact and inexact alarm dispatching via `AlarmManager` for scheduled timetable intervals, daily usage resets, and study reminders. |
| **Full-Screen Interception Overlays** | `BlockOverlayActivity`, `AppLimitOverlayActivity` | `SYSTEM_ALERT_WINDOW` | High-priority window overlays rendered on top of restricted applications with custom task affinity and single-instance launch flags. |
| **Glance AppWidgets** | `FocusShieldFocusWidgetReceiver`, `FocusShieldUsageWidgetReceiver` | None | Real-time interactive home screen widgets providing one-tap session control and live daily quota monitoring. |

---

## 2. Navigation Architecture & Global State Flow

FocusShield utilizes **Navigation Compose** with a single-activity architecture (`MainActivity.kt`) managed by `FocusNavGraph.kt`. Navigation transitions feature coordinated fade and horizontal slide animations.

### 2.1 Navigation Graph Topology

```
Screen.Splash (Root Entrance)
   │
   ├── [First Run] ──► Screen.Onboarding (Permissions & Initial Goals)
   │                           │
   └── [Configured] ───────────┴──► Screen.Home (Primary Hub Scaffold)
                                       │
         ┌───────────────┬─────────────┼───────────────┬────────────────┐
         ▼               ▼             ▼               ▼                ▼
    Tab 1: Home     Tab 2: Planner  Tab 3: Stats   Tab 4: Blocks   Tab 5: Profile
         │               │             │               │                │
         ├► ModeSelection├► EditSchedule├► ExportCard  ├► BlockedApps  ├► Account
         ├► SessionSetup ├► CreatePlan ├► HistoryList  ├► StudyChannels├► Settings
         ├► ActiveSession└► ToggleDays └► DetailTrends ├► WebsiteBlock └► Rewards
         ├► Summary                                    ├► AppLimits
         └► ScratchCard                                └► StrictMode
```

### 2.2 Deep Linking & Notification Launch Routing

`FocusNavGraph` implements a **Root-Redirect Gate** to prevent back-stack corruption when launched from external intents:
- **Study Plan Reminders (`ACTION_STUDY_PLAN_REMINDER`):** Carries `PlanLaunchPayload` (subject, topic, target duration, plan ID, autoStart flag). Queued while `Screen.Splash` is resolving and executed once the root scaffold is stable.
- **In-App Update Push (`ACTION_OPEN_UPDATES`):** Navigates directly to `Screen.Update` with launch single-top semantics.
- **BackHandler Integrity:** Every modal, sub-screen, and secondary sheet binds to Jetpack Compose `BackHandler` to preserve navigation stack ordering and prevent accidental drop-out to the Android launcher.

---

## 3. Detailed Technical Breakdown of Every UI Screen

### 3.1 Splash Screen (`Screen.Splash`)
- **Source File:** `com.example.feature.splash.SplashScreen.kt`
- **Purpose:** Cold-start bootstrap, local state verification, and seamless onboarding routing.
- **UI Components:**
  - Full-screen container adapting to `LocalFocusColors.current.background`.
  - Centered animated app mark (`FocusSheild.png` / Vector logo) with smooth scale and alpha interpolation (`Animatable`).
  - Animated progress pulse and typographic brand label (`FocusShield`).
- **Technical Engine:**
  - Asynchronously initializes `FocusPreferencesRepository.isCompletedOnboardingSync()`.
  - Pre-warms `RoomDatabase` connections and verifies `FocusAccessibilityService` binding state.
  - Automatically pops `Screen.Splash` from the backstack to prevent reverse navigation into the splash container.

### 3.2 Onboarding Screen (`Screen.Onboarding`)
- **Source File:** `com.example.feature.onboarding.OnboardingScreen.kt`
- **Purpose:** First-run configuration, value proposition showcase, and sequential system permission acquisition.
- **UI Components:**
  - Horizontal pager with 4 steps: (1) Welcome & Focus Philosophy, (2) Essential System Permissions, (3) Daily Goal Configuration, (4) Cloud Backup Onboarding.
  - Interactive permission cards displaying real-time status indicators (Granted / Required) for:
    - Accessibility Service (`FocusAccessibilityService`)
    - Package Usage Access (`PACKAGE_USAGE_STATS`)
    - System Alert Window Overlay (`SYSTEM_ALERT_WINDOW`)
    - Battery Optimization Whitelist (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
    - Notification Access & Interception (`POST_NOTIFICATIONS`)
  - Primary filled action button (`testTag="onboarding_next_button"`).
- **Technical Engine:**
  - Uses `FocusPermissionManager` to query OS permission tables via reflection and context system services (`AppOpsManager`, `PowerManager`).
  - Invokes system intent dispatchers (`Settings.ACTION_ACCESSIBILITY_SETTINGS`, `Settings.ACTION_USAGE_ACCESS_SETTINGS`, `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`).
  - Writes completion state to DataStore / SharedPreferences upon completion.

### 3.3 Home Dashboard Screen (`Screen.Home`)
- **Source File:** `com.example.feature.home.HomeScreen.kt`
- **ViewModel:** `HomeViewModel.kt`
- **Purpose:** Primary command center displaying real-time focus metrics, quick-start session controls, active study plans, and daily progress.
- **UI Components:**
  - **Header Row:** Dynamic greeting (Morning/Afternoon/Evening), cloud sync status icon badge, and quick link to `Screen.Profile`.
  - **Hero Focus Card:** Circular SVG gauge showing daily completed focus minutes vs. target goal (e.g., 180 min), current focus streak counter with flame glyph, and instant "Start Focus" CTA button (`testTag="home_start_focus_button"`).
  - **Quick Mode Selector Grid:** 4 elevated cards:
    - *Normal Focus:* Standard timer, configurable distraction block.
    - *Deep Work:* Strict mode enabled, emergency pause limits.
    - *Extreme Lock:* Complete app lockdown, cannot be cancelled once initiated.
    - *YouTube Study:* Whitelisted educational channel sandbox.
  - **Active Study Plans Carousel:** Horizontal scrollable list showing user's current academic study plans (e.g., "Organic Chemistry", "Physics Mechanics") with progress bars, due dates, and one-tap launch chips.
  - **Today's Focus Timeline:** Chronological vertical list of completed sessions with subject tags, focus efficiency scores, and earned XP.
  - **Motivational Quote Banner:** Randomized contextual aphorisms curated for productivity.

### 3.4 Mode Selection Screen (`Screen.ModeSelection`)
- **Source File:** `com.example.feature.session.ModeSelectionScreen.kt`
- **Purpose:** Detailed configuration and selection of focus discipline rules prior to session assembly.
- **UI Components:**
  - Vertical list of selectable focus modes with high-contrast icon badges, technical rule descriptions, and restriction badges.
  - Strict Mode parameter toggles: Emergency Pause Allowance (0, 1, or 2 pauses), Penalty Cooldown Timers (10s to 60s), Device Admin protection toggle.
  - "Proceed to Setup" floating action bar.

### 3.5 Session Setup Screen (`Screen.SessionSetup`)
- **Source File:** `com.example.feature.session.SessionSetupScreen.kt`
- **ViewModel:** `SessionViewModel.kt`
- **Purpose:** Fine-tuning session parameters: subject, topic, duration, ambient audio, and custom blocking rules.
- **UI Components:**
  - **Duration Picker Wheel:** Radial and linear presets (15m, 25m Pomodoro, 45m Lecture, 60m Deep Work, 90m Marathon, 120m Mastery, or custom minute slider).
  - **Subject & Topic Selector:** Auto-complete dropdown connected to `SubjectEntity` and `TopicEntity` tables with recent tag chips.
  - **Ambient Soundscape Selector:** Audio chips for generative sound generator:
    - None (Silence)
    - Gentle Rain
    - Ocean Waves
    - Deep White Noise
    - Pine Forest
    - Cozy Coffeehouse
  - **Distraction Shield Checklist:** Dynamic summary of apps, websites, and YouTube restrictions active for this specific session.
  - **Commitment Contract:** Interactive button requiring a 1-second long-press to prevent accidental starts (`testTag="session_setup_start_button"`).

### 3.6 Active Session Screen (`Screen.ActiveSession`)
- **Source File:** `com.example.feature.session.ActiveSessionScreen.kt`
- **ViewModel:** `SessionViewModel.kt`
- **Service Binding:** `FocusSessionForegroundService.kt`
- **Purpose:** Immersive, distraction-free environment displaying the active countdown, real-time focus health, and audio controls.
- **UI Components:**
  - **Immersive Dark/Light Canvas:** Full-bleed background responding to `LocalFocusColors.current`.
  - **SVG Progress Ring:** Animated circular arc indicating percentage elapsed with smooth sub-second interpolation.
  - **Monospaced Countdown Timer:** Scaled typography displaying `HH:mm:ss` remaining.
  - **Session Meta Card:** Displays active subject, topic, and current mode tag (e.g., "YouTube Study Mode").
  - **Ambient Sound HUD:** Mini player controls to mute, adjust volume, or switch ambient background sounds.
  - **Session Controls:**
    - *Pause Button:* Subject to strict mode allowances; displays remaining pause credits and cooldown timers.
    - *End Session Button:* In normal mode, presents a confirmation dialog. In Strict/Extreme modes, initiates a 30-second penalty countdown with motivational friction challenges (e.g., typing a commitment sentence) to prevent impulse quitting.
- **Technical Engine:**
  - Binds to `FocusSessionForegroundService` using an explicit ServiceConnection.
  - Emits tick pulses every 1,000ms via coroutine `StateFlow`.
  - Maintains Android CPU wake-lock (`PowerManager.PARTIAL_WAKE_LOCK`) to ensure precision timing when the display is powered off.

### 3.7 Focus Session Summary Screen (`Screen.SessionSummary`)
- **Source File:** `com.example.feature.session.ui.FocusSessionSummaryScreen.kt`
- **Purpose:** Post-session debriefing, performance analytics, gamification reward distribution, and social sharing.
- **UI Components:**
  - **Celebration Header:** Dynamic visual banner with animated confetti canvas for successfully completed sessions.
  - **Core Metrics Grid:**
    - Total Focus Time (minutes)
    - Focus Efficiency Score (0–100% calculated based on attempts/interruptions)
    - Blocked Distraction Attempts (number of times user attempted to open blocked apps)
    - XP Earned & Level Progression Bar
  - **Scratch Card Reward Trigger:** Interactive gold-foil scratch card widget (`ScratchCardView.kt`) that unlocks bonus XP, study badges, or motivational collectibles.
  - **Reflection Notes Input:** Outlined text area allowing the student to write notes on what was accomplished, persisted to `SessionRecordEntity`.
  - **Social Share Card Generator:** Automatically composites a high-resolution exportable bitmap (`ShareCardGenerator.kt`) with custom typography and session achievements for sharing on social platforms or messaging apps.
  - **Return Home CTA Button:** (`testTag="session_summary_done_button"`).

### 3.8 Planner & Timetable Screen (`Screen.Planner`)
- **Source File:** `com.example.feature.planner.PlannerScreen.kt`
- **Dialog:** `com.example.feature.planner.FocusScheduleDialog.kt`
- **Repository:** `FocusScheduleRepository.kt`, `StudyPlanRepository.kt`
- **Purpose:** Weekly timetable calendar, automated recurring focus sessions, and multi-week study plan manager.
- **UI Components:**
  - **Day-of-Week Selector Strip:** Horizontal row (Mon, Tue, Wed, Thu, Fri, Sat, Sun) highlighting the active selection and showing scheduled event indicators.
  - **Automated Focus Schedules List:**
    - Clean card layout showing scheduled start time, end time, assigned mode, and active toggle switch.
    - Interactive dismissal / deletion with instantaneous database sync and alarm un-registration.
    - Fully harmonized color tokens (`LocalFocusColors.current.surface`, `primaryContainer`, `onSurface`) eliminating hardcoded hex artifacts.
  - **Suggested Schedules Section:** Quick presets (e.g., "Morning Deep Work", "Late Night Review") with one-tap add buttons; dismissed suggestions are permanently remembered in local preferences.
  - **Study Plans Accordion:** Expandable subject cards displaying scheduled topics, target completion dates, exam milestones, and progress checkboxes.
  - **Add Schedule FAB:** Triggers `FocusScheduleDialog` (`testTag="planner_add_schedule_fab"`).
- **Technical Engine:**
  - Interfaces with `FocusScheduleAlarmScheduler` to register exact alarms (`AlarmManager.setExactAndAllowWhileIdle`) on Android 12+ (API 31+) with inexact fallback when permissions are restricted.
  - Handles system broadcasts via `BootAndDailyResetReceiver` to re-arm all active timetable alarms upon device reboot or system timezone adjustments.
  - Idempotent data lifecycle: Removed preloaded default schedules (`"Morning Deep Work"`, `"Afternoon Focus Window"`) are completely eliminated and will never resurrect upon database re-queries.

### 3.9 Analytics & Statistics Screen (`Screen.StatsDetail`)
- **Source File:** `com.example.feature.analytics.AnalyticsScreen.kt`
- **ViewModel:** `AnalyticsViewModel.kt`
- **Purpose:** Deep visual analytics, historical trends, distraction heatmaps, and habit formation metrics.
- **UI Components:**
  - **Timeframe Segmented Selector:** Daily (Today), Weekly (7 Days), Monthly (30 Days), All Time.
  - **Bar & Trend Charts:** Jetpack Compose canvas charts rendering daily focus minutes vs. weekly averages.
  - **Hourly Heatmap Matrix:** Visual 24-hour density grid displaying peak concentration hours during the day.
  - **Distraction Interception Breakdown:** Donut chart illustrating which apps generated the highest distraction volume (e.g., Instagram 42%, TikTok 31%, Twitter 15%).
  - **Streak & Habit Statistics:** Longest unbroken streak, average session duration, and completion success rate percentages.

### 3.10 Blocks Hub Screen (`Screen.Blocks`)
- **Source File:** `com.example.feature.blocks.BlocksScreen.kt`
- **Purpose:** Centralized administrative control room for all 6 blocking sub-systems.
- **UI Components:**
  - 6 large navigation cards with live status badges:
    1. **Blocked Apps:** Quota & whitelist/blacklist management (`Screen.BlockedApps`).
    2. **YouTube Study Mode:** Safe box & educational channel whitelist (`Screen.StudyChannels`).
    3. **Website Blocker:** Adult & custom DNS filtering (`Screen.WebsiteBlocker`).
    4. **App Limits:** Daily usage quotas & allowances (`Screen.AppLimits`).
    5. **Strict Mode:** Self-defense & tamper-proofing (`Screen.StrictMode`).
    6. **Notification Blocker:** Notification suppression & vault (`Screen.SettingsScreen`).

### 3.11 Blocked Apps Management (`Screen.BlockedApps`)
- **Source File:** `com.example.feature.blockedapps.BlockedAppsScreen.kt`
- **ViewModel:** `BlockedAppsViewModel.kt`
- **Purpose:** Package-level application blocking during active focus sessions.
- **UI Components:**
  - Search bar for filtering installed Android applications by label or package name.
  - Category filter chips: All, Social, Games, Entertainment, Productivity.
  - Two-state or three-state switches for individual apps.
  - "Block All Distractions" bulk action button.
- **Technical Engine:**
  - Scans Android device packages via `PackageManager.getInstalledApplications()`.
  - Persists selections to `blocked_apps` Room table (`BlockedAppEntity`).
  - Cached in-memory by `BlockerManager` to achieve sub-millisecond evaluation when `FocusAccessibilityService` detects window transitions.

### 3.12 YouTube Study Mode & Whitelisted Channels (`Screen.StudyChannels`)
- **Source File:** `com.example.feature.channels.StudyChannelsScreen.kt`
- **ViewModel:** `StudyChannelsViewModel.kt`
- **Detector Engine:** `YouTubeContentInspectionEngine.kt`
- **Purpose:** Educational YouTube viewing sandbox. Allows students to watch lectures from approved channels while strictly prohibiting Shorts, home feed rabbit-holes, and recommended distraction videos.
- **UI Components:**
  - Channel search & manual URL/handle addition bar (`@channelHandle` or URL).
  - Whitelist of approved channels displaying channel avatar thumbnail (cached via `ChannelLogoStorageManager`), channel title, subscriber count, and approval toggle switch.
  - "Default Educational Presets" drawer (e.g., Khan Academy, MIT OpenCourseWare, CrashCourse, Veritasium, 3Blue1Brown).
- **Technical Engine & Safe Box Architecture:**
  - **Safe Box Playback Sandbox (`YouTubePlaybackSandbox`):** When an approved video begins playing, the inspection engine enters a passive sandbox state to suspend CPU-intensive accessibility node walks and eliminate UI lag.
  - **Below-Player Recommendation Feed Inspection:** When the user scrolls down to the recommended videos feed beneath an active video and clicks an unapproved video:
    1. `YouTubePlaybackSandbox.onVideoClickedOrChanged()` detects the navigation event and exits the passive sandbox.
    2. The inspection engine walks the watch subtree to extract the new video's title and channel identity.
    3. If the newly clicked video belongs to an unapproved channel, `handleYouTubeUnapprovedChannelBlocked()` is invoked:
       - Sends media pause key events (`KEYCODE_MEDIA_PAUSE`) and clicks the on-screen pause control in the node tree.
       - Dispatches an accessibility `GLOBAL_ACTION_BACK` gesture (after 160ms delay) to immediately pop out of the unapproved video player and return the user to the previous screen.
       - Dismisses any lingering picture-in-picture miniplayer (`YouTubeNodeExtractor.dismissMiniplayerIfPresent`).
       - Displays a Heads-Up Display (HUD) overlay banner notifying the user: *"Channel Blocked: [Channel Name] is not on your approved study list."*

### 3.13 Website & Domain Blocker (`Screen.WebsiteBlocker`)
- **Source File:** `com.example.feature.websiteblocker.ui.WebsiteBlockerScreen.kt`
- **ViewModel:** `WebsiteBlockerViewModel.kt`
- **Engine:** `WebsiteBlockerEngine.kt`
- **Purpose:** Blocks user-listed distracting domains and adult content encountered in Android browsers.
- **UI Components:**
  - Custom domain blacklist input field (e.g., `reddit.com`, `twitter.com`, `twitch.tv`).
  - Quick-add pills for common distracting sites.
  - Per-site enable/disable switch and delete action.
  - Accessibility permission banner explaining why browser URL inspection is required.
- **Technical Engine:**
  - The Accessibility Service inspects browser address bars and passes the current URL to `WebsiteBlockerEngine` for evaluation.
  - Manual matches are blocked with an in-app block screen; during focus sessions the family-DNS adult-content classifier also runs automatically.
  - No VPN or local tunnel is involved.

### 3.14 Daily App Limits Dashboard (`Screen.AppLimits`)
- **Source File:** `com.example.feature.applimits.ui.AppLimitsDashboardScreen.kt`
- **ViewModel:** `AppLimitsViewModel.kt`
- **Overlay Activity:** `AppLimitOverlayActivity.kt`
- **Purpose:** Device-wide daily quota control (independent of active focus sessions).
- **UI Components:**
  - Daily usage breakdown for monitored apps showing consumed time vs. quota (e.g., Instagram: 25m used / 30m limit).
  - Quota configuration dialog with slider and preset chips (15m, 30m, 45m, 1h).
  - Grace Period & Extension rules: Optional 5-minute emergency extension allowance.
- **Technical Engine:**
  - Continuously monitors foreground package transitions via `UsageStatsManager` and `FocusAccessibilityService.evaluateAppLimitGate()`.
  - When quota expires, immediately launches `AppLimitOverlayActivity` (`Theme.TransparentOverlay`, `showWhenLocked="true"`, `launchMode="singleInstance"`).
  - Pauses background media and locks the foreground screen until the daily reset occurs at midnight.

### 3.15 Strict Mode & Anti-Uninstall Security (`Screen.StrictMode`)
- **Source File:** `com.example.feature.strictmode.StrictModeScreen.kt`
- **Receiver:** `FocusDeviceAdminReceiver.kt`
- **Purpose:** Bulletproof anti-tamper mechanisms preventing impulsive cancellation during moments of weakness.
- **UI Components:**
  - Device Administrator activation card with explanation of OS capabilities.
  - Anti-Uninstall switch: Disables the Android "Uninstall" button for FocusShield while locked.
  - Settings App Shield: Automatically blocks access to Android Settings to prevent manual force-stopping or revoking accessibility permissions during a session.
  - Emergency Password / PIN Lock: Allows a parent, partner, or study buddy to set a master override PIN.

### 3.16 Rewards & Gamification Showcase (`Screen.Rewards`)
- **Source File:** `com.example.feature.rewards.ui.RewardsScreen.kt`
- **Scratch Card Engine:** `com.example.feature.rewards.scratch.ScratchCardView.kt`
- **Purpose:** Positive reinforcement engine encouraging consistent daily focus habits.
- **UI Components:**
  - **Player Level & XP Progress:** Visual RPG-style level badge (e.g., "Level 12 Focus Scholar") with numeric XP status.
  - **Scratch Cards Vault:** Grid of earned scratch cards awarded after completing 25m+ sessions. Tapping opens an interactive canvas where dragging a finger reveals concealed rewards.
  - **Badges & Achievements Trophy Case:**
    - *First Step:* Complete your first focus session.
    - *Pomodoro Master:* Complete 4 consecutive Pomodoros.
    - *Night Owl:* Complete a focus session after 10 PM.
    - *Early Riser:* Complete a focus session before 7 AM.
    - *YouTube Scholar:* Accumulate 5 hours of approved educational YouTube watch time.
    - *Unstoppable:* Maintain a 7-day focus streak.

### 3.17 Account & Multi-Device Cloud Sync (`Screen.Account` & `Screen.Profile`)
- **Source File:** `com.example.feature.account.AccountScreen.kt`, `com.example.feature.profile.ProfileScreen.kt`
- **Auth Engine:** `AuthRepository.kt` (Jetpack Credential Manager)
- **Sync Engine:** `SyncEngine.kt`
- **Purpose:** Cloud account identity, multi-device data synchronization, and cloud backup.
- **UI Components:**
  - Google Sign-In / Account profile banner showing avatar, email, and sync status badge ("Synced", "Syncing", "Offline").
  - "Sync Now" manual trigger button.
  - Data Export & Cloud Backup management card.
  - Account deletion and sign-out controls.
- **Technical Engine & Multi-Device Two-Way Sync:**
  - **Authentication:** Uses Jetpack `CredentialManager` with Google Identity Services (`GetGoogleIdOption`).
  - **Firestore Two-Way Sync (`SyncEngine.kt`):**
    - Subcollections under `/users/{userId}/`:
      - `/sessions/`: User completed focus session records.
      - `/study_plans/`: Academic study plans and topic checklists.
      - `/study_channels/`: Approved educational YouTube channels.
      - `/focus_schedules/`: Recurring automated focus timetable entries.
      - `/scratch_cards/`: Gamification unlock states.
    - **Outbox Deletion Priority Engine:** When a user deletes a schedule or unapproves a study channel, a tombstone record is placed in `sync_outbox`. When `SyncEngine.performFullSync()` runs, it **first** executes all pending deletions against Firestore before pulling remote records. This guarantees that deleted items are never resurrected when signing into a second device with the same Google Account.
    - **Merge on Sign-In:** When signing into a fresh phone, remote channels and schedules are merged into the local Room database, channel logos are cached to disk, and the accessibility block engine cache is refreshed in real time.

### 3.18 Sessions History Log (`Screen.SessionsHistory`)
- **Source File:** `com.example.feature.history.SessionsHistoryScreen.kt`
- **ViewModel:** `HistoryViewModel.kt`
- **Purpose:** Searchable, filterable archive of all historical focus sessions.
- **UI Components:**
  - Filter chips by subject (e.g., All, Mathematics, Biology, Coding).
  - Chronological grouped list (Today, Yesterday, Last Week, Earlier).
  - Expandable session detail card showing reflection notes, duration, interruptions, and mode badges.

### 3.19 In-App Update Engine (`Screen.Update`)
- **Source File:** `com.example.feature.update.ui.UpdateScreen.kt`
- **Engine:** `UpdateEngine.kt`
- **Purpose:** Built-in self-updating mechanism for standalone distribution builds.
- **UI Components:**
  - Current installed version vs. latest available remote version.
  - Markdown-rendered changelog highlighting new features and bug fixes.
  - Download progress bar with real-time transfer speed.
  - "Install Update" button invoking Android Package Installer intent (`REQUEST_INSTALL_PACKAGES`).

### 3.20 Settings & Preferences Configuration (`Screen.SettingsScreen`)
- **Source File:** `com.example.feature.settings.SettingsScreen.kt`
- **ViewModel:** `SettingsViewModel.kt`
- **Purpose:** Global application preferences, visual theming toggles, and notification settings.
- **UI Components:**
  - Theme mode selector: System Default, Daylight (Clean Lavender/Slate), Late-Night Study (Deep Obsidian/Violet).
  - Sound & Haptics toggles: Tick sound, completion bell, tactile haptic feedback.
  - Daily Reset Time picker (default: 00:00 midnight).
  - Diagnostic & Accessibility status validator.

---

## 4. Database Schema & Local Persistence Specification

FocusShield leverages a unified `RoomDatabase` with SQLite foreign keys and indexes.

### 4.1 Room Entity Specifications

```
┌───────────────────────────────┐        ┌───────────────────────────────┐
│     SessionRecordEntity       │        │       StudyPlanEntity         │
├───────────────────────────────┤        ├───────────────────────────────┤
│ id: String (UUID, PK)         │        │ id: String (UUID, PK)         │
│ startTime: Long (epoch ms)    │        │ subject: String               │
│ endTime: Long (epoch ms)      │        │ topic: String                 │
│ durationMinutes: Int          │        │ targetHours: Float            │
│ mode: String (Normal/Strict)  │        │ completedMinutes: Int         │
│ subject: String?              │        │ examDate: Long?               │
│ topic: String?                │        │ isCompleted: Boolean          │
│ focusScore: Int (0-100)       │        │ updatedAt: Long               │
│ xpEarned: Int                 │        └───────────────────────────────┘
│ reflectionNotes: String?      │
│ attemptsBlocked: Int          │        ┌───────────────────────────────┐
└───────────────────────────────┘        │      FocusScheduleEntity      │
                                         ├───────────────────────────────┤
┌───────────────────────────────┐        │ id: String (UUID, PK)         │
│      StudyChannelEntity       │        │ title: String                 │
├───────────────────────────────┤        │ startTime: String (HH:mm)     │
│ id: String (PK)               │        │ endTime: String (HH:mm)       │
│ channelName: String           │        │ daysOfWeek: List<Int>         │
│ channelHandle: String?        │        │ isEnabled: Boolean            │
│ channelId: String?            │        │ mode: String                  │
│ isApproved: Boolean           │        │ subject: String?              │
│ avatarUrl: String?            │        │ updatedAt: Long               │
│ localLogoPath: String?        │        └───────────────────────────────┘
│ subscriberCount: String?      │
│ updatedAt: Long               │        ┌───────────────────────────────┐
└───────────────────────────────┘        │       SyncOutboxEntity        │
                                         ├───────────────────────────────┤
┌───────────────────────────────┐        │ id: Long (Auto-Inc PK)        │
│       AppLimitEntity          │        │ collectionName: String        │
├───────────────────────────────┤        │ documentId: String            │
│ packageName: String (PK)      │        │ operation: String (UPSERT/DEL)│
│ appName: String               │        │ payloadJson: String?          │
│ dailyLimitMinutes: Int        │        │ timestamp: Long               │
│ isEnabled: Boolean            │        │ retryCount: Int               │
└───────────────────────────────┘        └───────────────────────────────┘
```

---

## 5. Security, Anti-Cheating & Enforcement Priority Matrix

When an event triggers on the Android device, `FocusAccessibilityService` evaluates protection through a strict hierarchical decision tree:

```
[Accessibility Event Received]
           │
           ▼
[Is App Limits Gate active for this package?]
    ├── YES (Quota Depleted) ──► Launch AppLimitOverlayActivity (BLOCK)
    └── NO
           │
           ▼
[Is Focus Session currently active?]
    ├── NO ──► Allow app launch
    └── YES
           │
           ▼
[Is Package == "com.google.android.youtube"?]
    ├── YES (YouTube Study Mode Engine)
    │      │
    │      ├── Is it YouTube Shorts? ──► Dismiss & Step Back (BLOCK_SHORTS)
    │      ├── Is it Home Feed? ────► Show Study Mode Search Dialog
    │      ├── Is Active Video Approved? ──► Enter Playback Sandbox (ALLOW)
    │      └── Clicked Unapproved Recommendation? ──► Pause & Step Back (BLOCK)
    │
    └── NO (Standard App Evaluation)
           │
           ├── Is Package Whitelisted for this session? ──► ALLOW
           ├── Is Package Android Settings & Strict Mode ON? ──► BLOCK (Prevent Tamper)
           └── Is Package in Blocked List? ──► Launch BlockOverlayActivity (BLOCK)
```

---

## 6. Adaptive Theming & Design System

FocusShield features a centralized, accessible color design system built on Material Design 3 guidelines:

### 6.1 Color Palette Specifications

- **Daylight Study Palette (Light Theme):**
  - Primary Accent: Royal Indigo `#6366F1`
  - Background: Soft Lavender Slate `#F3ECFF`
  - Surface: Pure White `#FFFFFF`
  - Primary Container: Soft Lilac `#EDE9FE`
  - Text High-Emphasis: Deep Obsidian `#0F172A`
  - Text Medium-Emphasis: Slate Charcoal `#475569`
  - Success / Approved: Emerald `#10B981`
  - Danger / Blocked: Crimson Rose `#EF4444`

- **Late-Night Study Palette (Dark Theme):**
  - Primary Accent: Neon Violet `#8B5CF6`
  - Background: Deep Void Obsidian `#090A0F`
  - Surface: Elevated Charcoal `#13141C`
  - Primary Container: Muted Midnight Violet `#2E1065`
  - Text High-Emphasis: Crisp Off-White `#F8FAFC`
  - Text Medium-Emphasis: Muted Silver `#94A3B8`
  - Success / Approved: Bright Mint `#34D399`
  - Danger / Blocked: Vivid Coral `#F87171`

### 6.2 Typography & Spacing Grid

- **Grid:** Strict 8.dp baseline grid with standard 16.dp screen padding.
- **Touch Targets:** Minimum 48.dp x 48.dp interactive component boundaries across all icon buttons, switches, and cards for full accessibility compliance.
- **Dynamic Type Scale:** Typography utilizes scalable `sp` units respecting system font sizing and bold display accessibility preferences.
