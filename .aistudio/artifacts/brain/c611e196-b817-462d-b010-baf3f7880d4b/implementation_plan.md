# Implementation Plan: Fix False "Unapproved Channel" Block in YouTube Full-Screen Mode

## 1. Root Cause Analysis

When a student watches an approved YouTube study channel and switches to **Full Screen** (via the Full Screen button or rotating the device), FocusShield currently blocks the video as an **"Unapproved Channel"** due to three interacting flaws:

1. **Player Control Clicks & Orientation Changes Wipe Out the Approved Channel Latch (`FocusAccessibilityService.kt` & `YouTubeContentBlockEngine.kt`)**
   - In `FocusAccessibilityService.kt` (lines 544–550), **every** `TYPE_VIEW_CLICKED` and `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOWS_CHANGED` event inside YouTube unconditionally triggers `YouTubePlaybackSandbox.onVideoNavigationOccurred(now)` and `YouTubeContentBlockEngine.onUserNavigationOrClick(event, now)`.
   - Tapping **"Enter full screen"**, rotating the screen, or tapping any in-player control (Play/Pause, Seek bar, 10s skip, Video Quality/Settings, Captions, Playback Speed, Chapters, or showing/hiding the player overlay) emits `TYPE_VIEW_CLICKED` and/or `TYPE_WINDOW_STATE_CHANGED`.
   - `onUserNavigationOrClick()` immediately clears `lastApprovedVideoKey = null`, `lastApprovedChannel = null`, and `lastApprovedHandle = null`, exits `YouTubePlaybackSandbox`, and enters a 3-second cooldown where `isLatchedApproved` is forced to `false`.

2. **Full-Screen Mode Hides the Portrait Channel Header (`YouTubeNodeExtractor.kt` & `YouTubeContentBlockEngine.kt`)**
   - Once YouTube is in full-screen landscape mode, the portrait metadata area below the player (`channel_handle`, `subscribe_button`, `channel_name`, `owner_text`) is hidden from the accessibility hierarchy.
   - Because the approved channel latch was wiped on entering full screen, `YouTubeContentBlockEngine.evaluate()` attempts to re-detect the channel in full screen and fails in two ways:
     - **Path A — Full-Screen "More Videos" / Endscreen Recommendation Leak:** `YouTubeNodeExtractor` falls back to scanning `rootNode` when the portrait watch container is absent, picking up a channel name or `@handle` from YouTube's full-screen **"More videos" / Up Next** strip, endscreen overlay, or engagement panel (`fullscreen_engagement_panel`, `autonav_preview`, `related_endscreen_results`), immediately returning `BLOCK_UNAPPROVED_CHANNEL`.
     - **Path B — 5-Second Metadata Grace Timeout:** When full-screen player controls fade out, both `rawChannel` and `rawHandle` are `null`. After 5 seconds (`CHANNEL_METADATA_GRACE_MILLIS`), `YouTubeContentBlockEngine` returns `BLOCK_UNKNOWN_CONTENT`, which `FocusAccessibilityService` (line 672) routes to `handleYouTubeUnapprovedChannelBlocked` with the fallback label `"Unapproved Channel"`.

3. **Blocking or Re-Verifying While in Full Screen (`FocusAccessibilityService.kt`)**
   - If a brand-new video starts while already in full screen (e.g., via Autoplay or an external deep link) and its channel metadata is hidden by full-screen mode, the engine cannot read the portrait watch header unless YouTube exits full screen back to portrait watch view first.

---

## 2. Proposed Solution

### A. Distinguish Player Control / Full-Screen Actions from Real Video Navigation (`YouTubeNodeExtractor.kt` & `YouTubeContentBlockEngine.kt`)
- Add `YouTubeNodeExtractor.isPlayerControlOrFullscreenInteraction(event: AccessibilityEvent?, rootNode: AccessibilityNodeInfo?): Boolean` to accurately detect clicks and window transitions that belong to the video player itself rather than navigating to a new video:
  - **Full-screen & orientation controls:** `"Enter full screen"`, `"Exit full screen"`, `"Full screen"`, `fullscreen_button`, `youtube_controls_fullscreen_button`.
  - **Playback & settings controls:** Play, Pause, Fast forward, Rewind, Seek bar (`time_bar`), Settings/Gear (`player_overflow_button`), Quality, Captions/Subtitles (`subtitle_button`), Playback speed, Lock screen, Audio track, Player overlay tap (`player_control_screen`, `controls_layout`).
  - **Window orientation / full-screen layout transitions:** When `TYPE_WINDOW_STATE_CHANGED` or `TYPE_WINDOWS_CHANGED` occurs within YouTube and the player remains in `VIDEO_WATCH` (or transitions between portrait watch and full-screen watch for the same video title).
- Update `YouTubeContentBlockEngine.onUserNavigationOrClick(event, now)` and `FocusAccessibilityService` so that:
  - Clicking player controls or toggling full-screen / rotating the device **never** clears `lastApprovedChannel`, `lastApprovedHandle`, `lastApprovedVideoKey`, or exits `YouTubePlaybackSandbox`.
  - Only clicking an actual video card (detected via `extractClickedVideoCardIdentity`), navigating to Shorts/Home/Search, or detecting a changed video title resets the approved channel latch.

### B. Full-Screen Detection & Approved Latch Preservation (`YouTubeNodeExtractor.kt`, `YouTubeContentBlockEngine.kt`, `YouTubeContentInspectionEngine.kt`)
- Add `YouTubeNodeExtractor.isFullScreenVideoPlayback(rootNode: AccessibilityNodeInfo?, displayMetrics: DisplayMetrics?): Boolean` to detect when YouTube's player is in full-screen mode:
  - Checks for landscape display orientation while in `VIDEO_WATCH`, full-screen control nodes (`"Exit full screen"`, `fullscreen_engagement_panel`), or when the player container occupies the full screen width/height and the portrait watch metadata panel (`watch_panel` below player / `subscribe_button`) is absent.
