# FocusShield — Cloud Data & Analytics Sync Audit

**Status:** read-only inspection (§23). This file records the *pre-implementation* state — every
❌ below has since been resolved; see the resolution note.
**Scope:** every persistent statistic the UI shows, traced UI → ViewModel → Repository → DAO →
Entity → cloud table; plus a full preference classification (§13).

## Resolution (2026-09-10)

Every ❌ in this audit is now implemented, and the live Supabase project carries the tables:

- `blocked_attempts` reworked to a real event model (`eventId` UUID identity, typed
  `eventType`/`source`, domain/channel/keyword/rule/session/subject/topic/device columns) and
  bound to sync; Room migrated 12 → 13 non-destructively, legacy rows backfilled with
  `eventId = 'legacy-<id>'` and a best-guess type.
- `scratch_cards` and `daily_unlocks` bound to sync.
- `claimedRewardIds`, `blockedNotificationPackages`, `alwaysBlockedNotificationPackages` moved
  into `accountPreferencesJson`.
- YouTube Study Mode watch time is now captured (`YouTubeStudyDwellTracker` +
  `ScreenStateTracker`), so the YouTube statistics are no longer permanently zero.
- `SyncEngine.restoreLocked()` no longer carves telemetry out of a restore.
- Supabase: 14 → **19 tables**, 80 owner-only RLS policies; migration
  `add_analytics_telemetry_tables` applied live.

### Reversal (2026-09-10) — app-limit data is device-local

App limits are enforced on-device, and their data is not wanted as account history, so the
decision above was walked back for them specifically: `app_limits`, `daily_app_usage` and
`app_limit_sessions` were dropped from Supabase (migration `drop_app_limit_sync_tables`) and
removed from `SYNCED_ROOM_TABLES`. Room still owns all three tables and the App Limits feature is
unaffected — it simply no longer syncs. Supabase is now **16 tables**.

Two consequences worth recording:

- `SyncEngine.drain()` used to resolve an outbox row's table through `metaFor()`, which fell back
  to `SYNCED_ROOM_TABLES.first()`. Any op left over from a dropped table would have been pushed at
  `study_subjects`. Stale ops are now discarded by table lookup instead.
- `daily_app_usage` was the only table using `clearsOnReconcile = false` and the `merge` binding, so
  that machinery now has no users. It is left in place — it is a general engine capability and the
  default (`true`) is what every remaining table wants.

Legend: ✅ already cloud-synced · ❌ **no cloud support** · ⚙️ derived at runtime from the OS (correctly device-local)

---

## 1. §23 Dependency map

### 1.1 Home screen — `HomeViewModel` ← `AnalyticsRepository.todaySummaryFlow`

| UI statistic | ViewModel | Repository / logic | DAO | Entity | Cloud table |
|---|---|---|---|---|---|
| Focus Time (today) | `HomeProcessedData.todayStudyTime` | `todaySummaryFlow` | `SessionDao` (range flow) | `session_records` | ✅ `session_records` |
| Sessions (today) | `statistics[0]` | `todaySummaryFlow.completedSessionCount` | `SessionDao` | `session_records` | ✅ |
| **Shielded (blocked today)** | `statistics[1]` | `todaySummaryFlow.blockedAttemptsCount` | `BlockedAttemptDao` | `blocked_attempts` | ❌ |
| Focus Time (all-time) | `allTimeStudyTime` | `todaySummaryFlow.allTimeStudyTimeMillis` | `SessionDao` | `session_records` | ✅ |
| All-time session count | `allTimeSessionCount` | `todaySummaryFlow.allTimeSessionCount` | `SessionDao` | `session_records` | ✅ |
| Streak (current) | `streakDays` | `StreakCalculator.computeCurrentStreak` | `SessionDao` | `session_records` | ✅ |
| Weekly bars / labels | `weeklyBars`, `weeklyBarLabels` | `getPeriodAnalyticsFlow(LAST_7_DAYS).dailyChart` | `SessionDao` | `session_records` | ✅ |
| Today's plan progress | `planCompletionPercentage` | `StudyPlanRepository.getTodayPlansFlow` | `StudyPlanDao` | `study_plans` | ✅ |
| **Pending reward badge** | `pendingRewardBadge` | `RewardBadge.isUnlocked(allTimeMillis)` **+ `prefs.claimedRewardIds`** | — / DataStore | — | ⚠️ unlock ✅ (from `session_records`), **claim state ❌** |
| Total screen time | `totalScreenTime` | `DeviceUsageStatsHelper` (`UsageStatsManager`) | — | ⚙️ OS query | ⚙️ correctly local |
| Focus : screen-time ratio | `focusToScreenRatioPercentage` | computed from the two above | — | ⚙️ derived | ⚙️ correctly local |
| User name / avatar | `userName`, `userAvatarPreset`, `userPhotoUri` | `preferencesFlow` | DataStore | `profiles` | name+preset ✅, `userPhotoUri` ⚙️ device path |

