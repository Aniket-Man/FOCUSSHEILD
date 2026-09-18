# FocusShield — Foreground Service (session protection)

**Status:** implemented (2026-09-18). Referenced from `AndroidManifest.xml` (`<service>` comment) and
`FocusSessionForegroundService` / `ForegroundServiceTypes` KDoc.

The focus session's timer and the app blockers must keep running while FocusShield is not on screen.
That is the entire job of `FocusSessionForegroundService`. This document records what the service
guarantees, what it deliberately does **not** guarantee, and the per-API-level rules that make
`startForeground` succeed on every supported release.

## 1. `foregroundServiceType` — the Android 10/14 contract

Two rules, enforced by the platform, both of which have crashed this service in the past:

| Release | Rule |
| --- | --- |
| Android 10 (API 29) | `startForeground(id, notification, type)` throws `IllegalArgumentException` unless **every bit** of `type` is declared on the `<service>` element. |
| Android 14 (API 34) | Each used type additionally needs its `FOREGROUND_SERVICE_<TYPE>` permission, and omitting `type` throws `MissingForegroundServiceTypeException`. |

What the app does now:

* Manifest: `android:foregroundServiceType="dataSync|specialUse"` **and** both permissions
  (`FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_SPECIAL_USE`). Declaring both is required
  because no single value is correct across API 29–35: `specialUse` only exists from API 34, while
  `dataSync` is the closest fit on 29–33.
* `ForegroundServiceTypes.resolve(sdkInt, declaredTypes)` picks the type per API level
  (`specialUse` on 34+, `dataSync` on 29–33, untyped below 29) and validates the choice against the
  **actual** mask read from the platform (`ServiceInfo.foregroundServiceType`). A bit the manifest
  does not declare is never passed to the OS; it is replaced by one that is, with a warning.
* The manifest mask is re-read at runtime on purpose: the previous bug was a manifest/code drift
  (code passed `FOREGROUND_SERVICE_TYPE_DATA_SYNC` while the element declared only `specialUse`),
  which made every Android 10–13 start throw.
* Every failure mode (`IllegalArgumentException`, `IllegalStateException` — including
  `ForegroundServiceStartNotAllowedException` and `MissingForegroundServiceTypeException` — and
  `SecurityException`) is caught, logged with the reason, and followed by `stopSelf()`. A service
  that cannot enter the foreground must not linger: the platform would kill it with a
  foreground-service timeout ANR instead.

`ForegroundServiceTypesTest` pins the mapping and the subset rule (`type and declared == type`) for
every API level, including the exact pre-fix scenario.

## 2. Wake lock — renewed, not rented for four hours

The session keeps the CPU awake with a **partial** wake lock (`FocusShield:SessionWakeLock`,
non-reference-counted), because the timer must keep ticking with the screen off.

* Held **only while the session is `RUNNING`**. Pausing or ending the session releases it; a paused
  session needs no CPU, and holding a lock there is pure battery drain.
* Renewed every 15 minutes with a 20-minute chunk timeout (`WAKE_LOCK_RENEW_INTERVAL_MILLIS` /
  `WAKE_LOCK_CHUNK_MILLIS`). Re-acquiring an already-held, non-reference-counted lock resets its
  timeout rather than stacking, so an 8-hour session stays covered and the lock still expires by
  itself if the process dies.
* The previous implementation acquired one lock with a fixed **4-hour** timeout and never renewed it,
  so any longer session silently stopped being protected partway through.
* Both `onDestroy` and the "session is over" path release the lock and leave the foreground.

## 3. `START_STICKY` is not a resurrection guarantee

`onStartCommand` returns `START_STICKY`: best effort, *not* a contract. Android may refuse to
recreate the service — background foreground-service starts are restricted from Android 12, and there
are known Android 14/15 platform bugs where a sticky restart of a foreground service throws
`ForegroundServiceStartNotAllowedException` (followed by an FGS-timeout ANR). Aggressive OEM battery
managers (Xiaomi, Huawei, Oppo, Samsung, …) freeze or kill even sticky services.

A null intent is handled as `ACTION_START` ("resume whatever the persisted state says"), which is
what the platform sends when it recreates a sticky service.

The recovery design is layered instead of trusting that one flag:

1. **START_STICKY** — the OS may recreate the service;
2. **`onTaskRemoved`** (app swiped from Recents) — the session snapshot is persisted and an exact
   alarm is armed 1 s ahead that starts the service through a foreground-service `PendingIntent`.
   Exact alarms are documented as exempt from the Android 12+ background-start restriction; if
   exactness is unavailable the alarm is still armed inexactly and the limitation is logged;
3. **`FocusShieldApp.onCreate`** — restores a persisted session on the next launch and re-starts the
   service;
4. **persisted session snapshot** — `FocusSessionManager.saveActiveSessionToDisk()` keeps elapsed
   time across a process death;
5. **`BootAndDailyResetReceiver`** — re-arms alarms and schedules after a reboot, time change or
   permission change.

Each layer logs why it fired or failed (`adb logcat -s FocusSessionService`), so "the session stopped
in the background" is diagnosable rather than mysterious.

## 4. Practical verification

```bash
# Which type does the manifest declare?
aapt2 dump xmltree app/build/outputs/apk/debug/app-debug.apk --file AndroidManifest.xml | grep -A2 foregroundServiceType

# Does the service actually enter the foreground with the right type?
adb logcat -s FocusSessionService | grep "entered the foreground"

# Is the wake lock held and renewed?  (expect a live lock while a session runs)
adb shell dumpsys power | grep -i focusshield

# Was the exact alarm path used?
adb logcat -s FocusScheduleScheduler StudyPlanScheduler ExactAlarmGate
```

## 5. What is still not covered

* No instrumentation test drives the service through a real `startForeground` on API 34 hardware —
  `ForegroundServiceTypesTest` covers the decision logic, not the platform call.
* Android 14's foreground-service **timeout** (a `dataSync`/mediaProcessing service may run at most
  6 hours per 24) is not a concern for `specialUse` on 34+, but `dataSync` on 29–33 gets no such
  guarantee either way; a session longer than the session's own configuration is simply not a case
  the platform forces.
