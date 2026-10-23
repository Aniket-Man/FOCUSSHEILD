# Unified Firebase Database & Release Website Migration Plan

Unify FocusShield's OTA update infrastructure onto the primary Firebase project (`gen-lang-client-0733915575`), replacing the external update database with the existing user database (`ai-studio-android-focusshe-c611e196-b817-462d-b010-baf3f7880d4b`). This eliminates dual-database complexity, resolves the 403 Permission Denied read errors by deploying public read security rules directly from this repository, and provides a ready-to-use prompt for your web AI agent.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Review the unified architecture and copy-paste web agent prompt below. Click **Proceed** when you are ready for us to apply the unified Firebase credentials, update `firestore.rules`, and deploy the security rules to the primary project.

- **Confirmed Decision 1 — Single Unified Firebase Project**: Migrate OTA update storage from `eminent-physics-z8p5g` to the app's existing user data Firebase project (`gen-lang-client-0733915575`). User sessions and app releases will co-exist in separate top-level collections (`users`, `session_quotes`, `releases`, `release_binaries`) within the same Firestore database.
- **Confirmed Decision 2 — Public Read Security Rules Deployed Locally**: Security rules will be updated in `firestore.rules` and deployed via AI Studio's `deploy_firebase` tool to allow unauthenticated read access (`allow read: if true;`) on `/releases` and `/release_binaries`, fixing the 403 Forbidden issue permanently while protecting write operations (`allow write: if isSignedIn();`).
- **Confirmed Decision 3 — Turnkey Web Admin Portal Prompt**: Section 5 provides the complete, self-contained prompt containing all exact project IDs, database IDs, API keys, storage buckets, and document schemas for your web AI agent to build and connect the release management website.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. Consolidates both app user data and app OTA release distribution into a single Firebase project (`gen-lang-client-0733915575`) and database (`ai-studio-android-focusshe-c611e196-b817-462d-b010-baf3f7880d4b`).
  2. Updates FocusShield Android app's built-in defaults (`ExternalFirebaseUpdateConfig.kt`) and `.env` to point directly to this unified project, with no need for the user to paste tokens or configure multiple backends.
  3. Updates `firestore.rules` on the primary database to grant public read permission to `/releases/{releaseId}` and `/release_binaries/{releaseId}/chunks/{chunkId}`, enabling any installed FocusShield app to check for updates and download APK chunks over REST without authentication errors.
  4. Delivers a complete, copy-pasteable specification prompt for an AI agent in a web development chat to build the companion admin website that publishes APKs, manages release notes, inspects storage quotas, and manages past versions.
- **Target Audience / Persona**:
  - **Admin / Developer (You)**: Uploads new APK builds (e.g. `FocusShield-v1.6.0.apk`) and writes structured release notes via the web portal.
  - **End Users**: Instantly receive update notifications, view what's new, and download APKs directly from the unified backend without errors.
- **Key Value**: One Firebase project for everything; zero external project management; zero 403 Forbidden permission issues; instant deployment of rules.

---

## 2. User Experience & Flows

### Android App Update Flow
1. **Seamless Auto-Check**:
   - The app boots or resumes and checks `ai-studio-android-focusshe-c611e196-b817-462d-b010-baf3f7880d4b` under `gen-lang-client-0733915575`.
   - Because public read rules are active on `releases`, the query completes in ~200ms with HTTP 200 OK.
2. **Notification & Update Available Dialog**:
   - If a release with higher `versionCode` or `versionName` exists, the in-app modal opens showing structured release notes (What Changed, What Improved, Bug Fixes).
3. **One-Tap Download & Install**:
   - The user taps **Update Now**. The app downloads the APK (from Firebase Storage or Firestore chunk stream), validates the APK package name (`com.aistudio.focusshield.krtjfp`), and hands off to the Android system package installer.
4. **Profile & Settings Version Sync**:
   - The installed version dynamically displays `BuildConfig.VERSION_NAME` across Profile and Settings screens.

### Web Admin Portal Flow
1. **Admin Login**:
   - Admin signs in using Google Sign-In or Firebase Auth.
2. **APK Upload & Inspection**:
   - Admin drops an `.apk` file (e.g. `FocusShield-v1.6.0.apk`). The website parses the file, extracts `versionName` ("1.6.0"), `versionCode`, computes SHA-256, and fills in the release form.
3. **Publishing**:
   - Admin writes What Changed, What Improved, and Bug Fixes, then clicks **Publish**.
   - The website writes the record to `/releases/{releaseId}` and uploads the APK file to Firebase Storage (with automatic fallback to Firestore chunks).
4. **Immediate Distribution**:
   - All installed FocusShield Android apps detect the update immediately.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Merge Update Collections into Primary Database**
  - *Chosen Approach*: Add `releases` and `release_binaries` as top-level collections alongside `users` and `session_quotes` in `ai-studio-android-focusshe-c611e196-b817-462d-b010-baf3f7880d4b`.
  - *Why*: Eliminates the need to maintain, configure, and authenticate against two separate GCP/Firebase projects. Enables deploying security rules directly from this repository via `deploy_firebase`.
