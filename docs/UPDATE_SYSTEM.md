# FocusShield — App Update System

**Status:** implemented (2026-09-12). Source of truth: `prompt.txt` §1–§24.
**Purpose:** keep an installed FocusShield copy current by downloading the next signed APK from
GitHub Releases and handing it to the Android package installer.

This document is the reference the `UpdateRepository` KDoc points at. It records the architecture,
the security posture, and — importantly — the two things that must be true before the updater can
actually find a release.

## 1. Why GitHub Releases

Releases are the distribution channel the project already has. The updater reads
`https://api.github.com/repos/Aniket-Man/FOCUSSHEILD/releases/latest` **unauthenticated**, so there
is no credential of any kind inside the APK (§1, §15). The repository slug is a compile-time
constant in `GitHubReleaseRepository`; nothing about the source is user-configurable.

> ⚠️ **Blocker — the repository is currently private.** GitHub returns **404** for
> `releases/latest` on a private repo to an unauthenticated caller, and §15 forbids embedding a PAT
> to fix that. Until the repo is public, or the endpoint is replaced by a backend the app can call,
> every check resolves to "couldn't check for updates". This is the intended consequence of the
> security rule, not a bug — and it is why the fetch sits behind an interface (§15).

## 2. Layering (§14)

```
Composables  (UpdateScreen, UpdateAvailableDialog — no networking, no business rules)
     ▲
UpdateViewModel            stateless projection of the manager's StateFlows
     ▲
UpdateManager              application-scoped coordinator; owns UpdateState + the red dot
     ▲
UpdateChecker              "is there a newer version, and is it due for a check?"
     ▲                    ├── UpdateRepository  ──► GitHubReleaseRepository (OkHttp)
UpdateDownloadManager      └── UpdatePreferences (DataStore, device-local)
```

Nothing above `UpdateManager` holds update state, so the red dot on Profile, the download in
flight, and the popup all agree by construction.

### Files

| File | Role |
| --- | --- |
| `feature/update/domain/UpdateInfo.kt` | Immutable description of a release (§3) |
| `feature/update/domain/SemanticVersion.kt` | Component-wise version comparison (§2) |
| `feature/update/domain/UpdateState.kt` | The seven-state model (§13) |
| `feature/update/data/UpdateRepository.kt` | The source interface + `UpdateSourceUnavailableException` |
| `feature/update/data/GitHubReleaseRepository.kt` | The only GitHub-aware class |
| `feature/update/data/UpdatePreferences.kt` | Local persistence (cooldowns, dot, cached release) |
| `feature/update/data/UpdateChecker.kt` | Feed → `UpToDate` / `Available` / `Failed` / `Skipped` |
| `feature/update/data/UpdateDownloadManager.kt` | Download, validate, clean up, install intent |
| `feature/update/engine/UpdateManager.kt` | Application-scoped state owner |
| `feature/update/notification/UpdateNotificationHelper.kt` | "FocusShield update available" (§5) |
| `feature/update/ui/UpdateViewModel.kt` | ViewModel layer |
| `feature/update/ui/UpdateScreen.kt` | The dedicated update screen (§8) |
| `feature/update/ui/UpdateAvailableDialog.kt` | The once-per-version popup (§6) |

## 3. Version comparison (§2)

`SemanticVersion.compare` splits on `-` into a core and a pre-release tail, compares core components
numerically (padding missing components with zero), and ranks a bare release above its own
pre-release. `v1.0.1` and `1.0.1` are the same version. Equal or older ⇒ no update, ever — the
comparison is never a string compare.

`BuildConfig.VERSION_NAME` / `VERSION_CODE` are read from `app/build.gradle.kts`. Per §24 those are
bumped **by hand, per release** — never automatically per build.

## 4. Checking (§4, §16, §17)

| Trigger | Path |
| --- | --- |
| Cold start | `FocusShieldApp.onCreate` → `restore()` then `checkInBackground()` |
| Return to foreground | `MainActivity.onResume` → `checkInBackground()` |
| Connectivity regained | `connectivityMonitor.isOnline` collector → `checkInBackground()` |
| User taps "Check for updates" | `UpdateManager.checkManually()` |

Automatic checks are gated by `UpdateChecker.isDueForCheck`: **6 hours** after a successful check,
**15 minutes** after a failed one, and they are skipped outright while offline. Manual checks bypass
both. `checkInBackground` additionally holds a `Mutex`, so overlapping triggers collapse into one
request rather than queueing.

A failed *automatic* check never raises UI: it falls back to whatever release was last cached, or to
`Idle`. Only a manual check surfaces "Couldn't check for updates." with a **Try Again** button
(§16).

