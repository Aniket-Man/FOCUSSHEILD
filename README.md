# FocusShield

**FocusShield** is an offline-first Android study companion. It runs timed focus sessions,
enforces them by blocking distracting apps, websites and notifications, and turns the result
into streaks, analytics and rewards — with optional end-to-end cloud sync.

> Package: `com.aistudio.focusshield.krtjfp` · `minSdk 24` · `targetSdk / compileSdk 35` ·
> Kotlin + Jetpack Compose + Room.

---

## Features

| Area | What it does |
| --- | --- |
| **Focus sessions** | Timer, Stopwatch and Pomodoro engines behind one `FocusSessionManager`, kept alive by a foreground service so a session survives process death (`docs/FOREGROUND_SERVICE.md`). |
| **App limits & blocker** | Per-app daily limits and a full-screen block overlay driven by an `AccessibilityService`, with Strict Mode for sessions that must not be escapable. |
| **Website & keyword blocking** | URL and keyword detectors for browsers, plus Reels/Shorts detectors for Instagram, Facebook and YouTube. |
| **Notification blocker** | Session-only, always-silent (24/7) and smart-hybrid modes, with a vault that keeps the metadata of everything it silenced. |
| **Scheduled sessions** | Recurring or one-off automated sessions armed through `AlarmManager`, re-armed on boot, time change and DST shift (`docs/SCHEDULED_SESSIONS.md`). |
| **Planner & analytics** | Daily study plans, subject/topic tracking, streaks, heatmaps and shareable stat cards. |
| **Rewards** | Scratch cards granted for eligible completed sessions. |
| **Home-screen widgets** | Glance widgets for the current session, today's usage and unlock count. |
| **Cloud sync (optional)** | Supabase-backed sync of sessions, plans, schedules and profile (`docs/CLOUD_SYNC_AUDIT.md`, `supabase/cloud_schema.sql`). |
| **In-app updater** | Checks a release feed and installs the next signed APK (`docs/UPDATE_SYSTEM.md`). |

---

## Getting started

### Requirements

* **JDK 17** (the Android Gradle Plugin toolchain; `settings.gradle.kts` uses the foojay resolver,
  so Gradle can provision a matching JDK automatically).
* **Android SDK** with platform **35** and build-tools installed — Android Studio, or the
  command-line tools with `ANDROID_HOME` / `ANDROID_SDK_ROOT` exported.
* No global Gradle install is needed: use the wrapper.

### Clone and build

```bash
git clone https://github.com/Aniket-Man/FOCUSSHEILD.git
cd FOCUSSHEILD

./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows
```

A fresh clone builds with **no extra setup**: no keystore, no `.env`, no `google-services.json`.
Everything optional degrades to a documented no-op instead of failing the build.

### Run the checks

```bash
./gradlew test                   # JVM + Robolectric unit tests
./gradlew testDebugUnitTest      # debug variant only
./gradlew verifyRoborazziDebug   # screenshot goldens (see "Screenshot tests" below)
./gradlew assembleDebug test     # what CI runs
```

---

## Configuration

### `.env` (all keys optional)