- **Decision 2: Public Read, Authenticated Write for Releases**
  - *Chosen Approach*: `allow read: if true;` for `releases` and `release_binaries`; `allow write: if isSignedIn();`.
  - *Why*: Android client apps checking for app updates do not need a signed-in user account just to check if a new APK version exists. Only admins publishing or deleting releases via the website need to be authenticated.
- **Decision 3: Retain Dual-Transport Fallback in Android Engine**
  - *Chosen Approach*: The Android engine keeps supporting both standard Firebase Storage URLs (`*.firebasestorage.app`) and Firestore binary chunks (`firebase-chunked://...`).
  - *Why*: Maximizes reliability regardless of whether Cloud Storage or Firestore binary chunks are used by the website uploader.

---

## 4. Technical Architecture & Data Strategy

### Unified System Architecture

```
┌────────────────────────────────────────────────────────────────────────────┐
│         UNIFIED FIREBASE PROJECT: gen-lang-client-0733915575               │
│         DATABASE: ai-studio-android-focusshe-c611e196-b817-462d-b010...    │
│                                                                            │
│  ┌─────────────────────────┐  ┌─────────────────────────────────────────┐  │
│  │ USER DATA COLLECTIONS   │  │ OTA UPDATE COLLECTIONS                  │  │
│  │  - /users/{userId}      │  │  - /releases/{releaseId} (Public Read)  │  │
│  │  - /session_quotes      │  │  - /release_binaries (Chunk Fallback)   │  │
│  └─────────────────────────┘  └─────────────────────────────────────────┘  │
│                               ┌─────────────────────────────────────────┐  │
│                               │ Firebase Storage:                       │  │
│                               │  - gen-lang-client-0733915575.firebase...│ │
│                               └─────────────────────────────────────────┘  │
└───────────────────────┬────────────────────────────────────▲───────────────┘
                        │                                    │
           Public Read  │ HTTP 200 OK           Auth Write   │ Upload & Manage
           Updates REST │ (No 403s!)            Releases     │ APKs
                        ▼                                    │
┌───────────────────────────────────┐    ┌───────────────────┴───────────────┐
│     FOCUSSHIELD ANDROID APP       │    │      COMPANION ADMIN WEBSITE      │
│  - ExternalFirebaseUpdateConfig   │    │  - Google Sign-In Admin Auth      │
│    (Default: unified credentials) │    │  - APK Parser & SHA-256 Engine    │
│  - CompositeUpdateRepository      │    │  - Storage Quota & Health Meter   │
│  - UpdateDownloadManager          │    │  - Release Notes & History List   │
│  - Dynamic BuildConfig Version    │    │  - Storage + Chunk Upload Engine  │
└───────────────────────────────────┘    └───────────────────────────────────┘
```

### Exact Credentials for the Unified Project
| Config Property | Value |
|---|---|
| **Project ID** | `gen-lang-client-0733915575` |
| **Firestore Database ID** | `ai-studio-android-focusshe-c611e196-b817-462d-b010-baf3f7880d4b` |
| **Web API Key** | `AIzaSyATUoJaSohY3N11_aj1rx2rarvoHW3fmmg` |
| **Storage Bucket** | `gen-lang-client-0733915575.firebasestorage.app` |
| **Android App ID** | `1:699105574821:android:bbac9eea2c482a9811724c` |
| **Package Name** | `com.aistudio.focusshield.krtjfp` |

### Security Rules Specification (`firestore.rules`)
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isSignedIn() {
      return request.auth != null;
    }

    function isOwner(userId) {
      return isSignedIn() && request.auth.uid == userId;
    }

    // User Profile & Subcollections
    match /users/{userId} {
      allow read, write: if isOwner(userId);
      match /{document=**} {
        allow read, write: if isOwner(userId);
      }
    }

    // Session Quotes
    match /session_quotes/{quoteId} {
      allow read: if true;
      allow write: if isSignedIn();
    }

    // OTA Releases (Public read for all Android clients, authenticated write for admin website)
    match /releases/{document=**} {
      allow read: if true;
      allow write: if isSignedIn();
    }

    // Binary Chunks Fallback
    match /release_binaries/{document=**} {
      allow read: if true;
      allow write: if isSignedIn();
    }
  }
}
```

---

## 5. Copy-Paste Prompt for Your Web AI Agent

Give the entire prompt block below to your AI agent in a web development chat to create the Release Admin Website:

```markdown
Build a complete, production-grade **FocusShield Release & OTA Update Admin Portal** web application connecting directly to our existing Firebase project.

### 1. Firebase Project Credentials (DO NOT PROVISION A NEW PROJECT)
Configure the Firebase Web SDK with these exact credentials for our existing Firebase project:

- **Project ID**: `gen-lang-client-0733915575`
- **Firestore Database ID**: `ai-studio-android-focusshe-c611e196-b817-462d-b010-baf3f7880d4b`
- **API Key**: `AIzaSyATUoJaSohY3N11_aj1rx2rarvoHW3fmmg`
- **Storage Bucket**: `gen-lang-client-0733915575.firebasestorage.app`
- **Auth Domain**: `gen-lang-client-0733915575.firebaseapp.com`
- **Target Android Application ID**: `com.aistudio.focusshield.krtjfp`

Initialize Firestore targeting the specific named database:
```javascript
import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";
import { getStorage } from "firebase/storage";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
  apiKey: "AIzaSyATUoJaSohY3N11_aj1rx2rarvoHW3fmmg",
  authDomain: "gen-lang-client-0733915575.firebaseapp.com",
  projectId: "gen-lang-client-0733915575",
  storageBucket: "gen-lang-client-0733915575.firebasestorage.app",
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app, "ai-studio-android-focusshe-c611e196-b817-462d-b010-baf3f7880d4b");
export const storage = getStorage(app);
```

### 2. Website Features & Functional Requirements

#### A. Authentication & Access Control
- Google Sign-In or Email authentication via Firebase Auth so that only authorized administrators can upload or delete releases.
- Top bar showing current admin profile avatar, email, and Sign Out button.

#### B. Storage Health & Capacity Meter (Top Header)
- **Laptop / Local Browser Storage**: Display browser quota estimation using `navigator.storage.estimate()` (GB used vs available).
- **Firebase Storage & Firestore Binary Meter**: Calculate total bytes used across all releases stored in `/releases`. Display quota percentage, average APK size, active release count, and how many releases fit in remaining storage.

#### C. Publish New Release (Drag & Drop APK Parser)
- **APK File Drag & Drop**:
  - Accept `.apk` files.
  - Automatically calculate file size in bytes and SHA-256 hash using `crypto.subtle.digest('SHA-256', buffer)`.
  - Extract or auto-detect `versionName` (e.g. `1.6.0`), `versionCode` (integer), and filename (e.g. `FocusShield-v1.6.0.apk`) from the APK name/manifest.
  - Auto-generate `tagName` (e.g. `v1.6.0`).
- **Structured Release Notes Form**:
  - **Release Title**: (e.g. "FocusShield v1.6.0 — Focus Mode & Sync Enhancements")
  - **What Changed**: Bullet points of new features.
  - **What Improved**: Performance and stability enhancements.
  - **Bug Fixes**: Fixes and notes.
  - **Combined Markdown**: Automatically merge the three sections into clean markdown for `releaseNotes`.
  - **Live Android Preview Card**: Show a live simulation of the Android app's "Update Available" popup and post-install "What's New" dialog.

#### D. Dual Storage Upload Strategy (Firebase Storage + Firestore Chunks)
- Primary: Upload APK binary to Firebase Storage path `releases/{versionName}/{apkAssetName}`, obtain public download URL, and store in `apkUrl`.
- Fallback: If Firebase Storage fails or is unavailable, automatically slice the APK into 650 KB base64 chunks and write to subcollection `/release_binaries/{releaseId}/chunks/{index}`, setting `apkUrl` to `firebase-chunked://gen-lang-client-0733915575/{releaseId}`.

#### E. Exact Firestore Document Schema (`releases/{releaseId}`)
Write documents to `/releases` with these exact fields:
```typescript
interface ReleaseDocument {
  versionName: string;         // e.g. "1.6.0"
  versionCode: number;         // e.g. 7
  tagName: string;             // e.g. "v1.6.0"
  title: string;               // e.g. "FocusShield v1.6.0"
  whatChanged: string;
  whatImproved: string;
  bugFixes: string;
  releaseNotes: string;        // Markdown combining the three sections
  apkUrl: string;              // HTTPS Storage URL or firebase-chunked:// URL
  apkAssetName: string;        // e.g. "FocusShield-v1.6.0.apk"
  apkSizeBytes: number;        // e.g. 24890123
  sha256: string;              // Hex string
  storageMode: "firebase_storage" | "firestore_chunks";
  chunkCount: number;          // 0 if firebase_storage
  channel: string;             // "production"
  publishedAt: string;         // ISO-8601 string, e.g. new Date().toISOString()
  createdAtEpochMs: number;    // Date.now()
}
```

#### F. Past Releases List & 1-Click Cleanup
- Display all published releases ordered by `versionCode` descending.
- Show version badge, publish date, size, SHA-256 snippet, and expandable release notes.
- **Direct Download APK** button.
- **Delete Release** button with confirmation: removes document from `/releases/{releaseId}` and deletes the APK binary from Firebase Storage or chunk collection, updating storage meters immediately.
```
