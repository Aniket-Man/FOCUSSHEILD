# FocusShield

A distraction-blocking Android study app that helps students stay focused during study sessions.

## Features

- **Focus Sessions** — Timer-based study sessions with configurable durations
- **App Blocking** — Blocks distracting apps and websites during focus time
- **Study Plans** — Schedule and track daily study plans with overlap detection
- **Analytics** — Track study time, streaks, and blocked distractions
- **Cloud Sync** — Sync data across devices via Supabase
- **Widgets** — Home screen widgets for quick session start and progress
- **Self-Update** — In-app update system with GitHub Releases integration
- **Rewards** — Milestone badges for study time achievements

## Architecture

```
app/src/main/java/com/example/
├── cloud/          # Supabase sync, auth, HTTP client
├── core/           # Accessibility service, navigation, permissions, notifications
├── data/           # Room database, DAOs, repositories, DataStore preferences
├── feature/
│   ├── applimits/  # App usage limits and overlay blocking
│   ├── blocker/    # Block overlay activity
│   ├── home/       # Dashboard with stats and today's plans
│   ├── planner/    # Study plan creation and management
│   ├── profile/    # User profile and settings
│   ├── rewards/    # Achievement badges and scratch cards
│   ├── session/    # Focus session engine, foreground service, notifications
│   ├── update/     # Self-update system (checker, downloader, installer)
│   ├── widgets/    # Home screen widgets (Glance)
│   └── youtube/    # YouTube content extraction
```

**Pattern:** MVVM with application-scoped managers for cross-lifecycle state.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + StateFlow |
| Database | Room + KSP |
| Navigation | Compose Navigation |
| Networking | OkHttp + Retrofit + Moshi |
| DI | Manual (application-scoped singletons) |
| Async | Kotlin Coroutines + Flow |
| Charts | Vico |
| Animations | Lottie |
| Cloud | Supabase (PostgREST + Auth + Realtime) |
| Background | WorkManager + Foreground Service + AlarmManager |
| Testing | JUnit + Robolectric + Roborazzi |

## Build

### Prerequisites
- JDK 17
- Android SDK (compileSdk 35)
- `.env` file with Supabase credentials (copy from `.env.example`)

### Commands

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (requires signing config)
KEYSTORE_PATH=my-upload-key.jks \
STORE_PASSWORD=your-store-password \
KEY_PASSWORD=your-key-password \
./gradlew :app:assembleRelease -x lintVitalAnalyzeRelease

# Run tests
./gradlew :app:testDebugUnitTest
```

### Environment Variables

| Variable | Description |
|----------|------------|
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | Supabase anonymous key |
| `UPDATE_FEED_URL` | (Optional) Custom release feed URL |
| `UPDATE_REPO_SLUG` | (Optional) GitHub owner/repo for updates |

## Release Process

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Build signed APK
3. Create GitHub Release with the APK as a release asset
4. The in-app updater fetches releases from GitHub API

## Security

- No API keys are shipped in the APK — all secrets come from `.env` at build time
- Signing credentials are read from environment variables, never hardcoded
- The update system uses unauthenticated GitHub API calls (no tokens in APK)
- R8/ProGuard obfuscation is enabled for release builds

## Documentation

- `docs/UPDATE_SYSTEM.md` — Update distribution architecture
- `docs/FOREGROUND_SERVICE.md` — Foreground service lifecycle and API contracts
- `docs/CLOUD_SYNC_AUDIT.md` — Cloud sync data flow audit
- `docs/SCHEDULED_SESSIONS.md` — Alarm scheduling and DST handling

## License

Private — All rights reserved.
