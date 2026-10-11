# Firebase Auth & Cloud Sync Migration with Modern Google Sign-In

Replace the custom Supabase backend in FocusShield with native Firebase Authentication and Cloud Firestore, introducing a dedicated modern Google Sign-In screen with an optional skip flow, and synchronizing user profile pictures and study data seamlessly across devices.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions have been aligned based on user preferences and environment requirements:

- **Confirmed Decision 1 (Auth UX)**: Dedicated modern Google Sign-In onboarding screen welcoming users with a high-fidelity visual design and an optional skip ("Continue as Guest") button. Users who skip can easily sign in anytime via the Account/Profile screen.
- **Confirmed Decision 2 (Profile Photo Sync)**: Profile pictures selected by the user are compressed into an optimized base64 avatar payload and persisted directly in the user's Firestore profile document (`/users/{userId}`). When signing in on a new device, the profile image is retrieved, decoded, cached locally, and displayed automatically.
- **Confirmed Decision 3 (Backend Clean-Up)**: Completely excise Supabase classes, credentials, and network clients (`SupabaseConfig`, `GoTrueClient`, `SupabaseHttp`, etc.), replacing the synchronization layer with standard Firestore repositories conforming to zero-trust security rules and `CredentialManager` Google Sign-In.

---

### 1. Overview & Core Concept

- **What It Does**: FocusShield transitions from an ad-hoc Supabase network client to native Firebase services. Users can authenticate seamlessly using one-tap Google Sign-In via Android's `CredentialManager`. Their personal study statistics, focus sessions, app limits, custom study plans, and custom profile pictures are backed up to Cloud Firestore and restored automatically when logging in on any device.
- **Target Audience / Persona**: Students and professionals seeking distraction-free deep work who rely on FocusShield across multiple devices and want their streaks, goals, and customized profiles safely preserved in the cloud.
- **Key Value**: Frictionless, secure cloud backup without manual token management or external server dependencies; immediate cross-device profile and session restoration.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **First-Launch / Dedicated Sign-In Screen**:
   - The user is greeted by an immersive, polished sign-in screen featuring the FocusShield glowing brand insignia, dynamic headline ("Unlock Your Focus Potential"), bulleted feature highlights (Cross-device streaks, Cloud study plans, Protected focus stats), a prominent "Sign in with Google" button with official branding, and a subtle "Continue as Guest" text action.
   - Tapping "Sign in with Google" triggers Android's native `CredentialManager` bottom sheet. Upon success, user data and profile pictures are synchronized and the user navigates into the main dashboard.
   - Tapping "Continue as Guest" takes the user directly to the app without blocking core local blocker functionality.
2. **Account & Profile Management**:
   - The Account screen displays cloud sync status (last synced time, active account email, avatar). If guest, an elegant card prompts "Connect Google Account to enable Cloud Backup".
   - When the user updates their profile photo from the gallery or camera, the photo is instantly cropped, saved locally, and synced to Firestore.
3. **Cross-Device Restoration**:
   - On signing in on a second device, the user's profile photo and data are automatically pulled from Firestore, updating local UI instantly.

#### Visual Identity & Theme
- **Aesthetic Direction**: Premium Tech / Minimalist Deep Focus. Clean geometric cards, subtle glow accents, confident typographic hierarchy, and purposeful whitespace.
- **Color Palette & Mood**:
  - Dominant Background: Deep Charcoal / Slate (`#0B0F19` in dark mode, `#F8FAFC` in light mode).
  - Primary Accent: Electric Indigo / Shield Blue (`#4F46E5` / `#6366F1`) symbolizing protection and focus.
  - Secondary Accent: Mint Green (`#10B981`) for completed sessions and active sync status.
  - Surface Cards: Translucent elevated container surfaces (`#1E293B` / `#FFFFFF`) with subtle 1dp borders.
- **Typography & Hierarchy**:
  - Display / Title: Bold, modern sans-serif with tight tracking for screen headers and brand marks.
  - Body & Captions: Clean, legible body text with balanced line heights.
- **Interactive Feedback & Motion**:
  - Smooth button press ripple and loading spinner inside the Google Sign-In button during token exchange.
  - Animated sync status indicator with success checkmarks and cross-fade avatar transitions.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Native Jetpack CredentialManager over Legacy Auth**:
  - *Chosen Approach*: Implement `CredentialManager` using `GetSignInWithGoogleOption` with Web Client ID from `R.string.default_web_client_id`, pairing with `FirebaseAuth.signInWithCredential`.
  - *Why*: Modern Android standard compliant with API 35/36, zero deprecated APIs, and pre-integrated with Firebase project credentials.
  - *Alternatives Considered*: Custom web redirect or email/password auth—passed over because email/password requires manual credential input, prone to errors, and unsupported by backend provisioning.
