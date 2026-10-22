# Implementation Plan - Editable Session Tags & Removal of Preloaded Tags

Remove hardcoded/preloaded session tags (`"Physics"`, `"Electrostatics"`, `"Solve 20 JEE Advanced problems"`), add custom Subject tag chips (with add, edit, and delete support) plus a Topic input in the Session Setup sheet, hide the tags section on the Active Session screen when none are selected, and save the chosen Subject and Topic to Session Summary and Session History.

## User Requirements
1. **Remove Preloaded Tags:**
   - Remove the preloaded defaults (`"Physics"`, `"Electrostatics"`, `"Solve 20 JEE Advanced problems"`) so starting a session without selecting a tag does not display fake preloaded subjects/topics.
   - Stop auto-seeding default subjects (`Physics`, `Chemistry`, `Mathematics`, `Biology`) and remove any untouched preloaded default subject rows so the user starts with a clean slate for their own tags.
2. **Custom Editable Tags & Topic Input in Session Setup Sheet:**
   - In `FocusSessionSetupSheet` (before starting a session), add a **Subject Tag & Topic** section:
     - **Subject Tag Chips:** Display user-created tags as interactive chips, with a `+ Add Tag` chip to create a new tag, and an edit/delete option (via a long-press or edit icon / dialog) to rename or delete any tag. Tapping a selected tag chip deselects it.
     - **Topic Input:** A clean text field allowing the user to type the specific topic for the session when a tag is selected or independently.
3. **Active Session Screen Behavior:**
   - If the user starts a session without selecting a Subject tag or entering a Topic, completely hide the Subject pill and Topic text on `ActiveSessionScreen`.
   - Only render the Subject pill when `subject.isNotBlank()` and only render the Topic heading when `topic.isNotBlank()`.
4. **Save with Session Summary & Session History:**
   - Pass the user's selected Subject tag and Topic into the active session, display them on `FocusSessionSummaryScreen`, and save them into `SessionRecordEntity` so they appear in `SessionsHistoryScreen` (both in the history list cards and the session detail sheet).

## Proposed Changes

### 1. Data & Repository Layer (`com.example.data`)
- **`SubjectDao.kt` & `SubjectRepository.kt`**:
  - Add `deleteSubject(id: String)` and `renameSubject(id: String, newName: String)` methods to `SubjectDao` and `SubjectRepository` (including Cloud Sync outbox updates).
  - Remove preloaded subject/topic seeding in `initializeDefaultSubjectsIfEmpty()` and add a one-time cleanup helper to remove the legacy seeded IDs (`"physics"`, `"chemistry"`, `"mathematics"`, `"biology"`) if they were auto-seeded.
- **`FocusPreferencesRepository.kt`**:
  - Change `defaultSubject` and `defaultTopic` defaults from `"Physics"` and `"Electrostatics"` to `""` (empty string).

### 2. ViewModel & Domain State (`com.example.feature.session`)
- **`SessionViewModel.kt` & `FocusSession.kt`**:
  - Change default `selectedSubject`, `selectedTopic`, and `sessionGoal` in `SessionSetupState` and `ActiveSessionUiState` from `"Physics"`, `"Electrostatics"`, and `"Solve 20 JEE Advanced problems"` to `""`.
  - Expose `customSubjects: StateFlow<List<SubjectEntity>>` from `SubjectRepository` in `SessionViewModel`.
  - Add ViewModel actions: `addCustomSubjectTag(name: String)`, `updateCustomSubjectTag(subject: SubjectEntity, newName: String)`, `deleteCustomSubjectTag(subject: SubjectEntity)`, `selectSubjectTag(name: String)` (toggles selection), and `setTopic(topic: String)`.
- **`HomeViewModel.kt`**:
  - Remove `"Physics"` / `"Electrostatics"` fallback strings when mapping active session state.

### 3. UI Layer (`com.example.feature.session` & `com.example.feature.history`)
- **`FocusSessionSetupSheet.kt`**:
  - Add a **Session Tag & Topic** card above the Core Session Settings card:
    - Horizontal/flow row of user-created Subject tag chips + `+ Add Tag` chip.
    - Edit/Delete dialog when the user taps the edit icon on a selected tag chip (or long-presses a chip) to rename or remove it.
    - `OutlinedTextField` for entering the **Topic** (e.g., chapter, task, or focus topic).
- **`ActiveSessionScreen.kt`**:
  - Conditionally render the Subject pill only when `uiState.subject.isNotBlank()`.
  - Conditionally render the Topic headline only when `uiState.topic.isNotBlank()`.
  - If no Subject, Topic, or status pills are active, hide the empty header spacing cleanly.
- **`FocusSessionSummaryScreen.kt` & `SessionsHistoryScreen.kt`**:
  - Show the user's Subject tag and Topic in the Summary card and History cards; if a session was recorded without a Subject/Topic, hide the empty row or display a clean fallback label (`"General Focus"` only on the history card title when `subject.isBlank()`) so cards remain visually balanced.

## Verification Plan
- **Compilation & Unit Tests:**
  - Run `compile_applet` and unit tests (`gradle :app:testDebugUnitTest --tests "com.example.feature.session.*"`).
- **Functional Verification:**
  - Open the Session Setup sheet, verify no preloaded `"Physics"` or `"Electrostatics"` tags are selected, add/edit/delete custom Subject tags, enter a Topic, start the session, and verify they appear on the Active Session screen and save to Session History.
  - Start a session with no Subject or Topic selected and verify the tags area is completely hidden on the Active Session screen.
