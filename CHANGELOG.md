# Changelog

All notable changes to FocusShield are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). `versionName` /
`versionCode` live in `app/build.gradle.kts` and are bumped by hand per release.

## [Unreleased]

### Added

* `README.md`, `LICENSE` (MIT), `CONTRIBUTING.md` and this changelog (#31).
* Unix `gradlew` wrapper script, so the build runs on macOS and Linux (#31).
* GitHub Actions CI: `assembleDebug`, unit tests and Roborazzi verification on every push and
  pull request (#31).
* `ScheduleValidation.findOverlap` — overlapping automated schedules are now rejected at save time
  instead of being silently skipped when the alarm fires (#28).
* `TimeZoneOffsetGuard` — the UTC offset is recorded when alarms are armed and re-checked when they
  fire, so a DST shift re-arms every schedule instead of firing an hour off (#28).
* `NotificationBlockDecision` and `SingleFlightThrottle` — pure, unit-tested decision/throttle logic
  extracted from the notification blocker and the widget updater (#30).

### Fixed

* Nested bottom-sheet gesture dismissal: swiping down (or tapping the scrim) while a sub-page of
  the focus-session setup sheet — focus time, break or select apps — was open used to drive the
  sheet to `Hidden` but only reset the sub-view, leaving the setup sheet stranded off-screen while
  the caller still thought it was open (a soft-lock). It now re-expands and returns to the main
  page, matching the in-sheet back gesture. The stacked `WebsiteBlockerSheet` and
  `YouTubeStudyChannelManagerSheet` now animate to `Hidden` before leaving composition, so
  dismissing them via "Done"/back slides out instead of blinking away.
* Midnight re-arm is no longer lost when the daily-reset database work throws; alarms are armed
  first and in a `finally` (#28).
* `FocusScheduleRepository.syncAllAlarms` is a `suspend` function and is awaited by the boot
  receiver, so the process cannot be killed with schedules unarmed (#28).
* `FocusSessionManager.startSession` refuses to start while a session is `RUNNING` or `PAUSED`,
  so a double tap can no longer abandon a live session (#28).
* Focus-session setup sheet state survives rotation (`rememberSaveable`) and the device-admin
  screen is launched once per request instead of on every recomposition (#28).
* The protection-permission dialog's 400 ms poll no longer compares against a stale first-composition
  snapshot, so it stops refreshing once everything is granted (#28).
* Scratch-card reveal state is keyed to the card, so swapping cards in place no longer shows a
  pre-revealed card and drops the reward (#29).
* A failed avatar upload no longer pushes a partial profile edit to the cloud (#29).
* The "Always-Silent (24/7)" notification list is evaluated before the master toggle, honouring its
  documented 24/7 contract, and the engine's caches are primed before notifications are judged (#30).
* The widget throttle commits its timestamp after the update and coalesces a trailing run, so a
  session-end refresh within the throttle window is no longer dropped (#30).
* `GreetingScreenshotTest` actually asserts; Roborazzi verification is wired into `check` (#31).

## [1.3.0]

* In-app updater hardening: correct version comparison, 404 handled as "no release", download state
  preserved across navigation, pinned URLs (#26).
* Analytics/streaks: thread-safe date formatting, live "today" window, correct midnight bucketing (#25).
* Strict mode: bypass closed, enforcement failures surfaced, verified block-screen retries (#27).
* Room: destructive migration fallbacks removed, 12→13 domain backfill corrected, schema export
  enabled (#23).
* Cloud restore: identity checks enforced, fabricated dates and coercions rejected, install state
  made durable (#22).
* Auth tokens encrypted at rest; server logout failures surfaced (#20).
* Build: conditional keystore signing and R8 minification enabled (#24).