- When `isFullScreenVideoPlayback` is `true` and the current session already has a verified approved channel (`lastApprovedChannel` / `YouTubePlaybackSandbox.isSandboxActive`):
  - **Keep the approved status locked** as long as the visible video title in the full-screen player overlay (if visible) has not changed to a different video.
  - When the full-screen player controls are hidden (so both title and channel nodes are invisible), maintain `ALLOW` under the latched approved channel instead of timing out after 5 seconds into `BLOCK_UNKNOWN_CONTENT`.
  - Exclude full-screen recommendation/engagement overlays (`fullscreen_engagement_panel`, `quick_actions_container`, `autonav`, `endscreen`, `scrim_overlay`, `more_videos`) in `YouTubeDetectionRules.EXCLUDED_RECOMMENDATION_VIEW_IDS` and `YouTubeNodeExtractor.isInsideExcludedContainer()` so related video cards in full screen are never mistaken for the active video's channel.

### C. Title-Change & Full-Screen "More Videos" Card Click Detection
- If the student is in full screen and:
  1. **Clicks a video card** in the full-screen "More videos" / Up Next shelf or endscreen: `extractClickedVideoCardIdentity(event)` immediately extracts the clicked card's channel and title.
     - If the clicked card belongs to an **approved** study channel, update the latch to the new title/channel and allow playback seamlessly in full screen.
     - If the clicked card belongs to an **unapproved** channel, immediately block it.
  2. **The video title changes** (e.g., via Autoplay or clicking a card whose channel wasn't in the click event) while in full screen and no approved channel header is visible:
     - Trigger a one-time **Auto-Exit Full Screen for Verification** action (`YouTubeNodeExtractor.exitFullScreenToPortrait(rootNode, service)`), which clicks the `"Exit full screen"` button (or dispatches a single `GLOBAL_ACTION_BACK` that exits full screen into the portrait watch page) and resets the 5-second metadata grace timer so FocusShield can read the portrait channel header (`@handle` / Subscribe button) and either approve or block accurately.

### D. Clean Full-Screen Exit When Blocking an Unapproved Video (`FocusAccessibilityService.kt`)
- In `handleYouTubeUnapprovedChannelBlocked`, if the student is currently in full-screen mode when a genuinely unapproved video is blocked, ensure stepping back exits full screen and closes the unapproved watch player cleanly rather than merely dropping from full screen into portrait while leaving the unapproved video open.

---

## 3. Files to Modify

1. `app/src/main/java/com/example/feature/youtube/detector/YouTubeDetectionRules.kt`
   - Expand `EXCLUDED_RECOMMENDATION_VIEW_IDS` with full-screen recommendation/overlay container IDs (`fullscreen_engagement`, `autonav`, `endscreen`, `more_videos`, `related_endscreen`, `quick_actions`) and add player-control resource ID & content-description matchers.
2. `app/src/main/java/com/example/feature/youtube/engine/YouTubeNodeExtractor.kt`
   - Add `isPlayerControlOrFullscreenInteraction(event, rootNode)` so full-screen toggles and player control clicks are recognized as safe in-player interactions.
   - Add `isFullScreenVideoPlayback(rootNode)` and `exitFullScreenIfPossible(rootNode)`.
   - Tighten `extractChannelFromWatchPlayer` and `extractHandleFromWatchSubtree` so they never scrape full-screen recommendation strips when the portrait watch header is absent.
3. `app/src/main/java/com/example/feature/youtube/engine/YouTubeContentBlockEngine.kt`
   - Preserve `lastApprovedChannel`, `lastApprovedHandle`, and `lastApprovedVideoKey` across full-screen transitions, orientation changes, and player control clicks.
   - Maintain `ALLOW` while in full screen for a latched approved video even when player controls auto-hide, only invalidating when a different video title or video-card click is detected.
   - Return a dedicated signal (`needsExitFullScreenForVerification`) when a new unverified video starts in full screen with hidden channel metadata.
4. `app/src/main/java/com/example/feature/youtube/detector/YouTubeContentInspectionEngine.kt`
   - Keep `YouTubePlaybackSandbox` active across full-screen toggles and player control clicks; only exit the sandbox when an actual video navigation or title change occurs.
5. `app/src/main/java/com/example/core/accessibility/FocusAccessibilityService.kt`
   - Filter out player-control/full-screen clicks from triggering `YouTubePlaybackSandbox.onVideoNavigationOccurred()`.
   - Handle `needsExitFullScreenForVerification` by automatically exiting full screen once to read the portrait watch header before making a block decision.
   - Update `handleYouTubeUnapprovedChannelBlocked` to properly exit full screen and close the unapproved video if a block occurs while in full screen.
6. `app/src/test/java/com/example/feature/youtube/YouTubeFullScreenApprovalTest.kt`
   - Add unit tests verifying:
     - Approved portrait video -> Enter Full Screen -> controls hidden > 5s -> stays `ALLOW` (no false block).
     - Approved full-screen video -> tap Play/Pause, Seek, Quality, Exit Full Screen -> stays `ALLOW`.
     - Full-screen "More videos" shelf visible with unapproved channel cards -> ignored while watching approved video, blocked immediately if an unapproved card is clicked.
     - Unverified video starting directly in full screen -> triggers one-time full-screen exit for portrait header verification instead of immediate false block.
