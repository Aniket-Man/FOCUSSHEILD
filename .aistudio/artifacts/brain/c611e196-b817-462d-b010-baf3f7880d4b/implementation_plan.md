# FocusShield System-Wide AI Ecosystem & Schedule UI Fix — Master Plan

## 1. Executive Summary & Core Vision
FocusShield will evolve into a **proactive, system-wide AI productivity and digital wellbeing ecosystem** accompanied by an immediate layout fix for the **New Schedule Save button**.

Rather than functioning only as a standalone chatbot, **AI is woven directly into every feature of FocusShield** (Focus Timer, Planner & Schedules, Digital Wellbeing & Stats, and App/Website/18+/Notification Blockers) while also providing a **dedicated AI Navigation Tab** on the home screen's bottom navigation bar.

### Core Pillars of the Master Plan
1. **Google Account & Cloud Sync Prerequisite**: All AI capabilities require an authenticated Google Account with Cloud Sync active. The AI synthesizes long-term cloud-synced records (study sessions, streaks, schedules, blocker states, and past AI conversations) with real-time on-device usage telemetry (per-app daily/weekly screen time, launch frequency, and peak distraction hours).
2. **Strict Mode is 100% Human-Only (Hard Safety Boundary)**: **The AI is strictly prohibited from applying, enabling, or disabling Strict Mode in every feature** (Focus Timer, App Limits, Schedules, and Blockers). Because Strict Mode locks down settings and prevents modifications or early exits, only the human user can manually turn on or confirm Strict Mode.
3. **Embedded AI in Every Feature**: Every screen displays context-aware AI insights and **Inline One-Tap Apply Cards** tailored to the user's real usage patterns.
4. **Home Navigation AI Button & Cloud-Synced AI Chat + App Control**: The bottom navigation bar (alongside Focus, Planner, Stats, and Block) includes a dedicated **AI button** styled in the exact same translucent glass navigation bar aesthetic, using your custom radial neural-starburst PNG icon. Tapping it opens the **AI Chat & Control Hub**, where all conversations are **saved to the cloud and loaded from the cloud**, and where you can ask for guidance or instruct the AI to directly control and update any feature in the app (except Strict Mode).
5. **24/7 Proactive Background AI Notifications**: A continuous background engine monitors app usage spikes, distraction windows, and daily schedules to deliver personalized, AI-generated notifications around the clock.

---

## 2. Immediate UI Fix: "New Schedule" Save Button Visibility

### Root Cause
On the **New Schedule / Edit Schedule** screen, the full-screen dialog uses a custom window layout where system navigation bar insets are not properly padded at the bottom of the container. As a result, the bottom **Save** button gets clipped or hidden underneath the system navigation bar or bottom bar area.

### Solution
- **Sticky Safe-Area Footer Bar**: Anchor the bottom **Save** button inside an elevated, persistent footer bar that applies explicit system navigation bar and keyboard (`ime`) safe-area insets plus generous vertical padding so it always floats cleanly above the bottom of the screen.
- **Scrollable Form Body**: Ensure the schedule form content scrolls independently above the pinned footer so the **Save** button is never pushed off-screen or obscured on any screen size.
- **Header Save Action Retained**: Keep the top-right header **Save** action synced with the bottom button for convenient one-handed access.

---

## 3. Prerequisite Gate: Google Account & Cloud Sync Requirement

### Why Sign-In Is Mandatory for AI
To suggest practical app limits, build realistic schedules, and maintain continuity across sessions, the AI must combine:
- **Cloud-Synced Account Data**: Historical study sessions, subject preferences, recurring schedules, configured app limits, streak history, and **saved AI chat history** tied to the user's Google Account.
- **On-Device Telemetry**: Real-time per-app screen time (last 24 hours and 7-day trend), app launch counts, hourly usage heatmaps, and active blocker states.

### Gate Behavior
- **Signed-Out State**:
  - Inline AI cards across Focus, Planner, Stats, and Blockers, as well as the **AI Navigation Tab**, display a sleek **"Unlock AI with Google Cloud Sync"** card.
  - Explains that connecting a Google Account enables personalized app limits, automated schedule recommendations, cloud-synced AI chats, and 24/7 background coaching.
  - Includes a one-tap **Sign in with Google** button that triggers the Google Sign-In & Cloud Sync flow and immediately activates all AI features upon completion.
- **Signed-In State**:
  - Unlocks inline AI recommendations across all tabs, loads the user's cloud-saved AI chat history, enables natural-language app control, and activates 24/7 background AI notifications.

---

## 4. Hard Safety Boundary: Strict Mode Is Human-Only Across Every Feature

To protect user agency and prevent unintended lockouts, **Strict Mode is strictly restricted to human interaction across the entire application**:
- **Focus Timer**: The AI can start or configure a Focus Timer session (selecting duration and subject), but `Strict Mode` is always forced to `OFF` for any AI-initiated or AI-suggested session. Only the user can manually toggle Strict Mode on the Focus screen.
- **Planner & Schedules**: When the AI suggests or creates a focus schedule (via inline Apply cards or chat commands), the schedule's Strict Mode setting is locked to `OFF`. Only a human editing the schedule manually can enable Strict Mode.
- **App Limits & Blockers**: The AI can set daily app time limits, toggle website blocking, toggle the 18+ adult site blocker, and toggle notification blocking, but it **cannot** activate Strict Mode lockouts on limits or blockers.
- **Chat Guardrail**: If the user asks the AI in chat to *"Turn on Strict Mode"*, the AI will politely explain that **Strict Mode is a human-only safety control** and must be toggled manually by the user.