- **Decision 2: Firestore Document Avatar Sync over Unprovisioned Storage Buckets**:
  - *Chosen Approach*: Compress profile images locally (JPEG/WebP to max 256x256, ~30–45 KB) and store as Base64 in `/users/{userId}`.
  - *Why*: The environment tools provision Firestore Enterprise edition automatically; storing the avatar directly in the user document guarantees instantaneous, zero-latency sync and 100% reliable cross-device fetching without requiring external unprovisioned cloud storage buckets.
  - *Alternatives Considered*: Direct Firebase Storage—requires separate bucket permissions not configured in standard provisioning.
- **Decision 3: Firestore-Backed Sync Layer replacing Supabase Outbox**:
  - *Chosen Approach*: Replace the raw HTTP Supabase sync queue with a streamlined `FirestoreSyncManager` that updates `/users/{userId}/sessions`, `/users/{userId}/study_plans`, `/users/{userId}/app_limits`, and `/users/{userId}/profile`.
  - *Why*: Native offline caching in Firestore SDK, automatic retry on reconnect, and strict security rules enforcing `request.auth.uid == userId`.

---

### 4. Technical Architecture & Data Strategy

#### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                          FocusShield UI Layer                          │
│                                                                        │
│  ┌───────────────────────┐   ┌─────────────────┐   ┌─────────────────┐ │
│  │   GoogleSignInScreen  │   │  AccountScreen  │   │  ProfileScreen  │ │
│  │ (CredentialManager UI)│   │  (Sync Status)  │   │ (Avatar Picker) │ │
│  └───────────┬───────────┘   └────────┬────────┘   └────────┬────────┘ │
└──────────────┼────────────────────────┼─────────────────────┼──────────┘
               │                        │                     │
               ▼                        ▼                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Authentication & Sync                           │
│                                                                        │
│  ┌─────────────────────────┐             ┌───────────────────────────┐ │
│  │   FirebaseAuthManager   │             │   FirestoreSyncRepository │ │
│  │  - CredentialManager    │────────────►│  - User Profile & Avatar  │ │
│  │  - Google Sign-In       │  uid token  │  - Sessions & Study Plans │ │
│  │  - Sign Out / StateFlow │             │  - App Limits & Attempts  │ │
│  └─────────────────────────┘             └─────────────┬─────────────┘ │
└────────────────────────────────────────────────────────┼───────────────┘
                                                         │
                                  ┌──────────────────────┴──────────────────────┐
                                  ▼                                             ▼
                     ┌─────────────────────────┐                   ┌─────────────────────────┐
                     │   Local Room Database   │                   │     Cloud Firestore     │
                     │  - FocusShieldDatabase  │                   │  - /users/{userId}      │
                     │  - Local Cached Data    │                   │  - Security Rules Valid.│
                     └─────────────────────────┘                   └─────────────────────────┘
```

#### Data Model & Firestore Schema

1. **User Profile Document** (`/users/{userId}`):
   - `userId`: `String` (matches `request.auth.uid`)
   - `displayName`: `String`
   - `email`: `String`
   - `photoBase64`: `String?` (compressed avatar)
   - `updatedAt`: `Timestamp` (server timestamp)
   - `dailyStudyGoalMinutes`: `Int`
   - `currentStreak`: `Int`

2. **User Subcollections**:
   - `/users/{userId}/sessions/{sessionId}`: Session records (mode, duration, completedAt).
   - `/users/{userId}/study_plans/{planId}`: Target subject, scheduled times, active days.
   - `/users/{userId}/app_limits/{limitId}`: Package name, daily limit minutes, strict mode toggle.

#### Security Rules Blueprint (`firestore.rules`)
- Strict ownership verification:
  - Rules enforce `request.auth != null && request.auth.uid == userId` for all read/write operations under `/users/{userId}`.
  - Validation predicates ensuring valid string lengths, non-empty fields, and server timestamps.

#### Migration & Cleanup Steps
1. **Remove Supabase Artifacts**:
   - Delete `com/example/cloud/` package files (`SupabaseConfig.kt`, `GoTrueClient.kt`, `SupabaseHttp.kt`, `SessionStore.kt`, `SyncEngine.kt`, `SyncGateway.kt`, etc.).
   - Remove `supabase/cloud_schema.sql` and unused Supabase configuration entries from `.env.example` and `build.gradle.kts`.
2. **Setup Firebase Backend**:
   - Extract debug keystore SHA-1 / SHA-256 fingerprints.
   - Call `ProvisionFirebase` via `rpc_action` to configure Google Sign-In and Cloud Firestore.
   - Generate `firebase-blueprint.json` and hardened `firestore.rules`.
   - Test and deploy rules via `DeployRules`.
3. **Implement UI & Data Layer**:
   - Build `GoogleSignInScreen` with modern hero typography, Google button, and skip option.
   - Implement `FirebaseAuthManager` wrapping Jetpack `CredentialManager`.
   - Implement `FirestoreSyncRepository` managing two-way synchronization for profile photos and study data.
   - Update `NavigationGraph` and `AccountScreen` to integrate the new flows.
4. **Verification**:
   - Validate security rules.
   - Verify build and tests via `compile_applet`.