## 5. Notification (§5)

`UpdateNotificationHelper` posts on the `focus_shield_updates` channel at `IMPORTANCE_DEFAULT` —
deliberately *not* the high-importance block channel, so an update never behaves like a shield
alert. It fires at most once per version: `notifiedVersion` is persisted before the post, and the
tap target is `MainActivity` carrying `EXTRA_OPEN_UPDATES`, which `handleIncomingIntent` turns into
a navigation to `Screen.Update`.

## 6. The popup and the red dot (§6, §7, §18)

These two have independent, persisted lifetimes, and both survive "Later" — which is the point:

- **Popup** — shown when `UpdateState.UpdateAvailable.showPrompt` is true, which requires
  `dismissedVersion != info.versionName`. Pressing **Later** writes `dismissedVersion` and the popup
  never returns for that version. It is raised by the navigation host, not by the update screen, so
  it can appear wherever the user is; and because visibility is derived from persisted state rather
  than a screen-local flag, navigating around cannot resurrect it.
- **Red dot** — `UpdateManager.showDot` is true while a known newer release exists and
  `readVersion != info.versionName`. It is cleared in exactly two places: a successful install
  (`clearForInstalled`), or an explicit mark-as-read. **Opening the update screen does not clear
  it** (§18).

## 7. Download and install (§9, §10, §11, §20)

`UpdateDownloadManager.download` streams to `<name>.part` and renames only on success, so an
interrupted download can never leave a file that looks installable. It checks free space first
(20 MB floor), reports integer progress 0→100, honours cancellation between chunks, and rejects
non-`.apk` URLs and `text/html` / `application/json` responses. Afterwards `validateApk` requires a
non-empty file, ZIP `PK` magic, and a `getPackageArchiveInfo` package name equal to
`context.packageName`.

**No checksum is asserted.** GitHub's API publishes no digest for the asset, and §20 forbids
inventing one.

Installation is always explicit:

1. `canRequestPackageInstalls()` is checked; if false the screen explains that Android needs
   permission to install updates downloaded from FocusShield and offers **Allow Installation**,
   which opens `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package (§10).
2. The APK is exposed via `FileProvider` (`${applicationId}.fileprovider`, path `updates/`, matching
   `res/xml/file_paths.xml`) and handed to `ACTION_VIEW` as a `content://` URI with a one-shot read
   grant. A raw `file://` URI is never used (§9).
3. The system installer takes over. Nothing is installed silently, Play Protect is never disabled,
   and no install restriction is bypassed (§21).

`UpdateDownloadManager.cleanUpObsolete` deletes previous APKs whenever a new download starts, so
`cacheDir/updates` holds at most one.

## 8. Persistence and state restore (§13)

`UpdatePreferences` is a device-local DataStore (`focus_shield_update`), deliberately separate from
the cloud-synced `FocusPreferencesRepository` — update bookkeeping is about *this install* and is
never uploaded.

Beyond the scalars, the last known `UpdateInfo` is stored as JSON so a cold start can render the
update screen before any network call. `UpdateManager.restore()` rehydrates `UpdateState.Downloaded`
when `downloadedPath` still exists on disk and `downloadedVersion` still matches, so a process death
mid-flow does not lose a finished download.

## 9. Release checklist (§24, and prompt.txt lines 809–852)

The first release is `versionCode 1` / `versionName "1.0.0"`; then `2` / `"1.0.1"`, then `3` /
`"1.1.0"`. Bump both by hand, for that release only.

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Build the **signed** release APK. Signing reads `STORE_PASSWORD` / `KEY_PASSWORD` from the
   environment; the keystore itself is untracked and must stay that way.
3. **If the signed build fails, do not create a release.**
4. Tag `v<versionName>`, create the GitHub Release, and attach the signed APK as an asset whose name
   ends in `.apk` — the updater ignores every other asset (§3).
5. Release notes go in the release **body**; the app renders them in the update screen (§12).

Nothing is published without showing the versionCode, versionName, tag, APK filename and release
notes for confirmation first.

## 10. Known limitations

- Private repository ⇒ 404 ⇒ updater inert (§1/§15 conflict, documented above).
- The unauthenticated API is rate-limited to 60 requests/hour per IP. The 6-hour cooldown keeps a
  single device far below that.
- Release notes are parsed as a small markdown subset (headings, bullets, `**bold**`, `` `code` ``)
  locally; no HTML is fetched or rendered as trusted UI (§12).
- `UpdateState.Installing` is declared in the state model but is not currently produced — the
  installer is a separate process the app cannot observe past `ActivityResult`.