---

## 5. Embedded AI Across Every App Feature (Inline One-Tap Apply)

### A. App Limits & Blockers (`Block` Tab)
- **Practical App Limit Suggestions**:
  - Analyzes each installed app's 7-day average daily screen time and today's usage.
  - Generates **Inline One-Tap Apply Cards** with realistic, graduated reduction targets (for example: *"You average 2h 15m/day on Instagram, mostly between 9 PM and 11 PM. Set a practical 1h 30m daily limit to save 45m today"*).
  - Tapping **Apply Limit** immediately configures the daily limit for that app (without Strict Mode).
- **Smart Shield Recommendations**:
  - Recommends enabling the **18+ Adult Site Blocker**, **Notification Blocker**, or specific distracting **Website Blocks** when late-night browsing or high notification interruptions are detected, with a one-tap **Apply** button.

### B. Smart Planner & Focus Schedules (`Planner` Tab)
- **AI Schedule Generator & Optimizer**:
  - Cross-references existing schedules with peak distraction windows and historical focus completion rates.
  - Displays tailored **AI Schedule Suggestion Cards** directly inside the Planner (e.g., *"Afternoon Deep Work: 2:30 PM – 4:00 PM (Mon–Fri) • Blocks top 3 distracting apps"*).
  - Tapping **Apply Schedule** creates and activates the recurring schedule (with Strict Mode off) and syncs it to the cloud.

### C. Focus Timer & Home (`Focus` Tab)
- **Adaptive Session Coach**:
  - Displays a compact AI recommendation banner suggesting the ideal focus duration and subject based on time of day, upcoming planner blocks, and recent completion streak.
  - Includes a one-tap **Apply Session Setup** button that configures the timer duration and subject (never enabling Strict Mode).

### D. Digital Wellbeing & Usage Dashboard (`Stats` Tab)
- **AI Wellbeing Diagnosis & Weekly Progress Report**:
  - Positioned above the weekly usage bar chart and storage/wellbeing breakdown.
  - Summarizes week-over-week screen-time trends, highlights top time-sink apps, compares focus hours against distraction hours, and provides one-tap **Apply** actions to cap newly detected time-sink apps.

---

## 6. Home / Bottom Navigation AI Icon & Cloud-Synced AI Chat + App Control

### A. Bottom Navigation Bar Integration & Custom PNG Icon
- **Navigation Bar Placement**:
  - The bottom navigation bar (`FocusBottomNavigation`) will feature the **AI tab** right alongside **Focus**, **Planner**, **Stats**, and **Block**, using the exact same translucent glass pill aesthetic, spring scale animations, and subtle border glow.
- **Custom PNG Icon Design**:
  - Uses your provided radial neural-starburst gradient PNG icon (`Screenshot_2026_1029_102332_ea1ca7bc.png`), processed with a clean transparent background so the mint-cyan-blue-purple neural nodes render crisply inside the navigation bar and home UI.

### B. Cloud-Saved & Cloud-Loaded AI Chat History
- **Full Cloud Persistence**:
  - Every conversation and message with the AI is saved to your Google Account in the cloud (Firestore) and cached locally on the device for instant loading.
  - When signing in on a new device or reinstalling the app, your previous AI chats, advice, and action history are automatically loaded from the cloud.
  - Users can start a new chat thread or clear chat history at any time, keeping cloud and local storage in sync.

### C. Full App Control via AI Chat (Except Strict Mode)
- Talking to the AI in chat is both an intelligent coaching conversation **and** a live command center that can control the app on your behalf.
- When you ask the AI to make a change—or ask for suggestions—the AI responds with tailored guidance and executes (or offers one-tap confirmation chips for) structured app actions:
  - **Set / Update / Remove App Time Limits**: e.g., *"Set a 45-minute limit on YouTube and 30 minutes on Instagram"* → updates app limits immediately.
  - **Create / Edit / Toggle Focus Schedules**: e.g., *"Create a morning study schedule from 8 AM to 10 AM on weekdays"* → creates and saves the schedule.
  - **Control Blockers**: e.g., *"Turn on the 18+ adult site blocker, block reddit.com, and mute notifications"* → toggles the 18+ filter, adds the website to blocked sites, and enables the notification blocker.
  - **Start / Configure Focus Sessions**: e.g., *"Start a 45-minute Physics focus session"* → configures and starts a normal focus session.
  - **Strict Mode Blocked**: Any request to enable or modify Strict Mode is blocked by the human-only safety rule.

---

## 7. 24/7 Proactive AI Notification Engine

For signed-in users, a periodic background worker runs 24/7 to evaluate device usage telemetry and cloud-synced study goals, generating four distinct categories of smart notifications:

1. **Real-Time App Usage Spike Warnings**:
   - Detects rapid continuous usage or approaching daily limits on distracting apps and sends a personalized intervention nudge with a quick action to cap the app.
2. **Morning Focus Plan & Evening Usage Reflection Briefings**:
   - **Morning Briefing**: Summarizes today's scheduled focus blocks, target study hours, and top priority subject.
   - **Evening Reflection**: Reviews total focus time achieved vs. screen time, streak progress, and a practical adjustment for tomorrow.
3. **High-Distraction Hour Smart Nudges**:
   - Recognizes when the user enters their historical peak distraction window with no active schedule and nudges them to start a focus session.
4. **Weekly Deep-Dive Productivity & Digital Wellbeing Report**:
   - Delivers a comprehensive weekly summary comparing total focus hours, most-used apps, time saved by blockers, and suggested limit calibrations for the upcoming week.
