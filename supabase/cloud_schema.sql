-- ============================================================================
-- FocusShield — optional Supabase cloud account/backup/sync backend
-- ============================================================================
-- One script. Paste into the Supabase SQL Editor (Dashboard > SQL > New query)
-- and run it. It is fully idempotent: safe to run again after partial success.
--
-- What this sets up
--   1. The 15 tables the Android app actually syncs (Room camelCase columns
--      mirrored 1:1; every table carries `user_id` uuid + RLS, so a user can
--      only ever see their own rows). This script creates exactly those 15 and
--      nothing else.
--
--      `app_limits` is LEGACY and is deliberately NOT created here any more:
--      the App Limit system became device-local, so no app code path syncs,
--      writes or reads it. An old deployment that already has the table keeps
--      it (its rows are not touched — see section 6, which closes the Data-API
--      access to it instead of dropping data for you).
--   2. RLS enabled + 4 owner-only policies per table (SELECT/INSERT/UPDATE/
--      DELETE all gated on `auth.uid() = user_id`).
--   3. Data-API GRANTs to the `authenticated` role (RLS gates rows; GRANT
--      gates table access — PostgREST needs both).
--   4. `profiles` auto-row trigger on auth signup (SECURITY DEFINER, EXECUTE
--      revoked from PUBLIC so it is not a client-callable endpoint).
--   5. Private Storage bucket `profile-images` + owner-only object policies,
--      matching the app's deterministic object path `profile-images/{uid}/avatar.jpg`.
--
-- What is deliberately NOT synced (device-local by design)
--   The cloud stores what the student does *with FocusShield*, not what they do
--   on their phone. The App Limit system is device-local in its entirety: a limit
--   is an instruction about what *this handset* should enforce for whoever is
--   holding it, so a second device must never inherit it. That leaves no synced
--   cloud table for any of:
--     app_limits         — the configuration (daily limit, enabled flag, strict-mode
--                          preference, reminders, emergency-allowance count, streak).
--     app_limit_sessions — this device's allowance/enforcement episodes.
--     daily_app_usage    — per-app foreground time, i.e. Android UsageStats data.
--     daily_unlocks      — how often this handset was unlocked, equally general
--                          device usage statistics.
--   All four stay in Room and never leave the device.
--
--   Note the contrast with `blocked_apps`, which IS synced: that is the *study
--   session* block list the student picks in Start Study Session — a FocusShield
--   activity that should follow the account. Both reference the same Android
--   package names, but "YouTube gets 2h/day on this phone" and "block YouTube
--   while I study" are different features with different persistence semantics.
--
-- Prerequisites (app side, NOT this script): enable the "Email" auth provider
-- in Dashboard > Auth. Copy `.env.example` to `.env` at the repository root and
-- fill in SUPABASE_URL / SUPABASE_ANON_KEY (the Gradle build reads that file and
-- exposes the values as BuildConfig fields — `local.properties` is NOT used),
-- then rebuild the app. `.env` is git-ignored. The anon key is publishable by
-- design; the service_role/secret key and DB password are NEVER used by the app.
--
-- Column naming note
--   The app posts Room field names verbatim, so camelCase columns are declared
--   double-quoted (Postgres folds unquoted identifiers to lowercase). The only
--   snake_case table is `blocked_keywords`, whose `created_at` column is the
--   app's OWN bigint millis field — that table intentionally has no server
--   `created_at` timestamp (only `updated_at`).
-- ============================================================================

begin;

-- ---------------------------------------------------------------------------
-- 1. TABLES
-- ---------------------------------------------------------------------------
-- Keys are composite (user_id, <stable key>) so upserts are idempotent and a
-- user_id can never collide with another user's row. All key/text columns are
-- nullable-or-defaulted except the PK components to tolerate partial payloads.
-- No cross-table FKs: the app clears + re-inserts whole tables each reconcile,
-- so a hard FK could fail a sync on a transient orphan. Integrity is enforced
-- by RLS + the app's own order (subjects before topics before plans).
-- ---------------------------------------------------------------------------

