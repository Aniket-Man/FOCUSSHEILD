# FocusShield — Supabase Cloud, Authentication & Profile Implementation Prompt

You are working on the FocusShield Android application.

IMPORTANT:
This is an existing, functional native Android/Jetpack Compose project.
Do NOT rebuild the application from scratch.
Do NOT replace existing architecture unnecessarily.
Do NOT remove, disable, or simplify existing features.
First inspect the COMPLETE codebase and understand the current architecture, Room database, DAOs, repositories, ViewModels, DataStore preferences, profile system, navigation, and existing UI.

GOAL:
Implement a complete optional Supabase cloud-account system for FocusShield using EMAIL + PASSWORD authentication.

The app must remain fully usable without an account.

The final architecture must be:

    FocusShield Android
          |
          +----------------------+
          |                      |
       Room                 Supabase
    local/offline          Auth + PostgreSQL
          |                      |
          +------ Sync ----------+

ROOM remains the local/offline database and the primary runtime data source.

SUPABASE provides:
- Email/password authentication
- User identity
- Cloud backup
- Cross-device synchronization
- User profile data
- Profile image storage
- Cloud persistence of appropriate user-owned data

DO NOT use Firebase.
DO NOT implement Google Sign-In in this task.
Google authentication may be added later, but the current authentication method is email/password only.


==================================================
1. FIRST: FULL CODEBASE AUDIT
==================================================

Before making changes, inspect the entire FocusShield source code.

Pay particular attention to:

app/src/main/java/com/example/data/local/
app/src/main/java/com/example/data/repository/
app/src/main/java/com/example/data/preferences/
app/src/main/java/com/example/feature/profile/
app/src/main/java/com/example/feature/settings/
app/src/main/java/com/example/feature/planner/
app/src/main/java/com/example/feature/session/
app/src/main/java/com/example/feature/blockedapps/
app/src/main/java/com/example/feature/applimits/
app/src/main/java/com/example/feature/channels/
app/src/main/java/com/example/feature/youtube/
app/src/main/java/com/example/feature/notificationblocker/
app/src/main/java/com/example/core/

Inspect:
- FocusShieldDatabase
- every Room @Entity
- every DAO
- every Repository
- DataStore preferences
- Profile UI and profile editing
- existing authentication/account-related code, if any
- navigation
- Application initialization
- dependency versions
- Gradle configuration
- AndroidManifest
- existing image handling
- existing error/loading UI patterns

Do not assume what should be synchronized before inspecting the actual entities and how they are used.


==================================================
2. CURRENT LOCAL DATA MODEL
==================================================

The current codebase contains Room entities including, but not necessarily limited to:

- AppLimitEntity
- AppLimitSessionEntity
- BlockedAppEntity
- BlockedAttemptEntity
- BlockedWebsiteEntity
- BreakRecordEntity
- DailyAppUsageEntity
- DailyUnlockEntity
- FocusScheduleEntity
- KeywordEntity
- ScratchCardEntity
- SessionRecordEntity
- StudyActivityEntity
- StudyChannelEntity
- StudyPlanEntity
- SubjectEntity
- TopicEntity

There is also a DataStore-based FocusPreferencesRepository containing user preferences and profile fields.

Current profile-related preferences include:
- userName
- userPhotoUri
- userAvatarPreset
- userMotto
- userAcademicGoal

There are also application settings/preferences for:
- default timer
- Pomodoro settings
- default subject/topic
- app blocking
- strict mode
- YouTube Study Mode
- YouTube Shorts blocking
- Instagram Reels blocking
- Facebook Reels blocking
- website blocking
- uninstall protection
- split-screen protection
- floating-window protection
- notification blocking
- notification blocking mode
- blocked notification packages
- always-blocked notification packages
- daily goal
- streak threshold
- theme
- rewards
- onboarding state

Use the actual source of truth from the codebase when determining the complete list.


==================================================
3. DECIDE WHAT SHOULD BE CLOUD-SYNCED
==================================================

Do NOT simply copy every Room table into Supabase.

Classify the data into:

A. USER CLOUD DATA
Data that represents the user's configuration, history, preferences, profile, or meaningful user-created content.

B. DEVICE-LOCAL DATA
Data that is inherently tied to Android device state, system permissions, accessibility services, active runtime state, temporary UI state, caches, generated files, or other information that should not be restored blindly onto another device.

Create a documented mapping:

LOCAL ENTITY / PREFERENCE
        ↓
CLOUD TABLE OR STORAGE
        ↓
SYNC BEHAVIOR
        ↓
REASON

Examples likely appropriate for cloud synchronization include:
- user profile
- focus schedules
- blocked apps configuration
- blocked websites
- app limits
- approved YouTube study channels
- study plans
- subjects
- topics
- focus/session history
- study activity history
- relevant preferences/settings
- other user-created configuration discovered during the audit

Examples that may remain device-local include:
- active Android services
- accessibility permission state
- device admin state
- overlay permission state
- notification-listener permission state
- current running timer/runtime state
- device-specific package installation state
- temporary caches
- local image caches

Use engineering judgment based on the actual source code.


==================================================
4. SUPABASE PROJECT SETUP THROUGH MCP
==================================================

Supabase MCP is already connected.

Use the connected Supabase MCP to inspect the current project.

Before creating anything:
- inspect existing Supabase tables
- inspect existing Storage buckets
- inspect existing Auth configuration where accessible
- inspect existing SQL/schema
- avoid destroying unrelated existing data
- avoid recreating tables that already exist
- use migrations or safe CREATE IF NOT EXISTS patterns where appropriate

Then create the required database schema.

DO NOT merely generate SQL and leave it unused.

Actually execute the required SQL through the connected Supabase MCP where the MCP supports it.

After execution:
- inspect the resulting schema
- verify tables
- verify indexes
- verify constraints
- verify RLS
- verify policies
- verify Storage configuration


==================================================
5. AUTHENTICATION
==================================================

Implement Supabase Auth using EMAIL + PASSWORD.

Required functionality:

SIGN UP:
- email
- password
- validation
- loading state
- error state
- success state
- handle email confirmation if enabled
- correctly persist Supabase session

SIGN IN:
- email
- password
- loading
- errors
- session persistence

SIGN OUT:
- securely sign out
- update local authentication state
- stop cloud synchronization

PASSWORD RESET:
- provide forgot-password flow
- use Supabase password recovery
- do not implement custom insecure password handling

Never store the user's raw password in Room, DataStore, Supabase tables, logs, or analytics.


==================================================
6. HYBRID ACCOUNT MODEL
==================================================

This is extremely important.

FocusShield MUST work without login.

A user can:

1. Install FocusShield
2. Skip account creation
3. Use FocusShield for days/weeks/months
4. Create schedules
5. Block apps
6. Configure YouTube Study Mode
7. Use focus sessions
8. Configure notification blocking
9. Change profile information
10. Build study history
11. Later decide to create an account

All existing local Room data must remain intact.

When the user creates their first account, perform a LOCAL → CLOUD MIGRATION.

Do NOT wipe Room.

Do NOT replace the local database with an empty cloud database.

Do NOT require the user to start over.


==================================================
7. FIRST LOGIN / ACCOUNT CREATION MIGRATION
==================================================

When an unauthenticated/local-only user creates an account:

    Supabase Auth
          ↓
       user.id
          ↓
   inspect existing Room data
          ↓
   upload appropriate local data
          ↓
   mark synchronization state
          ↓
   enable normal synchronization

The migration must be safe to retry.

If the upload partially fails:
- do not delete local data
- keep unsynchronized records available
- retry later
- show an understandable sync state

Use stable IDs.

Do NOT create duplicate cloud records when the same local record is synchronized again.

For existing Room records with stable IDs:
- preserve their IDs where appropriate
- associate them with the authenticated user
- use upsert/update semantics
- use unique constraints to prevent duplicates


==================================================
8. ROOM ↔ SUPABASE SYNC ARCHITECTURE
==================================================

Implement a dedicated synchronization layer rather than putting Supabase calls directly into Compose screens.

Recommended conceptual structure:

UI
 ↓
ViewModel
 ↓
Existing Repository / Room
 ↓
Sync Manager / Sync Repository
 ↓
Supabase

Room remains immediately responsive.

When a user changes something:

    UI
     ↓
    Room UPDATE
     ↓
    Sync queue/change tracking
     ↓
    Supabase UPDATE/UPSERT

Do not wait for the network before updating the UI.

If offline:
- Room update succeeds
- synchronization remains pending
- when internet becomes available, pending changes synchronize

When online:
- synchronize automatically where appropriate


==================================================
9. EDITING DATA MUST NOT CREATE DUPLICATES
==================================================

Example:

Existing schedule:

ID = 42
Biology Study
7:00 PM → 9:00 PM

User changes it to:

8:00 PM → 10:00 PM

Expected behavior:

Room:
ID 42 → updated

Supabase:
ID 42 → updated

NOT:

ID 43 → new copy

The old schedule must not remain as a duplicate.

Apply the same principle to:
- schedules
- blocked apps
- blocked websites
- app limits
- YouTube channels
- study plans
- subjects
- topics
- preferences
- profile


==================================================
10. DELETE SYNCHRONIZATION
==================================================

When a user deletes cloud-synced user data:

- update Room appropriately
- synchronize the deletion to Supabase

Design deletion carefully so an offline deletion is not accidentally restored from the cloud.

If necessary, introduce a sync metadata/deletion mechanism.

Do not blindly download cloud data over local deletions.


==================================================
11. CONFLICT HANDLING
==================================================

Design a practical conflict strategy.

The app may eventually be used on multiple Android devices.

For records modified on two devices:
- use updated_at/version metadata where appropriate
- use deterministic conflict rules
- avoid silently duplicating records
- avoid losing data unnecessarily

For simple user configuration:
- latest valid update can generally win

For historical append-only records:
- preserve records rather than overwriting unrelated history

Document the conflict strategy.


==================================================
12. DATABASE SCHEMA
==================================================

Create a normalized Supabase PostgreSQL schema based on the actual audited Room model.

Every user-owned table must have:

- primary key
- user_id referencing auth.users(id), where appropriate
- created_at
- updated_at where appropriate
- useful indexes
- appropriate uniqueness constraints

Use UUID/text IDs consistently with the existing application where practical.

Do not blindly convert every existing Room field without understanding its purpose.

Create relationships between related entities.

Examples:

profiles
focus_schedules
blocked_apps
blocked_websites
app_limits
study_channels
study_plans
subjects
topics
focus_sessions / session_records
study_activities
break_records
relevant user preferences/settings
other appropriate cloud entities discovered during audit

The final schema must reflect the actual FocusShield codebase.


==================================================
13. ROW LEVEL SECURITY
==================================================

Enable RLS on every user-owned table.

Policies must ensure:

Authenticated user A can only:
- SELECT their own records
- INSERT records belonging to their own user ID
- UPDATE their own records
- DELETE their own records

User A must never be able to read User B's data by modifying IDs or request parameters.

Use:

auth.uid()

where appropriate.

Do not create insecure policies such as:
- allow all
- authenticated users can read everything
- authenticated users can update everything

Verify every policy after creating it.


==================================================
14. PROFILE SYSTEM
==================================================

The existing FocusShield profile system already supports:

- user name
- profile photo URI
- avatar preset
- motto
- academic goal

Integrate this with Supabase.

Create a profiles table linked to auth.users.

At minimum support:
- user ID
- display name
- avatar preset
- motto
- academic goal
- profile image path
- created_at
- updated_at

Do not store the raw local Android content URI as the permanent cloud profile-image reference.


==================================================
15. PROFILE IMAGE UPLOAD
==================================================

The user must be able to select/change their profile image from the existing profile UI.

When authenticated:

    User selects image
          ↓
    Android local URI
          ↓
    read image safely
          ↓
    upload to Supabase Storage
          ↓
    save Storage path in profiles
          ↓
    display cloud image

Create a dedicated Supabase Storage bucket for profile images.

Recommended logical path:

    profile-images/{user_id}/avatar.{extension}

or another secure deterministic structure.

IMPORTANT:
- user must only be able to access their own profile image
- Storage policies must enforce user ownership
- do not make the entire bucket publicly writable
- handle image replacement
- handle image deletion
- avoid accumulating abandoned old profile images
- use a stable path per user when practical so replacing the avatar replaces the previous object
- handle upload failure without destroying the existing local avatar

The profile UI should continue to work offline using the local image/cache where possible.


==================================================
16. PROFILE IMAGE FALLBACK
==================================================

The current app has avatar presets such as:
- SHIELD / Scholar
- PHOENIX / Flame
- MEDAL / Champion
- ASTRONAUT / Cosmic
- BRAIN / Thinker
- LIGHTNING / Velocity

Preserve this existing functionality.

If the user has no uploaded image:
- show their selected avatar preset.

If they have an uploaded image:
- show the uploaded image.

If the cloud image cannot currently be downloaded:
- gracefully fall back to cached/local image or avatar preset.