### 1.2 Analytics screen — `AnalyticsViewModel` ← `AnalyticsRepository`

| UI statistic | Repository / logic | DAO | Entity | Cloud table |
|---|---|---|---|---|
| Total focus time (period) | `getPeriodAnalyticsFlow` → `buildPeriodSummary` | `SessionDao`, `BreakRecordDao` | `session_records`, `break_records` | ✅ |
| Daily chart | `generateDailyChart` | `SessionDao` | `session_records` | ✅ |
| **Distractions Blocked (total)** | `computeBlockedAppStats` | `BlockedAttemptDao` | `blocked_attempts` | ❌ |
| **Blocked-app breakdown** (per package) | `computeBlockedAppStats` `groupBy { packageName }` | `BlockedAttemptDao` | `blocked_attempts` | ❌ |
| **"No distraction attempts detected"** empty state | `blockedAttemptsCount == 0` | `BlockedAttemptDao` | `blocked_attempts` | ❌ |
| Average session / session count | `buildPeriodSummary` | `SessionDao` | `session_records` | ✅ |
| Subject breakdown | `computeSubjectBreakdowns` | `StudyActivityDao` / `SessionDao` | `study_activities`, `session_records` | ✅ |
| Topic breakdown | `computeTopicBreakdowns` | same | same | ✅ |
| Source breakdown (Timer/Pomodoro/YouTube) | `computeSourceBreakdowns` | `StudyActivityDao` | `study_activities` | ✅ |
| **YouTube Study Mode** (total / daily / weekly / monthly study time, sessions, approved-video sessions) | `computeYouTubeStats` over `StudyActivityEntity(source = YOUTUBE)` | `StudyActivityDao` | `study_activities` | ⚠️ **path synced but NEVER WRITTEN — `recordYouTubeStudyActivity()` has zero call sites** |
| **Shorts Blocked** | `computeYouTubeStats.shortsBlocked` ← `appName.contains("Short")` | `BlockedAttemptDao` | `blocked_attempts` | ❌ (string-matching, not a real field) |
| **Off-Topic Blocked** | `computeYouTubeStats` unapproved = total − shorts (same string heuristic) | `BlockedAttemptDao` | `blocked_attempts` | ❌ |
| Streak card (current / longest / total study days / threshold) | `StreakCalculator` + prefs | `SessionDao` | `session_records` | ✅ |

### 1.3 App Limits — `AppLimitsViewModel` ← `AppLimitRepository`

| UI statistic | DAO | Entity | Cloud table |
|---|---|---|---|
| Limit configuration (minutes, strict, reminders, emergency allowance) | `AppLimitDao` | `app_limits` | ⚙️ device-local (reversal above) |
| **Today's usage progress bar / used minutes** | `DailyAppUsageDao` | `daily_app_usage` | ⚙️ device-local |
| **Emergency uses consumed / bypassed-today state** | `DailyAppUsageDao` | `daily_app_usage` | ⚙️ device-local |
| **Limit session history** (start/end, selected vs actual, end reason) | `AppLimitSessionDao` | `app_limit_sessions` | ⚙️ device-local |

### 1.4 Widgets & rewards

| UI statistic | Repository | DAO | Entity | Cloud table |
|---|---|---|---|---|
| **Today's phone unlocks** (widget) | `DailyUnlockRepository.getTodayUnlock()` | `DailyUnlockDao` | `daily_unlocks` | ❌ |
| **Average unlocks / day** (widget) | `DailyUnlockRepository.getAverageUnlocks` | `DailyUnlockDao` | `daily_unlocks` | ❌ |
| **Scratch card reveal** (session completion screen) | `ScratchCardRepository` | `ScratchCardDao` | `scratch_cards` | ❌ |
| **Rewards claimed `n / 11`** (Profile) | `prefs.claimedRewardIds` | — | DataStore | ❌ |

### 1.5 Notification blocking

| UI statistic | Producer | Storage | Cloud |
|---|---|---|---|
| **Silenced count (today)** | `NotificationBlockerEngine` → `incrementBlockedNotificationsCount()` | `prefs.blockedNotificationsCount` (mutable DataStore counter) | ❌ |
| **Per-notification block events** | `NotificationBlockerEngine.recordAttempt(...)` | `blocked_attempts` | ❌ |
| Silenced-notification vault (title/text/sender) | in-memory, capped at 150 | *nothing persisted* | must stay local — §22 forbids uploading message contents |

---

## 2. Gap list (blocking §3–§6, §9–§10, §14–§15)

1. **`blocked_attempts` has no cloud support** — and its schema cannot express §3.
   It has only `id (autogenerate Long)`, `timestamp`, `packageName`, `appName`, `sessionId`.
   Event type is encoded by *string conventions* in `appName` (`"Blocked Website (x.com)"`,
   `"$appName (Silenced Notification)"`, `"YouTube ($chan)"`, `"Split-Screen Multitasking"`), and
   analytics re-derives it by `contains("Short")` / `contains("youtube")`. There is no
   `eventType`, no `source`, no domain column, no channel/video ID, no blocking-rule reference.
   The `autoGenerate = true` Long PK also breaks §2's stable-id requirement for multi-device dedup.