create table if not exists public.focus_schedules (
    user_id uuid not null,
    "id" text not null,
    "title" text,
    "daysOfWeek" text,
    "startTime" text,
    "endTime" text,
    "isEnabled" boolean,
    "isAutoStartSession" boolean,
    "mode" text,
    "subjectName" text,
    "colorHex" text,
    "repeatEnabled" boolean,
    "scheduledDateMillis" bigint,
    "breakMinutes" integer,
    "description" text,
    "blockedAppPackages" text,
    "blockNotifications" boolean,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "id")
);

create table if not exists public.blocked_apps (
    user_id uuid not null,
    "packageName" text not null,
    "appName" text,
    "isEnabled" boolean,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "packageName")
);

create table if not exists public.blocked_websites (
    user_id uuid not null,
    "domain" text not null,
    "isEnabled" boolean,
    "category" text,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "domain")
);

-- ---------------------------------------------------------------------------
-- LEGACY / NOT SYNCED — `app_limits` (NOT created by this script)
--
-- The App Limit system became device-local in its entirety: the daily limit,
-- enabled flag, strict-mode preference, reminders, emergency-allowance count
-- and discipline streak all describe what *this handset* should enforce, so the
-- app no longer registers the table for sync, enqueues uploads for it,
-- serializes it, or restores from it.
--
-- Because nothing reads it, this script no longer creates it. An older
-- deployment may still hold rows that a student configured; deleting someone's
-- data to tidy up a schema is not this script's call, so section 6 leaves the
-- rows alone and only closes their Data-API exposure. Run the commented-out
-- DROP there if you have confirmed the rows are expendable.
-- ---------------------------------------------------------------------------

create table if not exists public.study_channels (
    user_id uuid not null,
    "id" text not null,
    "channelId" text,
    "channelName" text,
    "channelUrl" text,
    "thumbnailUrl" text,
    "isApproved" boolean,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "id")
);

create table if not exists public.study_subjects (
    user_id uuid not null,
    "id" text not null,
    "name" text,
    "colorHex" text,
    "iconIdentifier" text,
    "isArchived" boolean,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "id")
);

create table if not exists public.study_topics (
    user_id uuid not null,
    "id" text not null,
    "subjectId" text,
    "name" text,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "id")
);

create table if not exists public.study_plans (
    user_id uuid not null,
    "id" text not null,
    "subjectId" text,
    "subjectName" text,
    "topicName" text,
    "plannedDurationMinutes" integer,
    "targetDate" bigint,
    "isCompleted" boolean,
    "colorHex" text,
    "createdAt" bigint,
    "dateString" text,
    "startTime" text,
    "endTime" text,
    "startMinutes" integer,
    "endMinutes" integer,
    "status" text,
    "notes" text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "id")
);

-- Natural-key keywords (the Room `keywords` table has an autoincrement Long PK
-- that is NEVER synced; cloud identity is (keyword_type, keyword_value)).
create table if not exists public.blocked_keywords (
    user_id uuid not null,
    keyword_type text not null check (keyword_type in ('allow','block')),
    keyword_value text not null,
    is_active boolean,
    created_at bigint,            -- the app's own millis field (not a timestamp)
    updated_at timestamptz not null default now(),
    primary key (user_id, keyword_type, keyword_value)
);

-- History (append-only on pull; `created_at` here is the server watermark used
-- by the engine's `created_at=gt.<ts>` incremental fetch. The app's own millis
-- live in the camelCase `createdAt` column.)
create table if not exists public.session_records (
    user_id uuid not null,
    "id" text not null,
    "mode" text,
    "title" text,
    "subject" text,
    "topic" text,
    "goal" text,
    "plannedDurationMillis" bigint,
    "actualDurationMillis" bigint,
    "startTime" bigint,
    "endTime" bigint,
    "completed" boolean,
    "cancelled" boolean,
    "pomodoroCycles" integer,
    "completedPomodoroCycles" integer,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "id")
);

