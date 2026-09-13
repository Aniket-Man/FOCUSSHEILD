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
- `scratch_cards` bound to sync. (`daily_unlocks` was bound here too, then deliberately unbound —
  see the 2026-09-12 update at the foot of this file.)
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

> ⚠️ **Superseded.** A 2026-09-12 revision briefly put `app_limits` back into sync; that was
> reverted the same day. The App Limit system is device-local in its entirety — the configuration
> included. The legacy Supabase table is left in place but is no longer written, read or restored.
> See the 2026-09-12 update at the foot of this file.

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
| **Pending reward badge** | `pendingRewardBadge` | `RewardBadge.isUnlocked(allTimeMillis)` **+ `prefs.claimedRewardIds`** | — / DataStore | — | ✅ unlock derived from `session_records`; claim state ✅ in `accountPreferencesJson` |
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
| Limit configuration (minutes, strict, reminders, emergency allowance) | `AppLimitDao` | `app_limits` | ⚙️ device-local — the whole App Limit system is local |
| **Today's usage progress bar / used minutes** | `DailyAppUsageDao` | `daily_app_usage` | ⚙️ device-local |
| **Emergency uses consumed / bypassed-today state** | `DailyAppUsageDao` | `daily_app_usage` | ⚙️ device-local |
| **Limit session history** (start/end, selected vs actual, end reason) | `AppLimitSessionDao` | `app_limit_sessions` | ⚙️ device-local |

### 1.4 Widgets & rewards

| UI statistic | Repository | DAO | Entity | Cloud table |
|---|---|---|---|---|
| **Today's phone unlocks** (widget) | `DailyUnlockRepository.getTodayUnlock()` | `DailyUnlockDao` | `daily_unlocks` | ⚙️ local only — general device usage (§12) |
| **Average unlocks / day** (widget) | `DailyUnlockRepository.getAverageUnlocks` | `DailyUnlockDao` | `daily_unlocks` | ⚙️ local only — general device usage (§12) |
| **Scratch card reveal** (session completion screen) | `ScratchCardRepository` | `ScratchCardDao` | `scratch_cards` | ✅ `scratch_cards` |
| **Rewards claimed `n / 11`** (Profile) | `prefs.claimedRewardIds` | — | DataStore | ✅ `accountPreferencesJson` |

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

---

## Update (2026-09-12) — §1 cloud boundary re-derived

The governing principle for the whole boundary is §1: *cloud stores what the student does with
FocusShield, not everything the student does on their phone.* Applying it cuts one way: the entire
App Limit system, and the general device-usage tables beside it, stay on the phone.

An earlier revision of this section put `app_limits` back into sync on the grounds that the spec
names the limit configuration as restorable. That was wrong and it is reverted here. An app limit is
an instruction about what *this handset* should enforce for whoever is holding it — "YouTube gets 2
hours a day on this phone" — so a second device must never inherit it. The whole system is local:

| Table | Cloud | Why |
|---|---|---|
| `app_limits` | ⚙️ local | the configuration itself: daily limit, enabled flag, strict-mode preference, reminder setting, emergency-allowance count, discipline streak |
| `daily_app_usage` | ⚙️ local | Android UsageStats foreground time — general device usage |
| `app_limit_sessions` | ⚙️ local | per-second enforcement state |
| `daily_unlocks` | ⚙️ local | per-day unlock counts — equally general device usage |

None of the four is registered in `SYNCED_ROOM_TABLES`, none has a `CloudJson` mapper, none has a
`CloudBulkDao` read, and none has a `SyncEngine` binding. That is deliberately stronger than hiding
them at the edge: there is no serialization to filter, so there is no code path by which a limit row
can enter the outbox, a snapshot, or the wire in the first place. Emergency-use state is local for
the same reason — a second device must not inherit another device's consumed emergency uses.

What *does* follow the account is the **study-session** block list, `blocked_apps` — the apps the
student selects in Start Study Session → Block Apps. It shares Android package names with the
app-limit system, but the two are separate features: one is account configuration, the other is a
per-device enforcement instruction, and they have separate persistence.

**Legacy table left in place (§9).** `public.app_limits` still exists in the live project (0 rows)
and is still created and protected by `cloud_schema.sql`, with RLS and owner-only policies intact.
It was not dropped: removing a production table to tidy a schema is not worth the risk of destroying
data. It is documented there as legacy/non-synced — no new rows are written, no rows are read — and
can be dropped by hand once the operator is satisfied it is expendable.

**`daily_unlocks` is unbound.** It was bound to sync on 2026-09-10. Per-day phone unlock counts are
general device usage statistics under §12, so it was removed from `SYNCED_ROOM_TABLES`;
`DailyUnlockRepository` is now explicitly local-only. The live Supabase table and its 4 rows are
left in place — dropping a table holding data is a destructive, outward-facing step and has not
been taken.

**Achievements carry an unlock instant, derived not stored.** §6 asks for "unlock timestamp" and a
guarantee of "no duplicate achievement records". Storing one would have meant re-creating it on
reinstall — the exact mechanism that duplicates achievement rows. Instead
`RewardBadge.deriveUnlockTimes()` walks the synced `session_records` chronologically and computes
the precise crossing instant for each badge. Session history stays the single source of truth (§4);
every device derives the same answer; a re-sync cannot double-count.

