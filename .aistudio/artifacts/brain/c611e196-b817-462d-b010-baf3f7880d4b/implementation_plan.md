# Sign-In Onboarding Lottie & Firebase Live-Writing Session Summary Quotes Plan

## 1. Overview
Based on your scrolling reference screenshot (`Session Summary`), we will:
1. **Match the exact Session Summary UI from your screenshot** while **completely removing the top gray circle dot** (`108.dp` circle container above `"Great Job! Session Finished"`).
2. **Run the Live-Writing Quote in that exact top space** (between the `"Session Summary"` top bar and `"Great Job! Session Finished"`), written out live character-by-character in a **very eye-attractive custom Google Font** (`font-util`).
3. **Restore the exact `"TOTAL STUDY TIME RECORDED"` hero card** from your screenshot (purple-bordered dark gradient card with the clock icon + `"TOTAL STUDY TIME RECORDED"`, large white duration text, and `start → end` time range, replacing the 3D flip card).
4. **Set up Cloud Firestore (`/session_quotes`)** with preloaded quotes and security rules so your **separate external Admin Website** can add, edit, toggle, or delete quotes directly in the database.
5. **Add the looping Security Shield & Cloud Sync Lottie animation** to the **Sign-In Onboarding Page** (`OnboardingScreen.kt`).

---

## 2. Session Summary Screen Exact Layout (`FocusSessionSummaryScreen.kt`)
Matching your scrolling screenshot top-to-bottom:
1. **Top Bar**: Back arrow (`←`), centered `"Session Summary"` title, and Close (`✕`) button.
2. **Live-Writing Quote Showcase (Replacing the Gray Circle Dot)**:
   - The gray circle dot and its radial glow container are **100% removed**.
   - In that exact space above `"Great Job! Session Finished"`, the quote fetched from Cloud Firestore writes itself out **live** (smooth typewriter/ink-reveal animation with a glowing writing cursor), styled with an eye-attractive custom font (**Playfair Display Italic / Cormorant Garamond / Cinzel** installed via `font-util`), followed by the author attribution fading in gently below.
3. **Header Text**:
   - `"Great Job! Session Finished"` (bold white headline)
   - `"Review all data taken during this session below. Choose whether to save it to your history and database."` (centered muted subtitle)
4. **`TOTAL STUDY TIME RECORDED` Card (Matching Screenshot)**:
   - Dark purple gradient card with a rounded purple border (`FocusColors.Primary`).
   - Top label row: clock icon (`🕒`) + `"TOTAL STUDY TIME RECORDED"` in purple uppercase text.
   - Center: large bold white study duration (e.g. `0m`, `25m`, `1h 15m`).
   - Bottom row: `12:37 AM → 12:37 AM` start-to-end timestamp range.
5. **Subject, Topic & Goal Card**:
   - `Subject` row, `Topic` row, and inner rounded goal pill with purple flag icon.
6. **2×2 Telemetry Grid**:
   - `SESSION MODE`, `DISTRACTIONS BLOCKED`, `YOUTUBE STUDY MODE`, and `BREAK TIME`.
7. **`Protection & Telemetry Breakdown` Card**:
   - All 5 status rows (`Distractions Intercepted & Blocked`, `YouTube Educational Study Mode`, `App Shield & Distraction Blocker`, `Safe Browser Study Mode`, `Strict Mode Tamper Guard`).
8. **Bottom Action Bar**:
   - Purple filled `"Save Session"` pill button + amber/orange outlined `"✕ Don't Save"` pill button.

---

## 3. Cloud Firestore Setup for Your External Admin Website (`/session_quotes`)
- **Collection Path**: `/session_quotes/{quoteId}`
- **Document Schema (`firebase-blueprint.json`)**:
  - `id` (`string`): Document ID (`quote_01`, or custom ID from your Admin Website)
  - `text` (`string`): The quote text written live on the Session Summary screen
  - `author` (`string`): Author or attribution
  - `category` (`string`, optional): Tag such as `"Deep Work"`, `"Discipline"`, `"Focus"`
  - `fontStyle` (`string`, optional): `"editorial"` | `"calligraphy"` | `"classic"`
  - `active` (`boolean`): Whether the quote is enabled in rotation
  - `typingSpeedMs` (`number`, optional): Milliseconds per character for live writing (default `32`)
  - `createdAt` / `updatedAt` (`number`): Timestamp in milliseconds
- **Security Rules (`firestore.rules`) & Preloads**:
  - Authenticated app users can `get` and `list` `/session_quotes/{quoteId}`.
  - Your admin account (`aniket4man@gmail.com`) can `create`, `update`, and `delete` quotes from your external Admin Website, plus initial preload seeding of 10 curated study/focus quotes into Firestore.

---

## 4. Sign-In Onboarding Page Lottie Animation (`OnboardingScreen.kt`)
- Create `app/src/main/res/raw/lottie_cloud_shield.json` featuring a **prominent looping Security Shield & Cloud Sync animation** in `#6D28D9` and `#1E1235` to fill the blank space on the Sign-In onboarding screen (`step == 4`).