create table if not exists public.study_activities (
    user_id uuid not null,
    "id" text not null,
    "sessionId" text,
    "activityType" text,
    "source" text,
    "subject" text,
    "topic" text,
    "channelName" text,
    "channelId" text,
    "videoTitle" text,
    "startedAt" bigint,
    "endedAt" bigint,
    "durationMillis" bigint,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "id")
);

create table if not exists public.break_records (
    user_id uuid not null,
    "id" text not null,
    "sessionId" text,
    "requestedDurationMillis" bigint,
    "actualDurationMillis" bigint,
    "startTime" bigint,
    "endTime" bigint,
    "completed" boolean,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "id")
);

-- Protection events: everything FocusShield blocked, silenced or explicitly allowed. Cloud identity
-- is the app-generated `eventId` UUID, not the Room autoincrement `id` (which is device-local and
-- never leaves the device) — that is what makes re-uploading a row idempotent and lets two devices
-- merge their histories without either overwriting the other.
create table if not exists public.blocked_attempts (
    user_id uuid not null,
    "eventId" text not null,
    "timestamp" bigint,
    "eventType" text,
    "source" text,
    "packageName" text,
    "appName" text,
    "domain" text,
    "channelId" text,
    "channelName" text,
    "videoTitle" text,
    "matchedKeyword" text,
    "ruleRef" text,
    "sessionId" text,
    "scheduleId" text,
    "subject" text,
    "topic" text,
    "deviceId" text,
    "createdAt" bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "eventId")
);

-- NOTE (device-local, deliberately unsynced):
--   app_limits         — the limit configuration itself. Legacy rows may still exist in older
--                        deployments (see the note on its create statement above); the app
--                        neither writes nor reads them, and never syncs them.
--   app_limit_sessions — individual allowance episodes ("unlocked 5 more minutes"). This is
--                        current enforcement state, not account configuration, so the device
--                        determines it. The table stays in Room only.
--   daily_app_usage    — per-app foreground usage for a day. Android UsageStats-derived data
--                        must never become cloud history. The table stays in Room only.

-- Scratch-card rewards. The reward text is generated once, at card creation, and is not
-- reconstructible from anything else — so it has to travel with the account or the card would
-- reappear blank after a restore.
create table if not exists public.scratch_cards (
    user_id uuid not null,
    "sessionId" text not null,
    "createdAt" bigint,
    "rewardType" text,
    "rewardEmoji" text,
    "rewardTitle" text,
    "rewardMessage" text,
    "studyMinutes" integer,
    "isRevealed" boolean,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, "sessionId")
);

-- Daily phone-unlock counts — deliberately absent, see the header note. Counting how often the
-- student picks up their phone is general device usage statistics (§12), not FocusShield activity.
-- The Room table of the same name stays on the device for the widget.