2. **YouTube Study Mode records nothing.** `AnalyticsRepository.recordYouTubeStudyActivity()` is
   dead code (zero callers), so `verifiedWatchTimeMillis` and every §4 figure are permanently 0.
   `study_activities` *is* synced — the storage path exists and is simply never fed.
3. **Two more unsynced tables** carrying user-visible history: `daily_unlocks`,
   `scratch_cards`.
4. **`claimedRewardIds` is device-local** (DataStore, not in `accountPreferencesJson`) — §10 requires
   claimed rewards/achievement progress to follow the account.
5. **`blockedNotificationsCount` is a mutable counter** (§2/§9: aggregates must not be the only
   record; here it is *also* the only record for the count shown in the UI).
6. **`SyncEngine.restoreLocked()` documents telemetry as intentionally device-local**
   ("Device-local telemetry is never cleared") — directly contradicts §15.

**Good news (no work needed):** focus time, sessions, streaks, subject/topic/source breakdowns and
planner data are already fully derivable from synced `session_records` / `break_records` /
`study_activities` / `study_plans`. §2's "don't store only a mutable total" is satisfied there.

---

## 3. §13 Preference classification

| # | Preference | Class | Rationale |
|---|---|---|---|
| 1 | `defaultTimerMinutes` | **A** — cloud | Timer config; already synced |
| 2 | `pomodoroFocusMinutes` | **A** | already synced |
| 3 | `pomodoroShortBreakMinutes` | **A** | already synced |
| 4 | `pomodoroLongBreakMinutes` | **A** | already synced |
| 5 | `pomodoroCycles` | **A** | already synced |
| 6 | `defaultSubject` | **A** | already synced |
| 7 | `defaultTopic` | **A** | already synced |
| 8 | `isAppBlockingDefault` | **A** | protection policy |
| 9 | `isStrictModeDefault` | **A** | protection policy |
| 10 | `isStudyChannelsDefault` | **A** | protection policy |
| 11 | `isYouTubeShortsBlockingEnabled` | **A** | protection policy |
| 12 | `isInstagramReelsBlockingEnabled` | **A** | protection policy |
| 13 | `isFacebookReelsBlockingEnabled` | **A** | protection policy |
| 14 | `isShortsReelsAlwaysBlocked` | **A** | protection policy |
| 15 | `dailyGoalMinutes` | **A** | goal — drives stats |
| 16 | `minimumStreakThresholdMinutes` | **A** | changes streak math — must travel with history |
| 17 | `themeMode` | **A** | user-visible preference (travelling it is harmless) |
| 18 | `isAutoAdultWebsiteBlockingEnabled` | **A** | protection policy |
| 19 | `isManualWebsiteBlockingEnabled` | **A** | protection policy |
| 20 | `isBlockUninstallEnabled` | **A** | protection policy |
| 21 | `isBlockSplitScreenEnabled` | **A** | protection policy |
| 22 | `isBlockFloatingWindowEnabled` | **A** | protection policy |
| 23 | `isBlockNotificationsEnabled` | **A** | protection policy |
| 24 | `notificationBlockMode` | **A** | protection policy |
| 25 | **`blockedNotificationPackages`** | **A → proposed** | ⚠️ *judgment call*: user-chosen apps to silence, i.e. protection configuration, not device capability. Currently local. |
| 26 | **`alwaysBlockedNotificationPackages`** | **A → proposed** | ⚠️ *judgment call*: same reasoning |
| 27 | **`claimedRewardIds`** | **A → proposed** | ⚠️ *judgment call*: §10 explicitly requires claimed rewards + achievement progress to sync |
| 28 | `hasCompletedOnboarding` | **B** — local | per-install UX gate; a restored account is admitted by the restore flow, not by this flag |
| 29 | `userPhotoUri` | **B** — local | device `content://` URI into local storage; the account-level image is the already-synced `profiles.avatarPath` |
| 30 | `userCloudAvatarPath` | **A** — cloud | already synced (inside `profiles`) |
| 31 | `userName` / `userAvatarPreset` / `userMotto` / `userAcademicGoal` | **A** | already synced in `profiles` |
| 32 | **`blockedNotificationsCount`** | **C → replace** | ⚠️ runtime aggregate. §2/§9 want the underlying `blocked_attempts` events to be the record and this to be a cache (or be derived). Keep the DataStore key as a local cache for the "today" chip; never the source of truth. |

**Device/OS-derived (never uploaded, recomputed per device — §5):** `DeviceUsageStatsHelper`
total screen time, focus:screen ratio, per-app foreground time read from `UsageStatsManager` for
apps *the user has limited*. Only FocusShield's own app-limit usage rows get persisted.

**Never uploaded (§22):** notification title/text/sender, unrelated device telemetry, secrets.