Do not break the existing profile UI.


==================================================
17. PREFERENCES CLOUD SYNC
==================================================

The current FocusPreferencesRepository stores many user preferences using DataStore.

Do not automatically copy every DataStore value into a separate cloud column.

Design a clean representation for cloud-synced user preferences.

Separate:
- user preferences that should follow the account
from:
- device-specific preferences/state

For example, settings such as:
- daily goal
- default timer
- Pomodoro configuration
- theme preference
- blocking preferences
- YouTube Study Mode preferences
- website blocking configuration
- notification-blocking configuration
may be cloud candidates.

But permissions and Android device state should remain device-local.

Preserve existing DataStore behavior.


==================================================
18. FIRST CLOUD SYNC / MERGE UI
==================================================

When the user first signs up/logs in after using FocusShield without an account, provide a clear migration state.

Example:

"Back up your FocusShield data?"

"Your existing schedules, settings and study history are on this device."

Buttons:

"Back Up & Continue"
"Not Now"

If the architecture requires immediate migration after account creation, perform it automatically and show progress.

Show useful states:

- Preparing data
- Uploading
- Syncing
- Complete
- Some items couldn't sync
- Retry

Never silently delete local data.


==================================================
19. LOGGED-IN STATE
==================================================

Create a central authentication/session state.

The app must know whether the current user is:

LOCAL_ONLY
AUTHENTICATED
SYNCING
SYNC_ERROR

Avoid scattering authentication checks throughout the UI.

Expose authentication state through a clean repository/ViewModel.

When authenticated:
- show account/profile state
- enable cloud sync
- allow sign out

When signed out:
- continue local-only mode normally.


==================================================
20. LOGOUT BEHAVIOR
==================================================

Signing out must NOT delete the user's local Room database.

After logout:

    local data remains available

The user should be able to continue using FocusShield locally.

However, be careful not to automatically upload another user's local data to a different account.

When another user signs into the same device:
- establish a clear account boundary
- handle existing local data explicitly
- never mix User A's cloud data with User B's account

Design this carefully.


==================================================
21. CROSS-DEVICE RESTORE
==================================================

If User A signs into FocusShield on a second phone:

    Google/login is NOT being used yet.
    Current implementation is EMAIL + PASSWORD.

    Sign in
       ↓
    Supabase user ID
       ↓
    fetch user's cloud data
       ↓
    merge/restore into Room
       ↓
    FocusShield operates locally

Do not duplicate records already existing locally.

Use stable IDs and upsert/merge behavior.


==================================================
22. ANDROID / SUPABASE CONFIGURATION
==================================================

Add the required Supabase Android dependencies using the versions compatible with the current project.

Inspect the current Gradle setup first.

Do not randomly upgrade the entire project.

Credentials/configuration:
- Supabase URL
- Supabase publishable/anon key

These may be exposed to the client application as intended by Supabase's client architecture.

NEVER include:
- service_role key
- Supabase secret key
- database password

inside the Android application.

Use local.properties / Gradle BuildConfig or the project's existing secure configuration pattern.

Do not commit local secrets.


==================================================
23. SUPABASE STORAGE
==================================================

Create and configure the profile-image Storage bucket through Supabase MCP where supported.

Create appropriate Storage RLS policies.

The authenticated user must only be able to upload/update/delete their own profile image path.

Test:
- upload
- replacement
- deletion
- unauthorized path access
- signed/public URL behavior as appropriate

Choose the safest practical image-access approach.


==================================================
24. DATABASE MIGRATIONS
==================================================

If Room needs additional fields for synchronization, add proper Room migrations.

Do NOT destroy the existing Room database.

Do NOT use destructive migration just to add cloud synchronization.

Possible metadata may include:
- sync status
- last synced timestamp
- updated_at
- pending operation
- sync version

Only add fields when actually required by the chosen architecture.

Keep the existing application data intact.


==================================================
25. BACKGROUND SYNCHRONIZATION
==================================================

Use an appropriate Android background mechanism for reliable synchronization.

Consider WorkManager if appropriate.

Requirements:
- retry transient failures
- respect network availability
- avoid excessive battery use
- avoid continuous polling
- do not block the main thread
- do not interfere with FocusShield accessibility/blocking services

The app should not become dependent on internet connectivity for core blocking functionality.


==================================================
26. ERROR HANDLING
==================================================