-- Whole-document single-row-per-user tables (not stored in Room).
create table if not exists public.profiles (
    user_id uuid primary key references auth.users (id) on delete cascade,
    "displayName" text not null default 'Focus Scholar',
    "avatarPreset" text not null default 'SHIELD',
    "motto" text not null default '',
    "academicGoal" text not null default '',
    "avatarPath" text,            -- storage object path profile-images/{uid}/avatar.jpg
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.user_preferences (
    user_id uuid primary key references auth.users (id) on delete cascade,
    payload jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- 2. RLS + OWNER-ONLY POLICIES + DATA-API GRANTS (the 15 synced tables)
-- ---------------------------------------------------------------------------
-- Each table: enable RLS; four policies SELECT/INSERT/UPDATE/DELETE restricted
-- to `TO authenticated` with `(select auth.uid()) = user_id` in BOTH USING and
-- WITH CHECK (WITH CHECK stops a user reassigning user_id). UPDATE can read the
-- row through the SELECT policy. Finally GRANT CRUD to `authenticated` so
-- PostgREST exposes the table at all (RLS then gates which rows).
-- Note: auth.uid() is wrapped as (select auth.uid()) so Postgres evaluates it
-- once per query (initplan) instead of once per row — same result, faster at
-- scale (Supabase performance advisor 0003_auth_rls_initplan).
-- ---------------------------------------------------------------------------
-- A DO block keeps this compact and re-runnable. `tbls` is the complete list.
do $$
declare
    t text;
begin
    foreach t in array array[
        'focus_schedules',
        'blocked_apps',
        'blocked_websites',
        -- `app_limits` is intentionally absent: this script no longer creates it, and section 6
        -- handles the legacy table (if the deployment has one) without touching its rows.
        'study_channels',
        'study_subjects',
        'study_topics',
        'study_plans',
        'blocked_keywords',
        'session_records',
        'study_activities',
        'break_records',
        'blocked_attempts',
        'scratch_cards',
        'profiles',
        'user_preferences'
    ] loop
        execute format('alter table public.%I enable row level security', t);

        execute format('drop policy if exists "%s_select_own" on public.%I', t, t);
        execute format('create policy "%s_select_own" on public.%I for select to authenticated using ((select auth.uid()) = user_id)', t, t);

        execute format('drop policy if exists "%s_insert_own" on public.%I', t, t);
        execute format('create policy "%s_insert_own" on public.%I for insert to authenticated with check ((select auth.uid()) = user_id)', t, t);

        execute format('drop policy if exists "%s_update_own" on public.%I', t, t);
        execute format('create policy "%s_update_own" on public.%I for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id)', t, t);

        execute format('drop policy if exists "%s_delete_own" on public.%I', t, t);
        execute format('create policy "%s_delete_own" on public.%I for delete to authenticated using ((select auth.uid()) = user_id)', t, t);

        execute format('grant select, insert, update, delete on public.%I to authenticated', t);
    end loop;
end
$$;

-- (Optional) keep the anon key from even seeing these tables exist in the API.
revoke all on table public.focus_schedules, public.blocked_apps, public.blocked_websites,
    public.study_channels, public.study_subjects, public.study_topics,
    public.study_plans, public.blocked_keywords, public.session_records,
    public.study_activities, public.break_records, public.blocked_attempts,
    public.scratch_cards, public.profiles, public.user_preferences
    from anon;

-- ---------------------------------------------------------------------------
-- 3. PROFILES AUTO-ROW ON SIGNUP
-- ---------------------------------------------------------------------------
-- A new auth user gets a profiles row immediately. SECURITY DEFINER is the
-- legitimate case (the insert happens with no active user session); it is made
-- non-callable by clients by revoking EXECUTE from PUBLIC.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (user_id)
    values (new.id)
    on conflict (user_id) do nothing;
    return new;
end;
$$;

-- Revoke EXECUTE broadly. `from public` alone is NOT enough: Supabase grants
-- EXECUTE on new public functions to anon/authenticated by default privilege, so
-- those explicit per-role grants must also be revoked or the function is exposed
-- as a callable RPC endpoint (security advisors 0028/0029). It only ever runs
-- from the auth.users AFTER INSERT trigger below, never as a client RPC.
revoke all on function public.handle_new_user() from public;
revoke execute on function public.handle_new_user() from anon, authenticated;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- 4. HISTORY WATERMARK INDEXES
-- ---------------------------------------------------------------------------
-- The engine pulls history incrementally with `created_at=gt.<watermark>`, so
-- those lookups want an index on created_at.
-- ---------------------------------------------------------------------------
create index if not exists session_records_created_at_idx on public.session_records (created_at);
create index if not exists study_activities_created_at_idx on public.study_activities (created_at);
create index if not exists break_records_created_at_idx on public.break_records (created_at);
create index if not exists blocked_attempts_created_at_idx on public.blocked_attempts (created_at);
create index if not exists scratch_cards_created_at_idx on public.scratch_cards (created_at);

-- ---------------------------------------------------------------------------
-- 5. PRIVATE PROFILE-IMAGE STORAGE BUCKET + OWNER-ONLY OBJECT POLICIES
-- ---------------------------------------------------------------------------
-- Bucket is private (public=false). Object path is deterministic:
--   profile-images/{user_id}/avatar.jpg   (first folder = the owner's uid)
-- Replacing a photo overwrites the same object (x-upsert), so no stale
-- avatars accumulate; deleting removes exactly one object. The storage server
-- sets owner_id = auth.uid() automatically on authenticated uploads.
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('profile-images', 'profile-images', false)
on conflict (id) do nothing;

drop policy if exists "profile_images_insert_own" on storage.objects;
create policy "profile_images_insert_own" on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

drop policy if exists "profile_images_select_own" on storage.objects;
create policy "profile_images_select_own" on storage.objects
    for select to authenticated
    using (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

-- Storage "replace/upsert" needs UPDATE + SELECT as well as INSERT.
drop policy if exists "profile_images_update_own" on storage.objects;
create policy "profile_images_update_own" on storage.objects
    for update to authenticated
    using (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    )
    with check (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

drop policy if exists "profile_images_delete_own" on storage.objects;
create policy "profile_images_delete_own" on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'profile-images'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

-- ---------------------------------------------------------------------------
-- 6. LEGACY CLEANUP — `app_limits`
-- ---------------------------------------------------------------------------
-- Every older deployment that ran a previous version of this script has a
-- populated `public.app_limits`. This block does not delete the rows; it only
-- makes sure the abandoned table cannot be reached through the Data API:
--   * RLS is enabled (an unprotected table would be readable by anyone holding
--     the publishable anon key, which is exactly the state this avoids);
--   * any permissive policy from an earlier run is dropped;
--   * CRUD is revoked from `authenticated` and `anon`.
-- `service_role` (the SQL editor / dashboard) keeps full access, so the rows
-- can still be exported before being discarded.
--
-- Guarded by to_regclass(), so this is a no-op on a fresh project where the
-- table was never created — the old unconditional `revoke ... app_limits`
-- would in fact ERROR there.
do $$
declare
    t text;
begin
    if to_regclass('public.app_limits') is not null then
        execute 'alter table public.app_limits enable row level security';
        execute 'revoke all on table public.app_limits from anon, authenticated';
        for t in
            select policyname from pg_policies
            where schemaname = 'public' and tablename = 'app_limits'
        loop
            execute format('drop policy if exists %I on public.app_limits', t);
        end loop;
        raise notice 'app_limits is legacy: rows preserved, Data-API access revoked.';
    end if;
end
$$;

-- Once the rows are confirmed expendable, uncomment to remove the table:
-- drop table if exists public.app_limits;

commit;

-- ============================================================================
-- VERIFY (run after the script; each should return its expected value)
-- ============================================================================
-- select count(*) from pg_tables where schemaname='public'
--   and tablename in ('focus_schedules','blocked_apps','blocked_websites',
--     'study_channels','study_subjects','study_topics','study_plans',
--     'blocked_keywords','session_records','study_activities','break_records',
--     'blocked_attempts','scratch_cards',
--     'profiles','user_preferences');   --> 15 (every synced table)
--
-- select count(*) from pg_policies
--   where schemaname in ('public','storage') and policyname like '%_own';  --> 64 total (15 tables x 4 + 4 storage)
--   (a deployment that still has legacy `app_limits` reports 0 policies for it after section 6)
--
-- select id, public from storage.buckets where id='profile-images';        --> public = false
--
-- select count(*) from pg_trigger where tgname='on_auth_user_created';     --> 1
-- ============================================================================