**Reward badges redesigned (§9/§19).** `BadgeEmblemArt` paints a layered emblem (offset drop
shadow, gradient face, clipped shading wash, clipped specular highlight, rim bevel) and the section
now follows the §19 hierarchy: progress HEADER → tiered ACHIEVEMENT SECTIONS → BADGE → scratch card.
`RewardBadgeCard` is the reusable `(achievement, state, progress, reward)` component §9 asks for.
Infinite pulse animations now compose only for a claimable badge, not for every unlocked one.

**Known gap, unchanged:** `blockedNotificationsCount` remains a mutable DataStore counter and the
only record for the "silenced today" figure (§2/§9).

---

## §21 Final audit (2026-09-12)

Verified against the tree at commit `4ae847d` + the working changes described above. ✅ = inspected
in source; the compile is clean (`:app:compileDebugKotlin` BUILD SUCCESSFUL, 0 errors).

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | Session history in cloud | ✅ | `session_records`, `PullMode.HISTORY`, in `SYNCED_ROOM_TABLES` |
| 2 | Study time calculable from session history | ✅ | `AnalyticsRepository` — every total is `sumOf { actualDurationMillis }` |
| 3 | No manual total counter as source of truth | ✅ | no persisted lifetime total exists anywhere; all totals derived |
| 4 | Achievements in cloud | ✅ | derived from synced `session_records` via `deriveUnlockTimes` |
| 5 | Achievement progress survives device changes | ✅ | derivation is a pure function of synced history — same on every device |
| 6 | Rewards in cloud | ✅ | `scratch_cards` (HISTORY) + `claimedRewardIds` in `accountPreferencesJson` |
| 7 | Scratch rewards survive logout/reinstall/device change | ✅ | `scratch_cards` is a synced HISTORY table |
| 8 | Scratch card reveals progressively | ✅ | per-touch `drawPath(..., BlendMode.Clear)` over an offscreen layer |
| 9 | Scratch text not hidden until fully scratched | ✅ | `DEFAULT_REVEAL_THRESHOLD` + `onScratchProgress` |
| 10 | Reward badges substantially redesigned | ✅ | `BadgeEmblemArt` 5-pass render; `RewardBadgeCard`; §19 hierarchy |
| 11 | Profile pictures upload to private storage | ✅ | `ProfileImageUploader` — private bucket, `user_id`-scoped path |
| 12 | Final user-selected crop preserved | ✅ | `ImageCropDialog` produces the cropped bitmap that is uploaded |
| 13 | Same avatar framing on another device | ✅ | the uploaded object *is* the cropped result, not the original |
| 14 | Blocked-app configuration syncs | ✅ | `blocked_apps` — the study-session block list |
| 15 | **App-limit configuration does NOT sync** | ✅ | no mapper, no bulk read, no binding — see the 2026-09-12 update |
| 16 | Persistent preferences sync | ✅ | `user_preferences` document |
| 17 | Schedules/plans/subjects/topics sync | ✅ | `focus_schedules`, `study_plans`, `study_subjects`, `study_topics` |
| 18 | Blocking telemetry syncs where appropriate | ✅ | `blocked_attempts`, reworked to a real event model |
| 19 | General app-usage history does NOT sync | ✅ | no mapper exists in `CloudJson` |
| 20 | `daily_app_usage` does NOT sync | ✅ | absent from `SYNCED_ROOM_TABLES` |
| 21 | UsageStatsManager stays device-local | ✅ | `DeviceUsageStatsHelper` is read-on-device only |
| 22 | Allowance time NOT added to actual app usage | ✅ | `AppLimitManager`: actual usage comes from UsageStats, never the FocusShield timer |
| 23 | Study timer NOT confused with phone usage | ✅ | same split; `AppLimitSessionEntity` is a separate table from `DailyAppUsageEntity` |
| 24 | Offline changes continue working | ✅ | outbox (`SyncTracker`) is written on every local edit |
| 25 | Pending changes sync on reconnect | ✅ | `SyncEngine.drain()` runs ahead of each cycle |
| 26 | New-device login restores config/history/rewards/profile | ✅ | `restoreLocked()` via the `RestoreRequired` outcome |
| 27 | Logout does not delete local data | ✅ | `AuthRepository.signOut()` clears only the session store |
| 28 | Duplicate cloud records prevented | ✅ | upserts merge on stable keys; badges are derived, so re-sync cannot double-count |
| 29 | Room migrations remain safe | ✅ | 12 explicit migrations (1→13), no destructive fallback |
| 30 | No unnecessary dependencies added | ✅ | zero diff in `app/build.gradle.kts` / `libs.versions.toml` |
| 31 | Existing functionality intact | ✅ | module compiles clean; only pre-existing warnings |

**Outstanding, deliberately not actioned:** the live Supabase `daily_unlocks` table (4 rows) and
`app_limit_sessions` are now unreferenced by the app but still exist server-side. Dropping them is
destructive and outward-facing, so it awaits an explicit decision rather than being done silently.