Handle:
- no internet
- timeout
- invalid credentials
- wrong password
- email already registered
- email confirmation required
- Supabase unavailable
- upload failure
- database failure
- storage failure
- session expiration
- malformed cloud data
- partial synchronization

Errors must not cause loss of local Room data.


==================================================
27. SECURITY
==================================================

Security requirements:

- Never store passwords.
- Never use a service-role key in Android.
- Use Supabase Auth.
- Enable RLS.
- Verify auth.uid() ownership.
- Protect Storage paths.
- Do not trust client-provided user_id.
- Server-side ownership must come from the authenticated session.
- Do not expose another user's data.
- Do not log sensitive authentication tokens.
- Do not put secrets in source control.


==================================================
28. DO NOT CHANGE UNRELATED FEATURES
==================================================

Preserve all existing FocusShield functionality, including existing:
- focus sessions
- blocking engine
- accessibility service
- app blocking
- app limits
- website blocking
- YouTube Study Mode
- Shorts/Reels blocking
- schedules
- planner
- notification blocker
- floating-window protection
- split-screen protection
- anti-uninstall functionality
- rewards
- widgets
- profile presets
- onboarding
- theme system
- existing navigation
- existing UI design

Cloud synchronization must be an additional layer, not a rewrite of these systems.


==================================================
29. USE EXISTING REPOSITORIES WHERE POSSIBLE
==================================================

Do not create duplicate business logic.

If a feature already has:

Room DAO
→ Repository
→ ViewModel

integrate synchronization at the appropriate repository/data layer.

Avoid:

Compose UI
→ Supabase directly

Prefer:

Compose
→ ViewModel
→ Repository
→ Room
→ Sync layer
→ Supabase


==================================================
30. SUPABASE MCP EXECUTION REQUIREMENT
==================================================

Because Supabase MCP is already connected:

DO NOT merely tell me what SQL I should run.

Actually:
1. inspect the Supabase project
2. create/update the schema
3. execute SQL
4. configure RLS
5. configure Storage
6. verify the resulting database
7. report what was executed

Before destructive operations, inspect the existing Supabase project and avoid deleting unrelated data.


==================================================
31. TESTING
==================================================

Perform static/code-level verification throughout the implementation.

Test at minimum:

A. Local-only:
- launch without account
- create schedule
- edit schedule
- delete schedule
- create blocked app
- modify app limit
- modify profile
- app remains functional without internet

B. Account creation:
- create email/password account
- existing Room data remains
- local data uploads
- no duplicate records

C. Editing after login:
- modify schedule
- Room updates
- Supabase updates

D. Offline:
- modify data while offline
- Room updates
- sync occurs after connectivity returns

E. Profile:
- change name
- change avatar preset
- upload profile image
- replace profile image
- delete profile image
- sign out
- local fallback still works

F. Security:
- user A cannot access user B's rows
- user A cannot access user B's profile image

G. Second device simulation:
- sign in with same account
- cloud data restores into Room
- existing IDs do not duplicate

H. Logout:
- local data remains
- cloud sync stops
- another account cannot inherit the previous user's cloud identity accidentally


==================================================
32. DOCUMENTATION
==================================================

Create/update a concise documentation file such as:

SUPABASE_SETUP.md

Document:
- Supabase project setup
- Auth configuration
- email/password configuration
- database tables
- relationships
- RLS policies
- Storage bucket
- Storage policies
- Android configuration
- required local.properties variables
- sync architecture
- local-only behavior
- first-login migration behavior
- logout/account switching behavior
- profile image behavior
- how to test synchronization

Do not put actual secrets in documentation.


==================================================
33. FINAL REPORT
==================================================

When finished, report:

1. Files created
2. Files modified
3. Dependencies added/changed
4. Supabase tables created
5. SQL migrations executed
6. RLS policies created
7. Storage bucket created
8. Storage policies created
9. Authentication implementation
10. Room synchronization implementation
11. Local → cloud migration implementation
12. Profile-image upload implementation
13. Offline synchronization implementation
14. Conflict strategy
15. Tests/checks performed
16. Any remaining issues

IMPORTANT:
Do not claim a build or test succeeded if you did not actually run it.

If the environment cannot build the Android project because dependencies/SDK are unavailable, clearly report that instead of pretending the build passed.

Do not leave TODO/FIXME placeholders for core functionality.

The final implementation must be production-oriented, secure, maintainable, and integrated with the EXISTING FocusShield architecture.