Copy `.env.example` to `.env` and fill in only what you need. The
[Secrets Gradle Plugin](https://github.com/google/secrets-gradle-plugin) reads `.env`, falls back to
`.env.example`, and the values land in `BuildConfig`.

| Key | Purpose | Left empty ⇒ |
| --- | --- | --- |
| `SUPABASE_URL` | Cloud sync endpoint | app runs fully offline |
| `SUPABASE_ANON_KEY` | Cloud sync anon key | app runs fully offline |
| `UPDATE_FEED_URL` | Release feed for the in-app updater | falls back to the public GitHub Releases API |
| `UPDATE_REPO_SLUG` | `owner/repo` override for forks | defaults to `Aniket-Man/FOCUSSHEILD` |

`.env` is git-ignored; `.env.example` is tracked and documents every key.

### Signing / keystore bootstrap

Keystores are **never** committed (`*.jks` and `*.keystore` are git-ignored), so the build wires
signing in only when the files and credentials are actually present (issue #24).

**Debug.** If a shared `debug.keystore` exists in the repository root it is used; otherwise AGP's
own default debug keystore is used and `assembleDebug` just works.

**Release.** Provide a keystore plus both passwords through the environment:

```bash
export KEYSTORE_PATH=/absolute/path/to/my-upload-key.jks   # defaults to <repo>/my-upload-key.jks
export STORE_PASSWORD='…'
export KEY_PASSWORD='…'
./gradlew assembleRelease
```

The key alias is `upload`. To create one:

```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

If any of the three is missing, `assembleRelease` still succeeds — it simply produces an **unsigned**
APK rather than breaking a fresh clone.

### Firebase (optional)

`google-services.json` is absent by default and `missingGoogleServicesStrategy = WARN`, so the build
only warns. Drop your own file into `app/` to enable the Firebase features.

---

## Architecture overview

```
app/src/main/java/com/example/
├── FocusShieldApp.kt          # Application: DI-by-hand root, engine bootstrap
├── core/                      # cross-cutting infrastructure
│   ├── accessibility/         # FocusAccessibilityService + protection policy
│   ├── detector/              # Reels / Shorts / URL / keyword detectors
│   ├── engine/  overlay/      # block-screen orchestration and overlays
│   ├── navigation/ ui/ design/# Compose navigation, shared UI, theme
│   ├── notification/          # NotificationListenerService
│   ├── permission/ receiver/  # runtime permissions, broadcast receivers
│   └── tracking/ util/        # usage tracking, time & formatting helpers
├── data/
│   ├── local/                 # Room database, DAOs, entities, migrations
│   ├── preferences/           # DataStore-backed FocusPreferencesRepository
│   ├── model/ repository/     # domain models and repositories
├── cloud/                     # Supabase auth, http, storage, sync engine
└── feature/                   # one package per user-facing feature
    ├── session/               # engines, scheduling, foreground service, UI
    ├── applimits/ blocker/ blocks/ websiteblocker/ notificationblocker/
    ├── planner/ analytics/ history/ rewards/ widgets/
    └── account/ profile/ settings/ onboarding/ splash/ update/ youtube/
```

Guiding rules the codebase holds itself to:

1. **Nothing is invented.** A value that cannot be parsed is reported and skipped — never replaced
   by a plausible-looking default (see `ScheduleValidation`, `ScheduledSessionFactory`,
   the cloud restore path).
2. **Enforcement failures are visible.** If a block, a sync or an alarm cannot be applied, it is
   surfaced or logged loudly rather than silently degraded.
3. **Alarms are re-armed before fallible work**, so one database hiccup can never kill every future
   reminder.
4. **Pure logic is separated from Android plumbing** (`ScheduleAlarmPlanner`, `ScheduleValidation`,
   `NotificationBlockDecision`, `SingleFlightThrottle`) so it can be unit-tested without a device.

Further reading: [`docs/`](docs/) — foreground service, scheduled sessions, cloud sync audit and the
update system.

---

## Testing

* **Unit / Robolectric:** `app/src/test` — run with `./gradlew test`.
* **Instrumented:** `app/src/androidTest` — run with `./gradlew connectedAndroidTest` (needs a
  device or emulator).
* **Screenshot tests:** Roborazzi. Record new goldens, then verify them:

  ```bash
  ./gradlew recordRoborazziDebug     # write app/src/test/screenshots/*.png
  ./gradlew verifyRoborazziDebug     # fail the build when a golden drifts
  ```

  `verifyRoborazziDebug` is wired into `check`, so a drifted golden fails CI instead of passing
  quietly (issue #31).

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch/commit conventions and the checks a pull request
must pass. Release history lives in [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE) © FocusShield contributors.
